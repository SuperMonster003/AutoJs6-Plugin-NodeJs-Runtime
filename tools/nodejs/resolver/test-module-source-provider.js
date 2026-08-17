#!/usr/bin/env node

"use strict";

const assert = require("node:assert/strict");
const childProcess = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const CONTRACT_VERSION = 2;
const CPP_SOURCE = path.resolve(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp");

// Windows checkouts may carry CRLF (git core.autocrlf); the source contract
// targets below are written with plain \n, so normalize once on read.
function readCppSource() {
  return fs.readFileSync(CPP_SOURCE, "utf8").replace(/\r\n/g, "\n");
}
const JAVA_PROVIDER_SOURCE = path.resolve(
  __dirname,
  "../../../app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginModuleSourceProviderFileTransportSession.java",
);
const JAVA_SERVICE_SOURCE = path.resolve(
  __dirname,
  "../../../app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java",
);
const JAVA_PERMISSION_SOURCE = path.resolve(
  __dirname,
  "../../../app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java",
);
const MIN_NODE_MAJOR = 24;

if (process.argv[2] === "--worker") {
  runTransportWorker(process.argv[3]);
} else {
  runTests(parseTestArgs(process.argv.slice(2)));
}

function parseTestArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument !== "--report") throw new Error(`Unknown argument: ${argument}`);
    const value = argv[index + 1];
    if (!value) throw new Error("Missing value for --report.");
    options.report = path.resolve(value);
    index += 1;
  }
  return options;
}

function runTests(options = {}) {
  const nodeMajor = Number(process.versions.node.split(".")[0]);
  assert.ok(nodeMajor >= MIN_NODE_MAJOR, `Node ${MIN_NODE_MAJOR}+ is required; found ${process.version}.`);
  const chunks = embeddedScriptChunks();
  assert.equal(chunks.length, 15, "embedded script interpolation layout changed");
  assertAndroidCredentialDataAliasContract();
  process.stdout.write(`PASS Android credential-data canonical path alias contract${os.EOL}`);
  const sourceMutationResults = runX3hResolverSourceMutationSelfTests();
  process.stdout.write(`PASS ${sourceMutationResults.length} X3h resolver source mutations${os.EOL}`);

  const scenarios = [
    {
      name: "decrypted CommonJS",
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.cjs": { status: "decrypted", source: "module.exports = 'decrypted-cjs';" } },
      source: "globalThis.__providerTestValue = require('./dep.cjs');",
      expectedValue: "decrypted-cjs",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
    },
    {
      name: "decrypted CommonJS over legacy v1 host transport",
      contractVersion: 1,
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.cjs": { status: "decrypted", source: "module.exports = 'decrypted-cjs-v1';" } },
      source: "globalThis.__providerTestValue = require('./dep.cjs');",
      expectedValue: "decrypted-cjs-v1",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
    },
    {
      name: "plaintext CommonJS fallback",
      files: { "dep.cjs": "module.exports = 'plain-cjs-\u4e2d\ud83d\ude80';" },
      responses: { "dep.cjs": { status: "not_encrypted" } },
      source: "globalThis.__providerTestValue = require('./dep.cjs');",
      expectedValue: "plain-cjs-\u4e2d\ud83d\ude80",
      expectedPreparationCount: 0,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext TypeScript CommonJS",
      files: { "dep.ts": "const value: string = 'plain-ts'; module.exports = value;" },
      responses: { "dep.ts": { status: "not_encrypted" } },
      preparations: {
        "dep.ts": { status: "prepared", source: "const value = 'plain-ts'; module.exports = value;" },
      },
      source: "globalThis.__providerTestValue = require('./dep.ts');",
      expectedValue: "plain-ts",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 2, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext CTS cache and one-time parent",
      timeoutMs: 2000,
      files: {
        "dep.cts": "const value: number = 40 + 2; module.exports = value; // \u4e2d\ud83d\ude80",
      },
      responses: { "dep.cts": { status: "not_encrypted" } },
      preparations: {
        "dep.cts": {
          status: "prepared",
          source: "const value = 40 + 2; module.exports = value; // \u4e2d\ud83d\ude80",
        },
      },
      source: [
        "const first = require('./dep.cts');",
        "const resolved = require.resolve('./dep.cts');",
        "delete require.cache[resolved];",
        "const second = require('./dep.cts');",
        "globalThis.__providerTestValue = [first, second];",
      ].join(" "),
      expectedValue: [42, 42],
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext MTS static ESM",
      entryName: "entry.mjs",
      files: { "dep.mts": "const value: string = 'plain-mts-static'; export default value;" },
      responses: { "dep.mts": { status: "not_encrypted" } },
      preparations: {
        "dep.mts": { status: "prepared", source: "const value = 'plain-mts-static'; export default value;" },
      },
      source: "import value from './dep.mts'; globalThis.__providerTestValue = value;",
      expectedValue: "plain-mts-static",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1, esmEntry: true },
    },
    {
      name: "plaintext MTS dynamic import",
      files: { "dep.mts": "const value: string = 'plain-mts-dynamic'; export default value;" },
      responses: { "dep.mts": { status: "not_encrypted" } },
      preparations: {
        "dep.mts": { status: "prepared", source: "const value = 'plain-mts-dynamic'; export default value;" },
      },
      source: "return import('./dep.mts').then((ns) => { globalThis.__providerTestValue = ns.default; });",
      expectedValue: "plain-mts-dynamic",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext TypeScript declaration bypass",
      files: {
        "a.d.ts": "module.exports = 'd-ts';",
        "b.d.cts": "module.exports = 'd-cts';",
        "c.d.mts": "export default 'd-mts';",
      },
      responses: {
        "a.d.ts": { status: "not_encrypted" },
        "b.d.cts": { status: "not_encrypted" },
        "c.d.mts": { status: "not_encrypted" },
      },
      source: [
        "const a = require('./a.d.ts');",
        "const b = require('./b.d.cts');",
        "return import('./c.d.mts').then((ns) => { globalThis.__providerTestValue = [a, b, ns.default]; });",
      ].join(" "),
      expectedValue: ["d-ts", "d-cts", "d-mts"],
      expectedPreparationCount: 0,
      expected: { moduleProviderRequestCount: 4, moduleProviderNotEncryptedCount: 3 },
    },
    {
      name: "plaintext TSX canonical rejection",
      files: { "dep.tsx": "export default <div />;" },
      responses: { "dep.tsx": { status: "not_encrypted" } },
      source: "require('./dep.tsx');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION",
      expectedProviderEnabled: false,
      expectedPreparationCount: 0,
      expected: { moduleProviderRequestCount: 0 },
    },
    {
      name: "missing plaintext CommonJS materialization",
      files: {},
      responses: {
        "missing.cjs": {
          resolve: { status: "not_encrypted" },
          materialize: { status: "plaintext", source: "module.exports = 'missing-cjs';" },
        },
      },
      source: "globalThis.__providerTestValue = require('./missing.cjs');",
      expectedValue: "missing-cjs",
      expectedPreparationCount: 0,
      expected: {
        moduleProviderRequestCount: 2,
        moduleProviderMissingCandidateRequestCount: 1,
        moduleProviderNotEncryptedCount: 1,
        moduleProviderMaterializedCount: 1,
        moduleProviderMaterializedSourceBytes: Buffer.byteLength("module.exports = 'missing-cjs';"),
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "missing.cjs", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "missing.cjs", status: "materialized_plaintext" },
      ],
    },
    {
      name: "missing plaintext CTS materialization uses private preparation",
      files: {},
      responses: {
        "missing.cts": {
          resolve: { status: "not_encrypted" },
          materialize: {
            status: "plaintext",
            source: "const value: number = 42; module.exports = value;",
          },
        },
      },
      preparations: {
        "missing.cts": { status: "prepared", source: "const value = 42; module.exports = value;" },
      },
      source: "const stem = 'missing'; globalThis.__providerTestValue = require('./' + stem + '.cts');",
      expectedValue: 42,
      expectedPreparationCount: 1,
      expected: {
        moduleProviderRequestCount: 2,
        moduleProviderMissingCandidateRequestCount: 1,
        moduleProviderNotEncryptedCount: 1,
        moduleProviderMaterializedCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "missing.cts", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "missing.cts", status: "materialized_plaintext" },
      ],
    },
    {
      name: "missing plaintext MTS materialization uses private preparation",
      entryName: "entry.mjs",
      files: {},
      responses: {
        "missing.mts": {
          resolve: { status: "not_encrypted" },
          materialize: {
            status: "plaintext",
            source: "const value: string = 'missing-mts'; export default value;",
          },
        },
      },
      preparations: {
        "missing.mts": {
          status: "prepared",
          source: "const value = 'missing-mts'; export default value;",
        },
      },
      source: "import value from './missing.mts'; globalThis.__providerTestValue = value;",
      expectedValue: "missing-mts",
      expectedPreparationCount: 1,
      expected: {
        moduleProviderRequestCount: 2,
        moduleProviderMissingCandidateRequestCount: 1,
        moduleProviderNotEncryptedCount: 1,
        moduleProviderMaterializedCount: 1,
        esmEntry: true,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "missing.mts", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "missing.mts", status: "materialized_plaintext" },
      ],
    },
    {
      name: "extensionless CommonJS probes supported candidates but not raw base",
      files: {},
      responses: {
        "missing.js": {
          resolve: { status: "not_encrypted" },
          materialize: { status: "plaintext", source: "module.exports = 'extension-probed';" },
        },
      },
      source: "globalThis.__providerTestValue = require('./missing');",
      expectedValue: "extension-probed",
      expectedPreparationCount: 0,
      expected: {
        moduleProviderRequestCount: 3,
        moduleProviderMissingCandidateRequestCount: 1,
        moduleProviderNotEncryptedCount: 1,
        moduleProviderNotFoundCount: 1,
        moduleProviderMaterializedCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "missing.js", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "missing.js", status: "materialized_plaintext" },
        { operation: "resolve", path: "package.json", status: "not_found" },
      ],
    },
    {
      name: "missing exact not-found is authoritative",
      files: {},
      responses: { "missing.cjs": { status: "not_found" } },
      source: "require('./missing.cjs');",
      expectedSucceeded: false,
      expectedPreparationCount: 0,
      expected: {
        moduleProviderRequestCount: 1,
        moduleProviderMissingCandidateRequestCount: 0,
        moduleProviderNotFoundCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "missing.cjs", status: "not_found" },
      ],
    },
    {
      name: "plaintext preparation timeout inherits parent deadline",
      // Production intentionally caps the effective provider timeout at 5s.
      // A fixture clock keeps host scheduling pauses outside that production
      // deadline while still advancing the time observed by the runtime.
      timeoutMs: 30000,
      virtualClockStartMs: 1700000000000,
      files: { "dep.cts": "const value: number = 7; module.exports = value;" },
      responses: {
        "dep.cts": {
          status: "not_encrypted",
          delayMs: 300,
          advanceVirtualClockMs: 300,
          minimumDeadlineDeductionMs: 100,
        },
      },
      preparations: { "dep.cts": { status: "timed_out" } },
      source: "require('./dep.cts');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT",
      expectedPreparationCount: 1,
      expectedPreparationParentTimeoutMs: 5000,
      expectedMinimumPreparationDeadlineDeductionMs: 100,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext preparation rejects wrong prepared path",
      files: { "dep.ts": "const value: number = 1; module.exports = value;" },
      responses: { "dep.ts": { status: "not_encrypted" } },
      preparations: {
        "dep.ts": { status: "prepared", source: "const value = 1; module.exports = value;", wrongPath: true },
      },
      source: "require('./dep.ts');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext preparation rejects prepared byte mismatch",
      files: { "dep.cts": "const value: number = 2; module.exports = value;" },
      responses: { "dep.cts": { status: "not_encrypted" } },
      preparations: {
        "dep.cts": {
          status: "prepared",
          source: "const value = 2; module.exports = value;",
          sourceBytesDelta: 1,
        },
      },
      source: "require('./dep.cts');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext preparation rejects raw byte receipt mismatch",
      files: { "dep.mts": "const value: number = 3; export default value;" },
      responses: { "dep.mts": { status: "not_encrypted" } },
      preparations: {
        "dep.mts": {
          status: "prepared",
          source: "const value = 3; export default value;",
          rawSourceBytesDelta: 1,
        },
      },
      source: "return import('./dep.mts');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "plaintext preparation rejects canonical source mismatch",
      files: { "dep.ts": "const value: number = 4; module.exports = value;" },
      responses: { "dep.ts": { status: "not_encrypted" } },
      preparations: {
        "dep.ts": {
          status: "prepared",
          source: "const value = 4; module.exports = value;",
          resolvedPath: "wrong.ts",
        },
      },
      source: "require('./dep.ts');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED",
      expectedPreparationCount: 1,
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
    },
    {
      name: "decrypted static ESM",
      entryName: "entry.mjs",
      files: { "dep.mjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.mjs": { status: "decrypted", source: "export default 'decrypted-esm';" } },
      source: "import value from './dep.mjs'; globalThis.__providerTestValue = value;",
      expectedValue: "decrypted-esm",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1, esmEntry: true },
    },
    {
      name: "decrypted dynamic import",
      files: { "dep.mjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.mjs": { status: "decrypted", source: "export default 'dynamic-esm';" } },
      source: "return import('./dep.mjs').then((ns) => { globalThis.__providerTestValue = ns.default; });",
      expectedValue: "dynamic-esm",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
    },
    {
      name: "CommonJS extensionless bounded candidate order",
      files: {},
      responses: {
        "ordered.json": { status: "decrypted", source: "{\"value\":\"cjs-order\"}" },
      },
      source: "globalThis.__providerTestValue = require('./ordered').value;",
      expectedValue: "cjs-order",
      expected: {
        moduleProviderRequestCount: 5,
        moduleProviderDecryptedCount: 1,
        moduleProviderNotFoundCount: 4,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "ordered.js", status: "not_found" },
        { operation: "resolve", path: "ordered.cjs", status: "not_found" },
        { operation: "resolve", path: "ordered.cts", status: "not_found" },
        { operation: "resolve", path: "ordered.ts", status: "not_found" },
        { operation: "resolve", path: "ordered.json", status: "decrypted" },
      ],
    },
    {
      name: "ESM extensionless bounded candidate order",
      entryName: "entry.mjs",
      files: {},
      responses: {
        "ordered.json": { status: "decrypted", source: "{\"value\":\"esm-order\"}" },
      },
      source: "import value from './ordered' with { type: 'json' }; globalThis.__providerTestValue = value.value;",
      expectedValue: "esm-order",
      expected: {
        moduleProviderRequestCount: 7,
        moduleProviderDecryptedCount: 1,
        moduleProviderNotFoundCount: 6,
        esmEntry: true,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "ordered.mts", status: "not_found" },
        { operation: "resolve", path: "ordered.ts", status: "not_found" },
        { operation: "resolve", path: "ordered.mjs", status: "not_found" },
        { operation: "resolve", path: "ordered.js", status: "not_found" },
        { operation: "resolve", path: "ordered.cts", status: "not_found" },
        { operation: "resolve", path: "ordered.cjs", status: "not_found" },
        { operation: "resolve", path: "ordered.json", status: "decrypted" },
      ],
    },
    {
      name: "missing local package metadata materializes before main candidates",
      files: {},
      responses: {
        "localpkg/package.json": {
          resolve: { status: "not_encrypted" },
          materialize: { status: "plaintext", source: "{\"main\":\"main\"}" },
        },
        "localpkg/main.cjs": { status: "decrypted", source: "module.exports = 'local-main';" },
      },
      source: "globalThis.__providerTestValue = require('./localpkg');",
      expectedValue: "local-main",
      expected: {
        moduleProviderRequestCount: 9,
        moduleProviderMissingCandidateRequestCount: 1,
        moduleProviderNotEncryptedCount: 1,
        moduleProviderMaterializedCount: 1,
        moduleProviderDecryptedCount: 1,
        moduleProviderNotFoundCount: 6,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "localpkg.js", status: "not_found" },
        { operation: "resolve", path: "localpkg.cjs", status: "not_found" },
        { operation: "resolve", path: "localpkg.cts", status: "not_found" },
        { operation: "resolve", path: "localpkg.ts", status: "not_found" },
        { operation: "resolve", path: "localpkg.json", status: "not_found" },
        { operation: "resolve", path: "localpkg/package.json", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "localpkg/package.json", status: "materialized_plaintext" },
        { operation: "resolve", path: "localpkg/main.js", status: "not_found" },
        { operation: "resolve", path: "localpkg/main.cjs", status: "decrypted" },
      ],
    },
    {
      name: "materialized package metadata rejects byte receipt drift",
      files: {},
      responses: {
        "receiptpkg/package.json": {
          resolve: { status: "not_encrypted" },
          materialize: {
            status: "plaintext",
            source: "{\"main\":\"index.cjs\"}",
            sourceBytesDelta: 1,
          },
        },
      },
      source: "require('./receiptpkg');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expectedProviderEvents: [
        { operation: "resolve", path: "receiptpkg.js", status: "not_found" },
        { operation: "resolve", path: "receiptpkg.cjs", status: "not_found" },
        { operation: "resolve", path: "receiptpkg.cts", status: "not_found" },
        { operation: "resolve", path: "receiptpkg.ts", status: "not_found" },
        { operation: "resolve", path: "receiptpkg.json", status: "not_found" },
        { operation: "resolve", path: "receiptpkg/package.json", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "receiptpkg/package.json", status: "materialized_plaintext" },
      ],
    },
    {
      name: "materialized package metadata rejects open-time identity race",
      files: {},
      responses: {
        "racepkg/package.json": {
          resolve: { status: "not_encrypted" },
          materialize: { status: "plaintext", source: "{\"main\":\"index.cjs\"}" },
        },
      },
      raceReplaceOnOpen: {
        path: "racepkg/package.json",
        source: "{\"main\":\"other.cjs\"}",
      },
      source: "require('./racepkg');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expectedProviderEvents: [
        { operation: "resolve", path: "racepkg.js", status: "not_found" },
        { operation: "resolve", path: "racepkg.cjs", status: "not_found" },
        { operation: "resolve", path: "racepkg.cts", status: "not_found" },
        { operation: "resolve", path: "racepkg.ts", status: "not_found" },
        { operation: "resolve", path: "racepkg.json", status: "not_found" },
        { operation: "resolve", path: "racepkg/package.json", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "racepkg/package.json", status: "materialized_plaintext" },
      ],
    },
    {
      name: "materialized package metadata enforces single-source budget",
      files: {},
      responses: {
        "budgetpkg/package.json": {
          resolve: { status: "not_encrypted" },
          materialize: {
            status: "plaintext",
            repeatedByte: 0x20,
            repeatedByteCount: 16 * 1024 * 1024 + 1,
          },
        },
      },
      source: "require('./budgetpkg');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED",
      expectedProviderEvents: [
        { operation: "resolve", path: "budgetpkg.js", status: "not_found" },
        { operation: "resolve", path: "budgetpkg.cjs", status: "not_found" },
        { operation: "resolve", path: "budgetpkg.cts", status: "not_found" },
        { operation: "resolve", path: "budgetpkg.ts", status: "not_found" },
        { operation: "resolve", path: "budgetpkg.json", status: "not_found" },
        { operation: "resolve", path: "budgetpkg/package.json", status: "not_encrypted" },
        { operation: "materialize_missing_plaintext", path: "budgetpkg/package.json", status: "materialized_plaintext" },
      ],
    },
    {
      name: "missing local package metadata advances to bounded index order",
      files: {},
      responses: {
        "indexpkg/index.cjs": { status: "decrypted", source: "module.exports = 'local-index';" },
      },
      source: "globalThis.__providerTestValue = require('./indexpkg');",
      expectedValue: "local-index",
      expected: {
        moduleProviderRequestCount: 8,
        moduleProviderDecryptedCount: 1,
        moduleProviderNotFoundCount: 7,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "indexpkg.js", status: "not_found" },
        { operation: "resolve", path: "indexpkg.cjs", status: "not_found" },
        { operation: "resolve", path: "indexpkg.cts", status: "not_found" },
        { operation: "resolve", path: "indexpkg.ts", status: "not_found" },
        { operation: "resolve", path: "indexpkg.json", status: "not_found" },
        { operation: "resolve", path: "indexpkg/package.json", status: "not_found" },
        { operation: "resolve", path: "indexpkg/index.js", status: "not_found" },
        { operation: "resolve", path: "indexpkg/index.cjs", status: "decrypted" },
      ],
    },
    {
      name: "missing package imports metadata and target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"app\",\"imports\":{\"#alias\":\"./lib/alias\"}}",
        },
        "lib/alias.js": { status: "decrypted", source: "module.exports = 'imports-target';" },
      },
      source: "globalThis.__providerTestValue = require('#alias');",
      expectedValue: "imports-target",
      expected: { moduleProviderRequestCount: 2, moduleProviderDecryptedCount: 2 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
        { operation: "resolve", path: "lib/alias.js", status: "decrypted" },
      ],
    },
    {
      name: "package imports wildcard prefers specific null over earlier broad target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"app\",\"imports\":{\"#*\":\"./public/*.js\",\"#private/*\":null}}",
        },
        "public/private/x.js": { status: "decrypted", source: "module.exports = 'bypass';" },
      },
      source: "require('#private/x');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_PACKAGE_IMPORT_NOT_DEFINED",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
      ],
    },
    {
      name: "package imports wildcard prefers specific target over earlier broad target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"app\",\"imports\":{\"#*\":\"./public/*.js\",\"#private/*\":\"./private/*.cjs\"}}",
        },
        "public/private/x.js": { status: "decrypted", source: "module.exports = 'broad';" },
        "private/x.cjs": { status: "decrypted", source: "module.exports = 'specific-import';" },
      },
      source: "globalThis.__providerTestValue = require('#private/x');",
      expectedValue: "specific-import",
      expected: { moduleProviderRequestCount: 2, moduleProviderDecryptedCount: 2 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
        { operation: "resolve", path: "private/x.cjs", status: "decrypted" },
      ],
    },
    {
      name: "package imports rejects invalid non-hash wildcard key",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"app\",\"imports\":{\"*\":\"./secret/*.js\"}}",
        },
        "secret/#x.js": { status: "decrypted", source: "module.exports = 'bypass';" },
      },
      source: "require('#x');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_INVALID_PACKAGE_TARGET",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
      ],
    },
    {
      name: "missing package self-reference exports target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"selfpkg\",\"exports\":{\"./feature\":\"./feature\"}}",
        },
        "feature.js": { status: "decrypted", source: "module.exports = 'self-target';" },
      },
      source: "globalThis.__providerTestValue = require('selfpkg/feature');",
      expectedValue: "self-target",
      expected: { moduleProviderRequestCount: 2, moduleProviderDecryptedCount: 2 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
        { operation: "resolve", path: "feature.js", status: "decrypted" },
      ],
    },
    {
      name: "package exports wildcard prefers specific null over earlier broad target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"selfpkg\",\"exports\":{\"./*\":\"./public/*.js\",\"./private/*\":null}}",
        },
        "public/private/x.js": { status: "decrypted", source: "module.exports = 'bypass';" },
      },
      source: "require('selfpkg/private/x');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_PACKAGE_PATH_NOT_EXPORTED",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
      ],
    },
    {
      name: "package exports wildcard prefers specific target over earlier broad target",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"selfpkg\",\"exports\":{\"./*\":\"./public/*.js\",\"./private/*\":\"./private/*.cjs\"}}",
        },
        "public/private/x.js": { status: "decrypted", source: "module.exports = 'broad';" },
        "private/x.cjs": { status: "decrypted", source: "module.exports = 'specific-export';" },
      },
      source: "globalThis.__providerTestValue = require('selfpkg/private/x');",
      expectedValue: "specific-export",
      expected: { moduleProviderRequestCount: 2, moduleProviderDecryptedCount: 2 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
        { operation: "resolve", path: "private/x.cjs", status: "decrypted" },
      ],
    },
    {
      name: "package exports rejects mixed subpath and condition keys",
      files: {},
      responses: {
        "package.json": {
          status: "decrypted",
          source: "{\"name\":\"selfpkg\",\"exports\":{\"./public\":\"./public.js\",\"default\":\"./secret.js\"}}",
        },
        "secret.js": { status: "decrypted", source: "module.exports = 'bypass';" },
      },
      source: "require('selfpkg');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_INVALID_PACKAGE_TARGET",
      expected: { moduleProviderRequestCount: 1, moduleProviderDecryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "decrypted" },
      ],
    },
    {
      name: "missing bare node_modules package metadata and main",
      files: {},
      responses: {
        "node_modules/barepkg/package.json": { status: "decrypted", source: "{\"main\":\"entry\"}" },
        "node_modules/barepkg/entry.js": { status: "decrypted", source: "module.exports = 'bare-main';" },
      },
      source: "globalThis.__providerTestValue = require('barepkg');",
      expectedValue: "bare-main",
      expected: {
        moduleProviderRequestCount: 3,
        moduleProviderDecryptedCount: 2,
        moduleProviderNotFoundCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "not_found" },
        { operation: "resolve", path: "node_modules/barepkg/package.json", status: "decrypted" },
        { operation: "resolve", path: "node_modules/barepkg/entry.js", status: "decrypted" },
      ],
    },
    {
      name: "missing node_modules subpath exports target",
      files: {},
      responses: {
        "node_modules/subpkg/package.json": {
          status: "decrypted",
          source: "{\"exports\":{\"./sub\":\"./lib/sub\"}}",
        },
        "node_modules/subpkg/lib/sub.js": { status: "decrypted", source: "module.exports = 'subpath-target';" },
      },
      source: "globalThis.__providerTestValue = require('subpkg/sub');",
      expectedValue: "subpath-target",
      expected: {
        moduleProviderRequestCount: 3,
        moduleProviderDecryptedCount: 2,
        moduleProviderNotFoundCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "not_found" },
        { operation: "resolve", path: "node_modules/subpkg/package.json", status: "decrypted" },
        { operation: "resolve", path: "node_modules/subpkg/lib/sub.js", status: "decrypted" },
      ],
    },
    {
      name: "missing candidate denial is terminal before next extension",
      files: {},
      responses: {
        "stop.js": {
          status: "denied",
          errorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
          errorMessage: "candidate denied",
        },
        "stop.cjs": { status: "decrypted", source: "module.exports = 'must-not-run';" },
      },
      source: "require('./stop');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expected: { moduleProviderRequestCount: 1, moduleProviderDeniedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "stop.js", status: "denied" },
      ],
    },
    {
      name: "missing candidate timeout is terminal before next extension",
      timeoutMs: 75,
      files: {},
      responses: {
        "stop.js": { status: "ignore" },
        "stop.cjs": { status: "decrypted", source: "module.exports = 'must-not-run';" },
      },
      source: "require('./stop');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT",
      expected: { moduleProviderRequestCount: 1, moduleProviderTimedOutCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "stop.js", status: "no_response" },
      ],
    },
    {
      name: "authorized plaintext candidate deletion is terminal before next extension",
      files: {
        "vanish.js": "module.exports = 'original';",
      },
      responses: {
        "vanish.js": { status: "not_encrypted", removeCandidateBeforeResponse: true },
        "vanish.cjs": { status: "decrypted", source: "module.exports = 'must-not-run';" },
      },
      source: "require('./vanish');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "vanish.js", status: "not_encrypted" },
      ],
    },
    {
      name: "authorized plaintext candidate type change is terminal before next extension",
      files: {
        "shape.js": "module.exports = 'original';",
      },
      responses: {
        "shape.js": { status: "not_encrypted", replaceCandidateWithDirectoryBeforeResponse: true },
        "shape.cjs": { status: "decrypted", source: "module.exports = 'must-not-run';" },
      },
      source: "require('./shape');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expected: { moduleProviderRequestCount: 1, moduleProviderNotEncryptedCount: 1 },
      expectedProviderEvents: [
        { operation: "resolve", path: "shape.js", status: "not_encrypted" },
      ],
    },
    {
      name: "missing native addon never reaches provider",
      files: {},
      responses: { "addon.node": { status: "decrypted", source: "unexpected" } },
      source: "require('./addon.node');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
      expectedProviderEnabled: false,
      expected: { moduleProviderRequestCount: 0 },
      expectedProviderEvents: [],
    },
    {
      name: "missing unknown extension never reaches provider",
      files: {},
      responses: { "addon.coffee": { status: "decrypted", source: "unexpected" } },
      source: "require('./addon.coffee');",
      expectedSucceeded: false,
      expectedProviderEnabled: false,
      expected: { moduleProviderRequestCount: 0 },
      expectedProviderEvents: [],
    },
    {
      name: "decrypted package metadata and CommonJS main",
      files: {
        "node_modules/pkg/package.json": "AUTOJS6_ENCRYPTED_PACKAGE_JSON",
        "node_modules/pkg/index.cjs": "AUTOJS6_ENCRYPTED_PACKAGE_MAIN",
      },
      responses: {
        "node_modules/pkg/package.json": { status: "decrypted", source: "{\"main\":\"index.cjs\"}" },
        "node_modules/pkg/index.cjs": { status: "decrypted", source: "module.exports = 'package-main';" },
      },
      source: "globalThis.__providerTestValue = require('pkg');",
      expectedValue: "package-main",
      expected: {
        moduleProviderRequestCount: 3,
        moduleProviderDecryptedCount: 2,
        moduleProviderNotFoundCount: 1,
      },
      expectedProviderEvents: [
        { operation: "resolve", path: "package.json", status: "not_found" },
        { operation: "resolve", path: "node_modules/pkg/package.json", status: "decrypted" },
        { operation: "resolve", path: "node_modules/pkg/index.cjs", status: "decrypted" },
      ],
    },
    {
      name: "provider not found",
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.cjs": { status: "not_found" } },
      source: "require('./dep.cjs');",
      expectedSucceeded: false,
      expected: { moduleProviderNotFoundCount: 1, moduleProviderLastStatus: "not_found" },
    },
    {
      name: "provider denied",
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: {
        "dep.cjs": {
          status: "denied",
          errorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
          errorMessage: "test denial",
        },
      },
      source: "require('./dep.cjs');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      expected: {
        moduleProviderDeniedCount: 1,
        moduleProviderLastDenialReason: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
      },
    },
    {
      name: "provider cancelled",
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: {
        "dep.cjs": {
          status: "cancelled",
          errorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_CANCELLED",
          errorMessage: "test cancellation",
        },
      },
      source: "require('./dep.cjs');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_CANCELLED",
      expected: { moduleProviderCancelledCount: 1 },
    },
    {
      name: "provider failed",
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: {
        "dep.cjs": {
          status: "failed",
          errorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED",
          errorMessage: "test provider failure",
        },
      },
      source: "require('./dep.cjs');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED",
      expected: { moduleProviderFailedCount: 1 },
    },
    {
      name: "provider transport timeout",
      timeoutMs: 75,
      files: { "dep.cjs": "AUTOJS6_ENCRYPTED_PLACEHOLDER" },
      responses: { "dep.cjs": { status: "ignore" } },
      source: "require('./dep.cjs');",
      expectedSucceeded: false,
      expectedErrorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT",
      expected: { moduleProviderTimedOutCount: 1 },
    },
  ];

  const buildRoot = path.resolve(__dirname, "../../../build/module-source-provider-tests");
  fs.mkdirSync(buildRoot, { recursive: true });
  const root = fs.mkdtempSync(path.join(buildRoot, "run-"));
  const scenarioResults = [];
  try {
    for (const scenario of scenarios) {
      scenarioResults.push(runScenario(chunks, root, scenario));
      process.stdout.write(`PASS ${scenario.name}${os.EOL}`);
    }
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
  if (options.report) {
    fs.mkdirSync(path.dirname(options.report), { recursive: true });
    fs.writeFileSync(options.report, `${JSON.stringify({
      schema: "autojs6-node-module-source-provider-resolver-report-v2",
      status: "passed",
      stage: "X3h",
      evidenceLevel: "node-direct-static-and-process",
      nodeVersion: process.version,
      scenarioCount: scenarios.length,
      scenarios: scenarioResults,
      sourceMutationCount: sourceMutationResults.length,
      sourceMutations: sourceMutationResults,
      resolverContract: {
        boundedExactCandidatesOnly: true,
        directoryScanAllowed: false,
        commonJsExtensionOrder: [".js", ".cjs", ".cts", ".ts", ".json"],
        esmExtensionOrder: [".mts", ".ts", ".mjs", ".js", ".cts", ".cjs", ".json"],
        forbiddenRawProviderPaths: ["extensionless", ".tsx", ".node", "unknown-extension"],
        missingCandidateOperations: ["resolve_existing", "materialize_missing_plaintext"],
        onlyNotFoundAdvances: true,
        materializedMetadataVerifiedRead: true,
      },
      coverage: {
        commonJs: true,
        staticEsm: true,
        dynamicImport: true,
        plaintextTypeScriptExtensions: [".ts", ".mts", ".cts"],
        unsupportedTypeScriptExtensions: [".tsx"],
        declarationBypassExtensions: [".d.ts", ".d.mts", ".d.cts"],
        privatePreparationEnvelope: true,
        oneTimeParentAndInheritedDeadline: true,
        preparedRecordCacheReuse: true,
        negativePreparedPathAndByteTruth: true,
        extensionlessCommonJs: true,
        extensionlessEsm: true,
        packageJsonMainIndex: true,
        packageImportsExportsSelfBareSubpath: true,
        terminalProviderFailures: true,
        materializedMetadataReceiptRaceAndBudget: true,
      },
      hostPreloadQuarantined: false,
      hostOwnershipMigrated: false,
      runtimeKitFrozen: false,
      deviceOrSoakExecuted: false,
    }, null, 2)}\n`, "utf8");
    process.stdout.write(`PASS module-source provider report ${options.report}${os.EOL}`);
  }
  process.stdout.write(`PASS ${scenarios.length} module-source provider scenarios${os.EOL}`);
}

function runX3hResolverSourceMutationSelfTests() {
  const source = readCppSource();
  verifyX3hResolverSourceContract(source);
  const mutations = [
    {
      name: "path-only-provider-cache",
      functionName: "__autojs6_request_module_source",
      target: 'const cacheKey = requestOperation + "\\n" + String(readable || "");',
      replacement: 'const cacheKey = String(readable || "");',
    },
    {
      name: "missing-candidate-allows-extensionless-provider-path",
      functionName: "__autojs6_probe_missing_module_candidate",
      target: "!extension ||",
      replacement: "false ||",
    },
    {
      name: "missing-candidate-skips-resolve-existing",
      functionName: "__autojs6_probe_missing_module_candidate",
      target: 'const resolveResult = __autojs6_request_module_source(\n      exactCandidate,\n      "resolve_missing_candidate"\n    );',
      replacement: 'const resolveResult = Object.freeze({ status: "not_encrypted" });',
    },
    {
      name: "missing-plaintext-skips-materialization",
      functionName: "__autojs6_probe_missing_module_candidate",
      target: 'const materializedResult = __autojs6_request_module_source(\n      exactCandidate,\n      "materialize_missing_plaintext"\n    );',
      replacement: 'const materializedResult = Object.freeze({ status: "materialized_plaintext" });',
    },
    {
      name: "materialized-metadata-bypasses-verified-read",
      functionName: "__autojs6_module_metadata_record",
      target: 'providerResult.status === "not_encrypted" ||\n      providerResult.status === "materialized_plaintext"',
      replacement: 'providerResult.status === "not_encrypted"',
    },
    {
      name: "package-target-drops-missing-candidate-probe",
      functionName: "__autojs6_resolve_package_path_target",
      target: "const resolved = __autojs6_first_local_module_candidate(candidates, root, moduleName, allowEsm, true);",
      replacement: "const resolved = __autojs6_first_local_module_candidate(candidates, root, moduleName, allowEsm, false);",
    },
    {
      name: "package-index-drops-missing-candidate-probe",
      functionName: "__autojs6_resolve_package_directory",
      target: "__autojs6_package_condition_mode(mode) === \"esm\",\n      true",
      replacement: "__autojs6_package_condition_mode(mode) === \"esm\",\n      false",
    },
    {
      name: "local-module-drops-missing-candidate-probe",
      functionName: "__autojs6_resolve_local_module",
      target: "moduleName,\n      allowEsm,\n      true",
      replacement: "moduleName,\n      allowEsm,\n      false",
    },
    {
      name: "node-modules-subpath-drops-missing-candidate-probe",
      functionName: "__autojs6_resolve_node_modules_subpath",
      target: "moduleName,\n      allowEsm,\n      true",
      replacement: "moduleName,\n      allowEsm,\n      false",
    },
    {
      name: "esm-relative-drops-missing-candidate-probe",
      functionName: "__autojs6_resolve_esm_module",
      target: "parentFilename,\n      undefined,\n      true",
      replacement: "parentFilename,\n      undefined,\n      false",
    },
    {
      name: "commonjs-candidate-order-drift",
      functionName: "__autojs6_resolve_local_module",
      target: '[base, base + ".js", base + ".cjs", base + ".cts", base + ".ts", base + ".json"]',
      replacement: '[base, base + ".ts", base + ".js", base + ".cjs", base + ".cts", base + ".json"]',
    },
    {
      name: "esm-candidate-order-drift",
      functionName: "__autojs6_resolve_esm_module",
      target: '[base + ".mts", base + ".ts", base + ".mjs", base + ".js", base + ".cts", base + ".cjs", base + ".json"]',
      replacement: '[base + ".mjs", base + ".mts", base + ".ts", base + ".js", base + ".cts", base + ".cjs", base + ".json"]',
    },
    {
      name: "package-type-swallows-provider-failure",
      functionName: "__autojs6_package_type_module_for_file",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) throw error;",
      replacement: "void error;",
    },
    {
      name: "package-main-swallows-provider-failure",
      functionName: "__autojs6_resolve_package_directory",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n            throw error;\n          }",
      replacement: "void error;",
    },
    {
      name: "module-metadata-swallows-provider-enoent-spoof",
      functionName: "__autojs6_module_metadata_record",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n          throw error;\n        }\n        if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
      replacement: "if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
    },
    {
      name: "runtime-module-swallows-provider-enoent-spoof",
      functionName: "__autojs6_runtime_module_record",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n        throw error;\n      }\n      if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
      replacement: "if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
    },
    {
      name: "package-pattern-selection-reverts-to-declaration-order",
      functionName: "__autojs6_best_package_pattern_match",
      target: "if (bestKey === null || __autojs6_package_pattern_key_compare(patternKey, bestKey) < 0)",
      replacement: "if (bestKey === null)",
    },
    {
      name: "plaintext-revalidation-downgrades-disappearance-to-not-found",
      functionName: "__autojs6_revalidate_plaintext_module_path",
      target: "if (!current) {\n      throw __autojs6_module_source_provider_error(",
      replacement: "if (!current) return null;\n    if (false) {\n      throw __autojs6_module_source_provider_error(",
    },
    {
      name: "exports-allows-mixed-subpath-and-condition-keys",
      functionName: "__autojs6_package_exports_target",
      target: "const conditionalMainShape = __autojs6_validate_package_exports_keys(exportsValue, moduleName);",
      replacement: "const conditionalMainShape = __autojs6_package_condition_map(exportsValue);",
    },
    {
      name: "imports-allows-invalid-map-keys",
      functionName: "__autojs6_package_imports_target",
      target: "__autojs6_validate_package_imports_keys(importsValue, moduleName);",
      replacement: "void importsValue;",
    },
  ];
  const results = [];
  for (const mutation of mutations) {
    const mutated = mutateEmbeddedFunctionSource(source, mutation);
    let rejected = false;
    try {
      verifyX3hResolverSourceContract(mutated);
    } catch (error) {
      rejected = error instanceof assert.AssertionError;
      if (!rejected) throw error;
    }
    assert.equal(rejected, true, `X3h source mutation was not rejected: ${mutation.name}`);
    results.push({ name: mutation.name, status: "rejected" });
  }
  return results.concat(runX3hMetadataSourceMutationSelfTests());
}

function runX3hMetadataSourceMutationSelfTests() {
  const sources = {
    provider: fs.readFileSync(JAVA_PROVIDER_SOURCE, "utf8"),
    service: fs.readFileSync(JAVA_SERVICE_SOURCE, "utf8"),
    permission: fs.readFileSync(JAVA_PERMISSION_SOURCE, "utf8"),
  };
  verifyX3hMetadataSourceContract(sources);
  const mutations = [
    {
      name: "metadata-provider-first-bypass",
      file: "provider",
      target: "MetadataProviderResponse resolve = callMetadataProvider(runtimePath, false, deadline);",
      replacement: "MetadataProviderResponse resolve = null;",
    },
    {
      name: "metadata-materialize-missing-counter-dropped",
      file: "provider",
      target: "if (materializationRequest) {\n            missingCandidateRequestCount.incrementAndGet();\n        }",
      replacement: "if (materializationRequest) { /* missing counter dropped */ }",
    },
    {
      name: "metadata-plaintext-counter-dropped",
      file: "provider",
      target: "if (materialize) {\n                plaintextCount.incrementAndGet();",
      replacement: "if (materialize) {",
    },
    {
      name: "metadata-replay-double-counts-provider",
      file: "provider",
      target: "writeResponse(requestFile, response, sourceFile, false);\n        metadataPreflightCacheReplayCount.incrementAndGet();",
      replacement: "writeResponse(requestFile, response, sourceFile, true);\n        metadataPreflightCacheReplayCount.incrementAndGet();",
    },
    {
      name: "metadata-decrypted-private-source-not-cleaned",
      file: "provider",
      target: "} finally {\n            privateSource.delete();\n        }",
      replacement: "} finally {\n            // private source retained\n        }",
    },
    {
      name: "metadata-materialized-replay-status-drift",
      file: "provider",
      target: "return STATUS_NOT_ENCRYPTED;",
      replacement: "return STATUS_MATERIALIZED_PLAINTEXT;",
    },
    {
      name: "metadata-exact-path-dedup-dropped",
      file: "provider",
      target: "java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();",
      replacement: "java.util.List<String> paths = new java.util.ArrayList<>();",
    },
    {
      name: "metadata-session-created-after-snapshot",
      file: "service",
      target: "if (moduleSourceProvider != null) {\n                moduleSourceProviderSession = new PluginModuleSourceProviderFileTransportSession(",
      replacement: "if (false && moduleSourceProvider != null) {\n                moduleSourceProviderSession = new PluginModuleSourceProviderFileTransportSession(",
    },
    {
      name: "metadata-predispatch-failure-commits-workspace",
      file: "service",
      target: "if (shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)) {\n                commitWorkspaceQuietly(workspaceSession);\n            }\n            String failureErrorCode",
      replacement: "if (true) {\n                commitWorkspaceQuietly(workspaceSession);\n            }\n            String failureErrorCode",
    },
    {
      name: "permission-manifest-reopens-working-directory",
      file: "permission",
      target: "public String runtimeModuleSourceForMetadata(",
      replacement: "public String runtimeModuleSourceForWorkingDirectory(",
    },
  ];
  const results = [];
  for (const mutation of mutations) {
    const mutated = { ...sources };
    mutated[mutation.file] = replaceUnique(mutated[mutation.file], mutation.target, mutation.replacement,
      mutation.name);
    let rejected = false;
    try {
      verifyX3hMetadataSourceContract(mutated);
    } catch (error) {
      rejected = error instanceof assert.AssertionError;
      if (!rejected) throw error;
    }
    assert.equal(rejected, true, `X3h metadata source mutation was not rejected: ${mutation.name}`);
    results.push({ name: mutation.name, status: "rejected" });
  }
  return results;
}

function verifyX3hMetadataSourceContract(sources) {
  const provider = sources.provider;
  const service = sources.service;
  const permission = sources.permission;
  const providerFirst = "MetadataProviderResponse resolve = callMetadataProvider(runtimePath, false, deadline);";
  const localRead = "workspaceSession.readExactRuntimeMetadataNoFollow(";
  assert.ok(provider.includes(providerFirst), "X3h metadata preflight lost provider-first resolve_existing");
  assert.ok(
    provider.indexOf(providerFirst) < provider.indexOf(localRead, provider.indexOf(providerFirst)),
    "X3h metadata preflight reads workspace before provider resolve_existing",
  );
  assert.ok(service.includes("boolean nativeDispatchStarted = false;"),
    "X3h service lost explicit native-dispatch state");
  assert.ok(service.includes("nativeDispatchStarted = true;\n            String[] nativePayload = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript("),
    "X3h native-dispatch marker no longer precedes the native call");
  assert.equal((service.match(/if \(shouldCommitWorkspaceAfterFailure\(nativeDispatchStarted\)\) \{\n\s+commitWorkspaceQuietly\(workspaceSession\);\n\s+\}/g) || []).length, 2,
    "X3h predispatch failure/finally paths no longer suppress workspace commit");
  assert.ok(service.includes("static boolean shouldCommitWorkspaceAfterFailure(boolean nativeDispatchStarted) {\n        return nativeDispatchStarted;\n    }"),
    "X3h predispatch workspace commit policy drifted");
  for (const marker of [
    "embedded_script.runtime_plugin.native_dispatch_started=",
    "embedded_script.runtime_plugin.workspace.commit_allowed=",
    "embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false",
  ]) {
    assert.ok(service.includes(marker), `X3h predispatch failure receipt drifted: ${marker}`);
  }
  for (const target of [
    "if (materializationRequest) {\n            missingCandidateRequestCount.incrementAndGet();\n        }",
    "if (materialize) {\n                plaintextCount.incrementAndGet();",
    "writeResponse(requestFile, response, sourceFile, false);\n        metadataPreflightCacheReplayCount.incrementAndGet();",
    "} finally {\n            privateSource.delete();\n        }",
    "if (STATUS_MATERIALIZED_PLAINTEXT.equals(preflightStatus) ||",
    "return STATUS_NOT_ENCRYPTED;",
    "java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();",
    'paths.add(metadataPath(workingDirectory, "project.json"));',
    'paths.add(metadataPath(workingDirectory, "package.json"));',
    'paths.add(metadataPath(sandboxRoot, "project.json"));',
    'paths.add(metadataPath(sandboxRoot, "package.json"));',
    "metadataProviderStatusAllowsDefault(resolve.status)",
    "throw terminalMetadataResponse(runtimePath, resolve);",
  ]) {
    assert.ok(provider.includes(target), `X3h metadata transport contract drifted: ${target}`);
  }
  const createSession = "moduleSourceProviderSession = new PluginModuleSourceProviderFileTransportSession(";
  const guardedCreateSession = "if (moduleSourceProvider != null) {\n                " + createSession;
  const readSnapshot = "moduleSourceProviderSession.readPolicyMetadataSnapshot(";
  const injectRuntimeModules = "RuntimeModuleInjection runtimeModuleInjection = withPluginRuntimeModules(";
  assert.ok(service.includes(createSession), "X3h metadata transport lost its request-scoped session");
  assert.ok(service.includes(guardedCreateSession), "X3h metadata transport no longer creates the shared session when provider is present");
  assert.ok(service.includes(readSnapshot), "X3h metadata transport lost its snapshot read");
  assert.ok(
    service.indexOf(createSession) < service.indexOf(readSnapshot) &&
      service.indexOf(readSnapshot) < service.indexOf(injectRuntimeModules),
    "X3h metadata session/snapshot/runtime-module order drifted",
  );
  for (const target of [
    "NodeBridgePermissionManifest.INSTANCE.runtimeModuleSourceForMetadata(",
    "BridgeLimitPolicy.fromMetadata(",
    '"exact_metadata_snapshot"',
  ]) {
    assert.ok(service.includes(target), `X3h runtime policy snapshot consumer drifted: ${target}`);
  }
  assert.ok(permission.includes("public String runtimeModuleSourceForMetadata("),
    "X3h permission manifest no longer consumes metadata text");
  assert.equal(permission.includes("runtimeModuleSourceForWorkingDirectory"), false,
    "X3h permission manifest reintroduced working-directory reads");
  assert.equal(permission.includes("FileInputStream"), false,
    "X3h permission manifest reintroduced direct file reads");
}

function replaceUnique(source, target, replacement, label) {
  const first = source.indexOf(target);
  assert.ok(first >= 0, `X3h mutation target is missing: ${label}`);
  assert.equal(source.indexOf(target, first + target.length), -1, `X3h mutation target is ambiguous: ${label}`);
  return source.slice(0, first) + replacement + source.slice(first + target.length);
}

function verifyX3hResolverSourceContract(source) {
  const clauses = [
    {
      functionName: "__autojs6_request_module_source",
      target: 'const cacheKey = requestOperation + "\\n" + String(readable || "");',
    },
    {
      functionName: "__autojs6_probe_missing_module_candidate",
      target: "!extension ||",
    },
    {
      functionName: "__autojs6_probe_missing_module_candidate",
      target: 'const resolveResult = __autojs6_request_module_source(\n      exactCandidate,\n      "resolve_missing_candidate"\n    );',
    },
    {
      functionName: "__autojs6_probe_missing_module_candidate",
      target: 'const materializedResult = __autojs6_request_module_source(\n      exactCandidate,\n      "materialize_missing_plaintext"\n    );',
    },
    {
      functionName: "__autojs6_module_metadata_record",
      target: 'providerResult.status === "not_encrypted" ||\n      providerResult.status === "materialized_plaintext"',
    },
    {
      functionName: "__autojs6_resolve_package_path_target",
      target: "const resolved = __autojs6_first_local_module_candidate(candidates, root, moduleName, allowEsm, true);",
    },
    {
      functionName: "__autojs6_resolve_package_directory",
      target: "__autojs6_package_condition_mode(mode) === \"esm\",\n      true",
    },
    {
      functionName: "__autojs6_resolve_local_module",
      target: "moduleName,\n      allowEsm,\n      true",
    },
    {
      functionName: "__autojs6_resolve_node_modules_subpath",
      target: "moduleName,\n      allowEsm,\n      true",
    },
    {
      functionName: "__autojs6_resolve_esm_module",
      target: "parentFilename,\n      undefined,\n      true",
    },
    {
      functionName: "__autojs6_resolve_local_module",
      target: '[base, base + ".js", base + ".cjs", base + ".cts", base + ".ts", base + ".json"]',
    },
    {
      functionName: "__autojs6_resolve_esm_module",
      target: '[base + ".mts", base + ".ts", base + ".mjs", base + ".js", base + ".cts", base + ".cjs", base + ".json"]',
    },
    {
      functionName: "__autojs6_package_type_module_for_file",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) throw error;",
    },
    {
      functionName: "__autojs6_resolve_package_directory",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n            throw error;\n          }",
    },
    {
      functionName: "__autojs6_module_metadata_record",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n          throw error;\n        }\n        if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
    },
    {
      functionName: "__autojs6_runtime_module_record",
      target: "if (error && error.__autojs6ModuleSourceProviderRecorded) {\n        throw error;\n      }\n      if (error && (error.code === \"ENOENT\" || error.code === \"ENOTDIR\"))",
    },
    {
      functionName: "__autojs6_best_package_pattern_match",
      target: "if (bestKey === null || __autojs6_package_pattern_key_compare(patternKey, bestKey) < 0)",
    },
    {
      functionName: "__autojs6_package_exports_target",
      target: "const patternMatch = __autojs6_best_package_pattern_match(exportsValue, key, moduleName, \"exports\");",
    },
    {
      functionName: "__autojs6_package_imports_target",
      target: "const patternMatch = __autojs6_best_package_pattern_match(importsValue, specifier, moduleName, \"imports\");",
    },
    {
      functionName: "__autojs6_revalidate_plaintext_module_path",
      target: "if (!current) {\n      throw __autojs6_module_source_provider_error(",
    },
    {
      functionName: "__autojs6_package_exports_target",
      target: "const conditionalMainShape = __autojs6_validate_package_exports_keys(exportsValue, moduleName);",
    },
    {
      functionName: "__autojs6_package_imports_target",
      target: "__autojs6_validate_package_imports_keys(importsValue, moduleName);",
    },
  ];
  for (const clause of clauses) {
    const segment = embeddedFunctionSegment(source, clause.functionName);
    assert.ok(
      segment.includes(clause.target),
      `X3h resolver contract drifted in ${clause.functionName}: ${clause.target}`,
    );
  }
  for (const functionName of [
    "__autojs6_probe_missing_module_candidate",
    "__autojs6_resolve_package_path_target",
    "__autojs6_resolve_package_directory",
    "__autojs6_resolve_local_module",
    "__autojs6_resolve_node_modules_subpath",
    "__autojs6_resolve_esm_module",
  ]) {
    const segment = embeddedFunctionSegment(source, functionName);
    assert.equal(/readdir|listFiles|scandir|globSync|opendir/.test(segment), false,
      `X3h resolver introduced a directory scan in ${functionName}`);
  }
}

function embeddedFunctionSegment(source, name) {
  const start = source.indexOf(`  function ${name}(`);
  assert.ok(start >= 0, `could not locate embedded function ${name}`);
  const next = source.indexOf("\n  function ", start + 3);
  assert.ok(next > start, `could not locate embedded function boundary ${name}`);
  return source.slice(start, next);
}

function mutateEmbeddedFunctionSource(source, mutation) {
  const segment = embeddedFunctionSegment(source, mutation.functionName);
  const first = segment.indexOf(mutation.target);
  assert.ok(first >= 0, `X3h mutation target is missing: ${mutation.name}`);
  assert.equal(
    segment.indexOf(mutation.target, first + mutation.target.length),
    -1,
    `X3h mutation target is ambiguous: ${mutation.name}`,
  );
  const mutatedSegment = segment.slice(0, first) + mutation.replacement +
    segment.slice(first + mutation.target.length);
  return source.replace(segment, mutatedSegment);
}

function runScenario(chunks, parentRoot, scenario) {
  const safeScenarioName = scenario.name.replace(/[^A-Za-z0-9_.-]/g, "_");
  const root = path.join(parentRoot, safeScenarioName);
  const requestDir = path.join(root, "transport", "requests");
  const responseDir = path.join(root, "transport", "responses");
  const resultPath = path.join(root, "result.json");
  const eventsPath = path.join(root, "transport-events.jsonl");
  const virtualClockPath = scenario.virtualClockStartMs === undefined
    ? ""
    : path.join(root, "virtual-clock.txt");
  const executionId = `test-${safeScenarioName}`;
  fs.mkdirSync(requestDir, { recursive: true });
  fs.mkdirSync(responseDir, { recursive: true });
  if (virtualClockPath) {
    assert.ok(
      Number.isSafeInteger(scenario.virtualClockStartMs),
      `${scenario.name}: virtual clock start must be a safe integer`,
    );
  }
  for (const [relativePath, contents] of Object.entries(scenario.files || {})) {
    const file = path.join(root, relativePath);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, contents, "utf8");
  }
  const entryName = scenario.entryName || "entry.cjs";
  const entryPath = path.join(root, entryName);
  fs.writeFileSync(entryPath, "AUTOJS6_TEST_ENTRY_PLACEHOLDER", "utf8");
  const timeoutMs = scenario.timeoutMs || 1000;
  const contractVersion = scenario.contractVersion || CONTRACT_VERSION;
  const config = {
    enabled: true,
    version: contractVersion,
    executionId,
    requestDir,
    responseDir,
    timeoutMs,
    pollIntervalMs: 2,
  };
  const responseActions = {};
  for (const [relativePath, action] of Object.entries(scenario.responses || {})) {
    responseActions[path.resolve(root, relativePath)] = action;
  }
  const preparationActions = {};
  for (const [relativePath, action] of Object.entries(scenario.preparations || {})) {
    const sourceName = path.resolve(root, relativePath);
    const response = (scenario.responses || {})[relativePath] || {};
    const materializationResponse = response.materialize || response;
    preparationActions[sourceName] = {
      ...action,
      expectedRawSource: String(
        (scenario.files || {})[relativePath] ||
        materializationResponse.source ||
        "",
      ),
    };
  }
  const workerSpecPath = path.join(root, "worker.json");
  fs.writeFileSync(workerSpecPath, JSON.stringify({
    requestDir,
    responseDir,
    executionId,
    contractVersion,
    eventsPath,
    virtualClockPath,
    virtualClockStartMs: scenario.virtualClockStartMs,
    actions: responseActions,
    preparations: preparationActions,
  }), "utf8");
  const worker = childProcess.spawn(process.execPath, [__filename, "--worker", workerSpecPath], {
    stdio: ["ignore", "ignore", "inherit"],
  });
  sleepSync(100);
  try {
    const embedded = buildEmbeddedScript(chunks, {
      source: scenario.source,
      sourceName: entryPath,
      workingDirectory: root,
      sandboxRoot: root,
      runtimeModuleSources: {
        "autojs6:module-source-provider": JSON.stringify(config),
      },
      esmExperimentalEnabled: true,
      dynamicImportExperimentalEnabled: true,
    });
    const runner = virtualClockTestHookPrefix(virtualClockPath, scenario.virtualClockStartMs) +
      testHookPrefix() + materializedRaceTestHook(root, scenario.raceReplaceOnOpen) +
      embedded + testHookSuffix(resultPath);
    const runnerPath = path.join(root, "runner.cjs");
    fs.writeFileSync(runnerPath, runner, "utf8");
    const run = childProcess.spawnSync(process.execPath, [runnerPath], {
      cwd: root,
      encoding: "utf8",
      // This watchdog detects a wedged child process; it is not the provider
      // contract deadline under test. Keep it independent and generous enough
      // that Gradle/CI host scheduling cannot pre-empt the inner timeout oracle.
      timeout: Math.max(30000, timeoutMs * 4),
    });
    assert.equal(run.error, undefined, `${scenario.name}: runtime process error: ${run.error && run.error.message}`);
    assert.equal(run.status, 0, `${scenario.name}: runtime exited ${run.status}: ${run.stderr}`);
    assert.ok(fs.existsSync(resultPath), `${scenario.name}: runtime test hook did not publish a result`);
    const hookResult = JSON.parse(fs.readFileSync(resultPath, "utf8"));
    assert.equal(typeof hookResult.probe, "string", `${scenario.name}: probe result is missing`);
    const envelope = JSON.parse(hookResult.probe);
    assert.equal(
      envelope.succeeded,
      scenario.expectedSucceeded !== false,
      `${scenario.name}: unexpected result: ${envelope.errorMessage || hookResult.probe}`,
    );
    if (Object.hasOwn(scenario, "expectedValue")) {
      assert.deepEqual(hookResult.value, scenario.expectedValue, `${scenario.name}: wrong module value`);
    }
    if (scenario.expectedErrorCode) {
      assert.equal(envelope.errorCode, scenario.expectedErrorCode, `${scenario.name}: wrong error identity`);
    }
    for (const [field, expected] of Object.entries(scenario.expected || {})) {
      assert.deepEqual(envelope[field], expected, `${scenario.name}: wrong diagnostic ${field}`);
    }
    const events = readWorkerEvents(eventsPath);
    const providerEvents = events.filter((event) =>
      event.operation === "resolve" || event.operation === "materialize_missing_plaintext"
    );
    const actualProviderEvents = providerEvents.map((event) => ({
      operation: event.operation,
      path: relativeEventPath(root, event.path),
      status: event.status,
    }));
    for (const event of actualProviderEvents) {
      const extension = path.extname(event.path).toLowerCase();
      assert.ok(extension, `${scenario.name}: provider received an extensionless raw path: ${event.path}`);
      assert.ok(
        [".js", ".cjs", ".cts", ".ts", ".json", ".mjs", ".mts"].includes(extension),
        `${scenario.name}: provider received an unsupported raw path: ${event.path}`,
      );
    }
    if (scenario.expectedProviderEvents) {
      assert.deepEqual(
        actualProviderEvents,
        scenario.expectedProviderEvents,
        `${scenario.name}: provider operation/path order drifted`,
      );
    }
    const preparationEvents = events.filter((event) => event.operation === "prepare_plaintext_typescript");
    if (Object.hasOwn(scenario, "expectedPreparationCount")) {
      assert.equal(
        preparationEvents.length,
        scenario.expectedPreparationCount,
        `${scenario.name}: wrong private preparation request count`,
      );
    }
    for (const event of preparationEvents) {
      assert.equal(event.envelopeValid, true, `${scenario.name}: invalid private preparation envelope: ${event.error || ""}`);
      assert.equal(event.parentUseCount, 1, `${scenario.name}: parent authorization was not one-time`);
      assert.equal(event.rawBytesExact, true, `${scenario.name}: private raw source bytes changed`);
      assert.equal(event.privatePathExact, true, `${scenario.name}: private raw source path drifted`);
      assert.equal(event.deadlineBounded, true, `${scenario.name}: private preparation deadline escaped parent budget`);
    }
    if (scenario.expectedMinimumPreparationDeadlineDeductionMs !== undefined) {
      assert.equal(preparationEvents.length, 1, `${scenario.name}: expected one deadline-bearing preparation`);
      assert.equal(
        preparationEvents[0].minimumDeadlineDeductionMs,
        scenario.expectedMinimumPreparationDeadlineDeductionMs,
        `${scenario.name}: wrong required parent-deadline deduction`,
      );
      assert.ok(
        preparationEvents[0].actualDeadlineDeductionMs >=
          scenario.expectedMinimumPreparationDeadlineDeductionMs,
        `${scenario.name}: private preparation did not deduct the known provider delay from its parent deadline`,
      );
    }
    if (scenario.expectedPreparationParentTimeoutMs !== undefined) {
      assert.equal(preparationEvents.length, 1, `${scenario.name}: expected one parent-deadline preparation`);
      assert.equal(
        preparationEvents[0].parentTimeoutMs,
        scenario.expectedPreparationParentTimeoutMs,
        `${scenario.name}: wrong effective parent timeout`,
      );
    }
    assert.equal(
      envelope.moduleProviderEnabled,
      scenario.expectedProviderEnabled !== false,
      `${scenario.name}: unexpected provider-enabled diagnostic`,
    );
    assert.equal(envelope.moduleProviderLegacyDirectoryScanRan, false, `${scenario.name}: legacy scan flag changed`);
    assert.equal(Object.hasOwn(envelope, "moduleProviderLastPath"), false, `${scenario.name}: path diagnostic leaked`);
    assert.equal(
      Object.hasOwn(envelope, "moduleProviderLastResolvedPath"),
      false,
      `${scenario.name}: resolved-path diagnostic leaked`,
    );
    assert.equal(
      Object.hasOwn(envelope, "moduleProviderLastErrorMessage"),
      false,
      `${scenario.name}: error-message diagnostic leaked`,
    );
    return {
      name: scenario.name,
      status: "passed",
      providerEvents: actualProviderEvents,
    };
  } finally {
    worker.kill();
  }
}

function embeddedScriptChunks() {
  const source = readCppSource();
  const start = source.indexOf("std::string buildEmbeddedScriptExecutionSource(");
  const end = source.indexOf("    return script;", start);
  assert.ok(start >= 0 && end > start, "could not locate embedded script builder");
  return [...source.slice(start, end).matchAll(/script \+= R"JS\(([\s\S]*?)\)JS";/g)]
    .map((match) => match[1]);
}

function assertAndroidCredentialDataAliasContract() {
  const source = readCppSource();
  const partsSource = embeddedFunctionSource(
    source,
    "__autojs6_android_credential_data_path_parts",
  );
  const comparisonSource = embeddedFunctionSource(
    source,
    "__autojs6_same_authorized_canonical_path",
  );
  const createComparison = new Function(
    "process",
    `${partsSource}\n${comparisonSource}\nreturn __autojs6_same_authorized_canonical_path;`,
  );
  const androidComparison = createComparison({ platform: "android" });
  const linuxComparison = createComparison({ platform: "linux" });
  const posixPath = path.posix;
  const packageName = "io.github.supermonster003.autojs6.plugin.nodejs";
  const userPath = `/data/user/0/${packageName}/files/state.cjs`;
  const legacyPath = `/data/data/${packageName}/files/state.cjs`;

  assert.equal(androidComparison(posixPath, userPath, legacyPath), true, "user/0 to data alias was rejected");
  assert.equal(androidComparison(posixPath, legacyPath, userPath), true, "data to user/0 alias was rejected");
  assert.equal(
    androidComparison(posixPath, `/data/user/0/${packageName}`, `/data/data/${packageName}`),
    true,
    "package-root alias was rejected",
  );
  assert.equal(androidComparison(posixPath, userPath, userPath), true, "exact canonical path was rejected");
  assert.equal(
    androidComparison(posixPath, userPath, "/data/data/io.github.supermonster003.autojs6/files/state.cjs"),
    false,
    "cross-package alias was accepted",
  );
  assert.equal(
    androidComparison(posixPath, userPath, `/data/data/${packageName}/files/other.cjs`),
    false,
    "different credential-data tail was accepted",
  );
  assert.equal(
    androidComparison(posixPath, `/data/user/10/${packageName}/files/state.cjs`, legacyPath),
    false,
    "non-owner Android user alias was accepted",
  );
  assert.equal(
    androidComparison(posixPath, `/data/user_de/0/${packageName}/files/state.cjs`, legacyPath),
    false,
    "device-encrypted data alias was accepted",
  );
  assert.equal(
    androidComparison(posixPath, `/data/user/0foo/${packageName}/files/state.cjs`, legacyPath),
    false,
    "credential-data prefix spoof was accepted",
  );
  assert.equal(
    androidComparison(posixPath, userPath, `/data/data/${packageName}.attacker/files/state.cjs`),
    false,
    "application-id prefix collision was accepted",
  );
  assert.equal(
    androidComparison(posixPath, "/data/user/0/not-a-package/files/state.cjs", "/data/data/not-a-package/files/state.cjs"),
    false,
    "invalid Android application id was accepted",
  );
  assert.equal(linuxComparison(posixPath, userPath, legacyPath), false, "non-Android alias was accepted");
  assert.equal(linuxComparison(posixPath, userPath, userPath), true, "non-Android exact path was rejected");
}

function embeddedFunctionSource(source, name) {
  const start = source.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `could not locate embedded function ${name}`);
  const bodyStart = source.indexOf("{", start);
  assert.ok(bodyStart >= 0, `could not locate embedded function body ${name}`);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] !== "}") continue;
    depth -= 1;
    if (depth === 0) return source.slice(start, index + 1);
  }
  assert.fail(`could not locate embedded function end ${name}`);
}

function buildEmbeddedScript(chunks, options) {
  const moduleSources = options.moduleSources || {};
  const runtimeModuleSources = options.runtimeModuleSources || {};
  const inserts = [
    escapedStringContents(options.source || ""),
    escapedStringContents(options.sourceName || "<test-entry.cjs>"),
    escapedStringContents(options.workingDirectory || ""),
    escapedStringContents(options.sandboxRoot || options.workingDirectory || ""),
    embeddedSourcesLiteral(moduleSources),
    embeddedSourcesLiteral(runtimeModuleSources),
    Object.keys(moduleSources).length || Object.keys(runtimeModuleSources).length ? "true" : "false",
    "{}",
    options.esmExperimentalEnabled === false ? "false" : "true",
    options.dynamicImportExperimentalEnabled === true ? "true" : "false",
    "false",
    "false",
    "false",
    "false",
  ];
  assert.equal(chunks.length, inserts.length + 1, "embedded script interpolation count changed");
  let script = chunks[0];
  for (let index = 0; index < inserts.length; index += 1) {
    script += inserts[index] + chunks[index + 1];
  }
  new Function(script);
  return script;
}

function embeddedSourcesLiteral(sources) {
  const entries = Object.entries(sources).map(([name, source]) =>
    `${JSON.stringify(name)}:{source:${JSON.stringify(String(source))},sourceURL:${JSON.stringify(name)}}`,
  );
  return `{${entries.join(",")}}`;
}

function escapedStringContents(value) {
  return JSON.stringify(String(value)).slice(1, -1);
}

function virtualClockTestHookPrefix(clockPath, clockStartMs) {
  if (!clockPath) return "";
  return [
    "const __autojs6ProviderTestClockFs = process.getBuiltinModule('fs');",
    `const __autojs6ProviderTestClockPath = ${JSON.stringify(clockPath)};`,
    `let __autojs6ProviderTestNow = ${JSON.stringify(clockStartMs)};`,
    "if (!Number.isSafeInteger(__autojs6ProviderTestNow)) throw new Error('Invalid provider fixture clock.');",
    "Date.now = function __autojs6ProviderTestDateNow() {",
    "  try {",
    "    const candidate = Number(__autojs6ProviderTestClockFs.readFileSync(__autojs6ProviderTestClockPath, 'utf8'));",
    "    if (Number.isSafeInteger(candidate) && candidate >= __autojs6ProviderTestNow) {",
    "      __autojs6ProviderTestNow = candidate;",
    "    }",
    "  } catch (_) {}",
    "  return __autojs6ProviderTestNow;",
    "};",
    "",
  ].join("\n");
}

function testHookPrefix() {
  return [
    "const __autojs6ProviderTestFs = process.getBuiltinModule('fs');",
    "const __autojs6ProviderTestPath = process.getBuiltinModule('path');",
    "const __autojs6ProviderTestSetTimeout = globalThis.setTimeout;",
    "",
  ].join("\n");
}

function materializedRaceTestHook(root, race) {
  if (!race) return "";
  const target = path.resolve(root, String(race.path || ""));
  const replacement = String(race.source || "");
  return [
    `const __autojs6ProviderRaceTarget = ${JSON.stringify(target)};`,
    `const __autojs6ProviderRaceSource = ${JSON.stringify(replacement)};`,
    "const __autojs6ProviderOriginalOpenSync = __autojs6ProviderTestFs.openSync;",
    "const __autojs6ProviderOriginalWriteFileSync = __autojs6ProviderTestFs.writeFileSync;",
    "const __autojs6ProviderOriginalCloseSync = __autojs6ProviderTestFs.closeSync;",
    "const __autojs6ProviderOriginalRenameSync = __autojs6ProviderTestFs.renameSync;",
    "let __autojs6ProviderRaceFired = false;",
    "__autojs6ProviderTestFs.openSync = function __autojs6ProviderRaceOpenSync(file, flags, ...args) {",
    "  if (!__autojs6ProviderRaceFired && typeof flags === 'number' &&",
    "      __autojs6ProviderTestPath.resolve(String(file || '')) === __autojs6ProviderRaceTarget) {",
    "    __autojs6ProviderRaceFired = true;",
    "    const temporary = __autojs6ProviderRaceTarget + '.race';",
    "    const replacementFd = __autojs6ProviderOriginalOpenSync.call(this, temporary, 'wx', 0o600);",
    "    try {",
    "      __autojs6ProviderOriginalWriteFileSync.call(this, replacementFd, __autojs6ProviderRaceSource, 'utf8');",
    "    } finally {",
    "      __autojs6ProviderOriginalCloseSync.call(this, replacementFd);",
    "    }",
    "    __autojs6ProviderOriginalRenameSync.call(this, temporary, __autojs6ProviderRaceTarget);",
    "  }",
    "  return __autojs6ProviderOriginalOpenSync.call(this, file, flags, ...args);",
    "};",
    "",
  ].join("\n");
}

function testHookSuffix(resultPath) {
  return `
;(function __autojs6ProviderTestPublish(attempt) {
  if (typeof globalThis.__autojs6_probe_result === "string" || attempt >= 400) {
    __autojs6ProviderTestFs.writeFileSync(
      ${JSON.stringify(resultPath)},
      JSON.stringify({ probe: globalThis.__autojs6_probe_result || "", value: globalThis.__providerTestValue }),
      "utf8"
    );
    return;
  }
  __autojs6ProviderTestSetTimeout(function () { __autojs6ProviderTestPublish(attempt + 1); }, 5);
})(0);
`;
}

function readWorkerEvents(eventsPath) {
  if (!fs.existsSync(eventsPath)) return [];
  return fs.readFileSync(eventsPath, "utf8")
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

function relativeEventPath(root, value) {
  return path.relative(root, path.resolve(String(value || ""))).split(path.sep).join("/");
}

function runTransportWorker(specPath) {
  const spec = JSON.parse(fs.readFileSync(specPath, "utf8"));
  const seen = new Set();
  const pendingParents = new Map();
  const parentUseCounts = new Map();
  const timer = setInterval(() => {
    let files = [];
    try {
      files = fs.readdirSync(spec.requestDir).filter((name) => name.endsWith(".json"));
    } catch (_) {
      return;
    }
    for (const name of files.sort()) {
      if (seen.has(name)) continue;
      seen.add(name);
      const requestFile = path.join(spec.requestDir, name);
      let request;
      try {
        request = JSON.parse(fs.readFileSync(requestFile, "utf8"));
      } catch (_) {
        continue;
      }
      if (request.operation === "prepare_plaintext_typescript") {
        handlePreparationRequest(spec, requestFile, request, pendingParents, parentUseCounts);
        continue;
      }
      handleResolveRequest(spec, requestFile, request, pendingParents);
    }
  }, 2);
  timer.unref = timer.unref || function () {};
  process.on("SIGTERM", () => process.exit(0));
  process.on("SIGINT", () => process.exit(0));
}

function handleResolveRequest(spec, requestFile, request, pendingParents) {
  const requestedPath = path.resolve(String(request.path || ""));
  const materializationRequest = request.operation === "materialize_missing_plaintext";
  const configuredAction = spec.actions[requestedPath];
  const action = configuredAction && (configuredAction.resolve || configuredAction.materialize)
    ? configuredAction[materializationRequest ? "materialize" : "resolve"] || { status: "not_found" }
    : configuredAction || { status: "not_found" };
  if (action.delayMs) sleepSync(Number(action.delayMs));
  if (action.advanceVirtualClockMs) {
    advanceVirtualClock(
      spec.virtualClockPath,
      Number(spec.virtualClockStartMs),
      Number(action.advanceVirtualClockMs),
    );
  }
  if (action.status === "ignore") {
    appendWorkerEvent(spec.eventsPath, {
      operation: materializationRequest ? "materialize_missing_plaintext" : "resolve",
      id: String(request.id || ""),
      path: requestedPath,
      status: "no_response",
      timeoutMs: Number(request.timeoutMs),
    });
    return;
  }
  const safeId = safeName(request.id);
  let sourcePath = "";
  let sourceBytes = 0;
  let responseStatus = action.status;
  if (action.status === "decrypted") {
    const bytes = Buffer.from(String(action.source || ""), "utf8");
    sourceBytes = bytes.length;
    sourcePath = path.join(spec.responseDir, `${safeId}.source`);
    publishFileAtomically(sourcePath, bytes);
  } else if (action.status === "plaintext") {
    assert.equal(materializationRequest, true, "plaintext response requires missing materialization operation");
    const bytes = Number.isSafeInteger(action.repeatedByteCount)
      ? Buffer.alloc(Number(action.repeatedByteCount), Number(action.repeatedByte || 0))
      : Buffer.from(String(action.source || ""), "utf8");
    fs.mkdirSync(path.dirname(requestedPath), { recursive: true });
    const descriptor = fs.openSync(requestedPath, "wx", 0o600);
    try {
      fs.writeFileSync(descriptor, bytes);
      fs.fsyncSync(descriptor);
    } finally {
      fs.closeSync(descriptor);
    }
    sourceBytes = bytes.length + Number(action.sourceBytesDelta || 0);
    responseStatus = "materialized_plaintext";
  }
  const resolvedPath = action.resolvedPath === undefined
    ? requestedPath
    : path.resolve(String(action.resolvedPath));
  if (
    responseStatus === "materialized_plaintext" ||
    (action.status === "not_encrypted" && fs.existsSync(requestedPath))
  ) {
    const timeoutMs = Number(request.timeoutMs);
    pendingParents.set(String(request.id), {
      sourceName: resolvedPath,
      timeoutMs,
      minimumDeadlineDeductionMs: Number(action.minimumDeadlineDeductionMs || 0),
    });
  }
  if (action.removeCandidateBeforeResponse) {
    fs.unlinkSync(requestedPath);
  } else if (action.replaceCandidateWithDirectoryBeforeResponse) {
    fs.unlinkSync(requestedPath);
    fs.mkdirSync(requestedPath);
  }
  appendWorkerEvent(spec.eventsPath, {
    operation: materializationRequest ? "materialize_missing_plaintext" : "resolve",
    id: String(request.id || ""),
    path: requestedPath,
    status: responseStatus,
    timeoutMs: Number(request.timeoutMs),
  });
  publishResponse(spec.responseDir, safeId, {
    version: spec.contractVersion || CONTRACT_VERSION,
    id: String(request.id),
    status: responseStatus,
    resolvedPath,
    sourcePath,
    sourceBytes,
    elapsedMs: 1,
    errorCode: action.errorCode || "",
    errorMessage: action.errorMessage || "",
  });
  removeFileQuietly(requestFile);
}

function handlePreparationRequest(spec, requestFile, request, pendingParents, parentUseCounts) {
  const id = String(request.id || "");
  const safeId = safeName(id);
  const parentRequestId = String(request.parentRequestId || "");
  const parent = pendingParents.get(parentRequestId);
  pendingParents.delete(parentRequestId);
  const parentUseCount = (parentUseCounts.get(parentRequestId) || 0) + 1;
  parentUseCounts.set(parentRequestId, parentUseCount);
  const sourceName = String(request.sourceName || "");
  const expectedPrivatePath = path.join(spec.requestDir, `${safeId}.source`);
  const privatePathExact = String(request.sourcePath || "") === expectedPrivatePath;
  let rawSource = Buffer.alloc(0);
  let regularPrivateFile = false;
  try {
    const stat = fs.lstatSync(expectedPrivatePath);
    regularPrivateFile = stat.isFile() && !stat.isSymbolicLink();
    rawSource = fs.readFileSync(expectedPrivatePath);
  } catch (_) {}
  const declaredBytes = Number(request.sourceBytes);
  const action = spec.preparations[sourceName] || { status: "failed" };
  const expectedRawSource = Buffer.from(String(action.expectedRawSource || ""), "utf8");
  const rawBytesExact = regularPrivateFile && Number.isSafeInteger(declaredBytes) &&
    declaredBytes === rawSource.length && rawSource.equals(expectedRawSource);
  const timeoutMs = Number(request.timeoutMs);
  // The runtime computes this remaining timeout against its own absolute parent
  // deadline before publishing the request. A separately scheduled fixture
  // process cannot reconstruct that instant from Date.now(): subtracting its
  // later handling delay makes an already-valid envelope fail nondeterministically.
  // The transport contract can deterministically assert that the child budget
  // is positive and never expands the original parent budget. A fixture-owned
  // lower-bound delay also proves elapsed time was deducted without comparing
  // this worker's later Date.now() against the runtime process's deadline.
  const deadlineBounded = Boolean(parent) && Number.isSafeInteger(timeoutMs) && timeoutMs > 0 &&
    timeoutMs <= parent.timeoutMs;
  const minimumDeadlineDeductionMs = parent
    ? Number(parent.minimumDeadlineDeductionMs || 0)
    : 0;
  const actualDeadlineDeductionMs = parent && Number.isSafeInteger(parent.timeoutMs) &&
    Number.isSafeInteger(timeoutMs)
    ? parent.timeoutMs - timeoutMs
    : -1;
  const deadlineDeductionSatisfied = Boolean(parent) &&
    Number.isSafeInteger(minimumDeadlineDeductionMs) && minimumDeadlineDeductionMs >= 0 &&
    actualDeadlineDeductionMs >= minimumDeadlineDeductionMs;
  const envelopeChecks = {
    versionExact: request.version === (spec.contractVersion || CONTRACT_VERSION),
    idSafe: id === safeId,
    executionIdExact: request.executionId === spec.executionId,
    parentOneTime: parentUseCount === 1,
    parentPresent: Boolean(parent),
    sourceNameExact: Boolean(parent) && sourceName === parent.sourceName,
    privatePathExact,
    rawBytesExact,
    deadlineBounded,
    deadlineDeductionSatisfied,
  };
  const failedEnvelopeChecks = Object.entries(envelopeChecks)
    .filter(([, passed]) => !passed)
    .map(([name]) => name);
  const envelopeValid = failedEnvelopeChecks.length === 0;
  appendWorkerEvent(spec.eventsPath, {
    operation: "prepare_plaintext_typescript",
    id,
    parentRequestId,
    parentUseCount,
    sourceName,
    sourceBytes: declaredBytes,
    timeoutMs,
    envelopeValid,
    privatePathExact,
    rawBytesExact,
    deadlineBounded,
    minimumDeadlineDeductionMs,
    actualDeadlineDeductionMs,
    deadlineDeductionSatisfied,
    parentTimeoutMs: parent ? parent.timeoutMs : -1,
    envelopeChecks,
    error: envelopeValid ? "" : `invalid private preparation envelope: ${failedEnvelopeChecks.join(", ")}`,
  });
  if (action.status === "ignore" && envelopeValid) return;
  const effectiveAction = envelopeValid ? action : {
    status: "failed",
    errorCode: "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_INVALID_REQUEST",
    errorMessage: `invalid private preparation envelope: ${failedEnvelopeChecks.join(", ")}`,
  };
  let sourcePath = "";
  let actualPreparedBytes = 0;
  if (effectiveAction.status === "prepared") {
    const bytes = Buffer.from(String(effectiveAction.source || ""), "utf8");
    actualPreparedBytes = bytes.length;
    sourcePath = path.join(
      spec.responseDir,
      effectiveAction.wrongPath ? `${safeId}.wrong.source` : `${safeId}.source`,
    );
    publishFileAtomically(sourcePath, bytes);
  }
  publishResponse(spec.responseDir, safeId, {
    version: spec.contractVersion || CONTRACT_VERSION,
    id,
    status: effectiveAction.status,
    resolvedPath: effectiveAction.resolvedPath === undefined
      ? sourceName
      : String(effectiveAction.resolvedPath),
    sourcePath,
    sourceBytes: actualPreparedBytes + Number(effectiveAction.sourceBytesDelta || 0),
    rawSourceBytes: declaredBytes + Number(effectiveAction.rawSourceBytesDelta || 0),
    elapsedMs: 1,
    errorCode: effectiveAction.errorCode || "",
    errorMessage: effectiveAction.errorMessage || "",
  });
  removeFileQuietly(requestFile);
}

function appendWorkerEvent(eventsPath, event) {
  fs.appendFileSync(eventsPath, `${JSON.stringify(event)}${os.EOL}`, "utf8");
}

function publishFileAtomically(destination, contents) {
  const temporary = `${destination}.tmp`;
  fs.writeFileSync(temporary, contents);
  fs.renameSync(temporary, destination);
}

function publishResponse(responseDir, safeId, response) {
  const responseFile = path.join(responseDir, `${safeId}.json`);
  publishFileAtomically(responseFile, JSON.stringify(response));
}

function advanceVirtualClock(clockPath, clockStartMs, elapsedMs) {
  assert.ok(clockPath, "virtual clock advance requires a fixture clock path");
  assert.ok(Number.isSafeInteger(clockStartMs), "virtual clock start must be a safe integer");
  assert.ok(Number.isSafeInteger(elapsedMs) && elapsedMs >= 0, "virtual clock advance must be non-negative");
  assert.equal(fs.existsSync(clockPath), false, "virtual clock advance marker was already published");
  publishFileAtomically(clockPath, String(clockStartMs + elapsedMs));
}

function removeFileQuietly(file) {
  try {
    fs.unlinkSync(file);
  } catch (_) {}
}

function safeName(value) {
  const safe = String(value || "invalid").replace(/[^A-Za-z0-9_.-]/g, "_");
  return safe.length <= 120 ? safe : safe.slice(0, 120);
}

function sleepSync(milliseconds) {
  const array = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(array, 0, 0, milliseconds);
}

#!/usr/bin/env node

"use strict";

const assert = require("node:assert/strict");
const childProcess = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const CONTRACT_VERSION = 1;
const CPP_SOURCE = path.resolve(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp");
const MIN_NODE_MAJOR = 24;

if (process.argv[2] === "--worker") {
  runTransportWorker(process.argv[3]);
} else {
  runTests();
}

function runTests() {
  const nodeMajor = Number(process.versions.node.split(".")[0]);
  assert.ok(nodeMajor >= MIN_NODE_MAJOR, `Node ${MIN_NODE_MAJOR}+ is required; found ${process.version}.`);
  const chunks = embeddedScriptChunks();
  assert.equal(chunks.length, 15, "embedded script interpolation layout changed");
  assertAndroidCredentialDataAliasContract();
  process.stdout.write(`PASS Android credential-data canonical path alias contract${os.EOL}`);

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
      name: "plaintext CommonJS fallback",
      files: { "dep.cjs": "module.exports = 'plain-cjs';" },
      responses: { "dep.cjs": { status: "not_encrypted" } },
      source: "globalThis.__providerTestValue = require('./dep.cjs');",
      expectedValue: "plain-cjs",
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
      expected: { moduleProviderRequestCount: 2, moduleProviderDecryptedCount: 2 },
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
  try {
    for (const scenario of scenarios) {
      runScenario(chunks, root, scenario);
      process.stdout.write(`PASS ${scenario.name}${os.EOL}`);
    }
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
  process.stdout.write(`PASS ${scenarios.length} module-source provider scenarios${os.EOL}`);
}

function runScenario(chunks, parentRoot, scenario) {
  const safeScenarioName = scenario.name.replace(/[^A-Za-z0-9_.-]/g, "_");
  const root = path.join(parentRoot, safeScenarioName);
  const requestDir = path.join(root, "transport", "requests");
  const responseDir = path.join(root, "transport", "responses");
  const resultPath = path.join(root, "result.json");
  const executionId = `test-${safeScenarioName}`;
  fs.mkdirSync(requestDir, { recursive: true });
  fs.mkdirSync(responseDir, { recursive: true });
  for (const [relativePath, contents] of Object.entries(scenario.files || {})) {
    const file = path.join(root, relativePath);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, contents, "utf8");
  }
  const entryName = scenario.entryName || "entry.cjs";
  const entryPath = path.join(root, entryName);
  fs.writeFileSync(entryPath, "AUTOJS6_TEST_ENTRY_PLACEHOLDER", "utf8");
  const timeoutMs = scenario.timeoutMs || 1000;
  const config = {
    enabled: true,
    version: CONTRACT_VERSION,
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
  const workerSpecPath = path.join(root, "worker.json");
  fs.writeFileSync(workerSpecPath, JSON.stringify({ requestDir, responseDir, actions: responseActions }), "utf8");
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
    const runner = testHookPrefix() + embedded + testHookSuffix(resultPath);
    const runnerPath = path.join(root, "runner.cjs");
    fs.writeFileSync(runnerPath, runner, "utf8");
    const run = childProcess.spawnSync(process.execPath, [runnerPath], {
      cwd: root,
      encoding: "utf8",
      timeout: Math.max(5000, timeoutMs * 4),
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
    assert.equal(envelope.moduleProviderEnabled, true, `${scenario.name}: provider was not enabled`);
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
  } finally {
    worker.kill();
  }
}

function embeddedScriptChunks() {
  const source = fs.readFileSync(CPP_SOURCE, "utf8");
  const start = source.indexOf("std::string buildEmbeddedScriptExecutionSource(");
  const end = source.indexOf("    return script;", start);
  assert.ok(start >= 0 && end > start, "could not locate embedded script builder");
  return [...source.slice(start, end).matchAll(/script \+= R"JS\(([\s\S]*?)\)JS";/g)]
    .map((match) => match[1]);
}

function assertAndroidCredentialDataAliasContract() {
  const source = fs.readFileSync(CPP_SOURCE, "utf8");
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

function testHookPrefix() {
  return [
    "const __autojs6ProviderTestFs = process.getBuiltinModule('fs');",
    "const __autojs6ProviderTestSetTimeout = globalThis.setTimeout;",
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

function runTransportWorker(specPath) {
  const spec = JSON.parse(fs.readFileSync(specPath, "utf8"));
  const seen = new Set();
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
      const action = spec.actions[path.resolve(request.path)] || { status: "failed" };
      if (action.status === "ignore") continue;
      const safeId = safeName(request.id);
      let sourcePath = "";
      let sourceBytes = 0;
      if (action.status === "decrypted") {
        const bytes = Buffer.from(String(action.source || ""), "utf8");
        sourceBytes = bytes.length;
        sourcePath = path.join(spec.responseDir, `${safeId}.source`);
        const sourceTemporary = `${sourcePath}.tmp`;
        fs.writeFileSync(sourceTemporary, bytes);
        fs.renameSync(sourceTemporary, sourcePath);
      }
      const response = {
        version: CONTRACT_VERSION,
        id: String(request.id),
        status: action.status,
        resolvedPath: path.resolve(request.path),
        sourcePath,
        sourceBytes,
        elapsedMs: 1,
        errorCode: action.errorCode || "",
        errorMessage: action.errorMessage || "",
      };
      const responseFile = path.join(spec.responseDir, `${safeId}.json`);
      const responseTemporary = `${responseFile}.tmp`;
      fs.writeFileSync(responseTemporary, JSON.stringify(response), "utf8");
      fs.renameSync(responseTemporary, responseFile);
      try {
        fs.unlinkSync(requestFile);
      } catch (_) {}
    }
  }, 2);
  timer.unref = timer.unref || function () {};
  process.on("SIGTERM", () => process.exit(0));
  process.on("SIGINT", () => process.exit(0));
}

function safeName(value) {
  const safe = String(value || "invalid").replace(/[^A-Za-z0-9_.-]/g, "_");
  return safe.length <= 120 ? safe : safe.slice(0, 120);
}

function sleepSync(milliseconds) {
  const array = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(array, 0, 0, milliseconds);
}

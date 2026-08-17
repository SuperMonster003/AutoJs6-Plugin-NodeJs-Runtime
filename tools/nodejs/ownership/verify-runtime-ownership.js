#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const POLICY_SCHEMA = "autojs6-node-runtime-ownership-policy-v1";
const REPORT_SCHEMA = "autojs6-node-runtime-ownership-report-v1";
const REQUIRED_PLUGIN_AUTHORITIES = [
  "bootstrap",
  "commonjs",
  "esm",
  "typescript_stripping",
  "startup_environment_policy",
  "native_runtime_lifecycle",
  "capability_catalog",
  "error_catalog",
  "runtime_build",
  "runtime_kit",
  "api_publication",
  "zlib_android_conformance",
  "crypto_android_conformance",
];
const REQUIRED_NORMAL_GATES = [
  "check",
  "verifyNodeCapabilityTruthGate",
  "verifyNodePluginRuntimeKitRelease",
];
const X3H_ANDROID_SELECTORS = [
  "x3h_20_rawTsEntryLoadsExtensionlessCjsThroughExactProvider",
  "x3h_21_packageMetadataMainIndexAndBareGraphUsesExactProvider",
  "x3h_22_workspaceCloseSerializesWithProviderMaterialization",
];
const X3I_SELECTOR = "x3i_23_zlibCallbacksStreamsAndShadowingPassThroughPublishedBinder";
const X3J_SELECTOR = "x3j_24_cryptoSafeExpansionAndShadowingPassThroughPublishedBinder";
const X3I_RECEIPT = {
  path: "tools/nodejs/ownership/evidence/x3i-host-removal.json",
  bytes: 5836,
  sha256: "80f8f4a86359fb94a926a813bb8c49c5611b9b1ecbbd90a97d0dda1a078c3aa4",
};
const X3I_DEVICE = {
  path: "tools/nodejs/ownership/evidence/x3i-device-run-api34-x86_64.json",
  bytes: 9404,
  sha256: "7096aaa64c452668a0d9702b21bfd02fcbae216eafaee58046dc3ccb28b7caf2",
};
const X3I_RAW = {
  path: "tools/nodejs/ownership/evidence/x3i-device-run-api34-x86_64.raw.txt",
  bytes: 957,
  sha256: "2b984dc7d588d467e21fe109eb11690103bde01289df52c8c5e64c623108eb4f",
};
const X3J_RECEIPT = {
  path: "tools/nodejs/ownership/evidence/x3j-host-removal.json",
  bytes: 9269,
  sha256: "ec7a5f25581ede1350dce4c7cf236da26a939e1589cc6006bfc3f8ab5ca9bd7e",
};
const X3J_DEVICE = {
  path: "tools/nodejs/ownership/evidence/x3j-device-run-api34-x86_64.json",
  bytes: 12632,
  sha256: "dc34afd236864d5a79a9bbb7bac746c451f6502e53a3952f2ff42c3875e30c29",
};
const X3J_RAW = {
  path: "tools/nodejs/ownership/evidence/x3j-device-run-api34-x86_64.raw.txt",
  bytes: 954,
  sha256: "861eec05bcc73202f3dae515f93ffc9903581c8bc7a79df6e4417b34d34b8222",
};
const SHA256_PATTERN = /^[0-9a-f]{64}$/;

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function normalizePath(value) {
  return String(value || "").replace(/\\/g, "/").replace(/^\.\//, "");
}

function sortedUnique(values) {
  return [...new Set(values.map(normalizePath).filter(Boolean))].sort();
}

function pathInventorySha256(paths) {
  const normalized = sortedUnique(paths);
  return sha256(normalized.length === 0 ? "" : `${normalized.join("\n")}\n`);
}

function violation(code, detail, relativePath = null) {
  return { code, path: relativePath, detail };
}

function addClaim(claims, relativePath, claim) {
  const normalized = normalizePath(relativePath);
  if (!claims.has(normalized)) claims.set(normalized, []);
  claims.get(normalized).push(claim);
}

function validatePolicy(policy) {
  const violations = [];
  if (policy.schema !== POLICY_SCHEMA) {
    violations.push(violation("POLICY_SCHEMA", `Expected ${POLICY_SCHEMA}, actual ${policy.schema || "missing"}.`));
  }
  if (policy.repositoryRole !== "node_runtime_plugin") {
    violations.push(violation("POLICY_ROLE", "Policy repositoryRole must be node_runtime_plugin."));
  }
  if (policy.principles?.pluginIsRuntimeAuthority !== true ||
      policy.principles?.newUnclassifiedRuntimePathsAreDenied !== true ||
      policy.principles?.deviceOrSoakRequired !== false) {
    violations.push(violation("POLICY_PRINCIPLES", "Runtime authority, deny-unclassified, and no-device/no-soak principles must be explicit."));
  }
  const authorities = new Set(policy.declaredAuthorities?.plugin || []);
  for (const authority of REQUIRED_PLUGIN_AUTHORITIES) {
    if (!authorities.has(authority)) {
      violations.push(violation("MISSING_PLUGIN_AUTHORITY", `Missing declared plugin authority ${authority}.`));
    }
  }
  const surfaceIds = new Set();
  for (const surface of policy.surfaces || []) {
    if (!surface.id || surfaceIds.has(surface.id)) {
      violations.push(violation("SURFACE_ID", `Surface id is missing or duplicated: ${surface.id || "missing"}.`));
    }
    surfaceIds.add(surface.id);
    if (!surface.owner || !surface.category) {
      violations.push(violation("SURFACE_METADATA", `Surface ${surface.id || "missing"} must declare owner and category.`));
    }
    for (const tree of surface.trees || []) {
      if (!String(tree.prefix || "").endsWith("/") || !Number.isInteger(tree.expectedFileCount) ||
          tree.expectedFileCount < 0 || !SHA256_PATTERN.test(String(tree.pathInventorySha256 || ""))) {
        violations.push(violation("TREE_POLICY", `Surface ${surface.id} has an invalid tree declaration.`));
      }
    }
  }
  for (const group of policy.boundedPathGroups || []) {
    if (!surfaceIds.has(group.surfaceId) || !Array.isArray(group.paths) || group.paths.length === 0 ||
        !Number.isInteger(group.minPresent) || !Number.isInteger(group.maxPresent) ||
        group.minPresent < 0 || group.maxPresent < group.minPresent) {
      violations.push(violation("BOUNDED_GROUP_POLICY", `Bounded path group ${group.id || "missing"} is invalid.`));
    }
  }
  const x3hSlices = (policy.stagedPluginOnlySlices || []).filter((slice) =>
    slice?.id === "x3h_external_raw_module_handoff",
  );
  const x3h = x3hSlices[0];
  const metadata = x3h?.policyMetadataSnapshot || {};
  if (x3hSlices.length !== 1 || x3h?.milestone !== "X3h" ||
      x3h?.state !== "staged-plugin-only" || x3h?.runtimeOwner !== "plugin_runtime" ||
      x3h?.testOwner !== "plugin_tests" || x3h?.providerV2ExactBoundedCandidates !== true ||
      JSON.stringify(x3h?.androidSelectors || []) !== JSON.stringify(X3H_ANDROID_SELECTORS) ||
      x3h?.initialModuleSourcesCount !== 0 || x3h?.initialRuntimeModuleSourcesCount !== 0 ||
      metadata.providerFirst !== true || metadata.sameRequestScopedSession !== true ||
      metadata.sharedAbsoluteDeadline !== true || metadata.sharedRequestAndByteBudgets !== true ||
      metadata.permissionAndBridgeLimitsConsumeSnapshot !== true ||
      metadata.onlyNotFoundUsesDefaults !== true || x3h?.hostPreloadQuarantined !== false ||
      x3h?.hostOwnershipMigrated !== false || x3h?.deviceExecuted !== false ||
      x3h?.runtimeKitFrozen !== true || x3h?.releaseNativeRebuildRequired !== false) {
    violations.push(violation(
      "X3H_STAGED_PLUGIN_ONLY_POLICY",
      "X3h must stay Plugin-only/A-staged with zero preload maps, exact provider metadata, no Host migration claim, no device claim, and a frozen Runtime Kit after its release native rebuild.",
    ));
  }
  const x3iStaged = (policy.stagedPluginOnlySlices || []).filter((slice) =>
    slice?.id === "x3i_zlib_android_handoff",
  );
  const hostBacklogs = (policy.externalMigrationBacklog || []).filter((entry) =>
    entry?.id === "host_node_device_tests_and_corpora",
  );
  const hostBacklog = hostBacklogs[0];
  const completedX3i = hostBacklog?.completedX3iSlice || {};
  const completedX3j = hostBacklog?.completedX3jSlice || {};
  const currentHostBacklog = hostBacklog?.currentHostBacklog || {};
  const x3iReceipt = completedX3i.hostRemovalReceipt || {};
  const x3iDevice = completedX3i.directedDevicePrerequisite || {};
  const x3jStaged = (policy.stagedPluginOnlySlices || []).filter((slice) =>
    slice?.id === "x3j_crypto_android_handoff",
  );
  const x3jReceipt = completedX3j.hostRemovalReceipt || {};
  const x3jDevice = completedX3j.directedDevicePrerequisite || {};
  const annotation = hostBacklog?.annotationAccounting || {};
  const evidence = new Set(hostBacklog?.evidence || []);
  if (x3iStaged.length !== 0 || hostBacklogs.length !== 1 ||
      hostBacklog?.state !== "node_compat_v1_v2_typescript_zlib_and_crypto_android_migrated_from_host" ||
      currentHostBacklog.nodeRelatedSourceFiles !== 132 ||
      currentHostBacklog.testAnnotations !== 749 || currentHostBacklog.corpusFiles !== 493 ||
      completedX3i.state !== "migrated-from-host" ||
      completedX3i.pluginAuthority !== "zlib_android_conformance" ||
      completedX3i.hostSourceSelector !== "zlibBufferCallbacksStreamsAndShadowingWork" ||
      completedX3i.pluginSelector !== X3I_SELECTOR || completedX3i.selectorCount !== 1 ||
      completedX3i.hostRemovalVerified !== true ||
      completedX3i.migratedAuthorityClaimed !== true || completedX3i.deviceExecuted !== true ||
      x3iReceipt.path !== X3I_RECEIPT.path || x3iReceipt.bytes !== X3I_RECEIPT.bytes ||
      x3iReceipt.sha256 !== X3I_RECEIPT.sha256 ||
      x3iDevice.path !== X3I_DEVICE.path || x3iDevice.bytes !== X3I_DEVICE.bytes ||
      x3iDevice.sha256 !== X3I_DEVICE.sha256 || x3iDevice.rawPath !== X3I_RAW.path ||
      x3iDevice.rawBytes !== X3I_RAW.bytes || x3iDevice.rawSha256 !== X3I_RAW.sha256 ||
      x3iDevice.attemptCount !== 1 || x3iDevice.testsRun !== 1 || x3iDevice.testsPassed !== 1 ||
      x3iDevice.baselineRestored !== true || x3iDevice.temporaryAvdStartedAndStopped !== true ||
      x3iDevice.serialAbsentAfterShutdown !== true || annotation.x3iRemovedZlibAnnotations !== 1 ||
      annotation.currentHostNodeTestAnnotations !== 749 ||
      ![X3I_RECEIPT.path, X3I_DEVICE.path, X3I_RAW.path].every((entry) => evidence.has(entry))) {
    violations.push(violation(
      "X3I_MIGRATED_AUTHORITY_POLICY",
      "X3i must have no staged entry and must pin the exact directed-device, Host-removal, backlog, and migrated-authority receipt.",
    ));
  }
  if (x3jStaged.length !== 0 || hostBacklogs.length !== 1 ||
      completedX3j.state !== "migrated-from-host" ||
      completedX3j.pluginAuthority !== "crypto_android_conformance" ||
      completedX3j.hostSourceSelector !== "safeCryptoExpansionAndShadowingWork" ||
      completedX3j.pluginSelector !== X3J_SELECTOR || completedX3j.selectorCount !== 1 ||
      completedX3j.hostRemovalVerified !== true ||
      completedX3j.migratedAuthorityClaimed !== true || completedX3j.deviceExecuted !== true ||
      x3jReceipt.path !== X3J_RECEIPT.path || x3jReceipt.bytes !== X3J_RECEIPT.bytes ||
      x3jReceipt.sha256 !== X3J_RECEIPT.sha256 ||
      x3jDevice.path !== X3J_DEVICE.path || x3jDevice.bytes !== X3J_DEVICE.bytes ||
      x3jDevice.sha256 !== X3J_DEVICE.sha256 || x3jDevice.rawPath !== X3J_RAW.path ||
      x3jDevice.rawBytes !== X3J_RAW.bytes || x3jDevice.rawSha256 !== X3J_RAW.sha256 ||
      x3jDevice.attemptCount !== 1 || x3jDevice.testsRun !== 1 || x3jDevice.testsPassed !== 1 ||
      x3jDevice.baselineRestored !== true || x3jDevice.temporaryAvdStartedAndStopped !== true ||
      x3jDevice.serialAbsentAfterShutdown !== true || annotation.x3jRemovedCryptoAnnotations !== 1 ||
      annotation.currentHostNodeTestAnnotations !== 749 ||
      ![X3J_RECEIPT.path, X3J_DEVICE.path, X3J_RAW.path].every((entry) => evidence.has(entry))) {
    violations.push(violation(
      "X3J_MIGRATED_AUTHORITY_POLICY",
      "X3j must have no staged entry and must pin the exact directed-device, Host-removal, backlog, and migrated crypto authority receipt.",
    ));
  }
  const normalGateWiring = policy.normalGateWiring || {};
  const normalGates = new Set(normalGateWiring.requiredGates || []);
  if (!normalGateWiring.gradlePath || REQUIRED_NORMAL_GATES.some((name) => !normalGates.has(name))) {
    violations.push(violation(
      "NORMAL_GATE_WIRING_POLICY",
      `normalGateWiring must declare ${REQUIRED_NORMAL_GATES.join(", ")} and its Gradle source path.`,
    ));
  }
  return violations;
}

function inspectX3iEvidence(repoRoot) {
  const violations = [];
  const identities = [X3I_RECEIPT, X3I_DEVICE, X3I_RAW].map((expected) => {
    const absolutePath = path.join(repoRoot, ...expected.path.split("/"));
    const exists = fs.existsSync(absolutePath) && fs.statSync(absolutePath).isFile();
    const bytes = exists ? fs.statSync(absolutePath).size : null;
    const actualSha256 = exists ? sha256(fs.readFileSync(absolutePath)) : null;
    const passed = exists && bytes === expected.bytes && actualSha256 === expected.sha256;
    if (!passed) {
      violations.push(violation(
        "X3I_EVIDENCE_DRIFT",
        `Expected ${expected.bytes}/${expected.sha256}, actual ${bytes}/${actualSha256}.`,
        expected.path,
      ));
    }
    return { path: expected.path, expectedBytes: expected.bytes, actualBytes: bytes,
      expectedSha256: expected.sha256, actualSha256, passed };
  });
  return { state: "migrated-from-host", selector: X3I_SELECTOR, identities,
    passed: violations.length === 0, violations };
}

function validateX3jReceipt(receipt) {
  const violations = [];
  const check = (condition, code, detail) => {
    if (!condition) violations.push(violation(code, detail, X3J_RECEIPT.path));
  };
  const same = (actual, expected) => JSON.stringify(actual) === JSON.stringify(expected);
  const plugin = receipt?.pluginPrerequisite || {};
  const directed = plugin.directedDeviceReport || {};
  const raw = plugin.rawDirectedDeviceOutput || {};
  const assertions = plugin.verifiedAssertions || {};
  const artifacts = plugin.artifacts || {};
  const app = artifacts.appDebugX86_64 || {};
  const androidTest = artifacts.androidTest || {};
  const source = artifacts.testSource || {};
  const packageOnly = plugin.packageOnlyHarnessPrerequisite || {};
  const staging = plugin.stagingInputs || {};
  const ownership = receipt?.ownershipGate || {};
  const compile = receipt?.androidTestCompile || {};
  const invocation = receipt?.invocation || {};
  const removals = receipt?.removals || {};
  const hostTest = removals.hostTestFile || {};
  const gateReferences = removals.hostGateReferences || {};
  const documentationReferences = removals.activeDocumentationReferences || {};
  const references = receipt?.referenceAudit || {};
  const staleAudit = receipt?.staleBuildOutputAudit || {};
  const staleDex = staleAudit.stalePreExistingDex || {};
  const backlog = receipt?.remainingHostBacklog || {};

  check(
    receipt?.schema === "autojs6-node-host-removal-receipt-v1" &&
      receipt?.repository === "AutoJs6" && receipt?.repositoryRole === "host" &&
      receipt?.scope === "x3j-crypto-focused-instrumentation-migration" &&
      receipt?.evidenceLevel === "S/A with prerequisite directed-device D" &&
      receipt?.sourceState?.decisionInput === "current_filesystem_including_untracked" &&
      receipt?.sourceState?.baseCommit === "25ec9f8488d942dd13791bfad600fadbc5429893" &&
      receipt?.sourceState?.wholeRepositoryHeadUsedForDecision === false,
    "X3J_RECEIPT_SCHEMA",
    "X3j receipt schema, scope, repository, or current-filesystem source state drifted.",
  );
  check(
    plugin.selector === X3J_SELECTOR && plugin.selectorCount === 1 &&
      directed.path === X3J_DEVICE.path && directed.bytes === X3J_DEVICE.bytes &&
      directed.sha256 === X3J_DEVICE.sha256 &&
      directed.schema === "autojs6-node-plugin-x3j-directed-device-run-v1" &&
      directed.result === "pass" && directed.device === "DEX_R1_API34_X64" &&
      directed.serial === "emulator-5562" && directed.apiLevel === 34 &&
      directed.abi === "x86_64" && directed.emulator === true &&
      directed.attemptCount === 1 && directed.retryCount === 0 &&
      directed.testsRun === 1 && directed.testsPassed === 1 &&
      directed.testsFailed === 0 && directed.testsIgnored === 0 &&
      directed.runnerSummary === "OK (1 test)" && directed.instrumentationCode === -1 &&
      directed.firstAndOnlyRunPassed === true && directed.baselineRestored === true &&
      directed.temporaryAvdStartedAndStopped === true &&
      directed.serialAbsentAfterShutdown === true &&
      directed.physicalDeviceDirectedCommandCount === 0 &&
      directed.connectedTaskUsed === false && directed.soakRun === false &&
      raw.path === X3J_RAW.path && raw.bytes === X3J_RAW.bytes &&
      raw.sha256 === X3J_RAW.sha256 && raw.verbatimAdbStdoutCaptured === true,
    "X3J_RECEIPT_DEVICE",
    "X3j receipt directed-device or raw-output prerequisite drifted.",
  );
  check(
    assertions.realPublishedBinderExecute === true &&
      assertions.workspaceArchiveV2Committed === true &&
      assertions.oneStartedOneTerminalEvent === true &&
      assertions.pendingCryptoCallbacks === 0 && assertions.timedOut === false &&
      assertions.moduleSourceProviderUsed === false &&
      assertions.exactGraphProviderUsed === false,
    "X3J_RECEIPT_ASSERTIONS",
    "X3j receipt crypto, lifecycle, workspace, timeout, or provider assertions drifted.",
  );
  check(
    app.path === "app/build/outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.1.3-x86_64.apk" &&
      app.bytes === 33764503 &&
      app.sha256 === "93f4dd32076a4f4f2d24b9c614962dddf83c3c6fad420c986d5f7c8d4a419de9" &&
      app.packageName === "io.github.supermonster003.autojs6.plugin.nodejs" &&
      app.versionName === "1.1.3" && app.versionCode === 40 &&
      app.minSdk === 24 && app.targetSdk === 36 &&
      app.signerSha256 === "31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213" &&
      androidTest.path === "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" &&
      androidTest.bytes === 988518 &&
      androidTest.sha256 === "bf56d938648de2f89185b7f4fc3595bfcb8776b2988fef11eac026b6dbd943ca" &&
      androidTest.packageName === "io.github.supermonster003.autojs6.plugin.nodejs.test" &&
      androidTest.targetPackageName === "io.github.supermonster003.autojs6.plugin.nodejs" &&
      androidTest.runner === "androidx.test.runner.AndroidJUnitRunner" &&
      androidTest.minSdk === 24 && androidTest.targetSdk === 36 &&
      androidTest.signerSha256 === "31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213" &&
      source.path ===
        "app/src/androidTest/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeRuntimePluginAndroidConformanceTest.java" &&
      source.bytes === 132491 &&
      source.sha256 === "53b493aeaf6d26c99c48560dc6ee5c24bbf9d8a4635def58da8c3002cb662e56",
    "X3J_RECEIPT_ARTIFACTS",
    "X3j receipt app, Android-test, source, package, version, or signer identity drifted.",
  );
  check(
    packageOnly.path === "build/reports/nodejs/android-conformance/harness-package.json" &&
      packageOnly.bytes === 25584 &&
      packageOnly.sha256 === "b4d98a45aa631c239ac6649b7fce9779cd179144568077df2e1e6faf1e1ff361" &&
      packageOnly.decision === "harness_packaged_not_executed" &&
      packageOnly.verificationLevel === "A" && packageOnly.selectorCount === 24 &&
      packageOnly.x3jSelectorCount === 1 && packageOnly.passedChecks === 41 &&
      packageOnly.totalChecks === 41 && packageOnly.deviceOrSoakExecuted === false &&
      staging.androidConformanceGate?.bytes === 143871 &&
      staging.androidConformanceGate?.sha256 ===
        "1518e8131e73d9d0ca2f78080500e5b8aba2a674dd965681c819ad3349de48f8" &&
      staging.ownershipPolicy?.bytes === 20053 &&
      staging.ownershipPolicy?.sha256 ===
        "a2c8a458c7d2aecf48abdf116403ef339c9d36ea75f70390f3d173245ecedc5b" &&
      staging.ownershipVerifier?.bytes === 38000 &&
      staging.ownershipVerifier?.sha256 ===
        "9469d2316e4750f6f425079c4575bcee583abd476f8e95a9a5f3d24756fd854e" &&
      staging.versionProperties?.bytes === 700 &&
      staging.versionProperties?.sha256 ===
        "4572f9799fb7cadae5f28cb51ca0cb8b38d7ddf67347970c555d77c399c839d3" &&
      staging.versionProperties?.versionName === "1.1.3" &&
      staging.versionProperties?.versionCode === 40 &&
      staging.versionProperties?.buildTime === 1786621005354,
    "X3J_RECEIPT_STAGING",
    "X3j receipt package-only A or frozen staging input identity drifted.",
  );
  check(
    ownership.task === ":app:verifyNodeRuntimeOwnershipGate" &&
      ownership.reportPath === "build/reports/nodejs/runtime-ownership.json" &&
      ownership.reportBytes === 10267 &&
      ownership.reportSha256 ===
        "447fe69b935beaaeb3af285ce0b386c7d967feef20add861891e0a0d942eb20c" &&
      ownership.allowlistPath === "docs/nodejs/runtime-ownership-allowlist.json" &&
      ownership.allowlistBytes === 19520 &&
      ownership.allowlistSha256 ===
        "0b44512fd0e37c1dfc7b2e23b6ba44a441d5d8d248699a5be1265b881592215b" &&
      ownership.ready === true &&
      ownership.decision === "boundary_enforced_migration_backlog_open" &&
      ownership.candidateCount === 2205 && ownership.classifiedCandidateCount === 2205 &&
      ownership.violationCount === 0 && ownership.migrationBacklogFileCount === 1707,
    "X3J_RECEIPT_HOST_OWNERSHIP",
    "X3j receipt Host ownership report, allowlist, counts, or decision drifted.",
  );
  check(
    compile.task === ":app:compileAppDebugAndroidTestKotlin" &&
      compile.result === "passed" && compile.sameInvocationAsOwnershipGate === true &&
      compile.freshTargetClassCount === 0 &&
      compile.freshTargetClassGlob ===
        "app/build/intermediates/built_in_kotlinc/appDebugAndroidTest/compileAppDebugAndroidTestKotlin/classes/**/NodeCryptoExpansionInstrumentationTest*",
    "X3J_RECEIPT_COMPILE",
    "X3j receipt Host AndroidTest compile or fresh-class result drifted.",
  );
  check(
    invocation.singleGradleInvocation === true && invocation.rerunPerformed === false &&
      invocation.outerToolTimedOut === false &&
      invocation.gradleDaemonResult === "BUILD SUCCESSFUL" &&
      invocation.gradleDaemonDuration === "2m 31s" && invocation.totalTasks === 328 &&
      invocation.executedTasks === 9 && invocation.upToDateTasks === 319,
    "X3J_RECEIPT_INVOCATION",
    "X3j receipt must pin the single successful non-timeout 328/9/319 Gradle invocation.",
  );
  check(
    hostTest.path ===
        "app/src/androidTest/java/org/autojs/autojs/engine/NodeCryptoExpansionInstrumentationTest.kt" &&
      hostTest.exists === false &&
      hostTest.preRemovalIdentitySource === "current_filesystem_including_untracked" &&
      hostTest.preRemovalBytes === 4485 &&
      hostTest.preRemovalSha256 ===
        "63c28215f332c9a68bc9709638cef3842edd242dd9ad7a2d58c0b5f2d6874033" &&
      hostTest.headGitBlobObjectIdSha1 === "22130a83a0d21645de5c1805d4e68e474afe6571" &&
      hostTest.headBlobBytes === 4384 &&
      hostTest.headBlobSha256 ===
        "fd124349f3149a996ca27d77e55924f4220d559722534749736175bcb5bdb58b" &&
      hostTest.headBlobContentEncoding === "UTF-8 with LF and trailing LF" &&
      hostTest.symbol === "safeCryptoExpansionAndShadowingWork" &&
      hostTest.remainingMatches === 0 && hostTest.removedTestAnnotations === 1,
    "X3J_RECEIPT_SOURCE_REMOVAL",
    "X3j receipt Host source removal or pre-removal/HEAD identity drifted.",
  );
  check(
    gateReferences.path === "app/node-instrumentation-gates.gradle.kts" &&
      gateReferences.className ===
        "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest" &&
      gateReferences.removedReferences === 1 && gateReferences.remainingMatches === 0 &&
      same(documentationReferences.paths, [
        "docs/nodejs/TESTING.md",
        "docs/nodejs/RELEASE_CHECKLIST.md",
      ]) &&
      documentationReferences.className ===
        "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest" &&
      documentationReferences.removedReferences === 2 &&
      documentationReferences.remainingOldClassReferences === 0,
    "X3J_RECEIPT_REMOVAL_REFERENCES",
    "X3j receipt Host gate or active-documentation removal counts drifted.",
  );
  check(
    same(references.activeScanRoots, [
      "app", "docs/nodejs/TESTING.md", "docs/nodejs/RELEASE_CHECKLIST.md",
    ]) && references.activeScanExcludedPrefix === "docs/nodejs/reports/" &&
      references.activeOldClassReferences === 0 && references.activeOldSymbolReferences === 0 &&
      references.activeOldGateOrDocumentationReferences === 0 &&
      same(references.activeReferencePaths, []) &&
      references.historicalClassReferencesRetained === 4 &&
      same(references.historicalReferenceLocations, [
        "docs/nodejs/reports/BASELINE_AFTER_TASK_49.md:264",
        "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:265",
        "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:379",
        "docs/nodejs/reports/BASELINE_AFTER_TASK_18.md:628",
      ]),
    "X3J_RECEIPT_REFERENCE_AUDIT",
    "X3j receipt active-zero and historical-four reference audit drifted.",
  );
  check(
    staleAudit.freshTargetClassCount === 0 && staleAudit.stalePreExistingDexCount === 1 &&
      staleDex.path ===
        "app/build/intermediates/project_dex_archive/appDebugAndroidTest/dexBuilderAppDebugAndroidTest/out/org/autojs/autojs/engine/NodeCryptoExpansionInstrumentationTest.dex" &&
      staleDex.bytes === 6940 &&
      staleDex.sha256 === "770c6c393e36f58e8d23215ce9c2b9e28ab7e299fe940556c49adcb333036760" &&
      staleDex.lastWriteTimeUtc === "2026-08-09T01:51:13.8577903Z" &&
      staleDex.classification === "pre-existing dexBuilder archive; not fresh Kotlin compile output" &&
      staleDex.deleted === false && staleDex.claimedFresh === false,
    "X3J_RECEIPT_STALE_DEX",
    "X3j receipt must distinguish zero fresh target classes from the one stale dexBuilder archive.",
  );
  check(
    backlog.nodeRelatedSourceFiles === 132 && backlog.testAnnotations === 749 &&
      backlog.corpusFiles === 493 && backlog.migrationBacklogFileCount === 1707 &&
      backlog.migrationBacklogPathSetSha256 ===
        "2aa453ef274dd3c260c071b1194cf62d6e138c89dc58c0ac865856689853e521" &&
      backlog.instrumentationFileCount === 132 &&
      backlog.instrumentationPathSetSha256 ===
        "b13fe21f53bfb699acb14688736775e01e375871bdebda0b8473d990fb9eb306" &&
      backlog.corpusPathSetSha256 ===
        "7d00b2347f7ac53c56e24f9a2ea2d50aa0466b17b920e27b5ef31c0b443348c5",
    "X3J_RECEIPT_REMAINING_BACKLOG",
    "X3j receipt Host backlog counts or path-set hashes drifted.",
  );
  return violations;
}

function inspectX3jEvidence(repoRoot) {
  const violations = [];
  const identities = [X3J_RECEIPT, X3J_DEVICE, X3J_RAW].map((expected) => {
    const absolutePath = path.join(repoRoot, ...expected.path.split("/"));
    const exists = fs.existsSync(absolutePath) && fs.statSync(absolutePath).isFile();
    const bytes = exists ? fs.statSync(absolutePath).size : null;
    const actualSha256 = exists ? sha256(fs.readFileSync(absolutePath)) : null;
    const passed = exists && bytes === expected.bytes && actualSha256 === expected.sha256;
    if (!passed) {
      violations.push(violation(
        "X3J_EVIDENCE_DRIFT",
        `Expected ${expected.bytes}/${expected.sha256}, actual ${bytes}/${actualSha256}.`,
        expected.path,
      ));
    }
    return { path: expected.path, expectedBytes: expected.bytes, actualBytes: bytes,
      expectedSha256: expected.sha256, actualSha256, passed };
  });
  const receiptPath = path.join(repoRoot, ...X3J_RECEIPT.path.split("/"));
  let receiptFieldViolations = [];
  if (fs.existsSync(receiptPath) && fs.statSync(receiptPath).isFile()) {
    try {
      receiptFieldViolations = validateX3jReceipt(JSON.parse(fs.readFileSync(receiptPath, "utf8")));
    } catch (error) {
      receiptFieldViolations = [violation(
        "X3J_RECEIPT_JSON",
        `X3j Host-removal receipt is not valid JSON: ${error.message}`,
        X3J_RECEIPT.path,
      )];
    }
  }
  violations.push(...receiptFieldViolations);
  return { state: "migrated-from-host", selector: X3J_SELECTOR, identities,
    receiptFieldsValidated: receiptFieldViolations.length === 0,
    passed: violations.length === 0, violations };
}

function inspectNormalGateWiring(policy, repoRoot) {
  const config = policy.normalGateWiring || {};
  const gradlePath = normalizePath(config.gradlePath);
  const requiredGates = sortedUnique(config.requiredGates || []);
  const absolutePath = path.join(repoRoot, ...gradlePath.split("/"));
  const violations = [];
  let source = "";
  if (!gradlePath || !fs.existsSync(absolutePath)) {
    violations.push(violation("NORMAL_GATE_WIRING_SOURCE", "Normal-gate wiring Gradle source is missing.", gradlePath || null));
  } else {
    source = fs.readFileSync(absolutePath, "utf8");
  }
  const taskNamesPresent = requiredGates.filter((name) => source.includes(`"${name}"`));
  const dependencyMarkerPresent = source.includes("dependsOn(verifyNodeRuntimeOwnershipGate)");
  const lazyMatchingMarkerPresent = source.includes("tasks.matching") && source.includes("configureEach");
  if (taskNamesPresent.length !== requiredGates.length || !dependencyMarkerPresent || !lazyMatchingMarkerPresent) {
    violations.push(violation(
      "NORMAL_GATE_WIRING",
      "Plugin check, capability truth, and Runtime Kit release gates must lazily depend on verifyNodeRuntimeOwnershipGate.",
      gradlePath || null,
    ));
  }
  return {
    gradlePath,
    requiredGates,
    taskNamesPresent,
    dependencyMarkerPresent,
    lazyMatchingMarkerPresent,
    passed: violations.length === 0,
    violations,
  };
}

function collectClaims(policy, repoFiles) {
  const fileSet = new Set(repoFiles);
  const claims = new Map();
  const violations = [];
  const treeInventories = [];
  const boundedGroups = [];

  for (const surface of policy.surfaces || []) {
    for (const relativePath of surface.requiredExactPaths || []) {
      const normalized = normalizePath(relativePath);
      if (!fileSet.has(normalized)) {
        violations.push(violation("MISSING_REQUIRED_PATH", `Required ${surface.id} path is missing.`, normalized));
      } else {
        addClaim(claims, normalized, {
          surfaceId: surface.id,
          owner: surface.owner,
          category: surface.category,
          source: "required_exact",
        });
      }
    }
    for (const relativePath of surface.optionalExactPaths || []) {
      const normalized = normalizePath(relativePath);
      if (fileSet.has(normalized)) {
        addClaim(claims, normalized, {
          surfaceId: surface.id,
          owner: surface.owner,
          category: surface.category,
          source: "optional_exact",
        });
      }
    }
    for (const tree of surface.trees || []) {
      const prefix = normalizePath(tree.prefix);
      const paths = repoFiles.filter((entry) => entry.startsWith(prefix));
      const actualSha256 = pathInventorySha256(paths);
      const passed = paths.length === tree.expectedFileCount && actualSha256 === tree.pathInventorySha256;
      treeInventories.push({
        surfaceId: surface.id,
        owner: surface.owner,
        category: surface.category,
        prefix,
        expectedFileCount: tree.expectedFileCount,
        actualFileCount: paths.length,
        expectedPathInventorySha256: tree.pathInventorySha256,
        actualPathInventorySha256: actualSha256,
        passed,
      });
      if (!passed) {
        violations.push(violation(
          "TREE_PATH_INVENTORY_DRIFT",
          `Tree path inventory changed: expected ${tree.expectedFileCount}/${tree.pathInventorySha256}, actual ${paths.length}/${actualSha256}.`,
          prefix,
        ));
      }
      for (const relativePath of paths) {
        addClaim(claims, relativePath, {
          surfaceId: surface.id,
          owner: surface.owner,
          category: surface.category,
          source: `tree:${prefix}`,
        });
      }
    }
  }

  for (const group of policy.boundedPathGroups || []) {
    const present = sortedUnique(group.paths || []).filter((entry) => fileSet.has(entry));
    const passed = present.length >= group.minPresent && present.length <= group.maxPresent;
    boundedGroups.push({
      id: group.id,
      surfaceId: group.surfaceId,
      minPresent: group.minPresent,
      maxPresent: group.maxPresent,
      present,
      passed,
    });
    if (!passed) {
      violations.push(violation(
        "BOUNDED_PATH_GROUP",
        `Bounded group ${group.id} expected ${group.minPresent}..${group.maxPresent} paths, actual ${present.length}: ${present.join(", ") || "none"}.`,
      ));
    }
    for (const relativePath of present) {
      addClaim(claims, relativePath, {
        surfaceId: group.surfaceId,
        owner: group.owner,
        category: group.category,
        source: `bounded_group:${group.id}`,
      });
    }
  }

  return { claims, violations, treeInventories, boundedGroups };
}

function discoverCandidates(policy, repoRoot, repoFiles, readText = null) {
  const discovery = policy.candidateDiscovery || {};
  const managedPrefixes = (discovery.managedPrefixes || []).map(normalizePath);
  const managedExact = new Set((discovery.managedExactPaths || []).map(normalizePath));
  const managedPatterns = (discovery.managedPathPatterns || []).map((pattern) => new RegExp(pattern));
  const contentRoots = (discovery.contentScanRoots || []).map(normalizePath);
  const contentExtensions = discovery.contentScanExtensions || [];
  const contentTokens = discovery.contentTokens || [];
  const excludedPrefixes = (discovery.contentScanExcludePrefixes || []).map(normalizePath);
  const result = new Map();
  const record = (relativePath, reason) => {
    if (!result.has(relativePath)) result.set(relativePath, []);
    result.get(relativePath).push(reason);
  };
  const reader = readText || ((relativePath) => {
    const absolutePath = path.join(repoRoot, ...relativePath.split("/"));
    const stat = fs.statSync(absolutePath);
    if (stat.size > 4 * 1024 * 1024) return { oversized: true, text: "" };
    const buffer = fs.readFileSync(absolutePath);
    if (buffer.includes(0)) return { binary: true, text: "" };
    return { text: buffer.toString("utf8") };
  });

  for (const relativePath of repoFiles) {
    if (managedExact.has(relativePath)) record(relativePath, "managed_exact_path");
    const managedPrefix = managedPrefixes.find((prefix) => relativePath.startsWith(prefix));
    if (managedPrefix) record(relativePath, `managed_prefix:${managedPrefix}`);
    const managedPattern = managedPatterns.find((pattern) => pattern.test(relativePath));
    if (managedPattern) record(relativePath, `managed_pattern:${managedPattern.source}`);

    const inContentRoot = contentRoots.some((prefix) => relativePath.startsWith(prefix));
    const excluded = excludedPrefixes.some((prefix) => relativePath.startsWith(prefix));
    const supportedExtension = contentExtensions.some((extension) => relativePath.endsWith(extension));
    if (!inContentRoot || excluded || !supportedExtension) continue;
    const content = reader(relativePath);
    if (content.oversized) {
      record(relativePath, "oversized_content_scan_file");
      continue;
    }
    if (content.binary) continue;
    const matchedTokens = contentTokens.filter((token) => content.text.includes(token));
    if (matchedTokens.length > 0) record(relativePath, `content_tokens:${matchedTokens.join(",")}`);
  }
  return result;
}

function evaluate(policy, repoRoot, repoFiles, readText = null) {
  const policyViolations = validatePolicy(policy);
  const collected = collectClaims(policy, repoFiles);
  const candidates = discoverCandidates(policy, repoRoot, repoFiles, readText);
  const violations = [...policyViolations, ...collected.violations];
  const entries = [];

  for (const [relativePath, reasons] of [...candidates.entries()].sort(([left], [right]) => left.localeCompare(right))) {
    const claims = collected.claims.get(relativePath) || [];
    if (claims.length === 0) {
      violations.push(violation(
        "UNCLASSIFIED_RUNTIME_PATH",
        `Node-owned candidate has no ownership classification; update the canonical policy intentionally. Discovery: ${reasons.join("; ")}.`,
        relativePath,
      ));
    }
    if (claims.length > 1) {
      violations.push(violation(
        "DUPLICATE_OWNERSHIP_CLAIM",
        `Path is claimed ${claims.length} times: ${claims.map((claim) => `${claim.surfaceId}/${claim.source}`).join(", ")}.`,
        relativePath,
      ));
    }
    entries.push({ path: relativePath, discovery: [...new Set(reasons)].sort(), claims });
  }

  for (const [relativePath, claims] of collected.claims.entries()) {
    if (claims.length > 1 && !candidates.has(relativePath)) {
      violations.push(violation(
        "DUPLICATE_OWNERSHIP_CLAIM",
        `Classified path is claimed ${claims.length} times.`,
        relativePath,
      ));
    }
  }

  violations.sort((left, right) => `${left.code}:${left.path || ""}`.localeCompare(`${right.code}:${right.path || ""}`));
  return {
    entries,
    violations,
    treeInventories: collected.treeInventories,
    boundedGroups: collected.boundedGroups,
    candidateCount: candidates.size,
    classifiedCandidateCount: entries.filter((entry) => entry.claims.length === 1).length,
  };
}

function runSelfTests(repoRoot) {
  const tests = [];
  const test = (id, callback) => {
    callback();
    tests.push({ id, passed: true });
  };
  const basePolicy = {
    schema: POLICY_SCHEMA,
    repositoryRole: "node_runtime_plugin",
    principles: {
      pluginIsRuntimeAuthority: true,
      newUnclassifiedRuntimePathsAreDenied: true,
      deviceOrSoakRequired: false,
    },
    declaredAuthorities: { plugin: REQUIRED_PLUGIN_AUTHORITIES },
    surfaces: [
      {
        id: "runtime",
        owner: "plugin_runtime",
        category: "runtime_semantics",
        requiredExactPaths: ["runtime/known.js"],
        trees: [{
          prefix: "native/",
          expectedFileCount: 1,
          pathInventorySha256: pathInventorySha256(["native/a.cpp"]),
        }],
      },
    ],
    boundedPathGroups: [{
      id: "optional",
      surfaceId: "runtime",
      owner: "plugin_runtime",
      category: "runtime_semantics",
      minPresent: 0,
      maxPresent: 1,
      paths: ["runtime/optional.java", "runtime/optional.kt"],
    }],
    stagedPluginOnlySlices: [{
      id: "x3h_external_raw_module_handoff",
      milestone: "X3h",
      state: "staged-plugin-only",
      runtimeOwner: "plugin_runtime",
      testOwner: "plugin_tests",
      providerV2ExactBoundedCandidates: true,
      policyMetadataSnapshot: {
        providerFirst: true,
        sameRequestScopedSession: true,
        sharedAbsoluteDeadline: true,
        sharedRequestAndByteBudgets: true,
        permissionAndBridgeLimitsConsumeSnapshot: true,
        onlyNotFoundUsesDefaults: true,
      },
      androidSelectors: X3H_ANDROID_SELECTORS,
      initialModuleSourcesCount: 0,
      initialRuntimeModuleSourcesCount: 0,
      hostPreloadQuarantined: false,
      hostOwnershipMigrated: false,
      deviceExecuted: false,
      runtimeKitFrozen: true,
      releaseNativeRebuildRequired: false,
    }],
    externalMigrationBacklog: [{
      id: "host_node_device_tests_and_corpora",
      state: "node_compat_v1_v2_typescript_zlib_and_crypto_android_migrated_from_host",
      currentHostBacklog: { nodeRelatedSourceFiles: 132, testAnnotations: 749, corpusFiles: 493 },
      completedX3iSlice: {
        state: "migrated-from-host",
        pluginAuthority: "zlib_android_conformance",
        hostSourceSelector: "zlibBufferCallbacksStreamsAndShadowingWork",
        pluginSelector: X3I_SELECTOR,
        selectorCount: 1,
        hostRemovalVerified: true,
        migratedAuthorityClaimed: true,
        deviceExecuted: true,
        hostRemovalReceipt: X3I_RECEIPT,
        directedDevicePrerequisite: {
          path: X3I_DEVICE.path,
          bytes: X3I_DEVICE.bytes,
          sha256: X3I_DEVICE.sha256,
          rawPath: X3I_RAW.path,
          rawBytes: X3I_RAW.bytes,
          rawSha256: X3I_RAW.sha256,
          attemptCount: 1,
          testsRun: 1,
          testsPassed: 1,
          baselineRestored: true,
          temporaryAvdStartedAndStopped: true,
          serialAbsentAfterShutdown: true,
        },
      },
      completedX3jSlice: {
        state: "migrated-from-host",
        pluginAuthority: "crypto_android_conformance",
        hostSourceSelector: "safeCryptoExpansionAndShadowingWork",
        pluginSelector: X3J_SELECTOR,
        selectorCount: 1,
        hostRemovalVerified: true,
        migratedAuthorityClaimed: true,
        deviceExecuted: true,
        hostRemovalReceipt: X3J_RECEIPT,
        directedDevicePrerequisite: {
          path: X3J_DEVICE.path,
          bytes: X3J_DEVICE.bytes,
          sha256: X3J_DEVICE.sha256,
          rawPath: X3J_RAW.path,
          rawBytes: X3J_RAW.bytes,
          rawSha256: X3J_RAW.sha256,
          attemptCount: 1,
          testsRun: 1,
          testsPassed: 1,
          baselineRestored: true,
          temporaryAvdStartedAndStopped: true,
          serialAbsentAfterShutdown: true,
        },
      },
      annotationAccounting: {
        x3iRemovedZlibAnnotations: 1,
        x3jRemovedCryptoAnnotations: 1,
        currentHostNodeTestAnnotations: 749,
      },
      evidence: [
        X3I_RECEIPT.path, X3I_DEVICE.path, X3I_RAW.path,
        X3J_RECEIPT.path, X3J_DEVICE.path, X3J_RAW.path,
      ],
    }],
    normalGateWiring: {
      gradlePath: "ownership.gradle.kts",
      requiredGates: REQUIRED_NORMAL_GATES,
    },
    candidateDiscovery: {
      managedPrefixes: ["runtime/", "native/"],
      managedExactPaths: [],
      contentScanRoots: [],
      contentScanExtensions: [],
      contentTokens: [],
      contentScanExcludePrefixes: [],
    },
  };
  const noRead = () => ({ text: "" });
  const x3jReceiptFixture = JSON.parse(fs.readFileSync(
    path.join(repoRoot, ...X3J_RECEIPT.path.split("/")),
    "utf8",
  ));

  test("valid_exact_tree_and_optional_absence", () => {
    const result = evaluate(basePolicy, ".", ["native/a.cpp", "runtime/known.js"], noRead);
    if (result.violations.length !== 0) throw new Error(JSON.stringify(result.violations));
  });
  test("x3j_receipt_exact_fields_are_valid", () => {
    const violations = validateX3jReceipt(x3jReceiptFixture);
    if (violations.length !== 0) throw new Error(JSON.stringify(violations));
  });
  test("x3j_receipt_rejects_missing_invocation_field", () => {
    const mutated = JSON.parse(JSON.stringify(x3jReceiptFixture));
    delete mutated.invocation.outerToolTimedOut;
    const violations = validateX3jReceipt(mutated);
    if (!violations.some((entry) => entry.code === "X3J_RECEIPT_INVOCATION")) {
      throw new Error("Expected X3j missing invocation-field violation.");
    }
  });
  test("x3j_receipt_rejects_task_count_drift", () => {
    const mutated = JSON.parse(JSON.stringify(x3jReceiptFixture));
    mutated.invocation.totalTasks = 329;
    const violations = validateX3jReceipt(mutated);
    if (!violations.some((entry) => entry.code === "X3J_RECEIPT_INVOCATION")) {
      throw new Error("Expected X3j task-count violation.");
    }
  });
  test("x3j_receipt_rejects_removed_reference_drift", () => {
    const mutated = JSON.parse(JSON.stringify(x3jReceiptFixture));
    mutated.removals.hostGateReferences.removedReferences = 0;
    mutated.removals.activeDocumentationReferences.removedReferences = 1;
    const violations = validateX3jReceipt(mutated);
    if (!violations.some((entry) => entry.code === "X3J_RECEIPT_REMOVAL_REFERENCES")) {
      throw new Error("Expected X3j removed-reference violation.");
    }
  });
  test("x3h_staged_slice_rejects_host_quarantine_promotion", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.stagedPluginOnlySlices[0].hostPreloadQuarantined = true;
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3H_STAGED_PLUGIN_ONLY_POLICY")) {
      throw new Error("Expected X3h staged-only promotion violation.");
    }
  });
  test("x3j_rejects_unverified_host_removal", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.externalMigrationBacklog[0].completedX3jSlice.hostRemovalVerified = false;
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3J_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3j unverified Host-removal violation.");
    }
  });
  test("x3j_rejects_receipt_hash_drift", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.externalMigrationBacklog[0].completedX3jSlice.hostRemovalReceipt.sha256 = "0".repeat(64);
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3J_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3j receipt-hash violation.");
    }
  });
  test("x3j_rejects_reintroduced_staged_slice", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.stagedPluginOnlySlices.push({ id: "x3j_crypto_android_handoff" });
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3J_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3j staged-slice violation.");
    }
  });
  test("x3i_rejects_unverified_host_removal", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.externalMigrationBacklog[0].completedX3iSlice.hostRemovalVerified = false;
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3I_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3i unverified Host-removal violation.");
    }
  });
  test("x3i_rejects_receipt_hash_drift", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.externalMigrationBacklog[0].completedX3iSlice.hostRemovalReceipt.sha256 = "0".repeat(64);
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3I_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3i receipt-hash violation.");
    }
  });
  test("x3i_rejects_reintroduced_staged_slice", () => {
    const mutated = JSON.parse(JSON.stringify(basePolicy));
    mutated.stagedPluginOnlySlices.push({ id: "x3i_zlib_android_handoff" });
    const violations = validatePolicy(mutated);
    if (!violations.some((entry) => entry.code === "X3I_MIGRATED_AUTHORITY_POLICY")) {
      throw new Error("Expected X3i staged-slice violation.");
    }
  });
  test("new_unclassified_runtime_path_is_denied", () => {
    const result = evaluate(basePolicy, ".", ["native/a.cpp", "runtime/known.js", "runtime/new.js"], noRead);
    if (!result.violations.some((entry) => entry.code === "UNCLASSIFIED_RUNTIME_PATH" && entry.path === "runtime/new.js")) {
      throw new Error("Expected unclassified runtime path violation.");
    }
  });
  test("tree_path_inventory_drift_is_denied", () => {
    const result = evaluate(basePolicy, ".", ["native/a.cpp", "native/b.cpp", "runtime/known.js"], noRead);
    if (!result.violations.some((entry) => entry.code === "TREE_PATH_INVENTORY_DRIFT")) {
      throw new Error("Expected tree path inventory drift violation.");
    }
  });
  test("missing_required_path_is_denied", () => {
    const result = evaluate(basePolicy, ".", ["native/a.cpp"], noRead);
    if (!result.violations.some((entry) => entry.code === "MISSING_REQUIRED_PATH")) {
      throw new Error("Expected missing required path violation.");
    }
  });
  test("bounded_alternatives_reject_duplicate_implementations", () => {
    const result = evaluate(
      basePolicy,
      ".",
      ["native/a.cpp", "runtime/known.js", "runtime/optional.java", "runtime/optional.kt"],
      noRead,
    );
    if (!result.violations.some((entry) => entry.code === "BOUNDED_PATH_GROUP")) {
      throw new Error("Expected bounded path group violation.");
    }
  });
  test("duplicate_surface_claim_is_denied", () => {
    const duplicatePolicy = JSON.parse(JSON.stringify(basePolicy));
    duplicatePolicy.surfaces.push({
      id: "duplicate",
      owner: "plugin_runtime",
      category: "runtime_semantics",
      requiredExactPaths: ["runtime/known.js"],
    });
    const result = evaluate(duplicatePolicy, ".", ["native/a.cpp", "runtime/known.js"], noRead);
    if (!result.violations.some((entry) => entry.code === "DUPLICATE_OWNERSHIP_CLAIM")) {
      throw new Error("Expected duplicate ownership claim violation.");
    }
  });
  return tests;
}

function git(repoRoot, args, encoding = "utf8") {
  const result = childProcess.spawnSync("git", args, {
    cwd: repoRoot,
    encoding,
    windowsHide: true,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const stderr = Buffer.isBuffer(result.stderr) ? result.stderr.toString("utf8") : String(result.stderr || "");
    throw new Error(`git ${args.join(" ")} failed (${result.status}): ${stderr.trim()}`);
  }
  return result.stdout;
}

function repositoryFiles(repoRoot) {
  const output = git(repoRoot, ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], null);
  return sortedUnique(output.toString("utf8").split("\0")).filter((relativePath) =>
    fs.existsSync(path.join(repoRoot, ...relativePath.split("/"))),
  );
}

function repositoryIdentity(repoRoot) {
  const status = git(repoRoot, ["status", "--porcelain=v1", "-z"], null);
  return { worktreeDirty: status.length > 0 };
}

function parseArgs(argv) {
  const options = { selfTest: false };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--self-test") {
      options.selfTest = true;
      continue;
    }
    if (!["--repo-root", "--policy", "--report"].includes(arg)) {
      throw new Error(`Unknown argument: ${arg}`);
    }
    const value = argv[index + 1];
    if (!value) throw new Error(`Missing value for ${arg}.`);
    options[arg.slice(2).replace(/-([a-z])/g, (_, character) => character.toUpperCase())] = value;
    index += 1;
  }
  for (const required of ["repoRoot", "policy", "report"]) {
    if (!options[required]) throw new Error(`Missing --${required.replace(/[A-Z]/g, (character) => `-${character.toLowerCase()}`)}.`);
  }
  return options;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot);
  const policyPath = path.resolve(repoRoot, options.policy);
  const reportPath = path.resolve(repoRoot, options.report);
  const policyBytes = fs.readFileSync(policyPath);
  const policy = JSON.parse(policyBytes.toString("utf8"));
  const selfTests = options.selfTest ? runSelfTests(repoRoot) : [];
  const files = repositoryFiles(repoRoot);
  const evaluation = evaluate(policy, repoRoot, files);
  const normalGateWiring = inspectNormalGateWiring(policy, repoRoot);
  const x3iEvidence = inspectX3iEvidence(repoRoot);
  const x3jEvidence = inspectX3jEvidence(repoRoot);
  evaluation.violations.push(...normalGateWiring.violations);
  evaluation.violations.push(...x3iEvidence.violations);
  evaluation.violations.push(...x3jEvidence.violations);
  evaluation.violations.sort((left, right) =>
    `${left.code}:${left.path || ""}`.localeCompare(`${right.code}:${right.path || ""}`),
  );
  const identity = repositoryIdentity(repoRoot);
  const passed = evaluation.violations.length === 0;
  const typescriptImplementation = evaluation.boundedGroups.find((entry) =>
    entry.id === "typescript_stripper_implementation",
  );
  const typescriptUnitTest = evaluation.boundedGroups.find((entry) =>
    entry.id === "typescript_stripper_unit_test",
  );
  const typescriptEntryStrippingOwnedAndPresent = Boolean(
    typescriptImplementation?.passed && typescriptImplementation.present.length === 1 &&
    typescriptUnitTest?.passed && typescriptUnitTest.present.length === 1,
  );
  const startupEnvironmentImplementationPath =
    "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeStartupEnvironmentPolicy.java";
  const startupEnvironmentUnitTestPath =
    "app/src/test/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeStartupEnvironmentPolicyTest.java";
  const singleClaimedPaths = new Set(
    evaluation.entries
      .filter((entry) => entry.claims.length === 1)
      .map((entry) => entry.path),
  );
  const startupEnvironmentPolicyOwnedAndPresent =
    singleClaimedPaths.has(startupEnvironmentImplementationPath) &&
    singleClaimedPaths.has(startupEnvironmentUnitTestPath);
  const report = {
    schema: REPORT_SCHEMA,
    policy: {
      schema: policy.schema,
      version: policy.version,
      id: policy.id,
      path: normalizePath(path.relative(repoRoot, policyPath)),
      sha256: sha256(policyBytes),
    },
    repository: {
      role: policy.repositoryRole,
      worktreeDirty: identity.worktreeDirty,
      gitHeadUsedForDecision: false,
      scannedTrackedAndUntrackedNonIgnoredFiles: files.length,
    },
    decision: {
      status: passed ? "boundary_passed" : "blocked",
      gatePassed: passed,
      migrationState: (policy.externalMigrationBacklog || []).length > 0 ? "in_progress" : "complete",
      ownershipExitReady: passed && (policy.externalMigrationBacklog || []).length === 0,
      typescriptEntryStrippingOwnedAndPresent,
      typescriptPreloadedSourceStrippingOwnedAndPresent: typescriptEntryStrippingOwnedAndPresent,
      startupEnvironmentPolicyOwnedAndPresent,
      evidenceLevel: policy.evidenceLevel,
      deviceOrSoakExecuted: false,
    },
    summary: {
      candidatePaths: evaluation.candidateCount,
      classifiedCandidatePaths: evaluation.classifiedCandidateCount,
      violations: evaluation.violations.length,
      surfaces: (policy.surfaces || []).length,
      boundedPathGroups: (policy.boundedPathGroups || []).length,
      externalMigrationBacklogItems: (policy.externalMigrationBacklog || []).length,
      selfTests: selfTests.length,
    },
    declaredAuthorities: policy.declaredAuthorities,
    authorityEvidence: {
      typescriptEntryStripping: {
        authority: "typescript_stripping",
        owner: "plugin_runtime",
        implementationPaths: typescriptImplementation?.present || [],
        unitTestPaths: typescriptUnitTest?.present || [],
        coveredSourceKinds: ["entry_source", "module_sources", "runtime_module_sources"],
        ownedAndPresent: typescriptEntryStrippingOwnedAndPresent,
      },
      startupEnvironmentPolicy: {
        authority: "startup_environment_policy",
        owner: "plugin_runtime",
        implementationPaths: startupEnvironmentPolicyOwnedAndPresent
          ? [startupEnvironmentImplementationPath]
          : [],
        unitTestPaths: startupEnvironmentPolicyOwnedAndPresent
          ? [startupEnvironmentUnitTestPath]
          : [],
        ownedAndPresent: startupEnvironmentPolicyOwnedAndPresent,
      },
      x3iZlibAndroidConformance: {
        authority: "zlib_android_conformance",
        owner: "plugin_tests",
        ...x3iEvidence,
      },
      x3jCryptoAndroidConformance: {
        authority: "crypto_android_conformance",
        owner: "plugin_tests",
        ...x3jEvidence,
      },
    },
    treeInventories: evaluation.treeInventories,
    boundedPathGroups: evaluation.boundedGroups,
    normalGateWiring,
    entries: evaluation.entries,
    externalMigrationBacklog: policy.externalMigrationBacklog || [],
    stagedPluginOnlySlices: policy.stagedPluginOnlySlices || [],
    selfTests,
    violations: evaluation.violations,
  };
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Node runtime ownership candidates: ${evaluation.candidateCount}`);
  console.log(`Node runtime ownership classified: ${evaluation.classifiedCandidateCount}`);
  console.log(`Node runtime ownership self-tests: ${selfTests.length}`);
  console.log(`Node runtime ownership report: ${reportPath}`);
  if (!passed) {
    for (const entry of evaluation.violations) {
      console.error(`[${entry.code}]${entry.path ? ` ${entry.path}` : ""}: ${entry.detail}`);
    }
    process.exitCode = 1;
  }
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error(error && error.stack ? error.stack : String(error));
    process.exitCode = 1;
  }
}

module.exports = {
  collectClaims,
  discoverCandidates,
  evaluate,
  inspectNormalGateWiring,
  pathInventorySha256,
  runSelfTests,
  validatePolicy,
};

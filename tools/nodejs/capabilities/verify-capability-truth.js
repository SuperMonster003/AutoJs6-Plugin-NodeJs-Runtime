#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const EXPECTED_SCHEMA = "autojs6-node-capability-catalog-v1";
const EXPECTED_VERSION = "1.1.1";
const LOCK_SCHEMA = "autojs6-node-capability-catalog-lock-v1";
const SOURCE_REPOSITORY = "AutoJs6-Plugin-NodeJs-Runtime";
const SOURCE_FILE = "app/src/main/assets/nodejs/node-capability-catalog.json";
const RELEASE_ROOT = "releases/nodejs-capability-catalog/1.1.1";
const RELEASE_FILE = "node-capability-catalog.json";
const LOCK_FILE = "node-capability-catalog.lock";

function fail(message) {
  throw new Error(message);
}

function check(condition, message) {
  if (!condition) fail(message);
}

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function slash(value) {
  return value.replace(/\\/g, "/");
}

function compareText(left, right) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function sorted(values, key = (value) => value) {
  return values.slice().sort((left, right) => compareText(key(left), key(right)));
}

function assertSorted(values, label, key = (value) => value) {
  const actual = values.map(key);
  const expected = actual.slice().sort(compareText);
  check(JSON.stringify(actual) === JSON.stringify(expected), `${label} must be stably sorted`);
}

function assertUnique(values, label) {
  const seen = new Set();
  for (const value of values) {
    check(!seen.has(value), `${label} contains duplicate '${value}'`);
    seen.add(value);
  }
}

function sameSet(actual, expected, label) {
  const left = sorted(actual);
  const right = sorted(expected);
  check(JSON.stringify(left) === JSON.stringify(right), `${label} drift: expected ${JSON.stringify(right)}, actual ${JSON.stringify(left)}`);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function parseArgs(argv) {
  const values = Object.create(null);
  for (let index = 0; index < argv.length; index += 1) {
    const item = argv[index];
    if (!item.startsWith("--")) fail(`Unexpected argument '${item}'`);
    const name = item.slice(2);
    if (name === "self-test" || name === "stage-release" || name === "verify-release") {
      values[name] = true;
    } else {
      const next = argv[index + 1];
      check(next && !next.startsWith("--"), `Missing value for --${name}`);
      values[name] = next;
      index += 1;
    }
  }
  return values;
}

function readCatalog(catalogPath) {
  const bytes = fs.readFileSync(catalogPath);
  let catalog;
  try {
    catalog = JSON.parse(bytes.toString("utf8"));
  } catch (error) {
    fail(`Catalog is not valid JSON: ${error.message}`);
  }
  const canonicalBytes = Buffer.from(`${JSON.stringify(catalog, null, 2)}\n`, "utf8");
  check(bytes.equals(canonicalBytes), "Catalog must use deterministic two-space JSON with one trailing LF");
  return {catalog, bytes, digest: sha256(bytes)};
}

function validateCatalog(catalog) {
  check(catalog && typeof catalog === "object" && !Array.isArray(catalog), "Catalog root must be an object");
  check(catalog.schema === EXPECTED_SCHEMA, `Unsupported catalog schema '${catalog.schema}'`);
  check(catalog.catalogVersion === EXPECTED_VERSION, `Unsupported catalog version '${catalog.catalogVersion}'`);
  sameSet(Object.keys(catalog), ["schema", "catalogVersion", "runtime", "budgets", "routing", "features", "builtins", "transport", "lifecycle", "bridge", "errors", "diagnostics"], "catalog top-level keys");

  check(catalog.runtime.pluginId === "nodejs", "runtime.pluginId drift");
  check(catalog.runtime.engine === "nodejs", "runtime.engine drift");
  check(catalog.runtime.runtimeSlot === "node24_5", "runtime.runtimeSlot drift");
  check(catalog.runtime.nodeVersion === "24.5.0", "runtime.nodeVersion drift");
  check(catalog.runtime.profileVersion === "autojs6-node-profile-v1.1", "runtime.profileVersion drift");
  check(catalog.runtime.contract.min === 1 && catalog.runtime.contract.max === 1, "runtime contract range drift");
  assertSorted(catalog.runtime.supportedAbis, "runtime.supportedAbis");
  assertUnique(catalog.runtime.supportedAbis, "runtime.supportedAbis");

  for (const [key, value] of Object.entries(catalog.budgets)) {
    check(Number.isSafeInteger(value) && value > 0, `budgets.${key} must be a positive integer`);
  }
  for (const key of ["backendSwitches", "commonJsModuleExtensions", "commonJsRecursiveSourceExtensions", "nodeRouteFileSuffixes", "typeScriptModuleExtensions", "unsupportedTypeScriptModuleExtensions"]) {
    assertSorted(catalog.routing[key], `routing.${key}`);
    assertUnique(catalog.routing[key], `routing.${key}`);
  }
  sameSet(
    catalog.routing.backendSwitches,
    ["auto", "embedded", "native", "plugin", "rhino"],
    "routing.backendSwitches"
  );
  sameSet(catalog.routing.packageJsonExportConditionsPriority, ["require", "node", "default"], "routing.packageJsonExportConditionsPriority");

  check(Array.isArray(catalog.features) && catalog.features.length === 13, "features must contain exactly 13 entries");
  assertSorted(catalog.features, "features", (item) => item.id);
  assertUnique(catalog.features.map((item) => item.id), "feature ids");
  for (const item of catalog.features) {
    check(/^[a-z][a-z0-9_]*$/.test(item.id), `Invalid feature id '${item.id}'`);
    check(typeof item.defaultEnabled === "boolean", `Feature ${item.id} defaultEnabled must be boolean`);
    check(["partial", "experimental", "disabled", "unsupported"].includes(item.status), `Feature ${item.id} has invalid status`);
    check(["medium", "high", "critical"].includes(item.securityLevel), `Feature ${item.id} has invalid security level`);
    sameSet(Object.keys(item.overrides), ["buildTime", "projectJson", "packagedApk", "releaseEnableAllowed"], `Feature ${item.id} override keys`);
  }
  const featuresById = Object.fromEntries(catalog.features.map((item) => [item.id, item]));
  check(featuresById.esm.defaultEnabled === true && featuresById.esm.status === "partial", "ESM must be partial and default-on");
  check(featuresById.dynamic_import.defaultEnabled === true, "Local dynamic import must be default-on");
  check(featuresById.dynamic_import.requestFallbackEnabled === true, "Dynamic import request fallback must be true");
  check(featuresById.dynamic_import.legacyExperimentalDefaultEnabled === false, "Legacy experimental dynamic import property must remain default-off");

  for (const key of ["commonJsRecognized", "disabled", "disabledProcessApis", "limited", "network"]) {
    assertSorted(catalog.builtins[key], `builtins.${key}`);
    assertUnique(catalog.builtins[key], `builtins.${key}`);
  }
  assertSorted(catalog.builtins.directAliases, "builtins.directAliases", (item) => item.alias);
  assertUnique(catalog.builtins.directAliases.map((item) => item.alias), "builtin aliases");

  assertSorted(catalog.transport.runtimeServiceCapabilities, "transport.runtimeServiceCapabilities");
  assertUnique(catalog.transport.runtimeServiceCapabilities, "transport.runtimeServiceCapabilities");
  check(
    catalog.transport.runtimeServiceCapabilities.includes("hostPlaintextModuleSourceMaterialization"),
    "Runtime transport must declare Host plaintext module-source materialization"
  );
  check(catalog.transport.output.mode === "buffered" && catalog.transport.output.streaming === false, "Runtime output must be buffered and non-streaming");
  check(catalog.transport.output.terminalEventCount === 1, "Runtime transport must have one terminal event");

  check(catalog.lifecycle.processModel === "persistent", "Runtime process must be persistent");
  check(catalog.lifecycle.defaultExecutionMode === "one_shot", "Default execution mode must be one_shot");
  check(catalog.lifecycle.admission.maxConcurrentExecutions === 1, "Runtime admission must be single-active");
  check(catalog.lifecycle.admission.queueCapacity === 0, "Runtime admission queue must be zero");
  check(catalog.lifecycle.cancellation.mode === "process_restart", "Cancellation mode must be process_restart");
  check(catalog.lifecycle.cancellation.automaticRetryAfterDispatch === false, "Retry after dispatch must remain disabled");
  assertSorted(catalog.lifecycle.executionModes, "lifecycle.executionModes", (item) => item.id);
  assertUnique(catalog.lifecycle.executionModes.map((item) => item.id), "execution mode ids");
  const executionModeIds = new Set(catalog.lifecycle.executionModes.map((item) => item.id));
  const longRunning = catalog.lifecycle.executionModes.find((item) => item.id === "interactive_long_running");
  check(longRunning && longRunning.longRunning === true, "interactive_long_running mode is missing");
  sameSet(longRunning.launchSurfaces, ["interactive_session", "packaged_long_running"], "interactive long-running launch surfaces");
  assertSorted(catalog.lifecycle.metadataAliases, "lifecycle.metadataAliases", (item) => item.alias);
  for (const alias of catalog.lifecycle.metadataAliases) check(executionModeIds.has(alias.target), `Unknown lifecycle alias target '${alias.target}'`);

  const permissionIds = catalog.bridge.permissionCapabilities.map((item) => item.id);
  check(JSON.stringify(catalog.bridge.androidPermissionOrder) === JSON.stringify([
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECORD_AUDIO",
    "android.permission.INTERNET",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE"
  ]), "bridge.androidPermissionOrder drift");
  check(permissionIds.length === 47, "bridge.permissionCapabilities must contain exactly 47 entries");
  assertSorted(catalog.bridge.permissionCapabilities, "bridge.permissionCapabilities", (item) => item.id);
  assertUnique(permissionIds, "permission capability ids");
  const permissionIdSet = new Set(permissionIds);
  const permissionAliases = [];
  for (const permission of catalog.bridge.permissionCapabilities) {
    assertSorted(permission.aliases, `permission ${permission.id} aliases`);
    assertUnique(permission.aliases, `permission ${permission.id} aliases`);
    assertSorted(permission.androidPermissions, `permission ${permission.id} Android permissions`);
    for (const alias of permission.aliases) permissionAliases.push(alias);
    for (const androidPermission of permission.androidPermissions) check(/^android\.permission\.[A-Z0-9_]+$/.test(androidPermission), `Invalid Android permission '${androidPermission}'`);
  }
  check(permissionAliases.length === 82, "permission aliases must contain exactly 82 entries");
  assertUnique(permissionAliases, "permission aliases");

  assertSorted(catalog.bridge.modules, "bridge.modules", (item) => item.id);
  assertUnique(catalog.bridge.modules.map((item) => item.id), "bridge module ids");
  const moduleIds = new Set(catalog.bridge.modules.map((item) => item.id));
  const moduleAliases = [];
  for (const module of catalog.bridge.modules) {
    const expectedOwner = module.id === "lifecycle" ? "host_control_plane" : "host_capability_provider";
    check(module.providerOwner === expectedOwner, `Module ${module.id} providerOwner must be ${expectedOwner}`);
    assertSorted(module.aliases, `module ${module.id} aliases`);
    for (const alias of module.aliases) moduleAliases.push(alias);
  }
  assertUnique(moduleAliases, "bridge module aliases");
  const operationKeys = catalog.bridge.operations.map((item) => `${item.module}\u0000${item.method}`);
  assertSorted(catalog.bridge.operations, "bridge.operations", (item) => `${item.module}\u0000${item.method}`);
  assertUnique(operationKeys, "bridge operations");
  for (const operation of catalog.bridge.operations) {
    check(moduleIds.has(operation.module), `Operation references unknown module '${operation.module}'`);
    check(Array.isArray(operation.requiredCapabilities), `Operation ${operation.module}.${operation.method} requiredCapabilities must be an array`);
    assertSorted(operation.requiredCapabilities, `operation ${operation.module}.${operation.method} capabilities`);
    assertUnique(operation.requiredCapabilities, `operation ${operation.module}.${operation.method} capabilities`);
    for (const capability of operation.requiredCapabilities) check(permissionIdSet.has(capability), `Operation ${operation.module}.${operation.method} references unknown capability '${capability}'`);
    if (Object.prototype.hasOwnProperty.call(operation, "conditionalCapabilities")) {
      check(Array.isArray(operation.conditionalCapabilities), `Operation ${operation.module}.${operation.method} conditionalCapabilities must be an array`);
    }
    const conditionalKeys = [];
    for (const conditional of operation.conditionalCapabilities || []) {
      check(conditional && typeof conditional === "object" && !Array.isArray(conditional), `Operation ${operation.module}.${operation.method} conditional capability must be an object`);
      sameSet(Object.keys(conditional), ["when", "requiredCapability"], `operation ${operation.module}.${operation.method} conditional capability keys`);
      check(typeof conditional.when === "string" && /^options\.[A-Za-z][A-Za-z0-9_]*=(?:true|false)$/.test(conditional.when), `Operation ${operation.module}.${operation.method} has unsupported conditional expression '${conditional.when}'`);
      check(typeof conditional.requiredCapability === "string" && permissionIdSet.has(conditional.requiredCapability), `Operation ${operation.module}.${operation.method} references unknown conditional capability '${conditional.requiredCapability}'`);
      conditionalKeys.push(`${conditional.when}\u0000${conditional.requiredCapability}`);
    }
    assertSorted(conditionalKeys, `operation ${operation.module}.${operation.method} conditional capabilities`);
    assertUnique(conditionalKeys, `operation ${operation.module}.${operation.method} conditional capabilities`);
    if (Object.prototype.hasOwnProperty.call(operation, "executionModes")) {
      check(Array.isArray(operation.executionModes), `Operation ${operation.module}.${operation.method} executionModes must be an array`);
    }
    assertSorted(operation.executionModes || [], `operation ${operation.module}.${operation.method} execution modes`);
    assertUnique(operation.executionModes || [], `operation ${operation.module}.${operation.method} execution modes`);
    for (const mode of operation.executionModes || []) check(executionModeIds.has(mode), `Operation ${operation.module}.${operation.method} references unknown execution mode '${mode}'`);
  }

  check(catalog.errors.constants.length === 116, "errors.constants must contain exactly 116 entries");
  assertSorted(catalog.errors.constants, "errors.constants", (item) => item.name);
  assertUnique(catalog.errors.constants.map((item) => item.name), "error constant names");
  assertUnique(catalog.errors.constants.map((item) => item.code), "error codes");
  const errorCodes = new Set(catalog.errors.constants.map((item) => item.code));
  for (const item of catalog.errors.constants) check(/^ERR_[A-Z0-9_]+$/.test(item.code), `Invalid error code '${item.code}'`);
  assertSorted(catalog.errors.legacyAliases, "errors.legacyAliases", (item) => item.code);
  for (const alias of catalog.errors.legacyAliases) {
    check(errorCodes.has(alias.code), `Unknown legacy error '${alias.code}'`);
    check(errorCodes.has(alias.canonicalCode), `Unknown canonical error '${alias.canonicalCode}'`);
    check(alias.code !== alias.canonicalCode, `Legacy error '${alias.code}' aliases itself`);
  }

  sameSet(Object.values(catalog.diagnostics.bundleKeys), ["nodeCapabilityCatalogSchema", "nodeCapabilityCatalogVersion", "nodeCapabilityCatalogSha256"], "diagnostic bundle keys");
  return {featuresById, permissionIdSet, moduleIds, executionModeIds, errorCodes};
}

function javaStringConstants(source) {
  return Object.fromEntries([...source.matchAll(/(?:public|private)\s+static\s+final\s+String\s+([A-Z][A-Z0-9_]*)\s*=\s*(?:\r?\n\s*)?"([^"]+)";/g)].map((match) => [match[1], match[2]]));
}

function kotlinStringConstants(source) {
  return Object.fromEntries([...source.matchAll(/const val\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"/g)].map((match) => [match[1], match[2]]));
}

function javaStringArray(source, name) {
  const match = source.match(new RegExp(`${escapeRegex(name)}\\s*=\\s*(?:new\\s+String\\[\\]\\s*)?\\{([^}]*)}\\s*;`));
  check(match, `Java String array ${name} is missing`);
  const values = [...match[1].matchAll(/"([^"]+)"/g)].map((item) => item[1]);
  check(values.length > 0, `Java String array ${name} must not be empty`);
  return values;
}

function verifyPermissionRegistry(catalog, source) {
  const constants = javaStringConstants(source);
  const block = source.match(/DEFINED_CAPABILITIES\s*=\s*immutableList\(([\s\S]*?)\n\s*\);/);
  check(block, "Permission registry DEFINED_CAPABILITIES block is missing");
  const defined = [...block[1].matchAll(/\b([A-Z][A-Z0-9_]*)\b/g)].map((match) => constants[match[1]]);
  sameSet(defined, catalog.bridge.permissionCapabilities.map((item) => item.id), "permission capability registry");

  const aliasesBlock = source.match(/private static Map<String, String> aliases\(\) \{([\s\S]*?)return Collections\.unmodifiableMap\(map\);/);
  check(aliasesBlock, "Permission alias registry is missing");
  const actualAliases = [...aliasesBlock[1].matchAll(/map\.put\("([^"]+)",\s*([A-Z][A-Z0-9_]*)\);/g)]
    .map((match) => `${match[1]}\u0000${constants[match[2]]}`);
  const catalogAliases = catalog.bridge.permissionCapabilities.flatMap((item) => item.aliases.map((alias) => `${alias}\u0000${item.id}`));
  sameSet(actualAliases, catalogAliases, "permission alias registry");

  const androidBlock = source.match(/private static Map<String, List<String>> androidPermissionsByCapability\(\) \{([\s\S]*?)return Collections\.unmodifiableMap\(map\);/);
  check(androidBlock, "Android permission registry is missing");
  const actualAndroid = [];
  for (const match of androidBlock[1].matchAll(/map\.put\(([A-Z][A-Z0-9_]*),\s*immutableList\(([\s\S]*?)\)\s*\);/g)) {
    const capability = constants[match[1]];
    for (const token of match[2].matchAll(/\b([A-Z][A-Z0-9_]*)\b/g)) actualAndroid.push(`${capability}\u0000${constants[token[1]]}`);
  }
  const catalogAndroid = catalog.bridge.permissionCapabilities.flatMap((item) => item.androidPermissions.map((permission) => `${item.id}\u0000${permission}`));
  sameSet(actualAndroid, catalogAndroid, "Android permission registry");
}

function verifyServiceCapabilities(catalog, service, infoService, contract, pluginIds, capabilityKeys) {
  const constants = {...javaStringConstants(service)};
  for (const match of contract.matchAll(/const val\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"/g)) constants[match[1]] = match[2];
  const ids = kotlinStringConstants(pluginIds);
  const infoKeys = kotlinStringConstants(capabilityKeys);
  check(ids.ID === catalog.runtime.pluginId, `Plugin info id drift: expected '${catalog.runtime.pluginId}', actual '${ids.ID}'`);
  check(ids.ENGINE === catalog.runtime.engine, `Plugin info engine drift: expected '${catalog.runtime.engine}', actual '${ids.ENGINE}'`);
  check(ids.VARIANT_NODE_24_5 === catalog.runtime.runtimeSlot, `Plugin runtime slot id drift: expected '${catalog.runtime.runtimeSlot}', actual '${ids.VARIANT_NODE_24_5}'`);
  check(constants.NODE_VERSION === catalog.runtime.nodeVersion, `Runtime service Node version drift: expected '${catalog.runtime.nodeVersion}', actual '${constants.NODE_VERSION}'`);
  check(constants.NATIVE_LIBRARY_NAME === catalog.runtime.nativeLibraryName, `Runtime service native library drift: expected '${catalog.runtime.nativeLibraryName}', actual '${constants.NATIVE_LIBRARY_NAME}'`);
  check(constants.BRIDGE_LIBRARY_NAME === catalog.runtime.bridgeLibraryName, `Runtime service bridge library drift: expected '${catalog.runtime.bridgeLibraryName}', actual '${constants.BRIDGE_LIBRARY_NAME}'`);
  check(
    /const val\s+MODULE_SOURCE_PROVIDER_CONTRACT_VERSION\s*=\s*2\b/.test(contract) &&
      /const val\s+MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION\s*=\s*2\b/.test(contract) &&
      /const val\s+MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION\s*=\s*2\b/.test(contract),
    "Node.js API module-source provider contract must be exact v2"
  );
  check(
    constants.KEY_MODULE_SOURCE_PROVIDER_OPERATION === "moduleSourceProviderOperation" &&
      constants.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS ===
        "moduleSourceProviderDeadlineElapsedRealtimeMs" &&
      constants.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING === "resolve_existing" &&
      constants.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT ===
        "materialize_missing_plaintext" &&
      constants.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT === "plaintext" &&
      constants.CAPABILITY_HOST_PLAINTEXT_MODULE_SOURCE_MATERIALIZATION ===
        "hostPlaintextModuleSourceMaterialization",
    "Node.js API provider-v2 materialization constants drifted"
  );
  const runtimeServiceAbis = javaStringArray(service, "SUPPORTED_ABIS");
  check(JSON.stringify(runtimeServiceAbis) === JSON.stringify(catalog.runtime.supportedAbis), `Runtime service supported ABIs drift: expected ${JSON.stringify(catalog.runtime.supportedAbis)}, actual ${JSON.stringify(runtimeServiceAbis)}`);
  const infoServiceAbis = javaStringArray(infoService, "SUPPORTED_ABIS");
  check(JSON.stringify(infoServiceAbis) === JSON.stringify(catalog.runtime.supportedAbis), `Plugin info service supported ABIs drift: expected ${JSON.stringify(catalog.runtime.supportedAbis)}, actual ${JSON.stringify(infoServiceAbis)}`);

  check(service.includes("info.putString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_5);"), "Runtime service runtimeSlot assignment drift");
  check(service.includes("info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_VERSION);"), "Runtime service nodeVersion assignment drift");
  check(service.includes("info.putString(NodeJsRuntimeContract.KEY_NATIVE_LIBRARY_NAME, NATIVE_LIBRARY_NAME);"), "Runtime service nativeLibraryName assignment drift");
  check(service.includes("info.putString(NodeJsRuntimeContract.KEY_BRIDGE_LIBRARY_NAME, BRIDGE_LIBRARY_NAME);"), "Runtime service bridgeLibraryName assignment drift");
  check(
    service.includes(
      "info.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, " +
        "NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);"
    ),
    "Runtime service module-source provider-v2 assignment drift"
  );
  check(
    service.includes(
      "IBinder requestedModuleSourceProvider = request.getBinder(\n" +
        "                PluginModuleSourceProviderFileTransportSession.KEY_PROVIDER_BINDER\n" +
        "        );"
    ) && service.includes("if (requestedModuleSourceProvider == null) {") &&
      service.includes(
        "Object rawModuleSourceProviderVersion = request.get(\n" +
          "                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION\n" +
          "        );"
      ) &&
      service.includes("return rawValue instanceof Integer ? (Integer) rawValue : -1;") &&
      service.includes(
        "NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(\n" +
          "                receivedModuleSourceProviderVersion\n" +
          "        )"
      ),
    "Runtime service must raw-strictly reject mixed module-source provider contract pairs"
  );
  const requestContractValidation = service.indexOf(
    "Bundle contractFailure = validateRequestContract(normalizedRequest, startedAt);"
  );
  const runtimeAdmission = service.indexOf("executionGate.tryAcquire(executionId);");
  check(
    requestContractValidation >= 0 && runtimeAdmission > requestContractValidation,
    "Runtime request contract validation must precede workspace/native admission"
  );
  const predispatchReceiptStart = service.indexOf(
    "private static void appendPredispatchNoCommitReceipt(Bundle failure)"
  );
  const predispatchReceiptEnd = service.indexOf("private static String messageOf(", predispatchReceiptStart);
  const predispatchReceipt = service.slice(predispatchReceiptStart, predispatchReceiptEnd);
  check(
    predispatchReceiptStart >= 0 && predispatchReceiptEnd > predispatchReceiptStart &&
      (service.match(/appendPredispatchNoCommitReceipt\(failure\);/g) || []).length === 2 &&
      predispatchReceipt.includes("embedded_script.runtime_plugin.native_dispatch_started=false") &&
      predispatchReceipt.includes("embedded_script.runtime_plugin.workspace.commit_allowed=false") &&
      predispatchReceipt.includes("embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false"),
    "Runtime service readiness and empty-source failures must publish an explicit predispatch no-commit receipt"
  );
  check(service.includes('info.putStringArray("supportedAbis", SUPPORTED_ABIS.clone());'), "Runtime service supportedAbis assignment drift");
  check(service.includes("System.loadLibrary(NATIVE_LIBRARY_NAME);") && service.includes("System.loadLibrary(BRIDGE_LIBRARY_NAME);"), "Runtime service library loading must use catalog-checked constants");

  check(infoKeys.NODE_VERSION === "nodeVersion" && infoKeys.RUNTIME_SLOT === "runtimeSlot" && infoKeys.NATIVE_LIBRARY_NAME === "nativeLibraryName", "Plugin info capability metadata keys drift");
  check(new RegExp(`capabilities\\.putString\\(NodeJsPluginCapabilityKeys\\.NODE_VERSION,\\s*"${escapeRegex(catalog.runtime.nodeVersion)}"\\)`).test(infoService), "Plugin info service nodeVersion assignment drift");
  check(/capabilities\.putString\(NodeJsPluginCapabilityKeys\.RUNTIME_SLOT,\s*NodeJsPluginIds\.VARIANT_NODE_24_5\)/.test(infoService), "Plugin info service runtimeSlot assignment drift");
  check(new RegExp(`capabilities\\.putString\\(NodeJsPluginCapabilityKeys\\.NATIVE_LIBRARY_NAME,\\s*"${escapeRegex(catalog.runtime.nativeLibraryName)}"\\)`).test(infoService), "Plugin info service nativeLibraryName assignment drift");
  check(/NodeJsPluginIds\.ID,\s*NodeJsPluginIds\.ENGINE,\s*NodeJsPluginIds\.VARIANT_NODE_24_5,\s*SUPPORTED_ABIS/s.test(infoService), "PluginInfo identity/runtimeSlot/supportedAbis constructor mapping drift");

  const block = service.match(/CAPABILITIES\s*=\s*new String\[\]\s*\{([\s\S]*?)\n\s*};/);
  check(block, "Runtime service CAPABILITIES block is missing");
  const actual = [...block[1].matchAll(/(?:NodeJsRuntimeContract\.)?([A-Z][A-Z0-9_]*)/g)].map((match) => constants[match[1]]).filter(Boolean);
  sameSet(actual, catalog.transport.runtimeServiceCapabilities, "runtime service capabilities");
  const expectedDefaults = [
    ["KEY_ESM_EXPERIMENTAL_ENABLED", true],
    ["KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED", true],
    ["KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED", false],
    ["KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED", false],
    ["KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED", false],
    ["KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED", false]
  ];
  for (const [key, value] of expectedDefaults) {
    const pattern = new RegExp(`request\\.getBoolean\\(NodeJsRuntimeContract\\.${key},\\s*${value}\\)`);
    check(pattern.test(service), `Runtime service fallback for ${key} must be ${value}`);
  }
  check(service.includes(`info.putString("processModel", "${catalog.lifecycle.processModel}")`), "Runtime service process model drift");
  check(
    new RegExp(`info\\.putInt\\("maxConcurrentExecutions",\\s*${catalog.lifecycle.admission.maxConcurrentExecutions}\\)`).test(service),
    "Runtime service max concurrency drift"
  );
  check(
    new RegExp(`info\\.putInt\\("queueCapacity",\\s*${catalog.lifecycle.admission.queueCapacity}\\)`).test(service),
    "Runtime service queue capacity drift"
  );
  check(/info\.putBoolean\("persistentProcessRuntime",\s*true\)/.test(service), "Runtime service persistent-process token drift");
  check(
    service.includes(`info.putBoolean("dedicatedRuntimeProcess", isDedicatedRuntimeProcess())`) && catalog.lifecycle.dedicatedProcess,
    "Runtime service dedicated-process token drift"
  );
  check(
    service.includes(`info.putBoolean("isolatePerExecution", ${catalog.lifecycle.isolatePerExecution})`),
    "Runtime service execution-isolation token drift"
  );
  check(
    service.includes(`info.putString("defaultExecutionMode", "${catalog.lifecycle.defaultExecutionMode}")`),
    "Runtime service default execution mode drift"
  );
  check(service.includes('CANCELLATION_STRATEGY_PROCESS_RESTART = "process_restart"'), "Runtime service cancellation mode drift");
  check(
    /info\.putString\(\s*"cancellationMode",\s*CANCELLATION_STRATEGY_PROCESS_RESTART\s*\);/.test(service),
    "Runtime service cancellation-mode assignment drift"
  );
  check(
    service.includes(`info.putString("outputMode", "${catalog.transport.output.mode}")`),
    "Runtime service output mode drift"
  );
  check(
    service.includes(`info.putBoolean("streamingOutput", ${catalog.transport.output.streaming})`),
    "Runtime service streaming-output token drift"
  );
  check(
    service.includes(`info.putInt("terminalEventCount", ${catalog.transport.output.terminalEventCount})`),
    "Runtime service terminal-event count drift"
  );
}

function verifyRuntimeCatalogMetadataAssignments(catalog, service, digest) {
  const constants = javaStringConstants(service);
  const expectedConstants = {
    NODE_CAPABILITY_CATALOG_SCHEMA: catalog.schema,
    NODE_CAPABILITY_CATALOG_VERSION: catalog.catalogVersion,
    NODE_CAPABILITY_CATALOG_SHA256: digest,
    KEY_NODE_CAPABILITY_CATALOG_SCHEMA: catalog.diagnostics.bundleKeys.schema,
    KEY_NODE_CAPABILITY_CATALOG_VERSION: catalog.diagnostics.bundleKeys.version,
    KEY_NODE_CAPABILITY_CATALOG_SHA256: catalog.diagnostics.bundleKeys.sha256,
    DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA: catalog.diagnostics.nativePayloadKeys.schema,
    DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION: catalog.diagnostics.nativePayloadKeys.version,
    DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256: catalog.diagnostics.nativePayloadKeys.sha256
  };
  for (const [name, expected] of Object.entries(expectedConstants)) {
    check(constants[name] === expected, `Runtime service ${name} drift: expected '${expected}', actual '${constants[name]}'`);
  }

  const runtimeInfoAssignments = [
    ["SCHEMA", "SCHEMA"],
    ["VERSION", "VERSION"],
    ["SHA256", "SHA256"]
  ];
  for (const [keySuffix, valueSuffix] of runtimeInfoAssignments) {
    const pattern = new RegExp(
      `info\\.putString\\(\\s*KEY_NODE_CAPABILITY_CATALOG_${keySuffix},\\s*NODE_CAPABILITY_CATALOG_${valueSuffix}\\s*\\);`
    );
    check(pattern.test(service), `Runtime-info capability catalog ${keySuffix.toLowerCase()} assignment drift`);
  }

  const nativeDiagnosticAssignments = [
    ["SCHEMA", "SCHEMA"],
    ["VERSION", "VERSION"],
    ["SHA256", "SHA256"]
  ];
  for (const [keySuffix, valueSuffix] of nativeDiagnosticAssignments) {
    const pattern = new RegExp(
      `values\\.put\\(\\s*DIAGNOSTIC_NODE_CAPABILITY_CATALOG_${keySuffix},\\s*NODE_CAPABILITY_CATALOG_${valueSuffix}\\s*\\);`
    );
    check(pattern.test(service), `Native diagnostic capability catalog ${keySuffix.toLowerCase()} assignment drift`);
  }
}

function cppDefaultPermissionResolver(cpp) {
  const match = cpp.match(/(function __autojs6_bridge_default_permissions\(moduleName, methodName\) \{[\s\S]*?\r?\n  \})\r?\n  function __autojs6_bridge_effective_permissions/);
  check(match, "C++ bridge default permission resolver is missing");
  return Function(`"use strict"; ${match[1]}; return __autojs6_bridge_default_permissions;`)();
}

function verifyConditionalCppPermissions(catalog, cpp) {
  for (const operation of catalog.bridge.operations) {
    const grouped = new Map();
    for (const conditional of operation.conditionalCapabilities || []) {
      const values = grouped.get(conditional.when) || [];
      values.push(conditional.requiredCapability);
      grouped.set(conditional.when, values);
    }
    for (const [condition, conditionalCapabilities] of grouped) {
      const parsed = /^options\.([A-Za-z][A-Za-z0-9_]*)=(true|false)$/.exec(condition);
      check(parsed, `Unsupported conditional bridge permission expression '${condition}'`);
      const property = escapeRegex(parsed[1]);
      const moduleName = escapeRegex(operation.module);
      const methodName = escapeRegex(operation.method);
      const pattern = new RegExp(
        `const\\s+permissions\\s*=\\s*[A-Za-z_$][A-Za-z0-9_$]*\\.${property}\\s*\\?\\s*(\\[[^\\]]*])\\s*:\\s*(\\[[^\\]]*])\\s*;[\\s\\S]{0,600}?__autojs6_call_autojs\\(\\s*"${moduleName}"\\s*,\\s*"${methodName}"`,
      );
      const mapping = cpp.match(pattern);
      check(mapping, `C++ conditional permission mapping for ${operation.module}.${operation.method} (${condition}) is missing`);
      let truePermissions;
      let falsePermissions;
      try {
        truePermissions = JSON.parse(mapping[1]);
        falsePermissions = JSON.parse(mapping[2]);
      } catch (error) {
        fail(`C++ conditional permission arrays for ${operation.module}.${operation.method} are not JSON string arrays: ${error.message}`);
      }
      check(Array.isArray(truePermissions) && truePermissions.every((value) => typeof value === "string"), `C++ true-branch permissions for ${operation.module}.${operation.method} must be a string array`);
      check(Array.isArray(falsePermissions) && falsePermissions.every((value) => typeof value === "string"), `C++ false-branch permissions for ${operation.module}.${operation.method} must be a string array`);
      const expectedWhenMatched = [...operation.requiredCapabilities, ...conditionalCapabilities];
      const expectedWhenNotMatched = operation.requiredCapabilities;
      const actualWhenMatched = parsed[2] === "true" ? truePermissions : falsePermissions;
      const actualWhenNotMatched = parsed[2] === "true" ? falsePermissions : truePermissions;
      sameSet(actualWhenMatched, expectedWhenMatched, `C++ conditional permissions for ${operation.module}.${operation.method} when ${condition}`);
      sameSet(actualWhenNotMatched, expectedWhenNotMatched, `C++ conditional permissions for ${operation.module}.${operation.method} when not ${condition}`);
    }
  }
}

function verifyCpp(catalog, cpp, internalHeader) {
  check(cpp.includes(`name: "${catalog.runtime.profileName}"`), "C++ profile name drift");
  check(cpp.includes(`engineVersion: "${catalog.runtime.profileVersion}"`), "C++ profile version drift");
  for (const feature of catalog.features) {
    const marker = `id: "${feature.id}"`;
    const start = cpp.indexOf(marker, cpp.indexOf("const featureFlags"));
    check(start >= 0, `C++ feature '${feature.id}' is missing`);
    const slice = cpp.slice(start, start + 900);
    check(new RegExp(`defaultEnabled:\\s*${feature.defaultEnabled}`).test(slice), `C++ feature '${feature.id}' default drift`);
    check(new RegExp(`status:\\s*"${feature.status}"`).test(slice), `C++ feature '${feature.id}' status drift`);
    if (feature.property) check(slice.includes(`gradleProperty: "${feature.property}"`), `C++ feature '${feature.id}' property drift`);
  }
  check(cpp.includes('dynamicImportDefaultEnabled: true'), "C++ local dynamic import default must be true");
  check(internalHeader.includes("bool dynamicImportExperimentalEnabled = false;"), "Legacy C++ experimental dynamic import fallback must remain false");
  check(cpp.includes('executionMode: "one_shot"'), "C++ lifecycle default must be one_shot");
  check(cpp.includes('restartPolicy: "never"'), "C++ lifecycle restart policy drift");
  const permissionResolver = cppDefaultPermissionResolver(cpp);
  const operationsByModule = new Map();
  for (const operation of catalog.bridge.operations) {
    const actual = permissionResolver(operation.module, operation.method);
    check(Array.isArray(actual) && actual.every((value) => typeof value === "string"), `C++ bridge permission mapping for ${operation.module}.${operation.method} must return a string array`);
    assertUnique(actual, `C++ bridge permission mapping for ${operation.module}.${operation.method}`);
    sameSet(actual, operation.requiredCapabilities, `C++ bridge permission mapping for ${operation.module}.${operation.method}`);
    const methods = operationsByModule.get(operation.module) || [];
    methods.push(operation);
    operationsByModule.set(operation.module, methods);
  }
  for (const module of catalog.bridge.modules) {
    for (const alias of module.aliases) {
      for (const operation of operationsByModule.get(module.id) || []) {
        const actual = permissionResolver(alias, operation.method);
        check(Array.isArray(actual) && actual.every((value) => typeof value === "string"), `C++ bridge permission mapping for alias ${alias}.${operation.method} must return a string array`);
        assertUnique(actual, `C++ bridge permission mapping for alias ${alias}.${operation.method}`);
        sameSet(actual, operation.requiredCapabilities, `C++ bridge permission mapping for alias ${alias}.${operation.method}`);
      }
    }
  }
  verifyConditionalCppPermissions(catalog, cpp);

  const aliasBlock = cpp.match(/const __autojs6_error_code_aliases = Object\.freeze\(\{([\s\S]*?)\n\s*}\);/);
  check(aliasBlock, "C++ legacy error alias registry is missing");
  const actualAliases = [...aliasBlock[1].matchAll(/"(ERR_[A-Z0-9_]+)"\s*:\s*"(ERR_[A-Z0-9_]+)"/g)].map((match) => `${match[1]}\u0000${match[2]}`);
  const expectedAliases = catalog.errors.legacyAliases.map((item) => `${item.code}\u0000${item.canonicalCode}`);
  sameSet(actualAliases, expectedAliases, "C++ legacy error aliases");
}

function walkFiles(root, predicate, output = []) {
  if (!fs.existsSync(root)) return output;
  for (const entry of fs.readdirSync(root, {withFileTypes: true})) {
    const current = path.join(root, entry.name);
    if (entry.isDirectory()) walkFiles(current, predicate, output);
    else if (predicate(current)) output.push(current);
  }
  return output;
}

function verifyRuntimeErrors(projectRoot, errorCodes) {
  const roots = [path.join(projectRoot, "app/src/main/java"), path.join(projectRoot, "app/src/main/cpp")];
  const files = roots.flatMap((root) => walkFiles(root, (file) => /\.(?:java|kt|c|cc|cpp|h|hpp)$/.test(file)));
  const runtimeCodes = new Set();
  for (const file of files) {
    const source = fs.readFileSync(file, "utf8");
    for (const match of source.matchAll(/ERR_AUTOJS6_[A-Z0-9_]+|ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX/g)) runtimeCodes.add(match[0]);
  }
  const unknown = sorted([...runtimeCodes].filter((code) => !errorCodes.has(code)));
  check(unknown.length === 0, `Runtime sources contain uncatalogued errors: ${unknown.join(", ")}`);
  return runtimeCodes.size;
}

function verifyNoStaleNarrative(projectRoot) {
  const roots = [path.join(projectRoot, "README.md"), path.join(projectRoot, ".readme"), path.join(projectRoot, "sample/nodejs")];
  const files = [];
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    if (fs.statSync(root).isFile()) files.push(root);
    else walkFiles(root, (file) => /(?:README\.md|project\.json|lang_[^/\\]+\.json)$/.test(file), files);
  }
  const forbidden = [
    /\bESM (?:is )?(?:disabled|off) by default\b/i,
    /\bdefault[- ]off ESM\b/i,
    /\bNode(?:\.js)? (?:engine|runtime) (?:is )?(?:strictly )?one[- ]shot\b/i,
    /\blong[- ]running (?:mode )?(?:is )?(?:unsupported|unavailable|not supported)\b/i,
    /\bEmbedded Node (?:ESM|dynamic import) is unsupported\b/i
  ];
  const findings = [];
  for (const file of files) {
    const source = fs.readFileSync(file, "utf8");
    for (const pattern of forbidden) if (pattern.test(source)) findings.push(`${slash(path.relative(projectRoot, file))}: ${pattern}`);
  }
  check(findings.length === 0, `Stale Node capability narrative found: ${findings.join("; ")}`);
  return files.length;
}

function verifyTypeScriptEntryWiring(service) {
  const helper = "return NodeTypeScriptStripper.stripIfTypeScript(sourceName, source);";
  check(service.includes(helper), "Plugin TypeScript entry preparation helper is not delegated to the plugin-owned stripper");
  check(
    service.includes("NodeTypeScriptStripper.DIAGNOSTIC_SCOPE_MODULE_SOURCES") &&
      service.includes("NodeTypeScriptStripper.DIAGNOSTIC_SCOPE_RUNTIME_MODULE_SOURCES"),
    "Plugin TypeScript preloaded-source helpers are not delegated with their bounded diagnostic scopes"
  );

  const methodStart = service.indexOf("private Bundle runScriptActive(Bundle request, INodeJsRuntimeCallback callback)");
  const methodEnd = service.indexOf("private Bundle runtimeInfoBundle()", methodStart);
  check(methodStart >= 0 && methodEnd > methodStart, "Plugin runScriptActive source boundary is missing");
  const active = service.slice(methodStart, methodEnd);
  const preparation = active.indexOf("prepareTypeScriptEntryForNative(requestedSourceName, source);");
  const sourceReplacement = active.indexOf("source = typeScriptEntry.source();");
  const moduleNameMapping = active.indexOf("moduleSources = workspaceSession.mapModuleSourceNames(moduleSources);");
  const modulePreparation = active.indexOf("prepareTypeScriptModuleSourcesForNative(moduleSources);");
  const moduleReplacement = active.indexOf("moduleSources = typeScriptModuleSources.sources();");
  const runtimeInjection = active.indexOf("RuntimeModuleInjection runtimeModuleInjection = withPluginRuntimeModules(");
  const runtimePreparation = active.indexOf("prepareTypeScriptRuntimeModuleSourcesForNative(runtimeModuleSources);");
  const runtimeReplacement = active.indexOf("runtimeModuleSources = typeScriptRuntimeModuleSources.sources();");
  const nativeDispatch = active.indexOf("NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(");
  check(preparation >= 0, "Plugin TypeScript entry source is not prepared in runScriptActive");
  check(sourceReplacement > preparation, "Plugin TypeScript entry source replacement is missing or out of order");
  check(moduleNameMapping >= 0, "Plugin preloaded module-source workspace mapping is missing");
  check(modulePreparation >= 0 && modulePreparation < moduleNameMapping, "Plugin preloaded module TypeScript stripping must preserve original request names before workspace mapping");
  check(moduleReplacement > modulePreparation, "Plugin preloaded module TypeScript source replacement is missing or out of order");
  check(moduleNameMapping > moduleReplacement, "Plugin workspace mapping must consume prepared module sources");
  check(runtimeInjection >= 0, "Plugin-generated runtime-module injection boundary is missing");
  check(runtimePreparation > runtimeInjection, "Plugin runtime-module TypeScript stripping must follow plugin runtime-module injection");
  const lastRuntimeModuleAddition = active.lastIndexOf("runtimeModuleSources = withRuntimeModuleSource(", nativeDispatch);
  check(
    lastRuntimeModuleAddition < 0 || runtimePreparation > lastRuntimeModuleAddition,
    "Plugin runtime-module TypeScript stripping must follow every generated runtime-module addition"
  );
  check(runtimeReplacement > runtimePreparation, "Plugin runtime-module TypeScript source replacement is missing or out of order");
  check(nativeDispatch > sourceReplacement, "Plugin TypeScript stripping must happen before native dispatch");
  check(nativeDispatch > moduleReplacement, "Plugin preloaded module TypeScript stripping must happen before native dispatch");
  check(nativeDispatch > runtimeReplacement, "Plugin runtime-module TypeScript stripping must happen before native dispatch");
  check(
    active.includes(
      "NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(\n" +
      "                    source,\n" +
      "                    sourceName,\n" +
      "                    workingDirectory,\n" +
      "                    sandboxRoot,\n" +
      "                    moduleSources,\n" +
      "                    runtimeModuleSources,"
    ),
    "Native dispatch is not consuming all prepared TypeScript sources"
  );
  check(
    !active.includes("prepareTypeScriptModuleSourcesForNative(moduleSourceProvider") &&
      !active.includes("prepareTypeScriptRuntimeModuleSourcesForNative(moduleSourceProvider"),
    "On-demand module-source provider transport must not be materialized into preloaded TypeScript stripping"
  );

  const entrySuccessDiagnostics = active.indexOf("nativePayloadFromMap(typeScriptEntry.diagnostics())", nativeDispatch);
  const moduleSuccessDiagnostics = active.indexOf("nativePayloadFromMap(typeScriptModuleSources.diagnostics())", nativeDispatch);
  const runtimeSuccessDiagnostics = active.indexOf("nativePayloadFromMap(typeScriptRuntimeModuleSources.diagnostics())", nativeDispatch);
  check(entrySuccessDiagnostics > nativeDispatch, "TypeScript entry success diagnostics are not appended to native payload");
  check(moduleSuccessDiagnostics > entrySuccessDiagnostics, "TypeScript module-source diagnostics are missing or out of order");
  check(runtimeSuccessDiagnostics > moduleSuccessDiagnostics, "TypeScript runtime-module diagnostics are missing or out of order");
  const unsupportedBranch = active.indexOf("error instanceof NodeTypeScriptStripper.UnsupportedTypeScriptException", nativeDispatch);
  const preservedErrorCode = active.indexOf("failureErrorCode = typeScriptError.errorCode();", unsupportedBranch);
  const failureDiagnostics = active.indexOf("nativePayloadFromMap(typeScriptError.diagnostics())", unsupportedBranch);
  check(unsupportedBranch > nativeDispatch, "Plugin service does not distinguish unsupported TypeScript failures");
  check(preservedErrorCode > unsupportedBranch, "Plugin service collapses the TypeScript catalog error code");
  check(failureDiagnostics > unsupportedBranch, "TypeScript failure source/syntax/line/column diagnostics are not appended");
}

function verifyTypeScriptProviderTransport(providerTransport, cpp) {
  const handlerStart = providerTransport.indexOf("private void handleProviderResponse(");
  const handlerEnd = providerTransport.indexOf("private void prepareDecryptedTypeScriptSource(", handlerStart);
  check(handlerStart >= 0 && handlerEnd > handlerStart, "Plugin module-source provider response boundary is missing");
  const handler = providerTransport.slice(handlerStart, handlerEnd);
  const pfdBranch = handler.indexOf("if (pfdStatus) {");
  const rawSingleBudget = handler.indexOf("if (declaredBytes > SINGLE_SOURCE_BYTES_LIMIT)", pfdBranch);
  const rawAggregateBudget = handler.indexOf("long aggregateBefore = sourceBytes.get();", pfdBranch);
  const rawAggregateCondition = handler.indexOf(
    "if (aggregateBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes)",
    rawAggregateBudget
  );
  const pfdCopy = handler.indexOf("long copiedBytes = copySource(", rawAggregateCondition);
  const rawByteValidation = handler.indexOf("if (copiedBytes != declaredBytes)", pfdCopy);
  const rawCountAccounting = handler.indexOf("rawSourceCount.incrementAndGet();", rawByteValidation);
  const rawByteAccounting = handler.indexOf("sourceBytes.addAndGet(copiedBytes);", rawCountAccounting);
  const plaintextBranch = handler.indexOf("if (STATUS_PLAINTEXT.equals(status))", rawByteAccounting);
  const preparation = handler.indexOf(
    "prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);",
    plaintextBranch
  );
  const preparedResponseBytes = handler.indexOf(
    '.put("sourceBytes", responseSourceBytes)',
    preparation
  );
  check(pfdBranch >= 0, "Plugin encrypted/plaintext PFD module-source provider branch is missing");
  check(rawSingleBudget > pfdBranch, "Plugin provider raw single-source budget must precede PFD materialization");
  check(rawAggregateBudget > rawSingleBudget, "Plugin provider raw aggregate budget must precede PFD materialization");
  check(rawAggregateCondition > rawAggregateBudget, "Plugin provider raw aggregate budget comparison no longer uses declared PFD bytes");
  check(pfdCopy > rawAggregateCondition, "Plugin provider TypeScript preparation must follow raw PFD copy and budgets");
  check(rawByteValidation > pfdCopy, "Plugin provider raw PFD byte-count validation is missing or out of order");
  check(rawCountAccounting > rawByteValidation, "Plugin provider raw source-count accounting is missing or out of order");
  check(rawByteAccounting > rawCountAccounting, "Plugin provider legacy source_bytes no longer records accepted raw PFD bytes");
  check(plaintextBranch > rawByteAccounting, "Plugin provider plaintext materialization must follow accepted raw-byte accounting");
  check(preparation > plaintextBranch, "Plugin provider TypeScript preparation escaped the non-plaintext PFD response branch");
  check(preparedResponseBytes > preparation, "Plugin native provider response no longer reports prepared file bytes");
  check(
    (handler.match(/prepareDecryptedTypeScriptSource\(/g) || []).length === 1 &&
      handler.slice(plaintextBranch, preparation).includes("} else {") &&
      !handler.slice(preparation + "prepareDecryptedTypeScriptSource(".length)
        .includes("prepareDecryptedTypeScriptSource("),
    "Plugin encrypted PFD preparation must remain inside the decrypted response branch"
  );

  const preparationStart = providerTransport.indexOf("private void prepareDecryptedTypeScriptSource(");
  const preparationEnd = providerTransport.indexOf("private void recordPreparedSource(", preparationStart);
  check(preparationStart >= 0 && preparationEnd > preparationStart, "Plugin provider TypeScript file-preparation boundary is missing");
  const preparationBody = providerTransport.slice(preparationStart, preparationEnd);
  const mappedSourceName = preparationBody.indexOf(
    "String sourceName = nonBlank(resolvedPath, sourceFile.getName());"
  );
  const nonTypeScriptBranch = preparationBody.indexOf("if (!typeScript) {", mappedSourceName);
  const nonTypeScriptPreparedAccounting = preparationBody.indexOf("recordPreparedSource(rawBytes);", nonTypeScriptBranch);
  const typeScriptInputAccounting = preparationBody.indexOf("typeScriptInputBytes.addAndGet(rawBytes);", nonTypeScriptBranch);
  const boundedRead = preparationBody.indexOf("readSourceBytesBounded(sourceFile, rawBytes);", typeScriptInputAccounting);
  const bytePreparation = preparationBody.indexOf(
    "prepareDecryptedTypeScriptBytes(sourceName, rawSource);",
    boundedRead
  );
  const preparedSingleBudget = preparationBody.indexOf("if (prepared.length > SINGLE_SOURCE_BYTES_LIMIT)", bytePreparation);
  const preparedAggregateBudget = preparationBody.indexOf("long preparedBefore = preparedSourceBytes.get();", preparedSingleBudget);
  const preparedAggregateCondition = preparationBody.indexOf(
    "if (preparedBefore > TOTAL_SOURCE_BYTES_LIMIT - prepared.length)",
    preparedAggregateBudget
  );
  const atomicPublish = preparationBody.indexOf("replaceSourceAtomically(sourceFile, prepared);", preparedAggregateCondition);
  const outputAccounting = preparationBody.indexOf("typeScriptOutputBytes.addAndGet(prepared.length);", atomicPublish);
  const preparedAccounting = preparationBody.indexOf("recordPreparedSource(prepared.length);", outputAccounting);
  check(mappedSourceName >= 0, "Plugin provider TypeScript classification is not based on mapped resolvedPath authority");
  check(
    nonTypeScriptPreparedAccounting > nonTypeScriptBranch &&
      nonTypeScriptPreparedAccounting < typeScriptInputAccounting,
    "Plugin provider non-TypeScript prepared-byte accounting is missing or applies TypeScript decoding"
  );
  check(typeScriptInputAccounting > nonTypeScriptPreparedAccounting, "Plugin provider TypeScript raw input-byte accounting is missing");
  check(boundedRead > typeScriptInputAccounting, "Plugin provider TypeScript bytes are not re-read through the bounded private file");
  check(bytePreparation > boundedRead, "Plugin provider production path is not wired to the JVM-tested byte-preparation seam");
  check(preparedSingleBudget > bytePreparation, "Plugin provider prepared TypeScript single-source budget is missing");
  check(preparedAggregateBudget > preparedSingleBudget, "Plugin provider prepared TypeScript aggregate budget is missing");
  check(preparedAggregateCondition > preparedAggregateBudget, "Plugin provider prepared aggregate budget comparison no longer uses output bytes");
  check(atomicPublish > preparedAggregateCondition, "Plugin provider TypeScript atomic publication is missing or precedes prepared budgets");
  check(outputAccounting > atomicPublish, "Plugin provider TypeScript output-byte accounting precedes publication");
  check(preparedAccounting > outputAccounting, "Plugin provider prepared-byte accounting is missing or out of order");

  const preparedAccountingStart = providerTransport.indexOf("private void recordPreparedSource(");
  const preparedAccountingEnd = providerTransport.indexOf("private void recordTypeScriptSuccess(", preparedAccountingStart);
  check(
    preparedAccountingStart >= 0 && preparedAccountingEnd > preparedAccountingStart,
    "Plugin provider prepared-source accounting boundary is missing"
  );
  const preparedAccountingBody = providerTransport.slice(preparedAccountingStart, preparedAccountingEnd);
  const preparedBudgetStart = providerTransport.indexOf("private void ensurePreparedSourceBudget(", preparedAccountingStart);
  const preparedCommitStart = providerTransport.indexOf("private void commitPreparedSource(", preparedBudgetStart);
  check(
    preparedBudgetStart > preparedAccountingStart && preparedCommitStart > preparedBudgetStart &&
      preparedCommitStart < preparedAccountingEnd,
    "Plugin provider prepared-source budget/commit boundaries are missing or out of order"
  );
  const preparedBudgetBody = providerTransport.slice(preparedBudgetStart, preparedCommitStart);
  const preparedCommitBody = providerTransport.slice(preparedCommitStart, preparedAccountingEnd);
  check(
    preparedAccountingBody.includes("ensurePreparedSourceBudget(bytes);") &&
      preparedAccountingBody.includes("commitPreparedSource(bytes);") &&
      preparedBudgetBody.includes("if (bytes < 0L || bytes > SINGLE_SOURCE_BYTES_LIMIT)") &&
      preparedBudgetBody.includes("if (before > TOTAL_SOURCE_BYTES_LIMIT - bytes)") &&
      preparedCommitBody.includes("preparedSourceCount.incrementAndGet();") &&
      preparedCommitBody.includes("preparedSourceBytes.addAndGet(bytes);"),
    "Plugin provider prepared-source accounting no longer enforces single/aggregate bytes before counters"
  );

  const pureStart = providerTransport.indexOf("static PreparedTypeScriptSource prepareDecryptedTypeScriptBytes(");
  const pureEnd = providerTransport.indexOf("private static void replaceSourceAtomically(", pureStart);
  check(pureStart >= 0 && pureEnd > pureStart, "Plugin provider pure TypeScript byte-preparation seam is missing");
  const purePreparation = providerTransport.slice(pureStart, pureEnd);
  const classification = purePreparation.indexOf("NodeTypeScriptStripper.isTypeScriptSourceName(resolvedPath)");
  const strictDecode = purePreparation.indexOf("String source = decodeStrictUtf8(boundedRawSource);", classification);
  const pluginStripper = purePreparation.indexOf("NodeTypeScriptStripper.stripIfTypeScript(resolvedPath, source);", strictDecode);
  check(classification >= 0, "Plugin provider byte-preparation seam no longer classifies mapped resolvedPath");
  check(strictDecode > classification, "Plugin provider TypeScript preparation no longer performs strict UTF-8 admission");
  check(pluginStripper > strictDecode, "Plugin provider byte-preparation seam no longer delegates to the plugin-owned stripper");

  const decoderStart = providerTransport.indexOf("private static String decodeStrictUtf8(");
  const decoderEnd = providerTransport.indexOf("static PreparedTypeScriptSource prepareDecryptedTypeScriptBytes(", decoderStart);
  check(decoderStart >= 0 && decoderEnd > decoderStart, "Plugin provider strict UTF-8 decoder boundary is missing");
  const decoder = providerTransport.slice(decoderStart, decoderEnd);
  check(
    decoder.includes(".onMalformedInput(CodingErrorAction.REPORT)") &&
      decoder.includes(".onUnmappableCharacter(CodingErrorAction.REPORT)"),
    "Plugin provider TypeScript decoder must fail closed for malformed or unmappable UTF-8"
  );

  const atomicStart = providerTransport.indexOf("private static void replaceSourceAtomically(");
  const atomicEnd = providerTransport.indexOf("static String diagnosticSourceName(", atomicStart);
  check(atomicStart >= 0 && atomicEnd > atomicStart, "Plugin provider atomic TypeScript publication boundary is missing");
  const atomic = providerTransport.slice(atomicStart, atomicEnd);
  const sameDirectoryTemporary = atomic.indexOf('destination.getName() + ".typescript.tmp"');
  const fileSync = atomic.indexOf("output.getFD().sync();", sameDirectoryTemporary);
  const atomicRename = atomic.indexOf("Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());", fileSync);
  check(sameDirectoryTemporary >= 0, "Plugin provider prepared TypeScript temporary file must share the response directory");
  check(fileSync > sameDirectoryTemporary, "Plugin provider prepared TypeScript temporary file is not fsynced");
  check(atomicRename > fileSync, "Plugin provider prepared TypeScript file is not atomically renamed after fsync");

  const diagnosticKeys = [
    "raw_source_count",
    "source_bytes",
    "raw_source_bytes",
    "prepared_source_count",
    "prepared_source_bytes",
    "typescript.source_count",
    "typescript.stripped_count",
    "typescript.failure_count",
    "typescript.input_bytes",
    "typescript.output_bytes",
    "typescript.last_status",
    "typescript.last_source_name",
    "typescript.last_extension",
    "typescript.last_stripped",
    "typescript.last_error_code",
    "typescript.last_syntax_kind",
    "typescript.last_line",
    "typescript.last_column"
  ];
  for (const key of diagnosticKeys) {
    check(
      providerTransport.includes(`embedded_script.runtime_plugin.module_provider.${key}`),
      `Plugin provider TypeScript/raw/prepared diagnostic '${key}' is missing`
    );
  }
  check(
    providerTransport.includes("String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;") &&
      providerTransport.includes("TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256") &&
      providerTransport.includes("Character.isLowSurrogate(name.charAt(start))") &&
      providerTransport.includes("Character.isHighSurrogate(name.charAt(start - 1))"),
    "Plugin provider last TypeScript source diagnostic is not basename-only, bounded, and UTF-16 boundary safe"
  );

  const dispatchStart = providerTransport.indexOf("private void dispatch(File requestFile)");
  const dispatchEnd = providerTransport.indexOf("private Bundle callProvider(", dispatchStart);
  check(dispatchStart >= 0 && dispatchEnd > dispatchStart, "Plugin provider dispatch error boundary is missing");
  const dispatch = providerTransport.slice(dispatchStart, dispatchEnd);
  const canonicalCatch = dispatch.indexOf("catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)");
  const genericCatch = dispatch.indexOf("catch (Throwable error)", canonicalCatch);
  check(canonicalCatch >= 0 && genericCatch > canonicalCatch, "Plugin provider canonical TypeScript failure is swallowed by generic transport handling");
  const canonicalFailure = dispatch.slice(canonicalCatch, genericCatch);
  check(
    canonicalFailure.includes("error.errorCode(),") &&
      canonicalFailure.includes("messageOf(error)") &&
      canonicalFailure.includes("error.syntaxKind()") &&
      canonicalFailure.includes("error.line()") &&
      canonicalFailure.includes("error.column()"),
    "Plugin provider canonical TypeScript code/message/diagnostics are not preserved in the native response"
  );
  check(
    (canonicalFailure.match(/recordTypeScriptFailure\(/g) || []).length === 1,
    "Plugin provider canonical TypeScript failure_count must be recorded exactly once at dispatch"
  );
  const preparationUnsupportedCatch = preparationBody.indexOf(
    "catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)"
  );
  const preparationBudgetCatch = preparationBody.indexOf(
    "catch (BudgetExceededException error)",
    preparationUnsupportedCatch
  );
  const preparationUtf8Catch = preparationBody.indexOf(
    "catch (CharacterCodingException error)",
    preparationBudgetCatch
  );
  const preparationIoCatch = preparationBody.indexOf(
    "catch (IOException error)",
    preparationUtf8Catch
  );
  check(
    preparationUnsupportedCatch >= 0 &&
      preparationBudgetCatch > preparationUnsupportedCatch &&
      preparationUtf8Catch > preparationBudgetCatch &&
      preparationIoCatch > preparationUtf8Catch,
    "Plugin provider TypeScript preparation failure branches are missing or out of order"
  );
  check(
    (preparationBody.slice(preparationUnsupportedCatch, preparationBudgetCatch)
      .match(/recordTypeScriptFailure\(/g) || []).length === 0,
    "Plugin provider unsupported TypeScript failure_count must be deferred to canonical dispatch"
  );
  for (const [failureKind, branch] of [
    ["budget", preparationBody.slice(preparationBudgetCatch, preparationUtf8Catch)],
    ["UTF-8", preparationBody.slice(preparationUtf8Catch, preparationIoCatch)],
    ["I/O", preparationBody.slice(preparationIoCatch)]
  ]) {
    check(
      (branch.match(/recordTypeScriptFailure\(/g) || []).length === 1,
      `Plugin provider ${failureKind} TypeScript failure_count must be recorded exactly once during preparation`
    );
  }
  check(
    cpp.includes('String(response.errorMessage || "Module-source provider request returned " + status + ".")') &&
      cpp.includes("String(response.errorCode || defaultCodes[status] || defaultCodes.failed)"),
    "Native module-source provider error construction no longer propagates the plugin response code and message"
  );
}

function verifyPlaintextTypeScriptProviderTransport(providerTransport, cpp) {
  const dispatchStart = providerTransport.indexOf("private void dispatch(File requestFile)");
  const dispatchEnd = providerTransport.indexOf("private Bundle callProvider(", dispatchStart);
  check(dispatchStart >= 0 && dispatchEnd > dispatchStart, "Plugin provider dispatch boundary is missing");
  const dispatch = providerTransport.slice(dispatchStart, dispatchEnd);
  const operationRead = dispatch.indexOf('requestJson.optString("operation")');
  const privateOperationSelection = dispatch.indexOf(
    "plaintextPreparationRequest = OPERATION_PREPARE_PLAINTEXT_TYPESCRIPT.equals(operation);",
    operationRead
  );
  const privateOperationBranch = dispatch.indexOf("if (plaintextPreparationRequest) {", privateOperationSelection);
  const privateOperationCall = dispatch.indexOf("handlePlaintextTypeScriptPreparation(", privateOperationBranch);
  const privateOperationReturn = dispatch.indexOf("return;", privateOperationCall);
  const providerCount = dispatch.indexOf("providerRequestCount.incrementAndGet();", privateOperationReturn);
  const binderCall = dispatch.indexOf("Bundle providerResponse = callProvider(", providerCount);
  check(operationRead >= 0 && privateOperationSelection > operationRead,
    "Plugin plaintext TypeScript private-operation selection is missing");
  check(
    privateOperationBranch > privateOperationSelection && privateOperationCall > privateOperationBranch &&
      privateOperationReturn > privateOperationCall && providerCount > privateOperationReturn && binderCall > providerCount,
    "Plugin plaintext TypeScript private operation must return before provider Binder dispatch"
  );
  check(
    providerTransport.includes("static final int REQUEST_COUNT_LIMIT = 1024;") &&
      providerTransport.includes("static final int TRANSPORT_REQUEST_COUNT_LIMIT = REQUEST_COUNT_LIMIT * 2;") &&
      dispatch.includes("int count = requestCount.incrementAndGet();") &&
      dispatch.includes("if (count > TRANSPORT_REQUEST_COUNT_LIMIT)") &&
      dispatch.includes("int providerCount = providerRequestCount.incrementAndGet();") &&
      dispatch.includes("if (providerCount > REQUEST_COUNT_LIMIT)"),
    "Plugin provider and private-operation transport budgets must remain independently bounded at 1024 and 2048"
  );

  const preparationStart = providerTransport.indexOf("private void handlePlaintextTypeScriptPreparation(");
  const preparationEnd = providerTransport.indexOf("private void handleProviderResponse(", preparationStart);
  check(preparationStart >= 0 && preparationEnd > preparationStart,
    "Plugin plaintext TypeScript private preparation boundary is missing");
  const preparation = providerTransport.slice(preparationStart, preparationEnd);
  const parentRead = preparation.indexOf('requestJson.optString("parentRequestId")');
  const parentConsume = preparation.indexOf("pendingPlaintextPreparations.remove(parentRequestId);", parentRead);
  const parentMatch = preparation.indexOf("pending == null || !pending.sourceName.equals(sourceName)", parentConsume);
  const inheritedDeadline = preparation.indexOf("Math.min(requestDeadline, pending.deadline)", parentMatch);
  check(
    parentRead >= 0 && parentConsume > parentRead && parentMatch > parentConsume && inheritedDeadline > parentMatch &&
      (preparation.match(/pendingPlaintextPreparations\.remove\(parentRequestId\)/g) || []).length === 1,
    "Plugin plaintext TypeScript preparation must consume one authorized parent response and its original deadline"
  );

  const declaredPath = preparation.indexOf('requestJson.optString("sourcePath")', inheritedDeadline);
  const fixedPrivatePath = preparation.indexOf(
    'new File(requestDir, safeFileName(id) + ".source")',
    declaredPath
  );
  const declaredBytes = preparation.indexOf('requestJson.opt("sourceBytes")', fixedPrivatePath);
  const envelopeStart = preparation.indexOf("static long validatePlaintextTypeScriptPreparationEnvelope(");
  const privateHandler = preparation.slice(0, envelopeStart);
  const envelope = preparation.slice(envelopeStart);
  const exactPrivatePath = envelope.indexOf(
    "if (!expectedSource.getAbsolutePath().equals(declaredSourcePath))"
  );
  const rawSingleBudget = envelope.indexOf(
    "if (declaredBytes < 0L || declaredBytes > SINGLE_SOURCE_BYTES_LIMIT)",
    exactPrivatePath
  );
  const sourceNameAdmission = envelope.indexOf(
    "NodeTypeScriptStripper.isTypeScriptSourceName(sourceName)",
    rawSingleBudget
  );
  const rawAggregateBudget = preparation.indexOf("long rawBefore = sourceBytes.get();", declaredBytes);
  const rawAggregateCondition = preparation.indexOf(
    "if (rawBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes)",
    rawAggregateBudget
  );
  const privateRead = preparation.indexOf("readPrivatePreparationSource(", rawAggregateCondition);
  const rawAccounting = preparation.indexOf("sourceBytes.addAndGet(rawSource.length);", privateRead);
  const stripper = preparation.indexOf("prepareDecryptedTypeScriptBytes(sourceName, rawSource);", rawAccounting);
  const preparedSingleBudget = preparation.indexOf(
    "if (prepared.length > SINGLE_SOURCE_BYTES_LIMIT)",
    stripper
  );
  const preparedAggregateBudget = preparation.indexOf("ensurePreparedSourceBudget(prepared.length);", preparedSingleBudget);
  const atomicPublish = preparation.indexOf("replaceSourceAtomically(responseSource, prepared);", preparedAggregateBudget);
  const preparedStatus = preparation.indexOf('.put("status", STATUS_PREPARED)', atomicPublish);
  const preparedPath = preparation.indexOf('.put("sourcePath", responseSource.getAbsolutePath())', preparedStatus);
  const preparedBytes = preparation.indexOf('.put("sourceBytes", responseSource.length())', preparedPath);
  const rawResponseBytes = preparation.indexOf('.put("rawSourceBytes", rawSource.length)', preparedBytes);
  check(
    declaredPath >= 0 && fixedPrivatePath > declaredPath && declaredBytes > fixedPrivatePath && envelopeStart > declaredBytes &&
      exactPrivatePath >= 0 && rawSingleBudget > exactPrivatePath && sourceNameAdmission > rawSingleBudget,
    "Plugin plaintext TypeScript request does not enforce its fixed private path, exact byte shape, and source-name admission"
  );
  check(
      !privateHandler.includes("workspaceSession") &&
      !privateHandler.includes("new File(sourceName)") &&
      !privateHandler.includes("new File(declaredSourcePath)") &&
      privateHandler.includes("readPrivatePreparationSource(") && privateHandler.includes("requestSource,"),
    "Plugin plaintext TypeScript private handler must not reopen or remap the authorized workspace path"
  );
  check(
    rawAggregateBudget > declaredBytes && rawAggregateCondition > rawAggregateBudget &&
      privateRead > rawAggregateCondition && rawAccounting > privateRead && stripper > rawAccounting,
    "Plugin plaintext TypeScript raw budgets/read/accounting/stripper order is invalid"
  );
  check(
    preparedSingleBudget > stripper && preparedAggregateBudget > preparedSingleBudget &&
      atomicPublish > preparedAggregateBudget && preparedStatus > atomicPublish &&
      preparedPath > preparedStatus && preparedBytes > preparedPath && rawResponseBytes > preparedBytes,
    "Plugin plaintext TypeScript prepared budgets/publication/response byte truth is invalid"
  );
  check(
    preparation.includes("catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)") &&
      dispatch.includes("catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)"),
    "Plugin plaintext TypeScript canonical failure is not preserved through private preparation and dispatch"
  );
  const envelopeValidation = privateHandler.indexOf("declaredBytes = validatePlaintextTypeScriptPreparationEnvelope(");
  const envelopeBudgetCatch = privateHandler.indexOf("catch (BudgetExceededException error)", envelopeValidation);
  const rawDeclaration = privateHandler.indexOf("byte[] rawSource;", envelopeBudgetCatch);
  const rawReadBudgetCatch = privateHandler.indexOf("catch (BudgetExceededException error)", rawDeclaration);
  const rawReadIoCatch = privateHandler.indexOf("catch (IOException error)", rawReadBudgetCatch);
  const rawSuccess = privateHandler.indexOf("rawSourceCount.incrementAndGet();", rawReadIoCatch);
  const preparedUnsupportedCatch = privateHandler.indexOf(
    "catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)",
    rawSuccess
  );
  const preparedBudgetCatch = privateHandler.indexOf("catch (BudgetExceededException error)", preparedUnsupportedCatch);
  const preparedUtf8Catch = privateHandler.indexOf("catch (CharacterCodingException error)", preparedBudgetCatch);
  const preparedIoCatch = privateHandler.indexOf("catch (IOException error)", preparedUtf8Catch);
  check(
    envelopeValidation >= 0 && envelopeBudgetCatch > envelopeValidation && rawDeclaration > envelopeBudgetCatch &&
      rawReadBudgetCatch > rawDeclaration && rawReadIoCatch > rawReadBudgetCatch && rawSuccess > rawReadIoCatch &&
      preparedUnsupportedCatch > rawSuccess && preparedBudgetCatch > preparedUnsupportedCatch &&
      preparedUtf8Catch > preparedBudgetCatch && preparedIoCatch > preparedUtf8Catch,
    "Plugin plaintext TypeScript raw/prepared failure accounting branches are missing or out of order"
  );
  check(
    (privateHandler.slice(envelopeBudgetCatch, rawDeclaration).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      (privateHandler.slice(rawReadBudgetCatch, rawReadIoCatch).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      (privateHandler.slice(rawReadIoCatch, rawSuccess).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      (privateHandler.slice(preparedUnsupportedCatch, preparedBudgetCatch).match(/recordTypeScriptFailure\(/g) || []).length === 0 &&
      (privateHandler.slice(preparedBudgetCatch, preparedUtf8Catch).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      (privateHandler.slice(preparedUtf8Catch, preparedIoCatch).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      (privateHandler.slice(preparedIoCatch).match(/recordTypeScriptFailure\(/g) || []).length === 1 &&
      privateHandler.includes('"raw_budget_exceeded"') &&
      privateHandler.includes('"private_input_io"') && privateHandler.includes('"invalid_utf8"') &&
      privateHandler.includes('"transport_io"'),
    "Plugin plaintext TypeScript raw-single/raw-aggregate/private-I/O/prepared/UTF-8 failure_count accounting is not exactly once"
  );
  const canonicalDispatchCatch = dispatch.indexOf("catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)");
  const genericDispatchCatch = dispatch.indexOf("catch (Throwable error)", canonicalDispatchCatch);
  check(
    canonicalDispatchCatch >= 0 && genericDispatchCatch > canonicalDispatchCatch &&
      (dispatch.slice(canonicalDispatchCatch, genericDispatchCatch).match(/recordTypeScriptFailure\(/g) || []).length === 1,
    "Plugin plaintext canonical TypeScript failure_count must be recorded exactly once at terminal dispatch"
  );

  const writeResponseStart = providerTransport.indexOf("private void writeResponse(");
  const recordStart = providerTransport.indexOf(
    "private void record(JSONObject response, boolean providerResponse)",
    writeResponseStart
  );
  const recordEnd = providerTransport.indexOf("private static JSONObject failureJson(", recordStart);
  check(writeResponseStart >= 0 && recordStart > writeResponseStart && recordEnd > recordStart,
    "Plugin provider response accounting boundary is missing");
  const responseAccounting = providerTransport.slice(writeResponseStart, recordEnd);
  const recordBody = providerTransport.slice(recordStart, recordEnd);
  const transportElapsed = recordBody.indexOf("transportElapsedMs.addAndGet(responseElapsedMs);");
  const providerResponseGuard = recordBody.indexOf("if (!providerResponse)", transportElapsed);
  const providerResponseReturn = recordBody.indexOf("return;", providerResponseGuard);
  const providerLastStatus = recordBody.indexOf("lastStatus.set(status);", providerResponseReturn);
  const providerElapsed = recordBody.indexOf("elapsedMs.addAndGet(responseElapsedMs);", providerLastStatus);
  check(
    (responseAccounting.match(/record\(response, providerResponse\);/g) || []).length === 2 &&
      responseAccounting.includes("writeResponse(requestFile, response, null, providerResponse);") &&
      (recordBody.match(/transportElapsedMs\.addAndGet\(responseElapsedMs\);/g) || []).length === 1 &&
      transportElapsed >= 0 && providerResponseGuard > transportElapsed &&
      providerResponseReturn > providerResponseGuard && providerLastStatus > providerResponseReturn &&
      providerElapsed > providerLastStatus,
    "Plugin terminal responses must add transport elapsed exactly once while private responses skip provider status/elapsed accounting"
  );
  check(
    privateHandler.includes("writeResponse(requestFile, responseJson, responseSource, false);") &&
      (dispatch.match(/!plaintextPreparationRequest\);/g) || []).length === 7 &&
      dispatch.includes("writeResponseQuietly(requestFile, failureJson("),
    "Plugin plaintext TypeScript success/failure responses must be recorded as private transport terminals"
  );

  const providerHandlerStart = providerTransport.indexOf("private void handleProviderResponse(");
  const providerHandlerEnd = providerTransport.indexOf("private void prepareDecryptedTypeScriptSource(", providerHandlerStart);
  const providerHandler = providerTransport.slice(providerHandlerStart, providerHandlerEnd);
  const pendingCreation = providerHandler.indexOf("new PendingPlaintextTypeScriptPreparation(");
  const prePublishRunningGuard = providerHandler.indexOf("if (running.get() && !stopped.get())", pendingCreation);
  const plaintextAuthorization = providerHandler.indexOf("pendingPlaintextPreparations.put(id, pending);", prePublishRunningGuard);
  const postPublishStopGuard = providerHandler.indexOf("if (!running.get() || stopped.get())", plaintextAuthorization);
  const conditionalAuthorizationRemoval = providerHandler.indexOf(
    "pendingPlaintextPreparations.remove(id, pending);",
    postPublishStopGuard
  );
  const responsePublish = providerHandler.indexOf(
    "writeResponse(requestFile, responseJson, sourceFile, true);",
    conditionalAuthorizationRemoval
  );
  const failedPublishRemoval = providerHandler.indexOf(
    "pendingPlaintextPreparations.remove(id, pending);",
    responsePublish
  );
  check(
    pendingCreation >= 0 && prePublishRunningGuard > pendingCreation &&
      plaintextAuthorization > prePublishRunningGuard && postPublishStopGuard > plaintextAuthorization &&
      conditionalAuthorizationRemoval > postPublishStopGuard && responsePublish > conditionalAuthorizationRemoval &&
      failedPublishRemoval > responsePublish &&
      providerHandler.includes("STATUS_NOT_ENCRYPTED.equals(nativeStatus)") &&
      providerHandler.includes("STATUS_MATERIALIZED_PLAINTEXT.equals(nativeStatus)") &&
      providerHandler.includes("NodeTypeScriptStripper.isTypeScriptSourceName(resolvedPath)"),
    "Plugin plaintext TypeScript parent authorization is not stop-safe before publishing a mapped provider response"
  );
  const loopStart = providerTransport.indexOf("private void loop()");
  const loopEnd = providerTransport.indexOf("private void drainRequestFiles()", loopStart);
  const loop = providerTransport.slice(loopStart, loopEnd);
  check(
    loopStart >= 0 && loopEnd > loopStart && loop.includes("finally {") &&
      loop.includes("pendingPlaintextPreparations.clear();") &&
      providerTransport.includes("running.set(false);") &&
      (providerTransport.match(/pendingPlaintextPreparations\.clear\(\);/g) || []).length >= 2,
    "Plugin plaintext TypeScript parent authorizations must be cleared both at stop initiation and worker termination"
  );
  check(
    providerTransport.includes("this.thread = new Thread(this::loop);") &&
      providerTransport.includes("drainRequestFiles();") &&
      providerTransport.includes("for (File file : files)") &&
      providerTransport.includes("dispatch(file);"),
    "Plugin plaintext TypeScript post-publish parent authorization requires one serial request-drain worker"
  );

  const privateReadStart = providerTransport.indexOf("private byte[] readPrivatePreparationSource(");
  const privateReadEnd = providerTransport.indexOf("private static String openedDescriptorPath(", privateReadStart);
  check(privateReadStart >= 0 && privateReadEnd > privateReadStart,
    "Plugin plaintext TypeScript exact private read boundary is missing");
  const exactRead = providerTransport.slice(privateReadStart, privateReadEnd);
  check(
    exactRead.includes("canonicalRequestDirectory.equals(canonicalSource.getParentFile())") &&
      exactRead.includes("Os.lstat(sourceFile.getAbsolutePath())") &&
      exactRead.includes("OsConstants.S_ISREG(before.st_mode)") &&
      exactRead.includes("OsConstants.S_ISLNK(before.st_mode)") &&
      exactRead.includes("OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW") &&
      exactRead.includes("Os.fstat(input.getFD())") &&
      exactRead.includes("openedDescriptorPath(input.getFD())") &&
      exactRead.includes("total != expectedBytes") &&
      exactRead.includes("after.st_dev != before.st_dev") &&
      exactRead.includes("after.st_ino != before.st_ino") &&
      exactRead.includes("after.st_size != expectedBytes"),
    "Plugin plaintext TypeScript private read no longer enforces containment, NOFOLLOW, exact bytes, and before/after identity"
  );

  for (const key of [
    "typescript.plaintext_preparation_request_count",
    "typescript.plaintext_preparation_count"
  ]) {
    check(providerTransport.includes(`embedded_script.runtime_plugin.module_provider.${key}`),
      `Plugin plaintext TypeScript diagnostic '${key}' is missing`);
  }

  const classifierStart = cpp.indexOf("function __autojs6_is_plaintext_typescript_source(");
  const responseValidationStart = cpp.lastIndexOf(
    "function __autojs6_module_source_provider_response(",
    classifierStart
  );
  const responseValidation = cpp.slice(responseValidationStart, classifierStart);
  const preparationStatusBranch = responseValidation.indexOf("const preparationResponse = responseKind === \"typescript_preparation\"");
  const ordinaryStatusBranch = responseValidation.indexOf(": (", preparationStatusBranch);
  const validStatusCheck = responseValidation.indexOf("if (!validStatus)", ordinaryStatusBranch);
  const preparationStatuses = responseValidation.slice(preparationStatusBranch, ordinaryStatusBranch);
  const ordinaryStatuses = responseValidation.slice(ordinaryStatusBranch, validStatusCheck);
  check(
    responseValidationStart >= 0 && preparationStatusBranch >= 0 && ordinaryStatusBranch > preparationStatusBranch &&
      validStatusCheck > ordinaryStatusBranch && preparationStatuses.includes('status === "prepared"') &&
      !ordinaryStatuses.includes('status === "prepared"'),
    "Native provider response validation must accept prepared only for the private TypeScript response kind"
  );
  const classifierEnd = cpp.indexOf("function __autojs6_prepare_plaintext_typescript_source(", classifierStart);
  const nativePreparationEnd = cpp.indexOf("function __autojs6_request_module_source(", classifierEnd);
  check(classifierStart >= 0 && classifierEnd > classifierStart && nativePreparationEnd > classifierEnd,
    "Native plaintext TypeScript classifier/preparation boundaries are missing");
  const classifier = cpp.slice(classifierStart, classifierEnd);
  const nativePreparation = cpp.slice(classifierEnd, nativePreparationEnd);
  check(
    classifier.includes(".toLowerCase()") && classifier.includes('/\\.d\\.(?:ts|mts|cts)$/') &&
      classifier.includes('extension === ".ts"') && classifier.includes('extension === ".mts"') &&
      classifier.includes('extension === ".cts"'),
    "Native plaintext TypeScript classifier must be case-insensitive and exclude declaration sources"
  );
  const nonTypeScriptBypass = nativePreparation.indexOf("if (!__autojs6_is_plaintext_typescript_source(sourceName))");
  const nonTypeScriptDecode = nativePreparation.indexOf('rawSource.toString("utf8")', nonTypeScriptBypass);
  const parentDeadlineValidation = nativePreparation.indexOf(
    "const authorizedParentDeadline = Number(parentDeadline);",
    nonTypeScriptDecode
  );
  const nativeRawSingleBudget = nativePreparation.indexOf(
    "rawSourceBytes > __autojs6_runtime_module_single_source_bytes_limit",
    parentDeadlineValidation
  );
  const rawAtomicWrite = nativePreparation.indexOf("__autojs6_module_source_provider_atomic_write(", nonTypeScriptBypass);
  const rawAtomicDestination = nativePreparation.indexOf("rawSourceTempPath,", rawAtomicWrite);
  const rawAtomicValue = nativePreparation.indexOf("rawSource,", rawAtomicDestination);
  const requestJsonWrite = nativePreparation.indexOf(
    "__autojs6_module_source_provider_atomic_write(",
    rawAtomicWrite + 1
  );
  const versionField = nativePreparation.indexOf("version: 2", requestJsonWrite);
  const idField = nativePreparation.indexOf("id,", versionField);
  const executionIdField = nativePreparation.indexOf("executionId: config.executionId", idField);
  const operationField = nativePreparation.indexOf('operation: "prepare_plaintext_typescript"', executionIdField);
  const parentField = nativePreparation.indexOf("parentRequestId: authorizedParentRequestId", operationField);
  const sourceNameField = nativePreparation.indexOf('sourceName: String(sourceName || "")', parentField);
  const sourcePathField = nativePreparation.indexOf("sourcePath: rawSourcePath", sourceNameField);
  const sourceBytesField = nativePreparation.indexOf("sourceBytes: rawSourceBytes", sourcePathField);
  const timeoutField = nativePreparation.indexOf("timeoutMs: remainingTimeoutMs", sourceBytesField);
  const responseDeadline = nativePreparation.indexOf("authorizedParentDeadline,", timeoutField);
  const responseKind = nativePreparation.indexOf('"typescript_preparation"', responseDeadline);
  const nativePreparedStatus = nativePreparation.indexOf('status !== "prepared"', responseKind);
  const exactPreparedPath = nativePreparation.indexOf("responseSourcePath !== preparedSourcePath", responseKind);
  const exactRawResponseBytes = nativePreparation.indexOf("declaredRawSourceBytes !== rawSourceBytes", exactPreparedPath);
  const exactResolvedPath = nativePreparation.indexOf('responseResolvedPath !== String(sourceName || "")', exactRawResponseBytes);
  const preparedVerifiedRead = nativePreparation.indexOf(
    "__autojs6_module_source_provider_verified_read(",
    exactRawResponseBytes
  );
  const strictPreparedDecode = nativePreparation.indexOf(
    "__autojs6_text_decoder_decode_utf8(preparedSource, true, true)",
    preparedVerifiedRead
  );
  check(
    nonTypeScriptBypass >= 0 && nonTypeScriptDecode > nonTypeScriptBypass &&
      parentDeadlineValidation > nonTypeScriptDecode && nativeRawSingleBudget > parentDeadlineValidation,
    "Native non-TypeScript plaintext sources no longer bypass private preparation or TypeScript raw/deadline admission drifted"
  );
  check(
    rawAtomicWrite > nativeRawSingleBudget && rawAtomicDestination > rawAtomicWrite && rawAtomicValue > rawAtomicDestination &&
      requestJsonWrite > rawAtomicValue && versionField > requestJsonWrite && idField > versionField &&
      executionIdField > idField && operationField > executionIdField && parentField > operationField &&
      sourceNameField > parentField && sourcePathField > sourceNameField && sourceBytesField > sourcePathField &&
      timeoutField > sourceBytesField && responseDeadline > timeoutField,
    "Native plaintext TypeScript raw bytes are not atomically published before the authorized private request"
  );
  check(
    responseKind > responseDeadline && nativePreparedStatus > responseKind && exactPreparedPath > nativePreparedStatus && exactRawResponseBytes > exactPreparedPath &&
      exactResolvedPath > exactRawResponseBytes && preparedVerifiedRead > exactResolvedPath &&
      strictPreparedDecode > preparedVerifiedRead,
    "Native plaintext TypeScript prepared response path/resolved-name/raw/prepared byte truth is incomplete"
  );
  check(
    nativePreparation.includes('typeof fs.openSync !== "function"') &&
      nativePreparation.includes('typeof fs.writeFileSync !== "function"') &&
      nativePreparation.includes('typeof fs.fsyncSync !== "function"') &&
      nativePreparation.includes('typeof fs.closeSync !== "function"') &&
      nativePreparation.includes('typeof fs.renameSync !== "function"'),
    "Native plaintext TypeScript preparation availability no longer requires the atomic private-file primitives"
  );
  const atomicWriteStart = cpp.indexOf("function __autojs6_module_source_provider_atomic_write(");
  const atomicWriteEnd = cpp.indexOf("function __autojs6_prepare_plaintext_typescript_source(", atomicWriteStart);
  const atomicWrite = cpp.slice(atomicWriteStart, atomicWriteEnd);
  check(
    atomicWriteStart >= 0 && atomicWriteEnd > atomicWriteStart &&
      atomicWrite.includes('fs.openSync(temporaryPath, "wx", 0o600)') &&
      atomicWrite.includes("fs.writeFileSync(fd, value") && atomicWrite.includes("fs.fsyncSync(fd)") &&
      atomicWrite.includes("fs.closeSync(fd)") && atomicWrite.includes("fs.renameSync(temporaryPath, destinationPath)"),
    "Native plaintext TypeScript private files must use exclusive mode-0600 fsync-before-rename publication"
  );
  const outerPreparationCatch = nativePreparation.indexOf("} catch (error) {", strictPreparedDecode);
  const preparationCleanup = nativePreparation.slice(outerPreparationCatch);
  check(
    outerPreparationCatch > strictPreparedDecode &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, requestTempPath)") &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, requestPath)") &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, rawSourceTempPath)") &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, rawSourcePath)") &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, responsePath)") &&
      preparationCleanup.includes("__autojs6_module_source_provider_cleanup_file(fs, preparedSourcePath)"),
    "Native plaintext TypeScript failure cleanup no longer covers every private request/raw/response/prepared artifact"
  );
  check(
    nativePreparation.includes("__autojs6_module_source_provider_cleanup_file(fs, responsePath);") &&
      nativePreparation.includes("__autojs6_module_source_provider_cleanup_file(fs, requestPath);") &&
      nativePreparation.includes("__autojs6_module_source_provider_cleanup_file(fs, rawSourcePath);") &&
      nativePreparation.includes("__autojs6_module_source_provider_cleanup_file(fs, responseSourcePath);"),
    "Native plaintext TypeScript success/finally cleanup no longer covers private request/raw/response/prepared artifacts"
  );

  const plaintextRecordStart = cpp.indexOf("function __autojs6_plaintext_module_source_record(");
  const plaintextRecordEnd = cpp.indexOf("function __autojs6_module_metadata_record(", plaintextRecordStart);
  check(plaintextRecordStart >= 0 && plaintextRecordEnd > plaintextRecordStart,
    "Native plaintext module-source convergence boundary is missing");
  const plaintextRecord = cpp.slice(plaintextRecordStart, plaintextRecordEnd);
  const cacheRead = plaintextRecord.indexOf("__autojs6_has_own(__autojs6_plaintext_module_source_records, readable)");
  const revalidate = plaintextRecord.indexOf("__autojs6_revalidate_plaintext_module_path(");
  const verifiedRawRead = plaintextRecord.indexOf("__autojs6_module_source_provider_verified_read(", revalidate);
  const canonicalPreparationName = plaintextRecord.indexOf("providerResult.resolvedPath", verifiedRawRead);
  const privatePrepare = plaintextRecord.indexOf("__autojs6_prepare_plaintext_typescript_source(", canonicalPreparationName);
  const exactRawArgument = plaintextRecord.indexOf("rawSource,", privatePrepare);
  const parentRequestArgument = plaintextRecord.indexOf("providerResult.requestId", exactRawArgument);
  const parentDeadlineArgument = plaintextRecord.indexOf("providerResult.deadline", parentRequestArgument);
  const workspaceSourceUrl = plaintextRecord.indexOf(
    "__autojs6_runtime_module_source_url(plaintextReadable)",
    parentDeadlineArgument
  );
  const cacheWrite = plaintextRecord.indexOf("__autojs6_plaintext_module_source_records[readable] = record", workspaceSourceUrl);
  check(
    cacheRead >= 0 && revalidate > cacheRead && verifiedRawRead > revalidate &&
      canonicalPreparationName > verifiedRawRead && privatePrepare > canonicalPreparationName &&
      exactRawArgument > privatePrepare && parentRequestArgument > exactRawArgument &&
      parentDeadlineArgument > parentRequestArgument && workspaceSourceUrl > parentDeadlineArgument &&
      cacheWrite > workspaceSourceUrl,
    "Native plaintext source must revalidate, verified-read raw bytes, privately prepare TypeScript, then retain the workspace source URL"
  );
  check(
    plaintextRecord.includes('const preparationSourceName = String(providerResult.resolvedPath || "")') &&
      /__autojs6_prepare_plaintext_typescript_source\(\s*preparationSourceName,\s*rawSource,\s*providerResult\.requestId,\s*providerResult\.deadline\s*\)/.test(plaintextRecord),
    "Native plaintext TypeScript preparation must receive the canonical provider name, exact verified bytes, parent id, and original deadline"
  );
  check(
    cpp.includes("const __autojs6_plaintext_module_source_records = Object.create(null)") &&
      plaintextRecord.includes("const record = Object.freeze({") &&
      plaintextRecord.includes("source: String(source)") &&
      plaintextRecord.includes("return __autojs6_plaintext_module_source_records[readable]") &&
      plaintextRecord.includes("__autojs6_plaintext_module_source_records[readable] = record"),
    "Native plaintext module-source record cache must remain null-prototype, frozen, and keyed by canonical readable path"
  );
  check(
    cpp.includes("requestId: status === \"not_encrypted\" ? id : \"\"") &&
      cpp.includes("deadline: status === \"not_encrypted\" ? requestDeadline : 0") &&
      cpp.includes("return __autojs6_plaintext_module_source_record(readable, allowEsm, providerResult);") &&
      cpp.includes("? __autojs6_plaintext_module_source_record(readable, allowEsm, providerResult)"),
    "Native CommonJS/ESM/dynamic plaintext consumers do not converge on the verified private-preparation seam"
  );
  check(
    cpp.includes("function __autojs6_throw_if_unsupported_typescript_extension(") &&
      cpp.includes('"ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION"'),
    "Native plaintext TSX routing no longer fails with the canonical extension error"
  );
}

function verifyProviderV2MissingPlaintextMaterialization(providerTransport, workspaceArchive, cpp, service = "") {
  const dispatchStart = providerTransport.indexOf("private void dispatch(File requestFile)");
  const dispatchEnd = providerTransport.indexOf("private Bundle callProvider(", dispatchStart);
  const dispatch = providerTransport.slice(dispatchStart, dispatchEnd);
  const materializationSelection = dispatch.indexOf(
    "materializationRequest = OPERATION_MATERIALIZE_MISSING_PLAINTEXT.equals(operation);"
  );
  const exactExtensionAdmission = dispatch.indexOf(
    "PluginWorkspaceArchiveSession.isSupportedProviderMaterializationPath(path)",
    materializationSelection
  );
  const providerCall = dispatch.indexOf("Bundle providerResponse = callProvider(", exactExtensionAdmission);
  check(
    dispatchStart >= 0 && dispatchEnd > dispatchStart && materializationSelection >= 0 &&
      exactExtensionAdmission > materializationSelection && providerCall > exactExtensionAdmission,
    "Plugin provider-v2 missing materialization must admit an exact supported extension before Binder dispatch"
  );

  const callStart = providerTransport.indexOf("private Bundle callProvider(");
  const callEnd = providerTransport.indexOf(
    "private void handlePlaintextTypeScriptPreparation(",
    callStart
  );
  const binderCall = providerTransport.slice(callStart, callEnd);
  const operationField = binderCall.indexOf("KEY_OPERATION,");
  const resolveOperation = binderCall.indexOf(
    "MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING",
    operationField
  );
  const materializeOperation = binderCall.indexOf(
    "MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT",
    operationField
  );
  const absoluteDeadline = binderCall.indexOf(
    "providerRequest.putLong(KEY_DEADLINE_ELAPSED_REALTIME_MS, requestDeadline);",
    materializeOperation
  );
  check(
    callStart >= 0 && callEnd > callStart && operationField >= 0 &&
      resolveOperation > operationField && materializeOperation > operationField &&
      absoluteDeadline > materializeOperation,
    "Plugin provider-v2 Binder request must carry the exact public operation and absolute monotonic deadline"
  );

  const shapeStart = providerTransport.indexOf("static void validateProviderResponseShape(");
  const rawTypeStart = providerTransport.indexOf("static void validateProviderResponseRawTypes(", shapeStart);
  const handlerStart = providerTransport.indexOf("private void handleProviderResponse(", rawTypeStart);
  check(shapeStart >= 0 && rawTypeStart > shapeStart && handlerStart > rawTypeStart,
    "Plugin provider-v2 response validation seams are missing");
  const shape = providerTransport.slice(shapeStart, rawTypeStart);
  const rawTypes = providerTransport.slice(rawTypeStart, handlerStart);
  check(
    shape.includes("!expectedOperation.equals(responseOperation)") &&
      shape.includes("materializationRequest &&") && shape.includes("STATUS_PLAINTEXT.equals(status)") &&
      shape.includes("!materializationRequest && STATUS_PLAINTEXT.equals(status)") &&
      shape.includes("pfdStatus && (!hasSourceFd || !hasSourceBytes)") &&
      shape.includes("!pfdStatus && (hasSourceFd || sourceBytes != 0L)"),
    "Plugin provider-v2 response shape must echo operation exactly, keep plaintext materialize-only, and reject mixed PFD pairs"
  );
  check(
    rawTypes.includes("version instanceof Integer") &&
      rawTypes.includes("responseId instanceof String") &&
      rawTypes.includes("operation instanceof String") &&
      rawTypes.includes("status instanceof String") &&
      rawTypes.includes("hasSourceFd && !sourceFdIsParcelFileDescriptor") &&
      rawTypes.includes("hasSourceBytes && !(sourceBytes instanceof Long)"),
    "Plugin provider-v2 response must reject Bundle type coercion before typed reads"
  );

  const handlerEnd = providerTransport.indexOf("private void prepareDecryptedTypeScriptSource(", handlerStart);
  const handler = providerTransport.slice(handlerStart, handlerEnd);
  const rawRead = handler.indexOf("Object rawVersion = response.get(KEY_VERSION);");
  const rawValidation = handler.indexOf("validateProviderResponseRawTypes(", rawRead);
  const typedVersion = handler.indexOf("int version = (Integer) rawVersion;", rawValidation);
  const shapeValidation = handler.indexOf("validateProviderResponseShape(", typedVersion);
  const exactPathValidation = handler.indexOf(
    "validatePositiveProviderResolvedPath(requestedPath, resolvedPath, status);",
    shapeValidation
  );
  const exactPathGuardEnd = handler.indexOf("String errorCode", exactPathValidation);
  const exactPathGuard = handler.slice(exactPathValidation, exactPathGuardEnd);
  const exactPathHelper = handler.indexOf("static void validatePositiveProviderResolvedPath(", exactPathGuardEnd);
  const exactPathHelperEnd = handler.indexOf("private void prepareDecryptedTypeScriptSource(", exactPathHelper);
  const exactPathContract = handler.slice(exactPathHelper, exactPathHelperEnd);
  const pfdCopy = handler.indexOf("long copiedBytes = copySource(", exactPathValidation);
  const rawAccounting = handler.indexOf("sourceBytes.addAndGet(copiedBytes);", pfdCopy);
  const transportDeadline = handler.indexOf("if (remainingMs(requestDeadline) <= 0L)", rawAccounting);
  const workspacePublication = handler.indexOf("workspaceSession.materializeProviderSourceNoReplace(", transportDeadline);
  const workspaceDeadlineArgument = handler.indexOf("requestDeadline", workspacePublication);
  const receipt = handler.indexOf("STATUS_MATERIALIZED_PLAINTEXT", workspacePublication);
  const rawReceipt = handler.indexOf('.put("rawAlreadyAccounted", STATUS_MATERIALIZED_PLAINTEXT.equals(nativeStatus))', receipt);
  check(
    rawRead >= 0 && rawValidation > rawRead && typedVersion > rawValidation && shapeValidation > typedVersion &&
      exactPathValidation > shapeValidation && exactPathGuardEnd > exactPathValidation &&
      exactPathGuard.includes("catch (IOException error)") &&
      exactPathGuard.includes("closeSourceFd(response);") &&
      exactPathHelper > exactPathGuardEnd &&
      exactPathContract.includes("STATUS_DECRYPTED.equals(status)") &&
      exactPathContract.includes("STATUS_NOT_ENCRYPTED.equals(status)") &&
      exactPathContract.includes("STATUS_PLAINTEXT.equals(status)") &&
      exactPathContract.includes("!requestedPath.equals(resolvedPath)") &&
      pfdCopy > exactPathValidation && rawAccounting > pfdCopy && transportDeadline > rawAccounting &&
      workspacePublication > transportDeadline && workspaceDeadlineArgument > workspacePublication &&
      receipt > workspaceDeadlineArgument && rawReceipt > receipt &&
      handler.includes("ProviderMaterializationDeadlineExceededException") &&
      handler.includes("throw new ProviderTimeoutException(messageOf(error), error);"),
    "Plugin provider-v2 must type/shape/exact-path validate with PFD cleanup, bounded-copy, publish, then issue a single-accounting receipt"
  );

  const publishStart = workspaceArchive.indexOf("synchronized ProviderMaterialization materializeProviderSourceNoReplace(");
  const publishEnd = workspaceArchive.indexOf("String mapEngineInfo(", publishStart);
  const publish = workspaceArchive.slice(publishStart, publishEnd);
  const extensionGuard = publish.indexOf("!isSupportedProviderMaterializationPath(relative)");
  const mainTry = publish.indexOf("try {", extensionGuard);
  const initialDeadline = publish.indexOf('"before workspace publication"', mainTry);
  const parentCreation = publish.indexOf("ensureProviderMaterializationDirectories(parent, createdDirectories)", initialDeadline);
  const parentValidation = publish.indexOf("validateProviderMaterializationParent(parent)", parentCreation);
  const diagnosticPrecheck = publish.indexOf("providerTargetExistsNoFollowForDiagnostics(target)", parentValidation);
  const exclusiveFlag = publish.indexOf("OsConstants.O_EXCL", diagnosticPrecheck);
  const noFollowFlag = publish.indexOf("OsConstants.O_NOFOLLOW", exclusiveFlag);
  const exclusiveCreate = publish.indexOf(
    "Os.open(target.getAbsolutePath(), outputFlags, 0000)",
    noFollowFlag
  );
  const targetCreated = publish.indexOf("targetCreated = true;", exclusiveCreate);
  const initialTargetFstat = publish.indexOf("ownedTargetIdentity = Os.fstat(output.getFD());", targetCreated);
  const emptyTargetValidation = publish.indexOf("ownedTargetIdentity.st_size != 0L", initialTargetFstat);
  const mode000CreationValidation = publish.indexOf("permissionBits(ownedTargetIdentity) != 0", emptyTargetValidation);
  const boundedCopy = publish.indexOf("copyProviderMaterializationSource(", mode000CreationValidation);
  const fileSync = publish.indexOf("output.getFD().sync();", boundedCopy);
  const mode000IdentityValidation = publish.indexOf(
    "validateOwnedProviderMaterializationTarget(",
    fileSync
  );
  const mode000ValidationArgument = publish.indexOf("0000", mode000IdentityValidation);
  const postCopyDeadline = publish.indexOf('"after private copy and fsync"', mode000ValidationArgument);
  const permissionPromotion = publish.indexOf(
    "Os.fchmod(output.getFD(), PROVIDER_TARGET_PRIVATE_MODE);",
    postCopyDeadline
  );
  const promotedFileSync = publish.indexOf("output.getFD().sync();", permissionPromotion);
  const mode0600IdentityValidation = publish.indexOf(
    "validateOwnedProviderMaterializationTarget(",
    promotedFileSync
  );
  const mode0600ValidationArgument = publish.indexOf(
    "PROVIDER_TARGET_PRIVATE_MODE",
    mode0600IdentityValidation
  );
  const postPermissionDeadline = publish.indexOf('"after permission promotion"', mode0600ValidationArgument);
  const closedIdentityValidation = publish.indexOf(
    "validateClosedProviderMaterializationTarget(",
    postPermissionDeadline
  );
  const finalReceiptDeadline = publish.indexOf('"before materialization receipt"', closedIdentityValidation);
  const protectedFileTracking = publish.indexOf("providerMaterializedFiles.add(relative);", finalReceiptDeadline);
  const cleanupOwnedTarget = publish.indexOf(
    "deleteProviderTargetIfOwnedRegularInode(target, ownedTargetIdentity);",
    protectedFileTracking
  );
  const cleanupDirectories = publish.indexOf(
    "pruneCreatedProviderDirectories(createdDirectories);",
    cleanupOwnedTarget
  );
  check(
    publishStart >= 0 && publishEnd > publishStart && extensionGuard >= 0 && mainTry > extensionGuard &&
      initialDeadline > mainTry && parentCreation > initialDeadline &&
      parentValidation > parentCreation && diagnosticPrecheck > parentValidation &&
      exclusiveFlag > diagnosticPrecheck && noFollowFlag > exclusiveFlag &&
      exclusiveCreate > noFollowFlag && targetCreated > exclusiveCreate &&
      initialTargetFstat > targetCreated && emptyTargetValidation > initialTargetFstat &&
      mode000CreationValidation > emptyTargetValidation && boundedCopy > mode000CreationValidation &&
      fileSync > boundedCopy && mode000IdentityValidation > fileSync &&
      mode000ValidationArgument > mode000IdentityValidation && postCopyDeadline > mode000ValidationArgument &&
      permissionPromotion > postCopyDeadline && promotedFileSync > permissionPromotion &&
      mode0600IdentityValidation > promotedFileSync && mode0600ValidationArgument > mode0600IdentityValidation &&
      postPermissionDeadline > mode0600ValidationArgument && closedIdentityValidation > postPermissionDeadline &&
      finalReceiptDeadline > closedIdentityValidation && protectedFileTracking > finalReceiptDeadline &&
      cleanupOwnedTarget > protectedFileTracking && cleanupDirectories > cleanupOwnedTarget &&
      !publish.includes("Os.link(") && !publish.includes("if (existedBeforeExclusiveOpen"),
    "Plugin workspace provider publication must use SELinux-safe direct O_EXCL mode-000 creation, verified copy, mode-0600 promotion, final deadline, and owned rollback"
  );

  const ownedValidationStart = workspaceArchive.indexOf(
    "private void validateOwnedProviderMaterializationTarget("
  );
  const closedValidationStart = workspaceArchive.indexOf(
    "private void validateClosedProviderMaterializationTarget(",
    ownedValidationStart
  );
  const targetPathValidationStart = workspaceArchive.indexOf(
    "private void validateProviderMaterializationTargetPath(",
    closedValidationStart
  );
  const copyStart = workspaceArchive.indexOf(
    "private static void copyProviderMaterializationSource(",
    targetPathValidationStart
  );
  const ownedValidation = workspaceArchive.slice(ownedValidationStart, closedValidationStart);
  const closedValidation = workspaceArchive.slice(closedValidationStart, targetPathValidationStart);
  const targetPathValidation = workspaceArchive.slice(targetPathValidationStart, copyStart);
  check(
    ownedValidationStart >= 0 && closedValidationStart > ownedValidationStart &&
      targetPathValidationStart > closedValidationStart && copyStart > targetPathValidationStart &&
      ownedValidation.includes("descriptorIdentity = Os.fstat(descriptor);") &&
      ownedValidation.includes("pathIdentity = Os.lstat(target.getAbsolutePath());") &&
      ownedValidation.includes("sameOwnedRegularInode(ownedIdentity, descriptorIdentity)") &&
      ownedValidation.includes("sameOwnedRegularInode(ownedIdentity, pathIdentity)") &&
      ownedValidation.includes("descriptorIdentity.st_size != expectedBytes") &&
      ownedValidation.includes("pathIdentity.st_size != expectedBytes") &&
      ownedValidation.includes("permissionBits(descriptorIdentity) != expectedMode") &&
      ownedValidation.includes("permissionBits(pathIdentity) != expectedMode") &&
      ownedValidation.includes("openedDescriptorPath(descriptor)") &&
      ownedValidation.includes("validateProviderMaterializationTargetPath(target, parent, expectedRuntimePath)") &&
      closedValidation.includes("sameOwnedRegularInode(ownedIdentity, pathIdentity)") &&
      closedValidation.includes("pathIdentity.st_size != expectedBytes") &&
      closedValidation.includes("permissionBits(pathIdentity) != expectedMode") &&
      closedValidation.includes("validateProviderMaterializationTargetPath(target, parent, expectedRuntimePath)") &&
      targetPathValidation.includes("validateProviderMaterializationParent(parent)") &&
      targetPathValidation.includes("target.getCanonicalFile().getAbsolutePath()") &&
      targetPathValidation.includes("sameAndroidCredentialAliasedPath(expectedRuntimePath, canonicalTarget)") &&
      targetPathValidation.includes("runtimeSandboxRoot.getCanonicalPath()"),
    "Plugin provider materialization must pin fd/path/parent/canonical identity and exact size/mode before receipt"
  );

  const sameOwnedStart = workspaceArchive.indexOf("private static boolean sameOwnedRegularInode(");
  const permissionBitsStart = workspaceArchive.indexOf("private static int permissionBits(", sameOwnedStart);
  const cleanupStart = workspaceArchive.indexOf("private static void deleteProviderTargetIfOwnedRegularInode(");
  const cleanupEnd = workspaceArchive.indexOf("private void pruneCreatedProviderDirectories(", cleanupStart);
  const sameOwned = workspaceArchive.slice(sameOwnedStart, permissionBitsStart);
  const cleanup = workspaceArchive.slice(cleanupStart, cleanupEnd);
  check(
    sameOwnedStart >= 0 && permissionBitsStart > sameOwnedStart &&
      cleanupStart > permissionBitsStart && cleanupEnd > cleanupStart &&
      sameOwned.includes("OsConstants.S_ISREG(expected.st_mode)") &&
      sameOwned.includes("OsConstants.S_ISREG(actual.st_mode)") &&
      sameOwned.includes("expected.st_dev == actual.st_dev") &&
      sameOwned.includes("expected.st_ino == actual.st_ino") &&
      !sameOwned.includes("st_size") &&
      cleanup.includes("StructStat current = Os.lstat(target.getAbsolutePath());") &&
      cleanup.includes("if (sameOwnedRegularInode(identity, current))") &&
      cleanup.includes("Os.remove(target.getAbsolutePath());"),
    "Plugin provider rollback must remove only the still-owned regular dev/inode, regardless of partial size"
  );
  const deadlineStart = workspaceArchive.indexOf("static void validateProviderMaterializationDeadline(");
  const deadlineEnd = workspaceArchive.indexOf("private static void requireProviderMaterializationDeadline(", deadlineStart);
  const deadline = workspaceArchive.slice(deadlineStart, deadlineEnd);
  check(
    deadlineStart >= 0 && deadlineEnd > deadlineStart &&
      deadline.includes("deadlineElapsedRealtimeMs <= 0L") &&
      deadline.includes("nowElapsedRealtimeMs >= deadlineElapsedRealtimeMs") &&
      deadline.includes("ProviderMaterializationDeadlineExceededException"),
    "Plugin workspace materialization deadline must be absolute, positive, and fail closed at expiry"
  );
  const tombstoneStart = workspaceArchive.indexOf("private void writeTombstoneManifest(");
  const outputStart = workspaceArchive.indexOf("private void writeDirectory(", tombstoneStart);
  const exportableStart = workspaceArchive.indexOf("private boolean hasExportableWorkspaceEntry(", outputStart);
  const extensionStart = workspaceArchive.indexOf("static boolean isSupportedProviderMaterializationPath(", exportableStart);
  const tombstone = workspaceArchive.slice(tombstoneStart, outputStart);
  const output = workspaceArchive.slice(outputStart, exportableStart);
  const exportable = workspaceArchive.slice(exportableStart, extensionStart);
  check(
    tombstone.includes("if (providerMaterializedFiles.contains(relative))") &&
      tombstone.includes("providerProtectedTombstoneCount++") &&
      output.includes("providerMaterializedFiles.contains(relative)") &&
      output.includes("providerProtectedOutputFileCount++") &&
      output.includes("providerMaterializedDirectories.contains(relative)") &&
      output.includes("providerProtectedOutputDirectoryCount++") &&
      exportable.includes("!providerMaterializedFiles.contains(relative)") &&
      exportable.includes("!providerMaterializedDirectories.contains(relative)"),
    "Plugin workspace commit must exclude provider materializations, empty shells, and tombstones"
  );
  const providerFailureStop = service.indexOf(
    'moduleSourceProviderSession.stop("Node.js runtime plugin execution finished after failure.");'
  );
  const workspaceCloseAfterProviderStop = service.indexOf("workspaceSession.close();", providerFailureStop);
  check(
    workspaceArchive.includes("public synchronized void close()") &&
      providerFailureStop >= 0 && workspaceCloseAfterProviderStop > providerFailureStop,
    "Plugin failure cleanup must quiesce provider transport before synchronized workspace deletion"
  );

  const requestStart = cpp.indexOf("function __autojs6_request_module_source(readable, operation)");
  const requestEnd = cpp.indexOf("function __autojs6_plaintext_module_source_record(", requestStart);
  const request = cpp.slice(requestStart, requestEnd);
  check(
    request.includes('operation === "materialize_missing_plaintext"') &&
      request.includes('operation === "resolve_missing_candidate"') &&
      request.includes('const cacheKey = requestOperation + "\\n" + String(readable || "")') &&
      request.includes('const resolveMissingCandidate = requestOperation === "resolve_missing_candidate"') &&
      request.includes("if (!materializeMissingPlaintext)") &&
      request.includes("if (candidateIdentity && resolveMissingCandidate)") &&
      request.includes("if (!candidateIdentity && !resolveMissingCandidate)") &&
      request.includes("version: 2") &&
      request.includes('operation: materializeMissingPlaintext ? "materialize_missing_plaintext" : "resolve"') &&
      request.includes('if (status === "materialized_plaintext")') &&
      request.includes("__autojs6_module_source_provider_file_identity(fs, materializedReadable)") &&
      request.includes("String(response.resolvedPath || \"\") !== String(readable)") &&
      request.includes("resolveMissingCandidate &&") &&
      request.includes("__autojs6_same_authorized_canonical_path(") &&
      request.includes("requestId: id") && request.includes("deadline: requestDeadline"),
    "Native provider-v2 request must isolate operation-aware cache entries, resolve exact missing candidates first, and pin canonical materialized/decrypted receipts"
  );

  const probeStart = cpp.indexOf("function __autojs6_probe_missing_module_candidate(resolved, allowEsm)");
  const probeEnd = cpp.indexOf("function __autojs6_module_metadata_record(", probeStart);
  const probe = cpp.slice(probeStart, probeEnd);
  const probeExtensionGuard = probe.indexOf("!extension ||");
  const probeSupportedExtension = probe.indexOf(
    "__autojs6_supported_local_module_extension(extension)",
    probeExtensionGuard,
  );
  const probeResolveDispatch = probe.indexOf('"resolve_missing_candidate"', probeSupportedExtension);
  const probeResolveNotFound = probe.indexOf('resolveResult.status === "not_found"', probeResolveDispatch);
  const probeDecryptedDirect = probe.indexOf('resolveResult.status === "decrypted"', probeResolveNotFound);
  const probeNotEncryptedOnly = probe.indexOf('resolveResult.status !== "not_encrypted"', probeDecryptedDirect);
  const probeMaterializeDispatch = probe.indexOf('"materialize_missing_plaintext"', probeNotEncryptedOnly);
  const probeMaterializedOnly = probe.indexOf(
    'materializedResult.status !== "materialized_plaintext"',
    probeMaterializeDispatch,
  );
  const probeRevalidate = probe.indexOf(
    "__autojs6_validate_runtime_module_path(exactCandidate, allowEsm)",
    probeMaterializedOnly,
  );
  check(
    probeStart >= 0 && probeEnd > probeStart && probeExtensionGuard >= 0 &&
      probeSupportedExtension > probeExtensionGuard && probeResolveDispatch > probeSupportedExtension &&
      probeResolveNotFound > probeResolveDispatch && probeDecryptedDirect > probeResolveNotFound &&
      probeNotEncryptedOnly > probeDecryptedDirect && probeMaterializeDispatch > probeNotEncryptedOnly &&
      probeMaterializedOnly > probeMaterializeDispatch && probeRevalidate > probeMaterializedOnly &&
      (probe.match(/__autojs6_request_module_source\(/g) || []).length === 2,
    "Native missing resolver must probe only supported exact candidates, accept decrypted directly, and materialize only after not_encrypted"
  );
  const metadataStart = probeEnd;
  const metadataEnd = cpp.indexOf("function __autojs6_runtime_module_record(", metadataStart);
  const metadata = cpp.slice(metadataStart, metadataEnd);
  const runtimeEnd = cpp.indexOf("function __autojs6_module_not_found(", metadataEnd);
  const runtime = cpp.slice(metadataEnd, runtimeEnd);
  check(
    metadata.includes("__autojs6_probe_missing_module_candidate(resolved, allowEsm)") &&
      metadata.includes('providerResult.status === "materialized_plaintext"') &&
      metadata.includes("__autojs6_plaintext_module_source_record(readable, allowEsm, providerResult)") &&
      metadata.indexOf("__autojs6_plaintext_module_source_record(readable, allowEsm, providerResult)") <
        metadata.indexOf('fs.readFileSync(readable, "utf8")'),
    "Native missing package metadata must use the exact provider probe and send materialized plaintext through verified-read/private preparation"
  );
  const revalidateStart = cpp.indexOf("function __autojs6_revalidate_plaintext_module_path(");
  const revalidateEnd = cpp.indexOf("function __autojs6_enforce_runtime_module_budget(", revalidateStart);
  const revalidate = cpp.slice(revalidateStart, revalidateEnd);
  const metadataProviderFailure = metadata.indexOf("error.__autojs6ModuleSourceProviderRecorded");
  const metadataMissingFallback = metadata.indexOf('error.code === "ENOENT"', metadataProviderFailure);
  const runtimeProviderFailure = runtime.indexOf("error.__autojs6ModuleSourceProviderRecorded");
  const runtimeMissingFallback = runtime.indexOf('error.code === "ENOENT"', runtimeProviderFailure);
  const patternCompareStart = cpp.indexOf("function __autojs6_package_pattern_key_compare(");
  const patternMatchStart = cpp.indexOf("function __autojs6_best_package_pattern_match(", patternCompareStart);
  const patternApplyStart = cpp.indexOf("function __autojs6_apply_package_pattern_target(", patternMatchStart);
  const patternCompare = cpp.slice(patternCompareStart, patternMatchStart);
  const patternMatch = cpp.slice(patternMatchStart, patternApplyStart);
  const exportsKeyValidationStart = cpp.indexOf("function __autojs6_validate_package_exports_keys(");
  const importsKeyValidationStart = cpp.indexOf(
    "function __autojs6_validate_package_imports_keys(",
    exportsKeyValidationStart
  );
  const keyValidationEnd = cpp.indexOf("function __autojs6_package_pattern_key_compare(", importsKeyValidationStart);
  const exportsKeyValidation = cpp.slice(exportsKeyValidationStart, importsKeyValidationStart);
  const importsKeyValidation = cpp.slice(importsKeyValidationStart, keyValidationEnd);
  const exportsStart = cpp.indexOf("function __autojs6_package_exports_target(", patternApplyStart);
  const importsStart = cpp.indexOf("function __autojs6_package_imports_target(", exportsStart);
  const exportsBody = cpp.slice(exportsStart, importsStart);
  const importsEnd = cpp.indexOf("function __autojs6_resolve_package_exports(", importsStart);
  const importsBody = cpp.slice(importsStart, importsEnd);
  check(
    revalidateStart >= 0 && revalidateEnd > revalidateStart &&
      revalidate.includes("if (!current) {") &&
      revalidate.includes("Module-source candidate disappeared or changed type before plaintext read.") &&
      !revalidate.includes("if (!current) return null") &&
      metadataProviderFailure >= 0 && metadataMissingFallback > metadataProviderFailure &&
      runtimeProviderFailure >= 0 && runtimeMissingFallback > runtimeProviderFailure,
    "Native plaintext revalidation and metadata/runtime catches must never downgrade a positive provider response to candidate absence"
  );
  check(
    patternCompareStart >= 0 && patternMatchStart > patternCompareStart && patternApplyStart > patternMatchStart &&
      patternCompare.includes("if (leftBaseLength > rightBaseLength) return -1;") &&
      patternCompare.includes("if (left.length > right.length) return -1;") &&
      patternMatch.includes("__autojs6_package_pattern_key_compare(patternKey, bestKey) < 0") &&
      exportsBody.includes('__autojs6_best_package_pattern_match(exportsValue, key, moduleName, "exports")') &&
      importsBody.includes('__autojs6_best_package_pattern_match(importsValue, specifier, moduleName, "imports")'),
    "Native package exports/imports wildcard resolution must select the Node-compatible most-specific matching pattern"
  );
  check(
    exportsKeyValidationStart >= 0 && importsKeyValidationStart > exportsKeyValidationStart &&
      keyValidationEnd > importsKeyValidationStart &&
      exportsKeyValidation.includes("subpathShape !== currentSubpathShape") &&
      exportsKeyValidation.includes("cannot mix subpath keys and condition keys") &&
      importsKeyValidation.includes('key.indexOf("#") !== 0') &&
      importsKeyValidation.includes('key.indexOf("#/") === 0') &&
      exportsBody.includes("__autojs6_validate_package_exports_keys(exportsValue, moduleName)") &&
      importsBody.includes("__autojs6_validate_package_imports_keys(importsValue, moduleName)"),
    "Native package metadata must reject mixed exports shapes and invalid imports-map keys before target selection"
  );
  check(
    cpp.includes("missingCandidateRequestCount") && cpp.includes("materializedCount") &&
      cpp.includes("materializedSourceBytes"),
    "Native CJS/ESM/package-metadata missing-provider diagnostics are incomplete"
  );
}

function verifyTypeScriptStripperCatalog(catalog, stripper, errorCodes) {
  const extensionBlock = stripper.match(/TYPESCRIPT_EXTENSIONS\s*=\s*\r?\n?\s*Arrays\.asList\(([^;]+)\);/);
  check(extensionBlock, "Plugin TypeScript extension registry is missing");
  const stripperExtensions = [...extensionBlock[1].matchAll(/"([a-z0-9]+)"/g)].map((match) => match[1]);
  assertUnique(stripperExtensions, "Plugin TypeScript extension registry");
  sameSet(
    stripperExtensions,
    catalog.routing.typeScriptModuleExtensions,
    "Plugin TypeScript extension registry versus catalog routing.typeScriptModuleExtensions"
  );
  sameSet(
    catalog.routing.unsupportedTypeScriptModuleExtensions,
    ["tsx"],
    "Plugin unsupported TypeScript extension registry"
  );
  check(stripper.includes('if ("tsx".equals(extension))'), "Plugin TypeScript entry dispatch no longer rejects catalogued TSX");
  check(
    stripper.includes('"tsx".equalsIgnoreCase(extension(sourceName))'),
    "Plugin TypeScript unsupported-extension query no longer recognizes catalogued TSX"
  );

  const errorConstants = Object.create(null);
  for (const match of stripper.matchAll(/static final String (ERROR_[A-Z_]+)\s*=\s*\r?\n?\s*"([A-Z0-9_]+)";/g)) {
    errorConstants[match[1]] = match[2];
  }
  const catalogErrorsByName = Object.fromEntries(catalog.errors.constants.map((item) => [item.name, item.code]));
  const expectedExtensionError = catalogErrorsByName.TYPESCRIPT_UNSUPPORTED_EXTENSION;
  const expectedSyntaxError = catalogErrorsByName.UNSUPPORTED_TYPESCRIPT_SYNTAX;
  check(expectedExtensionError, "Catalog TYPESCRIPT_UNSUPPORTED_EXTENSION error is missing");
  check(expectedSyntaxError, "Catalog UNSUPPORTED_TYPESCRIPT_SYNTAX error is missing");
  check(
    errorConstants.ERROR_UNSUPPORTED_EXTENSION === expectedExtensionError,
    `Plugin TypeScript extension error drift: expected '${expectedExtensionError}', actual '${errorConstants.ERROR_UNSUPPORTED_EXTENSION}'`
  );
  check(
    errorConstants.ERROR_UNSUPPORTED_SYNTAX === expectedSyntaxError,
    `Plugin TypeScript syntax error drift: expected '${expectedSyntaxError}', actual '${errorConstants.ERROR_UNSUPPORTED_SYNTAX}'`
  );
  check(errorCodes.has(errorConstants.ERROR_UNSUPPORTED_EXTENSION), "Plugin TypeScript extension error is not catalogued");
  check(errorCodes.has(errorConstants.ERROR_UNSUPPORTED_SYNTAX), "Plugin TypeScript syntax error is not catalogued");
  check(
    stripper.includes('DIAGNOSTIC_SCOPE_MODULE_SOURCES = "module_sources"') &&
      stripper.includes('DIAGNOSTIC_SCOPE_RUNTIME_MODULE_SOURCES = "runtime_module_sources"'),
    "Plugin TypeScript preloaded-source diagnostic scopes drifted"
  );
  check(
    stripper.includes("for (Map.Entry<String, String> entry : sources.entrySet())") &&
      stripper.includes("Result result = stripIfTypeScript(sourceName, source);") &&
      stripper.includes("preparedSources.put(sourceName, result.source());"),
    "Plugin TypeScript source-map preparation no longer preserves ordered names while reusing catalogued routing"
  );
  check(
    stripper.includes("static final int SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT = 16;") &&
      stripper.includes("if (typeScriptCount < SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT)") &&
      stripper.includes("int detailCount = Math.min(typeScriptCount, SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT);") &&
      stripper.includes('prefix + "detail_truncated_count"'),
    "Plugin TypeScript source-map diagnostic details are no longer capped with explicit truncation accounting"
  );
  check(
    stripper.includes("static final int DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256;") &&
      stripper.includes('private static final String DIAGNOSTIC_SOURCE_NAME_SEPARATOR = "...";') &&
      stripper.includes("int headEnd = safeUtf16PrefixEnd(normalized, headBudget);") &&
      stripper.includes("Character.isLowSurrogate(normalized.charAt(tailStart))") &&
      stripper.includes("Character.isHighSurrogate(normalized.charAt(tailStart - 1))") &&
      stripper.includes("Character.isHighSurrogate(value.charAt(end - 1))") &&
      stripper.includes("Character.isLowSurrogate(value.charAt(end))"),
    "Plugin TypeScript diagnostic source-name representation is not bounded or UTF-16 boundary safe"
  );
  check(
    stripper.includes('diagnostics.put("embedded_script.typescript.source", diagnosticSourceName.value());') &&
      stripper.includes('values.put("embedded_script.typescript.source", diagnosticSourceName.value());') &&
      stripper.includes('prefix + "name_truncated"') &&
      stripper.includes('result.diagnostics().get("embedded_script.typescript.source_truncated")'),
    "Plugin TypeScript entry, failure, and source-map diagnostics do not consistently use bounded names and truncation markers"
  );
  check(
    !stripper.includes('diagnostics.put("embedded_script.typescript.source", sourceName);') &&
      !stripper.includes('values.put("embedded_script.typescript.source", sourceName);') &&
      !stripper.includes('sourceDiagnostics.put(prefix + "name", sourceName);'),
    "Plugin TypeScript diagnostics still expose an unbounded raw source name"
  );
  check(
    (stripper.match(/\+ diagnosticSourceName\.value\(\)/g) || []).length >= 2,
    "Plugin unsupported-TypeScript exception messages no longer use bounded diagnostic source names"
  );

  const newlineRewriteStart = stripper.indexOf(
    "private static String replaceWithNewlinePadding(Pattern pattern, String source)"
  );
  const braceRewriteStart = stripper.indexOf(
    "private static String removeBraceDeclarations(String source, Pattern pattern)",
    newlineRewriteStart
  );
  const functionRewriteStart = stripper.indexOf(
    "private static String stripFunctionTypeParameters(String source)",
    braceRewriteStart
  );
  const annotationsStart = stripper.indexOf(
    "private static String stripTypeAnnotations(String source)",
    functionRewriteStart
  );
  const groupsStart = stripper.indexOf(
    "private static String replaceGroups(Pattern pattern, String source, Object... parts)",
    annotationsStart
  );
  const matcherStart = stripper.indexOf(
    "private static Matcher matcherAgainstCode(Pattern pattern, String source)",
    groupsStart
  );
  const appendGroupStart = stripper.indexOf(
    "private static void appendOriginalGroup(",
    matcherStart
  );
  const matchingBraceStart = stripper.indexOf(
    "private static int matchingBraceEnd(String source, int braceStart)",
    appendGroupStart
  );
  const newlineRewrite = stripper.slice(newlineRewriteStart, braceRewriteStart);
  const braceRewrite = stripper.slice(braceRewriteStart, functionRewriteStart);
  const functionRewrite = stripper.slice(functionRewriteStart, annotationsStart);
  const groupsRewrite = stripper.slice(groupsStart, matcherStart);
  const matcherHelpers = stripper.slice(matcherStart, matchingBraceStart);
  check(
    newlineRewriteStart >= 0 && braceRewriteStart > newlineRewriteStart &&
      functionRewriteStart > braceRewriteStart && annotationsStart > functionRewriteStart &&
      groupsStart > annotationsStart && matcherStart > groupsStart &&
      appendGroupStart > matcherStart && matchingBraceStart > appendGroupStart &&
      newlineRewrite.includes("Matcher matcher = matcherAgainstCode(pattern, source);") &&
      newlineRewrite.includes("result.append(source, cursor, matcher.start());") &&
      newlineRewrite.includes("source.substring(matcher.start(), matcher.end())") &&
      newlineRewrite.includes("result.append(source, cursor, source.length());") &&
      !newlineRewrite.includes("appendTail") && !newlineRewrite.includes("appendReplacement") &&
      braceRewrite.includes("String masked = maskNonCodeSameLength(source);") &&
      braceRewrite.includes("Matcher matcher = pattern.matcher(masked);") &&
      braceRewrite.includes("masked.indexOf('{', matcher.end())") &&
      braceRewrite.includes("source.substring(matcher.start(), end + 1)") &&
      functionRewrite.includes("Matcher matcher = matcherAgainstCode(FUNCTION_TYPE_PARAMETERS_PATTERN, source);") &&
      functionRewrite.includes("appendOriginalGroup(result, source, matcher, 1)") &&
      functionRewrite.includes("result.append(source, cursor, source.length());") &&
      groupsRewrite.includes("Matcher matcher = matcherAgainstCode(pattern, source);") &&
      groupsRewrite.includes("result.append(source, cursor, matcher.start());") &&
      groupsRewrite.includes("appendOriginalGroup(result, source, matcher, (Integer) part)") &&
      groupsRewrite.includes("result.append(source, cursor, source.length());") &&
      matcherHelpers.includes("return pattern.matcher(maskNonCodeSameLength(source));") &&
      matcherHelpers.includes("String masked = maskNonCode(source);") &&
      matcherHelpers.includes("masked.length() != source.length()") &&
      matcherHelpers.includes("output.append(source, start, end);"),
    "Plugin TypeScript erasure must match only same-length masked code and rebuild exclusively from original source indices"
  );
}

function verifyStartupEnvironmentPolicy(service, startupEnvironmentPolicy, cpp) {
  const expectedDenylist = [
    "NODE_OPTIONS",
    "NODE_INSPECT_RESUME_ON_START",
    "NODE_COMPILE_CACHE",
    "NODE_DISABLE_COMPILE_CACHE"
  ];
  const denylistBlock = startupEnvironmentPolicy.match(
    /DENIED_ENVIRONMENT_NAMES\s*=\s*Collections\.unmodifiableList\(\s*Arrays\.asList\(([\s\S]*?)\)\s*\);/
  );
  check(denylistBlock, "Plugin Node startup-environment denylist declaration is missing");
  const actualDenylist = [...denylistBlock[1].matchAll(/"([A-Z0-9_]+)"/g)].map((match) => match[1]);
  check(
    JSON.stringify(actualDenylist) === JSON.stringify(expectedDenylist),
    `Plugin Node startup-environment denylist drift: expected ${expectedDenylist.join(",")}, actual ${actualDenylist.join(",")}`
  );
  check(
    startupEnvironmentPolicy.includes("name.toUpperCase(Locale.ROOT)") &&
      startupEnvironmentPolicy.includes("DENIED_ENVIRONMENT_NAMES.contains"),
    "Plugin Node startup-environment denylist must be locale-stable and case-insensitive"
  );
  check(
    startupEnvironmentPolicy.includes("for (Map.Entry<String, String> entry : environment.entrySet())") &&
      startupEnvironmentPolicy.includes("sanitized.put(name, entry.getValue() == null ? \"\" : entry.getValue());"),
    "Plugin startup-environment sanitizer no longer preserves allowed names and insertion order"
  );
  check(
    startupEnvironmentPolicy.includes("static final int FILTERED_DIAGNOSTIC_DETAIL_LIMIT = 16;") &&
      startupEnvironmentPolicy.includes("if (filteredCount < FILTERED_DIAGNOSTIC_DETAIL_LIMIT)") &&
      startupEnvironmentPolicy.includes("int detailCount = Math.min(filteredCount, FILTERED_DIAGNOSTIC_DETAIL_LIMIT);") &&
      startupEnvironmentPolicy.includes('"embedded_script.runtime_plugin.startup_env.detail_truncated_count"'),
    "Plugin startup-environment filtered-key diagnostics are no longer bounded with truncation accounting"
  );
  check(
    service.includes("return NodeStartupEnvironmentPolicy.sanitize(environment);"),
    "Plugin service startup-environment preparation is not delegated to the plugin-owned policy"
  );

  const methodStart = service.indexOf("private Bundle runScriptActive(Bundle request, INodeJsRuntimeCallback callback)");
  const methodEnd = service.indexOf("private Bundle runtimeInfoBundle()", methodStart);
  check(methodStart >= 0 && methodEnd > methodStart, "Plugin runScriptActive environment boundary is missing");
  const active = service.slice(methodStart, methodEnd);
  const workspaceMapping = active.indexOf("env = workspaceSession.mapEnvironment(env);");
  const preparation = active.indexOf("prepareNodeStartupEnvironmentForNative(env);");
  const replacement = active.indexOf("env = startupEnvironment.environment();");
  const nativeDispatch = active.indexOf("NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(");
  const diagnostics = active.indexOf("nativePayloadFromMap(startupEnvironment.diagnostics())", nativeDispatch);
  check(workspaceMapping >= 0, "Plugin environment workspace mapping is missing");
  check(preparation > workspaceMapping, "Plugin startup-environment sanitization must follow workspace mapping");
  check(replacement > preparation, "Plugin sanitized startup-environment replacement is missing or out of order");
  check(nativeDispatch > replacement, "Plugin startup-environment sanitization must happen before native dispatch");
  check(diagnostics > nativeDispatch, "Plugin startup-environment diagnostics are not appended after native dispatch");
  check(
    active.includes("                    runtimeModuleSources,\n                    env,"),
    "Native dispatch is not consuming the sanitized environment map"
  );

  const nativeFilterStart = cpp.indexOf("function __autojs6_internal_compile_cache_env_key(key)");
  const nativeFilterEnd = cpp.indexOf("function __autojs6_compile_cache_status_name", nativeFilterStart);
  check(nativeFilterStart >= 0 && nativeFilterEnd > nativeFilterStart, "Native Node startup-environment filter boundary is missing");
  const nativeFilters = cpp.slice(nativeFilterStart, nativeFilterEnd);
  for (const name of expectedDenylist) {
    check(nativeFilters.includes(`key === "${name}"`), `Native Node startup-environment filter is missing ${name}`);
  }
}

function parseLock(source) {
  const values = Object.create(null);
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const separator = line.indexOf("=");
    check(separator > 0, `Malformed release lock line '${line}'`);
    values[line.slice(0, separator)] = line.slice(separator + 1);
  }
  return values;
}

function verifyWorkspaceArchivePathMapping(workspaceArchive) {
  const hostStart = workspaceArchive.indexOf("String mapHostPathToRuntime(String value) {");
  const runtimeStart = workspaceArchive.indexOf("String mapRuntimePathToHost(String value) {", hostStart);
  const environmentStart = workspaceArchive.indexOf("Map<String, String> mapEnvironment(", runtimeStart);
  check(hostStart >= 0 && runtimeStart > hostStart && environmentStart > runtimeStart,
    "Plugin workspace path-mapping boundaries are missing");
  const hostMapping = workspaceArchive.slice(hostStart, runtimeStart);
  const runtimeMapping = workspaceArchive.slice(runtimeStart, environmentStart);
  check(
    hostMapping.includes("mapContainedPathAcrossAndroidCredentialAlias(") &&
      hostMapping.includes("requestedSandboxRoot,") &&
      hostMapping.includes("runtimeSandboxRoot.getAbsolutePath()"),
    "Plugin Host-to-runtime workspace mapping does not use the strict Android credential-data alias seam"
  );
  check(
    runtimeMapping.includes("mapContainedPathAcrossAndroidCredentialAlias(") &&
      runtimeMapping.includes("runtimeSandboxRoot.getAbsolutePath(),") &&
      runtimeMapping.includes("requestedSandboxRoot"),
    "Plugin runtime-to-Host workspace mapping does not use the symmetric Android credential-data alias seam"
  );

  const mappingStart = workspaceArchive.indexOf(
    "static String mapContainedPathAcrossAndroidCredentialAlias("
  );
  const aliasStart = workspaceArchive.indexOf(
    "private static String androidCredentialDataComparablePath(String value)",
    mappingStart
  );
  const containsStart = workspaceArchive.indexOf(
    "private static boolean containsPath(String root, String candidate)",
    aliasStart
  );
  check(mappingStart >= 0 && aliasStart > mappingStart && containsStart > aliasStart,
    "Plugin workspace Android credential-data alias implementation is missing");
  const mapping = workspaceArchive.slice(mappingStart, aliasStart);
  const alias = workspaceArchive.slice(aliasStart, containsStart);
  check(
    mapping.includes("String normalizedCandidate = tryNormalizeAbsolutePath(value);") &&
      mapping.includes("String normalizedSourceRoot = tryNormalizeAbsolutePath(sourceRoot);") &&
      mapping.includes("String normalizedDestinationRoot = tryNormalizeAbsolutePath(destinationRoot);") &&
      mapping.includes("String comparableSourceRoot = androidCredentialDataComparablePath(normalizedSourceRoot);") &&
      mapping.includes("String comparableCandidate = androidCredentialDataComparablePath(normalizedCandidate);") &&
      mapping.includes("comparableSourceRoot != null && comparableCandidate != null") &&
      mapping.includes("if (!containsPath(containmentRoot, containmentCandidate)) {") &&
      mapping.includes("String relative = relativePath(containmentRoot, containmentCandidate);") &&
      mapping.includes('normalizedDestinationRoot + "/" + relative'),
    "Plugin workspace alias mapping must normalize both sides, preserve containment, and map only the exact relative tail"
  );
  check(
    alias.includes('final String userZeroPrefix = "/data/user/0/";') &&
      alias.includes('final String legacyDataPrefix = "/data/data/";') &&
      alias.includes('packageName.matches("^[A-Za-z][A-Za-z0-9_]*(?:\\\\.[A-Za-z][A-Za-z0-9_]*)+$")') &&
      alias.includes("return userZeroPrefix + relative;"),
    "Plugin workspace alias normalization must admit only exact owner-user credential-data namespaces and application ids"
  );
}

function lockText(digest, gitBase) {
  return [
    "# Immutable Node.js capability catalog distribution lock.",
    "format=1",
    `schema=${LOCK_SCHEMA}`,
    `catalog.version=${EXPECTED_VERSION}`,
    `file=${RELEASE_FILE}`,
    `sha256=${digest}`,
    `source.file=${SOURCE_FILE}`,
    `source.owner=${SOURCE_REPOSITORY}`,
    `source.repository=${SOURCE_REPOSITORY}`,
    `source.gitBase=${gitBase}`,
    `source.sha256=${digest}`,
    ""
  ].join("\n");
}

function gitBase(projectRoot) {
  const value = childProcess.execFileSync("git", ["rev-parse", "HEAD"], {cwd: projectRoot, encoding: "utf8"}).trim();
  check(/^[0-9a-f]{40}$/.test(value), `Invalid source git base '${value}'`);
  return value;
}

function stageRelease(projectRoot, bytes, digest) {
  const releaseDir = path.join(projectRoot, RELEASE_ROOT);
  check(!fs.existsSync(releaseDir), `Refusing to overwrite immutable release directory ${slash(path.relative(projectRoot, releaseDir))}`);
  fs.mkdirSync(releaseDir, {recursive: true});
  fs.writeFileSync(path.join(releaseDir, RELEASE_FILE), bytes);
  fs.writeFileSync(path.join(releaseDir, LOCK_FILE), lockText(digest, gitBase(projectRoot)), "utf8");
}

function verifyRelease(projectRoot, canonicalBytes, digest) {
  const releaseDir = path.join(projectRoot, RELEASE_ROOT);
  const releasePath = path.join(releaseDir, RELEASE_FILE);
  const lockPath = path.join(releaseDir, LOCK_FILE);
  check(fs.existsSync(releasePath), `Immutable catalog release is missing: ${slash(path.relative(projectRoot, releasePath))}`);
  check(fs.existsSync(lockPath), `Immutable catalog lock is missing: ${slash(path.relative(projectRoot, lockPath))}`);
  const releaseBytes = fs.readFileSync(releasePath);
  check(releaseBytes.equals(canonicalBytes), "Immutable release catalog differs byte-for-byte from canonical source");
  const lock = parseLock(fs.readFileSync(lockPath, "utf8"));
  const expected = {
    format: "1", schema: LOCK_SCHEMA, "catalog.version": EXPECTED_VERSION, file: RELEASE_FILE,
    sha256: digest, "source.file": SOURCE_FILE, "source.owner": SOURCE_REPOSITORY,
    "source.repository": SOURCE_REPOSITORY, "source.sha256": digest
  };
  for (const [key, value] of Object.entries(expected)) check(lock[key] === value, `Release lock ${key} drift: expected '${value}', actual '${lock[key]}'`);
  check(/^[0-9a-f]{40}$/.test(lock["source.gitBase"] || ""), "Release lock source.gitBase must be a 40-character lowercase Git hash");
  sameSet(Object.keys(lock), [...Object.keys(expected), "source.gitBase"], "release lock keys");
  return lock["source.gitBase"];
}

function verifySources(projectRoot, catalog, digest, validation) {
  const servicePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java");
  const providerTransportPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginModuleSourceProviderFileTransportSession.java");
  const workspaceArchivePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginWorkspaceArchiveSession.java");
  const typeScriptStripperPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeTypeScriptStripper.java");
  const startupEnvironmentPolicyPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeStartupEnvironmentPolicy.java");
  const infoServicePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java");
  const permissionPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java");
  const contractPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsRuntimeContract.kt");
  const pluginIdsPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginIds.kt");
  const capabilityKeysPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginCapabilityKeys.kt");
  const cppPath = path.join(projectRoot, "app/src/main/cpp/node_bridge_sources.cpp");
  const internalHeaderPath = path.join(projectRoot, "app/src/main/cpp/node_bridge_internal.h");
  const service = fs.readFileSync(servicePath, "utf8");
  const providerTransport = fs.readFileSync(providerTransportPath, "utf8");
  const workspaceArchive = fs.readFileSync(workspaceArchivePath, "utf8");
  const typeScriptStripper = fs.readFileSync(typeScriptStripperPath, "utf8");
  const startupEnvironmentPolicy = fs.readFileSync(startupEnvironmentPolicyPath, "utf8");
  const infoService = fs.readFileSync(infoServicePath, "utf8");
  const permission = fs.readFileSync(permissionPath, "utf8");
  const contract = fs.readFileSync(contractPath, "utf8");
  const pluginIds = fs.readFileSync(pluginIdsPath, "utf8");
  const capabilityKeys = fs.readFileSync(capabilityKeysPath, "utf8");
  const cpp = fs.readFileSync(cppPath, "utf8");
  const internalHeader = fs.readFileSync(internalHeaderPath, "utf8");
  verifyPermissionRegistry(catalog, permission);
  verifyServiceCapabilities(catalog, service, infoService, contract, pluginIds, capabilityKeys);
  verifyRuntimeCatalogMetadataAssignments(catalog, service, digest);
  verifyTypeScriptEntryWiring(service);
  verifyTypeScriptProviderTransport(providerTransport, cpp);
  verifyPlaintextTypeScriptProviderTransport(providerTransport, cpp);
  verifyProviderV2MissingPlaintextMaterialization(providerTransport, workspaceArchive, cpp, service);
  verifyWorkspaceArchivePathMapping(workspaceArchive);
  verifyTypeScriptStripperCatalog(catalog, typeScriptStripper, validation.errorCodes);
  verifyStartupEnvironmentPolicy(service, startupEnvironmentPolicy, cpp);
  verifyCpp(catalog, cpp, internalHeader);
  return {
    runtimeErrorCodeCount: verifyRuntimeErrors(projectRoot, validation.errorCodes),
    narrativeFileCount: verifyNoStaleNarrative(projectRoot)
  };
}

function selfTest(catalog) {
  const operationWithConditional = catalog.bridge.operations.find((operation) => (operation.conditionalCapabilities || []).length > 0);
  check(operationWithConditional, "Self-test requires at least one conditional bridge operation");
  const cases = [
    ["retired pluginOptional backend switch reinserted", (copy) => {
      copy.routing.backendSwitches.push("pluginOptional");
      copy.routing.backendSwitches.sort(compareText);
    }],
    ["duplicate feature", (copy) => copy.features.push({...copy.features[0]})],
    ["unknown capability", (copy) => copy.bridge.operations[0].requiredCapabilities.push("missing.capability")],
    ["unsorted errors", (copy) => copy.errors.constants.reverse()],
    ["missing Host plaintext materialization capability", (copy) => {
      copy.transport.runtimeServiceCapabilities = copy.transport.runtimeServiceCapabilities.filter(
        (item) => item !== "hostPlaintextModuleSourceMaterialization"
      );
    }],
    ["invalid conditional syntax", (copy) => {
      const operation = copy.bridge.operations.find((item) => item.module === operationWithConditional.module && item.method === operationWithConditional.method);
      operation.conditionalCapabilities[0].when = "args.root=true";
    }],
    ["unexpected conditional key", (copy) => {
      const operation = copy.bridge.operations.find((item) => item.module === operationWithConditional.module && item.method === operationWithConditional.method);
      operation.conditionalCapabilities[0].unsafe = true;
    }],
    ["duplicate conditional capability", (copy) => {
      const operation = copy.bridge.operations.find((item) => item.module === operationWithConditional.module && item.method === operationWithConditional.method);
      operation.conditionalCapabilities.push({...operation.conditionalCapabilities[0]});
    }],
    ["unsorted conditional capabilities", (copy) => {
      const operation = copy.bridge.operations.find((item) => item.module === operationWithConditional.module && item.method === operationWithConditional.method);
      operation.conditionalCapabilities.unshift({when: "options.z=true", requiredCapability: operation.requiredCapabilities[0]});
    }]
  ];
  for (const [name, mutate] of cases) {
    const copy = JSON.parse(JSON.stringify(catalog));
    mutate(copy);
    let rejected = false;
    try { validateCatalog(copy); } catch (_) { rejected = true; }
    check(rejected, `Self-test '${name}' did not reject invalid catalog`);
  }
}

function sourceMutationSelfTest(projectRoot, catalog, digest) {
  const servicePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java");
  const providerTransportPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginModuleSourceProviderFileTransportSession.java");
  const workspaceArchivePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginWorkspaceArchiveSession.java");
  const typeScriptStripperPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeTypeScriptStripper.java");
  const startupEnvironmentPolicyPath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeStartupEnvironmentPolicy.java");
  const infoServicePath = path.join(projectRoot, "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java");
  const contractPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsRuntimeContract.kt");
  const pluginIdsPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginIds.kt");
  const capabilityKeysPath = path.join(projectRoot, "plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginCapabilityKeys.kt");
  const cppPath = path.join(projectRoot, "app/src/main/cpp/node_bridge_sources.cpp");
  const service = fs.readFileSync(servicePath, "utf8");
  const providerTransport = fs.readFileSync(providerTransportPath, "utf8");
  const workspaceArchive = fs.readFileSync(workspaceArchivePath, "utf8");
  const typeScriptStripper = fs.readFileSync(typeScriptStripperPath, "utf8");
  const startupEnvironmentPolicy = fs.readFileSync(startupEnvironmentPolicyPath, "utf8");
  const infoService = fs.readFileSync(infoServicePath, "utf8");
  const contract = fs.readFileSync(contractPath, "utf8");
  const pluginIds = fs.readFileSync(pluginIdsPath, "utf8");
  const capabilityKeys = fs.readFileSync(capabilityKeysPath, "utf8");
  const cpp = fs.readFileSync(cppPath, "utf8");

  const catalogErrorCodes = new Set(catalog.errors.constants.map((item) => item.code));

  function verify(
    mutatedService,
    mutatedTypeScriptStripper = typeScriptStripper,
    mutatedStartupEnvironmentPolicy = startupEnvironmentPolicy,
    mutatedProviderTransport = providerTransport,
    mutatedCpp = cpp,
    mutatedWorkspaceArchive = workspaceArchive
  ) {
    verifyServiceCapabilities(catalog, mutatedService, infoService, contract, pluginIds, capabilityKeys);
    verifyRuntimeCatalogMetadataAssignments(catalog, mutatedService, digest);
    verifyTypeScriptEntryWiring(mutatedService);
    verifyTypeScriptProviderTransport(mutatedProviderTransport, mutatedCpp);
    verifyPlaintextTypeScriptProviderTransport(mutatedProviderTransport, mutatedCpp);
    verifyProviderV2MissingPlaintextMaterialization(
      mutatedProviderTransport,
      mutatedWorkspaceArchive,
      mutatedCpp,
      mutatedService
    );
    verifyWorkspaceArchivePathMapping(mutatedWorkspaceArchive);
    verifyTypeScriptStripperCatalog(catalog, mutatedTypeScriptStripper, catalogErrorCodes);
    verifyStartupEnvironmentPolicy(mutatedService, mutatedStartupEnvironmentPolicy, mutatedCpp);
  }

  function replaceRequired(source, expected, replacement, label) {
    check(source.includes(expected), `Source-mutation self-test '${label}' could not find its target`);
    const mutated = source.replace(expected, replacement);
    check(mutated !== source, `Source-mutation self-test '${label}' did not mutate the service source`);
    return mutated;
  }

  const assignments = [
    {
      name: "runtime catalog schema",
      statement: "info.putString(KEY_NODE_CAPABILITY_CATALOG_SCHEMA, NODE_CAPABILITY_CATALOG_SCHEMA);",
      wrongKey: "info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_CAPABILITY_CATALOG_SCHEMA);"
    },
    {
      name: "runtime catalog version",
      statement: "info.putString(KEY_NODE_CAPABILITY_CATALOG_VERSION, NODE_CAPABILITY_CATALOG_VERSION);",
      wrongKey: "info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_CAPABILITY_CATALOG_VERSION);"
    },
    {
      name: "runtime catalog sha256",
      statement: "info.putString(KEY_NODE_CAPABILITY_CATALOG_SHA256, NODE_CAPABILITY_CATALOG_SHA256);",
      wrongKey: "info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_CAPABILITY_CATALOG_SHA256);"
    },
    {
      name: "runtime cancellation mode",
      statement: "info.putString(\"cancellationMode\", CANCELLATION_STRATEGY_PROCESS_RESTART);",
      wrongKey: "info.putString(\"outputMode\", CANCELLATION_STRATEGY_PROCESS_RESTART);"
    },
    {
      name: "runtime module-source provider contract version",
      statement: "info.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);",
      wrongKey: "info.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);"
    },
    {
      name: "native diagnostic catalog schema",
      statement: "values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA, NODE_CAPABILITY_CATALOG_SCHEMA);",
      wrongKey: "values.put(\"embedded_script.runtime_plugin.service_name\", NODE_CAPABILITY_CATALOG_SCHEMA);"
    },
    {
      name: "native diagnostic catalog version",
      statement: "values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION, NODE_CAPABILITY_CATALOG_VERSION);",
      wrongKey: "values.put(\"embedded_script.runtime_plugin.service_name\", NODE_CAPABILITY_CATALOG_VERSION);"
    },
    {
      name: "native diagnostic catalog sha256",
      statement: "values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256, NODE_CAPABILITY_CATALOG_SHA256);",
      wrongKey: "values.put(\"embedded_script.runtime_plugin.service_name\", NODE_CAPABILITY_CATALOG_SHA256);"
    }
  ];

  verify(service);
  const workspacePathMutations = [
    [
      "remove credential-data candidate namespace normalization",
      "String comparableCandidate = androidCredentialDataComparablePath(normalizedCandidate);",
      "String comparableCandidate = normalizedCandidate;"
    ],
    [
      "bypass credential-data workspace containment",
      "if (!containsPath(containmentRoot, containmentCandidate)) {",
      "if (false) {"
    ],
    [
      "accept credential-data application-id prefix collisions",
      'packageName.matches("^[A-Za-z][A-Za-z0-9_]*(?:\\\\.[A-Za-z][A-Za-z0-9_]*)+$")',
      'packageName.matches("^.+$")'
    ]
  ];
  for (const [name, target, replacement] of workspacePathMutations) {
    const mutated = replaceRequired(workspaceArchive, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, providerTransport, cpp, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe workspace alias mapping`);
  }
  const providerV2TransportMutations = [
    [
      "coerce provider-v2 response version type",
      "version instanceof Integer",
      "version instanceof Number"
    ],
    [
      "coerce provider-v2 response source-byte type",
      "hasSourceBytes && !(sourceBytes instanceof Long)",
      "hasSourceBytes && !(sourceBytes instanceof Number)"
    ],
    [
      "accept mismatched provider-v2 response operation",
      "!expectedOperation.equals(responseOperation)",
      "false"
    ],
    [
      "accept source data on provider-v2 non-PFD status",
      "!pfdStatus && (hasSourceFd || sourceBytes != 0L)",
      "false"
    ],
    [
      "drop provider-v2 absolute monotonic deadline",
      "providerRequest.putLong(KEY_DEADLINE_ELAPSED_REALTIME_MS, requestDeadline);",
      "providerRequest.putLong(KEY_DEADLINE_ELAPSED_REALTIME_MS, timeoutMs);"
    ],
    [
      "bypass provider-v2 workspace raw publication",
      "                            workspaceSession.materializeProviderSourceNoReplace(\n                                    resolvedPath,",
      "                            workspaceSession.materializeProviderSourceReplace(\n                                    resolvedPath,"
    ],
    [
      "drop provider-v2 pre-publication deadline check",
      "if (remainingMs(requestDeadline) <= 0L)",
      "if (false)"
    ],
    [
      "drop provider-v2 workspace absolute deadline argument",
      "                                    copiedBytes,\n                                    requestDeadline",
      "                                    copiedBytes,\n                                    Long.MAX_VALUE"
    ],
    [
      "collapse workspace deadline into generic provider failure",
      "                        throw new ProviderTimeoutException(messageOf(error), error);\n                    }\n                    if (error instanceof Exception)",
      "                        throw new IOException(messageOf(error), error);\n                    }\n                    if (error instanceof Exception)"
    ],
    [
      "drop positive provider exact resolved-path binding",
      "            validatePositiveProviderResolvedPath(requestedPath, resolvedPath, status);",
      "            voidPositiveProviderResolvedPath(requestedPath, resolvedPath, status);"
    ],
    [
      "leak provider PFD after exact resolved-path rejection",
      "        } catch (IOException error) {\n            closeSourceFd(response);\n            throw error;\n        }\n        String errorCode",
      "        } catch (IOException error) {\n            throw error;\n        }\n        String errorCode"
    ]
  ];
  for (const [name, target, replacement] of providerV2TransportMutations) {
    const mutated = replaceRequired(providerTransport, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe provider-v2 transport wiring`);
  }
  const providerV2WorkspaceMutations = [
    [
      "admit unsupported provider materialization extension",
      "isTransportProtocolPath(relative) || !isSupportedProviderMaterializationPath(relative)",
      "isTransportProtocolPath(relative)"
    ],
    [
      "drop provider materialization exclusive-create flag",
      "OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL |",
      "OsConstants.O_WRONLY | OsConstants.O_CREAT |"
    ],
    [
      "create provider materialization target readable before verification",
      "Os.open(target.getAbsolutePath(), outputFlags, 0000)",
      "Os.open(target.getAbsolutePath(), outputFlags, PROVIDER_TARGET_PRIVATE_MODE)"
    ],
    [
      "drop immediate provider target fd identity capture",
      "ownedTargetIdentity = Os.fstat(output.getFD());",
      "ownedTargetIdentity = sourceBefore;"
    ],
    [
      "accept nonempty provider exclusive-create target",
      "ownedTargetIdentity.st_size != 0L ||",
      "ownedTargetIdentity.st_size < 0L ||"
    ],
    [
      "drop provider target permission promotion",
      "Os.fchmod(output.getFD(), PROVIDER_TARGET_PRIVATE_MODE);",
      "Os.fchmod(output.getFD(), 0000);"
    ],
    [
      "drop provider materialization protected-file tracking",
      "providerMaterializedFiles.add(relative);",
      "voidProviderMaterializedFile(relative);"
    ],
    [
      "export provider materialized plaintext file",
      "if (OsConstants.S_ISREG(stat.st_mode) && providerMaterializedFiles.contains(relative)) {",
      "if (false) {"
    ],
    [
      "emit tombstone for provider materialized plaintext file",
      "if (providerMaterializedFiles.contains(relative)) {\n                    providerProtectedTombstoneCount++;",
      "if (false) {\n                    providerProtectedTombstoneCount++;"
    ],
    [
      "weaken provider materialization deadline equality",
      "nowElapsedRealtimeMs >= deadlineElapsedRealtimeMs",
      "nowElapsedRealtimeMs > deadlineElapsedRealtimeMs"
    ],
    [
      "drop provider materialization counted initial deadline",
      '"before workspace publication"',
      '"before publication"'
    ],
    [
      "drop provider materialization post-copy deadline",
      '"after private copy and fsync"',
      '"after private copy"'
    ],
    [
      "drop provider materialization post-permission deadline",
      '"after permission promotion"',
      '"after permission receipt"'
    ],
    [
      "drop provider materialization closed-path validation",
      "validateClosedProviderMaterializationTarget(\n                    target,",
      "skipClosedProviderMaterializationTarget(\n                    target,"
    ],
    [
      "drop provider materialization final receipt deadline",
      '"before materialization receipt"',
      '"after materialization receipt"'
    ],
    [
      "delete provider materialization by size-sensitive identity",
      "if (sameOwnedRegularInode(identity, current)) {",
      "if (sameFileIdentity(identity, current)) {"
    ],
    [
      "delete foreign provider target replacement",
      "if (sameOwnedRegularInode(identity, current)) {",
      "if (true) {"
    ],
    [
      "allow workspace close to race provider materialization",
      "public synchronized void close() {",
      "public void close() {"
    ]
  ];
  for (const [name, target, replacement] of providerV2WorkspaceMutations) {
    const mutated = replaceRequired(workspaceArchive, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, providerTransport, cpp, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe provider materialization wiring`);
  }
  const cleanupOrderMutations = [
    [
      "close workspace before provider transport quiescence",
      '            if (moduleSourceProviderSession != null) {\n                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished after failure.");\n            } else {\n                cancelModuleSourceProvider(\n                        moduleSourceProvider,\n                        "Node.js runtime plugin execution finished before module-source transport startup."\n                );\n            }\n            if (workspaceSession != null) {\n                workspaceSession.close();\n            }',
      '            if (workspaceSession != null) {\n                workspaceSession.close();\n            }\n            if (moduleSourceProviderSession != null) {\n                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished after failure.");\n            } else {\n                cancelModuleSourceProvider(\n                        moduleSourceProvider,\n                        "Node.js runtime plugin execution finished before module-source transport startup."\n                );\n            }'
    ]
  ];
  for (const [name, target, replacement] of cleanupOrderMutations) {
    const mutated = replaceRequired(service, target, replacement, name);
    let rejected = false;
    try {
      verify(mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe provider cleanup ordering`);
  }
  const providerV2NativeMutations = [
    [
      "dispatch provider for extensionless missing CJS candidate",
      "      !extension ||\n      (!__autojs6_supported_local_module_extension(extension) &&",
      "      false ||\n      (!__autojs6_supported_local_module_extension(extension) &&"
    ],
    [
      "skip resolve-existing before missing materialization",
      '      "resolve_missing_candidate"\n    );',
      '      "materialize_missing_plaintext"\n    );'
    ],
    [
      "accept invalid resolve-existing status before materialization",
      'if (resolveResult.status !== "not_encrypted") {',
      "if (false) {"
    ],
    [
      "accept non-materialized missing-provider response",
      'materializedResult.status !== "materialized_plaintext"',
      "false"
    ],
    [
      "drop materialized missing-candidate identity capture",
      "__autojs6_module_source_provider_file_identity(fs, materializedReadable)",
      '"unverified"'
    ],
    [
      "bypass verified plaintext record for materialized package metadata",
      '      providerResult.status === "not_encrypted" ||\n      providerResult.status === "materialized_plaintext"',
      '      providerResult.status === "not_encrypted"'
    ],
    [
      "downgrade authorized plaintext disappearance to not-found",
      "    if (!current) {\n      throw __autojs6_module_source_provider_error(\n        \"denied\",\n        readable,\n        \"Module-source candidate disappeared or changed type before plaintext read.\",",
      "    if (!current) return null;\n    if (false) {\n      throw __autojs6_module_source_provider_error(\n        \"denied\",\n        readable,\n        \"Module-source candidate disappeared or changed type before plaintext read.\","
    ],
    [
      "restore declaration-order package pattern selection",
      "if (bestKey === null || __autojs6_package_pattern_key_compare(patternKey, bestKey) < 0)",
      "if (bestKey === null)"
    ],
    [
      "allow mixed package exports subpath and condition keys",
      "const conditionalMainShape = __autojs6_validate_package_exports_keys(exportsValue, moduleName);",
      "const conditionalMainShape = __autojs6_package_condition_map(exportsValue);"
    ],
    [
      "allow invalid package imports map keys",
      "__autojs6_validate_package_imports_keys(importsValue, moduleName);",
      "void importsValue;"
    ]
  ];
  for (const [name, target, replacement] of providerV2NativeMutations) {
    const mutated = replaceRequired(cpp, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, providerTransport, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe native missing-provider wiring`);
  }
  for (const assignment of assignments) {
    const cases = [
      [`remove ${assignment.name}`, ""],
      [`wrong key for ${assignment.name}`, assignment.wrongKey]
    ];
    for (const [name, replacement] of cases) {
      const mutated = replaceRequired(service, assignment.statement, replacement, name);
      let rejected = false;
      try {
        verify(mutated);
      } catch (_) {
        rejected = true;
      }
      check(rejected, `Source-mutation self-test '${name}' did not reject invalid runtime metadata wiring`);
    }
  }
  const providerContractMutations = [
    [
      "bypass module-source provider Binder contract gate",
      "if (requestedModuleSourceProvider == null) {",
      "if (true) {"
    ],
    [
      "coerce module-source provider contract version",
      "return rawValue instanceof Integer ? (Integer) rawValue : -1;",
      "return rawValue instanceof Number ? ((Number) rawValue).intValue() : -1;"
    ],
    [
      "read global version as module-source provider version",
      "Object rawModuleSourceProviderVersion = request.get(\n" +
        "                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION\n" +
        "        );",
      "Object rawModuleSourceProviderVersion = request.get(\n" +
        "                NodeJsRuntimeContract.KEY_CONTRACT_VERSION\n" +
        "        );"
    ],
    [
      "accept global contract range for module-source provider",
      "NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(\n" +
        "                receivedModuleSourceProviderVersion\n" +
        "        )",
      "NodeJsRuntimeContract.supportsContractVersion(\n" +
        "                receivedModuleSourceProviderVersion\n" +
        "        )"
    ]
  ];
  for (const [name, target, replacement] of providerContractMutations) {
    const mutated = replaceRequired(service, target, replacement, name);
    let rejected = false;
    try {
      verify(mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject mixed provider contract wiring`);
  }
  const typeScriptWiringMutations = [
    ["remove TypeScript entry preparation", "prepareTypeScriptEntryForNative(requestedSourceName, source);", "source;"],
    ["remove TypeScript prepared-source replacement", "source = typeScriptEntry.source();", ""],
    ["remove TypeScript module-source preparation", "prepareTypeScriptModuleSourcesForNative(moduleSources);", "null;"],
    ["remove TypeScript module-source replacement", "moduleSources = typeScriptModuleSources.sources();", ""],
    ["remove TypeScript runtime-module preparation", "prepareTypeScriptRuntimeModuleSourcesForNative(runtimeModuleSources);", "null;"],
    ["remove TypeScript runtime-module replacement", "runtimeModuleSources = typeScriptRuntimeModuleSources.sources();", ""],
    ["remove TypeScript success diagnostics", "nativePayloadFromMap(typeScriptEntry.diagnostics())", "new String[0]"],
    ["remove TypeScript module-source diagnostics", "nativePayloadFromMap(typeScriptModuleSources.diagnostics())", "new String[0]"],
    ["remove TypeScript runtime-module diagnostics", "nativePayloadFromMap(typeScriptRuntimeModuleSources.diagnostics())", "new String[0]"],
    ["collapse TypeScript error code", "failureErrorCode = typeScriptError.errorCode();", "failureErrorCode = ERROR_UNAVAILABLE;"],
    ["remove TypeScript failure diagnostics", "nativePayloadFromMap(typeScriptError.diagnostics())", "new String[0]"]
  ];
  for (const [name, target, replacement] of typeScriptWiringMutations) {
    const mutated = replaceRequired(service, target, replacement, name);
    let rejected = false;
    try {
      verify(mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject invalid TypeScript entry wiring`);
  }
  const typeScriptLexicalRewriteMutations = [
    [
      "match newline-padded TypeScript erasure against raw literals",
      "Matcher matcher = matcherAgainstCode(pattern, source);",
      "Matcher matcher = pattern.matcher(source);"
    ],
    [
      "scan interface declarations against raw literals",
      "String masked = maskNonCodeSameLength(source);",
      "String masked = source;"
    ],
    [
      "match function type parameters against raw literals",
      "Matcher matcher = matcherAgainstCode(FUNCTION_TYPE_PARAMETERS_PATTERN, source);",
      "Matcher matcher = FUNCTION_TYPE_PARAMETERS_PATTERN.matcher(source);"
    ],
    [
      "match grouped TypeScript erasure against raw literals",
      "private static String replaceGroups(Pattern pattern, String source, Object... parts) {\n" +
        "        Matcher matcher = matcherAgainstCode(pattern, source);",
      "private static String replaceGroups(Pattern pattern, String source, Object... parts) {\n" +
        "        Matcher matcher = pattern.matcher(source);"
    ],
    [
      "append masked group bytes instead of original source bytes",
      "output.append(source, start, end);",
      "output.append(matcher.group(group));"
    ],
    [
      "drop TypeScript lexical mask offset invariant",
      "if (masked.length() != source.length()) {",
      "if (false) {"
    ]
  ];
  for (const [name, target, replacement] of typeScriptLexicalRewriteMutations) {
    const mutated = replaceRequired(typeScriptStripper, target, replacement, name);
    let rejected = false;
    try {
      verify(service, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject unsafe TypeScript lexical rewriting`);
  }
  const providerTypeScriptMutations = [
    [
      "remove encrypted provider TypeScript preparation",
      "prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);",
      ""
    ],
    [
      "classify encrypted provider private transport name",
      "prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);",
      "prepareDecryptedTypeScriptSource(sourceFile, sourceFile.getName(), copiedBytes);"
    ],
    [
      "report raw encrypted provider bytes to native",
      '.put("sourceBytes", responseSourceBytes)',
      '.put("sourceBytes", sourceBytes.get())'
    ],
    [
      "collapse encrypted provider canonical TypeScript catch",
      "catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error)",
      "catch (IllegalArgumentException error)"
    ],
    [
      "remove encrypted provider atomic prepared rename",
      "Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());",
      "temporary.renameTo(destination);"
    ],
    [
      "weaken encrypted provider strict UTF-8 admission",
      ".onMalformedInput(CodingErrorAction.REPORT)",
      ".onMalformedInput(CodingErrorAction.REPLACE)"
    ],
    [
      "bypass encrypted provider raw aggregate budget",
      "if (aggregateBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes)",
      "if (false)"
    ],
    [
      "bypass encrypted provider prepared aggregate budget",
      "if (preparedBefore > TOTAL_SOURCE_BYTES_LIMIT - prepared.length)",
      "if (false)"
    ],
    [
      "bypass encrypted provider prepared accounting aggregate budget",
      "if (before > TOTAL_SOURCE_BYTES_LIMIT - bytes)",
      "if (false)"
    ],
    [
      "expand encrypted provider last-source diagnostic bound",
      "TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256",
      "TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 8192"
    ],
    [
      "break encrypted provider last-source UTF-16 boundary",
      "Character.isLowSurrogate(name.charAt(start))",
      "false"
    ]
  ];
  for (const [name, target, replacement] of providerTypeScriptMutations) {
    const mutated = replaceRequired(providerTransport, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject invalid encrypted provider TypeScript wiring`);
  }
  const plaintextProviderMutations = [
    [
      "expand provider Binder request budget",
      "static final int REQUEST_COUNT_LIMIT = 1024;",
      "static final int REQUEST_COUNT_LIMIT = 2048;"
    ],
    [
      "collapse private-operation transport budget into provider budget",
      "static final int TRANSPORT_REQUEST_COUNT_LIMIT = REQUEST_COUNT_LIMIT * 2;",
      "static final int TRANSPORT_REQUEST_COUNT_LIMIT = REQUEST_COUNT_LIMIT;"
    ],
    [
      "route private plaintext preparation into Binder dispatch",
      "if (plaintextPreparationRequest) {",
      "if (false) {"
    ],
    [
      "reuse plaintext preparation parent authorization",
      "pendingPlaintextPreparations.remove(parentRequestId);",
      "pendingPlaintextPreparations.get(parentRequestId);"
    ],
    [
      "accept arbitrary plaintext preparation input path",
      "if (!expectedSource.getAbsolutePath().equals(declaredSourcePath))",
      "if (false)"
    ],
    [
      "classify plaintext preparation transport id",
      "!NodeTypeScriptStripper.isTypeScriptSourceName(sourceName)",
      "!NodeTypeScriptStripper.isTypeScriptSourceName(id)"
    ],
    [
      "bypass plaintext TypeScript raw single-source budget",
      "        if (declaredBytes < 0L || declaredBytes > SINGLE_SOURCE_BYTES_LIMIT) {\n            throw new BudgetExceededException(\n                    \"Plaintext TypeScript module source exceeds the single-source byte budget.\"",
      "        if (declaredBytes < 0L) {\n            throw new BudgetExceededException(\n                    \"Plaintext TypeScript module source exceeds the single-source byte budget.\""
    ],
    [
      "bypass plaintext TypeScript raw aggregate budget",
      "if (rawBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes)",
      "if (false)"
    ],
    [
      "bypass plaintext TypeScript prepared aggregate budget",
      "ensurePreparedSourceBudget(prepared.length);",
      "commitPreparedSource(prepared.length);"
    ],
    [
      "weaken plaintext TypeScript private read NOFOLLOW",
      "OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW",
      "OsConstants.O_RDONLY | OsConstants.O_CLOEXEC"
    ],
    [
      "weaken plaintext TypeScript private read exact byte check",
      "if (total != expectedBytes ||\n                    completed.st_dev",
      "if (false ||\n                    completed.st_dev"
    ],
    [
      "reopen workspace instead of private plaintext source",
      "rawSource = readPrivatePreparationSource(",
      "rawSource = readWorkspaceSource("
    ],
    [
      "remove plaintext TypeScript parent authorization",
      "pendingPlaintextPreparations.put(id, pending);",
      "pendingPlaintextPreparations.computeIfAbsent(id, ignored -> pending);"
    ],
    [
      "authorize plaintext preparation after stop",
      "if (running.get() && !stopped.get())",
      "if (true)"
    ],
    [
      "retain plaintext preparation authorization across stop race",
      "pendingPlaintextPreparations.remove(id, pending);",
      "voidPendingAuthorization(id);"
    ],
    [
      "retain plaintext preparation authorization after worker termination",
      "closeActiveSourceDescriptor();\n            pendingPlaintextPreparations.clear();\n            if (stopped.get())",
      "closeActiveSourceDescriptor();\n            if (stopped.get())"
    ],
    [
      "remove serial plaintext preparation worker",
      "this.thread = new Thread(this::loop);",
      "this.thread = new Thread(() -> {});"
    ],
    [
      "misreport prepared plaintext TypeScript bytes",
      '.put("sourceBytes", responseSource.length())',
      '.put("sourceBytes", prepared.length + 1L)'
    ],
    [
      "misreport raw plaintext TypeScript bytes",
      '.put("rawSourceBytes", rawSource.length)',
      '.put("rawSourceBytes", rawSource.length + 1L)'
    ],
    [
      "drop plaintext raw-single failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "raw_budget_exceeded", 0, 0);\n            throw error;\n        }\n        byte[] rawSource;',
      'throw error;\n        }\n        byte[] rawSource;'
    ],
    [
      "drop plaintext raw-aggregate failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "raw_budget_exceeded", 0, 0);\n            throw error;\n        } catch (IOException error)',
      'throw error;\n        } catch (IOException error)'
    ],
    [
      "drop plaintext private I/O failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_FAILED, "private_input_io", 0, 0);',
      ""
    ],
    [
      "drop plaintext prepared-budget failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "budget_exceeded", 0, 0);',
      ""
    ],
    [
      "drop plaintext invalid-UTF8 failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_FAILED, "invalid_utf8", 0, 0);',
      ""
    ],
    [
      "drop plaintext prepared-I/O failure accounting",
      'recordTypeScriptFailure(sourceName, ERROR_FAILED, "transport_io", 0, 0);',
      ""
    ],
    [
      "drop terminal transport elapsed accounting",
      "transportElapsedMs.addAndGet(responseElapsedMs);",
      ""
    ],
    [
      "count private response as provider terminal status",
      "if (!providerResponse) {",
      "if (false) {"
    ],
    [
      "double-record successful terminal response",
      "responseCount.incrementAndGet();\n        record(response, providerResponse);",
      "responseCount.incrementAndGet();\n        record(response, providerResponse);\n        record(response, providerResponse);"
    ],
    [
      "count private successful preparation as provider response",
      "writeResponse(requestFile, responseJson, responseSource, false);",
      "writeResponse(requestFile, responseJson, responseSource, true);"
    ],
    [
      "count private failed preparation as provider response",
      "), !plaintextPreparationRequest);",
      "), true);"
    ],
    [
      "drop Binder provider response accounting",
      "writeResponse(requestFile, responseJson, sourceFile, true);",
      "writeResponse(requestFile, responseJson, sourceFile, false);"
    ],
    [
      "discard response accounting scope in quiet path",
      "writeResponse(requestFile, response, null, providerResponse);",
      "writeResponse(requestFile, response, null, true);"
    ]
  ];
  for (const [name, target, replacement] of plaintextProviderMutations) {
    const mutated = replaceRequired(providerTransport, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject invalid plaintext TypeScript transport wiring`);
  }
  const plaintextNativeMutations = [
    [
      "remove plaintext canonical path revalidation",
      "const plaintextReadable = __autojs6_revalidate_plaintext_module_path(",
      "const plaintextReadable = __autojs6_validate_runtime_module_path("
    ],
    [
      "replace plaintext verified raw read",
      "const rawSource = __autojs6_module_source_provider_verified_read(",
      "const rawSource = __autojs6_module_source_provider_unverified_read("
    ],
    [
      "remove plaintext private TypeScript preparation",
      "const source = __autojs6_prepare_plaintext_typescript_source(",
      "const source = String("
    ],
    [
      "route non-TypeScript plaintext through private preparation",
      "if (!__autojs6_is_plaintext_typescript_source(sourceName)) {",
      "if (false) {"
    ],
    [
      "bypass native plaintext TypeScript raw byte budget",
      "rawSourceBytes > __autojs6_runtime_module_single_source_bytes_limit",
      "false"
    ],
    [
      "classify plaintext TypeScript declarations as executable",
      "/\\.d\\.(?:ts|mts|cts)$/.test(normalizedSourceName)",
      "/\\.never$/.test(normalizedSourceName)"
    ],
    [
      "reencode plaintext raw bytes before private publication",
      "        rawSource,\n        \"\"",
      "        Buffer.from(String(rawSource)),\n        \"\""
    ],
    [
      "weaken plaintext private atomic exclusive mode",
      'fs.openSync(temporaryPath, "wx", 0o600)',
      'fs.openSync(temporaryPath, "w", 0o666)'
    ],
    [
      "remove plaintext private atomic fsync",
      "fs.fsyncSync(fd);",
      "void fd;"
    ],
    [
      "omit plaintext preparation remaining deadline",
      "timeoutMs: remainingTimeoutMs",
      "timeoutMs: config.timeoutMs"
    ],
    [
      "wait for plaintext preparation under generic response kind",
      '        "typescript_preparation"\n      );',
      '        "module_source"\n      );'
    ],
    [
      "accept prepared status for ordinary provider response",
      'status === "decrypted" || status === "materialized_plaintext" ||',
      'status === "prepared" || status === "decrypted" || status === "materialized_plaintext" ||'
    ],
    [
      "replace plaintext preparation parent deadline",
      "        authorizedParentDeadline,\n        \"typescript_preparation\"",
      "        Date.now() + config.timeoutMs,\n        \"typescript_preparation\""
    ],
    [
      "accept arbitrary prepared plaintext path",
      "responseSourcePath !== preparedSourcePath",
      "false"
    ],
    [
      "accept mismatched plaintext raw byte receipt",
      "declaredRawSourceBytes !== rawSourceBytes",
      "declaredRawSourceBytes < 0"
    ],
    [
      "accept mismatched plaintext canonical source name",
      'responseResolvedPath !== String(sourceName || "")',
      "false"
    ],
    [
      "prepare plaintext TypeScript under transport path",
      'const preparationSourceName = String(providerResult.resolvedPath || "");',
      "const preparationSourceName = String(readable);"
    ],
    [
      "drop plaintext preparation parent request identity",
      "providerResult.requestId,",
      '"",'
    ],
    [
      "drop plaintext preparation parent deadline",
      "providerResult.deadline",
      "Date.now() + 60000"
    ],
    [
      "replace plaintext canonical source URL with preparation name",
      "sourceURL: __autojs6_runtime_module_source_url(plaintextReadable)",
      "sourceURL: __autojs6_runtime_module_source_url(preparationSourceName)"
    ],
    [
      "remove plaintext prepared record cache",
      "__autojs6_plaintext_module_source_records[readable] = record;",
      "void record;"
    ],
    [
      "remove plaintext prepared record cache lookup",
      "if (__autojs6_has_own(__autojs6_plaintext_module_source_records, readable)) {",
      "if (false) {"
    ],
    [
      "make plaintext prepared record mutable",
      "const record = Object.freeze({",
      "const record = ({"
    ],
    [
      "leak plaintext private request temporary artifact",
      "__autojs6_module_source_provider_cleanup_file(fs, requestTempPath);",
      "void requestTempPath;"
    ],
    [
      "leak plaintext private raw temporary artifact",
      "__autojs6_module_source_provider_cleanup_file(fs, rawSourceTempPath);",
      "void rawSourceTempPath;"
    ],
    [
      "leak plaintext private prepared artifact",
      "__autojs6_module_source_provider_cleanup_file(fs, preparedSourcePath);",
      "void preparedSourcePath;"
    ],
    [
      "drop initial plaintext preparation deadline",
      'deadline: status === "not_encrypted" ? requestDeadline : 0',
      "deadline: 0"
    ],
    [
      "change canonical plaintext TSX error",
      '"ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION"',
      '"ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED"'
    ]
  ];
  for (const [name, target, replacement] of plaintextNativeMutations) {
    const mutated = replaceRequired(cpp, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, startupEnvironmentPolicy, providerTransport, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject invalid native plaintext TypeScript wiring`);
  }
  const movedRawPublicationName = "publish plaintext raw bytes before native byte budget";
  const rawPublicationBlock = [
    "      __autojs6_module_source_provider_atomic_write(",
    "        fs,",
    "        rawSourceTempPath,",
    "        rawSourcePath,",
    "        rawSource,",
    "        \"\"",
    "      );"
  ].join("\n");
  let movedRawPublication = replaceRequired(
    cpp,
    rawPublicationBlock,
    "",
    movedRawPublicationName
  );
  movedRawPublication = replaceRequired(
    movedRawPublication,
    '    const rawSourceBytes = rawSource && typeof rawSource.length === "number"',
    `${rawPublicationBlock}\n    const rawSourceBytes = rawSource && typeof rawSource.length === "number"`,
    movedRawPublicationName
  );
  let movedRawPublicationRejected = false;
  try {
    verify(service, typeScriptStripper, startupEnvironmentPolicy, providerTransport, movedRawPublication);
  } catch (_) {
    movedRawPublicationRejected = true;
  }
  check(
    movedRawPublicationRejected,
    `Source-mutation self-test '${movedRawPublicationName}' did not reject raw publication before byte admission`
  );
  const movedProviderPreparationName = "move encrypted provider TypeScript preparation before raw budget";
  let movedProviderPreparation = replaceRequired(
    providerTransport,
    "prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);",
    "",
    movedProviderPreparationName
  );
  movedProviderPreparation = replaceRequired(
    movedProviderPreparation,
    "if (declaredBytes > SINGLE_SOURCE_BYTES_LIMIT)",
    "prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);\n            if (declaredBytes > SINGLE_SOURCE_BYTES_LIMIT)",
    movedProviderPreparationName
  );
  let movedProviderPreparationRejected = false;
  try {
    verify(service, typeScriptStripper, startupEnvironmentPolicy, movedProviderPreparation);
  } catch (_) {
    movedProviderPreparationRejected = true;
  }
  check(
    movedProviderPreparationRejected,
    `Source-mutation self-test '${movedProviderPreparationName}' did not reject preparation before raw budget`
  );
  const duplicateCanonicalFailureName = "duplicate encrypted provider canonical TypeScript failure count";
  const canonicalFailureRecord = [
    "recordTypeScriptFailure(",
    "                    error.sourceName(),",
    "                    error.errorCode(),",
    "                    error.syntaxKind(),",
    "                    error.line(),",
    "                    error.column()",
    "            );"
  ].join("\n");
  const duplicateCanonicalFailure = replaceRequired(
    providerTransport,
    canonicalFailureRecord,
    `${canonicalFailureRecord}\n            ${canonicalFailureRecord}`,
    duplicateCanonicalFailureName
  );
  let duplicateCanonicalFailureRejected = false;
  try {
    verify(service, typeScriptStripper, startupEnvironmentPolicy, duplicateCanonicalFailure);
  } catch (_) {
    duplicateCanonicalFailureRejected = true;
  }
  check(
    duplicateCanonicalFailureRejected,
    `Source-mutation self-test '${duplicateCanonicalFailureName}' did not reject duplicate failure_count accounting`
  );
  const typeScriptStripperMutations = [
    ["drop plugin CTS routing", 'Arrays.asList("ts", "mts", "cts")', 'Arrays.asList("ts", "mts")'],
    ["accept plugin TSX routing", 'if ("tsx".equals(extension))', 'if ("jsx".equals(extension))'],
    [
      "change plugin TypeScript extension error",
      '"ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION"',
      '"ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE"'
    ],
    ["remove TypeScript source-map diagnostic cap", "if (typeScriptCount < SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT)", "if (true)"],
    ["expand TypeScript source-map diagnostic cap", "SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT = 16", "SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT = 8192"],
    ["remove TypeScript source-map truncation accounting", 'prefix + "detail_truncated_count"', 'prefix + "detail_omitted"'],
    ["expand TypeScript diagnostic source-name bound", "DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256", "DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 8192"],
    ["break TypeScript diagnostic UTF-16 prefix boundary", "int headEnd = safeUtf16PrefixEnd(normalized, headBudget);", "int headEnd = headBudget;"],
    ["expose raw TypeScript entry diagnostic name", 'diagnostics.put("embedded_script.typescript.source", diagnosticSourceName.value());', 'diagnostics.put("embedded_script.typescript.source", sourceName);'],
    ["expose raw TypeScript failure diagnostic name", 'values.put("embedded_script.typescript.source", diagnosticSourceName.value());', 'values.put("embedded_script.typescript.source", sourceName);'],
    ["remove TypeScript map name truncation marker", 'prefix + "name_truncated"', 'prefix + "name_was_truncated"']
  ];
  for (const [name, target, replacement] of typeScriptStripperMutations) {
    const mutated = replaceRequired(typeScriptStripper, target, replacement, name);
    let rejected = false;
    try {
      verify(service, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject TypeScript catalog drift`);
  }
  const startupEnvironmentWiringMutations = [
    ["remove startup-environment preparation", "prepareNodeStartupEnvironmentForNative(env);", "null;"],
    ["remove sanitized startup-environment replacement", "env = startupEnvironment.environment();", ""],
    ["remove startup-environment diagnostics", "nativePayloadFromMap(startupEnvironment.diagnostics())", "new String[0]"]
  ];
  for (const [name, target, replacement] of startupEnvironmentWiringMutations) {
    const mutated = replaceRequired(service, target, replacement, name);
    let rejected = false;
    try {
      verify(mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject invalid startup-environment wiring`);
  }
  const startupEnvironmentPolicyMutations = [
    ["drop NODE_OPTIONS denylist", '                    "NODE_OPTIONS",\n', ""],
    ["change compile-cache denylist", '                    "NODE_COMPILE_CACHE",', '                    "NODE_COMPILE_CACHE_UNSAFE",'],
    ["remove locale-stable case folding", "name.toUpperCase(Locale.ROOT)", "name"],
    ["remove startup-environment diagnostic cap", "if (filteredCount < FILTERED_DIAGNOSTIC_DETAIL_LIMIT)", "if (true)"],
    ["expand startup-environment diagnostic cap", "FILTERED_DIAGNOSTIC_DETAIL_LIMIT = 16", "FILTERED_DIAGNOSTIC_DETAIL_LIMIT = 8192"]
  ];
  for (const [name, target, replacement] of startupEnvironmentPolicyMutations) {
    const mutated = replaceRequired(startupEnvironmentPolicy, target, replacement, name);
    let rejected = false;
    try {
      verify(service, typeScriptStripper, mutated);
    } catch (_) {
      rejected = true;
    }
    check(rejected, `Source-mutation self-test '${name}' did not reject startup-environment policy drift`);
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const defaultRoot = path.resolve(__dirname, "../../..");
  const projectRoot = path.resolve(args["project-root"] || defaultRoot);
  const catalogPath = path.resolve(args.catalog || path.join(projectRoot, SOURCE_FILE));
  const {catalog, bytes, digest} = readCatalog(catalogPath);
  const validation = validateCatalog(catalog);
  if (args["self-test"]) {
    selfTest(catalog);
    sourceMutationSelfTest(projectRoot, catalog, digest);
  }
  const sourceCounts = verifySources(projectRoot, catalog, digest, validation);
  if (args["stage-release"]) stageRelease(projectRoot, bytes, digest);
  let releaseGitBase = null;
  if (args["stage-release"] || args["verify-release"]) releaseGitBase = verifyRelease(projectRoot, bytes, digest);
  const report = {
    schema: "autojs6-node-capability-truth-report-v1",
    catalog: {
      schema: catalog.schema,
      version: catalog.catalogVersion,
      sha256: digest,
      source: SOURCE_FILE
    },
    counts: {
      bridgeModules: catalog.bridge.modules.length,
      bridgeOperations: catalog.bridge.operations.length,
      errorConstants: catalog.errors.constants.length,
      featureFlags: catalog.features.length,
      legacyErrorAliases: catalog.errors.legacyAliases.length,
      permissionAliases: catalog.bridge.permissionCapabilities.reduce((sum, item) => sum + item.aliases.length, 0),
      permissionCapabilities: catalog.bridge.permissionCapabilities.length,
      runtimeSourceErrorCodes: sourceCounts.runtimeErrorCodeCount
    },
    checks: [
      "catalog-schema-and-deterministic-json",
      "conditional-capability-shape-order-closure-and-runtime-mapping",
      "unique-identifiers-and-reference-closure",
      "stable-set-ordering",
      "runtime-and-plugin-info-service-catalog-metadata",
      "runtime-service-capabilities-defaults-and-lifecycle",
      "cpp-profile-defaults-lifecycle-operation-permissions-and-error-aliases",
      "permission-capabilities-aliases-and-android-permissions",
      "runtime-error-codes-covered-by-catalog",
      "routing-backend-switch-exact-set",
      "typescript-entry-source-pre-native-dispatch-and-diagnostics",
      "typescript-encrypted-provider-post-pfd-pre-native-preparation-budgets-and-canonical-errors",
      "typescript-plaintext-provider-private-operation-parent-deadline-fixed-transport-and-budgets",
      "typescript-plaintext-provider-native-revalidate-verified-read-prepare-order-and-frozen-cache",
      "typescript-plaintext-provider-non-typescript-exact-byte-bypass-and-canonical-source-url",
      "typescript-plaintext-provider-private-publication-response-truth-and-complete-cleanup",
      "typescript-plaintext-provider-failure-count-exactly-once",
      "typescript-plaintext-provider-private-terminal-transport-only-accounting",
      "typescript-plaintext-provider-prepared-status-private-kind-only",
      "typescript-plaintext-provider-parent-stop-race-terminal-clear",
      "x3f-provider-v2-exact-supported-extension-missing-candidate-only",
      "x3f-provider-v2-operation-deadline-strict-response-envelope-and-single-accounting",
      "x3f-provider-v2-selinux-safe-o-excl-mode000-copy-fsync-fchmod-mode0600",
      "x3f-provider-v2-fd-path-parent-canonical-identity-and-final-deadline-before-receipt",
      "x3f-provider-v2-owned-dev-inode-only-rollback-and-created-directory-prune",
      "x3f-provider-v2-materialized-file-directory-and-tombstone-output-exclusion",
      "x3h-provider-v2-positive-response-exact-path-and-pfd-cleanup",
      "x3h-provider-only-not-found-advances-after-positive-path-revalidation",
      "x3h-package-pattern-most-specific-and-map-shape-fail-closed",
      "x3h-predispatch-readiness-and-empty-source-no-commit-receipt",
      "x3h-provider-transport-quiesces-before-synchronized-workspace-cleanup",
      "typescript-erasure-same-length-code-mask-and-original-source-index-rebuild",
      "typescript-preloaded-module-sources-pre-native-dispatch-order-and-diagnostics",
      "typescript-preloaded-source-diagnostic-detail-cap-and-truncation-accounting",
      "typescript-diagnostic-source-name-bound-and-utf16-safe-representation",
      "typescript-stripper-routing-and-error-catalog-parity",
      "startup-environment-final-dispatch-denylist-order-and-native-parity",
      "startup-environment-diagnostic-detail-cap-and-truncation-accounting",
      "active-docs-free-of-stale-capability-narrative",
      ...(args["self-test"] ? [
        "producer-runtime-metadata-source-mutation-self-test",
        "retired-pluginoptional-backend-switch-mutation-self-test"
      ] : []),
      ...(args["stage-release"] || args["verify-release"] ? ["immutable-release-byte-and-lock-identity"] : [])
    ].sort().map((id) => ({id, status: "passed"})),
    narrativeFilesChecked: sourceCounts.narrativeFileCount,
    release: args["stage-release"] || args["verify-release"] ? {path: RELEASE_ROOT, sourceGitBase: releaseGitBase, verified: true} : {verified: false}
  };
  if (args.report) {
    const reportPath = path.resolve(args.report);
    fs.mkdirSync(path.dirname(reportPath), {recursive: true});
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  }
  process.stdout.write(`Node capability truth PASS schema=${catalog.schema} version=${catalog.catalogVersion} sha256=${digest}\n`);
}

try {
  main();
} catch (error) {
  process.stderr.write(`Node capability truth FAILED: ${error.message}\n`);
  process.exitCode = 1;
}

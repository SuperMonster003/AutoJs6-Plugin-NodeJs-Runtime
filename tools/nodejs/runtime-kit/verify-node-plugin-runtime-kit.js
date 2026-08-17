#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const SCHEMA = "autojs6-node-plugin-runtime-kit-v1";
const LOCK_SCHEMA = "autojs6-node-plugin-runtime-kit-lock-v1";
const KIT_VERSION = "1.1.3";
const KIT_ID = "autojs6-node-plugin-runtime-kit-v1.1.3-node24_5";
const RELEASE_TARGET_VERSION = "1.1.3";
const RELEASE_TARGET_ID = "autojs6-node-plugin-runtime-kit-v1.1.3-node24_5";
const SOURCE_FILE = "app/src/main/assets/nodejs/node-plugin-runtime-kit.json";
const RELEASE_ROOT = `releases/nodejs-plugin-runtime-kit/${RELEASE_TARGET_VERSION}`;
const RELEASE_FILE = "node-plugin-runtime-kit.json";
const RELEASE_LOCK = "node-plugin-runtime-kit.lock";
const NODEJS_API_COORDINATE = "org.autojs.plugin.nodejs:nodejs-api:1.1.0";
const NODEJS_API_VERSION = "1.1.0";
const NODEJS_API_RELEASE_AAR = "releases/nodejs-api/1.1.0/nodejs-api-1.1.0.aar";
const NODEJS_API_RELEASE_LOCK = "releases/nodejs-api/1.1.0/nodejs-api.lock";
const NODEJS_API_AAR_SHA256 = "4b5eb13622137078641d7259b83189ae8ea618824c5974a5f13d77fa02e4a0dc";
const NODEJS_API_AIDL_TRANSACTIONS_SHA256 = "74c886570e635704bb10f9735ebba7838dd6e0a8df79641f4478320deb723084";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const REQUIRED_NATIVE_ROLES = ["native_runtime", "native_bridge", "native_dependency"];

function fail(message) { throw new Error(message); }
function check(condition, message) { if (!condition) fail(message); }
function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function slash(value) { return value.replace(/\\/g, "/"); }
function sorted(values) { return values.slice().sort((a, b) => a < b ? -1 : a > b ? 1 : 0); }
function sameSet(actual, expected, label) {
  check(JSON.stringify(sorted(actual)) === JSON.stringify(sorted(expected)),
    `${label} mismatch: expected ${JSON.stringify(sorted(expected))}, actual ${JSON.stringify(sorted(actual))}`);
}

function parseArgs(argv) {
  const result = Object.create(null);
  const flags = new Set(["stage-release", "verify-release", "require-release-apks", "self-test"]);
  const repeated = new Set(["release-apk"]);
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    check(arg.startsWith("--"), `Unexpected argument '${arg}'`);
    const key = arg.slice(2);
    if (flags.has(key)) result[key] = true;
    else {
      check(argv[i + 1] && !argv[i + 1].startsWith("--"), `Missing value for --${key}`);
      const value = argv[++i];
      if (repeated.has(key)) (result[key] ||= []).push(value);
      else result[key] = value;
    }
  }
  return result;
}

function readCanonicalJson(file, label) {
  const bytes = fs.readFileSync(file);
  let value;
  try { value = JSON.parse(bytes.toString("utf8")); }
  catch (error) { fail(`${label} is not valid JSON: ${error.message}`); }
  const canonical = Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
  check(bytes.equals(canonical), `${label} must use deterministic two-space JSON with one trailing LF`);
  return {value, bytes, digest: sha256(bytes)};
}

function validateShape(kit) {
  sameSet(Object.keys(kit), ["schema", "schemaVersion", "kitId", "kitVersion", "owner", "runtime", "contract", "nodeJsApi",
    "capabilityCatalog", "reproducibility", "buildProvenance", "payloads", "diagnostics"], "manifest top-level keys");
  check(kit.schema === SCHEMA && kit.schemaVersion === 1, "runtime kit schema mismatch");
  check(kit.kitVersion === KIT_VERSION && kit.kitId === KIT_ID, "runtime kit identity mismatch");
  check(kit.owner.pluginId === "nodejs" &&
    kit.owner.applicationId === "io.github.supermonster003.autojs6.plugin.nodejs" &&
    kit.owner.repository === "AutoJs6-Plugin-NodeJs-Runtime", "runtime kit owner mismatch");
  check(kit.runtime.engine === "nodejs" && kit.runtime.runtimeSlot === "node24_5", "runtime identity mismatch");
  check(kit.runtime.nodeVersion === "24.5.0" && kit.runtime.bridgeApiVersion === 1, "runtime version mismatch");
  check(kit.runtime.nativeLibraryName === "node" && kit.runtime.bridgeNativeLibraryName === "autojs6-node", "native library names mismatch");
  check(JSON.stringify(kit.runtime.libraryLoadOrder) === JSON.stringify(["node", "autojs6-node"]), "library load order mismatch");
  check(JSON.stringify(kit.runtime.supportedAbis) === JSON.stringify(REQUIRED_ABIS), "supported ABI order mismatch");
  sameSet(Object.keys(kit.contract), ["min", "max", "moduleSourceProvider"], "runtime contract keys");
  check(kit.contract.min === 1 && kit.contract.max === 1 && kit.contract.moduleSourceProvider === 2,
    "runtime/provider contract mismatch");
  sameSet(Object.keys(kit.nodeJsApi), ["coordinate", "version", "sha256", "aidlTransactionsSha256"],
    "nodejs-api identity keys");
  check(kit.nodeJsApi.coordinate === NODEJS_API_COORDINATE && kit.nodeJsApi.version === NODEJS_API_VERSION &&
    kit.nodeJsApi.sha256 === NODEJS_API_AAR_SHA256 &&
    kit.nodeJsApi.aidlTransactionsSha256 === NODEJS_API_AIDL_TRANSACTIONS_SHA256,
    "nodejs-api 1.1.0 identity mismatch");
  check(kit.capabilityCatalog.schema === "autojs6-node-capability-catalog-v1" &&
    kit.capabilityCatalog.version === "1.1.1" &&
    kit.capabilityCatalog.sha256 === "1a33e3f3df88412ea3cc1dbf125886e0daa01862663157eaf2c5c284857a89e7",
    "capability catalog identity mismatch");
  check(kit.reproducibility.artifactMaterialization === "pinned_upstream_binary" &&
    kit.reproducibility.artifactMaterializationStatus === "ready", "prebuilt materialization status mismatch");
  check(kit.reproducibility.sourceBuild === "not_source_reproducible" &&
    kit.reproducibility.sourceBuildStatus === "bootstrap_only", "source build status must remain bootstrap_only");
  check(kit.reproducibility.kitUsable === true && kit.reproducibility.releaseReady === false,
    "kit must be usable without claiming source-build release readiness");
  check(Array.isArray(kit.reproducibility.blockedReasons) && kit.reproducibility.blockedReasons.length >= 3,
    "source-build blocked reasons are required");
  check(kit.buildProvenance.bridgeBuild.variant === "release_universal" &&
    kit.buildProvenance.bridgeBuild.gradleTask === ":app:assembleRelease", "runtime kit must pin the release universal variant");
  check(kit.diagnostics.installedApkEntry === "assets/nodejs/node-plugin-runtime-kit.json", "installed APK manifest entry mismatch");
  sameSet(Object.values(kit.diagnostics.bundleKeys),
    ["nodeRuntimeKitSchema", "nodeRuntimeKitVersion", "nodeRuntimeKitSha256", "nodeRuntimeKitId"], "runtime kit bundle keys");

  check(Array.isArray(kit.payloads) && kit.payloads.length === 10, "runtime kit must contain exactly 10 payload descriptors");
  const ids = kit.payloads.map(item => item.id);
  check(new Set(ids).size === ids.length && JSON.stringify(ids) === JSON.stringify(sorted(ids)), "payload IDs must be unique and sorted");
  const packagedPaths = kit.payloads.map(item => item.packagedPath);
  check(new Set(packagedPaths).size === packagedPaths.length, "payload packaged paths must be unique");
  for (const item of kit.payloads) {
    sameSet(Object.keys(item), ["id", "role", "fileName", "sourcePath", "packagedPath", "abi", "payloadProfile",
      "delivery", "required", "size", "sha256", "buildId"], `payload ${item.id} keys`);
    check(item.payloadProfile === "embedded_runtime" && item.required === true, `payload ${item.id} must be required embedded_runtime`);
    check(Number.isSafeInteger(item.size) && item.size > 0 && /^[0-9a-f]{64}$/.test(item.sha256), `payload ${item.id} size/hash invalid`);
    check(["metadata", ...REQUIRED_NATIVE_ROLES].includes(item.role), `payload ${item.id} role invalid`);
    if (item.role === "metadata") {
      check(item.id === "capability-catalog" && item.abi === "universal" && item.delivery === "source_tree" &&
        item.fileName === "node-capability-catalog.json" &&
        item.sourcePath === "app/src/main/assets/nodejs/node-capability-catalog.json" &&
        item.packagedPath === "assets/nodejs/node-capability-catalog.json" &&
        item.sha256 === kit.capabilityCatalog.sha256, "metadata payload mismatch");
    } else {
      check(REQUIRED_ABIS.includes(item.abi) && item.delivery === "plugin_apk_release_entry", `native payload ${item.id} delivery/ABI mismatch`);
      check(item.sourcePath.includes("/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/"),
        `native payload ${item.id} must reference the stripped release output`);
      check(item.packagedPath === `lib/${item.abi}/${item.fileName}`, `native payload ${item.id} packaged path mismatch`);
      const expectedFileName = {
        native_runtime: "libnode.so",
        native_bridge: "libautojs6-node.so",
        native_dependency: "libc++_shared.so",
      }[item.role];
      check(item.fileName === expectedFileName, `native payload ${item.id} file name mismatch`);
      if (item.role !== "native_runtime") {
        check(/^[0-9a-f]{40}$/.test(item.buildId || ""), `${item.role} payload ${item.id} Build ID missing`);
      }
    }
  }
  for (const abi of REQUIRED_ABIS) {
    check(kit.payloads.filter(item => item.abi === abi && item.role === "native_runtime").length === 1,
      `ABI ${abi} must have exactly one native_runtime payload`);
    check(kit.payloads.filter(item => item.abi === abi && item.role === "native_bridge").length === 1,
      `ABI ${abi} must have exactly one native_bridge payload`);
    check(kit.payloads.filter(item => item.abi === abi && item.role === "native_dependency").length === 1,
      `ABI ${abi} must have exactly one native_dependency payload`);
  }
}

function verifyFileIdentity(root, relative, expectedSha, expectedSize, label) {
  const file = path.resolve(root, relative);
  check(fs.statSync(file).isFile(), `${label} is missing: ${relative}`);
  const bytes = fs.readFileSync(file);
  if (expectedSize !== null) {
    check(bytes.length === expectedSize, `${label} size mismatch: expected ${expectedSize}, actual ${bytes.length}`);
  }
  check(sha256(bytes) === expectedSha, `${label} SHA-256 mismatch`);
  return {file: slash(relative), size: bytes.length, sha256: expectedSha};
}

function elfBuildId(bytes) {
  const marker = Buffer.from("040000001400000003000000474e5500", "hex");
  const offset = bytes.indexOf(marker);
  return offset < 0 ? null : bytes.subarray(offset + marker.length, offset + marker.length + 20).toString("hex");
}

function validateProducerAssignments(infoService, runtimeService) {
  const identities = [
    ["SCHEMA", "nodeRuntimeKitSchema", "NODE_PLUGIN_RUNTIME_KIT_SCHEMA", "embedded_script.runtime_plugin.runtime_kit_schema"],
    ["VERSION", "nodeRuntimeKitVersion", "NODE_PLUGIN_RUNTIME_KIT_VERSION", "embedded_script.runtime_plugin.runtime_kit_version"],
    ["SHA256", "nodeRuntimeKitSha256", "NODE_PLUGIN_RUNTIME_KIT_SHA256", "embedded_script.runtime_plugin.runtime_kit_sha256"],
    ["ID", "nodeRuntimeKitId", "NODE_PLUGIN_RUNTIME_KIT_ID", "embedded_script.runtime_plugin.runtime_kit_id"],
  ];
  for (const [suffix, key, field, diagnostic] of identities) {
    check(infoService.includes(`capabilities.putString("${key}", BuildConfig.${field})`),
      `PluginInfo capability ${key} must come from generated BuildConfig identity`);
    check(runtimeService.includes(`private static final String KEY_NODE_RUNTIME_KIT_${suffix} = "${key}";`),
      `runtime info constant ${key} is missing or mapped incorrectly`);
    check(runtimeService.includes(`info.putString(KEY_NODE_RUNTIME_KIT_${suffix}, BuildConfig.${field});`),
      `runtime info assignment ${key} must map to BuildConfig.${field}`);
    check(runtimeService.includes(`"${diagnostic}";`), `runtime diagnostic constant ${diagnostic} is missing`);
    check(runtimeService.includes(`values.put(DIAGNOSTIC_NODE_RUNTIME_KIT_${suffix}, BuildConfig.${field});`),
      `runtime diagnostic ${diagnostic} must map to BuildConfig.${field}`);
  }
}

function validateRepository(kit, root) {
  verifyFileIdentity(root, NODEJS_API_RELEASE_AAR, kit.nodeJsApi.sha256, null, "nodejs-api 1.1.0 AAR");
  const nodeJsApiLock = parseProperties(fs.readFileSync(path.resolve(root, NODEJS_API_RELEASE_LOCK), "utf8"));
  check(nodeJsApiLock.coordinate === kit.nodeJsApi.coordinate &&
    nodeJsApiLock.file === path.basename(NODEJS_API_RELEASE_AAR) &&
    nodeJsApiLock.sha256 === kit.nodeJsApi.sha256 &&
    nodeJsApiLock["contract.version"] === String(kit.contract.max) &&
    nodeJsApiLock["contract.min"] === String(kit.contract.min) &&
    nodeJsApiLock["contract.max"] === String(kit.contract.max) &&
    nodeJsApiLock["module.source.provider.contract.version"] === String(kit.contract.moduleSourceProvider) &&
    nodeJsApiLock["aidl.transactions.sha256"] === kit.nodeJsApi.aidlTransactionsSha256,
    "nodejs-api release lock does not match Runtime Kit identity");
  const payloadEvidence = [];
  for (const item of kit.payloads) {
    const evidence = verifyFileIdentity(root, item.sourcePath, item.sha256, item.size, `payload ${item.id}`);
    const actualBuildId = elfBuildId(fs.readFileSync(path.resolve(root, item.sourcePath)));
    check(actualBuildId === item.buildId, `payload ${item.id} Build ID mismatch: expected ${item.buildId}, actual ${actualBuildId}`);
    payloadEvidence.push({...evidence, id: item.id, abi: item.abi, role: item.role, buildId: actualBuildId});
  }

  const provenance = kit.buildProvenance;
  check(/^[0-9a-f]{40}$/.test(provenance.sourceGitBase || ""), "build provenance sourceGitBase invalid");
  const ancestry = childProcess.spawnSync("git", ["merge-base", "--is-ancestor", provenance.sourceGitBase, "HEAD"],
    {cwd: root, encoding: "utf8", windowsHide: true});
  check(ancestry.status === 0, `build provenance sourceGitBase is not an ancestor of HEAD: ${provenance.sourceGitBase}`);
  for (const [fileKey, digestKey] of [
    ["lockFile", "lockSha256"],
    ["powershellMaterializer", "powershellMaterializerSha256"],
    ["shellMaterializer", "shellMaterializerSha256"],
  ]) verifyFileIdentity(root, provenance.runtimeBuildPlan[fileKey], provenance.runtimeBuildPlan[digestKey],
    fs.statSync(path.resolve(root, provenance.runtimeBuildPlan[fileKey])).size, `runtime build ${fileKey}`);
  verifyFileIdentity(root, provenance.bridgeBuild.cmakeFile, provenance.bridgeBuild.cmakeSha256,
    fs.statSync(path.resolve(root, provenance.bridgeBuild.cmakeFile)).size, "bridge CMake input");

  const buildLock = JSON.parse(fs.readFileSync(path.resolve(root, provenance.runtimeBuildPlan.lockFile), "utf8"));
  check(buildLock.reproducibility.artifactMaterialization.status === "ready" &&
    buildLock.reproducibility.artifactMaterialization.classification === "pinned_upstream_binary", "runtime build lock materialization mismatch");
  check(buildLock.reproducibility.sourceBuild.status === "bootstrap_only", "runtime build lock must retain source-build blocker");
  const upstream = provenance.upstreamArtifact;
  check(upstream.url === buildLock.androidFork.androidArtifact.url && upstream.size === buildLock.androidFork.androidArtifact.size &&
    upstream.sha256 === buildLock.androidFork.androidArtifact.sha256, "upstream artifact provenance drift");
  for (const abi of REQUIRED_ABIS) {
    const origin = buildLock.androidFork.androidArtifact.entries[abi];
    verifyFileIdentity(root, `app/src/main/jniLibs/${abi}/libnode.so`, origin.sha256, origin.size, `upstream-origin libnode ${abi}`);
  }
  const infoService = fs.readFileSync(path.resolve(root,
    "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java"), "utf8");
  const runtimeService = fs.readFileSync(path.resolve(root,
    "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java"), "utf8");
  validateProducerAssignments(infoService, runtimeService);
  return payloadEvidence;
}

function scanZipEntries(apkBytes, shouldRead, onBytes) {
  let eocd = -1;
  for (let i = apkBytes.length - 22; i >= Math.max(0, apkBytes.length - 65557); i -= 1) {
    if (apkBytes.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  check(eocd >= 0, "release APK ZIP end record is missing");
  const count = apkBytes.readUInt16LE(eocd + 10);
  let offset = apkBytes.readUInt32LE(eocd + 16);
  const names = new Set();
  for (let i = 0; i < count; i += 1) {
    check(apkBytes.readUInt32LE(offset) === 0x02014b50, "release APK central directory is malformed");
    const method = apkBytes.readUInt16LE(offset + 10);
    const compressedSize = apkBytes.readUInt32LE(offset + 20);
    const size = apkBytes.readUInt32LE(offset + 24);
    const nameLength = apkBytes.readUInt16LE(offset + 28);
    const extraLength = apkBytes.readUInt16LE(offset + 30);
    const commentLength = apkBytes.readUInt16LE(offset + 32);
    const localOffset = apkBytes.readUInt32LE(offset + 42);
    const name = apkBytes.subarray(offset + 46, offset + 46 + nameLength).toString("utf8");
    check(!names.has(name), `release APK contains a duplicate ZIP entry: ${name}`);
    names.add(name);
    if (shouldRead(name)) {
      check(apkBytes.readUInt32LE(localOffset) === 0x04034b50, `release APK local entry is malformed: ${name}`);
      const localNameLength = apkBytes.readUInt16LE(localOffset + 26);
      const localExtraLength = apkBytes.readUInt16LE(localOffset + 28);
      const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
      const compressed = apkBytes.subarray(dataOffset, dataOffset + compressedSize);
      const bytes = method === 0 ? compressed : method === 8 ? zlib.inflateRawSync(compressed) : fail(`unsupported APK ZIP method ${method}: ${name}`);
      check(bytes.length === size, `release APK entry size header mismatch: ${name}`);
      onBytes(name, bytes);
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return names;
}

function releaseApkCarrierAbi(apkFile) {
  const name = path.basename(apkFile);
  if (name.endsWith("-universal.apk")) return "universal";
  const abi = REQUIRED_ABIS.find(value => name.endsWith(`-${value}.apk`));
  check(abi, `release APK filename does not identify a supported ABI or universal carrier: ${name}`);
  return abi;
}

function validateReleaseApkCarrierSet(releaseApkFiles, required) {
  check(!required || releaseApkFiles.length > 0,
    "release APK carrier verification was required, but no --release-apk inputs were provided");
  const carrierAbis = releaseApkFiles.map(releaseApkCarrierAbi);
  if (releaseApkFiles.length > 0) {
    sameSet(carrierAbis, ["universal", ...REQUIRED_ABIS], "release APK carrier set");
  }
  return carrierAbis;
}

function verifyReleaseApk(kit, manifestBytes, apkFile) {
  const carrierAbi = releaseApkCarrierAbi(apkFile);
  const expectedPayloads = kit.payloads.filter(item =>
    item.role === "metadata" || carrierAbi === "universal" || item.abi === carrierAbi);
  const expectedByPath = new Map(expectedPayloads.map(item => [item.packagedPath, item]));
  const allPayloadByPath = new Map(kit.payloads.map(item => [item.packagedPath, item]));
  const manifestEntry = kit.diagnostics.installedApkEntry;
  const evidence = [];
  const entries = scanZipEntries(fs.readFileSync(apkFile), name => {
    if (name === manifestEntry) return true;
    const item = allPayloadByPath.get(name);
    if (!item) return false;
    check(expectedByPath.has(name), `ABI-specific release APK contains an unexpected foreign-ABI payload: ${name}`);
    return true;
  }, (name, bytes) => {
    if (name === manifestEntry) {
      check(bytes.equals(manifestBytes), "release APK runtime-kit manifest is not byte-identical");
      return;
    }
    const item = expectedByPath.get(name);
    check(bytes.length === item.size && sha256(bytes) === item.sha256, `release APK payload identity mismatch: ${item.id}`);
    check(elfBuildId(bytes) === item.buildId, `release APK payload Build ID mismatch: ${item.id}`);
    evidence.push({id: item.id, entry: item.packagedPath, size: bytes.length, sha256: item.sha256, buildId: item.buildId});
  });
  check(entries.has(manifestEntry), "release APK runtime-kit manifest is missing");
  for (const item of expectedPayloads) check(entries.has(item.packagedPath), `release APK payload is missing: ${item.packagedPath}`);
  evidence.sort((left, right) => left.id.localeCompare(right.id));
  return {carrierAbi, payloadEvidence: evidence};
}

function runGit(root, args) {
  const out = childProcess.spawnSync("git", args, {cwd: root, encoding: "utf8", windowsHide: true});
  check(out.status === 0, `git ${args.join(" ")} failed`);
  return out.stdout.trim();
}

function lockText(manifest, digest, size, gitBase) {
  return [
    "# Immutable AutoJs6 Node plugin Runtime Kit distribution lock.",
    "format=1",
    `schema=${LOCK_SCHEMA}`,
    `kit.id=${manifest.kitId}`,
    `kit.version=${manifest.kitVersion}`,
    `file=${RELEASE_FILE}`,
    `size=${size}`,
    `sha256=${digest}`,
    `build.variant=${manifest.buildProvenance.bridgeBuild.variant}`,
    "delivery=plugin_apk",
    `apk.entry=${manifest.diagnostics.installedApkEntry}`,
    `runtime.slot=${manifest.runtime.runtimeSlot}`,
    `runtime.nodeVersion=${manifest.runtime.nodeVersion}`,
    `contract.min=${manifest.contract.min}`,
    `contract.max=${manifest.contract.max}`,
    `contract.moduleSourceProvider=${manifest.contract.moduleSourceProvider}`,
    `nodejs-api.coordinate=${manifest.nodeJsApi.coordinate}`,
    `nodejs-api.version=${manifest.nodeJsApi.version}`,
    `nodejs-api.sha256=${manifest.nodeJsApi.sha256}`,
    `nodejs-api.aidlTransactions.sha256=${manifest.nodeJsApi.aidlTransactionsSha256}`,
    `catalog.version=${manifest.capabilityCatalog.version}`,
    `catalog.sha256=${manifest.capabilityCatalog.sha256}`,
    `payload.count=${manifest.payloads.length}`,
    `reproducibility.artifact=${manifest.reproducibility.artifactMaterialization}`,
    `reproducibility.sourceBuild=${manifest.reproducibility.sourceBuildStatus}`,
    `upstream.url=${manifest.buildProvenance.upstreamArtifact.url}`,
    `upstream.sha256=${manifest.buildProvenance.upstreamArtifact.sha256}`,
    `materialize.command=powershell -ExecutionPolicy Bypass -File tools/nodejs/runtime-build/build-node-runtime.ps1 -Execute`,
    `verify.command=.\\gradlew.bat :app:verifyNodePluginRuntimeKitGate`,
    `source.file=${SOURCE_FILE}`,
    "source.owner=AutoJs6-Plugin-NodeJs-Runtime",
    "source.repository=AutoJs6-Plugin-NodeJs-Runtime",
    `source.gitBase=${gitBase}`,
    `source.sha256=${digest}`,
    "",
  ].join("\n");
}

function parseProperties(text) {
  const result = Object.create(null);
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.startsWith("#")) continue;
    const split = line.indexOf("=");
    check(split > 0, `invalid lock line '${line}'`);
    result[line.slice(0, split)] = line.slice(split + 1);
  }
  return result;
}

function stageRelease(root, source) {
  const releaseDir = path.resolve(root, RELEASE_ROOT);
  const releaseFile = path.join(releaseDir, RELEASE_FILE);
  const lockFile = path.join(releaseDir, RELEASE_LOCK);
  fs.mkdirSync(releaseDir, {recursive: true});
  const gitBase = fs.existsSync(lockFile)
    ? parseProperties(fs.readFileSync(lockFile, "utf8"))["source.gitBase"]
    : runGit(root, ["rev-parse", "HEAD"]);
  check(/^[0-9a-f]{40}$/.test(gitBase || ""), "Runtime Kit release source.gitBase is invalid");
  const expectedLock = lockText(source.value, source.digest, source.bytes.length, gitBase);
  if (fs.existsSync(releaseFile)) check(fs.readFileSync(releaseFile).equals(source.bytes), "refusing to overwrite a different Runtime Kit release manifest");
  else fs.writeFileSync(releaseFile, source.bytes);
  if (fs.existsSync(lockFile)) check(fs.readFileSync(lockFile, "utf8") === expectedLock, "refusing to overwrite a different Runtime Kit release lock");
  else fs.writeFileSync(lockFile, expectedLock, "utf8");
}

function verifyRelease(root, source) {
  const releaseFile = path.resolve(root, RELEASE_ROOT, RELEASE_FILE);
  const lockFile = path.resolve(root, RELEASE_ROOT, RELEASE_LOCK);
  check(fs.readFileSync(releaseFile).equals(source.bytes), "released Runtime Kit manifest differs from canonical source");
  const lock = parseProperties(fs.readFileSync(lockFile, "utf8"));
  check(/^[0-9a-f]{40}$/.test(lock["source.gitBase"] || ""), "release lock source.gitBase invalid");
  check(fs.readFileSync(lockFile, "utf8") === lockText(source.value, source.digest, source.bytes.length, lock["source.gitBase"]),
    "Runtime Kit release lock is not canonical or has drifted");
}

function selfTest(base, root) {
  const mutations = [
    kit => { kit.schema = "wrong"; },
    kit => { kit.kitVersion = "2.0.0"; },
    kit => { kit.contract.moduleSourceProvider = 1; },
    kit => { kit.nodeJsApi.sha256 = "0".repeat(64); },
    kit => { kit.runtime.supportedAbis.pop(); },
    kit => { kit.reproducibility.sourceBuildStatus = "ready"; },
    kit => { kit.reproducibility.releaseReady = true; },
    kit => { kit.payloads[0].sha256 = "invalid"; },
    kit => { kit.payloads[1].delivery = "source_tree"; },
    kit => { kit.payloads[2].id = kit.payloads[1].id; },
    kit => { kit.payloads.find(item => item.role === "native_dependency").fileName = "libwrong.so"; },
    kit => { kit.payloads.find(item => item.role === "native_dependency").role = "native_bridge"; },
  ];
  for (const mutate of mutations) {
    const copy = JSON.parse(JSON.stringify(base));
    mutate(copy);
    let rejected = false;
    try { validateShape(copy); } catch (_) { rejected = true; }
    check(rejected, "Runtime Kit verifier self-test mutation was not rejected");
  }
  const infoService = fs.readFileSync(path.resolve(root,
    "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java"), "utf8");
  const runtimeService = fs.readFileSync(path.resolve(root,
    "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java"), "utf8");
  const producerMutations = [
    [infoService.replace("nodeRuntimeKitSchema", "nodeRuntimeKitWrongSchema"), runtimeService],
    [infoService, runtimeService.replace(
      "info.putString(KEY_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);",
      "info.putString(KEY_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);"
    )],
  ];
  for (const [mutatedInfo, mutatedRuntime] of producerMutations) {
    let rejected = false;
    try { validateProducerAssignments(mutatedInfo, mutatedRuntime); } catch (_) { rejected = true; }
    check(rejected, "Runtime Kit producer assignment mutation was not rejected");
  }
  const carrierSetMutations = [
    [],
    ["plugin-universal.apk", "plugin-arm64-v8a.apk", "plugin-armeabi-v7a.apk"],
  ];
  for (const mutatedCarrierSet of carrierSetMutations) {
    let rejected = false;
    try { validateReleaseApkCarrierSet(mutatedCarrierSet, true); } catch (_) { rejected = true; }
    check(rejected, "Runtime Kit required release APK carrier-set mutation was not rejected");
  }
  return mutations.length + producerMutations.length + carrierSetMutations.length;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const root = path.resolve(args["project-root"] || path.resolve(__dirname, "../../.."));
  const manifestFile = path.resolve(args.manifest || path.join(root, SOURCE_FILE));
  const source = readCanonicalJson(manifestFile, "Node plugin Runtime Kit manifest");
  validateShape(source.value);
  const payloadEvidence = validateRepository(source.value, root);
  const selfTests = args["self-test"] ? selfTest(source.value, root) : 0;
  if (args["stage-release"] || args["verify-release"]) {
    check(
      source.value.kitVersion === RELEASE_TARGET_VERSION && source.value.kitId === RELEASE_TARGET_ID,
      `Runtime Kit release target must be ${RELEASE_TARGET_ID}; current manifest is ${source.value.kitId}`
    );
  }
  if (args["stage-release"]) stageRelease(root, source);
  if (args["verify-release"] || args["stage-release"]) verifyRelease(root, source);
  const releaseApkFiles = (args["release-apk"] || []).map(file => path.resolve(file));
  const releaseApkVerificationRequired = args["require-release-apks"] === true;
  check(!releaseApkVerificationRequired || args["verify-release"] === true,
    "--require-release-apks requires --verify-release");
  validateReleaseApkCarrierSet(releaseApkFiles, releaseApkVerificationRequired);
  const apkSetEvidence = releaseApkFiles.map(apkFile => {
    const bytes = fs.readFileSync(apkFile);
    const verified = verifyReleaseApk(source.value, source.bytes, apkFile);
    return {
      file: slash(path.relative(root, apkFile)),
      carrierAbi: verified.carrierAbi,
      size: bytes.length,
      sha256: sha256(bytes),
      payloadEvidence: verified.payloadEvidence,
    };
  });
  const releaseControlPlane = (args["verify-release"] || args["stage-release"]) ? (() => {
    const releaseManifest = path.resolve(root, RELEASE_ROOT, RELEASE_FILE);
    const releaseLock = path.resolve(root, RELEASE_ROOT, RELEASE_LOCK);
    const manifestBytes = fs.readFileSync(releaseManifest);
    const lockBytes = fs.readFileSync(releaseLock);
    return {
      manifest: {file: slash(path.relative(root, releaseManifest)), size: manifestBytes.length, sha256: sha256(manifestBytes)},
      lock: {file: slash(path.relative(root, releaseLock)), size: lockBytes.length, sha256: sha256(lockBytes)},
      carriesNativePayloads: false,
      payloadCarrier: "release_universal_plugin_apk",
    };
  })() : null;
  const universalApk = apkSetEvidence.find(item => item.carrierAbi === "universal") || null;
  const report = {
    schema: "autojs6-node-plugin-runtime-kit-report-v1",
    decision: "usable_prebuilt_source_build_bootstrap_only",
    verificationLevel: releaseApkFiles.length > 0 ? "S/A" : "S",
    manifest: {file: slash(path.relative(root, manifestFile)), schema: source.value.schema, id: source.value.kitId,
      version: source.value.kitVersion, size: source.bytes.length, sha256: source.digest},
    runtime: source.value.runtime,
    contract: source.value.contract,
    nodeJsApi: source.value.nodeJsApi,
    capabilityCatalog: source.value.capabilityCatalog,
    reproducibility: source.value.reproducibility,
    payloadEvidence,
    releaseControlPlane,
    releaseApk: universalApk && universalApk.file,
    releaseApkIdentity: universalApk && {file: universalApk.file, size: universalApk.size, sha256: universalApk.sha256},
    releaseApkEvidence: universalApk ? universalApk.payloadEvidence : [],
    releaseApks: apkSetEvidence,
    releaseApkCarrierVerification: releaseApkFiles.length > 0 ? "verified" : "not_requested",
    selfTests,
  };
  if (args.report) {
    const reportFile = path.resolve(args.report);
    fs.mkdirSync(path.dirname(reportFile), {recursive: true});
    fs.writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
  }
  console.log(`Node plugin Runtime Kit: ${source.value.kitId}`);
  console.log(`Manifest SHA-256: ${source.digest}`);
  const apkEntryCount = apkSetEvidence.reduce((total, apk) => total + apk.payloadEvidence.length, 0);
  if (releaseApkFiles.length > 0) {
    console.log(`Payloads verified: ${payloadEvidence.length}; release APKs verified: ${apkSetEvidence.length}; release APK entries verified: ${apkEntryCount}`);
  } else {
    console.log(`Payloads verified: ${payloadEvidence.length}; release APK carrier verification: not requested (run :app:verifyNodePluginRuntimeKitGate)`);
  }
  console.log(`Decision: ${report.decision}`);
}

try { main(); }
catch (error) { console.error(`Node plugin Runtime Kit verification failed: ${error.message}`); process.exitCode = 1; }

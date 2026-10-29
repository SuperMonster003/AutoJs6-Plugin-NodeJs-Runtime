"use strict";
// Desktop test of zip.js (node --test test.js): argument checks, option -> ZipParameters call sequences, handle
// releases and the queue, against a recording mock of the Java facade; plus a parse check of every case of cases.js.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = require("./extract.js")("zip");
const createZip = new Function(`${source}\nreturn __autojs6_js_tools_zip;`)();
const {cases} = require("./cases-zip.js");

const CALL_FAILED = "ERR_AUTOJS6_JAVA_CALL_FAILED";
const WORK = 2147483647;
const ZIP_PARAMETERS = "net.lingala.zip4j.model.ZipParameters";
const UNZIP_PARAMETERS = "net.lingala.zip4j.model.UnzipParameters";
const short = className => className.slice(className.lastIndexOf(".") + 1);
const isConstantClass = className => className.startsWith("net.lingala.zip4j.model.enums.") || className.endsWith("$SymbolicLinkAction");
const facadeError = (className, message, code = CALL_FAILED) => Object.assign(new Error(`call: ${className}: ${message}`), {code});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// A recording Java facade: handles are frozen snapshots with call(); log keeps every call, live the unreleased handles.
function mockJava(options = {}) {
  let next = 0;
  const log = [];
  const live = new Map();
  const data = new Map();
  const behavior = options.behavior || {};
  const exists = options.exists || (() => true);
  const isFile = options.isFile || (() => true);
  const instanceOf = options.instanceOf || ((target, type) => type === "java.lang.Iterable" || type === "java.util.Collection");
  function handle(className, info = {}, snapshot = {}) {
    const id = `java-${++next}`;
    const h = {__autojs6JavaHandle: id, className, value: {string: `${className}@${id}`, ...snapshot}};
    Object.defineProperty(h, "call", {value: (method, args, opts) => api.call(h, method, args, opts)});
    Object.defineProperty(h, "retain", {value: () => api.retain(h)});
    live.set(id, Object.freeze(h));
    data.set(id, {className, calls: [], ...info});
    return h;
  }
  const header = name => handle("net.lingala.zip4j.model.FileHeader", {name});
  function check(target) {
    if (!live.has(target.__autojs6JavaHandle)) {
      throw Object.assign(new Error("call: Java object handle is released or belongs to another execution"), {code: "ERR_AUTOJS6_JAVA_METHOD_DENIED"});
    }
  }
  function fallback(target, info, method, args) {
    switch (`${short(target.className)}.${method}`) {
      case "File.exists": return exists(info.args[0]);
      case "File.isFile": return isFile(info.args[0]);
      case "ZipFile.getFileHeaders": return handle("java.util.ArrayList", {items: [header("a.txt"), header("b.txt")]});
      case "ZipFile.getFileHeader": return header(args[0]);
      case "ZipFile.isEncrypted": return false;
      case "ZipFile.isValidZipFile": return true;
    }
    if (method === "toArray") return Object.freeze(info.items.slice());
    if (method === "iterator") return handle("java.util.Iterator", {items: info.items.slice(), index: 0});
    if (method === "hasNext") return info.index < info.items.length;
    if (method === "next") return info.items[info.index++];
    return null;
  }
  const api = {
    async new(className, args = [], opts = {}) {
      log.push({op: "new", className, args, types: opts.parameterTypes, timeoutMs: opts.timeoutMs});
      return handle(className, {args});
    },
    async getStatic(className, field) {
      log.push({op: "getStatic", className, field});
      return handle(className, {}, {string: field, name: field});
    },
    async callStatic(className, method, args = [], opts = {}) {
      log.push({op: "callStatic", className, method, args, types: opts.parameterTypes});
      assert.equal(`${className}.${method}`, "java.util.Arrays.asList");
      return handle("java.util.Arrays$ArrayList", {items: args[0]});
    },
    async call(target, method, args = [], opts = {}) {
      check(target);
      const info = data.get(target.__autojs6JavaHandle);
      info.calls.push([method, args]);
      log.push({op: "call", className: target.className, target, method, args, types: opts.parameterTypes, timeoutMs: opts.timeoutMs});
      const custom = behavior[`${short(target.className)}.${method}`];
      return custom ? custom(args, target, info) : fallback(target, info, method, args);
    },
    async instanceOf(target, type) {
      log.push({op: "instanceOf", className: target.className, type});
      return instanceOf(target, type);
    },
    async retain(target) {
      check(target);
      log.push({op: "retain", className: target.className});
      return handle(target.className, data.get(target.__autojs6JavaHandle));
    },
    async release(target) {
      log.push({op: "release", className: target.className, id: target.__autojs6JavaHandle});
      return live.delete(target.__autojs6JavaHandle);
    },
    async proxy(interfaces, implementation, opts) {
      log.push({op: "proxy", interfaces, opts});
      return handle("$Proxy7", {implementation, opts});
    },
  };
  // The unreleased handles other than the cached enum constants, by simple class name.
  const owned = () => [...live.values()].filter(h => !isConstantClass(h.className)).map(h => short(h.className)).sort();
  return {api, log, live, data, handle, owned};
}

function load(options = {}) {
  const java = mockJava(options);
  const host = {java: () => java.api, cwd: () => options.cwd === undefined ? "/sdcard/Scripts" : options.cwd};
  return {zip: createZip(host), java};
}
// The calls on objects of a class as [method, value]: a handle by its name (enum constants) or class, a value as is.
function calls(java, className) {
  const shown = v => v && typeof v === "object" && v.__autojs6JavaHandle ? (v.value.name || short(v.className)) : v;
  return java.log.filter(e => e.op === "call" && short(e.className) === className).map(e => [e.method, ...e.args.map(shown)]);
}
const javaError = name => e => e.name === name;
const DEFAULT_SETTERS = [
  ["setAesKeyStrength", "KEY_STRENGTH_256"], ["setAesVersion", "TWO"], ["setCompressionLevel", "NORMAL"],
  ["setCompressionMethod", "DEFLATE"], ["setEncryptionMethod", "AES"], ["setSymbolicLinkAction", "INCLUDE_LINKED_FILE_ONLY"],
];

test("zip.js is one self-contained function for the shared core", () => {
  assert.equal(source.split("\n")[0], "    function __autojs6_js_tools_zip(host) {");
  assert.ok(!source.includes(")JS\""));
  assert.ok([...source].every(c => c === "\n" || (c >= " " && c <= "~")), "ASCII without control characters");
  for (const name of [/require[(]/, /process[.]/, /globalThis/, /(^|[^A-Za-z])Buffer/, /import[(]/]) assert.ok(!name.test(source), String(name));
  assert.deepEqual([...new Set(source.match(/host[.][A-Za-z]+/g))].sort(), ["host.cwd", "host.java"]);
});

test("module shape", () => {
  const {zip} = load();
  assert.equal(typeof zip, "function");
  assert.deepEqual(Object.keys(zip), ["open", "zipFile", "zipDir", "zipFiles", "unzip"]);
  assert.deepEqual([zip, zip.open, zip.zipFile, zip.zipDir, zip.zipFiles, zip.unzip].map(f => [f.name, f.length]),
    [["zip", 1], ["open", 1], ["zipFile", 2], ["zipDir", 2], ["zipFiles", 2], ["unzip", 2]]);
});

test("argument errors are thrown synchronously before any Java call", () => {
  const {zip, java} = load();
  const deep = "../".repeat(5) + "x.zip";
  const table = [
    [() => zip(), "TypeError", "Failed to call method \"zip\": [ Method only accepts 1 argument ]"],
    [() => zip(null), "TypeError", "Failed to call method \"zip\": [ Argument \"zipPath\" must be of type string, got null ]"],
    [() => zip("a", 1), "TypeError", null],
    [() => zip(" "), "RangeError", "Failed to call method \"zip\": [ Argument \"zipPath\" cannot be \" \" ]"],
    [() => zip.open(), "TypeError", "Failed to call method \"zip.open\": [ Method only accepts a number of arguments in the range [1..2] ]"],
    [() => zip.open(" ", "x"), "RangeError", null],
    [() => zip.open("/d/a.zip", "x"), "TypeError", "Failed to call method \"zip.open\": [ Argument \"options\" must be of type Object, got string ]"],
    [() => zip.open("/d/a.zip", [1]), "TypeError", null],
    [() => zip.open("/d/a.zip", () => {}), "TypeError", null],
    [() => zip.zipFile("/d/a.txt"), "TypeError", null],
    [() => zip.zipFile(" ", [], {}), "TypeError", "Failed to call method \"zip.zipFile\": [ Argument \"destZipPath\" must be of type string | null | undefined, got Array ]"],
    [() => zip.zipFile(" ", "/d/q.zip"), "RangeError", null],
    [() => zip.zipFile("/d/a.txt", " "), "IllegalArgumentException", "java.lang.IllegalArgumentException: Argument \"path\"   (String) for zip.zipFile cannot be empty"],
    [() => zip.zipFile("/d/a.txt", {aesKeyStrength: 5}), "InternalError", "Options property aesKeyStrength 5 (Number) is invalid for zip.zipFile"],
    [() => zip.zipDir("", "/d/q.zip"), "RangeError", null],
    [() => zip.zipFiles(null, "/d/q.zip"), "TypeError", "Failed to call method \"zip.zipFiles\": [ Argument \"filePathList\" cannot be null ]"],
    [() => zip.zipFiles("a", "/d/q.zip"), "TypeError", "Failed to call method \"zip.zipFiles\": [ Argument \"filePathList\" must be of type Array | java.lang.Iterable, got string ]"],
    [() => zip.zipFiles([null], "/d/q.zip"), "TypeError", null],
    [() => zip.zipFiles([" "], "/d/q.zip"), "RangeError", "Failed to call method \"zip.zipFiles\": [ Argument \"filePathList[0]\" cannot be \" \" ]"],
    [() => zip.unzip("/d/a.zip"), "TypeError", null],
    [() => zip.unzip("/d/a.zip", () => {}), "TypeError", null],
    [() => zip.unzip("/d/a.zip", "/d/out", {ignoreDateTimeAttributes: 0}), "InternalError",
      "Options property ignoreDateTimeAttributes for zip.unzip is no longer supported since Zip4j library version 2.x"],
    [() => zip.open("/d/a.zip", {aesKeyStrength: 100}), "InternalError", "Options property aesKeyStrength 100 (Number) is invalid for zip.open"],
    [() => zip.open("/d/a.zip", {aesKeyStrength: "bad"}), "IllegalArgumentException",
      "java.lang.IllegalArgumentException: No enum constant net.lingala.zip4j.model.enums.AesKeyStrength.BAD"],
    [() => zip.open("/d/a.zip", {aesKeyStrength: NaN}), "IllegalArgumentException", "java.lang.IllegalArgumentException: Failed to coerce NaN (Number) into a valid number"],
    [() => zip.open("/d/a.zip", {compressionLevel: Infinity}), "IllegalArgumentException",
      "java.lang.IllegalArgumentException: Failed to coerce Infinity (Number) into a finite Int number"],
    [() => zip.open("/d/a.zip", {compressionLevel: 3e9}), "IllegalArgumentException",
      "java.lang.IllegalArgumentException: Failed to coerce 3000000000 (Number) into a Int number without overflow"],
    [() => zip.open("/d/a.zip", {encryptionMethod: 7}), "InternalError", "Options property encryptionMethod 7 (Number) is invalid for zip.open"],
    [() => zip.open("/d/a.zip", {symbolicLinkAction: 1}), "IllegalArgumentException",
      "java.lang.IllegalArgumentException: No enum constant net.lingala.zip4j.model.ZipParameters.SymbolicLinkAction.1"],
    [() => zip.open("/d/a.zip", {excludeFileFilter: "x"}), "InternalError",
      "Options property excludeFileFilter x (String) for zip.open must be a ExcludeFileFilter or a JavaScript Function that can be used as an ExcludeFileFilter"],
    [() => zip.open("/d/a.zip", {entryCRC: "abc"}), "IllegalArgumentException", "java.lang.IllegalArgumentException: Failed to coerce abc (String) into a valid number"],
    [() => zip.open("/d/a.zip", {lastModifiedFileTime: 1e30}), "IllegalArgumentException",
      "java.lang.IllegalArgumentException: Failed to coerce 1e+30 (Number) into a Long number without overflow"],
    [() => zip.open("/d/a.zip", {isIgnoreDateTimeAttributes: false}), "InternalError", null],
    [() => zip(deep), "IllegalArgumentException", "java.lang.IllegalArgumentException: Resolved path for \"%1$s\" cannot be null"],
    [() => zip.unzip("/d/a.zip", deep), "IllegalArgumentException", null],
  ];
  for (const [run, name, message] of table) {
    let error = null;
    let result;
    try {
      result = run();
    } catch (e) {
      error = e;
    }
    assert.ok(error, `${run} throws synchronously, got ${result}`);
    assert.equal(error.name, name, String(run));
    if (message !== null) assert.equal(error.message, message, String(run));
  }
  assert.deepEqual(java.log, []);
});

test("zip.open builds the ZipParameters and the UnzipParameters like ZipNativeObject", async () => {
  const {zip, java} = load();
  const z = await zip.open("/d/a.zip");
  assert.deepEqual(java.log.filter(e => e.op === "new").map(e => [short(e.className), e.args, e.types]),
    [["ZipFile", ["/d/a.zip"], ["java.lang.String"]], ["ZipParameters", [], []], ["UnzipParameters", [], []]]);
  assert.deepEqual(calls(java, "ZipParameters"), DEFAULT_SETTERS);
  assert.deepEqual(java.log.filter(e => e.op === "call" && e.method === "setCompressionLevel")[0].types,
    ["net.lingala.zip4j.model.enums.CompressionLevel"]);
  assert.deepEqual(java.log.filter(e => e.op === "call" && e.method === "setSymbolicLinkAction")[0].types,
    ["net.lingala.zip4j.model.ZipParameters$SymbolicLinkAction"]);
  assert.deepEqual(calls(java, "ZipFile"), []);
  assert.deepEqual([z.name, z.path, z.options], ["open", "/d/a.zip", {}]);
  assert.deepEqual(java.owned(), ["UnzipParameters", "ZipFile", "ZipParameters"]);
  // The constants are looked up once.
  await zip("/d/b.zip");
  assert.equal(java.log.filter(e => e.op === "getStatic").length, 6);
});

test("options become ZipParameters calls in Rhino's order", async () => {
  const {zip, java} = load();
  await zip.open("/d/a.zip", {
    aesKeyStrength: 1, aesVersion: "1", compressionLevel: "deflate_level_fast", compressionMethod: "comp_aes_enc",
    encryptionMethod: "enc_method_strong", symbolicLinkAction: "include_link_only", defaultFolderPath: "/x", entryCRC: "42",
    entrySize: 7.9, comment: "c", fileNameInZip: "n", includeRootFolder: 0, isOverrideExistingFilesInZip: 1, readHiddenFiles: "",
    isReadHiddenFolders: null, readHiddenFolders: "y", unixMode: true, writeExtendedLocalFileHeader: false,
    lastModifiedFileTime: -5.5, rootFolderNameInZip: "r", encryptFiles: false, password: "pw",
  });
  assert.deepEqual(calls(java, "ZipParameters"), [
    ["setAesKeyStrength", "KEY_STRENGTH_128"], ["setAesVersion", "ONE"], ["setCompressionLevel", "FAST"],
    ["setCompressionMethod", "AES_INTERNAL_ONLY"], ["setEncryptionMethod", "ZIP_STANDARD_VARIANT_STRONG"],
    ["setDefaultFolderPath", "/x"], ["setEntryCRC", 42n], ["setEntrySize", 7n], ["setFileComment", "c"], ["setFileNameInZip", "n"],
    ["setIncludeRootFolder", false], ["setOverrideExistingFilesInZip", true], ["setReadHiddenFiles", false], ["setReadHiddenFolders", true],
    ["setUnixMode", true], ["setWriteExtendedLocalFileHeader", false], ["setLastModifiedFileTime", -5n], ["setRootFolderNameInZip", "r"],
    ["setSymbolicLinkAction", "INCLUDE_LINK_ONLY"], ["setEncryptFiles", false], ["setEncryptFiles", true],
  ]);
  const typesOf = method => java.log.find(e => e.method === method).types;
  assert.deepEqual([typesOf("setEntryCRC"), typesOf("setFileComment"), typesOf("setUnixMode")], [["long"], ["java.lang.String"], ["boolean"]]);
  // The password goes to the ZipFile once, before setEncryptFiles(true); the UnzipParameters would set the same again.
  assert.deepEqual(calls(java, "ZipFile"), [["setPassword", ["p", "w"]]]);
  assert.deepEqual(java.log.find(e => e.method === "setPassword").types, ["char[]"]);
  const order = java.log.filter(e => e.op === "call").map(e => e.method);
  assert.ok(order.indexOf("setPassword") < order.lastIndexOf("setEncryptFiles"));
});

test("numbers, names and Java values of the enum options", async () => {
  const {zip, java} = load();
  const level = java.handle("net.lingala.zip4j.model.enums.CompressionLevel", {}, {string: "ULTRA", name: "ULTRA"});
  const cases = [
    [{aesKeyStrength: 192.7, aesVersion: 5, compressionLevel: 42, compressionMethod: 3, encryptionMethod: 99},
      ["KEY_STRENGTH_192", "TWO", "NORMAL", "DEFLATE", "AES", "INCLUDE_LINKED_FILE_ONLY"]],
    [{aesKeyStrength: "256", aesVersion: "two", compressionLevel: -0, compressionMethod: 8, encryptionMethod: -1},
      ["KEY_STRENGTH_256", "TWO", "NO_COMPRESSION", "DEFLATE", "NONE", "INCLUDE_LINKED_FILE_ONLY"]],
    [{aesKeyStrength: [128], compressionLevel: level, compressionMethod: "Store", encryptionMethod: "enc_no", symbolicLinkAction: "include_link_and_linked_file"},
      ["KEY_STRENGTH_128", "TWO", "ULTRA", "STORE", "NONE", "INCLUDE_LINK_AND_LINKED_FILE"]],
    [{encryptionMethod: "standard"}, ["KEY_STRENGTH_256", "TWO", "NORMAL", "DEFLATE", "ZIP_STANDARD", "INCLUDE_LINKED_FILE_ONLY"]],
  ];
  for (const [options, expected] of cases) {
    java.log.length = 0;
    await zip.open("/d/a.zip", options);
    assert.deepEqual(calls(java, "ZipParameters").map(c => c[1]), expected, JSON.stringify(options));
  }
  // The Java value is passed on, not looked up.
  assert.equal(java.log.filter(e => e.op === "getStatic" && e.field === "ULTRA").length, 0);
});

test("zipFile adds the file and keeps only the Zip's own handles", async () => {
  const {zip, java} = load();
  const z = await zip.zipFile("/d/a.txt", "/d/one.zip");
  const add = java.log.find(e => e.method === "addFile");
  assert.deepEqual([short(add.className), add.args.map(a => short(a.className)), add.types, add.timeoutMs],
    ["ZipFile", ["File", "ZipParameters"], ["java.io.File", ZIP_PARAMETERS], WORK]);
  assert.deepEqual(java.log.find(e => e.op === "new" && e.className === "java.io.File").args, ["/d/a.txt"]);
  assert.ok(java.log.some(e => e.op === "release" && e.className === "java.io.File"));
  assert.equal(java.log.find(e => e.method === "setAesKeyStrength").timeoutMs, undefined);
  assert.deepEqual([z.name, z.path], ["zipFile", "/d/one.zip"]);
  assert.deepEqual(java.owned(), ["UnzipParameters", "ZipFile", "ZipParameters"]);
  const dir = await zip.zipDir("/d/tree", "/d/tree.zip");
  const split = java.log.find(e => e.method === "createSplitZipFileFromFolder");
  assert.deepEqual([split.args.slice(2), split.types], [[false, -1], ["java.io.File", ZIP_PARAMETERS, "boolean", "long"]]);
  assert.equal(dir.name, "zipDir");
  const u = await zip.unzip("/d/one.zip", "/d/out", {password: "p"});
  const extract = java.log.find(e => e.method === "extractAll");
  assert.deepEqual([extract.args[0], short(extract.args[1].className), u.name], ["/d/out", "UnzipParameters", "unzip"]);
});

test("default destinations", async () => {
  const {zip, java} = load({isFile: p => p.endsWith(".txt")});
  const pathOf = async promise => (await promise).path;
  assert.equal(await pathOf(zip.zipFile("/d/x.tar.gz", undefined)), "/sdcard/Scripts/x.tar.zip");
  const withOptions = await zip.zipFile("/d/a.txt", {password: "p"});
  assert.deepEqual([withOptions.path, withOptions.options], ["/sdcard/Scripts/a.zip", {password: "p"}]);
  assert.equal(await pathOf(zip.zipDir("/d/dir.v1/", null)), "/sdcard/Scripts/dir.zip");
  assert.equal(await pathOf(zip.zipFiles(["/d/a.txt"], null)), "/sdcard/Scripts/a.zip");
  assert.equal(await pathOf(zip.zipFiles(["/d/a.txt", "/d//b.txt"], null)), "/sdcard/Scripts/d.zip");
  assert.equal(await pathOf(zip.zipFiles(["/d/sub", "/d/sub/a.txt"], null)), "/sdcard/Scripts/sub.zip");
  assert.equal(await pathOf(zip.zipFiles(["/a.txt", "/b.txt"], null)), "/sdcard/Scripts/.zip");
  assert.match(await pathOf(zip.zipFiles(["/d/a.txt", "/e/b.txt"], null)), /^[/]sdcard[/]Scripts[/][0-9]{8}-[0-9]{6}-[0-9A-F]{4}[.]zip$/);
  await zip.unzip("/d/a.zip", undefined);
  assert.equal(java.log.filter(e => e.method === "extractAll").pop().args[0], "/sdcard/Scripts");
  assert.equal(await pathOf(zip.zipFiles([], "/d/e.zip")), "/d/e.zip");
});

test("relative paths resolve like Files.path", async () => {
  const {zip} = load({cwd: "/sdcard/Scripts/"});
  const pathOf = async p => (await zip(p)).path;
  assert.equal(await pathOf("a/../b/./c.zip"), "/sdcard/Scripts/b/c.zip");
  assert.equal(await pathOf("../x.zip"), "/sdcard/x.zip");
  assert.equal(await pathOf("t/"), "/sdcard/Scripts/t/");
  assert.equal(await pathOf("a/ /b//c.zip"), "/sdcard/Scripts/a/b/c.zip");
  assert.equal(await pathOf("/abs//x/"), "/abs//x/");
  const root = load({cwd: "/"});
  assert.equal((await root.zip("x.zip")).path, "/x.zip");
  assert.throws(() => root.zip("../x.zip"), javaError("IllegalArgumentException"));
  assert.throws(() => load({cwd: null}).zip("x.zip"), javaError("IllegalArgumentException"));
});

test("a failed operation closes the ZipFile, releases every handle and reports the Java exception", async () => {
  const failure = facadeError("net.lingala.zip4j.exception.ZipException", "java.io.FileNotFoundException: /d/m.txt: open failed");
  const {zip, java} = load({behavior: {"ZipFile.addFile": () => { throw failure; }}});
  await assert.rejects(zip.zipFile("/d/m.txt", "/d/m.zip"), e => {
    assert.equal(e.name, "ZipException");
    assert.equal(e.message, "net.lingala.zip4j.exception.ZipException: java.io.FileNotFoundException: /d/m.txt: open failed");
    assert.equal(e.cause, failure);
    assert.equal(e.code, undefined);
    return true;
  });
  assert.deepEqual(calls(java, "ZipFile").map(c => c[0]), ["addFile", "close"]);
  assert.deepEqual(java.owned(), []);
  // Other facade errors are kept as they are.
  const denied = facadeError("x.Y", "z", "ERR_AUTOJS6_JAVA_METHOD_DENIED");
  const other = load({behavior: {"ZipFile.isEncrypted": () => { throw denied; }}});
  const z = await other.zip("/d/a.zip");
  await assert.rejects(z.isEncrypted(), e => e === denied);
});

test("zipFiles checks the elements in order and the options after the files", async () => {
  const {zip, java} = load({exists: p => !p.includes("missing")});
  const missing = zip.zipFiles(["/d/missing.txt", null], "/d/q.zip");
  await assert.rejects(missing, e => e.name === "IllegalArgumentException" && e.message === "java.lang.IllegalArgumentException: File /d/missing.txt does not exist");
  await assert.rejects(zip.zipFiles(["/d/a.txt", null], "/d/q.zip"), e => e instanceof TypeError && e.message.includes("filePathList[1]"));
  await assert.rejects(zip.zipFiles(["/d/missing.txt"], {encryptionMethod: 7}), javaError("IllegalArgumentException"));
  await assert.rejects(zip.zipFiles(["/d/a.txt"], {encryptionMethod: 7}), javaError("InternalError"));
  assert.deepEqual(java.owned(), []);
  const z = await zip.zipFiles(["/d/a.txt", "b.txt"], "/d/q.zip");
  const asList = java.log.find(e => e.op === "callStatic");
  assert.deepEqual([asList.args[0].map(h => short(h.className)), asList.types], [["File", "File"], ["java.lang.Object[]"]]);
  assert.deepEqual(java.log.filter(e => e.op === "new" && e.className === "java.io.File").map(e => e.args[0]).slice(-2), ["/d/a.txt", "/sdcard/Scripts/b.txt"]);
  const add = java.log.find(e => e.method === "addFiles");
  assert.deepEqual([add.types, add.timeoutMs], [["java.util.List", ZIP_PARAMETERS], WORK]);
  assert.equal(z.name, "zipFiles");
  assert.deepEqual(java.owned(), ["UnzipParameters", "ZipFile", "ZipParameters"]);
  // A Java Iterable (Node: a handle) is read through its iterator.
  const list = java.handle("java.util.HashSet", {items: ["/d/a.txt"]});
  assert.equal((await zip.zipFiles(list, null)).path, "/sdcard/Scripts/a.zip");
  const notIterable = load({instanceOf: () => false});
  await assert.rejects(notIterable.zip.zipFiles(notIterable.java.handle("java.lang.Object"), "/d/q.zip"), e => e instanceof TypeError);
});

test("Zip members check their arguments synchronously", async () => {
  const {zip, java} = load();
  const z = await zip.open("/d/a.zip");
  java.log.length = 0;
  const table = [
    [() => z.addFile(), "InternalError", "Method only accepts a number of arguments in the range [1..2]"],
    [() => z.addFile("/d/a.txt", {}, 1), "InternalError", null],
    [() => z.addFile(null), "IllegalArgumentException", "java.lang.IllegalArgumentException: Failed to coerce nullish (Null) into a string"],
    [() => z.addFile("/d/a.txt", "x"), "IllegalArgumentException", "java.lang.IllegalArgumentException: Argument \"options\" x (String) for zip.addFile must be a JavaScript Object"],
    [() => z.addFile("/d/a.txt", {aesKeyStrength: 0}), "InternalError", "Options property aesKeyStrength 0 (Number) is invalid for zip.open"],
    [() => z.addFiles("x"), "IllegalArgumentException", "java.lang.IllegalArgumentException: Argument \"filePathList\" x (String) for ZipNativeObject#addFiles must be an Iterable"],
    [() => z.addFiles([undefined]), "IllegalArgumentException", "java.lang.IllegalArgumentException: Failed to coerce nullish (Undefined) into a string"],
    [() => z.addFolder(), "InternalError", null],
    [() => z.extractAll(), "InternalError", null],
    [() => z.extractAll("/d/o", 1), "IllegalArgumentException", "java.lang.IllegalArgumentException: Argument \"options\" 1 (Number) for zip.extractAll must be a JavaScript Object"],
    [() => z.extractFile("a"), "InternalError", "Method only accepts a number of arguments in the range [2..4]"],
    [() => z.extractFile("a", null), "IllegalArgumentException", null],
    [() => z.setPassword(), "InternalError", "Arguments cannot be empty"],
    [() => z.setPassword(1, 2), "InternalError", "Method only accepts 1 argument"],
    [() => z.getFileHeader(undefined), "IllegalArgumentException", null],
    [() => z.getFileHeaders(undefined), "InternalError", "Arguments must be empty"],
    [() => z.isEncrypted(1), "InternalError", null],
    [() => z.removeFile(), "InternalError", null],
    [() => z.isValidZipFile(null), "InternalError", null],
    [() => z.getPath(1), "InternalError", null],
    [() => z.getZipFile(1), "InternalError", null],
    [() => z.toString(1), "InternalError", null],
  ];
  for (const [run, name, message] of table) {
    assert.throws(run, e => e.name === name && (message === null || e.message === message), String(run));
  }
  assert.deepEqual(java.log, []);
  assert.equal(z.getPath(), "/d/a.zip");
  assert.equal(z.toStringReadable(1, 2), String(z));
});

test("Zip members call zip4j and release their temporary handles", async () => {
  const {zip, java} = load();
  const z = await zip.open("/d/a.zip");
  java.log.length = 0;
  assert.equal(await z.addFile("/d/a.txt"), undefined);
  // Nullish options give a plain ZipParameters.
  assert.deepEqual(calls(java, "ZipParameters"), []);
  assert.deepEqual(java.log.filter(e => e.op === "release").map(e => short(e.className)).sort(), ["File", "ZipParameters"]);
  java.log.length = 0;
  await z.addFiles(["/d/a.txt", "b"], {comment: "x"});
  assert.deepEqual(calls(java, "ZipParameters"), [...DEFAULT_SETTERS.slice(0, 5), ["setFileComment", "x"], DEFAULT_SETTERS[5]]);
  assert.deepEqual(java.log.filter(e => e.op === "release").map(e => short(e.className)).sort(), ["Arrays$ArrayList", "File", "File", "ZipParameters"]);
  java.log.length = 0;
  await z.extractAll("/d/out", {password: "p"});
  assert.deepEqual(java.log.filter(e => e.op !== "release").map(e => [e.op, short(e.className), e.method || ""]),
    [["new", "UnzipParameters", ""], ["call", "ZipFile", "setPassword"], ["call", "ZipFile", "extractAll"]]);
  java.log.length = 0;
  await z.extractFile("a.txt", "/d/out");
  assert.deepEqual(calls(java, "ZipFile"), [["extractFile", "/sdcard/Scripts/a.txt", "/d/out", null, "UnzipParameters"]]);
  const headers = await z.getFileHeaders();
  assert.deepEqual([Array.isArray(headers), Object.isFrozen(headers), headers.map(h => short(h.className))], [true, false, ["FileHeader", "FileHeader"]]);
  assert.equal(short((await z.getFileHeader("a.txt")).className), "FileHeader");
  assert.deepEqual([await z.isEncrypted(), await z.isValidZipFile(), await z.removeFile("a.txt"), await z.setPassword("q")], [false, true, undefined, undefined]);
  const retained = await Promise.all([z.getZipFile(), z.zipFile(), z.zipParameters(), z.unzipParameters()]);
  assert.deepEqual(retained.map(h => short(h.className)), ["ZipFile", "ZipFile", "ZipParameters", "UnzipParameters"]);
  assert.equal(java.log.filter(e => e.op === "retain").length, 4);
  // The Zip's three handles, the headers and the retained handles remain; everything else was released.
  assert.deepEqual(java.owned(), ["FileHeader", "FileHeader", "FileHeader", "UnzipParameters", "UnzipParameters", "ZipFile", "ZipFile",
    "ZipFile", "ZipParameters", "ZipParameters"]);
  assert.ok(java.log.filter(e => ["addFiles", "extractAll", "extractFile", "getFileHeaders", "isEncrypted", "removeFile"].includes(e.method))
    .every(e => e.timeoutMs === WORK));
});

test("extractAll keeps the password set before an ignoreDateTimeAttributes error", async () => {
  const {zip, java} = load();
  const z = await zip.open("/d/a.zip");
  java.log.length = 0;
  assert.throws(() => z.extractAll("/d/o", {password: "p", ignoreDateTimeAttributes: true}), javaError("InternalError"));
  await z.extractAll("/d/o");
  assert.deepEqual(calls(java, "ZipFile"), [["setPassword", ["p"]], ["extractAll", "/d/o", "UnzipParameters"]]);
});

test("calls on one Zip run one at a time, also after a failure", async () => {
  let active = 0;
  let most = 0;
  let calls = 0;
  const {zip} = load({behavior: {"ZipFile.addFile": async () => {
    active++;
    most = Math.max(most, active);
    await sleep(5);
    active--;
    if (++calls === 1) throw facadeError("net.lingala.zip4j.exception.ZipException", "first");
    return null;
  }}});
  const z = await zip("/d/a.zip");
  const results = await Promise.allSettled([z.addFile("/d/a"), z.addFile("/d/b"), z.addFile("/d/c")]);
  assert.deepEqual(results.map(r => r.status), ["rejected", "fulfilled", "fulfilled"]);
  assert.equal(results[0].reason.name, "ZipException");
  assert.equal(most, 1);
});

test("excludeFileFilter functions become ExcludeFileFilter proxies", async () => {
  const {zip, java} = load({instanceOf: (target, type) => target.className === "x.Filter" && type === "net.lingala.zip4j.model.ExcludeFileFilter"});
  const options = {skip: "b", excludeFileFilter(file) { return Promise.resolve(file.name === this.skip ? 1 : undefined); }};
  const z = await zip.zipDir("/d/tree", "/d/tree.zip", options);
  const proxy = java.log.find(e => e.op === "proxy");
  assert.equal(proxy.interfaces, "net.lingala.zip4j.model.ExcludeFileFilter");
  assert.equal(proxy.opts.callbackTimeoutMs, 120000);
  const filter = [...java.live.values()].find(h => h.className === "$Proxy7");
  const {implementation} = java.data.get(filter.__autojs6JavaHandle);
  assert.deepEqual([await implementation.isExcluded({name: "b"}), await implementation.isExcluded({name: "c"})], [true, false]);
  assert.deepEqual(calls(java, "ZipParameters").find(c => c[0] === "setExcludeFileFilter"), ["setExcludeFileFilter", "$Proxy7"]);
  assert.match(String(z), /excludeFileFilter: .* [[] [$]Proxy7@java-[0-9]+ ],/);
  // The Zip keeps its filter; a member call releases its own.
  assert.deepEqual(java.owned(), ["$Proxy7", "UnzipParameters", "ZipFile", "ZipParameters"]);
  await z.addFolder("/d/tree", {excludeFileFilter: () => false});
  assert.deepEqual(java.owned(), ["$Proxy7", "UnzipParameters", "ZipFile", "ZipParameters"]);
  // A Java value must be an ExcludeFileFilter.
  await z.addFolder("/d/tree", {excludeFileFilter: java.handle("x.Filter")});
  await assert.rejects(z.addFolder("/d/tree", {excludeFileFilter: java.handle("x.Other")}), javaError("InternalError"));
  await assert.rejects(zip.zipDir("/d/tree", "/d/t.zip", {excludeFileFilter: java.handle("x.Other")}), javaError("InternalError"));
});

test("toString, members and attributes", async () => {
  const {zip} = load();
  const options = {compressionLevel: "fast", comment: "hi", other: 1, password: "pw", entrySize: "12", none: undefined};
  const z = await zip.open("/d/a.zip", options);
  const text = [
    "Zip {",
    "  operation: open,",
    "  path: /d/a.zip,",
    "  options: {",
    "    compressionLevel: fast [ FAST ],",
    "    comment: hi [ hi ],",
    "    other: 1,",
    "    password: pw [ pw ],",
    "    entrySize: 12 [ 12 ],",
    "    none: null,",
    "  },",
    "}",
  ].join("\n");
  assert.equal(String(z), text);
  assert.equal(z.toStringReadable(), text);
  assert.equal(String(await zip("/d/b.zip")), "Zip {\n  operation: open,\n  path: /d/b.zip,\n  options: {},\n}");
  // Rhino keeps the values of the last build of each key.
  await z.addFile("/d/a.txt", {compressionLevel: "maximum"});
  assert.ok(String(z).includes("compressionLevel: fast [ MAXIMUM ],"));
  assert.deepEqual(Object.keys(z), ["toString", "addFile", "addFiles", "addFolder", "extractAll", "extractFile", "setPassword",
    "getFileHeader", "getFileHeaders", "isEncrypted", "removeFile", "isValidZipFile", "getPath", "getZipFile", "name", "path", "zipFile",
    "options", "zipParameters", "unzipParameters"]);
  assert.equal(z.options, options);
  const descriptor = key => {
    const d = Object.getOwnPropertyDescriptor(z, key);
    return [d.writable, d.enumerable, d.configurable];
  };
  assert.deepEqual([descriptor("addFile"), descriptor("toStringReadable"), descriptor("name"), descriptor("zipParameters")],
    [[true, true, false], [false, false, false], [false, true, false], [false, true, false]]);
  assert.equal(String(await zip.open("/d/c.zip", new Date(0))).split("\n")[3], "  options: [object Date],");
});

test("a collected Zip releases its own handles", async t => {
  let gc;
  try {
    require("node:v8").setFlagsFromString("--expose-gc");
    gc = require("node:vm").runInNewContext("gc");
  } catch (_) {
    gc = null;
  }
  if (typeof gc !== "function") return t.skip("gc is not available");
  const {zip, java} = load();
  await (async () => {
    const z = await zip.open("/d/a.zip", {excludeFileFilter: () => false});
    await z.isEncrypted();
  })();
  for (let i = 0; i < 50 && java.owned().length; i++) {
    gc();
    await sleep(10);
  }
  assert.deepEqual(java.owned(), []);
});

test("every Node case runs against the mock without script errors", async () => {
  // NE of cases.js, changed to let script errors (a wrong member, a null value) escape.
  const context = {module: {exports: {}}};
  require("node:vm").runInNewContext(fs.readFileSync(path.join(__dirname, "cases-zip.js"), "utf8"), context);
  const scriptError = /is not a function|is not defined|Cannot read properties|is not iterable/;
  const strict = context.NE.replace("catch (e) { ", `catch (e) { if (${scriptError}.test(String(e && e.message))) throw e; `);
  assert.notEqual(strict, context.NE);
  const store = new Map();
  const under = p => [...store.keys()].filter(k => k.startsWith(`${p}/`));
  const files = {
    write: (p, text) => void store.set(p, String(text)),
    read: p => {
      if (!store.has(p)) throw new Error(`no file ${p}`);
      return store.get(p);
    },
    exists: p => store.has(p) || under(p).length > 0,
    isFile: p => store.has(p),
    isDir: p => p === "/t" || under(p).length > 0,
    listDir: p => [...new Set(under(p).map(k => k.slice(p.length + 1).split("/")[0]))],
  };
  // zip4j writes nothing here; extraction is simulated so that files.read finds something.
  const {zip, java} = load({behavior: {
    "ZipFile.extractAll": args => void store.set(`${args[0]}/a.txt`, "alpha"),
    "ZipFile.getFile": (args, target, info) => java.handle("java.io.File", {args: info.args}),
    "File.getName": (args, target, info) => info.args[0].slice(info.args[0].lastIndexOf("/") + 1),
    "FileHeader.getFileName": (args, target, info) => info.name,
  }});
  const fallback = java.api.call;
  java.api.call = async (target, method, args, opts) => {
    const value = await fallback(target, method, args, opts);
    if (value !== null || /^set|^add|^extract|^remove|^close|^create/.test(method)) return value;
    return method.startsWith("is") ? false : java.handle(`x.${method}`, {}, {string: "X", name: "X"});
  };
  const failures = [];
  for (const c of cases) {
    const expression = (c.length > 2 ? c[2] : c[1]).split(context.NE).join(strict);
    try {
      await new Function("zip", "files", "D", `return (${expression});`)(zip, files, "/t");
    } catch (e) {
      if (scriptError.test(String(e && e.message))) failures.push(`${c[0]}: ${e.message}`);
    }
  }
  assert.deepEqual(failures, []);
});

test("every case parses, Rhino cases use plain ES5 and arrow functions", () => {
  const ids = new Set();
  assert.ok(cases.length >= 80, `${cases.length} cases`);
  for (const c of cases) {
    assert.ok(Array.isArray(c) && (c.length === 2 || c.length === 3), JSON.stringify(c));
    assert.ok(!ids.has(c[0]), `duplicate ${c[0]}`);
    ids.add(c[0]);
    for (const expression of c.slice(1)) {
      if (expression === null) continue;
      assert.doesNotThrow(() => new Function("zip", "files", "D", `return (${expression});`), `${c[0]}: ${expression}`);
    }
    assert.ok(!/[`]|[.][.][.]|\b(async|await|const|let|class)\b/.test(c[1]), `${c[0]} Rhino expression: ${c[1]}`);
  }
});

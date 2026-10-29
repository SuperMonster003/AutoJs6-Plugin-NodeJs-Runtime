"use strict";
// Desktop tests of __autojs6_js_tools_mime_recorder (node --test test.js): the function source loads in a fresh realm
// with ES built-ins only, every Node expression of cases.js evaluates (no ReferenceError / SyntaxError), and the results
// equal the hand-derived values below (the expected Rhino results on Android 10 - 15).
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const vm = require("node:vm");
const {cases} = require("./cases-mime-recorder.js");

const SOURCE = require("./extract.js")("mime_recorder");
const FIRST_LINE = "    function __autojs6_js_tools_mime_recorder(host) {";

// A new instance from the source, as the core compiles it.
function create(host) {
  return new Function(`${SOURCE}\nreturn __autojs6_js_tools_mime_recorder;`)()(host);
}

// The same in a context of its own, without Node globals (another realm: some identity checks differ).
function createIsolated(host) {
  return vm.runInNewContext(`${SOURCE}\n__autojs6_js_tools_mime_recorder;`, {})(host);
}

// cases.js encoding of a result (jsToolsEncode without Java values) and of a thrown value.
function encode(v, depth = 0) {
  if (depth > 6) return {deep: 1};
  if (v === undefined) return {u: 1};
  if (v === null) return null;
  const t = typeof v;
  if (t === "boolean" || t === "string") return v;
  if (t === "number") {
    if (v !== v) return {n: "NaN"};
    if (v === Infinity) return {n: "Infinity"};
    if (v === -Infinity) return {n: "-Infinity"};
    if (v === 0 && 1 / v < 0) return {n: "-0"};
    return v;
  }
  if (t === "function") return {fn: 1};
  if (Array.isArray(v)) return v.map(x => encode(x, depth + 1));
  const o = {};
  for (const k of Object.keys(v).sort()) o[k] = encode(v[k], depth + 1);
  return {o};
}

function run(env) {
  const names = Object.keys(env);
  const values = names.map(name => env[name]);
  const results = new Map();
  for (const c of cases) {
    const source = c.length > 2 ? c[2] : c[1];
    if (source === null) continue;
    try {
      results.set(c[0], {ok: encode(new Function(...names, `return (${source});`)(...values))});
    } catch (e) {
      results.set(c[0], {th: e && e.name, message: e && e.message});
    }
  }
  return results;
}

const T = name => ({th: name});
const raw = text => JSON.parse(text);
const FIELDS = (rawText, type, subtype, mimeType, refined, parameters) => [rawText, type, subtype, mimeType, refined, parameters];
const JSMIME = (type, subtype, mimeType, refined, parameters, rawText) => `JsMime {\n  type: '${type}',\n  subtype: '${subtype}',\n` +
  `  mimeType: '${mimeType}',\n  mimeTypeRefined: '${refined}',\n  parameters: ${parameters},\n  raw: '${rawText}',\n}`;

// Hand-derived from MimeModule / Mime / MimeType / MediaType / Recorder and the Rhino Java interop rules; the strings of
// the host's MimeModuleDifferentialTest are taken over as they are.
const EXPECTED = {
  "mime.typeof": "function",
  "mime.length": 1,
  "mime.name": "mime",
  "mime.members": Array(7).fill("function"),
  "mime.memberLength": [0, 0, 0],
  "mime.parse.plain": FIELDS("text/plain", "text", "plain", "text/plain", "text/plain", "{}"),
  "mime.parse.charset": FIELDS("text/html; charset=UTF-8", "text", "html", "text/html; charset=UTF-8", "text/html", '{"charset":"UTF-8"}'),
  "mime.parse.compact": FIELDS("text/html;charset=UTF-8;q=0.5", "text", "html", "text/html; charset=UTF-8; q=0.5", "text/html",
    '{"charset":"UTF-8","q":"0.5"}'),
  "mime.parse.flag": FIELDS("multipart/form-data; boundary=----x; flag", "multipart", "form-data", "multipart/form-data; boundary=----x; flag",
    "multipart/form-data", '{"boundary":"----x"}'),
  "mime.parse.spaces": FIELDS("application/json ; charset = utf-8 ", "application", "json ", "application/json ; charset = utf-8 ",
    "application/json ", '{"charset":"utf-8 "}'),
  "mime.parse.upper": FIELDS("TEXT/PLAIN", "TEXT", "PLAIN", "TEXT/PLAIN", "TEXT/PLAIN", "{}"),
  "mime.parse.subtypeWildcard": FIELDS("image/*", "image", "*", "image/*", "image/*", "{}"),
  "mime.parse.wildcard": FIELDS("*/*", "*", "*", "*/*", "*/*", "{}"),
  "mime.parse.suffix": FIELDS("application/vnd.api+json", "application", "vnd.api+json", "application/vnd.api+json", "application/vnd.api+json", "{}"),
  "mime.parse.trailingSemicolon": FIELDS("text/plain;", "text", "plain", "text/plain", "text/plain", "{}"),
  "mime.parse.twoEquals": FIELDS("text/plain; a=1=2", "text", "plain", "text/plain; a=1=2", "text/plain", '{"a":"1"}'),
  "mime.parse.leadingSpace": FIELDS(" text/plain", "text", "plain", "text/plain", "text/plain", "{}"),
  "mime.parse.typeOnly": FIELDS("text", "text", "*", "text/*", "text/*", "{}"),
  "mime.parse.indexKeys": FIELDS("text/plain; 2=b; 1=a", "text", "plain", "text/plain; 2=b; 1=a", "text/plain", '{"1":"a","2":"b"}'),
  "mime.parse.slashOnly": FIELDS("text/", "text", "*", "text/*", "text/*", "{}"),
  "mime.parse.noType": FIELDS("/plain", "*", "*", "*/*", "*/*", "{}"),
  "mime.parse.wildcardType": FIELDS("*/plain", "*", "*", "*/*", "*/*", "{}"),
  "mime.parse.semicolonType": FIELDS("text;charset=utf-8", "text", "charset=utf-8", "text/charset=utf-8; charset=utf-8", "text/charset=utf-8",
    '{"charset":"utf-8"}'),
  "mime.parse.threeParts": FIELDS("a/b/c", "a", "b", "a/b", "a/b", "{}"),
  "mime.parse.doubleSlash": FIELDS("text//plain", "text", "plain", "text/plain", "text/plain", "{}"),
  "mime.parse.separatorsOnly": FIELDS(";", "*", "*", "*/*", "*/*", "{}"),
  "mime.parse.spacedSubtype": FIELDS("x/ * ", "x", " *", "x/ *", "x/ *", "{}"),
  "mime.parse.spacedType": FIELDS("text /plain", "text ", "plain", "text /plain", "text /plain", "{}"),
  "mime.parse.emptyEntries": "text/plain; a=1",
  "mime.parse.concat": "text",
  "mime.parse.nbsp": [true, "*"],
  "mime.parse.quotedValue": '"utf-8"',
  "mime.parse.keyCase": ["A", "a"],
  "mime.parse.duplicateKey": '{"a":"3","b":"2"}',
  "mime.parse.emptyKey": '{"":"x"}',
  "mime.parse.emptyValue": '{"a":""}',
  // Android's ICU regex counts U+00A0 as \s (Rhino on a device).
  "mime.parse.nbspKey": false,
  "mime.parse.tabKey": ["a"],
  "mime.parse.protoKey": '{"__proto__":"x"}',
  "mime.parse.extraUndefined": "a",

  "mime.toString.plain": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain',\n  mimeTypeRefined: 'text/plain',\n  parameters: {},\n  raw: 'text/plain',\n}"`),
  "mime.toString.charset": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'html',\n  mimeType: 'text/html; charset=UTF-8',\n  mimeTypeRefined: 'text/html',\n  parameters: { charset: 'UTF-8' },\n  raw: 'text/html; charset=UTF-8',\n}"`),
  "mime.toString.quotedKey": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain; my-key=1; q=0.5',\n  mimeTypeRefined: 'text/plain',\n  parameters: { 'my-key': '1', q: '0.5' },\n  raw: 'text/plain; my-key=1; q=0.5',\n}"`),
  "mime.toString.quotes": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain; a=it's; b=\"x\"',\n  mimeTypeRefined: 'text/plain',\n  parameters: { a: 'it\\'s', b: '\"x\"' },\n  raw: 'text/plain; a=it's; b=\"x\"',\n}"`),
  "mime.toString.escapes": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain; a=\\x\ty',\n  mimeTypeRefined: 'text/plain',\n  parameters: { a: '\\\\x\\ty' },\n  raw: 'text/plain; a=\\x\ty',\n}"`),
  "mime.toString.long": raw(String.raw`"JsMime {\n  type: 'multipart',\n  subtype: 'form-data',\n  mimeType: 'multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW; charset=utf-8',\n  mimeTypeRefined: 'multipart/form-data',\n  parameters: { boundary: '----WebKitFormBoundary7MA4YWxkTrZu0gW',\n  charset: 'utf-8' },\n  raw: 'multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW; charset=utf-8',\n}"`),
  "mime.toString.threeLong": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain; aaaaaaaaaa=bbbbbbbbbb; cccccccccc=dddddddddd; eeee=ffff',\n  mimeTypeRefined: 'text/plain',\n  parameters: { aaaaaaaaaa: 'bbbbbbbbbb',\n  cccccccccc: 'dddddddddd',\n  eeee: 'ffff' },\n  raw: 'text/plain; aaaaaaaaaa=bbbbbbbbbb; cccccccccc=dddddddddd; eeee=ffff',\n}"`),
  "mime.toString.emptyKey": JSMIME("text", "plain", "text/plain; =x", "text/plain", "{ '': 'x' }", "text/plain; =x"),
  "mime.toString.same": true,
  "mime.toString.readable": true,
  "mime.toString.distinct": false,
  "mime.toString.foreign": raw(String.raw`"JsMime {\n  type: 't',\n  subtype: 's',\n  mimeType: 'undefined',\n  mimeTypeRefined: 'undefined',\n  parameters: undefined,\n  raw: 'undefined',\n}"`),
  "mime.toString.foreignArray": JSMIME("a", "undefined", "undefined", "undefined", "{ '0': 1, '1': 'x' }", "undefined"),
  "mime.toString.foreignNull": JSMIME("undefined", "undefined", "undefined", "undefined", "undefined", "undefined"),
  "mime.toString.mutated": raw(String.raw`"JsMime {\n  type: 'text',\n  subtype: 'plain',\n  mimeType: 'text/plain',\n  mimeTypeRefined: 'text/plain',\n  parameters: { extra: 'x' },\n  raw: 'text/plain',\n}"`),

  "mime.result.json": '{"raw":"text/plain; a=1","type":"text","subtype":"plain","mimeType":"text/plain; a=1","mimeTypeRefined":"text/plain","parameters":{"a":"1"}}',
  "mime.result.keys": ["raw", "type", "subtype", "mimeType", "mimeTypeRefined", "parameters"],
  "mime.result.parameterKeys": ["b", "a"],
  "mime.result.forIn": ["raw", "type", "subtype", "mimeType", "mimeTypeRefined", "parameters", "constructor", "toString"],
  "mime.result.descriptor": [false, true, true],
  "mime.result.assign": "a",
  "mime.result.extensible": true,
  "mime.result.protoDescriptors": [true, true, false],
  "mime.result.protoOfProto": true,
  "mime.constructor.same": true,
  "mime.constructor.instanceof": true,
  "mime.constructor.name": ["JsMime", 1],
  "mime.constructor.prototype": true,
  "mime.constructor.new": "html",
  "mime.constructor.call": "x",
  "mime.constructor.none": T("TypeError"),

  "mime.arg.none": T("TypeError"),
  "mime.arg.undefined": T("TypeError"),
  "mime.arg.null": T("TypeError"),
  "mime.arg.number": T("TypeError"),
  "mime.arg.stringObject": T("TypeError"),
  "mime.arg.array": T("TypeError"),
  "mime.arg.surplus": T("TypeError"),
  "mime.arg.empty": T("MimeException"),
  "mime.arg.blank": T("MimeException"),
  "mime.arg.tab": T("MimeException"),

  "mime.getType.plain": "text",
  "mime.getSubtype.plain": "html",
  "mime.getSubtype.none": "*",
  "mime.getSubtype.wildcardType": "*",
  "mime.getType.spaced": "a ",
  "mime.getType.undefined": "undefined",
  "mime.getType.number": "1",
  "mime.getType.empty": T("MimeException"),
  "mime.getType.null": T("MimeException"),
  "mime.getType.noArgs": T("InternalError"),
  "mime.getSubtype.twoArgs": T("InternalError"),

  "mime.fromFile.png": "image/png",
  "mime.fromFile.upper": "image/jpeg",
  "mime.fromFile.common": ["text/plain", "application/json", "application/pdf", "text/html", "audio/mpeg", "video/mp4", "application/zip",
    "application/vnd.android.package-archive", "image/webp", "image/svg+xml"],
  "mime.fromFile.android": ["image/x-ms-bmp", "audio/mpeg", "text/comma-separated-values", "text/xml", "video/3gpp", "image/heic"],
  "mime.fromFile.office": ["application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/epub+zip"],
  "mime.fromFile.lastDot": "application/gzip",
  "mime.fromFile.symbol": "application/x-trash",
  "mime.fromFile.unknown": null,
  "mime.fromFile.kotlin": null,
  "mime.fromFile.noExtension": "*/*",
  "mime.fromFile.empty": "*/*",
  "mime.fromFile.hidden": "*/*",
  "mime.fromFile.hiddenInDir": null,
  "mime.fromFile.trailingDot": "*/*",
  "mime.fromFile.dotInDir": null,
  "mime.fromFile.path": "application/pdf",
  "mime.fromFile.dir": "image/png",
  "mime.fromFile.undefined": "*/*",
  "mime.fromFile.number": ["*/*", null],
  "mime.fromFile.null": T("NullPointerException"),
  "mime.fromFile.noArgs": T("InternalError"),
  "mime.fromFile.twoArgs": T("InternalError"),

  "mime.fromFileOr.default": "x/y",
  "mime.fromFileOr.known": "image/png",
  "mime.fromFileOr.nullDefault": "*/*",
  "mime.fromFileOr.noExtension": "*/*",
  "mime.fromFileOr.undefinedDefault": "undefined",
  "mime.fromFileOr.numberDefault": "1",
  "mime.fromFileOr.oneArg": T("InternalError"),
  "mime.fromFileOr.threeArgs": T("InternalError"),
  "mime.fromFileOr.nullPath": T("NullPointerException"),
  "mime.fromFileOrWildcard.unknown": "*/*",
  "mime.fromFileOrWildcard.known": "application/json",
  "mime.fromFileOrWildcard.noArgs": T("InternalError"),
  "mime.fromFileOrWildcard.null": T("NullPointerException"),

  "mime.getMediaType.string": "text/plain",
  "mime.getMediaType.typeof": ["object", "function"],
  "mime.getMediaType.parts": ["text", "plain", "TEXT/Plain; Charset=UTF-8", "TEXT/Plain; Charset=UTF-8"],
  "mime.getMediaType.parameter": ["UTF-8", "UTF-8", null],
  "mime.getMediaType.quoted": ["x y", "1"],
  "mime.getMediaType.singleQuoted": ["utf-8", "''"],
  "mime.getMediaType.tab": "1",
  "mime.getMediaType.trailing": ["text/plain;", "text/plain;  ", "d"],
  "mime.getMediaType.tokenChars": "a!#$%&*+.^_`|~-",
  "mime.getMediaType.charset": ["UTF-8", "ISO-8859-1", null],
  "mime.getMediaType.equals": [true, false, false, false],
  "mime.getMediaType.hashCode": [94772, 817335912],
  "mime.getMediaType.noSubtype": T("IllegalArgumentException"),
  "mime.getMediaType.leadingSpace": T("IllegalArgumentException"),
  "mime.getMediaType.trailingSpace": T("IllegalArgumentException"),
  "mime.getMediaType.bareParameter": T("IllegalArgumentException"),
  "mime.getMediaType.unterminated": T("IllegalArgumentException"),
  "mime.getMediaType.number": T("IllegalArgumentException"),
  "mime.getMediaType.null": T("NullPointerException"),
  "mime.getMediaType.noArgs": T("InternalError"),
  "mime.getMediaType.parameterNull": T("NullPointerException"),
  "mime.getMediaType.typeArgs": T("InternalError"),
  "mime.parseMediaType.valid": "text/plain; charset=utf-8",
  "mime.parseMediaType.invalid": [null, null, null],
  "mime.parseMediaType.null": T("NullPointerException"),
  "mime.parseMediaType.noArgs": T("InternalError"),

  "mime.constants.documented": ["*/*", "*", "application/json", "application/octet-stream", "multipart/form-data", "text/plain", "text/html",
    "image/png", "image/jpeg", "audio/mpeg", "video/mp4"],
  "mime.constants.aliases": ["application/x-www-form-urlencoded", "text/event-stream", "text/event-stream", "application/msword",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"],
  "mime.constants.irregular": ["audio/amr-wb+", "image/emf", "text/x-c++hdr", "application/vnd.wap.multipart.*", "text/*"],
  "mime.constants.derived": ["text/directory;profile=vCard", "application/3gppHalForms+json", "application/A2L", "x-world/x-vrml", "inode/directory",
    "vnd.android.document/directory", "font/woff2", "chemical/x-pdb", "message/rfc822"],
  "mime.constants.absent": ["undefined", "undefined"],

  "recorder.typeof": "function",
  "recorder.length": 0,
  "recorder.name": "recorder",
  "recorder.members": Array(7).fill("function"),
  "recorder.memberLength": [0, 0, 0],
  "recorder.save.timestamp": 1000,
  "recorder.save.load": 500,
  "recorder.save.now": ["number", true, true],
  "recorder.save.nullTimestamp": true,
  "recorder.save.truncated": [1000, -1000, 0],
  "recorder.save.coerced": [1500, 15, 16, 0, 1, 7],
  "recorder.save.longMin": true,
  "recorder.save.string": T("IllegalArgumentException"),
  "recorder.save.undefinedTimestamp": T("IllegalArgumentException"),
  "recorder.save.object": T("IllegalArgumentException"),
  "recorder.save.nan": T("IllegalArgumentException"),
  "recorder.save.infinity": T("IllegalArgumentException"),
  "recorder.save.overflow": T("IllegalArgumentException"),
  "recorder.save.threeArgs": T("InternalError"),
  "recorder.save.keyTypes": [true, true, true, true],
  "recorder.save.undefinedKey": [true, 50, NaN],
  "recorder.save.nullKey": 60,
  "recorder.load.anonymousStack": [800, 900, NaN],
  "recorder.load.emptyAnonymous": [NaN, NaN],
  "recorder.load.named": [500, 1500, true],
  "recorder.load.now": true,
  "recorder.load.recent": true,
  "recorder.load.missing": T("NoSuchElementException"),
  "recorder.load.missingBadTimestamp": T("IllegalArgumentException"),
  "recorder.load.undefinedTimestamp": T("IllegalArgumentException"),
  "recorder.load.keepsAnonymousOnError": 50,
  "recorder.load.threeArgs": T("InternalError"),
  "recorder.compare.basic": [true, true, false],
  "recorder.compare.future": [true, true, true],
  "recorder.compare.anonymousEmpty": [false, false],
  "recorder.compare.takesAnonymous": [true, NaN],
  "recorder.compare.takesBeforeError": NaN,
  "recorder.compare.oneArg": T("InternalError"),
  "recorder.compare.missing": T("NoSuchElementException"),
  "recorder.compare.string": T("IllegalArgumentException"),
  "recorder.compare.null": T("IllegalArgumentException"),
  "recorder.compare.undefined": T("IllegalArgumentException"),
  "recorder.compare.overflow": T("IllegalArgumentException"),
  "recorder.has.basic": [true, false, false],
  "recorder.has.anonymous": false,
  "recorder.has.noArgs": T("InternalError"),
  "recorder.has.twoArgs": T("InternalError"),
  "recorder.remove.basic": [true, false, false],
  "recorder.remove.absent": false,
  "recorder.remove.null": [false, 1],
  "recorder.remove.undefined": [true, false],
  "recorder.remove.noArgs": T("InternalError"),
  "recorder.clear.result": undefined,
  "recorder.clear.all": [false, NaN],
  "recorder.clear.args": T("InternalError"),
  "recorder.call.named": ["number", true],
  "recorder.call.namedFirst": true,
  "recorder.call.namedSecond": true,
  "recorder.call.namedSaved": true,
  "recorder.call.timestampIgnored": [true, true],
  "recorder.call.anonymous": [true, true, true],
  "recorder.call.anonymousNull": [true, true],
  "recorder.call.anonymousTakesSaved": [true, NaN],
  "recorder.call.keyTypes": [true, true, true],
  "recorder.call.trailingUndefined": "number",
  "recorder.call.surplus": T("TypeError"),
  "recorder.call.function": [1, true, true],
  "recorder.call.functionThis": 7,
  "recorder.call.functionThisArray": 2,
  "recorder.call.functionThisGlobal": [true, true],
  "recorder.call.functionNoRecord": [false, NaN],
  "recorder.call.functionThrows": "threw",
  "recorder.call.functionClears": T("NoSuchElementException"),
};

function withDir(f) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mime-recorder-"));
  try {
    return f(dir.split(path.sep).join("/"));
  } finally {
    fs.rmSync(dir, {recursive: true, force: true});
  }
}

test("the function source keeps the shape of the core section", () => {
  assert.equal(SOURCE.split("\n")[0], FIRST_LINE);
  assert.ok(SOURCE.endsWith("\n    }\n"));
  assert.ok(!SOURCE.includes(')JS"'));
  assert.ok(!/[^\x00-\x7f]/.test(SOURCE), "ASCII only");
  assert.ok(!/\brequire\s*\(|\bprocess\.|\bBuffer\b|\bTextDecoder\b/.test(SOURCE), "no Node globals");
});

test("every case has a unique id and an expected value", () => {
  const ids = cases.map(c => c[0]);
  assert.equal(new Set(ids).size, ids.length);
  assert.ok(ids.length >= 120, `${ids.length} cases`);
  assert.deepEqual(ids.filter(id => !(id in EXPECTED)), []);
  assert.deepEqual(Object.keys(EXPECTED).filter(id => !ids.includes(id)), []);
});

const unresolved = results => [...results].filter(([, r]) => r.th === "ReferenceError" || r.th === "SyntaxError")
  .map(([id, r]) => `${id}: ${r.th} ${r.message}`);

test("all Node expressions evaluate with ES built-ins only", () => {
  const results = withDir(D => run({...createIsolated({device: {sdkInt: 33}}), D}));
  assert.equal(results.size, cases.length);
  assert.deepEqual(unresolved(results), []);
});

for (const sdkInt of [33, 35]) {
  test(`all Node expressions give the expected results (sdkInt ${sdkInt})`, () => {
    const results = withDir(D => run({...create({device: {sdkInt}}), D}));
    assert.deepEqual(unresolved(results), []);
    const failures = [];
    for (const [id, result] of results) {
      const expected = EXPECTED[id];
      if (expected && typeof expected === "object" && "th" in expected) {
        if (result.th !== expected.th) failures.push(`${id}: expected ${expected.th}, got ${JSON.stringify(result)}`);
      } else {
        try {
          assert.deepEqual(result, {ok: encode(expected)});
        } catch {
          failures.push(`${id}: expected ${JSON.stringify(encode(expected))}, got ${JSON.stringify(result)}`);
        }
      }
    }
    assert.deepEqual(failures, []);
  });
}

test("error messages follow the host", () => {
  const {mime, recorder} = create({device: {sdkInt: 33}});
  const message = f => {
    try {
      f();
    } catch (e) {
      return `${e.name}: ${e.message}`;
    }
    return "no error";
  };
  assert.equal(message(() => mime()), 'TypeError: Failed to call method "mime": [ Method only accepts 1 argument ]');
  assert.equal(message(() => mime(1)), 'TypeError: Failed to call method "mime": [ Argument "mimeString" must be of type string, got number ]');
  assert.equal(message(() => mime(new String("a"))), 'TypeError: Failed to call method "mime": [ Argument "mimeString" must be of type string, got String ]');
  assert.equal(message(() => mime(" ")), "MimeException: Invalid MimeType [ ]");
  assert.equal(message(() => recorder(1, 2, 3)),
    'TypeError: Failed to call method "recorder": [ Method only accepts a number of arguments in the range [0..2] ]');
  assert.equal(message(() => recorder.has()), "InternalError: Can't find method org.autojs.autojs.runtime.api.Recorder.has().");
  assert.equal(message(() => recorder.save("a", 1, undefined)),
    "InternalError: Can't find method org.autojs.autojs.runtime.api.Recorder.save(string,number,org.mozilla.javascript.Undefined).");
  assert.equal(message(() => recorder.load("missing")), 'NoSuchElementException: Recorder key "missing" does not exist');
  assert.equal(message(() => recorder.save("a", "x")), "IllegalArgumentException: Failed to coerce x (String) into a valid number");
  assert.equal(message(() => recorder.isLessThan("missing", null)), 'NoSuchElementException: Recorder key "missing" does not exist');
  recorder.save("a", 1);
  assert.equal(message(() => recorder.isLessThan("a", null)), "IllegalArgumentException: Failed to coerce nullish (Null) into a valid number");
  assert.equal(message(() => mime.fromFile(null)),
    "NullPointerException: Parameter specified as non-null is null: method org.autojs.autojs.runtime.api.Mime.fromFile, parameter path");
  assert.equal(message(() => mime.getType()), "InternalError: Can't find method eu.medsea.mimeutil.MimeUtil2.getType().");
  assert.equal(message(() => mime.getMediaType("bad")), 'IllegalArgumentException: No subtype found for: "bad"');
  assert.equal(message(() => mime.getMediaType("text/plain; charset")),
    'IllegalArgumentException: Parameter is not formatted correctly: "charset" for: "text/plain; charset"');
  assert.equal(message(() => mime.getMediaType("text/plain ")),
    'IllegalArgumentException: Parameter is not formatted correctly: " " for: "text/plain "');
});

test("mime surface: frozen function, enumerable methods, every constant of Mime.kt (not enumerable), parse kept", () => {
  const {mime} = create({device: {sdkInt: 33}});
  assert.ok(Object.isFrozen(mime));
  assert.deepEqual(Object.keys(mime), ["getMediaType", "parseMediaType", "fromFile", "fromFileOr", "fromFileOrWildcard", "getType", "getSubtype"]);
  const constants = Object.getOwnPropertyNames(mime).filter(k => /^[A-Z0-9_]+$/.test(k));
  assert.equal(constants.length, 2540);
  assert.ok(constants.every(k => typeof mime[k] === "string" && !Object.getOwnPropertyDescriptor(mime, k).enumerable));
  assert.equal(mime.APPLICATION_ATOM_XML, "application/atom+xml");
  assert.equal(mime.APPLICATION_OASIS_TEXT, "application/vnd.oasis.opendocument.text");
  assert.equal(mime.VIDEO_X_MSVIDEO, "video/x-msvideo");
  assert.equal(mime.X_EPOC_X_SISX_APP, "x-epoc/x-sisx-app");
  assert.equal(mime.TEXT_X_C_SRC, "text/x-c++src");
  assert.ok(!Object.getOwnPropertyDescriptor(mime, "parse").enumerable);
  assert.equal(mime.parse("a/b; c=d").parameters.c, "d");
  assert.throws(() => mime.parse(), {name: "TypeError"});
});

test("recorder surface: enumerable members, not frozen so the audio members can be added", () => {
  const {recorder} = create({});
  assert.deepEqual(Object.keys(recorder), ["save", "load", "isLessThan", "isGreaterThan", "has", "remove", "clear"]);
  assert.ok(!Object.isFrozen(recorder));
  Object.defineProperty(recorder, "getStatus", {value: () => null, enumerable: true});
  assert.equal(typeof recorder.getStatus, "function");
});

test("MimeTypeMap table by Android version", () => {
  const q = create({device: {sdkInt: 33}}).mime;
  const v = create({device: {sdkInt: 35}}).mime;
  const unknown = create(undefined).mime;
  assert.deepEqual(["a.js", "a.otf", "a.rar", "a.mid", "a.midi", "a.yaml"].map(p => q.fromFile(p)),
    ["application/javascript", "font/ttf", "application/rar", "audio/midi", "audio/midi", null]);
  assert.deepEqual(["a.js", "a.otf", "a.rar", "a.mid", "a.midi", "a.yaml"].map(p => v.fromFile(p)),
    ["text/javascript", "font/otf", "application/vnd.rar", "audio/sp-midi", null, "application/yaml"]);
  assert.equal(unknown.fromFile("a.yaml"), "application/yaml");
});

test("recorder state is per instance and timing uses Date.now", () => {
  const a = create({}).recorder;
  const b = create({}).recorder;
  a.save("k", 1);
  assert.equal(a.has("k"), true);
  assert.equal(b.has("k"), false);
  const realNow = Date.now;
  const before = realNow();
  const saved = a.save("t");
  assert.ok(saved >= before && saved <= realNow());
});

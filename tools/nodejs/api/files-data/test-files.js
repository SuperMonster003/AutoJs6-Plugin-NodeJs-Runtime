"use strict";
// Desktop test of the files module port (node --test test.js). files.js runs against a host whose fs maps the absolute
// POSIX paths of the module ("/work/cwd", "/storage/emulated/0/...") into a temporary directory, so it also runs on
// Windows, and a stub Java facade standing in for PFiles.open, java.io.File and the APK assets. The assertions come
// from the host's Kotlin code (Files.kt, PFiles.kt, FilesModule.kt, Bytes.kt); the cases of cases.js are evaluated
// once to check that every Node expression runs (no ReferenceError / SyntaxError). FILES_CASES_DUMP=<file> writes
// the encoded case results there.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { cases } = require("./cases-files.js");

const SOURCE = require("./extract.js")("files");
const createFiles = new Function(`${SOURCE}\nreturn __autojs6_js_tools_files;`)();

const CWD = "/work/cwd";
const SDCARD = "/storage/emulated/0";
const APP_DATA = "/data/user/0/org.autojs.autojs6";
const ASSETS = { "init.js": "// init" + String.fromCharCode(10), "apk_builder_default_signer_certificate_sha256.txt": "c4".repeat(32) + String.fromCharCode(13, 10) };
const ASSET_DIRECTORIES = new Set(["modules"]);

// node:fs with the absolute POSIX paths of the module below root.
function mappedFs(root) {
  const real = p => typeof p === "string" && p.startsWith("/") ? path.join(root, ...p.split("/").filter(Boolean)) : p;
  const mapped = {};
  for (const name of ["accessSync", "existsSync", "lstatSync", "mkdirSync", "openSync", "readdirSync", "readFileSync", "rmdirSync", "statSync", "unlinkSync"]) {
    mapped[name] = (p, ...rest) => fs[name](real(p), ...rest);
  }
  mapped.renameSync = (from, to) => fs.renameSync(real(from), real(to));
  for (const name of ["closeSync", "fstatSync", "readSync", "writeSync"]) mapped[name] = fs[name].bind(fs);
  mapped.real = real;
  return mapped;
}

// A facade error as the host reports a Java exception (RuntimeJavaInterop.reflectCall).
function javaCallFailed(label, className, message) {
  return Object.assign(new Error(`${label}: ${className}: ${message}`), { code: "ERR_AUTOJS6_JAVA_CALL_FAILED" });
}

// The Java facade: java.io.File, PFiles.open (PReadableTextFile / PWritableTextFile) and the assets, enough for the cases.
function javaStub(hostFs, calls) {
  let id = 0;
  const released = [];
  const handle = (className, methods) => {
    const target = { __autojs6JavaHandle: `h${++id}`, className };
    Object.defineProperty(target, "call", {
      value: async (method, args = [], options) => {
        calls.push({ target: className, method, args, options });
        if (!methods[method]) throw javaCallFailed("call", "java.lang.NoSuchMethodException", method);
        return methods[method](...args);
      },
    });
    return Object.freeze(target);
  };
  const normalize = p => {
    const collapsed = p.replace(/\/{2,}/g, "/");
    return p.length > 1 && collapsed.endsWith("/") ? collapsed.slice(0, -1) : collapsed;
  };
  const file = p => {
    const n = normalize(p);
    const stat = () => { try { return hostFs.statSync(n); } catch { return null; } };
    return handle("java.io.File", {
      getName: () => n.slice(n.lastIndexOf("/") + 1),
      getPath: () => n,
      exists: () => stat() !== null,
      isFile: () => stat()?.isFile() === true,
    });
  };
  function open(p, mode, encoding, bufferSize) {
    if (p === null) throw javaCallFailed("callStatic", "java.lang.IllegalArgumentException", "Illegal argument path: null");
    const m = mode === null ? "r" : mode;
    if (m === "") throw javaCallFailed("callStatic", "java.lang.StringIndexOutOfBoundsException", "begin 0, end 1, length 0");
    const kind = m.toLowerCase()[0];
    const n = normalize(p);
    const notFound = code => javaCallFailed("callStatic", "org.autojs.autojs.pio.UncheckedIOException", `java.io.FileNotFoundException: ${n}: open failed: ${code}`);
    if (kind === "r") {
      let text;
      try {
        if (hostFs.statSync(n).isDirectory()) throw notFound("EISDIR");
        text = hostFs.readFileSync(n, "utf8");
      } catch (e) {
        throw e.code === "ERR_AUTOJS6_JAVA_CALL_FAILED" ? e : notFound("ENOENT");
      }
      const lines = text.split(String.fromCharCode(10));
      let line = 0;
      return handle("org.autojs.autojs.pio.PReadableTextFile", {
        read: () => text,
        readline: () => line < lines.length ? lines[line++] : null,
        close: () => undefined,
        getPath: () => p,
      });
    }
    if (kind !== "w" && kind !== "a") throw javaCallFailed("callStatic", "java.lang.IllegalArgumentException", `Illegal argument mode: ${mode}`);
    if (encoding !== null && !/^utf-?8$/i.test(encoding)) {
      throw javaCallFailed("callStatic", "org.autojs.autojs.pio.UncheckedIOException", `java.io.UnsupportedEncodingException: ${encoding}`);
    }
    let fd;
    try {
      fd = hostFs.openSync(n, kind === "a" ? "a" : "w");
    } catch {
      throw notFound("ENOENT");
    }
    return handle("org.autojs.autojs.pio.PWritableTextFile", {
      write: s => void hostFs.writeSync(fd, String(s)),
      writeline: s => void hostFs.writeSync(fd, `${s}${String.fromCharCode(10)}`),
      close: () => void hostFs.closeSync(fd),
      getPath: () => p,
    });
  }
  const api = {
    released,
    async create(className, args, options) {
      calls.push({ create: className, args, options });
      if (className !== "java.io.File") throw new Error(`unexpected class ${className}`);
      return file(args[0]);
    },
    async callStatic(className, method, args, options) {
      calls.push({ callStatic: className, method, args, options });
      if (className === "org.autojs.autojs.pio.PFiles" && method === "open") return open(...args);
      if (className === "org.autojs.autojs.pio.PFiles" && method === "read") {
        const [stream, encoding] = args;
        if (args.length === 2 && encoding === null) throw javaCallFailed("callStatic", "java.lang.IllegalArgumentException", "Null charset name");
        if (args.length === 2 && !/^(utf-?8|iso-8859-1)$/i.test(encoding)) throw javaCallFailed("callStatic", "java.nio.charset.UnsupportedCharsetException", encoding);
        return stream.text;
      }
      throw new Error(`unexpected static call ${className}.${method}`);
    },
    async getContext() {
      return handle("android.app.Application", {
        getAssets: () => handle("android.content.res.AssetManager", {
          open: name => {
            if (!(name in ASSETS) || ASSET_DIRECTORIES.has(name)) throw javaCallFailed("call", "java.io.FileNotFoundException", name);
            return Object.freeze(Object.assign(handle("android.content.res.AssetManager$AssetInputStream", {}), {}));
          },
        }),
      });
    },
    async release(target) {
      released.push(target.className);
      return true;
    },
  };
  // The stream handle carries its text for the PFiles.read stub.
  const getAssetsOpen = api.getContext;
  api.getContext = async () => {
    const context = await getAssetsOpen();
    return handle(context.className, {
      getAssets: async () => {
        const assets = await context.call("getAssets");
        return handle(assets.className, {
          open: async name => {
            await assets.call("open", [name]);
            return Object.freeze({ __autojs6JavaHandle: `h${++id}`, className: "android.content.res.AssetManager$AssetInputStream", text: ASSETS[name] });
          },
        });
      },
    });
  };
  return api;
}

function setup(t, extra = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "files-port-"));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const hostFs = mappedFs(root);
  for (const dir of [CWD, `${SDCARD}/Download`]) hostFs.mkdirSync(dir, { recursive: true });
  const calls = [];
  const java = javaStub(hostFs, calls);
  let javaAsked = 0;
  const host = {
    fs: hostFs, cwd: () => CWD, sdcardPath: SDCARD, TextDecoder,
    filesDir: `${APP_DATA}/files`, cacheDir: `${APP_DATA}/cache`, dataDir: APP_DATA,
    java: () => (javaAsked++, java),
    ...extra,
  };
  return { files: createFiles(host), hostFs, root, calls, java, host, javaAsked: () => javaAsked };
}

// The thrown value as the comparison harness records it.
function thrown(f) {
  try {
    f();
  } catch (e) {
    return e.name;
  }
  return null;
}
async function rejected(promise) {
  try {
    await promise;
  } catch (e) {
    return e.name;
  }
  return null;
}
const bytes = b => Array.from(b);
const label = v => Array.isArray(v) ? `[${v.map(label)}]` : typeof v === "symbol" ? v.toString() : String(v);

test("source", () => {
  assert.equal(SOURCE.split("\n")[0], "    function __autojs6_js_tools_files(host) {");
  assert.ok(!SOURCE.includes(')JS"'));
  assert.ok(!/[^\x00-\x7f]/.test(SOURCE), "ASCII only");
  // Only the parameter and ES built-ins: no reference to the globals of Node outside the comments.
  const code = SOURCE.split("\n").filter(line => !line.trim().startsWith("//")).join("\n");
  for (const name of ["require", "process", "Buffer", "TextDecoder", "TextEncoder", "globalThis", "console", "module", "setTimeout"]) {
    assert.ok(!new RegExp(`(^|[^.\\w$])${name}\\b`).test(code), `uses ${name}`);
  }
  assert.equal(SOURCE.trimEnd().split("\n").at(-1), "    }");
});

test("members", t => {
  const { files } = setup(t);
  const members = ["open", "path", "join", "toFile", "sdcardPath", "getSdcardPath", "nonNullPath", "cwd", "create", "createIfNotExists",
    "createWithDirs", "exists", "ensureDir", "read", "readAssets", "readBytes", "write", "append", "appendBytes", "writeBytes", "copy",
    "renameWithoutExtension", "rename", "move", "getExtension", "getName", "getNameWithoutExtension", "remove", "removeDir", "listDir",
    "isFile", "isDir", "isEmptyDir", "getHumanReadableSize", "formatSizeWithUnit", "getSimplifiedPath"];
  assert.deepEqual(Object.keys(files).sort(), [...members].sort());
  // Members of the old Node module are all kept.
  for (const old of ["path", "join", "read", "write", "append", "exists", "listDir", "remove", "copy", "move", "ensureDir", "isFile", "isDir", "getName", "getExtension", "open", "toFile"]) {
    assert.ok(old in files, old);
  }
  assert.equal(files.path.length, 0);
  assert.equal(files.join.length, 1);
  assert.equal(files.open.length, 1);
  assert.equal(files.toFile.length, 1);
  assert.equal(files.read.name, "read");
  assert.equal(files.sdcardPath, SDCARD);
  assert.equal(files.getSdcardPath(), SDCARD);
  assert.equal(files.cwd(), CWD);
});

test("path: Files.path against the working directory", t => {
  const { files } = setup(t);
  assert.equal(files.path(), null);
  assert.equal(files.path(undefined), null);
  assert.equal(files.path(null), null);
  assert.equal(files.path("a"), "/work/cwd/a");
  assert.equal(files.path("a/"), "/work/cwd/a/");
  assert.equal(files.path(""), "/work/cwd");
  assert.equal(files.path("./a/./b"), "/work/cwd/a/b");
  assert.equal(files.path("a/ /" + String.fromCharCode(0x3000) + "/b//c"), "/work/cwd/a/b/c");
  assert.equal(files.path("../x"), "/work/x");
  assert.equal(files.path("../.."), "/");
  assert.equal(files.path("../../"), "//");
  assert.equal(files.path("../../.."), null);
  assert.equal(files.path("/abs//x/../"), "/abs//x/../");
  assert.equal(files.path(12), "/work/cwd/12");
  assert.equal(files.path("a", undefined), "/work/cwd/a");
  assert.equal(thrown(() => files.path("a", "b")), "TypeError");
  assert.equal(thrown(() => files.path(Symbol("s"))), "TypeError");
  assert.equal(files.nonNullPath("a"), "/work/cwd/a");
  assert.equal(thrown(() => files.nonNullPath("../../..")), "IllegalArgumentException");
  assert.equal(thrown(() => files.nonNullPath(null)), "NullPointerException");
  const relative = setup(t, { cwd: () => "/" }).files;
  assert.equal(relative.path("a"), "/a");
  assert.equal(relative.path(".."), null);
});

test("join: java.io.File composition", t => {
  const { files } = setup(t);
  assert.equal(files.join("a"), "a");
  assert.equal(files.join("a", "b", "c"), "a/b/c");
  assert.equal(files.join("/a/", "b/"), "/a/b");
  assert.equal(files.join("a", "/b"), "a/b");
  assert.equal(files.join("/", "b"), "/b");
  assert.equal(files.join("a", ""), "a");
  assert.equal(files.join("", "a"), "/a");
  assert.equal(files.join("a", "/"), "a");
  assert.equal(files.join("//"), "");
  assert.equal(files.join("a//b/", "//c"), "a/b/c");
  assert.equal(files.join(1, 2), "1/2");
  assert.equal(thrown(() => files.join()), "TypeError");
  assert.equal(thrown(() => files.join(null)), "TypeError");
  assert.equal(thrown(() => files.join("a", undefined)), "TypeError");
  assert.match(String(thrown(() => files.join("a", null))), /TypeError/);
  try {
    files.join();
  } catch (e) {
    assert.equal(e.message, 'Failed to call method "files.join": [ Method only accepts no less than 1 argument ]');
  }
  try {
    files.join("a", null);
  } catch (e) {
    assert.equal(e.message, 'Failed to call method "files.join": [ Argument "paths" must be of type string, got null ]');
  }
});

test("names: getExtension, getName, getNameWithoutExtension, getSimplifiedPath", t => {
  const { files } = setup(t);
  const cases = [["a.txt", "txt", "a.txt", "a"], ["a.tar.gz", "gz", "a.tar.gz", "a.tar"], [".nomedia", "", ".nomedia", ""], ["a.", "", "a.", "a"],
    ["", "", "", ""], ["/a/b/", "", "b", "b"], ["/", "", "", ""], ["types.D.Ts", "Ts", "types.D.Ts", "types"], ["x/y.d.mts", "mts", "y.d.mts", "y"],
    ["dir.d/file", "d/file", "file", "file"], ["..", "", "..", "."]];
  for (const [input, extension, name, base] of cases) {
    assert.equal(files.getExtension(input), extension, `getExtension ${input}`);
    assert.equal(files.getName(input), name, `getName ${input}`);
    assert.equal(files.getNameWithoutExtension(input), base, `getNameWithoutExtension ${input}`);
  }
  assert.equal(files.getName("C:" + String.fromCharCode(92) + "x" + String.fromCharCode(92) + "y.txt"), "y.txt");
  assert.equal(files.getExtension(undefined), "");
  assert.equal(files.getName(undefined), "undefined");
  assert.equal(thrown(() => files.getExtension(null)), "InternalError");
  assert.equal(thrown(() => files.getName(null)), "InternalError");
  assert.equal(thrown(() => files.getNameWithoutExtension()), "InternalError");
  assert.equal(thrown(() => files.getName("a", "b")), "InternalError");
  assert.equal(files.getSimplifiedPath(`${SDCARD}/a`), "/a");
  assert.equal(files.getSimplifiedPath(SDCARD), "");
  assert.equal(files.getSimplifiedPath(`${SDCARD}x`), `${SDCARD}x`);
  assert.equal(files.getSimplifiedPath(`${APP_DATA}/files/sample/s.js`), "/s.js");
  assert.equal(files.getSimplifiedPath(`${APP_DATA}/files/f.js`), "/f.js");
  assert.equal(files.getSimplifiedPath(`${APP_DATA}/cache/c`), "/c");
  assert.equal(files.getSimplifiedPath(`${APP_DATA}/shared_prefs`), "/shared_prefs");
  assert.equal(files.getSimplifiedPath("/x"), "/x");
  assert.equal(thrown(() => files.getSimplifiedPath(null)), "InternalError");
  const bare = setup(t, { filesDir: undefined, cacheDir: undefined, dataDir: undefined }).files;
  assert.equal(bare.getSimplifiedPath(`${APP_DATA}/files/f.js`), `${APP_DATA}/files/f.js`);
});

test("sizes: getHumanReadableSize (Bytes.string) and formatSizeWithUnit", t => {
  const { files } = setup(t);
  const human = [[0, "0.0 B"], [1, "1.0 B"], [1023, "1023.0 B"], [1024, "1.0 KB"], [1280, "1.3 KB"], [1536, "1.5 KB"], [1048524, "1023.9 KB"],
    [1048575, "1024.0 KB"], [1048576, "1.0 MB"], [1572864, "1.5 MB"], [1073741824, "1.0 GB"], [1099511627775, "1.0 TB"], [1099511627776, "1.0 TB"],
    [2 ** 50, "1.0 PB"], [9007199254740991, "8.0 PB"], [2 ** 60, "1.0 EB"], [1.5 * 2 ** 62, "6.0 EB"], [1.9, "1.0 B"], [-0.5, "0.0 B"], ["2048", "2.0 KB"],
    [[3072], "3.0 KB"], [4096n, "4.0 KB"]];
  for (const [input, expected] of human) assert.equal(files.getHumanReadableSize(input), expected, `getHumanReadableSize ${input}`);
  assert.equal(files.getHumanReadableSize(1536, true), "1.5 KiB");
  assert.equal(files.getHumanReadableSize(1099511627775, true), "1.0 TiB");
  // 1024^5 - 600: the 6-digit quotient reaches 1024 and carries; 1024^5 - 600000 does not.
  assert.equal(files.getHumanReadableSize(2 ** 50 - 600), "1.0 PB");
  assert.equal(files.getHumanReadableSize(2 ** 50 - 600000), "1024.0 TB");
  for (const bad of [NaN, Infinity, 2 ** 63, "1 KB", {}, Symbol("s")]) assert.equal(thrown(() => files.getHumanReadableSize(bad)), "InternalError", label(bad));
  for (const bad of [true, null, undefined]) assert.equal(thrown(() => files.getHumanReadableSize(bad)), "InternalError", label(bad));
  assert.equal(thrown(() => files.getHumanReadableSize(-1)), "IllegalArgumentException");
  assert.equal(thrown(() => files.getHumanReadableSize(1, "true")), "InternalError");
  assert.equal(thrown(() => files.getHumanReadableSize(1, undefined)), "InternalError");
  assert.equal(files.getHumanReadableSize(9223372036854774784), "8.0 EB");
  const format = [[0, "0 B"], [999, "999 B"], [1000, "1.0 KiB"], [1023, "1.0 KiB"], [1280, "1.3 KiB"], [1536, "1.5 KiB"], [1023999, "1000.0 KiB"],
    [1024000, "0.98 MiB"], [1572864, "1.50 MiB"], [1073741824, "1.00 GiB"], [1099511627776, "1.00 TiB"], [5000000000000, "4.55 TiB"], [1.9, "1 B"],
    [-0.5, "0 B"], ["1024", "1.0 KiB"]];
  for (const [input, expected] of format) assert.equal(files.formatSizeWithUnit(input), expected, `formatSizeWithUnit ${input}`);
  assert.equal(thrown(() => files.formatSizeWithUnit(-1)), "IllegalArgumentException");
  assert.equal(thrown(() => files.formatSizeWithUnit(null)), "InternalError");
  assert.equal(thrown(() => files.formatSizeWithUnit(1, 2)), "InternalError");
});

test("read / write: charsets, conversions and errors", t => {
  const { files, hostFs } = setup(t);
  const d = `${SDCARD}/Download`;
  files.write(`${d}/w.txt`, "hello");
  assert.equal(files.read(`${d}/w.txt`), "hello");
  assert.equal(files.read(`${d}/w.txt`, "utf8"), "hello");
  files.write(`${d}/n.txt`, undefined);
  assert.equal(files.read(`${d}/n.txt`), "undefined");
  files.write(`${d}/n.txt`, [1, 2]);
  assert.equal(files.read(`${d}/n.txt`), "1,2");
  files.write(`${d}/n.txt`, Symbol("s"));
  assert.equal(files.read(`${d}/n.txt`), "Symbol(s)");
  files.write(`${d}/s.txt`, "a" + String.fromCharCode(0xd800) + "b" + String.fromCharCode(0xd83d, 0xde00));
  assert.deepEqual(bytes(files.readBytes(`${d}/s.txt`)), [0x61, 0x3f, 0x62, 0xf0, 0x9f, 0x98, 0x80]);
  files.write(`${d}/u16.txt`, "ab", "UTF-16");
  assert.deepEqual(bytes(files.readBytes(`${d}/u16.txt`)), [0xfe, 0xff, 0, 0x61, 0, 0x62]);
  assert.equal(files.read(`${d}/u16.txt`, "unicode"), "ab");
  assert.equal(files.read(`${d}/u16.txt`, "UTF-16BE").charCodeAt(0), 0xfeff);
  files.write(`${d}/u16.txt`, "", "UTF-16");
  assert.equal(files.readBytes(`${d}/u16.txt`).length, 0);
  files.write(`${d}/l1.txt`, "caf" + String.fromCharCode(0xe9, 0xd83d, 0xde00), "latin1");
  assert.deepEqual(bytes(files.readBytes(`${d}/l1.txt`)), [99, 97, 102, 233, 63, 63]);
  assert.equal(files.read(`${d}/l1.txt`, "US-ASCII"), "caf" + String.fromCharCode(0xfffd) + "??");
  // UTF-8: one U+FFFD per maximal subpart, a BOM kept.
  files.writeBytes(`${d}/m.txt`, [0x61, -0x20, -0x80, 0x62, -0x13, -0x60, -0x80, -1, 0x63, -0x11, -0x45, -0x41]);
  assert.equal(files.read(`${d}/m.txt`), "a" + String.fromCharCode(0xfffd, 0xfffd) + "b" + String.fromCharCode(0xfffd, 0xfffd, 0xfffd, 0xfffd) + "c" + String.fromCharCode(0xfeff));
  files.writeBytes(`${d}/g.txt`, [-42, -48]);
  assert.equal(files.read(`${d}/g.txt`, "GBK"), String.fromCharCode(0x4e2d));
  assert.equal(thrown(() => files.read(`${d}/g.txt`, "GBK", 1)), "InternalError");
  const plain = setup(t, { TextDecoder: undefined }).files;
  plain.write(`${d}/w.txt`, "hello");
  assert.equal(thrown(() => plain.read(`${d}/w.txt`, "GBK")), "UnsupportedCharsetException");
  assert.equal(thrown(() => files.write(`${d}/g2.txt`, "x", "GBK")), "UnsupportedCharsetException");
  assert.equal(thrown(() => files.read(`${d}/w.txt`, null)), "IllegalArgumentException");
  assert.equal(thrown(() => files.read(`${d}/w.txt`, undefined)), "UnsupportedCharsetException");
  assert.equal(thrown(() => files.read(`${d}/w.txt`, "")), "IllegalCharsetNameException");
  // Android finds a charset through ICU, which ignores case and everything but letters and digits in the name.
  assert.equal(files.read(`${d}/w.txt`, "-utf8"), files.read(`${d}/w.txt`));
  assert.equal(files.read(`${d}/w.txt`, "utf 8"), files.read(`${d}/w.txt`));
  assert.equal(thrown(() => files.read(`${d}/w.txt`, "bad name")), "IllegalCharsetNameException");
  assert.equal(thrown(() => files.read(`${d}/missing.txt`, "bad name")), "UncheckedIOException");
  assert.equal(thrown(() => files.read(d)), "UncheckedIOException");
  assert.equal(files.read(null, "bad name"), "");
  assert.equal(thrown(() => files.read()), "InternalError");
  assert.equal(thrown(() => files.write(`${d}/x.txt`, null)), "NullPointerException");
  assert.ok(!hostFs.existsSync(`${d}/x.txt`));
  assert.equal(thrown(() => files.write(null, "x")), "NullPointerException");
  assert.equal(thrown(() => files.write(`${d}/be.txt`, "x", "no-such")), "UnsupportedCharsetException");
  assert.equal(files.read(`${d}/be.txt`), "");
  files.write(`${d}/ne.txt`, "x", null);
  assert.equal(files.read(`${d}/ne.txt`), "x");
  files.write(`${d}/slash.txt/`, "s");
  assert.equal(files.read(`${d}/slash.txt`), "s");
  assert.equal(thrown(() => files.write(`${d}/none/x.txt`, "x")), "UncheckedIOException");
  try {
    files.read(`${d}/missing.txt`);
  } catch (e) {
    assert.match(e.message, /^org\.autojs\.autojs\.pio\.UncheckedIOException: java\.io\.FileNotFoundException: .*missing\.txt: open failed: ENOENT/);
  }
});

test("append, bytes and LiveConnect conversions", t => {
  const { files } = setup(t);
  const d = `${SDCARD}/Download`;
  files.append(`${d}/a.txt`, "x");
  files.append(`${d}/a.txt`, 1, "UTF-8");
  assert.equal(files.read(`${d}/a.txt`), "x1");
  assert.equal(thrown(() => files.append(null, "x")), "InternalError");
  assert.equal(thrown(() => files.append(`${d}/a.txt`, null)), "NullPointerException");
  assert.equal(thrown(() => files.append(`${d}/ad/`, "x")), "UncheckedIOException");
  assert.ok(files.isDir(`${d}/ad`));
  assert.throws(() => files.append(`${d}/ad`, "x"), { message: /FileNotFoundException: .*\/ad: open failed: EISDIR \(Is a directory\)$/ });
  files.writeBytes(`${d}/b.bin`, [1, -1, 127, -128, "5", 2.9, -2.9, 3n]);
  assert.deepEqual(bytes(files.readBytes(`${d}/b.bin`)), [1, 255, 127, 128, 5, 2, 254, 3]);
  files.writeBytes(`${d}/b2.bin`, files.readBytes(`${d}/b.bin`));
  files.appendBytes(`${d}/b2.bin`, new Int8Array([-1]));
  assert.equal(files.readBytes(`${d}/b2.bin`).length, 9);
  assert.ok(files.readBytes(`${d}/b.bin`) instanceof Uint8Array);
  for (const bad of [[128], [-129], [1, , 2], [true], [null], [undefined], ["x"], [Symbol("s")], [NaN]]) {
    assert.equal(thrown(() => files.writeBytes(`${d}/b3.bin`, bad)), "InternalError", label(bad));
  }
  for (const bad of ["ab", undefined, {}, 1, true, new Int16Array(1)]) {
    assert.equal(thrown(() => files.writeBytes(`${d}/b3.bin`, bad)), "InternalError", label(bad));
  }
  assert.ok(!files.exists(`${d}/b3.bin`));
  assert.equal(thrown(() => files.writeBytes(`${d}/b4.bin`, null)), "NullPointerException");
  assert.ok(files.exists(`${d}/b4.bin`));
  assert.equal(thrown(() => files.appendBytes(`${d}/b5.bin`, null)), "NullPointerException");
  assert.ok(files.exists(`${d}/b5.bin`));
  assert.equal(thrown(() => files.writeBytes(null, [1])), "InternalError");
  assert.equal(thrown(() => files.readBytes(null)), "NullPointerException");
  assert.equal(thrown(() => files.readBytes(d)), "UncheckedIOException");
  try {
    files.exists();
  } catch (e) {
    assert.equal(e.message, "Can't find method org.autojs.autojs.runtime.api.Files.exists().");
  }
  try {
    files.exists(undefined, 1, null, true, "s", () => 1, {});
  } catch (e) {
    assert.equal(e.message, "Can't find method org.autojs.autojs.runtime.api.Files.exists(org.mozilla.javascript.Undefined,number,null,boolean,string,function,object).");
  }
  try {
    files.write(`${d}/x.txt`, null);
  } catch (e) {
    assert.equal(e.message, "java.lang.NullPointerException: Parameter specified as non-null is null: method org.autojs.autojs.runtime.api.Files.write, parameter text");
  }
});

test("create, ensureDir, copy, rename, move, remove", t => {
  const { files, hostFs } = setup(t);
  const d = `${SDCARD}/Download`;
  assert.equal(files.create(`${d}/c.txt`), true);
  assert.equal(files.create(`${d}/c.txt`), true);
  assert.equal(files.create(`${d}/c/`), true);
  assert.equal(files.create(`${d}/c/`), false);
  assert.equal(files.create(`${d}/c`), true);
  assert.equal(files.create(`${d}/none/c.txt`), false);
  assert.equal(files.create(null), false);
  assert.equal(files.createIfNotExists(`${d}/ci/a/b.txt`), true);
  assert.equal(files.createIfNotExists(`${d}/ci/a/b.txt`), false);
  assert.equal(files.createIfNotExists(`${d}/ci/d/`), false);
  assert.ok(files.isDir(`${d}/ci/d`));
  assert.equal(files.createWithDirs(`${d}/cw/x.txt`), true);
  assert.equal(files.ensureDir(`${d}/e1/e2/f.txt`), true);
  assert.ok(files.isDir(`${d}/e1/e2`) && !files.exists(`${d}/e1/e2/f.txt`));
  assert.equal(files.ensureDir(`${d}/c.txt/y`), true);
  assert.equal(files.ensureDir(`${d}/c.txt/x/y`), false);
  assert.equal(files.exists(`${d}/c.txt/`), true);
  assert.equal(files.isFile(`${d}//c.txt`), true);
  assert.equal(files.isEmptyDir(`${d}/c`), true);
  assert.equal(files.isEmptyDir(d), false);
  files.write(`${d}/src.txt`, "copy me");
  assert.equal(files.copy(`${d}/src.txt`, `${d}/out/dst.txt`), true);
  assert.equal(files.read(`${d}/out/dst.txt`), "copy me");
  assert.equal(files.copy(`${d}/missing.txt`, `${d}/out/x.txt`), false);
  assert.equal(files.copy(d, `${d}/out/x.txt`), false);
  assert.equal(files.copy(`${d}/src.txt`, `${d}/out`), false);
  assert.equal(files.copy(`${d}/src.txt`, `${d}/c.txt/x.txt`), false);
  assert.equal(thrown(() => files.copy(null, `${d}/x`)), "InternalError");
  files.write(`${d}/self.txt`, "self");
  assert.equal(files.copy(`${d}/self.txt`, `${d}/self.txt`), true);
  assert.equal(files.read(`${d}/self.txt`), "");
  files.write(`${d}/r.txt`, "r");
  assert.equal(files.rename(`${d}/r.txt`, "/r2.txt"), true);
  assert.equal(files.read(`${d}/r2.txt`), "r");
  assert.equal(files.renameWithoutExtension(`${d}/r2.txt`, "r3"), true);
  assert.equal(files.read(`${d}/r3.txt`), "r");
  assert.equal(files.renameWithoutExtension(`${d}/missing.txt`, "m"), false);
  assert.equal(thrown(() => files.rename(`${d}/r3.txt`, null)), "NullPointerException");
  assert.equal(thrown(() => files.rename(null, null)), "NullPointerException");
  assert.equal(thrown(() => files.rename(null, "x")), "InternalError");
  assert.equal(files.move(`${d}/r3.txt`, `${d}/mv.txt`), true);
  assert.equal(files.move(`${d}/mv.txt`, `${d}/none/mv.txt`), false);
  assert.equal(files.move(`${d}/mv.txt`, "relative.txt"), true);
  assert.ok(hostFs.existsSync("/relative.txt"), "a relative target is resolved against /");
  assert.equal(files.remove(`${d}/c`), true);
  assert.equal(files.remove(`${d}/ci`), false);
  assert.equal(files.removeDir(`${d}/ci`), true);
  assert.equal(files.exists(`${d}/ci`), false);
  assert.equal(files.removeDir(`${d}/missing`), false);
  assert.equal(files.remove(null), false);
});

test("listDir filters through the interface adapter", t => {
  const { files } = setup(t);
  const d = `${SDCARD}/Download/ls`;
  files.createWithDirs(`${d}/a.txt`);
  files.create(`${d}/b.js`);
  assert.deepEqual(files.listDir(d).sort(), ["a.txt", "b.js"]);
  assert.deepEqual(files.listDir(d, n => n === "b.js"), ["b.js"]);
  assert.deepEqual(files.listDir(d, { call: n => n === "a.txt" }), ["a.txt"]);
  assert.deepEqual(files.listDir(`${d}/missing`, () => { throw new Error("called"); }), []);
  assert.deepEqual(files.listDir(null), []);
  assert.deepEqual(files.listDir(`${d}/a.txt`), []);
  assert.equal(thrown(() => files.listDir(d, () => undefined)), "ClassCastException");
  assert.equal(thrown(() => files.listDir(d, () => "true")), "ClassCastException");
  assert.equal(thrown(() => files.listDir(d, () => null)), "NullPointerException");
  assert.equal(thrown(() => files.listDir(d, {})), "NullPointerException");
  assert.equal(thrown(() => files.listDir(d, { call: true })), "InternalError");
  assert.equal(thrown(() => files.listDir(d, [])), "InternalError");
  assert.equal(thrown(() => files.listDir(d, new Date())), "InternalError");
  assert.equal(thrown(() => files.listDir(d, "x")), "InternalError");
  assert.equal(thrown(() => files.listDir(d, undefined)), "InternalError");
  assert.equal(thrown(() => files.listDir(d, null)), "NullPointerException");
  assert.equal(thrown(() => files.listDir(d, () => { throw new RangeError("stop"); })), "RangeError");
});

test("Java facade: toFile, open, readAssets", async t => {
  const { files, calls, java, javaAsked } = setup(t);
  assert.equal(javaAsked(), 0, "the facade is asked for on first use");
  const d = `${SDCARD}/Download`;
  const file = await files.toFile("x//y/");
  assert.equal(file.className, "java.io.File");
  assert.deepEqual(calls.at(-1), { create: "java.io.File", args: ["/work/cwd/x/y/"], options: { parameterTypes: ["java.lang.String"] } });
  assert.equal(await rejected(files.toFile()), "TypeError");
  assert.equal(await rejected(files.toFile(null)), "TypeError");
  assert.equal(await rejected(files.toFile("a", "b")), "TypeError");
  assert.equal(await rejected(files.toFile("../../..")), "IllegalArgumentException");
  files.write(`${d}/o.txt`, "o");
  const handle = await files.open("../../storage/emulated/0/Download/o.txt", "r", undefined, "16.9");
  assert.equal(handle.className, "org.autojs.autojs.pio.PReadableTextFile");
  assert.deepEqual(calls.at(-1), { callStatic: "org.autojs.autojs.pio.PFiles", method: "open", args: [`${d}/o.txt`, "r", null, 16],
    options: { parameterTypes: ["java.lang.String", "java.lang.String", "java.lang.String", "java.lang.Integer"] } });
  await files.open(`${d}/o.txt`, null, null, null, undefined);
  assert.deepEqual(calls.at(-1).args, [`${d}/o.txt`, null, null, null]);
  assert.equal(await rejected(files.open(`${d}/o.txt`, 1)), "IllegalArgumentException");
  assert.equal(calls.at(-1).args[1], "1");
  // The checks of FilesModule come first, then the Java exceptions of PFiles.open, renamed after their class.
  assert.equal(await rejected(files.open()), "TypeError");
  assert.equal(await rejected(files.open(undefined)), "TypeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, NaN)), "RangeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, Infinity)), "RangeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, 2 ** 31)), "RangeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, "x")), "TypeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, 1n)), "TypeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "r", null, 8, 1)), "TypeError");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "x")), "IllegalArgumentException");
  assert.equal(await rejected(files.open(`${d}/o.txt`, "")), "StringIndexOutOfBoundsException");
  assert.equal(await rejected(files.open(`${d}/missing.txt`)), "UncheckedIOException");
  assert.equal(await rejected(files.open("../../..")), "IllegalArgumentException");
  try {
    await files.open(`${d}/o.txt`, "r", null, NaN);
  } catch (e) {
    assert.equal(e.message, 'Failed to call method "files.open": [ Argument "bufferSize" must be of type number, got NaN ]');
  }
  try {
    await files.open(`${d}/missing.txt`);
  } catch (e) {
    assert.equal(e.code, "ERR_AUTOJS6_JAVA_CALL_FAILED");
    assert.match(e.message, /^org\.autojs\.autojs\.pio\.UncheckedIOException: java\.io\.FileNotFoundException: /);
    assert.ok(e.cause);
  }
  const before = java.released.length;
  assert.equal((await files.readAssets("apk_builder_default_signer_certificate_sha256.txt")).length, 66);
  assert.deepEqual(calls.filter(c => c.callStatic).at(-1).args.length, 1);
  assert.equal(java.released.length - before, 3, "context, assets and stream are released");
  assert.equal(await files.readAssets("init.js", "UTF-8"), ASSETS["init.js"]);
  assert.equal(await rejected(files.readAssets("no-such-asset")), "UncheckedIOException");
  assert.equal(await rejected(files.readAssets("modules")), "UncheckedIOException");
  assert.equal(await rejected(files.readAssets(null)), "InternalError");
  assert.equal(await rejected(files.readAssets()), "InternalError");
  assert.equal(await rejected(files.readAssets("init.js", null)), "IllegalArgumentException");
  assert.equal(await rejected(files.readAssets("init.js", undefined)), "UnsupportedCharsetException");
  try {
    await files.readAssets("no-such-asset");
  } catch (e) {
    assert.equal(e.message, "org.autojs.autojs.pio.UncheckedIOException: java.io.FileNotFoundException: no-such-asset");
  }
});

// The encoded case values, as the comparison harness records them.
function encode(v, depth = 0) {
  if (depth > 6) return { deep: 1 };
  if (v === undefined) return { u: 1 };
  if (v === null || typeof v === "boolean" || typeof v === "string") return v;
  if (typeof v === "number") return Number.isNaN(v) ? { n: "NaN" } : Object.is(v, -0) ? { n: "-0" } : Number.isFinite(v) ? v : { n: String(v) };
  if (typeof v === "bigint") return { big: String(v) };
  if (typeof v === "symbol") return { sym: String(v) };
  if (typeof v.__autojs6JavaHandle === "string") return { java: v.className };
  if (typeof v === "function") return { fn: 1 };
  if (Array.isArray(v)) return v.map(x => encode(x, depth + 1));
  return { o: Object.fromEntries(Object.keys(v).sort().map(k => [k, encode(v[k], depth + 1)])) };
}

test("every Node expression of cases.js runs", async t => {
  const { files, hostFs } = setup(t);
  const D = `${SDCARD}/Download/files-cases-D`;
  hostFs.mkdirSync(D);
  assert.ok(cases.length >= 200, `${cases.length} cases`);
  assert.equal(new Set(cases.map(c => c[0])).size, cases.length, "unique ids");
  const results = {};
  const broken = [];
  for (const c of cases) {
    const source = c.length > 2 ? c[2] : c[1];
    if (source === null) continue;
    try {
      let value = new Function("files", "D", `return (${source});`)(files, D);
      if (value && typeof value.then === "function") value = await value;
      results[c[0]] = { ok: encode(value) };
    } catch (e) {
      results[c[0]] = { th: { name: String(e && e.name), message: String(e && e.message) } };
      if (e instanceof ReferenceError || e instanceof SyntaxError) broken.push(`${c[0]}: ${e.name}: ${e.message}`);
    }
  }
  if (process.env.FILES_CASES_DUMP) fs.writeFileSync(process.env.FILES_CASES_DUMP, JSON.stringify(results, null, 1));
  assert.deepEqual(broken, []);
  // Thrown values are the error names the Rhino side reports (JavaException names without the prefix).
  const names = new Set(Object.values(results).filter(r => r.th).map(r => r.th.name));
  const known = new Set(["TypeError", "RangeError", "InternalError", "NullPointerException", "UncheckedIOException", "IllegalArgumentException",
    "UnsupportedCharsetException", "IllegalCharsetNameException", "ClassCastException", "StringIndexOutOfBoundsException"]);
  assert.deepEqual([...names].filter(n => !known.has(n)), []);
  const count = (pattern) => Object.entries(results).filter(([id]) => pattern.test(id)).length;
  assert.ok(count(/^files\.(open|toFile|readAssets)\./) > 40);
});

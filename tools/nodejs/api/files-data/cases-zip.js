// Rhino / Node comparison cases of the zip module (AutoJs6 api-coverage P3.2b, zip.js). Each case is [id, expression,
// nodeExpression]; expressions see zip, files (Rhino: the global files module; Node: a Rhino-like files module with
// read / write / exists / listDir / isDir / isFile, synchronous) and D, the absolute path of an empty writable test
// directory. Rhino expressions are plain ES5 and arrow functions and synchronous; Node expressions may return a Promise,
// which is awaited. Cases run in order and share the files under D. Results never contain absolute paths: errors are
// returned as names (RE / NE below) and paths are compared or written with D as <D>. Nothing is written outside D.

// RE(f, m) (Rhino) / NE(f, m) (Node): "no error" or the name of what f throws, a Java exception by its simple class name;
// with m a Java exception also gives its message with D as <D>. The script module layer writes its messages in the app
// language, so only Java exception messages are compared.
var RE = "(function (f, m) { try { f(); return 'no error'; } catch (e) { if (!e || !e.javaException) return String(e && e.name); " +
    "var n = String(e.javaException.getClass().getSimpleName()); return m ? n + ': ' + String(e.message).split(D).join('<D>') : n; } })";
var NE = "(async (f, m) => { try { await f(); return 'no error'; } catch (e) { const n = String(e && e.name); " +
    "return m && /Exception$/.test(n) ? n + ': ' + String(e.message).split(D).join('<D>') : n; } })";
// The sorted entry names of a Zip.
var RN = "(function (z) { return z.getFileHeaders().map(function (h) { return String(h.getFileName()); }).sort(); })";
var NN = "(async z => (await Promise.all((await z.getFileHeaders()).map(h => h.call('getFileName')))).sort())";
// The sorted names in a directory (the same in both).
var LS = "(function (p) { return [].slice.call(files.listDir(p)).map(String).sort(); })";
// The enum values and the encryption flag of a ZipParameters.
var RP = "(function (p) { return [String(p.getAesKeyStrength()), String(p.getAesVersion()), String(p.getCompressionLevel()), " +
    "String(p.getCompressionMethod()), String(p.getEncryptionMethod()), String(p.getSymbolicLinkAction()), p.isEncryptFiles()]; })";
var NP = "(async p => [...(await Promise.all(['getAesKeyStrength', 'getAesVersion', 'getCompressionLevel', 'getCompressionMethod', " +
    "'getEncryptionMethod', 'getSymbolicLinkAction'].map(m => p.call(m)))).map(e => e.value.name), await p.call('isEncryptFiles')])";
// The ZipParameters of zip.open(D + '/o.zip', options) as RP / NP give them.
function params(options) {
    return [RP + "(zip.open(D + '/o.zip', " + options + ").zipParameters)",
        "zip.open(D + '/o.zip', " + options + ").then(z => z.zipParameters()).then(" + NP + ")"];
}
// An error case: Rhino and Node statements (Node: an expression, possibly a Promise).
function error(id, rhino, node, withMessage) {
    var m = withMessage ? ", 1" : "";
    return [id, RE + "(function () { " + rhino + "; }" + m + ")", NE + "(() => " + node + m + ")"];
}
// An error case of an expression that is the same in both.
function same(id, expression, withMessage) {
    return error(id, expression, expression, withMessage);
}
// An error case of a Zip member: z is zip.open(D + '/o.zip').
function member(id, call, withMessage) {
    return error(id, "zip.open(D + '/o.zip')." + call, "zip.open(D + '/o.zip').then(z => z." + call + ")", withMessage);
}

var ZIP_CASES = [

    // Module shape
    ["zip.shape.members", "['open', 'zipFile', 'zipDir', 'zipFiles', 'unzip'].map(function (k) { return typeof zip[k]; })"],
    ["zip.shape.lengths", "[zip.open.length, zip.zipFile.length, zip.zipDir.length, zip.zipFiles.length, zip.unzip.length]"],

    // Arguments of the module functions (no file is touched)
    same("zip.invoke.none", "zip()"),
    same("zip.invoke.null", "zip(null)"),
    same("zip.invoke.blank", "zip(' ')"),
    same("zip.invoke.two", "zip(D + '/x.zip', 1)"),
    same("zip.open.none", "zip.open()"),
    same("zip.open.undefined", "zip.open(undefined)"),
    same("zip.open.empty", "zip.open('')"),
    same("zip.open.optionsString", "zip.open(D + '/x.zip', 'a')"),
    same("zip.open.optionsArray", "zip.open(D + '/x.zip', [1])"),
    same("zip.open.optionsFunction", "zip.open(D + '/x.zip', function () {})"),
    same("zip.open.three", "zip.open(D + '/x.zip', {}, 1)"),
    same("zip.open.blankBeforeOptions", "zip.open(' ', 'a')"),
    same("zip.zipFile.one", "zip.zipFile(D + '/a.txt')"),
    same("zip.zipFile.nullSource", "zip.zipFile(null, D + '/q.zip')"),
    same("zip.zipFile.blankSource", "zip.zipFile(' ', D + '/q.zip')"),
    same("zip.zipFile.destArray", "zip.zipFile(D + '/a.txt', [])"),
    same("zip.zipFile.destBeforeBlank", "zip.zipFile(' ', [], {})"),
    same("zip.zipFile.optionsType", "zip.zipFile(D + '/a.txt', D + '/q.zip', 'x')"),
    same("zip.zipFile.blankDest", "zip.zipFile(D + '/a.txt', ' ')", true),
    same("zip.zipFile.emptyDest", "zip.zipFile(D + '/a.txt', '')", true),
    same("zip.zipDir.one", "zip.zipDir(D)"),
    same("zip.zipDir.blank", "zip.zipDir('', D + '/q.zip')"),
    same("zip.zipFiles.null", "zip.zipFiles(null, D + '/q.zip')"),
    same("zip.zipFiles.string", "zip.zipFiles('a', D + '/q.zip')"),
    same("zip.zipFiles.object", "zip.zipFiles({}, D + '/q.zip')"),
    same("zip.zipFiles.elementNull", "zip.zipFiles([null], D + '/q.zip')"),
    same("zip.zipFiles.elementBlank", "zip.zipFiles([' '], D + '/q.zip')"),
    same("zip.unzip.one", "zip.unzip(D + '/a.zip')"),
    same("zip.unzip.blank", "zip.unzip('', D + '/out')"),
    same("zip.unzip.destType", "zip.unzip(D + '/a.zip', function () {})"),

    // Options the ZipNativeObject constructor rejects (no file is touched)
    same("zip.options.aesKeyStrength.code", "zip.open(D + '/o.zip', {aesKeyStrength: 100})"),
    same("zip.options.aesKeyStrength.name", "zip.open(D + '/o.zip', {aesKeyStrength: 'bad'})", true),
    same("zip.options.aesKeyStrength.nan", "zip.open(D + '/o.zip', {aesKeyStrength: NaN})", true),
    same("zip.options.aesVersion", "zip.open(D + '/o.zip', {aesVersion: 'three'})", true),
    same("zip.options.compressionLevel", "zip.open(D + '/o.zip', {compressionLevel: 'super'})", true),
    same("zip.options.compressionLevel.infinity", "zip.open(D + '/o.zip', {compressionLevel: Infinity})", true),
    same("zip.options.compressionMethod", "zip.open(D + '/o.zip', {compressionMethod: 'zip'})", true),
    same("zip.options.encryptionMethod.code", "zip.open(D + '/o.zip', {encryptionMethod: 7})"),
    same("zip.options.encryptionMethod.name", "zip.open(D + '/o.zip', {encryptionMethod: 'rot13'})", true),
    same("zip.options.symbolicLinkAction", "zip.open(D + '/o.zip', {symbolicLinkAction: 1})", true),
    same("zip.options.excludeFileFilter", "zip.open(D + '/o.zip', {excludeFileFilter: 'x'})"),
    same("zip.options.entryCRC", "zip.open(D + '/o.zip', {entryCRC: 'abc'})", true),
    same("zip.options.entrySize", "zip.open(D + '/o.zip', {entrySize: -Infinity})", true),
    same("zip.options.lastModifiedFileTime", "zip.open(D + '/o.zip', {lastModifiedFileTime: 1e30})", true),
    same("zip.options.ignoreDateTimeAttributes", "zip.open(D + '/o.zip', {ignoreDateTimeAttributes: false})"),
    same("zip.options.isIgnoreDateTimeAttributes", "zip.open(D + '/o.zip', {isIgnoreDateTimeAttributes: 0})"),
    same("zip.options.zipFileDestination", "zip.zipFile(D + '/a.txt', {aesKeyStrength: 5})"),

    // Zip values (zip.open touches no file)
    ["zip.open.props",
        "(function () { var z = zip.open(D + '/o.zip'); return [z.name, z.path === D + '/o.zip', z.getPath() === z.path, Object.keys(z.options).length]; })()",
        "zip.open(D + '/o.zip').then(z => [z.name, z.path === D + '/o.zip', z.getPath() === z.path, Object.keys(z.options).length])"],
    ["zip.invoke.name", "zip(D + '/o.zip').name", "zip(D + '/o.zip').then(z => z.name)"],
    ["zip.open.optionsIdentity",
        "(function () { var o = {comment: 'c'}; return zip.open(D + '/o.zip', o).options === o; })()",
        "(() => { const o = {comment: 'c'}; return zip.open(D + '/o.zip', o).then(z => z.options === o); })()"],
    ["zip.open.nullOptions", "Object.keys(zip.open(D + '/o.zip', null).options)", "zip.open(D + '/o.zip', null).then(z => Object.keys(z.options))"],
    ["zip.keys", "Object.keys(zip.open(D + '/o.zip'))", "zip.open(D + '/o.zip').then(z => Object.keys(z))"],
    ["zip.toString.empty", "String(zip.open(D + '/o.zip')).split(D).join('<D>')", "zip.open(D + '/o.zip').then(z => String(z).split(D).join('<D>'))"],
    ["zip.toString.options",
        "String(zip.open(D + '/o.zip', {compressionLevel: 'fast', comment: 'hi', other: 1, password: 'pw', entrySize: '12'})).split(D).join('<D>')",
        "zip.open(D + '/o.zip', {compressionLevel: 'fast', comment: 'hi', other: 1, password: 'pw', entrySize: '12'}).then(z => String(z).split(D).join('<D>'))"],
    ["zip.toStringReadable",
        "(function () { var z = zip.open(D + '/o.zip', {unixMode: true}); return z.toStringReadable(1) === String(z); })()",
        "zip.open(D + '/o.zip', {unixMode: true}).then(z => z.toStringReadable(1) === String(z))"],
    member("zip.toString.args", "toString(1)"),
    member("zip.getPath.args", "getPath(1)"),
    ["zip.path.relative",
        "(function () { var p = zip.open('rel/x.zip').path; return [p.charAt(0), p.slice(-10)]; })()",
        "zip.open('rel/x.zip').then(z => [z.path.charAt(0), z.path.slice(-10)])"],
    ["zip.path.dots",
        "zip.open('./a/../b//c.zip').path === zip.open('b/c.zip').path",
        "Promise.all([zip.open('./a/../b//c.zip'), zip.open('b/c.zip')]).then(([a, b]) => a.path === b.path)"],
    ["zip.path.parent",
        "(function () { var a = zip.open('x.zip').path, b = zip.open('../x.zip').path; var dir = a.slice(0, a.lastIndexOf('/')); return b === dir.slice(0, dir.lastIndexOf('/')) + '/x.zip'; })()",
        "Promise.all([zip.open('x.zip'), zip.open('../x.zip')]).then(([a, b]) => { const dir = a.path.slice(0, a.path.lastIndexOf('/')); return b.path === dir.slice(0, dir.lastIndexOf('/')) + '/x.zip'; })"],
    ["zip.path.trailing", "zip.open('t/').path.slice(-3)", "zip.open('t/').then(z => z.path.slice(-3))"],
    ["zip.path.absolute", "zip.open(D + '//abs//x.zip/').path === D + '//abs//x.zip/'", "zip.open(D + '//abs//x.zip/').then(z => z.path === D + '//abs//x.zip/')"],

    // ZipParameters and UnzipParameters built from the options
    ["zip.params.defaults"].concat(params("undefined")),
    ["zip.params.numbers"].concat(params("{aesKeyStrength: 1, aesVersion: 1, compressionLevel: 9, compressionMethod: 0, encryptionMethod: 0}")),
    ["zip.params.bits"].concat(params("{aesKeyStrength: 192.7, aesVersion: 5, compressionLevel: 42, compressionMethod: 3, encryptionMethod: 99}")),
    ["zip.params.names"].concat(params("{aesKeyStrength: 'aes_strength_128', aesVersion: '1', compressionLevel: 'deflate_level_fastest', " +
        "compressionMethod: 'comp_aes_enc', encryptionMethod: 'enc_method_strong', symbolicLinkAction: 'include_link_only'}")),
    ["zip.params.names2"].concat(params("{aesKeyStrength: 'key_strength_192', aesVersion: 'two', compressionLevel: 'maximum', " +
        "compressionMethod: 'store', encryptionMethod: 'enc_no_encryption', symbolicLinkAction: 'INCLUDE_LINK_AND_LINKED_FILE'}")),
    ["zip.params.encryptionNames",
        "['no', 'standard', 'zip_standard', 'enc_strong', -1, -0].map(function (m) { return String(zip.open(D + '/o.zip', {encryptionMethod: m}).zipParameters.getEncryptionMethod()); })",
        "Promise.all(['no', 'standard', 'zip_standard', 'enc_strong', -1, -0].map(m => zip.open(D + '/o.zip', {encryptionMethod: m}).then(z => z.zipParameters()).then(p => p.call('getEncryptionMethod')).then(e => e.value.name)))"],
    ["zip.params.flags",
        "(function () { var p = zip.open(D + '/o.zip', {includeRootFolder: false, overrideExistingFilesInZip: 0, readHiddenFiles: '', readHiddenFolders: null, unixMode: 1, writeExtendedLocalFileHeader: 'yes'}).zipParameters; " +
        "return [p.isIncludeRootFolder(), p.isOverrideExistingFilesInZip(), p.isReadHiddenFiles(), p.isReadHiddenFolders(), p.isUnixMode(), p.isWriteExtendedLocalFileHeader()]; })()",
        "zip.open(D + '/o.zip', {includeRootFolder: false, overrideExistingFilesInZip: 0, readHiddenFiles: '', readHiddenFolders: null, unixMode: 1, writeExtendedLocalFileHeader: 'yes'}).then(z => z.zipParameters())" +
        ".then(p => Promise.all(['isIncludeRootFolder', 'isOverrideExistingFilesInZip', 'isReadHiddenFiles', 'isReadHiddenFolders', 'isUnixMode', 'isWriteExtendedLocalFileHeader'].map(m => p.call(m))))"],
    ["zip.params.aliases",
        "(function () { var p = zip.open(D + '/o.zip', {isIncludeRootFolder: null, includeRootFolder: false, isUnixMode: true, unixMode: false, fileComment: 'a', comment: 'b'}).zipParameters; " +
        "return [p.isIncludeRootFolder(), p.isUnixMode(), String(p.getFileComment())]; })()",
        "zip.open(D + '/o.zip', {isIncludeRootFolder: null, includeRootFolder: false, isUnixMode: true, unixMode: false, fileComment: 'a', comment: 'b'}).then(z => z.zipParameters())" +
        ".then(p => Promise.all([p.call('isIncludeRootFolder'), p.call('isUnixMode'), p.call('getFileComment')]))"],
    ["zip.params.comment",
        "String(zip.open(D + '/o.zip', {comment: 'cc'}).zipParameters.getFileComment())",
        "zip.open(D + '/o.zip', {comment: 'cc'}).then(z => z.zipParameters()).then(p => p.call('getFileComment'))"],
    ["zip.params.values",
        "(function () { var p = zip.open(D + '/o.zip', {fileNameInZip: 'n.txt', defaultFolderPath: '/x', rootFolderNameInZip: 'root', entryCRC: '42', entrySize: 7.9, lastModifiedFileTime: 1000}).zipParameters; " +
        "return [String(p.getFileNameInZip()), String(p.getDefaultFolderPath()), String(p.getRootFolderNameInZip()), Number(p.getEntryCRC()), Number(p.getEntrySize()), Number(p.getLastModifiedFileTime())]; })()",
        "zip.open(D + '/o.zip', {fileNameInZip: 'n.txt', defaultFolderPath: '/x', rootFolderNameInZip: 'root', entryCRC: '42', entrySize: 7.9, lastModifiedFileTime: 1000}).then(z => z.zipParameters())" +
        ".then(p => Promise.all(['getFileNameInZip', 'getDefaultFolderPath', 'getRootFolderNameInZip', 'getEntryCRC', 'getEntrySize', 'getLastModifiedFileTime'].map(m => p.call(m))))" +
        ".then(v => v.map(x => typeof x === 'bigint' ? Number(x) : x))"],
    ["zip.params.encrypt",
        "[{password: 'pw'}, {encryptFiles: true}, {password: 'x', encryptFiles: false}, {encryptFiles: 0}].map(function (o) { return zip.open(D + '/o.zip', o).zipParameters.isEncryptFiles(); })",
        "Promise.all([{password: 'pw'}, {encryptFiles: true}, {password: 'x', encryptFiles: false}, {encryptFiles: 0}].map(o => zip.open(D + '/o.zip', o).then(z => z.zipParameters()).then(p => p.call('isEncryptFiles'))))"],
    ["zip.params.javaEnum",
        "(function () { var lv = zip.open(D + '/o.zip', {compressionLevel: 'ultra'}).zipParameters.getCompressionLevel(); return String(zip.open(D + '/o.zip', {compressionLevel: lv}).zipParameters.getCompressionLevel()); })()",
        "(async () => { const lv = await (await (await zip.open(D + '/o.zip', {compressionLevel: 'ultra'})).zipParameters()).call('getCompressionLevel'); " +
        "return (await (await (await zip.open(D + '/o.zip', {compressionLevel: lv})).zipParameters()).call('getCompressionLevel')).value.name; })()"],
    ["zip.params.unzip",
        "zip.open(D + '/o.zip', {isExtractSymbolicLinks: false}).unzipParameters.isExtractSymbolicLinks()",
        "zip.open(D + '/o.zip', {isExtractSymbolicLinks: false}).then(z => z.unzipParameters()).then(p => p.call('isExtractSymbolicLinks'))"],
    ["zip.handles.classes",
        "(function () { var z = zip.open(D + '/o.zip'); return [z.getZipFile(), z.zipFile, z.zipParameters, z.unzipParameters].map(function (h) { return String(h.getClass().getName()); }); })()",
        "zip.open(D + '/o.zip').then(z => Promise.all([z.getZipFile(), z.zipFile(), z.zipParameters(), z.unzipParameters()])).then(hs => hs.map(h => h.className))"],
    member("zip.getZipFile.args", "getZipFile(1)"),
    member("zip.isEncrypted.args", "isEncrypted(1)"),
    member("zip.isValidZipFile.args", "isValidZipFile(null)"),
    member("zip.getFileHeaders.args", "getFileHeaders(undefined)"),

    // Test files
    ["zip.setup",
        "(function () { files.write(D + '/a.txt', 'alpha'); files.write(D + '/b.txt', 'bravo'); files.write(D + '/c.log', 'charlie'); files.write(D + '/bad.zip', 'not a zip'); " +
        "return [files.read(D + '/a.txt'), files.isFile(D + '/b.txt'), files.isDir(D)]; })()"],

    // zipFile, isValidZipFile, isEncrypted, getFileHeader(s)
    ["zip.zipFile.basic",
        "(function () { var z = zip.zipFile(D + '/a.txt', D + '/one.zip'); return [z.name, z.path === D + '/one.zip', files.exists(D + '/one.zip'), " + RN + "(z)]; })()",
        "zip.zipFile(D + '/a.txt', D + '/one.zip').then(async z => [z.name, z.path === D + '/one.zip', files.exists(D + '/one.zip'), await " + NN + "(z)])"],
    ["zip.zipFile.fileNameInZip",
        RN + "(zip.zipFile(D + '/b.txt', D + '/two.zip', {fileNameInZip: 'docs/b2.txt'}))",
        "zip.zipFile(D + '/b.txt', D + '/two.zip', {fileNameInZip: 'docs/b2.txt'}).then(" + NN + ")"],
    same("zip.zipFile.missing", "zip.zipFile(D + '/missing.txt', D + '/m.zip')", true),
    // The default destination is in the working directory; zip4j checks the source before it writes anything.
    same("zip.zipFile.missingDefault", "zip.zipFile(D + '/missing.txt', undefined)", true),
    ["zip.isValidZipFile",
        "[zip(D + '/one.zip').isValidZipFile(), zip(D + '/a.txt').isValidZipFile(), zip(D + '/none.zip').isValidZipFile()]",
        "Promise.all([D + '/one.zip', D + '/a.txt', D + '/none.zip'].map(p => zip(p).then(z => z.isValidZipFile())))"],
    ["zip.isEncrypted.plain", "zip(D + '/one.zip').isEncrypted()", "zip(D + '/one.zip').then(z => z.isEncrypted())"],
    ["zip.getFileHeader.found",
        "(function () { var h = zip(D + '/one.zip').getFileHeader('a.txt'); return [String(h.getFileName()), Number(h.getUncompressedSize()), h.isDirectory()]; })()",
        "zip(D + '/one.zip').then(z => z.getFileHeader('a.txt')).then(h => Promise.all([h.call('getFileName'), h.call('getUncompressedSize'), h.call('isDirectory')]))"],
    ["zip.getFileHeader.missing", "zip(D + '/one.zip').getFileHeader('nope')", "zip(D + '/one.zip').then(z => z.getFileHeader('nope'))"],
    error("zip.getFileHeader.empty", "zip(D + '/one.zip').getFileHeader('')", "zip(D + '/one.zip').then(z => z.getFileHeader(''))", true),
    member("zip.getFileHeader.none", "getFileHeader()"),
    member("zip.getFileHeader.two", "getFileHeader('a', 'b')"),
    member("zip.getFileHeader.null", "getFileHeader(null)", true),
    ["zip.getFileHeaders.classes",
        "zip(D + '/one.zip').getFileHeaders().map(function (h) { return String(h.getClass().getName()); })",
        "zip(D + '/one.zip').then(z => z.getFileHeaders()).then(hs => hs.map(h => h.className))"],

    // addFile, addFiles, zipFiles, removeFile
    ["zip.addFile.existing",
        "(function () { var z = zip(D + '/one.zip'); var r = z.addFile(D + '/b.txt'); return [r, " + RN + "(z)]; })()",
        "zip(D + '/one.zip').then(async z => [await z.addFile(D + '/b.txt'), await " + NN + "(z)])"],
    ["zip.addFile.options",
        "(function () { var z = zip(D + '/one.zip'); z.addFile(D + '/c.log', {fileNameInZip: 'logs/c.txt'}); return " + RN + "(z); })()",
        "zip(D + '/one.zip').then(async z => { await z.addFile(D + '/c.log', {fileNameInZip: 'logs/c.txt'}); return " + NN + "(z); })"],
    error("zip.addFile.missing", "zip(D + '/one.zip').addFile(D + '/missing.txt')", "zip(D + '/one.zip').then(z => z.addFile(D + '/missing.txt'))", true),
    member("zip.addFile.none", "addFile()"),
    member("zip.addFile.three", "addFile(D + '/a.txt', {}, 1)"),
    member("zip.addFile.optionsString", "addFile(D + '/a.txt', 'x')"),
    member("zip.addFile.null", "addFile(null)", true),
    member("zip.addFile.badOption", "addFile(D + '/a.txt', {compressionMethod: 'zip'})", true),
    ["zip.addFiles.basic",
        "(function () { var z = zip(D + '/many.zip'); z.addFiles([D + '/a.txt', D + '/b.txt']); return " + RN + "(z); })()",
        "zip(D + '/many.zip').then(async z => { await z.addFiles([D + '/a.txt', D + '/b.txt']); return " + NN + "(z); })"],
    member("zip.addFiles.string", "addFiles('x')"),
    member("zip.addFiles.null", "addFiles(null)"),
    member("zip.addFiles.element", "addFiles([null])", true),
    ["zip.zipFiles.basic",
        "(function () { var z = zip.zipFiles([D + '/a.txt', D + '/b.txt', D + '/c.log'], D + '/three.zip'); return [z.name, " + RN + "(z)]; })()",
        "zip.zipFiles([D + '/a.txt', D + '/b.txt', D + '/c.log'], D + '/three.zip').then(async z => [z.name, await " + NN + "(z)])"],
    ["zip.zipFiles.store",
        "String(zip.zipFiles([D + '/a.txt'], D + '/store.zip', {compressionMethod: 'store'}).getFileHeader('a.txt').getCompressionMethod())",
        "zip.zipFiles([D + '/a.txt'], D + '/store.zip', {compressionMethod: 'store'}).then(z => z.getFileHeader('a.txt')).then(h => h.call('getCompressionMethod')).then(e => e.value.name)"],
    same("zip.zipFiles.missing", "zip.zipFiles([D + '/missing.txt'], D + '/q.zip')", true),
    same("zip.zipFiles.missingBeforeType", "zip.zipFiles([D + '/missing.txt', null], D + '/q.zip')"),
    same("zip.zipFiles.typeAfterExisting", "zip.zipFiles([D + '/a.txt', null], D + '/q.zip')"),
    same("zip.zipFiles.optionsDestination", "zip.zipFiles([D + '/a.txt'], {encryptionMethod: 7})"),
    ["zip.zipFiles.empty",
        "[" + RE + "(function () { zip.zipFiles([], D + '/empty.zip'); }, 1), files.exists(D + '/empty.zip')]",
        NE + "(() => zip.zipFiles([], D + '/empty.zip'), 1).then(r => [r, files.exists(D + '/empty.zip')])"],
    ["zip.removeFile",
        "(function () { var z = zip(D + '/three.zip'); var r = z.removeFile('b.txt'); return [r, " + RN + "(z)]; })()",
        "zip(D + '/three.zip').then(async z => [await z.removeFile('b.txt'), await " + NN + "(z)])"],
    ["zip.removeFile.missing",
        "(function () { var z = zip(D + '/three.zip'); return [" + RE + "(function () { z.removeFile('nope'); }), " + RN + "(z)]; })()",
        "zip(D + '/three.zip').then(async z => [await " + NE + "(() => z.removeFile('nope')), await " + NN + "(z)])"],
    member("zip.removeFile.none", "removeFile()"),

    // extractAll, extractFile, unzip
    ["zip.extractAll.basic",
        "(function () { zip(D + '/three.zip').extractAll(D + '/out1'); return [" + LS + "(D + '/out1'), files.read(D + '/out1/a.txt')]; })()",
        "zip(D + '/three.zip').then(z => z.extractAll(D + '/out1')).then(() => [" + LS + "(D + '/out1'), files.read(D + '/out1/a.txt')])"],
    member("zip.extractAll.null", "extractAll(null)", true),
    member("zip.extractAll.none", "extractAll()"),
    member("zip.extractAll.optionsString", "extractAll(D + '/out', 'x')"),
    // Rhino resolves the entry name like a file path, so a relative name is looked up under the working directory.
    error("zip.extractFile.relative", "zip(D + '/three.zip').extractFile('a.txt', D + '/out2')", "zip(D + '/three.zip').then(z => z.extractFile('a.txt', D + '/out2'))"),
    error("zip.extractFile.absolute", "zip(D + '/three.zip').extractFile('/a.txt', D + '/out2')", "zip(D + '/three.zip').then(z => z.extractFile('/a.txt', D + '/out2'))", true),
    member("zip.extractFile.one", "extractFile('a.txt')"),
    member("zip.extractFile.five", "extractFile('a', 'b', null, 'c', 'd')"),
    member("zip.extractFile.nullDest", "extractFile('/a.txt', null)", true),
    ["zip.unzip.tree",
        "(function () { var z = zip.unzip(D + '/two.zip', D + '/tree'); return [z.name, " + LS + "(D + '/tree'), " + LS + "(D + '/tree/docs'), files.read(D + '/tree/docs/b2.txt')]; })()",
        "zip.unzip(D + '/two.zip', D + '/tree').then(z => [z.name, " + LS + "(D + '/tree'), " + LS + "(D + '/tree/docs'), files.read(D + '/tree/docs/b2.txt')])"],
    ["zip.setup.tree",
        "(function () { files.write(D + '/tree/top.txt', 'top'); files.write(D + '/tree/.hid', 'h'); return " + LS + "(D + '/tree'); })()"],
    same("zip.unzip.badZip", "zip.unzip(D + '/bad.zip', D + '/out5')", true),
    // An object in place of the destination is the options; the destination is then the working directory, which
    // zip4j only checks before it fails to read the headers. Node's working directory is the plugin's copy of the
    // project, which the host cannot write, so zip4j fails there on the directory instead (same exception class).
    same("zip.unzip.optionsDestination", "zip.unzip(D + '/bad.zip', {})"),
    same("zip.unzip.optionsDestinationIgnoreDate", "zip.unzip(D + '/bad.zip', {ignoreDateTimeAttributes: 1})"),
    ["zip.unzip.missing",
        "[" + RE + "(function () { zip.unzip(D + '/none.zip', D + '/out4'); }, 1), files.exists(D + '/out4')]",
        NE + "(() => zip.unzip(D + '/none.zip', D + '/out4'), 1).then(r => [r, files.exists(D + '/out4')])"],
    ["zip.bad.isValidZipFile", "zip(D + '/bad.zip').isValidZipFile()", "zip(D + '/bad.zip').then(z => z.isValidZipFile())"],
    error("zip.bad.getFileHeaders", "zip(D + '/bad.zip').getFileHeaders()", "zip(D + '/bad.zip').then(z => z.getFileHeaders())", true),
    error("zip.bad.isEncrypted", "zip(D + '/bad.zip').isEncrypted()", "zip(D + '/bad.zip').then(z => z.isEncrypted())", true),

    // zipDir, addFolder, excludeFileFilter
    ["zip.zipDir.basic",
        "(function () { var z = zip.zipDir(D + '/tree', D + '/tree.zip'); return [z.name, " + RN + "(z)]; })()",
        "zip.zipDir(D + '/tree', D + '/tree.zip').then(async z => [z.name, await " + NN + "(z)])"],
    same("zip.zipDir.exists", "zip.zipDir(D + '/tree', D + '/tree.zip')", true),
    ["zip.zipDir.noRoot",
        RN + "(zip.zipDir(D + '/tree', D + '/tree-flat.zip', {includeRootFolder: false}))",
        "zip.zipDir(D + '/tree', D + '/tree-flat.zip', {includeRootFolder: false}).then(" + NN + ")"],
    ["zip.zipDir.hidden",
        RN + "(zip.zipDir(D + '/tree', D + '/tree-visible.zip', {readHiddenFiles: false}))",
        "zip.zipDir(D + '/tree', D + '/tree-visible.zip', {readHiddenFiles: false}).then(" + NN + ")"],
    ["zip.zipDir.filter",
        RN + "(zip.zipDir(D + '/tree', D + '/tree-filter.zip', {excludeFileFilter: function (f) { return String(f.getName()) === 'top.txt'; }}))",
        "zip.zipDir(D + '/tree', D + '/tree-filter.zip', {excludeFileFilter: f => f.call('getName').then(n => n === 'top.txt')}).then(" + NN + ")"],
    ["zip.zipDir.filterThis",
        RN + "(zip.zipDir(D + '/tree', D + '/tree-this.zip', {skip: 'b2.txt', excludeFileFilter: function (f) { return String(f.getName()) === this.skip; }}))",
        "zip.zipDir(D + '/tree', D + '/tree-this.zip', {skip: 'b2.txt', excludeFileFilter: function (f) { return f.call('getName').then(n => n === this.skip); }}).then(" + NN + ")"],
    ["zip.zipDir.filterUndefined",
        RN + "(zip.zipDir(D + '/tree', D + '/tree-all.zip', {excludeFileFilter: function () {}}))",
        "zip.zipDir(D + '/tree', D + '/tree-all.zip', {excludeFileFilter: function () {}}).then(" + NN + ")"],
    // zip4j wraps the error of the filter in a ZipException before it writes anything.
    ["zip.zipDir.filterThrows",
        "[" + RE + "(function () { zip.zipDir(D + '/tree', D + '/tree-throw.zip', {excludeFileFilter: function () { throw new Error('stop'); }}); }), files.exists(D + '/tree-throw.zip')]",
        NE + "(() => zip.zipDir(D + '/tree', D + '/tree-throw.zip', {excludeFileFilter: () => { throw new Error('stop'); }})).then(r => [r, files.exists(D + '/tree-throw.zip')])"],
    same("zip.zipDir.optionsDestination", "zip.zipDir(D + '/tree', {compressionLevel: 'x'})", true),
    ["zip.addFolder.basic",
        "(function () { var z = zip(D + '/folder.zip'); z.addFolder(D + '/tree/docs'); return " + RN + "(z); })()",
        "zip(D + '/folder.zip').then(async z => { await z.addFolder(D + '/tree/docs'); return " + NN + "(z); })"],
    ["zip.addFolder.rootName",
        "(function () { var z = zip(D + '/folder2.zip'); z.addFolder(D + '/tree/docs', {rootFolderNameInZip: 'r'}); return " + RN + "(z); })()",
        "zip(D + '/folder2.zip').then(async z => { await z.addFolder(D + '/tree/docs', {rootFolderNameInZip: 'r'}); return " + NN + "(z); })"],
    error("zip.addFolder.file", "zip(D + '/folder.zip').addFolder(D + '/a.txt')", "zip(D + '/folder.zip').then(z => z.addFolder(D + '/a.txt'))", true),
    member("zip.addFolder.none", "addFolder()"),

    // Passwords and encryption
    ["zip.aes.create",
        "(function () { var z = zip.zipFile(D + '/a.txt', D + '/enc.zip', {password: 'secret'}); var h = z.getFileHeader('a.txt'); " +
        "return [z.isEncrypted(), h.isEncrypted(), String(h.getEncryptionMethod()), String(h.getAesExtraDataRecord().getAesKeyStrength())]; })()",
        "zip.zipFile(D + '/a.txt', D + '/enc.zip', {password: 'secret'}).then(async z => { const h = await z.getFileHeader('a.txt'); " +
        "return [await z.isEncrypted(), await h.call('isEncrypted'), (await h.call('getEncryptionMethod')).value.name, (await (await h.call('getAesExtraDataRecord')).call('getAesKeyStrength')).value.name]; })"],
    ["zip.aes.isEncrypted", "zip(D + '/enc.zip').isEncrypted()", "zip(D + '/enc.zip').then(z => z.isEncrypted())"],
    error("zip.aes.noPassword", "zip(D + '/enc.zip').extractAll(D + '/out6')", "zip(D + '/enc.zip').then(z => z.extractAll(D + '/out6'))", true),
    error("zip.aes.wrongPassword", "zip(D + '/enc.zip').extractAll(D + '/out7', {password: 'wrong'})", "zip(D + '/enc.zip').then(z => z.extractAll(D + '/out7', {password: 'wrong'}))", true),
    ["zip.aes.options",
        "(function () { zip(D + '/enc.zip').extractAll(D + '/out8', {password: 'secret'}); return files.read(D + '/out8/a.txt'); })()",
        "zip(D + '/enc.zip').then(z => z.extractAll(D + '/out8', {password: 'secret'})).then(() => files.read(D + '/out8/a.txt'))"],
    ["zip.aes.setPassword",
        "(function () { var z = zip(D + '/enc.zip'); var r = z.setPassword('secret'); z.extractAll(D + '/out9'); return [r, files.read(D + '/out9/a.txt')]; })()",
        "zip(D + '/enc.zip').then(async z => { const r = await z.setPassword('secret'); await z.extractAll(D + '/out9'); return [r, files.read(D + '/out9/a.txt')]; })"],
    ["zip.aes.openPassword",
        "(function () { zip.open(D + '/enc.zip', {password: 'secret'}).extractAll(D + '/out10'); return files.read(D + '/out10/a.txt'); })()",
        "zip.open(D + '/enc.zip', {password: 'secret'}).then(z => z.extractAll(D + '/out10')).then(() => files.read(D + '/out10/a.txt'))"],
    ["zip.aes.unzip",
        "(function () { var z = zip.unzip(D + '/enc.zip', D + '/out11', {password: 'secret'}); return [z.name, files.read(D + '/out11/a.txt')]; })()",
        "zip.unzip(D + '/enc.zip', D + '/out11', {password: 'secret'}).then(z => [z.name, files.read(D + '/out11/a.txt')])"],
    error("zip.aes.unzipNoPassword", "zip.unzip(D + '/enc.zip', D + '/out15')", "zip.unzip(D + '/enc.zip', D + '/out15')", true),
    member("zip.setPassword.none", "setPassword()"),
    member("zip.setPassword.two", "setPassword('a', 'b')"),
    member("zip.setPassword.null", "setPassword(null)", true),
    ["zip.standard",
        "(function () { var z = zip.zipFile(D + '/b.txt', D + '/std.zip', {password: 'p', encryptionMethod: 'standard'}); var m = String(z.getFileHeader('b.txt').getEncryptionMethod()); " +
        "zip.unzip(D + '/std.zip', D + '/out12', {password: 'p'}); return [m, files.read(D + '/out12/b.txt')]; })()",
        "zip.zipFile(D + '/b.txt', D + '/std.zip', {password: 'p', encryptionMethod: 'standard'}).then(z => z.getFileHeader('b.txt')).then(h => h.call('getEncryptionMethod'))" +
        ".then(async e => { await zip.unzip(D + '/std.zip', D + '/out12', {password: 'p'}); return [e.value.name, files.read(D + '/out12/b.txt')]; })"],
    ["zip.aes128",
        "(function () { var r = zip.zipFile(D + '/a.txt', D + '/aes128.zip', {password: 'p', aesKeyStrength: 128}).getFileHeader('a.txt').getAesExtraDataRecord(); " +
        "return [String(r.getAesKeyStrength()), String(r.getAesVersion())]; })()",
        "zip.zipFile(D + '/a.txt', D + '/aes128.zip', {password: 'p', aesKeyStrength: 128}).then(z => z.getFileHeader('a.txt')).then(h => h.call('getAesExtraDataRecord'))" +
        ".then(r => Promise.all([r.call('getAesKeyStrength'), r.call('getAesVersion')])).then(v => v.map(e => e.value.name))"],
    ["zip.encryptFiles.noPassword",
        "[" + RE + "(function () { zip.zipFile(D + '/a.txt', D + '/nopw.zip', {encryptFiles: true}); }, 1), files.exists(D + '/nopw.zip')]",
        NE + "(() => zip.zipFile(D + '/a.txt', D + '/nopw.zip', {encryptFiles: true}), 1).then(r => [r, files.exists(D + '/nopw.zip')])"],
    ["zip.addFile.password",
        "(function () { var z = zip(D + '/mix.zip'); z.addFile(D + '/a.txt', {password: 'k'}); z.extractAll(D + '/out13'); return [z.isEncrypted(), files.read(D + '/out13/a.txt')]; })()",
        "zip(D + '/mix.zip').then(async z => { await z.addFile(D + '/a.txt', {password: 'k'}); await z.extractAll(D + '/out13'); return [await z.isEncrypted(), files.read(D + '/out13/a.txt')]; })"],
    // buildUnzipParameters sets the password before it rejects ignoreDateTimeAttributes, and the ZipFile keeps it.
    ["zip.extractAll.ignoreDateKeepsPassword",
        "(function () { var z = zip(D + '/enc.zip'); var r = " + RE + "(function () { z.extractAll(D + '/out14', {password: 'secret', ignoreDateTimeAttributes: true}); }); " +
        "z.extractAll(D + '/out14'); return [r, files.read(D + '/out14/a.txt')]; })()",
        "zip(D + '/enc.zip').then(async z => { const r = await " + NE + "(() => z.extractAll(D + '/out14', {password: 'secret', ignoreDateTimeAttributes: true})); " +
        "await z.extractAll(D + '/out14'); return [r, files.read(D + '/out14/a.txt')]; })"],
    ["zip.toString.passwordZip",
        "String(zip.open(D + '/enc.zip', {password: 'secret', aesVersion: 1})).split(D).join('<D>')",
        "zip.open(D + '/enc.zip', {password: 'secret', aesVersion: 1}).then(z => String(z).split(D).join('<D>'))"],

    // Several calls on one Zip (Node runs them one at a time)
    ["zip.queue",
        "(function () { var z = zip(D + '/q2.zip'); z.addFile(D + '/a.txt'); z.addFile(D + '/b.txt'); return [z.isValidZipFile(), " + RN + "(z)]; })()",
        "zip(D + '/q2.zip').then(z => Promise.all([z.addFile(D + '/a.txt'), z.addFile(D + '/b.txt'), z.isValidZipFile()]).then(async r => [r[2], await " + NN + "(z)]))"],
    ["zip.getZipFile.file",
        "String(zip(D + '/one.zip').getZipFile().getFile().getName())",
        "zip(D + '/one.zip').then(z => z.getZipFile()).then(f => f.call('getFile')).then(f => f.call('getName'))"],
    ["zip.zipFile.member",
        "zip(D + '/one.zip').zipFile.isValidZipFile()",
        "zip(D + '/one.zip').then(z => z.zipFile()).then(f => f.call('isValidZipFile'))"],
    ["zip.done", LS + "(D).filter(function (n) { return /[.]zip$/.test(n); })"]
];

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: ZIP_CASES};
}

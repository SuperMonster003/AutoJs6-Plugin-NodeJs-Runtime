"use strict";
// Rhino comparison of the shared JS tools core (AutoJs6 api-coverage P3.2a): the cases of js-tools/cases.js against the
// values Rhino gave on a device (js-tools/rhino-expected.json), on the main thread and in a nested Worker started with
// the real Worker preload. Cases that need the host (Java handles, the axios facade) run on a device only
// (js-tools/device_js_tools.py); dayjs / pangu / cheerio / i18n have no Node entry yet (api-coverage Q61).
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const {cases} = require("./js-tools/cases.js");
const {runCases, compareAll} = require("./js-tools/harness.js");
const source = fs.readFileSync(path.join(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
function section(start, end) {
  const first = source.indexOf(start), last = source.indexOf(end, first);
  assert.ok(first >= 0 && last > first, "missing embedded section: " + start);
  return source.slice(first, last);
}
const core = section("  // Shared JS tools core.", "  // End shared JS tools core.");
const createJsTools = new Function(core + ";return __autojs6_create_js_tools;")();
// The oracle device (Redmi 22120RN86C, API 33, density 2).
const device = {sdkInt: 33, density: 2, densityDpi: 320, scaledDensity: 2};
const deviceOnly = new Set(cases.filter(([id, rhino, node]) =>
  /^(util\.java\.|s13n\.(point|throwable)\.|global\.(axios|dayjs|pangu|cheerio|i18n)\.)/.test(id) ||
  /\bjava\./.test(node === undefined ? rhino : String(node))).map(([id]) => id));
const desktop = id => !deviceOnly.has(id) && !id.startsWith("jsox.extend");

function check(results) {
  const report = compareAll(results);
  assert.deepEqual(report.failures, {});
  assert.ok(report.pass > 400, "compared cases: " + report.pass);
  return report;
}

function tools(vibrations = []) {
  return createJsTools({device, log() {}, warn() {}, java() {
    return {callStatic: async (type, method, args) => { vibrations.push(args[0]); }};
  }});
}
function env(t) {
  return {util: t.util, species: t.species, G: t.global, Mathx: t["jsox.mathx"], Arrayx: t["jsox.arrayx"], Numberx: t["jsox.numberx"],
    jsox: t.jsox, cvt: t.converter, fmt: t.formatter, s13n: t.s13n};
}

test("main thread core matches Rhino", async () => {
  const vibrations = [];
  check(await runCases(env(tools(vibrations)), desktop));
  assert.ok(vibrations.length === 0 || vibrations.every(Array.isArray));
});

test("Worker modules match Rhino and keep node:util", async () => {
  const constructor = section("  function __autojs6_worker_constructor(", "  // The worker keeps synchronous metadata");
  const modules = section("  function __autojs6_worker_host_modules(", "  async function __autojs6_worker_invoke(");
  const preload = section("  function __autojs6_worker_preload(", "  function __autojs6_limited_worker_threads(");
  const createWorker = new Function("return (" + constructor + ")")();
  const directory = path.resolve(__dirname, "../../../build/js-tools-tests");
  fs.mkdirSync(directory, {recursive: true});
  const preloadPath = path.join(directory, "preload.cjs");
  const skip = ",undefined".repeat(20);
  fs.writeFileSync(preloadPath, `(${preload})(require,${constructor},()=>{},${modules}${skip},${core.slice(core.indexOf("function __autojs6_create_js_tools"))});`);
  const t = tools();
  const workerGlobals = JSON.parse(/global: (\[[^\]]*\])/.exec(section("hostModuleMethods: {", "hostModuleConstants: {"))[1]);
  const hostModuleMethods = {java: [], util: Object.keys(t.util), species: Object.keys(t.species), global: workerGlobals, s13n: Object.keys(t.s13n),
    formatter: Object.keys(t.formatter), fmt: Object.keys(t.formatter), converter: Object.keys(t.converter), cvt: Object.keys(t.converter),
    jsox: Object.keys(t.jsox), "jsox.mathx": Object.keys(t["jsox.mathx"]), "jsox.arrayx": Object.keys(t["jsox.arrayx"]), "jsox.numberx": Object.keys(t["jsox.numberx"])};
  const config = {preloadPath, entryUrl: "file:///entry.cjs", gatedBuiltins: [], moduleSources: {}, typeScriptSources: {}, resourceLimits: {},
    hostModuleMethods, hostModuleConstants: {device}, consoleControls: []};
  const Worker = createWorker(require, () => config, async () => { throw new Error("no host call expected"); });
  const casesPath = JSON.stringify(path.join(__dirname, "js-tools/harness.js"));
  const body = `
    const {parentPort} = require("node:worker_threads");
    const {runCases} = require(${casesPath});
    const nodeUtil = require("util");
    const env = {util: require("autojs6:util"), species: require("autojs6:species"), G: require("autojs6:global"),
      Mathx: require("jsox.mathx"), Arrayx: require("jsox.arrayx"), Numberx: require("jsox.numberx"), jsox: require("jsox"),
      cvt: require("cvt"), fmt: require("fmt"), s13n: require("s13n")};
    import("autojs6:util").then(async esm => {
      const ids = new Set(${JSON.stringify(cases.map(([id]) => id).filter(desktop))});
      const results = await runCases(env, id => ids.has(id));
      parentPort.postMessage({results, nativeUtil: typeof nodeUtil.promisify, esm: esm.isEmptyObject === env.util.isEmptyObject,
        globals: Object.keys(env.G).sort()});
    });`;
  const outer = `const {Worker, parentPort} = require("node:worker_threads");
    new Worker(${JSON.stringify(body)}, {eval: true}).on("message", value => parentPort.postMessage(value)).on("error", error => { throw error; });`;
  const message = await new Promise((resolve, reject) => {
    let result;
    const worker = new Worker(outer, {eval: true, execArgv: []});
    worker.on("message", value => { result = value; });
    worker.on("error", reject);
    worker.on("exit", code => code === 0 ? resolve(result) : reject(new Error("Worker exit " + code)));
  });
  check(message.results);
  assert.equal(message.nativeUtil, "function");
  assert.equal(message.esm, true);
  assert.deepEqual(message.globals, [...workerGlobals].sort());
});

test("jsox.extend installs the members on the built-ins like Rhino", async () => {
  // Last: the cases change this realm's Math, Array.prototype, Number.prototype and Number, as they do in Rhino.
  const report = compareAll(await runCases(env(tools()), id => id.startsWith("jsox.extend")));
  assert.deepEqual(report.failures, {});
  assert.equal(report.pass, 4);
});

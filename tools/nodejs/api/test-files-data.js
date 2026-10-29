"use strict";
// Rhino comparison of colors / Color, mime and the timing recorder of the shared JS tools core (AutoJs6 api-coverage
// P3.2b): the cases of files-data/cases-colors.js and cases-mime-recorder.js against the values Rhino gave on a device
// (files-data/rhino-expected.json), on the main thread and in a nested Worker started with the real Worker preload. Cases
// that need the host (Java handles, Paint, images, the theme) and the files / zip / storages / mediainfo groups run on a
// device only (files-data/device_files_data.py). Cases on which Rhino's interpreter itself fails (a host Color member
// rethrowing a checked exception) only require Node to throw.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const {encode, thrown} = require("./js-tools/cases.js");
const {compare} = require("./js-tools/harness.js");
const expected = require("./files-data/rhino-expected.json").cases;
const source = fs.readFileSync(path.join(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
function section(start, end) {
  const first = source.indexOf(start), last = source.indexOf(end, first);
  assert.ok(first >= 0 && last > first, "missing embedded section: " + start);
  return source.slice(first, last);
}
const core = section("  // Shared JS tools core.", "  // End shared JS tools core.");
const createJsTools = new Function(core + ";return __autojs6_create_js_tools;")();
// The oracle device (Xiaomi 23046RP50C, API 35).
const device = {sdkInt: 35, density: 2.75, densityDpi: 440, scaledDensity: 2.75};
const deviceOnly = (id, node) => /\b(java|D)\b/.test(node) || /themeColor|toColorStateList|^extract\./i.test(id);
const GROUPS = {colors: "cases-colors.js", mime: "cases-mime-recorder.js"};
const desktopCases = Object.fromEntries(Object.entries(GROUPS).map(([group, file]) => [group,
  require("./files-data/" + file).cases.filter(c => c[2] !== null && !deviceOnly(c[0], c.length > 2 ? c[2] : c[1]))
    .map(c => [group + "/" + c[0], c.length > 2 ? c[2] : c[1]])]));

// The results of [id, expression] cases evaluated with the names of env in scope.
async function run(cases, env) {
  const names = Object.keys(env), values = names.map(name => env[name]);
  const results = {};
  for (const [id, expression] of cases) {
    try {
      let value = new Function(...names, "return (" + expression + ");")(...values);
      if (value && typeof value.then === "function") value = await value;
      results[id] = {ok: encode(value, () => null)};
    } catch (e) {
      results[id] = {th: thrown(e)};
    }
  }
  return results;
}

function check(results) {
  const failures = {};
  let pass = 0;
  for (const [id, actual] of Object.entries(results)) {
    const wanted = expected[id];
    const verdict = !wanted ? "no expected value" : wanted.rhinoCrash ? (actual.th ? "pass" : "Rhino fails on this error, Node returned " +
      JSON.stringify(actual.ok)) : compare(wanted, actual);
    if (verdict === "pass") pass += 1;
    else failures[id] = verdict;
  }
  assert.deepEqual(failures, {});
  return pass;
}

function tools() {
  return createJsTools({device, log() {}, warn() {}, java() { throw new Error("no host call expected"); }, android: () => ({})});
}

test("main thread colors, mime and recorder match Rhino", async () => {
  const t = tools();
  const pass = check(Object.assign(await run(desktopCases.colors, {colors: t.colors, Color: t.global.Color}),
    await run(desktopCases.mime, {mime: t.mime, recorder: t.recorder})));
  assert.ok(pass > 700, "compared cases: " + pass);
});

test("Worker colors, mime and recorder match Rhino", async () => {
  const constructor = section("  function __autojs6_worker_constructor(", "  // The worker keeps synchronous metadata");
  const modules = section("  function __autojs6_worker_host_modules(", "  async function __autojs6_worker_invoke(");
  const preload = section("  function __autojs6_worker_preload(", "  function __autojs6_limited_worker_threads(");
  const createWorker = new Function("return (" + constructor + ")")();
  const directory = path.resolve(__dirname, "../../../build/files-data-tests");
  fs.mkdirSync(directory, {recursive: true});
  const preloadPath = path.join(directory, "preload.cjs");
  fs.writeFileSync(preloadPath, `(${preload})(require,${constructor},()=>{},${modules}${",undefined".repeat(20)},${core.slice(core.indexOf("function __autojs6_create_js_tools"))});`);
  const t = tools();
  const workerGlobals = JSON.parse(/global: (\[[^\]]*\])/.exec(section("hostModuleMethods: {", "hostModuleConstants: {"))[1]);
  const hostModuleMethods = {java: [], util: [], global: workerGlobals, colors: Object.keys(t.colors), mime: Object.keys(t.mime),
    recorder: ["getStatus", "start", "stop"]};
  const config = {preloadPath, entryUrl: "file:///entry.cjs", gatedBuiltins: [], moduleSources: {}, typeScriptSources: {}, resourceLimits: {},
    hostModuleMethods, hostModuleConstants: {device}, consoleControls: []};
  const Worker = createWorker(require, () => config, async () => { throw new Error("no host call expected"); });
  const body = `
    const {parentPort} = require("node:worker_threads");
    const {encode, thrown} = require(${JSON.stringify(path.join(__dirname, "js-tools/cases.js"))});
    ${run.toString()}
    import("autojs6:colors").then(async esm => {
      const colors = require("colors"), Color = require("autojs6:global").Color;
      const results = Object.assign(await run(${JSON.stringify(desktopCases.colors)}, {colors, Color}),
        await run(${JSON.stringify(desktopCases.mime)}, {mime: require("autojs6:mime"), recorder: require("recorder")}));
      parentPort.postMessage({results, esm: esm.toInt === colors.toInt && esm.default === colors});
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
  assert.ok(check(message.results) > 700);
  assert.equal(message.esm, true);
});

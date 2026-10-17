"use strict";
const assert = require("node:assert/strict"), fs = require("node:fs"), path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
function section(start, end) {
  const a = source.indexOf(start), b = source.indexOf(end, a);
  assert(a >= 0 && b > a); return source.slice(a, b);
}
const calls = []; let active = null, serial = 0, delayStart = 0;
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
async function call(module, method, args, options) {
  calls.push({ module, method, args, options });
  if (module === "media_projection") {
    if (method === "requestScreenCapture") return { id: "capture", __autojs6ScreenCapturerHandle: "capture", width: 720, height: 1280 };
    if (method === "nextImage") return { id: "image", __autojs6ImageHandle: "image", width: 720, height: 1280 };
    return null;
  }
  if (module === "recorder") return { recording: method === "start" };
  assert.equal(module, "screen_recorder");
  if (method === "start") {
    assert.equal(active, null); await wait(delayStart);
    return active = { id: String(++serial), state: "recording", path: args[0], ...args[1] };
  }
  if (method === "getStatus") return active || { state: "idle" };
  if (args[0] && args[0].id !== active?.id) return method === "stop" ? null : false;
  const previous = active; active = null;
  return method === "stop" ? previous : !!previous;
}
const setup = new Function("__autojs6_call_autojs", `
  let __autojs6_limited_screen_recorder_cache, __autojs6_limited_media_projection_cache, __autojs6_limited_recorder_cache;
  const __autojs6_bridge_timeout_ms=x=>x, __autojs6_bridge_signal_from_options=x=>x.signal;
  const __autojs6_invalid_arg_type=x=>new TypeError(x), __autojs6_number_property=(x,k)=>x[k];
  const __autojs6_image_options=x=>x||{}, __autojs6_media_options=__autojs6_image_options;
  const __autojs6_media_projection_bridge_options=x=>x, __autojs6_media_bridge_options=(_m,_n,x)=>x;
  const __autojs6_media_freeze_record=x=>Object.freeze(x), __autojs6_track_image_creation=(_m,_n,fn)=>fn();
  ${section("  function __autojs6_media_projection_handle_id(", "  function __autojs6_limited_image(")}
  ${section("  function __autojs6_limited_recorder(", "  function __autojs6_media_file_path(")}
  ${section("  async function __autojs6_worker_invoke(", "  function __autojs6_worker_preload(")}
  return { api: __autojs6_limited_screen_recorder(), projection: __autojs6_limited_media_projection(),
    recorder: __autojs6_limited_recorder(), invoke: __autojs6_worker_invoke };
`)(call);
const constructor = section("  function __autojs6_worker_constructor(", "  // The worker keeps synchronous metadata");
const modules = section("  function __autojs6_worker_host_modules(", "  async function __autojs6_worker_invoke(");
const preload = section("  function __autojs6_worker_preload(", "  function __autojs6_limited_worker_threads(");
const directory = path.resolve(__dirname, "../../../build/screen-recording-tests"); fs.mkdirSync(directory, { recursive: true });
const preloadPath = path.join(directory, "preload.cjs");
fs.writeFileSync(preloadPath, `(${preload})(require,${constructor},()=>{},${modules});`);
const config = { preloadPath, gatedBuiltins: [], moduleSources: {}, typeScriptSources: {}, resourceLimits: {},
  hostModuleMethods: { screen_recorder: Object.keys(setup.api), media_projection: Object.keys(setup.projection), recorder: Object.keys(setup.recorder) } };
const Worker = new Function("return (" + constructor + ")")()(require, () => config, setup.invoke);
function run(code, options = { eval: true }) {
  return new Promise((resolve, reject) => {
    let result; const worker = new Worker(code, options);
    worker.on("message", value => result = value); worker.on("error", reject);
    worker.on("exit", status => status ? reject(Error("Worker exit " + status)) : resolve(result));
  });
}
async function eventually(predicate) {
  for (let i = 0; i < 100; i++) { if (predicate()) return; await wait(20); }
  assert(predicate(), "Worker cleanup did not settle");
}
(async () => {
  const api = setup.api;
  for (const audio of [undefined, false, "none", "microphone", "playback", "mixed"]) {
    const start = await api.start("movie.mp4", { audio });
    assert.deepEqual(calls.at(-1).options.permissions, !audio || audio === "none" ? ["screen_capture"] : ["screen_capture", "media", "media.recording"]);
    assert.equal(await api.cancel({ id: "stale" }), false); assert.equal((await api.getStatus()).id, start.id);
    assert.equal((await api.stop({ id: start.id })).id, start.id);
  }
  for (const options of [{ audio: true }, { audio: "invalid" }, { width: NaN }, { frameRate: 1.5 }]) await assert.rejects(api.start("movie.mp4", options), TypeError);
  await assert.rejects(api.start(""), TypeError);
  for (const id of [null, "", 0, {}]) assert.throws(() => api.cancel({ id }), TypeError);
  const abort = new AbortController(); delayStart = 60;
  const pending = api.start("aborted.mp4", { signal: abort.signal }); abort.abort();
  await assert.rejects(pending, { code: "ABORT_ERR" }); assert.equal(active, null); delayStart = 0;
  const body = `const assert=require('node:assert/strict'),{parentPort}=require('node:worker_threads');
    (async()=>{const screen=require('autojs6:screen_recorder'),projection=require('media_projection');
      const cap=await projection.requestScreenCapture();assert.equal(typeof cap.nextImage,'function');
      const recording=await screen.start('worker.mp4',{audio:'mixed'});
      assert.equal((await cap.nextImage()).width,720);assert.equal((await screen.stop()).id,recording.id);
      await cap.stop();assert.equal((await require('recorder').start({path:'audio.m4a'})).recording,true);
      await require('autojs6:recorder').stop();parentPort.postMessage('passed');})();`;
  assert.equal(await run(body), "passed");
  assert.equal(await run(`const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSON.stringify(body)},{eval:true}).on('message',x=>parentPort.postMessage(x));`), "passed");
  const esm = path.join(directory, "entry.mjs");
  fs.writeFileSync(esm, `import screen,{start,stop} from 'autojs6:screen_recorder';import {requestScreenCapture} from 'autojs6:media_projection';import recorder from 'autojs6:recorder';import {parentPort} from 'node:worker_threads';
    const cap=await requestScreenCapture();await start('esm.mp4');await cap.nextImage();await stop();await cap.stop();if(typeof recorder.start!=='function'||screen.start!==start)throw Error('exports');parentPort.postMessage('esm');`);
  assert.equal(await run(esm, {}), "esm");
  await run(`require('screen_recorder').start('exit.mp4').then(()=>require('node:worker_threads').parentPort.postMessage('started'));`);
  await eventually(() => active === null);
  delayStart = 120;
  const worker = new Worker(`require('screen_recorder').start('terminated.mp4');setInterval(()=>{},1000);`, { eval: true });
  await eventually(() => calls.at(-1).args?.[0] === "terminated.mp4"); await worker.terminate();
  await eventually(() => active === null); await wait(180); assert.equal(active, null); delayStart = 0;
  const held = new Worker(`require('screen_recorder').start('held.mp4').then(x=>require('node:worker_threads').parentPort.postMessage(x.id));setInterval(()=>{},1000);`, { eval: true });
  const old = await new Promise(resolve => held.once("message", resolve)); await api.stop({ id: old });
  const replacement = await api.start("replacement.mp4"); await held.terminate(); await wait(80);
  assert.equal((await api.getStatus()).id, replacement.id); await api.cancel();
  console.log("Screen recording: main/CJS/ESM/nested, audio options, capture handles, stale IDs, abort/exit/startup cleanup passed");
})().catch(error => { console.error(error); process.exitCode = 1; });

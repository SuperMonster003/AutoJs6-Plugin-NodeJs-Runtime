"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const source = fs.readFileSync(path.join(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
function section(a, b) { const start = source.indexOf(a), end = source.indexOf(b, start); assert(start >= 0 && end > start); return source.slice(start, end); }
const ui = section("  function __autojs6_create_ui(", "  function __autojs6_create_compose(");
const compose = section("  function __autojs6_create_compose(", "  function __autojs6_ui_options(");
const createCompose = new Function("return (" + compose + ")")();
const createUi = new Function("return (" + ui + ")")();
const live = new Map(), calls = [], released = [];
let next = 0;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function host(module, method, args) {
  calls.push({ module, method, args: structuredClone(args) });
  if (method === "isAvailable") return true;
  if (["mount", "dialog", "floaty", "showLayout"].includes(method)) {
    const id = "window-" + ++next; live.set(id, { tree: args[0], events: [{ type: "shown" }] }); return { id };
  }
  const record = live.get(args[0]);
  if (method === "close") { live.delete(args[0]); released.push(args[0]); return null; }
  if (method === "drainEvents") { await delay(15); return record ? record.events.splice(0) : [{ type: "close" }]; }
  assert(record, "active window required");
  if (method === "render") record.tree = args[1];
  return null;
}
async function exercise(api) {
  const counter = api.state(0), reference = api.ref(); let clicks = 0, renders = 0;
  const session = await api.mount(() => { renders++; return api.Column({},
    api.Text({ text: "count=" + counter.value, ref: reference }),
    api.Button({ onClick() { clicks++; counter.value++; } }, api.Text({ text: "next" }))); });
  assert(reference.current); assert.equal(api.sessions.length, 1);
  const record = live.get(session.id);
  record.events.push({ type: "click", callbackId: record.tree.children[1].callbacks.onClick });
  for (let i = 0; !clicks && i < 50; i++) await delay(10);
  assert.equal(clicks, 1); await session.flush(); assert.equal(record.tree.children[0].props.text, "count=1");
  const before = renders; api.batch(() => { counter.value = 2; counter.value = 3; }); await session.flush();
  assert.equal(renders, before + 1); assert.equal(record.tree.children[0].props.text, "count=3");
  reference.current.set({ text: "edited" }); await session.flush();
  await reference.current.focus(); assert(calls.some(value => value.method === "command"));
  const circular = api.Column(); circular.append(circular);
  await assert.rejects(api.mount(circular), /repeated/);
  await session.close(); assert.equal(api.sessions.length, 0); assert.equal(reference.current, null);
  const after = calls.length; counter.value = 9; await delay(40); assert.equal(calls.length, after);
}
(async () => {
  await exercise(createCompose(host));
  const memoApi=createCompose(host),outer=memoApi.state(0),inner=memoApi.state(0);let builds=0;
  const memoSession=await memoApi.mount(()=>memoApi.Column({},memoApi.Text({text:String(outer.value)}),
    memoApi.memo(()=>{builds++;return memoApi.Text({text:String(inner.value)});},[])));
  outer.value++;await memoSession.flush();assert.equal(builds,1,'unrelated state preserves memo');
  inner.value++;await memoSession.flush();assert.equal(builds,2,'memo reads retain reactive subscriptions');
  await memoSession.close();
  const floating=await memoApi.floaty(memoApi.Text({text:'floating'}));
  assert.equal(floating.session,floating);assert.equal(typeof floating.setPosition,'function');await floating.close();
  await assert.rejects(memoApi.mount(memoApi.Text({fontSize:NaN})),/finite/);
  await assert.rejects(memoApi.mount(memoApi.Text({text:()=>"invalid"})),/JSON values/);
  const layouts = createUi(host), state = layouts.state({ text: "before" });
  const window = await layouts.showLayout({ type: "Text", id: "text", text: layouts.bind(state, "text") });
  state.set("text", "after"); await delay(25);
  assert(calls.some(value => value.method === "batchUpdate" && value.args[1][0].text === "after"));
  await window.close(); assert.equal(window.closed, true);
  const constructor = section("  function __autojs6_worker_constructor(", "  // The worker keeps synchronous metadata");
  const modules = section("  function __autojs6_worker_host_modules(", "  async function __autojs6_worker_invoke(");
  const preload = section("  function __autojs6_worker_preload(", "  function __autojs6_limited_worker_threads(");
  const directory = path.resolve(__dirname, "../../../build/ui-compose-tests"); fs.mkdirSync(directory, { recursive: true });
  const preloadPath = path.join(directory, "preload.cjs");
  fs.writeFileSync(preloadPath, `(${preload})(require,${constructor},()=>{},${modules},undefined,${ui},${compose});`);
  const config = { preloadPath, entryUrl: pathToFileURL(path.join(directory, "entry.cjs")).href,
    gatedBuiltins: [], moduleSources: {}, typeScriptSources: {}, resourceLimits: {}, hostModuleMethods: { ui: Object.keys(layouts), compose: Object.keys(createCompose(host)) } };
  const Worker = new Function("return (" + constructor + ")")()(require, () => config, host);
  const body = `const session=await compose.mount(compose.Text({text:'worker'})); parentPort.postMessage({id:session.id, methods:typeof requireUi.state});`;
  for (const esm of [false, true]) {
    const code = esm ? `import compose,{sessions,Text} from 'compose'; if(!Array.isArray(sessions)||typeof Text!=='function')throw Error('named Compose exports'); import requireUi from 'ui'; import {parentPort} from 'node:worker_threads'; ${body}`
      : `const compose=require('compose'),requireUi=require('ui'),{parentPort}=require('worker_threads'); (async()=>{${body}})();`;
    const worker = new Worker(esm ? new URL("data:text/javascript," + encodeURIComponent(code)) : code, esm ? {} : { eval: true });
    const result = await new Promise((resolve, reject) => { worker.once("message", resolve); worker.once("error", reject); });
    assert.equal(result.methods, "function"); assert(live.has(result.id));
    await worker.terminate(); for (let i = 0; live.has(result.id) && i < 50; i++) await delay(10);
    assert(!live.has(result.id), "worker exit releases its Compose window");
  }
  let pendingWindow;
  const LateWorker=new Function("return ("+constructor+")")()(require,()=>config,async(module,method,args)=>{
    const value=await host(module,method,args);
    if(method==='mount'||method==='showLayout'){pendingWindow=value.id;await delay(150);}
    return value;
  });
  const late=new LateWorker("require('compose').mount(require('compose').Text({text:'late'}));",{eval:true});
  for(let i=0;!pendingWindow&&i<100;i++)await delay(5);
  assert(pendingWindow,'creation request reached host');await late.terminate();
  for(let i=0;live.has(pendingWindow)&&i<100;i++)await delay(5);
  assert(!live.has(pendingWindow),'late reply after Worker exit releases its window');
  pendingWindow=undefined;
  const aborting=new LateWorker("const {parentPort}=require('node:worker_threads');const c=new AbortController();require('ui').showLayout({type:'Text',text:'abort'},{signal:c.signal}).catch(()=>parentPort.postMessage('aborted'));setTimeout(()=>c.abort(),50);setInterval(()=>{},1000);",{eval:true});
  try {
    await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Missing abort reply')),3000);aborting.once('message',value=>{clearTimeout(timer);resolve(value);});aborting.once('error',error=>{clearTimeout(timer);reject(error);});});
    assert(pendingWindow,'aborted creation reached host');
    for(let i=0;live.has(pendingWindow)&&i<100;i++)await delay(5);
    assert(!live.has(pendingWindow),'aborted request releases its late window while Worker remains alive');
  } finally {await aborting.terminate();}
  assert.equal(live.size, 0);
  console.log("UI bindings and Compose state/callbacks/refs/batches, CJS/ESM Workers and termination cleanup passed");
})().catch(error => { console.error(error); process.exitCode = 1; });

const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const {pathToFileURL} = require('node:url');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
function part(a, z) { const start = source.indexOf(a), end = source.indexOf(z, start); assert(start >= 0 && end > start); return source.slice(start, end); }
const constructor = part('  function __autojs6_worker_constructor(', '  // The worker keeps synchronous metadata');
const modules = part('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(');
const preload = part('  function __autojs6_worker_preload(', '  function __autojs6_limited_worker_threads(');
const facade = part('  // Shared AI facade.', '  // End shared AI facade.');
const factory = new Function(facade + ';return __autojs6_create_ai;')();
const createWorker = new Function('return (' + constructor + ')')();
const directory = path.resolve(__dirname, '../../../build/worker-ai-agent-tests'); fs.mkdirSync(directory, {recursive: true});
const preloadPath = path.join(directory, 'preload.cjs');
fs.writeFileSync(preloadPath, facade + '\n(' + preload + ')(require,' + constructor + ',()=>{},' + modules + ',' + Array(19).fill('undefined').join(',') + ',__autojs6_create_ai);');
const config = {preloadPath, entryUrl: pathToFileURL(path.join(directory, 'entry.cjs')).href, gatedBuiltins: [], moduleSources: {}, typeScriptSources: {},
  resourceLimits: {}, hostModuleMethods: {ai: Object.keys(factory(() => {}))}};
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

test('Agent CJS, ESM and nested Worker transport cleanup preserves the parent client and registered context', async () => {
  const owners = new Map(); let next = 0;
  const backend = async (module, method, args) => {
    assert.equal(module, 'aiAgent');
    if (method === 'context') return {runId: 'registered', parameters: {value: 7}, presetName: null};
    if (method === 'result') { assert.deepEqual(args, [{value: 14}]); return true; }
    if (method === 'open') { const id = 'owner-' + ++next; owners.set(id, []); return {id}; }
    if (method === 'close') return owners.delete(args[0]);
    if (method === 'ack' || method === 'cancel' || method === 'release') return true;
    if (method === 'poll') return owners.get(args[0])?.splice(0) ?? [];
    if (method === 'start') {
      const id = 'task-' + ++next;
      const value = args[1] === 'status' ? {state: 'attached'} : {handle: 'handle-' + next, id: 'run-' + next, goal: args[2][0],
        state: 'queued', startedAt: 1, detached: args[2][2].detached ?? false, interaction: 'script'};
      owners.get(args[0]).push({id, data: Buffer.from(JSON.stringify({type: 'resolve', value})).toString('base64'), last: true});
      return {id};
    }
    throw Error(method);
  };
  const parent = factory(backend).agent;
  await parent.status(); const parentOwner = [...owners.keys()][0];
  const Worker = createWorker(require, () => config, backend);
  const body = "const context=await ai.agent.context();const reported=await ai.agent.result({value:context.parameters.value*2});const run=await ai.agent.run('owned',{interaction:'script'});parentPort.postMessage({reported,id:run.id,runId:context.runId});setInterval(()=>{},1000);";
  const cjs = "const {parentPort}=require('node:worker_threads'),ai=require('ai');(async()=>{" + body + '})();';
  const esm = "import {parentPort} from 'node:worker_threads';import ai from 'autojs6:ai';" + body;
  const nested = "const {parentPort,Worker}=require('node:worker_threads');const child=new Worker(" + JSON.stringify(cjs) + ",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
  for (const [entry, options] of [[cjs, {eval: true}], [new URL('data:text/javascript,' + encodeURIComponent(esm)), {}], [nested, {eval: true}]]) {
    const worker = new Worker(entry, {execArgv: [], ...options});
    try {
      const result = await new Promise((resolve, reject) => { worker.once('message', resolve); worker.once('error', reject); });
      assert(result.reported); assert.equal(result.runId, 'registered'); assert.equal(owners.size, 2);
    } finally { await worker.terminate(); }
    for (let n = 0; n < 100 && owners.size !== 1; n++) await delay(10);
    assert.deepEqual([...owners.keys()], [parentOwner]); assert.equal((await parent.status()).state, 'attached');
  }
  await parent.close(); assert.equal(owners.size, 0);
});

test('A late Agent client opening is closed even after its Worker has exited', async () => {
  let opened, release, closed = 0;
  const started = new Promise(resolve => { opened = resolve; }), gate = new Promise(resolve => { release = resolve; });
  const Worker = createWorker(require, () => config, async (module, method, args) => {
    assert.equal(module, 'aiAgent');
    if (method === 'open') { opened(); await gate; return {id: 'late'}; }
    if (method === 'close') { assert.equal(args[0], 'late'); closed++; return true; }
    throw Error(method);
  });
  const worker = new Worker("require('ai').agent.run('late');setInterval(()=>{},1000);", {eval: true, execArgv: []});
  await started; await worker.terminate(); release();
  for (let n = 0; n < 100 && closed === 0; n++) await delay(10);
  assert.equal(closed, 1);
});

test('Worker exit closes its pending Agent application authorization window', async () => {
  let opened, release, token, closed = 0;
  const pending = new Promise(resolve => { opened = resolve; });
  const Worker = createWorker(require, () => config, async (_, method, args) => {
    if (method === 'hasAccess') return false;
    if (method === 'requestAccess') { token = args[0]; opened(); return await new Promise(resolve => { release = resolve; }); }
    assert.equal(method, 'close'); assert.equal(args[0], token); closed++; release(false); return true;
  });
  const worker = new Worker("require('ai').agent.requestAccess();setInterval(()=>{},1000);", {eval: true, execArgv: []});
  await pending; await worker.terminate();
  for (let n = 0; n < 100 && closed === 0; n++) await delay(10);
  assert.equal(closed, 1);
});

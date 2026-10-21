const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const {pathToFileURL} = require('node:url');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
function part(a, z) { const start = source.indexOf(a), end = source.indexOf(z, start); assert(start >= 0 && end > start); return source.slice(start, end); }
const constructor = part('  function __autojs6_worker_constructor(', '  // The worker keeps synchronous metadata');
const modules = part('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(');
const preload = part('  function __autojs6_worker_preload(', '  function __autojs6_limited_worker_threads(');
const facade = part('  // Shared flow core.', '  // End shared flow core.');
const createWorker = new Function('return (' + constructor + ')')();
const directory = path.resolve(__dirname, '../../../build/worker-flow-tests'); fs.mkdirSync(directory, {recursive: true});
const preloadPath = path.join(directory, 'preload.cjs');
fs.writeFileSync(preloadPath, facade + '\n(' + preload + ')(require,' + constructor + ',()=>{},' + modules + ',' + Array(18).fill('undefined').join(',') + ',__autojs6_create_flow);');

test('CJS, ESM and nested Workers run isolated Flow chains and stop querying on termination', async () => {
  const config = {preloadPath, entryUrl: pathToFileURL(path.join(directory, 'entry.cjs')).href, gatedBuiltins: [], moduleSources: {}, typeScriptSources: {}, resourceLimits: {}, hostModuleMethods: {flow: ['wait', 'of', 'sleep']}};
  let queries = 0;
  const Worker = createWorker(require, () => config, async (module, method) => {
    assert.equal(module, 'accessibility'); assert.equal(method, 'flowProbe'); queries++;
    return {available: true, value: null};
  });
  const body = "flow.defaults({interval:10,timeout:Infinity});const value=await flow.of(3).sleep(1).map(x=>x*2);flow.wait('absent');parentPort.postMessage({value,workers:flow.workers});";
  const cjs = "const {parentPort}=require('node:worker_threads'),flow=require('flow');(async()=>{" + body + '})();';
  const esm = "import {parentPort} from 'node:worker_threads';import flow from 'autojs6:flow';" + body;
  const nested = "const {parentPort,Worker}=require('node:worker_threads');const child=new Worker(" + JSON.stringify(cjs) + ",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
  for (const [entry, options] of [[cjs, {eval:true}], [new URL('data:text/javascript,' + encodeURIComponent(esm)), {}], [nested, {eval:true}]]) {
    const worker = new Worker(entry, {execArgv:[], ...options});
    try {
      const result = await new Promise((resolve, reject) => { worker.once('message', resolve); worker.once('error', reject); });
      assert.deepEqual(result, {value:6, workers:0}); await new Promise(resolve => setTimeout(resolve, 50));
    } finally { await worker.terminate(); }
    await new Promise(resolve => setTimeout(resolve, 30)); const stopped = queries;
    await new Promise(resolve => setTimeout(resolve, 40)); assert.equal(queries, stopped);
  }
  assert(queries >= 3);
});

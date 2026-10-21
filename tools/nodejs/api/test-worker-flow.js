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

test('CJS, ESM and nested Worker termination close only their native Flow clients', async () => {
  const config = {preloadPath, entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href, gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{flow:['waitForToast','of']}};
  const clients=new Map();let next=0;
  const Worker=createWorker(require,()=>config,async(module,method,args)=>{
    assert.equal(module,'flow');
    if(method==='open'){const id=String(++next);clients.set(id,[]);return {id};}
    if(method==='close'){clients.delete(args[0]);return true;}
    if(method==='start'){clients.get(args[0]).push({id:args[1],data:Buffer.from(JSON.stringify({type:'ready',value:null})).toString('base64'),last:true});return true;}
    if(method==='poll')return clients.get(args[0])?.splice(0)??[];
    if(method==='cancel')return true;
    throw Error(method);
  });
  const body="const task=flow.waitForToast('pending',Infinity);await task.ready;parentPort.postMessage('ready');";
  const cjs="const {parentPort}=require('node:worker_threads'),flow=require('flow');(async()=>{"+body+'})();';
  const esm="import {parentPort} from 'node:worker_threads';import flow from 'autojs6:flow';"+body;
  const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));";
  for(const [entry,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const worker=new Worker(entry,{execArgv:[],...opts});
    try{assert.equal(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}), 'ready');assert.equal(clients.size,1);}
    finally{await worker.terminate();}
    await new Promise(resolve=>setTimeout(resolve,50));assert.equal(clients.size,0);
  }
});

test('A late native Flow open reply is closed after its Worker exits',async()=>{
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{flow:['waitForToast']}};
  let opened,release,closed=0;const started=new Promise(resolve=>opened=resolve),gate=new Promise(resolve=>release=resolve);
  const Worker=createWorker(require,()=>config,async(module,method,args)=>{
    assert.equal(module,'flow');
    if(method==='open'){opened();await gate;return {id:'late'};}
    if(method==='close'){assert.equal(args[0],'late');closed++;return true;}
    throw Error('Unexpected '+method);
  });
  const worker=new Worker("require('flow').waitForToast('pending',Infinity);",{eval:true,execArgv:[]});
  await started;await worker.terminate();release();await new Promise(resolve=>setTimeout(resolve,50));assert.equal(closed,1);
});

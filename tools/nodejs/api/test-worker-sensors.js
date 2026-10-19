const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const sensors=part('  function __autojs6_create_sensors(','  // Main-thread sensors facade;');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-sensors-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},undefined,undefined,undefined,undefined,${sensors});`);

function fixture(openHook) {
 const calls=[],live=new Map();let id=0;
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{sensors:['policy','getAvailableSensors','once','subscribe']}};
 const Worker=createWorker(require,()=>config,async(module,method,args,options)=>{
  calls.push({module,method,args,options});
  if(method==='subscribe'){if(openHook)await openHook();const handle='owned-'+(++id);live.set(handle,args[0]);return {id:handle,type:args[0].type};}
  if(method==='unsubscribe')return live.delete(args[0].id);
  if(method==='drainEvents')return [{type:'event',event:{type:'accelerometer',values:[1,2,3]}}];
  return [];
 });return {Worker,calls,live};
}
async function cleared(live){for(let n=0;n<100&&live.size;n++)await new Promise(r=>setTimeout(r,10));assert.equal(live.size,0);}
function run(Worker,code,options={eval:true}) {return new Promise((resolve,reject)=>{
 const worker=new Worker(code,{execArgv:[],...options});worker.on('message',async value=>{await worker.terminate();resolve(value);});worker.on('error',reject);
});}
test('CJS, ESM and nested Workers receive real events and release subscriptions on termination',async()=>{
 const {Worker,calls,live}=fixture();
 const body=`(async()=>{const sample=await sensors.once('accelerometer',{drainIntervalMs:25});const sub=sensors.subscribe('accelerometer');await sub.ready;parentPort.postMessage({sample:sample.values,max:sensors.policy.maxSubscriptions});})().catch(e=>{throw e});`;
 const cjs=`const {parentPort}=require('node:worker_threads'),sensors=require('autojs6:sensors');${body}`;
 assert.deepEqual(await run(Worker,cjs),{sample:[1,2,3],max:4});await cleared(live);
 const esm=`import {parentPort} from 'node:worker_threads';import sensors,{once,policy} from 'sensors';if(once!==sensors.once||policy.maxSubscriptions!==4)throw Error('ESM exports');${body}`;
 assert.deepEqual(await run(Worker,new URL('data:text/javascript,'+encodeURIComponent(esm)),{}),{sample:[1,2,3],max:4});await cleared(live);
 const nested=`const {Worker,parentPort}=require('node:worker_threads');const child=new Worker(${JSON.stringify(cjs)},{eval:true});child.on('message',x=>parentPort.postMessage(x));child.on('error',e=>{throw e});`;
 assert.deepEqual(await run(Worker,nested),{sample:[1,2,3],max:4});await cleared(live);
 assert(calls.filter(c=>c.method==='subscribe').every(c=>c.options.events===false));
});
test('terminated Worker releases a subscription delivered after exit',async()=>{
 let received,finish;const arrived=new Promise(r=>received=r),delayed=new Promise(r=>finish=r);
 const {Worker,calls,live}=fixture(async()=>{received();await delayed;});
 const worker=new Worker(`require('sensors').subscribe('light').ready.catch(()=>{});`,{eval:true,execArgv:[]});
 await arrived;await worker.terminate();finish();
 for(let n=0;n<100&&!calls.some(c=>c.method==='unsubscribe');n++)await new Promise(r=>setTimeout(r,10));
 assert(calls.some(c=>c.method==='unsubscribe'));await cleared(live);
});

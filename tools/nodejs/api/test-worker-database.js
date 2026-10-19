const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const database=part('  function __autojs6_create_database(','  // Main-thread database facade;');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-database-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},undefined,undefined,undefined,${database});`);
function fixture(openHook) {
 const calls=[],live=new Set();let id=0;
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{database:['open'],sqlite:['open']}};
 const Worker=createWorker(require,()=>config,async(module,method,args,options)=>{
  calls.push({module,method,args,options});
  if(method==='open'){if(openHook)await openHook();const handle='owned-'+(++id);live.add(handle);return {id:handle,name:args[0].name};}
  if(method==='close')return live.delete(args[0]);
  if(method==='get')return {value:42};
  return null;
 });return {Worker,calls,live};
}
function run(Worker,code,options={eval:true}) {return new Promise((resolve,reject)=>{
 let result;const worker=new Worker(code,{execArgv:[],...options});worker.on('message',value=>{result=value});worker.on('error',reject);worker.on('exit',code=>code===0?resolve(result):reject(Error('Worker exit '+code)));
});}
async function cleared(live){for(let n=0;n<100&&live.size;n++)await new Promise(r=>setTimeout(r,10));assert.equal(live.size,0);}
test('CJS, ESM and nested Workers load both database facades and clean unclosed handles',async()=>{
 const {Worker,calls,live}=fixture();
 const body=`(async()=>{const db=await database.open({name:'owned',timeoutMs:73});const alias=await sqlite({name:'alias'});const value=await db.get('SELECT 42 AS value');await alias.close();parentPort.postMessage({value:value.value,native:typeof require('node:sqlite').DatabaseSync});})().catch(e=>{throw e});`;
 const cjs=`const {parentPort}=require('node:worker_threads'),database=require('autojs6:database'),sqlite=require('sqlite');${body}`;
 assert.deepEqual(await run(Worker,cjs),{value:42,native:'function'});await cleared(live);
 const esm=`import {parentPort} from 'node:worker_threads';import database from 'database';import sqlite from 'autojs6:sqlite';import {createRequire} from 'node:module';const require=createRequire(${JSON.stringify(pathToFileURL(path.join(directory,'entry.mjs')).href)});${body}`;
 assert.deepEqual(await run(Worker,new URL('data:text/javascript,'+encodeURIComponent(esm)),{}),{value:42,native:'function'});await cleared(live);
 const nested=`const {Worker,parentPort}=require('node:worker_threads');const child=new Worker(${JSON.stringify(cjs)},{eval:true});child.on('message',x=>parentPort.postMessage(x));child.on('error',e=>{throw e});`;
 assert.deepEqual(await run(Worker,nested),{value:42,native:'function'});await cleared(live);
 assert(calls.filter(c=>c.method==='open'&&c.args[0].name==='owned').every(c=>c.options.timeoutMs===73));
});
test('terminated Worker closes an open result delivered after its exit',async()=>{
 let received,finish;const arrived=new Promise(r=>received=r),delayed=new Promise(r=>finish=r);
 const {Worker,calls,live}=fixture(async()=>{received();await delayed;});
 const worker=new Worker(`require('database').open('late').catch(()=>{});`,{eval:true,execArgv:[]});
 await arrived;await worker.terminate();finish();
 for(let n=0;n<100&&!calls.some(c=>c.method==='close');n++)await new Promise(r=>setTimeout(r,10));
 assert(calls.some(c=>c.method==='close'));await cleared(live);
});

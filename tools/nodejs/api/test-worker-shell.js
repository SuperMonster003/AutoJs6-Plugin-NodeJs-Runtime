const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const shell=part('  function __autojs6_create_shell(','  // Main-thread shell facade;');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-shell-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},undefined,undefined,undefined,undefined,undefined,${shell});`);


function fixture(hook){const calls=[],live=new Map();let n=0;
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{shell:['exec','spawn','open','checkAccess','getAccess'],shizuku:['exec','spawn','open','checkAccess','getAccess']}};
 const Worker=createWorker(require,()=>config,async(module,method,args,options)=>{
  calls.push({module,method,args,options});
  if(['spawn','open'].includes(method)){if(hook)await hook();const id='process-'+ ++n;live.set(id,args[0]==='echo'?[{type:'stdout',data:Buffer.from('ok').toString('base64')},{type:'exit',code:0,timedOut:false}]:[]);return {id,pid:n,mode:args[1]?.shizuku?'adb':'sh'};}
  if(method==='close'){live.delete(args[0]);return {code:137,wasAlive:true};}
  if(method==='drain'){const events=live.get(args[0])||[];if(events.length)live.delete(args[0]);return events;}
  return null;
 });return {Worker,calls,live};}
function run(Worker,source,options={eval:true}){return new Promise((resolve,reject)=>{const w=new Worker(source,{execArgv:[],...options});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});}
async function cleared(live){for(let n=0;n<100&&live.size;n++)await new Promise(r=>setTimeout(r,10));assert.equal(live.size,0);}
test('CJS, ESM and nested Workers stream through shell/shizuku and close owned processes on exit',async()=>{
 const {Worker,calls,live}=fixture();const body=`(async()=>{const r=await shizuku.exec('echo',{cwd:'/system',env:{OWNED:'yes'}});const p=shell.spawn('hold');await p.ready;parentPort.postMessage({stdout:r.stdout,code:r.code,access:shell.checkAccess({shizuku:true}).shizuku});})().catch(e=>{throw e});`;
 const cjs=`const {parentPort}=require('node:worker_threads'),shell=require('autojs6:shell'),shizuku=require('shizuku');${body}`;
 const esm=`import {parentPort} from 'node:worker_threads';import shell from 'shell';import shizuku from 'autojs6:shizuku';${body}`;
 for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',x=>parentPort.postMessage(x));w.on('error',e=>{throw e});`,{eval:true}]]){
  assert.deepEqual(await run(Worker,code,opts),{stdout:'ok',code:0,access:true});await cleared(live);
 }
 assert(calls.filter(c=>c.method==='spawn'&&c.args[0]==='echo').every(c=>c.args[1].cwd==='/system'&&c.options.permissions.includes('shell.shizuku')));
});
test('Worker termination cleans a shell creation reply that arrives after exit',async()=>{
 let arrived,finish;const received=new Promise(r=>arrived=r),delayed=new Promise(r=>finish=r);const {Worker,calls,live}=fixture(async()=>{arrived();await delayed;});
 const w=new Worker(`require('shell').spawn('hold').ready.catch(()=>{});`,{eval:true,execArgv:[]});await received;await w.terminate();finish();
 for(let n=0;n<100&&!calls.some(c=>c.method==='close');n++)await new Promise(r=>setTimeout(r,10));assert(calls.some(c=>c.method==='close'));await cleared(live);
});

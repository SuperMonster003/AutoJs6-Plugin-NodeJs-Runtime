const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const facades=part('  // Shared Java object facade','  // Main Java facade transport.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-java-interop-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,facades+`\n(${preload})(require,${constructor},()=>{},${modules},${Array(13).fill('undefined').join(',')},__autojs6_create_java);`);
function fixture(hook){
  const handles=new Set(['parent']),calls=[];let next=0;
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{java:['new','call','release','type']}};
  const Worker=createWorker(require,()=>config,async(module,method,args)=>{
    calls.push({module,method,args});if(hook)await hook(module,method);
    if(method==='new'){const handle='owned-'+ ++next;handles.add(handle);return {__autojs6JavaHandle:handle,className:'java.lang.StringBuilder'};}
    if(method==='release')return handles.delete(args[0].handle);
    if(method==='call')return {__autojs6JavaLong:'9223372036854775807'};
  });return {Worker,handles,calls};
}
async function waitFor(check){for(let i=0;i<100&&!check();i++)await new Promise(r=>setTimeout(r,10));assert(check());}
test('CJS, ESM and nested Worker Java facades return bigints and release owned handles on exit',async()=>{
  const f=fixture(),body=`const own=await java.new('java.lang.StringBuilder');const exact=await own.call('longValue');parentPort.postMessage({own,exact});setInterval(()=>{},1000);`;
  const cjs=`const {parentPort}=require('node:worker_threads'),java=require('java');(async()=>{${body}})().catch(e=>{throw e;});`;
  const esm=`import {parentPort} from 'node:worker_threads';import java from 'autojs6:java';${body}`;
  const nested=`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});`;
  for(const [entry,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]) {
    const value=await new Promise((resolve,reject)=>{const w=new f.Worker(entry,{execArgv:[],...opts});w.on('message',async v=>{await w.terminate();resolve(v);});w.on('error',reject);});
    assert.equal(value.exact,9223372036854775807n);assert.equal(value.own.className,'java.lang.StringBuilder');await waitFor(()=>f.handles.size===1);assert(f.handles.has('parent'));
  }
});
test('Worker exit releases an object returned after termination',async()=>{
  let arrive,resume;const ready=new Promise(r=>arrive=r),gate=new Promise(r=>resume=r);
  const f=fixture(async(_,method)=>{if(method==='new'){arrive();await gate;}});
  const w=new f.Worker("require('java').new('T').catch(()=>{});",{eval:true,execArgv:[]});await ready;await w.terminate();resume();
  await waitFor(()=>f.calls.some(x=>x.method==='release'));assert.deepEqual([...f.handles],['parent']);
});

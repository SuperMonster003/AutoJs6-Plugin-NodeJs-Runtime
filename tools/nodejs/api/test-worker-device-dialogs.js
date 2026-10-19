const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const facades=part('  // Shared device and dialog facades','  // Main-thread device facade.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-device-dialog-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,facades+`\n(${preload})(require,${constructor},()=>{},${modules},undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,__autojs6_create_device,__autojs6_create_dialogs);`);
function fixture(hook){
  const locks=new Set(['parent']),windows=new Map([['parent','parent']]),calls=[];let next=0;
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{device:['width','getWidth','keepScreenOn','cancelKeepingAwake','getMusicVolume'],dialogs:['progress','input']},hostModuleConstants:{device:{width:360}}};
  const Worker=createWorker(require,()=>config,async(module,method,args,options)=>{
    calls.push({module,method,args,options});if(hook)await hook(module,method);
    if(module==='device'){
      if(method==='info')return {screen:{width:720}};
      if(method==='keepScreenOn')locks.add(args[1]);
      if(method==='closeScope'||method==='cancelKeepingAwake')locks.delete(args[0]);
    }
    if(module==='media')return 0;
    if(module==='dialogs'){
      if(method==='progressShow'){const id='dialog-'+ ++next;windows.set(id,args[0].__scope);return {id,shown:true,max:100,value:0};}
      if(method==='closeScope')for(const [key,scope] of windows)if(scope===args[0])windows.delete(key);
      if(method==='progressDismiss')return windows.delete(args[0]);
    }
  });return {Worker,locks,windows,calls};
}
async function clean(f){for(let i=0;i<100&&(f.locks.size>1||f.windows.size>1);i++)await new Promise(r=>setTimeout(r,10));assert.deepEqual([...f.locks],['parent']);assert.deepEqual([...f.windows.keys()],['parent']);}
test('CJS, ESM and nested Workers expose device/dialog objects and close only their resources',async()=>{
  const f=fixture();
  const body=`await device.keepScreenOn(10000);await dialogs.progress.show({title:'Owned'});parentPort.postMessage({width:await device.getWidth(),startup:device.width,volume:await device.getMusicVolume()});setInterval(()=>{},1000);`;
  const cjs=`const {parentPort}=require('node:worker_threads'),device=require('device'),dialogs=require('autojs6:dialogs');(async()=>{${body}})().catch(e=>{throw e;});`;
  const esm=`import {parentPort} from 'node:worker_threads';import device from 'autojs6:device';import {progress} from 'dialogs';const dialogs={progress};${body}`;
  const nested=`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});`;
  for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const result=await new Promise((resolve,reject)=>{const w=new f.Worker(code,{execArgv:[],...opts});w.on('message',async v=>{await w.terminate();resolve(v);});w.on('error',reject);});
    assert.deepEqual(result,{width:720,startup:360,volume:0});await clean(f);
  }
});
test('Worker exit closes a wake lock whose creation reply arrives late',async()=>{
  let arrived,release;const ready=new Promise(r=>arrived=r),gate=new Promise(r=>release=r);
  const f=fixture(async(module,method)=>{if(module==='device'&&method==='keepScreenOn'){arrived();await gate;}});
  const w=new f.Worker("require('device').keepScreenOn(10000).catch(()=>{});",{eval:true,execArgv:[]});await ready;await w.terminate();release();
  for(let i=0;i<100&&f.calls.filter(c=>c.method==='closeScope').length<2;i++)await new Promise(r=>setTimeout(r,10));
  assert(f.calls.some(c=>c.method==='closeScope'));await clean(f);
});

const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const events=part('  function __autojs6_create_host_events(','  function __autojs6_create_notifications(');
const notifications=part('  function __autojs6_create_notifications(','  // Main-thread event and notification facades.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-events-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},undefined,undefined,undefined,undefined,undefined,undefined,${events},${notifications});`);
function fixture(hook){
  const live=new Map([['parent',[]]]),posted=new Map([['parent:7',true]]),calls=[];let next=0;
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{events:['observeBroadcasts','observeKey','observeToast','observeNotification','on','once','off','close'],notifications:['notify','cancel','cancelAll','getPermissionStatus','openSettings','openNotificationSettings']}};
  const Worker=createWorker(require,()=>config,async(module,method,args,options)=>{
    calls.push({module,method,args,options});if(hook)await hook(module,method);
    if(module==='events'){
      if(method.startsWith('observe')){const id='observer-'+ ++next;live.set(id,[{type:'battery_changed',level:62,time:1}]);return {id,source:'broadcast'};}
      if(method==='close')return live.delete(args[0]);
      if(method==='drainEvents')return live.get(args[0])?.splice(0,32)||[];
    }else if(module==='notifications'){
      if(method==='notify'){posted.set(args[1]+':'+args[0].id,true);return args[0].id;}
      if(method==='cancel')posted.delete(args[1]+':'+args[0]);
      if(method==='cancelAll')for(const key of posted.keys())if(key.startsWith(args[0]+':'))posted.delete(key);
    }
  });return {Worker,live,posted,calls};
}
async function clean(f){for(let i=0;i<100&&(f.live.size>1||f.posted.size>1);i++)await new Promise(r=>setTimeout(r,10));assert.deepEqual([...f.live.keys()],['parent']);assert.deepEqual([...f.posted.keys()],['parent:7']);}
test('CJS, ESM and nested Workers retain native events and release only their own observers/notifications',async()=>{
  const f=fixture();
  const body=`const sub=await events.observeBroadcasts();const rows=await sub.drainEvents();await notifications.notify({id:7,title:'owned'});parentPort.postMessage({level:rows[0].level,native:typeof EventEmitter==='function'});setInterval(()=>{},1000);`;
  const cjs=`const {parentPort}=require('node:worker_threads'),{EventEmitter}=require('events'),events=require('autojs6:events'),notifications=require('autojs6:notifications');(async()=>{${body}})().catch(e=>{throw e;});`;
  const esm=`import {parentPort} from 'node:worker_threads';import {EventEmitter} from 'node:events';import events from 'autojs6:events';import notifications from 'notifications';${body}`;
  const nested=`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});`;
  for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const result=await new Promise((resolve,reject)=>{const w=new f.Worker(code,{execArgv:[],...opts});w.on('message',async v=>{await w.terminate();resolve(v);});w.on('error',reject);});
    assert.deepEqual(result,{level:62,native:true});await clean(f);
  }
});
for(const module of ['events','notifications'])test('Worker exit cleans a late '+module+' creation reply',async()=>{
  let arrived,release;const ready=new Promise(r=>arrived=r),gate=new Promise(r=>release=r);
  const f=fixture(async(m,method)=>{if(m===module&&(method==='notify'||method==='observeBroadcasts')){arrived();await gate;}});
  const code=module==='events'?"require('autojs6:events').observeBroadcasts()":"require('notifications').notify({id:7,title:'late'})";
  const w=new f.Worker(code+'.catch(()=>{});',{eval:true,execArgv:[]});await ready;await w.terminate();release();
  for(let i=0;i<100&&!f.calls.some(c=>c.method===(module==='events'?'close':'cancel'));i++)await new Promise(r=>setTimeout(r,10));
  assert(f.calls.some(c=>c.method===(module==='events'?'close':'cancel')));await clean(f);
});

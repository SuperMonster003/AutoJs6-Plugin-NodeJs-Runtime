const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const facades=part('  // Shared native Android helpers, colors and calendar task facades.','  // Main-thread native Android and calendar modules.');
// colors is the shared JS tools core's (api-coverage P3.2b).
const core=part('  // Shared JS tools core.','  // End shared JS tools core.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-android-tasks-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,facades+core+`\n(${preload})(require,${constructor},()=>{},${modules},${Array(10).fill('undefined').join(',')},__autojs6_create_android,__autojs6_create_tasks,undefined,${Array(7).fill('undefined').join(',')},__autojs6_create_js_tools);`);
function fixture(hook){
  const objects=new Map([['parent',{}]]),tasks=new Map(),calls=[];let next=0;
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{util:[],android:['createPaint','createColorStateList','getThemeColor'],colors:['toInt','createPaint','setPaintColor','themeColor'],tasks:['addCountdownTask','removeTimedTask']}};
  const Worker=createWorker(require,()=>config,async(module,method,args)=>{
    calls.push({module,method,args});if(hook)await hook(module,method);
    if(method==='getThemeColor')return {colorPrimary:1,colorPrimaryDark:2,colorAccent:3};
    if(module==='android'){
      if(method==='createPaint'){const value={id:'paint-'+ ++next,__autojs6AndroidObject:'paint',color:args[0].color};objects.set(value.id,value);return value;}
      if(method==='close')return objects.delete(args[0]);
    }
    if(module==='tasks'){
      if(method==='addTimedTask'){const value={id:++next,kind:'timed',options:args[0]};tasks.set(value.id,value);return value;}
      if(method==='removeTimedTask')return tasks.delete(args[0]);
    }
  });return {Worker,objects,tasks,calls};
}
async function waitFor(check){for(let i=0;i<100&&!check();i++)await new Promise(r=>setTimeout(r,10));assert(check());}
test('CJS, ESM and nested Workers release Paints but keep confirmed persistent tasks',async()=>{
  const f=fixture();
  const body=`const paint=await colors.createPaint({color:'red'});const task=await tasks.addCountdownTask({path:'child.cjs',delayMs:1000});parentPort.postMessage({color:paint.color,id:task.id});setInterval(()=>{},1000);`;
  const cjs=`const {parentPort}=require('node:worker_threads'),colors=require('colors'),tasks=require('autojs6:tasks');(async()=>{${body}})().catch(e=>{throw e;});`;
  const esm=`import {parentPort} from 'node:worker_threads';import colors from 'autojs6:colors';import tasks from 'tasks';${body}`;
  const nested=`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});`;
  for(const [entry,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const result=await new Promise((resolve,reject)=>{const w=new f.Worker(entry,{execArgv:[],...opts});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});
    assert.equal(result.color,-65536);await waitFor(()=>f.objects.size===1);assert(f.objects.has('parent'));assert(f.tasks.has(result.id));
  }
  assert.equal(f.tasks.size,3);
});
for(const module of ['android','tasks'])test('Worker exit removes an unconfirmed late '+module+' creation',async()=>{
  let arrived,release;const ready=new Promise(r=>arrived=r),gate=new Promise(r=>release=r);
  const f=fixture(async(m,method)=>{if(m===module&&['createPaint','addTimedTask'].includes(method)){arrived();await gate;}});
  const code=module==='android'?"require('android').createPaint()":"require('tasks').addCountdownTask({path:'child.cjs',delayMs:1000})";
  const w=new f.Worker(code+'.catch(()=>{});',{eval:true,execArgv:[]});await ready;await w.terminate();release();
  await waitFor(()=>f.calls.some(c=>c.method===(module==='android'?'close':'removeTimedTask')));assert.equal(f.objects.size,1);assert.equal(f.tasks.size,0);
});

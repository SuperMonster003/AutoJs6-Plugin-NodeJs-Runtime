const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a);return source.slice(a,z);}
const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
const facade=part('  // Shared Java object facade','  // Main Java facade transport.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-java-callbacks-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,facade+`\n(${preload})(require,${constructor},()=>{},${modules},${Array(13).fill('undefined').join(',')},__autojs6_create_java);`);
function fixture(){
  const registrations=new Map(),events=new Map(),pending=new Map();let next=0;
  const invoke=async(module,method,args)=>{
    const data=args[0];
    if(method==='proxy'){const handle='proxy-'+ ++next;registrations.set(handle,data);return {__autojs6JavaHandle:handle,className:'owned.Proxy',__autojs6JavaCallbackOwner:data.callback};}
    if(method==='drainCallbacks'){await new Promise(resolve=>setTimeout(resolve,10));const result=events.get(data.client)||[];events.set(data.client,[]);return result;}
    if(method==='callStatic')return 9;
    if(method==='call'){
      const registration=registrations.get(data.handle);if(!registration)throw Error('closed owner');const id='call-'+ ++next;
      const result=new Promise((resolve,reject)=>pending.set(id,{resolve,reject,owner:data.handle}));
      const queue=events.get(registration.client)||[];queue.push({id,callback:registration.callback,method:'call',signature:'call()',key:'*',receiver:{__autojs6JavaHandle:'borrowed',className:'owned.Proxy'},args:[]});events.set(registration.client,queue);return result;
    }
    if(method==='completeCallback'){const entry=pending.get(data.id);if(!entry)return false;pending.delete(data.id);data.error?entry.reject(new Error(data.error)):entry.resolve(data.value);return true;}
    if(method==='release'){
      registrations.delete(data.handle);for(const [id,value]of pending)if(value.owner===data.handle){pending.delete(id);value.reject(new Error('closed owner'));}return true;
    }
    if(method==='closeCallbacks')return true;
    throw Error(method);
  };
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{java:['proxy','call','callStatic','release','closeCallbacks']}};
  return {Worker:createWorker(require,()=>config,invoke),registrations,pending,invoke};
}
async function until(check){for(let i=0;i<100&&!check();i++)await new Promise(resolve=>setTimeout(resolve,10));assert(check());}
test('real CJS, ESM and nested Workers execute async callbacks locally and close their owners',async()=>{
  const f=fixture(),body="const value=await java.proxy('java.util.concurrent.Callable',async()=> 'child:'+await java.callStatic('java.lang.Math','max',[1,9]));parentPort.postMessage({value:await value.call('call'),handle:value});setInterval(()=>{},1000);";
  const cjs="const {parentPort}=require('node:worker_threads'),java=require('java');(async()=>{"+body+"})().catch(e=>{throw e;});";
  const esm="import {parentPort} from 'node:worker_threads';import java from 'autojs6:java';"+body;
  const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
  for(const [entry,options]of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const w=new f.Worker(entry,{execArgv:[],...options});const result=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});assert.equal(result.value,'child:9');
    await w.terminate();await until(()=>f.registrations.size===0);assert.equal(f.pending.size,0);
  }
});
test('terminating the owner releases a callback whose Java caller is still waiting',async()=>{
  const f=fixture(),w=new f.Worker("const {parentPort}=require('node:worker_threads'),java=require('java');(async()=>{const value=await java.proxy('java.util.concurrent.Callable',()=>new Promise(()=>{}));parentPort.postMessage(value);})();",{eval:true,execArgv:[]});
  const handle=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});
  const called=f.invoke('java','call',[{handle:handle.__autojs6JavaHandle}]);const rejected=assert.rejects(called,/closed owner/);
  await until(()=>f.pending.size===1);await w.terminate();await rejected;await until(()=>f.registrations.size===0);
});

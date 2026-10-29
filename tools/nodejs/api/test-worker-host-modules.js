"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {pathToFileURL}=require("node:url");
const source=fs.readFileSync(path.join(__dirname,"../../../app/src/main/cpp/node_bridge_sources.cpp"),"utf8");
function section(a,b) {const start=source.indexOf(a),end=source.indexOf(b,start);assert(start>=0&&end>start);return source.slice(start,end);}
const constructor=section('  function __autojs6_worker_constructor(', '  // The worker keeps synchronous metadata');
const modules=section('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(');
const preload=section('  function __autojs6_worker_preload(', '  function __autojs6_limited_worker_threads(');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-host-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');
fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules});`);
const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},
  hostModuleMethods:{opencc:['convert','s2t'],image:['fromBytes','toBytes','pixel','recycle','getSize','isRecycled'],images:['fromBytes','toBytes','pixel','recycle','getSize','isRecycled']}};
const calls=[];
const hostCall=async(module,method,args)=>{
  calls.push({module,method,args});
  if(method==='convert')return [['bei'],['jing']];
  if(method==='compact')return [['bei','jing']];
  if(module==='pinyin'||module==='pinyin4j')return 'beijing';
  if(module==='screen_metrics')return 360;
};
const extraApis=new Function('__autojs6_call_autojs','__autojs6_invalid_arg_type','process',`
  let __autojs6_limited_pinyin_cache,__autojs6_limited_pinyin4j_cache,__autojs6_limited_screen_metrics_cache,__autojs6_limited_console_cache;
  const __autojs6_has_own=(object,name)=>Object.prototype.hasOwnProperty.call(object,name);
  ${section('  const __autojs6_pinyin_styles =','  // Shared JS tools core.')}
  ${section('  function __autojs6_limited_console()','  let __autojs6_main_module_ref =')}
  return {pinyin:__autojs6_limited_pinyin(),pinyin4j:__autojs6_limited_pinyin4j(),screen_metrics:__autojs6_limited_screen_metrics(),console:__autojs6_limited_console()};
`)(hostCall,message=>new TypeError(message),process);
for(const [name,api] of Object.entries(extraApis)) config.hostModuleMethods[name]=Object.keys(api);
config.hostModuleConstants={pinyin:Object.fromEntries(Object.entries(extraApis.pinyin).filter(([,v])=>typeof v!=='function'))};
config.consoleControls=Object.keys(extraApis.console).filter(name=>extraApis.console[name].__autojs6ConsoleControl);
const handle={id:'test-image',__autojs6ImageHandle:'test-image',width:2,height:1};
const Worker=createWorker(require,()=>config,async(module,method,args)=>{
  const extra=extraApis[module.slice('autojs6:worker-module:'.length)];
  if(extra)return extra[method](...args);
  calls.push({module,method,args});
  if(module.endsWith(':opencc')){assert.equal(typeof args[0],'string');return '漢語';}
  if(method==='fromBytes') {assert.equal(args[0].byteLength,100000);return handle;}
  if(method==='toBytes')return Buffer.from([137,80,78,71]);
  if(method==='pixel')return -65536;
});
function run(code,options={eval:true}) {return new Promise((resolve,reject)=>{
  let result;const worker=new Worker(code,options);worker.on('message',value=>{result=value});worker.on('error',reject);
  worker.on('exit',code=>code===0?resolve(result):reject(Error('Worker exit '+code)));
});}
(async()=>{
  const body=`
    const assert=require('node:assert/strict'),{parentPort}=require('node:worker_threads');
    (async()=>{
      const cc=require('autojs6:opencc'),images=require('images');
      const pinyin=require('autojs6:pinyin'),four=require('pinyin4j'),screen=require('autojs6:screen_metrics');
      assert.equal(pinyin.STYLE_NORMAL,0);assert.equal(pinyin.STYLE_TO3NE,5);assert.equal(pinyin.MODE_PLACE_NAME,2);
      const rows=await pinyin({toString(){return '北京';}},{style:pinyin.STYLE_NORMAL});
      assert.deepEqual(rows,[['bei'],['jing']]);assert.deepEqual(await rows.compact(),[['bei','jing']]);
      assert.equal(await pinyin.simple(Buffer.from('北京')),'beijing');
      assert.equal(await four('北京'),'beijing');
      assert.equal(await screen.cX(0.5),360);
      await assert.rejects(screen.cX(undefined));await assert.rejects(screen.cY(NaN));
      const controls=require('node:console');
      assert.equal(controls,require('console'));assert.equal(controls,require('autojs6:console'));
      assert.equal(controls.log('WORKER_NATIVE_OUTPUT'),undefined);
      assert.equal(await controls.setTitle('worker'),undefined);
      assert.equal(await console.hide(),undefined);
      assert.throws(()=>controls.input());assert.equal(controls.createTask().run(x=>x+1,4),5);
      const chunks=[];const {Writable}=require('node:stream');
      new controls.Console(new Writable({write(chunk,_encoding,done){chunks.push(chunk.toString());done();}})).log('local-stream');
      assert.equal(chunks.join(''),'local-stream\\n');
      const custom=controls.Console(new Writable({write(chunk,_encoding,done){chunks.push(chunk.toString());done();}}));
      custom.printAllStackTrace({stack:'local-stack'});assert.equal(chunks.join(''),'local-stream\\nlocal-stack\\n');
      assert.equal(await cc('汉语','s2t'),'漢語');assert.equal(await cc.s2t('汉语'),'漢語');
      assert.equal(await cc.s2t(Buffer.from('汉语')),'漢語');
      assert.equal(await cc.s2t({toString(){return '汉语'}}),'漢語');
      await assert.rejects(cc.s2t(null),TypeError);
      const img=await images.fromBytes(new Uint8Array(100000));
      assert.deepEqual(images.getSize(img),{width:2,height:1});
      assert.equal(await images.pixel(img,0,0),-65536);
      assert(Buffer.isBuffer(await images.toBytes(img)));
      await images.recycle(img);assert(require('image').isRecycled(img));
      parentPort.postMessage('passed');
    })().catch(error=>{throw error;});`;
  assert.equal(await run(body),'passed');
  const esm=path.join(directory,'entry.mjs');
  fs.writeFileSync(esm,`import cc,{s2t} from 'autojs6:opencc'; import images from 'autojs6:images'; import {parentPort} from 'node:worker_threads';
    import py,{STYLE_NORMAL} from 'autojs6:pinyin';import four from 'autojs6:pinyin4j';import {cX} from 'screen_metrics';import {setTitle} from 'node:console';
    if(STYLE_NORMAL!==0||(await py('北京')).length!==2||await four('北京')!=='beijing'||await cX(0.5)!==360)throw Error('Worker extra exports');await setTitle('esm');
    if(await s2t('汉语')!=='漢語'||typeof images.pixel!=='function'||typeof cc!=='function')throw Error('ESM exports'); parentPort.postMessage('esm');`);
  assert.equal(await run(esm,{}),'esm');
  assert.equal(await run(`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(body)},{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e});`),'passed');
  const npmRoot=path.join(directory,'node_modules/opencc');fs.mkdirSync(npmRoot,{recursive:true});fs.writeFileSync(path.join(npmRoot,'index.js'),'module.exports="npm-opencc";');
  const shadow=path.join(directory,'shadow.cjs');fs.writeFileSync(shadow,`const a=require('node:assert/strict');a.equal(require('opencc'),'npm-opencc');a.equal(typeof require('autojs6:opencc'),'function');require('node:worker_threads').parentPort.postMessage('shadow');`);
  assert.equal(await run(shadow,{}),'shadow');
  assert(calls.some(call=>call.method==='fromBytes'));
  const invokeSource=section('  async function __autojs6_worker_invoke(', '  function __autojs6_worker_preload(');
  let finish, recycled=0;
  const imageApi={copy:()=>new Promise(resolve=>{finish=resolve}),recycle:async()=>{recycled++},
    toBytes:async(_image,options)=>{assert(options.signal instanceof AbortSignal);return Buffer.from([1])}};
  const invoke=new Function('__autojs6_call_autojs','__autojs6_limited_image','__autojs6_limited_opencc','__autojs6_invalid_arg_type',
    'return ('+invokeSource+')')(()=>{throw Error('Unexpected raw call')},()=>imageApi,()=>({}),message=>new TypeError(message));
  const controller=new AbortController();
  const orphan=invoke('autojs6:worker-module:images','copy',[handle],{signal:controller.signal});
  controller.abort();finish(handle);await assert.rejects(orphan,{code:'ABORT_ERR'});assert.equal(recycled,1);
  await invoke('autojs6:worker-module:images','toBytes',[handle,{}],{signal:new AbortController().signal,signalArgumentIndex:1});
  await assert.rejects(invoke('autojs6:worker-module:unknown','copy',[],{}),TypeError);
  assert(calls.some(call=>call.module==='pinyin'&&call.method==='simple'&&call.args[0]==='北京'));
  assert(calls.some(call=>call.module==='console'&&call.method==='setTitle'&&call.args[0]==='worker'));
  console.log('Worker CJS/ESM/nested modules, pinyin constants/compact, console controls/native streams, coordinates, byte cloning and npm precedence passed');
})().catch(error=>{console.error(error);process.exitCode=1;});

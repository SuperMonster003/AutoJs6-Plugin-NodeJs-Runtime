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
const handle={id:'test-image',__autojs6ImageHandle:'test-image',width:2,height:1};
const calls=[];
const Worker=createWorker(require,()=>config,async(module,method,args)=>{
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
  console.log('Worker CJS/ESM/nested modules, byte cloning, Buffer results and npm precedence passed');
})().catch(error=>{console.error(error);process.exitCode=1;});

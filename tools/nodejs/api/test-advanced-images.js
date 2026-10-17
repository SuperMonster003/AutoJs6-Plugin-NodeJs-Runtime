"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const source=fs.readFileSync(path.join(__dirname,"../../../app/src/main/cpp/node_bridge_sources.cpp"),"utf8");
function segment(a,b){const start=source.indexOf(a),end=source.indexOf(b,start);assert(start>=0&&end>start);return source.slice(start,end);}
const helper=segment("  function __autojs6_image_extensions(","  function __autojs6_limited_image(");
const create=new Function("return ("+helper+")")();
const image={id:"image",__autojs6ImageHandle:"image",width:10,height:8};
const feature={id:"features",__autojs6ImageFeaturesHandle:"features",count:24,method:"ORB",scale:1,region:{x:0,y:0,width:10,height:8}};
const mat={id:"mat",__autojs6MatHandle:"mat",width:10,height:8,channels:3,depth:0};
const frame={topLeft:{x:0,y:0},topRight:{x:10,y:0},bottomLeft:{x:0,y:8},bottomRight:{x:10,y:8},matches:20,inliers:18,points:[{x:2,y:2}]};
const calls=[],released=[];let reply=image;
const base={readImage:async path=>({...image,id:path}),fromBytes:async()=>({...image,id:"upload"}),recycle:async value=>{released.push(value.id)},
 getSize:value=>({width:value.width,height:value.height}),findColor:async(...args)=>{calls.push(["findColor",args]);return {x:1,y:1}},
 findImage:async()=>({x:1,y:1}),findMultiColors:async()=>({x:1,y:1}),requestScreenCapture:async()=>true,saveImage:async()=>{}};
const dispatch=async(method,args,options)=>{calls.push([method,args,options]);return reply};
const api={...base,...create(base,dispatch,value=>Buffer.from(value,"base64"))};
const constructor=segment("  function __autojs6_worker_constructor(","  // The worker keeps synchronous metadata");
const modules=segment("  function __autojs6_worker_host_modules(","  async function __autojs6_worker_invoke(");
const preload=segment("  function __autojs6_worker_preload(","  function __autojs6_limited_worker_threads(");
const directory=path.resolve(__dirname,"../../../build/advanced-image-tests");fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,"preload.cjs");fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},${helper});`);
const methods=Object.keys(api).filter(name=>typeof api[name]==="function");
const config={preloadPath,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{images:methods,image:methods}};
const Worker=new Function("return ("+constructor+")")()(require,()=>config,async(module,method,args)=>{
 if(method==="matchFeatures")return new api.ObjectFrame(frame.topLeft,frame.topRight,frame.bottomLeft,frame.bottomRight,frame.matches,frame.inliers,frame.points);
 if(method==="detectAndComputeFeatures")return feature;
 if(method==="imread")return mat;
 if(method==="quantize")return {bytes:Buffer.from([137,80,78,71]),quality:90,size:4,quantizationError:1,peakWorkingMemoryBytes:1024};
 if(method==="compressToBytes")return Buffer.from([137,80,78,71]);
 if(method==="recycle")return undefined;
 return image;
});
function worker(code,opts={eval:true}){return new Promise((resolve,reject)=>{let value;const w=new Worker(code,opts);w.on("message",v=>value=v);w.on("error",reject);w.on("exit",code=>code?reject(Error("Worker exit "+code)):resolve(value));});}
(async()=>{
 await api.inRange(image,"red","blue");assert.deepEqual(calls.at(-1).slice(0,2),["inRange",[image,{lower:"red",upper:"blue"}]]);
 await api.adaptiveThreshold(image,255,"MEAN_C","BINARY",3,2);assert.equal(calls.at(-1)[1][1].C,2);
 await api.bilateralFilter(image,{d:5,sigmaColor:30,signal:{aborted:false}});assert(!("signal" in calls.at(-1)[1][1]));
 await api.cvtColor(image,"RGB2HSV",3);assert.deepEqual(calls.at(-1)[1][1],{code:"RGB2HSV",dstCn:3});
 await assert.rejects(api.interval(image,"red",NaN),TypeError);
  await assert.rejects(api.load(null),TypeError);await assert.rejects(api.imread(''),TypeError);await assert.rejects(api.quantizeToFile(image,null),TypeError);
 await api.concat("first.png","second.png","bottom");assert.deepEqual(released.splice(0),["second.png","first.png"]);
 await api.downsample(Buffer.from([1,2]),2,2);assert.equal(released.pop(),"upload");
 await api.findColorInRegion(image,"red",1,2);assert.deepEqual(calls.at(-1)[1][2].region,[1,2,9,6]);
 assert.deepEqual(api.buildRegion(image,[1,2]),{x:1,y:2,width:9,height:6});assert.equal(api.__buildRegion([],undefined,10),null);
 assert.throws(()=>api.buildRegion(image,[9,0,2,1]),TypeError);
 reply={...frame};const matched=await api.matchFeatures(feature,feature);assert(matched instanceof api.ObjectFrame);assert.equal(matched.centerX,5);assert.equal(matched.height,8);assert(matched.summary().includes("inliers=18"));
 reply={base64:"iVBORw==",size:4,quality:90,quantizationError:0,peakWorkingMemoryBytes:32};const quant=await api.quantize(image);assert(Buffer.isBuffer(quant.bytes));assert.equal(quant.bytes.length,4);
 reply="Infinity";assert.equal(await api.psnr(image,image),Infinity);reply="NaN";assert(Number.isNaN(await api.ncc(image,image)));
 await api.recycle([feature,mat]);assert(api.isRecycled(feature,mat));assert(!api.isRecycled(image));
 const body=`async function exercise(images){const a=require('node:assert/strict');const descriptor=await images.detectAndComputeFeatures(${JSON.stringify(image)});const frame=await images.matchFeatures(descriptor,descriptor);a(frame instanceof images.ObjectFrame);a.equal(frame.width,10);a(frame.summary().includes('inliers=18'));a.equal(images.buildRegion(${JSON.stringify(image)},[1]).width,9);a.equal(images.__buildRegion([],undefined,1),null);const q=await images.quantize(${JSON.stringify(image)});a(Buffer.isBuffer(q.bytes));a(Buffer.isBuffer(await images.compressToBytes(${JSON.stringify(image)})));const mat=await images.imread('image.png');await images.recycle([descriptor,mat]);a(images.isRecycled(descriptor,mat));return 'passed';}`;
 const cjs=body+`;exercise(require('autojs6:images')).then(v=>require('node:worker_threads').parentPort.postMessage(v));`;
 assert.equal(await worker(cjs),'passed');
 const esm=path.join(directory,'entry.mjs');fs.writeFileSync(esm,`import images,{ObjectFrame} from 'autojs6:images';import {createRequire} from 'node:module';const require=createRequire(import.meta.url);${body};if(ObjectFrame!==images.ObjectFrame)throw Error('named constructor');require('node:worker_threads').parentPort.postMessage(await exercise(images));`);
 assert.equal(await worker(esm,{}),'passed');
 assert.equal(await worker(`const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSON.stringify(cjs)},{eval:true}).on('message',v=>parentPort.postMessage(v));`),'passed');
 console.log('Advanced image adapters and real CJS/ESM/nested Workers: native handles, frame constructors, Buffer results, regions, aliases and resource cleanup passed');
})().catch(error=>{console.error(error);process.exitCode=1});

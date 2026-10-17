"use strict";
// Exercise the embedded client code itself; Android pixels are checked in the host device suite.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname,"../../../app/src/main/cpp/node_bridge_sources.cpp"),"utf8");
function segment(a,b) { const start=source.indexOf(a),end=source.indexOf(b,start); assert(start>=0&&end>start); return source.slice(start,end); }
const calls=[], released=[];
let response, failure, upload=true;
const factory=new Function("__autojs6_call_autojs","__autojs6_bridge_upload_write","__autojs6_bridge_upload_release",`
  let __autojs6_limited_opencc_cache,__autojs6_limited_image_cache,__autojs6_limited_images_cache;
  const __autojs6_recycled_image_handles=new Set();
  const __autojs6_invalid_arg_type=message=>new TypeError(message);
  const __autojs6_bridge_error=(message,code)=>Object.assign(new Error(message),{code});
  const __autojs6_bridge_signal_from_options=value=>value&&value.signal;
  const __autojs6_buffer_constructor_required=()=>Buffer;
  const __autojs6_rhino_compat_request_screen_capture=()=>{};
  const __autojs6_rhino_compat_stop_screen_capture=()=>{};
  ${segment('  const __autojs6_opencc_methods =','  const __autojs6_pinyin_styles =')}
  ${segment('  function __autojs6_number_property(','  function __autojs6_media_projection_handle_id(')}
  ${segment('  function __autojs6_limited_image(moduleName)','  function __autojs6_ocr_bridge_options(')}
  return {cc:__autojs6_limited_opencc(),images:__autojs6_limited_image('images'),image:__autojs6_limited_image('image')};
`);
const api=factory(async(module,method,args,options)=>{calls.push({module,method,args,options});if(failure)throw failure;return response;},
  bytes=>upload?{id:'upload',byteCount:bytes.length,bytes:Buffer.from(bytes)}:null,value=>released.push(value));
const handle={id:'test',__autojs6ImageHandle:'test',width:2,height:1};
(async()=>{
  response='converted';
  for(const name of Object.keys(api.cc)) { assert.equal(await (name==='convert'?api.cc[name]('汉字','s2t'):api.cc[name]('汉字')),'converted'); assert.equal(calls.at(-1).method,'convert'); }
  await assert.rejects(api.cc.s2t(null));
  response=handle;
  const bytes=Buffer.from([0,137,80,78,71,0]);
  await api.images.fromBytes(bytes.subarray(1,5));
  assert.deepEqual(calls.at(-1).options.upload.bytes,Buffer.from([137,80,78,71]));
  assert.equal(released.length,1);
  failure=new Error('provider failed');
  await assert.rejects(api.images.fromBytes(bytes),/provider failed/);
  assert.equal(released.length,2); failure=null;
  upload=false;
  await api.images.fromBytes(bytes);
  assert.equal(calls.at(-1).method,'fromBase64');
  await assert.rejects(api.images.fromBytes(Buffer.alloc(65536)),/descriptor upload/);
  upload=true;
  await api.images.fromBase64('data:image/png;base64,iVBORw==');
  assert.deepEqual(calls.at(-1).options.upload.bytes,Buffer.from([137,80,78,71]));
  await assert.rejects(api.images.fromBase64('%%%%'));
  response=Buffer.from([255,0,0,128,0,255,0,255]);
  assert.deepEqual(await api.images.readPixels(handle),{data:[0x80ff0000|0,0xff00ff00|0],width:2,height:1});
  assert.equal(calls.at(-1).args[1].format,'rgba');
  response=true;
  await api.images.detectsColor(handle,-65536,0,0,{algorithm:'equal',threshold:0});
  assert.equal(calls.at(-1).args[1],-65536);
  response=handle;
  await api.images.medianBlur(handle,[3,3]); assert.equal(calls.at(-1).args[1].size,3);
  await assert.rejects(api.images.medianBlur(handle,[3,5]));
  await api.images.rotate(handle,90,{filter:false}); assert.equal(calls.at(-1).args[1].filter,false);
  await api.images.flip(handle,false,true); assert.equal(calls.at(-1).args[1].direction,'vertical');
  await api.images.recycle(handle); assert.equal(api.image.isRecycled(handle),true);
  const count=calls.length; await api.image.recycle(handle); assert.equal(calls.length,count);
  console.log('OpenCC and image embedded-wrapper regression passed');
})().catch(error=>{console.error(error);process.exitCode=1;});

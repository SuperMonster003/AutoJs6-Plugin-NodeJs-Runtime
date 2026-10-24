"use strict";
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function createWebFixture() {
  let next=0;const views=new Map(),calls=[],cleared=[],cancelled=[],cameraCancelled=[],timing={subscribeDelayMs:0};
  function emit(record,event){if(record.readers.size)for(const events of record.readers.values())events.push(event);else record.events.push(event);}
  async function invoke(module,method,args) {
    assert.equal(module,'ui');calls.push({method,args:structuredClone(args)});
    if(method==='inflate') {
      const id='web-'+ ++next,token={__autojs6ViewHandle:id,id,type:'RuntimeWebView'};
      views.set(id,{token,props:{title:'Browser',...args[0]},events:[],readers:new Map(),clients:{},requests:new Map(),tasks:new Map()});return token;
    }
    const record=views.get(args[0].__autojs6ViewHandle);assert(record,'unknown View');
    if(method==='viewEvents'){await delay(4);return (args[1]?record.readers.get(args[1])||[]:record.events).splice(0);}
    if(method==='viewUpdate'){Object.assign(record.props,args[1]);return record.props;}
    if(method==='viewGet')return args.length>1?record.props[args[1]]:record.props;
    if(method==='disposeView'){for(const done of record.requests.values())done(null);record.requests.clear();return null;}
    assert.equal(method,'viewCommand');const [_,name,parameters]=args;
    if(name==='subscribeViewEvents'){if(timing.subscribeDelayMs)await delay(timing.subscribeDelayMs);if(!record.readers.has(parameters[0]))record.readers.set(parameters[0],record.events.splice(0));return true;}
    if(name==='releaseViewEvents'){record.readers.delete(parameters[0]);return true;}
    if(name==='configureWebClient') {
      const config=parameters[0];assert(config.callbacks.every(value=>typeof value==='string'));
      if(config.options.invalid)throw Error('Invalid native policy');
      for(const done of record.requests.values())done(null);record.requests.clear();record.clients[config.family]=config;return null;
    }
    if(name==='clearWebClient') {
      const [family,id]=parameters;cleared.push({family,id});
      if(record.clients[family]?.clientId!==id)return false;
      record.clients[family]=undefined;for(const done of record.requests.values())done(null);record.requests.clear();return true;
    }
    if(name==='beginWebEvaluation'||name==='beginAsyncWebEvaluation') {
      const taskId='eval-'+ ++next;
      if(parameters[0]==='pendingAsync') {record.tasks.set(taskId,()=>emit(record,{type:'webTaskResult',taskId,error:'cancelled'}));return {taskId};}
      const callback={confirm:'onJsConfirm',prompt:'onJsPrompt',file:'onShowFileChooser'}[parameters[0]];
      if(!callback){emit(record,{type:'webTaskResult',taskId,value:42});return {taskId};}
      const client=record.clients.chrome;assert(client?.callbacks.includes(callback));
      const requestId='request-'+ ++next;
      emit(record,{type:'webCallback',family:'chrome',clientId:client.clientId,callback,requestId,
        data:{message:'fixture',url:'https://fixture.invalid/',title:'Browser',acceptTypes:['text/plain'],multiple:false,mode:0,captureEnabled:false}});
      const done=value=>{record.tasks.delete(taskId);emit(record,{type:'webTaskResult',taskId,value:callback==='onJsConfirm'?value===true:value});};
      record.requests.set(requestId,done);record.tasks.set(taskId,()=>done(null));return {taskId};
    }
    if(name==='replyWebRequest') {const done=record.requests.get(parameters[0]);record.requests.delete(parameters[0]);if(done)done(parameters[1]);return !!done;}
    if(name==='chooseFiles') {
      const taskId='task-'+ ++next,pickerId='picker-'+next;
      if(!parameters[0].wait)emit(record,{type:'webTaskResult',taskId,value:['content://fixture/document/alpha']});
      else {record.tasks.set(taskId,()=>emit(record,{type:'webTaskResult',taskId,value:null}));record.pickers=(record.pickers||new Set()).add(taskId);}
      // Exercise an event that reaches the client before the command's creation acknowledgement.
      await delay(20);return {taskId,pickerId};
    }
    if(name==='cancelFileChooser'){
      cancelled.push(parameters[0]);
      // Like the host, an unscoped cancel closes only pickers that are already registered.
      if(parameters[0]==null)for(const taskId of record.pickers||[]){record.pickers.delete(taskId);const done=record.tasks.get(taskId);record.tasks.delete(taskId);done?.();}
      return true;
    }
    if(name==='cancelWebTask'){cancelled.push(parameters[0]);const done=record.tasks.get(parameters[0]);record.tasks.delete(parameters[0]);done?.();return !!done;}
    if(name==='download')return {id:parameters[0].url.startsWith('data:')?'web:fixture':7,url:parameters[0].url,fileName:'fixture.txt'};
    if(name==='getDownload')return {id:parameters[0],status:'successful'};
    if(name==='readDownload')return {base64:Buffer.from('downloaded').toString('base64'),bytesRead:10};
    if(name==='readDocument')return {base64:Buffer.from([0,128,255,42]).toString('base64'),bytesRead:4};
    if(name==='cancelDownload'||name==='exitFullscreen')return true;
    if(name==='stopMediaCapture')return true;
    if(name==='enableDisplayCapture'){const id='display-'+ ++next;record.display=id;return {id,installed:true,source:'android-media-projection',audio:false};}
    if(name==='disableDisplayCapture'){if(parameters[0]&&record.display!==parameters[0])return false;const active=!!record.display;record.display=null;return active;}
    if(name==='startBackgroundRecording'){
      const id='camera-'+ ++next,taskId='start-'+next;record.recording={id,state:'recording',audio:!!parameters[0].audio};
      emit(record,{type:'webTaskResult',taskId,value:record.recording});return {taskId,recordingId:id};
    }
    if(name==='getBackgroundRecording')return record.recording||{state:'idle'};
    if(name==='cancelBackgroundRecording'){
      if(!record.recording||parameters[0]&&parameters[0]!==record.recording.id)return false;
      cameraCancelled.push(record.recording.id);record.recording=null;return true;
    }
    throw Error('Unexpected command '+name);
  }
  return {invoke,views,calls,cleared,cancelled,cameraCancelled,emit,timing};
}
async function exercise(ui,fixture) {
  const web=await ui.createView('WebView',{javaScriptEnabled:true}),record=fixture.views.get(web.id),errors=[];
  try {
    web.on('webCallbackError',event=>errors.push(event.message));
    await web.setWebChromeClient({
      async onJsConfirm(){assert.equal(await this.title,'Browser','UI reads must not deadlock behind pending evaluation');return true;},
      onJsPrompt(){return 'typed';},onShowFileChooser(event){return this.chooseFiles(event);}
    });
    assert.equal(await web.evaluateJavascript('confirm'),true);
    assert.equal(await web.evaluateJavascript('prompt'),'typed');
    assert.equal(await web.evaluateJavascriptAsync('Promise.resolve(42)',{timeoutMs:180000}),42);
    assert.equal(fixture.calls.findLast(call=>call.method==='viewCommand'&&call.args[1]==='beginAsyncWebEvaluation').args[2][1].timeoutMs,180000);
    assert.deepEqual(await web.evaluateJavascript('file'),['content://fixture/document/alpha']);
    await assert.rejects(web.setWebChromeClient({}, {invalid:true}),/Invalid native policy/);
    assert.equal(await web.evaluateJavascript('confirm'),true,'Rejected configuration retains prior callbacks');
    await web.setWebChromeClient({onJsConfirm(){throw Error('callback failure');}});
    assert.equal(await web.evaluateJavascript('confirm'),false);
    assert(errors.some(value=>value.includes('callback failure')));
    await web.setWebChromeClient({onJsConfirm(){return new Promise(()=>{});}});
    const pending=web.evaluateJavascript('confirm');
    for(let i=0;!record.requests.size&&i<50;i++)await delay(5);
    assert(record.requests.size);await web.setWebChromeClient(null);
    assert.equal(await pending,false,'Client replacement can cancel an evaluation waiting for a dialog');
    let navigation;
    await web.setWebViewClient({shouldOverrideUrlLoading(event){navigation=event.blocked;}}, {navigationRules:[{urlPrefix:'https://fixture.invalid/blocked',action:'block'}]});
    fixture.emit(record,{type:'webCallback',family:'client',clientId:record.clients.client.clientId,callback:'shouldOverrideUrlLoading',data:{blocked:true}});
    for(let i=0;navigation===undefined&&i<50;i++)await delay(5);assert.equal(navigation,true);
    const token=structuredClone(web);assert.equal(token.setWebViewClient,undefined,'Callbacks must stay out of transferable View identities');
    const download=await web.download({url:'https://fixture.invalid/file'});assert.equal((await web.getDownload(download.id)).status,'successful');
    assert.equal(Buffer.from(await web.readDownload(download.id)).toString(),'downloaded');assert.equal(await web.cancelDownload(download.id),true);
    for(const mode of ['save','directory']) {
      const selected=await web.chooseFiles({mode,acceptTypes:['text/plain'],fileName:'fixture.txt',writable:true});assert(selected[0].startsWith('content:'));
      assert(fixture.calls.some(call=>call.method==='viewCommand'&&call.args[1]==='chooseFiles'&&call.args[2][0].mode===mode));
    }
    for(const captureKind of ['image','video','audio']) {
      const options={captureEnabled:true,captureKind,acceptTypes:['*/*'],videoQuality:0,durationLimitSeconds:4,sizeLimitBytes:2097152,timeoutMs:180000};
      await web.chooseFiles(options);
      assert.deepEqual(fixture.calls.findLast(call=>call.method==='viewCommand'&&call.args[1]==='chooseFiles').args[2][0],options,'Media capture options reach the host unchanged');
    }
    const late=await ui.createView('WebView');
    try {
      // chooseFiles first subscribes to View events; an unscoped cancel must not overtake that submission.
      fixture.timing.subscribeDelayMs=60;
      const chosen=late.chooseFiles({wait:true});await late.cancelFileChooser();
      assert.equal(await Promise.race([chosen,delay(1000).then(()=>'pending')]),null,'An unscoped cancel closes a chooser still being submitted');
    } finally {fixture.timing.subscribeDelayMs=0;await late.dispose();}
    assert.deepEqual([...await web.readDocument('content://fixture/document/alpha')],[0,128,255,42]);
    const display=await web.enableDisplayCapture({origin:'https://fixture.invalid',frameRate:5,maxWidth:640,maxHeight:480});
    assert.equal(display.audio,false);assert.equal(await web.disableDisplayCapture('stale-id'),false);assert.equal(await web.disableDisplayCapture(display.id),true);
    assert.equal(await web.stopMediaCapture(),true);
    const camera=await web.startBackgroundRecording({audio:true,facing:'back'});assert.equal((await web.getBackgroundRecording()).id,camera.id);
    assert.equal(await web.cancelBackgroundRecording('stale-camera'),false);assert.equal(await web.cancelBackgroundRecording(camera.id),true);
    const local=await web.download({url:'data:text/plain,fixture',directoryUri:'content://fixture/tree/root'});
    assert.equal((await web.getDownload(local.id)).id,'web:fixture');await web.cancelDownload(local.id);
    assert.throws(()=>web.setWebViewClient({unknown(){}}),/Unsupported/);
  } finally {await web.dispose();}
}
async function workers(source,createUi) {
  function section(a,b){const start=source.indexOf(a),end=source.indexOf(b,start);assert(start>=0&&end>start);return source.slice(start,end);}
  const constructor=section('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
  const modules=section('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
  const preload=section('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
  const uiSource=section('  function __autojs6_create_ui(','  function __autojs6_create_compose(');
  const directory=path.resolve(__dirname,'../../../build/webview-tests');fs.mkdirSync(directory,{recursive:true});
  const preloadPath=path.join(directory,'preload.cjs');fs.writeFileSync(preloadPath,`(${preload})(require,${constructor},()=>{},${modules},undefined,${uiSource});`);
  const fixture=createWebFixture(),ui=createUi(fixture.invoke);
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{ui:Object.keys(ui)}};
  const Worker=new Function('return ('+constructor+')')()(require,()=>config,fixture.invoke);
  const body=`const view=await ui.createView('WebView');await view.setWebChromeClient({onJsConfirm:async()=>true});if(!await view.evaluateJavascript('confirm'))throw Error('Worker callback');if(await view.evaluateJavascriptAsync('Promise.resolve(42)')!==42)throw Error('Worker asynchronous evaluation');if((await view.readDocument('content://fixture/document/alpha'))[1]!==128)throw Error('Worker document bytes');const local=await view.download({url:'data:text/plain,fixture'});if((await view.getDownload(local.id)).id!=='web:fixture')throw Error('Worker download identity');await view.cancelDownload(local.id);for(const captureKind of ['video','audio'])await view.chooseFiles({captureEnabled:true,captureKind,durationLimitSeconds:3,sizeLimitBytes:1048576,videoQuality:0,timeoutMs:180000});await view.enableDisplayCapture({origin:'https://fixture.invalid'});await view.startBackgroundRecording({audio:true});view.evaluateJavascriptAsync('pendingAsync').catch(()=>{});view.chooseFiles({wait:true,mode:'directory'});parentPort.postMessage(view);setInterval(()=>{},1000);`;
  for(const mode of ['cjs','esm','nested']) {
    let code=mode==='esm'?`import ui from 'ui';import {parentPort} from 'node:worker_threads';${body}`:`const ui=require('ui'),{parentPort}=require('node:worker_threads');(async()=>{${body}})();`;
    if(mode==='nested')code=`const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSON.stringify(code)},{eval:true}).on('message',value=>parentPort.postMessage(value)).on('error',error=>{throw error;});`;
    const worker=new Worker(mode==='esm'?new URL('data:text/javascript,'+encodeURIComponent(code)):code,mode==='esm'?{}:{eval:true});
    const token=await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
    await delay(40);const record=fixture.views.get(token.id),old=record.clients.chrome.clientId;
    const oldCamera=record.recording.id;let replacementCamera,replacementDisplay;
    if(mode==='cjs'){await ui.view(token).setWebChromeClient({onJsConfirm:()=>false});await ui.view(token).cancelBackgroundRecording(oldCamera);replacementCamera=await ui.view(token).startBackgroundRecording({audio:true});replacementDisplay=await ui.view(token).enableDisplayCapture({origin:'https://fixture.invalid'});}
    await worker.terminate();
    for(let i=0;!fixture.cleared.some(value=>value.id===old)&&i<100;i++)await delay(5);
    assert(fixture.cleared.some(value=>value.id===old),'Exited worker must clear its callback registration');
    if(mode==='cjs')assert(record.clients.chrome&&record.clients.chrome.clientId!==old,'Old worker must not clear a replacement client');
    else assert.equal(record.clients.chrome,undefined);
    if(mode==='cjs'){assert.equal(record.recording.id,replacementCamera.id);assert.equal(record.display,replacementDisplay.id);await ui.view(token).cancelBackgroundRecording(replacementCamera.id);await ui.view(token).disableDisplayCapture(replacementDisplay.id);}else{assert.equal(record.recording,null);assert.equal(record.display,null);}
    await ui.view(token).dispose();
  }
  for(const kind of ['video','audio'])assert.equal(fixture.calls.filter(c=>c.method==='viewCommand'&&c.args[1]==='chooseFiles'&&c.args[2][0].captureKind===kind).length,3,'Every Worker mode forwards '+kind);
  assert(fixture.cancelled.length>=6,'Worker termination must cancel pending file pickers and page Promises');
}
module.exports={createWebFixture,exercise};
if(require.main===module)(async()=>{
  const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
  const start=source.indexOf('  function __autojs6_create_ui('),end=source.indexOf('  function __autojs6_create_compose(',start);
  const createUi=new Function('return ('+source.slice(start,end)+')')();
  const fixture=createWebFixture();await exercise(createUi(fixture.invoke),fixture);await workers(source,createUi);
  console.log('WebView callback replies, nonblocking evaluation, file-result races, downloads and CJS/ESM/nested Worker cleanup passed');
})().catch(error=>{console.error(error);process.exitCode=1;});

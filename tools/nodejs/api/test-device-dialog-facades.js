const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const block=source.slice(source.indexOf('  // Shared device and dialog facades'),source.indexOf('  // Main-thread device facade.'));
const {device,dialogs}=new Function(block+';return {device:__autojs6_create_device,dialogs:__autojs6_create_dialogs}')();
test('device snapshots, live values, permissions and volume streams use the same contract',async()=>{
  const calls=[],full={screen:{width:720},build:{model:'fixture'},battery:{percent:67}};
  const api=device(async(module,method,args,opts)=>{calls.push({module,method,args,opts});return method==='info'?full:method.startsWith('getAudio')?4:null;},{width:360});
  assert.equal(api.width,360);assert.equal(await api.getWidth(),720);assert.equal(await api.getModel(),'fixture');assert.equal(await api.getInfo(),full);
  await api.setBrightness(90);assert.deepEqual(calls.at(-1).opts.permissions,['device','device.power']);
  await api.setMusicVolume(0);assert.deepEqual(calls.at(-1).args,['music',0]);assert.deepEqual(calls.at(-1).opts.permissions,['media','media.audio']);
  for(const [method,value] of [['setBrightness',256],['setBrightnessMode',2],['keepScreenOn',0],['setAlarmVolume',-1]]) await assert.rejects(api[method](value),{code:'ERR_AUTOJS6_BRIDGE_INVALID_REQUEST'});
});
test('late wake lock creation is released when the requesting signal was aborted',async()=>{
  const calls=[],controller=new AbortController();
  const api=device(async(_,method,args)=>{calls.push({method,args});if(method==='keepScreenOn')controller.abort();});
  await assert.rejects(api.keepScreenOn(1000,{signal:controller.signal}),{code:'ABORT_ERR'});
  assert.equal(calls[1].method,'cancelKeepingAwake');assert.equal(calls[0].args[1],calls[1].args[0]);
});
test('dialogs preserve overloads, cancellation token, selection and live progress state',async()=>{
  const calls=[];let state;
  const api=dialogs(async(_,method,args)=>{
    calls.push({method,args});
    if(method==='progressShow')return state={id:'progress',shown:true,max:100,value:0,...args[0]};
    if(method==='progressUpdate')return state={...state,...args[1]};
    if(method==='progressDismiss')return true;
    return method==='confirm'?true:method==='multiChoice'?[0,2]:'text';
  });
  assert.equal(await api.prompt('Input',{positive:'Go'}),'text');assert.equal(calls.at(-1).args[1],'');assert.equal(calls.at(-1).args[2].positive,'Go');
  await api.confirm('Title','Text',{neutral:'Later'});assert.equal(calls.at(-1).args[2].neutral,'Later');
  assert.deepEqual(await api.multiChoice('Pick',[1,2,3],[0,2]),[0,2]);await assert.rejects(api.singleChoice('Pick',['a'],0.5));
  const handle=await api.progress.show({title:'Progress',max:5,value:1});await handle.update({value:4});assert.equal(handle.value,4);await handle.dismiss();assert.equal(handle.shown,false);
  assert(calls.filter(c=>['input','confirm','multiChoice','progressShow'].includes(c.method)).every(c=>c.args.at(-1).__requestId));
});
test('dialog timeout or abort dismisses its token even before its Activity attaches',async()=>{
  const calls=[],api=dialogs(async(_,method,args)=>{calls.push({method,args});if(method==='input')throw Object.assign(new Error('cancelled'),{code:'ABORT_ERR'});});
  await assert.rejects(api.input('Pending'),{code:'ABORT_ERR'});
  assert.equal(calls[1].method,'cancelRequest');assert.equal(calls[1].args[0],calls[0].args[2].__requestId);
});

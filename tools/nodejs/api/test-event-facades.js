const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const a=source.indexOf('  function __autojs6_create_host_events('),z=source.indexOf('  // Main-thread event and notification facades.',a);
assert(a>=0&&z>a);
const factories=new Function(source.slice(a,z)+';return {events:__autojs6_create_host_events,notifications:__autojs6_create_notifications};')();
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function fixture(hook){
  const calls=[],live=new Map(),posted=new Map();let next=0;
  const call=async(module,method,args,options)=>{
    calls.push({module,method,args,options});if(hook)await hook(module,method,args);
    if(module==='events'){
      if(method.startsWith('observe')){const id='observer-'+ ++next,source={observeBroadcasts:'broadcast',observeNotification:'notification',observeToast:'toast',observeKey:'key'}[method];live.set(id,[]);return {id,source};}
      if(method==='close')return live.delete(args[0]);
      if(method==='drainEvents'){assert(live.has(args[0]));return live.get(args[0]).splice(0,32);}
    }else if(module==='notifications'){
      if(method==='notify'){const id=args[0].id??++next;posted.set(args[1]+':'+id,args[0]);return id;}
      if(method==='cancel')posted.delete(args[1]+':'+args[0]);
      if(method==='cancelAll')for(const key of posted.keys())if(key.startsWith(args[0]+':'))posted.delete(key);
      if(method==='getPermissionStatus')return {notificationsEnabled:true};
      if(method==='openSettings')return {opened:!args[0].dryRun,dryRun:args[0].dryRun};
    }
  };
  return {call,calls,live,posted};
}
test('all observer kinds retain explicit grants, immutable snapshots and independent subscriptions',async()=>{
  const f=fixture(),events=factories.events(f.call,EventEmitter);
  for(const method of ['observeBroadcasts','observeNotification','observeToast','observeKey']){
    const sub=await events[method]();assert.equal(await events[method](),sub);
    f.live.get(sub.id).push({type:'owned',time:1});const rows=await sub.drainEvents();assert(Object.isFrozen(rows)&&Object.isFrozen(rows[0]));assert.equal(rows[0].source,sub.source);
  }
  assert.deepEqual(f.calls.find(c=>c.method==='observeNotification').options.permissions,['events','events.notification']);
  assert.equal(f.live.size,4);await events.close();assert.equal(f.live.size,0);
});
test('listeners poll once, deliver module/subscription events and release on AbortSignal',async()=>{
  const f=fixture(),events=factories.events(f.call,EventEmitter),signal=new AbortController(),seen=[];
  const sub=await events.observeBroadcasts({signal:signal.signal,drainIntervalMs:25});
  events.once('battery_changed',e=>seen.push(e.level));sub.once('battery_changed',e=>seen.push(e.level));
  f.live.get(sub.id).push({type:'battery_changed',level:73,time:1});
  for(let n=0;n<30&&seen.length<2;n++)await pause(10);assert.deepEqual(seen,[73,73]);
  let closed=0;sub.on('close',()=>closed++);sub.on('error',e=>assert.equal(e.code,'ABORT_ERR'));signal.abort();
  await pause(10);assert(sub.closed);assert.equal(closed,1);assert.equal(f.live.size,0);await events.close();
});
test('an abort while registration is pending closes the late native subscription',async()=>{
  let release;const wait=new Promise(r=>release=r),f=fixture(async(_,method)=>{if(method==='observeKey')await wait;});
  const events=factories.events(f.call,EventEmitter),controller=new AbortController();
  const pending=events.observeKey({signal:controller.signal});controller.abort();release();
  await assert.rejects(pending,{code:'ABORT_ERR'});assert.equal(f.live.size,0);assert.equal(f.calls.filter(c=>c.method==='close').length,1);
});
test('module close waits for pending registration and remains reusable',async()=>{
  let release;const wait=new Promise(r=>release=r),f=fixture(async(_,method)=>{if(method==='observeToast')await wait;});
  const events=factories.events(f.call,EventEmitter),pending=events.observeToast(),closed=events.close();
  release();await assert.rejects(pending,{code:'ABORT_ERR'});await closed;assert.equal(f.live.size,0);
  const sub=await events.observeBroadcasts();assert(!sub.closed);await sub.close();
});
test('pushed observers buffer manual reads, report overflow and update native references after once',async()=>{
  const f=fixture(),refs=[];let push,stopped=0;
  const events=factories.events(f.call,EventEmitter,(_,handler)=>{push=handler;const stop=()=>stopped++;stop.ref=value=>refs.push(value);return stop;});
  const sub=await events.observeBroadcasts();for(let n=0;n<130;n++)push({type:'battery_changed',level:n,time:n});
  const rows=await sub.drainEvents();assert.equal(rows.length,129);assert.equal(rows[0].dropped,2);assert.equal(rows.at(-1).level,129);
  let value;sub.once('battery_changed',e=>value=e.level);push({type:'battery_changed',level:5,time:1});assert.equal(value,5);assert.equal(refs.at(-1),false);
  assert(!f.calls.some(c=>c.method==='drainEvents'));await sub.close();assert.equal(await sub.close(),false);assert.equal(stopped,1);
});
test('poll failures close the subscription instead of repeatedly consuming bridge calls',async()=>{
  const f=fixture((_,method)=>{if(method==='drainEvents')throw Error('owned transport failure');}),events=factories.events(f.call,EventEmitter);
  const sub=await events.observeBroadcasts({drainIntervalMs:25});let failure;sub.on('error',e=>failure=e.message);
  for(let n=0;n<30&&!sub.closed;n++)await pause(10);assert(sub.closed);assert.equal(failure,'owned transport failure');assert.equal(f.live.size,0);
  assert.equal(f.calls.filter(c=>c.method==='drainEvents').length,1);
});
test('notification scopes isolate identical IDs and cancelAll while preserving status/settings aliases',async()=>{
  const f=fixture(),one=factories.notifications(f.call,undefined,'one'),two=factories.notifications(f.call,undefined,'two');
  await one.notify({id:7,title:'one'});await two.notify({id:7,title:'two'});assert.equal(f.posted.size,2);
  await one.cancelAll();assert.equal(f.posted.size,1);assert(f.posted.has('two:7'));await two.cancel(7);assert.equal(f.posted.size,0);
  assert(Object.isFrozen(await one.getPermissionStatus()));assert.deepEqual(await one.openNotificationSettings({dryRun:true}),{opened:false,dryRun:true});
  assert.deepEqual(f.calls.at(-1).options.permissions,['notifications','notifications.settings']);
  await assert.rejects(one.notify({title:'invalid signal'},{signal:{}}));
  for(const id of [NaN,Infinity,-1,0,2147483648])await assert.rejects(one.notify({id,title:'invalid'}));
  assert.equal(f.posted.size,0);
});
test('aborted notification posting cancels its late result using the same client scope',async()=>{
  let release;const wait=new Promise(r=>release=r),f=fixture(async(_,method)=>{if(method==='notify')await wait;}),notifications=factories.notifications(f.call,undefined,'late'),controller=new AbortController();
  const pending=notifications.notify({id:99,title:'owned'},{signal:controller.signal});controller.abort();release();
  await assert.rejects(pending,{code:'ABORT_ERR'});assert.equal(f.posted.size,0);assert.deepEqual(f.calls.at(-1).args,[99,'late']);
});

test('a throwing close listener still detaches the native event stream',async()=>{
  const f=fixture();let push,stopped=0;
  const events=factories.events(f.call,EventEmitter,(_,handler)=>{push=handler;return ()=>stopped++;});
  const sub=await events.observeBroadcasts();sub.on('close',()=>{throw Error('owned callback failure');});
  assert.throws(()=>push({type:'close',reason:'source disconnected'}),/owned callback failure/);
  assert(sub.closed);assert.equal(stopped,1);await events.close();
});

const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const start=source.indexOf('  function __autojs6_create_sensors('),end=source.indexOf('  // Main-thread sensors facade;',start);
const create=new Function('return ('+source.slice(start,end)+')')();
function fixture({delay,events=true,failDrain=false,push=false}={}){
 const calls=[],live=new Map();let n=0,handler,detached=0;
 const sensors=create(async(module,method,args,options)=>{
  calls.push({module,method,args,options});
  if(method==='getAvailableSensors')return [{type:'accelerometer',name:'fixture'}];
  if(method==='subscribe'){if(delay)await delay;const id='sensor-'+ ++n;live.set(id,args[0]);return {id,type:args[0].type,...(push?{subscriptionId:id}:{})};}
  if(method==='unsubscribe'){live.delete(args[0].id);return null;}
  if(method==='drainEvents'){if(failDrain)throw Error('transport failed');const entry=live.get(args[0].id);return events&&entry?[{type:'event',event:{type:entry.type,timestamp:1,accuracy:3,values:[1,2,3]}}]:[];}
 },EventEmitter,push?(record,fn)=>{handler=fn;return()=>detached++;}:undefined);
 return {sensors,calls,live,push:event=>handler(event),get detached(){return detached;}};
}
test('sample/subscribe freeze events, clamp options, and close idempotently',async()=>{
 const f=fixture(),s=f.sensors;assert(Object.isFrozen(await s.getAvailableSensors()));
 const value=await s.once('accelerometer',{drainIntervalMs:25});assert.deepEqual(value.values,[1,2,3]);assert(Object.isFrozen(value.values));assert.equal(f.live.size,0);
 const sub=s.subscribe('light',undefined,{intervalMs:1,maxQueueSize:999,drainIntervalMs:25});await sub.ready;
 const event=await new Promise(r=>sub.once('event',r));assert.equal(event.type,'light');let closed=0;sub.on('close',()=>closed++);
 await Promise.all([sub.close(),sub.close()]);assert.equal(closed,1);assert.equal(f.live.size,0);
 assert.equal(f.calls.findLast(c=>c.method==='subscribe').args[0].samplingIntervalMs,20);assert.equal(f.calls.findLast(c=>c.method==='subscribe').args[0].maxQueueSize,64);
});
test('single sample timeout and abort unregister their temporary subscriptions',async()=>{
 for(const abort of [false,true]){const f=fixture({events:false}),controller=new AbortController();const pending=f.sensors.once('light',{timeoutMs:100,signal:controller.signal,drainIntervalMs:25});if(abort)setTimeout(()=>controller.abort(),15);await assert.rejects(pending,abort?{name:'AbortError'}:{code:'ERR_AUTOJS6_BRIDGE_TIMEOUT'});assert.equal(f.live.size,0);}
});
test('close or abort while registration is pending releases the late handle',async()=>{
 for(const abort of [false,true]){let finish;const f=fixture({delay:new Promise(r=>finish=r)}),controller=new AbortController();const sub=f.sensors.subscribe('light',undefined,{signal:controller.signal});const ready=sub.ready.catch(e=>e);await new Promise(r=>setImmediate(r));if(abort)controller.abort();const closed=sub.close();finish();await ready;await closed;assert.equal(f.live.size,0);assert(sub.closed);}
});
test('pre-abort and invalid inputs never register; failed drains emit error and release',async()=>{
 const f=fixture({failDrain:true});const controller=new AbortController();controller.abort();await assert.rejects(f.sensors.once('light',{signal:controller.signal}));await assert.rejects(f.sensors.once('pressure'));assert.throws(()=>f.sensors.subscribe('light',null));assert.equal(f.calls.length,0);
 const sub=f.sensors.subscribe('light',undefined,{drainIntervalMs:25});const failed=new Promise(r=>sub.on('error',r));await sub.ready;assert.match((await failed).message,/transport failed/);assert.equal(f.live.size,0);assert(sub.closed);
});
test('main-thread pushed events and live AbortSignal detach without polling',async()=>{
 const f=fixture({push:true}),controller=new AbortController(),sub=f.sensors.subscribe('light',undefined,{signal:controller.signal});await sub.ready;
 const value=new Promise(r=>sub.once('event',r));f.push({type:'event',event:{type:'light',values:[42]}});assert.deepEqual((await value).values,[42]);
 const closed=new Promise(r=>sub.once('close',r));controller.abort();await closed;assert.equal(f.live.size,0);assert.equal(f.detached,1);assert(!f.calls.some(c=>c.method==='drainEvents'));
});

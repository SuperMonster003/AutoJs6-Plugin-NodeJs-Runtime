const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {AsyncLocalStorage}=require('node:async_hooks');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const factory=new Function(source.slice(source.indexOf('  // Shared Java object facade'),source.indexOf('  // Main Java facade transport.'))+';return __autojs6_create_java;')();
function fixture() {
  const calls=[],events=[],pending=new Map(),registrations=new Map();let serial=0,waiter;
  const native=async(_,method,args)=>{
    const data=args[0];calls.push({method,data});
    if(method==='proxy'||method==='defineClass') {
      registrations.set(data.callback,data);
      return {__autojs6JavaHandle:'owner-'+data.callback,className:'java.lang.Class',__autojs6JavaCallbackOwner:data.callback,...(method==='defineClass'?{__autojs6JavaClass:'owned.Defined'}:{})};
    }
    if(method==='drainCallbacks') {
      if(!events.length)await new Promise(resolve=>{const timer=setTimeout(resolve,20);waiter=()=>{clearTimeout(timer);resolve();};});
      return events.splice(0);
    }
    if(method==='completeCallback'){const item=pending.get(data.id);pending.delete(data.id);data.error?item.reject(new Error(data.error)):item.resolve(data.value);return true;}
    if(method==='closeCallbacks'){registrations.delete(data.callback);waiter?.();return true;}
    if(method==='release')return true;
    if(method==='callStatic')return 9;
    if(method==='callSuper')return 'base:'+data.args[0];
    if(method==='loadJar'||method==='loadDex')return {__autojs6JavaHandle:'loader',className:'dalvik.system.DexClassLoader',__autojs6JavaLoader:true,paths:['private/code.jar']};
    if(method==='new')return {__autojs6JavaHandle:'instance',className:data.className};
    if(method==='call') {
      const entry=registrations.values().next().value,id='invocation-'+ ++serial;
      const result=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));
      events.push({id,callback:entry.callback,method:data.method,signature:data.method+'(java.lang.String)',key:entry.methods[0],receiver:{__autojs6JavaHandle:'borrowed-'+id,className:'owned.Defined'},args:data.args});waiter?.();return result;
    }
    throw Error(method);
  };
  const java=factory(native,{access:'public'},true,AsyncLocalStorage);return {java,calls,registrations,pending};
}
test('proxy callbacks return async values and keep independent reentry contexts',async()=>{
  const f=fixture();const proxy=await f.java.proxy('java.util.function.Function',async function(value){
    await new Promise(resolve=>setTimeout(resolve,value==='first'?10:0));
    assert.equal(this.method,'apply');return value+':'+await this.java.callStatic('java.lang.Math','max',[1,9]);
  });
  assert.deepEqual(await Promise.all([proxy.call('apply',['first']),proxy.call('apply',['second'])]),['first:9','second:9']);
  const callbacks=f.calls.filter(x=>x.method==='callStatic');assert.equal(new Set(callbacks.map(x=>x.data.callbackInvocationId)).size,2);
  await proxy.release();assert.equal(f.registrations.size,0);
});
test('generated descriptors support construction, super calls, and cloneable handles',async()=>{
  const f=fixture();const cls=await f.java.defineClass({extends:'owned.Base',methods:{decorate:async function(value){return (await this.callSuper(value))+':js';}}});
  assert.equal(cls.className,'owned.Defined');assert.equal(typeof cls.new,'function');assert.doesNotThrow(()=>structuredClone(cls));
  const object=await f.java.new(JSON.parse(JSON.stringify(cls)),['seed']);assert.equal(object.className,'owned.Defined');assert.equal(await object.call('decorate',['value']),'base:value:js');
  assert(f.calls.some(x=>x.method==='callSuper'&&x.data.callbackInvocationId));await cls.release();assert.equal(f.registrations.size,0);
});
test('callback errors are observable and closeCallbacks stops active pumps',async()=>{
  const f=fixture(),errors=[];const object=await f.java.wrap(()=>{throw Error('owned callback failure');},'java.lang.Runnable',{onError:e=>errors.push(e)});
  await assert.rejects(object.call('run'),/owned callback failure/);await new Promise(resolve=>setTimeout(resolve,0));assert.equal(errors.length,1);
  await f.java.closeCallbacks();assert.equal(f.registrations.size,0);await object.release();
});
test('loader descriptors select their own loader on construction and static access',async()=>{
  const f=fixture(),loader=await f.java.loadJar(['one.jar','two.jar']);const type=loader.type('owned.Fixture');
  await type.new();await type.callStatic('get');assert(f.calls.filter(x=>['new','callStatic'].includes(x.method)).every(x=>x.data.loader==='loader'));
  await f.java.loadDex('owned.dex');assert.deepEqual(f.calls.find(x=>x.method==='loadDex').data.paths,['owned.dex']);await loader.release();
});

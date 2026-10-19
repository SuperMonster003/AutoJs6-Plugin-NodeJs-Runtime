const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const factory=new Function(source.slice(source.indexOf('  // Shared Java object facade'),source.indexOf('  // Main Java facade transport.'))+';return __autojs6_create_java;')();
test('Java facade preserves exact longs, typed arrays, null overloads and cloneable object references',async()=>{
  const calls=[];const java=factory(async(module,method,args,options)=>{
    calls.push({module,method,args,options});
    if(method==='new')return {__autojs6JavaHandle:'owned',className:args[0].className};
    if(method==='call')return [{__autojs6JavaLong:'9223372036854775807'},{__autojs6JavaHandle:'child',className:'java.lang.Object'},null];
    return true;
  },{access:'public'});
  const object=await java.type('java.lang.StringBuilder').new(['text']);
  assert.equal(typeof object.call,'function');assert.deepEqual(structuredClone(object),object.toJSON());
  const result=await object.call('method',[9223372036854775807n,java.cast('java.lang.String',null),new Int8Array([-1,3])],{parameterTypes:['long','java.lang.String','byte[]'],thread:'main'});
  assert.equal(result[0],9223372036854775807n);assert.equal(typeof result[1].release,'function');assert.equal(result[2],null);
  assert.deepEqual(calls[1].args[0].args,[{__autojs6JavaType:'long',value:'9223372036854775807'},{__autojs6JavaType:'java.lang.String',value:null},[-1,3]]);
  assert.equal(calls[1].args[0].thread,'main');assert.deepEqual(calls[1].args[0].parameterTypes,['long','java.lang.String','byte[]']);
  await java.callStatic(java.type('java.lang.Math'),'max',[3,7]);assert.equal(calls.at(-1).args[0].className,'java.lang.Math');
});
test('Java facade rejects unsafe numbers, plain objects, cyclic arrays and invalid thread before dispatch',async()=>{
  let calls=0;const java=factory(async()=>{calls++;});const cycle=[];cycle.push(cycle);
  for(const args of [[9007199254740992],[{}],[cycle],[()=>{}]])await assert.rejects(java.callStatic('T','f',args));
  await assert.rejects(java.new('T',[],{thread:'bogus'}));assert.equal(calls,0);
  assert.throws(()=>java.type(""),{code:"ERR_AUTOJS6_JAVA_CLASS_DENIED"});
  assert.throws(()=>factory(()=>{}, {},false).type('T'),{code:'ERR_AUTOJS6_JAVA_INTEROP_DISABLED'});
});
test('aborted resolved Java object arrays are released, never exposed',async()=>{
  const abort=new AbortController(),released=[];
  const java=factory(async(_,method,args)=>{
    if(method==='release'){released.push(args[0].handle);return true;}
    abort.abort();return [{__autojs6JavaHandle:'late-1',className:'T'},[{__autojs6JavaHandle:'late-2',className:'T'}]];
  });
  await assert.rejects(java.callStatic('T','f',[],{signal:abort.signal}),{code:'ABORT_ERR'});assert.deepEqual(released,['late-1','late-2']);
});
test('special floating results and big integer fields are decoded without JSON loss',async()=>{
  const java=factory(async()=>({__autojs6JavaNumber:'NaN'}));assert(Number.isNaN(await java.getStatic('T','NAN')));
  assert.equal(await factory(async()=>({__autojs6JavaNumber:'Infinity'})).getStatic('T','INF'),Infinity);
});
test('cancelling the JavaScript wait retains the native reply for late-handle cleanup',async()=>{
  const controller=new AbortController(),released=[];let finish;
  const java=factory((_,method,args,options)=>{
    if(method==='release'){released.push(args[0].handle);return Promise.resolve(true);}
    return new Promise((resolve,reject)=>{finish=resolve;options.signal?.addEventListener('abort',()=>reject(Object.assign(new Error('transport aborted'),{code:'ABORT_ERR'})),{once:true});});
  });
  const pending=java.getContext({signal:controller.signal});controller.abort();await assert.rejects(pending,{code:'ABORT_ERR'});
  finish({__autojs6JavaHandle:'late-context',className:'android.content.Context'});
  await new Promise(resolve=>setTimeout(resolve,0));assert.deepEqual(released,['late-context']);
});

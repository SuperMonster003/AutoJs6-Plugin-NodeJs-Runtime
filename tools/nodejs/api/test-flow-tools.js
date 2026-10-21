const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(process.env.AUTOJS6_FLOW_SOURCE || path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const block = source.slice(source.indexOf('  // Shared flow core.'), source.indexOf('  // End shared flow core.'));
const factory = new Function(block + ';return __autojs6_create_flow;')();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const node = {windowId:4,indexInParent:0,depth:1,packageName:'fixture',className:'Button',id:'fixture:id/a',text:'ready',desc:'',bounds:{left:0,top:0,right:10,bottom:10}};
function fixture(handler, options = {}) {
  const calls=[], packets=[], cancelled=new Set(), answers=new Map();let closed=0, sequence=0;
  const emit=(id,type,value)=>{
    if(cancelled.has(id))return;
    const bytes=Buffer.from(JSON.stringify({type,value}));
    for(let offset=0;offset<bytes.length;offset+=options.chunk??4096){const end=Math.min(bytes.length,offset+(options.chunk??4096));packets.push({id,data:bytes.subarray(offset,end).toString('base64'),last:end===bytes.length});}
  };
  const invoke=async(id,callback,args)=>{
    const token=String(++sequence), result=new Promise((resolve,reject)=>answers.set(token,{resolve,reject,id}));
    emit(id,'callback',{token,callback:callback.__flowCallback,args});return result;
  };
  const call=async(module,method,args,opts)=>{
    calls.push({module,method,args,opts});
    if(module==='accessibility')return {available:true,value:node};
    assert.equal(module,'flow');
    if(method==='open'){await options.openGate;return {id:'client'};}
    assert.equal(args[0],'client');
    if(method==='close'){closed++;return true;}
    if(method==='poll')return packets.splice(0,3).filter(p=>args[1].includes(p.id));
    if(method==='cancel'){cancelled.add(args[1]);for(let i=packets.length-1;i>=0;i--)if(packets[i].id===args[1])packets.splice(i,1);return true;}
    if(method==='answer'){
      const answer=answers.get(args[2]);if(!answer)return false;answers.delete(args[2]);
      if(args[3])answer.resolve(args[4]);else answer.reject({name:'Error',message:'callback',callbackError:args[2]});return true;
    }
    assert.equal(method,'start');
    await options.startGate;
    if(cancelled.has(args[1]))return false;
    Promise.resolve().then(()=>handler({id:args[1],name:args[2],args:args[3],options:args[4],budget:args[5],emit:(type,value)=>emit(args[1],type,value),invoke:(cb,values=[])=>invoke(args[1],cb,values)}))
      .then(value=>emit(args[1],'resolve',value),error=>emit(args[1],'reject',error));
    return true;
  };
  const flow=factory(call,undefined,require('node:async_hooks').AsyncLocalStorage);
  return {flow,calls,cancelled,answers,get closed(){return closed;}};
}

test('Advanced tools preserve Unicode across byte splits and capture options at creation', async()=>{
  const text='\u4f60\u597d\ud83d\ude03'.repeat(10000);
  const f=fixture(async task=>{assert.equal(task.name,'collectList');assert.equal(task.options.maxItems,2);return [text,{__flowNode:node}];},{chunk:4093});
  try {
    const opts={maxItems:2,timeout:4000};const task=f.flow.collectList(null,{textContains:'row'},opts);opts.maxItems=8;
    const result=await task;assert.equal(result[0],text);assert.deepEqual(result[1],node);
    assert.equal(f.calls.find(c=>c.method==='start').args[3][0],null);
  }finally{await f.flow.dispose();}
  assert.equal(f.closed,1);
});
test('Callbacks may asynchronously reenter native tools without blocking the poll pump', async()=>{
  let f;f=fixture(async task=>{
    if(task.name==='findAny')return {index:0,node:{__flowNode:node},target:task.args[0][0]};
    assert.equal(task.name,'smartClick');const verified=await task.invoke(task.options.verify);
    return {ok:true,method:'node',target:{__flowNode:node},verified};
  });
  try {
    const result=await f.flow.smartClick('ready',{gestureFallback:false,verify:async()=>{await delay(1);return (await f.flow.findAny(['ready'])).node;}});
    assert.deepEqual(result.verified,node);assert.deepEqual(f.calls.find(c=>c.method==='start').opts.permissions,['accessibility']);
  }finally{await f.flow.dispose();}
});
test('Callback failures retain their exact Error identity across the bridge', async()=>{
  const f=fixture(task=>task.invoke(task.options.verify));
  try {
    for(const error of [new Error('owned callback failure'),null,undefined,false,0,''])
      await assert.rejects(f.flow.smartClick('ready',{verify:()=>{throw error;}}).toPromise(),value=>value===error);
  }finally{await f.flow.dispose();}
});
test('Cancellation precedes delayed start completion and suppresses its side effects', async()=>{
  let release,ran=0;const gate=new Promise(resolve=>release=resolve),f=fixture(()=>{ran++;return true;},{startGate:gate});
  const task=f.flow.smartClick('ready');await delay(20);task.cancel();
  await assert.rejects(task.toPromise(),e=>e.code==='CANCELLED');await delay(10);release();await delay(20);
  assert.equal(ran,0);assert.equal(f.cancelled.size,1);assert.equal(f.flow.workers,0);await f.flow.dispose();
});
test('Disposal closes a client whose open reply arrives after cancellation',async()=>{
  let release;const gate=new Promise(resolve=>release=resolve),f=fixture(()=>true,{openGate:gate});
  const task=f.flow.findAny(['ready']);await delay(2);const disposed=f.flow.dispose();release();await disposed;
  await assert.rejects(task.toPromise(),e=>e.code==='CANCELLED');assert.equal(f.closed,1);assert(!f.calls.some(c=>c.method==='start'));
});
test('Operation timeout cancels a callback and its owned Flow, including asynchronous continuation',async()=>{
  const f=fixture(task=>task.invoke(task.options.verify));let child;
  try {
    const task=f.flow.smartClick('ready',{timeout:60,verify:async()=>{await delay(5);child=f.flow.sleep(10000);return child;}});
    await assert.rejects(task.toPromise(),e=>e.code==='TIMEOUT');await delay(25);
    assert.equal(child.state,'cancelled');assert.equal(f.flow.workers,0);assert(f.cancelled.size>0);
  }finally{await f.flow.dispose();}
});
test('Event ready follows native subscription and cancellation rejects a not-yet-ready wait',async()=>{
  let fire;const f=fixture(async task=>{assert.equal(task.name,'waitForToast');task.emit('ready',null);return new Promise(resolve=>fire=()=>resolve({text:'ready'}));});
  try {
    const task=f.flow.waitForToast({text:/ready/i},2000);await task.ready;fire();assert.equal((await task).text,'ready');
    const call=f.calls.find(c=>c.method==='start');assert.deepEqual(call.opts.permissions,['accessibility','events','events.toast']);assert.deepEqual(call.args[4].filter.text,{pattern:'ready',flags:'i'});
    const cancelled=f.flow.sleep(1000).waitForToast('later');cancelled.cancel();
    await assert.rejects(cancelled.ready,e=>e.code==='CANCELLED');await assert.rejects(cancelled.toPromise(),e=>e.code==='CANCELLED');
  }finally{await f.flow.dispose();}
});
test('Node actions, click aliases, strict defaults and extra permissions use explicit action targets',async()=>{
  const f=fixture(task=>{assert.equal(task.name,'nodeAction');return task.args[0];});
  try {
    f.flow.defaults({strictActions:false,humanize:{delay:[1,2]}});
    const task=f.flow.waitThenClick('ready',{timeout:2000});f.flow.defaults({strictActions:true});assert.deepEqual(await task,node);
    let start=f.calls.find(c=>c.method==='start');assert.equal(start.args[4].strict,false);assert.deepEqual(start.args[3][0],{__flowNode:node});
    await f.flow.of(node).paste();await f.flow.of(node).clickBounds(1,2);
    const starts=f.calls.filter(c=>c.method==='start');assert(starts[1].opts.permissions.includes('clipboard'));assert(starts[2].opts.permissions.includes('accessibility.gesture'));
    assert.equal(f.flow.clickWait,f.flow.waitThenClick);assert.equal(f.flow.clickWhenStable,f.flow.waitForStableThenClick);
    assert.throws(()=>f.flow.defaults({humanize:{delay:[3,1]}}));
  }finally{await f.flow.dispose();}
});
test('Chain tool argument positions and popup/idle pass-through match Flow semantics',async()=>{
  const seen=[],f=fixture(task=>{seen.push(task);if(task.name==='waitForIdle')task.emit('ready',null);return null;});
  try {
    await f.flow.of(node).typeInto('value');await f.flow.of(node).collectList('row');await f.flow.of(node).scrollUntil('row');
    const value={keep:true};assert.deepEqual(await f.flow.of(value).dismissPopups(['close'],{click:{gestureFallback:false}}),value);
    assert.deepEqual(await f.flow.of(value).waitForIdle({quietFor:0,timeout:1000}),value);
    assert.deepEqual(seen[0].args,[{__flowNode:node},'value']);assert.deepEqual(seen[1].args,[{__flowNode:node},'row']);assert.deepEqual(seen[2].options.container,{__flowNode:node});
  }finally{await f.flow.dispose();}
});
test('Infinite deadlines are encoded explicitly and transport rejection is preserved',async()=>{
  const f=fixture(task=>{assert.deepEqual(task.budget,{__flowNumber:'Infinity'});assert.deepEqual(task.options.timeout,{__flowNumber:'Infinity'});return false;});
  try {f.flow.defaults({timeout:Infinity});assert.equal(await f.flow.clickIfExists('absent',{gestureFallback:false}),false);}
  finally{await f.flow.dispose();}
  const error=Object.assign(new Error('not granted'),{code:'NOT_GRANTED'}),flow=factory(()=>Promise.reject(error));
  await assert.rejects(flow.findAny(['ready']).toPromise(),e=>e===error);await flow.dispose();
});

test('Invalid delayed-click arguments do not create an unreachable polling chain',()=>{
  const flow=factory(()=>{throw Error('No host request expected');});
  for(const delay of [undefined,-1,[3,2],[1,2,3]])assert.throws(()=>flow.clickWhenStableAfter('ready',{delay}));
  assert.equal(flow.pending,0);
});

test('Gesture actions preserve their input and callbacks, snapshot arguments and require only gesture capability',async()=>{
  const seen=[], f=fixture(task=>{seen.push(task);return true;});
  try {
    const point=[10,20], original={token:'kept'};
    const pending=f.flow.of(original).press(point,100,value=>({...value,callback:true}));point[0]=99;
    assert.deepEqual(await pending,{token:'kept',callback:true});assert.deepEqual(seen[0].args,['press',[[10,20],100]]);
    assert.strictEqual(await f.flow.of(original).swipe([10,20],[30,40],200),original);
    assert.strictEqual(await f.flow.of(original).gesture(200,[[10,20],[30,40]]),original);
    assert.strictEqual(await f.flow.of(original).gestures([200,[10,20],[30,40]],[50,100,[40,50]]),original);
    for(const call of f.calls.filter(call=>call.method==='start'))assert.deepEqual(call.opts.permissions,['accessibility','accessibility.gesture']);
    assert.throws(()=>f.flow.of(0).press());
  }finally{await f.flow.dispose();}
});

test('Scoped tools inherit the chain root while explicit root wins and projections stay local to waits',async()=>{
  const seen=[], f=fixture(task=>{seen.push(task);return null;});
  try {
    const other={...node,indexInParent:1}, scoped=f.flow.of(node).scope({root:node,compass:'p',resultType:'text'});
    await scoped.findAny(['child']);assert.deepEqual(seen[0].options.root,{__flowNode:node});assert.equal(seen[0].options.compass,undefined);
    await scoped.findAny(['child'],{root:other});assert.deepEqual(seen[1].options.root,{__flowNode:other});
    await f.flow.of([node,other]).click();assert.deepEqual(seen[2].args[0],[{__flowNode:node},{__flowNode:other}]);
  }finally{await f.flow.dispose();}
});

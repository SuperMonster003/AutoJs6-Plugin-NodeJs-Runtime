// Rhino parity of the automation family (api-coverage P3.4): automator's argument forms with setScreenMetrics scaling and the
// capabilities of the host's RuntimeAutomationBridge routes (Q178 (a)), auto's service, windows, flags and event sessions,
// D-1 auto.waitFor (Q179), Rhino's events module members on autojs6:events (sticky events, key names, key interception,
// touches, broadcast), threads / continuation / timers (D-6 / D-8, Q175 (a) / Q176 (a)), yolo detectors, ocr modes, the
// global sleep / wait and selector functions, the family's globals (Q177 (a)) and the main thread / Worker wiring.
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events'),{AsyncLocalStorage}=require('node:async_hooks'),{MessageChannel}=require('node:worker_threads'),{pathToFileURL}=require('node:url');
const root=path.join(__dirname,'../../..');
const source=fs.readFileSync(path.join(root,'app/src/main/cpp/node_bridge_sources.cpp'),'utf8').replace(/\r\n/g,'\n');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a,first);return source.slice(a,z);}
const createAutomation=new Function('return ('+part('  function __autojs6_create_automation(','\n  // api-coverage P3.4: the main thread\'s automation family.')+')')();
const createHostEvents=new Function('return ('+part('  function __autojs6_create_host_events(','\n  function __autojs6_create_notifications(')+')')();
const createSystem=new Function('return ('+part('  function __autojs6_create_system(','\n  function __autojs6_create_app(')+')')();
const tick=(ms=0)=>new Promise(resolve=>setTimeout(resolve,ms));

// A fake host: answers[module.method] is a value or a function of the arguments; every call is recorded.
function fake(answers={}){
 const calls=[];
 const call=async(module,method,args,options={})=>{
  calls.push({module,method,args,options});
  const answer=answers[module+'.'+method];
  return typeof answer==='function'?answer(...args):answer;
 };
 return {calls,call,last:()=>calls[calls.length-1],find:(module,method)=>calls.filter(c=>c.module===module&&c.method===method)};
}
const identity={x:v=>v,y:v=>v};

test('automator taps, presses, swipes and gestures follow Rhino argument forms with setScreenMetrics scaling',async()=>{
 const {call,last}=fake({'automator.tap':true,'automator.press':true,'automator.gestures':true,'automator.targetAction':true});
 const a=createAutomation(call,{scale:()=>({x:v=>v*2,y:v=>v*3})}).automator;
 const sent=()=>[last().method,last().args,last().options.permissions];
 assert.equal(await a.click(10,20),true);
 assert.deepEqual(sent(),['tap',[20,60,'click'],['accessibility']]);
 await a.click([5]);assert.deepEqual(last().args,[10,15,'click']);
 await a.click({x:1,y:2});assert.deepEqual(last().args,[2,6,'click']);
 await a.click({left:0,top:0,right:10,bottom:20});assert.deepEqual(last().args,[10,30,'click']);
 await a.longClick({bounds:{left:0,top:0,right:10,bottom:20},text:'node'});assert.deepEqual(last().args,[10,30,'longClick']);
 await a.longClick('OK');assert.deepEqual(sent(),['targetAction',['longClick',{text:'OK',index:-1}],['accessibility']]);
 await a.click('OK',2);assert.deepEqual(last().args,['click',{text:'OK',index:2}]);
 await a.click(1,2,3,4);assert.deepEqual(last().args,['click',{bounds:[1,2,3,4]}]);
 await a.press(1,2);assert.deepEqual(sent(),['press',[2,6,100],['accessibility','accessibility.gesture']]);
 await a.press(500,[1,2]);assert.deepEqual(last().args,[2,6,500]);
 await a.press([1,2],300);assert.deepEqual(last().args,[2,6,300]);
 await a.press(1,2,50);assert.deepEqual(last().args,[2,6,50]);
 const swipe=[[[0,300,[[0,0],[20,30]]]],false];
 for(const args of [[0,0,10,10,300],[[0,0],[10,10],300],[300,[0,0],[10,10]],[300,[[0,0],[10,10]]],[[0,0,10,10],300]]){
  await a.swipe(...args);assert.deepEqual(sent(),['gestures',swipe,['accessibility','accessibility.gesture']],JSON.stringify(args));
 }
 await a.gesture(100,[0,0],[5,5]);assert.deepEqual(last().args,[[[0,100,[[0,0],[10,15]]]],false]);
 await a.gesture(100,[0,0,5,5]);assert.deepEqual(last().args,[[[0,100,[[0,0],[10,15]]]],false]);
 await a.gestures([0,100,[0,0],[1,1]],[50,100,[2,2],[3,3]]);
 assert.deepEqual(last().args,[[[0,100,[[0,0],[2,3]]],[50,100,[[4,6],[6,9]]]],false]);
 await a.gestures([100,[0,0],[1,1]]);assert.deepEqual(last().args,[[[0,100,[[0,0],[2,3]]]],false]);
 await assert.rejects(a.click(),TypeError);
 await assert.rejects(a.press('x'),TypeError);
 await assert.rejects(a.gesture(100,[0,0,1]),TypeError);
});

test('the *Async gestures return at once and report to their callback',async()=>{
 const {call,last}=fake({'automator.gestures':false});
 const a=createAutomation(call,{scale:()=>identity}).automator;
 const seen=[];
 assert.equal(await a.gestureAsync(100,[0,0],[1,1],ok=>seen.push(ok)),undefined);
 assert.equal(last().args[1],false);
 await tick(5);assert.deepEqual(seen,[false]);
 await a.gesturesAsync([100,[0,0],[1,1]],{onCompleted:()=>seen.push('done'),onCancelled:()=>seen.push('cancelled')});
 await tick(5);assert.deepEqual(seen,[false,'cancelled']);
 await a.gesturesAsync([100,[0,0],[1,1]]);assert.equal(last().args[1],true);
});

test('global actions, scrolls, text input and the service calls reach the host with Rhino capabilities',async()=>{
 const {call,last,find}=fake({'automator.globalAction':true,'automator.scroll':true,'automator.targetAction':true,'automator.editText':true,
  'automator.switchToInputMethod':true,'automator.isServiceRunning':true,'accessibility.captureScreenImage':{id:'img-1',width:1,height:2}});
 const a=createAutomation(call).automator;
 for(const name of ['back','home','recents']){await a[name]();assert.deepEqual([last().args,last().options.permissions],[[name],['accessibility']]);}
 for(const name of ['notifications','quickSettings','powerDialog','splitScreen','lockScreen','takeScreenshot','headsetHook','accessibilityButton',
  'accessibilityButtonChooser','accessibilityShortcut','accessibilityAllApps','dismissNotificationShade']){
  await a[name]();assert.deepEqual([last().args,last().options.permissions],[[name],['keys']]);
 }
 await a.headsethook();assert.deepEqual(last().args,['headsetHook']);
 await a.scrollUp();assert.deepEqual([last().method,last().args],['scroll',['up',-1]]);
 await a.scrollDown(2);assert.deepEqual(last().args,['down',2]);
 await a.scrollUp('list');assert.deepEqual([last().method,last().args],['targetAction',['scrollUp',{text:'list',index:-1}]]);
 await a.scrollDown(0,0,9,9);assert.deepEqual(last().args,['scrollDown',{bounds:[0,0,9,9]}]);
 await a.input('x');assert.deepEqual([last().method,last().args],['editText',['append',-1,'x']]);
 await a.setText(1,'y');assert.deepEqual(last().args,['set',1,'y']);
 await assert.rejects(a.setText(),TypeError);
 assert.equal(await a.switchToInputMethod('com.ime'),true);
 assert.equal(await a.isServiceRunning(),true);
 const image=await a.captureScreen();
 assert.deepEqual([image.id,find('accessibility','captureScreenImage')[0].options.permissions],['img-1',['accessibility','screen_capture','image']]);
 assert(Object.isFrozen(image));
});

test('auto.waitFor / automator.waitForService enable, then poll the service (D-1, Q179)',async()=>{
 let enabled=0;
 const {call,find}=fake({'accessibility.ensureEnabled':false,'accessibility.isEnabled':()=>++enabled>=3});
 const kit=createAutomation(call);
 await kit.auto.waitFor();
 assert.equal(find('accessibility','isEnabled').length,3);
 assert.deepEqual(find('accessibility','ensureEnabled')[0].args,[{prompt:false}]);
 const never=fake({'accessibility.ensureEnabled':false,'accessibility.isEnabled':false});
 const started=Date.now();
 await assert.rejects(createAutomation(never.call).automator.waitForService(250),error=>error.code==='ERR_AUTOJS6_TIMEOUT');
 assert(Date.now()-started>=200);
 await assert.rejects(createAutomation(never.call).auto.waitFor('x'),TypeError);
});

test('retry reruns with Rhino options',async()=>{
 const {retry}=createAutomation(async()=>null).automator;
 let runs=0;
 assert.equal(await retry(attempt=>{runs++;if(attempt<2)throw new Error('again');return 'ok';},{times:3,delay:0}),'ok');
 assert.equal(runs,3);
 runs=0;
 await assert.rejects(retry(()=>{runs++;throw new Error('no');},{times:1,delay:0}),/no/);
 assert.equal(runs,2);
 await assert.rejects(retry(()=>1,{backoff:0.5}),RangeError);
 await assert.rejects(retry(null),TypeError);
});

test('auto keeps Rhino mode, flags and window filter for its window queries',async()=>{
 const windows=[{id:1,type:'application',packageName:'com.a',title:'A',active:true,focused:true,layer:2,displayId:0},
  {id:2,type:'inputMethod',packageName:'com.ime',title:null,active:false,focused:false,layer:3,displayId:0}];
 const {call,last,find}=fake({'auto.windows':windows,'auto.roots':(mode,fallback,ids)=>ids?ids.map(id=>({windowId:id})):[{windowId:1},{windowId:2}],
  'auto.state':{hasInstance:true,hasService:true,isRunning:true,isOperational:false},'auto.start':true,'auto.stop':true,'automator.ensureService':null,
  'accessibility.dump':{text:'tree'},'accessibility.explain':{found:false,text:'report'},'auto.activeRoot':{windowId:1}});
 const {auto}=createAutomation(call);
 assert.equal(typeof auto,'function');
 assert.deepEqual((await auto.findWindows({type:'application'})).map(w=>w.id),[1]);
 assert.deepEqual((await auto.findWindows({packageName:/ime/})).map(w=>w.id),[2]);
 await assert.rejects(auto.findWindows({kind:1}),RangeError);
 assert.deepEqual((await auto.findWindowRoots({type:'inputMethod'})).map(r=>r.windowId),[2]);
 assert.deepEqual(last().args,['normal',false,[2]]);
 auto.setFlags(['appWindowsFallback','!noFastPath']);
 auto.setMode('FAST');
 await auto.windowRoots;assert.deepEqual(last().args,['fast',true]);
 await auto.root;assert.deepEqual([last().method,last().args],['activeRoot',['fast',true]]);
 await auto.rootInActiveWindow;assert.deepEqual(last().args,['normal',false]);
 auto.clearFlags('appWindowsFallback');
 auto.setWindowFilter(window=>window.id===1);
 assert.deepEqual((await auto.windowRoots).map(r=>r.windowId),[1]);
 auto.setWindowFilter({type:'inputMethod'});
 assert.deepEqual((await auto.windowRoots).map(r=>r.windowId),[2]);
 auto.setWindowFilter(null);
 assert.throws(()=>auto.setMode('slow'),RangeError);
 assert.throws(()=>auto.setFlags(['useShell','!useShell']),RangeError);
 assert.throws(()=>auto.setFlags('nope'),RangeError);
 assert.throws(()=>auto.clearFlags(3),TypeError);
 for(const name of ['service','services','windows','root','rootInActiveWindow','windowRoots','state','stats']){
  assert.equal(Object.getOwnPropertyDescriptor(auto,name).get.__autojs6LazyExport,true,name);
 }
 assert.deepEqual([await auto.exists(),await auto.isRunning(),await auto.isOperational()],[true,true,false]);
 const stats=await auto.stats;
 assert.deepEqual([stats.mode,stats.flags,stats.hasService],['fast',[],true]);
 assert.equal(await auto.start(),true);
 assert.deepEqual(last().options.permissions,['accessibility','accessibility.service']);
 assert.equal(await auto.enable(),true);assert.equal(last().method,'start');
 assert.equal(await auto.disable(),true);assert.deepEqual([last().method,last().options.permissions],['stop',['accessibility','accessibility.service']]);
 await auto();
 assert.equal(last().method,'ensureService');
 await auto('normal',true);
 assert.deepEqual(find('auto','stop').length+find('auto','start').length,5);
 assert.equal(await auto.dump('json'),'tree');
 assert.deepEqual(find('accessibility','dump')[0].args,[{format:'json'}]);
 const explained=await auto.explain({text:'OK'});
 assert.equal(String(explained),'report');
 await assert.rejects(auto.explain('OK'),TypeError);
 assert.equal(await auto.getWindowRoot(windows[0]),null);
 await assert.rejects(auto.getWindowRoot('x'),TypeError);
});

// A host events module over fake sessions: observe* answers a session record, drainEvents hands out the queued events.
function eventsHost(){
 const queues=new Map(),closed=[];let next=0;
 const host=fake({});
 const call=async(module,method,args,options={})=>{
  host.calls.push({module,method,args,options});
  if(method.startsWith('observe')||method==='interceptKeys'){const id='s'+(++next);queues.set(id,[]);return {id,source:{observeKey:'key',observeTouch:'touch',
   observeAccessibility:'a11yEvent',observeAccessibilityState:'a11yState',observeScriptBroadcasts:'scriptBroadcast',interceptKeys:'keyIntercept',
   observeNotification:'notification',observeToast:'toast'}[method]};}
  if(method==='drainEvents'){const items=queues.get(args[0])||[];queues.set(args[0],[]);return items;}
  if(method==='close'){closed.push(args[0]);return true;}
  if(method==='sendScriptBroadcast')return true;
 };
 const push=(id,...events)=>queues.get(id).push(...events.map(event=>({time:Date.now(),...event})));
 return {...host,call,push,closed,last:()=>host.calls[host.calls.length-1]};
}
const timer={setTimeout(){}};

test('autojs6:events is Rhino\'s events module: EventEmitter members, sticky events, emitter and getTimer',async()=>{
 const events=createHostEvents(async()=>null,EventEmitter,undefined,undefined,{timer:()=>timer});
 const seen=[];
 assert.equal(events.emit('error',new Error('x')),false);
 events.emitSticky('ready',1,2);
 events.on('ready',(...args)=>seen.push(['on',...args]));
 events.once('ready',()=>seen.push(['once']));
 assert.deepEqual(seen,[['on',1,2],['once']]);
 assert.equal(events.listenerCount('ready'),1);
 events.prependListener('tick',()=>seen.push('first'));
 events.addListener('tick',()=>seen.push('second'));
 events.prependListener('tick',()=>seen.push('zero'));
 assert.equal(events.emit('tick'),true);
 assert.deepEqual(seen.slice(2),['zero','first','second']);
 assert.deepEqual(events.eventNames().sort(),['ready','tick']);
 assert.equal(events.listeners('tick').length,3);
 assert.equal(events.setMaxListeners(20),events);assert.equal(events.getMaxListeners(),20);
 events.removeAllListeners('tick');assert.equal(events.listenerCount('tick'),0);
 assert.equal(events.getTimer(),timer);
 const emitter=events.emitter();
 emitter.emitSticky('go',7);
 let got;emitter.on('go',value=>{got=value;});
 assert.equal(got,7);assert.equal(emitter.getTimer(),timer);
 for(const name of ['addListener','broadcast','emit','emitSticky','emitter','eventNames','getMaxListeners','getTimer','getTouchEventTimeout','listenerCount',
  'listeners','observeTouch','onKeyDown','onKeyUp','onNotification','onToast','onTouch','onceKeyDown','onceKeyUp','prependListener','prependOnceListener',
  'removeAllKeyDownListeners','removeAllKeyUpListeners','removeAllListeners','removeAllTouchListeners','removeListener','removeNotificationObserver',
  'removeToastObserver','setKeyInterceptionEnabled','setMaxListeners','setTouchEventTimeout']) assert.equal(name in events,true,name);
 assert.equal(Object.keys(events).includes('__autojs6Observe'),false);
});

test('key events reach Rhino\'s key names and key interception opens a consuming session',async()=>{
 const host=eventsHost();
 const events=createHostEvents(host.call,EventEmitter);
 const seen=[];
 events.onKeyDown('volume_up',event=>seen.push(['down#',event.keyCode]));
 events.on('key_down',(keyCode,event)=>seen.push(['key_down',keyCode,event.action]));
 events.on('volume_up',event=>seen.push(['name',event.action]));
 const sub=await events.observeKey({drainIntervalMs:25});
 assert.deepEqual(host.last().options.permissions,['events','events.key']);
 host.push(sub.id,{type:'key',key:'volume_up',keyCode:24,action:'down'},{type:'key',key:'volume_up',keyCode:24,action:'up'});
 await tick(120);
 assert.deepEqual(seen,[['name','down'],['down#',24],['key_down',24,'down'],['name','up']]);
 events.removeAllKeyDownListeners('volume_up');
 assert.equal(events.listenerCount('__key_down__#volume_up'),0);
 await events.setKeyInterceptionEnabled('VOLUME_UP',true);
 assert.deepEqual([host.last().method,host.last().args,host.last().options.permissions],
  ['interceptKeys',[{all:false,keys:['volume_up']}],['events','events.key','events.key.intercept']]);
 await events.setKeyInterceptionEnabled(true);
 assert.deepEqual(host.last().args,[{all:true,keys:['volume_up']}]);
 await events.setKeyInterceptionEnabled(false);
 await events.setKeyInterceptionEnabled('volume_up',false);
 assert.equal(host.last().method,'close');
 await events.close();
});

test('touches follow the touch event timeout and broadcasts reach every script',async()=>{
 const host=eventsHost();
 const events=createHostEvents(host.call,EventEmitter);
 const touches=[];
 events.onTouch(point=>touches.push([point.x,point.y]));
 assert.equal(events.getTouchEventTimeout(),10);
 events.setTouchEventTimeout(1000);
 assert.throws(()=>events.setTouchEventTimeout(-1),Error);
 const sub=await events.observeTouch({drainIntervalMs:25});
 assert.deepEqual([host.last().args,host.last().options.permissions],[[{timeout:0}],['events','events.touch']]);
 host.push(sub.id,{type:'touch',x:1,y:2},{type:'touch',x:3,y:4});
 await tick(120);
 assert.deepEqual(touches,[[1,2]]);
 events.removeAllTouchListeners();
 assert.equal(await events.broadcast.emit('hi',1,{a:2}),true);
 assert.deepEqual([host.last().method,host.last().args,host.last().options.permissions],['sendScriptBroadcast',['hi',[1,{a:2}]],['events']]);
 const heard=[];
 events.broadcast.on('hi',(...args)=>heard.push(args));
 await tick(10);
 const session=host.calls.find(c=>c.method==='observeScriptBroadcasts');
 assert(session);
 host.push('s2',{type:'broadcast',name:'hi',args:[5,'x']});
 await tick(220);
 assert.deepEqual(heard,[[5,'x']]);
 // No listener left: the session closes (it would keep the execution alive); listening again opens a new one.
 events.broadcast.removeAllListeners('hi');
 await tick(10);
 assert(host.closed.includes('s2'));
 events.broadcast.once('bye',(...args)=>heard.push(args));
 await tick(10);
 assert.equal(host.calls.filter(c=>c.method==='observeScriptBroadcasts').length,2);
 host.push('s3',{type:'broadcast',name:'bye',args:[6]});
 await tick(220);
 assert.deepEqual(heard,[[5,'x'],[6]]);
 assert(host.closed.includes('s3'));
 const notes=await events.observeNotification({drainIntervalMs:25});
 await events.removeNotificationObserver();
 assert(host.closed.includes(notes.id));
 await events.close();
});

test('a broadcast emitted right after the first on() waits for the opening session',async()=>{
 const order=[];
 const call=async(module,method,args)=>{
  order.push(method);
  if(method==='observeScriptBroadcasts'){await tick(50);order.push('observed');return {id:'b1',source:'scriptBroadcast'};}
  if(method==='drainEvents')return [];
  return true;
 };
 const events=createHostEvents(call,EventEmitter);
 events.broadcast.on('x',()=>{});
 assert.equal(await events.broadcast.emit('x',1),true);
 assert.deepEqual(order.slice(0,3),['observeScriptBroadcasts','observed','sendScriptBroadcast']);
 events.broadcast.removeAllListeners();
 await events.close();
});

test('auto.registerEvent and stateListener open accessibility sessions with Rhino\'s distinct / debounce',async()=>{
 const host=eventsHost();
 const events=createHostEvents(host.call,EventEmitter);
 const {auto}=createAutomation(host.call,{events:()=>events});
 const seen=[];
 await auto.registerEvent('window_state_changed',event=>seen.push(event.className),{distinct:true});
 const opened=host.calls.find(c=>c.method==='observeAccessibility');
 assert.deepEqual([opened.args,opened.options.permissions],[[{names:['window_state_changed']}],['accessibility']]);
 host.push('s1',{type:'accessibility',name:'window_state_changed',packageName:'p',className:'A'},
  {type:'accessibility',name:'window_state_changed',packageName:'p',className:'A'},{type:'accessibility',name:'window_state_changed',packageName:'p',className:'B'});
 await tick(250);
 assert.deepEqual(seen,['A','B']);
 const states=[];
 await auto.stateListener({onConnected:()=>states.push('up'),onDisconnected:()=>states.push('down')});
 host.push('s2',{type:'connected'},{type:'disconnected'});
 await tick(250);
 assert.deepEqual(states,['up','down']);
 await auto.removeEvent('window_state_changed');
 assert(host.closed.includes('s1'));
 await auto.stateListener(null);
 assert(host.closed.includes('s2'));
 await assert.rejects(auto.registerEvent('x',()=>{},{distinct:'all'}),RangeError);
 await events.close();
});

test('threads: same-thread tasks, pools, shared atomic and lock, disposable and sync (Q175 (a) / Q176 (a))',async()=>{
 const {threads}=createAutomation(async()=>null,{AsyncLocalStorage});
 const order=[];
 const task=threads.start(async signal=>{order.push(['run',threads.currentThread().name,signal.aborted]);await tick(5);});
 order.push('after start');
 assert.equal(task.isAlive(),true);
 await task.waitFor();
 assert.equal(await task.join(),true);
 assert.deepEqual(order,['after start',['run',task.name,false]]);
 assert.equal(task.isAlive(),false);
 assert.equal(threads.currentThread().name,'main');
 const long=threads.start(signal=>new Promise((resolve,reject)=>signal.addEventListener('abort',()=>reject(Object.assign(new Error('x'),{name:'AbortError'})))));
 await tick(1);threads.interrupt(long);
 assert.equal(await long.join(100),true);
 assert.throws(()=>threads.start(1),TypeError);
 let running=0,peak=0;
 const pool=threads.pool({maxPoolSize:2});
 const jobs=[1,2,3,4].map(n=>pool.submit(async()=>{running++;peak=Math.max(peak,running);await tick(10);running--;return n*2;}));
 assert.deepEqual(await Promise.all(jobs),[2,4,6,8]);
 assert.equal(peak,2);
 pool.shutdown();assert.equal(pool.isShutdown(),true);assert.equal(await pool.awaitTermination(100),true);
 assert.throws(()=>pool.submit(()=>1),/shut down/);
 assert.throws(()=>threads.pool({corePoolSize:-1}),RangeError);
 assert.throws(()=>threads.pool({corePoolSize:3,maxPoolSize:2}),RangeError);
 threads.pool({corePoolSize:1}).shutdown();
 const counter=threads.atomic(5);
 assert.deepEqual([counter.incrementAndGet(),counter.getAndAdd(10),counter.get(),counter.compareAndSet(16,1),counter.decrementAndGet(),String(counter)],[6,6,16,true,0,'0']);
 const {port1,port2}=new MessageChannel();
 const received=new Promise(resolve=>port2.once('message',buffer=>{threads.atomic(buffer).addAndGet(41);resolve();}));
 port1.postMessage(counter.buffer);await received;port1.close();
 assert.equal(counter.get(),41);
 const lock=threads.lock(),shared=threads.lock(lock.buffer);
 await lock.lock();
 assert.equal(await shared.tryLock(),false);assert.equal(await shared.tryLock(20),false);
 setTimeout(()=>lock.unlock(),10);
 assert.equal(await shared.tryLock(500),true);assert.equal(lock.isLocked(),true);
 shared.unlock();assert.throws(()=>shared.unlock(),/not locked/);
 const box=threads.disposable();
 setTimeout(()=>box.setAndNotify('v'),5);
 assert.equal(await box.blockedGet(),'v');
 assert.equal(await threads.disposable().blockedGet(10),null);
 assert.equal(await threads.disposable().blockedGetOrThrow(Error,10,'fallback'),'fallback');
 class Stop extends Error {}
 const waiting=threads.start(async()=>threads.disposable().blockedGetOrThrow(Stop));
 const result=new Promise(resolve=>{const original=console.error;console.error=message=>{console.error=original;resolve(message);};});
 await tick(1);waiting.interrupt();
 assert.match(await result,/Thread-/);
 const steps=[];
 const serial=threads.sync(async n=>{steps.push('in'+n);await tick(5);steps.push('out'+n);},{});
 await Promise.all([serial(1),serial(2)]);
 assert.deepEqual(steps,['in1','out1','in2','out2']);
 const locked=threads.sync(async n=>{steps.push(n);},lock);
 await Promise.all([locked('a'),locked('b')]);assert.equal(lock.isLocked(),false);
 threads.shutDownAll();
});

test('continuation and timers: resumable promises, keepAlive, setIntervalExt and loop (D-8, Q179)',async()=>{
 const warnings=[];
 const {continuation,timers}=createAutomation(async()=>null,{console:{warn:message=>warnings.push(message),error(){}}});
 assert.equal(continuation.enabled,true);
 const creator=continuation.create();
 setTimeout(()=>creator.resume(3),1);
 assert.equal(await creator.await(),3);
 const failing=continuation.create();failing.resumeError(new Error('bad'));
 await assert.rejects(failing.await(),/bad/);
 assert.equal(await continuation.await(Promise.resolve(9)),9);
 await continuation.delay(1);
 const alive=timers.keepAlive();
 assert.equal(typeof alive.hasRef,'function');clearInterval(alive);
 clearTimeout(timers.keepAlive(1000));
 let runs=0;
 const done=new Promise(resolve=>timers.setIntervalExt(()=>{runs++;},5,()=>runs>=3?'enough':false,value=>resolve(value)));
 assert.equal(await done,'enough');assert.equal(runs,3);
 const timed=new Promise(resolve=>timers.setIntervalExt(()=>{},5,30,value=>resolve(value)));
 assert.equal(await timed,true);
 assert.throws(()=>timers.setIntervalExt(1),TypeError);
 timers.loop();timers.loop();
 assert.equal(warnings.length,1);
});

test('yolo.load opens a detector session; Rhino\'s YOLO_* codes reach error.code',async()=>{
 const {call,last}=fake({'yolo.load':{id:'yolo-1',component:'p/c'},'yolo.detect':[{classId:1,label:'cat',confidence:0.9,bounds:{left:1,top:2,right:3,bottom:4}}],'yolo.close':true});
 const {yolo}=createAutomation(call);
 const detector=await yolo.load('/sdcard/model',{component:'p/c',threads:2});
 assert.deepEqual([last().args,last().options.permissions],[['/sdcard/model',{component:'p/c',threads:2}],['yolo','image']]);
 const found=await detector.detect({__autojs6ImageHandle:'img-9',width:4,height:4},{confidence:0.5});
 assert.deepEqual([last().args,found[0].label,Object.isFrozen(found[0].bounds)],[['yolo-1','img-9',{confidence:0.5}],'cat',true]);
 await assert.rejects(detector.detect(null),error=>error.code==='YOLO_INVALID_ARGUMENT');
 assert.equal(await detector.close(),true);assert.deepEqual(last().options.permissions,['yolo']);
 assert.equal(await detector.close(),false);assert.equal(detector.closed,true);
 const failing=createAutomation(async()=>{throw Object.assign(new Error('provider failed: YOLO_MODEL_INVALID: modelDir must contain model.json'),{code:'ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED'});});
 await assert.rejects(failing.yolo.load('/x',{component:'p/c'}),error=>error.code==='YOLO_MODEL_INVALID');
});

test('ocr keeps Rhino\'s mode, tap and the fixed-engine modules over a base',async()=>{
 const calls=[];
 const base={recognize:async(image,options)=>{calls.push(['recognize',options]);return [];},recognizeText:async(image,options)=>{calls.push(['recognizeText',options]);return 'text';},
  detectTextBounds:async(image,options)=>{calls.push(['detectTextBounds',options]);return [];}};
 const ocr=createAutomation(async()=>null).rhinoOcr(base);
 assert.equal(ocr.mode,'auto');
 assert.equal(await ocr.recognizeText('img'),'text');assert.deepEqual(calls.pop(),['recognizeText',{}]);
 ocr.tap('Paddle');assert.equal(ocr.mode,'paddle');
 await ocr.detect('img');assert.deepEqual(calls.pop(),['detectTextBounds',{engine:'paddle-ocr'}]);
 await ocr.recognize('img',{mode:'rapid'});assert.deepEqual(calls.pop(),['recognize',{engine:'rapid-ocr'}]);
 await ocr.recognize('img',{engineId:'custom'});assert.deepEqual(calls.pop(),['recognize',{engineId:'custom'}]);
 await ocr.mlkit.detect('img');assert.deepEqual(calls.pop(),['detectTextBounds',{engine:'mlkit-ocr'}]);
 await ocr.rapid.recognizeText('img');assert.deepEqual(calls.pop(),['recognizeText',{engine:'rapid-ocr'}]);
 assert.throws(()=>ocr.tap('tesseract'),RangeError);
 ocr.mode='';assert.equal(ocr.mode,'auto');
 assert.match(ocr.summary(),/Current mode: auto/);assert.equal(String(ocr),ocr.summary());
});

test('sleep, wait and the selector functions follow Rhino; system carries sleep / wait',async()=>{
 let probes=0;
 const flow={waitAsync:(...args)=>['async',...args],wait:(target,options)=>({toPromise:async()=>{probes++;
  if(target==='missing')throw Object.assign(new Error('timeout'),{code:'TIMEOUT'});return {target,options};}})};
 const kit=createAutomation(async()=>null,{flow:()=>flow});
 const system=createSystem(async()=>null,{automation:()=>kit,EventEmitter}).system;
 const started=Date.now();await system.sleep(30,40);assert(Date.now()-started>=25);
 await assert.rejects(system.sleep('x'),TypeError);await assert.rejects(system.sleep(-1),RangeError);
 let n=0;
 assert.equal(await system.wait(()=>++n>=2,5,1),true);
 assert.equal(await system.wait(()=>false,2,1,{else:value=>'no '+value}),'no false');
 assert.equal(await system.wait('present',3,1),true);
 assert.equal(await system.wait('missing',2,1),false);
 assert.deepEqual(system.wait.async('sel',{timeout:1}),['async','sel',{timeout:1}]);
 assert.deepEqual(await kit.selector.pickup('present','p2','txt'),{target:'present',options:{timeout:0,compass:'p2',resultType:'txt'}});
 assert.equal(await kit.selector.pickup('missing'),null);
 await assert.rejects(kit.selector.detect('x'),TypeError);
 assert.equal(await kit.selector.existsOne('missing','present'),true);
 assert.equal(await kit.selector.existsAll('present','missing'),false);
 assert.equal(await kit.selector.existsAll(['present','present']),true);
 await assert.rejects(createSystem(async()=>null,{EventEmitter}).system.sleep(1),error=>error.code==='ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED');
});

test('the family\'s Rhino globals are installed as replaceable accessors (Q177 (a))',()=>{
 const kit=createAutomation(async()=>null);
 const target={click:'mine'};
 const names=kit.installGlobals(target);
 assert.deepEqual(names.sort(),['back','detect','existsAll','existsOne','gesture','gestureAsync','gestures','gesturesAsync','home','input','keepAlive','longClick',
  'loop','notifications','pickup','powerDialog','press','quickSettings','recents','retry','scrollDown','scrollUp','setText','splitScreen','swipe','sync']);
 assert.equal(target.click,'mine');
 assert.equal(target.back,kit.automator.back);
 assert.equal(target.sync,kit.threads.sync);
 target.back=1;assert.equal(target.back,1);
 assert.deepEqual({...kit.keys},{home:3,HOME:3,back:4,BACK:4,menu:82,MENU:82,volumeUp:24,volume_up:24,VOLUME_UP:24,volumeDown:25,volume_down:25,VOLUME_DOWN:25});
});

test('main threads and Workers wire the family; capabilities are declared and never implied',()=>{
 for(const name of ['automator','auto','threads','continuation','timers','yolo','selector','qrcode']){
  assert(source.includes(`"autojs6:${name}": function() {`),'factory autojs6:'+name);
 }
 const table=source.slice(source.indexOf('hostModuleMethods: {'),source.indexOf('\n          },',source.indexOf('hostModuleMethods: {')));
 for(const name of ['automator','auto','threads','continuation','timers','yolo','selector','keys','ocr','barcode','qrcode']){
  assert(new RegExp(`(^|[\\s,{])${name}\\s*:\\s*Object\\.keys\\(`).test(table),'Worker table '+name);
 }
 assert(source.includes('"automator", "auto", "threads", "continuation", "timers", "yolo", "selector", "qrcode"].includes(hostName)'),'Worker names answer only to autojs6:');
 for(const name of ['keys','ocr','barcode','qrcode']) assert(source.includes(`: name === "${name}" ? __autojs6_`),'Worker forwards '+name);
 assert(source.includes('__autojs6_create_system.toString() + ", " + __autojs6_create_automation.toString() + ");"'),'Worker preload carries the factory');
 const declares=part('  function __autojs6_bridge_declares_capability(','\n  function __autojs6_process_permission_normalize_scope(');
 for(const name of ['accessibility.service','events.key.intercept','events.touch']) assert(declares.includes(`required === "${name}"`),name);
 const manifest=fs.readFileSync(path.join(root,'app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java'),'utf8');
 const catalog=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/nodejs/node-capability-catalog.json'),'utf8'));
 for(const name of ['accessibility.service','events.key.intercept','events.touch','yolo']){
  assert(manifest.includes(`"${name}"`),'plugin capability '+name);
  assert(catalog.bridge.permissionCapabilities.some(item=>item.id===name),'catalog capability '+name);
 }
 const operations=new Map(catalog.bridge.operations.map(item=>[item.module+'.'+item.method,item.requiredCapabilities]));
 assert.deepEqual(operations.get('automator.press'),['accessibility','accessibility.gesture']);
 assert.deepEqual(operations.get('auto.start'),['accessibility','accessibility.service']);
 assert.deepEqual(operations.get('events.interceptKeys'),['events','events.key','events.key.intercept']);
 assert.deepEqual(operations.get('yolo.detect'),['yolo','image']);
});

test('CJS, ESM and nested Workers get the automation family and its globals',async()=>{
 const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
 const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
 const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
 const createWorker=new Function('return ('+constructor+')')();
 const directory=path.resolve(__dirname,'../../../build/worker-automation-tests');fs.mkdirSync(directory,{recursive:true});
 const preloadPath=path.join(directory,'preload.cjs');
 const names=preload.slice(preload.indexOf('(')+1,preload.indexOf(')')).split(',').map(name=>name.trim());
 const args=Array(names.length).fill('undefined');args[0]='require';args[1]=constructor;args[2]='()=>{}';args[3]=modules;
 args[names.indexOf('createSystem')]=part('  function __autojs6_create_system(','\n  function __autojs6_create_app(');
 args[names.indexOf('createAutomation')]=part('  function __autojs6_create_automation(','\n  // api-coverage P3.4: the main thread\'s automation family.');
 fs.writeFileSync(preloadPath,`(${preload})(${args.join(',')});`);
 const kit=createAutomation(async()=>null),system=createSystem(async()=>null,{EventEmitter,automation:()=>kit});
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},
  hostModuleMethods:{autojs:Object.keys(system.autojs),system:Object.keys(system.system),automator:Object.keys(kit.automator),auto:Object.keys(kit.auto),
   threads:Object.keys(kit.threads),continuation:Object.keys(kit.continuation),timers:Object.keys(kit.timers),yolo:Object.keys(kit.yolo),selector:Object.keys(kit.selector),
   keys:['notifications',...Object.keys(kit.keys)],ocr:['recognize','recognizeText','detectTextBounds','mode','tap']},
  hostModuleConstants:{engineRecord:{versionName:'6.8.0',versionCode:5935},device:{sdkInt:35,width:1080,height:2400}}};
 const calls=[];
 const Worker=createWorker(require,()=>config,async(module,method,args,options={})=>{calls.push([module,method,args,options.permissions]);
  return method==='tap'||method==='notifications'?true:method==='recognizeText'?'text':null;});
 const body=`(async()=>{const task=threads.start(async()=>{await sleep(1);});await task.join();ocr.tap('rapid');
  parentPort.postMessage({tapped:await click(1,2),notified:await keys.notifications(),home:keys.HOME,text:await ocr.recognizeText('img'),alive:task.isAlive(),
   counter:threads.atomic(2).incrementAndGet(),enabled:continuation.enabled,types:[typeof auto,typeof auto.waitFor,typeof timers.setIntervalExt,typeof yolo.load,typeof selector.pickup],
   globals:[typeof click,typeof back,typeof sleep,typeof wait,typeof pickup,typeof sync,typeof keepAlive,typeof existsAll]});})();`;
 const cjs=`const {parentPort}=require('node:worker_threads'),automator=require('autojs6:automator'),auto=require('autojs6:auto'),threads=require('autojs6:threads'),
  continuation=require('autojs6:continuation'),timers=require('autojs6:timers'),yolo=require('autojs6:yolo'),selector=require('autojs6:selector'),keys=require('keys'),ocr=require('ocr');${body}`;
 const esm=`import {parentPort} from 'node:worker_threads';import auto from 'autojs6:auto';import threads from 'autojs6:threads';import continuation from 'autojs6:continuation';
  import timers from 'autojs6:timers';import yolo from 'autojs6:yolo';import selector from 'autojs6:selector';import keys from 'autojs6:keys';import ocr from 'autojs6:ocr';${body}`;
 const run=(code,opts)=>new Promise((resolve,reject)=>{const w=new Worker(code,{execArgv:[],...opts});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});
 const expected={tapped:true,notified:true,home:3,text:'text',alive:false,counter:3,enabled:true,types:['function','function','function','function','function'],
  globals:Array(8).fill('function')};
 for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',x=>parentPort.postMessage(x));`,{eval:true}]])
  assert.deepEqual(await run(code,opts),expected);
 assert(calls.some(c=>c[0]==='automator'&&c[1]==='tap'&&c[2].join()==='1,2,click'&&c[3].join()==='accessibility'));
 assert(calls.some(c=>c[0]==='autojs6:worker-module:keys'&&c[1]==='notifications'));
 assert(calls.some(c=>c[0]==='autojs6:worker-module:ocr'&&c[1]==='recognizeText'&&c[2][1].engine==='rapid-ocr'));
});

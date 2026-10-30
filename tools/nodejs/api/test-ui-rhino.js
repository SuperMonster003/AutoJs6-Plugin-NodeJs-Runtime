// Rhino parity of the UI family (api-coverage P3.3): the ui content view and window members (Q164 (a)), floaty, web,
// terminal and console.build of the shared UI facade (Q165 - Q168), compose's ComposeError and ComposeNode members
// (Q104 / Q106), the leveled toasts, and the CJS / ESM / nested Worker modules and globals.
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8').replace(/\r\n/g,'\n');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a,first);return source.slice(a,z);}
const uiSource=part('  function __autojs6_create_ui(','  function __autojs6_create_compose(');
const composeSource=part('  function __autojs6_create_compose(','  function __autojs6_ui_options(');
const createUi=new Function('return ('+uiSource+')')(),createCompose=new Function('return ('+composeSource+')')();
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));

// A fake host: answers['module.method'] is a value or a function of (args, options); every call is recorded.
function fake(answers={}){
 const calls=[],views=new Map();let next=0;
 const view=(type='TextView')=>{const id='view-'+ ++next,token={__autojs6ViewHandle:id,id,type};views.set(id,token);return token;};
 const call=async(module,method,args,options={})=>{
  calls.push({module,method,args,options});
  const answer=answers[module+'.'+method];
  if(answer!==undefined)return typeof answer==='function'?answer(args,options):answer;
  if(module==='ui'&&method==='findView')return /^missing/.test(args[1])?null:view();
  if(module==='ui'&&method==='drainEvents')return new Promise(()=>{});
  return null;
 };
 return {calls,call,view,find:(module,method)=>calls.filter(c=>c.module===module&&c.method===method)};
}

test('ui.layout opens the content window, expands widgets and bindings and answers root / findById synchronously',async()=>{
 const host=fake();host.call.answers=null;
 let shown=0;
 const answers={'ui.showLayout':()=>({id:'w1',rootView:host.view('FrameLayout'),replaced:shown++>0})};
 const h=fake(answers),ui=createUi(h.call);
 class Labeled extends ui.Widget{constructor(){super();this.defineAttr('label',(view,name,value)=>{this.applied=[view.id,name,value];});}
  render(){return '<text id="inner" text="w"/>';}onViewCreated(view){this.created=view.id;}}
 let instance;ui.registerWidget('labeled',function(){instance=new Labeled();return instance;});
 ui.bindingContext={who:'node'};
 assert.equal(ui.root,null);
 await ui.layout('<vertical id="box"><text id="t" text="hi {{who}}"/><labeled label="L"/></vertical>');
 const show=h.find('ui','showLayout')[0];
 assert.deepEqual(show.args[1],{contentView:true});
 assert.match(show.args[0],/text="hi node"/);
 assert.match(show.args[0],/<text id="inner" text="w"( id="__autojs6_widget_\d+")?\/>|<text id="inner"/);
 assert.doesNotMatch(show.args[0],/labeled/);
 assert.equal(ui.root.__autojs6ViewHandle,'view-1');
 const t=ui.findById('t'),box=ui.findById('box');
 assert(t&&box);assert.equal(ui.findById('@+id/t'),t);
 assert.equal(ui.findByStringId(box,'t'),t);
 assert.equal(ui.findByStringId(t,'box'),null);
 assert.equal(ui.findById('missing'),null);
 assert.equal(instance.created,ui.findById('inner').id);
 assert.deepEqual(instance.applied,[ui.findById('inner').id,'label','L']);
 // A second layout replaces the root in the same window without a second event subscription.
 await ui.layout('<frame><text id="t2"/></frame>');
 assert.equal(h.find('ui','showLayout')[1].options.events,false);
 assert(ui.findById('t2'));assert.equal(ui.findById('t'),null);
 await ui.finish();
 assert.deepEqual(h.find('ui','close').map(c=>c.args),[['w1']]);
 assert.equal(ui.root,null);
});

test('ui window members apply to the content window, back_pressed is intercepted while listened to',async()=>{
 const h=fake({'ui.showLayout':()=>({id:'w2',rootView:{__autojs6ViewHandle:'root',id:'root'},subscriptionId:'s'}),'ui.barHeight':args=>args[0]==='status'?(args[1]?60:63):130});
 let events;const ui=createUi(h.call,(result,dispatch)=>{events=dispatch;const stop=()=>{};stop.ref=()=>{};return stop;});
 await ui.statusBarColor('#123456');await ui.keepScreenOn();
 assert.equal(h.find('ui','windowControl').length,0);
 const back=[];ui.emitter.on('back_pressed',e=>{back.push(e);e.consumed=true;});
 await ui.layout('<frame/>');
 assert.deepEqual(h.find('ui','windowControl').map(c=>c.args),[['w2','interceptBack',true],['w2','statusBarColor','#123456'],['w2','keepScreenOn',true]]);
 await ui.navigationBarIconLightBy(0xff000000);
 assert.deepEqual(h.calls.at(-1).args,['w2','navigationBarIconLightBy',0xff000000]);
 const life=[];ui.emitter.on('resume',e=>life.push(e.name));
 events({type:'lifecycle',name:'resume'});events({type:'lifecycle',name:'back_pressed'});
 assert.deepEqual(life,['resume']);assert.equal(back.length,1);
 assert.equal(h.find('ui','close').length,0);
 ui.emitter.removeAllListeners('back_pressed');
 assert.deepEqual(h.calls.at(-1).args,['w2','interceptBack',false]);
 events({type:'lifecycle',name:'back_pressed'});await tick();
 assert.equal(h.find('ui','close').length,1);
 assert.equal(await ui.getStatusBarHeight(),63);assert.equal(await ui.visibleStatusBarHeight,60);assert.equal(await ui.navigationBarHeight,130);
 assert.deepEqual(h.find('ui','barHeight')[0].args,['status',false,{}]);
 await assert.rejects(ui.getNavigationBarHeight([]),TypeError);
 assert.equal(ui.R.drawable.icon,'@drawable/icon');assert.equal(ui.R.string.app_name,'@string/app_name');
 assert.equal(ui.isUiThread(),false);assert.equal(ui.post(()=>{}),true);
 assert.equal(await ui.run(()=>7),7);await assert.rejects(ui.run(()=>{throw new Error('x');}),/x/);
 await assert.rejects(ui.statusBarColor({}),TypeError);
 // Lazy exports: an ESM named binding would freeze the first root / bar height.
 for(const name of ['root','statusBarHeight','bindingContext'])assert.equal(Object.getOwnPropertyDescriptor(ui,name).get.__autojs6LazyExport,true,name);
 assert.equal(Object.keys(ui).includes('__autojs6Family'),false);
});

test('floaty windows, permissions and getClip',async()=>{
 let n=0;const h=fake({'ui.floatyWindow':()=>({id:'floaty-'+ ++n,rootView:{__autojs6ViewHandle:'fr'+n,id:'fr'+n}}),'ui.floatyRawWindow':()=>({id:'floaty-raw',rootView:{__autojs6ViewHandle:'rr',id:'rr'}}),
  'ui.floatyControl':args=>args[1]==='geometry'?{x:10,y:20,width:300,height:400}:args[1]==='isAdjustEnabled'?true:null,'ui.floatyCloseAll':2,
  'ui.overlay.hasPermission':{granted:false,checked:true},'clipboard.getText':'clip'});
 const exits=[];const {floaty}=createUi(h.call).__autojs6Family({exit:()=>exits.push(1)});
 const w=await floaty.window('<frame><text id="label"/><button id="close"/></frame>');
 const open=h.find('ui','floatyWindow')[0];
 assert.deepEqual(open.options.permissions,['ui','ui.overlay']);assert.deepEqual(open.args[1],{});
 assert(w.label&&w.findView('label')===w.label);
 assert.equal(typeof w.close,'function','a view id never hides a member');
 assert.deepEqual([await w.x,await w.y,await w.width,await w.height],[10,20,300,400]);
 assert.equal(await w.isAdjustEnabled(),true);
 await w.setPosition(1.4,2.6);assert.deepEqual(h.calls.at(-1).args,['floaty-1','setPosition',1,3]);
 await assert.rejects(w.setSize('a',1),TypeError);
 w.exitOnClose();await w.close();assert.deepEqual(exits,[1]);assert.equal(w.closed,true);
 const raw=await floaty.rawWindow('<frame/>');
 assert.deepEqual(h.find('ui','floatyRawWindow')[0].options.permissions,['ui','ui.overlay','floaty.raw']);
 await raw.setTouchable(false);assert.deepEqual(h.calls.at(-1).options.permissions,['ui','ui.overlay','floaty.touch']);
 assert.equal(raw.isAdjustEnabled,undefined);
 await raw.close();
 assert.equal(await floaty.closeAll(),2);
 assert.equal(await floaty.hasPermission(),false);assert.equal(await floaty.checkPermission(),false);
 await assert.rejects(floaty.ensurePermission(),{code:'ERR_AUTOJS6_FLOATY_PERMISSION'});
 assert.equal(h.find('ui.overlay','openPermissionSettings').length,1);
 assert.equal(await floaty.getClip(),'clip');
 await assert.rejects(floaty.window(42),TypeError);
});

test('web: ByteString, WebSocket events and queue, injectable clients',async()=>{
 const {web}=createUi(fake().call).__autojs6Family({});
 const {ByteString}=web,b=ByteString.encodeUtf8('h\u00e9');
 assert.deepEqual([b.size(),b.hex(),b.base64(),b.utf8(),String(ByteString.of(0,1)),String(ByteString.encodeUtf8('ok'))],[3,'68c3a9','aMOp','h\u00e9','[hex=0001]','[text=ok]']);
 assert(ByteString.decodeHex('68c3a9').equals(b));assert.equal(ByteString.decodeBase64('aMOp').utf8(),'h\u00e9');assert.equal(ByteString.decodeBase64('%%'),null);
 assert.throws(()=>ByteString.decodeHex('6'),RangeError);assert.equal(b.getByte(1),-61);
 // A connection of the runtime: on / send / close.
 const sockets=[];
 const connectWebSocket=async(url,options)=>{const listeners={},sent=[];const conn={url,options,sent,listeners,on(n,f){(listeners[n]||=[]).push(f);return conn;},
  async send(data){sent.push(typeof data==='string'?data:Buffer.from(data).toString('hex'));},async close(code,reason){conn.closed=[code,reason];listeners.close.forEach(f=>f({code,reason}));}};
  sockets.push(conn);return conn;};
 const family=createUi(fake().call).__autojs6Family({connectWebSocket});
 const ws=family.web.newWebSocket('wss://example.test',{headers:{a:'b'}}),events=[];
 for(const name of ['open','message','text','bytes','closing','closed','failure'])ws.on(name,(...args)=>events.push([name,args.at(-1)===ws,typeof args[0]==='object'&&args[0]?.utf8?args[0].utf8():args[0]]));
 assert.equal(ws.send('early'),true);assert.equal(ws.queueSize(),5);
 await tick();
 const conn=sockets[0];assert.deepEqual(conn.sent,['early']);
 ws.send(ByteString.of(1,2));await tick();assert.deepEqual(conn.sent,['early','0102']);
 conn.listeners.message.forEach(f=>f({type:'text',text:'hi'}));conn.listeners.message.forEach(f=>f({type:'binary',dataBase64:'AQI='}));
 assert.deepEqual(ws.request(),{url:'wss://example.test',method:'GET',headers:{a:'b'}});assert.equal(ws.client,null);assert.equal(ws.url,'wss://example.test');
 assert.equal(ws.close(1000,'bye'),true);await tick();
 assert.deepEqual(events.map(e=>e[0]),['open','message','text','message','bytes','closing','closed']);
 assert(events.every(e=>e[1]),'listeners receive the socket last');
 assert.deepEqual(events.slice(1,5).map(e=>e[2]),['hi','hi','\u0001\u0002','\u0001\u0002']);
 assert.equal(ws.send('late'),false);
 const sticky=family.web.newWebSocket('wss://x');sticky.emitSticky('ready',1,2);let got;sticky.on('ready',(...a)=>{got=a;});assert.deepEqual(got,[1,2]);
 let max;sticky.on('max_rebuilds',value=>{max=value;});sticky.rebuild(0);assert.equal(max,0);
 // A failure listener that cancels (the device case web.newWebSocket.failure~) is called once per failure, as Rhino's OkHttp.
 const refused=createUi(fake().call).__autojs6Family({connectWebSocket:async()=>{throw Object.assign(new Error('refused'),{code:'ECONNREFUSED'});}}).web.newWebSocket('ws://127.0.0.1:9');
 const failures=[];refused.on('failure',error=>{failures.push(error.message);refused.cancel();});await tick();
 assert.deepEqual(failures,['refused']);refused.cancel();assert.deepEqual(failures,['refused']);
 const canceling=family.web.newWebSocket('wss://cancel'),canceled=[];canceling.on('failure',error=>{canceled.push(error.code);canceling.cancel();});
 await tick();canceling.cancel();canceling.cancel();assert.deepEqual(canceled,['ERR_AUTOJS6_WEBSOCKET_CANCELED']);
 // InjectableWebClient waits for the first page finished, then evaluates on that view.
 const client=family.web.newInjectableWebClient();assert.deepEqual(Object.keys(client),['onPageFinished']);
 const pending=client.injectAndWait('1+1');let callbackValue;client.inject('2+2',value=>{callbackValue=value;});
 client.onPageFinished.call({evaluateJavascript:async script=>script==='1+1'?2:'4'},{});
 assert.equal(await pending,'2');await tick();assert.equal(callbackValue,'4');
});

test('terminal routes, TerminalError and the Async forms',async()=>{
 const h=fake({'terminal.open':()=>({id:'s1',cwd:'/x',state:'running',alive:true,exitCode:null}),'terminal.state':'not_installed',
  'terminal.isAlive':()=>{throw Object.assign(new Error('Terminal plugin is not installed'),{code:'PLUGIN_UNAVAILABLE',name:'TerminalError'});},
  'terminal.sessions':[{id:'a'},{id:'b'}],'terminal.exec':{exitCode:0}});
 const {terminal}=createUi(h.call).__autojs6Family({});
 assert.equal(terminal.openAsync,terminal.open);assert.equal(terminal.npm.runAsync,terminal.npm.run);
 assert.deepEqual(Object.keys(terminal).sort(),['TerminalError','close','closeAll','closeAllAsync','closeAsync','env','envAsync','exec','execAsync','isAvailable','isAvailableAsync',
  'npm','open','openAsync','session','sessions','sessionsAsync','show','showAsync','state']);
 const s=await terminal.open({cwd:'.'});
 assert.deepEqual(h.calls[0].options.permissions,['terminal']);
 assert.deepEqual([s.id,s.cwd,String(s)],['s1','/x','TerminalSession { id: s1, cwd: /x, state: running }']);
 await s.write('ls\n');assert.deepEqual(h.calls.at(-1).args,['s1','ls\n']);
 await s.waitFor(/\$ $/m,1000);assert.deepEqual(h.calls.at(-1).args,['s1',{regex:'\\$ $',flags:'m'},1000]);assert.equal(h.calls.at(-1).options.timeoutMs,6000);
 const error=await s.isAlive().catch(e=>e);
 assert(error instanceof terminal.TerminalError);assert.deepEqual([error.name,error.code,error.sessionId],['TerminalError','PLUGIN_UNAVAILABLE','s1']);
 assert.equal(String(error),'TerminalError [PLUGIN_UNAVAILABLE]: Terminal plugin is not installed');
 assert.equal(await terminal.state(),'not_installed');
 assert.deepEqual((await terminal.sessions()).map(x=>x.id),['a','b']);
 await terminal.close(s);assert.deepEqual(h.calls.at(-1).args,['s1']);
 await terminal.exec('echo 1',{timeout:5000});assert.equal(h.calls.at(-1).options.timeoutMs,15000);
 await terminal.npm.run('build','/p');assert.deepEqual(h.calls.at(-1).args,['build','/p']);
 const own=terminal.TerminalError('m','C');assert(own instanceof Error);assert.equal(own.code,'C');
});

test('console.build records the 52 ConsoleConfigurator members and show applies them once',async()=>{
 const h=fake(),{console:family}=createUi(h.call).__autojs6Family({});
 const c=family.build({size:[100,200],position:{x:1,y:2}.x===1?[1,2]:null,title:'T',touchable:false,exitOnClose:3000,contentTextColors:['#f00','#0f0']});
 const members=new Set();for(let p=Object.getPrototypeOf(c);p&&p!==Object.prototype;p=Object.getPrototypeOf(p))for(const n of Object.getOwnPropertyNames(p))members.add(n);
 for(const n of ['constructor','toOptions'])members.delete(n);
 assert.equal(members.size,52);
 assert.deepEqual([c.size,c.title,c.isTouchable(),c.isExitOnClose(),c.exitOnCloseTimeout,c.gravity,c.contentTextColors],[{width:100,height:200},'T',false,true,3000,null,['#f00','#0f0']]);
 assert.equal(c.setTextColor('#fff').setBackgroundAlpha(0.5),c);
 await c.show();
 assert.deepEqual(h.calls.at(-1),{module:'console',method:'configure',args:[{size:{width:100,height:200},position:{x:1,y:2},title:'T',titleTextColor:'#fff',
  titleBackgroundAlpha:0.5,contentTextColors:['#fff','#fff','#fff','#fff','#fff','#fff'],contentBackgroundAlpha:0.5,touchable:false,exitOnClose:3000}],options:{permissions:['console']}});
 assert.throws(()=>family.build({nope:1}),/Unknown key "nope"/);
});

test('compose: ComposeError and the ComposeNode members',async()=>{
 const compose=createCompose(async()=>null);
 assert.equal(typeof compose.ComposeError,'function');
 const error=(()=>{try{compose.createElement('Nope');}catch(e){return e;}})();
 assert(error instanceof compose.ComposeError);assert.deepEqual([error.name,error.code,error.retryable],['ComposeError','INVALID_ARGUMENT',false]);
 assert.equal(compose.ComposeError('m','PLUGIN_DISABLED').retryable,true);
 const child=compose.Text({text:'a',key:'k',w:10}),parent=compose.Column({},child);
 assert.deepEqual([child.type,child.key,child.parent,parent.children[0],child.w],['Text','k',parent,child,10]);
 assert(Number.isInteger(child.nodeId));
 child.bg='#fff';assert.equal(child.get('bg'),'#fff');
 parent.remove(child);assert.equal(child.parent,null);
 assert.doesNotThrow(()=>JSON.stringify(parent));
});

test('leveled toasts print the text at their console level and dismissAll goes to the host',async()=>{
 const calls=[];
 const factory=new Function('__autojs6_call_autojs','__autojs6_bridge_signal_from_options','console',
  'let __autojs6_limited_toast_cache=null;'+part('  function __autojs6_limited_toast(','  function __autojs6_clipboard_options(')+'return __autojs6_limited_toast;');
 const printed=[],fakeConsole={log:m=>printed.push(['log',m]),info:m=>printed.push(['info',m]),warn:m=>printed.push(['warn',m]),error:m=>printed.push(['error',m]),verbose:m=>printed.push(['verbose',m])};
 const toast=factory(async(module,method,args,options)=>{calls.push([module,method,args,options.permissions]);},o=>o&&o.signal,fakeConsole)();
 await toast.toastInfo('i',true);await toast.toastVerbose('v');await toast.toastError(null);await toast.dismissAll();
 assert.deepEqual(printed,[['info','i'],['verbose','v'],['error','']]);
 assert.deepEqual(calls.map(c=>c.slice(0,3)),[['toast','showToast',['i',{duration:'long',log:false}]],['toast','showToast',['v',{duration:'short',log:false}]],
  ['toast','showToast',['',{duration:'short',log:false}]],['toast','dismissAll',[]]]);
 assert.deepEqual(calls[3][3],['toast']);
});

test('CJS, ESM and nested Workers get floaty / web / terminal, toast, console input and the UI globals',async()=>{
 const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
 const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
 const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
 const createWorker=new Function('return ('+constructor+')')();
 const directory=path.resolve(__dirname,'../../../build/worker-ui-rhino-tests');fs.mkdirSync(directory,{recursive:true});
 const preloadPath=path.join(directory,'preload.cjs');
 const names=preload.slice(preload.indexOf('(')+1,preload.indexOf(')')).split(',').map(name=>name.trim());
 const args=Array(names.length).fill('undefined');args[0]='require';args[1]=constructor;args[2]='()=>{}';args[3]=modules;
 args[names.indexOf('createUi')]=uiSource;args[names.indexOf('createCompose')]=composeSource;
 fs.writeFileSync(preloadPath,`(${preload})(${args.join(',')});`);
 const family=createUi(async()=>null).__autojs6Family({});
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},
  hostModuleMethods:{ui:Object.keys(createUi(async()=>null)),compose:['createElement','ComposeError'],floaty:Object.keys(family.floaty),web:Object.keys(family.web),terminal:Object.keys(family.terminal),
   toast:['showToast','toast','dismissAll','toastLog','toastInfo','toastWarn','toastError','toastVerbose'],console:['log','rawInput','input','build','isShowing']},
  consoleControls:['rawInput','isShowing'],hostModuleConstants:{}};
 const calls=[];
 const Worker=createWorker(require,()=>config,async(module,method,args)=>{calls.push([module,method,args]);
  return method==='rawInput'?'6*7':method==='state'?'available':method==='isShowing'?true:null;});
 const body=`(async()=>{parentPort.postMessage({members:[typeof floaty.window,typeof web.ByteString.encodeUtf8,typeof terminal.TerminalError,typeof toast.dismissAll],
  state:await terminal.state(),input:await console.input('?'),raw:await console.rawInput(),showing:await console.isShowing(),built:typeof console.build({title:'t'}).show,
  globals:[isUiThread(),typeof toastInfo,typeof toastinfo,typeof newWebSocket,typeof ComposeError],toast:(await toastInfo('x'),1)});})();`;
 const cjs=`const {parentPort}=require('node:worker_threads'),floaty=require('autojs6:floaty'),web=require('autojs6:web'),terminal=require('autojs6:terminal'),toast=require('toast'),console=require('console');${body}`;
 const esm=`import {parentPort} from 'node:worker_threads';import floaty from 'autojs6:floaty';import web from 'autojs6:web';import terminal from 'autojs6:terminal';import toast from 'autojs6:toast';import console from 'autojs6:console';${body}`;
 const run=(code,opts)=>new Promise((resolve,reject)=>{const w=new Worker(code,{execArgv:[],...opts});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});
 const expected={members:['function','function','function','function'],state:'available',input:42,raw:'6*7',showing:true,built:'function',
  globals:[false,'function','function','function','function'],toast:1};
 for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',x=>parentPort.postMessage(x));`,{eval:true}]])
  assert.deepEqual(await run(code,opts),expected);
 assert(calls.some(c=>c[0]==='terminal'&&c[1]==='state'));
 assert(calls.some(c=>c[0]==='autojs6:worker-module:console'&&c[1]==='rawInput'));
 assert(calls.some(c=>c[0]==='autojs6:worker-module:toast'&&c[1]==='toastInfo'));
 assert(!fs.readdirSync(directory).some(name=>name!=='preload.cjs'));
 fs.rmSync(directory,{recursive:true,force:true});
});

// Rhino parity of the system information family (api-coverage P3.1b): autojs (+ version, R), installer, memory, settings,
// sysprops, power_manager, the global system functions of autojs6:system, plugins.extend* / tools, notice extras,
// dialogs.build and the Worker modules. autojs.version is checked against io.github.g00fy2.versioncompare 1.5.0
// (system/version-compare-expected.json, recorded from the library the host uses).
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events'),{pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8').replace(/\r\n/g,'\n');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a,first);return source.slice(a,z);}
const systemSource=part('  function __autojs6_create_system(','\n  function __autojs6_create_app(');
const createSystem=new Function('return ('+systemSource+')')();
const createDialogs=new Function('return ('+part('  function __autojs6_create_dialogs(','  // Main-thread device facade.')+')')();
const createNotifications=new Function('return ('+part('  function __autojs6_create_notifications(','  // Main-thread event and notification facades.')+')')();

// A fake host: answers[module.method] is a value or a function of the arguments; every call is recorded.
function fake(answers={},hostOptions={}){
 const calls=[];
 const call=async(module,method,args,options={})=>{
  calls.push({module,method,args,options});
  const answer=answers[module+'.'+method];
  return typeof answer==='function'?answer(...args):answer;
 };
 const lifecycle={stopped:false,exits:[],isStopped(){return this.stopped;},exit(error){this.stopped=true;this.exits.push(error);}};
 const system=createSystem(call,{engine:{versionName:'6.8.0',versionCode:5935,versionDate:'Oct 30, 2026',appName:'AutoJs6',packageName:'org.autojs.autojs6',
  cwd:'/sdcard/Scripts',sourceName:'main.js'},device:{sdkInt:35,width:1080,height:2400},EventEmitter,lifecycle,
  jsox:()=>hostOptions.jsox,shell:()=>hostOptions.shell,shizuku:()=>hostOptions.shizuku});
 return {system,calls,lifecycle,last:()=>calls[calls.length-1]};
}

test('autojs.version compares as io.github.g00fy2.versioncompare 1.5.0',()=>{
 const expected=JSON.parse(fs.readFileSync(path.join(__dirname,'system/version-compare-expected.json'),'utf8'));
 for(const [current,other,higher,lower,equal,atLeast,atLeastIgnoringSuffix] of expected){
  const version=createSystem(async()=>null,{engine:{versionName:current},device:{},EventEmitter}).autojs.version;
  assert.deepEqual([version.isHigherThan(other),version.isLowerThan(other),version.isEqual(other),version.isAtLeast(other),version.isAtLeast(other,true)],
   [higher,lower,equal,atLeast,atLeastIgnoringSuffix],current+' vs '+other);
 }
 const {system}=fake();
 assert.deepEqual([system.autojs.version.code,system.autojs.version.name,system.autojs.version.date],[5935,'6.8.0','Oct 30, 2026']);
});

test('autojs constants, R, root mode, language and script names follow Rhino',async()=>{
 const {system,calls,last}=fake({'autojs.resourceId':(type,name)=>type==='string'&&name==='app_name'?42:0,'autojs.getRootMode':'AUTO_DETECT',
  'autojs.getLanguage':{tag:'zh-Hans-CN'},'autojs.screen':{portrait:true,landscape:false,rotation:0,orientation:1},'autojs.themeColor':{colorPrimary:1,colorPrimaryDark:2,colorAccent:3}});
 const autojs=system.autojs;
 assert.deepEqual([autojs.versionCode,autojs.versionName,autojs.versionDate,autojs.name,autojs.packageName],[5935,'6.8.0','Oct 30, 2026','AutoJs6','org.autojs.autojs6']);
 assert.equal(await autojs.R.strings.app_name,42);
 assert.deepEqual(last().args,['string','app_name']);
 assert.equal(autojs.R.then,undefined);
 assert.equal(await autojs.getRootMode(),'AUTO_DETECT');
 for(const [mode,name] of [[1,'FORCE_ROOT'],[0,'FORCE_NON_ROOT'],[-1,'AUTO_DETECT'],[true,'FORCE_ROOT'],[false,'FORCE_NON_ROOT'],[' Non-Root ','FORCE_NON_ROOT'],['auto','AUTO_DETECT']]){
  await autojs.setRootMode(mode);
  assert.deepEqual(last().args,[name,false]);
  assert.deepEqual(last().options.permissions,['autojs','autojs.control']);
 }
 await autojs.setRootMode('root','write_into_pref');
 assert.deepEqual(last().args,['FORCE_ROOT',true]);
 await assert.rejects(autojs.setRootMode(2),RangeError);
 await assert.rejects(autojs.setRootMode('su'),RangeError);
 await assert.rejects(autojs.setRootMode({}),TypeError);
 const locale=await autojs.getLanguage();
 assert(locale instanceof Intl.Locale);
 assert.equal(locale.language,'zh');
 assert.equal(await autojs.getLanguageTag(),'zh-Hans-CN');
 assert.equal(await autojs.isScreenPortrait(),true);
 assert.equal(await autojs.rotation,0);
 assert.equal(await autojs.orientation,1);
 assert.deepEqual({...await autojs.themeColor},{colorPrimary:1,colorPrimaryDark:2,colorAccent:3});
 for(const name of ['rotation','orientation','themeColor'])assert.equal(Object.getOwnPropertyDescriptor(autojs,name).get.__autojs6LazyExport,true);
 await autojs.restart([' a ','b.js',null,'@']);
 assert.deepEqual(last().args,[['a.js','b.js','/sdcard/Scripts/main.js']]);
 await autojs.exit();
 assert.deepEqual(last().args,[null]);
 await assert.rejects(autojs.exit(1),TypeError);
 assert(calls.every(c=>c.module==='autojs'));
});

test('installer answers values, InstallerError and session events',async()=>{
 const events=[[{type:'stage',stage:'copying',detail:{}},{type:'progress',progress:0.5,detail:{}}],[{type:'complete',result:{status:'success'}}]];
 const {system,calls,last}=fake({'installer.isAvailable':{value:true},'installer.users':{value:[{id:0}]},'installer.status':{value:{authorizers:{shizuku:{available:true},root:{available:false}}}},
  'installer.install':{error:{code:'INSTALL_FAILED',message:'nope',retryable:false,packageName:'com.a'}},
  'installer.sessionOpen':{value:{id:'s1',state:'created'}},'installer.sessionStart':{value:'running'},'installer.sessionState':{value:'running'},
  'installer.sessionDrain':()=>({value:events.shift()||[]}),'installer.sessionClose':{value:true}});
 const installer=system.installer;
 assert.equal(await installer.isAvailable(),true);
 assert.deepEqual(last().options.permissions,['installer']);
 assert.equal(installer.usersAsync,installer.users);
 assert.deepEqual(await installer.users(),[{id:0}]);
 assert.deepEqual(await installer.authorizer.available,['shizuku']);
 const failure=await installer.install('a.apk').catch(error=>error);
 assert(failure instanceof installer.InstallerError);
 assert.deepEqual([failure.name,failure.code,failure.message,failure.packageName],['InstallerError','INSTALL_FAILED','nope','com.a']);
 assert.deepEqual(calls.find(c=>c.method==='install').options.permissions,['installer','installer.mutate']);
 assert.deepEqual(calls.find(c=>c.method==='install').args,['a.apk',null]);
 const session=await installer.session(['a.apk','b.apk']);
 const seen=[];
 session.on('stage',stage=>seen.push('stage:'+stage)).on('progress',value=>seen.push('progress:'+value)).on('complete',result=>seen.push('complete:'+result.status));
 assert.deepEqual(await session.wait(),{status:'success'});
 assert.deepEqual(seen,['stage:copying','progress:0.5','complete:success']);
 assert.deepEqual([session.id,session.state,session.result.status],['s1','completed','success']);
 await new Promise(resolve=>setImmediate(resolve));
 assert(calls.some(c=>c.method==='sessionClose'&&c.args[0]==='s1'));
});

test('memory, settings, sysprops and power_manager pass Rhino arguments and capabilities',async()=>{
 const {system,last}=fake({'memory.collect':{cleared:true},'sysprops.get':null,'sysprops.getInt':7,'power_manager.isIgnoringBatteryOptimizations':true});
 await system.memory.collect();
 assert.deepEqual([last().args,last().options.timeoutMs],[[null],40000]);
 await assert.rejects(system.memory.collect(60001),RangeError);
 await system.memory.dumpHeap('dumps/');
 assert.deepEqual(last().options.permissions,['memory','memory.dump']);
 await assert.rejects(system.settings.setEnabled('x','yes'),TypeError);
 await system.settings.setEnabled('x',true);
 assert.deepEqual([last().args,last().options.permissions],[['x',true],['settings','settings.write']]);
 await system.settings.export();
 assert.deepEqual([last().args,last().options.permissions],[[null,null],['settings','files.write']]);
 assert.equal(await system.sysprops.get('ro.x','d'),'d');
 assert.equal(await system.sysprops.getInt('ro.y',1),7);
 await assert.rejects(system.sysprops.get(''),TypeError);
 assert.equal(await system.power_manager.isIgnoringBatteryOptimizations(),true);
 await system.power_manager.requestIgnoreBatteryOptimizations(true,'com.a');
 assert.deepEqual([last().args,last().options.permissions],[[true,'com.a'],['device','device.power']]);
});

test('global system functions: current* by mode, waitFor*, the stop state and requires*',async()=>{
 const shell={currentPackage:async()=>'com.root',currentActivity:async()=>'.Root',currentComponent:async()=>'com.root/.Root'};
 const shizuku={currentPackage:async()=>'',currentActivity:async()=>'',currentComponent:async()=>''};
 let polls=0;
 const {system:{system:s},lifecycle}=fake({'accessibility.flowForeground':()=>({available:true,value:{packageName:++polls>2?'com.target':'com.a11y',activityName:'.Main'}})},{shell,shizuku});
 assert.equal(await s.currentPackage(),'com.root');
 assert.equal(await s.currentPackage('shizuku'),'');
 assert.equal(await s.currentComponent({by:'a11y'}),'com.a11y/.Main');
 await assert.rejects(s.currentActivity('adb'),RangeError);
 const slow=fake({},{shell:{...shell,currentPackage:async()=>'com.other'},shizuku}).system.system;
 assert.equal(await slow.waitForPackage('com.target',3,0),false);
 assert.equal(await slow.waitForPackage('com.target',2,0,{else:value=>'else:'+value}),'else:false');
 polls=0;
 assert.equal(await s.waitForPackage('com.target',{then:()=>undefined}),false);
 assert.equal(await fake({},{shell:{...shell,currentPackage:async()=>'com.target'},shizuku}).system.system.waitForPackage('com.target',5,0,{then:value=>'then:'+value}),'then:true');
 await assert.rejects(s.waitForPackage('x',-1),RangeError);
 assert.deepEqual([s.isStopped(),s.notStopped(),s.isRunning(),s.isShuttingDown()],[false,true,true,false]);
 s.stop();
 assert.deepEqual([s.isStopped(),lifecycle.exits.length],[true,1]);
 s.requiresApi(24);
 assert.throws(()=>s.requiresApi(36),{code:'ERR_AUTOJS6_API_LEVEL'});
 s.requiresAutojsVersion('6.8.0');
 s.requiresAutojsVersion(5935);
 s.requiresAutojsVersion(6);
 assert.throws(()=>s.requiresAutojsVersion('6.8.1'));
 assert.throws(()=>s.requiresAutojsVersion(460),RangeError);
 assert.throws(()=>s.requiresAutojsVersion(7));
 assert.deepEqual([s.WIDTH,s.HEIGHT,s.isAutoJs6],[1080,2400,true]);
});

test('setScreenMetrics scales the coordinates of this thread as ScreenMetrics.scaleX / scaleY',()=>{
 const {system}=fake();
 assert.deepEqual([system.scale.x(540),system.scale.y(100)],[540,100]);
 system.system.setScreenMetrics(720,1600);
 assert.deepEqual([system.scale.x(360),system.scale.y(801)],[540,1201]);
 assert.throws(()=>system.system.setScreenMetrics('a',1),TypeError);
});

test('plugins.extend* use the shared jsox, plugins.tools the host plugin tools',async()=>{
 const extended=[];
 const jsox={extend:(...names)=>extended.push(names.flat()),extendAll:()=>extended.push('all')};
 const {system,last}=fake({'plugin_tools.list':{tools:[{name:'a'}],providers:[{packageName:'p'}]},'plugin_tools.invoke':{ok:true}},{jsox});
 system.plugins.extend('Mathx');
 system.plugins.extendAll();
 system.plugins.extendAllBut('arrayx',['Numberx']);
 assert.deepEqual(extended,[['Mathx'],'all',['Mathx']]);
 // Rhino ignores unknown names and keeps the exclusions for extendAll.
 system.plugins.extendAllBut('Stringx');
 system.plugins.extendAll();
 assert.deepEqual(extended.slice(3),[['Mathx'],['Mathx']]);
 assert.deepEqual(await system.plugins.tools.list(),[{name:'a'}]);
 assert.deepEqual(await system.plugins.tools.providers(),[{packageName:'p'}]);
 assert.deepEqual(await system.plugins.tools.call('a',{x:1},{timeoutMs:1000}),{ok:true});
 assert.deepEqual([last().args[0],last().args[1],last().args[2].timeoutMs,last().options.permissions],['a',{x:1},1000,['plugin_tools']]);
});

test('notice config, switches, builder and channels',async()=>{
 const calls=[];
 const handle=(id,className)=>({__autojs6JavaHandle:id,className});
 const java={callStatic:async(type,method)=>{calls.push(['static',type,method]);return handle('b','Builder');},getContext:async()=>handle('c','Context'),
  call:async(target,method,args)=>{calls.push(['call',target.__autojs6JavaHandle,method,args.length]);return method==='build'?handle('n','Notification'):handle('m','Manager');},
  cast:(type,value)=>({type,value}),release:async()=>true};
 const notifications=createNotifications(async(module,method,args,options)=>{calls.push([module,method,args,options.permissions]);
  return method==='notify'?7:method==='ensureEnabled'?false:method==='channelContains'?false:method==='channelCreate'?args[0]:null;},undefined,'scope',()=>java);
 notifications.config({defaultTitle:'T',defaultChannelId:'ch'});
 assert.throws(()=>notifications.config({nope:1}),RangeError);
 assert.throws(()=>notifications.config({defaultTitle:1}),TypeError);
 await notifications.notify({text:'x'});
 assert.deepEqual(calls.at(-1).slice(0,3),['notifications','notify',[{text:'x',title:'T',channel:'ch'},'scope']]);
 await assert.rejects(notifications.ensureEnabled(),{code:'ERR_AUTOJS6_NOTIFICATIONS_DISABLED'});
 assert.deepEqual(calls.at(-1)[3],['notifications','notifications.settings']);
 assert.equal(await notifications.channel.create({id:'a',name:'A'}),'a');
 assert.deepEqual(calls.at(-1).slice(1,3),['channelCreate',['a',{id:'a',name:'A'}]]);
 await notifications.channel.createIfNeeded('b',{name:'B'});
 assert.deepEqual(calls.slice(-2).map(c=>c[1]),['channelContains','channelCreate']);
 await assert.rejects(notifications.channel.create({id:'a'},{}),TypeError);
 assert.equal(Object.getOwnPropertyDescriptor(notifications,'builder').get.__autojs6LazyExport,true);
 const builder=await notifications.getBuilder();
 assert.deepEqual(calls.at(-1),['static','org.autojs.autojs.util.NotificationUtils','getSimpleBuilder']);
 assert.equal(await notifications.notify(builder,{id:5}),5);
 assert.deepEqual(calls.filter(c=>c[0]==='call').map(c=>c[2]),['getSystemService','build','notify']);
});

test('dialogs.build takes the route from the properties and emits Rhino events',async()=>{
 const answers={alert:undefined,confirm:false,input:'typed',select:1,singleChoice:0,multiChoice:[0,2]};
 const calls=[];
 const dialogs=createDialogs(async(module,method,args)=>{calls.push([method,args]);return answers[method];},'scope');
 const run=async properties=>{const seen=[];const d=dialogs.build(properties);
  for(const event of ['show','positive','negative','input','item_select','single_choice','multi_choice','cancel','dismiss'])d.on(event,(...args)=>seen.push([event,...args.filter(a=>a!==d)]));
  const result=await d.show();assert.equal(d.show(),d.show());return {result,seen,method:calls.at(-1)[0],args:calls.at(-1)[1]};};
 let r=await run({title:'t',content:'c'});
 assert.deepEqual([r.method,r.seen.map(e=>e[0])],['alert',['show','positive','dismiss']]);
 r=await run({title:'t',content:'c',positive:'OK',negative:'No'});
 assert.deepEqual([r.method,r.result,r.seen.map(e=>e[0]),r.args.at(-1).negative],['confirm',false,['show','negative','dismiss'],'No']);
 r=await run({title:'t',inputPrefill:'p'});
 assert.deepEqual([r.method,r.args[1],r.result,r.seen[1]],['input','p','typed',['input','typed']]);
 r=await run({title:'t',items:['a','b']});
 assert.deepEqual([r.method,r.seen[1]],['select',['item_select',1,'b']]);
 r=await run({title:'t',items:['a','b'],itemsSelectMode:'single',itemsSelectedIndex:1});
 assert.deepEqual([r.method,r.args[2],r.seen[1]],['singleChoice',1,['single_choice',0,'a']]);
 r=await run({title:'t',items:['a','b','c'],itemsSelectMode:'multi'});
 assert.deepEqual([r.method,r.seen[1]],['multiChoice',['multi_choice',[0,2],['a','c']]]);
 assert.throws(()=>dialogs.build('x'),TypeError);
});

test('CJS, ESM and nested Workers get the system modules and engines',async()=>{
 const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
 const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
 const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
 const createWorker=new Function('return ('+constructor+')')();
 const directory=path.resolve(__dirname,'../../../build/worker-system-tests');fs.mkdirSync(directory,{recursive:true});
 const preloadPath=path.join(directory,'preload.cjs');
 const names=preload.slice(preload.indexOf('(')+1,preload.indexOf(')')).split(',').map(name=>name.trim());
 const args=Array(names.length).fill('undefined');args[0]='require';args[1]=constructor;args[2]='()=>{}';args[3]=modules;args[names.indexOf('createSystem')]=systemSource;
 fs.writeFileSync(preloadPath,`(${preload})(${args.join(',')});`);
 const {system}=fake();
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},
  hostModuleMethods:{autojs:Object.keys(system.autojs),system:Object.keys(system.system),installer:Object.keys(system.installer),engines:['myEngine','all','execScript']},
  hostModuleConstants:{engineRecord:{versionName:'6.8.0',versionCode:5935},engineInfo:{id:'e1'},device:{sdkInt:35,width:1080,height:2400}}};
 const calls=[];
 const Worker=createWorker(require,()=>config,async(module,method,args)=>{calls.push([module,method,args]);return method==='isRootAvailable'?true:method==='all'?[{id:'x'}]:null;});
 const body=`(async()=>{parentPort.postMessage({code:autojs.versionCode,atLeast:autojs.version.isAtLeast('6.8'),root:await autojs.isRootAvailable(),width:system.WIDTH,
  engine:engines.myEngine().id,all:(await engines.all()).length,members:[typeof system.currentPackage,typeof installer.install,typeof installer.InstallerError]});})();`;
 const cjs=`const {parentPort}=require('node:worker_threads'),autojs=require('autojs6:autojs'),system=require('autojs6:system'),installer=require('autojs6:installer'),engines=require('engines');${body}`;
 const esm=`import {parentPort} from 'node:worker_threads';import autojs from 'autojs6:autojs';import system from 'autojs6:system';import installer from 'autojs6:installer';import engines from 'autojs6:engines';${body}`;
 const run=(code,opts)=>new Promise((resolve,reject)=>{const w=new Worker(code,{execArgv:[],...opts});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});
 const expected={code:5935,atLeast:true,root:true,width:1080,engine:'e1',all:1,members:['function','function','function']};
 for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',x=>parentPort.postMessage(x));`,{eval:true}]])
  assert.deepEqual(await run(code,opts),expected);
 assert(calls.some(c=>c[0]==='autojs'&&c[1]==='isRootAvailable'));
 assert(calls.some(c=>c[0]==='autojs6:worker-module:engines'&&c[1]==='all'));
});

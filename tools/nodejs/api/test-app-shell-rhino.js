// Rhino app / shell / shizuku parity of the shared factories (api-coverage P3.1a): command arguments, results, routes,
// D-4 privileged and dual-user helpers, intent options, ShellProcess members and the Worker app module.
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events'),streams=require('node:stream'),{pathToFileURL}=require('node:url');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8').replace(/\r\n/g,'\n');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a,first);return source.slice(a,z);}
const shellSource=part('  function __autojs6_create_shell(','  // Main-thread shell facade;');
const appSource=part('  function __autojs6_create_app(','\n  function __autojs6_limited_app() {');
const createShell=new Function('return ('+shellSource+')')(),createApp=new Function('return ('+appSource+')')();

const DUMP='ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n  Display #0\n    mResumedActivity: ActivityRecord{1a2b u0 com.example/.MainActivity t12}\n';
// A fake host: shell processes answer from `respond(command, options)`, app queries from tables.
function host({access={},respond=()=>({}),declared,users=[999],packages={}}={}){
 const calls=[],live=new Map();let n=0;
 const access0={shell:true,rootAvailable:true,shizukuRunning:true,shizukuGranted:true,shizuku:true,shizukuInstalled:true,shizukuHasService:true,shizukuOperational:true,uid:10123,missingCapabilities:[],...access};
 const missing=required=>declared?required.filter(x=>!declared.includes(x)):[];
 const call=async(module,method,args,options={})=>{
  calls.push({module,method,args,options});
  const lacking=missing(options.permissions||[]);
  if(lacking.length)throw Object.assign(new Error('not declared'),{code:'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED',missingCapabilities:lacking});
  if(module==='app'){
   if(method==='resolvePackage')return packages[args[0]]??null;
   if(method==='getUserProfiles')return {current:0,others:users};
   return null;
  }
  if(method==='getAccess')return access0;
  if(method==='spawn'){
   const id='p'+ ++n,opts=args[1],out=respond(args[0],opts)||{};
   if(out.fail)throw Object.assign(new Error(out.fail),{code:'ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED'});
   const queue=[];if(out.stdout)queue.push({type:'stdout',data:Buffer.from(out.stdout).toString('base64')});if(out.stderr)queue.push({type:'stderr',data:Buffer.from(out.stderr).toString('base64')});
   queue.push({type:'exit',code:out.code??0,timedOut:false});live.set(id,queue);return {id,pid:n,mode:opts.shizuku?'adb':opts.root?'root':'sh'};
  }
  if(method==='drain'){const queue=live.get(args[0])||[];live.delete(args[0]);return queue;}
  if(method==='close'){live.delete(args[0]);return {wasAlive:false,code:0};}
  return null;
 };
 let app;
 const {shell,shizuku}=createShell(call,EventEmitter,streams,Buffer,missing,undefined,intent=>app.intentToShell(intent),()=>({width:1080,height:2400}));
 app=createApp(call,()=>({packageName:'org.autojs.autojs6',versionName:'6.8.0',versionCode:5935}),(message,code,module,method)=>Object.assign(new Error(message),{code,module,method}),
  options=>options&&options.signal,()=>({getUriForFile:()=>null}),()=>shell,missing);
 const spawned=()=>calls.filter(c=>c.method==='spawn').map(c=>({command:c.args[0],root:c.args[1].root,shizuku:c.args[1].shizuku,permissions:c.options.permissions}));
 return {shell,shizuku,app,calls,spawned};
}
const NOT_DECLARED={code:'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED'};

test('getCommand follows Rhino Shell.getCommandData',()=>{
 const {shell,shizuku}=host();
 assert.equal(shell.getCommand('ls'),'ls');
 assert.equal(shell.getCommand('ls',true),'su\nls');
 assert.equal(shell.getCommand('ls',0),'ls');
 assert.equal(shell.getCommand('ls',{l:true,color:'auto',maxDepth:2}),'ls -l --color auto --max-depth 2');
 assert.equal(shell.getCommand('ls','a|b=1|longName=x y'),'ls -a -b 1 --long-name "x y"');
 assert.equal(shell.getCommand('ls',{root:true}),'su\nls');
 assert.equal(shell.getCommand('ls',{exit:true}),'ls\nexit\nkill $$');
 assert.equal(shell.getCommand(['a','b']),'a\nb');
 assert.equal(shell.getCommand('ls','x',1),'su\nls -x');
 assert.equal(shell.getCommand('ls','x',undefined),'ls -x');
 assert.equal(shell.getCommand('ls',{'--parseHTML':'-v',x2:false}),'ls --parse-html "-v" -x2 false');
 assert.equal(shizuku.getCommand('ls',true),'ls');
 assert.throws(()=>shell.getCommand(1),TypeError);assert.throws(()=>shell.getCommand('ls',null),TypeError);assert.throws(()=>shell.getCommand('ls',1,2),TypeError);
});

test('execCommand returns Rhino results and honours setDefaultOptions',async()=>{
 const f=host({respond:command=>command==='warn'?{stdout:'a\r\nb',stderr:'careful'}:command==='typed'?{stderr:'Error type 3 x',code:0}:{stdout:'ok\n'}});
 assert.deepEqual({...await f.shell.execCommand('echo ok')},{code:0,result:'ok\n',error:'',timedOut:false,truncated:false});
 assert.deepEqual({...await f.shell.execCommand('warn')},{code:1,result:'a\nb\n',error:'careful\n',timedOut:false,truncated:false});
 assert.equal((await f.shell.execCommand('typed')).code,3);
 await f.shell.execCommand('adb shell id',true);
 f.shell.setDefaultOptions({adb:true});await f.shell.execCommand("echo 'x'",{root:true});
 f.shell.setDefaultOptions(null);await f.shell.execCommand('id');
 assert.deepEqual(f.spawned().slice(3),[
  {command:'id',root:true,shizuku:false,permissions:['shell','shell.root']},
  {command:"su -c 'echo '\"'\"'x'\"'\"''",root:false,shizuku:true,permissions:['shell','shell.shizuku']},
  {command:'id',root:false,shizuku:false,permissions:['shell']}]);
 assert.throws(()=>f.shell.setDefaultOptions({pty:true}),/Unknown default shell option/);assert.throws(()=>f.shell.setDefaultOptions(1),TypeError);
 const broken=host({respond:()=>({fail:'Cannot run program "su": error=2, No such file'})});
 assert.deepEqual({...await broken.shell.execCommand('id',true)},{code:2,result:'',error:'Cannot run program "su": error=2, No such file',timedOut:false,truncated:false});
 await assert.rejects(host({declared:['shell']}).shell.execCommand('id',true),NOT_DECLARED);
});

test('spawn applies the defaults, shizuku.spawn ignores them, booleans and arrays read as in Rhino',async()=>{
 const f=host();f.shell.setDefaultOptions({root:true});
 await f.shell.spawn(['echo a','echo b']).waitFor();await f.shell.spawn('x',false).waitFor();await f.shizuku.spawn('y').waitFor();await f.shell.exec('z');
 assert.deepEqual(f.spawned().map(x=>[x.command,x.root,x.shizuku]),[['echo a\necho b',true,false],['x',false,false],['y',false,true],['z',false,false]]);
});

test('kill, current activity and Shizuku state follow the root and Shizuku checks',async()=>{
 const f=host({packages:{QQ:{packageName:'com.tencent.mobileqq',via:'alias',launchIntent:null}},respond:command=>command.startsWith('dumpsys')?{stdout:DUMP}:{}});
 assert.equal(await f.shell.kill('QQ'),true);assert.equal(await f.shell.kill('missing'),false);
 assert.equal(await f.shell.currentPackage(),'com.example');assert.equal(await f.shell.currentActivity(),'com.example/.MainActivity');assert.equal(await f.shell.currentComponent(),'com.example/.MainActivity');
 assert.equal(await f.shizuku.currentComponent(),'com.example/com.example.MainActivity');assert.equal(await f.shizuku.currentActivity(),'com.example.MainActivity');assert.equal(await f.shizuku.currentPackage(),'com.example');
 assert.equal(await f.shizuku.kill('QQ'),true);
 assert.deepEqual(f.spawned().filter(x=>x.command.startsWith('am')).map(x=>[x.command,x.root,x.shizuku]),[['am force-stop com.tencent.mobileqq',true,false],['am force-stop com.tencent.mobileqq',false,true]]);
 assert.deepEqual({...await f.shizuku.state},{isInstalled:true,hasService:true,isRunning:true,hasPermission:true,isOperational:true});
 assert.equal(await f.shizuku.isOperational(),true);assert.equal(await f.shell.isRootAvailable(),true);
 const bare=host({access:{rootAvailable:false,shizukuOperational:false}});
 assert.equal(await bare.shell.kill('QQ'),false);assert.equal(await bare.shell.currentPackage(),'');assert.equal(await bare.shizuku.currentComponent(),'');assert.equal(await bare.shizuku.kill('QQ'),false);
 assert.equal(bare.spawned().length,0);
});

test('global key and input functions pick Rhino routes, key names and screen metrics',async()=>{
 const f=host();
 await f.shell.Home();await f.shell.KeyCode('KEYCODE_BACK');await f.shell.KeyCode('volume_up');await f.shell.KeyCode('0x04');await f.shell.KeyCode(26);await f.shell.KeyCode('4');
 await assert.rejects(f.shell.KeyCode('nope'),RangeError);await assert.rejects(f.shell.KeyCode({}),TypeError);
 f.shell.SetScreenMetrics(540,1200);await f.shell.Tap(100,200);await f.shell.Swipe(0,0,540,1200,300);await f.shell.Text('hi');await f.shell.Screencap('/sdcard/a.png');
 assert.deepEqual(f.spawned().map(x=>[x.command,x.shizuku?'adb':x.root?'root':'sh']),[
  ['input keyevent 3','adb'],['input keyevent 4','adb'],['input keyevent 24','adb'],['input keyevent 4','adb'],['input keyevent 26','adb'],['input keyevent 4','adb'],
  ['input tap 200 400','root'],['input swipe 0 0 1080 2400 300','root'],['input text hi','root'],['screencap -p /sdcard/a.png','root']]);
 const rooted=host({declared:['shell','shell.root']});await rooted.shell.Back();assert.deepEqual(rooted.spawned().map(x=>x.root),[true]);
 await assert.rejects(host({declared:['shell']}).shell.Back(),NOT_DECLARED);
});

test('ShellProcess has the Rhino members',async()=>{
 const f=host({respond:()=>({stdout:'line\n'})});const p=f.shell.spawn('echo line');
 assert.equal(typeof p.isAlive(),'boolean');assert.equal(p.getOutputStream(),p.stdin);assert.equal(p.getInputStream(),p.stdout);assert.equal(p.getErrorStream(),p.stderr);
 let text='';p.getInputStream().on('data',x=>text+=x);await p.waitFor();
 assert.equal(text,'line\n');assert.equal(p.isAlive(),false);assert.match(p.toString(),/^ShellProcess \{ pid: \d+, mode: sh, command: echo line, alive: false, exitCode: 0 \}$/);
 for(const name of ['addListener','removeListener','listenerCount','prependListener','prependOnceListener'])assert.equal(typeof p[name],'function');
 assert.throws(()=>p.readLine(),/already used/);
});

test('D-4 helpers run "am" through the first declared and available route',async()=>{
 const packages={QQ:{packageName:'com.tencent.mobileqq',via:'alias',launchIntent:{action:'android.intent.action.MAIN',category:['android.intent.category.LAUNCHER'],packageName:'com.tencent.mobileqq',className:'com.tencent.mobileqq.activity.SplashActivity',flags:270532608}},
  WeChat:{packageName:'com.tencent.mm',via:'label',launchIntent:{action:'android.intent.action.MAIN',packageName:'com.tencent.mm',className:'com.tencent.mm.ui.LauncherUI',flags:0}}};
 const f=host({packages,respond:command=>command.startsWith('cmd package')?{stdout:'package:com.tencent.mobileqq\n'}:{}});
 assert.equal(await f.app.kill('QQ'),true);assert.equal(await f.app.killDual('QQ'),true);assert.equal(await f.app.isDualInstalled('QQ'),true);
 assert.equal(await f.app.launchDual('QQ'),true);assert.equal(await f.app.launchDualPackage('WeChat'),false);assert.equal(await f.app.launchDualApp('WeChat'),true);
 assert.equal(await f.app.openDualAppSettings('QQ'),true);await f.app.uninstallDual('raw.pkg');await f.app.openDualUrl('example.com');
 await f.app.startDualActivity({action:'VIEW',data:'https://a.b',root:true,shizuku:false});
 const commands=f.spawned().map(x=>[x.command,x.shizuku?'adb':x.root?'root':'sh']);
 assert.deepEqual(commands,[
  ['am force-stop com.tencent.mobileqq','adb'],
  ['am force-stop com.tencent.mobileqq --user 999','adb'],
  ['cmd package list packages --user 999 com.tencent.mobileqq','adb'],
  ["am start -n 'com.tencent.mobileqq/com.tencent.mobileqq.activity.SplashActivity' -c 'android.intent.category.LAUNCHER' -a 'android.intent.action.MAIN' -f 270532608 --user 999",'adb'],
  ["am start -n 'com.tencent.mm/com.tencent.mm.ui.LauncherUI' -a 'android.intent.action.MAIN' -f 268435456 --user 999",'adb'],
  ["am start -a 'android.settings.APPLICATION_DETAILS_SETTINGS' -f 268435456 -d 'package:com.tencent.mobileqq' --user 999",'adb'],
  ["am start -a 'android.intent.action.DELETE' -f 268435456 -d 'package:raw.pkg' --user 999",'adb'],
  ["am start -a 'android.intent.action.VIEW' -f 268435456 -d 'http://example.com' --user 999",'adb'],
  ["am start -a 'android.intent.action.VIEW' -f 268435456 -d 'https://a.b' --user 999",'root']]);
 assert(f.calls.filter(c=>c.module==='app').every(c=>['resolvePackage','getUserProfiles'].includes(c.method)));
});

test('D-4 capabilities, failures and devices without other users',async()=>{
 const packages={QQ:{packageName:'com.tencent.mobileqq',via:'alias',launchIntent:{action:'android.intent.action.MAIN',packageName:'com.tencent.mobileqq',className:'a.B',flags:0}}};
 await assert.rejects(host({declared:['app.query']}).app.kill('QQ'),{...NOT_DECLARED,missingCapabilities:['shell']});
 await assert.rejects(host({declared:['shell','app.query']}).app.launchDual('QQ'),{...NOT_DECLARED,missingCapabilities:['app.launch']});
 const plain=host({declared:['shell','app.query'],packages,respond:()=>({code:255,stderr:'Error: permission denied'})});
 assert.equal(await plain.app.kill('QQ'),false);assert.equal(await plain.app.killDual('QQ'),false);
 assert.deepEqual(plain.spawned().map(x=>[x.root,x.shizuku]),[[false,false],[false,false]]);
 const failing=host({declared:['shell','shell.root','app.activity','app.query'],packages,respond:()=>({code:1,stderr:'Error: Activity not started'})});
 await assert.rejects(failing.app.uninstallDual('QQ'),{code:'ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED'});
 const single=host({packages,users:[]});
 assert.equal(await single.app.launchDual('QQ'),true);assert.equal(await single.app.killDual('QQ'),true);assert.equal(await single.app.isDualInstalled('QQ'),false);
 assert.equal(await single.app.uninstallDual('QQ'),undefined);assert.equal(single.spawned().length,0);
 assert.equal(await single.app.kill(null),false);assert.equal(await single.app.launchDualSettings(),false);
});

test('Rhino intent options route startActivity, startService and sendBroadcast',async()=>{
 const f=host({access:{shizukuOperational:false}});
 await f.app.startActivity({action:'VIEW',data:'https://a.b',shizuku:true,root:true});
 await f.app.startService({packageName:'p',className:'p.S',root:true});
 await f.app.sendBroadcast({action:'p.ACTION',root:true});
 await f.app.startActivity({action:'VIEW',data:'https://a.b',dual:true});
 assert.deepEqual(f.spawned().map(x=>[x.command,x.shizuku?'adb':x.root?'root':'sh']),[
  ["am start -a 'android.intent.action.VIEW' -f 268435456 -d 'https://a.b'",'root'],
  ["am startservice -n 'p/p.S'",'root'],
  ["am broadcast -a 'p.ACTION'",'root'],
  ["am start -a 'android.intent.action.VIEW' -f 268435456 -d 'https://a.b' --user 999",'root']]);
 const undeclared=host({declared:['app.activity']});await undeclared.app.startActivity({action:'VIEW',data:'https://a.b',root:true});
 assert.equal(undeclared.spawned().length,0);assert.deepEqual(undeclared.calls.map(c=>c.method),['startActivity']);
 await assert.rejects(f.app.startActivity({action:'VIEW',user:10}),/does not accept the intent option user/);
});

test('package queries and the local broadcast reach the host with app capabilities',async()=>{
 const f=host();
 await f.app.getInstalledPackages({get:['activities']});await f.app.getApkInfo('a.apk',{get:['permissions']});await f.app.getAppByAlias('QQ');await f.app.sendLocalBroadcastSync('my.ACTION');
 assert.equal(await f.app.getAppByAlias(null),null);
 assert.deepEqual(f.calls.map(c=>[c.method,c.args]),[['getInstalledPackages',[{get:['activities']}]],['getApkInfo',['a.apk',{get:['permissions']}]],['getAppByAlias',['QQ']],['sendLocalBroadcastSync',[{action:'my.ACTION'}]]]);
 assert.equal(f.shell.fromIntent({action:'VIEW',data:'https://a.b'}),"-a 'android.intent.action.VIEW' -d 'https://a.b'");
 assert.throws(()=>f.shell.fromIntent('x'),TypeError);
});

test('CJS, ESM and nested Workers get the same app, shell and shizuku members',async()=>{
 const constructor=part('  function __autojs6_worker_constructor(','  // The worker keeps synchronous metadata');
 const modules=part('  function __autojs6_worker_host_modules(','  async function __autojs6_worker_invoke(');
 const preload=part('  function __autojs6_worker_preload(','  function __autojs6_limited_worker_threads(');
 const createWorker=new Function('return ('+constructor+')')();
 const directory=path.resolve(__dirname,'../../../build/worker-app-shell-tests');fs.mkdirSync(directory,{recursive:true});
 const preloadPath=path.join(directory,'preload.cjs');
 const count=preload.slice(preload.indexOf('(')+1,preload.indexOf(')')).split(',').length;
 const args=Array(count).fill('undefined');args[0]='require';args[1]=constructor;args[2]='()=>{}';args[3]=modules;args[9]=shellSource;args[count-1]=appSource;
 fs.writeFileSync(preloadPath,`(${preload})(${args.join(',')});`);
 const shellNames=Object.keys(host().shell),appNames=Object.keys(host().app),shizukuNames=Object.keys(host().shizuku);
 const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},
  hostModuleMethods:{app:appNames,shell:shellNames,shizuku:shizukuNames},hostModuleConstants:{appInfo:{packageName:'org.autojs.autojs6'},appMissing:[],shellMissing:[]}};
 const calls=[];const Worker=createWorker(require,()=>config,async(module,method,args)=>{calls.push([module,method,args]);
  if(method==='resolvePackage')return {packageName:'com.a',via:'package',launchIntent:null};if(method==='getAccess')return {rootAvailable:false,shizukuOperational:false};return null;});
 const body=`(async()=>{parentPort.postMessage({kill:await app.kill('com.a'),command:shell.getCommand('ls',true),members:[typeof app.launchDual,typeof shell.Home,typeof shizuku.isOperational,typeof app.getAppByAlias],packageName:app.packageName});})();`;
 const cjs=`const {parentPort}=require('node:worker_threads'),app=require('autojs6:app'),shell=require('shell'),shizuku=require('autojs6:shizuku');${body}`;
 const esm=`import {parentPort} from 'node:worker_threads';import app from 'app';import shell from 'autojs6:shell';import shizuku from 'shizuku';${body}`;
 const run=(code,opts)=>new Promise((resolve,reject)=>{const w=new Worker(code,{execArgv:[],...opts});w.on('message',async value=>{await w.terminate();resolve(value);});w.on('error',reject);});
 const expected={kill:false,command:'su\nls',members:['function','function','function','function'],packageName:'org.autojs.autojs6'};
 for(const [code,opts] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[`const {Worker,parentPort}=require('node:worker_threads');const w=new Worker(${JSON.stringify(cjs)},{eval:true});w.on('message',x=>parentPort.postMessage(x));w.on('error',e=>{throw e});`,{eval:true}]])
  assert.deepEqual(await run(code,opts),expected);
 assert(calls.some(c=>c[1]==='resolvePackage'));
});

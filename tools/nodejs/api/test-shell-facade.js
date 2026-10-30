const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events'),streams=require('node:stream');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const a=source.indexOf('  function __autojs6_create_shell('),z=source.indexOf('  // Main-thread shell facade;',a);assert(a>=0&&z>a);
const create=new Function('return ('+source.slice(a,z)+')')();
function fixture({delay,closeInputDelay,denied=[]}={}){
 const calls=[],live=new Map();let n=0;
 const api=create(async(module,method,args,options)=>{
  calls.push({module,method,args,options});
  if(method==='getAccess')return {shell:true,rootAvailable:false,shizukuRunning:true,shizukuGranted:true,shizuku:true,uid:2000,missingCapabilities:[]};
  if(method==='requestShizukuPermission')return true;
  if(['spawn','open'].includes(method)){
   if(delay)await delay;const id='shell-'+ ++n,opts=args[method==='open'?0:1],command=method==='open'?'cat':args[0];
   const entry={queue:[],opts,command};live.set(id,entry);
   if(command==='echo')entry.queue.push({type:'stdout',data:Buffer.from('a\0中文\nlast').toString('base64')},{type:'stderr',data:Buffer.from('err\n').toString('base64')},{type:'end',stream:'stdout'},{type:'end',stream:'stderr'},{type:'exit',code:7,timedOut:false});
   if(command==='large')for(let i=0;i<80;i++)entry.queue.push({type:'stdout',data:Buffer.alloc(4096,i).toString('base64')});
   if(command==='large')entry.queue.push({type:'exit',code:0,timedOut:false});
   return {id,pid:n,mode:opts.shizuku?'adb':'sh'};
  }
  const entry=live.get(args[0]);
  if(method==='close'){live.delete(args[0]);return {wasAlive:!!entry,code:137};}
  if(method==='write'){entry.queue.push({type:'stdout',data:args[1]});return null;}
  if(method==='closeInput'){if(closeInputDelay)await new Promise(r=>setTimeout(r,closeInputDelay));if(entry?.command==='cat')entry.queue.push({type:'exit',code:0,timedOut:false});return null;}
  if(method==='kill'){entry.queue.push({type:'exit',code:137,timedOut:false});return true;}
  if(method==='drain'){if(!entry)return [];const batch=entry.queue.splice(0,8);if(batch.some(e=>e.type==='exit'))live.delete(args[0]);if(!batch.length)await new Promise(r=>setTimeout(r,5));return batch;}
 },EventEmitter,streams,Buffer,required=>required.filter(x=>denied.includes(x)));
 return {...api,calls,live};
}
test('exec captures binary transport, preserves nonzero exit, and bounds captured bytes',async()=>{
 const f=fixture(),r=await f.shell.exec('echo');assert.equal(r.code,7);assert.equal(r.stdout,'a\0中文\nlast');assert.equal(r.stderr,'err\n');assert.equal(r.truncated,false);assert(Object.isFrozen(r));assert.equal(f.live.size,0);
 const small=await f.shell.exec('echo',{maxOutputBytes:1});assert.equal(small.stdout,'a');assert.equal(small.truncated,true);
});
test('exec resolves when the command exits before the host acknowledges the end of input',async()=>{
 const f=fixture({closeInputDelay:50}),r=await f.shell.exec('echo');assert.equal(r.code,7);assert.equal(r.stdout,'a\0中文\nlast');assert.equal(f.live.size,0);
});
test('open stdin, asynchronous line reads and EOF share a real writable/readable pair',async()=>{
 const f=fixture(),p=f.shell.open({cwd:'/system',env:{OWNED:'yes'}});await p.ready;await p.writeLine('你好');assert.equal(await p.readLine(),'你好');await p.closeInput();assert.equal(await p.readLine(),null);assert.equal(await p.waitFor(),0);assert(p.closed);assert.equal(await p.close(),false);
});
test('raw stream backpressure pauses host draining until a consumer reads',async()=>{
 const f=fixture(),p=f.shell.spawn('large');await p.ready;await new Promise(r=>setTimeout(r,100));const before=f.calls.filter(c=>c.method==='drain').length;await new Promise(r=>setTimeout(r,100));assert.equal(f.calls.filter(c=>c.method==='drain').length,before);
 let bytes=0;p.stdout.on('data',x=>bytes+=x.length);p.stderr.resume();assert.equal(await p.waitFor(),0);assert.equal(bytes,80*4096);
});
test('Shizuku flags, access checks and permission requests use the shared transport',async()=>{
 const f=fixture({denied:['shell.root']});assert.equal(f.shell.checkAccess({root:true}).root,false);assert.equal(f.shizuku.checkAccess().shizuku,true);assert.equal((await f.shizuku.getAccess()).uid,2000);assert.equal(await f.shizuku.requestPermission(),true);
 await f.shizuku('echo');const c=f.calls.find(x=>x.method==='spawn');assert(c.args[1].shizuku);assert.deepEqual(c.options.permissions,['shell','shell.shizuku']);
});
test('abort during creation releases the late native process and keeps failures observable',async()=>{
 let finish;const f=fixture({delay:new Promise(r=>finish=r)}),c=new AbortController(),p=f.shell.spawn('sleep',{signal:c.signal});await new Promise(r=>setImmediate(r));c.abort();finish();await assert.rejects(p.ready,{code:'ABORT_ERR'});await assert.rejects(p.exited,{code:'ABORT_ERR'});await p.close();assert.equal(f.live.size,0);
});
test('close terminates an unread process; wait timeouts do not kill it',async()=>{
 const f=fixture(),p=f.shell.spawn('sleep');await p.ready;assert.equal(await p.waitFor(20),null);assert.equal(f.live.size,1);assert.equal(await p.close(),true);assert.equal(await p.waitFor(),137);assert.equal(f.live.size,0);
});
test('invalid requests fail before native execution and raw/line consumers cannot mix',async()=>{
 const f=fixture();await assert.rejects(f.shell.exec('x',{env:{BAD:2}}));await assert.rejects(f.shell.execFile('/system/bin/id',[1]));assert.throws(()=>f.shell.spawn('x',{timeoutMs:-1}));assert.equal(f.calls.length,0);
 const p=f.shell.open();p.stdout.resume();assert.throws(()=>p.readLine(),/already used/);await p.close();
});

test('line events flush an unterminated UTF-8 tail before the exit notification',async()=>{
 const f=fixture(),p=f.shell.spawn('echo'),out=[],err=[];p.on('output',line=>out.push(line));p.on('errorOutput',line=>err.push(line));await p.waitFor();assert.deepEqual(out,['a\0中文','last']);assert.deepEqual(err,['err']);
});

test('write chunks fit the host budget even when every base64 slash is escaped',async()=>{
 const f=fixture(),p=f.shell.open(),output=[];p.stdout.on('data',x=>output.push(x));p.stderr.resume();await p.ready;const bytes=Buffer.alloc(70000,255);await p.write(bytes);await p.closeInput();await p.waitFor();assert(Buffer.concat(output).equals(bytes));
 for(const call of f.calls.filter(c=>c.method==='write'))assert(Buffer.byteLength(JSON.stringify(call.args).replaceAll('/',String.fromCharCode(92)+'/'))<=32768);
});

const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const {pathToFileURL} = require('node:url');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
function part(a,z) { const start=source.indexOf(a),end=source.indexOf(z,start);assert(start>=0&&end>start);return source.slice(start,end); }
const constructor=part('  function __autojs6_worker_constructor(', '  // The worker keeps synchronous metadata');
const modules=part('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(');
const preload=part('  function __autojs6_worker_preload(', '  function __autojs6_limited_worker_threads(');
const facade=part('  // Shared mail facade.', '  // End shared mail facade.');
const createWorker=new Function('return ('+constructor+')')();
const directory=path.resolve(__dirname,'../../../build/worker-mail-tests');fs.mkdirSync(directory,{recursive:true});
const preloadPath=path.join(directory,'preload.cjs');
fs.writeFileSync(preloadPath,facade+'\n('+preload+')(require,'+constructor+',()=>{},'+modules+','+Array(17).fill('undefined').join(',')+',__autojs6_create_mail);');

test('CJS, ESM and nested mail Workers close their transport owners while the parent stays usable', async () => {
  const owners=new Map([['parent',[]]]);let next=0;
  const config={preloadPath,entryUrl:pathToFileURL(path.join(directory,'entry.cjs')).href,gatedBuiltins:[],moduleSources:{},typeScriptSources:{},resourceLimits:{},hostModuleMethods:{mail:Object.keys(new Function(facade+';return __autojs6_create_mail;')()(()=>{}))}};
  const Worker=createWorker(require,()=>config,async(_,method,args)=>{
    if(method==='open'){const id='worker-'+ ++next;owners.set(id,[]);return {id};}
    if(method==='close')return owners.delete(args[0]);
    if(method==='start'){
      const id='op-'+ ++next;
      owners.get(args[0]).push({id,data:Buffer.from(JSON.stringify({type:'resolve',value:{id,account:{address:'fixture@example.invalid',owner:args[0]},isConnected:true}})).toString('base64'),last:true});
      return {id};
    }
    if(method==='poll')return owners.get(args[0]).splice(0);
    throw Error(method);
  });
  const body="const account=await mail.connect({address:'fixture@example.invalid',password:'synthetic'});parentPort.postMessage(account.account);setInterval(()=>{},1000);";
  const cjs="const {parentPort}=require('node:worker_threads'),mail=require('mail');(async()=>{"+body+'})();';
  const esm="import {parentPort} from 'node:worker_threads';import mail from 'autojs6:mail';"+body;
  const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
  for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
    const worker=new Worker(entry,{execArgv:[],...options});
    const result=await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
    assert.equal(result.address,'fixture@example.invalid');assert(owners.has(result.owner));await worker.terminate();
    for(let n=0;n<100&&owners.size!==1;n++)await new Promise(resolve=>setTimeout(resolve,10));
    assert.deepEqual([...owners.keys()],['parent']);
  }
});

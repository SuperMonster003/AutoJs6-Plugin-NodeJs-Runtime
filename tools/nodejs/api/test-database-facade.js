const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const start=source.indexOf('  function __autojs6_create_database('),end=source.indexOf('  // Main-thread database facade;',start);
assert(start>=0&&end>start);const factory=new Function('return ('+source.slice(start,end)+')')();
function fixture() {
 const calls=[];let counter=0;
 const api=factory(async(module,method,args,options)=>{
  if(options.signal?.aborted)throw options.signal.reason;
  calls.push({module,method,args,options});
  if(method==='open')return {id:'owned-'+(++counter),name:args[0].name};
  if(method==='close')return true;
  if(method==='all')return [{n:1}];
  if(method==='transaction')return [null,{rowsAffected:1,insertId:1}];
  return method==='get'?null:{rowsAffected:1,insertId:1};
 });return {api,calls};
}
test('database and callable sqlite forward object overload timeout and AbortSignal',async()=>{
 const {api,calls}=fixture(),controller=new AbortController();
 const db=await api.database.open({name:' owned ',timeoutMs:37,signal:controller.signal});
 assert.equal(db.name,'owned');assert.equal(calls[0].options.timeoutMs,37);assert.equal(calls[0].options.signal,controller.signal);
 const alias=await api.sqlite({name:'alias',timeoutMs:41,signal:controller.signal});assert.equal(alias.name,'alias');assert.equal(calls[1].module,'sqlite');assert.equal(calls[1].options.timeoutMs,41);
 await api.sqlite.open('named',{timeoutMs:43});assert.equal(calls[2].options.timeoutMs,43);
 assert(Object.isFrozen(db));assert(Object.isFrozen(api.sqlite));
});
test('empty object names and invalid SQL parameters never reach transport',()=>{
 const {api,calls}=fixture();
 for(const name of [{},{name:null},{name:undefined},'',null,{name:'../outside'},'x'.repeat(65)])assert.throws(()=>api.database.open(name));
 assert.equal(calls.length,0);
});
test('pre-aborted object options preserve the cancellation and do not open a handle',async()=>{
 const {api,calls}=fixture(),controller=new AbortController();controller.abort(new Error('owned cancellation'));
 await assert.rejects(api.database.open({name:'owned',signal:controller.signal}),/owned cancellation/);assert.equal(calls.length,0);
});
test('parameter types, result defaults, transaction operations and close semantics remain intact',async()=>{
 const {api,calls}=fixture(),db=await api.database.open('owned');
 assert.equal(await db.exec('CREATE TABLE owned(id)'),undefined);
 await db.run('INSERT INTO owned VALUES(?)',[null]);assert.deepEqual(calls.at(-1).args.slice(1),['INSERT INTO owned VALUES(?)',[null]]);
 assert.equal(await db.get('SELECT * FROM owned'),null);assert(Object.isFrozen(await db.all('SELECT * FROM owned')));
 assert.deepEqual(await db.transaction([{type:'exec',sql:'DELETE FROM owned'},{type:'run',sql:'INSERT INTO owned VALUES(?)',params:[true]}]),[null,{rowsAffected:1,insertId:1}]);
 assert.equal(calls.at(-1).options.timeoutMs,15000);
 for(const params of [[NaN],[Infinity],[{}],[1n],Array(129).fill(1)])assert.throws(()=>db.run('SELECT ?',params));
 assert.throws(()=>db.transaction(()=>{}));assert.throws(()=>db.exec('ATTACH DATABASE "x" AS y'));
 assert.equal(await db.close(),true);const count=calls.length;assert.equal(await db.close(),false);assert.equal(calls.length,count);
 assert.throws(()=>db.get('SELECT 1'),/closed/);
});

const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8');
const chunk=source.slice(source.indexOf('  // Shared native Android helpers, colors and calendar task facades.'),source.indexOf('  // Main-thread native Android and calendar modules.'));
const factory=new Function(chunk+';return {android:__autojs6_create_android,colors:__autojs6_create_colors,tasks:__autojs6_create_tasks};')();
test('native Paint/color-state helpers send typed colors, expose cloneable snapshots and close',async()=>{
  const calls=[];let state;
  const call=async(module,method,args,opts)=>{
    calls.push({module,method,args,opts});
    if(method==='createPaint')return state={id:'paint',__autojs6AndroidObject:'paint',color:args[0].color,textSize:16};
    if(method==='setPaintColor')state={...state,color:args[1]};
    if(method==='getPaintInfo')return state;
    if(method==='createColorStateList')return {...args[0],id:'states',__autojs6AndroidObject:'colorStateList',defaultColor:args[0].colors.at(-1),stateful:true};
    if(method==='getThemeColor')return {colorPrimary:-65536,colorPrimaryDark:-16777216,colorAccent:-1};
    if(method==='close')return true;
  };
  let colors;const android=factory.android(call,value=>colors.toInt(value));colors=factory.colors(android);
  const paint=await colors.createPaint({color:'#f00'});assert.equal(paint.color,-65536);await colors.setPaintColor(paint,'#00ff00');assert.equal((await paint.getInfo()).color,-16711936);
  assert.equal(structuredClone(paint).color,-16711936);assert.equal(structuredClone(paint).close,undefined);
  const list=await colors.createColorStateList({states:[['pressed'],[]],colors:['red','blue']});assert.equal(list.defaultColor,-16776961);
  assert.equal((await colors.themeColor).colorPrimary,-65536);assert.equal(colors.toInt(await colors.themeColor),-65536);assert.equal(await paint.close(),true);assert.equal(await paint.close(),false);await list.close();
  assert(calls.every(c=>c.module==='android'&&c.opts.permissions[0]==='android.objects'));
});
test('tasks preserve Date instants, freeze snapshots, update options and use numeric identities',async()=>{
  const calls=[],api=factory.tasks(async(module,method,args)=>{calls.push({module,method,args});if(method.startsWith('add'))return {id:5,kind:method==='addIntentTask'?'intent':'timed',options:args[0]};if(method==='updateTask')return {id:5,kind:'timed',options:args[0]};return true;});
  const task=await api.addDailyTask({path:'child.cjs',time:new Date(12345)});assert.equal(calls[0].args[0].time,12345);assert.equal(calls[0].args[0].repeatMode,'daily');assert(Object.isFrozen(task.options));
  await api.updateTask(task,{time:'09:30'});assert.equal(calls.at(-1).args[0].time,'09:30');assert.equal(calls.at(-1).args[0].path,undefined,'updates contain only the explicit patch');await api.removeTask(task);assert.equal(calls.at(-1).method,'removeTimedTask');
  const intent=await api.addBroadcastIntentTask({path:'child.cjs',action:'owned'});await api.removeTask(intent);assert.equal(calls.at(-1).method,'removeIntentTask');
  assert.equal(api.daysToTimeFlag([0,1,1,6]),67);assert.deepEqual(api.timeFlagToDays(67),[0,1,6]);assert.throws(()=>api.daysToTimeFlag([7]));
});
test('aborted registrations close only the unconfirmed Android object or persistent task',async()=>{
  for(const kind of ['android','tasks']){
    const controller=new AbortController(),calls=[];
    const call=async(module,method,args)=>{calls.push({method,args});if(method.startsWith('create')||method.startsWith('add')){controller.abort();return {id:7,kind:'timed',__autojs6AndroidObject:'paint'};}return true;};
    const promise=kind==='android'?factory.android(call).createPaint({}, {signal:controller.signal}):factory.tasks(call).addCountdownTask({path:'child.cjs',delayMs:1000,signal:controller.signal});
    await assert.rejects(promise,{code:'ABORT_ERR'});assert.equal(calls[1].method,kind==='android'?'close':'removeTimedTask');
  }
});

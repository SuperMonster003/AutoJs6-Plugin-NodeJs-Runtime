// Rhino parity of the device, sensors and media family (api-coverage P3.1c): the synchronous device fields and checks of the
// startup snapshot, the host routes of Rhino's device members with their capabilities (Q146 (a)), the vibrate / keep*
// overloads, Rhino's EventEmitter semantics of sensors and sensors.register (Q147 (a)), and Rhino's media members in
// autojs6:media for main threads and Workers (Q149 (a)).
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {EventEmitter}=require('node:events');
const source=fs.readFileSync(path.join(__dirname,'../../../app/src/main/cpp/node_bridge_sources.cpp'),'utf8').replace(/\r\n/g,'\n');
function part(first,last){const a=source.indexOf(first),z=source.indexOf(last,a);assert(a>=0&&z>a,first);return source.slice(a,z);}
const createDevice=new Function('return ('+part('  function __autojs6_create_device(','  function __autojs6_create_dialogs(')+')')();
const createSensors=new Function('return ('+part('  function __autojs6_create_sensors(','  // Main-thread sensors facade')+')')();

const snapshot={sdkInt:35,width:1080,height:2400,density:2.75,densityDpi:440,scaledDensity:2.75,release:'15',model:'23049RAD8C',brand:'Redmi',
 product:'sunstone',board:'sunstone',bootloader:'unknown',hardware:'qcom',fingerprint:'Redmi/x',buildId:'AQ3A.240912.001',buildDisplay:'AQ3A.240912.001 release-keys',
 manufacturer:'Xiaomi',device:'sunstone',incremental:'V816',baseOS:'',securityPatch:'2026-09-01',codename:'REL',pageSize:4096,marketName:'Redmi Note 12 Turbo',
 manufacturers:{isXiaomi:true,isHuawei:false},brands:{isRedmi:true},roms:{isMiui:true,isQiku:false},
 sensors:{accelerometer:{name:'acc',vendor:'v',type:1,maximumRange:78.4},light:{name:'light',type:5}}};

// A fake host: answers[module.method] is a value or a function of the arguments; every call is recorded.
function fake(answers={}){
 const calls=[];
 const call=async(module,method,args,options={})=>{
  calls.push({module,method,args,options});
  const answer=answers[module+'.'+method];
  return typeof answer==='function'?answer(...args):answer;
 };
 return {calls,call,last:()=>calls[calls.length-1]};
}

test('device fields and checks come from the startup snapshot as in Rhino',()=>{
 const device=createDevice(async()=>null,snapshot,'scope-1');
 assert.deepEqual([device.width,device.height,device.density,device.buildId,device.buildDisplay,device.manufacturer,device.device,device.incremental,
  device.baseOS,device.securityPatch,device.codename,device.pageSize],[1080,2400,440,'AQ3A.240912.001 release-keys','AQ3A.240912.001 release-keys','Xiaomi',
  'sunstone','V816','','2026-09-01','REL',4096]);
 assert.equal(device.digest(),'Redmi (Xiaomi) / sunstone (23049RAD8C) / 15 (35)');
 assert.equal(device.getMarketName(),'Redmi Note 12 Turbo');
 assert.deepEqual([device.isManufacturer(' xiaomi '),device.isManufacturer('redmi')],[true,false]);
 assert.throws(()=>device.isManufacturer(null),TypeError);
 assert.deepEqual([device.manufacturers.isXiaomi(),device.manufacturers.isHuawei(),device.manufacturers.isSony(),device.brands.isRedmi(),device.brands.isXperia(),
  device.roms.isMiui(),device.roms.isFlyme()],[true,false,false,true,false,true,false]);
 assert.equal(Object.keys(device.manufacturers).length,17);
 assert.equal(device.sensors,undefined);
 assert(Object.isFrozen(device));
 const same=createDevice(async()=>null,{...snapshot,manufacturer:'Redmi',device:'23049RAD8C'});
 assert.equal(same.digest(),'Redmi / 23049RAD8C / 15 (35)');
});

test('device queries reach the host routes with Rhino capabilities (Q146 (a))',async()=>{
 const {calls,call,last}=fake({'device.getRotation':1,'device.getOrientation':2,'device.getIMEI':null,'device.getSerial':'unknown','device.isScreenOn':false,
  'device.setPointerLocation':true,'device.isBackgroundStartGranted':true,'device.getIpAddress':'10.0.0.2'});
 const device=createDevice(call,snapshot,'scope-1');
 assert.equal(await device.rotation,1);
 assert.equal(await device.orientation,2);
 assert.equal(await device.imei,null);
 assert.deepEqual([last().method,last().options.permissions],['getIMEI',['device','device.identifiers']]);
 assert.equal(await device.serial,'unknown');
 for(const name of ['rotation','orientation','imei','serial']) assert.equal(Object.getOwnPropertyDescriptor(device,name).get.__autojs6LazyExport,true);
 for(const method of ['getIMEI','getSerial','getMacAddress','getSharedDeviceId']){
  await device[method]();
  assert.deepEqual([last().method,last().args,last().options.permissions],[method,[],['device','device.identifiers']]);
 }
 await device.getAndroidId(true);
 assert.deepEqual([last().args,last().options.permissions],[[true],['device','device.identifiers']]);
 for(const method of ['summary','getOrientation','getRotation','isScreenOff','isScreenPortrait','isScreenLandscape','isPowerSourceAC','isPowerSourceUSB',
  'isPowerSourceWireless','isPowerSourceDock','getIpv6Address','getGatewayAddress','isActiveNetworkMetered','isConnectedOrConnecting','isWifiAvailable',
  'isPointerLocationEnabled','isPointerLocationDisabled','hasReadPhoneStatePermission','ensureReadPhoneStatePermission','cancelVibration']){
  await device[method]({timeoutMs:1234});
  assert.deepEqual([last().method,last().args,last().options.permissions,last().options.timeoutMs],[method,[],['device'],1234]);
 }
 assert.equal(await device.getIpAddress(),'10.0.0.2');
 assert.deepEqual(last().args,[]);
 await device.getIpAddress(false);
 assert.deepEqual(last().args,[false]);
 assert.equal(await device.roms.isBackgroundStartGranted(),true);
 assert.deepEqual([last().method,last().options.permissions],['isBackgroundStartGranted',['device']]);
 for(const [method,value] of [['setPointerLocationEnabled',true],['setPointerLocationDisabled',false]]){
  assert.equal(await device[method](),true);
  assert.deepEqual([last().method,last().args,last().options.permissions],['setPointerLocation',[value],['device','device.power']]);
 }
 await device.togglePointerLocation();
 assert.deepEqual([last().method,last().options.permissions],['togglePointerLocation',['device','device.power']]);
 const before=calls.length;
 await device.wakeUpIfNeeded();
 assert.deepEqual(calls.slice(before).map(c=>c.method),['isScreenOn','wakeUp']);
});

test('keepScreenOn / keepScreenDim / keepAwake take an optional timeout as in Rhino',async()=>{
 const {call,last}=fake();
 const device=createDevice(call,snapshot,'scope-1');
 await device.keepScreenOn();
 assert.deepEqual([last().method,last().args,last().options.permissions],['keepScreenOn',[null,'scope-1'],['device','device.power']]);
 await device.keepScreenOn(5000);
 assert.deepEqual(last().args,[5000,'scope-1']);
 await device.keepScreenOn({timeoutMs:100});
 assert.deepEqual([last().args,last().options.timeoutMs],[[null,'scope-1'],100]);
 await device.keepScreenDim(3000);
 assert.deepEqual([last().method,last().args],['keepAwake',[0x10000006,3000,'scope-1']]);
 await device.keepAwake(1);
 assert.deepEqual([last().method,last().args],['keepAwake',[1,null,'scope-1']]);
 await assert.rejects(device.keepScreenOn(0),TypeError);
 await assert.rejects(device.keepAwake('1'),TypeError);
});

test('device.vibrate takes Rhino overloads (millis, timings, morse code, off + millis, timings + off, morse + delay)',async()=>{
 const {call,last}=fake();
 const device=createDevice(call,snapshot,'scope-1',{morsePattern:source=>source==='e'?[100]:source===''?[]:[100,100,300]});
 await device.vibrate(200);
 assert.deepEqual(last().args,[200]);
 await device.vibrate(20000);
 assert.deepEqual(last().args,[10000]);
 await device.vibrate([0,100,'50']);
 assert.deepEqual(last().args,[[0,100,50]]);
 await device.vibrate(50,300);
 assert.deepEqual(last().args,[[50,300]]);
 await device.vibrate([100,200],30);
 assert.deepEqual(last().args,[[30,100,200]]);
 await device.vibrate('e');
 assert.deepEqual(last().args,[[0,100]]);
 await device.vibrate('ab',250.7);
 assert.deepEqual(last().args,[[250,100,100,300]]);
 const count=last();
 await device.vibrate('');
 assert.equal(last(),count);
 await assert.rejects(device.vibrate([1,'x']),TypeError);
 await assert.rejects(device.vibrate([1,-2]),TypeError);
 await assert.rejects(device.vibrate({}),TypeError);
 await assert.rejects(createDevice(call,snapshot).vibrate('sos'),TypeError);
});

// Pushed sensor events: attachEvents hands the test each subscription's event sink.
function sensorsHost(options={}){
 const host=fake({'sensors.subscribe':request=>({id:'sub-'+request.type}),'sensors.unsubscribe':true});
 const sinks=new Map();
 const attach=(record,process)=>{sinks.set(record.id,process);return ()=>sinks.delete(record.id);};
 const sensors=createSensors(host.call,EventEmitter,attach,undefined,{sensors:snapshot.sensors,missing:()=>options.missing||[]});
 return {...host,sensors,sinks};
}

test('sensors is a Rhino EventEmitter with a Timer (Q147 (a))',()=>{
 const {sensors}=sensorsHost();
 const seen=[];
 sensors.on('newListener',(name)=>seen.push('new:'+name));
 sensors.on('removeListener',(name)=>seen.push('remove:'+name));
 const a=(...args)=>seen.push(['a',...args]),b=(...args)=>seen.push(['b',...args]);
 assert.equal(sensors.on('x',a),sensors);
 sensors.prependOnceListener('x',b);
 assert.deepEqual(sensors.listeners('x'),[b,a]);
 assert.equal(sensors.emit('x',1,2),true);
 assert.equal(sensors.emit('y'),false);
 assert.equal(sensors.listenerCount('x'),1);
 sensors.emitSticky('s',7);
 sensors.once('s',a);
 assert.equal(sensors.listenerCount('s'),0);
 sensors.on('s',b);
 assert.equal(sensors.listenerCount('s'),1);
 sensors.removeListener('x',a);
 sensors.removeAllListeners('s');
 // Rhino emits newListener after adding, so the first listener sees its own registration.
 assert.deepEqual(seen,['new:newListener','new:removeListener','new:x','new:x',['b',1,2],['a',1,2],['a',7],'new:s',['b',7],'new:s','remove:x','remove:s']);
 const timer=sensors.getTimer();
 assert.equal(timer,sensors.register('light').getTimer());
 const fired=[];
 const id=timer.setTimeout((...args)=>fired.push(args),1,'a');
 assert.deepEqual([typeof id,timer.hasPendingCallbacks(),timer.clearInterval(id),timer.clearTimeout(id),timer.clearTimeout(id),timer.hasPendingCallbacks()],
  ['number',true,false,true,false,false]);
 assert.equal(sensors.getMaxListeners(),10);
 sensors.setMaxListeners(1);
 sensors.on('z',a);
 assert.throws(()=>sensors.on('z',b),RangeError);
 assert.deepEqual(sensors.eventNames(),['newListener','removeListener','x','z']);
 assert.deepEqual(sensors.delay,{normal:3,ui:2,game:1,fastest:0});
 sensors.register=null;
 assert.equal(typeof sensors.register,'function');
 sensors.ignoresUnsupportedSensor=true;
 assert.equal(sensors.ignoresUnsupportedSensor,true);
});

test('sensors.register follows Rhino: unknown sensors, delays, change / accuracy_change and unregister (Q147 (a))',async()=>{
 const {sensors,calls,sinks}=sensorsHost();
 assert.equal(sensors.register('gyroscope'),null);
 const unsupported=[];
 sensors.on('unsupported_sensor',name=>unsupported.push(name));
 sensors.ignoresUnsupportedSensor=true;
 const noOp=sensors.register('Gyroscope');
 assert.deepEqual(unsupported,['Gyroscope']);
 assert.equal(sensors.register('pressure'),noOp);
 noOp.unregister();
 assert.equal(sensors.getSensor('LIGHT').name,'light');
 assert.equal(sensors.getSensor('gravity'),null);
 const emitter=sensors.register('ACCELEROMETER',sensors.delay.game);
 const changes=[],accuracy=[];
 emitter.on('change',(event,x,y,z)=>changes.push([event.sensor.name,event.values.length,x,y,z]));
 emitter.on('accuracy_change',value=>accuracy.push(value));
 await new Promise(resolve=>setImmediate(resolve));
 const subscribe=calls.find(c=>c.method==='subscribe');
 assert.deepEqual([subscribe.args[0].type,subscribe.args[0].delay,subscribe.args[0].samplingIntervalMs,subscribe.options.permissions],['accelerometer',1,20,['sensors']]);
 sinks.get('sub-accelerometer')({type:'event',event:{type:'accelerometer',timestamp:1,accuracy:3,values:[0.5,9.8,0.1]}});
 sinks.get('sub-accelerometer')({type:'accuracy',accuracy:2});
 assert.deepEqual([changes,accuracy],[[['acc',3,0.5,9.8,0.1]],[2]]);
 const light=sensors.register('light',100000);
 await new Promise(resolve=>setImmediate(resolve));
 const lightRequest=calls.filter(c=>c.method==='subscribe')[1].args[0];
 assert.deepEqual([lightRequest.delay,lightRequest.samplingIntervalMs],[undefined,100]);
 emitter.unregister();
 sensors.unregisterAll();
 await new Promise(resolve=>setTimeout(resolve,10));
 assert.deepEqual(calls.filter(c=>c.method==='unsubscribe').map(c=>c.args[0].id).sort(),['sub-accelerometer','sub-light']);
 assert.equal(sinks.size,0);
 assert.throws(()=>sensors.register('light',-1),TypeError);
 assert(light);
});

test('sensors.register throws when the sensors capability is not declared; once keeps both forms',async()=>{
 const {sensors}=sensorsHost({missing:['sensors']});
 assert.throws(()=>sensors.register('light'),error=>error.code==='ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED'&&error.missingCapabilities[0]==='sensors');
 const seen=[];
 assert.equal(sensors.once('e',value=>seen.push(value)),sensors);
 sensors.emit('e',1);
 sensors.emit('e',2);
 assert.deepEqual(seen,[1]);
 const {sensors:other,sinks}=sensorsHost();
 const sample=other.once('light',{timeoutMs:1000});
 await new Promise(resolve=>setImmediate(resolve));
 sinks.get('sub-light')({type:'event',event:{type:'light',timestamp:2,accuracy:3,values:[42]}});
 assert.deepEqual((await sample).values,[42]);
});

test('media carries Rhino members in main threads and Workers; Workers get the device and sensors snapshots',()=>{
 const media=part('  function __autojs6_limited_media() {','  function __autojs6_mediainfo_path(');
 const lines=media.split(String.fromCharCode(10)).map(line=>line.trim());
 for(const name of ['playMusic','pauseMusic','resumeMusic','stopMusic','musicSeekTo','isMusicPlaying','getMusicDuration','getMusicCurrentPosition','scanFile']){
  assert(lines.some(line=>line===name+','||line===name||line.startsWith(name+':')),name);
 }
 const table=part('          hostModuleMethods: {','\n          },');
 assert(table.includes('sensors: Object.keys(__autojs6_limited_sensors())'));
 assert(table.includes('media: Object.keys(__autojs6_limited_media())'));
 assert(part('  async function __autojs6_worker_invoke(','  // The Worker system module').includes(': name === "media" ? __autojs6_limited_media()'));
 assert(source.includes('createSensors(bridgeCall, SensorEmitter, undefined, undefined,'));
 assert(source.includes('sensorsMissing:__autojs6_bridge_missing_capabilities(["sensors"])'));
});

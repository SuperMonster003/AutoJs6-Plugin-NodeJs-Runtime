// Rhino / Node comparison cases of the device, sensors and media family on a device (AutoJs6 api-coverage P3.1c).
// Each case is [id, rhinoExpression, nodeExpression] or [id, null, nodeExpression, expected] for Node-only members whose
// Rhino counterpart has no comparable value. Node expressions see device, sensors, media (autojs6:device / sensors / media)
// and WAV (a silent 3 s WAV file on the device); they may return a Promise, which is awaited. Rhino expressions use the
// Rhino globals. A case whose id ends with "~" waits for an event: its Rhino expression is a function taking done(value).
// Identifiers, addresses and the summary are compared through dmHash (FNV-1a), so no value of them is printed. Only cases
// without lasting effects: the pointer location is set to its current state, wake locks and vibrations are short and
// cancelled, the music is silent and stopped. Plain ES5 and arrow functions only.
var DM_MANUFACTURERS = ["isHtc", "isLG", "isOnePlus", "isSamsung", "isZte", "isLetv", "isHuawei", "isNova", "isHonor", "isXiaomi", "isOppo", "isSony",
    "isVivo", "isLenovo", "isCoolpad", "isSmartisan", "isMeizu"];
var DM_BRANDS = ["isXperia", "isRedmi", "isMiMix"];
var DM_ROMS = ["isMiui", "isEmui", "isOppo", "isSmartisan", "isVivo", "isGionee", "isLenovo", "isFlyme", "isQiku"];
var DM_SENSORS = ["accelerometer", "ambient_temperature", "gravity", "gyroscope", "light", "linear_acceleration", "magnetic_field", "orientation",
    "pressure", "proximity", "relative_humidity", "temperature", "step_counter", "rotation_vector"];

function dmHash(value) {
    if (value === null || value === undefined) return null;
    var text = String(value), h = 0x811c9dc5;
    for (var i = 0; i < text.length; i++) {
        h ^= text.charCodeAt(i);
        h = (h + ((h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24))) >>> 0;
    }
    return text.length + ":" + h.toString(16);
}

function dmCalls(target, names) {
    var out = [];
    for (var i = 0; i < names.length; i++) out.push(Boolean(target[names[i]]()));
    return out;
}

function dmContains(list, name) {
    for (var i = 0; i < list.length; i++) if (String(list[i]) === name) return true;
    return false;
}

var DEVICE_MEDIA_CASES = [

    // device: the startup snapshot (Q148 (a))
    ["device.build", "[device.width, device.height, device.density, String(device.buildId), String(device.buildDisplay), String(device.product), String(device.board)," +
        " String(device.brand), String(device.manufacturer), String(device.device), String(device.model), String(device.bootloader), String(device.hardware)," +
        " String(device.fingerprint), device.sdkInt, String(device.incremental), String(device.release), String(device.baseOS), String(device.securityPatch), String(device.codename)]",
        "[device.width, device.height, device.density, device.buildId, device.buildDisplay, device.product, device.board, device.brand, device.manufacturer, device.device," +
        " device.model, device.bootloader, device.hardware, device.fingerprint, device.sdkInt, device.incremental, device.release, device.baseOS, device.securityPatch, device.codename]"],
    ["device.derived", "[Number(device.pageSize), String(device.digest()), String(device.getMarketName()), device.isManufacturer(' ' + String(device.manufacturer).toUpperCase() + ' '), device.isManufacturer('p31c')]",
        "[device.pageSize, device.digest(), device.getMarketName(), device.isManufacturer(' ' + device.manufacturer.toUpperCase() + ' '), device.isManufacturer('p31c')]"],
    ["device.manufacturers", "dmCalls(device.manufacturers, DM_MANUFACTURERS)", "dmCalls(device.manufacturers, DM_MANUFACTURERS)"],
    ["device.brands", "dmCalls(device.brands, DM_BRANDS)", "dmCalls(device.brands, DM_BRANDS)"],
    ["device.roms", "dmCalls(device.roms, DM_ROMS)", "dmCalls(device.roms, DM_ROMS)"],
    ["device.roms.isBackgroundStartGranted", "device.roms.isBackgroundStartGranted(context)", "device.roms.isBackgroundStartGranted()"],

    // device: host queries
    ["device.screen", "[device.isScreenOn(), device.isScreenOff(), device.isScreenPortrait(), device.isScreenLandscape(), device.getOrientation(), device.getRotation(), device.orientation, device.rotation]",
        "Promise.all([device.isScreenOn(), device.isScreenOff(), device.isScreenPortrait(), device.isScreenLandscape(), device.getOrientation(), device.getRotation(), device.orientation, device.rotation])"],
    ["device.power", "[device.isPowerSourceAC(), device.isPowerSourceUSB(), device.isPowerSourceWireless(), device.isPowerSourceDock(), device.isCharging()]",
        "Promise.all([device.isPowerSourceAC(), device.isPowerSourceUSB(), device.isPowerSourceWireless(), device.isPowerSourceDock(), device.isCharging()])"],
    ["device.network", "[dmHash(device.getIpAddress()), dmHash(device.getIpAddress(true)), dmHash(device.getIpAddress(false)), dmHash(device.getGatewayAddress())," +
        " device.isActiveNetworkMetered(), device.isConnectedOrConnecting(), device.isWifiAvailable()]",
        "Promise.all([device.getIpAddress().then(dmHash), device.getIpAddress(true).then(dmHash), device.getIpAddress(false).then(dmHash), device.getGatewayAddress().then(dmHash)," +
        " device.isActiveNetworkMetered(), device.isConnectedOrConnecting(), device.isWifiAvailable()])"],
    ["device.ipv6", "typeof String(device.getIpv6Address())", "device.getIpv6Address().then(v => typeof v)"],
    ["device.summary", "dmHash(device.summary())", "device.summary().then(dmHash)"],
    // The current state again: no toggle (Rhino and Node both answer true without Root or Shizuku).
    ["device.pointerLocation", "[device.isPointerLocationEnabled(), device.isPointerLocationDisabled(), device.setPointerLocation(device.isPointerLocationEnabled())]",
        "(async () => [await device.isPointerLocationEnabled(), await device.isPointerLocationDisabled(), await device.setPointerLocation(await device.isPointerLocationEnabled())])()"],
    ["device.phoneState", "[device.hasReadPhoneStatePermission(), (function () { try { device.ensureReadPhoneStatePermission(); return 'ok'; } catch (e) { return 'threw'; } })()]",
        "Promise.all([device.hasReadPhoneStatePermission(), device.ensureReadPhoneStatePermission().then(() => 'ok', () => 'threw')])"],
    ["device.identifiers", "[dmHash(device.getAndroidId()), dmHash(device.getAndroidId(true)), dmHash(device.getIMEI()), dmHash(device.getSerial()), dmHash(device.imei)," +
        " dmHash(device.serial), dmHash(device.getSharedDeviceId())]",
        "Promise.all([device.getAndroidId(), device.getAndroidId(true), device.getIMEI(), device.getSerial(), device.imei, device.serial, device.getSharedDeviceId()]).then(v => v.map(dmHash))"],
    ["device.macAddress", "(function () { try { return dmHash(device.getMacAddress()); } catch (e) { return 'threw'; } })()",
        "device.getMacAddress().then(dmHash, () => 'threw')"],
    ["device.vibrate", "[device.vibrate(20), device.vibrate([0, 20, 20, 20]), device.vibrate('e', 0), device.vibrate(0, 20), device.vibrate([20], 10), device.cancelVibration()]",
        "(async () => [await device.vibrate(20), await device.vibrate([0, 20, 20, 20]), await device.vibrate('e', 0), await device.vibrate(0, 20), await device.vibrate([20], 10), await device.cancelVibration()])()"],
    ["device.keepAwake", "[device.keepScreenOn(1000), device.keepScreenDim(1000), device.keepAwake(1, 500), device.keepScreenOn(), device.cancelKeepingAwake(), device.wakeUpIfNeeded(), device.isScreenOn()]",
        "(async () => [await device.keepScreenOn(1000), await device.keepScreenDim(1000), await device.keepAwake(1, 500), await device.keepScreenOn(), await device.cancelKeepingAwake(), await device.wakeUpIfNeeded(), await device.isScreenOn()])()"],

    // sensors (Q147 (a))
    ["sensors.basics", "[sensors.delay.normal, sensors.delay.ui, sensors.delay.game, sensors.delay.fastest, sensors.ignoresUnsupportedSensor, sensors.register('p31c_none') === null, sensors.getSensor('p31c_none') === null]",
        "[sensors.delay.normal, sensors.delay.ui, sensors.delay.game, sensors.delay.fastest, sensors.ignoresUnsupportedSensor, sensors.register('p31c_none') === null, sensors.getSensor('p31c_none') === null]"],
    ["sensors.available", "DM_SENSORS.map(function (n) { return sensors.getSensor(n) !== null; })", "DM_SENSORS.map(n => sensors.getSensor(n) !== null)"],
    ["sensors.getSensor", "(function (s) { return s === null ? null : [String(s.getName()), String(s.getVendor()), s.getType(), s.getVersion(), Number(s.getMaximumRange()), s.getMinDelay(), s.getReportingMode(), s.isWakeUpSensor()]; })(sensors.getSensor('ACCELEROMETER'))",
        "(s => s === null ? null : [s.name, s.vendor, s.type, s.version, s.maximumRange, s.minDelay, s.reportingMode, s.wakeUpSensor])(sensors.getSensor('ACCELEROMETER'))"],
    ["sensors.unsupported", "(function () { var seen = []; var f = function (n) { seen.push(String(n)); }; sensors.on('unsupported_sensor', f); sensors.ignoresUnsupportedSensor = true;" +
        " var e = sensors.register('p31c_none'); var r = [e !== null, typeof e.unregister, seen.slice()]; e.unregister(); sensors.ignoresUnsupportedSensor = false;" +
        " sensors.removeListener('unsupported_sensor', f); return r; })()",
        "(() => { const seen = []; const f = n => seen.push(String(n)); sensors.on('unsupported_sensor', f); sensors.ignoresUnsupportedSensor = true;" +
        " const e = sensors.register('p31c_none'); const r = [e !== null, typeof e.unregister, seen.slice()]; e.unregister(); sensors.ignoresUnsupportedSensor = false;" +
        " sensors.removeListener('unsupported_sensor', f); return r; })()"],
    ["sensors.emitter", "(function () { var out = []; var f = function (x) { out.push('a' + x); }; var g = function (x) { out.push('b' + x); }; sensors.emitSticky('p31c', 1);" +
        " sensors.once('p31c', f); sensors.on('p31c', g); var n1 = sensors.listenerCount('p31c'); var hit = sensors.emit('p31c', 2); sensors.prependOnceListener('p31c', f); sensors.emit('p31c', 3);" +
        " sensors.removeListener('p31c', g); var r = [out, n1, hit, sensors.listenerCount('p31c'), sensors.getMaxListeners(), dmContains(sensors.eventNames(), 'p31c'), sensors.emit('p31c_none')];" +
        " sensors.removeAllListeners('p31c'); return r; })()",
        "(() => { const out = []; const f = x => out.push('a' + x); const g = x => out.push('b' + x); sensors.emitSticky('p31c', 1);" +
        " sensors.once('p31c', f); sensors.on('p31c', g); const n1 = sensors.listenerCount('p31c'); const hit = sensors.emit('p31c', 2); sensors.prependOnceListener('p31c', f); sensors.emit('p31c', 3);" +
        " sensors.removeListener('p31c', g); const r = [out, n1, hit, sensors.listenerCount('p31c'), sensors.getMaxListeners(), dmContains(sensors.eventNames(), 'p31c'), sensors.emit('p31c_none')];" +
        " sensors.removeAllListeners('p31c'); return r; })()"],
    // Q147 (a): Node's getTimer gives a Timer (Rhino's Sensors has none and returns null).
    ["sensors.getTimer", null, "typeof sensors.getTimer().setTimeout", "function"],
    ["sensors.unregisterAll", "(function () { var a = sensors.register('accelerometer'), b = sensors.register('accelerometer', 100000); sensors.unregisterAll(); sensors.unregister(null); return [a !== null, b !== null]; })()",
        "(() => { const a = sensors.register('accelerometer'), b = sensors.register('accelerometer', 100000); sensors.unregisterAll(); sensors.unregister(null); return [a !== null, b !== null]; })()"],

    // media (Q149 (a)): a silent file; positions depend on timing, so only their sign is compared.
    ["media.idle", "[media.isMusicPlaying(), media.getMusicDuration(), media.getMusicCurrentPosition()]",
        "Promise.all([media.isMusicPlaying(), media.getMusicDuration(), media.getMusicCurrentPosition()])"],
    ["media.music", "(function () { var r = []; media.playMusic(WAV, 0, false); sleep(800); r.push(media.isMusicPlaying(), media.getMusicDuration(), media.getMusicCurrentPosition() >= 0);" +
        " media.pauseMusic(); sleep(200); r.push(media.isMusicPlaying()); media.resumeMusic(); sleep(300); r.push(media.isMusicPlaying()); media.musicSeekTo(1000); sleep(200);" +
        " r.push(media.getMusicCurrentPosition() >= 1000); media.stopMusic(); sleep(200); r.push(media.isMusicPlaying(), media.getMusicDuration(), media.getMusicCurrentPosition()); return r; })()",
        "(async () => { const sleep = ms => new Promise(resolve => setTimeout(resolve, ms)); const r = []; await media.playMusic(WAV, 0, false); await sleep(800);" +
        " r.push(await media.isMusicPlaying(), await media.getMusicDuration(), await media.getMusicCurrentPosition() >= 0); await media.pauseMusic(); await sleep(200);" +
        " r.push(await media.isMusicPlaying()); await media.resumeMusic(); await sleep(300); r.push(await media.isMusicPlaying()); await media.musicSeekTo(1000); await sleep(200);" +
        " r.push(await media.getMusicCurrentPosition() >= 1000); await media.stopMusic(); await sleep(200);" +
        " r.push(await media.isMusicPlaying(), await media.getMusicDuration(), await media.getMusicCurrentPosition()); return r; })()"],
    // Rhino's scanFile returns nothing; Node resolves whether the host scanned the file.
    ["media.scanFile", "media.scanFile(WAV) === undefined", "media.scanFile(WAV).then(v => typeof v === 'boolean')"],
    // Last: Rhino runs the later cases before it dispatches the event, and sensors.unregisterAll would end this registration.
    ["sensors.change~", "(function (done) { var e = sensors.register('accelerometer', sensors.delay.game); if (e === null) { done(null); return; }" +
        " e.on('change', function (event) { var r = [event.values.length, arguments.length - 1, typeof arguments[1], String(event.sensor.getName()), event.timestamp > 0];" +
        " e.unregister(); done(r); }); })",
        "new Promise((resolve, reject) => { const e = sensors.register('accelerometer', sensors.delay.game); if (e === null) return resolve(null);" +
        " const timer = setTimeout(() => { sensors.unregister(e); reject(new Error('no change event in 10 s')); }, 10000);" +
        " e.on('change', (event, ...values) => { clearTimeout(timer); const r = [event.values.length, values.length, typeof values[0], event.sensor.name, event.timestamp > 0];" +
        " e.unregister(); resolve(r); }); })"]
];

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: DEVICE_MEDIA_CASES};
}

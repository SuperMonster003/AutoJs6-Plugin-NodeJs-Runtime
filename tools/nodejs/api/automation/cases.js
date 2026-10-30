// Rhino / Node comparison cases of the automation family on a device (AutoJs6 api-coverage P3.4).
// Each case is [id, rhinoExpression, nodeExpression] or [id, null, nodeExpression, expected] for Node-only members whose
// Rhino counterpart has no comparable value. Node expressions see automator, auto, threads, continuation, timers, yolo,
// selector, qrcode, keys, ocr, events and system (autojs6:<name>, ocr / keys / qrcode by their bare names too); they may
// return a Promise, which is awaited. Rhino expressions use the Rhino globals. A case whose id ends with "~" waits for an
// event: its Rhino expression is a function taking done(value). The accessibility service of the host is expected to run.
// Only cases without lasting effects: actions target texts, indexes and bounds that match no node, gestures use negative
// coordinates (Android rejects them before dispatching), modes / flags / key interception / timeouts are restored, and
// every listener, timer and task is removed or ends. Plain ES5 in Rhino expressions.
var AUTOMATION_CASES = [

    // automator: the service and actions that find nothing
    ["automator.isServiceRunning", "automator.isServiceRunning()", "automator.isServiceRunning()"],
    ["automator.ensureService", "[automator.ensureService(), 'ok']", "automator.ensureService().then(v => [v, 'ok'])"],
    ["automator.waitForService", "[automator.waitForService(2000), 'ok']", "automator.waitForService(2000).then(v => [v, 'ok'])"],
    ["automator.click.text", "[click('p34_none_text'), longClick('p34_none_text'), click('p34_none_text', 0)]",
        "Promise.all([automator.click('p34_none_text'), automator.longClick('p34_none_text'), automator.click('p34_none_text', 0)])"],
    ["automator.click.bounds", "[click(-30, -30, -20, -20), longClick(-30, -30, -20, -20)]",
        "Promise.all([automator.click(-30, -30, -20, -20), automator.longClick(-30, -30, -20, -20)])"],
    ["automator.scroll", "[scrollUp('p34_none_text'), scrollDown(-30, -30, -20, -20)]",
        "Promise.all([automator.scrollUp('p34_none_text'), automator.scrollDown(-30, -30, -20, -20)])"],
    ["automator.edit", "[setText(99, 'p34'), input(99, 'p34')]", "Promise.all([automator.setText(99, 'p34'), automator.input(99, 'p34')])"],
    ["automator.gesture.negative", "(function () { var r = []; [function () { return press(-10, -10, 1); }, function () { return swipe(-10, -10, -20, -20, 10); }," +
        " function () { return gesture(10, [-10, -10], [-20, -20]); }, function () { return gestures([0, 10, [-10, -10], [-20, -20]]); }].forEach(function (f) {" +
        " try { r.push(f()); } catch (e) { r.push('threw'); } }); return r; })()",
        "(async () => { const r = []; for (const f of [() => automator.press(-10, -10, 1), () => automator.swipe(-10, -10, -20, -20, 10)," +
        " () => automator.gesture(10, [-10, -10], [-20, -20]), () => automator.gestures([0, 10, [-10, -10], [-20, -20]])]) {" +
        " try { r.push(await f()); } catch (e) { r.push('threw'); } } return r; })()"],
    ["automator.retry", "retry(function (n) { if (n < 2) throw new Error('p34'); return n; }, {times: 3, delay: 1})",
        "automator.retry(n => { if (n < 2) throw new Error('p34'); return n; }, {times: 3, delay: 1})"],

    // auto
    ["auto.state", "(function (s) { return [s.hasInstance, s.hasService, s.isRunning, s.isOperational]; })(auto.state)",
        "auto.state.then(s => [s.hasInstance, s.hasService, s.isRunning, s.isOperational])"],
    ["auto.queries", "[auto.hasInstance(), auto.hasService(), auto.exists(), auto.isRunning(), auto.isOperational()]",
        "Promise.all([auto.hasInstance(), auto.hasService(), auto.exists(), auto.isRunning(), auto.isOperational()])"],
    ["auto.getters", "[auto.service !== null, auto.services.length > 0, auto.windows.length > 0, auto.root !== null, auto.rootInActiveWindow !== null, auto.windowRoots.length > 0]",
        "(async () => [(await auto.service) !== null, (await auto.services).length > 0, (await auto.windows).length > 0, (await auto.root) !== null," +
        " (await auto.rootInActiveWindow) !== null, (await auto.windowRoots).length > 0])()"],
    ["auto.findWindows", "[auto.findWindows({packageName: 'p34.none'}).length, auto.findWindows().length > 0, auto.findWindowRoots({packageName: 'p34.none'}).length]",
        "Promise.all([auto.findWindows({packageName: 'p34.none'}).then(l => l.length), auto.findWindows().then(l => l.length > 0)," +
        " auto.findWindowRoots({packageName: 'p34.none'}).then(l => l.length)])"],
    ["auto.findWindows.badKey", "(function () { try { auto.findWindows({p34: 1}); return 'ok'; } catch (e) { return 'threw'; } })()",
        "auto.findWindows({p34: 1}).then(() => 'ok', () => 'threw')"],
    ["auto.getWindowRoot.invalid", "(function () { try { auto.getWindowRoot('p34'); return 'ok'; } catch (e) { return 'threw'; } })()",
        "auto.getWindowRoot('p34').then(() => 'ok', () => 'threw')"],
    ["auto.mode", "(function () { var r = []; auto.setMode('FAST'); r.push('set'); try { auto.setMode('p34'); r.push('ok'); } catch (e) { r.push('threw'); } auto.setMode('normal'); return r; })()",
        "(() => { const r = []; auto.setMode('FAST'); r.push('set'); try { auto.setMode('p34'); r.push('ok'); } catch (e) { r.push('threw'); } auto.setMode('normal'); return r; })()"],
    // Not useUsageStats: Rhino then needs the usage stats permission and opens its settings page without it.
    ["auto.flags", "(function () { var r = []; auto.setFlags(['findOnUiThread', '!noFastPath']); r.push('set'); try { auto.setFlags('p34'); r.push('ok'); } catch (e) { r.push('threw'); }" +
        " try { auto.setFlags(['useShell', '!useShell']); r.push('ok'); } catch (e) { r.push('threw'); } auto.clearFlags('findOnUiThread'); return r; })()",
        "(() => { const r = []; auto.setFlags(['findOnUiThread', '!noFastPath']); r.push('set'); try { auto.setFlags('p34'); r.push('ok'); } catch (e) { r.push('threw'); }" +
        " try { auto.setFlags(['useShell', '!useShell']); r.push('ok'); } catch (e) { r.push('threw'); } auto.clearFlags('findOnUiThread'); return r; })()"],
    ["auto.waitFor", "[auto.waitFor(2000), 'ok']", "auto.waitFor(2000).then(v => [v, 'ok'])"],
    ["auto.registerEvent", "(function () { auto.registerEvent('window_state_changed', function () {}); auto.removeEvent('window_state_changed'); return 'ok'; })()",
        "(async () => { await auto.registerEvent('window_state_changed', () => {}); await auto.removeEvent('window_state_changed'); return 'ok'; })()"],
    ["auto.stateListener", "(function () { auto.stateListener({onConnected: function () {}}); auto.stateListener(null); return 'ok'; })()",
        "(async () => { await auto.stateListener({onConnected() {}}); await auto.stateListener(null); return 'ok'; })()"],
    ["auto.current", "[typeof auto.currentPackage(), typeof auto.currentActivity(), typeof auto.currentComponent()]",
        "Promise.all([auto.currentPackage(), auto.currentActivity(), auto.currentComponent()]).then(v => v.map(x => typeof x))"],
    ["auto.dump", "typeof auto.dump()", "auto.dump().then(v => typeof v)"],
    ["auto.clearCache", "typeof auto.clearCache()", "auto.clearCache().then(v => typeof v)"],
    ["auto.enable", null, "[auto.enable === auto.start, auto.disable === auto.stop]", [true, true]],

    // keys
    ["keys.codes", "[keys.home, keys.HOME, keys.back, keys.BACK, keys.menu, keys.MENU, keys.volumeUp, keys.volume_up, keys.VOLUME_UP, keys.volumeDown, keys.volume_down, keys.VOLUME_DOWN]",
        "[keys.home, keys.HOME, keys.back, keys.BACK, keys.menu, keys.MENU, keys.volumeUp, keys.volume_up, keys.VOLUME_UP, keys.volumeDown, keys.volume_down, keys.VOLUME_DOWN]"],

    // threads (Q175 (a) / Q176 (a))
    ["threads.start", "(function () { var t = threads.start(function () { sleep(50); }); t.join(); return [t.isAlive(), typeof t.getId()]; })()",
        "(async () => { const t = threads.start(() => system.sleep(50)); await t.join(); return [t.isAlive(), typeof t.getId()]; })()"],
    ["threads.join.timeout", "(function () { var t = threads.start(function () { sleep(800); }); t.join(50); var r = t.isAlive(); t.interrupt(); return r; })()",
        "(async () => { const t = threads.start(() => system.sleep(800)); await t.join(50); const r = t.isAlive(); t.interrupt(); return r; })()"],
    ["threads.atomic", "(function () { var a = threads.atomic(5); return [Number(a.incrementAndGet()), Number(a.getAndAdd(3)), Number(a.get()), a.compareAndSet(9, 1), Number(a.get()), Number(a.decrementAndGet())]; })()",
        "(() => { const a = threads.atomic(5); return [a.incrementAndGet(), a.getAndAdd(3), a.get(), a.compareAndSet(9, 1), a.get(), a.decrementAndGet()]; })()"],
    ["threads.lock", "(function () { var l = threads.lock(); var r = [l.tryLock(), l.isLocked()]; l.unlock(); r.push(l.isLocked()); return r; })()",
        "(async () => { const l = threads.lock(); const r = [await l.tryLock(), l.isLocked()]; l.unlock(); r.push(l.isLocked()); return r; })()"],
    ["threads.disposable", "(function () { var d = threads.disposable(); threads.start(function () { sleep(20); d.setAndNotify(7); }); return [Number(d.blockedGet(3000)), threads.disposable().blockedGet(50)]; })()",
        "(async () => { const d = threads.disposable(); threads.start(async () => { await system.sleep(20); d.setAndNotify(7); }); return [Number(await d.blockedGet(3000)), await threads.disposable().blockedGet(50)]; })()"],
    ["threads.pool", "(function () { var p = threads.pool({corePoolSize: 1, maxPoolSize: 2}); var r = [typeof p.submit]; p.shutdown(); r.push(p.isShutdown()); return r; })()",
        "(() => { const p = threads.pool({corePoolSize: 1, maxPoolSize: 2}); const r = [typeof p.submit]; p.shutdown(); r.push(p.isShutdown()); return r; })()"],
    // Rhino's rule when a maximum is given; without maxPoolSize Rhino also rejects corePoolSize > 0 (Q180), Node does not.
    ["threads.pool.sizes", "(function () { try { threads.pool({corePoolSize: 3, maxPoolSize: 2}); return 'ok'; } catch (e) { return 'threw'; } })()",
        "(() => { try { threads.pool({corePoolSize: 3, maxPoolSize: 2}); return 'ok'; } catch (e) { return 'threw'; } })()"],
    ["threads.sync", "sync(function (a) { return a + 1; })(1)", "threads.sync(a => a + 1)(1)"],

    // continuation (Q179: Node awaits natively)
    ["continuation.enabled", null, "continuation.enabled", true],
    ["continuation.create", null, "(async () => { const c = continuation.create(); setTimeout(() => c.resume(5), 10); return [await c.await(), await continuation.await(6)]; })()", [5, 6]],

    // timers (D-8)
    ["timers.keepAlive", null, "(() => { const t = timers.keepAlive(10); const r = [typeof t.ref, typeof t.unref]; clearTimeout(t); const u = timers.keepAlive(); clearInterval(u); return r; })()",
        ["function", "function"]],

    // yolo: no YOLO plugin answers a made-up component; the failure carries a YOLO_* code
    ["yolo.load.unknown", null, "yolo.load('/sdcard/Download/p34-none', {component: 'p34.none/p34.None', timeoutMillis: 3000}).then(() => 'ok', e => /^YOLO_/.test(String(e.code)))", true],

    // ocr / qrcode
    ["ocr.mode", null, "(() => { const r = [ocr.mode]; ocr.tap('Paddle'); r.push(ocr.mode); ocr.mode = 'mlkit'; r.push(ocr.mode); ocr.mode = ''; r.push(ocr.mode); return r; })()",
        ["auto", "paddle", "mlkit", "auto"]],
    ["ocr.tap.unknown", "(function () { try { ocr.tap('p34'); return 'ok'; } catch (e) { return 'threw'; } })()", "(() => { try { ocr.tap('p34'); return 'ok'; } catch (e) { return 'threw'; } })()"],
    ["ocr.fixed", null, "[typeof ocr.mlkit.recognize, typeof ocr.paddle.detect, typeof ocr.rapid.recognizeText, typeof ocr.summary(), typeof ocr.detect]",
        ["function", "function", "function", "string", "function"]],
    ["qrcode.members", "[typeof qrcode.detect, typeof qrcode.detectAll, typeof qrcode.recognizeText, typeof qrcode.recognizeTexts]",
        "[typeof qrcode.detect, typeof qrcode.detectAll, typeof qrcode.recognizeText, typeof qrcode.recognizeTexts]"],

    // events (Q18): sticky events, key listeners, key interception, the touch timeout
    ["events.emitter.sticky", "(function () { var e = events.emitter(); e.emitSticky('p34', 1); var got = []; e.on('p34', function (v) { got.push(v); }); e.emit('p34', 2); return got; })()",
        "(() => { const e = events.emitter(); e.emitSticky('p34', 1); const got = []; e.on('p34', v => got.push(v)); e.emit('p34', 2); return got; })()"],
    ["events.module.sticky", "(function () { events.emitSticky('p34m', 3); var got = []; var f = function (v) { got.push(v); }; events.on('p34m', f); var n = events.listenerCount('p34m');" +
        " events.removeAllListeners('p34m'); return [got, n, events.listenerCount('p34m')]; })()",
        "(() => { events.emitSticky('p34m', 3); const got = []; const f = v => got.push(v); events.on('p34m', f); const n = events.listenerCount('p34m');" +
        " events.removeAllListeners('p34m'); return [got, n, events.listenerCount('p34m')]; })()"],
    ["events.keyListeners", "(function () { var f = function () {}; events.onKeyDown('volume_up', f); events.onKeyUp('volume_up', f); events.onceKeyDown('volume_down', f);" +
        " events.removeAllKeyDownListeners('volume_up'); events.removeAllKeyUpListeners('volume_up'); events.removeAllKeyDownListeners('volume_down'); return 'ok'; })()",
        "(() => { const f = () => {}; events.onKeyDown('volume_up', f); events.onKeyUp('volume_up', f); events.onceKeyDown('volume_down', f);" +
        " events.removeAllKeyDownListeners('volume_up'); events.removeAllKeyUpListeners('volume_up'); events.removeAllKeyDownListeners('volume_down'); return 'ok'; })()"],
    ["events.touchTimeout", "(function () { var r = [Number(events.getTouchEventTimeout())]; events.setTouchEventTimeout(30); r.push(Number(events.getTouchEventTimeout())); events.setTouchEventTimeout(10); return r; })()",
        "(() => { const r = [events.getTouchEventTimeout()]; events.setTouchEventTimeout(30); r.push(events.getTouchEventTimeout()); events.setTouchEventTimeout(10); return r; })()"],
    ["events.keyInterception", "(function () { try { events.setKeyInterceptionEnabled('volume_up', true); events.setKeyInterceptionEnabled('volume_up', false); return 'ok'; } catch (e) { return 'threw'; } })()",
        "(async () => { try { await events.setKeyInterceptionEnabled('volume_up', true); await events.setKeyInterceptionEnabled('volume_up', false); return 'ok'; } catch (e) { return 'threw: ' + e.code; } })()"],

    // selector globals and Rhino's sleep / wait
    ["selector.absent", "[pickup('p34_none_text'), existsOne('p34_none_a', 'p34_none_b'), existsAll('p34_none_a')]",
        "Promise.all([selector.pickup('p34_none_text'), selector.existsOne('p34_none_a', 'p34_none_b'), selector.existsAll('p34_none_a')])"],
    ["global.sleep", "(function () { var t = Date.now(); sleep(30, 60); var d = Date.now() - t; return d >= 25 && d < 3000; })()",
        "(async () => { const t = Date.now(); await system.sleep(30, 60); const d = Date.now() - t; return d >= 25 && d < 3000; })()"],
    ["global.wait", "(function () { var n = 0, m = 0; return [wait(function () { return ++n >= 3; }, 10, 10), n, wait(function () { m++; return false; }, 2, 10, {'else': function () { return 'no'; }}), m]; })()",
        "(async () => { let n = 0, m = 0; return [await system.wait(() => ++n >= 3, 10, 10), n, await system.wait(() => { m++; return false; }, 2, 10, {else: () => 'no'}), m]; })()"],
    ["global.wait.async", null, "typeof system.wait.async", "function"],
    // Q177 (a): the family's Rhino globals in every form (main thread, ES module, nested Worker).
    ["global.installed", null, "['click', 'longClick', 'press', 'swipe', 'gesture', 'gestureAsync', 'gestures', 'gesturesAsync', 'scrollUp', 'scrollDown', 'input', 'setText', 'back', 'home'," +
        " 'recents', 'notifications', 'quickSettings', 'powerDialog', 'splitScreen', 'retry', 'sync', 'keepAlive', 'loop', 'pickup', 'detect', 'existsOne', 'existsAll', 'sleep', 'wait']" +
        ".filter(n => typeof globalThis[n] !== 'function')", []],

    // Last: events that arrive later (Rhino runs the cases above before it dispatches them).
    ["timers.setIntervalExt~", "(function (done) { var n = 0; timers.setIntervalExt(function () { n++; }, 20, function () { return n >= 3 ? n : false; }, function (v) { done([Number(v), n]); }); })",
        "new Promise(resolve => { let n = 0; timers.setIntervalExt(() => n++, 20, () => n >= 3 ? n : false, v => resolve([v, n])); })"],
    ["events.broadcast~", "(function (done) { events.broadcast.on('p34b', function (v) { events.broadcast.removeAllListeners('p34b'); done(v); }); events.broadcast.emit('p34b', 3); })",
        "new Promise(resolve => { events.broadcast.on('p34b', v => { events.broadcast.removeAllListeners('p34b'); resolve(v); }); events.broadcast.emit('p34b', 3); })"]
];

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: AUTOMATION_CASES};
}

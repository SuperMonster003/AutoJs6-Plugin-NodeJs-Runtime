// Rhino / Node comparison cases of the system information & engines family on a device (AutoJs6 api-coverage P3.1b).
// Each case is [id, rhinoExpression, nodeExpression] or [id, null, nodeExpression, expected] for Node-only members whose
// Rhino counterpart has no comparable value. Node expressions see autojs, installer, memory, settings, sysprops,
// powerManager, system (autojs6:system), notice (autojs6:notifications), engines, plugins, app, dialogs and APK (a copy
// of an installed APK); they may return a Promise, which is awaited. Rhino expressions use the Rhino globals. Values
// depend on the device, so device_system.py runs Rhino and Node in one session and compares them there. Only cases
// without lasting effects: no restart / exit / install / uninstall / stopAll / dumpHeap, no dialog or settings page.
// Plain ES5 and arrow functions only.
var SYSTEM_CASES = [

    // autojs constants, versions (io.github.g00fy2.versioncompare), root mode and the screen
    ["autojs.constants", "[autojs.versionCode, String(autojs.versionName), String(autojs.versionDate), String(autojs.name), String(autojs.packageName)]",
        "[autojs.versionCode, autojs.versionName, autojs.versionDate, autojs.name, autojs.packageName]"],
    ["autojs.version", "[autojs.version.code, String(autojs.version.name), String(autojs.version.date)]",
        "[autojs.version.code, autojs.version.name, autojs.version.date]"],
    ["autojs.version.compare", "[autojs.version.isHigherThan('6.0.0'), autojs.version.isLowerThan('99.0.0'), autojs.version.isEqual(String(autojs.versionName))," +
        " autojs.version.isAtLeast('6.6.0'), autojs.version.isAtLeast(String(autojs.versionName) + '-alpha'), autojs.version.isLowerThan('6.8.0-rc1')]",
        "[autojs.version.isHigherThan('6.0.0'), autojs.version.isLowerThan('99.0.0'), autojs.version.isEqual(autojs.versionName)," +
        " autojs.version.isAtLeast('6.6.0'), autojs.version.isAtLeast(autojs.versionName + '-alpha'), autojs.version.isLowerThan('6.8.0-rc1')]"],
    // Rhino's RootMode object in both (Q135 (b)).
    ["autojs.root", "(function () { var m = autojs.getRootMode(); return [autojs.isRootAvailable(), String(m.key), String(m.description), String(m), String(m.name()), m.ordinal()]; })()",
        "(async () => { const m = await autojs.getRootMode(); return [await autojs.isRootAvailable(), m.key, m.description, String(m), m.name(), m.ordinal()]; })()"],
    // The mode is set without writing the preference and restored (setRootMode takes the RootMode object).
    ["autojs.setRootMode", "(function () { var m = autojs.getRootMode(); autojs.setRootMode('non-root'); var r = String(autojs.getRootMode().name()); autojs.setRootMode(m); return [r, autojs.getRootMode() === m]; })()",
        "(async () => { const m = await autojs.getRootMode(); await autojs.setRootMode('non-root'); const r = (await autojs.getRootMode()).name(); await autojs.setRootMode(m); return [r, await autojs.getRootMode() === m]; })()"],
    ["autojs.setRootMode.bad", "autojs.setRootMode('p31b')", "autojs.setRootMode('p31b')"],
    ["autojs.permissions", "[autojs.canModifySystemSettings(), autojs.canWriteSecureSettings(), autojs.canDisplayOverOtherApps()]",
        "Promise.all([autojs.canModifySystemSettings(), autojs.canWriteSecureSettings(), autojs.canDisplayOverOtherApps()])"],
    ["autojs.language", "[String(autojs.getLanguageTag()), String(autojs.getLanguage().toLanguageTag())]",
        "Promise.all([autojs.getLanguageTag(), autojs.getLanguage().then(l => l.toString())])"],
    ["autojs.screen", "[autojs.isScreenPortrait(), autojs.isScreenLandscape(), Number(autojs.rotation), Number(autojs.orientation)]",
        "Promise.all([autojs.isScreenPortrait(), autojs.isScreenLandscape(), autojs.rotation, autojs.orientation])"],
    // Rhino's ThemeColor has colorPrimary (...) builder methods besides the getters; Node gives the three colors.
    ["autojs.themeColor", "(function (t) { return [Number(t.getColorPrimary()), Number(t.getColorPrimaryDark()), Number(t.getColorAccent())]; })(autojs.themeColor)",
        "autojs.themeColor.then(t => [t.colorPrimary, t.colorPrimaryDark, t.colorAccent])"],
    ["autojs.R", "[Number(R.string.app_name), Number(R.strings.app_name), Number(R.string.p31b_none)]",
        "Promise.all([autojs.R.string.app_name, autojs.R.strings.app_name, autojs.R.string.p31b_none])"],
    ["app.autojs", "app.autojs === autojs", "app.autojs === autojs"],

    // settings, sysprops, memory, powerManager
    ["settings.categories", "systemIds(settings.categories())", "settings.categories().then(systemIds)"],
    ["settings.isEnabled", "[settings.isEnabled('stable_mode'), settings.isEnabled('key_foreground_service')]",
        "Promise.all([settings.isEnabled('stable_mode'), settings.isEnabled('key_foreground_service')])"],
    ["settings.isEnabled.unknown", "settings.isEnabled('p31b')", "settings.isEnabled('p31b')"],
    // Written next to the script; the directory is removed afterwards.
    ["settings.export", "(function (r) { return [String(r.path).split('/').pop(), systemCount(r.categories), systemKeys(r)]; })(settings.export('p31b-settings.json'))",
        "settings.export('p31b-settings.json').then(r => [String(r.path).split('/').pop(), systemCount(r.categories), systemKeys(r)])"],
    ["sysprops.get", "[String(sysprops.get('ro.build.version.sdk')), String(sysprops.get('p31b.none')), String(sysprops.get('p31b.none', 'x'))]",
        "Promise.all([sysprops.get('ro.build.version.sdk'), sysprops.get('p31b.none'), sysprops.get('p31b.none', 'x')])"],
    ["sysprops.typed", "[Number(sysprops.getInt('ro.build.version.sdk')), Number(sysprops.getInt('p31b.none', 7)), sysprops.getBoolean('p31b.none', true)]",
        "Promise.all([sysprops.getInt('ro.build.version.sdk'), sysprops.getInt('p31b.none', 7), sysprops.getBoolean('p31b.none', true)])"],
    ["sysprops.getAll", "(function (a) { return [systemKeys(a).length > 20, String(a['ro.build.version.sdk'])]; })(sysprops.getAll())",
        "sysprops.getAll().then(a => [systemKeys(a).length > 20, String(a['ro.build.version.sdk'])])"],
    ["memory.snapshot", "systemKeys(memory.snapshot())", "memory.snapshot().then(systemKeys)"],
    ["memory.collect", "systemKeys(memory.collect(1000))", "memory.collect(1000).then(systemKeys)"],
    ["memory.collect.range", "memory.collect(-1)", "memory.collect(-1)"],
    ["memory.retained", "typeof systemCount(memory.retained())", "memory.retained().then(l => typeof systemCount(l))"],
    ["powerManager", "[powerManager.isIgnoringBatteryOptimizations(), powerManager.isIgnoringBatteryOptimizations('com.android.settings')]",
        "Promise.all([powerManager.isIgnoringBatteryOptimizations(), powerManager.isIgnoringBatteryOptimizations('com.android.settings')])"],

    // installer (queries only) and notice
    ["installer.queries", "[installer.isAvailable(), installer.isDefault(), systemCount(installer.users()) > 0]",
        "Promise.all([installer.isAvailable(), installer.isDefault(), installer.users().then(l => systemCount(l) > 0)])"],
    ["installer.inspect", "(function (r) { return r ? [String(r.packageName), Number(r.versionCode)] : null; })(installer.inspect(APK))",
        "installer.inspect(APK).then(r => r && [r.packageName, r.versionCode])"],
    ["notice.isEnabled", "notice.isEnabled()", "notice.isEnabled()"],
    ["notice.channel", "(function () { var id = notice.channel.create('p31b_channel', {name: 'P31B'}); var c = notice.channel.contains('p31b_channel');" +
        " var g = notice.channel.get('p31b_channel'); var r = notice.channel.remove('p31b_channel');" +
        " return [String(id), c, g ? String(g.getName()) : null, r, notice.channel.contains('p31b_channel')]; })()",
        "(async () => { const id = await notice.channel.create('p31b_channel', {name: 'P31B'}); const c = await notice.channel.contains('p31b_channel');" +
        " const g = await notice.channel.get('p31b_channel'); const r = await notice.channel.remove('p31b_channel');" +
        " return [id, c, g ? g.name : null, r, await notice.channel.contains('p31b_channel')]; })()"],
    ["notice.channel.getAll", "systemCount(notice.channel.getAll())", "notice.channel.getAll().then(systemCount)"],
    // An omitted id is the running file's full name in both (Q135 (b)): Rhino runs p31b_rhino_<run>.js, Node the main.js of a project.
    ["notice.channel.default", "(function () { var id = String(notice.channel.create({name: 'P31B'})); var r = notice.channel.remove(id); return [id === 'p31b_rhino_' + P31B_RUN + '.js', r]; })()",
        "(async () => { const id = await notice.channel.create({name: 'P31B'}); const r = await notice.channel.remove(id); return [id === 'main.js', r]; })()"],

    // engines (host view, Q131 (a)) and plugins
    ["engines.all", "engines.all().length >= 1", "engines.all().then(l => l.length >= 1)"],
    ["engines.all.shape", null, "engines.all().then(l => [typeof l[0].id, typeof l[0].sourceName, Object.isFrozen(l)])", ["number", "string", true]],
    ["plugins.extend", "(function () { plugins.extend('Mathx'); return [typeof Math.sum, Math.sum(1, 2, 3)]; })()",
        "(() => { plugins.extend('Mathx'); return [typeof Math.sum, Math.sum(1, 2, 3)]; })()"],
    ["plugins.extendAllBut.bad", "plugins.extendAllBut('p31b')", "plugins.extendAllBut('p31b')"],
    ["plugins.tools.list", "systemCount(plugins.tools.list())", "plugins.tools.list().then(systemCount)"],
    ["dialogs.build", null, "(d => [typeof d.show, typeof d.on])(dialogs.build({title: 'p31b'}))", ["function", "function"]],

    // Global system functions (autojs6:system)
    ["global.currentPackage", "[typeof currentPackage(), typeof currentActivity(), typeof currentComponent()]",
        "Promise.all([system.currentPackage(), system.currentActivity(), system.currentComponent()]).then(v => v.map(x => typeof x))"],
    ["global.currentPackage.mode", "currentPackage('p31b')", "system.currentPackage('p31b')"],
    ["global.waitForPackage", "waitForPackage('p31b.none', 2, 100)", "system.waitForPackage('p31b.none', 2, 100)"],
    ["global.waitForPackage.interval", "waitForPackage('p31b.none', 2, -1)", "system.waitForPackage('p31b.none', 2, -1)"],
    ["global.state", "[isStopped(), isRunning(), notStopped(), isShuttingDown()]",
        "[system.isStopped(), system.isRunning(), system.notStopped(), system.isShuttingDown()]"],
    ["global.requiresApi", "[requiresApi(24), requiresAutojsVersion('6.0.0'), requiresAutojsVersion(461)]",
        "[system.requiresApi(24), system.requiresAutojsVersion('6.0.0'), system.requiresAutojsVersion(461)]"],
    ["global.requiresApi.high", "requiresApi(999)", "system.requiresApi(999)"],
    ["global.requiresAutojsVersion.high", "requiresAutojsVersion('99.0.0')", "system.requiresAutojsVersion('99.0.0')"],
    ["global.requiresAutojsVersion.low", "requiresAutojsVersion(100)", "system.requiresAutojsVersion(100)"],
    ["global.metrics", "[WIDTH, HEIGHT, isAutoJs6]", "[system.WIDTH, system.HEIGHT, system.isAutoJs6]"],
    // The same members are globals in Node too (Q135 (b)): the same expressions in both.
    ["global.names", "[typeof currentPackage, typeof currentActivity, typeof currentComponent, typeof exit, typeof stop, typeof isRunning, typeof isStopped," +
        " typeof isShuttingDown, typeof notStopped, typeof requiresApi, typeof requiresAutojsVersion, typeof waitForActivity, typeof waitForPackage," +
        " typeof setScreenMetrics, typeof getClip, typeof setClip, typeof R, typeof WIDTH, typeof HEIGHT, isAutoJs6]",
        "[typeof currentPackage, typeof currentActivity, typeof currentComponent, typeof exit, typeof stop, typeof isRunning, typeof isStopped," +
        " typeof isShuttingDown, typeof notStopped, typeof requiresApi, typeof requiresAutojsVersion, typeof waitForActivity, typeof waitForPackage," +
        " typeof setScreenMetrics, typeof getClip, typeof setClip, typeof R, typeof WIDTH, typeof HEIGHT, isAutoJs6]"],
    ["global.values", "[isStopped(), notStopped(), isRunning(), WIDTH > 0, HEIGHT > 0, requiresApi(24)]",
        "[isStopped(), notStopped(), isRunning(), WIDTH > 0, HEIGHT > 0, requiresApi(24)]"],
    ["global.currentPackage.global", "currentPackage()", "currentPackage()"],
    ["global.R", "R.strings.app_name", "R.strings.app_name"],
    // Android 10+ lets only a focused app read the clipboard: Rhino borrows the focus of a floating window (with the overlay
    // permission), Node reads it directly and gets "" while AutoJs6 is in the background (Q135); only the type is compared.
    ["global.clip", null, "system.setClip('p31b-clip').then(() => system.getClip()).then(t => typeof t)", "string"]
];

function systemCount(value) {
    if (value === null || value === undefined) return null;
    return typeof value.length === "number" ? value.length : Number(value.size());
}

function systemKeys(value) {
    var keys = [];
    if (value && typeof value.keySet === "function") {
        for (var it = value.keySet().iterator(); it.hasNext();) keys.push(String(it.next()));
    } else {
        for (var key in value) keys.push(key);
    }
    return keys.sort();
}

function systemIds(list) {
    var ids = [];
    for (var i = 0; i < systemCount(list); i++) {
        var item = typeof list.length === "number" ? list[i] : list.get(i);
        ids.push(String(item.id));
    }
    return ids;
}

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: SYSTEM_CASES};
}

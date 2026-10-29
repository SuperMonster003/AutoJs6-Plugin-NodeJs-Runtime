// Rhino / Node comparison cases of the app / shell / shizuku family on a device (AutoJs6 api-coverage P3.1a).
// Each case is [id, rhinoExpression, nodeExpression] or [id, null, nodeExpression, expected] for Node-only members whose
// Rhino counterpart has no comparable value. Node expressions see app, shell and shizuku (autojs6:app / shell /
// shizuku) and APK (a copy of an installed APK); they may return a Promise, which is awaited. Rhino expressions use the
// Rhino globals. Results depend on the device (Root, Shizuku, other users), so device_app_shell.py runs Rhino and Node
// in one session and compares them there instead of recording expected values. Plain ES5 and arrow functions only.
var APP_SHELL_CASES = [

    // Command text (Shell.getCommandData)
    ["cmd.object", "String(shell.getCommand('ls', {l: true, maxDepth: 2}))", "shell.getCommand('ls', {l: true, maxDepth: 2})"],
    ["cmd.text", "String(shell.getCommand('ls', 'a|b=1|longName=x y'))", "shell.getCommand('ls', 'a|b=1|longName=x y')"],
    ["cmd.root", "String(shell.getCommand('ls', true))", "shell.getCommand('ls', true)"],
    ["cmd.exit", "String(shell.getCommand('ls', {exit: true}))", "shell.getCommand('ls', {exit: true})"],
    ["cmd.array", "String(shell.getCommand(['echo a', 'echo b']))", "shell.getCommand(['echo a', 'echo b'])"],
    ["cmd.shizuku", "String(shizuku.getCommand('ls', true))", "shizuku.getCommand('ls', true)"],
    ["cmd.badType", "shell.getCommand(1)", "shell.getCommand(1)"],

    // Command results (AbstractShell.Result)
    ["exec.echo", "appShellResult(shell('echo p31a'))", "shell.execCommand('echo p31a').then(appShellResult)"],
    ["exec.exit3", "appShellResult(shell('exit 3'))", "shell.execCommand('exit 3').then(appShellResult)"],
    ["exec.stderr", "appShellResult(shell('echo p31a 1>&2'))", "shell.execCommand('echo p31a 1>&2').then(appShellResult)"],
    ["exec.array", "appShellResult(shell(['echo a', 'echo b']))", "shell.execCommand(['echo a', 'echo b']).then(appShellResult)"],
    ["exec.args", "appShellResult(shell('echo p31a', 'n'))", "shell.execCommand('echo p31a', 'n').then(appShellResult)"],
    ["exec.adbShell", "appShellResult(shell('adb shell echo p31a'))", "shell.execCommand('adb shell echo p31a').then(appShellResult)"],
    ["exec.execCommand", "appShellResult(shell.execCommand('echo p31a'))", "shell.execCommand('echo p31a').then(appShellResult)"],
    // Root: on a device without Root only the exit code is compared (the error text names the failed process).
    ["exec.root.code", "shell('echo p31a', true).code", "shell.execCommand('echo p31a', true).then(r => r.code)"],
    ["root.available", "Boolean(shell.isRootAvailable())", "shell.isRootAvailable()"],
    ["shell.kill", "shell.kill('com.android.settings')", "shell.kill('com.android.settings')"],
    ["shell.currentPackage", "String(shell.currentPackage())", "shell.currentPackage()"],
    ["shell.currentComponent", "String(shell.currentComponent())", "shell.currentComponent()"],
    // Node quotes the data and omits a zero -f (app.intentToShell); the action and the data are compared.
    ["shell.fromIntent", "(function (s) { return [s.indexOf(\"-a 'android.intent.action.VIEW'\") >= 0, s.indexOf('https://example.com') >= 0]; })(String(shell.fromIntent(app.intent({action: 'VIEW', data: 'https://example.com'}))))",
        "(s => [s.includes(\"-a 'android.intent.action.VIEW'\"), s.includes('https://example.com')])(shell.fromIntent({action: 'VIEW', data: 'https://example.com'}))"],
    ["shell.setDefaultOptions", "(function () { shell.setDefaultOptions({root: false}); var r = shell('echo p31a').code; shell.setDefaultOptions(null); return r; })()",
        "(async () => { shell.setDefaultOptions({root: false}); const r = (await shell.execCommand('echo p31a')).code; shell.setDefaultOptions(null); return r; })()"],

    // Shizuku
    ["shizuku.exec", "appShellResult(shizuku('echo p31a'))", "shizuku.execCommand('echo p31a').then(appShellResult)"],
    ["shizuku.exec.code", "shizuku('exit 3').code", "shizuku.execCommand('exit 3').then(r => r.code)"],
    ["shizuku.currentPackage", "String(shizuku.currentPackage())", "shizuku.currentPackage()"],
    ["shizuku.currentComponent", "String(shizuku.currentComponent())", "shizuku.currentComponent()"],
    ["shizuku.kill", "shizuku.kill('com.android.settings')", "shizuku.kill('com.android.settings')"],
    // hasService tells whether the host has bound its Shizuku user service, which the first command through Shizuku
    // starts; the state cases follow the commands so that Rhino and Node, run one after the other, see the same service.
    ["shizuku.state", "appShellState(shizuku.state)", "shizuku.state.then(appShellState)"],
    ["shizuku.functions", "appShellState(shizuku.state)",
        "Promise.all([shizuku.isInstalled(), shizuku.hasService(), shizuku.isRunning(), shizuku.hasPermission(), shizuku.isOperational()])" +
        ".then(v => ({isInstalled: v[0], hasService: v[1], isRunning: v[2], hasPermission: v[3], isOperational: v[4]}))"],

    // app queries and D-4
    ["app.getAppByAlias", "(function (a) { return a ? String(a.packageName) : null; })(app.getAppByAlias('QQ'))",
        "app.getAppByAlias('QQ').then(a => a && a.packageName)"],
    ["app.getInstalledPackages", "app.getInstalledPackages().length > 0", "app.getInstalledPackages().then(l => l.length > 0)"],
    ["app.getInstalledPackages.activities", "(function (l) { for (var i = 0; i < l.length; i++) if (String(l[i].packageName) === 'com.android.settings') return l[i].activities !== null; return null; })(app.getInstalledPackages({get: ['activities']}))",
        "app.getInstalledPackages({get: ['activities']}).then(l => { const p = l.find(x => x.packageName === 'com.android.settings'); return p ? p.activities !== null : null; })"],
    ["app.getApkInfo", "(function (p) { return p ? [String(p.packageName), Number(p.versionCode)] : null; })(app.getApkInfo(APK))",
        "app.getApkInfo(APK).then(p => p && [p.packageName, p.versionCode])"],
    ["app.isDualInstalled", "app.isDualInstalled('com.android.settings')", "app.isDualInstalled('com.android.settings')"],
    ["app.kill", "app.kill('com.android.settings')", "app.kill('com.android.settings')"],
    ["app.killDual", "app.killDual('com.android.settings')", "app.killDual('com.android.settings')"],
    ["app.sendLocalBroadcastSync", "app.sendLocalBroadcastSync(app.intent({action: 'org.autojs.p31a.LOCAL'}))", "app.sendLocalBroadcastSync('org.autojs.p31a.LOCAL')"],

    // Global key functions
    ["key.unknown", "KeyCode('p31a-none')", "shell.KeyCode('p31a-none')"],
    ["key.badType", "KeyCode({})", "shell.KeyCode({})"],
    ["key.volume", "(function () { VolumeUp(); VolumeDown(); return 'done'; })()", "shell.VolumeUp().then(() => shell.VolumeDown()).then(() => 'done')"],

    // ShellProcess members (Q31)
    ["process.members", null,
        "(async () => { const p = shell.spawn('echo p31a'); let text = ''; p.getInputStream().on('data', x => text += x); await p.waitFor();" +
        " return [p.isAlive(), text, /^ShellProcess \\{ pid: \\d+, mode: sh, command: echo p31a, alive: false, exitCode: 0 \\}$/.test(String(p))," +
        " p.getOutputStream() === p.stdin, p.getErrorStream() === p.stderr, p.listenerCount('exit'), typeof p.prependOnceListener]; })()",
        [false, "p31a\n", true, true, true, 0, "function"]]
];

function appShellResult(r) {
    return {code: Number(r.code), result: String(r.result), error: String(r.error)};
}

function appShellState(s) {
    return {isInstalled: Boolean(s.isInstalled), hasService: Boolean(s.hasService), isRunning: Boolean(s.isRunning),
        hasPermission: Boolean(s.hasPermission), isOperational: Boolean(s.isOperational)};
}

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: APP_SHELL_CASES};
}

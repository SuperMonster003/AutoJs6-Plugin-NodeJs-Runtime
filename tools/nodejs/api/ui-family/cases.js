// Rhino / Node comparison cases of the UI family on a device (AutoJs6 api-coverage P3.3).
// Each case is [id, rhinoExpression, nodeExpression] or [id, null, nodeExpression, expected] for Node-only members whose
// Rhino counterpart has no comparable value (the content view needs a "ui" script in Rhino; ui.R answers integer ids
// there). Node expressions see ui, floaty, web, terminal, console, toast and compose (autojs6:<name>, toast by its bare
// name) and the family's globals; they may return a Promise, which is awaited. Rhino expressions use the Rhino globals.
// A case whose id ends with "~" waits for an event: its Rhino expression is a function taking done(value).
// The host is expected to hold the overlay permission, 3-Block Compose and 3-Shell Terminal to be installed. Only cases
// without lasting effects: windows, sessions and sockets are closed, the content window finishes, the toasts are short.
// Plain ES5 in Rhino expressions.
var UI_FAMILY_CASES = [

    // ui: the thread, bar heights, the emitter; the content view and R references (Node only)
    ["ui.isUiThread", "[ui.isUiThread(), isUiThread()]", "[ui.isUiThread(), isUiThread()]"],
    ["ui.barHeights", "[ui.getStatusBarHeight(), ui.getNavigationBarHeight(), ui.statusBarHeight === ui.getStatusBarHeight()]",
        "(async () => [await ui.getStatusBarHeight(), await ui.getNavigationBarHeight(), (await ui.statusBarHeight) === (await ui.getStatusBarHeight())])()"],
    // Rhino's ui.emitter exists in "ui" scripts only (null here); the runtimes give every thread its emitter.
    ["ui.emitter", null, "[typeof ui.emitter.on, typeof ui.emitter.emit]", ["function", "function"]],
    ["ui.R", null, "[ui.R.drawable.ic_p33, ui.R.string.app_name]", ["@drawable/ic_p33", "@string/app_name"]],
    ["ui.layout", null,
        "(async () => { await ui.layout('<vertical><text id=\"p33t\" text=\"p33\"/></vertical>'); const t = ui.findById('p33t'), root = ui.root;" +
        " await ui.finish(); return [t !== null && typeof t === 'object', root !== null, ui.root]; })()", [true, true, null]],

    // floaty (the host's overlay permission is granted)
    ["floaty.permission", "[floaty.checkPermission(), floaty.hasPermission()]", "Promise.all([floaty.checkPermission(), floaty.hasPermission()])"],
    ["floaty.rawWindow", "(function () { var w = floaty.rawWindow('<frame><text id=\"p33\" text=\"p33\"/></frame>'); w.setPosition(12, 34); sleep(300);" +
        " var r = [w.getX(), w.getY(), w.p33 !== null && w.p33 !== undefined]; w.close(); return r; })()",
        "(async () => { const w = await floaty.rawWindow('<frame><text id=\"p33\" text=\"p33\"/></frame>'); await w.setPosition(12, 34);" +
        " const r = [await w.x, await w.y, w.p33 !== null && w.p33 !== undefined]; await w.close(); return r; })()"],
    ["floaty.window", "(function () { var w = floaty.window('<frame/>'); var r = [w.isAdjustEnabled()]; w.setAdjustEnabled(true); sleep(100);" +
        " r.push(w.isAdjustEnabled()); w.close(); return r; })()",
        "(async () => { const w = await floaty.window('<frame/>'); const r = [await w.isAdjustEnabled()]; await w.setAdjustEnabled(true);" +
        " r.push(await w.isAdjustEnabled()); await w.close(); return r; })()"],

    // web
    ["web.ByteString", "(function (b) { return [b.size(), String(b.hex()), String(b.base64()), String(b.utf8())]; })(web.ByteString.encodeUtf8('h\\u00e9'))",
        "(b => [b.size(), b.hex(), b.base64(), b.utf8()])(web.ByteString.encodeUtf8('h\\u00e9'))"],
    ["web.newInjectableWebClient", "[typeof web.newInjectableWebClient().inject]", "[typeof web.newInjectableWebClient().inject]"],

    // terminal (3-Shell Terminal)
    ["terminal.state", "[terminal.state(), terminal.isAvailable()]", "Promise.all([terminal.state(), terminal.isAvailable()])"],
    // waitFor subscribes from its call, so the command prints a second after the write: a fast echo comes before the
    // subscription in Rhino, and in the runtimes write and waitFor are two host calls that may swap (0 / 8 and 6 / 8 on G8441).
    ["terminal.session", "(function () { var s = terminal.open({cwd: terminal.env().home, show: false, title: 'p33'}); s.write('sleep 1; echo p33_$((6*7))\\n');" +
        " var m = s.waitFor(/p33_42/, 15000); var r = [typeof s.id, m !== null && m !== undefined, s.isAlive()]; s.close(); return r; })()",
        "(async () => { const s = await terminal.open({cwd: (await terminal.env()).home, show: false, title: 'p33'}); const wait = s.waitFor(/p33_42/, 15000);" +
        " await s.write('sleep 1; echo p33_$((6*7))\\n'); const m = await wait; const r = [typeof s.id, m !== null && m !== undefined, await s.isAlive()]; await s.close(); return r; })()"],
    ["terminal.session.missing", "terminal.session('p33-none')", "terminal.session('p33-none')"],

    // console
    ["console.isShowing", "console.isShowing()", "console.isShowing()"],
    ["console.build", "(function (c) { return [String(c.title), Number(c.titleTextSize), c.setTitle('p34') === c, String(c.title)]; })(console.build({title: 'p33', titleTextSize: 15}))",
        "(c => [c.title, c.titleTextSize, c.setTitle('p34') === c, c.title])(console.build({title: 'p33', titleTextSize: 15}))"],

    // toast
    ["toast.dismissAll", "[toast.dismissAll()]", "toast.dismissAll().then(v => [v])"],
    // The runtimes' toasts settle with null, as their toast() (Rhino returns undefined).
    ["toastLog", "[toastLog('p33 ' + 1) === undefined, typeof toastInfo]", "(async () => [(await toastLog('p33 ' + 1)) === null, typeof toastInfo])()"],

    // compose
    ["compose.node", "(function (n) { return [n.type, n.key, n.w, n.parent]; })(compose.Text({text: 'p33', key: 'k', w: 10}))",
        "(n => [n.type, n.key, n.w, n.parent])(compose.Text({text: 'p33', key: 'k', w: 10}))"],
    ["compose.state", "[compose.state(1).value, compose.ref().current]", "[compose.state(1).value, compose.ref().current]"],
    ["ComposeError", "(function (e) { return [e.name, e.code, e.retryable, e instanceof Error, e instanceof ComposeError]; })(new ComposeError('m', 'PLUGIN_DISABLED'))",
        "(e => [e.name, e.code, e.retryable, e instanceof Error, e instanceof ComposeError])(new ComposeError('m', 'PLUGIN_DISABLED'))"],

    // the family's globals
    ["globals", "[typeof toastLog, typeof toastverbose, typeof newWebSocket, typeof newInjectableWebView, typeof newInjectableWebClient, typeof ComposeError, typeof isUiThread]",
        "[typeof toastLog, typeof toastverbose, typeof newWebSocket, typeof newInjectableWebView, typeof newInjectableWebClient, typeof ComposeError, typeof isUiThread]"],

    // events last: a WebSocket to a closed local port fails
    ["web.newWebSocket.failure~", "(function (done) { var ws = web.newWebSocket('ws://127.0.0.1:9'); ws.on('failure', function () { ws.cancel(); done('failure'); }); })",
        "new Promise(resolve => { const ws = web.newWebSocket('ws://127.0.0.1:9'); ws.on('failure', () => { ws.cancel(); resolve('failure'); }); })"]
];

if (typeof module === "object" && module && module.exports) {
    module.exports = {cases: UI_FAMILY_CASES};
}

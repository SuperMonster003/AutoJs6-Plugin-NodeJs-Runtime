#!/usr/bin/env python3
"""Rhino / Node comparison of the automation family on a device (AutoJs6 api-coverage P3.4).

The cases of cases.js run through the host installed on the device (RunIntentActivity), first in Rhino, then in Node in
three forms (the main thread with require, an ES module imported from a data: URL, a nested Worker), in one session. The
host's accessibility service is expected to run (the cases read its state and windows). A case whose id ends with "~"
waits for an event. A last Node run declares accessibility, events and events.key only and checks that the routes that
need more reject (ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with the missing capabilities): accessibility.service,
accessibility.gesture, events.key.intercept and events.touch are never implied by their parents (Q178 (a)), global
actions other than back / home / recents need keys, yolo needs yolo and image. Only counts, failing case ids and short
values are printed. The run, console and package-state helpers are those of ../js-tools/device_js_tools.py.

Phases (all take -s SERIAL):
  state   --state FILE   record the host / Node / Bun package versions and APK md5 (JSON)
  verify  --state FILE   compare the device with a recorded state
  compare [--forms main,esm,worker,undeclared] [--report FILE]
                         run the cases in Rhino and Node and compare them
  timeout [--forms main,worker] [--report FILE]
                         with the host's accessibility service off: auto.waitFor and automator.waitForService end the
                         whole execution (Q179 (b)): nothing after the wait runs, not even a timer armed before it
The pushed scripts are removed afterwards (/sdcard/Download/aj6-p34).
"""
import argparse
import json
import pathlib
import subprocess
import sys
import uuid

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "js-tools"))
import device_js_tools as base  # noqa: E402

CASES = HERE / "cases.js"
base.DEVICE_DIR = "/sdcard/Download/aj6-p34"
base.MARK = "P34"
EMIT_JS = base.EMIT_JS.replace("P32A", "P34").replace("p32aEmit", "p34Emit")
# The encoder of the JS tools cases (Rhino and Node).
JS_TOOLS = (HERE.parent / "js-tools" / "cases.js").read_text(encoding="utf-8")
ENCODER_JS = JS_TOOLS[JS_TOOLS.index("function jsToolsEncode("):JS_TOOLS.index("if (typeof module ===")]

RHINO_JS = """
function p34JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
var p34Pending = 1;
function p34Finish() {
    if (--p34Pending === 0) p34Emit(P34_RUN, -1, {done: AUTOMATION_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
}
for (var i = 0; i < AUTOMATION_CASES.length; i++) {
    var id = AUTOMATION_CASES[i][0], source = AUTOMATION_CASES[i][1], result;
    if (source === null) {
        p34Emit(P34_RUN, i, {skip: 1});
    } else if (id.charAt(id.length - 1) === '~') {
        // Waits for an event: done(value) once, or a timeout after 10 s.
        p34Pending++;
        (function (index, source) {
            var settled = false, timer;
            var settle = function (result) {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                p34Emit(P34_RUN, index, result);
                p34Finish();
            };
            timer = setTimeout(function () { settle({th: {name: 'Error', message: 'no event in 10 s'}}); }, 10000);
            try { eval('(' + source + ')')(function (value) { settle({ok: jsToolsEncode(value, p34JavaInfo)}); }); } catch (e) { settle({th: jsToolsThrown(e)}); }
        })(i, source);
    } else {
        try { result = {ok: jsToolsEncode(eval('(' + source + ')'), p34JavaInfo)}; } catch (e) { result = {th: jsToolsThrown(e)}; }
        p34Emit(P34_RUN, i, result);
    }
}
p34Finish();
"""
NODE_RUNNER_JS = """
async function p34Run(env, emit) {
    const names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < AUTOMATION_CASES.length; i++) {
        let result;
        try {
            let value = new Function(...names, 'return (' + AUTOMATION_CASES[i][2] + ');')(...values);
            if (value && typeof value.then === 'function') value = await value;
            result = {ok: jsToolsEncode(value, null)};
        } catch (e) {
            result = {th: jsToolsThrown(e), code: e && e.code ? String(e.code) : null};
        }
        emit(i, result);
    }
}
"""
CAPABILITIES = ["accessibility", "accessibility.gesture", "accessibility.service", "keys", "events", "events.key", "events.key.intercept",
                "events.touch", "yolo", "image", "ocr", "barcode"]
MODULES = [("automator", "autojs6:automator"), ("auto", "autojs6:auto"), ("threads", "autojs6:threads"), ("continuation", "autojs6:continuation"),
           ("timers", "autojs6:timers"), ("yolo", "autojs6:yolo"), ("selector", "autojs6:selector"), ("qrcode", "autojs6:qrcode"),
           ("keys", "keys"), ("ocr", "ocr"), ("events", "autojs6:events"), ("system", "autojs6:system")]
NODE_ENV_CJS = "{" + ", ".join(f"{name}: require('{module}')" for name, module in MODULES) + "}"
NODE_ENV_ESM = "".join(f"import {name} from '{module}';\n" for name, module in MODULES) + \
    "export const env = {" + ", ".join(name for name, _ in MODULES) + "};\n"
# Parent capabilities only: the dotted ones are not implied.
UNDECLARED_CAPABILITIES = ["accessibility", "events", "events.key"]
UNDECLARED_JS = """
(async () => {
    const automator = require('autojs6:automator'), auto = require('autojs6:auto'), events = require('autojs6:events'), yolo = require('autojs6:yolo');
    const out = {};
    for (const [name, run] of [['auto.clearCache', () => auto.clearCache()], ['auto.launchSettings', () => auto.launchSettings()],
            ['automator.press', () => automator.press(-10, -10, 1)], ['automator.dismissNotificationShade', () => automator.dismissNotificationShade()],
            ['events.setKeyInterceptionEnabled', () => events.setKeyInterceptionEnabled(true)], ['events.observeTouch', () => events.observeTouch()],
            ['yolo.load', () => yolo.load('/sdcard/Download/p34-none', {component: 'p34.none/p34.None'})], ['auto.isRunning', () => auto.isRunning()]]) {
        try { out[name] = {ok: await run()}; } catch (e) { out[name] = {code: e.code, missing: e.missingCapabilities ? [...e.missingCapabilities].sort() : null}; }
    }
    p34Emit(P34_RUN, 0, out);
    p34Emit(P34_RUN, -1, {done: 1});
})();
"""
UNDECLARED_EXPECTED = {
    "auto.clearCache": ["accessibility.service"],
    "auto.launchSettings": ["accessibility.service"],
    "automator.press": ["accessibility.gesture"],
    "automator.dismissNotificationShade": ["keys"],
    "events.setKeyInterceptionEnabled": ["events.key.intercept"],
    "events.observeTouch": ["events.touch"],
    "yolo.load": ["image", "yolo"],
}


def project(capabilities):
    return {"name": "p34-automation", "type": "node", "main": "main.js", "node": {"permissions": capabilities, "timeoutMs": 300000}}


def cases():
    out = subprocess.run(["node", "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).cases))", str(CASES)],
                         capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def node_script(form, run):
    common = f"const P34_RUN = '{run}';\n" + CASES.read_text(encoding="utf-8") + ENCODER_JS + EMIT_JS + NODE_RUNNER_JS
    emit_main = "(i, r) => p34Emit(P34_RUN, i, r)"
    finish = "p34Emit(P34_RUN, -1, {done: AUTOMATION_CASES.length, form: '%s'});" % form
    if form == "main":
        body = f"(async () => {{ try {{ await p34Run({NODE_ENV_CJS}, {emit_main}); }} finally {{ {finish} }} }})();"
    elif form == "esm":
        body = ("(async () => { try { const {env} = await import('data:text/javascript,' + encodeURIComponent(%s));"
                " await p34Run(env, %s); } finally { %s } })();") % (json.dumps(NODE_ENV_ESM), emit_main, finish)
    else:
        inner = (CASES.read_text(encoding="utf-8") + ENCODER_JS + NODE_RUNNER_JS + "\nconst {parentPort} = require('node:worker_threads');\n"
                 f"p34Run({NODE_ENV_CJS}, (i, r) => parentPort.postMessage([i, r])).catch(e => parentPort.postMessage([-2, String(e && e.stack || e)]))"
                 ".then(() => parentPort.postMessage([-1, null]));\n")
        outer = ("const {Worker, parentPort} = require('node:worker_threads');\n"
                 f"const w = new Worker({json.dumps(inner)}, {{eval: true}});\n"
                 "w.on('message', m => parentPort.postMessage(m)); w.on('error', e => parentPort.postMessage([-2, String(e && e.stack || e)]));\n")
        body = ("(async () => { const {Worker} = require('node:worker_threads'); try { await new Promise((resolve) => {"
                " const w = new Worker(" + json.dumps(outer) + ", {eval: true});"
                " w.on('message', ([i, r]) => { if (i === -1) { w.terminate(); resolve(); } else if (i === -2) { console.log('P34-worker-error ' + r); }"
                " else p34Emit(P34_RUN, i, r); }); w.on('error', e => { console.log('P34-worker-error ' + (e && e.stack || e)); resolve(); });"
                " w.on('exit', resolve); }); } finally { " + finish + " } })();")
    return '"nodejs";\n' + common + body + "\n"


def compare(expected, actual):
    """'pass' or the reason of a failure; thrown errors match by name, a Rhino Java exception any Node rejection."""
    if "th" in expected or "th" in actual:
        if "th" not in expected:
            return f"expected {json.dumps(expected.get('ok'))[:100]}, threw {actual['th']['name']}: {actual['th']['message'][:100]}"
        name = base.error_name(expected["th"]["name"])
        if "th" not in actual:
            return f"expected {name} ({expected['th'].get('message', '')[:80]}), got {json.dumps(actual.get('ok'))[:100]}"
        if expected["th"]["name"].startswith("JavaException "):
            return "pass"
        return "pass" if name == actual["th"]["name"] else f"expected {name}, got {actual['th']['name']}: {actual['th']['message'][:100]}"
    return "pass" if base.same(expected["ok"], actual["ok"]) else \
        f"expected {json.dumps(expected['ok'])[:120]}, got {json.dumps(actual['ok'])[:120]}"


def phase_compare(args):
    serial, table = args.serial, cases()
    report, failed = {"device": {}, "forms": {}}, 0
    try:
        run = uuid.uuid4().hex[:8]
        rhino_source = f"var P34_RUN = '{run}';\n" + CASES.read_text(encoding="utf-8") + ENCODER_JS + EMIT_JS + RHINO_JS
        rhino = base.run_script(serial, f"p34_rhino_{run}.js", rhino_source, run, args.wait)
        state = base.package_state(serial).get(base.HOST) or {}
        model = base.adb(serial, "shell", "getprop", "ro.product.model").stdout.strip()
        rhino_cases = [c for c in table if c[1] is not None]
        rhino_thrown = sorted(c[0] for i, c in enumerate(table) if c[1] is not None and "th" in (rhino.get(i) or {}))
        report["device"] = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": state.get("versionCode"), "rhinoThrown": rhino_thrown}
        print(f"rhino: {len([i for i in rhino if i >= 0 and 'skip' not in rhino[i]])} / {len(rhino_cases)} cases ({len(rhino_thrown)} throw),"
              f" host {state.get('versionCode')}, {model} API {rhino.get(-1, {}).get('sdk')}")
        for form in args.forms.split(","):
            run = uuid.uuid4().hex[:8]
            if form == "undeclared":
                text = f'"nodejs";\nconst P34_RUN = \'{run}\';\n' + EMIT_JS + UNDECLARED_JS
                results = base.run_script(serial, f"p34_node_{form}_{run}.js", text, run, args.wait, project(UNDECLARED_CAPABILITIES))
                out = results.get(0, {})
                failures = {k: f"expected {v}, got {out.get(k)}" for k, v in UNDECLARED_EXPECTED.items()
                            if out.get(k) != {"code": "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED", "missing": v}}
                if (out.get("auto.isRunning") or {}).get("ok") is not True:
                    failures["auto.isRunning"] = f"expected true, got {out.get('auto.isRunning')}"
                counts = {"pass": len(UNDECLARED_EXPECTED) + 1 - len(failures), "fail": len(failures)}
            else:
                results = base.run_script(serial, f"p34_node_{form}_{run}.js", node_script(form, run), run, args.wait, project(CAPABILITIES))
                counts, failures = {"pass": 0, "fail": 0, "missing": 0}, {}
                for i, case in enumerate(table):
                    actual = results.get(i)
                    if actual is None:
                        counts["missing"] += 1
                        failures[case[0]] = "missing"
                        continue
                    expected = {"ok": case[3]} if case[1] is None else rhino.get(i)
                    verdict = "missing Rhino value" if expected is None else compare(expected, actual)
                    if verdict == "pass":
                        counts["pass"] += 1
                    else:
                        counts["fail"] += 1
                        failures[case[0]] = verdict
            failed += counts["fail"] + counts.get("missing", 0)
            report["forms"][form] = {"counts": counts, "failures": failures}
            print(f"node {form}: " + " / ".join(f"{k} {v}" for k, v in counts.items()))
            for case_id, verdict in list(failures.items())[:args.show]:
                print(f"  {case_id}: {verdict}")
    finally:
        base.adb(serial, "shell", "rm", "-rf", base.DEVICE_DIR)
    if args.report:
        pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return 0 if failed == 0 else 1


# The timeout run: index 0 before the wait, 1 after it, 2 from a timer armed before it; only 0 may arrive (Q179 (b)).
TIMEOUT_SECONDS = 12
TIMEOUT_MAIN_JS = """
const auto = require('autojs6:auto'), automator = require('autojs6:automator');
p34Emit(P34_RUN, 0, {ok: 'before'});
setTimeout(() => { p34Emit(P34_RUN, 2, {ok: 'timer'}); p34Emit(P34_RUN, -1, {done: 1}); }, 6000);
(async () => {
    await P34_WAIT;
    p34Emit(P34_RUN, 1, {ok: 'after'});
    p34Emit(P34_RUN, -1, {done: 1});
})().catch(e => { p34Emit(P34_RUN, 3, {error: String(e && e.stack || e)}); p34Emit(P34_RUN, -1, {done: 1}); });
"""
TIMEOUT_WORKER_JS = """
const {Worker} = require('node:worker_threads');
p34Emit(P34_RUN, 0, {ok: 'before'});
setTimeout(() => { p34Emit(P34_RUN, 2, {ok: 'timer'}); p34Emit(P34_RUN, -1, {done: 1}); }, 6000);
const worker = new Worker(`
const auto = require('autojs6:auto'), automator = require('autojs6:automator');
const {parentPort} = require('node:worker_threads');
(async () => { await P34_WAIT; parentPort.postMessage({after: true}); })().catch(e => parentPort.postMessage({error: String(e && e.stack || e)}));
`, {eval: true});
worker.on('message', value => {
    p34Emit(P34_RUN, value.after ? 1 : 3, value.after ? {ok: 'after'} : value);
    p34Emit(P34_RUN, -1, {done: 1});
});
worker.on('error', e => { p34Emit(P34_RUN, 3, {error: String(e && e.stack || e)}); p34Emit(P34_RUN, -1, {done: 1}); });
"""


def phase_timeout(args):
    serial = args.serial
    enabled = base.adb(serial, "shell", "settings", "get", "secure", "enabled_accessibility_services").stdout
    if base.HOST + "/" in enabled:
        print(f"the accessibility service of {base.HOST} is enabled: the timeout run needs it off")
        return 2
    report, failed = {}, 0
    forms = [form for form in args.forms.split(",") if form in ("main", "worker")]
    for wait in ("auto.waitFor(1000)", "automator.waitForService(1000)"):
        for form in forms:
            run = uuid.uuid4().hex[:8]
            body = (TIMEOUT_MAIN_JS if form == "main" else TIMEOUT_WORKER_JS).replace("P34_WAIT", wait)
            text = f'"nodejs";\nconst P34_RUN = \'{run}\';\n' + EMIT_JS + body
            results = base.run_script(serial, f"p34_timeout_{run}.js", text, run, TIMEOUT_SECONDS, project(CAPABILITIES))
            verdict = "pass" if sorted(results) == [0] else f"got {sorted(results)} {json.dumps(results.get(3, ''))[:300]}"
            report[f"{form} {wait}"] = verdict
            failed += verdict != "pass"
            print(f"{form} {wait}: {verdict}")
    if args.report:
        pathlib.Path(args.report).write_text(json.dumps(report, indent=1) + "\n", encoding="utf-8")
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["state", "verify", "compare", "timeout"])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--state")
    parser.add_argument("--forms", default="main,esm,worker,undeclared")
    parser.add_argument("--report")
    parser.add_argument("--wait", type=float, default=180)
    parser.add_argument("--show", type=int, default=20)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase == "compare":
        return phase_compare(args)
    if args.phase == "timeout":
        return phase_timeout(args)
    state = base.package_state(args.serial)
    if args.phase == "state":
        pathlib.Path(args.state).write_text(json.dumps(state, indent=1) + "\n", encoding="utf-8")
        print("state: " + ", ".join(f"{k} {v and v['versionCode']}" for k, v in state.items()))
        return 0
    recorded = json.loads(pathlib.Path(args.state).read_text(encoding="utf-8"))
    differences = [k for k in recorded if recorded[k] != state.get(k)]
    print("verify: " + ("same packages and APK md5" if not differences else "different: " + ", ".join(differences)))
    return 0 if not differences else 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Rhino / Node comparison of the system information & engines family on a device (AutoJs6 api-coverage P3.1b).

The cases of cases.js run through the host installed on the device (RunIntentActivity), first in Rhino, then in Node in
three forms (the main thread with require, an ES module imported from a data: URL, a nested Worker), in one session:
the values depend on the device (Root, settings, notification channels, plugins). A last Node run declares the parent
capabilities without the dotted ones and checks that the routes that need them reject
(ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with the missing capabilities): autojs.control, settings.write, memory.dump
and installer.mutate are never implied. Only counts, failing case ids and short values are printed. The run, console and
package-state helpers are those of ../js-tools/device_js_tools.py.

Phases (all take -s SERIAL):
  state   --state FILE   record the host / Node / Bun package versions and APK md5 (JSON)
  verify  --state FILE   compare the device with a recorded state
  compare [--forms main,esm,worker,undeclared] [--report FILE]
                         run the cases in Rhino and Node and compare them
The pushed scripts, the APK copy and the exported settings are removed afterwards (/sdcard/Download/aj6-p31b).
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
base.DEVICE_DIR = "/sdcard/Download/aj6-p31b"
base.MARK = "P31B"
# A copy of an installed APK next to each script, for installer.inspect.
APK = "sample.apk"
APK_PACKAGE = "com.android.settings"
EMIT_JS = base.EMIT_JS.replace("P32A", "P31B").replace("p32aEmit", "p31bEmit")
# The encoder of the JS tools cases (Rhino and Node).
JS_TOOLS = (HERE.parent / "js-tools" / "cases.js").read_text(encoding="utf-8")
ENCODER_JS = JS_TOOLS[JS_TOOLS.index("function jsToolsEncode("):JS_TOOLS.index("if (typeof module ===")]

RHINO_JS = """
function p31bJavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
for (var i = 0; i < SYSTEM_CASES.length; i++) {
    var source = SYSTEM_CASES[i][1], result;
    if (source === null) {
        result = {skip: 1};
    } else {
        try { result = {ok: jsToolsEncode(eval('(' + source + ')'), p31bJavaInfo)}; } catch (e) { result = {th: jsToolsThrown(e)}; }
    }
    p31bEmit(P31B_RUN, i, result);
}
p31bEmit(P31B_RUN, -1, {done: SYSTEM_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
"""
NODE_RUNNER_JS = """
async function p31bRun(env, emit) {
    env = Object.assign({systemCount, systemKeys, systemIds}, env);
    const names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < SYSTEM_CASES.length; i++) {
        let result;
        try {
            let value = new Function(...names, 'return (' + SYSTEM_CASES[i][2] + ');')(...values);
            if (value && typeof value.then === 'function') value = await value;
            result = {ok: jsToolsEncode(value, null)};
        } catch (e) {
            result = {th: jsToolsThrown(e), code: e && e.code ? String(e.code) : null};
        }
        emit(i, result);
    }
}
"""
CAPABILITIES = ["autojs", "autojs.control", "settings", "files.write", "sysprops", "memory", "installer", "device", "device.power", "notifications",
                "engines", "plugin_tools", "clipboard", "accessibility", "app.query", "shell", "shell.root", "shell.shizuku"]
MODULES = [("autojs", "autojs"), ("installer", "installer"), ("memory", "memory"), ("settings", "settings"), ("sysprops", "sysprops"),
           ("powerManager", "power_manager"), ("system", "system"), ("notice", "notifications"), ("engines", "engines"),
           ("plugins", "plugins"), ("app", "app"), ("dialogs", "dialogs")]
NODE_ENV_CJS = "{" + ", ".join(f"{name}: require('autojs6:{module}')" for name, module in MODULES) + ", APK: %s}" % json.dumps(APK)
NODE_ENV_ESM = "".join(f"import {name} from 'autojs6:{module}';\n" for name, module in MODULES) + \
    "export const env = {" + ", ".join(name for name, _ in MODULES) + ", APK: %s};\n" % json.dumps(APK)
# Parent capabilities only: the dotted ones are not implied, the others are missing.
UNDECLARED_CAPABILITIES = ["autojs", "settings", "memory", "installer"]
UNDECLARED_JS = """
(async () => {
    const autojs = require('autojs6:autojs'), settings = require('autojs6:settings'), memory = require('autojs6:memory');
    const installer = require('autojs6:installer'), sysprops = require('autojs6:sysprops'), powerManager = require('autojs6:power_manager');
    const out = {};
    for (const [name, run] of [['autojs.setRootMode', () => autojs.setRootMode('auto')], ['settings.setEnabled', () => settings.setEnabled('stable_mode', false)],
            ['memory.dumpHeap', () => memory.dumpHeap()], ['installer.install', () => installer.install('p31b.apk')],
            ['sysprops.get', () => sysprops.get('ro.build.version.sdk')], ['powerManager', () => powerManager.isIgnoringBatteryOptimizations()],
            ['autojs.getRootMode', () => autojs.getRootMode()]]) {
        try { out[name] = {ok: await run()}; } catch (e) { out[name] = {code: e.code, missing: e.missingCapabilities || null}; }
    }
    p31bEmit(P31B_RUN, 0, out);
    p31bEmit(P31B_RUN, -1, {done: 1});
})();
"""
UNDECLARED_EXPECTED = {
    "autojs.setRootMode": ["autojs.control"],
    "settings.setEnabled": ["settings.write"],
    "memory.dumpHeap": ["memory.dump"],
    "installer.install": ["installer.mutate"],
    "sysprops.get": ["sysprops"],
    "powerManager": ["device", "device.power"],
}


def project(capabilities):
    # Projects get a 5 s budget by default; memory.collect and the installer queries take longer.
    return {"name": "p31b-system", "type": "node", "main": "main.js", "node": {"permissions": capabilities, "timeoutMs": 300000}}


def cases():
    out = subprocess.run(["node", "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).cases))", str(CASES)],
                         capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def node_script(form, run):
    common = f"const P31B_RUN = '{run}';\n" + CASES.read_text(encoding="utf-8") + ENCODER_JS + EMIT_JS + NODE_RUNNER_JS
    emit_main = "(i, r) => p31bEmit(P31B_RUN, i, r)"
    finish = "p31bEmit(P31B_RUN, -1, {done: SYSTEM_CASES.length, form: '%s'});" % form
    if form == "main":
        body = f"(async () => {{ try {{ await p31bRun({NODE_ENV_CJS}, {emit_main}); }} finally {{ {finish} }} }})();"
    elif form == "esm":
        body = ("(async () => { try { const {env} = await import('data:text/javascript,' + encodeURIComponent(%s));"
                " await p31bRun(env, %s); } finally { %s } })();") % (json.dumps(NODE_ENV_ESM), emit_main, finish)
    else:
        inner = (CASES.read_text(encoding="utf-8") + ENCODER_JS + NODE_RUNNER_JS + "\nconst {parentPort} = require('node:worker_threads');\n"
                 f"p31bRun({NODE_ENV_CJS}, (i, r) => parentPort.postMessage([i, r])).catch(e => parentPort.postMessage([-2, String(e && e.stack || e)]))"
                 ".then(() => parentPort.postMessage([-1, null]));\n")
        outer = ("const {Worker, parentPort} = require('node:worker_threads');\n"
                 f"const w = new Worker({json.dumps(inner)}, {{eval: true}});\n"
                 "w.on('message', m => parentPort.postMessage(m)); w.on('error', e => parentPort.postMessage([-2, String(e && e.stack || e)]));\n")
        body = ("(async () => { const {Worker} = require('node:worker_threads'); try { await new Promise((resolve) => {"
                " const w = new Worker(" + json.dumps(outer) + ", {eval: true});"
                " w.on('message', ([i, r]) => { if (i === -1) { w.terminate(); resolve(); } else if (i === -2) { console.log('P31B-worker-error ' + r); }"
                " else p31bEmit(P31B_RUN, i, r); }); w.on('error', e => { console.log('P31B-worker-error ' + (e && e.stack || e)); resolve(); });"
                " w.on('exit', resolve); }); } finally { " + finish + " } })();")
    return '"nodejs";\n' + common + body + "\n"


def compare(expected, actual):
    """'pass' or the reason of a failure; thrown errors match by name, a Rhino Java exception any Node rejection."""
    if "th" in expected or "th" in actual:
        if "th" not in expected:
            return f"expected {json.dumps(expected.get('ok'))[:100]}, threw {actual['th']['name']}: {actual['th']['message'][:100]}"
        name = base.error_name(expected["th"]["name"])
        if "th" not in actual:
            return f"expected {name}, got {json.dumps(actual.get('ok'))[:100]}"
        if expected["th"]["name"].startswith("JavaException "):
            return "pass"
        return "pass" if name == actual["th"]["name"] else f"expected {name}, got {actual['th']['name']}: {actual['th']['message'][:100]}"
    return "pass" if base.same(expected["ok"], actual["ok"]) else \
        f"expected {json.dumps(expected['ok'])[:120]}, got {json.dumps(actual['ok'])[:120]}"


def copy_apk(serial, source, directory):
    base.adb(serial, "shell", "mkdir", "-p", directory, check=True)
    base.adb(serial, "shell", "cp", source, f"{directory}/{APK}", check=True)


def phase_compare(args):
    serial, table = args.serial, cases()
    source = base.adb(serial, "shell", "pm", "path", APK_PACKAGE).stdout.split("package:")[-1].split()[0]
    copy_apk(serial, source, base.DEVICE_DIR)
    report, failed = {"device": {}, "forms": {}}, 0
    try:
        run = uuid.uuid4().hex[:8]
        rhino_source = (f"var P31B_RUN = '{run}';\nvar APK = {json.dumps(APK)};\n" + CASES.read_text(encoding="utf-8")
                        + ENCODER_JS + EMIT_JS + RHINO_JS)
        rhino = base.run_script(serial, f"p31b_rhino_{run}.js", rhino_source, run, args.wait)
        state = base.package_state(serial).get(base.HOST) or {}
        model = base.adb(serial, "shell", "getprop", "ro.product.model").stdout.strip()
        report["device"] = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": state.get("versionCode")}
        print(f"rhino: {len([i for i in rhino if i >= 0])} / {len(table)} cases, host {state.get('versionCode')}, {model} API {rhino.get(-1, {}).get('sdk')}")
        # Values that describe the device, for the report.
        for case_id in ("autojs.root", "autojs.permissions", "installer.queries", "notice.isEnabled", "powerManager", "plugins.tools.list"):
            index = [c[0] for c in table].index(case_id)
            report["device"][case_id] = rhino.get(index)
        if args.rhino:
            pathlib.Path(args.rhino).write_text(json.dumps({table[i][0]: v for i, v in rhino.items() if i >= 0}, ensure_ascii=False, indent=1) + "\n",
                                                encoding="utf-8")
        for form in args.forms.split(","):
            run = uuid.uuid4().hex[:8]
            copy_apk(serial, source, f"{base.DEVICE_DIR}/{run}")
            if form == "undeclared":
                text = f'"nodejs";\nconst P31B_RUN = \'{run}\';\n' + EMIT_JS + UNDECLARED_JS
                results = base.run_script(serial, f"p31b_node_{form}_{run}.js", text, run, args.wait, project(UNDECLARED_CAPABILITIES))
                out = results.get(0, {})
                failures = {k: f"expected {v}, got {out.get(k)}" for k, v in UNDECLARED_EXPECTED.items()
                            if out.get(k) != {"code": "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED", "missing": v}}
                if "ok" not in (out.get("autojs.getRootMode") or {}):
                    failures["autojs.getRootMode"] = f"expected a value, got {out.get('autojs.getRootMode')}"
                counts = {"pass": len(UNDECLARED_EXPECTED) + 1 - len(failures), "fail": len(failures)}
            else:
                results = base.run_script(serial, f"p31b_node_{form}_{run}.js", node_script(form, run), run, args.wait, project(CAPABILITIES))
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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["state", "verify", "compare"])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--state")
    parser.add_argument("--forms", default="main,esm,worker,undeclared")
    parser.add_argument("--report")
    parser.add_argument("--rhino", help="also write the Rhino values by case id (JSON)")
    parser.add_argument("--wait", type=float, default=180)
    parser.add_argument("--show", type=int, default=20)
    args = parser.parse_args()
    if args.phase == "compare":
        return phase_compare(args)
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

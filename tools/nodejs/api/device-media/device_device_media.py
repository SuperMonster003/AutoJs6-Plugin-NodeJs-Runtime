#!/usr/bin/env python3
"""Rhino / Node comparison of the device, sensors and media family on a device (AutoJs6 api-coverage P3.1c).

The cases of cases.js run through the host installed on the device (RunIntentActivity), first in Rhino, then in Node in
three forms (the main thread with require, an ES module imported from a data: URL, a nested Worker), in one session:
the values depend on the device (Build fields, sensors, network, permissions). A case whose id ends with "~" waits for a
sensor event (the accelerometer reports without moving the device). A last Node run declares device, sensors and media
only and checks that the routes that need more reject (ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with the missing
capabilities): device.identifiers and device.power are never implied by device, media.playback / media.library never by
media. Only counts, failing case ids and short values are printed; identifiers and addresses are compared as hashes. The
run, console and package-state helpers are those of ../js-tools/device_js_tools.py.

Phases (all take -s SERIAL):
  state   --state FILE   record the host / Node / Bun package versions and APK md5 (JSON)
  verify  --state FILE   compare the device with a recorded state
  compare [--forms main,esm,worker,undeclared] [--report FILE]
                         run the cases in Rhino and Node and compare them
The pushed scripts and the WAV file are removed afterwards (/sdcard/Download/aj6-p31c).
"""
import argparse
import io
import json
import pathlib
import subprocess
import sys
import uuid
import wave

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "js-tools"))
import device_js_tools as base  # noqa: E402

CASES = HERE / "cases.js"
base.DEVICE_DIR = "/sdcard/Download/aj6-p31c"
base.MARK = "P31C"
WAV = base.DEVICE_DIR + "/p31c.wav"
EMIT_JS = base.EMIT_JS.replace("P32A", "P31C").replace("p32aEmit", "p31cEmit")
# The encoder of the JS tools cases (Rhino and Node).
JS_TOOLS = (HERE.parent / "js-tools" / "cases.js").read_text(encoding="utf-8")
ENCODER_JS = JS_TOOLS[JS_TOOLS.index("function jsToolsEncode("):JS_TOOLS.index("if (typeof module ===")]

RHINO_JS = """
function p31cJavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
var p31cPending = 1;
function p31cFinish() {
    if (--p31cPending === 0) p31cEmit(P31C_RUN, -1, {done: DEVICE_MEDIA_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
}
for (var i = 0; i < DEVICE_MEDIA_CASES.length; i++) {
    var id = DEVICE_MEDIA_CASES[i][0], source = DEVICE_MEDIA_CASES[i][1], result;
    if (source === null) {
        p31cEmit(P31C_RUN, i, {skip: 1});
    } else if (id.charAt(id.length - 1) === '~') {
        // Waits for an event: done(value) once, or a timeout after 10 s.
        p31cPending++;
        (function (index, source) {
            var settled = false, timer;
            var settle = function (result) {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                p31cEmit(P31C_RUN, index, result);
                p31cFinish();
            };
            timer = setTimeout(function () { settle({th: {name: 'Error', message: 'no event in 10 s'}}); }, 10000);
            try { eval('(' + source + ')')(function (value) { settle({ok: jsToolsEncode(value, p31cJavaInfo)}); }); } catch (e) { settle({th: jsToolsThrown(e)}); }
        })(i, source);
    } else {
        try { result = {ok: jsToolsEncode(eval('(' + source + ')'), p31cJavaInfo)}; } catch (e) { result = {th: jsToolsThrown(e)}; }
        p31cEmit(P31C_RUN, i, result);
    }
}
p31cFinish();
"""
NODE_RUNNER_JS = """
async function p31cRun(env, emit) {
    env = Object.assign({dmHash, dmCalls, dmContains, DM_MANUFACTURERS, DM_BRANDS, DM_ROMS, DM_SENSORS}, env);
    const names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < DEVICE_MEDIA_CASES.length; i++) {
        let result;
        try {
            let value = new Function(...names, 'return (' + DEVICE_MEDIA_CASES[i][2] + ');')(...values);
            if (value && typeof value.then === 'function') value = await value;
            result = {ok: jsToolsEncode(value, null)};
        } catch (e) {
            result = {th: jsToolsThrown(e), code: e && e.code ? String(e.code) : null};
        }
        emit(i, result);
    }
}
"""
CAPABILITIES = ["device", "device.power", "device.identifiers", "sensors", "media", "media.playback", "media.library"]
MODULES = [("device", "device"), ("sensors", "sensors"), ("media", "media")]
NODE_ENV_CJS = "{" + ", ".join(f"{name}: require('autojs6:{module}')" for name, module in MODULES) + ", WAV: %s}" % json.dumps(WAV)
NODE_ENV_ESM = "".join(f"import {name} from 'autojs6:{module}';\n" for name, module in MODULES) + \
    "export const env = {" + ", ".join(name for name, _ in MODULES) + ", WAV: %s};\n" % json.dumps(WAV)
# Parent capabilities only: the dotted ones are not implied.
UNDECLARED_CAPABILITIES = ["device", "media"]
UNDECLARED_JS = """
(async () => {
    const device = require('autojs6:device'), sensors = require('autojs6:sensors'), media = require('autojs6:media');
    const out = {};
    for (const [name, run] of [['device.getIMEI', () => device.getIMEI()], ['device.getAndroidId', () => device.getAndroidId()],
            ['device.imei', () => device.imei], ['device.setPointerLocation', () => device.setPointerLocation(false)],
            ['device.keepScreenDim', () => device.keepScreenDim(500)], ['sensors.register', () => sensors.register('accelerometer')],
            ['media.playMusic', () => media.playMusic(%s, 0)], ['media.scanFile', () => media.scanFile(%s)],
            ['device.isScreenOff', () => device.isScreenOff()]]) {
        try { out[name] = {ok: await run()}; } catch (e) { out[name] = {code: e.code, missing: e.missingCapabilities ? [...e.missingCapabilities] : null}; }
    }
    p31cEmit(P31C_RUN, 0, out);
    p31cEmit(P31C_RUN, -1, {done: 1});
})();
""" % (json.dumps(WAV), json.dumps(WAV))
UNDECLARED_EXPECTED = {
    "device.getIMEI": ["device.identifiers"],
    "device.getAndroidId": ["device.identifiers"],
    "device.imei": ["device.identifiers"],
    "device.setPointerLocation": ["device.power"],
    "device.keepScreenDim": ["device.power"],
    "sensors.register": ["sensors"],
    "media.playMusic": ["media.playback"],
    "media.scanFile": ["media.library"],
}


def project(capabilities):
    return {"name": "p31c-device-media", "type": "node", "main": "main.js", "node": {"permissions": capabilities, "timeoutMs": 300000}}


def cases():
    out = subprocess.run(["node", "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).cases))", str(CASES)],
                         capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def node_script(form, run):
    common = f"const P31C_RUN = '{run}';\n" + CASES.read_text(encoding="utf-8") + ENCODER_JS + EMIT_JS + NODE_RUNNER_JS
    emit_main = "(i, r) => p31cEmit(P31C_RUN, i, r)"
    finish = "p31cEmit(P31C_RUN, -1, {done: DEVICE_MEDIA_CASES.length, form: '%s'});" % form
    if form == "main":
        body = f"(async () => {{ try {{ await p31cRun({NODE_ENV_CJS}, {emit_main}); }} finally {{ {finish} }} }})();"
    elif form == "esm":
        body = ("(async () => { try { const {env} = await import('data:text/javascript,' + encodeURIComponent(%s));"
                " await p31cRun(env, %s); } finally { %s } })();") % (json.dumps(NODE_ENV_ESM), emit_main, finish)
    else:
        inner = (CASES.read_text(encoding="utf-8") + ENCODER_JS + NODE_RUNNER_JS + "\nconst {parentPort} = require('node:worker_threads');\n"
                 f"p31cRun({NODE_ENV_CJS}, (i, r) => parentPort.postMessage([i, r])).catch(e => parentPort.postMessage([-2, String(e && e.stack || e)]))"
                 ".then(() => parentPort.postMessage([-1, null]));\n")
        outer = ("const {Worker, parentPort} = require('node:worker_threads');\n"
                 f"const w = new Worker({json.dumps(inner)}, {{eval: true}});\n"
                 "w.on('message', m => parentPort.postMessage(m)); w.on('error', e => parentPort.postMessage([-2, String(e && e.stack || e)]));\n")
        body = ("(async () => { const {Worker} = require('node:worker_threads'); try { await new Promise((resolve) => {"
                " const w = new Worker(" + json.dumps(outer) + ", {eval: true});"
                " w.on('message', ([i, r]) => { if (i === -1) { w.terminate(); resolve(); } else if (i === -2) { console.log('P31C-worker-error ' + r); }"
                " else p31cEmit(P31C_RUN, i, r); }); w.on('error', e => { console.log('P31C-worker-error ' + (e && e.stack || e)); resolve(); });"
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


def silent_wav(seconds=3, rate=8000):
    data = io.BytesIO()
    with wave.open(data, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(bytes(2 * rate * seconds))
    return data.getvalue()


def push_wav(serial):
    local = HERE / "__pycache__" / "p31c.wav"
    local.parent.mkdir(exist_ok=True)
    local.write_bytes(silent_wav())
    base.adb(serial, "shell", "mkdir", "-p", base.DEVICE_DIR, check=True)
    base.adb(serial, "push", str(local), WAV, check=True)
    local.unlink()


def phase_compare(args):
    serial, table = args.serial, cases()
    push_wav(serial)
    report, failed = {"device": {}, "forms": {}}, 0
    try:
        run = uuid.uuid4().hex[:8]
        rhino_source = (f"var P31C_RUN = '{run}';\nvar WAV = {json.dumps(WAV)};\n" + CASES.read_text(encoding="utf-8")
                        + ENCODER_JS + EMIT_JS + RHINO_JS)
        rhino = base.run_script(serial, f"p31c_rhino_{run}.js", rhino_source, run, args.wait)
        state = base.package_state(serial).get(base.HOST) or {}
        model = base.adb(serial, "shell", "getprop", "ro.product.model").stdout.strip()
        report["device"] = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": state.get("versionCode")}
        print(f"rhino: {len([i for i in rhino if i >= 0])} / {len(table)} cases, host {state.get('versionCode')}, {model} API {rhino.get(-1, {}).get('sdk')}")
        # Values that describe the device, for the report (no identifiers).
        for case_id in ("device.derived", "device.manufacturers", "device.roms", "device.screen", "sensors.available", "media.music"):
            index = [c[0] for c in table].index(case_id)
            report["device"][case_id] = rhino.get(index)
        for form in args.forms.split(","):
            run = uuid.uuid4().hex[:8]
            if form == "undeclared":
                text = f'"nodejs";\nconst P31C_RUN = \'{run}\';\n' + EMIT_JS + UNDECLARED_JS
                results = base.run_script(serial, f"p31c_node_{form}_{run}.js", text, run, args.wait, project(UNDECLARED_CAPABILITIES))
                out = results.get(0, {})
                failures = {k: f"expected {v}, got {out.get(k)}" for k, v in UNDECLARED_EXPECTED.items()
                            if out.get(k) != {"code": "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED", "missing": v}}
                if "ok" not in (out.get("device.isScreenOff") or {}):
                    failures["device.isScreenOff"] = f"expected a value, got {out.get('device.isScreenOff')}"
                counts = {"pass": len(UNDECLARED_EXPECTED) + 1 - len(failures), "fail": len(failures)}
            else:
                results = base.run_script(serial, f"p31c_node_{form}_{run}.js", node_script(form, run), run, args.wait, project(CAPABILITIES))
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

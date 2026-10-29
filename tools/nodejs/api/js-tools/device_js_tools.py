#!/usr/bin/env python3
"""Rhino / Node comparison of the shared JS tools core on a device (AutoJs6 api-coverage P3.2a).

The cases of cases.js run through the host installed on the device (RunIntentActivity, as the host's
tools/augment/device_eval.py does): once in Rhino, which records the expected values, and in Node in three forms
(the main thread with require, an ES module imported from a data: URL, a nested Worker). Results travel as console
lines (GlobalConsole logcat); only counts, failing case ids and short values are printed.

Phases (all take -s SERIAL):
  state   --state FILE            record the host / Node / Bun package versions and APK md5 (JSON)
  verify  --state FILE            compare the device with a recorded state
  rhino   [--out FILE]            run the cases in Rhino and write the expected values (default rhino-expected.json)
  node    [--expected FILE] [--forms main,esm,worker] [--report FILE]
                                  run the cases in Node and compare them with the expected values
The pushed scripts are removed afterwards (/sdcard/Download/aj6-p32a).
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import threading
import time
import urllib.parse
import uuid

HERE = pathlib.Path(__file__).resolve().parent
CASES = HERE / "cases.js"
EXPECTED = HERE / "rhino-expected.json"
HOST = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGES = [HOST, HOST + ".test", "io.github.supermonster003.autojs6.plugin.nodejs",
            "io.github.supermonster003.autojs6.plugin.nodejs.test", "io.github.supermonster003.autojs6.plugin.bun.runtime"]
DEVICE_DIR = "/sdcard/Download/aj6-p32a"
MARK = "P32A"
CHUNK = 2400

# Prints one result as console lines of at most CHUNK characters: MARK run index part/parts payload END. Node's stdout
# relay may split a line into several GlobalConsole entries; END tells the collector where the line really ends.
END = "~"
EMIT_JS = """
function p32aEmit(run, index, result) {
    var text = encodeURIComponent(JSON.stringify(result));
    var parts = Math.max(1, Math.ceil(text.length / %d));
    for (var k = 0; k < parts; k++) {
        console.log('%s ' + run + ' ' + index + ' ' + (k + 1) + '/' + parts + ' ' + text.substr(k * %d, %d) + ' %s');
    }
}
""" % (CHUNK, MARK, CHUNK, CHUNK, END)

RHINO_JS = """
var p32aGlobal = (function () { return this; })();
function p32aJavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try {
        if (v instanceof java.util.List) {
            var items = [];
            for (var i = 0; i < v.size(); i++) items.push(v.get(i));
            return {list: items};
        }
        if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())};
    } catch (e) {
        return null;
    }
    return null;
}
(function (G) {
    for (var i = 0; i < JS_TOOLS_CASES.length; i++) {
        var result;
        try {
            result = {ok: jsToolsEncode(eval('(' + JS_TOOLS_CASES[i][1] + ')'), p32aJavaInfo)};
        } catch (e) {
            result = {th: jsToolsThrown(e)};
        }
        p32aEmit(P32A_RUN, i, result);
    }
    p32aEmit(P32A_RUN, -1, {done: JS_TOOLS_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
})(p32aGlobal);
"""

# The Node case runner, shared by the three forms: env holds the modules the expressions see.
NODE_RUNNER_JS = """
async function p32aRun(env, emit) {
    const names = Object.keys(env), values = names.map(name => env[name]);
    const javaInfo = v => v && typeof v === 'object' && typeof v.__autojs6JavaHandle === 'string' ? {className: v.className} : null;
    for (let i = 0; i < JS_TOOLS_CASES.length; i++) {
        const c = JS_TOOLS_CASES[i];
        const source = c.length > 2 ? c[2] : c[1];
        let result;
        if (source === null) {
            result = {skip: 1};
        } else {
            try {
                let value = new Function(...names, 'return (' + source + ');')(...values);
                let async = false;
                if (value && typeof value.then === 'function') {
                    async = true;
                    value = await value;
                }
                result = {ok: jsToolsEncode(value, javaInfo)};
                if (async) result.async = 1;
            } catch (e) {
                result = {th: jsToolsThrown(e)};
            }
        }
        emit(i, result);
    }
}
"""

# util.java, s13n.point / throwable and util.morseCode.vibrate use the Java facade.
NODE_PROJECT = {"name": "p32a-js-tools", "type": "node", "main": "main.js", "node": {"permissions": ["java_interop"]}}

NODE_ENV_CJS = """{
    util: require('autojs6:util'), species: require('autojs6:species'), G: require('autojs6:global'),
    Mathx: require('jsox.mathx'), Arrayx: require('jsox.arrayx'), Numberx: require('jsox.numberx'), jsox: require('jsox'),
    cvt: require('cvt'), fmt: require('fmt'), s13n: require('s13n'), java: require('java')
}"""

NODE_ENV_ESM = """
import util from 'autojs6:util';
import species from 'autojs6:species';
import G from 'autojs6:global';
import Mathx from 'jsox.mathx';
import Arrayx from 'jsox.arrayx';
import Numberx from 'jsox.numberx';
import jsox from 'jsox';
import cvt from 'cvt';
import fmt from 'fmt';
import s13n from 's13n';
import java from 'java';
export const env = {util, species, G, Mathx, Arrayx, Numberx, jsox, cvt, fmt, s13n, java};
"""


def adb(serial, *args, check=False, timeout=120):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def package_state(serial):
    state = {}
    for package in PACKAGES:
        dump = adb(serial, "shell", "dumpsys", "package", package).stdout
        version = re.search(r"versionCode=(\d+)", dump)
        name = re.search(r"versionName=(\S+)", dump)
        paths = [line.split(":", 1)[1].strip() for line in adb(serial, "shell", "pm", "path", package).stdout.splitlines()
                 if line.startswith("package:")]
        md5 = {pathlib.PurePosixPath(p).name: adb(serial, "shell", "md5sum", p).stdout.split()[0] for p in paths}
        state[package] = {"versionCode": version and int(version.group(1)), "versionName": name and name.group(1),
                          "md5": md5} if paths else None
    return state


class Collector:
    """Streams the GlobalConsole logcat lines of one run and reassembles the chunked results."""

    def __init__(self, serial, run):
        self.run = run
        self.parts = {}
        self.results = {}
        self.done = threading.Event()
        adb(serial, "logcat", "-c")
        self.process = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "raw", "-s", "GlobalConsole:*"],
                                        stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        pattern = re.compile(MARK + r" (\S+) (-?\d+) (\d+)/(\d+) (\S*) " + re.escape(END) + "$")
        prefix = re.compile(r"^\d\d:\d\d:\d\d\.\d{3}/[A-Z]: ")
        pending = None
        for line in self.process.stdout:
            line = prefix.sub("", line.rstrip(chr(13) + chr(10)))
            # A payload has no spaces, so a continuation never contains the mark.
            if MARK + " " in line:
                pending = line[line.index(MARK + " "):]
            elif pending is None:
                continue
            else:
                pending += line
            if not pending.endswith(" " + END):
                continue
            match = pattern.search(pending)
            pending = None
            if not match or match.group(1) != self.run:
                continue
            index, part, parts = int(match.group(2)), int(match.group(3)), int(match.group(4))
            chunks = self.parts.setdefault(index, {})
            chunks[part] = match.group(5)
            if len(chunks) == parts:
                self.results[index] = json.loads(urllib.parse.unquote("".join(chunks[k] for k in range(1, parts + 1))))
                if index == -1:
                    self.done.set()

    def close(self):
        self.process.kill()


def run_script(serial, name, text, run, wait, project=None):
    """`project`: a project.json pushed next to the script (the Node runs declare node.permissions there)."""
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(text, encoding="utf-8")
    directory = f"{DEVICE_DIR}/{run}" if project else DEVICE_DIR
    remote = f"{directory}/{project['main'] if project else name}"
    adb(serial, "shell", "mkdir", "-p", directory, check=True)
    adb(serial, "push", str(local), remote, check=True)
    if project:
        config = HERE / "__pycache__" / f"project_{run}.json"
        config.write_text(json.dumps(project), encoding="utf-8")
        adb(serial, "push", str(config), f"{directory}/project.json", check=True)
        config.unlink()
    collector = Collector(serial, run)
    try:
        time.sleep(0.5)
        adb(serial, "shell", "am", "start", "-n", f"{HOST}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
        if not collector.done.wait(wait):
            print(f"warning: {name}: {len(collector.results)} results before the timeout", file=sys.stderr)
    finally:
        collector.close()
        local.unlink(missing_ok=True)
        adb(serial, "shell", "rm", "-rf", directory if project else remote)
    return collector.results


def cases_source():
    return CASES.read_text(encoding="utf-8")


def case_ids():
    out = subprocess.run(["node", "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).cases.map(c => c[0])))",
                          str(CASES)], capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def phase_rhino(args):
    run = uuid.uuid4().hex[:8]
    script = f"var P32A_RUN = '{run}';\n" + cases_source() + EMIT_JS + RHINO_JS
    results = run_script(args.serial, f"p32a_rhino_{run}.js", script, run, args.wait)
    ids = case_ids()
    meta = results.get(-1, {})
    state = package_state(args.serial)
    host = state.get(HOST) or {}
    model = adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    expected = {"schema": "autojs6-js-tools-rhino-expected-v1", "host": {"versionCode": host.get("versionCode"),
                "versionName": host.get("versionName")}, "device": {"model": model, "sdk": meta.get("sdk")},
                "cases": {ids[i]: results[i] for i in range(len(ids)) if i in results}}
    pathlib.Path(args.out).write_text(json.dumps(expected, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    thrown = sum(1 for v in expected["cases"].values() if "th" in v)
    print(f"rhino: {len(expected['cases'])} / {len(ids)} cases ({thrown} throw), host {host.get('versionCode')}, {model} API {meta.get('sdk')}")
    return 0 if len(expected["cases"]) == len(ids) else 1


def same(a, b):
    """Encoded values are equal; a Java list (Rhino) equals an array (Node) with equal elements."""
    if isinstance(a, dict) and "list" in a and isinstance(b, list):
        a = a["list"]
    if isinstance(b, dict) and "list" in b and isinstance(a, list):
        b = b["list"]
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(same(a[k], b[k]) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(same(x, y) for x, y in zip(a, b))
    return a == b and type(a) is type(b)


def error_name(name):
    """A Java exception Rhino reports as "JavaException <Simple name>" matches a Node error of that name (as harness.js)."""
    return name[len("JavaException "):] if name.startswith("JavaException ") else name


def compare(expected, actual):
    """'pass', 'skip' or the reason of a failure."""
    if "skip" in actual:
        return "skip"
    if "th" in expected:
        name = error_name(expected["th"]["name"])
        if "th" not in actual:
            return f"expected {name}, got {json.dumps(actual.get('ok'), ensure_ascii=False)[:120]}"
        return "pass" if name == actual["th"]["name"] else f"expected {name}, got {actual['th']['name']}: {actual['th']['message'][:120]}"
    if "th" in actual:
        return f"threw {actual['th']['name']}: {actual['th']['message'][:160]}"
    if same(expected["ok"], actual["ok"]):
        return "pass"
    return f"expected {json.dumps(expected['ok'], ensure_ascii=False)[:160]}, got {json.dumps(actual['ok'], ensure_ascii=False)[:160]}"


def node_script(form, run):
    common = f"const P32A_RUN = '{run}';\n" + cases_source() + EMIT_JS + NODE_RUNNER_JS
    emit_main = "(i, r) => p32aEmit(P32A_RUN, i, r)"
    finish = "p32aEmit(P32A_RUN, -1, {done: JS_TOOLS_CASES.length, form: '%s'});" % form
    if form == "main":
        body = f"(async () => {{ try {{ await p32aRun({NODE_ENV_CJS}, {emit_main}); }} finally {{ {finish} }} }})();"
    elif form == "esm":
        body = ("(async () => { try { const {env} = await import('data:text/javascript,' + encodeURIComponent(%s));"
                " await p32aRun(env, %s); } finally { %s } })();") % (json.dumps(NODE_ENV_ESM), emit_main, finish)
    else:
        # The cases run in a Worker started by a Worker; results travel back through both parent ports.
        inner = (cases_source() + NODE_RUNNER_JS + "\nconst {parentPort} = require('node:worker_threads');\n"
                 f"p32aRun({NODE_ENV_CJS}, (i, r) => parentPort.postMessage([i, r])).catch(e => parentPort.postMessage([-2, String(e && e.stack || e)]))"
                 ".then(() => parentPort.postMessage([-1, null]));\n")
        outer = ("const {Worker, parentPort} = require('node:worker_threads');\n"
                 f"const w = new Worker({json.dumps(inner)}, {{eval: true}});\n"
                 "w.on('message', m => parentPort.postMessage(m)); w.on('error', e => parentPort.postMessage([-2, String(e && e.stack || e)]));\n")
        body = ("(async () => { const {Worker} = require('node:worker_threads'); try { await new Promise((resolve) => {"
                " const w = new Worker(" + json.dumps(outer) + ", {eval: true});"
                " w.on('message', ([i, r]) => { if (i === -1) { w.terminate(); resolve(); } else if (i === -2) { console.log('P32A-worker-error ' + r); }"
                " else p32aEmit(P32A_RUN, i, r); }); w.on('error', e => { console.log('P32A-worker-error ' + (e && e.stack || e)); resolve(); });"
                " w.on('exit', resolve); }); } finally { " + finish + " } })();")
    return '"nodejs";\n' + common + body + "\n"


def phase_node(args):
    expected = json.loads(pathlib.Path(args.expected).read_text(encoding="utf-8"))["cases"]
    ids = case_ids()
    report = {}
    failed = 0
    for form in args.forms.split(","):
        run = uuid.uuid4().hex[:8]
        results = run_script(args.serial, f"p32a_node_{form}_{run}.js", node_script(form, run), run, args.wait, NODE_PROJECT)
        counts = {"pass": 0, "fail": 0, "skip": 0, "missing": 0, "async": 0}
        failures = {}
        for i, case_id in enumerate(ids):
            actual = results.get(i)
            if actual is None:
                counts["missing"] += 1
                failures[case_id] = "missing"
                continue
            counts["async"] += 1 if actual.get("async") else 0
            verdict = compare(expected[case_id], actual)
            if verdict in ("pass", "skip"):
                counts[verdict] += 1
            else:
                counts["fail"] += 1
                failures[case_id] = verdict
        failed += counts["fail"] + counts["missing"]
        report[form] = {"counts": counts, "failures": failures}
        print(f"node {form}: pass {counts['pass']} / fail {counts['fail']} / skip {counts['skip']} / missing {counts['missing']} (async {counts['async']})")
        for case_id, verdict in list(failures.items())[:args.show]:
            print(f"  {case_id}: {verdict}")
    if args.report:
        pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["state", "verify", "rhino", "node"])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--state")
    parser.add_argument("--out", default=str(EXPECTED))
    parser.add_argument("--expected", default=str(EXPECTED))
    parser.add_argument("--forms", default="main,esm,worker")
    parser.add_argument("--report")
    parser.add_argument("--show", type=int, default=40, help="failing cases printed per form")
    parser.add_argument("--wait", type=int, default=180)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase == "state":
        state = package_state(args.serial)
        pathlib.Path(args.state).write_text(json.dumps(state, indent=1) + "\n", encoding="utf-8")
        for package, info in state.items():
            print(f"{package}: {info and info['versionCode']} {info and ' '.join(info['md5'].values())}")
        return 0
    if args.phase == "verify":
        recorded = json.loads(pathlib.Path(args.state).read_text(encoding="utf-8"))
        current = package_state(args.serial)
        differ = [p for p in recorded if recorded[p] != current.get(p)]
        print("verify: " + ("same as recorded" if not differ else "differs: " + ", ".join(differ)))
        return 1 if differ else 0
    return phase_rhino(args) if args.phase == "rhino" else phase_node(args)


if __name__ == "__main__":
    sys.exit(main())

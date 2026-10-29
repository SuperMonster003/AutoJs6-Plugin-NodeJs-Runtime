#!/usr/bin/env python3
"""Rhino / Node comparison of the files and data family on a device (AutoJs6 api-coverage P3.2b).

The cases of the cases-*.js files (colors, files, mime and the timing recorder, zip, storages / mediainfo / epub) run
through the host installed on the device, as ../js-tools/device_js_tools.py does: once in Rhino, which records the
expected values, and in Node in three forms (the main thread with require, an ES module imported from a data: URL, a
nested Worker). Each group gets an empty directory D on shared storage for the run (Rhino and Node use different ones;
the host and the Node plugin both need all-files access). Only counts, failing case ids and short values are printed.

Phases (all take -s SERIAL):
  rhino   [--out FILE] [--groups ...]  run the cases in Rhino and write the expected values (default rhino-expected.json)
  node    [--expected FILE] [--forms main,esm,worker] [--groups ...] [--report FILE]
                                       run the cases in Node and compare them with the expected values
The state / verify phases of device_js_tools.py record and check the package versions and APK md5. The pushed scripts
and the run directories (/sdcard/Download/aj6-p32b) are removed afterwards.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import uuid

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "js-tools"))
import device_js_tools as base  # noqa: E402

EXPECTED = HERE / "rhino-expected.json"
DEVICE_DIR = "/sdcard/Download/aj6-p32b"
MARK = "P32B"
base.DEVICE_DIR = DEVICE_DIR
base.MARK = MARK
base.HERE = HERE

# group: the cases file and the Node modules its expressions see, "specifier" or "specifier#member" (Rhino sees the
# same names as globals, G is the global object and D the group's directory in both).
GROUPS = {
    "colors": ("cases-colors.js", {"colors": "autojs6:colors", "Color": "autojs6:global#Color", "java": "java"}),
    "files": ("cases-files.js", {"files": "autojs6:files"}),
    "mime": ("cases-mime-recorder.js", {"mime": "autojs6:mime", "recorder": "autojs6:recorder"}),
    "zip": ("cases-zip.js", {"zip": "autojs6:zip", "files": "autojs6:files"}),
    "data": ("cases-data.js", {"storages": "autojs6:storages", "mediainfo": "autojs6:mediainfo", "G": "autojs6:global"}),
}

# base.EMIT_JS was formatted with base's own mark when it was imported.
EMIT_JS = base.EMIT_JS.replace("p32aEmit", "p32bEmit").replace("'P32A ", "'P32B ")

RHINO_JS = """
var p32bGlobal = (function () { return this; })();
function p32bJavaInfo(v) {
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
// Each case runs in a Runnable called from the script, so that an interpreter failure stays within the case.
var p32bCase = null, p32bResult = null;
var p32bRunner = new java.lang.Runnable({run: function () {
    try {
        p32bResult = {ok: jsToolsEncode(p32bCase(), p32bJavaInfo)};
    } catch (e) {
        p32bResult = {th: jsToolsThrown(e)};
    }
}});
(function (G) {
    var index = 0;
    for (var g = 0; g < P32B_GROUPS.length; g++) {
        var D = P32B_DIRS[g], cases = P32B_GROUPS[g];
        for (var i = 0; i < cases.length; i++, index++) {
            var source = cases[i][1];
            p32bCase = function () { return eval('(' + source + ')'); };
            p32bResult = null;
            try {
                p32bRunner.run();
            } catch (e) {
                // A Java exception that a host member rethrows unwrapped (a checked InvocationTargetException) makes
                // the interpreter itself fail with a ClassCastException, past the case's try / catch; it arrives here
                // through the Runnable, wrapped like any other Java exception.
                p32bResult = {rhinoCrash: String(e && e.javaException ? e.javaException.getClass().getName() : e)};
            }
            p32bEmit(P32B_RUN, index, p32bResult);
        }
    }
    p32bEmit(P32B_RUN, -1, {done: index, sdk: android.os.Build.VERSION.SDK_INT});
})(p32bGlobal);
"""

# The Node case runner, shared by the three forms: envs[g] holds the modules group g's expressions see.
NODE_RUNNER_JS = """
async function p32bRun(envs, emit) {
    const javaInfo = v => v && typeof v === 'object' && typeof v.__autojs6JavaHandle === 'string' ? {className: v.className} : null;
    let index = 0;
    for (let g = 0; g < P32B_GROUPS.length; g++) {
        const env = Object.assign({D: P32B_DIRS[g]}, envs[g]);
        const names = Object.keys(env), values = names.map(name => env[name]);
        for (const c of P32B_GROUPS[g]) {
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
            emit(index++, result);
            // A burst of console lines can lose some on the way to logcat.
            if (index % 20 === 0) await new Promise(resolve => setTimeout(resolve, 30));
        }
    }
}
"""

# zip and files.open / toFile use the Java facade; storages, mediainfo and files have their own permissions. Without
# timeoutMs the host gives the run a budget of a few seconds, which the 1500 cases exceed.
NODE_PROJECT = {"name": "p32b-files-data", "type": "node", "main": "main.js",
                "node": {"permissions": ["java_interop", "storage", "media.metadata", "files", "files.write", "epub"],
                         "timeoutMs": 900000}}

JS_TOOLS_HELPERS = (HERE.parent / "js-tools" / "cases.js").read_text(encoding="utf-8")


def helpers_source():
    """jsToolsEncode / jsToolsThrown of the P3.2a cases file, without its cases."""
    start = JS_TOOLS_HELPERS.index("// Encodes a result as JSON-safe data")
    end = JS_TOOLS_HELPERS.index("if (typeof module === \"object\"")
    return JS_TOOLS_HELPERS[start:end]


def group_names(args):
    return [g for g in args.groups.split(",") if g]


def cases_variable(text):
    return re.search(r"^var (\w+_CASES) = \[", text, re.M).group(1)


def groups_source(names):
    """P32B_GROUPS, the case arrays of the groups; each cases file runs in its own function, so that their helpers do
    not clash."""
    groups = []
    for name in names:
        text = (HERE / GROUPS[name][0]).read_text(encoding="utf-8")
        text = text[:text.index("if (typeof module === \"object\"")] if "if (typeof module === \"object\"" in text else text
        groups.append("(function () {\n" + text + "\nreturn " + cases_variable(text) + ";\n})()")
    return "var P32B_GROUPS = [" + ",\n".join(groups) + "];\n"


def case_ids(names):
    ids = []
    for name in names:
        out = subprocess.run(["node", "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).cases.map(c => c[0])))",
                              str(HERE / GROUPS[name][0])], capture_output=True, text=True, check=True)
        ids += [f"{name}/{case_id}" for case_id in json.loads(out.stdout)]
    return ids


def make_dirs(serial, run, kind, names):
    dirs = [f"{DEVICE_DIR}/{run}/{kind}/{name}" for name in names]
    for d in dirs:
        base.adb(serial, "shell", "mkdir", "-p", d, check=True)
    return "var P32B_DIRS = " + json.dumps(dirs) + ";\n"


def phase_rhino(args):
    names = group_names(args)
    run = uuid.uuid4().hex[:8]
    script = (f"var P32B_RUN = '{run}';\n" + make_dirs(args.serial, run, "rhino", names) + helpers_source() +
              groups_source(names) + EMIT_JS + RHINO_JS)
    try:
        results = base.run_script(args.serial, f"p32b_rhino_{run}.js", script, run, args.wait)
    finally:
        base.adb(args.serial, "shell", "rm", "-rf", f"{DEVICE_DIR}/{run}")
    ids = case_ids(names)
    meta = results.get(-1, {})
    state = base.package_state(args.serial)
    host = state.get(base.HOST) or {}
    model = base.adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    previous = {}
    if pathlib.Path(args.out).exists():
        previous = json.loads(pathlib.Path(args.out).read_text(encoding="utf-8")).get("cases", {})
    # Groups not run keep their recorded values.
    cases = {k: v for k, v in previous.items() if k.split("/", 1)[0] not in names}
    cases.update({ids[i]: results[i] for i in range(len(ids)) if i in results})
    expected = {"schema": "autojs6-files-data-rhino-expected-v1", "host": {"versionCode": host.get("versionCode"),
                "versionName": host.get("versionName")}, "device": {"model": model, "sdk": meta.get("sdk")}, "cases": cases}
    pathlib.Path(args.out).write_text(json.dumps(expected, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    got = sum(1 for i in range(len(ids)) if i in results)
    thrown = sum(1 for i in range(len(ids)) if i in results and "th" in results[i])
    crashed = [ids[i] for i in range(len(ids)) if i in results and "rhinoCrash" in results[i]]
    print(f"rhino: {got} / {len(ids)} cases ({thrown} throw, {len(crashed)} interpreter failures), host {host.get('versionCode')},"
          f" {model} API {meta.get('sdk')}")
    for case_id in crashed[:args.show]:
        print(f"  interpreter failure: {case_id}")
    return 0 if got == len(ids) else 1


def node_envs(names, form):
    """The env objects of the groups: CJS requires, or ESM imports and the expression naming them."""
    def split(entry):
        specifier, _, member = entry.partition("#")
        return specifier, "." + member if member else ""
    if form == "esm":
        imports, envs, seen = [], [], {}
        for name in names:
            members = []
            for local, entry in GROUPS[name][1].items():
                specifier, member = split(entry)
                if specifier not in seen:
                    seen[specifier] = f"m{len(seen)}"
                    imports.append(f"import {seen[specifier]} from '{specifier}';")
                members.append(f"{local}: {seen[specifier]}{member}")
            envs.append("{" + ", ".join(members) + "}")
        return "\n".join(imports) + "\nexport const envs = [" + ", ".join(envs) + "];\n"
    return "[" + ", ".join("{" + ", ".join("%s: require('%s')%s" % ((local,) + split(entry)) for local, entry in GROUPS[name][1].items()) + "}"
                           for name in names) + "]"


def node_script(form, run, names, dirs):
    common = f"const P32B_RUN = '{run}';\n" + dirs + helpers_source() + groups_source(names) + EMIT_JS + NODE_RUNNER_JS
    emit_main = "(i, r) => p32bEmit(P32B_RUN, i, r)"
    finish = "p32bEmit(P32B_RUN, -1, {done: P32B_GROUPS.reduce((n, g) => n + g.length, 0), form: '%s'});" % form
    if form == "main":
        body = f"(async () => {{ try {{ await p32bRun({node_envs(names, form)}, {emit_main}); }} finally {{ {finish} }} }})();"
    elif form == "esm":
        body = ("(async () => { try { const {envs} = await import('data:text/javascript,' + encodeURIComponent(%s));"
                " await p32bRun(envs, %s); } finally { %s } })();") % (json.dumps(node_envs(names, form)), emit_main, finish)
    else:
        # The cases run in a Worker started by a Worker; results travel back through both parent ports.
        inner = (dirs + helpers_source() + groups_source(names) + NODE_RUNNER_JS + "\nconst {parentPort} = require('node:worker_threads');\n"
                 f"p32bRun({node_envs(names, 'main')}, (i, r) => parentPort.postMessage([i, r])).catch(e => parentPort.postMessage([-2, String(e && e.stack || e)]))"
                 ".then(() => parentPort.postMessage([-1, null]));\n")
        outer = ("const {Worker, parentPort} = require('node:worker_threads');\n"
                 f"const w = new Worker({json.dumps(inner)}, {{eval: true}});\n"
                 "w.on('message', m => parentPort.postMessage(m)); w.on('error', e => parentPort.postMessage([-2, String(e && e.stack || e)]));\n")
        body = ("(async () => { const {Worker} = require('node:worker_threads'); try { await new Promise((resolve) => {"
                " const w = new Worker(" + json.dumps(outer) + ", {eval: true});"
                " w.on('message', ([i, r]) => { if (i === -1) { w.terminate(); resolve(); } else if (i === -2) { console.log('P32B-worker-error ' + r); }"
                " else p32bEmit(P32B_RUN, i, r); }); w.on('error', e => { console.log('P32B-worker-error ' + (e && e.stack || e)); resolve(); });"
                " w.on('exit', resolve); }); } finally { " + finish + " } })();")
    return '"nodejs";\n' + common + body + "\n"


def phase_node(args):
    names = group_names(args)
    expected = json.loads(pathlib.Path(args.expected).read_text(encoding="utf-8"))["cases"]
    ids = case_ids(names)
    report = {}
    failed = 0
    for form in args.forms.split(","):
        run = uuid.uuid4().hex[:8]
        dirs = make_dirs(args.serial, run, "d", names)
        # run_script removes the project directory DEVICE_DIR/run, D included.
        results = base.run_script(args.serial, f"p32b_node_{form}_{run}.js", node_script(form, run, names, dirs), run, args.wait,
                                  NODE_PROJECT)
        counts = {"pass": 0, "fail": 0, "skip": 0, "missing": 0, "async": 0, "rhinoCrash": 0}
        failures = {}
        for i, case_id in enumerate(ids):
            actual = results.get(i)
            if actual is None or case_id not in expected:
                counts["missing"] += 1
                failures[case_id] = "missing" if actual is None else "no Rhino value"
                continue
            counts["async"] += 1 if actual.get("async") else 0
            if "rhinoCrash" in expected[case_id]:
                # Rhino fails on the error itself (see RHINO_JS); Node must throw.
                counts["rhinoCrash"] += 1
                verdict = "pass" if "th" in actual or "skip" in actual else (
                    "Rhino fails on this error, Node returned " + json.dumps(actual.get("ok"), ensure_ascii=False)[:120])
            else:
                verdict = base.compare(expected[case_id], actual)
            if verdict in ("pass", "skip"):
                counts[verdict] += 1
            else:
                counts["fail"] += 1
                failures[case_id] = verdict
        failed += counts["fail"] + counts["missing"]
        per_group = {name: sum(1 for c in failures if c.startswith(name + "/")) for name in names}
        report[form] = {"counts": counts, "failuresPerGroup": per_group, "failures": failures}
        print(f"node {form}: pass {counts['pass']} / fail {counts['fail']} / skip {counts['skip']} / missing {counts['missing']}"
              f" (async {counts['async']}, Rhino failures {counts['rhinoCrash']}); failing per group {per_group}")
        for case_id, verdict in list(failures.items())[:args.show]:
            print(f"  {case_id}: {verdict}")
    if args.report:
        pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["rhino", "node"])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--groups", default=",".join(GROUPS))
    parser.add_argument("--out", default=str(EXPECTED))
    parser.add_argument("--expected", default=str(EXPECTED))
    parser.add_argument("--forms", default="main,esm,worker")
    parser.add_argument("--report")
    parser.add_argument("--show", type=int, default=40, help="failing cases printed per form")
    parser.add_argument("--wait", type=int, default=300)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    return phase_rhino(args) if args.phase == "rhino" else phase_node(args)


if __name__ == "__main__":
    sys.exit(main())

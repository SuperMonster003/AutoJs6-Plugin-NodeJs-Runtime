"""Install one release APK and its release androidTest APK on a device and run the acceptance classes.

    py tools/nodejs/release/release_acceptance.py --serial 63751f63 \
        --apk app/releases/1.32.0/autojs6-plugin-nodejs-runtime-v1.32.0-arm64-v8a-<crc>.apk \
        --test-apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk \
        --expect-version 1.32.0

The test APK must come from `-PnodeAndroidTestBuildType=release` (see AGENTS.md); a debug test APK
cannot drive the minified release app; a debug APK pairs with the debug test APK the same way.
Prints the device facts, the installed version before and after, and one line per class (pass /
fail, test count, assumption skips, seconds). Instrumentation output is kept only for failing
classes, cut to the failure summary. `--classes full` runs every `*SmokeTest` class plus the
conformance, manifest and PluginInfo classes (the full device regression). `--skip-install` reuses
the installed pair and `--report` writes the per-class results as JSON. `--detached` starts each
class without -w and reads the TestRunner logcat lines instead, for rental-device adb proxies that
drop long instrumentation streams (Samsung Remote Test Lab); such a run has no UiAutomation, so
NpmEcosystemSmokeTest and UnrestrictedFsSmokeTest fail there with a null UiAutomation and are rerun
without it (their short -w runs, like the cold-start runs, survive the proxy). `--cold-start N` then
force-stops the app N times and runs RuntimeColdStartMeasurementTest after each stop, printing
the cold first output per run with its P50/P95, and the warm first output / environment /
bootstrap P50/P95 as medians over the runs (the M23.8 table columns; M23.8 used 5 Debug and 3
Release runs). `--replace-incompatible`
uninstalls an installed build signed with another key before installing (its app data is lost).
The test APK is uninstalled afterwards unless `--keep-test-apk` is given. Exit code is non-zero
when any step fails.
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

PACKAGE = 'io.github.supermonster003.autojs6.plugin.nodejs'
TEST_PACKAGE = f'{PACKAGE}.test'
RUNNER = f'{TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner'
DEFAULT_CLASSES = ['SimpleRunSmokeTest', 'NpmEcosystemSmokeTest', 'PluginManifestContractTest', 'PluginInfoContractTest']
TEST_SOURCES = Path(__file__).resolve().parents[3] / 'app/src/androidTest/java' / PACKAGE.replace('.', '/')


def class_names(spec: str) -> list[str]:
    if spec == 'full':
        smoke = sorted(path.stem for path in TEST_SOURCES.glob('*SmokeTest.java'))
        return smoke + ['NodeRuntimePluginAndroidConformanceTest', 'PluginManifestContractTest', 'PluginInfoContractTest']
    return [name.strip() for name in spec.split(',') if name.strip()]


def adb(serial: str, *args: str, timeout: int = 1800) -> tuple[int, str]:
    proc = subprocess.run(['adb', '-s', serial, *args], capture_output=True, text=True,
                          encoding='utf-8', errors='replace', timeout=timeout)
    return proc.returncode, (proc.stdout + proc.stderr).replace('\r', '')


def shell(serial: str, command: str) -> str:
    return adb(serial, 'shell', command)[1].strip()


def installed_version(serial: str, package: str) -> str:
    dump = shell(serial, f'dumpsys package {package}')
    name = re.search(r'versionName=(\S+)', dump)
    code = re.search(r'versionCode=(\d+)', dump)
    return f'{name.group(1)} ({code.group(1)})' if name and code else 'not installed'


def install(serial: str, apk: str, package: str, replace_incompatible: bool) -> tuple[bool, str]:
    try:
        code, out = adb(serial, 'install', '-r', apk, timeout=300)
    except subprocess.TimeoutExpired:
        # Some ROMs (ColorOS InstallGuideActivity, MIUI USB install) hold adb installs behind an on-device
        # confirmation that the device owner has to answer.
        focus = re.search(r'mCurrentFocus=\S+ \S+ (\S+)\}', shell(serial, 'dumpsys window | grep mCurrentFocus'))
        return False, f'timed out after 300s, foreground {focus.group(1) if focus else "unknown"}'
    if 'Success' in out:
        return True, 'installed'
    if 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' in out and replace_incompatible:
        adb(serial, 'uninstall', package)
        code, out = adb(serial, 'install', apk, timeout=300)
        if 'Success' in out:
            return True, 'reinstalled (previous signature differed, its data was removed)'
    failure = re.search(r'(INSTALL_\w+|Failure \[[^\]]*\])', out)
    return False, failure.group(1) if failure else out.strip()[-200:]


def run_class(serial: str, name: str) -> dict:
    started = time.monotonic()
    _, out = adb(serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', f'{PACKAGE}.{name}', RUNNER, timeout=3600)
    seconds = round(time.monotonic() - started)
    # -r reports every test; status -4 is an assumption failure, -3 an ignored test.
    skips = len(re.findall(r'^INSTRUMENTATION_STATUS_CODE: -[34]\s*$', out, re.M))
    result = {'class': name, 'seconds': seconds, 'skips': skips}
    ok = re.search(r'^OK \((\d+) tests?\)', out, re.M)
    broken = re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg=|Process crashed', out)
    if ok and not broken:
        return {**result, 'passed': True, 'tests': int(ok.group(1)),
                'detail': f'{ok.group(1)} tests, {skips} skipped, {seconds}s'}
    counts = re.search(r'Tests run: (\d+),\s+Failures: (\d+)', out)
    # Without a result line (adb transport dropped, runner killed) the output tail is the only clue.
    summary = f'run {counts.group(1)}, failures {counts.group(2)}' if counts else \
        f'no result line ({" ".join(out.split())[-160:] or "no output"})'
    failures = re.findall(r'^\d+\) (\S+)', out, re.M)
    detail = re.findall(r'^(?:[\w.$]+(?:Error|Exception)[^\n]{0,160})', out, re.M)[:3]
    # The report keeps each failure's leading stack lines (assertion text includes stdout/stderr).
    stacks = [stack[:4000] for stack in re.findall(r'^INSTRUMENTATION_STATUS: stack=(.*?)(?=^INSTRUMENTATION_STATUS)', out, re.M | re.S)]
    return {**result, 'passed': False, 'tests': int(counts.group(1)) if counts else 0, 'failed': failures, 'stacks': stacks,
            'detail': f'{summary}, {seconds}s; failed: {", ".join(failures) or "-"}; ' + ' | '.join(detail)}


def run_class_detached(serial: str, name: str) -> dict:
    # Rental-device proxies (Samsung Remote Test Lab) drop long `am instrument -w` streams, so the class starts
    # without -w and the outcome comes from the TestRunner logcat lines, read with short commands. Without -w the
    # runner gets no UiAutomation connection.
    adb(serial, 'logcat', '-c')
    started = time.monotonic()
    adb(serial, 'shell', 'am', 'instrument', '-r', '-e', 'class', f'{PACKAGE}.{name}', RUNNER, timeout=120)
    lines: dict[str, None] = {}  # Polled repeatedly so lines survive a rotating logcat buffer.
    while True:
        time.sleep(5)
        _, log = adb(serial, 'logcat', '-d', '-v', 'tag', '-s', 'TestRunner:*', timeout=120)
        lines.update(dict.fromkeys(log.splitlines()))
        out = '\n'.join(lines)
        finished = re.search(r'run finished: (\d+) tests?, (\d+) failed', out)
        crashed = 'Process crashed' in out
        elapsed = time.monotonic() - started
        if finished or crashed or elapsed > 3600 or (elapsed > 180 and 'run started' not in out):
            break
    seconds = round(elapsed)
    skips = len(re.findall(r'assumption failed: |ignored: ', out))
    failures = re.findall(r'(?<!assumption )failed: (\S+)', out)
    stacks = [stack[:4000] for stack in re.findall(r'----- begin exception -----\n(.*?)----- end exception -----', out, re.S)]
    result = {'class': name, 'seconds': seconds, 'skips': skips, 'tests': int(finished.group(1)) if finished else 0}
    if finished and finished.group(2) == '0' and not crashed:
        return {**result, 'passed': True, 'detail': f'{finished.group(1)} tests, {skips} skipped, {seconds}s'}
    summary = f'run {finished.group(1)}, failures {finished.group(2)}' if finished else \
        'process crashed' if crashed else f'no run finished line ({" ".join(out.split())[-160:] or "no output"})'
    return {**result, 'passed': False, 'failed': failures, 'stacks': stacks,
            'detail': f'{summary}, {seconds}s; failed: {", ".join(failures) or "-"}'}


def cold_start(serial: str, runs: int) -> dict:
    samples = []
    for _ in range(runs):
        adb(serial, 'shell', 'am', 'force-stop', PACKAGE)
        time.sleep(2)
        _, out = adb(serial, 'shell', 'am', 'instrument', '-w', '-e', 'class',
                     f'{PACKAGE}.RuntimeColdStartMeasurementTest', RUNNER, timeout=600)
        found = re.search(r'M10_COLD_START_RESULT=(\{.*\})', out)
        if not found:
            return {'passed': False, 'samples': samples, 'detail': out.strip()[-300:]}
        samples.append(json.loads(found.group(1)))
    cold = sorted(s['coldFirstOutputMs'] for s in samples)
    # The M23.8 table: cold P50/P95 across runs (P95 is the maximum for N <= 5); the warm columns are
    # the median over runs of each run's P50 and P95, not percentiles of the pooled samples.
    warm = lambda pick: '/'.join(str(statistics.median(pick(s)[key] for s in samples)).removesuffix('.0')
                                 for key in ('medianMs', 'p95Ms'))
    phase = lambda name: warm(lambda s: s['warmPhaseDistribution'][name])
    return {'passed': True, 'samples': samples,
            'detail': f'cold first output {"/".join(str(s["coldFirstOutputMs"]) for s in samples)} ms '
                      f'(P50/P95 {statistics.median(cold):g}/{cold[min(len(cold) - 1, round(0.95 * (len(cold) - 1)))]}); '
                      f'warm first output {warm(lambda s: s["warmFirstOutput"])}, environment {phase("environmentCreateMs")}, '
                      f'bootstrap {phase("bootstrapMs")} ms (P50/P95)'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk')
    parser.add_argument('--test-apk')
    parser.add_argument('--skip-install', action='store_true', help='run against the installed app and test APK')
    parser.add_argument('--detached', action='store_true',
                        help='start each class without -w and read TestRunner logcat (rental-device adb proxies)')
    parser.add_argument('--expect-version', help='versionName the installed app must report')
    parser.add_argument('--classes', default=','.join(DEFAULT_CLASSES),
                        help='comma-separated simple class names, or "full"')
    parser.add_argument('--report', type=Path, help='write the per-class results to this JSON file')
    parser.add_argument('--cold-start', type=int, default=0, metavar='N',
                        help='force-stop and run the cold-start measurement N times after the classes')
    parser.add_argument('--replace-incompatible', action='store_true')
    parser.add_argument('--keep-test-apk', action='store_true')
    args = parser.parse_args()
    if not args.skip_install and not (args.apk and args.test_apk):
        parser.error('--apk and --test-apk are required unless --skip-install is given')
    serial = args.serial

    state = adb(serial, 'get-state')[1].strip()
    if state != 'device':
        print(f'FAIL device {serial}: {state}')
        return 1
    facts = shell(serial, 'echo "$(getprop ro.product.model) API $(getprop ro.build.version.sdk) '
                          '$(getprop ro.product.cpu.abi) page $(getconf PAGE_SIZE)"')
    print(f'device   {serial}: {facts}')
    print(f'before   {installed_version(serial, PACKAGE)}')

    failed = False
    for apk, package in () if args.skip_install else ((args.apk, PACKAGE), (args.test_apk, TEST_PACKAGE)):
        ok, detail = install(serial, apk, package, args.replace_incompatible)
        print(f'{"ok  " if ok else "FAIL"}     install {package.rsplit(".", 1)[-1]}: {detail}')
        if not ok:
            return 1
    after = installed_version(serial, PACKAGE)
    version_ok = args.expect_version is None or after.startswith(f'{args.expect_version} ')
    failed |= not version_ok
    print(f'{"ok  " if version_ok else "FAIL"}     installed {after}')

    results = []
    for name in class_names(args.classes):
        result = (run_class_detached if args.detached else run_class)(serial, name)
        results.append(result)
        failed |= not result['passed']
        print(f'{"ok  " if result["passed"] else "FAIL"}     {name}: {result["detail"]}', flush=True)
    passed = sum(result['passed'] for result in results)
    print(f'summary  {passed}/{len(results)} classes passed, {sum(r["tests"] for r in results)} tests, '
          f'{sum(r["skips"] for r in results)} skipped')
    measured = None
    if args.cold_start:
        measured = cold_start(serial, args.cold_start)
        failed |= not measured['passed']
        print(f'{"ok  " if measured["passed"] else "FAIL"}     cold start: {measured["detail"]}', flush=True)
    if args.report:
        report = {'serial': serial, 'device': facts, 'installed': after, 'classes': results, 'coldStart': measured}
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    if not args.keep_test_apk:
        adb(serial, 'uninstall', TEST_PACKAGE)
        print(f'cleanup  test APK uninstalled')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())

"""Install one release APK and its release androidTest APK on a device and run the acceptance classes.

    py tools/nodejs/release/release_acceptance.py --serial 63751f63 \
        --apk app/releases/1.32.0/autojs6-plugin-nodejs-runtime-v1.32.0-arm64-v8a-<crc>.apk \
        --test-apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk \
        --expect-version 1.32.0

The test APK must come from `-PnodeAndroidTestBuildType=release` (see AGENTS.md); a debug test APK
cannot drive the minified release app. Prints the device facts, the installed version before and
after, and one line per class (pass / fail, test count, seconds). Instrumentation output is kept
only for failing classes, cut to the failure summary. `--replace-incompatible` uninstalls an
installed build signed with another key before installing (its app data is lost). The test APK is
uninstalled afterwards unless `--keep-test-apk` is given. Exit code is non-zero when any step fails.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time

PACKAGE = 'io.github.supermonster003.autojs6.plugin.nodejs'
TEST_PACKAGE = f'{PACKAGE}.test'
RUNNER = f'{TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner'
DEFAULT_CLASSES = ['SimpleRunSmokeTest', 'NpmEcosystemSmokeTest', 'PluginManifestContractTest', 'PluginInfoContractTest']


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


def run_class(serial: str, name: str) -> tuple[bool, str]:
    started = time.monotonic()
    _, out = adb(serial, 'shell', 'am', 'instrument', '-w', '-e', 'class', f'{PACKAGE}.{name}', RUNNER, timeout=3600)
    seconds = time.monotonic() - started
    ok = re.search(r'^OK \((\d+) tests?\)', out, re.M)
    broken = re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg=|Process crashed', out)
    if ok and not broken:
        return True, f'{ok.group(1)} tests, {seconds:.0f}s'
    counts = re.search(r'Tests run: (\d+),\s+Failures: (\d+)', out)
    summary = f'run {counts.group(1)}, failures {counts.group(2)}' if counts else 'no result line'
    failures = re.findall(r'^\d+\) (\S+)', out, re.M)
    detail = re.findall(r'^(?:[\w.$]+(?:Error|Exception)[^\n]{0,160})', out, re.M)[:3]
    return False, f'{summary}, {seconds:.0f}s; failed: {", ".join(failures) or "-"}; ' + ' | '.join(detail)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--test-apk', required=True)
    parser.add_argument('--expect-version', help='versionName the installed app must report')
    parser.add_argument('--classes', default=','.join(DEFAULT_CLASSES), help='comma-separated simple class names')
    parser.add_argument('--replace-incompatible', action='store_true')
    parser.add_argument('--keep-test-apk', action='store_true')
    args = parser.parse_args()
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
    for apk, package in ((args.apk, PACKAGE), (args.test_apk, TEST_PACKAGE)):
        ok, detail = install(serial, apk, package, args.replace_incompatible)
        print(f'{"ok  " if ok else "FAIL"}     install {package.rsplit(".", 1)[-1]}: {detail}')
        if not ok:
            return 1
    after = installed_version(serial, PACKAGE)
    version_ok = args.expect_version is None or after.startswith(f'{args.expect_version} ')
    failed |= not version_ok
    print(f'{"ok  " if version_ok else "FAIL"}     installed {after}')

    for name in [c.strip() for c in args.classes.split(',') if c.strip()]:
        ok, detail = run_class(serial, name)
        failed |= not ok
        print(f'{"ok  " if ok else "FAIL"}     {name}: {detail}')

    if not args.keep_test_apk:
        adb(serial, 'uninstall', TEST_PACKAGE)
        print(f'cleanup  test APK uninstalled')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())

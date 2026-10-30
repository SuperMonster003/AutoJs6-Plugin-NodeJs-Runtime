"""Run the AutoJs6 host's Node plugin contract classes against one installed plugin build.

    py tools/nodejs/release/host_contract.py --serial emulator-5630 \
        --plugin-apk app/releases/2.0.0/autojs6-plugin-nodejs-runtime-v2.0.0-x86_64-<crc>.apk \
        --host-apk <host>/app/build/outputs/apk/app/debug/<host debug>.apk \
        --host-test-apk <host>/app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk \
        --expect-plugin-version 2.0.0

A release must pass these classes with each plugin line the host still discovers (M25.3: 1.32.x and
2.0.0). The host pair comes from one host checkout (`:app:assembleAppDebug :app:assembleAppDebugAndroidTest`);
the plugin APK is any signed release or debug build. Prints the device facts, the installed host and plugin
versions, and one line per class like release_acceptance.py. A class that skips every test (the host
harness assumes a discoverable, trusted plugin) counts as failed. `--skip-host-install` reuses the installed
host pair; the host test APK is uninstalled afterwards unless `--keep-test-apk` is given, the host app stays.
NodeHostPluginIntegrationInstrumentationTest's TypeScript case also needs the TypeScript Engine plugin, and the
provider registry's ocr / barcode health the ML Kit OCR / Barcode plugins, on the device.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from release_acceptance import PACKAGE, adb, install, installed_version, run_class, shell

HOST_PACKAGE = 'org.autojs.autojs6'
HOST_TEST_PACKAGE = f'{HOST_PACKAGE}.test'
HOST_RUNNER = f'{HOST_TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner'
HOST_PREFIX = 'org.autojs.autojs.engine'
# Discovery, Binder execution, the capability bridge and its permission manifest, provider registry,
# conformance, engine switching and the required-plugin security check.
DEFAULT_CLASSES = [
    'NodeHostPluginIntegrationInstrumentationTest',
    'NodeRuntimePluginBridgeInstrumentationTest',
    'NodeRuntimePluginR5InstrumentationTest',
    'NodeBridgePermissionManifestInstrumentationTest',
    'NodeBridgeProviderRegistryInstrumentationTest',
    'NodeBridgeConformanceInstrumentationTest',
    'NodeR6EngineSwitchInstrumentationTest',
    'NodeR6RequiredPluginSecurityInstrumentationTest',
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--serial', required=True)
    parser.add_argument('--plugin-apk', help='plugin APK to install first; omit to keep the installed plugin')
    parser.add_argument('--host-apk')
    parser.add_argument('--host-test-apk')
    parser.add_argument('--skip-host-install', action='store_true', help='reuse the installed host and host test APK')
    parser.add_argument('--expect-plugin-version', help='versionName the installed plugin must report')
    parser.add_argument('--classes', default=','.join(DEFAULT_CLASSES), help='comma-separated simple class names')
    parser.add_argument('--report', type=Path, help='write the per-class results to this JSON file')
    parser.add_argument('--replace-incompatible', action='store_true')
    parser.add_argument('--keep-test-apk', action='store_true')
    args = parser.parse_args()
    if not args.skip_host_install and not (args.host_apk and args.host_test_apk):
        parser.error('--host-apk and --host-test-apk are required unless --skip-host-install is given')
    serial = args.serial

    state = adb(serial, 'get-state')[1].strip()
    if state != 'device':
        print(f'FAIL device {serial}: {state}')
        return 1
    facts = shell(serial, 'echo "$(getprop ro.product.model) API $(getprop ro.build.version.sdk) '
                          '$(getprop ro.product.cpu.abi) page $(getconf PAGE_SIZE)"')
    print(f'device   {serial}: {facts}')

    installs = [(args.plugin_apk, PACKAGE)] if args.plugin_apk else []
    if not args.skip_host_install:
        installs += [(args.host_apk, HOST_PACKAGE), (args.host_test_apk, HOST_TEST_PACKAGE)]
    for apk, package in installs:
        ok, detail = install(serial, apk, package, args.replace_incompatible)
        print(f'{"ok  " if ok else "FAIL"}     install {package}: {detail}')
        if not ok:
            return 1
    host, plugin = installed_version(serial, HOST_PACKAGE), installed_version(serial, PACKAGE)
    version_ok = args.expect_plugin_version is None or plugin.startswith(f'{args.expect_plugin_version} ')
    failed = not version_ok
    print(f'ok       host {host}')
    print(f'{"ok  " if version_ok else "FAIL"}     plugin {plugin}')

    results = []
    for name in [name.strip() for name in args.classes.split(',') if name.strip()]:
        result = run_class(serial, name, HOST_PREFIX, HOST_RUNNER)
        if result['passed'] and result['skips'] >= result['tests']:
            result = {**result, 'passed': False, 'detail': f'all {result["tests"]} tests skipped (plugin not discovered?)'}
        results.append(result)
        failed |= not result['passed']
        print(f'{"ok  " if result["passed"] else "FAIL"}     {name}: {result["detail"]}', flush=True)
    passed = sum(result['passed'] for result in results)
    print(f'summary  {passed}/{len(results)} classes passed, {sum(r["tests"] for r in results)} tests, '
          f'{sum(r["skips"] for r in results)} skipped')
    if args.report:
        report = {'serial': serial, 'device': facts, 'host': host, 'plugin': plugin, 'classes': results}
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    if not args.keep_test_apk:
        adb(serial, 'uninstall', HOST_TEST_PACKAGE)
        print('cleanup  host test APK uninstalled')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())

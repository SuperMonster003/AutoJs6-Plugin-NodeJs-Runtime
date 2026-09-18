#!/usr/bin/env bash
set -euo pipefail
prefix=io.github.supermonster003.autojs6.plugin.nodejs

# Kept next to the results so a slot or test-process death on the CI emulator can be read
# without re-running: system kills (lmkd, ActivityManager), native crashes and our own tags.
collect_diagnostics() {
    adb shell "dumpsys activity exit-info $prefix; echo; dumpsys activity exit-info $prefix.test" > smoke-exit-info.txt 2>&1 || true
    adb shell 'head -6 /proc/meminfo; echo; dumpsys meminfo --oom 2>/dev/null | head -60' > smoke-meminfo.txt 2>&1 || true
    adb logcat -d -v time 2>/dev/null \
        | grep -E 'lowmemorykiller|lmkd|Fatal signal|F DEBUG|FATAL|AndroidRuntime|ActivityManager: (Killing|Process .* has died|Force stopping|Start proc)|NodeJsRuntimePlugin|nodejs|autojs6' \
        > smoke-logcat.txt || true
    echo '::group::exit-info'; cat smoke-exit-info.txt; echo '::endgroup::'
}
trap collect_diagnostics EXIT

page_size="$(adb shell 'getconf PAGE_SIZE 2>/dev/null || getconf PAGESIZE 2>/dev/null' | tr -d '\r')"
test "$page_size" = "${EXPECTED_PAGE_SIZE:?Expected emulator page size is required}"
# The runner returns as soon as sys.boot_completed flips; the package manager can still be busy.
for _ in $(seq 1 30); do
    if adb shell pm path android > /dev/null 2>&1; then break; fi
    sleep 2
done
adb logcat -c || true
adb install -r apks/debug/*-x86_64.apk
adb install -r apks/androidTest/debug/*-androidTest.apk
adb shell am instrument -w -e class "$prefix.SimpleRunSmokeTest,$prefix.StreamingOutputSmokeTest,$prefix.NodeRuntimePluginAndroidConformanceTest,$prefix.PluginManifestContractTest,$prefix.NodeCliLauncherSmokeTest" "$prefix.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results.txt
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' smoke-results.txt; then
    exit 1
fi
grep -Eq '^OK \([1-9][0-9]* tests?\)' smoke-results.txt

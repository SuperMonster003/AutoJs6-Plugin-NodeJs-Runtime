#!/usr/bin/env bash
set -euo pipefail
page_size="$(adb shell 'getconf PAGE_SIZE 2>/dev/null || getconf PAGESIZE 2>/dev/null' | tr -d '\r')"
test "$page_size" = "${EXPECTED_PAGE_SIZE:?Expected emulator page size is required}"
adb install -r apks/debug/*-x86_64.apk
adb install -r apks/androidTest/debug/*-androidTest.apk
prefix=io.github.supermonster003.autojs6.plugin.nodejs
adb shell am instrument -w -e class "$prefix.SimpleRunSmokeTest,$prefix.StreamingOutputSmokeTest,$prefix.NodeRuntimePluginAndroidConformanceTest" "$prefix.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results.txt
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' smoke-results.txt; then
    exit 1
fi
grep -Eq '^OK \([1-9][0-9]* tests?\)' smoke-results.txt

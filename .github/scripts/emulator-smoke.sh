#!/usr/bin/env bash
set -euo pipefail
adb install -r apks/debug/*-x86_64.apk
adb install -r apks/androidTest/debug/*-androidTest.apk
prefix=io.github.supermonster003.autojs6.plugin.nodejs
adb shell am instrument -w -e class "$prefix.SimpleRunSmokeTest,$prefix.StreamingOutputSmokeTest,$prefix.NodeRuntimePluginAndroidConformanceTest" "$prefix.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results.txt
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' smoke-results.txt; then
    exit 1
fi
grep -Eq '^OK \([0-9]+ tests?\)' smoke-results.txt

package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import static io.github.supermonster003.autojs6.plugin.nodejs.ConcurrentExecutionSmokeTest.*;

import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import java.io.File;

/**
 * Roadmap M12.5: a Node.js fatal error aborts the runtime slot; the failure carries the exit record (M12.5a) plus
 * Node's own diagnostic report, and getRuntimeInfo exposes the newest report as lastCrash. The crash is reproduced
 * deterministically with the execution heap cap (maxOldGenerationSizeMb): a script that grows its heap past the
 * cap dies of "JavaScript heap out of memory", the class of crash first seen on the CI emulator. Only wire literals
 * are used: the Release test APK runs against an R8-minified app.
 */
public final class NativeCrashReportSmokeTest {
    private static final int HEAP_CAP_MB = 64;
    private static final String HEAP_LIMIT_SCRIPT =
            "const s = require('v8').getHeapStatistics(); console.log('limit=' + s.heap_size_limit + ' used=' + s.used_heap_size);";
    private static final String OOM_SCRIPT =
            "function grow() { const keep = []; for (;;) keep.push(new Array(100000).fill(1)); }\n"
            + "function main() { grow(); }\n"
            + "main();";

    @Test public void theHeapCapLowersTheIsolateLimitAndLeavesASmallScriptAlone() throws Exception {
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            long defaultLimit = heapLimit(runtime.runScript(request("m12-5-limit-default", HEAP_LIMIT_SCRIPT), null));
            Bundle capped = request("m12-5-limit-capped", HEAP_LIMIT_SCRIPT);
            capped.putInt("maxOldGenerationSizeMb", HEAP_CAP_MB);
            Bundle result = runtime.runScript(capped, null);
            long cappedLimit = heapLimit(result);
            assertTrue("capped " + cappedLimit + " vs default " + defaultLimit, cappedLimit < defaultLimit);
            // heap_size_limit adds the young generation and code space to the old-generation cap.
            assertTrue("capped limit " + cappedLimit, cappedLimit < 512L * 1024L * 1024L);
            Bundle next = runtime.runScript(request("m12-5-limit-restored", HEAP_LIMIT_SCRIPT), null);
            assertEquals("the cap must not leak into the next execution", defaultLimit, heapLimit(next));
            Bundle measurement = new Bundle();
            measurement.putString("stream", "\nm12_5.heap_cap=PASS default=" + defaultLimit + " capped=" + cappedLimit
                    + " " + result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").trim() + "\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, measurement);
        }
    }

    @Test public void aV8HeapOutOfMemoryAbortLeavesAReportThatTheFailureAndRuntimeInfoCarry() throws Exception {
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            String executionId = "m12-5-v8-oom";
            Bundle request = request(executionId, OOM_SCRIPT);
            request.putInt("maxOldGenerationSizeMb", HEAP_CAP_MB);
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 90_000L);
            long startedAt = SystemClock.elapsedRealtime();
            Bundle result = runtime.runScript(request, null);
            long elapsedMs = SystemClock.elapsedRealtime() - startedAt;

            String message = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertFalse(message, result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertFalse(result.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT));
            assertEquals(message, "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE", result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
            int pid = result.getInt(NodeJsRuntimeContract.KEY_PID);
            int slotId = result.getInt(NodeJsRuntimeContract.KEY_SLOT_ID, -1);
            assertTrue(message, message.startsWith("Node runtime slot " + slotId + " (pid " + pid + ") exited during the execution: "));
            Bundle exit = result.getBundle("slotExit");
            assertNotNull("slotExit missing: " + message, exit);
            Bundle crash = exit.getBundle("crash");
            assertNotNull("crash report missing: " + message, crash);
            assertEquals("OOMError", crash.getString("trigger"));
            assertEquals("Allocation failed - JavaScript heap out of memory", crash.getString("event"));
            assertEquals(pid, crash.getLong("pid"));
            assertTrue(crash.getLong("heapLimitBytes") > 0);
            assertTrue(crash.getLong("heapUsedBytes") > 0);
            assertTrue(crash.getString("nodejsVersion", "").startsWith("v24."));
            File file = new File(crash.getString("file", ""));
            assertEquals(executionId + ".json", file.getName());
            assertTrue(file.getAbsolutePath(), file.isFile());
            assertEquals("crash", file.getParentFile().getName());
            String summary = exit.getString("summary", "");
            assertTrue(summary, summary.contains("Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap "));
            assertTrue(summary, summary.contains("; old-generation cap " + HEAP_CAP_MB + " MB; "));
            assertEquals(HEAP_CAP_MB, crash.getInt("maxOldGenerationSizeMb"));
            assertTrue(summary, summary.contains("; report " + file.getAbsolutePath()));
            assertTrue(message, message.endsWith(summary + "."));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals(summary, "CRASH_NATIVE", exit.getString("reasonName"));
                assertTrue(exit.getBoolean("nativeCrash"));
                // The signal comes from the system's exit record. An x86_64 API 36 emulator was seen to keep status 0
                // in a CRASH_NATIVE record when the zygote's SIGCHLD preceded the death note (the record never
                // settles); the report line still says what happened, so only a non-zero status must name SIGABRT.
                int status = exit.getInt("status", -1);
                assertTrue(summary, status == 0 || status == 6);
                assertEquals(status == 0 ? "signal 0" : "SIGABRT", exit.getString("signal"));
                assertTrue(summary, summary.startsWith("CRASH_NATIVE (" + exit.getString("signal")
                        + "; see the logcat tombstone; Node.js fatal error: "));
            } else {
                assertTrue(summary, summary.startsWith("exit reason unavailable below Android 11 (Node.js fatal error: "));
            }

            Bundle info = runtime.getRuntimeInfo();
            Bundle lastExit = info.getBundle("lastSlotExit");
            assertNotNull(lastExit);
            assertNotNull(lastExit.getBundle("crash"));
            assertEquals(summary, lastExit.getString("summary"));
            Bundle lastCrash = info.getBundle("lastCrash");
            assertNotNull("lastCrash missing from getRuntimeInfo", lastCrash);
            assertEquals(crash.getString("file"), lastCrash.getString("file"));
            assertEquals(crash.getString("event"), lastCrash.getString("event"));
            assertEquals(pid, lastCrash.getLong("pid"));
            // Read back from the file alone, so it carries the report line but not this request's cap.
            assertTrue(lastCrash.getString("summary", ""), lastCrash.getString("summary", "")
                    .startsWith("Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap "));
            assertTrue(lastCrash.getString("summary", "").endsWith("; report " + file.getAbsolutePath()));

            Bundle recovered = runtime.runScript(request(executionId + "-recovered", "console.log('recovered=' + (6 * 7))"), null);
            assertSucceeded(recovered);
            assertTrue(recovered.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("recovered=42"));
            assertNotEquals(pid, recovered.getInt(NodeJsRuntimeContract.KEY_PID));

            Bundle measurement = new Bundle();
            measurement.putString("stream", "\nm12_5.v8_oom=PASS elapsedMs=" + elapsedMs + " waitedMs=" + exit.getLong("waitedMs")
                    + " reportBytes=" + file.length() + " summary=" + summary + "\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, measurement);
        }
    }

    private static long heapLimit(Bundle result) {
        assertSucceeded(result);
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        int start = stdout.indexOf("limit=") + "limit=".length();
        int end = stdout.indexOf(' ', start);
        return Long.parseLong(stdout.substring(start, end < 0 ? stdout.length() : end).trim());
    }
}

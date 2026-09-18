package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import static io.github.supermonster003.autojs6.plugin.nodejs.ConcurrentExecutionSmokeTest.*;

import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.system.OsConstants;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Roadmap M12.5a: a slot that dies mid-execution reports the system's exit record (ApplicationExitInfo),
 * not a bare DeadObjectException. The runtime disables process.abort()/process.kill(), so the signal is
 * raised from outside the slot, which is also how a real libnode crash or an lmkd kill reaches the pool.
 * Only wire literals are used: the Release test APK runs against an R8-minified app, so app classes
 * cannot be referenced at runtime.
 */
public final class SlotExitReasonSmokeTest {
    private static final long ACTIVE_SLOT_TIMEOUT_MS = 15_000L;

    @Test public void nativeSignalOnTheSlotIsReportedAsANativeCrashWithItsSignal() throws Exception {
        Bundle result = killActiveSlot("m12-5a-sigabrt", OsConstants.SIGABRT);
        Bundle exit = result.getBundle("slotExit");
        assertNotNull(exit);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(exit.getString("summary"), "CRASH_NATIVE", exit.getString("reasonName"));
            assertEquals("SIGABRT", exit.getString("signal"));
            assertEquals(OsConstants.SIGABRT, exit.getInt("status"));
            assertTrue(exit.getBoolean("nativeCrash"));
            assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                    .contains("CRASH_NATIVE (SIGABRT; see the logcat tombstone)"));
        }
    }

    @Test public void sigkillOnTheSlotIsReportedAsSignaled() throws Exception {
        Bundle result = killActiveSlot("m12-5a-sigkill", OsConstants.SIGKILL);
        Bundle exit = result.getBundle("slotExit");
        assertNotNull(exit);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(exit.getString("summary"), "SIGNALED", exit.getString("reasonName"));
            assertEquals("SIGKILL", exit.getString("signal"));
            assertFalse(exit.getBoolean("nativeCrash"));
            assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "").contains("SIGNALED (SIGKILL)"));
        }
    }

    private static Bundle killActiveSlot(String executionId, int signal) throws Exception {
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            AsyncExecutionSmokeTest.Completion completion = new AsyncExecutionSmokeTest.Completion();
            Bundle request = request(executionId, "console.log('ready'); setInterval(() => {}, 1000);");
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 60_000L);
            assertTrue(runtime.startScript(request, completion).getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
            assertTrue("script never started", completion.output.await(20, TimeUnit.SECONDS));
            int pid = activeSlotPid(runtime, executionId);
            long killedAt = SystemClock.elapsedRealtime();
            Process.sendSignal(pid, signal);
            Bundle result = completion.result();
            long reportedAfterMs = SystemClock.elapsedRealtime() - killedAt;

            assertFalse(result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertFalse(result.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT));
            assertEquals(NodeJsRuntimePluginService.ERROR_UNAVAILABLE, result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
            assertEquals(pid, result.getInt(NodeJsRuntimeContract.KEY_PID));
            int slotId = result.getInt(NodeJsRuntimeContract.KEY_SLOT_ID, -1);
            assertTrue(slotId >= 0);
            String message = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertTrue(message, message.startsWith("Node runtime slot " + slotId + " (pid " + pid + ") exited during the execution: "));
            Bundle exit = result.getBundle("slotExit");
            assertNotNull("slotExit missing: " + message, exit);
            assertEquals(pid, exit.getInt("pid"));
            assertEquals(slotId, exit.getInt("slotId"));
            assertEquals(executionId, exit.getString("executionId"));
            String summary = exit.getString("summary", "");
            assertTrue(message, message.endsWith(summary + "."));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertTrue(summary, exit.getBoolean("available"));
                assertEquals("application_exit_info", exit.getString("source"));
                assertTrue(exit.getLong("timestamp") > 0);
                assertTrue(exit.getString("processName", "").endsWith(":nodejs_runtime" + slotId));
            } else {
                assertFalse(exit.getBoolean("available"));
                assertEquals("unsupported_api", exit.getString("source"));
                assertEquals("exit reason unavailable below Android 11", summary);
            }

            Bundle last = runtime.getRuntimeInfo().getBundle("lastSlotExit");
            assertNotNull("lastSlotExit missing from getRuntimeInfo", last);
            assertEquals(pid, last.getInt("pid"));
            assertEquals(executionId, last.getString("executionId"));
            assertEquals(summary, last.getString("summary"));

            Bundle recovered = runtime.runScript(request(executionId + "-recovered", "console.log('recovered=' + (6 * 7))"), null);
            assertSucceeded(recovered);
            assertTrue(recovered.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("recovered=42"));
            assertNotEquals(pid, recovered.getInt(NodeJsRuntimeContract.KEY_PID));

            Bundle measurement = new Bundle();
            measurement.putString("stream", "\nm12_5a." + executionId + "=PASS reportedAfterMs=" + reportedAfterMs
                    + " waitedMs=" + exit.getLong("waitedMs") + " summary=" + summary + "\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, measurement);
            return result;
        }
    }

    private static int activeSlotPid(INodeJsRuntimePlugin runtime, String executionId) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + ACTIVE_SLOT_TIMEOUT_MS;
        while (true) {
            ArrayList<Bundle> slots = runtime.getRuntimeInfo().getParcelableArrayList("slots");
            if (slots != null) {
                for (Bundle slot : slots) {
                    if (executionId.equals(slot.getString("active")) && slot.getInt("pid") > 0) return slot.getInt("pid");
                }
            }
            assertTrue("execution never became active on a slot", SystemClock.elapsedRealtime() < deadline);
            Thread.sleep(100L);
        }
    }
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Roadmap M8.1: timeoutMs covers queue wait plus execution wall time. */
@RunWith(AndroidJUnit4.class)
public final class ExecutionTimeoutSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_TIMEOUT_MS = 3_000L;
    private static final long RESULT_TIMEOUT_MS = 20_000L;
    private static final String EXECUTION_ID = "m8-wall-clock-timeout";

    @Test
    public void tightLoopTimesOutAndRuntimeProcessRemainsReusable() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        ComponentName component = new ComponentName(
                context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService"
        );
        assertTrue(
                "bindService was rejected",
                context.bindService(new Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, EXECUTION_ID);
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, SCRIPT_TIMEOUT_MS);
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.timeout.started');\n" +
                            "while (true) {}\n"
            );
            long callStartedAt = SystemClock.elapsedRealtime();
            Future<Bundle> pending = executor.submit(() -> runtime.runScript(request, emptyCallback()));
            Bundle timedOut = pending.get(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long callerElapsedMs = SystemClock.elapsedRealtime() - callStartedAt;

            assertNotNull("timed execution returned null", timedOut);
            assertFalse("timed execution must not succeed", timedOut.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue("timeout marker missing", timedOut.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT));
            assertEquals(
                    NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT,
                    timedOut.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            );
            assertEquals(
                    SCRIPT_TIMEOUT_MS,
                    timedOut.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, -1L)
            );
            assertTrue(
                    "watchdog fired implausibly early: " + callerElapsedMs + "ms",
                    callerElapsedMs >= SCRIPT_TIMEOUT_MS - 500L
            );
            assertTrue(
                    "watchdog did not return within its cooperative-stop window: " + callerElapsedMs + "ms",
                    callerElapsedMs < RESULT_TIMEOUT_MS
            );
            int timeoutPid = timedOut.getInt(NodeJsRuntimeContract.KEY_PID, -1);
            assertTrue("timeout result carried no runtime PID", timeoutPid > 0);

            Bundle followUp = new Bundle();
            followUp.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.timeout.reuse=' + (40 + 2));\n"
            );
            Bundle reuse = runtime.runScript(followUp, emptyCallback());
            assertNotNull("follow-up execution returned null", reuse);
            assertTrue(
                    "follow-up failed after timeout: " +
                            reuse.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    reuse.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue(
                    "follow-up stdout missing marker",
                    reuse.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m8.timeout.reuse=42")
            );
            assertEquals(
                    "runtime process restarted instead of stopping cooperatively",
                    timeoutPid,
                    reuse.getInt(NodeJsRuntimeContract.KEY_PID, -2)
            );
        } finally {
            executor.shutdownNow();
            context.unbindService(connection);
        }
    }

    private static INodeJsRuntimeCallback emptyCallback() {
        return new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        };
    }
}

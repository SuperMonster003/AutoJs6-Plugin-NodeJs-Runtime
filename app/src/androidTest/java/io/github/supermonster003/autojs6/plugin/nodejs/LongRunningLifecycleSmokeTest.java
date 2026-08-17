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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M2.5: a resident script (timers keep the loop alive, no timeout
 * requested) runs indefinitely with live output, stays observable through
 * getRuntimeInfo while running, and stops on demand — the lifecycle of a
 * long-lived `node service.js` process.
 */
@RunWith(AndroidJUnit4.class)
public final class LongRunningLifecycleSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long RUN_RETURN_TIMEOUT_MS = 30_000L;
    private static final String EXECUTION_ID = "m2-long-running";
    private static final int TICKS_BEFORE_STOP = 12;

    @Test
    public void residentScriptRunsObservablyUntilStopped() throws Exception {
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

            AtomicInteger tickCount = new AtomicInteger(0);
            CountDownLatch enoughTicks = new CountDownLatch(TICKS_BEFORE_STOP);
            INodeJsRuntimeCallback.Stub callback = new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                    String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "");
                    String text = event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "");
                    if (NodeJsRuntimeContract.EVENT_STDOUT.equals(type) && text.contains("m2.resident.tick")) {
                        tickCount.incrementAndGet();
                        enoughTicks.countDown();
                    }
                }
            };

            // No timeout field at all: resident semantics must not require
            // callers to invent a giant number.
            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, EXECUTION_ID);
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "let n = 0;\n" +
                            "setInterval(() => { console.log('m2.resident.tick=' + (++n)); }, 250);\n"
            );
            Future<Bundle> pendingResult = executor.submit(() -> runtime.runScript(request, callback));

            // ≥12 ticks ≈ 3s of live streaming: the script is genuinely
            // resident (no implicit timeout) and its output keeps flowing.
            assertTrue(
                    "resident script stopped ticking early (implicit timeout?); ticks=" + tickCount.get(),
                    enoughTicks.await(RUN_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );

            // While running, the runtime reports the execution as active.
            Bundle info = runtime.getRuntimeInfo();
            assertEquals(
                    "active execution id not observable while resident script runs",
                    EXECUTION_ID,
                    info.getString(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID, "")
            );
            assertTrue(
                    "activeForMs should reflect the resident runtime",
                    info.getLong(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_FOR_MS, -1L) >= 1_000L
            );

            // Manual stop ends it without killing the runtime process.
            assertTrue("cancelScript declined the resident execution", runtime.cancelScript(EXECUTION_ID));
            Bundle stopped = pendingResult.get(RUN_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("stopped runScript returned null", stopped);
            assertFalse(
                    "a stopped resident script must not report success",
                    stopped.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertEquals(
                    "stop must surface the readable cancel code",
                    "ERR_AUTOJS6_NODE_SCRIPT_CANCELLED",
                    stopped.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            );
            assertTrue("resident script ticked fewer than expected", tickCount.get() >= TICKS_BEFORE_STOP);
            int residentPid = stopped.getInt(NodeJsRuntimeContract.KEY_PID, -1);

            // The runtime survives for the next script.
            Bundle followUp = new Bundle();
            followUp.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m2.resident.after=' + (6 * 7));\n"
            );
            Bundle reuse = runtime.runScript(followUp, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });
            assertTrue(
                    "follow-up script failed after resident stop: " +
                            reuse.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    reuse.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue(
                    "follow-up stdout missing marker",
                    reuse.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m2.resident.after=42")
            );
            assertEquals(
                    "runtime process changed after resident stop (restart instead of cooperative stop)",
                    residentPid,
                    reuse.getInt(NodeJsRuntimeContract.KEY_PID, -2)
            );
        } finally {
            executor.shutdownNow();
            context.unbindService(connection);
        }
    }
}

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M2.2: cancelScript must stop a live event loop via node::Stop and
 * leave the runtime process reusable. Restart-style cancellation would kill
 * the process, so the follow-up script asserting the same PID is the proof
 * that cancellation is now cooperative.
 */
@RunWith(AndroidJUnit4.class)
public final class CooperativeCancelSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long RUN_RETURN_TIMEOUT_MS = 30_000L;
    private static final String EXECUTION_ID = "m2-cancel-target";

    @Test
    public void cancellingEndlessScriptKeepsProcessReusable() throws Exception {
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

            CountDownLatch firstOutput = new CountDownLatch(1);
            INodeJsRuntimeCallback.Stub callback = new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                    String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "");
                    String text = event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "");
                    if (NodeJsRuntimeContract.EVENT_STDOUT.equals(type) && text.contains("m2.cancel.alive")) {
                        firstOutput.countDown();
                    }
                }
            };

            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, EXECUTION_ID);
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m2.cancel.alive');\n" +
                            "setInterval(() => { console.log('m2.cancel.tick'); }, 250);\n"
            );
            // runScript blocks its binder thread until the script ends, so it
            // must run off-thread while the test cancels from here.
            Future<Bundle> pendingResult = executor.submit(() -> runtime.runScript(request, callback));

            assertTrue(
                    "endless script never produced its first output (streaming broken?)",
                    firstOutput.await(RUN_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            assertTrue("cancelScript declined the active execution", runtime.cancelScript(EXECUTION_ID));

            Bundle cancelled = pendingResult.get(RUN_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("cancelled runScript returned null", cancelled);
            assertFalse(
                    "a cancelled endless script must not report success",
                    cancelled.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertEquals(
                    "cancellation must surface the readable cancel code",
                    "ERR_AUTOJS6_NODE_SCRIPT_CANCELLED",
                    cancelled.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            );
            int cancelledPid = cancelled.getInt(NodeJsRuntimeContract.KEY_PID, -1);
            assertTrue("cancelled result carried no runtime PID", cancelledPid > 0);

            // The same binder (same process) must accept and run the next
            // script: cooperative stop keeps the runtime alive.
            Bundle followUp = new Bundle();
            followUp.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m2.cancel.reuse=' + (40 + 2));\n"
            );
            Bundle reuseResult = runtime.runScript(followUp, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });
            assertNotNull("follow-up runScript returned null", reuseResult);
            assertTrue(
                    "follow-up script failed after cancellation: " +
                            reuseResult.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    reuseResult.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue(
                    "follow-up stdout missing marker",
                    reuseResult.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m2.cancel.reuse=42")
            );
            assertEquals(
                    "runtime process was restarted after cooperative cancel (PID changed)",
                    cancelledPid,
                    reuseResult.getInt(NodeJsRuntimeContract.KEY_PID, -2)
            );
        } finally {
            executor.shutdownNow();
            context.unbindService(connection);
        }
    }
}

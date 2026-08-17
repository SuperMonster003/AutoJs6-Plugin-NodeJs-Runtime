package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M2.3: concurrent runScript calls queue and complete serially
 * instead of bouncing with BUSY. Three scripts submitted at once must all
 * succeed; the old gate admitted one and rejected the other two on the spot.
 */
@RunWith(AndroidJUnit4.class)
public final class SerialQueueSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final int CONCURRENT_SCRIPTS = 3;
    private static final long ALL_RESULTS_TIMEOUT_MS = 120_000L;

    @Test
    public void concurrentSubmissionsQueueAndAllComplete() throws Exception {
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
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SCRIPTS);
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            List<Future<Bundle>> pending = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_SCRIPTS; index++) {
                final int scriptIndex = index;
                pending.add(executor.submit(() -> {
                    Bundle request = new Bundle();
                    request.putString(
                            NodeJsRuntimeContract.KEY_EXECUTION_ID,
                            "m2-queue-" + scriptIndex
                    );
                    request.putString(
                            NodeJsRuntimeContract.KEY_SOURCE,
                            "console.log('m2.queue.marker." + scriptIndex + "=ok');\n"
                    );
                    return runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                        @Override
                        public void onEvent(Bundle event) {
                        }
                    });
                }));
            }

            long deadline = System.currentTimeMillis() + ALL_RESULTS_TIMEOUT_MS;
            for (int index = 0; index < pending.size(); index++) {
                long remaining = Math.max(1L, deadline - System.currentTimeMillis());
                Bundle result = pending.get(index).get(remaining, TimeUnit.MILLISECONDS);
                assertNotNull("script " + index + " returned null", result);
                assertTrue(
                        "script " + index + " failed (queueing broken?): code=" +
                                result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "") +
                                " message=" + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                        result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
                );
                assertTrue(
                        "script " + index + " stdout missing its marker",
                        result.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                                .contains("m2.queue.marker." + index + "=ok")
                );
            }

            // All three finished through one runtime process: serial queue,
            // not restarts.
            Bundle info = runtime.getRuntimeInfo();
            assertEquals(
                    "queue capacity not advertised",
                    3,
                    info.getInt("queueCapacity", -1)
            );
        } finally {
            executor.shutdownNow();
            context.unbindService(connection);
        }
    }
}

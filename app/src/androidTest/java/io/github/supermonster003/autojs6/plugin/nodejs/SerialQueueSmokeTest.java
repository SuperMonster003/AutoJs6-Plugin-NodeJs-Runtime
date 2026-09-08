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

    @Test public void occupiedSlotsPreserveFifoCapacityAndQueuedCancellation() throws Exception {
        try (ConcurrentExecutionSmokeTest.Binding binding = new ConcurrentExecutionSmokeTest.Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            ConcurrentExecutionSmokeTest.assertSucceeded(runtime.prewarmRuntime(new Bundle()));
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<String> starts = java.util.Collections.synchronizedList(new ArrayList<>());
            CountDownLatch blockersReady = new CountDownLatch(2);
            try {
                List<Future<Bundle>> blockers = new ArrayList<>();
                for (int index = 0; index < 2; index++) {
                    String id = "m16-blocker-" + index;
                    blockers.add(executor.submit(() -> runtime.runScript(ConcurrentExecutionSmokeTest.request(id,
                            "console.log('ready'); setInterval(() => {}, 1000);"), ConcurrentExecutionSmokeTest.output(blockersReady))));
                }
                assertTrue(blockersReady.await(20, TimeUnit.SECONDS));
                List<Future<Bundle>> pending = new ArrayList<>();
                for (int index = 0; index < 3; index++) {
                    String id = "m16-fifo-" + index;
                    pending.add(executor.submit(() -> runtime.runScript(ConcurrentExecutionSmokeTest.request(id,
                            "console.log('queued-start');"), new INodeJsRuntimeCallback.Stub() {
                        @Override public void onEvent(Bundle event) {
                            if (NodeJsRuntimeContract.EVENT_STDOUT.equals(event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE))) starts.add(id);
                        }
                    })));
                    long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
                    while (runtime.getRuntimeInfo().getInt("queuedExecutions") != index + 1 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(10);
                    assertEquals(index + 1, runtime.getRuntimeInfo().getInt("queuedExecutions"));
                }
                Bundle overflow = runtime.runScript(ConcurrentExecutionSmokeTest.request("m16-overflow", "throw Error('must not run')"), null);
                assertEquals(NodeJsRuntimePluginService.ERROR_BUSY, overflow.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                assertTrue(runtime.cancelScript("m16-fifo-1"));
                assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED,
                        pending.get(1).get(10, TimeUnit.SECONDS).getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                assertTrue(runtime.cancelScript("m16-blocker-0"));
                Bundle stopped = blockers.get(0).get(15, TimeUnit.SECONDS);
                Bundle first = pending.get(0).get(15, TimeUnit.SECONDS), third = pending.get(2).get(15, TimeUnit.SECONDS);
                ConcurrentExecutionSmokeTest.assertSucceeded(first);
                ConcurrentExecutionSmokeTest.assertSucceeded(third);
                assertEquals(java.util.Arrays.asList("m16-fifo-0", "m16-fifo-2"), starts);
                assertEquals(stopped.getInt(NodeJsRuntimeContract.KEY_PID), first.getInt(NodeJsRuntimeContract.KEY_PID));
                assertEquals(first.getInt(NodeJsRuntimeContract.KEY_PID), third.getInt(NodeJsRuntimeContract.KEY_PID));
                assertTrue(!blockers.get(1).isDone());
                assertTrue(runtime.cancelScript("m16-blocker-1"));
                blockers.get(1).get(15, TimeUnit.SECONDS);
                assertEquals(0, runtime.getRuntimeInfo().getInt("queuedExecutions"));
            } finally {
                for (int i = 0; i < 2; i++) runtime.cancelScript("m16-blocker-" + i);
                for (int i = 0; i < 3; i++) runtime.cancelScript("m16-fifo-" + i);
                executor.shutdownNow();
            }
        }
    }

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

            // All submissions complete; the pool retains three global waiting positions.
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

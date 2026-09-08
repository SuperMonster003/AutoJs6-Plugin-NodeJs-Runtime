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

/** Proves that the same M8.1 wall-clock budget is consumed while queued. */
@RunWith(AndroidJUnit4.class)
public final class QueuedExecutionTimeoutSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long QUEUED_TIMEOUT_MS = 1_000L;
    private static final long RESULT_TIMEOUT_MS = 20_000L;
    private static final String ACTIVE_EXECUTION_ID = "m8-queue-blocker";

    @Test
    public void queuedRequestConsumesItsTotalBudgetWithoutRunning() throws Exception {
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
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Bundle> activeResult = null;
        Future<Bundle> secondResult = null;
        INodeJsRuntimePlugin runtime = null;
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            CountDownLatch activeStarted = new CountDownLatch(1);
            Bundle activeRequest = new Bundle();
            activeRequest.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, ACTIVE_EXECUTION_ID);
            activeRequest.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.queue.blocker.started');\n" +
                            "setInterval(() => {}, 250);\n"
            );
            INodeJsRuntimePlugin activeRuntime = runtime;
            activeResult = executor.submit(() -> activeRuntime.runScript(
                    activeRequest,
                    new INodeJsRuntimeCallback.Stub() {
                        @Override
                        public void onEvent(Bundle event) {
                            if (NodeJsRuntimeContract.EVENT_STDOUT.equals(
                                    event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "")
                            ) && event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "")
                                    .contains("m8.queue.blocker.started")) {
                                activeStarted.countDown();
                            }
                        }
                    }
            ));
            assertTrue(
                    "blocking execution never became active",
                    activeStarted.await(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            Bundle secondRequest = new Bundle(activeRequest);
            secondRequest.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, ACTIVE_EXECUTION_ID + "-second");
            CountDownLatch secondStarted = new CountDownLatch(1);
            secondResult = executor.submit(() -> activeRuntime.runScript(secondRequest, ConcurrentExecutionSmokeTest.output(secondStarted)));
            assertTrue(secondStarted.await(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Bundle queuedRequest = new Bundle();
            queuedRequest.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.queue.must-not-run');\n"
            );
            queuedRequest.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, QUEUED_TIMEOUT_MS);
            long queuedStartedAt = SystemClock.elapsedRealtime();
            Bundle timedOut = runtime.runScript(queuedRequest, emptyCallback());
            long queuedElapsedMs = SystemClock.elapsedRealtime() - queuedStartedAt;

            assertNotNull("queued execution returned null", timedOut);
            assertFalse("queued timeout must not succeed", timedOut.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue("queued timeout marker missing", timedOut.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT));
            assertEquals(
                    NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT,
                    timedOut.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            );
            assertEquals(
                    QUEUED_TIMEOUT_MS,
                    timedOut.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, -1L)
            );
            assertFalse(
                    "timed-out queued source unexpectedly executed",
                    timedOut.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                            .contains("m8.queue.must-not-run")
            );
            assertTrue(
                    "queue budget fired implausibly early: " + queuedElapsedMs + "ms",
                    queuedElapsedMs >= QUEUED_TIMEOUT_MS - 250L
            );
            assertEquals(
                    "queue blocker stopped while another request timed out",
                    ACTIVE_EXECUTION_ID,
                    runtime.getRuntimeInfo().getString(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID, "")
            );

            assertTrue("failed to stop queue blocker", runtime.cancelScript(ACTIVE_EXECUTION_ID));
            Bundle stopped = activeResult.get(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertEquals(
                    "ERR_AUTOJS6_NODE_SCRIPT_CANCELLED",
                    stopped.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            );
            assertTrue(runtime.cancelScript(ACTIVE_EXECUTION_ID + "-second"));
            secondResult.get(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            if (runtime != null && activeResult != null && !activeResult.isDone()) {
                runtime.cancelScript(ACTIVE_EXECUTION_ID);
            }
            if (runtime != null && secondResult != null && !secondResult.isDone()) runtime.cancelScript(ACTIVE_EXECUTION_ID + "-second");
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

package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.system.Os;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Roadmap M8.2: uncooperative cancellation escalates to a runtime-only restart. */
@RunWith(AndroidJUnit4.class)
public final class CancellationRestartFallbackSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long FIRST_OUTPUT_TIMEOUT_MS = 30_000L;
    private static final long NATIVE_BLOCK_ENTRY_TIMEOUT_MS = 10_000L;
    private static final long NATIVE_BLOCK_SETTLE_MS = 500L;
    private static final long PROCESS_DEATH_TIMEOUT_MS = 12_000L;
    private static final long RUN_RETURN_TIMEOUT_MS = 20_000L;
    private static final String EXECUTION_ID = "m8-uncooperative-cancel";

    @Test
    public void synchronousFifoReadEscalatesAfterGraceAndFreshProcessRunsNextScript() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        File fifo = new File(context.getCacheDir(), "m8-uncooperative-cancel.fifo");
        File entered = new File(context.getCacheDir(), "m8-uncooperative-cancel.entered");
        assertTrue("stale FIFO fixture could not be removed", !fifo.exists() || fifo.delete());
        assertTrue("stale entry marker could not be removed", !entered.exists() || entered.delete());
        Os.mkfifo(fifo.getAbsolutePath(), 0600);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        int firstPid;
        Future<Bundle> pending;
        try (ServiceSession first = ServiceSession.connect(context)) {
            firstPid = first.runtime.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID, -1);
            assertTrue("initial runtime PID unavailable", firstPid > 0);

            CountDownLatch firstOutput = new CountDownLatch(1);
            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, EXECUTION_ID);
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.fallback.blocked');\n" +
                            "const fs = require('node:fs');\n" +
                            "fs.writeFileSync(" + jsString(entered.getAbsolutePath()) + ", 'ready');\n" +
                            "fs.readFileSync(" + jsString(fifo.getAbsolutePath()) + ");\n" +
                            "console.log('m8.fallback.unexpected-return');\n"
            );
            pending = executor.submit(() -> first.runtime.runScript(
                    request,
                    new INodeJsRuntimeCallback.Stub() {
                        @Override
                        public void onEvent(Bundle event) {
                            if (NodeJsRuntimeContract.EVENT_STDOUT.equals(
                                    event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "")
                            ) && event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "")
                                    .contains("m8.fallback.blocked")) {
                                firstOutput.countDown();
                            }
                        }
                    }
            ));
            assertTrue(
                    "blocking script never started",
                    firstOutput.await(FIRST_OUTPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            assertTrue(
                    "script never entered the synchronous FIFO fixture",
                    waitForFile(entered, NATIVE_BLOCK_ENTRY_TIMEOUT_MS)
            );
            Thread.sleep(NATIVE_BLOCK_SETTLE_MS);
            assertFalse("FIFO fixture returned before cancellation", pending.isDone());

            CountDownLatch survivorReady = new CountDownLatch(1);
            Future<Bundle> survivor = executor.submit(() -> first.runtime.runScript(
                    ConcurrentExecutionSmokeTest.request("m16-fallback-survivor", "console.log('survivor'); setInterval(() => {}, 1000);"),
                    ConcurrentExecutionSmokeTest.output(survivorReady)));
            assertTrue(survivorReady.await(FIRST_OUTPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

            CountDownLatch binderDied = new CountDownLatch(1);
            first.binder.linkToDeath(binderDied::countDown, 0);
            long cancellationStartedAt = SystemClock.elapsedRealtime();
            assertTrue("cancelScript declined the active execution", first.runtime.cancelScript(EXECUTION_ID));
            Bundle stopped = pending.get(PROCESS_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED, stopped.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
            long restartElapsedMs = SystemClock.elapsedRealtime() - cancellationStartedAt;
            assertTrue(
                    "restart fallback fired before the 3s cooperative grace: " + restartElapsedMs + "ms",
                    restartElapsedMs >= 2_750L
            );
            assertTrue(
                    "restart fallback exceeded its bounded window: " + restartElapsedMs + "ms",
                    restartElapsedMs < PROCESS_DEATH_TIMEOUT_MS
            );

            assertEquals("dispatcher died with one worker", 1L, binderDied.getCount());
            assertFalse("unrelated worker stopped during fallback", survivor.isDone());
            assertTrue(first.runtime.cancelScript("m16-fallback-survivor"));
            Bundle survivorStopped = survivor.get(RUN_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotEquals(firstPid, survivorStopped.getInt(NodeJsRuntimeContract.KEY_PID));
        } finally {
            executor.shutdownNow();
            assertTrue("FIFO fixture could not be removed", !fifo.exists() || fifo.delete());
            assertTrue("entry marker could not be removed", !entered.exists() || entered.delete());
        }

        try (ServiceSession restarted = ServiceSession.connect(context)) {
            int restartedPid = restarted.runtime.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID, -1);
            assertTrue("restarted runtime PID unavailable", restartedPid > 0);
            assertNotEquals("runtime fallback did not create a fresh process", firstPid, restartedPid);

            Bundle followUp = new Bundle();
            followUp.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m8.fallback.recovered=' + (6 * 7));\n"
            );
            Bundle recovered = restarted.runtime.runScript(followUp, emptyCallback());
            assertNotNull("recovery execution returned null", recovered);
            assertTrue(
                    "fresh runtime rejected follow-up: " +
                            recovered.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    recovered.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertFalse("recovery execution was marked timed out", recovered.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT));
            assertTrue(
                    "recovery stdout missing marker",
                    recovered.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                            .contains("m8.fallback.recovered=42")
            );
            assertEquals(restartedPid, recovered.getInt(NodeJsRuntimeContract.KEY_PID, -2));
        }
    }

    private static boolean containsRemoteException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RemoteException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean waitForFile(File file, long timeoutMs) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (file.isFile()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return file.isFile();
    }

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static INodeJsRuntimeCallback emptyCallback() {
        return new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        };
    }

    private static final class ServiceSession implements AutoCloseable {
        final Context context;
        final ServiceConnection connection;
        final IBinder binder;
        final INodeJsRuntimePlugin runtime;

        private ServiceSession(
                Context context,
                ServiceConnection connection,
                IBinder binder
        ) {
            this.context = context;
            this.connection = connection;
            this.binder = binder;
            this.runtime = INodeJsRuntimePlugin.Stub.asInterface(binder);
        }

        static ServiceSession connect(Context context) throws InterruptedException {
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
                    context.bindService(
                            new Intent().setComponent(component),
                            connection,
                            Context.BIND_AUTO_CREATE
                    )
            );
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertNotNull("runtime binder unavailable", binder.get());
            return new ServiceSession(context, connection, binder.get());
        }

        @Override
        public void close() {
            context.unbindService(connection);
        }
    }
}

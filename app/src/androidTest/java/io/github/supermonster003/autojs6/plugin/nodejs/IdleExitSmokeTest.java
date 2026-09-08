package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;

import java.util.concurrent.*;

public final class IdleExitSmokeTest {
    @Test
    public void optedInIdleProcessExitsAndBoundClientReconnects() throws Exception {
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.next();
            Bundle first = run(runtime, "console.log('idle.before')", 3000L, null);
            int originalPid = pid(first);
            CountDownLatch died = new CountDownLatch(1);
            runtime.asBinder().linkToDeath(died::countDown, 0);
            SystemClock.sleep(800L);
            Bundle idle = runtime.getRuntimeInfo();
            assertEquals(3000L, idle.getLong(NodeJsRuntimeContract.KEY_IDLE_EXIT_MS));
            assertTrue(idle.getLong(NodeJsRuntimeContract.KEY_IDLE_FOR_MS) >= 600L);
            assertTrue("idle runtime did not exit", died.await(10, TimeUnit.SECONDS));
            INodeJsRuntimePlugin replacement = binding.next();
            Bundle second = run(replacement, "console.log('idle.after')", 0L, null);
            assertNotEquals(originalPid, pid(second));
            SystemClock.sleep(3500L);
            assertEquals(pid(second), replacement.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID));
            assertEquals(0L, replacement.getRuntimeInfo().getLong(NodeJsRuntimeContract.KEY_IDLE_EXIT_MS));
            InstrumentationRegistry.getInstrumentation().sendStatus(0, message("M12 idle PID " + originalPid + " -> " + pid(second)));
        }
    }

    @Test
    public void rebindAndQueuedExecutionCancelAnEarlierIdleDeadline() throws Exception {
        int originalPid;
        try (Binding first = new Binding()) {
            originalPid = pid(run(first.next(), "console.log('idle.arm')", 3000L, null));
        }
        // Recreate the Service in the still-resident process before its timer expires.
        SystemClock.sleep(300L);
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.next();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch started = new CountDownLatch(1);
            try {
                Future<Bundle> active = executor.submit(() -> run(runtime,
                        "console.log('idle.running'); setTimeout(() => console.log('idle.done'), 3500)",
                        3000L, started));
                assertTrue(started.await(20, TimeUnit.SECONDS));
                Future<Bundle> queued = executor.submit(() -> run(runtime, "console.log('idle.queued')", 0L, null));
                long deadline = SystemClock.elapsedRealtime() + 2000L;
                while (runtime.getRuntimeInfo().getInt("queuedExecutions") == 0 && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(20L);
                }
                assertEquals(1, runtime.getRuntimeInfo().getInt("queuedExecutions"));
                assertEquals(0L, runtime.getRuntimeInfo().getLong(NodeJsRuntimeContract.KEY_IDLE_FOR_MS));
                assertEquals(originalPid, pid(active.get(30, TimeUnit.SECONDS)));
                assertEquals(originalPid, pid(queued.get(30, TimeUnit.SECONDS)));
                SystemClock.sleep(3300L);
                assertEquals(originalPid, runtime.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static Bundle run(INodeJsRuntimePlugin runtime, String source, long idleMs, CountDownLatch started) throws Exception {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        if (idleMs > 0) request.putLong(NodeJsRuntimeContract.KEY_IDLE_EXIT_MS, idleMs);
        Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
            @Override public void onEvent(Bundle event) {
                if (started != null && NodeJsRuntimeContract.EVENT_STDOUT.equals(event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE))) started.countDown();
            }
        });
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        return result;
    }

    private static int pid(Bundle result) {
        int pid = result.getInt(NodeJsRuntimeContract.KEY_PID, -1);
        assertTrue(pid > 0);
        return pid;
    }

    private static Bundle message(String text) {
        Bundle result = new Bundle();
        result.putString("stream", "\n" + text + "\n");
        return result;
    }

    private static final class Binding implements ServiceConnection, AutoCloseable {
        private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        private final BlockingQueue<INodeJsRuntimePlugin> connections = new LinkedBlockingQueue<>();

        Binding() {
            ComponentName component = new ComponentName(context.getPackageName(), context.getPackageName() + ".NodeJsRuntimePluginService");
            assertTrue(context.bindService(new Intent().setComponent(component), this, Context.BIND_AUTO_CREATE));
        }

        INodeJsRuntimePlugin next() throws InterruptedException {
            INodeJsRuntimePlugin runtime = connections.poll(30, TimeUnit.SECONDS);
            assertNotNull("runtime did not connect", runtime);
            return runtime;
        }

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            connections.add(INodeJsRuntimePlugin.Stub.asInterface(service));
        }
        @Override public void onServiceDisconnected(ComponentName name) { }
        @Override public void close() { context.unbindService(this); }
    }
}

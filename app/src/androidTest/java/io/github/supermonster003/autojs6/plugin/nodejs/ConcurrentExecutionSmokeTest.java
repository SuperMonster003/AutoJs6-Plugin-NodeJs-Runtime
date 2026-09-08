package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ConcurrentExecutionSmokeTest {
    @Test public void residentScriptDoesNotDelayASecondProcess() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Binding single = new Binding("NodeJsRuntimeSlot0Service")) {
            assertSucceeded(single.runtime.prewarmRuntime(new Bundle()));
            int singlePid = single.runtime.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID);
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            long baselinePss = manager.getProcessMemoryInfo(new int[]{singlePid})[0].getTotalPss();
            try (Binding binding = new Binding()) {
                INodeJsRuntimePlugin runtime = binding.runtime;
                assertSucceeded(runtime.prewarmRuntime(new Bundle()));
                CountDownLatch started = new CountDownLatch(1);
                Future<Bundle> resident = executor.submit(() -> runtime.runScript(request("m16-resident",
                        "console.log('resident-ready'); setInterval(() => {}, 1000);"), output(started)));
                try {
                    assertTrue("resident never started", started.await(20, TimeUnit.SECONDS));
                    long submitted = SystemClock.elapsedRealtime();
                    AtomicLong firstOutput = new AtomicLong();
                    java.util.concurrent.atomic.AtomicBoolean inputAccepted = new java.util.concurrent.atomic.AtomicBoolean();
                    Bundle quick = runtime.runScript(request("m16-quick", "process.stdin.once('data', data => { console.log('quick-result=' + data.toString()); process.stdin.pause(); }); process.stdin.resume(); console.log('quick-ready');"), new INodeJsRuntimeCallback.Stub() {
                        @Override public void onEvent(Bundle event) {
                            if (NodeJsRuntimeContract.EVENT_STDOUT.equals(event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE))) {
                                firstOutput.compareAndSet(0, SystemClock.elapsedRealtime());
                                if (event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "").contains("quick-ready")) {
                                    Bundle message = new Bundle();
                                    message.putString(NodeJsRuntimeContract.KEY_MESSAGE_KIND, NodeJsRuntimeContract.MESSAGE_STDIN);
                                    message.putString(NodeJsRuntimeContract.KEY_MESSAGE_DATA, "42");
                                    try { inputAccepted.set(runtime.postMessage("m16-quick", message)); } catch (android.os.RemoteException ignored) { }
                                }
                            }
                        }
                    });
                    assertSucceeded(quick);
                    assertTrue("second-slot stdin route rejected", inputAccepted.get());
                    assertTrue(quick.getString(NodeJsRuntimeContract.KEY_STDOUT).contains("quick-result=42"));
                    assertTrue("first stdout exceeded 1 second: " + (firstOutput.get() - submitted),
                            firstOutput.get() > 0 && firstOutput.get() - submitted < 1000);
                    assertFalse("resident stopped when second script ran", resident.isDone());
                    Bundle state = runtime.getRuntimeInfo();
                    ArrayList<Bundle> slots = state.getParcelableArrayList("slots");
                    assertNotNull(slots);
                    assertEquals(2, slots.size());
                    assertEquals(0, state.getInt("queuedExecutions"));
                    assertNotEquals(slots.get(0).getInt("pid"), slots.get(1).getInt("pid"));
                    int[] pids = { slots.get(0).getInt("pid"), slots.get(1).getInt("pid"), state.getInt("dispatcherPid") };
                    long poolPss = 0;
                    for (Debug.MemoryInfo memory : manager.getProcessMemoryInfo(pids)) poolPss += memory.getTotalPss();
                    for (Bundle slot : slots) assertTrue("slot RSS missing", slot.getLong("rss") > 0);
                    assertTrue(runtime.cancelScript("m16-resident"));
                    Bundle stopped = resident.get(15, TimeUnit.SECONDS);
                    assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED, stopped.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                    assertNotEquals(stopped.getInt(NodeJsRuntimeContract.KEY_PID), quick.getInt(NodeJsRuntimeContract.KEY_PID));
                    assertNotEquals(stopped.getInt(NodeJsRuntimeContract.KEY_SLOT_ID), quick.getInt(NodeJsRuntimeContract.KEY_SLOT_ID));
                    Bundle measurement = new Bundle();
                    measurement.putString("stream", "\nm16.concurrent=PASS firstStdoutMs=" + (firstOutput.get() - submitted)
                            + " baselinePssKiB=" + baselinePss + " poolPssKiB=" + poolPss + " deltaPssKiB=" + (poolPss - baselinePss)
                            + " slotRssBytes=" + slots.get(0).getLong("rss") + "," + slots.get(1).getLong("rss") + "\n");
                    InstrumentationRegistry.getInstrumentation().sendStatus(0, measurement);
                } finally { runtime.cancelScript("m16-resident"); }
            }
        } finally { executor.shutdownNow(); }
    }

    static Bundle request(String id, String source) {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, id);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        return request;
    }
    static void assertSucceeded(Bundle result) {
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "") + ": "
                + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + "\n"
                + result.getString(NodeJsRuntimeContract.KEY_STDERR, ""), result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
    }
    static INodeJsRuntimeCallback output(CountDownLatch latch) {
        return new INodeJsRuntimeCallback.Stub() {
            @Override public void onEvent(Bundle event) {
                if (NodeJsRuntimeContract.EVENT_STDOUT.equals(event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE))) latch.countDown();
            }
        };
    }
    static final class Binding implements AutoCloseable, ServiceConnection {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final CountDownLatch connected = new CountDownLatch(1);
        INodeJsRuntimePlugin runtime;
        Binding() throws Exception { this("NodeJsRuntimePluginService"); }
        Binding(String service) throws Exception {
            assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                    context.getPackageName() + "." + service)), this, Context.BIND_AUTO_CREATE));
            assertTrue("runtime bind timeout", connected.await(30, TimeUnit.SECONDS));
        }
        @Override public void onServiceConnected(ComponentName name, IBinder binder) { runtime = INodeJsRuntimePlugin.Stub.asInterface(binder); connected.countDown(); }
        @Override public void onServiceDisconnected(ComponentName name) { }
        @Override public void close() { context.unbindService(this); }
    }
}

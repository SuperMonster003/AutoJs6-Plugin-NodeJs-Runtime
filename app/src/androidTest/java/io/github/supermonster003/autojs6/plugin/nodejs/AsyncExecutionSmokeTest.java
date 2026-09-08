package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import static io.github.supermonster003.autojs6.plugin.nodejs.ConcurrentExecutionSmokeTest.*;

import android.os.Bundle;
import android.os.SystemClock;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncExecutionSmokeTest {
    @Test public void acknowledgementsReleaseBinderWhileTwoScriptsRunAndThirdQueues() throws Exception {
        try (Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            assertEquals(1, runtime.getRuntimeInfo().getInt("maxConcurrentExecutions"));
            assertEquals(2, runtime.getRuntimeInfo().getInt("processPoolMaxConcurrentExecutions"));
            assertTrue(Arrays.asList(runtime.getRuntimeInfo().getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES))
                    .contains(NodeJsRuntimeContract.CAPABILITY_ASYNC_SCRIPT_EXECUTION));
            Completion[] callbacks = { new Completion(), new Completion(), new Completion() };
            try {
                for (int i = 0; i < 3; i++) {
                    Bundle request = request("async-" + i, "console.log('ready'); setInterval(() => {}, 1000);");
                    request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 60_000);
                    long before = SystemClock.elapsedRealtime();
                    Bundle ack = runtime.startScript(request, callbacks[i]);
                    assertTrue("start acknowledgement exceeded 1 s", SystemClock.elapsedRealtime() - before < 1000);
                    assertTrue(ack.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
                    assertEquals("async-" + i, ack.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID));
                    if (i < 2) assertTrue(callbacks[i].output.await(20, TimeUnit.SECONDS));
                }
                assertEquals(1, runtime.getRuntimeInfo().getInt("queuedExecutions"));
                assertEquals(1, callbacks[2].output.getCount());
                // Admission is already registered when startScript returns, including queued work.
                assertTrue(runtime.cancelScript("async-2"));
                Bundle queued = callbacks[2].result();
                assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED, queued.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                assertEquals(-1, queued.getInt(NodeJsRuntimeContract.KEY_SLOT_ID));
                for (int i = 0; i < 2; i++) {
                    assertTrue(runtime.cancelScript("async-" + i));
                    Bundle result = callbacks[i].result();
                    assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED, result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                    assertTrue(result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("ready"));
                    assertTrue(result.getInt(NodeJsRuntimeContract.KEY_PID) > 0);
                }
                assertSucceeded(runtime.runScript(request("async-legacy", "console.log(42)"), null));
                for (Completion callback : callbacks) assertEquals(1, callback.terminals.get());
            } finally { for (int i = 0; i < 3; i++) runtime.cancelScript("async-" + i); }
        }
    }

    @Test public void rejectedStartAndSuccessfulCompletionHaveDistinctResults() throws Exception {
        try (Binding binding = new Binding()) {
            Bundle rejected = binding.runtime.startScript(request("async-no-callback", "throw Error('must not run')"), null);
            assertFalse(rejected.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
            assertFalse(rejected.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            Completion completion = new Completion();
            assertTrue(binding.runtime.startScript(request("async-success", "console.log('answer=42')"), completion)
                    .getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
            Bundle result = completion.result();
            assertSucceeded(result);
            assertTrue(result.getString(NodeJsRuntimeContract.KEY_STDOUT).contains("answer=42"));
            assertFalse(binding.runtime.cancelScript("async-success"));
        }
    }

    static final class Completion extends INodeJsRuntimeCallback.Stub {
        final CountDownLatch output = new CountDownLatch(1);
        final LinkedBlockingQueue<Bundle> finished = new LinkedBlockingQueue<>();
        final AtomicInteger terminals = new AtomicInteger();
        @Override public void onEvent(Bundle event) {
            String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE);
            if (NodeJsRuntimeContract.EVENT_STDOUT.equals(type)) output.countDown();
            if (NodeJsRuntimeContract.EVENT_FINISHED.equals(type)) {
                terminals.incrementAndGet();
                Bundle result = event.getBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT);
                finished.add(result == null ? Bundle.EMPTY : result);
            }
        }
        Bundle result() throws Exception {
            Bundle result = finished.poll(20, TimeUnit.SECONDS);
            assertNotNull("terminal result missing", result);
            assertTrue("terminal result incomplete", result.containsKey(NodeJsRuntimeContract.KEY_SUCCEEDED));
            return result;
        }
    }
}

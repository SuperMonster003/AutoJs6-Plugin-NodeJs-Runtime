package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import static io.github.supermonster003.autojs6.plugin.nodejs.ConcurrentExecutionSmokeTest.*;

import android.os.Bundle;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ConfigurableProcessPoolSmokeTest {
    private static final String RESIDENT = "console.log('ready'); setInterval(() => {}, 1000);";

    @Test public void fourResidentsLeaveAnUnlimitedQueueWithIndependentCancellationAndTimeout() throws Exception {
        try (PoolSettingsFixture settings = new PoolSettingsFixture(4, -1); Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            Bundle info = runtime.getRuntimeInfo();
            assertEquals(4, info.getInt(NodeJsRuntimeContract.KEY_PROCESS_POOL_MAX_CONCURRENT_EXECUTIONS));
            assertEquals(Integer.MAX_VALUE, info.getInt(NodeJsRuntimeContract.KEY_QUEUE_CAPACITY));
            assertTrue(info.getBoolean(NodeJsRuntimeContract.KEY_QUEUE_UNLIMITED));
            AsyncExecutionSmokeTest.Completion[] residents = new AsyncExecutionSmokeTest.Completion[4];
            AsyncExecutionSmokeTest.Completion[] waiting = new AsyncExecutionSmokeTest.Completion[24];
            try {
                for (int i = 0; i < residents.length; i++) {
                    residents[i] = start(runtime, "pool-four-" + i, RESIDENT, 0);
                    assertTrue("resident " + i + " did not run", residents[i].output.await(30, TimeUnit.SECONDS));
                }
                Set<Integer> pids = new HashSet<>();
                for (Bundle slot : runtime.getRuntimeInfo().<Bundle>getParcelableArrayList("slots")) {
                    assertFalse(slot.getString("active").isEmpty());
                    assertTrue(slot.getInt("pid") > 0);
                    pids.add(slot.getInt("pid"));
                }
                assertEquals("each resident needs a separate process", 4, pids.size());
                for (int i = 0; i < waiting.length; i++) {
                    waiting[i] = start(runtime, "pool-wait-" + i, "console.log('queued work');", 0);
                }
                assertEquals(waiting.length, runtime.getRuntimeInfo().getInt("queuedExecutions"));
                for (AsyncExecutionSmokeTest.Completion completion : waiting) assertEquals(1, completion.output.getCount());
                AsyncExecutionSmokeTest.Completion timeout = start(runtime, "pool-deep-timeout", "throw Error('must not run')", 250);
                assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT, timeout.result().getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                assertEquals(1, timeout.output.getCount());
                assertTrue(runtime.cancelScript("pool-wait-23"));
                assertCancelled(waiting[23]);
                assertEquals(1, waiting[23].output.getCount());
                // Finish the first resident: the fifth script must now execute on that released slot.
                assertTrue(runtime.cancelScript("pool-four-0"));
                Bundle stopped = residents[0].result();
                Bundle fifth = waiting[0].result();
                assertSucceeded(fifth);
                assertEquals(stopped.getInt(NodeJsRuntimeContract.KEY_PID), fifth.getInt(NodeJsRuntimeContract.KEY_PID));
                for (int i = 1; i < 23; i++) assertSucceeded(waiting[i].result());
                for (int i = 1; i < 4; i++) assertEquals(0, residents[i].terminals.get());
            } finally {
                for (int i = 0; i < 24; i++) runtime.cancelScript("pool-wait-" + i);
                runtime.cancelScript("pool-deep-timeout");
                for (int i = 0; i < 4; i++) {
                    if (runtime.cancelScript("pool-four-" + i)) assertCancelled(residents[i]);
                }
            }
        }
    }

    @Test public void growingToEightStartsQueuedJobsAndShrinkingDrainsWithoutStoppingThem() throws Exception {
        try (PoolSettingsFixture settings = new PoolSettingsFixture(1, -1); Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            AsyncExecutionSmokeTest.Completion[] residents = new AsyncExecutionSmokeTest.Completion[8];
            try {
                for (int i = 0; i < residents.length; i++) residents[i] = start(runtime, "pool-grow-" + i, RESIDENT, 0);
                assertTrue(residents[0].output.await(30, TimeUnit.SECONDS));
                assertEquals(7, runtime.getRuntimeInfo().getInt("queuedExecutions"));
                settings.set(8, -1);
                Set<Integer> pids = new HashSet<>();
                for (int i = 0; i < residents.length; i++) {
                    assertTrue("grown slot " + i + " was starved", residents[i].output.await(30, TimeUnit.SECONDS));
                    assertEquals(0, residents[i].terminals.get());
                }
                for (Bundle slot : runtime.getRuntimeInfo().<Bundle>getParcelableArrayList("slots")) pids.add(slot.getInt("pid"));
                assertEquals(8, pids.size());
                assertFalse(pids.contains(-1));
                settings.set(1, -1);
                assertEquals(1, runtime.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PROCESS_POOL_MAX_CONCURRENT_EXECUTIONS));
                AsyncExecutionSmokeTest.Completion next = start(runtime, "pool-after-shrink", "console.log('after shrink')", 0);
                for (int i = 0; i < 7; i++) {
                    assertTrue(runtime.cancelScript("pool-grow-" + i));
                    assertCancelled(residents[i]);
                    assertEquals("shrinking admitted work before draining", 1, next.output.getCount());
                }
                assertEquals(0, residents[7].terminals.get());
                assertTrue(runtime.cancelScript("pool-grow-7"));
                assertCancelled(residents[7]);
                Bundle result = next.result();
                assertSucceeded(result);
                assertEquals(0, result.getInt(NodeJsRuntimeContract.KEY_SLOT_ID));
                assertEquals(1, runtime.getRuntimeInfo().<Bundle>getParcelableArrayList("slots").size());
            } finally {
                runtime.cancelScript("pool-after-shrink");
                for (int i = 0; i < 8; i++) if (runtime.cancelScript("pool-grow-" + i)) assertCancelled(residents[i]);
            }
        }
    }

    @Test public void loweringQueueLimitKeepsAcceptedWorkAndZeroRejectsOnlyNewWaiters() throws Exception {
        try (PoolSettingsFixture settings = new PoolSettingsFixture(1, 2); Binding binding = new Binding()) {
            INodeJsRuntimePlugin runtime = binding.runtime;
            AsyncExecutionSmokeTest.Completion active = start(runtime, "pool-limit-active", RESIDENT, 0);
            try {
                assertTrue(active.output.await(30, TimeUnit.SECONDS));
                AsyncExecutionSmokeTest.Completion first = start(runtime, "pool-limit-first", "console.log(1)", 0);
                AsyncExecutionSmokeTest.Completion second = start(runtime, "pool-limit-second", "console.log(2)", 0);
                settings.set(1, 0);
                Bundle info = runtime.getRuntimeInfo();
                assertEquals(0, info.getInt(NodeJsRuntimeContract.KEY_QUEUE_CAPACITY));
                assertFalse(info.getBoolean(NodeJsRuntimeContract.KEY_QUEUE_UNLIMITED));
                assertEquals(2, info.getInt("queuedExecutions"));
                Bundle rejected = runtime.startScript(request("pool-limit-overflow", "throw Error('must not run')"), new AsyncExecutionSmokeTest.Completion());
                assertFalse(rejected.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
                assertEquals(NodeJsRuntimePluginService.ERROR_BUSY, rejected.getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
                assertTrue(runtime.cancelScript("pool-limit-active"));
                assertCancelled(active);
                assertSucceeded(first.result());
                assertSucceeded(second.result());
                assertSucceeded(runtime.runScript(request("pool-limit-idle", "console.log('idle accepted')"), null));
            } finally {
                for (String suffix : new String[]{"active", "first", "second"}) runtime.cancelScript("pool-limit-" + suffix);
            }
        }
    }

    @Test public void settingsRejectOutOfRangeWithoutChangingSavedValues() {
        try (PoolSettingsFixture settings = new PoolSettingsFixture(2, -1)) {
            for (int[] invalid : new int[][]{{0, -1}, {9, -1}, {2, -2}}) {
                try { settings.set(invalid[0], invalid[1]); fail("invalid settings accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            Bundle saved = PoolSettingsFixture.read(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext());
            assertEquals(2, saved.getInt(NodeRuntimeSettings.SLOTS));
            assertEquals(-1, saved.getInt(NodeRuntimeSettings.QUEUE));
        }
    }

    private static AsyncExecutionSmokeTest.Completion start(INodeJsRuntimePlugin runtime, String id, String source, long timeout) throws Exception {
        AsyncExecutionSmokeTest.Completion completion = new AsyncExecutionSmokeTest.Completion();
        Bundle request = request(id, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeout);
        Bundle ack = runtime.startScript(request, completion);
        assertTrue(ack.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), ack.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED));
        return completion;
    }

    private static void assertCancelled(AsyncExecutionSmokeTest.Completion completion) throws Exception {
        assertNotNull(completion);
        assertEquals(NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED, completion.result().getString(NodeJsRuntimeContract.KEY_ERROR_CODE));
        assertEquals(1, completion.terminals.get());
    }
}

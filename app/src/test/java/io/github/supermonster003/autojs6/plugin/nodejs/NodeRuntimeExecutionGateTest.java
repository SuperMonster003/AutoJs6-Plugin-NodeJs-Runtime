package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NodeRuntimeExecutionGateTest {

    @Test
    public void prewarmTryAcquireIsRejectedWhileBusyWithoutQueueing() {
        AtomicLong clock = new AtomicLong(100L);
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(clock::get);

        NodeRuntimeExecutionGate.Lease first = gate.tryAcquire("first");
        assertNotNull(first);
        assertNull(gate.tryAcquire("second"));
        assertEquals(0, gate.queuedCount());

        clock.set(175L);
        NodeRuntimeExecutionGate.Snapshot snapshot = gate.snapshot();
        assertNotNull(snapshot);
        assertEquals("first", snapshot.executionId);
        assertEquals(75L, snapshot.activeForMs);
        assertFalse(snapshot.cancellationRequested);
    }

    @Test
    public void matchingCancellationClosesGateUntilProcessRestart() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 10L);
        NodeRuntimeExecutionGate.Lease lease = gate.tryAcquire("active");

        assertFalse(gate.requestCancellation("different"));
        assertFalse(gate.isClosed());
        assertTrue(gate.requestCancellation("active"));
        assertTrue(gate.requestCancellation("active"));
        assertTrue(gate.isClosed());
        assertTrue(gate.snapshot().cancellationRequested);

        assertTrue(gate.release(lease));
        assertNull(gate.snapshot());
        assertNull(gate.tryAcquire("after-cancel"));
    }

    @Test
    public void normalReleaseAllowsNextExecutionAndRejectsStaleLease() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 0L);
        NodeRuntimeExecutionGate.Lease first = gate.tryAcquire("first");

        assertTrue(gate.release(first));
        NodeRuntimeExecutionGate.Lease second = gate.tryAcquire("second");
        assertNotNull(second);
        assertFalse(gate.release(first));
        assertTrue(gate.release(second));
        assertNull(gate.snapshot());
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankExecutionIdIsRejected() {
        new NodeRuntimeExecutionGate(() -> 0L).tryAcquire("  ");
    }

    @Test
    public void cooperativeCancellationLeavesGateOpenForNextExecution() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 10L);
        NodeRuntimeExecutionGate.Lease lease = gate.tryAcquire("active");

        assertFalse(gate.requestCooperativeCancellation("different"));
        assertTrue(gate.requestCooperativeCancellation("active"));
        assertTrue(lease.cancellationRequested());
        assertFalse(gate.isClosed());
        assertTrue(gate.snapshot().cancellationRequested);

        assertTrue(gate.release(lease));
        // Unlike the restart path, the runtime process survives and the gate
        // admits the next script.
        assertNotNull(gate.tryAcquire("after-cooperative-cancel"));
    }

    @Test
    public void timeoutWinsOnceAndLeavesGateOpenForNextExecution() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 10L);
        NodeRuntimeExecutionGate.Lease lease = gate.tryAcquire("active");

        assertFalse(gate.requestTimeout("different"));
        assertTrue(gate.requestTimeout("active"));
        assertFalse("a deadline may only win once", gate.requestTimeout("active"));
        assertTrue(lease.cancellationRequested());
        assertTrue(lease.timedOut());
        assertFalse(lease.cancelled());
        assertFalse("manual cancellation must not overwrite timeout", gate.requestCooperativeCancellation("active"));
        assertFalse(gate.isClosed());

        assertTrue(gate.release(lease));
        assertNotNull("cooperative timeout must preserve the process", gate.tryAcquire("after-timeout"));
    }

    @Test
    public void completedLeaseRejectsLateCancellationAndTimeout() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 10L);
        NodeRuntimeExecutionGate.Lease lease = gate.tryAcquire("active");

        assertEquals(NodeRuntimeExecutionGate.Lease.State.COMPLETED, lease.markCompleted());
        assertFalse(gate.requestTimeout("active"));
        assertFalse(gate.requestCooperativeCancellation("active"));
        assertFalse(gate.requestCancellation("active"));
        assertFalse(gate.isClosed());

        assertTrue(gate.release(lease));
        assertNotNull(gate.tryAcquire("after-completion"));
    }

    @Test
    public void manualCancellationCannotBeReclassifiedAsTimeout() {
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(() -> 10L);
        NodeRuntimeExecutionGate.Lease lease = gate.tryAcquire("active");

        assertTrue(gate.requestCooperativeCancellation("active"));
        assertFalse(gate.requestTimeout("active"));
        assertTrue(lease.cancelled());
        assertFalse(lease.timedOut());
        assertEquals(NodeRuntimeExecutionGate.Lease.State.CANCELLED, lease.markCompleted());
    }

    @Test
    public void admissionDeadlineSaturatesInsteadOfOverflowing() {
        assertEquals(
                Long.MAX_VALUE,
                NodeRuntimeExecutionGate.saturatedDeadline(Long.MAX_VALUE - 5L, 10L)
        );
        assertEquals(125L, NodeRuntimeExecutionGate.saturatedDeadline(100L, 25L));
        assertEquals(100L, NodeRuntimeExecutionGate.saturatedDeadline(100L, -1L));
    }

    @Test
    public void remainingWallClockBudgetIncludesTimeBeforeAdmission() {
        assertEquals(
                750L,
                NodeJsRuntimePluginService.remainingTimeoutBudgetMs(1_000L, 5_000L, 5_250L)
        );
        assertEquals(
                0L,
                NodeJsRuntimePluginService.remainingTimeoutBudgetMs(1_000L, 5_000L, 6_000L)
        );
        assertEquals(
                Long.MAX_VALUE,
                NodeJsRuntimePluginService.remainingTimeoutBudgetMs(0L, 5_000L, Long.MAX_VALUE)
        );
    }

    @Test
    public void queuedExecutionsAreAdmittedInFifoOrderAsSlotsFree() throws Exception {
        NodeRuntimeExecutionGate gate =
                new NodeRuntimeExecutionGate(NodeRuntimeExecutionGateTest::wallClock);
        NodeRuntimeExecutionGate.Lease first = gate.tryAcquire("first");
        assertNotNull(first);

        List<String> admissionOrder = new ArrayList<>();
        CountDownLatch allDone = new CountDownLatch(2);
        Thread secondCaller = queueCaller(gate, "second", admissionOrder, allDone);
        secondCaller.start();
        waitForQueueSize(gate, 1);
        Thread thirdCaller = queueCaller(gate, "third", admissionOrder, allDone);
        thirdCaller.start();
        waitForQueueSize(gate, 2);

        gate.release(first);
        assertTrue("queued executions never completed", allDone.await(10, TimeUnit.SECONDS));
        secondCaller.join(1_000L);
        thirdCaller.join(1_000L);
        assertEquals(List.of("second", "third"), admissionOrder);
        assertNull(gate.snapshot());
    }

    @Test
    public void queueRejectsBeyondCapacityImmediately() throws Exception {
        NodeRuntimeExecutionGate gate =
                new NodeRuntimeExecutionGate(NodeRuntimeExecutionGateTest::wallClock);
        NodeRuntimeExecutionGate.Lease active = gate.tryAcquire("active");
        assertNotNull(active);

        List<Thread> waiters = new ArrayList<>();
        for (int index = 0; index < NodeRuntimeExecutionGate.QUEUE_CAPACITY; index++) {
            Thread waiter = new Thread(() -> gate.acquire("queued-" + Thread.currentThread().getId(), 60_000L));
            waiter.start();
            waiters.add(waiter);
        }
        waitForQueueSize(gate, NodeRuntimeExecutionGate.QUEUE_CAPACITY);

        NodeRuntimeExecutionGate.Admission overflow = gate.acquire("overflow", 60_000L);
        assertEquals(NodeRuntimeExecutionGate.AdmissionOutcome.QUEUE_FULL, overflow.outcome);
        assertNull(overflow.lease);

        gate.requestCancellation("active");
        gate.release(active);
        for (Thread waiter : waiters) {
            waiter.join(5_000L);
            assertFalse("waiter did not finish", waiter.isAlive());
        }
    }

    @Test
    public void queuedWaiterTimesOutWithItsBudget() {
        NodeRuntimeExecutionGate gate =
                new NodeRuntimeExecutionGate(NodeRuntimeExecutionGateTest::wallClock);
        NodeRuntimeExecutionGate.Lease active = gate.tryAcquire("active");
        assertNotNull(active);

        long before = wallClock();
        NodeRuntimeExecutionGate.Admission admission = gate.acquire("waiter", 120L);
        assertEquals(NodeRuntimeExecutionGate.AdmissionOutcome.WAIT_TIMEOUT, admission.outcome);
        assertTrue("waited less than the budget", wallClock() - before >= 100L);
        assertEquals(0, gate.queuedCount());
    }

    @Test
    public void cancelQueuedRemovesWaiterWithoutTouchingActive() throws Exception {
        NodeRuntimeExecutionGate gate =
                new NodeRuntimeExecutionGate(NodeRuntimeExecutionGateTest::wallClock);
        NodeRuntimeExecutionGate.Lease active = gate.tryAcquire("active");
        assertNotNull(active);

        List<NodeRuntimeExecutionGate.Admission> results = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            results.add(gate.acquire("queued", 60_000L));
            done.countDown();
        });
        waiter.start();
        waitForQueueSize(gate, 1);

        assertFalse(gate.cancelQueued("missing"));
        assertTrue(gate.cancelQueued("queued"));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(
                NodeRuntimeExecutionGate.AdmissionOutcome.CANCELLED_WHILE_QUEUED,
                results.get(0).outcome
        );
        assertFalse(gate.snapshot().cancellationRequested);
        gate.release(active);
    }

    private static Thread queueCaller(
            NodeRuntimeExecutionGate gate,
            String executionId,
            List<String> admissionOrder,
            CountDownLatch done
    ) {
        return new Thread(() -> {
            NodeRuntimeExecutionGate.Admission admission = gate.acquire(executionId, 60_000L);
            if (admission.outcome == NodeRuntimeExecutionGate.AdmissionOutcome.ADMITTED) {
                synchronized (admissionOrder) {
                    admissionOrder.add(executionId);
                }
                gate.release(admission.lease);
            }
            done.countDown();
        });
    }

    private static void waitForQueueSize(NodeRuntimeExecutionGate gate, int expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (gate.queuedCount() != expected) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "queue never reached size " + expected + "; current=" + gate.queuedCount()
                );
            }
            Thread.sleep(10L);
        }
    }

    private static long wallClock() {
        return System.currentTimeMillis();
    }
}

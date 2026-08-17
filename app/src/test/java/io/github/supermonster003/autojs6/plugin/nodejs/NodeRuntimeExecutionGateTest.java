package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NodeRuntimeExecutionGateTest {

    @Test
    public void secondExecutionIsRejectedWithoutQueueing() {
        AtomicLong clock = new AtomicLong(100L);
        NodeRuntimeExecutionGate gate = new NodeRuntimeExecutionGate(clock::get);

        NodeRuntimeExecutionGate.Lease first = gate.tryAcquire("first");
        assertNotNull(first);
        assertNull(gate.tryAcquire("second"));

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
}

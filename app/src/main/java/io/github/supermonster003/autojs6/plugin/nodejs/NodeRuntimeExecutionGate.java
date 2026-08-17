package io.github.supermonster003.autojs6.plugin.nodejs;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the service-process execution admission state. The native runtime is
 * process-global and strictly serial (one lifecycle mutex), so admission is
 * one active execution plus a bounded FIFO wait queue (M2.3): concurrent
 * runScript calls block on their binder threads until the slot frees instead
 * of bouncing with BUSY. Every admitted execution still receives a fresh
 * isolate and Environment from the native bridge.
 */
final class NodeRuntimeExecutionGate {

    static final int QUEUE_CAPACITY = 3;

    interface ElapsedClock {
        long nowMs();
    }

    static final class Lease {
        final String executionId;
        volatile long startedAtMs;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

        private Lease(String executionId, long startedAtMs) {
            this.executionId = executionId;
            this.startedAtMs = startedAtMs;
        }

        boolean cancellationRequested() {
            return cancellationRequested.get();
        }

        private void requestCancellation() {
            cancellationRequested.set(true);
        }
    }

    /** Terminal admission outcomes surfaced to the Binder caller. */
    enum AdmissionOutcome {
        ADMITTED,
        QUEUE_FULL,
        WAIT_TIMEOUT,
        CANCELLED_WHILE_QUEUED,
        CLOSED
    }

    static final class Admission {
        final AdmissionOutcome outcome;
        final Lease lease;
        final long waitedMs;

        private Admission(AdmissionOutcome outcome, Lease lease, long waitedMs) {
            this.outcome = outcome;
            this.lease = lease;
            this.waitedMs = waitedMs;
        }
    }

    static final class Snapshot {
        final String executionId;
        final long activeForMs;
        final boolean cancellationRequested;
        final int queuedCount;

        private Snapshot(
                String executionId,
                long activeForMs,
                boolean cancellationRequested,
                int queuedCount
        ) {
            this.executionId = executionId;
            this.activeForMs = activeForMs;
            this.cancellationRequested = cancellationRequested;
            this.queuedCount = queuedCount;
        }
    }

    private static final class Waiter {
        final String executionId;
        Lease admittedLease;
        boolean cancelled;

        private Waiter(String executionId) {
            this.executionId = executionId;
        }
    }

    private final ElapsedClock clock;
    private final Object monitor = new Object();
    private final ArrayDeque<Waiter> queue = new ArrayDeque<>();
    private Lease active;
    private boolean closed;

    NodeRuntimeExecutionGate(ElapsedClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Non-blocking admission used by prewarm: succeeds only when the gate is
     * fully idle. Never jumps ahead of queued executions.
     */
    Lease tryAcquire(String executionId) {
        requireExecutionId(executionId);
        synchronized (monitor) {
            if (closed || active != null || !queue.isEmpty()) {
                return null;
            }
            active = new Lease(executionId, clock.nowMs());
            return active;
        }
    }

    /**
     * Blocking admission: takes the slot immediately when idle, otherwise
     * waits in FIFO order for up to {@code maxWaitMs}. The queue holds at
     * most {@link #QUEUE_CAPACITY} waiters; further callers fail fast.
     */
    Admission acquire(String executionId, long maxWaitMs) {
        requireExecutionId(executionId);
        long enqueuedAt = clock.nowMs();
        Waiter waiter;
        synchronized (monitor) {
            if (closed) {
                return new Admission(AdmissionOutcome.CLOSED, null, 0L);
            }
            if (active == null && queue.isEmpty()) {
                active = new Lease(executionId, enqueuedAt);
                return new Admission(AdmissionOutcome.ADMITTED, active, 0L);
            }
            if (queue.size() >= QUEUE_CAPACITY) {
                return new Admission(AdmissionOutcome.QUEUE_FULL, null, 0L);
            }
            waiter = new Waiter(executionId);
            queue.addLast(waiter);
            long deadline = enqueuedAt + Math.max(0L, maxWaitMs);
            while (true) {
                if (waiter.admittedLease != null) {
                    return new Admission(
                            AdmissionOutcome.ADMITTED,
                            waiter.admittedLease,
                            Math.max(0L, clock.nowMs() - enqueuedAt)
                    );
                }
                if (waiter.cancelled) {
                    return new Admission(
                            AdmissionOutcome.CANCELLED_WHILE_QUEUED,
                            null,
                            Math.max(0L, clock.nowMs() - enqueuedAt)
                    );
                }
                if (closed) {
                    queue.remove(waiter);
                    return new Admission(AdmissionOutcome.CLOSED, null, Math.max(0L, clock.nowMs() - enqueuedAt));
                }
                long remaining = deadline - clock.nowMs();
                if (remaining <= 0L) {
                    queue.remove(waiter);
                    return new Admission(
                            AdmissionOutcome.WAIT_TIMEOUT,
                            null,
                            Math.max(0L, clock.nowMs() - enqueuedAt)
                    );
                }
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    queue.remove(waiter);
                    return new Admission(
                            AdmissionOutcome.WAIT_TIMEOUT,
                            null,
                            Math.max(0L, clock.nowMs() - enqueuedAt)
                    );
                }
            }
        }
    }

    boolean release(Lease lease) {
        if (lease == null) {
            return false;
        }
        synchronized (monitor) {
            if (active != lease) {
                return false;
            }
            active = null;
            if (!closed) {
                Waiter next = queue.pollFirst();
                if (next != null) {
                    next.admittedLease = new Lease(next.executionId, clock.nowMs());
                    active = next.admittedLease;
                }
            }
            monitor.notifyAll();
            return true;
        }
    }

    /**
     * Restart-path cancellation: closes the gate permanently (until the
     * runtime process restarts) and fails every queued waiter.
     */
    boolean requestCancellation(String executionId) {
        synchronized (monitor) {
            if (active == null || executionId == null || !active.executionId.equals(executionId)) {
                return false;
            }
            active.requestCancellation();
            closed = true;
            monitor.notifyAll();
            return true;
        }
    }

    /**
     * Marks the active lease as cancelled without closing the gate: the
     * execution ends via {@code node::Stop} and the runtime process stays up,
     * so queued executions still run afterwards. Returns false when the id
     * does not match the active execution.
     */
    boolean requestCooperativeCancellation(String executionId) {
        synchronized (monitor) {
            if (active == null || executionId == null || !active.executionId.equals(executionId)) {
                return false;
            }
            active.requestCancellation();
            return true;
        }
    }

    /** Removes a queued (not yet admitted) execution; its waiter returns cancelled. */
    boolean cancelQueued(String executionId) {
        if (executionId == null) {
            return false;
        }
        synchronized (monitor) {
            for (Waiter waiter : queue) {
                if (waiter.executionId.equals(executionId) && !waiter.cancelled) {
                    waiter.cancelled = true;
                    queue.remove(waiter);
                    monitor.notifyAll();
                    return true;
                }
            }
            return false;
        }
    }

    Snapshot snapshot() {
        synchronized (monitor) {
            if (active == null) {
                return null;
            }
            return new Snapshot(
                    active.executionId,
                    Math.max(0L, clock.nowMs() - active.startedAtMs),
                    active.cancellationRequested(),
                    queue.size()
            );
        }
    }

    int queuedCount() {
        synchronized (monitor) {
            return queue.size();
        }
    }

    boolean isClosed() {
        synchronized (monitor) {
            return closed;
        }
    }

    private static void requireExecutionId(String executionId) {
        if (executionId == null || executionId.trim().isEmpty()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
    }
}

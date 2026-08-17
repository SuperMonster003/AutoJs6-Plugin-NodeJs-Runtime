package io.github.supermonster003.autojs6.plugin.nodejs;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the service-process single-execution admission state without making Binder callers wait
 * behind a monitor. The runtime itself is process-global, while every admitted execution still
 * receives a fresh isolate and Environment from the native bridge.
 */
final class NodeRuntimeExecutionGate {

    private static final State IDLE = new State(null, false);
    private static final State CLOSED = new State(null, true);

    interface ElapsedClock {
        long nowMs();
    }

    static final class Lease {
        final String executionId;
        final long startedAtMs;
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

    static final class Snapshot {
        final String executionId;
        final long activeForMs;
        final boolean cancellationRequested;

        private Snapshot(String executionId, long activeForMs, boolean cancellationRequested) {
            this.executionId = executionId;
            this.activeForMs = activeForMs;
            this.cancellationRequested = cancellationRequested;
        }
    }

    private static final class State {
        final Lease lease;
        final boolean closed;

        private State(Lease lease, boolean closed) {
            this.lease = lease;
            this.closed = closed;
        }
    }

    private final ElapsedClock clock;
    private final AtomicReference<State> state = new AtomicReference<>(IDLE);

    NodeRuntimeExecutionGate(ElapsedClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Lease tryAcquire(String executionId) {
        if (executionId == null || executionId.trim().isEmpty()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        Lease candidate = new Lease(executionId, clock.nowMs());
        return state.compareAndSet(IDLE, new State(candidate, false)) ? candidate : null;
    }

    boolean release(Lease lease) {
        if (lease == null) {
            return false;
        }
        while (true) {
            State current = state.get();
            if (current.lease != lease) {
                return false;
            }
            State next = current.closed ? CLOSED : IDLE;
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    boolean requestCancellation(String executionId) {
        while (true) {
            State current = state.get();
            Lease lease = current.lease;
            if (lease == null || executionId == null || !lease.executionId.equals(executionId)) {
                return false;
            }
            if (current.closed) {
                return true;
            }
            if (state.compareAndSet(current, new State(lease, true))) {
                lease.requestCancellation();
                return true;
            }
        }
    }


    /**
     * Marks the active lease as cancelled without closing the gate: the
     * execution ends via {@code node::Stop} and the runtime process stays up,
     * so the next admission proceeds normally after release. Returns false
     * when the id does not match the active execution.
     */
    boolean requestCooperativeCancellation(String executionId) {
        Lease lease = state.get().lease;
        if (lease == null || executionId == null || !lease.executionId.equals(executionId)) {
            return false;
        }
        lease.requestCancellation();
        return true;
    }

    Snapshot snapshot() {
        Lease lease = state.get().lease;
        if (lease == null) {
            return null;
        }
        return new Snapshot(
                lease.executionId,
                Math.max(0L, clock.nowMs() - lease.startedAtMs),
                lease.cancellationRequested()
        );
    }

    boolean isClosed() {
        return state.get().closed;
    }
}

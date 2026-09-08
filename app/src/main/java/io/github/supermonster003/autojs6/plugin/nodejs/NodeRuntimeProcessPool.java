package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.*;

/** Two single-runtime processes, with one admission queue shared by all callers. */
final class NodeRuntimeProcessPool implements AutoCloseable {
    private final NodeJsRuntimePluginService service;
    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Slot[] slots = { new Slot(0, NodeJsRuntimeSlot0Service.class), new Slot(1, NodeJsRuntimeSlot1Service.class) };
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<String, Job> jobs = new HashMap<>();
    private final ExecutorService executions = Executors.newFixedThreadPool(5,
            task -> new Thread(task, "Node-pool-execution"));
    private volatile boolean closed;

    NodeRuntimeProcessPool(NodeJsRuntimePluginService service) {
        this.service = service;
        for (Slot slot : slots) bind(slot);
    }

    private final class Slot implements ServiceConnection {
        final int id;
        final Class<?> component;
        INodeJsRuntimePlugin remote;
        boolean bound;
        boolean warming;
        Job job;
        Slot(int id, Class<?> component) { this.id = id; this.component = component; }
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (lock) { remote = INodeJsRuntimePlugin.Stub.asInterface(binder); lock.notifyAll(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) { remote = null; lock.notifyAll(); }
        }
        @Override public void onBindingDied(ComponentName name) {
            synchronized (lock) {
                remote = null;
                if (bound) service.unbindService(this);
                bound = false;
                if (!closed) bind(this);
                lock.notifyAll();
            }
        }
        @Override public void onNullBinding(ComponentName name) { onBindingDied(name); }
    }

    private static final class Job {
        final String id;
        final Bundle request;
        final long startedAt = SystemClock.elapsedRealtime();
        final long timeout;
        volatile boolean cancelled;
        int pid;
        Slot slot;
        Job(Bundle request) {
            this.request = request;
            id = nonBlank(request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID), "plugin-" + UUID.randomUUID());
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, id);
            timeout = request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L);
        }
        long remaining() {
            return NodeJsRuntimePluginService.remainingTimeoutBudgetMs(timeout > 0 ? timeout : 600_000L,
                    startedAt, SystemClock.elapsedRealtime());
        }
    }

    private static final class AdmissionFailure extends Exception {
        final Bundle result;
        AdmissionFailure(Bundle result) { this.result = result; }
    }

    private void bind(Slot slot) {
        main.post(() -> {
            synchronized (lock) {
                if (closed || slot.bound) return;
                slot.bound = service.bindService(new Intent(service, slot.component), slot, Context.BIND_AUTO_CREATE);
                lock.notifyAll();
            }
        });
    }

    private INodeJsRuntimePlugin awaitRemote(Slot slot, Job job) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        synchronized (lock) {
            while (!closed && slot.remote == null && (job == null || !job.cancelled)) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (job != null) remaining = Math.min(remaining, job.remaining());
                if (remaining <= 0) throw new IllegalStateException("Timed out connecting to Node runtime slot " + slot.id);
                bind(slot);
                lock.wait(remaining);
            }
            if (closed || (job != null && job.cancelled)) throw new InterruptedException("Node execution cancelled.");
            return slot.remote;
        }
    }

    private Slot freeSlot() {
        for (Slot slot : slots) if (slot.job == null && !slot.warming && slot.remote != null) return slot;
        for (Slot slot : slots) if (slot.job == null && !slot.warming) return slot;
        return null;
    }

    Bundle runScript(Bundle original, INodeJsRuntimeCallback callback) {
        Job job = new Job(original == null ? new Bundle() : new Bundle(original));
        try {
            register(job);
        } catch (AdmissionFailure failure) {
            closeWorkspaceDescriptors(job.request);
            return finish(job, callback, failure.result);
        }
        return runRegistered(job, callback);
    }

    Bundle startScript(Bundle original, INodeJsRuntimeCallback callback) {
        // Keep the unmarshalled Binder descriptors alive until runRegistered closes them.
        Job job = new Job(original == null ? new Bundle() : new Bundle(original));
        try {
            if (callback == null) throw new AdmissionFailure(service.bundles.failureBundle(job.request,
                    job.startedAt, "startScript requires a completion callback.", null,
                    "ERR_AUTOJS6_NODE_PLUGIN_INVALID_REQUEST"));
            register(job); // Visible to cancelScript before the acknowledgement can reach the caller.
            try {
                executions.execute(() -> runRegistered(job, callback));
            } catch (RejectedExecutionException error) {
                unregister(job);
                throw new AdmissionFailure(service.bundles.failureBundle(job.request, job.startedAt,
                        "Node process pool is closing.", error, NodeJsRuntimePluginService.ERROR_UNAVAILABLE));
            }
            Bundle ack = new Bundle();
            ack.putBoolean(NodeJsRuntimeContract.KEY_ACCEPTED, true);
            ack.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, job.id);
            return ack;
        } catch (AdmissionFailure failure) {
            closeWorkspaceDescriptors(job.request);
            failure.result.putBoolean(NodeJsRuntimeContract.KEY_ACCEPTED, false);
            failure.result.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, job.id);
            return failure.result;
        }
    }

    private void register(Job job) throws AdmissionFailure {
        Bundle failure = service.validateRequestContract(job.request, job.startedAt);
        if (failure != null) throw new AdmissionFailure(failure);
        synchronized (lock) {
            if (closed) throw new AdmissionFailure(service.bundles.failureBundle(job.request, job.startedAt,
                    "Node process pool is closing.", null, NodeJsRuntimePluginService.ERROR_UNAVAILABLE));
            Slot available = queue.isEmpty() ? freeSlot() : null;
            if (jobs.containsKey(job.id) || (available == null && queue.size() >= NodeRuntimeExecutionGate.QUEUE_CAPACITY)) {
                throw new AdmissionFailure(service.bundles.busyFailureBundle(job.request, job.startedAt));
            }
            jobs.put(job.id, job);
            if (available == null) queue.addLast(job);
            else { available.job = job; job.slot = available; }
        }
    }

    private void unregister(Job job) {
        synchronized (lock) {
            queue.remove(job);
            jobs.remove(job.id);
            if (job.slot != null) job.slot.job = null;
            lock.notifyAll();
        }
    }

    private Bundle runRegistered(Job job, INodeJsRuntimeCallback callback) {
        Bundle result;
        try {
            synchronized (lock) {
                while (job.slot == null) {
                    if (closed || job.cancelled) throw new InterruptedException("Node execution cancelled while queued.");
                    if (job.remaining() <= 0) throw new AdmissionFailure(timeout(job, "queued"));
                    Slot available = freeSlot();
                    if (queue.peekFirst() == job && available != null) {
                        queue.removeFirst();
                        available.job = job;
                        job.slot = available;
                        lock.notifyAll();
                        break;
                    }
                    lock.wait(job.remaining());
                }
            }
            INodeJsRuntimePlugin remote = awaitRemote(job.slot, job);
            job.pid = remote.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID);
            if (job.timeout > 0) {
                long remaining = job.remaining();
                if (remaining <= 0) throw new AdmissionFailure(timeout(job, "dispatch"));
                job.request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, remaining);
            }
            if (job.cancelled) throw new InterruptedException("Node execution cancelled before dispatch.");
            job.request.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, job.slot.id);
            CompletableFuture<Bundle> completion = new CompletableFuture<>();
            IBinder.DeathRecipient death = () -> completion.completeExceptionally(new android.os.DeadObjectException());
            remote.asBinder().linkToDeath(death, 0);
            try {
                Bundle ack = remote.startScript(job.request, new INodeJsRuntimeCallback.Stub() {
                    @Override public void onEvent(Bundle event) {
                        String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE);
                        if (job.cancelled && NodeJsRuntimeContract.EVENT_STARTED.equals(type)) {
                            try { remote.cancelScript(job.id); } catch (RemoteException ignored) { }
                        }
                        // Deliver one terminal event after cancellation/timeout normalization and FD cleanup.
                        if (NodeJsRuntimeContract.EVENT_FINISHED.equals(type)) {
                            Bundle finished = event.getBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT);
                            if (job.id.equals(event.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID)) && finished != null) {
                                completion.complete(finished);
                            } else completion.completeExceptionally(new RemoteException("Invalid Node slot completion."));
                            return;
                        }
                        Bundle forwarded = new Bundle(event);
                        forwarded.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, job.id);
                        forwarded.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, job.slot.id);
                        send(callback, forwarded);
                    }
                });
                if (ack == null || !job.id.equals(ack.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID))) {
                    throw new RemoteException("Invalid Node slot acknowledgement.");
                }
                result = !ack.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED) ? ack : job.timeout > 0
                        ? completion.get(job.remaining() + 5_000L, TimeUnit.MILLISECONDS) : completion.get();
            } finally { remote.asBinder().unlinkToDeath(death, 0); }
            if (job.timeout > 0 && result.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT)) {
                result.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, job.timeout);
            }
        } catch (AdmissionFailure failure) {
            result = failure.result;
        } catch (InterruptedException error) {
            // Pool cancellation uses the flag, so only restore interruption for an interrupted waiter.
            if (!closed && !job.cancelled) Thread.currentThread().interrupt();
            result = service.bundles.failureBundle(job.request, job.startedAt, error.getMessage(), null,
                    NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED);
        } catch (Exception error) {
            result = job.cancelled ? service.bundles.failureBundle(job.request, job.startedAt,
                    "Node execution was cancelled and its runtime slot restarted.", null, NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED) :
                    job.timeout > 0 && job.remaining() <= 0 ? timeout(job, "dispatch") :
                    service.bundles.failureBundle(job.request, job.startedAt, "Node runtime slot failed: " + error.getMessage(),
                            error, NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        } finally {
            closeWorkspaceDescriptors(job.request);
            unregister(job);
        }
        return finish(job, callback, result);
    }

    private Bundle timeout(Job job, String phase) {
        return service.bundles.timeoutFailureBundle(job.request, job.startedAt,
                job.timeout > 0 ? job.timeout : 600_000L, phase);
    }

    private Bundle finish(Job job, INodeJsRuntimeCallback callback, Bundle result) {
        result.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, job.slot == null ? -1 : job.slot.id);
        if (job.pid > 0) result.putInt(NodeJsRuntimeContract.KEY_PID, job.pid);
        Bundle terminal = new Bundle();
        terminal.putString(NodeJsRuntimeContract.KEY_EVENT_TYPE, NodeJsRuntimeContract.EVENT_FINISHED);
        terminal.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, job.id);
        terminal.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, job.slot == null ? -1 : job.slot.id);
        terminal.putBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT, result);
        send(callback, terminal);
        return result;
    }

    private static void send(INodeJsRuntimeCallback callback, Bundle event) {
        if (callback != null) try { callback.onEvent(event); } catch (RemoteException ignored) { }
    }

    boolean cancelScript(String id) {
        INodeJsRuntimePlugin remote;
        synchronized (lock) {
            Job job = jobs.get(id);
            if (job == null) return false;
            job.cancelled = true;
            remote = job.slot == null ? null : job.slot.remote;
            lock.notifyAll();
        }
        if (remote != null) try { remote.cancelScript(id); } catch (RemoteException ignored) { }
        return true;
    }

    boolean postMessage(String id, Bundle message) {
        INodeJsRuntimePlugin remote;
        synchronized (lock) {
            Job job = jobs.get(id);
            remote = job == null || job.slot == null || job.cancelled ? null : job.slot.remote;
        }
        try { return remote != null && remote.postMessage(id, message); } catch (RemoteException error) { return false; }
    }

    Bundle prewarmRuntime(Bundle request) {
        Bundle result = null;
        try {
            for (Slot slot : slots) {
                synchronized (lock) {
                    if (slot.job != null || slot.warming || !queue.isEmpty()) continue;
                    slot.warming = true;
                }
                try {
                    Bundle warmed = awaitRemote(slot, null).prewarmRuntime(request);
                    warmed.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, slot.id);
                    if (result == null || !warmed.getBoolean("started")) result = warmed;
                } finally {
                    synchronized (lock) { slot.warming = false; lock.notifyAll(); }
                }
            }
            return result == null ? service.bundles.busyFailureBundle(request == null ? new Bundle() : request, SystemClock.elapsedRealtime()) : result;
        } catch (Exception error) {
            return service.bundles.failureBundle(request == null ? new Bundle() : request, SystemClock.elapsedRealtime(),
                    "Node process pool prewarm failed: " + error.getMessage(), error, NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        } finally { closeWorkspaceDescriptors(request); }
    }

    Bundle getRuntimeInfo() {
        Bundle info;
        try { info = awaitRemote(slots[0], null).getRuntimeInfo(); }
        catch (Exception error) { info = service.bundles.runtimeInfoBundle(); }
        ArrayList<Bundle> snapshots = new ArrayList<>();
        String active = "";
        for (Slot slot : slots) {
            Bundle state = new Bundle();
            state.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, slot.id);
            INodeJsRuntimePlugin remote;
            synchronized (lock) {
                remote = slot.remote;
                state.putString("active", slot.job == null ? "" : slot.job.id);
                if (active.isEmpty() && slot.job != null) active = slot.job.id;
            }
            state.putInt("queued", 0); // Waiting requests are owned by the global FIFO.
            state.putInt("pid", -1);
            state.putLong("rss", 0);
            try {
                if (remote != null) {
                    Bundle child = remote.getRuntimeInfo();
                    state.putInt("pid", child.getInt(NodeJsRuntimeContract.KEY_PID));
                    state.putLong("rss", child.getLong("rssBytes"));
                    state.putBoolean("ready", child.getBoolean("runtimeReady"));
                }
            } catch (RemoteException ignored) { }
            snapshots.add(state);
        }
        synchronized (lock) { info.putInt("queuedExecutions", queue.size()); }
        info.putString(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID, active);
        // Published v2 hosts require exactly 1 here. It remains the per-runtime capacity;
        // the additive field reports the capacity of the pool to aware clients.
        info.putInt("maxConcurrentExecutions", 1);
        info.putInt("processPoolMaxConcurrentExecutions", slots.length);
        info.putString("processModel", "process_pool");
        ArrayList<String> capabilities = new ArrayList<>(java.util.Arrays.asList(NodeJsRuntimePluginService.CAPABILITIES));
        capabilities.remove("singleActiveBackpressure");
        capabilities.add("processPoolExecution");
        info.putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, capabilities.toArray(new String[0]));
        info.putInt("dispatcherPid", android.os.Process.myPid());
        info.putParcelableArrayList("slots", snapshots);
        return info;
    }

    @Override public void close() {
        ArrayList<String> active;
        synchronized (lock) { closed = true; active = new ArrayList<>(jobs.keySet()); lock.notifyAll(); }
        for (String id : active) cancelScript(id);
        for (Slot slot : slots) synchronized (lock) {
            if (slot.bound) service.unbindService(slot);
            slot.bound = false;
            slot.remote = null;
        }
        executions.shutdown();
    }
}

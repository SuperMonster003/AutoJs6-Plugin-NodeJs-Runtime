package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.*;

/** Configurable, demand-started runtime processes with one FIFO shared by all callers. */
final class NodeRuntimeProcessPool implements AutoCloseable {
    private final NodeJsRuntimePluginService service;
    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Slot[] slots = {
            new Slot(0, NodeJsRuntimeSlot0Service.class), new Slot(1, NodeJsRuntimeSlot1Service.class),
            new Slot(2, NodeJsRuntimeSlot2Service.class), new Slot(3, NodeJsRuntimeSlot3Service.class),
            new Slot(4, NodeJsRuntimeSlot4Service.class), new Slot(5, NodeJsRuntimeSlot5Service.class),
            new Slot(6, NodeJsRuntimeSlot6Service.class), new Slot(7, NodeJsRuntimeSlot7Service.class)
    };
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<String, Job> jobs = new HashMap<>();
    private final ExecutorService executions = Executors.newFixedThreadPool(NodeRuntimeSettings.MAX_SLOTS,
            task -> new Thread(task, "Node-pool-execution"));
    // Queued jobs own descriptors and a completion, but never a waiting execution thread.
    private final ExecutorService completions = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "Node-pool-completion"));
    private final SharedPreferences preferences;
    private volatile boolean closed;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        synchronized (lock) {
            if (!closed) { dispatchQueued(); releaseUnusedBindings(); }
        }
    };
    /** Most recent unexpected slot death (M12.5a); cancellation restarts are not counted. */
    private volatile Bundle lastSlotExit;
    /** Newest fatal-error report on disk (M12.5), parsed once per file. */
    private volatile Bundle lastCrashCache;
    private volatile String lastCrashKey;

    NodeRuntimeProcessPool(NodeJsRuntimePluginService service) {
        this.service = service;
        preferences = NodeRuntimeSettings.preferences(service);
        preferences.registerOnSharedPreferenceChangeListener(settingsListener);
    }

    private int slotCount() { return preferences.getInt(NodeRuntimeSettings.SLOTS, NodeRuntimeSettings.DEFAULT_SLOTS); }
    private int queueLimit() { return preferences.getInt(NodeRuntimeSettings.QUEUE, NodeRuntimeSettings.UNLIMITED); }

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
            synchronized (lock) {
                remote = null;
                // Release AUTO_CREATE after an idle exit; only a new execution should restart it.
                if (job == null && !warming) unbind(this);
                lock.notifyAll();
            }
        }
        @Override public void onBindingDied(ComponentName name) {
            synchronized (lock) {
                remote = null;
                unbind(this);
                if (!closed && (job != null || warming)) bind(this);
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
        final INodeJsRuntimeCallback callback;
        final CompletableFuture<Bundle> completion = new CompletableFuture<>();
        Runnable queueTimeout;
        volatile boolean cancelled;
        int pid;
        long dispatchedAtWallMs;
        Slot slot;
        Job(Bundle request, INodeJsRuntimeCallback callback) {
            this.request = request;
            this.callback = callback;
            id = nonBlank(request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID), "plugin-" + UUID.randomUUID());
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, id);
            timeout = request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L);
        }
        long remaining() {
            return timeout <= 0 ? Long.MAX_VALUE : NodeJsRuntimePluginService.remainingTimeoutBudgetMs(
                    timeout, startedAt, SystemClock.elapsedRealtime());
        }
    }

    private static final class AdmissionFailure extends Exception {
        final Bundle result;
        AdmissionFailure(Bundle result) { this.result = result; }
    }

    private void bind(Slot slot) {
        main.post(() -> {
            synchronized (lock) {
                if (closed || slot.bound || (slot.job == null && !slot.warming)) return;
                slot.bound = service.bindService(new Intent(service, slot.component), slot, Context.BIND_AUTO_CREATE);
                lock.notifyAll();
            }
        });
    }

    /** Called under lock on the main thread, or posted there by the dispatcher. */
    private void unbind(Slot slot) {
        if (slot.bound) service.unbindService(slot);
        slot.bound = false;
        slot.remote = null;
    }

    private void releaseUnusedBindings() {
        main.post(() -> {
            synchronized (lock) {
                for (Slot slot : slots) {
                    if (slot.job == null && !slot.warming && (slot.id >= slotCount() || slot.remote == null)) unbind(slot);
                }
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
        int count = slotCount(), occupied = 0;
        for (Slot slot : slots) if (slot.job != null || slot.warming) occupied++;
        // Shrinking lets active scripts finish before admitting work at the smaller capacity.
        if (occupied >= count) return null;
        for (int i = 0; i < count; i++) if (slots[i].job == null && !slots[i].warming && slots[i].remote != null) return slots[i];
        for (int i = 0; i < count; i++) if (slots[i].job == null && !slots[i].warming) return slots[i];
        return null;
    }

    Bundle runScript(Bundle original, INodeJsRuntimeCallback callback) {
        Job job = new Job(original == null ? new Bundle() : new Bundle(original), callback);
        try {
            register(job);
        } catch (AdmissionFailure failure) {
            closeWorkspaceDescriptors(job.request);
            return finish(job, callback, failure.result);
        }
        try { return job.completion.get(); }
        catch (InterruptedException error) {
            cancelScript(job.id);
            Thread.currentThread().interrupt();
            return service.bundles.failureBundle(job.request, job.startedAt, "Node caller interrupted.", null,
                    NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED);
        } catch (java.util.concurrent.ExecutionException error) { throw new IllegalStateException(error); }
    }

    Bundle startScript(Bundle original, INodeJsRuntimeCallback callback) {
        // Keep unmarshalled Binder descriptors alive until completion, including while queued.
        Job job = new Job(original == null ? new Bundle() : new Bundle(original), callback);
        try {
            if (callback == null) throw new AdmissionFailure(service.bundles.failureBundle(job.request,
                    job.startedAt, "startScript requires a completion callback.", null,
                    "ERR_AUTOJS6_NODE_PLUGIN_INVALID_REQUEST"));
            register(job); // Visible to cancelScript before the acknowledgement can reach the caller.
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
            dispatchQueued();
            Slot available = queue.isEmpty() ? freeSlot() : null;
            if (jobs.containsKey(job.id)) {
                throw new AdmissionFailure(service.bundles.failureBundle(job.request, job.startedAt,
                        "Execution ID is already active or queued: " + job.id, null, NodeJsRuntimePluginService.ERROR_BUSY));
            }
            if (available == null && queueLimit() >= 0 && queue.size() >= queueLimit()) {
                throw new AdmissionFailure(service.bundles.failureBundle(job.request, job.startedAt,
                        "Node process pool queue is full (limit " + queueLimit() + ").", null, NodeJsRuntimePluginService.ERROR_BUSY));
            }
            jobs.put(job.id, job);
            queue.addLast(job);
            if (job.timeout > 0) {
                job.queueTimeout = () -> {
                    synchronized (lock) {
                        if (queue.remove(job)) {
                            jobs.remove(job.id);
                            completeQueued(job, timeout(job, "queued"));
                            dispatchQueued();
                        }
                    }
                };
                main.postDelayed(job.queueTimeout, job.remaining());
            }
            dispatchQueued();
        }
    }

    private void dispatchQueued() {
        if (closed) return;
        Slot available;
        while (!queue.isEmpty() && (available = freeSlot()) != null) {
            Job next = queue.removeFirst();
            if (next.queueTimeout != null) main.removeCallbacks(next.queueTimeout);
            if (next.remaining() <= 0) {
                jobs.remove(next.id);
                completeQueued(next, timeout(next, "queued"));
                continue;
            }
            available.job = next;
            next.slot = available;
            executions.execute(() -> runRegistered(next, next.callback));
        }
    }

    private void completeQueued(Job job, Bundle result) {
        if (job.queueTimeout != null) main.removeCallbacks(job.queueTimeout);
        completions.execute(() -> {
            closeWorkspaceDescriptors(job.request);
            finish(job, job.callback, result);
        });
    }

    private void unregister(Job job) {
        synchronized (lock) {
            queue.remove(job);
            jobs.remove(job.id, job);
            if (job.slot != null) job.slot.job = null;
            dispatchQueued();
            releaseUnusedBindings();
            lock.notifyAll();
        }
    }

    private Bundle runRegistered(Job job, INodeJsRuntimeCallback callback) {
        Bundle result;
        try {
            INodeJsRuntimePlugin remote = awaitRemote(job.slot, job);
            job.pid = remote.getRuntimeInfo().getInt(NodeJsRuntimeContract.KEY_PID);
            job.dispatchedAtWallMs = System.currentTimeMillis();
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
                    deadObject(error) != null && job.pid > 0 ? slotExitFailure(job, deadObject(error)) :
                    service.bundles.failureBundle(job.request, job.startedAt, "Node runtime slot failed: " + error.getMessage(),
                            error, NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        } finally {
            closeWorkspaceDescriptors(job.request);
            unregister(job);
        }
        return finish(job, callback, result);
    }

    private static Throwable deadObject(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof android.os.DeadObjectException) return current;
        }
        return null;
    }

    /** The slot process died mid-execution: report the system's exit record instead of a bare DeadObjectException. */
    private Bundle slotExitFailure(Job job, Throwable death) {
        int slotId = job.slot == null ? -1 : job.slot.id;
        Bundle exit = NodeRuntimeSlotExit.describe(service, job.pid, slotId, job.id,
                job.dispatchedAtWallMs - 1_000L, NodeRuntimeSlotExit.RECORD_WAIT_MS);
        // M12.5: a Node.js fatal error leaves its diagnostic report next to the exit record.
        File crashDirectory = NodeRuntimeCrashReport.directory(service.getCacheDir());
        NodeRuntimeCrashReport.Report crash = NodeRuntimeCrashReport.forExecution(crashDirectory, job.id, job.pid, job.dispatchedAtWallMs - 1_000L);
        if (crash != null) {
            crash.maxOldGenerationSizeMb = Math.max(0, job.request.getInt(NodeJsRuntimeContract.KEY_MAX_OLD_GENERATION_SIZE_MB, 0));
            exit.putBundle(NodeRuntimeCrashReport.KEY_CRASH, crash.toBundle());
            exit.putString("summary", NodeRuntimeSlotExit.withCrash(exit.getString("summary"), crash.summary()));
            NodeRuntimeCrashReport.prune(crashDirectory, NodeRuntimeCrashReport.KEEP_NEWEST);
        }
        lastSlotExit = exit;
        Bundle result = service.bundles.failureBundle(job.request, job.startedAt,
                NodeRuntimeSlotExit.failureMessage(slotId, job.pid, exit.getString("summary")),
                death, NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        result.putBundle(NodeRuntimeSlotExit.KEY_SLOT_EXIT, new Bundle(exit));
        return result;
    }

    private Bundle timeout(Job job, String phase) {
        return service.bundles.timeoutFailureBundle(job.request, job.startedAt,
                job.timeout, phase);
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
        job.completion.complete(result);
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
            if (queue.remove(job)) {
                jobs.remove(id);
                completeQueued(job, service.bundles.failureBundle(job.request, job.startedAt,
                        "Node execution cancelled while queued.", null, NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED));
                dispatchQueued();
            }
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
                    if (slot.id >= slotCount() || slot.job != null || slot.warming || !queue.isEmpty()) continue;
                    slot.warming = true;
                }
                try {
                    Bundle warmed = awaitRemote(slot, null).prewarmRuntime(request);
                    warmed.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, slot.id);
                    if (result == null || !warmed.getBoolean("started")) result = warmed;
                } finally {
                    synchronized (lock) {
                        slot.warming = false;
                        dispatchQueued();
                        releaseUnusedBindings();
                        lock.notifyAll();
                    }
                }
            }
            return result == null ? service.bundles.failureBundle(request == null ? new Bundle() : request,
                    SystemClock.elapsedRealtime(), "All configured Node process slots are busy.", null,
                    NodeJsRuntimePluginService.ERROR_BUSY) : result;
        } catch (Exception error) {
            return service.bundles.failureBundle(request == null ? new Bundle() : request, SystemClock.elapsedRealtime(),
                    "Node process pool prewarm failed: " + error.getMessage(), error, NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        } finally { closeWorkspaceDescriptors(request); }
    }

    Bundle getRuntimeInfo() {
        Bundle info;
        INodeJsRuntimePlugin first;
        synchronized (lock) { first = slots[0].remote; }
        try { info = first == null ? service.bundles.runtimeInfoBundle() : first.getRuntimeInfo(); }
        catch (RemoteException error) { info = service.bundles.runtimeInfoBundle(); first = null; }
        if (first == null) {
            info.putInt(NodeJsRuntimeContract.KEY_PID, -1);
            info.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, service.getPackageName() + ":nodejs_runtime0");
        }
        ArrayList<Bundle> snapshots = new ArrayList<>();
        String active = "";
        for (Slot slot : slots) {
            Bundle state = new Bundle();
            state.putInt(NodeJsRuntimeContract.KEY_SLOT_ID, slot.id);
            INodeJsRuntimePlugin remote;
            synchronized (lock) {
                if (slot.id >= slotCount() && slot.job == null && !slot.warming) continue;
                remote = slot.remote;
                state.putBoolean("enabled", slot.id < slotCount());
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
        synchronized (lock) {
            info.putInt("queuedExecutions", queue.size());
            info.putInt(NodeJsRuntimeContract.KEY_PROCESS_POOL_MAX_CONCURRENT_EXECUTIONS, slotCount());
            // Published v2 hosts reject negative queueCapacity. The additive flag disambiguates unlimited.
            info.putInt(NodeJsRuntimeContract.KEY_QUEUE_CAPACITY, queueLimit() < 0 ? Integer.MAX_VALUE : queueLimit());
            info.putBoolean(NodeJsRuntimeContract.KEY_QUEUE_UNLIMITED, queueLimit() < 0);
        }
        info.putString(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID, active);
        // Published v2 hosts require exactly 1 here. It remains the per-runtime capacity;
        // the additive field reports the capacity of the pool to aware clients.
        info.putInt("maxConcurrentExecutions", 1);
        info.putString("processModel", "process_pool");
        info.putBoolean("dedicatedRuntimeProcess", true);
        ArrayList<String> capabilities = new ArrayList<>(java.util.Arrays.asList(NodeJsRuntimePluginService.CAPABILITIES));
        capabilities.remove("singleActiveBackpressure");
        capabilities.add("processPoolExecution");
        info.putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, capabilities.toArray(new String[0]));
        info.putInt("dispatcherPid", android.os.Process.myPid());
        info.putParcelableArrayList("slots", snapshots);
        Bundle exit = lastSlotExit;
        if (exit != null) info.putBundle(NodeRuntimeSlotExit.KEY_LAST_SLOT_EXIT, new Bundle(exit));
        Bundle crash = lastCrash();
        if (crash != null) info.putBundle(NodeRuntimeCrashReport.KEY_LAST_CRASH, crash);
        return info;
    }

    /** The newest report on disk survives dispatcher restarts, unlike lastSlotExit. */
    private Bundle lastCrash() {
        File file = NodeRuntimeCrashReport.newestFile(NodeRuntimeCrashReport.directory(service.getCacheDir()));
        if (file == null) return null;
        String key = file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
        Bundle cached = lastCrashCache;
        if (cached != null && key.equals(lastCrashKey)) return new Bundle(cached);
        NodeRuntimeCrashReport.Report report = NodeRuntimeCrashReport.read(file);
        Bundle crash = report == null ? null : report.toBundle();
        lastCrashCache = crash;
        lastCrashKey = key;
        return crash == null ? null : new Bundle(crash);
    }

    @Override public void close() {
        ArrayList<String> active;
        preferences.unregisterOnSharedPreferenceChangeListener(settingsListener);
        synchronized (lock) { closed = true; active = new ArrayList<>(jobs.keySet()); lock.notifyAll(); }
        for (String id : active) cancelScript(id);
        for (Slot slot : slots) synchronized (lock) {
            if (slot.bound) service.unbindService(slot);
            slot.bound = false;
            slot.remote = null;
        }
        executions.shutdown();
        completions.shutdown();
    }
}

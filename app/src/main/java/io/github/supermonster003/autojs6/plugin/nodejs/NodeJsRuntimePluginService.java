package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.*;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeJsRuntimePluginService extends Service {

    private static final String TAG = "NodeJsRuntimePlugin";
    static final String NODE_VERSION = "24.5.0";
    static final String NATIVE_LIBRARY_NAME = "node";
    static final String BRIDGE_LIBRARY_NAME = "autojs6-node";
    static final String DEFAULT_SOURCE_NAME = "<plugin-node-script.js>";
    static final String RUNTIME_PROCESS_SUFFIX = ":nodejs_runtime";
    static final String CANCELLATION_STRATEGY_COOPERATIVE_STOP =
            "cooperative_stop_with_process_restart_fallback";
    private static final long PROCESS_RESTART_AFTER_CANCEL_DELAY_MS = 150L;
    /** Grace window for node::Stop to drain the loop before the restart fallback fires. */
    private static final long COOPERATIVE_STOP_FALLBACK_GRACE_MS = 3_000L;
    static final String ERROR_SCRIPT_CANCELLED = "ERR_AUTOJS6_NODE_SCRIPT_CANCELLED";
    static final String ERROR_SCRIPT_TIMEOUT = "ERR_AUTOJS6_SCRIPT_TIMEOUT";
    /** Queue wait ceiling for requests that carry no explicit timeout. */
    private static final long DEFAULT_ADMISSION_WAIT_MS = 10 * 60_000L;
    static final String ERROR_BUSY = "ERR_AUTOJS6_NODE_PLUGIN_BUSY";
    static final String ERROR_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE";
    static final String ERROR_CONTRACT_MISMATCH = "ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH";
    static final String NODE_CAPABILITY_CATALOG_SCHEMA = "autojs6-node-capability-catalog-v1";
    static final String NODE_CAPABILITY_CATALOG_VERSION = "1.3.0";
    static final String NODE_CAPABILITY_CATALOG_SHA256 =
            "78e573627e8a19e7dc511a4c8e527a564ecf7277d6389e08194963a597f1d0ab";
    static final String KEY_NODE_CAPABILITY_CATALOG_SCHEMA = "nodeCapabilityCatalogSchema";
    static final String KEY_NODE_CAPABILITY_CATALOG_VERSION = "nodeCapabilityCatalogVersion";
    static final String KEY_NODE_CAPABILITY_CATALOG_SHA256 = "nodeCapabilityCatalogSha256";
    static final String KEY_NODE_RUNTIME_KIT_SCHEMA = "nodeRuntimeKitSchema";
    static final String KEY_NODE_RUNTIME_KIT_VERSION = "nodeRuntimeKitVersion";
    static final String KEY_NODE_RUNTIME_KIT_SHA256 = "nodeRuntimeKitSha256";
    static final String KEY_NODE_RUNTIME_KIT_ID = "nodeRuntimeKitId";
    static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA =
            "embedded_script.runtime_plugin.capability_catalog_schema";
    static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION =
            "embedded_script.runtime_plugin.capability_catalog_version";
    static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256 =
            "embedded_script.runtime_plugin.capability_catalog_sha256";
    static final String DIAGNOSTIC_NODE_RUNTIME_KIT_SCHEMA =
            "embedded_script.runtime_plugin.runtime_kit_schema";
    static final String DIAGNOSTIC_NODE_RUNTIME_KIT_VERSION =
            "embedded_script.runtime_plugin.runtime_kit_version";
    static final String DIAGNOSTIC_NODE_RUNTIME_KIT_SHA256 =
            "embedded_script.runtime_plugin.runtime_kit_sha256";
    static final String DIAGNOSTIC_NODE_RUNTIME_KIT_ID =
            "embedded_script.runtime_plugin.runtime_kit_id";
    private static final String CAPABILITY_PERSISTENT_PROCESS_RUNTIME = "persistentProcessRuntime";
    private static final String CAPABILITY_SINGLE_ACTIVE_BACKPRESSURE = "singleActiveBackpressure";
    private static final String CAPABILITY_PROCESS_RESTART_CANCELLATION = "processRestartCancellation";
    static final String[] SUPPORTED_ABIS = new String[]{"arm64-v8a", "armeabi-v7a", "x86_64"};
    static final String[] CAPABILITIES = new String[]{
            NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION,
            NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT,
            NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE,
            NodeJsRuntimeContract.CAPABILITY_HOST_PLAINTEXT_MODULE_SOURCE_MATERIALIZATION,
            NodeJsRuntimeContract.CAPABILITY_HOST_TYPESCRIPT_ON_DEMAND_COMPILATION,
            NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER,
            CAPABILITY_PERSISTENT_PROCESS_RUNTIME,
            CAPABILITY_SINGLE_ACTIVE_BACKPRESSURE,
            CAPABILITY_PROCESS_RESTART_CANCELLATION,
            NodeJsRuntimeContract.CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT,
    };

    private final Object runtimeLifecycleLock = new Object();
    final NodeRuntimeExecutionGate executionGate =
            new NodeRuntimeExecutionGate(SystemClock::elapsedRealtime);
    final AtomicBoolean processRestartScheduled = new AtomicBoolean(false);
    private final Handler processHandler = new Handler(Looper.getMainLooper());
    volatile RuntimeReadiness lastRuntimeReadiness = RuntimeReadiness.notStarted();
    final NodeRuntimeModuleInjector moduleInjector = new NodeRuntimeModuleInjector(this);
    final NodePluginBundles bundles = new NodePluginBundles(this);

    private final INodeJsRuntimePlugin.Stub binder = new INodeJsRuntimePlugin.Stub() {
        @Override
        public Bundle getRuntimeInfo() {
            return bundles.runtimeInfoBundle();
        }

        @Override
        public Bundle runScript(Bundle request, INodeJsRuntimeCallback callback) {
            long startedAt = SystemClock.elapsedRealtime();
            Bundle normalizedRequest = request == null ? new Bundle() : new Bundle(request);
            try {
                Bundle contractFailure = validateRequestContract(normalizedRequest, startedAt);
                if (contractFailure != null) {
                    return completeImmediateFailure(callback, contractFailure);
                }
                String executionId = nonBlank(
                        normalizedRequest.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID),
                        "plugin-" + UUID.randomUUID()
                );
                normalizedRequest.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
                long timeoutMs = normalizedRequest.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L);
                long admissionWaitBudgetMs = admissionWaitBudgetMs(
                        normalizedRequest,
                        startedAt,
                        SystemClock.elapsedRealtime()
                );
                if (timeoutMs > 0L && admissionWaitBudgetMs <= 0L) {
                    return completeImmediateFailure(
                            callback,
                            bundles.timeoutFailureBundle(
                                    normalizedRequest,
                                    startedAt,
                                    timeoutMs,
                                    "pre_admission"
                            )
                    );
                }
                NodeRuntimeExecutionGate.Admission admission = executionGate.acquire(
                        executionId,
                        admissionWaitBudgetMs
                );
                if (admission.outcome != NodeRuntimeExecutionGate.AdmissionOutcome.ADMITTED) {
                    return completeImmediateFailure(
                            callback,
                            bundles.admissionFailureBundle(normalizedRequest, startedAt, admission)
                    );
                }
                NodeRuntimeExecutionGate.Lease lease = admission.lease;
                Runnable timeoutWatchdog = null;
                boolean stopScopeOpened = false;
                try {
                    long remainingBudgetMs = remainingTimeoutBudgetMs(
                            timeoutMs,
                            startedAt,
                            SystemClock.elapsedRealtime()
                    );
                    if (timeoutMs > 0L && remainingBudgetMs <= 0L) {
                        lease.markCompleted();
                        return completeImmediateFailure(
                                callback,
                                bundles.timeoutFailureBundle(
                                        normalizedRequest,
                                        startedAt,
                                        timeoutMs,
                                        "post_admission"
                                )
                        );
                    }
                    NativeNodeEmbeddedRuntimeBridge.beginScriptStopScope(
                            NodeJsRuntimePluginService.this,
                            executionId
                    );
                    stopScopeOpened = true;
                    if (timeoutMs > 0L) {
                        remainingBudgetMs = remainingTimeoutBudgetMs(
                                timeoutMs,
                                startedAt,
                                SystemClock.elapsedRealtime()
                        );
                        if (remainingBudgetMs <= 0L) {
                            executionGate.requestTimeout(executionId);
                            return completeImmediateFailure(
                                    callback,
                                    bundles.timeoutFailureBundle(
                                            normalizedRequest,
                                            startedAt,
                                            timeoutMs,
                                            "pre_execution"
                                    )
                            );
                        }
                        timeoutWatchdog = () -> handleExecutionTimeout(executionId);
                        processHandler.postDelayed(timeoutWatchdog, remainingBudgetMs);
                    }
                    Bundle result = runScriptActive(normalizedRequest, callback);
                    NodeRuntimeExecutionGate.Lease.State completionState = lease.markCompleted();
                    if (timeoutWatchdog != null) {
                        processHandler.removeCallbacks(timeoutWatchdog);
                    }
                    if (completionState == NodeRuntimeExecutionGate.Lease.State.TIMED_OUT) {
                        bundles.markResultTimedOut(
                                result,
                                timeoutMs,
                                elapsedSince(startedAt),
                                "execution"
                        );
                    } else if (completionState == NodeRuntimeExecutionGate.Lease.State.CANCELLED) {
                        markResultCancelled(result);
                    }
                    return result;
                } finally {
                    lease.markCompleted();
                    if (timeoutWatchdog != null) {
                        processHandler.removeCallbacks(timeoutWatchdog);
                    }
                    if (stopScopeOpened) {
                        NativeNodeEmbeddedRuntimeBridge.endScriptStopScope(NodeJsRuntimePluginService.this);
                    }
                    executionGate.release(lease);
                }
            } finally {
                closeWorkspaceDescriptors(normalizedRequest);
            }
        }

        @Override
        public boolean cancelScript(String executionId) {
            if (!isDedicatedRuntimeProcess()) {
                Log.e(TAG, "Refusing cancellation outside the dedicated runtime process.");
                return false;
            }
            String normalizedId = nonBlank(executionId, "");
            if (executionGate.cancelQueued(normalizedId)) {
                // Still waiting in the queue: nothing native to stop.
                return true;
            }
            if (!executionGate.requestCooperativeCancellation(normalizedId)) {
                return false;
            }
            boolean stopDispatched;
            try {
                stopDispatched = NativeNodeEmbeddedRuntimeBridge.requestScriptStop(
                        NodeJsRuntimePluginService.this,
                        normalizedId
                );
            } catch (Throwable error) {
                Log.w(TAG, "Cooperative stop dispatch failed; falling back to process restart.", error);
                stopDispatched = false;
            }
            if (!stopDispatched) {
                // No matching native scope (script finished, or stop symbol
                // unavailable): restore the pre-M2.2 restart behavior.
                if (executionGate.requestCancellation(normalizedId)) {
                    scheduleDedicatedRuntimeProcessRestart("cancel:" + normalizedId);
                }
                return true;
            }
            Log.i(TAG, "Cooperative stop dispatched for execution " + normalizedId);
            scheduleCooperativeStopFallback(normalizedId);
            return true;
        }

        @Override
        public Bundle prewarmRuntime(Bundle request) {
            long startedAt = SystemClock.elapsedRealtime();
            Bundle normalizedRequest = request == null ? new Bundle() : new Bundle(request);
            try {
                Bundle contractFailure = validateRequestContract(normalizedRequest, startedAt);
                if (contractFailure != null) {
                    contractFailure.putBoolean("started", false);
                    contractFailure.putString("status", "failed");
                    contractFailure.putString("reason", "contract_mismatch");
                    return contractFailure;
                }
                NodeRuntimeExecutionGate.Lease lease = executionGate.tryAcquire("prewarm-" + UUID.randomUUID());
                if (lease == null) {
                    Bundle busy = bundles.busyFailureBundle(normalizedRequest, startedAt);
                    busy.putBoolean("started", false);
                    busy.putString("status", "busy");
                    busy.putString("reason", "active_execution");
                    return busy;
                }
                try {
                    return bundles.prewarmRuntimeBundle(ensurePersistentRuntimeReady(), startedAt);
                } finally {
                    executionGate.release(lease);
                }
            } finally {
                closeWorkspaceDescriptors(normalizedRequest);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RuntimeReadiness readiness = ensurePersistentRuntimeReady();
        if (!readiness.ready) {
            Log.e(TAG, "Dedicated Node.js runtime process failed to become ready: " + readiness.detail);
        }
    }

    @Override
    public IBinder onBind(android.content.Intent intent) {
        return binder;
    }

    // Kept as a delegate: unit tests reference this via the service class.
    static long procStatusLongValue(String statusText, String fieldName, long fallback) {
        return NodePluginPayloads.procStatusLongValue(statusText, fieldName, fallback);
    }

    static String prepareTypeScriptEntryForNative(String sourceName, String source) {
        return NodeTypeScriptSourcePolicy.requireJavaScriptSource(sourceName, source);
    }

    static Map<String, String> prepareTypeScriptModuleSourcesForNative(
            Map<String, String> sources
    ) {
        return NodeTypeScriptSourcePolicy.requireJavaScriptSources(sources);
    }

    static Map<String, String> prepareTypeScriptRuntimeModuleSourcesForNative(
            Map<String, String> sources
    ) {
        return NodeTypeScriptSourcePolicy.requireJavaScriptSources(sources);
    }

    static NodeStartupEnvironmentPolicy.Result prepareNodeStartupEnvironmentForNative(
            Map<String, String> environment
    ) {
        return NodeStartupEnvironmentPolicy.sanitize(environment);
    }

    private Bundle runScriptActive(Bundle request, INodeJsRuntimeCallback callback) {
        return new NodePluginScriptExecution(this).run(request, callback);
    }

    RuntimeReadiness ensurePersistentRuntimeReady() {
        RuntimeReadiness cached = lastRuntimeReadiness;
        if (cached.ready) {
            return cached;
        }
        synchronized (runtimeLifecycleLock) {
            cached = lastRuntimeReadiness;
            if (cached.ready) {
                return cached;
            }
            long startedAt = SystemClock.elapsedRealtime();
            LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("process_runtime.process_name", currentProcessName());
            diagnostics.put("process_runtime.pid", Integer.toString(Process.myPid()));
            diagnostics.put("process_runtime.service_class", getClass().getName());
            diagnostics.put("process_runtime.dedicated_process", Boolean.toString(isDedicatedRuntimeProcess()));
            diagnostics.put("process_runtime.cancellation_strategy", CANCELLATION_STRATEGY_COOPERATIVE_STOP);
            try {
                loadNativeRuntime();
                diagnostics.putAll(
                        NativeNodeEmbeddedRuntimeBridge.setProcessRuntimePersistentEnabled(this, true)
                );
                diagnostics.putAll(NativeNodeEmbeddedRuntimeBridge.ensureProcessRuntimeReady(this));
                diagnostics.putAll(NativeNodeEmbeddedRuntimeBridge.processRuntimeDiagnostics(this));
            } catch (Throwable error) {
                diagnostics.put("process_runtime.state", "unavailable");
                diagnostics.put("process_runtime.healthy", "false");
                diagnostics.put("process_runtime.poisoned", "true");
                diagnostics.put("process_runtime.poison_reason", messageOf(error));
            }
            boolean ready = "ready".equals(diagnostics.get("process_runtime.state"))
                    && booleanValue(diagnostics, "process_runtime.healthy")
                    && booleanValue(diagnostics, "process_runtime.persistent_enabled")
                    && "done".equals(diagnostics.get("process_runtime.mode_change.status"))
                    && "done".equals(diagnostics.get("process_runtime.ensure.status"));
            String detail = ready
                    ? nonBlank(
                    diagnostics.get("process_runtime.ensure.detail"),
                    "process-global Node/V8 runtime is ready"
            )
                    : nonBlank(
                    diagnostics.get("process_runtime.poison_reason"),
                    nonBlank(
                            diagnostics.get("process_runtime.ensure.detail"),
                            "process-global Node/V8 runtime failed its readiness gate"
                    )
            );
            diagnostics.put("process_runtime.prewarm.status", ready ? "ready" : "failed");
            diagnostics.put("process_runtime.prewarm.detail", detail);
            diagnostics.put(
                    "timing.runtime_plugin_prewarm.ms",
                    Long.toString(elapsedSince(startedAt))
            );
            RuntimeReadiness readiness = new RuntimeReadiness(ready, detail, diagnostics);
            lastRuntimeReadiness = readiness;
            return readiness;
        }
    }

    private Bundle validateRequestContract(Bundle request, long startedAt) {
        int receivedVersion;
        try {
            // A request without an explicit version is treated as the current
            // contract so bare adb/debug/legacy callers are not locked out;
            // only an explicit unsupported value is rejected.
            receivedVersion = request.getInt(
                    NodeJsRuntimeContract.KEY_CONTRACT_VERSION,
                    NodeJsRuntimeContract.CONTRACT_VERSION
            );
        } catch (Throwable ignored) {
            receivedVersion = NodeJsRuntimeContract.CONTRACT_VERSION;
        }
        if (!NodeJsRuntimeContract.supportsContractVersion(receivedVersion)) {
            return bundles.requestContractFailureBundle(
                    request,
                    startedAt,
                    "contract",
                    "Unsupported Node.js runtime plugin contract version " + receivedVersion
                            + "; supported range " + NodeJsRuntimeContract.MIN_CONTRACT_VERSION
                            + ".." + NodeJsRuntimeContract.MAX_CONTRACT_VERSION + ".",
                    receivedVersion,
                    NodeJsRuntimeContract.CONTRACT_VERSION
            );
        }

        IBinder requestedModuleSourceProvider = request.getBinder(
                PluginModuleSourceProviderFileTransportSession.KEY_PROVIDER_BINDER
        );
        if (requestedModuleSourceProvider == null) {
            return null;
        }
        Object rawModuleSourceProviderVersion = request.get(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION
        );
        int receivedModuleSourceProviderVersion = strictModuleSourceProviderContractVersion(
                rawModuleSourceProviderVersion
        );
        if (NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(
                receivedModuleSourceProviderVersion
        )) {
            return null;
        }
        return bundles.requestContractFailureBundle(
                request,
                startedAt,
                "module_source_provider_contract",
                "Unsupported Node.js module-source provider contract version "
                        + receivedModuleSourceProviderVersion + "; supported range "
                        + NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION + ".."
                        + NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION + ".",
                receivedModuleSourceProviderVersion,
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION
        );
    }

    static int strictModuleSourceProviderContractVersion(Object rawValue) {
        // Published v1 hosts never send this field at all; treat absence as v1
        // instead of refusing the whole request. Explicit garbage still fails.
        if (rawValue == null) {
            return NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION;
        }
        return rawValue instanceof Integer ? (Integer) rawValue : -1;
    }

    static int initialModuleSourceProviderWireVersion(Object rawValue) {
        // Callers that declare a version get exactly that version on the wire.
        // Callers without the field start at the current version — a modern
        // provider works immediately, and a published v1 provider triggers the
        // session's one-time downgrade retry.
        if (rawValue instanceof Integer) {
            return (Integer) rawValue;
        }
        return NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION;
    }

    static boolean supportsRequestedModuleSourceProviderContract(
            boolean hasModuleSourceProviderBinder,
            Object rawVersion
    ) {
        return !hasModuleSourceProviderBinder ||
                NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(
                        strictModuleSourceProviderContractVersion(rawVersion)
                );
    }

    private Bundle completeImmediateFailure(INodeJsRuntimeCallback callback, Bundle failure) {
        notifyOutput(callback, failure);
        notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
        return failure;
    }

    /**
     * Wait budget for queued admission: honor an explicit request timeout,
     * otherwise wait generously — desktop `node foo.js` semantics are "run
     * when it's my turn", not "fail because someone else is running".
     */
    private static long admissionWaitBudgetMs(Bundle request, long startedAt, long now) {
        long timeoutMs = request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L);
        return timeoutMs > 0L
                ? remainingTimeoutBudgetMs(timeoutMs, startedAt, now)
                : DEFAULT_ADMISSION_WAIT_MS;
    }

    /** Remaining part of a positive wall-clock budget; non-positive means unbounded. */
    static long remainingTimeoutBudgetMs(long timeoutMs, long startedAt, long now) {
        if (timeoutMs <= 0L) {
            return Long.MAX_VALUE;
        }
        long elapsedMs = now <= startedAt ? 0L : now - startedAt;
        if (elapsedMs < 0L || elapsedMs >= timeoutMs) {
            return 0L;
        }
        return timeoutMs - elapsedMs;
    }

    boolean isDedicatedRuntimeProcess() {
        return currentProcessName().equals(getPackageName() + RUNTIME_PROCESS_SUFFIX);
    }

    /**
     * Escalates a cooperative stop to the pre-M2.2 process restart when the
     * cancelled execution is still holding the gate after the grace window —
     * e.g. a script stuck in synchronous native code that node::Stop cannot
     * interrupt.
     */
    private void scheduleCooperativeStopFallback(String executionId) {
        processHandler.postDelayed(() -> {
            NodeRuntimeExecutionGate.Snapshot active = executionGate.snapshot();
            if (active == null || !active.executionId.equals(executionId)) {
                return;
            }
            Log.w(
                    TAG,
                    "Cooperative stop did not release execution " + executionId +
                            " within " + COOPERATIVE_STOP_FALLBACK_GRACE_MS + "ms; restarting process."
            );
            if (executionGate.requestCancellation(executionId)) {
                scheduleDedicatedRuntimeProcessRestart("cooperative-stop-timeout:" + executionId);
            }
        }, COOPERATIVE_STOP_FALLBACK_GRACE_MS);
    }

    private void handleExecutionTimeout(String executionId) {
        if (!executionGate.requestTimeout(executionId)) {
            return;
        }
        Log.w(TAG, "Execution " + executionId + " exceeded its wall-clock budget; stopping it.");
        boolean stopDispatched;
        try {
            stopDispatched = NativeNodeEmbeddedRuntimeBridge.requestScriptStop(
                    NodeJsRuntimePluginService.this,
                    executionId
            );
        } catch (Throwable error) {
            Log.w(TAG, "Timeout stop dispatch failed; falling back to process restart.", error);
            stopDispatched = false;
        }
        if (!stopDispatched) {
            if (executionGate.requestCancellation(executionId)) {
                scheduleDedicatedRuntimeProcessRestart("timeout:" + executionId);
            }
            return;
        }
        Log.i(TAG, "Timeout stop dispatched for execution " + executionId);
        scheduleCooperativeStopFallback(executionId);
    }

    /**
     * Rewrites a finished execution's result as a cancellation outcome: the
     * caller asked for the stop, so a drained loop (often exit code 0) must
     * not read as normal success, and a stop-induced failure needs the
     * readable cancel code instead of a generic native error.
     */
    private void markResultCancelled(Bundle result) {
        if (result == null) {
            return;
        }
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, ERROR_SCRIPT_CANCELLED);
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_MESSAGE,
                "Script was cancelled while running."
        );
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                        new String[]{"embedded_script.runtime_plugin.cancelled=true"}
                )
        );
    }

    private void scheduleDedicatedRuntimeProcessRestart(String reason) {
        if (!processRestartScheduled.compareAndSet(false, true)) {
            return;
        }
        processHandler.postDelayed(() -> {
            Log.w(
                    TAG,
                    "Terminating dedicated Node.js runtime process after cancellation: " + reason
            );
            stopSelf();
            Process.killProcess(Process.myPid());
        }, PROCESS_RESTART_AFTER_CANCEL_DELAY_MS);
    }

    private void loadNativeRuntime() {
        try {
            System.loadLibrary("c++_shared");
        } catch (UnsatisfiedLinkError ignored) {
            // Some Android builds load libc++ transitively from the native bridge.
        }
        System.loadLibrary(NATIVE_LIBRARY_NAME);
        System.loadLibrary(BRIDGE_LIBRARY_NAME);
    }

    @SuppressWarnings("deprecation")
    long packageVersionCode() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.getLongVersionCode();
            }
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0L;
        }
    }

    static boolean hasCapability(String capability) {
        if (capability == null) {
            return false;
        }
        for (String item : CAPABILITIES) {
            if (capability.equals(item)) {
                return true;
            }
        }
        return false;
    }

    StreamingOutputSink installOutputStreamSink(INodeJsRuntimeCallback callback) {
        if (callback == null) {
            return null;
        }
        StreamingOutputSink sink = new StreamingOutputSink(callback);
        NativeNodeEmbeddedRuntimeBridge.setOutputStreamSink(this, sink);
        return sink;
    }

    void clearOutputStreamSink() {
        NativeNodeEmbeddedRuntimeBridge.setOutputStreamSink(this, null);
    }

    /**
     * Forwards native fd/pipe chunks to the host callback while the script is
     * still running. Called on native pipe-reader threads; stdout and stderr
     * each keep their own decoder because they arrive on separate threads.
     */
    final class StreamingOutputSink implements NativeNodeEmbeddedRuntimeBridge.OutputSink {

        private final INodeJsRuntimeCallback callback;
        private final Utf8StreamDecoder stdoutDecoder = new Utf8StreamDecoder();
        private final Utf8StreamDecoder stderrDecoder = new Utf8StreamDecoder();
        private final AtomicBoolean delivered = new AtomicBoolean(false);

        StreamingOutputSink(INodeJsRuntimeCallback callback) {
            this.callback = callback;
        }

        boolean deliveredAnything() {
            return delivered.get();
        }

        @Override
        public void onStdout(byte[] chunk) {
            String text = stdoutDecoder.decode(chunk);
            if (!text.isEmpty()) {
                delivered.set(true);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDOUT, "INFO", text);
            }
        }

        @Override
        public void onStderr(byte[] chunk) {
            String text = stderrDecoder.decode(chunk);
            if (!text.isEmpty()) {
                delivered.set(true);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDERR, "ERROR", text);
            }
        }
    }

    /**
     * Incremental UTF-8 decoder: a multi-byte character split across two pipe
     * chunks is held back until its remaining bytes arrive instead of being
     * replaced with U+FFFD.
     */
    static final class Utf8StreamDecoder {

        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private byte[] pending = new byte[0];

        String decode(byte[] chunk) {
            if (chunk == null || chunk.length == 0) {
                return "";
            }
            byte[] input;
            if (pending.length == 0) {
                input = chunk;
            } else {
                input = new byte[pending.length + chunk.length];
                System.arraycopy(pending, 0, input, 0, pending.length);
                System.arraycopy(chunk, 0, input, pending.length, chunk.length);
            }
            ByteBuffer in = ByteBuffer.wrap(input);
            CharBuffer out = CharBuffer.allocate(input.length + 1);
            decoder.reset();
            decoder.decode(in, out, false);
            pending = new byte[in.remaining()];
            in.get(pending);
            out.flip();
            return out.toString();
        }
    }

    void commitWorkspaceIfProcessStable(PluginWorkspaceArchiveSession session) throws IOException {
        if (session == null || processRestartScheduled.get() || executionGate.isClosed()) return;
        session.commit();
    }

    static boolean shouldCommitWorkspaceAfterFailure(boolean nativeDispatchStarted) {
        return nativeDispatchStarted;
    }

    void commitWorkspaceQuietly(PluginWorkspaceArchiveSession session) {
        if (session == null) return;
        try {
            commitWorkspaceIfProcessStable(session);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to checkpoint the Node.js plugin workspace.", error);
        }
    }

    static final class RuntimeReadiness {
        final boolean ready;
        final String detail;
        final Map<String, String> diagnostics;

        RuntimeReadiness(boolean ready, String detail, Map<String, String> diagnostics) {
            this.ready = ready;
            this.detail = nonBlank(detail, ready ? "ready" : "not ready");
            this.diagnostics = Collections.unmodifiableMap(
                    diagnostics == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(diagnostics)
            );
        }

        static RuntimeReadiness notStarted() {
            LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("process_runtime.state", "uninitialized");
            diagnostics.put("process_runtime.healthy", "false");
            diagnostics.put("process_runtime.persistent_enabled", "false");
            diagnostics.put("process_runtime.prewarm.status", "not_started");
            return new RuntimeReadiness(false, "runtime prewarm has not started", diagnostics);
        }

        String[] nativePayload() {
            return nativePayloadFromMap(diagnostics);
        }
    }

}

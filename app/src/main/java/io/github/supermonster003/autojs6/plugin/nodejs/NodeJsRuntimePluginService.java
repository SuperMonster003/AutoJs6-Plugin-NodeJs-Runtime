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
    private static final String NODE_VERSION = "24.5.0";
    private static final String NATIVE_LIBRARY_NAME = "node";
    private static final String BRIDGE_LIBRARY_NAME = "autojs6-node";
    private static final String DEFAULT_SOURCE_NAME = "<plugin-node-script.js>";
    private static final String HOST_APP_INFO_RUNTIME_MODULE_NAME = "autojs6:host-app-info";
    private static final String DEVICE_INFO_RUNTIME_MODULE_NAME = "autojs6:device-info";
    private static final String ENGINE_INFO_RUNTIME_MODULE_NAME = "autojs6:engine-info";
    private static final String LIFECYCLE_CONFIG_RUNTIME_MODULE_NAME = "autojs6:lifecycle-config";
    private static final int LIFECYCLE_CONFIG_SCHEMA_VERSION = 1;
    private static final int LIFECYCLE_MAX_CHECKPOINT_BYTES = 64 * 1024;
    private static final long LIFECYCLE_STOP_POLL_INTERVAL_MS = 50L;
    private static final long LIFECYCLE_STOP_GRACE_MS = 1500L;
    private static final String EXECUTION_MODE_INTERACTIVE_LONG_RUNNING = "interactive_long_running";
    private static final String LAUNCH_SURFACE_SCRIPT = "script";
    private static final String LAUNCH_SURFACE_INTERACTIVE_SESSION = "interactive_session";
    private static final String LAUNCH_SURFACE_PACKAGED_LONG_RUNNING = "packaged_long_running";
    private static final String RUNTIME_PROCESS_SUFFIX = ":nodejs_runtime";
    private static final String CANCELLATION_STRATEGY_PROCESS_RESTART = "process_restart";
    private static final long PROCESS_RESTART_AFTER_CANCEL_DELAY_MS = 150L;
    private static final String PROC_SELF_STATUS_PATH = "/proc/self/status";
    private static final String PROC_SELF_FD_PATH = "/proc/self/fd";
    private static final String PROC_SELF_TASK_PATH = "/proc/self/task";
    private static final int PROC_STATUS_MAX_BYTES = 64 * 1024;
    private static final String ERROR_BUSY = "ERR_AUTOJS6_NODE_PLUGIN_BUSY";
    private static final String ERROR_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE";
    private static final String ERROR_CONTRACT_MISMATCH = "ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH";
    private static final String BRIDGE_PROCESS_DEAD = "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD";
    private static final String BRIDGE_PROVIDER_FAILED = "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED";
    private static final String NODE_CAPABILITY_CATALOG_SCHEMA = "autojs6-node-capability-catalog-v1";
    private static final String NODE_CAPABILITY_CATALOG_VERSION = "1.1.1";
    private static final String NODE_CAPABILITY_CATALOG_SHA256 =
            "1a33e3f3df88412ea3cc1dbf125886e0daa01862663157eaf2c5c284857a89e7";
    private static final String KEY_NODE_CAPABILITY_CATALOG_SCHEMA = "nodeCapabilityCatalogSchema";
    private static final String KEY_NODE_CAPABILITY_CATALOG_VERSION = "nodeCapabilityCatalogVersion";
    private static final String KEY_NODE_CAPABILITY_CATALOG_SHA256 = "nodeCapabilityCatalogSha256";
    private static final String KEY_NODE_RUNTIME_KIT_SCHEMA = "nodeRuntimeKitSchema";
    private static final String KEY_NODE_RUNTIME_KIT_VERSION = "nodeRuntimeKitVersion";
    private static final String KEY_NODE_RUNTIME_KIT_SHA256 = "nodeRuntimeKitSha256";
    private static final String KEY_NODE_RUNTIME_KIT_ID = "nodeRuntimeKitId";
    private static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA =
            "embedded_script.runtime_plugin.capability_catalog_schema";
    private static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION =
            "embedded_script.runtime_plugin.capability_catalog_version";
    private static final String DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256 =
            "embedded_script.runtime_plugin.capability_catalog_sha256";
    private static final String DIAGNOSTIC_NODE_RUNTIME_KIT_SCHEMA =
            "embedded_script.runtime_plugin.runtime_kit_schema";
    private static final String DIAGNOSTIC_NODE_RUNTIME_KIT_VERSION =
            "embedded_script.runtime_plugin.runtime_kit_version";
    private static final String DIAGNOSTIC_NODE_RUNTIME_KIT_SHA256 =
            "embedded_script.runtime_plugin.runtime_kit_sha256";
    private static final String DIAGNOSTIC_NODE_RUNTIME_KIT_ID =
            "embedded_script.runtime_plugin.runtime_kit_id";
    private static final String CAPABILITY_PERSISTENT_PROCESS_RUNTIME = "persistentProcessRuntime";
    private static final String CAPABILITY_SINGLE_ACTIVE_BACKPRESSURE = "singleActiveBackpressure";
    private static final String CAPABILITY_PROCESS_RESTART_CANCELLATION = "processRestartCancellation";
    private static final String[] SUPPORTED_ABIS = new String[]{"arm64-v8a", "armeabi-v7a", "x86_64"};
    private static final long BRIDGE_DISPATCH_WAIT_MS = 1000L;
    private static final String[] CAPABILITIES = new String[]{
            NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION,
            NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT,
            NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE,
            NodeJsRuntimeContract.CAPABILITY_HOST_PLAINTEXT_MODULE_SOURCE_MATERIALIZATION,
            NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER,
            CAPABILITY_PERSISTENT_PROCESS_RUNTIME,
            CAPABILITY_SINGLE_ACTIVE_BACKPRESSURE,
            CAPABILITY_PROCESS_RESTART_CANCELLATION,
            NodeJsRuntimeContract.CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT,
    };

    private final Object runtimeLifecycleLock = new Object();
    private final NodeRuntimeExecutionGate executionGate =
            new NodeRuntimeExecutionGate(SystemClock::elapsedRealtime);
    private final AtomicBoolean processRestartScheduled = new AtomicBoolean(false);
    private final Handler processHandler = new Handler(Looper.getMainLooper());
    private volatile RuntimeReadiness lastRuntimeReadiness = RuntimeReadiness.notStarted();

    private final INodeJsRuntimePlugin.Stub binder = new INodeJsRuntimePlugin.Stub() {
        @Override
        public Bundle getRuntimeInfo() {
            return runtimeInfoBundle();
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
                NodeRuntimeExecutionGate.Lease lease = executionGate.tryAcquire(executionId);
                if (lease == null) {
                    return completeImmediateFailure(callback, busyFailureBundle(normalizedRequest, startedAt));
                }
                try {
                    return runScriptActive(normalizedRequest, callback);
                } finally {
                    executionGate.release(lease);
                }
            } finally {
                closeWorkspaceDescriptors(normalizedRequest);
            }
        }

        @Override
        public boolean cancelScript(String executionId) {
            if (!isDedicatedRuntimeProcess()) {
                Log.e(TAG, "Refusing process-restart cancellation outside the dedicated runtime process.");
                return false;
            }
            if (!executionGate.requestCancellation(nonBlank(executionId, ""))) {
                return false;
            }
            scheduleDedicatedRuntimeProcessRestart("cancel:" + executionId);
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
                    Bundle busy = busyFailureBundle(normalizedRequest, startedAt);
                    busy.putBoolean("started", false);
                    busy.putString("status", "busy");
                    busy.putString("reason", "active_execution");
                    return busy;
                }
                try {
                    return prewarmRuntimeBundle(ensurePersistentRuntimeReady(), startedAt);
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

    static NodeTypeScriptStripper.Result prepareTypeScriptEntryForNative(
            String sourceName,
            String source
    ) {
        return NodeTypeScriptStripper.stripIfTypeScript(sourceName, source);
    }

    static NodeTypeScriptStripper.SourceMapResult prepareTypeScriptModuleSourcesForNative(
            Map<String, String> sources
    ) {
        return NodeTypeScriptStripper.stripSourceMap(
                NodeTypeScriptStripper.DIAGNOSTIC_SCOPE_MODULE_SOURCES,
                sources
        );
    }

    static NodeTypeScriptStripper.SourceMapResult prepareTypeScriptRuntimeModuleSourcesForNative(
            Map<String, String> sources
    ) {
        return NodeTypeScriptStripper.stripSourceMap(
                NodeTypeScriptStripper.DIAGNOSTIC_SCOPE_RUNTIME_MODULE_SOURCES,
                sources
        );
    }

    static NodeStartupEnvironmentPolicy.Result prepareNodeStartupEnvironmentForNative(
            Map<String, String> environment
    ) {
        return NodeStartupEnvironmentPolicy.sanitize(environment);
    }

    private Bundle runScriptActive(Bundle request, INodeJsRuntimeCallback callback) {
        long startedAt = SystemClock.elapsedRealtime();
        INodeJsHostCapabilityBroker hostBroker = null;
        INodeJsModuleSourceProvider moduleSourceProvider = null;
        PluginNodeBridgeFileTransportSession liveBridgeSession = null;
        PluginModuleSourceProviderFileTransportSession moduleSourceProviderSession = null;
        PluginWorkspaceArchiveSession workspaceSession = null;
        boolean nativeDispatchStarted = false;
        StreamingOutputSink streamSink = null;
        notifyEvent(callback, NodeJsRuntimeContract.EVENT_STARTED, null, null);
        try {
            // Acquire the request-scoped provider before any operation that can
            // fail so the finally block can release it even for an empty source
            // or a native-library loading failure.
            moduleSourceProvider = moduleSourceProviderFrom(request);
            RuntimeReadiness readiness = ensurePersistentRuntimeReady();
            if (!readiness.ready) {
                Bundle failure = failureBundle(
                        request,
                        startedAt,
                        "Node.js persistent process runtime is unavailable: " + readiness.detail,
                        null,
                        ERROR_UNAVAILABLE
                );
                appendPredispatchNoCommitReceipt(failure);
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                readiness.nativePayload()
                        )
                );
                notifyOutput(callback, failure);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
                return failure;
            }
            String source = request.getString(NodeJsRuntimeContract.KEY_SOURCE, "");
            if (source.isEmpty()) {
                Bundle failure = failureBundle(
                        request,
                        startedAt,
                        "Node.js runtime request source is empty.",
                        null,
                        "ERR_AUTOJS6_NODE_PLUGIN_EMPTY_SOURCE"
                );
                appendPredispatchNoCommitReceipt(failure);
                notifyOutput(callback, failure);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
                return failure;
            }
            String requestedSourceName = nonBlank(
                    request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME),
                    DEFAULT_SOURCE_NAME
            );
            // Entry preparation stays ahead of workspace creation: canonical
            // TypeScript rejections must prove no workspace was materialized
            // (x3e negatives). Hosts recognize such pre-dispatch failures by
            // the unconditional commit_allowed=false marker below.
            NodeTypeScriptStripper.Result typeScriptEntry =
                    prepareTypeScriptEntryForNative(requestedSourceName, source);
            source = typeScriptEntry.source();
            workspaceSession = PluginWorkspaceArchiveSession.hasWorkspaceDescriptors(request)
                    ? PluginWorkspaceArchiveSession.open(getCacheDir(), request)
                    : PluginWorkspaceArchiveSession.openDirect(getCacheDir(), request);
            String sourceName = workspaceSession.mapHostPathToRuntime(requestedSourceName);
            String workingDirectory = workspaceSession.workingDirectory();
            String sandboxRoot = workspaceSession.sandboxRoot();
            Map<String, String> moduleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCES)
            );
            NodeTypeScriptStripper.SourceMapResult typeScriptModuleSources =
                    prepareTypeScriptModuleSourcesForNative(moduleSources);
            moduleSources = typeScriptModuleSources.sources();
            moduleSources = workspaceSession.mapModuleSourceNames(moduleSources);
            Map<String, String> runtimeModuleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES)
            );
            Map<String, String> env = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_VALUES)
            );
            env = workspaceSession.mapEnvironment(env);
            boolean permissionMetadataRequired = nonBlank(
                    runtimeModuleSources.get(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME),
                    null
            ) == null;
            boolean bridgeLimitMetadataRequired = nonBlank(
                    runtimeModuleSources.get(PluginNodeBridgeFileTransportSession.BRIDGE_LIMITS_RUNTIME_MODULE_NAME),
                    null
            ) == null;
            if (moduleSourceProvider != null) {
                moduleSourceProviderSession = new PluginModuleSourceProviderFileTransportSession(
                        getCacheDir(),
                        request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID),
                        moduleSourceProvider,
                        request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L),
                        workspaceSession,
                        initialModuleSourceProviderWireVersion(
                                request.get(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION)
                        )
                );
            }
            PluginModuleSourceProviderFileTransportSession.RuntimeMetadataSnapshot runtimeMetadata =
                    moduleSourceProviderSession == null
                            ? PluginModuleSourceProviderFileTransportSession.readPolicyMetadataWithoutProvider(
                                    workspaceSession,
                                    workingDirectory,
                                    sandboxRoot,
                                    permissionMetadataRequired,
                                    bridgeLimitMetadataRequired,
                                    request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L),
                                    startedAt
                            )
                            : moduleSourceProviderSession.readPolicyMetadataSnapshot(
                                    workingDirectory,
                                    sandboxRoot,
                                    permissionMetadataRequired,
                                    bridgeLimitMetadataRequired,
                                    startedAt
                            );
            hostBroker = hostBrokerFrom(request);
            Bundle hostBrokerInfo = hostBrokerInfo(hostBroker);
            RuntimeModuleInjection runtimeModuleInjection = withPluginRuntimeModules(
                    runtimeModuleSources,
                    request,
                    hostBrokerInfo,
                    workingDirectory,
                    workspaceSession,
                    runtimeMetadata
            );
            runtimeModuleSources = runtimeModuleInjection.sources;
            if (hostBroker != null) {
                liveBridgeSession = new PluginNodeBridgeFileTransportSession(
                        getCacheDir(),
                        request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID),
                        hostBroker,
                        PluginNodeBridgeFileTransportSession.maxPendingBridgeCallsFromRuntimeModule(runtimeModuleSources)
                );
                runtimeModuleSources = withRuntimeModuleSource(
                        runtimeModuleSources,
                        PluginNodeBridgeFileTransportSession.RUNTIME_MODULE_NAME,
                        liveBridgeSession.configJson()
                );
                liveBridgeSession.start();
            }
            if (moduleSourceProvider != null) {
                runtimeModuleSources = withRuntimeModuleSource(
                        runtimeModuleSources,
                        PluginModuleSourceProviderFileTransportSession.RUNTIME_MODULE_NAME,
                        moduleSourceProviderSession.configJson()
                );
                moduleSourceProviderSession.start();
            }

            NodeTypeScriptStripper.SourceMapResult typeScriptRuntimeModuleSources =
                    prepareTypeScriptRuntimeModuleSourcesForNative(runtimeModuleSources);
            runtimeModuleSources = typeScriptRuntimeModuleSources.sources();
            NodeStartupEnvironmentPolicy.Result startupEnvironment =
                    prepareNodeStartupEnvironmentForNative(env);
            env = startupEnvironment.environment();

            long nativeCallStartedAt = SystemClock.elapsedRealtime();
            nativeDispatchStarted = true;
            streamSink = installOutputStreamSink(callback);
            String[] nativePayload;
            try {
                nativePayload = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(
                        source,
                        sourceName,
                        workingDirectory,
                        sandboxRoot,
                        moduleSources,
                        runtimeModuleSources,
                        env,
                        request.getBoolean(NodeJsRuntimeContract.KEY_ESM_EXPERIMENTAL_ENABLED, true),
                        request.getBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, true),
                        request.getBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED, false),
                        request.getBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED, false),
                        request.getBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED, false),
                        request.getBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED, false)
                );
            } finally {
                if (streamSink != null) {
                    clearOutputStreamSink();
                }
            }
            nativePayload = appendNativePayload(
                    nativePayload,
                    nativePayloadFromMap(typeScriptEntry.diagnostics())
            );
            nativePayload = appendNativePayload(
                    nativePayload,
                    nativePayloadFromMap(typeScriptModuleSources.diagnostics())
            );
            nativePayload = appendNativePayload(
                    nativePayload,
                    nativePayloadFromMap(typeScriptRuntimeModuleSources.diagnostics())
            );
            nativePayload = appendNativePayload(
                    nativePayload,
                    nativePayloadFromMap(startupEnvironment.diagnostics())
            );
            long nativeCallFinishedAt = SystemClock.elapsedRealtime();
            commitWorkspaceIfProcessStable(workspaceSession);
            long workspaceCommitFinishedAt = SystemClock.elapsedRealtime();
            nativePayload = appendNativePayload(nativePayload, workspaceSession.nativePayload());
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            long liveBridgeStopFinishedAt = SystemClock.elapsedRealtime();
            String[] moduleSourceProviderNativePayload = moduleSourceProviderSession == null
                    ? new String[]{"embedded_script.runtime_plugin.module_provider.available=false"}
                    : moduleSourceProviderSession.providerNativePayload();
            if (moduleSourceProviderSession != null) {
                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished.");
            }
            String[] liveBridgePayload = liveBridgePayload(liveBridgeSession);
            String[] moduleSourceProviderPayload = moduleSourceProviderSession == null
                    ? new String[]{"embedded_script.runtime_plugin.module_provider.available=false"}
                    : moduleSourceProviderSession.nativePayload();
            String[] queuedBridgePayload = dispatchQueuedBridgeRequests(nativePayload, hostBroker);
            boolean hostBrokerWasUsed =
                    (liveBridgeSession != null && liveBridgeSession.hasDispatchedCalls()) ||
                            queuedBridgePayload.length > 0;
            String[] hostBrokerDiagnosticsPayload = hostBrokerNativeDiagnosticsPayload(
                    hostBroker,
                    hostBrokerInfo,
                    hostBrokerWasUsed
            );
            String[] runtimeModulePayload = runtimeModuleInjection.nativePayload();
            String[] runtimePluginPayload = runtimePluginPayload(hostBroker);
            long diagnosticsFinishedAt = SystemClock.elapsedRealtime();
            LinkedHashMap<String, String> serviceTiming = new LinkedHashMap<>();
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.pre_native.ms",
                    Long.toString(Math.max(0L, nativeCallStartedAt - startedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.native_call.ms",
                    Long.toString(Math.max(0L, nativeCallFinishedAt - nativeCallStartedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.workspace_commit.ms",
                    Long.toString(Math.max(0L, workspaceCommitFinishedAt - nativeCallFinishedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.live_bridge_stop.ms",
                    Long.toString(Math.max(0L, liveBridgeStopFinishedAt - workspaceCommitFinishedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.diagnostics.ms",
                    Long.toString(Math.max(0L, diagnosticsFinishedAt - liveBridgeStopFinishedAt))
            );
            String[] serviceTimingPayload = nativePayloadFromMap(serviceTiming);
            Bundle result = resultBundleFromNativePayload(
                    request,
                    workspaceSession.mapRuntimePathToHost(sourceName),
                    appendNativePayload(
                            appendNativePayload(
                                    appendNativePayload(
                                            appendNativePayload(
                                                    appendNativePayload(nativePayload, liveBridgePayload),
                                                    hostBrokerPayload(hostBroker, hostBrokerInfo)
                                            ),
                                            runtimeModulePayload
                                    ),
                                    queuedBridgePayload
                            ),
                            appendNativePayload(
                                    appendNativePayload(
                                            appendNativePayload(hostBrokerDiagnosticsPayload, runtimePluginPayload),
                                            appendNativePayload(moduleSourceProviderPayload, moduleSourceProviderNativePayload)
                                    ),
                                    serviceTimingPayload
                            )
                    ),
                    startedAt
            );
            result.putString(
                    NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,
                    request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY)
            );
            // Streamed executions already delivered stdout/stderr chunk by
            // chunk; replaying the aggregate here would double the output. If
            // the sink never fired (fd capture unavailable), fall back to the
            // pre-M2.1 terminal replay so callback callers still see output.
            if (streamSink == null || !streamSink.deliveredAnything()) {
                notifyOutput(callback, result);
            }
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return result;
        } catch (Throwable error) {
            // Policy-metadata/provider failures occur before native dispatch. Do not
            // serialize a private workspace in that state: materialized plaintext
            // remains private and the caller receives no partial output archive.
            if (shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)) {
                commitWorkspaceQuietly(workspaceSession);
            }
            String failureErrorCode = ERROR_UNAVAILABLE;
            String[] typeScriptFailurePayload = new String[0];
            if (error instanceof NodeTypeScriptStripper.UnsupportedTypeScriptException) {
                NodeTypeScriptStripper.UnsupportedTypeScriptException typeScriptError =
                        (NodeTypeScriptStripper.UnsupportedTypeScriptException) error;
                failureErrorCode = typeScriptError.errorCode();
                typeScriptFailurePayload = nativePayloadFromMap(typeScriptError.diagnostics());
            } else if (error instanceof PluginModuleSourceProviderFileTransportSession.PolicyMetadataException) {
                failureErrorCode = ((PluginModuleSourceProviderFileTransportSession.PolicyMetadataException) error)
                        .errorCode();
            }
            // The message is the user-facing "one readable sentence"; the
            // machine identity travels in KEY_ERROR_CODE. A boilerplate
            // "plugin execution failed:" prefix only pushed real messages
            // past readable length (M1.5).
            Bundle failure = failureBundle(
                    request,
                    startedAt,
                    messageOf(error),
                    error,
                    failureErrorCode
            );
            failure.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    appendNativePayload(
                            failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                            typeScriptFailurePayload
                    )
            );
            if (workspaceSession != null) {
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                workspaceSession.nativePayload()
                        )
                );
            }
            failure.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    appendNativePayload(
                            failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                            new String[]{
                                    "embedded_script.runtime_plugin.native_dispatch_started=" +
                                            nativeDispatchStarted,
                                    "embedded_script.runtime_plugin.workspace.commit_allowed=" +
                                            nativeDispatchStarted,
                                    "embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false"
                            }
                    )
            );
            if (moduleSourceProviderSession != null) {
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                moduleSourceProviderSession.nativePayload()
                        )
                );
            }
            if (streamSink == null || !streamSink.deliveredAnything()) {
                notifyOutput(callback, failure);
            }
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return failure;
        } finally {
            if (shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)) {
                commitWorkspaceQuietly(workspaceSession);
            }
            if (moduleSourceProviderSession != null) {
                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished after failure.");
            } else {
                cancelModuleSourceProvider(
                        moduleSourceProvider,
                        "Node.js runtime plugin execution finished before module-source transport startup."
                );
            }
            if (workspaceSession != null) {
                workspaceSession.close();
            }
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            destroyHostBroker(hostBroker, "Node.js runtime plugin execution finished.");
        }
    }

    private Bundle runtimeInfoBundle() {
        RuntimeReadiness readiness = lastRuntimeReadiness;
        NodeRuntimeExecutionGate.Snapshot activeExecution = executionGate.snapshot();
        Bundle info = new Bundle();
        info.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_5);
        info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_NATIVE_LIBRARY_NAME, NATIVE_LIBRARY_NAME);
        info.putString(NodeJsRuntimeContract.KEY_BRIDGE_LIBRARY_NAME, BRIDGE_LIBRARY_NAME);
        info.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);
        info.putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, CAPABILITIES.clone());
        info.putString(KEY_NODE_CAPABILITY_CATALOG_SCHEMA, NODE_CAPABILITY_CATALOG_SCHEMA);
        info.putString(KEY_NODE_CAPABILITY_CATALOG_VERSION, NODE_CAPABILITY_CATALOG_VERSION);
        info.putString(KEY_NODE_CAPABILITY_CATALOG_SHA256, NODE_CAPABILITY_CATALOG_SHA256);
        info.putString(KEY_NODE_RUNTIME_KIT_SCHEMA, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SCHEMA);
        info.putString(KEY_NODE_RUNTIME_KIT_VERSION, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
        info.putString(KEY_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
        info.putString(KEY_NODE_RUNTIME_KIT_ID, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_ID);
        info.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        info.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        info.putBoolean("runtimeReady", readiness.ready);
        info.putString("runtimeReadinessDetail", readiness.detail);
        info.putString("processAbi", processAbi());
        info.putStringArray("supportedAbis", SUPPORTED_ABIS.clone());
        info.putString("processModel", "persistent");
        info.putInt("maxConcurrentExecutions", 1);
        info.putInt("queueCapacity", 0);
        info.putBoolean("persistentProcessRuntime", true);
        info.putBoolean("dedicatedRuntimeProcess", isDedicatedRuntimeProcess());
        info.putBoolean("isolatePerExecution", true);
        info.putString("defaultExecutionMode", "one_shot");
        info.putString("cancellationMode", CANCELLATION_STRATEGY_PROCESS_RESTART);
        info.putString("outputMode", "streaming");
        info.putBoolean("streamingOutput", true);
        info.putInt("terminalEventCount", 1);
        info.putString(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID,
                activeExecution == null ? "" : activeExecution.executionId
        );
        info.putLong(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_FOR_MS,
                activeExecution == null ? 0L : activeExecution.activeForMs
        );
        info.putBoolean(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_CANCELLATION_REQUESTED,
                activeExecution != null && activeExecution.cancellationRequested
        );
        info.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(readiness.nativePayload(), runtimeProcessDiagnosticsPayload())
        );
        return info;
    }

    private RuntimeReadiness ensurePersistentRuntimeReady() {
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
            diagnostics.put("process_runtime.cancellation_strategy", CANCELLATION_STRATEGY_PROCESS_RESTART);
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
            return requestContractFailureBundle(
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
        return requestContractFailureBundle(
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

    private Bundle requestContractFailureBundle(
            Bundle request,
            long startedAt,
            String diagnosticScope,
            String message,
            int receivedVersion,
            int expectedVersion
    ) {
        Bundle failure = failureBundle(
                request,
                startedAt,
                message,
                null,
                ERROR_CONTRACT_MISMATCH
        );
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        String diagnosticPrefix = "embedded_script.runtime_plugin." + diagnosticScope;
        diagnostics.put(diagnosticPrefix + ".status", "rejected");
        diagnostics.put(diagnosticPrefix + ".received_version", Integer.toString(receivedVersion));
        diagnostics.put(diagnosticPrefix + ".expected_version", Integer.toString(expectedVersion));
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                lastRuntimeReadiness.nativePayload()
                        ),
                        nativePayloadFromMap(diagnostics)
                )
        );
        return failure;
    }

    private Bundle completeImmediateFailure(INodeJsRuntimeCallback callback, Bundle failure) {
        notifyOutput(callback, failure);
        notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
        return failure;
    }

    private Bundle busyFailureBundle(Bundle request, long startedAt) {
        NodeRuntimeExecutionGate.Snapshot active = executionGate.snapshot();
        boolean draining = executionGate.isClosed() || processRestartScheduled.get();
        String activeExecutionId = active == null ? "" : active.executionId;
        String message = draining
                ? "Node.js runtime plugin process is restarting after cancellation."
                : "Node.js runtime plugin is busy with execution " + activeExecutionId + ".";
        Bundle failure = failureBundle(request, startedAt, message, null, ERROR_BUSY);
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.status",
                draining ? "draining" : "busy"
        );
        diagnostics.put("embedded_script.runtime_plugin.admission.queue_capacity", "0");
        diagnostics.put("embedded_script.runtime_plugin.admission.active_execution_id", activeExecutionId);
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.active_for_ms",
                Long.toString(active == null ? 0L : active.activeForMs)
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.cancellation_requested",
                Boolean.toString(active != null && active.cancellationRequested)
        );
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                lastRuntimeReadiness.nativePayload()
                        ),
                        nativePayloadFromMap(diagnostics)
                )
        );
        return failure;
    }

    private Bundle prewarmRuntimeBundle(RuntimeReadiness readiness, long startedAt) {
        String status = readiness.ready ? "ready" : "failed";
        String reason = readiness.ready ? "runtime_ready" : readiness.detail;
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("embedded_script.prewarm_pool.enabled", "true");
        diagnostics.put("embedded_script.prewarm_pool.status", status);
        diagnostics.put("embedded_script.prewarm_pool.started", Boolean.toString(readiness.ready));
        diagnostics.put("embedded_script.prewarm_pool.process_only", "false");
        diagnostics.put(
                "embedded_script.prewarm_pool.runtime_loaded_before_execution",
                Boolean.toString(readiness.ready)
        );
        diagnostics.put("embedded_script.prewarm_pool.reason", reason);
        diagnostics.put("embedded_script.prewarm_pool.policy", "persistent-process-runtime");

        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, readiness.ready);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, readiness.ready ? 0 : 1);
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_NAME,
                readiness.ready ? null : "NodeJsRuntimePluginPrewarmError"
        );
        result.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, readiness.ready ? null : readiness.detail);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, readiness.ready ? null : ERROR_UNAVAILABLE);
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putBoolean("started", readiness.ready);
        result.putString("status", status);
        result.putString("reason", reason);
        result.putBoolean("processOnly", false);
        result.putBoolean("runtimeLoaded", readiness.ready);
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(readiness.nativePayload(), nativePayloadFromMap(diagnostics)),
                        runtimeProcessDiagnosticsPayload()
                )
        );
        return result;
    }

    private boolean isDedicatedRuntimeProcess() {
        return currentProcessName().equals(getPackageName() + RUNTIME_PROCESS_SUFFIX);
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

    private static String processAbi() {
        String[] abis = Process.is64Bit()
                ? Build.SUPPORTED_64_BIT_ABIS
                : Build.SUPPORTED_32_BIT_ABIS;
        if (abis != null && abis.length > 0) {
            return abis[0];
        }
        return "unknown";
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

    private INodeJsHostCapabilityBroker hostBrokerFrom(Bundle request) {
        IBinder binder = request.getBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER);
        return binder == null ? null : INodeJsHostCapabilityBroker.Stub.asInterface(binder);
    }

    private INodeJsModuleSourceProvider moduleSourceProviderFrom(Bundle request) {
        IBinder binder = request.getBinder(PluginModuleSourceProviderFileTransportSession.KEY_PROVIDER_BINDER);
        return binder == null ? null : INodeJsModuleSourceProvider.Stub.asInterface(binder);
    }

    private static void closeWorkspaceDescriptors(Bundle request) {
        if (request == null) return;
        ParcelFileDescriptor input = workspaceDescriptor(
                request,
                PluginWorkspaceArchiveSession.KEY_INPUT_FD
        );
        ParcelFileDescriptor output = workspaceDescriptor(
                request,
                PluginWorkspaceArchiveSession.KEY_OUTPUT_FD
        );
        closeWorkspaceDescriptor(input);
        if (output != input) {
            closeWorkspaceDescriptor(output);
        }
    }

    @SuppressWarnings("deprecation")
    private static ParcelFileDescriptor workspaceDescriptor(Bundle request, String key) {
        try {
            return request.getParcelable(key);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read Node.js plugin workspace descriptor " + key + ".", error);
            return null;
        }
    }

    private static void closeWorkspaceDescriptor(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to close a Node.js plugin workspace descriptor.", error);
        }
    }

    private static void cancelModuleSourceProvider(INodeJsModuleSourceProvider provider, String reason) {
        if (provider == null) {
            return;
        }
        Thread cancelThread = new Thread(() -> {
            try {
                provider.cancel(reason);
            } catch (Throwable error) {
                Log.w(TAG, "module_source_provider.cancel.failed", error);
            }
        });
        cancelThread.setName("AutoJs6PluginModuleSourceCancel");
        cancelThread.setDaemon(true);
        cancelThread.start();
    }

    private Bundle hostBrokerInfo(INodeJsHostCapabilityBroker hostBroker) {
        if (hostBroker == null) {
            return null;
        }
        try {
            return hostBroker.getBrokerInfo();
        } catch (RemoteException e) {
            Log.w(TAG, "Host capability broker info request failed.", e);
            return null;
        }
    }

    private void destroyHostBroker(INodeJsHostCapabilityBroker hostBroker, String message) {
        if (hostBroker == null) {
            return;
        }
        Bundle reason = new Bundle();
        reason.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message);
        try {
            hostBroker.destroy(reason);
        } catch (RemoteException e) {
            Log.w(TAG, "Host capability broker destroy request failed.", e);
        }
    }

    private String[] hostBrokerPayload(INodeJsHostCapabilityBroker hostBroker, Bundle hostBrokerInfo) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.runtime_plugin.host_broker.received", Boolean.toString(hostBroker != null));
        values.put("embedded_script.runtime_plugin.host_broker.info_available", Boolean.toString(hostBrokerInfo != null));
        values.put("embedded_script.runtime_plugin.host_broker.enabled", Boolean.toString(hostBroker != null));
        values.put("embedded_script.runtime_plugin.host_broker.destroyed", "false");
        values.put("embedded_script.runtime_plugin.host_broker.diagnostics_source", "plugin");
        if (hostBrokerInfo != null) {
            values.put(
                    "embedded_script.runtime_plugin.host_broker.id",
                    nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID), "")
            );
            values.put(
                    "embedded_script.runtime_plugin.host_broker.contract_version",
                    Integer.toString(hostBrokerInfo.getInt(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION, 0))
            );
            String[] modules = hostBrokerInfo.getStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES);
            values.put(
                    "embedded_script.runtime_plugin.host_broker.module_count",
                    Integer.toString(modules == null ? 0 : modules.length)
            );
            values.put(
                    "embedded_script.runtime_plugin.host_broker.engine_info_present",
                    Boolean.toString(nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null) != null)
            );
        }
        return nativePayloadFromMap(values);
    }

    private static String[] liveBridgePayload(PluginNodeBridgeFileTransportSession liveBridgeSession) {
        return liveBridgeSession == null ? new String[0] : liveBridgeSession.nativePayload();
    }

    private String[] runtimePluginPayload(INodeJsHostCapabilityBroker hostBroker) {
        RuntimeReadiness readiness = lastRuntimeReadiness;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.runtime_plugin.enabled", "true");
        values.put("embedded_script.runtime_plugin.diagnostics_source", "plugin");
        values.put("embedded_script.runtime_plugin.contract_version", Integer.toString(NodeJsRuntimeContract.CONTRACT_VERSION));
        values.put("embedded_script.runtime_plugin.package_name", getPackageName());
        values.put("embedded_script.runtime_plugin.service_name", getClass().getName());
        values.put("embedded_script.runtime_plugin.package_version_code", Long.toString(packageVersionCode()));
        values.put("embedded_script.runtime_plugin.runtime_slot", NodeJsPluginIds.VARIANT_NODE_24_5);
        values.put("embedded_script.runtime_plugin.node_version", NODE_VERSION);
        values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA, NODE_CAPABILITY_CATALOG_SCHEMA);
        values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION, NODE_CAPABILITY_CATALOG_VERSION);
        values.put(DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256, NODE_CAPABILITY_CATALOG_SHA256);
        values.put(DIAGNOSTIC_NODE_RUNTIME_KIT_SCHEMA, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SCHEMA);
        values.put(DIAGNOSTIC_NODE_RUNTIME_KIT_VERSION, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
        values.put(DIAGNOSTIC_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
        values.put(DIAGNOSTIC_NODE_RUNTIME_KIT_ID, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_ID);
        values.put("embedded_script.runtime_plugin.capability_count", Integer.toString(CAPABILITIES.length));
        values.put(
                "embedded_script.runtime_plugin.capability_host_broker",
                Boolean.toString(hasCapability(NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER))
        );
        values.put(
                "embedded_script.runtime_plugin.capability_live_bridge",
                Boolean.toString(hasCapability(NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE))
        );
        values.put(
                "embedded_script.runtime_plugin.capability_module_source_provider",
                Boolean.toString(hasCapability(NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER))
        );
        values.put("embedded_script.runtime_plugin.process_name", currentProcessName());
        values.put("embedded_script.runtime_plugin.pid", Integer.toString(Process.myPid()));
        values.put(
                "embedded_script.runtime_plugin.dedicated_process",
                Boolean.toString(isDedicatedRuntimeProcess())
        );
        values.put("embedded_script.runtime_plugin.persistent_process_runtime", "true");
        values.put("embedded_script.runtime_plugin.max_concurrent_executions", "1");
        values.put("embedded_script.runtime_plugin.queue_capacity", "0");
        values.put(
                "embedded_script.runtime_plugin.cancellation_mode",
                CANCELLATION_STRATEGY_PROCESS_RESTART
        );
        values.put(
                "embedded_script.runtime_plugin.runtime_ready",
                Boolean.toString(readiness.ready)
        );
        values.put("embedded_script.runtime_plugin.runtime_readiness_detail", readiness.detail);
        values.put("embedded_script.runtime_plugin.host_broker.attached", Boolean.toString(hostBroker != null));
        return nativePayloadFromMap(values);
    }

    private static String[] runtimeProcessDiagnosticsPayload() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String statusText = readProcText(PROC_SELF_STATUS_PATH, PROC_STATUS_MAX_BYTES);
        long rssKb = procStatusLongValue(statusText, "VmRSS", -1L);
        long threadCount = procStatusLongValue(statusText, "Threads", -1L);
        if (threadCount < 0L) {
            threadCount = directoryEntryCount(PROC_SELF_TASK_PATH);
        }
        long fdCount = directoryEntryCount(PROC_SELF_FD_PATH);

        Runtime javaRuntime = Runtime.getRuntime();
        long javaHeapCommittedBytes = Math.max(0L, javaRuntime.totalMemory());
        long javaHeapFreeBytes = Math.max(0L, javaRuntime.freeMemory());
        long javaHeapUsedBytes = Math.max(0L, javaHeapCommittedBytes - javaHeapFreeBytes);
        long javaHeapMaxBytes = Math.max(0L, javaRuntime.maxMemory());
        long nativeHeapAllocatedBytes = 0L;
        long nativeHeapCommittedBytes = 0L;
        try {
            nativeHeapAllocatedBytes = Math.max(0L, Debug.getNativeHeapAllocatedSize());
            nativeHeapCommittedBytes = Math.max(0L, Debug.getNativeHeapSize());
        } catch (Throwable ignored) {
            // Keep all metric fields numeric even on a platform without native heap diagnostics.
        }

        boolean procStatusReadable = statusText != null && rssKb >= 0L;
        boolean procFdReadable = fdCount >= 0L;
        boolean procThreadCountReadable = threadCount >= 0L;
        values.put("runtime_process.metrics.status",
                procStatusReadable && procFdReadable && procThreadCountReadable ? "ready" : "partial");
        values.put("runtime_process.metrics.source", "plugin_process_procfs");
        values.put("runtime_process.pid", Integer.toString(Process.myPid()));
        values.put("runtime_process.rss_kb", Long.toString(Math.max(0L, rssKb)));
        values.put("runtime_process.thread_count", Long.toString(Math.max(0L, threadCount)));
        values.put("runtime_process.fd_count", Long.toString(Math.max(0L, fdCount)));
        values.put("runtime_process.java_heap_used_kb", Long.toString(bytesToKb(javaHeapUsedBytes)));
        values.put("runtime_process.java_heap_committed_kb", Long.toString(bytesToKb(javaHeapCommittedBytes)));
        values.put("runtime_process.java_heap_max_kb", Long.toString(bytesToKb(javaHeapMaxBytes)));
        values.put("runtime_process.native_heap_allocated_kb", Long.toString(bytesToKb(nativeHeapAllocatedBytes)));
        values.put("runtime_process.native_heap_committed_kb", Long.toString(bytesToKb(nativeHeapCommittedBytes)));
        values.put("runtime_process.proc_status_readable", Boolean.toString(procStatusReadable));
        values.put("runtime_process.proc_fd_readable", Boolean.toString(procFdReadable));
        values.put("runtime_process.proc_thread_count_readable", Boolean.toString(procThreadCountReadable));
        values.put("runtime_process.sample_elapsed_realtime_ms", Long.toString(SystemClock.elapsedRealtime()));
        return nativePayloadFromMap(values);
    }

    private static long bytesToKb(long bytes) {
        return Math.max(0L, bytes) / 1024L;
    }

    private static int directoryEntryCount(String path) {
        try {
            String[] entries = new File(path).list();
            return entries == null ? -1 : entries.length;
        } catch (SecurityException ignored) {
            return -1;
        }
    }

    private static String readProcText(String path, int maxBytes) {
        if (path == null || path.isEmpty() || maxBytes <= 0) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[maxBytes];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    break;
                }
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    static long procStatusLongValue(String statusText, String fieldName, long fallback) {
        if (statusText == null || fieldName == null || fieldName.isEmpty()) {
            return fallback;
        }
        String prefix = fieldName + ":";
        int lineStart = 0;
        while (lineStart < statusText.length()) {
            int lineEnd = statusText.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = statusText.length();
            }
            if (statusText.regionMatches(lineStart, prefix, 0, prefix.length())) {
                int valueStart = lineStart + prefix.length();
                while (valueStart < lineEnd && Character.isWhitespace(statusText.charAt(valueStart))) {
                    valueStart += 1;
                }
                int valueEnd = valueStart;
                while (valueEnd < lineEnd && Character.isDigit(statusText.charAt(valueEnd))) {
                    valueEnd += 1;
                }
                if (valueEnd > valueStart) {
                    try {
                        return Long.parseLong(statusText.substring(valueStart, valueEnd));
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                }
                return fallback;
            }
            lineStart = lineEnd + 1;
        }
        return fallback;
    }

    @SuppressWarnings("deprecation")
    private long packageVersionCode() {
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

    private static boolean hasCapability(String capability) {
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

    private RuntimeModuleInjection withPluginRuntimeModules(
            Map<String, String> runtimeModuleSources,
            Bundle request,
            Bundle hostBrokerInfo,
            String workingDirectory,
            PluginWorkspaceArchiveSession workspaceSession,
            PluginModuleSourceProviderFileTransportSession.RuntimeMetadataSnapshot runtimeMetadata
    ) {
        String engineInfo = preferredEngineInfo(runtimeModuleSources, request, hostBrokerInfo);
        if (workspaceSession != null) {
            engineInfo = workspaceSession.mapEngineInfo(engineInfo);
        }
        // Workspace path mapping may rewrite a caller-supplied engine-info
        // module, so it is re-injected here. The diagnostics must keep the
        // original discovery source (existing/host_broker/request), not report
        // the pre-populated map as "existing".
        RuntimeModuleInjection injection = RuntimeModuleInjection.from(runtimeModuleSources);
        injection = injection.withReplacedRuntimeModule(
                "engine_info",
                ENGINE_INFO_RUNTIME_MODULE_NAME,
                engineInfo,
                preferredEngineInfoSource(runtimeModuleSources, request, hostBrokerInfo)
        );
        String injectedEngineInfo = nonBlank(injection.sources.get(ENGINE_INFO_RUNTIME_MODULE_NAME), engineInfo);
        injection = injection.withRuntimeModule(
                "host_app_info",
                HOST_APP_INFO_RUNTIME_MODULE_NAME,
                hostAppInfoRuntimeModuleSource(injectedEngineInfo),
                "bridge_engine_info"
        );
        injection = injection.withRuntimeModule(
                "device_info",
                DEVICE_INFO_RUNTIME_MODULE_NAME,
                deviceInfoRuntimeModuleSource(),
                "plugin_context"
        );
        injection = injection.withRuntimeModule(
                "bridge_permissions",
                NodeBridgePermissionManifest.RUNTIME_MODULE_NAME,
                NodeBridgePermissionManifest.INSTANCE.runtimeModuleSourceForMetadata(
                        runtimeMetadata == null ? null : runtimeMetadata.workingProjectJson,
                        runtimeMetadata == null ? null : runtimeMetadata.workingPackageJson,
                        runtimeMetadata == null ? null : runtimeMetadata.sandboxProjectJson,
                        runtimeMetadata == null ? null : runtimeMetadata.sandboxPackageJson,
                        BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED
                ),
                "exact_metadata_snapshot"
        );
        injection = injection.withRuntimeModule(
                "bridge_limits",
                PluginNodeBridgeFileTransportSession.BRIDGE_LIMITS_RUNTIME_MODULE_NAME,
                bridgeLimitsRuntimeModuleSource(runtimeMetadata),
                "exact_metadata_snapshot"
        );
        injection = injection.withRuntimeModule(
                "lifecycle_config",
                LIFECYCLE_CONFIG_RUNTIME_MODULE_NAME,
                lifecycleConfigRuntimeModuleSource(injectedEngineInfo, workingDirectory),
                "bridge_engine_info"
        );
        return injection;
    }

    private static String preferredEngineInfo(
            Map<String, String> runtimeModuleSources,
            Bundle request,
            Bundle hostBrokerInfo
    ) {
        String existing = runtimeModuleSources == null
                ? null
                : nonBlank(runtimeModuleSources.get(ENGINE_INFO_RUNTIME_MODULE_NAME), null);
        if (existing != null) {
            return existing;
        }
        String hostBrokerEngineInfo = hostBrokerInfo == null
                ? null
                : nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
        if (hostBrokerEngineInfo != null) {
            return hostBrokerEngineInfo;
        }
        return nonBlank(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
    }

    private static String preferredEngineInfoSource(
            Map<String, String> runtimeModuleSources,
            Bundle request,
            Bundle hostBrokerInfo
    ) {
        if (runtimeModuleSources != null
                && nonBlank(runtimeModuleSources.get(ENGINE_INFO_RUNTIME_MODULE_NAME), null) != null) {
            return "existing";
        }
        if (hostBrokerInfo != null
                && nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null) != null) {
            return "host_broker";
        }
        if (nonBlank(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null) != null) {
            return "request";
        }
        return "missing";
    }

    private static String hostAppInfoRuntimeModuleSource(String engineInfoJson) {
        JSONObject engineInfo = parseJsonObject(engineInfoJson);
        if (engineInfo == null) {
            return null;
        }
        try {
            return new JSONObject()
                    .put("packageName", stringJsonValue(engineInfo, "packageName", ""))
                    .put("versionName", stringJsonValue(engineInfo, "versionName", ""))
                    .put("versionCode", longJsonValue(engineInfo, "versionCode", 0L))
                    .toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String deviceInfoRuntimeModuleSource() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        double density = metrics == null || !Double.isFinite(metrics.density) || metrics.density <= 0.0f
                ? 1.0d
                : metrics.density;
        try {
            return new JSONObject()
                    .put("sdkInt", Build.VERSION.SDK_INT)
                    .put("width", metrics == null ? 0 : Math.max(0, metrics.widthPixels))
                    .put("height", metrics == null ? 0 : Math.max(0, metrics.heightPixels))
                    .put("density", density)
                    .toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String bridgeLimitsRuntimeModuleSource(
            PluginModuleSourceProviderFileTransportSession.RuntimeMetadataSnapshot metadata
    ) {
        return BridgeLimitPolicy.fromMetadata(
                metadata == null ? null : metadata.workingProjectJson,
                metadata == null ? null : metadata.workingPackageJson
        ).toJson();
    }

    private String lifecycleConfigRuntimeModuleSource(String engineInfoJson, String workingDirectory) {
        JSONObject engineInfo = parseJsonObject(engineInfoJson);
        if (engineInfo == null) {
            return null;
        }
        String packageName = stringJsonValue(engineInfo, "packageName", getPackageName());
        String cwd = canonicalPath(stringJsonValue(engineInfo, "cwd", workingDirectory));
        String executionMode = stringJsonValue(engineInfo, "executionMode", "");
        String launchSurface = stringJsonValue(engineInfo, "launchSurface", LAUNCH_SURFACE_SCRIPT);
        boolean checkpointEnabled = EXECUTION_MODE_INTERACTIVE_LONG_RUNNING.equals(executionMode) &&
                (LAUNCH_SURFACE_INTERACTIVE_SESSION.equals(launchSurface) ||
                        LAUNCH_SURFACE_PACKAGED_LONG_RUNNING.equals(launchSurface));
        try {
            return new JSONObject()
                    .put("schemaVersion", LIFECYCLE_CONFIG_SCHEMA_VERSION)
                    .put("executionId", stringJsonValue(engineInfo, "id", ""))
                    .put("sourceName", stringJsonValue(engineInfo, "sourceName", ""))
                    .put("packageName", packageName)
                    .put("workingDirectory", cwd)
                    .put("projectKey", sha256(packageName + "\n" + cwd).substring(0, 32))
                    .put("executionMode", executionMode)
                    .put("launchSurface", launchSurface)
                    .put("checkpoint", new JSONObject()
                            .put("enabled", checkpointEnabled)
                            .put("maxBytes", LIFECYCLE_MAX_CHECKPOINT_BYTES)
                            .put("automaticRestart", false)
                            .put("restartPolicy", "never"))
                    .put("stop", new JSONObject()
                            .put("enabled", false)
                            .put("requestPath", "")
                            .put("pollIntervalMs", LIFECYCLE_STOP_POLL_INTERVAL_MS)
                            .put("graceMs", LIFECYCLE_STOP_GRACE_MS))
                    .toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String canonicalPath(String path) {
        String normalized = nonBlank(path, null);
        if (normalized == null) {
            File filesDir = getApplicationContext().getFilesDir();
            normalized = filesDir == null ? "/" : filesDir.getAbsolutePath();
        }
        try {
            return new File(normalized).getCanonicalPath();
        } catch (IOException ignored) {
            return normalized;
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }

    private static JSONObject parseJsonObject(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readTextIfFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) Math.min(file.length(), Integer.MAX_VALUE)];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String stringJsonValue(JSONObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return String.valueOf(json.opt(key));
    }

    private static long longJsonValue(JSONObject json, String key, long fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        Object value = json.opt(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String[] hostBrokerNativeDiagnosticsPayload(
            INodeJsHostCapabilityBroker hostBroker,
            Bundle hostBrokerInfo,
            boolean refresh
    ) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (hostBroker == null) {
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "false");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "missing_broker");
            return nativePayloadFromMap(values);
        }
        if (!refresh && hostBrokerInfo != null) {
            String[] nativePayload = hostBrokerInfo.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "true");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "ok");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_snapshot", "broker_info");
            return appendNativePayload(nativePayloadFromMap(values), nativePayload);
        }
        try {
            Bundle diagnostics = hostBroker.getNativeDiagnostics();
            String[] nativePayload = diagnostics == null
                    ? null
                    : diagnostics.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "true");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "ok");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_snapshot", "post_dispatch");
            return appendNativePayload(nativePayloadFromMap(values), nativePayload);
        } catch (RemoteException e) {
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "false");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "remote_error");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_error", messageOf(e));
            return nativePayloadFromMap(values);
        }
    }

    private String[] dispatchQueuedBridgeRequests(String[] nativePayload, INodeJsHostCapabilityBroker hostBroker) {
        Map<String, String> nativeValues = parseNativePayload(nativePayload);
        List<JSONObject> requests = bridgeRequestsFromJson(nativeValues.get("embedded_script.bridge_requests_json"));
        if (requests.isEmpty()) {
            return new String[0];
        }
        if (hostBroker == null) {
            return bridgeDispatchPayload(
                    requests.size(),
                    requests.size(),
                    0,
                    "missing_broker",
                    bridgeFailureResponsesJson(requests, "AutoJs6 host capability broker is not available.", BRIDGE_PROCESS_DEAD)
            );
        }

        CountDownLatch latch = new CountDownLatch(requests.size());
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<String> responseJsonValues = Collections.synchronizedList(new ArrayList<>());

        for (JSONObject request : requests) {
            AtomicBoolean responded = new AtomicBoolean(false);
            BridgeRequestIdentity identity = bridgeRequestIdentity(request);
            INodeJsHostCapabilityCallback callback = new INodeJsHostCapabilityCallback.Stub() {
                @Override
                public void onResponse(Bundle response) {
                    if (!responded.compareAndSet(false, true)) {
                        return;
                    }
                    String responseJson = response == null
                            ? bridgeFailureResponseJson(
                            identity,
                            "AutoJs6 host capability broker returned an empty response.",
                            BRIDGE_PROVIDER_FAILED
                    )
                            : nonBlank(
                            response.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON),
                            bridgeFailureResponseJson(
                                    identity,
                                    response.getString(
                                            NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE,
                                            "AutoJs6 host capability broker returned a malformed response."
                                    ),
                                    BRIDGE_PROVIDER_FAILED
                            )
                    );
                    responseJsonValues.add(responseJson);
                    completed.incrementAndGet();
                    if (!bridgeResponseOk(responseJson)) {
                        failed.incrementAndGet();
                    }
                    latch.countDown();
                }
            };
            try {
                Bundle brokerRequest = new Bundle();
                brokerRequest.putString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON, request.toString());
                hostBroker.dispatch(brokerRequest, callback);
            } catch (Throwable error) {
                if (responded.compareAndSet(false, true)) {
                    responseJsonValues.add(bridgeFailureResponseJson(identity, messageOf(error), BRIDGE_PROVIDER_FAILED));
                    completed.incrementAndGet();
                    failed.incrementAndGet();
                    latch.countDown();
                }
            }
        }

        try {
            latch.await(BRIDGE_DISPATCH_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int pending = Math.max(0, (int) latch.getCount());
        return bridgeDispatchPayload(
                completed.get(),
                failed.get(),
                pending,
                pending == 0 ? "done" : "pending",
                bridgeResponsesJson(responseJsonValues)
        );
    }

    private static List<JSONObject> bridgeRequestsFromJson(String requestsJson) {
        if (requestsJson == null || requestsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(requestsJson);
            ArrayList<JSONObject> requests = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject request = array.optJSONObject(index);
                if (request != null) {
                    requests.add(request);
                }
            }
            return requests;
        } catch (Throwable error) {
            return Collections.emptyList();
        }
    }

    private static String[] bridgeDispatchPayload(
            int completed,
            int failed,
            int pending,
            String status,
            String responsesJson
    ) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.bridge_dispatch_count", Integer.toString(completed));
        values.put("embedded_script.bridge_dispatch_failed_count", Integer.toString(failed));
        values.put("embedded_script.bridge_dispatch_pending_count", Integer.toString(pending));
        values.put("embedded_script.bridge_dispatch_status", status);
        values.put("embedded_script.bridge_responses_json", responsesJson);
        values.put("embedded_script.runtime_plugin.host_broker.queued_dispatch_count", Integer.toString(completed));
        values.put("embedded_script.runtime_plugin.host_broker.queued_dispatch_failed_count", Integer.toString(failed));
        values.put("embedded_script.runtime_plugin.host_broker.queued_dispatch_pending_count", Integer.toString(pending));
        values.put("embedded_script.runtime_plugin.host_broker.queued_dispatch_status", status);
        return nativePayloadFromMap(values);
    }

    private static String bridgeResponsesJson(List<String> responseJsonValues) {
        JSONArray responses = new JSONArray();
        synchronized (responseJsonValues) {
            for (String responseJson : responseJsonValues) {
                try {
                    responses.put(new JSONObject(responseJson));
                } catch (Throwable ignored) {
                    responses.put(JSONObject.NULL);
                }
            }
        }
        return responses.toString();
    }

    private static String bridgeFailureResponsesJson(List<JSONObject> requests, String message, String code) {
        JSONArray responses = new JSONArray();
        for (JSONObject request : requests) {
            try {
                responses.put(new JSONObject(bridgeFailureResponseJson(bridgeRequestIdentity(request), message, code)));
            } catch (Throwable ignored) {
                responses.put(JSONObject.NULL);
            }
        }
        return responses.toString();
    }

    private static String bridgeFailureResponseJson(BridgeRequestIdentity request, String message, String code) {
        try {
            return new JSONObject()
                    .put("id", request.id)
                    .put("ok", false)
                    .put("error", new JSONObject()
                            .put("name", "Error")
                            .put("message", nonBlank(message, "AutoJs6 host capability broker dispatch failed."))
                            .put("code", nonBlank(code, BRIDGE_PROVIDER_FAILED))
                            .put("category", BRIDGE_PROVIDER_FAILED.equals(code) ? "provider-failed" : "process-dead")
                            .put("module", request.module)
                            .put("method", request.method)
                    )
                    .toString();
        } catch (Throwable ignored) {
            return "{\"id\":\"invalid\",\"ok\":false,\"error\":{\"name\":\"Error\",\"message\":\"AutoJs6 host capability broker dispatch failed.\",\"code\":\""
                    + BRIDGE_PROVIDER_FAILED
                    + "\",\"category\":\"provider-failed\"}}";
        }
    }

    private static BridgeRequestIdentity bridgeRequestIdentity(JSONObject request) {
        return new BridgeRequestIdentity(
                request == null ? "invalid" : nonBlank(request.optString("id"), "invalid"),
                request == null ? "" : nonBlank(request.optString("module"), ""),
                request == null ? "" : nonBlank(request.optString("method"), "")
        );
    }

    private static boolean bridgeResponseOk(String responseJson) {
        try {
            return new JSONObject(responseJson).optBoolean("ok", false);
        } catch (Throwable error) {
            return false;
        }
    }

    private static String[] appendNativePayload(String[] base, String[] extra) {
        int baseLength = base == null ? 0 : base.length;
        int extraLength = extra == null ? 0 : extra.length;
        String[] result = new String[baseLength + extraLength];
        if (baseLength > 0) {
            System.arraycopy(base, 0, result, 0, baseLength);
        }
        if (extraLength > 0) {
            System.arraycopy(extra, 0, result, baseLength, extraLength);
        }
        return result;
    }

    private static void appendPredispatchNoCommitReceipt(Bundle failure) {
        String[] receipt = new String[]{
                "embedded_script.runtime_plugin.native_dispatch_started=false",
                "embedded_script.runtime_plugin.workspace.commit_allowed=false",
                "embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false"
        };
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                        receipt
                )
        );
    }

    private static String[] nativePayloadFromMap(Map<String, String> values) {
        String[] payload = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            payload[index++] = entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue());
        }
        return payload;
    }

    private Bundle resultBundleFromNativePayload(
            Bundle request,
            String sourceName,
            String[] nativePayload,
            long startedAt
    ) {
        Map<String, String> nativeValues = parseNativePayload(nativePayload);
        String status = stringValue(nativeValues, "embedded_script.status");
        boolean nativeFailure = "failed".equals(status) || "skipped".equals(status);
        boolean scriptSucceeded = booleanValue(nativeValues, "embedded_script.succeeded");
        boolean succeeded = !nativeFailure && scriptSucceeded;

        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID));
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, succeeded);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, intValue(nativeValues, "embedded_script.exit_code", succeeded ? 0 : 1));
        result.putString(NodeJsRuntimeContract.KEY_RESULT_TEXT, emptyToNull(stringValue(nativeValues, "embedded_script.result_text")));
        result.putString(NodeJsRuntimeContract.KEY_STDOUT, preferMoreComplete(
                stringValue(nativeValues, "embedded_script.stdout"),
                captureText(nativeValues, "stdout_capture")
        ));
        result.putString(NodeJsRuntimeContract.KEY_STDERR, preferMoreComplete(
                stringValue(nativeValues, "embedded_script.stderr"),
                captureText(nativeValues, "stderr_capture")
        ));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_NAME, emptyToNull(stringValue(nativeValues, "embedded_script.error_name")));
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_MESSAGE,
                emptyToNull(nativeFailure
                        ? nonBlank(stringValue(nativeValues, "embedded_script.detail"), "Embedded script native lifecycle failed.")
                        : stringValue(nativeValues, "embedded_script.error_message"))
        );
        result.putString(NodeJsRuntimeContract.KEY_ERROR_STACK, emptyToNull(stringValue(nativeValues, "embedded_script.error_stack")));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, emptyToNull(stringValue(nativeValues, "embedded_script.error_code")));
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, longValue(nativeValues, "timing.total.ms", elapsedSince(startedAt)));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, sourceName);
        result.putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, booleanValue(nativeValues, "embedded_script.timed_out"));
        result.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, emptyToNull(
                nonBlank(stringValue(nativeValues, "embedded_script.working_directory"),
                        request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY))
        ));
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(nativePayload, runtimeProcessDiagnosticsPayload())
        );
        return result;
    }

    private Bundle failureBundle(
            Bundle request,
            long startedAt,
            String message,
            Throwable error,
            String errorCode
    ) {
        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID));
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, 1);
        result.putString(NodeJsRuntimeContract.KEY_STDOUT, "");
        result.putString(NodeJsRuntimeContract.KEY_STDERR, "");
        result.putString(NodeJsRuntimeContract.KEY_ERROR_NAME, error == null ? "NodeJsRuntimePluginError" : error.getClass().getSimpleName());
        result.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_STACK, error == null ? null : Log.getStackTraceString(error));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, errorCode);
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME, DEFAULT_SOURCE_NAME));
        result.putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, false);
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(runtimePluginPayload(null), runtimeProcessDiagnosticsPayload())
        );
        return result;
    }

    private StreamingOutputSink installOutputStreamSink(INodeJsRuntimeCallback callback) {
        if (callback == null) {
            return null;
        }
        StreamingOutputSink sink = new StreamingOutputSink(callback);
        NativeNodeEmbeddedRuntimeBridge.setOutputStreamSink(this, sink);
        return sink;
    }

    private void clearOutputStreamSink() {
        NativeNodeEmbeddedRuntimeBridge.setOutputStreamSink(this, null);
    }

    /**
     * Forwards native fd/pipe chunks to the host callback while the script is
     * still running. Called on native pipe-reader threads; stdout and stderr
     * each keep their own decoder because they arrive on separate threads.
     */
    private final class StreamingOutputSink implements NativeNodeEmbeddedRuntimeBridge.OutputSink {

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

    private void notifyOutput(INodeJsRuntimeCallback callback, Bundle result) {
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        if (!stdout.isEmpty()) {
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDOUT, "INFO", stdout);
        }
        String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
        if (!stderr.isEmpty()) {
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDERR, "ERROR", stderr);
        }
    }

    private void notifyEvent(INodeJsRuntimeCallback callback, String type, String level, String text) {
        if (callback == null) {
            return;
        }
        Bundle event = new Bundle();
        event.putString(NodeJsRuntimeContract.KEY_EVENT_TYPE, type);
        if (level != null) {
            event.putString(NodeJsRuntimeContract.KEY_EVENT_LEVEL, level);
        }
        if (text != null) {
            event.putString(NodeJsRuntimeContract.KEY_EVENT_TEXT, text);
        }
        try {
            callback.onEvent(event);
        } catch (RemoteException e) {
            Log.w(TAG, "Runtime callback failed.", e);
        }
    }

    private static Map<String, String> stringMapFromArrays(String[] names, String[] values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (names == null || values == null || names.length != values.length) {
            return result;
        }
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            if (name != null && !name.isEmpty()) {
                result.put(name, values[index] == null ? "" : values[index]);
            }
        }
        return result;
    }

    private void commitWorkspaceIfProcessStable(PluginWorkspaceArchiveSession session) throws IOException {
        if (session == null || processRestartScheduled.get() || executionGate.isClosed()) return;
        session.commit();
    }

    static boolean shouldCommitWorkspaceAfterFailure(boolean nativeDispatchStarted) {
        return nativeDispatchStarted;
    }

    private void commitWorkspaceQuietly(PluginWorkspaceArchiveSession session) {
        if (session == null) return;
        try {
            commitWorkspaceIfProcessStable(session);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to checkpoint the Node.js plugin workspace.", error);
        }
    }

    private static Map<String, String> withRuntimeModuleSource(
            Map<String, String> base,
            String name,
            String source
    ) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (base != null) {
            result.putAll(base);
        }
        result.put(name, source == null ? "" : source);
        return result;
    }

    private static final class RuntimeModuleInjection {
        final Map<String, String> sources;
        final LinkedHashMap<String, String> diagnostics;

        private RuntimeModuleInjection(Map<String, String> sources, LinkedHashMap<String, String> diagnostics) {
            this.sources = sources == null ? Collections.emptyMap() : sources;
            this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : diagnostics;
        }

        static RuntimeModuleInjection from(Map<String, String> sources) {
            return new RuntimeModuleInjection(sources, new LinkedHashMap<>());
        }

        RuntimeModuleInjection withRuntimeModule(
                String diagnosticName,
                String moduleName,
                String source,
                String sourceLabel
        ) {
            String existing = nonBlank(sources.get(moduleName), null);
            LinkedHashMap<String, String> nextDiagnostics = new LinkedHashMap<>(diagnostics);
            if (existing != null) {
                putDiagnostics(nextDiagnostics, diagnosticName, true, "existing");
                return new RuntimeModuleInjection(sources, nextDiagnostics);
            }
            String normalizedSource = nonBlank(source, null);
            if (normalizedSource == null) {
                putDiagnostics(nextDiagnostics, diagnosticName, false, "missing");
                return new RuntimeModuleInjection(sources, nextDiagnostics);
            }
            putDiagnostics(nextDiagnostics, diagnosticName, true, nonBlank(sourceLabel, "plugin"));
            return new RuntimeModuleInjection(
                    withRuntimeModuleSource(sources, moduleName, normalizedSource),
                    nextDiagnostics
            );
        }

        /**
         * Injects {@code source} even when the module is already present
         * (workspace mapping may have rewritten it) and reports the caller's
         * {@code sourceLabel} instead of collapsing to "existing".
         */
        RuntimeModuleInjection withReplacedRuntimeModule(
                String diagnosticName,
                String moduleName,
                String source,
                String sourceLabel
        ) {
            LinkedHashMap<String, String> nextDiagnostics = new LinkedHashMap<>(diagnostics);
            String normalizedSource = nonBlank(source, null);
            if (normalizedSource == null) {
                putDiagnostics(nextDiagnostics, diagnosticName, false, "missing");
                return new RuntimeModuleInjection(sources, nextDiagnostics);
            }
            putDiagnostics(nextDiagnostics, diagnosticName, true, nonBlank(sourceLabel, "plugin"));
            return new RuntimeModuleInjection(
                    withRuntimeModuleSource(sources, moduleName, normalizedSource),
                    nextDiagnostics
            );
        }

        String[] nativePayload() {
            return nativePayloadFromMap(diagnostics);
        }

        private static void putDiagnostics(
                LinkedHashMap<String, String> values,
                String name,
                boolean present,
                String source
        ) {
            values.put(
                    "embedded_script.runtime_plugin.runtime_module." + name + "_present",
                    Boolean.toString(present)
            );
            values.put(
                    "embedded_script.runtime_plugin.runtime_module." + name + "_source",
                    source == null ? "missing" : source
            );
        }
    }

    private static final class BridgeLimitPolicy {
        private static final String PROJECT_JSON = "project.json";
        private static final String PACKAGE_JSON = "package.json";
        private static final String[] FIELD_NAMES = {
                "maxPendingBridgeCalls",
                "maxImageHandles",
                "maxShellCommands",
                "maxNetworkRequests",
                "maxWebSocketConnections",
                "accessibilityQueriesPerSecond",
        };
        private static final int[] DEFAULT_VALUES = {32, 32, 2, 16, 2, 20};
        private static final int[] MIN_VALUES = {1, 0, 0, 0, 0, 1};
        private static final int[] HARD_MAX_VALUES = {128, 128, 4, 32, 4, 60};

        private final int[] values;
        private final List<String> sources;
        private final List<String> warnings;

        private BridgeLimitPolicy(int[] values, List<String> sources, List<String> warnings) {
            this.values = values;
            this.sources = sources == null || sources.isEmpty()
                    ? Collections.singletonList("default")
                    : sources;
            this.warnings = warnings == null ? Collections.emptyList() : warnings;
        }

        static BridgeLimitPolicy fromMetadata(String projectJson, String packageJson) {
            Builder builder = new Builder();
            List<String> warnings = new ArrayList<>();
            collectProjectJson(projectJson, builder, warnings);
            collectPackageJson(packageJson, builder, warnings);
            return builder.build(warnings);
        }

        String toJson() {
            try {
                JSONObject json = new JSONObject()
                        .put("version", 1)
                        .put("sources", new JSONArray(sources))
                        .put("warnings", new JSONArray(warnings));
                JSONObject defaults = new JSONObject();
                JSONObject hardMax = new JSONObject();
                for (int index = 0; index < FIELD_NAMES.length; index++) {
                    json.put(FIELD_NAMES[index], values[index]);
                    defaults.put(FIELD_NAMES[index], DEFAULT_VALUES[index]);
                    hardMax.put(FIELD_NAMES[index], HARD_MAX_VALUES[index]);
                }
                json.put("defaults", defaults);
                json.put("hardMax", hardMax);
                return json.toString();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void collectProjectJson(String text, Builder builder, List<String> warnings) {
            JSONObject json = parseBridgeLimitJsonObject(text, PROJECT_JSON, warnings);
            if (json == null) {
                return;
            }
            JSONObject node = json.optJSONObject("node");
            collectLimitObject(node == null ? null : node.optJSONObject("bridgeLimits"), "project.json:node.bridgeLimits", builder);
            collectLimitObject(node == null ? null : node.optJSONObject("limits"), "project.json:node.limits", builder);
        }

        private static void collectPackageJson(String text, Builder builder, List<String> warnings) {
            JSONObject json = parseBridgeLimitJsonObject(text, PACKAGE_JSON, warnings);
            if (json == null) {
                return;
            }
            JSONObject autojs6 = json.optJSONObject("autojs6");
            collectLimitObject(autojs6 == null ? null : autojs6.optJSONObject("bridgeLimits"), "package.json:autojs6.bridgeLimits", builder);
            JSONObject node = autojs6 == null ? null : autojs6.optJSONObject("node");
            collectLimitObject(node == null ? null : node.optJSONObject("bridgeLimits"), "package.json:autojs6.node.bridgeLimits", builder);
            collectLimitObject(node == null ? null : node.optJSONObject("limits"), "package.json:autojs6.node.limits", builder);
        }

        private static JSONObject parseBridgeLimitJsonObject(String text, String source, List<String> warnings) {
            if (text == null || text.isEmpty()) {
                return null;
            }
            try {
                return new JSONObject(text);
            } catch (Throwable error) {
                warnings.add(source + " bridge limits could not be parsed: " + messageOf(error));
                return null;
            }
        }

        private static void collectLimitObject(JSONObject json, String source, Builder builder) {
            if (json == null) {
                return;
            }
            boolean consumed = false;
            for (int index = 0; index < FIELD_NAMES.length; index++) {
                String name = FIELD_NAMES[index];
                if (!json.has(name) || json.isNull(name)) {
                    continue;
                }
                consumed = true;
                builder.set(index, json.opt(name), source);
            }
            if (consumed) {
                builder.addSource(source);
            }
        }

        private static final class Builder {
            private final int[] values = DEFAULT_VALUES.clone();
            private final List<String> sources = new ArrayList<>();
            private final List<String> warnings = new ArrayList<>();

            void addSource(String source) {
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }

            void set(int index, Object rawValue, String source) {
                Integer parsed = parseInt(rawValue);
                String name = FIELD_NAMES[index];
                if (parsed == null) {
                    warnings.add(source + "." + name + " ignored: expected an integer.");
                    return;
                }
                if (parsed < MIN_VALUES[index] || parsed > HARD_MAX_VALUES[index]) {
                    warnings.add(
                            source + "." + name + " ignored: " + parsed + " is outside " +
                                    MIN_VALUES[index] + ".." + HARD_MAX_VALUES[index] + "."
                    );
                    return;
                }
                values[index] = parsed;
            }

            BridgeLimitPolicy build(List<String> extraWarnings) {
                List<String> mergedWarnings = new ArrayList<>(warnings);
                if (extraWarnings != null) {
                    for (String warning : extraWarnings) {
                        if (!mergedWarnings.contains(warning)) {
                            mergedWarnings.add(warning);
                        }
                    }
                }
                return new BridgeLimitPolicy(values.clone(), new ArrayList<>(sources), mergedWarnings);
            }

            private static Integer parseInt(Object rawValue) {
                if (rawValue instanceof Integer) {
                    return (Integer) rawValue;
                }
                if (rawValue instanceof Long) {
                    long value = (Long) rawValue;
                    return value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? null : (int) value;
                }
                if (rawValue instanceof Number) {
                    double value = ((Number) rawValue).doubleValue();
                    return Double.isFinite(value) ? (int) value : null;
                }
                if (rawValue instanceof String) {
                    try {
                        return Integer.parseInt(((String) rawValue).trim());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    private static Map<String, String> parseNativePayload(String[] payload) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (payload == null) {
            return result;
        }
        for (String entry : payload) {
            if (entry == null) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator > 0) {
                result.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
        }
        return result;
    }

    private String normalizeWorkingDirectory(String requested) {
        String normalized = nonBlank(requested, null);
        if (normalized != null) {
            return normalized;
        }
        File filesDir = getApplicationContext().getFilesDir();
        return filesDir == null ? "/" : filesDir.getAbsolutePath();
    }

    private static String captureText(Map<String, String> values, String prefix) {
        String direct = values.get(prefix + ".text");
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        return decodeHexUtf8(values.get(prefix + ".text.hex"));
    }

    private static String decodeHexUtf8(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0) {
            return "";
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int high = Character.digit(hex.charAt(index * 2), 16);
            int low = Character.digit(hex.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return "";
            }
            bytes[index] = (byte) ((high << 4) | low);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean booleanValue(Map<String, String> values, String key) {
        String value = values.get(key);
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try {
            String value = values.get(key);
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            String value = values.get(key);
            return value == null || value.isEmpty() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringValue(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private static String preferMoreComplete(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second == null ? "" : second;
        }
        if (second != null && second.length() > first.length()) {
            return second;
        }
        return first;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getName() : message;
    }

    private static long elapsedSince(long startedAt) {
        return Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    }

    private static String currentProcessName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int length = input.read(buffer);
            if (length > 0) {
                int end = 0;
                while (end < length && buffer[end] != 0) {
                    end += 1;
                }
                String processName = new String(buffer, 0, end, StandardCharsets.UTF_8).trim();
                if (!processName.isEmpty()) {
                    return processName;
                }
            }
        } catch (IOException ignored) {
            // Fall through to a stable pid label when procfs is unavailable.
        }
        return "pid:" + Process.myPid();
    }

    private static final class RuntimeReadiness {
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

    private static final class BridgeRequestIdentity {
        final String id;
        final String module;
        final String method;

        BridgeRequestIdentity(String id, String module, String method) {
            this.id = id;
            this.module = module;
            this.method = method;
        }
    }
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String ERROR_BUSY = "ERR_AUTOJS6_NODE_PLUGIN_BUSY";
    private static final String ERROR_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE";
    private static final String BRIDGE_PROCESS_DEAD = "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD";
    private static final String BRIDGE_PROVIDER_FAILED = "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED";
    private static final long BRIDGE_DISPATCH_WAIT_MS = 1000L;

    private final Object executionLock = new Object();

    private final INodeJsRuntimePlugin.Stub binder = new INodeJsRuntimePlugin.Stub() {
        @Override
        public Bundle getRuntimeInfo() {
            return runtimeInfoBundle();
        }

        @Override
        public Bundle runScript(Bundle request, INodeJsRuntimeCallback callback) {
            synchronized (executionLock) {
                return runScriptLocked(request == null ? Bundle.EMPTY : request, callback);
            }
        }

        @Override
        public boolean cancelScript(String executionId) {
            return false;
        }

        @Override
        public Bundle prewarmRuntime(Bundle request) {
            long startedAt = SystemClock.elapsedRealtime();
            try {
                loadNativeRuntime();
                Bundle result = new Bundle();
                result.putBoolean("started", true);
                result.putString("status", "ready");
                result.putString("reason", "libraries_loaded");
                result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt));
                result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
                result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
                return result;
            } catch (Throwable error) {
                return failureBundle(
                        Bundle.EMPTY,
                        startedAt,
                        "Node.js runtime prewarm failed: " + messageOf(error),
                        error,
                        ERROR_UNAVAILABLE
                );
            }
        }
    };

    @Override
    public IBinder onBind(android.content.Intent intent) {
        return binder;
    }

    private Bundle runScriptLocked(Bundle request, INodeJsRuntimeCallback callback) {
        long startedAt = SystemClock.elapsedRealtime();
        INodeJsHostCapabilityBroker hostBroker = null;
        PluginNodeBridgeFileTransportSession liveBridgeSession = null;
        notifyEvent(callback, NodeJsRuntimeContract.EVENT_STARTED, null, null);
        try {
            loadNativeRuntime();
            String source = request.getString(NodeJsRuntimeContract.KEY_SOURCE, "");
            if (source.isEmpty()) {
                Bundle failure = failureBundle(
                        request,
                        startedAt,
                        "Node.js runtime request source is empty.",
                        null,
                        "ERR_AUTOJS6_NODE_PLUGIN_EMPTY_SOURCE"
                );
                notifyOutput(callback, failure);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
                return failure;
            }
            String sourceName = nonBlank(
                    request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME),
                    DEFAULT_SOURCE_NAME
            );
            String workingDirectory = normalizeWorkingDirectory(
                    request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY)
            );
            String sandboxRoot = nonBlank(
                    request.getString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT),
                    workingDirectory
            );
            Map<String, String> moduleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCES)
            );
            Map<String, String> runtimeModuleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES)
            );
            Map<String, String> env = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_VALUES)
            );
            hostBroker = hostBrokerFrom(request);
            Bundle hostBrokerInfo = hostBrokerInfo(hostBroker);
            RuntimeModuleInjection runtimeModuleInjection = withPluginRuntimeModules(
                    runtimeModuleSources,
                    request,
                    hostBrokerInfo,
                    workingDirectory,
                    sandboxRoot
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

            String[] nativePayload = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(
                    source,
                    sourceName,
                    workingDirectory,
                    sandboxRoot,
                    moduleSources,
                    runtimeModuleSources,
                    env,
                    request.getBoolean(NodeJsRuntimeContract.KEY_ESM_EXPERIMENTAL_ENABLED, true),
                    request.getBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, false),
                    request.getBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED, false),
                    request.getBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED, false),
                    request.getBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED, false),
                    request.getBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED, false)
            );
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            String[] liveBridgePayload = liveBridgePayload(liveBridgeSession);
            String[] queuedBridgePayload = dispatchQueuedBridgeRequests(nativePayload, hostBroker);
            String[] hostBrokerDiagnosticsPayload = hostBrokerNativeDiagnosticsPayload(hostBroker);
            String[] runtimeModulePayload = runtimeModuleInjection.nativePayload();
            Bundle result = resultBundleFromNativePayload(
                    request,
                    sourceName,
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
                            hostBrokerDiagnosticsPayload
                    ),
                    startedAt
            );
            notifyOutput(callback, result);
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return result;
        } catch (Throwable error) {
            Bundle failure = failureBundle(
                    request,
                    startedAt,
                    "Node.js runtime plugin execution failed: " + messageOf(error),
                    error,
                    ERROR_UNAVAILABLE
            );
            notifyOutput(callback, failure);
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return failure;
        } finally {
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            destroyHostBroker(hostBroker, "Node.js runtime plugin execution finished.");
        }
    }

    private Bundle runtimeInfoBundle() {
        Bundle info = new Bundle();
        info.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_5);
        info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NODE_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_NATIVE_LIBRARY_NAME, NATIVE_LIBRARY_NAME);
        info.putString(NodeJsRuntimeContract.KEY_BRIDGE_LIBRARY_NAME, BRIDGE_LIBRARY_NAME);
        info.putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, new String[]{
                NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION,
                NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT,
                NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME,
                NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER,
                NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE,
        });
        return info;
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

    private RuntimeModuleInjection withPluginRuntimeModules(
            Map<String, String> runtimeModuleSources,
            Bundle request,
            Bundle hostBrokerInfo,
            String workingDirectory,
            String sandboxRoot
    ) {
        RuntimeModuleInjection injection = RuntimeModuleInjection.from(runtimeModuleSources);
        String engineInfo = preferredEngineInfo(runtimeModuleSources, request, hostBrokerInfo);
        injection = injection.withRuntimeModule(
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
                NodeBridgePermissionManifest.INSTANCE.runtimeModuleSourceForWorkingDirectory(
                        workingDirectory,
                        sandboxRoot,
                        BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED
                ),
                "working_directory"
        );
        injection = injection.withRuntimeModule(
                "bridge_limits",
                PluginNodeBridgeFileTransportSession.BRIDGE_LIMITS_RUNTIME_MODULE_NAME,
                bridgeLimitsRuntimeModuleSource(workingDirectory),
                "working_directory"
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
        String requestEngineInfo = nonBlank(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
        if (requestEngineInfo != null) {
            return requestEngineInfo;
        }
        return hostBrokerInfo == null
                ? null
                : nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
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
        if (nonBlank(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null) != null) {
            return "request";
        }
        if (hostBrokerInfo != null
                && nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null) != null) {
            return "host_broker";
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

    private static String bridgeLimitsRuntimeModuleSource(String workingDirectory) {
        try {
            return BridgeLimitPolicy.fromWorkingDirectory(workingDirectory).toJson();
        } catch (Throwable ignored) {
            return null;
        }
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

    private String[] hostBrokerNativeDiagnosticsPayload(INodeJsHostCapabilityBroker hostBroker) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (hostBroker == null) {
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "false");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "missing_broker");
            return nativePayloadFromMap(values);
        }
        try {
            Bundle diagnostics = hostBroker.getNativeDiagnostics();
            String[] nativePayload = diagnostics == null
                    ? null
                    : diagnostics.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_available", "true");
            values.put("embedded_script.runtime_plugin.host_broker.diagnostics_status", "ok");
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
        result.putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, nativePayload == null ? new String[0] : nativePayload);
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
        result.putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, new String[0]);
        return result;
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

        static BridgeLimitPolicy fromWorkingDirectory(String workingDirectory) {
            Builder builder = new Builder();
            List<String> warnings = new ArrayList<>();
            File root = canonicalDirectory(workingDirectory);
            if (root == null) {
                return builder.build(warnings);
            }
            collectProjectJson(readTextIfFile(new File(root, PROJECT_JSON)), builder, warnings);
            collectPackageJson(readTextIfFile(new File(root, PACKAGE_JSON)), builder, warnings);
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

        private static File canonicalDirectory(String workingDirectory) {
            String path = nonBlank(workingDirectory, null);
            if (path == null) {
                return null;
            }
            try {
                File file = new File(path).getCanonicalFile();
                return file.isDirectory() ? file : null;
            } catch (IOException ignored) {
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
        return "pid:" + Process.myPid();
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

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
import java.nio.charset.StandardCharsets;
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
    private static final String ENGINE_INFO_RUNTIME_MODULE_NAME = "autojs6:engine-info";
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
            RuntimeModuleInjection engineInfoInjection = withEngineInfoRuntimeModule(
                    runtimeModuleSources,
                    request,
                    hostBrokerInfo
            );
            runtimeModuleSources = engineInfoInjection.sources;
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
            String[] runtimeModulePayload = engineInfoInjection.nativePayload();
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

    private static RuntimeModuleInjection withEngineInfoRuntimeModule(
            Map<String, String> runtimeModuleSources,
            Bundle request,
            Bundle hostBrokerInfo
    ) {
        String existing = runtimeModuleSources == null
                ? null
                : nonBlank(runtimeModuleSources.get(ENGINE_INFO_RUNTIME_MODULE_NAME), null);
        if (existing != null) {
            return new RuntimeModuleInjection(runtimeModuleSources, "existing", true);
        }
        String requestEngineInfo = nonBlank(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
        if (requestEngineInfo != null) {
            return new RuntimeModuleInjection(
                    withRuntimeModuleSource(runtimeModuleSources, ENGINE_INFO_RUNTIME_MODULE_NAME, requestEngineInfo),
                    "request",
                    true
            );
        }
        String brokerEngineInfo = hostBrokerInfo == null
                ? null
                : nonBlank(hostBrokerInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO), null);
        if (brokerEngineInfo != null) {
            return new RuntimeModuleInjection(
                    withRuntimeModuleSource(runtimeModuleSources, ENGINE_INFO_RUNTIME_MODULE_NAME, brokerEngineInfo),
                    "host_broker",
                    true
            );
        }
        return new RuntimeModuleInjection(runtimeModuleSources, "missing", false);
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
        final String engineInfoSource;
        final boolean engineInfoPresent;

        RuntimeModuleInjection(
                Map<String, String> sources,
                String engineInfoSource,
                boolean engineInfoPresent
        ) {
            this.sources = sources == null ? Collections.emptyMap() : sources;
            this.engineInfoSource = engineInfoSource;
            this.engineInfoPresent = engineInfoPresent;
        }

        String[] nativePayload() {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put(
                    "embedded_script.runtime_plugin.runtime_module.engine_info_present",
                    Boolean.toString(engineInfoPresent)
            );
            values.put(
                    "embedded_script.runtime_plugin.runtime_module.engine_info_source",
                    engineInfoSource == null ? "missing" : engineInfoSource
            );
            return nativePayloadFromMap(values);
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

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
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

/**
 * Stateless payload / bundle / binder-facing helpers shared by the plugin
 * service and its collaborators. Everything here is a pure static function
 * over its arguments; nothing touches Service state.
 */
final class NodePluginPayloads {

    private static final String TAG = "NodeJsRuntimePlugin";

    private NodePluginPayloads() {
    }

    static final String PROC_SELF_STATUS_PATH = "/proc/self/status";

    static final String PROC_SELF_FD_PATH = "/proc/self/fd";

    static final String PROC_SELF_TASK_PATH = "/proc/self/task";

    static final int PROC_STATUS_MAX_BYTES = 64 * 1024;

    static final String BRIDGE_PROCESS_DEAD = "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD";

    static final String BRIDGE_PROVIDER_FAILED = "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED";

    static final long BRIDGE_DISPATCH_WAIT_MS = 1000L;

    static String processAbi() {
        String[] abis = Process.is64Bit()
                ? Build.SUPPORTED_64_BIT_ABIS
                : Build.SUPPORTED_32_BIT_ABIS;
        if (abis != null && abis.length > 0) {
            return abis[0];
        }
        return "unknown";
    }

    static INodeJsHostCapabilityBroker hostBrokerFrom(Bundle request) {
        IBinder binder = request.getBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER);
        return binder == null ? null : INodeJsHostCapabilityBroker.Stub.asInterface(binder);
    }

    static INodeJsModuleSourceProvider moduleSourceProviderFrom(Bundle request) {
        IBinder binder = request.getBinder(PluginModuleSourceProviderFileTransportSession.KEY_PROVIDER_BINDER);
        return binder == null ? null : INodeJsModuleSourceProvider.Stub.asInterface(binder);
    }

    static void closeWorkspaceDescriptors(Bundle request) {
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
    static ParcelFileDescriptor workspaceDescriptor(Bundle request, String key) {
        try {
            return request.getParcelable(key);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read Node.js plugin workspace descriptor " + key + ".", error);
            return null;
        }
    }

    static void closeWorkspaceDescriptor(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to close a Node.js plugin workspace descriptor.", error);
        }
    }

    static void cancelModuleSourceProvider(INodeJsModuleSourceProvider provider, String reason) {
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

    static Bundle hostBrokerInfo(INodeJsHostCapabilityBroker hostBroker) {
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

    static void destroyHostBroker(INodeJsHostCapabilityBroker hostBroker, String message) {
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

    static String[] hostBrokerPayload(INodeJsHostCapabilityBroker hostBroker, Bundle hostBrokerInfo) {
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

    static String[] liveBridgePayload(PluginNodeBridgeFileTransportSession liveBridgeSession) {
        return liveBridgeSession == null ? new String[0] : liveBridgeSession.nativePayload();
    }

    static String[] runtimeProcessDiagnosticsPayload() {
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

    static long bytesToKb(long bytes) {
        return Math.max(0L, bytes) / 1024L;
    }

    static int directoryEntryCount(String path) {
        try {
            String[] entries = new File(path).list();
            return entries == null ? -1 : entries.length;
        } catch (SecurityException ignored) {
            return -1;
        }
    }

    static String readProcText(String path, int maxBytes) {
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

    static String sha256(String value) {
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

    static JSONObject parseJsonObject(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String readTextIfFile(File file) {
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

    static String stringJsonValue(JSONObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return String.valueOf(json.opt(key));
    }

    static long longJsonValue(JSONObject json, String key, long fallback) {
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

    static String[] hostBrokerNativeDiagnosticsPayload(
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

    static String[] dispatchQueuedBridgeRequests(String[] nativePayload, INodeJsHostCapabilityBroker hostBroker) {
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

    static List<JSONObject> bridgeRequestsFromJson(String requestsJson) {
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

    static String[] bridgeDispatchPayload(
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

    static String bridgeResponsesJson(List<String> responseJsonValues) {
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

    static String bridgeFailureResponsesJson(List<JSONObject> requests, String message, String code) {
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

    static String bridgeFailureResponseJson(BridgeRequestIdentity request, String message, String code) {
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

    static BridgeRequestIdentity bridgeRequestIdentity(JSONObject request) {
        return new BridgeRequestIdentity(
                request == null ? "invalid" : nonBlank(request.optString("id"), "invalid"),
                request == null ? "" : nonBlank(request.optString("module"), ""),
                request == null ? "" : nonBlank(request.optString("method"), "")
        );
    }

    static boolean bridgeResponseOk(String responseJson) {
        try {
            return new JSONObject(responseJson).optBoolean("ok", false);
        } catch (Throwable error) {
            return false;
        }
    }

    static String[] appendNativePayload(String[] base, String[] extra) {
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

    static void appendPredispatchNoCommitReceipt(Bundle failure) {
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

    static String[] nativePayloadFromMap(Map<String, String> values) {
        String[] payload = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            payload[index++] = entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue());
        }
        return payload;
    }

    static void notifyOutput(INodeJsRuntimeCallback callback, Bundle result) {
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        if (!stdout.isEmpty()) {
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDOUT, "INFO", stdout);
        }
        String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
        if (!stderr.isEmpty()) {
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_STDERR, "ERROR", stderr);
        }
    }

    static void notifyEvent(INodeJsRuntimeCallback callback, String type, String level, String text) {
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

    static Map<String, String> stringMapFromArrays(String[] names, String[] values) {
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

    static Map<String, String> withRuntimeModuleSource(
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

    static Map<String, String> parseNativePayload(String[] payload) {
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

    static String captureText(Map<String, String> values, String prefix) {
        String direct = values.get(prefix + ".text");
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        return decodeHexUtf8(values.get(prefix + ".text.hex"));
    }

    static String decodeHexUtf8(String hex) {
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

    static boolean booleanValue(Map<String, String> values, String key) {
        String value = values.get(key);
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    static int intValue(Map<String, String> values, String key, int fallback) {
        try {
            String value = values.get(key);
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            String value = values.get(key);
            return value == null || value.isEmpty() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String stringValue(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    static String preferMoreComplete(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second == null ? "" : second;
        }
        if (second != null && second.length() > first.length()) {
            return second;
        }
        return first;
    }

    static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getName() : message;
    }

    static long elapsedSince(long startedAt) {
        return Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    }

    static String currentProcessName() {
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

    static final class BridgeRequestIdentity {
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

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class PluginNodeBridgeFileTransportSession {

    static final String RUNTIME_MODULE_NAME = "autojs6:bridge-live-config";
    static final String BRIDGE_LIMITS_RUNTIME_MODULE_NAME = "autojs6:bridge-limits";

    private static final String TAG = "NodeJsRuntimePlugin";
    private static final String BRIDGE_PROCESS_DEAD = "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD";
    private static final String BRIDGE_PROVIDER_FAILED = "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED";
    private static final String BRIDGE_RESOURCE_LIMIT = "ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT";
    private static final int DEFAULT_MAX_PENDING_BRIDGE_CALLS = 32;
    private static final int HARD_MAX_PENDING_BRIDGE_CALLS = 128;
    private static final long POLL_INTERVAL_MS = 10L;
    private static final long STOP_JOIN_MS = 1000L;
    private static final int DRAIN_ITERATIONS = 20;

    private final File root;
    private final File requestDir;
    private final File responseDir;
    private final INodeJsHostCapabilityBroker hostBroker;
    private final int maxPendingBridgeCalls;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger pending = new AtomicInteger(0);
    private final AtomicInteger resourceLimited = new AtomicInteger(0);
    private final AtomicInteger pollCount = new AtomicInteger(0);
    private final AtomicLong lastPollAtEpochMs = new AtomicLong(0L);
    private final AtomicLong lastRequestAtEpochMs = new AtomicLong(0L);
    private final AtomicLong lastResponseAtEpochMs = new AtomicLong(0L);
    private final long startedAtEpochMs = System.currentTimeMillis();
    private final List<String> seen = Collections.synchronizedList(new ArrayList<>());
    private final List<String> responseJsonValues = Collections.synchronizedList(new ArrayList<>());
    private final Thread thread;

    PluginNodeBridgeFileTransportSession(
            File cacheDir,
            String executionId,
            INodeJsHostCapabilityBroker hostBroker,
            int maxPendingBridgeCalls
    ) {
        this.root = new File(
                new File(cacheDir, "nodejs-bridge-live"),
                safeFileName(nonBlank(executionId, "execution-" + System.nanoTime()))
        );
        this.requestDir = new File(root, "requests");
        this.responseDir = new File(root, "responses");
        this.hostBroker = hostBroker;
        this.maxPendingBridgeCalls = clamp(
                maxPendingBridgeCalls,
                1,
                HARD_MAX_PENDING_BRIDGE_CALLS
        );
        this.thread = new Thread(this::loop);
        this.thread.setName("AutoJs6PluginNodeBridgeFileTransport-" + root.getName());
        this.thread.setDaemon(true);
        deleteRecursively(root);
        requestDir.mkdirs();
        responseDir.mkdirs();
    }

    static int maxPendingBridgeCallsFromRuntimeModule(Map<String, String> runtimeModuleSources) {
        String limitsJson = runtimeModuleSources == null ? null : runtimeModuleSources.get(BRIDGE_LIMITS_RUNTIME_MODULE_NAME);
        if (limitsJson == null || limitsJson.trim().isEmpty()) {
            return DEFAULT_MAX_PENDING_BRIDGE_CALLS;
        }
        try {
            return clamp(
                    new JSONObject(limitsJson).optInt("maxPendingBridgeCalls", DEFAULT_MAX_PENDING_BRIDGE_CALLS),
                    1,
                    HARD_MAX_PENDING_BRIDGE_CALLS
            );
        } catch (Throwable ignored) {
            return DEFAULT_MAX_PENDING_BRIDGE_CALLS;
        }
    }

    String configJson() {
        try {
            return new JSONObject()
                    .put("enabled", true)
                    .put("version", 1)
                    .put("requestDir", requestDir.getAbsolutePath())
                    .put("responseDir", responseDir.getAbsolutePath())
                    .put("pollIntervalMs", POLL_INTERVAL_MS)
                    .toString();
        } catch (Throwable ignored) {
            return "{\"enabled\":false}";
        }
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            thread.start();
        }
    }

    void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (running.compareAndSet(true, false)) {
                try {
                    thread.join(STOP_JOIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            deleteRecursively(root);
        }
    }

    String[] nativePayload() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.bridge_live_enabled", "true");
        values.put("embedded_script.bridge_live_request_count", Integer.toString(requestCount.get()));
        values.put("embedded_script.bridge_live_dispatch_count", Integer.toString(completed.get()));
        values.put("embedded_script.bridge_live_dispatch_failed_count", Integer.toString(failed.get()));
        values.put("embedded_script.bridge_live_dispatch_pending_count", Integer.toString(Math.max(0, pending.get())));
        values.put("embedded_script.bridge_live_dispatch_pending_limit", Integer.toString(maxPendingBridgeCalls));
        values.put("embedded_script.bridge_live_dispatch_resource_limited_count", Integer.toString(resourceLimited.get()));
        values.put("embedded_script.bridge_live_dispatch_poll_count", Integer.toString(pollCount.get()));
        values.put("embedded_script.bridge_live_dispatch_started_at_epoch_ms", Long.toString(startedAtEpochMs));
        values.put("embedded_script.bridge_live_dispatch_last_poll_at_epoch_ms", Long.toString(lastPollAtEpochMs.get()));
        values.put("embedded_script.bridge_live_dispatch_last_request_at_epoch_ms", Long.toString(lastRequestAtEpochMs.get()));
        values.put("embedded_script.bridge_live_dispatch_last_response_at_epoch_ms", Long.toString(lastResponseAtEpochMs.get()));
        values.put("embedded_script.bridge_live_dispatch_status", pending.get() <= 0 ? "done" : "pending");
        values.put("embedded_script.bridge_live_responses_json", bridgeResponsesJsonSnapshot());
        values.put("embedded_script.runtime_plugin.host_broker.live_dispatch_count", Integer.toString(completed.get()));
        values.put("embedded_script.runtime_plugin.host_broker.live_dispatch_failed_count", Integer.toString(failed.get()));
        values.put("embedded_script.runtime_plugin.host_broker.live_dispatch_pending_count", Integer.toString(Math.max(0, pending.get())));
        values.put("embedded_script.runtime_plugin.host_broker.live_dispatch_status", pending.get() <= 0 ? "done" : "pending");
        return nativePayloadFromMap(values);
    }

    private void loop() {
        while (running.get()) {
            drainRequestFiles();
            sleep(POLL_INTERVAL_MS);
        }
        for (int index = 0; index < DRAIN_ITERATIONS; index++) {
            drainRequestFiles();
            if (pending.get() <= 0) {
                return;
            }
            sleep(POLL_INTERVAL_MS);
        }
    }

    private void drainRequestFiles() {
        pollCount.incrementAndGet();
        lastPollAtEpochMs.set(System.currentTimeMillis());
        File[] files = requestDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            dispatchRequestFile(file);
        }
    }

    private void dispatchRequestFile(File file) {
        synchronized (seen) {
            if (seen.contains(file.getName())) {
                return;
            }
            seen.add(file.getName());
        }
        String requestText;
        try {
            requestText = readText(file);
        } catch (Throwable error) {
            BridgeRequestIdentity identity = new BridgeRequestIdentity(fileId(file), "", "");
            recordResponse(
                    bridgeFailureResponseJson(
                            identity,
                            "AutoJs6 live bridge request could not be read: " + messageOf(error),
                            BRIDGE_PROCESS_DEAD
                    ),
                    identity
            );
            file.delete();
            return;
        }
        BridgeRequestIdentity identity = bridgeRequestIdentity(requestText, fileId(file));
        requestCount.incrementAndGet();
        lastRequestAtEpochMs.set(System.currentTimeMillis());
        if (pending.get() >= maxPendingBridgeCalls) {
            resourceLimited.incrementAndGet();
            recordResponse(
                    bridgeFailureResponseJson(
                            identity,
                            "AutoJs6 live bridge exceeded " + maxPendingBridgeCalls + " pending calls.",
                            BRIDGE_RESOURCE_LIMIT
                    ),
                    identity
            );
            file.delete();
            return;
        }
        pending.incrementAndGet();
        AtomicBoolean responded = new AtomicBoolean(false);
        INodeJsHostCapabilityCallback callback = new INodeJsHostCapabilityCallback.Stub() {
            @Override
            public void onResponse(Bundle response) {
                completeResponse(file, identity, responded, responseJsonFromBundle(response, identity));
            }
        };
        try {
            Bundle brokerRequest = new Bundle();
            brokerRequest.putString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON, requestText);
            hostBroker.dispatch(brokerRequest, callback);
        } catch (Throwable error) {
            completeResponse(
                    file,
                    identity,
                    responded,
                    bridgeFailureResponseJson(identity, messageOf(error), BRIDGE_PROVIDER_FAILED)
            );
        }
    }

    private void completeResponse(
            File requestFile,
            BridgeRequestIdentity identity,
            AtomicBoolean responded,
            String responseJson
    ) {
        if (!responded.compareAndSet(false, true)) {
            return;
        }
        recordResponse(responseJson, identity);
        pending.decrementAndGet();
        requestFile.delete();
    }

    private String responseJsonFromBundle(Bundle response, BridgeRequestIdentity identity) {
        if (response == null) {
            return bridgeFailureResponseJson(
                    identity,
                    "AutoJs6 host capability broker returned an empty response.",
                    BRIDGE_PROVIDER_FAILED
            );
        }
        String responseJson = response.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON);
        if (responseJson != null && !responseJson.trim().isEmpty()) {
            return responseJson;
        }
        return bridgeFailureResponseJson(
                identity,
                response.getString(
                        NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE,
                        "AutoJs6 host capability broker returned a malformed response."
                ),
                BRIDGE_PROVIDER_FAILED
        );
    }

    private void recordResponse(String responseJson, BridgeRequestIdentity fallbackIdentity) {
        responseJsonValues.add(responseJson);
        lastResponseAtEpochMs.set(System.currentTimeMillis());
        completed.incrementAndGet();
        if (!bridgeResponseOk(responseJson)) {
            failed.incrementAndGet();
        }
        writeResponse(responseJson, fallbackIdentity);
    }

    private void writeResponse(String responseJson, BridgeRequestIdentity fallbackIdentity) {
        String id = responseId(responseJson, fallbackIdentity.id);
        String fileName = safeFileName(id) + ".json";
        File target = new File(responseDir, fileName);
        File temp = new File(responseDir, fileName + ".tmp-" + System.nanoTime());
        try {
            writeText(temp, responseJson);
            if (!temp.renameTo(target)) {
                writeText(target, responseJson);
                temp.delete();
            }
        } catch (Throwable error) {
            temp.delete();
            Log.w(TAG, "Live bridge response write failed.", error);
        }
    }

    private String bridgeResponsesJsonSnapshot() {
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

    private static BridgeRequestIdentity bridgeRequestIdentity(String requestJson, String fallbackId) {
        try {
            JSONObject request = new JSONObject(requestJson);
            return new BridgeRequestIdentity(
                    nonBlank(request.optString("id"), fallbackId),
                    nonBlank(request.optString("module"), ""),
                    nonBlank(request.optString("method"), "")
            );
        } catch (Throwable ignored) {
            return new BridgeRequestIdentity(fallbackId, "", "");
        }
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
                            .put("category", bridgeErrorCategory(code))
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

    private static String bridgeErrorCategory(String code) {
        if (BRIDGE_PROCESS_DEAD.equals(code)) {
            return "process-dead";
        }
        if (BRIDGE_RESOURCE_LIMIT.equals(code)) {
            return "resource-limit";
        }
        return "provider-failed";
    }

    private static boolean bridgeResponseOk(String responseJson) {
        try {
            return new JSONObject(responseJson).optBoolean("ok", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String responseId(String responseJson, String fallbackId) {
        try {
            return nonBlank(new JSONObject(responseJson).optString("id"), fallbackId);
        } catch (Throwable ignored) {
            return fallbackId;
        }
    }

    private static String fileId(File file) {
        String name = file.getName();
        return nonBlank(name.endsWith(".json") ? name.substring(0, name.length() - 5) : name, "invalid");
    }

    private static String safeFileName(String id) {
        return nonBlank(id, "invalid").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readText(File file) throws IOException {
        long length = file.length();
        if (length < 0L || length > Integer.MAX_VALUE) {
            throw new IOException("Unexpected request file size: " + length);
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return new String(bytes, 0, offset, StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static String[] nativePayloadFromMap(Map<String, String> values) {
        String[] payload = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            payload[index++] = entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue());
        }
        return payload;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getName() : message;
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

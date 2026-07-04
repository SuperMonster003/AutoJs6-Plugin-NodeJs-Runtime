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
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class NodeJsRuntimePluginService extends Service {

    private static final String TAG = "NodeJsRuntimePlugin";
    private static final String NODE_VERSION = "24.5.0";
    private static final String NATIVE_LIBRARY_NAME = "node";
    private static final String BRIDGE_LIBRARY_NAME = "autojs6-node";
    private static final String DEFAULT_SOURCE_NAME = "<plugin-node-script.js>";
    private static final String ERROR_BUSY = "ERR_AUTOJS6_NODE_PLUGIN_BUSY";
    private static final String ERROR_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE";

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
            Bundle result = resultBundleFromNativePayload(request, sourceName, nativePayload, startedAt);
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
}

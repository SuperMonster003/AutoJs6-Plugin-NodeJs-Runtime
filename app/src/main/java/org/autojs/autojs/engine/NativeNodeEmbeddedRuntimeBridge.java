package org.autojs.autojs.engine;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class NativeNodeEmbeddedRuntimeBridge {

    private static final NativeNodeEmbeddedRuntimeBridge INSTANCE = new NativeNodeEmbeddedRuntimeBridge();
    private static volatile boolean librariesLoaded;

    private NativeNodeEmbeddedRuntimeBridge() {
    }

    public static Map<String, String> ensureProcessRuntimeReady(Context context) {
        return invokeProcessRuntime(context, INSTANCE::nativeEnsureProcessRuntimeReady);
    }

    public static Map<String, String> processRuntimeDiagnostics(Context context) {
        return invokeProcessRuntime(context, INSTANCE::nativeProcessRuntimeDiagnostics);
    }

    public static Map<String, String> shutdownProcessRuntime(Context context, String reason) {
        return invokeProcessRuntime(
                context,
                () -> INSTANCE.nativeShutdownProcessRuntime(reason == null ? "" : reason)
        );
    }

    public static Map<String, String> setProcessRuntimePersistentEnabled(
            Context context,
            boolean enabled
    ) {
        return invokeProcessRuntime(
                context,
                () -> INSTANCE.nativeSetProcessRuntimePersistentEnabled(enabled)
        );
    }

    private static Map<String, String> invokeProcessRuntime(Context context, NativePayloadCall call) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            loadLibraries(context);
            String[] payload = call.invoke();
            if (payload != null) {
                for (String entry : payload) {
                    if (entry == null) {
                        continue;
                    }
                    int separator = entry.indexOf('=');
                    if (separator >= 0) {
                        result.put(entry.substring(0, separator), entry.substring(separator + 1));
                    }
                }
            }
        } catch (Throwable error) {
            result.put("process_runtime.state", "unavailable");
            result.put("process_runtime.healthy", "false");
            result.put("process_runtime.poisoned", "true");
            result.put("process_runtime.poison", "true");
            result.put("process_runtime.teardown_clean", "false");
            result.put(
                    "process_runtime.poison_reason",
                    error.getMessage() == null ? error.getClass().getName() : error.getMessage()
            );
        }
        return result;
    }

    private static synchronized void loadLibraries(Context context) {
        if (librariesLoaded) {
            return;
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        try {
            System.loadLibrary("c++_shared");
        } catch (UnsatisfiedLinkError ignored) {
            // Some Android builds load libc++ transitively from libnode.
        }
        System.loadLibrary("node");
        System.loadLibrary("autojs6-node");
        librariesLoaded = true;
    }

    @FunctionalInterface
    private interface NativePayloadCall {
        String[] invoke();
    }

    /**
     * Receives raw output chunks from the native fd/pipe capture while a
     * script is still running. Method names and signatures are looked up from
     * native code as {@code onStdout([B)V} / {@code onStderr([B)V}; both are
     * invoked on a native pipe-reader thread, not the binder thread.
     */
    public interface OutputSink {
        void onStdout(byte[] chunk);

        void onStderr(byte[] chunk);

        default void onStdinState(byte[] state) {}
    }

    /** UTF-8 JSON crosses JNI as bytes, including supplementary Unicode characters. */
    public interface BridgeSink {
        boolean post(long channelId, byte[] requestJson);

        void useFileTransport();
    }

    public static void setBridgeSink(Context context, BridgeSink sink, int maxPending) {
        loadLibraries(context);
        INSTANCE.nativeSetBridgeSink(sink, maxPending);
    }

    public static void clearBridgeSink() {
        if (librariesLoaded) INSTANCE.nativeSetBridgeSink(null, 32);
    }

    public static void receiveBridgeResponse(long channelId, byte[] requestId, byte[] responseJson) {
        INSTANCE.nativeReceiveBridgeResponse(channelId, requestId, responseJson);
    }

    public static boolean attachBridgeBinary(String executionId, byte[] requestId, int fd, long byteCount) {
        return librariesLoaded && INSTANCE.nativeAttachBridgeBinary(executionId, requestId, fd, byteCount);
    }

    /**
     * Installs (or clears, when {@code sink} is null) the process-wide
     * streaming output sink consulted by the next embedded script execution.
     * Single-active execution keeps install → run → clear race-free.
     */
    public static void setOutputStreamSink(Context context, OutputSink sink) {
        loadLibraries(context);
        INSTANCE.nativeSetOutputStreamSink(sink);
    }

    /**
     * Opens the cooperative-stop scope for the given execution id. Must be
     * called before {@link #runEmbeddedScript}; a cancel with the same tag
     * then stops the script's event loop via {@code node::Stop} instead of a
     * process restart.
     */
    public static void beginScriptStopScope(Context context, String executionTag) {
        loadLibraries(context);
        INSTANCE.nativeBeginScriptStopScope(executionTag == null ? "" : executionTag);
    }

    /** Closes the cooperative-stop scope opened by {@link #beginScriptStopScope}. */
    public static void endScriptStopScope(Context context) {
        loadLibraries(context);
        INSTANCE.nativeEndScriptStopScope();
    }

    /**
     * Requests a cooperative stop of the tagged in-flight execution. Returns
     * true when the stop was dispatched (or recorded for imminent dispatch);
     * false when the tag does not match the open scope.
     */
    public static boolean requestScriptStop(Context context, String executionTag) {
        loadLibraries(context);
        return INSTANCE.nativeRequestScriptStop(executionTag == null ? "" : executionTag);
    }

    public static String[] runEmbeddedScript(
            String source,
            String sourceName,
            String workingDirectory,
            String sandboxRoot,
            Map<String, String> moduleSources,
            Map<String, String> runtimeModuleSources,
            Map<String, String> env,
            boolean typeScriptPrecompiledSnapshot,
            Set<String> typeScriptPrecompiledSourceNames,
            boolean esmEnabled,
            boolean dynamicImportEnabled,
            boolean rawNodeNetworkModulesEnabled,
            boolean inspectorEnabled,
            boolean workerThreadsEnabled,
            boolean childProcessEnabled,
            boolean javaInteropEnabled
    ) {
        return INSTANCE.nativeRunEmbeddedScriptLifecycleWithRuntimeModulesV2(
                source,
                sourceName,
                workingDirectory,
                sandboxRoot,
                moduleSources.keySet().toArray(new String[0]),
                moduleSources.values().toArray(new String[0]),
                runtimeModuleSources.keySet().toArray(new String[0]),
                runtimeModuleSources.values().toArray(new String[0]),
                env.keySet().toArray(new String[0]),
                env.values().toArray(new String[0]),
                typeScriptPrecompiledSnapshot,
                typeScriptPrecompiledSourceNames.toArray(new String[0]),
                esmEnabled,
                dynamicImportEnabled,
                rawNodeNetworkModulesEnabled,
                inspectorEnabled,
                workerThreadsEnabled,
                childProcessEnabled,
                javaInteropEnabled
        );
    }

    private native String[] nativeRunEmbeddedScriptLifecycleWithRuntimeModulesV2(
            String source,
            String sourceName,
            String workingDirectory,
            String sandboxRoot,
            String[] moduleSourceNames,
            String[] moduleSources,
            String[] runtimeModuleSourceNames,
            String[] runtimeModuleSources,
            String[] envNames,
            String[] envValues,
            boolean typeScriptPrecompiledSnapshot,
            String[] typeScriptPrecompiledSourceNames,
            boolean esmEnabled,
            boolean dynamicImportEnabled,
            boolean rawNodeNetworkModulesEnabled,
            boolean inspectorEnabled,
            boolean workerThreadsEnabled,
            boolean childProcessEnabled,
            boolean javaInteropEnabled
    );

    private native void nativeSetOutputStreamSink(OutputSink sink);

    private native void nativeSetBridgeSink(BridgeSink sink, int maxPending);

    private native void nativeReceiveBridgeResponse(long channelId, byte[] requestId, byte[] responseJson);
    private native boolean nativeAttachBridgeBinary(String executionId, byte[] requestId, int fd, long byteCount);

    public static void receiveBridgeEvent(long channelId, byte[] eventJson) {
        INSTANCE.nativeReceiveBridgeEvent(channelId, eventJson);
    }

    private native void nativeReceiveBridgeEvent(long channelId, byte[] eventJson);

    public static boolean postExecutionMessage(String executionId, boolean stdin, byte[] json) {
        return librariesLoaded && INSTANCE.nativePostExecutionMessage(executionId, stdin, json);
    }

    private native boolean nativePostExecutionMessage(String executionId, boolean stdin, byte[] json);

    private native void nativeBeginScriptStopScope(String executionTag);

    private native void nativeEndScriptStopScope();

    private native boolean nativeRequestScriptStop(String executionTag);

    private native String[] nativeEnsureProcessRuntimeReady();

    private native String[] nativeProcessRuntimeDiagnostics();

    private native String[] nativeShutdownProcessRuntime(String reason);

    private native String[] nativeSetProcessRuntimePersistentEnabled(boolean enabled);
}

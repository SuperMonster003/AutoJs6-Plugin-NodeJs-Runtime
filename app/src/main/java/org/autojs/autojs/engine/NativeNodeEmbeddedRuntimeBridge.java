package org.autojs.autojs.engine;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public static String[] runEmbeddedScript(
            String source,
            String sourceName,
            String workingDirectory,
            String sandboxRoot,
            Map<String, String> moduleSources,
            Map<String, String> runtimeModuleSources,
            Map<String, String> env,
            boolean esmExperimentalEnabled,
            boolean dynamicImportExperimentalEnabled,
            boolean rawNodeNetworkModulesExperimentalEnabled,
            boolean workerThreadsExperimentalEnabled,
            boolean childProcessExperimentalEnabled,
            boolean javaInteropExperimentalEnabled
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
                esmExperimentalEnabled,
                dynamicImportExperimentalEnabled,
                rawNodeNetworkModulesExperimentalEnabled,
                workerThreadsExperimentalEnabled,
                childProcessExperimentalEnabled,
                javaInteropExperimentalEnabled
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
            boolean esmExperimentalEnabled,
            boolean dynamicImportExperimentalEnabled,
            boolean rawNodeNetworkModulesExperimentalEnabled,
            boolean workerThreadsExperimentalEnabled,
            boolean childProcessExperimentalEnabled,
            boolean javaInteropExperimentalEnabled
    );

    private native String[] nativeEnsureProcessRuntimeReady();

    private native String[] nativeProcessRuntimeDiagnostics();

    private native String[] nativeShutdownProcessRuntime(String reason);

    private native String[] nativeSetProcessRuntimePersistentEnabled(boolean enabled);
}

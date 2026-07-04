package org.autojs.autojs.engine;

import java.util.Map;

public final class NativeNodeEmbeddedRuntimeBridge {

    private static final NativeNodeEmbeddedRuntimeBridge INSTANCE = new NativeNodeEmbeddedRuntimeBridge();

    private NativeNodeEmbeddedRuntimeBridge() {
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
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.longJsonValue;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.messageOf;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.withRuntimeModuleSource;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.nativePayloadFromMap;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.nonBlank;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.parseJsonObject;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.readTextIfFile;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.sha256;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.stringJsonValue;

/**
 * Builds the plugin-provided runtime modules (engine/host/device info,
 * bridge permissions/limits, lifecycle config) injected into every script
 * execution. Holds a Context only for package name / display metrics /
 * files-dir lookups.
 */
final class NodeRuntimeModuleInjector {

    private final Context context;

    NodeRuntimeModuleInjector(Context context) {
        this.context = context;
    }

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

    RuntimeModuleInjection withPluginRuntimeModules(
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
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
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
        String packageName = stringJsonValue(engineInfo, "packageName", context.getPackageName());
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
            File filesDir = context.getApplicationContext().getFilesDir();
            normalized = filesDir == null ? "/" : filesDir.getAbsolutePath();
        }
        try {
            return new File(normalized).getCanonicalPath();
        } catch (IOException ignored) {
            return normalized;
        }
    }

    static final class RuntimeModuleInjection {
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
}

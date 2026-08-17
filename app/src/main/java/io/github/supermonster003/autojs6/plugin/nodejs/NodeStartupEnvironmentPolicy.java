package io.github.supermonster003.autojs6.plugin.nodejs;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plugin-owned final-dispatch policy for environment variables interpreted by
 * Node before user code starts. The denylist intentionally matches the Host
 * legacy embedded-runtime boundary while that compatibility implementation is
 * still present.
 */
final class NodeStartupEnvironmentPolicy {

    static final int FILTERED_DIAGNOSTIC_DETAIL_LIMIT = 16;

    static final List<String> DENIED_ENVIRONMENT_NAMES = Collections.unmodifiableList(
            Arrays.asList(
                    "NODE_OPTIONS",
                    "NODE_INSPECT_RESUME_ON_START",
                    "NODE_COMPILE_CACHE",
                    "NODE_DISABLE_COMPILE_CACHE"
            )
    );

    private NodeStartupEnvironmentPolicy() {
    }

    static Result sanitize(Map<String, String> environment) {
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        LinkedHashMap<String, String> filteredDiagnostics = new LinkedHashMap<>();
        int inputCount = 0;
        int filteredCount = 0;

        if (environment != null) {
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                String name = entry.getKey();
                inputCount++;
                if (isDenied(name)) {
                    if (filteredCount < FILTERED_DIAGNOSTIC_DETAIL_LIMIT) {
                        filteredDiagnostics.put(
                                "embedded_script.runtime_plugin.startup_env.filtered."
                                        + filteredCount
                                        + ".name",
                                name
                        );
                    }
                    filteredCount++;
                } else {
                    sanitized.put(name, entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }

        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put(
                "embedded_script.runtime_plugin.startup_env.input_count",
                Integer.toString(inputCount)
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.startup_env.filtered_count",
                Integer.toString(filteredCount)
        );
        int detailCount = Math.min(filteredCount, FILTERED_DIAGNOSTIC_DETAIL_LIMIT);
        diagnostics.put(
                "embedded_script.runtime_plugin.startup_env.detail_count",
                Integer.toString(detailCount)
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.startup_env.detail_truncated_count",
                Integer.toString(filteredCount - detailCount)
        );
        diagnostics.putAll(filteredDiagnostics);
        return new Result(sanitized, diagnostics);
    }

    static boolean isDenied(String name) {
        return name != null
                && DENIED_ENVIRONMENT_NAMES.contains(name.toUpperCase(Locale.ROOT));
    }

    static final class Result {
        private final Map<String, String> environment;
        private final Map<String, String> diagnostics;

        Result(Map<String, String> environment, Map<String, String> diagnostics) {
            this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
            this.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
        }

        Map<String, String> environment() {
            return environment;
        }

        Map<String, String> diagnostics() {
            return diagnostics;
        }
    }
}

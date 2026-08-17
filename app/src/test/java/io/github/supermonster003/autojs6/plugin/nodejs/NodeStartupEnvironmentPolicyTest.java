package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeStartupEnvironmentPolicyTest {

    @Test
    public void denylistExactlyMatchesLegacyStartupEnvironmentBoundary() {
        assertEquals(Arrays.asList(
                "NODE_OPTIONS",
                "NODE_INSPECT_RESUME_ON_START",
                "NODE_COMPILE_CACHE",
                "NODE_DISABLE_COMPILE_CACHE"
        ), NodeStartupEnvironmentPolicy.DENIED_ENVIRONMENT_NAMES);
    }

    @Test
    public void serviceFinalDispatchSanitizationIsCaseInsensitiveAndOrderPreserving() {
        LinkedHashMap<String, String> environment = new LinkedHashMap<>();
        environment.put("SAFE_FIRST", "1");
        environment.put("node_options", "--inspect=0.0.0.0:9229");
        environment.put("SAFE_SECOND", "2");
        environment.put("Node_Compile_Cache", "/unsafe/cache");
        environment.put("NODE_DISABLE_COMPILE_CACHE", "1");
        environment.put("NODE_INSPECT_RESUME_ON_START", "1");

        NodeStartupEnvironmentPolicy.Result result =
                NodeJsRuntimePluginService.prepareNodeStartupEnvironmentForNative(environment);

        assertEquals(
                Arrays.asList("SAFE_FIRST", "SAFE_SECOND"),
                new ArrayList<>(result.environment().keySet())
        );
        assertEquals("1", result.environment().get("SAFE_FIRST"));
        assertEquals("2", result.environment().get("SAFE_SECOND"));
        assertEquals("6", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.input_count"
        ));
        assertEquals("4", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered_count"
        ));
        assertEquals(Arrays.asList(
                "embedded_script.runtime_plugin.startup_env.input_count",
                "embedded_script.runtime_plugin.startup_env.filtered_count",
                "embedded_script.runtime_plugin.startup_env.detail_count",
                "embedded_script.runtime_plugin.startup_env.detail_truncated_count",
                "embedded_script.runtime_plugin.startup_env.filtered.0.name",
                "embedded_script.runtime_plugin.startup_env.filtered.1.name",
                "embedded_script.runtime_plugin.startup_env.filtered.2.name",
                "embedded_script.runtime_plugin.startup_env.filtered.3.name"
        ), new ArrayList<>(result.diagnostics().keySet()));
        assertEquals("node_options", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered.0.name"
        ));
        assertEquals("Node_Compile_Cache", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered.1.name"
        ));
        assertEquals("4", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.detail_count"
        ));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.detail_truncated_count"
        ));
    }

    @Test
    public void nonStartupCompileCacheMetadataRemainsAvailableToPluginRuntime() {
        LinkedHashMap<String, String> environment = new LinkedHashMap<>();
        environment.put("AUTOJS6_NODE_COMPILE_CACHE_DIR", "/plugin/scoped/cache");
        environment.put("AUTOJS6_NODE_COMPILE_CACHE_KEY", "trusted-plugin-key");
        environment.put("NODE_ENV", "test");

        NodeStartupEnvironmentPolicy.Result result =
                NodeStartupEnvironmentPolicy.sanitize(environment);

        assertEquals(environment, result.environment());
        assertEquals("0", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered_count"
        ));
        assertFalse(NodeStartupEnvironmentPolicy.isDenied("AUTOJS6_NODE_COMPILE_CACHE_DIR"));
        assertTrue(NodeStartupEnvironmentPolicy.isDenied("node_options"));
    }

    @Test
    public void emptyEnvironmentProducesStableZeroDiagnostics() {
        NodeStartupEnvironmentPolicy.Result result = NodeStartupEnvironmentPolicy.sanitize(null);

        assertTrue(result.environment().isEmpty());
        assertEquals(Arrays.asList(
                "embedded_script.runtime_plugin.startup_env.input_count",
                "embedded_script.runtime_plugin.startup_env.filtered_count",
                "embedded_script.runtime_plugin.startup_env.detail_count",
                "embedded_script.runtime_plugin.startup_env.detail_truncated_count"
        ), new ArrayList<>(result.diagnostics().keySet()));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.input_count"
        ));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered_count"
        ));
    }

    @Test
    public void filteredEnvironmentDiagnosticsAreCappedWithoutLosingCounts() {
        LinkedHashMap<String, String> environment = new LinkedHashMap<>();
        int filteredCount = NodeStartupEnvironmentPolicy.FILTERED_DIAGNOSTIC_DETAIL_LIMIT + 5;
        for (int variant = 0; variant < filteredCount; variant++) {
            environment.put(caseVariant("NODE_OPTIONS", variant), "unsafe-" + variant);
        }

        NodeStartupEnvironmentPolicy.Result result = NodeStartupEnvironmentPolicy.sanitize(environment);

        assertTrue(result.environment().isEmpty());
        assertEquals(Integer.toString(filteredCount), result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.filtered_count"
        ));
        assertEquals(
                Integer.toString(NodeStartupEnvironmentPolicy.FILTERED_DIAGNOSTIC_DETAIL_LIMIT),
                result.diagnostics().get("embedded_script.runtime_plugin.startup_env.detail_count")
        );
        assertEquals("5", result.diagnostics().get(
                "embedded_script.runtime_plugin.startup_env.detail_truncated_count"
        ));
        assertTrue(result.diagnostics().containsKey(
                "embedded_script.runtime_plugin.startup_env.filtered.15.name"
        ));
        assertFalse(result.diagnostics().containsKey(
                "embedded_script.runtime_plugin.startup_env.filtered.16.name"
        ));
        assertEquals(
                4 + NodeStartupEnvironmentPolicy.FILTERED_DIAGNOSTIC_DETAIL_LIMIT,
                result.diagnostics().size()
        );
    }

    private static String caseVariant(String source, int variant) {
        StringBuilder result = new StringBuilder(source.length());
        int letterIndex = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                boolean lowerCase = (variant & (1 << letterIndex)) != 0;
                result.append(lowerCase ? Character.toLowerCase(character) : character);
                letterIndex++;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}

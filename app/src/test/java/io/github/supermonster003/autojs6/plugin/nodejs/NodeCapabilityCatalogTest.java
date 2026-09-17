package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The packaged capability catalog, the Runtime Kit manifest that pins it and the
 * BuildConfig identity reported through runtimeInfo / PluginInfo must describe the
 * same file, and the catalog must only advertise what the runtime really does.
 * zh-CN: 打包的能力目录、钉定它的 Runtime Kit 清单与 runtimeInfo / PluginInfo 上报的
 * BuildConfig 标识必须指向同一份文件, 且目录只能宣告运行时真实具备的行为.
 */
public class NodeCapabilityCatalogTest {

    private static final File CATALOG = new File("src/main/assets/nodejs/node-capability-catalog.json");
    private static final File KIT = new File("src/main/assets/nodejs/node-plugin-runtime-kit.json");

    private static final Pattern CATALOG_VERSION = Pattern.compile("\"catalogVersion\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern KIT_VERSION = Pattern.compile("\"kitVersion\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern KIT_CATALOG_BLOCK = Pattern.compile(
            "\"capabilityCatalog\"\\s*:\\s*\\{\\s*\"schema\"\\s*:\\s*\"([^\"]+)\",\\s*\"version\"\\s*:\\s*\"([^\"]+)\","
                    + "\\s*\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\""
    );
    private static final Pattern KIT_CATALOG_PAYLOAD = Pattern.compile(
            "\"id\"\\s*:\\s*\"capability-catalog\"[^}]*?\"size\"\\s*:\\s*(\\d+),\\s*\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"",
            Pattern.DOTALL
    );
    private static final Pattern TYPESCRIPT_FEATURE = Pattern.compile(
            "\"id\"\\s*:\\s*\"typescript\",\\s*\"diagnosticName\"\\s*:\\s*\"typeScript\",(.*?)\\n\\s{4}\\}",
            Pattern.DOTALL
    );
    private static final Pattern LIFECYCLE_BLOCK = Pattern.compile(
            "\"lifecycle\"\\s*:\\s*\\{(.*?)\\n\\s{2}\\},?\\n\\s{2}\"bridge\"",
            Pattern.DOTALL
    );
    private static final Pattern MODE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([a-z_]+)\"");

    private static String read(File file) throws Exception {
        assertTrue("missing: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String sha256(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static String group(Pattern pattern, String text, int group, String what) {
        Matcher matcher = pattern.matcher(text);
        assertTrue(what + " not found", matcher.find());
        return matcher.group(group);
    }

    @Test
    public void runtimeInfoCatalogIdentityIsDerivedFromTheKitThatPinsThePackagedCatalog() throws Exception {
        String catalog = read(CATALOG);
        String kit = read(KIT);
        String catalogSha = sha256(CATALOG);
        String catalogVersion = group(CATALOG_VERSION, catalog, 1, "catalogVersion");

        Matcher block = KIT_CATALOG_BLOCK.matcher(kit);
        assertTrue("kit capabilityCatalog block not found", block.find());
        assertEquals(NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SCHEMA, block.group(1));
        assertEquals(catalogVersion, block.group(2));
        assertEquals("kit pins a different catalog digest", catalogSha, block.group(3));

        Matcher payload = KIT_CATALOG_PAYLOAD.matcher(kit);
        assertTrue("kit capability-catalog payload not found", payload.find());
        assertEquals(CATALOG.length(), Long.parseLong(payload.group(1)));
        assertEquals(catalogSha, payload.group(2));

        assertEquals(catalogVersion, BuildConfig.NODE_CAPABILITY_CATALOG_VERSION);
        assertEquals(catalogSha, BuildConfig.NODE_CAPABILITY_CATALOG_SHA256);
        assertEquals(catalogVersion, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_VERSION);
        assertEquals(catalogSha, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SHA256);

        assertEquals(group(KIT_VERSION, kit, 1, "kitVersion"), BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
        assertEquals(sha256(KIT), BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
    }

    @Test
    public void typeScriptIsAStableCompilerRouteWithoutRetiredStrippingMetadata() throws Exception {
        String catalog = read(CATALOG);
        assertFalse("retired legacy stripping metadata is back", catalog.contains("legacyStripping"));
        assertFalse("retired legacy stripping request key is back", catalog.contains("LegacyStrippingRequestKey"));

        String feature = group(TYPESCRIPT_FEATURE, catalog, 1, "typescript feature");
        assertTrue(feature.contains("\"status\": \"stable\""));
        assertTrue(feature.contains("\"defaultPolicy\": \"compiler_output_required\""));
        assertTrue(feature.contains("\"rawSourceAdmission\": \"fail_closed\""));
        assertTrue(feature.contains(
                "\"rawSourceFailureCode\": \"" + NodeTypeScriptSourcePolicy.ERROR_COMPILER_REQUIRED + "\""
        ));
        assertTrue(feature.contains("\"onDemandCompilationOperation\": \"compile_missing_typescript\""));
    }

    @Test
    public void lifecycleCatalogListsOnlyRunnableModesAndRealAdmissionFacts() throws Exception {
        String catalog = read(CATALOG);
        String lifecycle = group(LIFECYCLE_BLOCK, catalog, 1, "lifecycle block");

        StringBuilder modes = new StringBuilder();
        Matcher ids = MODE_ID.matcher(lifecycle);
        while (ids.find()) {
            modes.append(ids.group(1)).append(',');
        }
        assertEquals("interactive_long_running,one_shot,scheduled,", modes.toString());
        assertFalse(lifecycle.contains("packaged_long_running"));
        assertFalse(lifecycle.contains("design_gated"));
        assertFalse(lifecycle.contains("reserved"));

        assertTrue(lifecycle.contains("\"processModel\": \"process_pool\""));
        assertTrue(lifecycle.contains("\"maxConcurrentExecutions\": 1"));
        assertTrue(lifecycle.contains("\"processPoolMaxConcurrentExecutions\": 2"));
        assertTrue(lifecycle.contains("\"queueCapacity\": " + NodeRuntimeExecutionGate.QUEUE_CAPACITY));
        assertTrue(lifecycle.contains(
                "\"mode\": \"" + NodeJsRuntimePluginService.CANCELLATION_STRATEGY_COOPERATIVE_STOP + "\""
        ));

        assertTrue(catalog.contains("\"mode\": \"streaming\""));
        assertTrue(catalog.contains("\"streaming\": true"));
        assertFalse(catalog.contains("\"streaming\": false"));
    }
}

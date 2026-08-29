package io.github.supermonster003.autojs6.plugin.nodejs;

import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NodeTypeScriptSourcePolicyTest {

    @Test
    public void javaScriptEntryAndPreloadedSourcesRemainExact() {
        String source = "'use strict';\nmodule.exports = 42;\n";
        assertSame(
                source,
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative("main.cjs", source)
        );

        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("lib/value.js", "module.exports = 42;\n");
        sources.put("runtime/empty.js", null);

        Map<String, String> prepared =
                NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources);
        assertEquals("module.exports = 42;\n", prepared.get("lib/value.js"));
        assertEquals("", prepared.get("runtime/empty.js"));
        assertEquals(2, prepared.size());
        try {
            prepared.put("other.js", "");
            fail("Expected an immutable admitted source map");
        } catch (UnsupportedOperationException expected) {
            assertFalse(expected.getClass().getSimpleName().isEmpty());
        }
    }

    @Test
    public void everyRawTypeScriptExtensionRequiresCompilerOutput() {
        for (String sourceName : new String[]{
                "main.ts",
                "main.mts",
                "main.cts",
                "view.tsx",
                "globals.d.ts",
                "globals.d.mts",
                "globals.d.cts",
                "UPPER.TS"
        }) {
            NodeTypeScriptSourcePolicy.CompilerRequiredException error =
                    expectCompilerRequired(sourceName);
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                    error.errorCode()
            );
            assertEquals(sourceName, error.sourceName());
            assertEquals("compiler_required", error.syntaxKind());
            assertEquals(1, error.line());
            assertEquals(1, error.column());
            assertTrue(error.getMessage().contains(sourceName));
        }
    }

    @Test
    public void preloadedTypeScriptFailsBeforeNativeDispatchPreparation() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("lib/value.js", "module.exports = 42;\n");
        sources.put("lib/raw.cts", "const value: number = 42;\n");

        try {
            NodeJsRuntimePluginService.prepareTypeScriptRuntimeModuleSourcesForNative(sources);
            fail("Expected raw TypeScript rejection");
        } catch (NodeTypeScriptSourcePolicy.CompilerRequiredException error) {
            assertEquals("lib/raw.cts", error.sourceName());
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                    error.errorCode()
            );
        }
    }

    @Test
    public void compilerRequiredDiagnosticsAreCanonicalAndBinderBounded() {
        StringBuilder sourceName = new StringBuilder("/private/");
        for (int index = 0; index < 140; index++) {
            sourceName.append("\ud83d\ude80");
        }
        sourceName.append("/feature/value.mts");

        NodeTypeScriptSourcePolicy.CompilerRequiredException error =
                expectCompilerRequired(sourceName.toString());
        Map<String, String> diagnostics = error.diagnostics();
        String diagnosticName = diagnostics.get("embedded_script.typescript.source");

        assertEquals(
                NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                diagnostics.get("embedded_script.error_code")
        );
        assertEquals("true", diagnostics.get("embedded_script.typescript.error"));
        assertEquals("true", diagnostics.get("embedded_script.typescript.source_truncated"));
        assertEquals("compiler_required", diagnostics.get("embedded_script.typescript.syntax_kind"));
        assertEquals("1", diagnostics.get("embedded_script.typescript.line"));
        assertEquals("1", diagnostics.get("embedded_script.typescript.column"));
        assertTrue(
                diagnosticName.length()
                        <= NodeTypeScriptSourcePolicy.DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS
        );
        assertTrue(diagnosticName.contains("..."));
        assertTrue(diagnosticName.endsWith("/feature/value.mts"));
        assertFalse(Character.isHighSurrogate(diagnosticName.charAt(diagnosticName.length() - 1)));
    }

    @Test
    public void sourceClassificationDoesNotConfuseDeclarationsWithExecutableTypeScript() {
        assertTrue(NodeTypeScriptSourcePolicy.isTypeScriptSourceName("main.ts"));
        assertTrue(NodeTypeScriptSourcePolicy.isTypeScriptSourceName("main.mts"));
        assertTrue(NodeTypeScriptSourcePolicy.isTypeScriptSourceName("main.cts"));
        assertFalse(NodeTypeScriptSourcePolicy.isTypeScriptSourceName("globals.d.ts"));
        assertTrue(NodeTypeScriptSourcePolicy.isTypeScriptDeclarationSourceName("globals.d.ts"));
        assertTrue(NodeTypeScriptSourcePolicy.isAnyTypeScriptSourceName("view.tsx"));
        assertFalse(NodeTypeScriptSourcePolicy.isAnyTypeScriptSourceName("main.mjs"));
    }

    private static NodeTypeScriptSourcePolicy.CompilerRequiredException expectCompilerRequired(
            String sourceName
    ) {
        try {
            NodeJsRuntimePluginService.prepareTypeScriptEntryForNative(
                    sourceName,
                    "const answer: number = 42;\n"
            );
            fail("Expected compiler-required rejection for " + sourceName);
            throw new AssertionError("unreachable");
        } catch (NodeTypeScriptSourcePolicy.CompilerRequiredException expected) {
            return expected;
        }
    }
}

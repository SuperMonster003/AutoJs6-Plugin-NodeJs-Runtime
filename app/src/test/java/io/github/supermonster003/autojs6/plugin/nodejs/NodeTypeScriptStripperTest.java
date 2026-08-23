package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NodeTypeScriptStripperTest {

    @Test
    public void serviceEntryPreparationStripsErasableTypeScriptAndReportsDiagnostics() {
        String source = "import type { User } from './types';\n"
                + "export interface Person {\n"
                + "  name: string;\n"
                + "}\n"
                + "type Identifier = string | number;\n"
                + "function identity<T>(value: T): T {\n"
                + "  const typed: T = value as T;\n"
                + "  return typed satisfies T;\n"
                + "}\n";

        NodeTypeScriptStripper.Result result =
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative("src/main.cts", source, true);

        assertTrue(result.stripped());
        assertFalse(result.source().contains("import type"));
        assertFalse(result.source().contains("interface Person"));
        assertFalse(result.source().contains("type Identifier"));
        assertEquals(
                "\n\n\n\n\n"
                        + "function identity(value){\n"
                        + "  const typed= value;\n"
                        + "  return typed;\n"
                        + "}\n",
                result.source()
        );
        assertEquals("true", result.diagnostics().get("embedded_script.typescript.stripped"));
        assertEquals("src/main.cts", result.diagnostics().get("embedded_script.typescript.source"));
        assertEquals("false", result.diagnostics().get(
                "embedded_script.typescript.source_truncated"
        ));
        assertEquals("cts", result.diagnostics().get("embedded_script.typescript.extension"));
    }

    @Test
    public void parenthesizedStringLiteralAsAssertionKeepsTheLiteral() {
        // Regression: the lexical mask blanked string literals with spaces, so
        // the as-assertion anchor (\S)\s+as swallowed the literal itself and
        // ('cjs' as string) became (), which is a syntax error at runtime.
        String source = "const value: string = ('cjs' as string);\n"
                + "console.log('ts.cjs=' + value);\n";

        NodeTypeScriptStripper.Result result =
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative("main.ts", source, true);

        assertTrue(result.stripped());
        assertEquals(
                "const value= ('cjs');\n"
                        + "console.log('ts.cjs=' + value);\n",
                result.source()
        );
    }

    @Test
    public void alreadyStrippedEntryIsPreservedAndNotReportedAsMutatedOnRepeatedPreparation() {
        String source = "const answer = 42;\nconsole.log(answer);\n";

        NodeTypeScriptStripper.Result first =
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative("main.ts", source, true);
        NodeTypeScriptStripper.Result second =
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative("main.ts", first.source(), true);

        assertEquals(source, first.source());
        assertFalse(first.stripped());
        assertEquals("false", first.diagnostics().get("embedded_script.typescript.stripped"));
        assertEquals(first.source(), second.source());
        assertFalse(second.stripped());
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    @Test
    public void nonTypeScriptAndDeclarationInputsRemainUntouched() {
        NodeTypeScriptStripper.Result javaScript = NodeTypeScriptStripper.stripIfTypeScript(
                "main.js",
                "const value = 1;\n",
                true
        );
        NodeTypeScriptStripper.Result declaration = NodeTypeScriptStripper.stripIfTypeScript(
                "globals.d.ts",
                "declare enum RuntimeState { Ready }\n",
                true
        );

        assertFalse(javaScript.stripped());
        assertTrue(javaScript.diagnostics().isEmpty());
        assertEquals("declare enum RuntimeState { Ready }\n", declaration.source());
        assertFalse(declaration.stripped());
        assertTrue(declaration.diagnostics().isEmpty());
        assertTrue(NodeTypeScriptStripper.isTypeScriptDeclarationSourceName("globals.d.ts"));
        assertFalse(NodeTypeScriptStripper.isTypeScriptSourceName("globals.d.ts"));
        assertTrue(NodeTypeScriptStripper.isTypeScriptSourceName("main.mts"));
    }

    @Test
    public void rawTypeScriptDefaultsToCompilerRequiredForSourcesAndDeclarations() {
        for (String sourceName : Arrays.asList("main.ts", "entry.mts", "entry.cts", "globals.d.ts")) {
            try {
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative(
                        sourceName,
                        "const value: number = 1;\n",
                        false
                );
                fail("Expected precompiled-only rejection for " + sourceName);
            } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
                assertEquals(NodeTypeScriptStripper.ERROR_COMPILER_REQUIRED, error.errorCode());
                assertEquals("compiler_required", error.syntaxKind());
                assertTrue(error.getMessage().contains("AutoJs6 TypeScript Compiler plugin"));
                assertTrue(error.getMessage().contains("legacyTypeScriptStrippingEnabled=true"));
            }
        }

        assertEquals(
                "precompiled_only",
                NodeTypeScriptStripper.policyDiagnostics(false).get(
                        NodeTypeScriptStripper.DIAGNOSTIC_PREPARATION_MODE
                )
        );
        assertEquals(
                "legacy_stripping",
                NodeTypeScriptStripper.policyDiagnostics(true).get(
                        NodeTypeScriptStripper.DIAGNOSTIC_PREPARATION_MODE
                )
        );
    }

    @Test
    public void preloadedAndRuntimeModuleSourcesAlsoDefaultToPrecompiledOnly() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("src/dynamic.ts", "const value: number = 1;\n");

        try {
            NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources, false);
            fail("Expected preloaded module TypeScript rejection");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            assertEquals(NodeTypeScriptStripper.ERROR_COMPILER_REQUIRED, error.errorCode());
            assertEquals("src/dynamic.ts", error.sourceName());
        }

        try {
            NodeJsRuntimePluginService.prepareTypeScriptRuntimeModuleSourcesForNative(sources, false);
            fail("Expected runtime module TypeScript rejection");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            assertEquals(NodeTypeScriptStripper.ERROR_COMPILER_REQUIRED, error.errorCode());
            assertEquals("src/dynamic.ts", error.sourceName());
        }
    }

    @Test
    public void unsupportedSyntaxFailsClosedWithCatalogCodeAndPreciseDiagnostics() {
        String source = "const ready = true;\n  @sealed\nclass Runtime {}\n";

        NodeTypeScriptStripper.UnsupportedTypeScriptException error =
                expectUnsupported("src/runtime.ts", source);

        assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_SYNTAX, error.errorCode());
        assertEquals("decorator", error.syntaxKind());
        assertEquals(2, error.line());
        assertEquals(1, error.column());
        assertTrue(error.getMessage().startsWith("ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX:"));
        Map<String, String> diagnostics = error.diagnostics();
        assertEquals(error.errorCode(), diagnostics.get("embedded_script.error_code"));
        assertEquals("true", diagnostics.get("embedded_script.typescript.error"));
        assertEquals("src/runtime.ts", diagnostics.get("embedded_script.typescript.source"));
        assertEquals("false", diagnostics.get("embedded_script.typescript.source_truncated"));
        assertEquals("decorator", diagnostics.get("embedded_script.typescript.syntax_kind"));
        assertEquals("2", diagnostics.get("embedded_script.typescript.line"));
        assertEquals("1", diagnostics.get("embedded_script.typescript.column"));
    }

    @Test
    public void everyCodeGeneratingSyntaxFamilyIsRejected() {
        assertEquals("enum", expectUnsupported("main.ts", "enum Color { Red }\n").syntaxKind());
        assertEquals("namespace", expectUnsupported("main.ts", "namespace Runtime { }\n").syntaxKind());
        assertEquals("decorator", expectUnsupported("main.ts", "@sealed\nclass Runtime {}\n").syntaxKind());
        assertEquals(
                "parameter_property",
                expectUnsupported("main.ts", "class User { constructor(public name: string) {} }\n").syntaxKind()
        );
        assertEquals("import_alias", expectUnsupported("main.ts", "import fs = require('fs');\n").syntaxKind());
    }

    @Test
    public void unsupportedTokensInsideCommentsAndStringsDoNotCauseFalseRejection() {
        String source = "// enum Hidden { Value }\n"
                + "const words = 'namespace Hidden { } @decorator import Alias =';\n"
                + "/* constructor(public id: string) */\n"
                + "const value: number = 7;\n";

        NodeTypeScriptStripper.Result result = NodeTypeScriptStripper.stripIfTypeScript("main.ts", source, true);

        assertTrue(result.stripped());
        assertTrue(result.source().contains("const value= 7;"));
    }

    @Test
    public void erasurePatternsPreserveStringAndCommentLiteralsByteForByte() {
        String stringLiteral = "const raw = \"const answer: number = 42; function fake<T>(value: T): T { return value as T; }\";\n";
        String lineComment = "// type Hidden = number; const commentValue: string = 'kept';\n";
        String blockComment = "/* interface Hidden { value: number; } export type AlsoHidden = string; */\n";
        String source = stringLiteral
                + lineComment
                + blockComment
                + "const answer: number = 42;\n";

        NodeTypeScriptStripper.Result result = NodeTypeScriptStripper.stripIfTypeScript("main.cts", source, true);

        assertTrue(result.stripped());
        assertTrue(result.source().contains(stringLiteral));
        assertTrue(result.source().contains(lineComment));
        assertTrue(result.source().contains(blockComment));
        assertTrue(result.source().contains("const answer= 42;\n"));
    }

    @Test
    public void x3fComputedCtsRawInspectionMarkerSurvivesPreparationExactly() {
        String source = "const fs = require(\"fs\");\n"
                + "const raw: string = fs.readFileSync(__filename, \"utf8\");\n"
                + "if (!raw.includes(\"const answer: number = 42;\")) {\n"
                + "  throw new Error(\"provider materialization was not raw TypeScript\");\n"
                + "}\n"
                + "console.log(\"x3f.raw-ts=true\");\n"
                + "fs.writeFileSync(__filename, \"module.exports = -1;\\n\", \"utf8\");\n"
                + "const answer: number = 42;\n"
                + "module.exports = answer;";

        NodeTypeScriptStripper.Result result = NodeTypeScriptStripper.stripIfTypeScript(
                "computed-module.cts",
                source,
                true
        );

        assertTrue(result.stripped());
        assertTrue(result.source().contains("raw.includes(\"const answer: number = 42;\")"));
        assertTrue(result.source().contains(
                "throw new Error(\"provider materialization was not raw TypeScript\")"
        ));
        assertTrue(result.source().contains(
                "fs.writeFileSync(__filename, \"module.exports = -1;\\n\", \"utf8\")"
        ));
        assertTrue(result.source().contains("const raw= fs.readFileSync"));
        assertTrue(result.source().contains("const answer= 42;"));
    }

    @Test
    public void tsxExtensionFailsWithCanonicalExtensionCode() {
        NodeTypeScriptStripper.UnsupportedTypeScriptException defaultPolicyError =
                expectUnsupported("src/App.TSX", "const view = <View />;\n", false);
        NodeTypeScriptStripper.UnsupportedTypeScriptException error =
                expectUnsupported("src/App.TSX", "const view = <View />;\n");

        assertTrue(NodeTypeScriptStripper.isUnsupportedTypeScriptSourceName("src/App.TSX"));
        assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_EXTENSION,
                defaultPolicyError.errorCode());
        assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_EXTENSION, error.errorCode());
        assertEquals("tsx", error.syntaxKind());
        assertEquals(1, error.line());
        assertEquals(1, error.column());
        assertEquals("src/App.TSX", error.diagnostics().get("embedded_script.typescript.source"));
    }

    @Test
    public void preloadedModuleSourcesPreserveNamesOrderAndAggregateDiagnostics() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("src/plain.js", "const plain = 1;\n");
        sources.put("src/first.ts", "const first: number = 1;\n");
        sources.put("types/globals.d.ts", "declare enum RuntimeState { Ready }\n");
        sources.put("src/second.mts", "export const second: string = 'two';\n");

        NodeTypeScriptStripper.SourceMapResult result =
                NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources, true);

        assertEquals(new ArrayList<>(sources.keySet()), new ArrayList<>(result.sources().keySet()));
        assertEquals("const plain = 1;\n", result.sources().get("src/plain.js"));
        assertEquals("const first= 1;\n", result.sources().get("src/first.ts"));
        assertEquals(
                "declare enum RuntimeState { Ready }\n",
                result.sources().get("types/globals.d.ts")
        );
        assertEquals("export const second= 'two';\n", result.sources().get("src/second.mts"));

        List<String> diagnosticKeys = new ArrayList<>(result.diagnostics().keySet());
        assertEquals(Arrays.asList(
                "embedded_script.typescript.module_sources.input_count",
                "embedded_script.typescript.module_sources.typescript_count",
                "embedded_script.typescript.module_sources.stripped_count",
                "embedded_script.typescript.module_sources.detail_count",
                "embedded_script.typescript.module_sources.detail_truncated_count",
                "embedded_script.typescript.module_sources.source.0.name",
                "embedded_script.typescript.module_sources.source.0.name_truncated",
                "embedded_script.typescript.module_sources.source.0.extension",
                "embedded_script.typescript.module_sources.source.0.stripped",
                "embedded_script.typescript.module_sources.source.1.name",
                "embedded_script.typescript.module_sources.source.1.name_truncated",
                "embedded_script.typescript.module_sources.source.1.extension",
                "embedded_script.typescript.module_sources.source.1.stripped"
        ), diagnosticKeys);
        assertEquals("4", result.diagnostics().get(
                "embedded_script.typescript.module_sources.input_count"
        ));
        assertEquals("2", result.diagnostics().get(
                "embedded_script.typescript.module_sources.typescript_count"
        ));
        assertEquals("2", result.diagnostics().get(
                "embedded_script.typescript.module_sources.stripped_count"
        ));
        assertEquals("2", result.diagnostics().get(
                "embedded_script.typescript.module_sources.detail_count"
        ));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.typescript.module_sources.detail_truncated_count"
        ));
        assertEquals("src/first.ts", result.diagnostics().get(
                "embedded_script.typescript.module_sources.source.0.name"
        ));
        assertEquals("src/second.mts", result.diagnostics().get(
                "embedded_script.typescript.module_sources.source.1.name"
        ));
        assertEquals("false", result.diagnostics().get(
                "embedded_script.typescript.module_sources.source.0.name_truncated"
        ));
    }

    @Test
    public void runtimeModulesAreUntouchedUnlessTheirNamesDeclareTypeScript() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        String generatedWithoutExtension = "const generated: number = 7;\n";
        sources.put("autojs6:generated-runtime", generatedWithoutExtension);
        sources.put("autojs6:generated-runtime.ts", "const generated: number = 8;\n");
        sources.put("autojs6:generated-config.json", "{\"typed\":\"value: number\"}");

        NodeTypeScriptStripper.SourceMapResult result =
                NodeJsRuntimePluginService.prepareTypeScriptRuntimeModuleSourcesForNative(sources, true);

        assertEquals(new ArrayList<>(sources.keySet()), new ArrayList<>(result.sources().keySet()));
        assertEquals(generatedWithoutExtension, result.sources().get("autojs6:generated-runtime"));
        assertEquals("const generated= 8;\n", result.sources().get("autojs6:generated-runtime.ts"));
        assertEquals(
                "{\"typed\":\"value: number\"}",
                result.sources().get("autojs6:generated-config.json")
        );
        assertEquals("3", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.input_count"
        ));
        assertEquals("1", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.typescript_count"
        ));
        assertEquals("1", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.stripped_count"
        ));
    }

    @Test
    public void preloadedSourceMapFailsClosedAtFirstUnsupportedSourceInInsertionOrder() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("src/valid.ts", "const valid: boolean = true;\n");
        sources.put("src/first-invalid.ts", "const ready = true;\n@sealed\nclass Runtime {}\n");
        sources.put("src/later-invalid.tsx", "const view = <View />;\n");

        try {
            NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources, true);
            fail("Expected preloaded TypeScript module failure");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_SYNTAX, error.errorCode());
            assertEquals("src/first-invalid.ts", error.sourceName());
            assertEquals("decorator", error.syntaxKind());
            assertEquals(2, error.line());
            assertEquals(1, error.column());
            assertEquals("src/first-invalid.ts", error.diagnostics().get(
                    "embedded_script.typescript.source"
            ));
        }
    }

    @Test
    public void emptyPreloadedSourceMapsProduceStableZeroDiagnostics() {
        NodeTypeScriptStripper.SourceMapResult result =
                NodeJsRuntimePluginService.prepareTypeScriptRuntimeModuleSourcesForNative(null, true);

        assertTrue(result.sources().isEmpty());
        assertEquals(Arrays.asList(
                "embedded_script.typescript.runtime_module_sources.input_count",
                "embedded_script.typescript.runtime_module_sources.typescript_count",
                "embedded_script.typescript.runtime_module_sources.stripped_count",
                "embedded_script.typescript.runtime_module_sources.detail_count",
                "embedded_script.typescript.runtime_module_sources.detail_truncated_count"
        ), new ArrayList<>(result.diagnostics().keySet()));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.input_count"
        ));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.typescript_count"
        ));
        assertEquals("0", result.diagnostics().get(
                "embedded_script.typescript.runtime_module_sources.stripped_count"
        ));
    }

    @Test
    public void preloadedSourceDiagnosticsAreCappedWithoutLosingAggregateCounts() {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        int sourceCount = NodeTypeScriptStripper.SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT + 5;
        for (int index = 0; index < sourceCount; index++) {
            sources.put("src/module-" + index + ".ts", "const value" + index + ": number = " + index + ";\n");
        }

        NodeTypeScriptStripper.SourceMapResult result =
                NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources, true);

        assertEquals(sourceCount, result.sources().size());
        assertEquals(Integer.toString(sourceCount), result.diagnostics().get(
                "embedded_script.typescript.module_sources.input_count"
        ));
        assertEquals(Integer.toString(sourceCount), result.diagnostics().get(
                "embedded_script.typescript.module_sources.typescript_count"
        ));
        assertEquals(Integer.toString(sourceCount), result.diagnostics().get(
                "embedded_script.typescript.module_sources.stripped_count"
        ));
        assertEquals(
                Integer.toString(NodeTypeScriptStripper.SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT),
                result.diagnostics().get("embedded_script.typescript.module_sources.detail_count")
        );
        assertEquals("5", result.diagnostics().get(
                "embedded_script.typescript.module_sources.detail_truncated_count"
        ));
        assertTrue(result.diagnostics().containsKey(
                "embedded_script.typescript.module_sources.source.15.name"
        ));
        assertFalse(result.diagnostics().containsKey(
                "embedded_script.typescript.module_sources.source.16.name"
        ));
        assertEquals(
                5 + NodeTypeScriptStripper.SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT * 4,
                result.diagnostics().size()
        );
    }

    @Test
    public void diagnosticSourceNamesAreBoundedWithoutChangingActualMapKeys() {
        String longName = repeat('a', 125)
                + "\uD83D\uDE80"
                + repeat('b', 300)
                + "/module.ts";
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put(longName, "const value: number = 7;\n");

        NodeTypeScriptStripper.SourceMapResult result =
                NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(sources, true);

        assertTrue(result.sources().containsKey(longName));
        assertEquals("const value= 7;\n", result.sources().get(longName));
        String diagnosticName = result.diagnostics().get(
                "embedded_script.typescript.module_sources.source.0.name"
        );
        assertTrue(diagnosticName.length()
                <= NodeTypeScriptStripper.DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS);
        assertTrue(diagnosticName.startsWith(repeat('a', 125) + "..."));
        assertTrue(diagnosticName.endsWith("/module.ts"));
        assertEquals("true", result.diagnostics().get(
                "embedded_script.typescript.module_sources.source.0.name_truncated"
        ));
        assertWellFormedUtf16(diagnosticName);

        NodeTypeScriptStripper.Result entry =
                NodeJsRuntimePluginService.prepareTypeScriptEntryForNative(
                        longName,
                        "const value: number = 8;\n",
                        true
                );
        assertEquals(diagnosticName, entry.diagnostics().get("embedded_script.typescript.source"));
        assertEquals("true", entry.diagnostics().get(
                "embedded_script.typescript.source_truncated"
        ));
    }

    @Test
    public void unsupportedLongSourceNameKeepsCanonicalFailureBounded() {
        String longName = repeat('p', 400) + "/component.tsx";

        NodeTypeScriptStripper.UnsupportedTypeScriptException error =
                expectUnsupported(longName, "const view = <View />;\n");

        String diagnosticName = error.diagnostics().get("embedded_script.typescript.source");
        assertEquals(longName, error.sourceName());
        assertTrue(diagnosticName.length()
                <= NodeTypeScriptStripper.DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS);
        assertTrue(diagnosticName.endsWith("/component.tsx"));
        assertEquals("true", error.diagnostics().get(
                "embedded_script.typescript.source_truncated"
        ));
        assertFalse(error.getMessage().contains(longName));
        assertTrue(error.getMessage().contains(diagnosticName));
        assertWellFormedUtf16(diagnosticName);
    }

    private static String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(character);
        }
        return result.toString();
    }

    private static void assertWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                assertTrue(index + 1 < value.length());
                assertTrue(Character.isLowSurrogate(value.charAt(index + 1)));
                index++;
            } else {
                assertFalse(Character.isLowSurrogate(character));
            }
        }
    }

    private static NodeTypeScriptStripper.UnsupportedTypeScriptException expectUnsupported(
            String sourceName,
            String source
    ) {
        return expectUnsupported(sourceName, source, true);
    }

    private static NodeTypeScriptStripper.UnsupportedTypeScriptException expectUnsupported(
            String sourceName,
            String source,
            boolean legacyTypeScriptStrippingEnabled
    ) {
        try {
            NodeJsRuntimePluginService.prepareTypeScriptEntryForNative(
                    sourceName,
                    source,
                    legacyTypeScriptStrippingEnabled
            );
            fail("Expected unsupported TypeScript input to fail closed: " + sourceName);
            throw new AssertionError("unreachable");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException expected) {
            return expected;
        }
    }
}

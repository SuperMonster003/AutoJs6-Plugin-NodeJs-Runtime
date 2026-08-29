package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginModuleSourceProviderTypeScriptPreparationTest {

    private static final File PRIVATE_REQUEST_DIRECTORY =
            new File("build/tmp/plaintext-typescript-private-requests").getAbsoluteFile();

    @Test
    public void v3CompilationUsesAnIndependentBoundedTransportBudget() {
        assertEquals(
                30_000L,
                PluginModuleSourceProviderFileTransportSession.selectPerRequestTimeoutMs(
                        5_000L,
                        30_000L,
                        30_000L,
                        true
                )
        );
        assertEquals(
                7_000L,
                PluginModuleSourceProviderFileTransportSession.selectPerRequestTimeoutMs(
                        5_000L,
                        30_000L,
                        7_000L,
                        true
                )
        );
        assertEquals(
                5_000L,
                PluginModuleSourceProviderFileTransportSession.selectPerRequestTimeoutMs(
                        5_000L,
                        30_000L,
                        30_000L,
                        false
                )
        );
    }

    @Test
    public void mappedResolvedPathStripsDecryptedTypeScriptAndKeepsRawPreparedByteTruthSeparate()
            throws Exception {
        String sourceName = "/workspace/src/入口.cts";
        String source = "const marker: string = '值';\nexport default marker;\n";
        byte[] rawSource = source.getBytes(StandardCharsets.UTF_8);

        PluginModuleSourceProviderFileTransportSession.PreparedTypeScriptSource prepared =
                PluginModuleSourceProviderFileTransportSession
                        .prepareDecryptedTypeScriptBytes(sourceName, rawSource, true);

        String output = new String(prepared.source(), StandardCharsets.UTF_8);
        assertTrue(prepared.typeScript());
        assertTrue(prepared.stripped());
        assertFalse(output.contains(": string"));
        assertTrue(output.contains("值"));
        assertEquals(rawSource.length, prepared.rawSourceBytes());
        assertEquals(prepared.source().length, prepared.sourceBytes());
        assertEquals("cts", prepared.diagnostics().get(
                "embedded_script.typescript.extension"
        ));
    }

    @Test
    public void x3fPlaintextPreparationPreservesRawInspectionLiterals() throws Exception {
        String source = "const fs = require(\"fs\");\n"
                + "const raw: string = fs.readFileSync(__filename, \"utf8\");\n"
                + "if (!raw.includes(\"const answer: number = 42;\")) {\n"
                + "  throw new Error(\"provider materialization was not raw TypeScript\");\n"
                + "}\n"
                + "console.log(\"x3f.raw-ts=true\");\n"
                + "fs.writeFileSync(__filename, \"module.exports = -1;\\n\", \"utf8\");\n"
                + "const answer: number = 42;\n"
                + "module.exports = answer;";
        byte[] rawSource = source.getBytes(StandardCharsets.UTF_8);

        PluginModuleSourceProviderFileTransportSession.PreparedTypeScriptSource prepared =
                PluginModuleSourceProviderFileTransportSession
                        .prepareDecryptedTypeScriptBytes("computed-module.cts", rawSource, true);

        String output = new String(prepared.source(), StandardCharsets.UTF_8);
        assertEquals(rawSource.length, prepared.rawSourceBytes());
        assertTrue(prepared.typeScript());
        assertTrue(prepared.stripped());
        assertTrue(output.contains("raw.includes(\"const answer: number = 42;\")"));
        assertTrue(output.contains(
                "throw new Error(\"provider materialization was not raw TypeScript\")"
        ));
        assertTrue(output.contains(
                "fs.writeFileSync(__filename, \"module.exports = -1;\\n\", \"utf8\")"
        ));
        assertTrue(output.contains("const raw= fs.readFileSync"));
        assertTrue(output.contains("const answer= 42;"));
    }

    @Test
    public void privateTransportSourceNameDoesNotInventTypeScriptAuthority() throws Exception {
        byte[] rawSource = "const answer: number = 42;\n".getBytes(StandardCharsets.UTF_8);

        PluginModuleSourceProviderFileTransportSession.PreparedTypeScriptSource privateName =
                PluginModuleSourceProviderFileTransportSession
                        .prepareDecryptedTypeScriptBytes("request-42.source", rawSource, false);
        PluginModuleSourceProviderFileTransportSession.PreparedTypeScriptSource resolvedName =
                PluginModuleSourceProviderFileTransportSession
                        .prepareDecryptedTypeScriptBytes("src/answer.mts", rawSource, true);

        assertFalse(privateName.typeScript());
        assertFalse(privateName.stripped());
        assertArrayEquals(rawSource, privateName.source());
        assertTrue(resolvedName.typeScript());
        assertTrue(resolvedName.stripped());
    }

    @Test
    public void nonTypeScriptProviderBytesRemainExactWithoutUtf8Admission() throws Exception {
        byte[] opaqueJavaScript = new byte[]{(byte) 0xc3, 0x28, 0x00, (byte) 0xff};

        PluginModuleSourceProviderFileTransportSession.PreparedTypeScriptSource prepared =
                PluginModuleSourceProviderFileTransportSession
                        .prepareDecryptedTypeScriptBytes("vendor/addon.js", opaqueJavaScript, false);

        assertFalse(prepared.typeScript());
        assertFalse(prepared.stripped());
        assertEquals(opaqueJavaScript.length, prepared.rawSourceBytes());
        assertEquals(opaqueJavaScript.length, prepared.sourceBytes());
        assertArrayEquals(opaqueJavaScript, prepared.source());
    }

    @Test
    public void malformedTypeScriptUtf8FailsClosedBeforeStripping() {
        byte[] malformedUtf8 = new byte[]{(byte) 0xc3, 0x28};

        try {
            PluginModuleSourceProviderFileTransportSession
                    .prepareDecryptedTypeScriptBytes("src/broken.ts", malformedUtf8, false);
            fail("Expected strict UTF-8 rejection");
        } catch (CharacterCodingException expected) {
            assertTrue(expected.getMessage() == null || !expected.getMessage().isEmpty());
        }
    }

    @Test
    public void tsxFailureKeepsCanonicalExtensionErrorForNativePropagation() throws Exception {
        try {
            PluginModuleSourceProviderFileTransportSession.prepareDecryptedTypeScriptBytes(
                    "src/view.tsx",
                    "export const view = <View />;\n".getBytes(StandardCharsets.UTF_8),
                    true
            );
            fail("Expected TSX rejection");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException expected) {
            assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_EXTENSION, expected.errorCode());
            assertEquals("src/view.tsx", expected.sourceName());
            assertEquals("tsx", expected.syntaxKind());
            assertEquals(1, expected.line());
            assertEquals(1, expected.column());
            assertTrue(expected.getMessage().contains(expected.errorCode()));
        }
    }

    @Test
    public void enumFailureKeepsCanonicalSyntaxDetailsForNativePropagation() throws Exception {
        try {
            PluginModuleSourceProviderFileTransportSession.prepareDecryptedTypeScriptBytes(
                    "src/mode.ts",
                    "export enum Mode { Ready }\n".getBytes(StandardCharsets.UTF_8),
                    true
            );
            fail("Expected enum rejection");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException expected) {
            assertEquals(NodeTypeScriptStripper.ERROR_UNSUPPORTED_SYNTAX, expected.errorCode());
            assertEquals("src/mode.ts", expected.sourceName());
            assertEquals("enum", expected.syntaxKind());
            assertTrue(expected.line() >= 1);
            assertTrue(expected.column() >= 1);
            assertTrue(expected.getMessage().contains(expected.errorCode()));
            assertTrue(expected.getMessage().contains(expected.sourceName()));
        }
    }

    @Test
    public void providerTypeScriptRequiresCompilerUnlessLegacyPreparationIsExplicitlyEnabled()
            throws Exception {
        try {
            PluginModuleSourceProviderFileTransportSession.prepareDecryptedTypeScriptBytes(
                    "src/dynamic.cts",
                    "const answer: number = 42;\n".getBytes(StandardCharsets.UTF_8),
                    false
            );
            fail("Expected provider TypeScript compiler-required rejection");
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException expected) {
            assertEquals(NodeTypeScriptStripper.ERROR_COMPILER_REQUIRED, expected.errorCode());
            assertEquals("compiler_required", expected.syntaxKind());
            assertEquals("src/dynamic.cts", expected.sourceName());
        }
    }

    @Test
    public void providerLastSourceDiagnosticIsBasenameOnlyAndBounded() {
        StringBuilder longName = new StringBuilder("/private/workspace/");
        for (int index = 0; index < 300; index++) {
            longName.append('a');
        }
        longName.append(".ts");

        String diagnosticName = PluginModuleSourceProviderFileTransportSession
                .diagnosticSourceName(longName.toString());

        assertEquals(256, diagnosticName.length());
        assertFalse(diagnosticName.contains("/"));
        assertTrue(diagnosticName.endsWith(".ts"));
    }

    @Test
    public void providerLastSourceDiagnosticDoesNotSplitUtf16SurrogatePair() {
        StringBuilder boundaryName = new StringBuilder("/private/workspace/\ud83d\ude80");
        for (int index = 0; index < 252; index++) {
            boundaryName.append('a');
        }
        boundaryName.append(".ts");

        String diagnosticName = PluginModuleSourceProviderFileTransportSession
                .diagnosticSourceName(boundaryName.toString());

        assertEquals(255, diagnosticName.length());
        assertFalse(Character.isLowSurrogate(diagnosticName.charAt(0)));
        assertFalse(Character.isHighSurrogate(diagnosticName.charAt(diagnosticName.length() - 1)));
        assertTrue(diagnosticName.endsWith(".ts"));
    }

    @Test
    public void plaintextPreparationEnvelopeAcceptsOnlyExactPrivatePathAndIntegerBytes()
            throws Exception {
        for (String sourceName : new String[]{"src/main.ts", "src/main.mts", "src/main.cts", "src/view.tsx"}) {
            String id = "typescript-envelope";
            File expected = new File(PRIVATE_REQUEST_DIRECTORY, id + ".source");
            long bytes = PluginModuleSourceProviderFileTransportSession
                    .validatePlaintextTypeScriptPreparationEnvelope(
                            PRIVATE_REQUEST_DIRECTORY,
                            id,
                            sourceName,
                            expected.getAbsolutePath(),
                            17L
                    );
            assertEquals(17L, bytes);
        }
    }

    @Test
    public void plaintextPreparationEnvelopeRejectsPathSmugglingAndNonIntegerBytes() {
        expectEnvelopeFailure("src/main.ts", new File(PRIVATE_REQUEST_DIRECTORY, "other.source"), 17L);
        expectEnvelopeFailure(
                "src/main.ts",
                new File(PRIVATE_REQUEST_DIRECTORY, "typescript-envelope.source"),
                "17"
        );
        expectEnvelopeFailure(
                "src/main.ts",
                new File(PRIVATE_REQUEST_DIRECTORY, "typescript-envelope.source"),
                17.0d
        );
    }

    @Test
    public void plaintextPreparationEnvelopeRejectsNonTypeScriptAndDeclarationSources() {
        File expected = new File(PRIVATE_REQUEST_DIRECTORY, "typescript-envelope.source");
        expectEnvelopeFailure("src/main.js", expected, 17L);
        expectEnvelopeFailure("src/package.json", expected, 17L);
        expectEnvelopeFailure("src/types.d.ts", expected, 17L);
        expectEnvelopeFailure("src/types.d.mts", expected, 17L);
        expectEnvelopeFailure("src/types.d.cts", expected, 17L);
    }

    @Test
    public void plaintextPreparationEnvelopeEnforcesRawSingleSourceBudget() throws Exception {
        File expected = new File(PRIVATE_REQUEST_DIRECTORY, "typescript-envelope.source");
        assertEquals(
                PluginModuleSourceProviderFileTransportSession.SINGLE_SOURCE_BYTES_LIMIT,
                PluginModuleSourceProviderFileTransportSession.validatePlaintextTypeScriptPreparationEnvelope(
                        PRIVATE_REQUEST_DIRECTORY,
                        "typescript-envelope",
                        "src/main.ts",
                        expected.getAbsolutePath(),
                        PluginModuleSourceProviderFileTransportSession.SINGLE_SOURCE_BYTES_LIMIT
                )
        );
        expectEnvelopeFailure("src/main.ts", expected, -1L);
        expectEnvelopeFailure(
                "src/main.ts",
                expected,
                PluginModuleSourceProviderFileTransportSession.SINGLE_SOURCE_BYTES_LIMIT + 1L
        );
    }

    @Test
    public void providerV2ResponseShapeAcceptsOnlyOperationCompatiblePfdStatuses() throws Exception {
        validateProviderResponseShape(
                "resolve_existing",
                "decrypted",
                true,
                true,
                17L,
                false
        );
        validateProviderResponseShape(
                "resolve_existing",
                "not_encrypted",
                false,
                true,
                0L,
                false
        );
        validateProviderResponseShape(
                "materialize_missing_plaintext",
                "plaintext",
                true,
                true,
                17L,
                true
        );
    }

    @Test
    public void providerV2PositiveStatusesRemainBoundToTheExactRequestedCandidate() throws Exception {
        for (String status : new String[]{"decrypted", "not_encrypted", "plaintext"}) {
            PluginModuleSourceProviderFileTransportSession.validatePositiveProviderResolvedPath(
                    "/runtime/workspace/pkg/value.cts",
                    "/runtime/workspace/pkg/value.cts",
                    status
            );
            try {
                PluginModuleSourceProviderFileTransportSession.validatePositiveProviderResolvedPath(
                        "/runtime/workspace/pkg/value.cts",
                        "/runtime/workspace/pkg/other.cts",
                        status
                );
                fail("Expected exact resolved-path rejection for " + status);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("exact candidate"));
            }
        }
        PluginModuleSourceProviderFileTransportSession.validatePositiveProviderResolvedPath(
                "/runtime/workspace/pkg/value.cts",
                "/runtime/workspace/pkg/other.cts",
                "not_found"
        );
    }

    @Test
    public void providerV2ResponseShapeRejectsWrongOperationAndMixedStatusPairs() {
        expectProviderResponseShapeFailure(
                "resolve_existing", "plaintext", true, true, 17L, false
        );
        expectProviderResponseShapeFailure(
                "materialize_missing_plaintext", "decrypted", true, true, 17L, true
        );
        expectProviderResponseShapeFailure(
                "materialize_missing_plaintext", "not_encrypted", false, true, 0L, true
        );
        try {
            PluginModuleSourceProviderFileTransportSession.validateProviderResponseShape(
                    PluginModuleSourceProviderFileTransportSession.CONTRACT_VERSION,
                    "provider-v2-response",
                    "provider-v2-response",
                    "resolve_existing",
                    "materialize_missing_plaintext",
                    "plaintext",
                    true,
                    true,
                    17L,
                    true,
                    false
            );
            fail("Expected response-operation echo rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("operation"));
        }
    }

    @Test
    public void providerV2ResponseShapeRejectsSourceDataOnEveryNonPfdStatus() {
        for (String status : new String[]{
                "not_encrypted", "not_found", "denied", "cancelled", "timed_out", "failed"
        }) {
            expectProviderResponseShapeFailure(
                    "resolve_existing", status, true, true, 0L, false
            );
            expectProviderResponseShapeFailure(
                    "resolve_existing", status, false, true, 1L, false
            );
        }
    }

    @Test
    public void providerV2ResponseShapeRequiresBothPfdAndDeclaredByteField() {
        expectProviderResponseShapeFailure(
                "resolve_existing", "decrypted", false, true, 17L, false
        );
        expectProviderResponseShapeFailure(
                "resolve_existing", "decrypted", true, false, 0L, false
        );
        expectProviderResponseShapeFailure(
                "materialize_missing_plaintext", "plaintext", false, true, 17L, true
        );
        expectProviderResponseShapeFailure(
                "materialize_missing_plaintext", "plaintext", true, false, 0L, true
        );
    }

    @Test
    public void providerV3CompilationBindsPfdOutputToExactGeneratedPath() throws Exception {
        PluginModuleSourceProviderFileTransportSession.validateProviderResponseShape(
                PluginModuleSourceProviderFileTransportSession.CONTRACT_VERSION,
                "provider-v3-compile",
                "provider-v3-compile",
                "compile_missing_typescript",
                "compile_missing_typescript",
                "compiled_typescript",
                true,
                true,
                17L,
                false,
                true
        );
        for (String[] pair : new String[][]{
                {"/runtime/src/new.ts", "/runtime/src/new.js"},
                {"/runtime/src/new.mts", "/runtime/src/new.mjs"},
                {"/runtime/src/new.cts", "/runtime/src/new.cjs"}
        }) {
            PluginModuleSourceProviderFileTransportSession.validateCompiledTypeScriptResolvedPath(
                    pair[0], pair[1], "compiled_typescript"
            );
        }
        try {
            PluginModuleSourceProviderFileTransportSession.validateCompiledTypeScriptResolvedPath(
                    "/runtime/src/new.ts", "/runtime/src/other.js", "compiled_typescript"
            );
            fail("Expected generated-path mismatch rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exact generated module"));
        }
    }

    @Test
    public void providerV2ResponseRawTypesRejectCoercibleControlAndByteValues() {
        expectProviderResponseRawTypeFailure(
                2L, "id", "resolve_existing", "not_found", false, false, false, null
        );
        expectProviderResponseRawTypeFailure(
                2, new StringBuilder("id"), "resolve_existing", "not_found",
                false, false, false, null
        );
        expectProviderResponseRawTypeFailure(
                2, "id", 7, "not_found", false, false, false, null
        );
        expectProviderResponseRawTypeFailure(
                2, "id", "resolve_existing", 7, false, false, false, null
        );
        expectProviderResponseRawTypeFailure(
                2, "id", "resolve_existing", "decrypted", true, false, true, 17L
        );
        expectProviderResponseRawTypeFailure(
                2, "id", "resolve_existing", "decrypted", true, true, true, 17
        );
        expectProviderResponseRawTypeFailure(
                2, "id", "resolve_existing", "decrypted", true, true, true, "17"
        );
    }

    @Test
    public void providerV2ResponseRawTypesAcceptExactBundleWireTypes() throws Exception {
        PluginModuleSourceProviderFileTransportSession.validateProviderResponseRawTypes(
                2,
                "id",
                "materialize_missing_plaintext",
                "plaintext",
                true,
                true,
                true,
                17L
        );
    }

    private static void expectEnvelopeFailure(String sourceName, File sourcePath, Object bytes) {
        try {
            PluginModuleSourceProviderFileTransportSession.validatePlaintextTypeScriptPreparationEnvelope(
                    PRIVATE_REQUEST_DIRECTORY,
                    "typescript-envelope",
                    sourceName,
                    sourcePath.getAbsolutePath(),
                    bytes
            );
            fail("Expected plaintext TypeScript preparation envelope rejection");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static void validateProviderResponseShape(
            String operation,
            String status,
            boolean hasSourceFd,
            boolean hasSourceBytes,
            long sourceBytes,
            boolean materializationRequest
    ) throws IOException {
        PluginModuleSourceProviderFileTransportSession.validateProviderResponseShape(
                PluginModuleSourceProviderFileTransportSession.CONTRACT_VERSION,
                "provider-v2-response",
                "provider-v2-response",
                operation,
                operation,
                status,
                hasSourceFd,
                hasSourceBytes,
                sourceBytes,
                materializationRequest,
                false
        );
    }

    private static void expectProviderResponseShapeFailure(
            String operation,
            String status,
            boolean hasSourceFd,
            boolean hasSourceBytes,
            long sourceBytes,
            boolean materializationRequest
    ) {
        try {
            validateProviderResponseShape(
                    operation,
                    status,
                    hasSourceFd,
                    hasSourceBytes,
                    sourceBytes,
                    materializationRequest
            );
            fail("Expected provider-v2 response-shape rejection for " + status);
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static void expectProviderResponseRawTypeFailure(
            Object version,
            Object responseId,
            Object operation,
            Object status,
            boolean hasSourceFd,
            boolean sourceFdIsParcelFileDescriptor,
            boolean hasSourceBytes,
            Object sourceBytes
    ) {
        try {
            PluginModuleSourceProviderFileTransportSession.validateProviderResponseRawTypes(
                    version,
                    responseId,
                    operation,
                    status,
                    hasSourceFd,
                    sourceFdIsParcelFileDescriptor,
                    hasSourceBytes,
                    sourceBytes
            );
            fail("Expected provider-v2 raw response-type rejection");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}

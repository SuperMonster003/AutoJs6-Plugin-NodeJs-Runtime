package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginModuleSourceProviderContractTest {

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

package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginModuleSourceProviderMetadataPolicyTest {

    @Test
    public void exactMetadataPathsAreEmptyWhenTrustedOverridesNeedNoMetadata() throws Exception {
        assertTrue(paths("work", "sandbox", false, false).isEmpty());
    }

    @Test
    public void bridgeLimitsNeedOnlyTwoExactWorkingDirectoryPaths() throws Exception {
        String working = absolute("work");
        assertEquals(
                List.of(
                        new File(working, "project.json").getAbsolutePath(),
                        new File(working, "package.json").getAbsolutePath()
                ),
                paths(working, absolute("sandbox"), false, true)
        );
    }

    @Test
    public void permissionMetadataUsesFourBoundedPathsWhenRootsDiffer() throws Exception {
        String working = absolute("work");
        String sandbox = absolute("sandbox");
        assertEquals(
                List.of(
                        new File(working, "project.json").getAbsolutePath(),
                        new File(working, "package.json").getAbsolutePath(),
                        new File(sandbox, "project.json").getAbsolutePath(),
                        new File(sandbox, "package.json").getAbsolutePath()
                ),
                paths(working, sandbox, true, true)
        );
    }

    @Test
    public void permissionMetadataDeduplicatesWorkingAndSandboxRoots() throws Exception {
        String root = absolute("same-root");
        assertEquals(2, paths(root, root, true, true).size());
    }

    @Test
    public void providerFirstPolicyReadsWorkspaceOnlyAfterNotEncrypted() {
        assertTrue(PluginModuleSourceProviderFileTransportSession
                .metadataProviderStatusReadsWorkspace("not_encrypted"));
        for (String status : new String[]{
                "decrypted", "not_found", "denied", "failed", "cancelled", "timed_out"
        }) {
            assertFalse(PluginModuleSourceProviderFileTransportSession
                    .metadataProviderStatusReadsWorkspace(status));
        }
    }

    @Test
    public void onlyNotFoundAllowsPolicyDefaults() {
        assertTrue(PluginModuleSourceProviderFileTransportSession
                .metadataProviderStatusAllowsDefault("not_found"));
        for (String status : new String[]{
                "decrypted", "not_encrypted", "denied", "failed", "cancelled", "timed_out"
        }) {
            assertFalse(PluginModuleSourceProviderFileTransportSession
                    .metadataProviderStatusAllowsDefault(status));
        }
    }

    @Test
    public void deniedFailedCancelledAndTimeoutAreTerminal() {
        for (String status : new String[]{"denied", "failed", "cancelled", "timed_out"}) {
            assertTrue(PluginModuleSourceProviderFileTransportSession
                    .metadataProviderStatusIsTerminal(status));
        }
        for (String status : new String[]{"decrypted", "not_encrypted", "not_found"}) {
            assertFalse(PluginModuleSourceProviderFileTransportSession
                    .metadataProviderStatusIsTerminal(status));
        }
    }

    @Test
    public void materializedMetadataReplaysAsNormalNotEncryptedResolve() {
        assertEquals(
                "not_encrypted",
                PluginModuleSourceProviderFileTransportSession
                        .metadataNormalResolveReplayStatus("materialized_plaintext")
        );
        assertEquals(
                "not_encrypted",
                PluginModuleSourceProviderFileTransportSession
                        .metadataNormalResolveReplayStatus("not_encrypted")
        );
        assertEquals(
                "decrypted",
                PluginModuleSourceProviderFileTransportSession
                        .metadataNormalResolveReplayStatus("decrypted")
        );
        assertEquals(
                "not_found",
                PluginModuleSourceProviderFileTransportSession
                        .metadataNormalResolveReplayStatus("not_found")
        );
    }

    @Test
    public void terminalMetadataStatusCannotEnterReplayCache() {
        try {
            PluginModuleSourceProviderFileTransportSession
                    .metadataNormalResolveReplayStatus("denied");
            fail("Expected terminal metadata replay rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("cannot be replayed"));
        }
    }

    @Test
    public void invalidProviderShapeRemainsTerminalBeforeMetadataConsumption() {
        try {
            PluginModuleSourceProviderFileTransportSession.validateProviderResponseShape(
                    PluginModuleSourceProviderFileTransportSession.CONTRACT_VERSION,
                    "metadata-id",
                    "metadata-id",
                    "resolve_existing",
                    "resolve_existing",
                    "decrypted",
                    false,
                    true,
                    7L,
                    false,
                    false
            );
            fail("Expected invalid metadata response rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("PFD"));
        }
    }

    @Test
    public void policyMetadataFailureBeforeNativeNeverCommitsWorkspace() {
        assertFalse(NodeJsRuntimePluginService.shouldCommitWorkspaceAfterFailure(false));
        assertTrue(NodeJsRuntimePluginService.shouldCommitWorkspaceAfterFailure(true));
    }

    private static List<String> paths(
            String working,
            String sandbox,
            boolean permission,
            boolean limits
    ) throws Exception {
        return PluginModuleSourceProviderFileTransportSession.policyMetadataExactPaths(
                working,
                sandbox,
                permission,
                limits
        );
    }

    private static String absolute(String name) {
        return new File("build/test-policy-metadata", name).getAbsolutePath();
    }
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginWorkspaceArchivePathMappingTest {

    private static final String HOST_PACKAGE = "io.github.supermonster003.autojs6.test";
    private static final String RUNTIME_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs";
    private static final String HOST_USER_ROOT =
            "/data/user/0/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox";
    private static final String HOST_DATA_ROOT =
            "/data/data/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox";
    private static final String RUNTIME_USER_ROOT =
            "/data/user/0/" + RUNTIME_PACKAGE + "/cache/node-plugin-workspaces/run/sandbox";
    private static final String RUNTIME_DATA_ROOT =
            "/data/data/" + RUNTIME_PACKAGE + "/cache/node-plugin-workspaces/run/sandbox";

    @Test
    public void hostCanonicalDataAliasMapsToRuntimeMirrorWithExactRelativeTail() {
        assertEquals(
                RUNTIME_USER_ROOT + "/lib/value.cts",
                map(HOST_DATA_ROOT + "/lib/value.cts", HOST_USER_ROOT, RUNTIME_USER_ROOT)
        );
        assertEquals(
                RUNTIME_USER_ROOT,
                map(HOST_DATA_ROOT, HOST_USER_ROOT, RUNTIME_USER_ROOT)
        );
    }

    @Test
    public void runtimeCanonicalDataAliasMapsBackToRequestedHostNamespace() {
        assertEquals(
                HOST_USER_ROOT + "/lib/value.cts",
                map(RUNTIME_DATA_ROOT + "/lib/value.cts", RUNTIME_USER_ROOT, HOST_USER_ROOT)
        );
        assertEquals(
                HOST_USER_ROOT,
                map(RUNTIME_DATA_ROOT, RUNTIME_USER_ROOT, HOST_USER_ROOT)
        );
    }

    @Test
    public void ordinarySameNamespaceContainmentRemainsExact() {
        assertEquals(
                RUNTIME_USER_ROOT + "/lib/value.cts",
                map(HOST_USER_ROOT + "/lib/value.cts", HOST_USER_ROOT, RUNTIME_USER_ROOT)
        );
        assertEquals(
                "/workspace/mirror/lib/value.cjs",
                map("/workspace/source/lib/value.cjs", "/workspace/source", "/workspace/mirror")
        );
    }

    @Test
    public void androidAliasRejectsCrossPackageAndPackagePrefixCollision() {
        String crossPackage =
                "/data/data/io.github.supermonster003.attacker/cache/node-conformance/run/sandbox/value.cts";
        String prefixCollision =
                "/data/data/" + HOST_PACKAGE + ".attacker/cache/node-conformance/run/sandbox/value.cts";

        assertEquals(crossPackage, map(crossPackage, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(prefixCollision, map(prefixCollision, HOST_USER_ROOT, RUNTIME_USER_ROOT));
    }

    @Test
    public void androidAliasRejectsDifferentTailAndNormalizedTraversalEscape() {
        String differentTail =
                "/data/data/" + HOST_PACKAGE + "/cache/node-conformance/other/value.cts";
        String traversalEscape = HOST_DATA_ROOT + "/../outside/value.cts";

        assertEquals(differentTail, map(differentTail, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(traversalEscape, map(traversalEscape, HOST_USER_ROOT, RUNTIME_USER_ROOT));
    }

    @Test
    public void androidAliasRejectsOtherUserAndPrefixSpoof() {
        String otherUser =
                "/data/user/10/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox/value.cts";
        String deviceEncrypted =
                "/data/user_de/0/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox/value.cts";
        String prefixSpoof =
                "/data/user/0foo/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox/value.cts";
        String legacyPrefixSpoof =
                "/data/datax/" + HOST_PACKAGE + "/cache/node-conformance/run/sandbox/value.cts";
        String siblingPrefix = HOST_DATA_ROOT + "-evil/value.cts";

        assertEquals(otherUser, map(otherUser, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(deviceEncrypted, map(deviceEncrypted, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(prefixSpoof, map(prefixSpoof, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(legacyPrefixSpoof, map(legacyPrefixSpoof, HOST_USER_ROOT, RUNTIME_USER_ROOT));
        assertEquals(siblingPrefix, map(siblingPrefix, HOST_USER_ROOT, RUNTIME_USER_ROOT));
    }

    @Test
    public void androidAliasRejectsInvalidApplicationId() {
        String invalidUserRoot = "/data/user/0/not-a-package/cache/run/sandbox";
        String invalidDataCandidate = "/data/data/not-a-package/cache/run/sandbox/value.cts";

        assertEquals(
                invalidDataCandidate,
                map(invalidDataCandidate, invalidUserRoot, RUNTIME_USER_ROOT)
        );
    }

    @Test
    public void providerMaterializationDeadlineAcceptsOnlyStrictlyPositiveRemainingTime()
            throws Exception {
        PluginWorkspaceArchiveSession.validateProviderMaterializationDeadline(
                999L,
                1000L,
                "before publication"
        );
    }

    @Test
    public void providerMaterializationDeadlineRejectsExpiredAndInvalidAbsoluteValues() {
        expectProviderMaterializationDeadlineFailure(1000L, 1000L);
        expectProviderMaterializationDeadlineFailure(1001L, 1000L);
        expectProviderMaterializationDeadlineFailure(0L, 0L);
    }

    @Test
    public void onDemandTypeScriptPathAdmissionRejectsDeclarationsAndOtherExtensions() {
        for (String accepted : new String[]{"runtime/new.ts", "runtime/new.mts", "runtime/new.cts"}) {
            assertTrue(PluginWorkspaceArchiveSession.isSupportedOnDemandTypeScriptPath(accepted));
        }
        for (String rejected : new String[]{
                "runtime/new.tsx", "runtime/new.js", "runtime/new.CTS", "runtime/types.d.ts",
                "runtime/types.d.mts", "runtime/types.d.cts", "runtime/new.ts/map"
        }) {
            assertFalse(PluginWorkspaceArchiveSession.isSupportedOnDemandTypeScriptPath(rejected));
        }
    }

    private static String map(String value, String sourceRoot, String destinationRoot) {
        return PluginWorkspaceArchiveSession.mapContainedPathAcrossAndroidCredentialAlias(
                value,
                sourceRoot,
                destinationRoot
        );
    }

    private static void expectProviderMaterializationDeadlineFailure(long now, long deadline) {
        try {
            PluginWorkspaceArchiveSession.validateProviderMaterializationDeadline(
                    now,
                    deadline,
                    "test"
            );
            fail("Expected provider materialization deadline rejection");
        } catch (PluginWorkspaceArchiveSession.ProviderMaterializationDeadlineExceededException expected) {
            assertTrue(expected.getMessage().contains("deadline expired"));
        }
    }
}

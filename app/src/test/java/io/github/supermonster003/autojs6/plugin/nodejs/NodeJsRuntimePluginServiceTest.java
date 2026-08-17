package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeJsRuntimePluginServiceTest {

    @Test
    public void procStatusLongValueParsesKilobytesAndCounts() {
        String status = "Name:\tnode\n"
                + "VmRSS:\t  48320 kB\n"
                + "Threads:\t17\n";

        assertEquals(
                48320L,
                NodeJsRuntimePluginService.procStatusLongValue(status, "VmRSS", -1L)
        );
        assertEquals(
                17L,
                NodeJsRuntimePluginService.procStatusLongValue(status, "Threads", -1L)
        );
    }

    @Test
    public void procStatusLongValueReturnsFallbackForMissingOrInvalidFields() {
        String status = "Name:\tnode\nVmRSS:\tunknown kB\n";

        assertEquals(
                7L,
                NodeJsRuntimePluginService.procStatusLongValue(status, "VmRSS", 7L)
        );
        assertEquals(
                9L,
                NodeJsRuntimePluginService.procStatusLongValue(status, "Threads", 9L)
        );
    }

    @Test
    public void moduleSourceProviderContractAcceptsExactV2WhenBinderIsPresent() {
        assertEquals(
                2,
                NodeJsRuntimePluginService.strictModuleSourceProviderContractVersion(Integer.valueOf(2))
        );
        assertTrue(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, 2));
    }

    @Test
    public void moduleSourceProviderContractAcceptsPublishedV1HostsAndRejectsFutureVersions() {
        assertTrue(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, 1));
        assertFalse(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, 3));
    }

    @Test
    public void moduleSourceProviderContractUsesRawStrictIntegerSemantics() {
        assertFalse(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, 2L));
        assertFalse(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, "2"));
        // Published v1 hosts never send the version field; absence means v1.
        assertTrue(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(true, null));
        assertTrue(NodeJsRuntimePluginService.supportsRequestedModuleSourceProviderContract(false, "wrong-type"));
    }
}

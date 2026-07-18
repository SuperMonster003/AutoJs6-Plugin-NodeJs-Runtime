package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BridgeSessionHistoryTest {
    @Test
    public void tenThousandCompletedCallsKeepAConstantSizeTail() {
        BridgeSessionHistory history = new BridgeSessionHistory();
        int fullTailLength = -1;
        for (int index = 0; index < 10_000; index++) {
            String id = String.format(Locale.ROOT, "call-%05d", index);
            assertTrue(history.beginRequest(id));
            assertFalse("an outstanding file must not be dispatched twice", history.beginRequest(id));
            history.recordResponse("{\"id\":\"" + id + "\",\"ok\":true}");
            history.forgetRequest(id);
            assertEquals(0, history.requestCount());
            assertTrue(history.responseCount() <= BridgeSessionHistory.MAX_RESPONSES);
            if (index == 31) fullTailLength = history.responsesJson().length();
            if (index >= 31) assertEquals(fullTailLength, history.responsesJson().length());
        }
        assertEquals(32, history.responseCount());
        assertTrue(history.responsesJson().startsWith("[{\"id\":\"call-09968\""));
        assertTrue(history.responsesJson().endsWith("{\"id\":\"call-09999\",\"ok\":true}]"));
        assertFalse(history.responsesJson().contains("call-00000"));
    }

    @Test
    public void largeAndMultibyteResponsesCannotExceedTheByteBudget() {
        BridgeSessionHistory history = new BridgeSessionHistory();
        String large = "{\"value\":\"" + "x".repeat(80_000) + "\"}";
        String multibyte = "{\"value\":\"" + "界".repeat(600) + "\"}";
        assertTrue(multibyte.length() < BridgeSessionHistory.MAX_RESPONSE_BYTES);
        for (int index = 0; index < 10_000; index++) {
            history.recordResponse(index % 2 == 0 ? large : multibyte);
        }
        assertEquals(32, history.responseCount());
        assertTrue(history.responsesJson().contains("\"diagnosticTruncated\":true"));
        assertTrue(history.responsesJson().getBytes(StandardCharsets.UTF_8).length < 64 * 1024);
    }
}

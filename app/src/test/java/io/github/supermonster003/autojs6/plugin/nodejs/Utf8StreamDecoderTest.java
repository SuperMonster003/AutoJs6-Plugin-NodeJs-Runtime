package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * M2.1 streaming: pipe chunks may split a multi-byte UTF-8 character between
 * two reads; the incremental decoder must hold the partial bytes back instead
 * of emitting U+FFFD.
 */
public final class Utf8StreamDecoderTest {

    @Test
    public void asciiPassesThrough() {
        NodeJsRuntimePluginService.Utf8StreamDecoder decoder =
                new NodeJsRuntimePluginService.Utf8StreamDecoder();
        assertEquals("hello\n", decoder.decode("hello\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void multiByteCharacterSplitAcrossChunksIsReassembled() {
        NodeJsRuntimePluginService.Utf8StreamDecoder decoder =
                new NodeJsRuntimePluginService.Utf8StreamDecoder();
        byte[] utf8 = "汉字".getBytes(StandardCharsets.UTF_8);
        byte[] first = new byte[4];
        byte[] second = new byte[utf8.length - 4];
        System.arraycopy(utf8, 0, first, 0, 4);
        System.arraycopy(utf8, 4, second, 0, second.length);

        String initial = decoder.decode(first);
        String remainder = decoder.decode(second);
        assertEquals("汉字", initial + remainder);
        assertEquals("汉", initial);
    }

    @Test
    public void emptyAndNullChunksYieldEmptyText() {
        NodeJsRuntimePluginService.Utf8StreamDecoder decoder =
                new NodeJsRuntimePluginService.Utf8StreamDecoder();
        assertEquals("", decoder.decode(null));
        assertEquals("", decoder.decode(new byte[0]));
    }

    @Test
    public void malformedBytesAreReplacedNotDropped() {
        NodeJsRuntimePluginService.Utf8StreamDecoder decoder =
                new NodeJsRuntimePluginService.Utf8StreamDecoder();
        // 0xFF can never begin a UTF-8 sequence; the following ASCII must
        // still decode.
        String decoded = decoder.decode(new byte[]{(byte) 0xFF, 'o', 'k'});
        assertEquals("�ok", decoded);
    }
}

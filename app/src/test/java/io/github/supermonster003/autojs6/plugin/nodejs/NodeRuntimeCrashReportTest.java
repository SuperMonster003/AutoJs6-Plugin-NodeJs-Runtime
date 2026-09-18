package io.github.supermonster003.autojs6.plugin.nodejs;

import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Roadmap M12.5: the readable subset of a Node diagnostic report and the wording it adds to a slot exit. */
public class NodeRuntimeCrashReportTest {

    /** The shape Node 24 writes with --report-compact for a heap out-of-memory abort (irrelevant sections cut). */
    private static final String OOM_REPORT = "{\"header\":{\"reportVersion\":5,\"event\":\"Allocation failed - JavaScript heap out of memory\","
            + "\"trigger\":\"OOMError\",\"filename\":\"m12-5-v8-oom.json\",\"dumpEventTime\":\"2026-09-18T19:30:29Z\",\"dumpEventTimeStamp\":\"1789731029000\","
            + "\"processId\":43576,\"threadId\":0,\"cwd\":\"/\",\"commandLine\":[\"node\"],\"nodejsVersion\":\"v24.21.0\",\"wordSize\":64,\"arch\":\"x64\",\"platform\":\"android\"},"
            + "\"javascriptStack\":{\"message\":\"No stack.\",\"stack\":[\"Unavailable.\"],\"errorProperties\":{}},"
            + "\"javascriptHeap\":{\"totalMemory\":118063104,\"executableMemory\":262144,\"totalCommittedMemory\":118063104,\"availableMemory\":200835872,"
            + "\"usedMemory\":64884736,\"memoryLimit\":67108864,\"mallocedMemory\":8192,\"externalMemory\":1234,\"peakMallocedMemory\":8192,\"heapSpaces\":{}},"
            + "\"nativeStack\":[],\"resourceUsage\":{\"rss\":105717760,\"maxRss\":126767104},\"libuv\":[],\"workers\":[],\"sharedObjects\":[]}";

    @Test
    public void theReadablePartOfAReportIsExtractedAndSummarised() {
        NodeRuntimeCrashReport.Report crash = NodeRuntimeCrashReport.fromJson(OOM_REPORT);
        assertNotNull(crash);
        assertEquals("Allocation failed - JavaScript heap out of memory", crash.event);
        assertEquals("OOMError", crash.trigger);
        assertEquals("2026-09-18T19:30:29Z", crash.dumpEventTime);
        assertEquals(43576L, crash.pid);
        assertEquals("v24.21.0", crash.nodejsVersion);
        assertEquals("No stack.", crash.message);
        assertEquals("Unavailable.", crash.stack);
        assertEquals(64884736L, crash.heapUsedBytes);
        assertEquals(118063104L, crash.heapTotalBytes);
        assertEquals(67108864L, crash.heapLimitBytes);
        assertEquals(105717760L, crash.rssBytes);
        assertEquals("Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; rss 100.8 MB",
                crash.summary());
        crash.file = "/data/user/0/io.github.supermonster003.autojs6.plugin.nodejs/cache/crash/m12-5-v8-oom.json";
        assertTrue(crash.summary().endsWith("; rss 100.8 MB; report " + crash.file));
        crash.maxOldGenerationSizeMb = 64;
        assertTrue(crash.summary(), crash.summary().contains(" used of 64.0 MB limit; old-generation cap 64 MB; rss 100.8 MB; report "));
    }

    @Test
    public void textThatIsNotAReportIsRejected() {
        assertNull(NodeRuntimeCrashReport.fromJson(null));
        assertNull(NodeRuntimeCrashReport.fromJson(""));
        assertNull(NodeRuntimeCrashReport.fromJson("not json"));
        assertNull(NodeRuntimeCrashReport.fromJson("{}"));
        assertNull(NodeRuntimeCrashReport.fromJson("{\"header\":null}"));
        NodeRuntimeCrashReport.Report bare = NodeRuntimeCrashReport.fromJson("{\"header\":{\"event\":\"x\"}}");
        assertNotNull(bare);
        assertEquals("Node.js fatal error: x", bare.summary());
        assertEquals("", bare.stack);
        assertEquals(0L, bare.pid);
    }

    @Test
    public void longStacksAreCapped() {
        JSONArray lines = new JSONArray();
        for (int index = 0; index < NodeRuntimeCrashReport.MAX_STACK_LINES + 3; index++) lines.put("at f" + index);
        String text = NodeRuntimeCrashReport.stackLines(lines);
        assertTrue(text.startsWith("at f0\nat f1\n"));
        assertTrue(text.endsWith("at f" + (NodeRuntimeCrashReport.MAX_STACK_LINES - 1) + "\n... 3 more"));
        assertEquals(NodeRuntimeCrashReport.MAX_STACK_LINES + 1, text.split("\n").length);
    }

    @Test
    public void reportFileNamesUseOnlyASafeSubsetOfTheExecutionId() {
        assertEquals("m12-5-v8-oom.json", NodeRuntimeCrashReport.fileName("m12-5-v8-oom"));
        assertEquals("_remote_Untitled_7.js.json", NodeRuntimeCrashReport.fileName("$remote/Untitled 7.js"));
        assertEquals("execution.json", NodeRuntimeCrashReport.fileName(""));
        assertEquals("execution.json", NodeRuntimeCrashReport.fileName(null));
        StringBuilder id = new StringBuilder();
        for (int index = 0; index < 300; index++) id.append('a');
        assertEquals(NodeRuntimeCrashReport.MAX_FILE_NAME_STEM + ".json".length(), NodeRuntimeCrashReport.fileName(id.toString()).length());
    }

    @Test
    public void theFatalErrorLineJoinsTheExitSummaryInsideItsParenthesis() {
        String crash = "Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit";
        assertEquals("CRASH_NATIVE (SIGABRT; see the logcat tombstone; " + crash + ")",
                NodeRuntimeSlotExit.withCrash("CRASH_NATIVE (SIGABRT; see the logcat tombstone)", crash));
        assertEquals("exit reason unavailable below Android 11 (" + crash + ")",
                NodeRuntimeSlotExit.withCrash("exit reason unavailable below Android 11", crash));
        assertEquals("LOW_MEMORY (killed by the system low-memory killer)",
                NodeRuntimeSlotExit.withCrash("LOW_MEMORY (killed by the system low-memory killer)", ""));
        assertEquals(crash, NodeRuntimeSlotExit.withCrash("", crash));
        assertEquals("", NodeRuntimeSlotExit.withCrash(null, null));
    }
}

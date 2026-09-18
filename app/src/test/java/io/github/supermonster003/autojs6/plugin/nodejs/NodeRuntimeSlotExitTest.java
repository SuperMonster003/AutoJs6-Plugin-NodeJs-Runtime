package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Roadmap M12.5a: the exit-record wording a host shows verbatim is fixed here, independent of Android. */
public class NodeRuntimeSlotExitTest {

    @Test
    public void reasonNamesMirrorApplicationExitInfo() {
        assertEquals("EXIT_SELF", NodeRuntimeSlotExit.reasonName(1));
        assertEquals("SIGNALED", NodeRuntimeSlotExit.reasonName(2));
        assertEquals("LOW_MEMORY", NodeRuntimeSlotExit.reasonName(3));
        assertEquals("CRASH", NodeRuntimeSlotExit.reasonName(4));
        assertEquals("CRASH_NATIVE", NodeRuntimeSlotExit.reasonName(5));
        assertEquals("ANR", NodeRuntimeSlotExit.reasonName(6));
        assertEquals("OTHER", NodeRuntimeSlotExit.reasonName(13));
        assertEquals("FREEZER", NodeRuntimeSlotExit.reasonName(14));
        assertEquals("REASON_42", NodeRuntimeSlotExit.reasonName(42));
        assertTrue(NodeRuntimeSlotExit.isSignal(2));
        assertTrue(NodeRuntimeSlotExit.isSignal(5));
        assertFalse(NodeRuntimeSlotExit.isSignal(3));
    }

    @Test
    public void aRecordIsTakenOnlyAfterItsFieldsStayedTheSameForTheSettleWindow() {
        // UNKNOWN -> SIGNALED, CRASH_NATIVE with signal 0 -> SIGABRT, SIGNALED/SIGKILL -> LOW_MEMORY all change the key.
        assertNotEquals(NodeRuntimeSlotExit.recordKey(0, 0, null), NodeRuntimeSlotExit.recordKey(2, 9, null));
        assertNotEquals(NodeRuntimeSlotExit.recordKey(5, 0, null), NodeRuntimeSlotExit.recordKey(5, 6, null));
        assertNotEquals(NodeRuntimeSlotExit.recordKey(2, 9, null), NodeRuntimeSlotExit.recordKey(3, 9, null));
        assertEquals(NodeRuntimeSlotExit.recordKey(5, 6, null), NodeRuntimeSlotExit.recordKey(5, 6, ""));
        assertFalse(NodeRuntimeSlotExit.settled(-1L, 10_000L));
        assertFalse(NodeRuntimeSlotExit.settled(1_000L, 1_000L + NodeRuntimeSlotExit.RECORD_SETTLE_MS - 1L));
        assertTrue(NodeRuntimeSlotExit.settled(1_000L, 1_000L + NodeRuntimeSlotExit.RECORD_SETTLE_MS));
    }

    @Test
    public void signalNamesCoverTheCrashSignalsAndFallBackToNumbers() {
        assertEquals("SIGABRT", NodeRuntimeSlotExit.signalName(6));
        assertEquals("SIGBUS", NodeRuntimeSlotExit.signalName(7));
        assertEquals("SIGKILL", NodeRuntimeSlotExit.signalName(9));
        assertEquals("SIGSEGV", NodeRuntimeSlotExit.signalName(11));
        assertEquals("SIGSYS", NodeRuntimeSlotExit.signalName(31));
        assertEquals("signal 40", NodeRuntimeSlotExit.signalName(40));
    }

    @Test
    public void summariesReadLikeTheDeviceRecords() {
        assertEquals("LOW_MEMORY (killed by the system low-memory killer; rss 5.2 GB)",
                NodeRuntimeSlotExit.summary(3, 9, 5_442_304L, null));
        assertEquals("LOW_MEMORY (killed by the system low-memory killer)",
                NodeRuntimeSlotExit.summary(3, 9, 0L, ""));
        assertEquals("CRASH_NATIVE (SIGABRT; see the logcat tombstone)",
                NodeRuntimeSlotExit.summary(5, 6, 120_000L, null));
        assertEquals("CRASH_NATIVE (SIGSEGV; see the logcat tombstone)",
                NodeRuntimeSlotExit.summary(5, 11, 0L, null));
        assertEquals("SIGNALED (SIGKILL)", NodeRuntimeSlotExit.summary(2, 9, 0L, null));
        assertEquals("EXIT_SELF (exit code 1)", NodeRuntimeSlotExit.summary(1, 1, 0L, null));
        assertEquals("CRASH (uncaught Java exception; see logcat)", NodeRuntimeSlotExit.summary(4, 0, 0L, null));
        assertEquals("ANR (not responding)", NodeRuntimeSlotExit.summary(6, 0, 0L, null));
        assertEquals("OTHER (empty for too long)", NodeRuntimeSlotExit.summary(13, 0, 0L, " empty for too long "));
        assertEquals("FREEZER", NodeRuntimeSlotExit.summary(14, 0, 0L, null));
    }

    @Test
    public void memoryIsFormattedInBinaryUnits() {
        assertEquals("512 KB", NodeRuntimeSlotExit.formatKb(512L));
        assertEquals("80.5 MB", NodeRuntimeSlotExit.formatKb(82_432L));
        assertEquals("2.6 GB", NodeRuntimeSlotExit.formatKb(2_730_136L));
        assertEquals("5.4 GB", NodeRuntimeSlotExit.formatKb(5_659_460L));
    }

    @Test
    public void failureMessageNamesTheSlotThePidAndTheSummary() {
        assertEquals("Node runtime slot 0 (pid 30583) exited during the execution: "
                        + "LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB).",
                NodeRuntimeSlotExit.failureMessage(0, 30583, NodeRuntimeSlotExit.summary(3, 9, 2_730_136L, null)));
        assertEquals("Node runtime slot 1 (pid 7) exited during the execution: exit reason unavailable below Android 11.",
                NodeRuntimeSlotExit.failureMessage(1, 7, NodeRuntimeSlotExit.unsupportedSummary()));
        assertEquals("Node runtime slot 1 (pid 7) exited during the execution: exit reason not recorded yet.",
                NodeRuntimeSlotExit.failureMessage(1, 7, NodeRuntimeSlotExit.notRecordedSummary()));
    }
}

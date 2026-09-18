package io.github.supermonster003.autojs6.plugin.nodejs;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

/**
 * Roadmap M12.5a: when a runtime slot process dies in the middle of an execution, the dispatcher reports the
 * exit record Android already keeps for it (ApplicationExitInfo, Android 11+) instead of a bare
 * DeadObjectException. The system writes the tombstone and decides the reason (low-memory kill, native
 * crash with its signal, plain kill, self exit); no in-process signal handler is installed.
 */
final class NodeRuntimeSlotExit {
    /** Failure result key: the exit record of the slot that died during this execution. */
    static final String KEY_SLOT_EXIT = "slotExit";
    /** getRuntimeInfo key: the most recent unexpected slot exit seen by this dispatcher. */
    static final String KEY_LAST_SLOT_EXIT = "lastSlotExit";
    static final String SOURCE_EXIT_INFO = "application_exit_info";
    static final String SOURCE_UNSUPPORTED = "unsupported_api";
    static final String SOURCE_NOT_RECORDED = "not_recorded";
    /** The system finalises the record shortly after the death; wait at most this long for it. */
    static final long RECORD_WAIT_MS = 2_000L;
    /** A plain SIGKILL record may still be refined to LOW_MEMORY once lmkd reports its kill. */
    static final long SIGKILL_REFINEMENT_WAIT_MS = 400L;
    private static final long POLL_INTERVAL_MS = 100L;
    private static final int SIGKILL = 9;

    // Mirrors android.app.ApplicationExitInfo.REASON_* so the pure helpers stay testable on the JVM.
    static final int REASON_UNKNOWN = 0;
    static final int REASON_EXIT_SELF = 1;
    static final int REASON_SIGNALED = 2;
    static final int REASON_LOW_MEMORY = 3;
    static final int REASON_CRASH = 4;
    static final int REASON_CRASH_NATIVE = 5;
    static final int REASON_ANR = 6;

    private NodeRuntimeSlotExit() { }

    /**
     * Describes why the slot process {@code pid} died. Records older than {@code notBeforeWallMs}
     * (epoch milliseconds) are ignored so a reused pid cannot report a previous process's exit.
     */
    static Bundle describe(Context context, int pid, int slotId, String executionId, long notBeforeWallMs, long waitMs) {
        long started = SystemClock.elapsedRealtime();
        Bundle exit = new Bundle();
        exit.putInt("pid", pid);
        exit.putInt("slotId", slotId);
        exit.putString("executionId", executionId == null ? "" : executionId);
        exit.putBoolean("available", false);
        exit.putInt("reason", -1);
        exit.putString("reasonName", "");
        exit.putInt("status", 0);
        exit.putString("signal", "");
        exit.putBoolean("nativeCrash", false);
        exit.putLong("rssBytes", 0L);
        exit.putLong("pssBytes", 0L);
        exit.putLong("timestamp", 0L);
        exit.putString("processName", "");
        exit.putString("description", "");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            exit.putString("source", SOURCE_UNSUPPORTED);
            exit.putString("summary", unsupportedSummary());
            exit.putLong("waitedMs", 0L);
            return exit;
        }
        ApplicationExitInfo record = awaitRecord(context, pid, notBeforeWallMs, waitMs);
        exit.putLong("waitedMs", SystemClock.elapsedRealtime() - started);
        if (record == null) {
            exit.putString("source", SOURCE_NOT_RECORDED);
            exit.putString("summary", notRecordedSummary());
            return exit;
        }
        fill(exit, record);
        return exit;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static void fill(Bundle exit, ApplicationExitInfo record) {
        int reason = record.getReason();
        int status = record.getStatus();
        String description = record.getDescription() == null ? "" : record.getDescription();
        exit.putBoolean("available", true);
        exit.putString("source", SOURCE_EXIT_INFO);
        exit.putInt("reason", reason);
        exit.putString("reasonName", reasonName(reason));
        exit.putInt("status", status);
        exit.putString("signal", isSignal(reason) ? signalName(status) : "");
        exit.putBoolean("nativeCrash", reason == ApplicationExitInfo.REASON_CRASH_NATIVE);
        exit.putLong("rssBytes", record.getRss() * 1024L);
        exit.putLong("pssBytes", record.getPss() * 1024L);
        exit.putLong("timestamp", record.getTimestamp());
        exit.putString("processName", record.getProcessName() == null ? "" : record.getProcessName());
        exit.putString("description", description);
        exit.putString("summary", summary(reason, status, record.getRss(), description));
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static ApplicationExitInfo awaitRecord(Context context, int pid, long notBeforeWallMs, long waitMs) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null || pid <= 0) return null;
        long deadline = SystemClock.elapsedRealtime() + Math.max(0L, waitMs);
        long firstSeenAt = -1L;
        ApplicationExitInfo record = null;
        while (true) {
            ApplicationExitInfo candidate = latestRecord(manager, context.getPackageName(), pid, notBeforeWallMs);
            long now = SystemClock.elapsedRealtime();
            if (candidate != null) {
                record = candidate;
                if (firstSeenAt < 0) firstSeenAt = now;
                boolean provisional = candidate.getReason() == ApplicationExitInfo.REASON_SIGNALED
                        && candidate.getStatus() == SIGKILL;
                if (!provisional || now - firstSeenAt >= SIGKILL_REFINEMENT_WAIT_MS) break;
            }
            if (now >= deadline) break;
            SystemClock.sleep(Math.min(POLL_INTERVAL_MS, deadline - now));
        }
        return record;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static ApplicationExitInfo latestRecord(ActivityManager manager, String packageName, int pid, long notBeforeWallMs) {
        List<ApplicationExitInfo> records;
        try {
            records = manager.getHistoricalProcessExitReasons(packageName, pid, 0);
        } catch (RuntimeException error) {
            return null;
        }
        ApplicationExitInfo latest = null;
        for (ApplicationExitInfo record : records) {
            if (record.getPid() != pid || record.getTimestamp() < notBeforeWallMs) continue;
            if (latest == null || record.getTimestamp() > latest.getTimestamp()) latest = record;
        }
        return latest;
    }

    static boolean isSignal(int reason) {
        return reason == REASON_SIGNALED || reason == REASON_CRASH_NATIVE;
    }

    static String reasonName(int reason) {
        switch (reason) {
            case REASON_UNKNOWN: return "UNKNOWN";
            case REASON_EXIT_SELF: return "EXIT_SELF";
            case REASON_SIGNALED: return "SIGNALED";
            case REASON_LOW_MEMORY: return "LOW_MEMORY";
            case REASON_CRASH: return "CRASH";
            case REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case REASON_ANR: return "ANR";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            default: return "REASON_" + reason;
        }
    }

    static String signalName(int signal) {
        switch (signal) {
            case 1: return "SIGHUP";
            case 2: return "SIGINT";
            case 3: return "SIGQUIT";
            case 4: return "SIGILL";
            case 5: return "SIGTRAP";
            case 6: return "SIGABRT";
            case 7: return "SIGBUS";
            case 8: return "SIGFPE";
            case SIGKILL: return "SIGKILL";
            case 11: return "SIGSEGV";
            case 13: return "SIGPIPE";
            case 15: return "SIGTERM";
            case 31: return "SIGSYS";
            default: return "signal " + signal;
        }
    }

    /** One line a host can show verbatim, for example {@code LOW_MEMORY (killed by the system low-memory killer; rss 5.2 GB)}. */
    static String summary(int reason, int status, long rssKb, String description) {
        String detail;
        switch (reason) {
            case REASON_LOW_MEMORY:
                detail = "killed by the system low-memory killer" + (rssKb > 0 ? "; rss " + formatKb(rssKb) : "");
                break;
            case REASON_CRASH_NATIVE:
                detail = signalName(status) + "; see the logcat tombstone";
                break;
            case REASON_SIGNALED:
                detail = signalName(status);
                break;
            case REASON_EXIT_SELF:
                detail = "exit code " + status;
                break;
            case REASON_CRASH:
                detail = "uncaught Java exception; see logcat";
                break;
            case REASON_ANR:
                detail = "not responding";
                break;
            default:
                detail = description == null ? "" : description.trim();
        }
        String name = reasonName(reason);
        return detail.isEmpty() ? name : name + " (" + detail + ")";
    }

    static String formatKb(long kb) {
        if (kb >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f GB", kb / (1024.0 * 1024.0));
        if (kb >= 1024L) return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
        return kb + " KB";
    }

    static String unsupportedSummary() {
        return "exit reason unavailable below Android 11";
    }

    static String notRecordedSummary() {
        return "exit reason not recorded yet";
    }

    static String failureMessage(int slotId, int pid, String summary) {
        return "Node runtime slot " + slotId + " (pid " + pid + ") exited during the execution: " + summary + ".";
    }
}

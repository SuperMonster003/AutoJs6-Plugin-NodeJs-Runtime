package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.longJsonValue;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.parseJsonObject;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.readTextIfFile;
import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.stringJsonValue;

/**
 * Roadmap M12.5: the crash marker of a slot that died of a Node.js fatal error (V8 heap out of memory, a failed
 * CHECK) is Node's own diagnostic report. The slot's bootstrap enables only the fatal-error trigger and points it
 * at {@code cacheDir/crash/<executionId>.json}; Node writes the file right before the abort. The dispatcher reads
 * it back, keeps the readable part in the failure result ({@code slotExit.crash}) and exposes the newest report on
 * disk as {@code lastCrash} in getRuntimeInfo. No signal handler is installed: an lmkd kill or a plain SIGKILL
 * leaves no report, and the exit record (M12.5a) still says why the slot died. The parser is plain Java so it
 * stays testable on the JVM; only {@link Report#toBundle()} touches Android.
 */
final class NodeRuntimeCrashReport {
    static final String DIRECTORY_NAME = "crash";
    /** slotExit sub-bundle: the readable part of the report of the slot that died during this execution. */
    static final String KEY_CRASH = "crash";
    /** getRuntimeInfo key: the newest report on disk, across dispatcher restarts. */
    static final String KEY_LAST_CRASH = "lastCrash";
    static final String FILE_SUFFIX = ".json";
    static final int KEEP_NEWEST = 5;
    static final int MAX_STACK_LINES = 16;
    static final int MAX_FILE_NAME_STEM = 96;
    private static final long MAX_REPORT_BYTES = 4L * 1024L * 1024L;

    private NodeRuntimeCrashReport() { }

    /** The readable subset of a Node diagnostic report. */
    static final class Report {
        String file = "";
        String event = "";
        String trigger = "";
        String dumpEventTime = "";
        long pid;
        long threadId;
        String nodejsVersion = "";
        String message = "";
        String stack = "";
        long heapUsedBytes;
        long heapTotalBytes;
        long heapLimitBytes;
        long rssBytes;
        /** The execution's old-generation cap when the request set one; heap_size_limit above also counts young space and code. */
        int maxOldGenerationSizeMb;

        /**
         * One line for the failure message, for example {@code Node.js fatal error: Allocation failed - JavaScript
         * heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; rss 100.8 MB; report /data/.../crash/x.json}.
         */
        String summary() {
            StringBuilder text = new StringBuilder("Node.js fatal error");
            if (!event.isEmpty()) text.append(": ").append(event);
            if (heapUsedBytes > 0 || heapLimitBytes > 0) {
                text.append("; JS heap ").append(formatBytes(heapUsedBytes));
                if (heapLimitBytes > 0) text.append(" used of ").append(formatBytes(heapLimitBytes)).append(" limit");
            }
            if (maxOldGenerationSizeMb > 0) text.append("; old-generation cap ").append(maxOldGenerationSizeMb).append(" MB");
            if (rssBytes > 0) text.append("; rss ").append(formatBytes(rssBytes));
            if (!file.isEmpty()) text.append("; report ").append(file);
            return text.toString();
        }

        Bundle toBundle() {
            Bundle crash = new Bundle();
            crash.putString("file", file);
            crash.putString("event", event);
            crash.putString("trigger", trigger);
            crash.putString("dumpEventTime", dumpEventTime);
            crash.putLong("pid", pid);
            crash.putLong("threadId", threadId);
            crash.putString("nodejsVersion", nodejsVersion);
            crash.putString("message", message);
            crash.putString("stack", stack);
            crash.putLong("heapUsedBytes", heapUsedBytes);
            crash.putLong("heapTotalBytes", heapTotalBytes);
            crash.putLong("heapLimitBytes", heapLimitBytes);
            crash.putLong("rssBytes", rssBytes);
            crash.putInt("maxOldGenerationSizeMb", maxOldGenerationSizeMb);
            crash.putString("summary", summary());
            return crash;
        }
    }

    static File directory(File cacheDir) {
        return new File(cacheDir, DIRECTORY_NAME);
    }

    /** One report per execution; the id is host-provided text, so only a safe subset of it names the file. */
    static String fileName(String executionId) {
        String id = executionId == null ? "" : executionId.trim();
        StringBuilder stem = new StringBuilder();
        for (int index = 0; index < id.length() && stem.length() < MAX_FILE_NAME_STEM; index++) {
            char c = id.charAt(index);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            stem.append(safe ? c : '_');
        }
        if (stem.length() == 0) stem.append("execution");
        return stem + FILE_SUFFIX;
    }

    /** The report {@code executionId} left at or after {@code notBeforeWallMs}, written by {@code pid} when known. */
    static Report forExecution(File directory, String executionId, int pid, long notBeforeWallMs) {
        File file = new File(directory, fileName(executionId));
        if (!file.isFile() || file.lastModified() < notBeforeWallMs) return null;
        Report report = read(file);
        if (report == null) return null;
        return pid > 0 && report.pid > 0 && report.pid != pid ? null : report;
    }

    static File newestFile(File directory) {
        File[] files = listReports(directory);
        return files.length == 0 ? null : files[0];
    }

    static void prune(File directory, int keep) {
        File[] files = listReports(directory);
        for (int index = keep; index < files.length; index++) {
            //noinspection ResultOfMethodCallIgnored
            files[index].delete();
        }
    }

    /** Reports newest first. */
    private static File[] listReports(File directory) {
        File[] files = directory == null ? null : directory.listFiles((dir, name) -> name.endsWith(FILE_SUFFIX));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    static Report read(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_REPORT_BYTES) return null;
        Report report = fromJson(readTextIfFile(file));
        if (report != null) report.file = file.getAbsolutePath();
        return report;
    }

    /** Parses a report; null when the text is not one. */
    static Report fromJson(String json) {
        JSONObject root = parseJsonObject(json);
        JSONObject header = root == null ? null : root.optJSONObject("header");
        if (header == null) return null;
        JSONObject stack = root.optJSONObject("javascriptStack");
        JSONObject heap = root.optJSONObject("javascriptHeap");
        JSONObject usage = root.optJSONObject("resourceUsage");
        Report report = new Report();
        report.event = stringJsonValue(header, "event", "");
        report.trigger = stringJsonValue(header, "trigger", "");
        report.dumpEventTime = stringJsonValue(header, "dumpEventTime", "");
        report.pid = longJsonValue(header, "processId", 0L);
        report.threadId = longJsonValue(header, "threadId", 0L);
        report.nodejsVersion = stringJsonValue(header, "nodejsVersion", "");
        report.message = stringJsonValue(stack, "message", "");
        report.stack = stackLines(stack == null ? null : stack.optJSONArray("stack"));
        report.heapUsedBytes = longJsonValue(heap, "usedMemory", 0L);
        report.heapTotalBytes = longJsonValue(heap, "totalMemory", 0L);
        report.heapLimitBytes = longJsonValue(heap, "memoryLimit", 0L);
        report.rssBytes = longJsonValue(usage, "rss", 0L);
        return report;
    }

    static String stackLines(JSONArray lines) {
        if (lines == null) return "";
        StringBuilder text = new StringBuilder();
        int count = Math.min(lines.length(), MAX_STACK_LINES);
        for (int index = 0; index < count; index++) {
            if (index > 0) text.append('\n');
            text.append(lines.optString(index, ""));
        }
        if (lines.length() > count) text.append("\n... ").append(lines.length() - count).append(" more");
        return text.toString();
    }

    static String formatBytes(long bytes) {
        return NodeRuntimeSlotExit.formatKb(bytes / 1024L);
    }
}

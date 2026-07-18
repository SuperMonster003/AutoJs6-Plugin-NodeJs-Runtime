package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Request-scoped mirror of a host-private Node sandbox. */
final class PluginWorkspaceArchiveSession implements AutoCloseable {

    static final int CONTRACT_VERSION = 2;
    static final String KEY_VERSION = "workspaceArchiveTransportVersion";
    static final String KEY_INPUT_FD = "workspaceArchiveInputFd";
    static final String KEY_OUTPUT_FD = "workspaceArchiveOutputFd";
    static final String KEY_RELATIVE_WORKING_DIRECTORY = "workspaceRelativeWorkingDirectory";
    static final String KEY_MAX_FILES = "workspaceArchiveMaxFiles";
    static final String KEY_MAX_BYTES = "workspaceArchiveMaxBytes";
    static final int HARD_MAX_FILES = 4_096;
    static final long HARD_MAX_BYTES = 64L * 1024L * 1024L;

    private static final int COPY_BUFFER_BYTES = 32 * 1024;
    private static final String WORKSPACE_PARENT = "node-plugin-workspaces";
    private static final String TRANSPORT_PROTOCOL_DIRECTORY = ".autojs6-workspace-transport";
    private static final String INPUT_MANIFEST_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/input-manifest-v1.json";
    private static final String OUTPUT_TOMBSTONE_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/deletions-v1.json";
    private static final int MANIFEST_VERSION = 1;
    private static final int MANIFEST_MAX_BYTES = 1024 * 1024;

    private final File sessionRoot;
    private final File runtimeSandboxRoot;
    private final File runtimeWorkingDirectory;
    private final String requestedSandboxRoot;
    private final String requestedWorkingDirectory;
    private final int maxFiles;
    private final long maxBytes;
    private final ParcelFileDescriptor outputDescriptor;
    private final AtomicBoolean committed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<String> deleteEligibleInputFiles = new LinkedHashSet<>();
    private int inputEntries;
    private long inputBytes;
    private int outputEntries;
    private long outputBytes;
    private int outputDeletedFiles;
    private volatile String status = "materialized";

    @SuppressWarnings("deprecation")
    static PluginWorkspaceArchiveSession open(File cacheDirectory, Bundle request) throws IOException {
        ParcelFileDescriptor input = null;
        ParcelFileDescriptor output = null;
        File parent = null;
        File sessionRoot = null;
        Throwable failure = null;
        boolean outputOwnershipTransferred = false;
        try {
            Throwable inputFailure = null;
            Throwable outputFailure = null;
            try {
                input = request.getParcelable(KEY_INPUT_FD);
            } catch (Throwable error) {
                inputFailure = error;
            }
            try {
                output = request.getParcelable(KEY_OUTPUT_FD);
            } catch (Throwable error) {
                outputFailure = error;
            }
            if (inputFailure != null || outputFailure != null) {
                IOException error = new IOException(
                        "Unable to read Node.js plugin workspace archive descriptors.",
                        inputFailure != null ? inputFailure : outputFailure
                );
                if (inputFailure != null && outputFailure != null && outputFailure != inputFailure) {
                    error.addSuppressed(outputFailure);
                }
                throw error;
            }

            int version = request.getInt(KEY_VERSION, 0);
            if (version != CONTRACT_VERSION) {
                throw new IOException("Unsupported Node.js plugin workspace archive contract: " + version);
            }
            if (input == null || output == null) {
                throw new IOException("Node.js plugin workspace archive descriptors are missing.");
            }
            if (input == output || input.getFd() == output.getFd()) {
                throw new IOException("Node.js plugin workspace archive descriptors must be distinct.");
            }
            int maxFiles = Math.min(
                    HARD_MAX_FILES,
                    Math.max(1, request.getInt(KEY_MAX_FILES, HARD_MAX_FILES))
            );
            long maxBytes = Math.min(
                    HARD_MAX_BYTES,
                    Math.max(1L, request.getLong(KEY_MAX_BYTES, HARD_MAX_BYTES))
            );
            String executionId = nonBlank(request.getString("executionId"), "request");
            parent = new File(cacheDirectory, WORKSPACE_PARENT);
            ensureDirectory(parent);
            sessionRoot = new File(parent, sha256(executionId).substring(0, 32));
            deleteRecursively(sessionRoot, parent);
            ensureDirectory(sessionRoot);
            File runtimeRoot = new File(sessionRoot, "sandbox");
            ensureDirectory(runtimeRoot);
            String relativeWorkingDirectory = validateRelativePath(
                    request.getString(KEY_RELATIVE_WORKING_DIRECTORY, ""),
                    true
            );
            File runtimeWorkingDirectory = relativeWorkingDirectory.isEmpty()
                    ? runtimeRoot
                    : new File(runtimeRoot, relativeWorkingDirectory);
            String requestedWorkingDirectory = normalizeAbsolutePath(
                    nonBlank(request.getString("workingDirectory"), request.getString("sandboxRoot"))
            );
            String requestedSandboxRoot = normalizeAbsolutePath(
                    nonBlank(request.getString("sandboxRoot"), requestedWorkingDirectory)
            );
            if (!containsPath(requestedSandboxRoot, requestedWorkingDirectory)) {
                throw new IOException("Requested Node.js plugin workingDirectory escapes sandboxRoot.");
            }
            PluginWorkspaceArchiveSession session = new PluginWorkspaceArchiveSession(
                    sessionRoot,
                    runtimeRoot,
                    runtimeWorkingDirectory,
                    requestedSandboxRoot,
                    requestedWorkingDirectory,
                    maxFiles,
                    maxBytes,
                    output
            );
            session.extractInput(input);
            ensureDirectory(runtimeWorkingDirectory);
            session.status = "ready";
            outputOwnershipTransferred = true;
            return session;
        } catch (Throwable error) {
            failure = error;
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to materialize Node.js plugin workspace.", error);
        } finally {
            closeQuietly(input);
            if (!outputOwnershipTransferred) {
                closeQuietly(output);
                if (sessionRoot != null && parent != null) {
                    try {
                        deleteRecursively(sessionRoot, parent);
                    } catch (Throwable cleanupError) {
                        if (failure != null) {
                            failure.addSuppressed(cleanupError);
                        }
                    }
                }
            }
        }
    }

    private PluginWorkspaceArchiveSession(
            File sessionRoot,
            File runtimeSandboxRoot,
            File runtimeWorkingDirectory,
            String requestedSandboxRoot,
            String requestedWorkingDirectory,
            int maxFiles,
            long maxBytes,
            ParcelFileDescriptor outputDescriptor
    ) {
        this.sessionRoot = sessionRoot;
        this.runtimeSandboxRoot = runtimeSandboxRoot;
        this.runtimeWorkingDirectory = runtimeWorkingDirectory;
        this.requestedSandboxRoot = requestedSandboxRoot;
        this.requestedWorkingDirectory = requestedWorkingDirectory;
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
        this.outputDescriptor = outputDescriptor;
    }

    String workingDirectory() {
        return runtimeWorkingDirectory.getAbsolutePath();
    }

    String sandboxRoot() {
        return runtimeSandboxRoot.getAbsolutePath();
    }

    String mapHostPathToRuntime(String value) {
        String normalized = tryNormalizeAbsolutePath(value);
        if (normalized == null || !containsPath(requestedSandboxRoot, normalized)) {
            return value;
        }
        String relative = relativePath(requestedSandboxRoot, normalized);
        return relative.isEmpty()
                ? runtimeSandboxRoot.getAbsolutePath()
                : new File(runtimeSandboxRoot, relative).getAbsolutePath();
    }

    String mapRuntimePathToHost(String value) {
        String normalized = tryNormalizeAbsolutePath(value);
        String runtimeRoot = runtimeSandboxRoot.getAbsolutePath();
        if (normalized == null || !containsPath(runtimeRoot, normalized)) {
            return value;
        }
        String relative = relativePath(runtimeRoot, normalized);
        return relative.isEmpty()
                ? requestedSandboxRoot
                : requestedSandboxRoot + "/" + relative;
    }

    Map<String, String> mapEnvironment(Map<String, String> source) {
        LinkedHashMap<String, String> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            mapped.put(entry.getKey(), mapHostPathToRuntime(entry.getValue()));
        }
        return mapped;
    }

    Map<String, String> mapModuleSourceNames(Map<String, String> source) {
        LinkedHashMap<String, String> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            mapped.put(mapHostPathToRuntime(entry.getKey()), entry.getValue());
        }
        return mapped;
    }

    String mapEngineInfo(String engineInfoJson) {
        if (engineInfoJson == null || engineInfoJson.trim().isEmpty()) {
            return engineInfoJson;
        }
        try {
            JSONObject json = new JSONObject(engineInfoJson);
            json.put("cwd", runtimeWorkingDirectory.getAbsolutePath());
            String sourceName = json.optString("sourceName", "");
            if (!sourceName.isEmpty()) {
                json.put("sourceName", mapHostPathToRuntime(sourceName));
            }
            return json.toString();
        } catch (Throwable ignored) {
            return engineInfoJson;
        }
    }

    synchronized void commit() throws IOException {
        if (!committed.compareAndSet(false, true)) {
            return;
        }
        status = "committing";
        try (OutputStream raw = new ParcelFileDescriptor.AutoCloseOutputStream(outputDescriptor);
             ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(raw))) {
            // Android may expose the same app-private directory through
            // /data/user/0 and canonicalize its children through /data/data.
            // Compare one canonical namespace on both sides of the boundary;
            // mixing the aliases incorrectly rejects every legitimate child
            // as escaping the private workspace.
            File canonicalRuntimeSandboxRoot = runtimeSandboxRoot.getCanonicalFile();
            writeDirectory(canonicalRuntimeSandboxRoot, canonicalRuntimeSandboxRoot, output);
            writeTombstoneManifest(output);
            status = "committed";
        } catch (Throwable error) {
            status = "commit_failed";
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to commit Node.js plugin workspace.", error);
        }
    }

    String[] nativePayload() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.runtime_plugin.workspace.contract_version", Integer.toString(CONTRACT_VERSION));
        values.put("embedded_script.runtime_plugin.workspace.mapped", "true");
        values.put("embedded_script.runtime_plugin.workspace.status", status);
        values.put("embedded_script.runtime_plugin.workspace.legacy_directory_scan_ran", "false");
        values.put("embedded_script.runtime_plugin.workspace.input_directory_scan_count", "0");
        values.put("embedded_script.runtime_plugin.workspace.explicit_input_entries", Integer.toString(inputEntries));
        values.put("embedded_script.runtime_plugin.workspace.output_private_workspace_scan_count", committed.get() ? "1" : "0");
        values.put("embedded_script.runtime_plugin.workspace.requested_working_directory", requestedWorkingDirectory);
        values.put("embedded_script.runtime_plugin.workspace.requested_sandbox_root", requestedSandboxRoot);
        values.put("embedded_script.runtime_plugin.workspace.runtime_working_directory", runtimeWorkingDirectory.getAbsolutePath());
        values.put("embedded_script.runtime_plugin.workspace.runtime_sandbox_root", runtimeSandboxRoot.getAbsolutePath());
        values.put("embedded_script.runtime_plugin.workspace.input_entries", Integer.toString(inputEntries));
        values.put("embedded_script.runtime_plugin.workspace.input_bytes", Long.toString(inputBytes));
        values.put("embedded_script.runtime_plugin.workspace.output_entries", Integer.toString(outputEntries));
        values.put("embedded_script.runtime_plugin.workspace.output_bytes", Long.toString(outputBytes));
        values.put("embedded_script.runtime_plugin.workspace.deleted_input_files", Integer.toString(outputDeletedFiles));
        String[] payload = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            payload[index++] = entry.getKey() + "=" + entry.getValue();
        }
        return payload;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(outputDescriptor);
        File parent = sessionRoot.getParentFile();
        if (parent != null) {
            try {
                deleteRecursively(sessionRoot, parent);
            } catch (Throwable ignored) {
                // A later request with the same execution id reclaims it safely.
            }
        }
    }

    private void extractInput(ParcelFileDescriptor inputDescriptor) throws IOException {
        boolean manifestSeen = false;
        Set<String> actualFileEntries = new LinkedHashSet<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        try (InputStream raw = new ParcelFileDescriptor.AutoCloseInputStream(inputDescriptor);
             ZipInputStream input = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (INPUT_MANIFEST_PATH.equals(entry.getName())) {
                    if (manifestSeen || entry.isDirectory()) {
                        throw new IOException("Workspace input manifest is duplicated or is a directory.");
                    }
                    deleteEligibleInputFiles.addAll(parseInputManifest(readManifestEntry(input)));
                    manifestSeen = true;
                    input.closeEntry();
                    continue;
                }
                inputEntries = checkedEntryCount(inputEntries);
                String relative = validateRelativePath(entry.getName(), false);
                if (isTransportProtocolPath(relative)) {
                    throw new IOException("Workspace input uses the reserved transport namespace.");
                }
                if (!seenPaths.add(relative)) {
                    throw new IOException("Workspace input contains a duplicate path.");
                }
                File target = new File(runtimeSandboxRoot, relative);
                ensureContained(runtimeSandboxRoot, target);
                if (entry.isDirectory()) {
                    ensureSafeDirectories(runtimeSandboxRoot, target);
                } else {
                    actualFileEntries.add(relative);
                    File parent = target.getParentFile();
                    if (parent == null) {
                        throw new IOException("Workspace input entry has no parent.");
                    }
                    ensureSafeDirectories(runtimeSandboxRoot, parent);
                    rejectSymbolicLink(target);
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        byte[] buffer = new byte[COPY_BUFFER_BYTES];
                        int count;
                        while ((count = input.read(buffer)) >= 0) {
                            if (count == 0) continue;
                            inputBytes += count;
                            if (inputBytes > maxBytes) {
                                throw new IOException("Workspace input exceeds the byte limit.");
                            }
                            output.write(buffer, 0, count);
                        }
                    }
                    if (entry.getTime() > 0L) {
                        target.setLastModified(entry.getTime());
                    }
                }
                input.closeEntry();
            }
        }
        if (!manifestSeen) {
            throw new IOException("Workspace input manifest is missing.");
        }
        if (!actualFileEntries.containsAll(deleteEligibleInputFiles)) {
            throw new IOException("Workspace input manifest declares a file absent from the archive.");
        }
    }

    private Set<String> parseInputManifest(String text) throws IOException {
        try {
            JSONObject json = new JSONObject(text);
            if (json.optInt("version", 0) != MANIFEST_VERSION) {
                throw new IOException("Unsupported workspace input manifest version.");
            }
            JSONArray files = json.optJSONArray("deleteEligibleFiles");
            if (files == null) {
                throw new IOException("Workspace input manifest has no deleteEligibleFiles array.");
            }
            Set<String> result = new LinkedHashSet<>();
            for (int index = 0; index < files.length(); index++) {
                Object raw = files.opt(index);
                if (!(raw instanceof String)) {
                    throw new IOException("Workspace input manifest path is not a string.");
                }
                String relative = validateRelativePath((String) raw, false);
                if (isTransportProtocolPath(relative) || !result.add(relative)) {
                    throw new IOException("Workspace input manifest contains an invalid path.");
                }
            }
            return result;
        } catch (IOException error) {
            throw error;
        } catch (Throwable error) {
            throw new IOException("Unable to parse workspace input manifest.", error);
        }
    }

    private static String readManifestEntry(ZipInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            total += count;
            if (total > MANIFEST_MAX_BYTES) {
                throw new IOException("Workspace manifest exceeds its byte limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void writeTombstoneManifest(ZipOutputStream output) throws IOException {
        try {
            JSONArray files = new JSONArray();
            for (String relative : deleteEligibleInputFiles) {
                File target = new File(runtimeSandboxRoot, relative);
                ensureContained(runtimeSandboxRoot, target);
                if (isMissingWithoutFollowingLinks(target)) {
                    files.put(relative);
                }
            }
            outputDeletedFiles = files.length();
            JSONObject manifest = new JSONObject()
                    .put("version", MANIFEST_VERSION)
                    .put("deletedFiles", files);
            byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
            if (manifestBytes.length > MANIFEST_MAX_BYTES) {
                throw new IOException("Workspace tombstone manifest exceeds its byte limit.");
            }
            output.putNextEntry(new ZipEntry(OUTPUT_TOMBSTONE_PATH));
            output.write(manifestBytes);
            output.closeEntry();
        } catch (IOException error) {
            throw error;
        } catch (Throwable error) {
            throw new IOException("Unable to write workspace tombstone manifest.", error);
        }
    }

    private static boolean isMissingWithoutFollowingLinks(File target) throws IOException {
        try {
            android.system.StructStat stat = Os.lstat(target.getPath());
            if (OsConstants.S_ISLNK(stat.st_mode)) {
                throw new IOException("Workspace deletion candidate is a symbolic link.");
            }
            return false;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) {
                return true;
            }
            throw new IOException("Unable to inspect workspace deletion candidate.", error);
        } catch (IOException error) {
            throw error;
        } catch (Throwable error) {
            throw new IOException("Unable to inspect workspace deletion candidate.", error);
        }
    }

    private void writeDirectory(File root, File directory, ZipOutputStream output) throws IOException {
        File[] listed = directory.listFiles();
        if (listed == null) {
            throw new IOException("Unable to enumerate plugin workspace output.");
        }
        List<File> children = new ArrayList<>();
        Collections.addAll(children, listed);
        children.sort(Comparator.comparing(File::getName));
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            ensureContained(root, canonical);
            android.system.StructStat stat;
            try {
                stat = Os.lstat(child.getPath());
            } catch (Throwable error) {
                throw new IOException("Unable to inspect plugin workspace output.", error);
            }
            if (OsConstants.S_ISLNK(stat.st_mode)) {
                throw new IOException("Plugin workspace output contains a symbolic link.");
            }
            String relative = relativePath(root.getAbsolutePath(), canonical.getAbsolutePath());
            if (isTransportProtocolPath(relative)) {
                throw new IOException("Plugin workspace output uses the reserved transport namespace.");
            }
            outputEntries = checkedEntryCount(outputEntries);
            if (OsConstants.S_ISDIR(stat.st_mode)) {
                output.putNextEntry(new ZipEntry(relative + "/"));
                output.closeEntry();
                writeDirectory(root, canonical, output);
            } else if (OsConstants.S_ISREG(stat.st_mode)) {
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(child.lastModified());
                output.putNextEntry(entry);
                try (FileInputStream input = new FileInputStream(child)) {
                    byte[] buffer = new byte[COPY_BUFFER_BYTES];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        outputBytes += count;
                        if (outputBytes > maxBytes) {
                            throw new IOException("Plugin workspace output exceeds the byte limit.");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                output.closeEntry();
            } else {
                throw new IOException("Plugin workspace output contains a special file.");
            }
        }
    }

    private int checkedEntryCount(int previous) throws IOException {
        if (previous >= maxFiles) {
            throw new IOException("Plugin workspace exceeds the entry-count limit.");
        }
        return previous + 1;
    }

    private static void ensureSafeDirectories(File root, File directory) throws IOException {
        ensureContained(root, directory);
        String relative = relativePath(root.getAbsolutePath(), directory.getAbsolutePath());
        File current = root;
        if (relative.isEmpty()) return;
        for (String segment : relative.split("/")) {
            current = new File(current, segment);
            rejectSymbolicLink(current);
            if (current.exists()) {
                if (!current.isDirectory()) {
                    throw new IOException("Workspace path collides with a non-directory.");
                }
            } else if (!current.mkdir()) {
                throw new IOException("Unable to create plugin workspace directory.");
            }
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create plugin workspace directory.");
        }
        rejectSymbolicLink(directory);
        if (!directory.isDirectory()) {
            throw new IOException("Plugin workspace path is not a directory.");
        }
    }

    private static void ensureContained(File root, File candidate) throws IOException {
        String rootPath = normalizeAbsolutePath(root.getAbsolutePath());
        String candidatePath = normalizeAbsolutePath(candidate.getAbsolutePath());
        if (!containsPath(rootPath, candidatePath)) {
            throw new IOException("Plugin workspace archive path escapes its root.");
        }
    }

    private static void rejectSymbolicLink(File file) throws IOException {
        try {
            if (OsConstants.S_ISLNK(Os.lstat(file.getPath()).st_mode)) {
                throw new IOException("Plugin workspace path targets a symbolic link.");
            }
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) {
                throw new IOException("Unable to inspect plugin workspace path.", error);
            }
        } catch (IOException error) {
            throw error;
        } catch (Throwable error) {
            throw new IOException("Unable to inspect plugin workspace path.", error);
        }
    }

    private static String validateRelativePath(String value, boolean allowEmpty) throws IOException {
        String text = value == null ? "" : value;
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty() && allowEmpty) return "";
        if (text.isEmpty() || text.startsWith("/") || text.indexOf('\\') >= 0 || text.indexOf('\0') >= 0) {
            throw new IOException("Workspace archive contains an invalid path.");
        }
        String[] segments = text.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Workspace archive contains path traversal.");
            }
        }
        return String.join("/", segments);
    }

    private static boolean isTransportProtocolPath(String relative) {
        return TRANSPORT_PROTOCOL_DIRECTORY.equals(relative) ||
                relative.startsWith(TRANSPORT_PROTOCOL_DIRECTORY + "/");
    }

    private static String normalizeAbsolutePath(String value) throws IOException {
        String normalized = tryNormalizeAbsolutePath(value);
        if (normalized == null) {
            throw new IOException("Workspace request path is not a normalized absolute Android path.");
        }
        return normalized;
    }

    private static String tryNormalizeAbsolutePath(String value) {
        if (value == null || !value.startsWith("/") || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) {
            return null;
        }
        ArrayDeque<String> segments = new ArrayDeque<>();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (segments.isEmpty()) return null;
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        StringBuilder result = new StringBuilder("/");
        boolean first = true;
        for (String segment : segments) {
            if (!first) result.append('/');
            first = false;
            result.append(segment);
        }
        return result.toString();
    }

    private static boolean containsPath(String root, String candidate) {
        return candidate.equals(root) || ("/".equals(root) ? candidate.startsWith("/") : candidate.startsWith(root + "/"));
    }

    private static String relativePath(String root, String candidate) {
        if (!containsPath(root, candidate)) {
            throw new IllegalArgumentException("Path escapes workspace root.");
        }
        if (root.equals(candidate)) return "";
        return candidate.substring("/".equals(root) ? 1 : root.length() + 1);
    }

    private static void deleteRecursively(File target, File expectedParent) throws IOException {
        if (!target.exists()) return;
        File canonicalParent = expectedParent.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        if (!canonicalParent.equals(canonicalTarget.getParentFile())) {
            throw new IOException("Refusing to delete an unexpected plugin workspace path.");
        }
        File[] children = canonicalTarget.listFiles();
        if (children != null) {
            for (File child : children) {
                if (isRealDirectory(child)) {
                    deleteTreeChild(child, canonicalTarget);
                } else if (!child.delete()) {
                    throw new IOException("Unable to delete stale plugin workspace entry.");
                }
            }
        }
        if (!canonicalTarget.delete()) {
            throw new IOException("Unable to delete stale plugin workspace.");
        }
    }

    private static void deleteTreeChild(File target, File expectedParent) throws IOException {
        File canonical = target.getCanonicalFile();
        if (!expectedParent.getCanonicalFile().equals(canonical.getParentFile())) {
            throw new IOException("Plugin workspace child escaped during cleanup.");
        }
        File[] children = canonical.listFiles();
        if (children != null) {
            for (File child : children) {
                if (isRealDirectory(child)) {
                    deleteTreeChild(child, canonical);
                } else if (!child.delete()) {
                    throw new IOException("Unable to delete plugin workspace entry.");
                }
            }
        }
        if (!canonical.delete()) {
            throw new IOException("Unable to delete plugin workspace directory.");
        }
    }

    private static boolean isRealDirectory(File file) throws IOException {
        try {
            int mode = Os.lstat(file.getPath()).st_mode;
            return OsConstants.S_ISDIR(mode) && !OsConstants.S_ISLNK(mode);
        } catch (Throwable error) {
            throw new IOException("Unable to inspect plugin workspace cleanup entry.", error);
        }
    }

    private static String sha256(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Throwable error) {
            throw new IOException("Unable to identify plugin workspace.", error);
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (Throwable ignored) {
            // Best effort during a process-death path.
        }
    }
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
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

    static final int CONTRACT_VERSION = NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION;
    static final String KEY_VERSION = NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION;
    static final String KEY_INPUT_FD = NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD;
    static final String KEY_OUTPUT_FD = NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD;
    static final String KEY_RELATIVE_WORKING_DIRECTORY =
            NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY;
    static final String KEY_MAX_FILES = NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_FILES;
    static final String KEY_MAX_BYTES = NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_BYTES;
    static final int HARD_MAX_FILES = 4_096;
    static final long HARD_MAX_BYTES = 64L * 1024L * 1024L;

    private static final int COPY_BUFFER_BYTES = 32 * 1024;
    private static final int PROVIDER_TARGET_PRIVATE_MODE = 0600;
    private static final ProviderMaterializationClock SYSTEM_PROVIDER_MATERIALIZATION_CLOCK =
            SystemClock::elapsedRealtime;
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
    private final Set<String> providerMaterializedFiles = new LinkedHashSet<>();
    private final Set<String> providerMaterializedDirectories = new LinkedHashSet<>();
    private int inputEntries;
    private long inputBytes;
    private int outputEntries;
    private long outputBytes;
    private int outputDeletedFiles;
    private int providerMaterializationCount;
    private long providerMaterializationBytes;
    private int providerMaterializationConflictCount;
    private int providerMaterializationFailureCount;
    private int providerProtectedOutputFileCount;
    private int providerProtectedOutputDirectoryCount;
    private int providerProtectedTombstoneCount;
    private volatile String status = "materialized";

    static boolean hasWorkspaceDescriptors(Bundle request) {
        if (request == null) {
            return false;
        }
        try {
            return request.containsKey(KEY_INPUT_FD) || request.containsKey(KEY_OUTPUT_FD);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Direct-run mode: no archive transport. The script executes in a fresh
     * plugin-private working directory; commit and output snapshotting are
     * no-ops. This is the path for minimal requests that only carry source.
     */
    static PluginWorkspaceArchiveSession openDirect(File cacheDirectory, Bundle request) throws IOException {
        File parent = new File(cacheDirectory, WORKSPACE_PARENT);
        ensureDirectory(parent);
        String executionId = nonBlank(request.getString("executionId"), "request");
        File sessionRoot = new File(parent, sha256(executionId).substring(0, 32));
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
        PluginWorkspaceArchiveSession session = new PluginWorkspaceArchiveSession(
                sessionRoot,
                runtimeRoot,
                runtimeWorkingDirectory,
                runtimeRoot.getAbsolutePath(),
                runtimeWorkingDirectory.getAbsolutePath(),
                HARD_MAX_FILES,
                HARD_MAX_BYTES,
                null
        );
        ensureDirectory(runtimeWorkingDirectory);
        session.status = "ready";
        return session;
    }

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
        return mapContainedPathAcrossAndroidCredentialAlias(
                value,
                requestedSandboxRoot,
                runtimeSandboxRoot.getAbsolutePath()
        );
    }

    String mapRuntimePathToHost(String value) {
        return mapContainedPathAcrossAndroidCredentialAlias(
                value,
                runtimeSandboxRoot.getAbsolutePath(),
                requestedSandboxRoot
        );
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

    /**
     * Reads one exact policy-metadata file from the private runtime mirror.
     * The caller supplies the request-scoped absolute deadline and byte bound;
     * no parent directory is enumerated and symbolic links are never followed.
     */
    synchronized byte[] readExactRuntimeMetadataNoFollow(
            String runtimePath,
            long maxBytes,
            long deadlineElapsedRealtimeMs
    ) throws IOException {
        if (committed.get() || closed.get()) {
            throw new IOException("Runtime metadata cannot be read after workspace finalization.");
        }
        String normalizedRuntimePath = normalizeAbsolutePath(runtimePath);
        String runtimeRootPath = normalizeAbsolutePath(runtimeSandboxRoot.getAbsolutePath());
        if (!containsPath(runtimeRootPath, normalizedRuntimePath) ||
                normalizedRuntimePath.equals(runtimeRootPath)) {
            throw new IOException("Runtime metadata path escapes the private workspace.");
        }
        File target = new File(normalizedRuntimePath);
        String name = target.getName();
        if (!("project.json".equals(name) || "package.json".equals(name))) {
            throw new IOException("Runtime policy metadata must be an exact project.json or package.json path.");
        }
        File parent = target.getParentFile();
        if (parent == null) {
            throw new IOException("Runtime metadata path has no parent directory.");
        }
        validateProviderMaterializationParent(parent);
        requireProviderMaterializationDeadline(
                deadlineElapsedRealtimeMs,
                "before exact runtime metadata inspection",
                SYSTEM_PROVIDER_MATERIALIZATION_CLOCK
        );
        StructStat expected = lstatOrNull(target);
        if (expected == null) {
            return null;
        }
        if (OsConstants.S_ISLNK(expected.st_mode) || !OsConstants.S_ISREG(expected.st_mode)) {
            throw new IOException("Runtime policy metadata is linked or not a regular file.");
        }
        if (expected.st_size < 0L || expected.st_size > maxBytes) {
            throw new IOException("Runtime policy metadata exceeds its exact-read byte budget.");
        }
        java.io.FileDescriptor descriptor;
        try {
            descriptor = Os.open(
                    target.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    0
            );
        } catch (ErrnoException error) {
            throw new IOException("Could not open runtime policy metadata without following links.", error);
        }
        try (FileInputStream input = new FileInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(expected.st_size, 8192L))) {
            StructStat opened;
            try {
                opened = Os.fstat(input.getFD());
            } catch (ErrnoException error) {
                throw new IOException("Could not inspect opened runtime policy metadata.", error);
            }
            if (!sameFileIdentity(expected, opened) || !OsConstants.S_ISREG(opened.st_mode)) {
                throw new IOException("Runtime policy metadata identity changed before read.");
            }
            String openedPath = openedDescriptorPath(input.getFD());
            if (openedPath == null || !sameAndroidCredentialAliasedPath(
                    target.getCanonicalPath(),
                    new File(openedPath).getCanonicalPath()
            )) {
                throw new IOException("Opened runtime policy metadata path changed before read.");
            }
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long copied = 0L;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                requireProviderMaterializationDeadline(
                        deadlineElapsedRealtimeMs,
                        "during exact runtime metadata read",
                        SYSTEM_PROVIDER_MATERIALIZATION_CLOCK
                );
                if (count == 0) continue;
                copied += count;
                if (copied > maxBytes || copied > expected.st_size) {
                    throw new IOException("Runtime policy metadata changed or exceeded its byte budget during read.");
                }
                output.write(buffer, 0, count);
            }
            StructStat completed;
            try {
                completed = Os.fstat(input.getFD());
            } catch (ErrnoException error) {
                throw new IOException("Could not revalidate runtime policy metadata.", error);
            }
            StructStat pathCompleted = lstatOrNull(target);
            if (copied != expected.st_size || !sameFileIdentity(expected, completed) ||
                    !sameFileIdentity(expected, pathCompleted) ||
                    !openedPath.equals(openedDescriptorPath(input.getFD()))) {
                throw new IOException("Runtime policy metadata identity or byte count changed during read.");
            }
            return output.toByteArray();
        }
    }

    /**
     * Publishes one provider-authorized raw source at its exact runtime path.
     * The destination itself is exclusively created at mode 000, populated and
     * identity-verified while inaccessible by pathname, then promoted to mode
     * 0600. O_EXCL is the only race-authoritative missing-candidate decision.
     * The published path remains protected from workspace output and tombstone
     * synchronization for the lifetime of this request.
     */
    synchronized ProviderMaterialization materializeProviderSourceNoReplace(
            String runtimePath,
            File privateSource,
            long expectedBytes,
            long deadlineElapsedRealtimeMs
    ) throws IOException {
        return materializeProviderSourceNoReplace(
                runtimePath,
                privateSource,
                expectedBytes,
                deadlineElapsedRealtimeMs,
                SYSTEM_PROVIDER_MATERIALIZATION_CLOCK
        );
    }

    synchronized ProviderMaterialization materializeProviderSourceNoReplace(
            String runtimePath,
            File privateSource,
            long expectedBytes,
            long deadlineElapsedRealtimeMs,
            ProviderMaterializationClock clock
    ) throws IOException {
        if (committed.get() || closed.get()) {
            providerMaterializationFailureCount++;
            throw new IOException("Provider source cannot be materialized after workspace finalization.");
        }
        if (expectedBytes < 0L || expectedBytes > HARD_MAX_BYTES) {
            providerMaterializationFailureCount++;
            throw new IOException("Provider materialization byte count is invalid.");
        }

        String runtimeRootPath = normalizeAbsolutePath(runtimeSandboxRoot.getAbsolutePath());
        String normalizedRuntimePath = normalizeAbsolutePath(runtimePath);
        if (!containsPath(runtimeRootPath, normalizedRuntimePath) ||
                normalizedRuntimePath.equals(runtimeRootPath)) {
            providerMaterializationFailureCount++;
            throw new IOException("Provider materialization path escapes the runtime workspace.");
        }
        String relative = validateRelativePath(relativePath(runtimeRootPath, normalizedRuntimePath), false);
        if (isTransportProtocolPath(relative) || !isSupportedProviderMaterializationPath(relative)) {
            providerMaterializationFailureCount++;
            throw new IOException("Provider materialization path has an unsupported extension or namespace.");
        }

        File target = new File(runtimeSandboxRoot, relative);
        ensureContained(runtimeSandboxRoot, target);
        File parent = target.getParentFile();
        if (parent == null) {
            providerMaterializationFailureCount++;
            throw new IOException("Provider materialization path has no parent directory.");
        }

        List<String> createdDirectories = new ArrayList<>();
        StructStat ownedTargetIdentity = null;
        boolean targetCreated = false;
        try {
            requireProviderMaterializationDeadline(
                    deadlineElapsedRealtimeMs,
                    "before workspace publication",
                    clock
            );
            ensureProviderMaterializationDirectories(parent, createdDirectories);
            validateProviderMaterializationParent(parent);
            StructStat sourceBefore = requireRegularFile(privateSource, expectedBytes, "provider private source");
            boolean existedBeforeExclusiveOpen =
                    providerTargetExistsNoFollowForDiagnostics(target);
            int outputFlags = OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL |
                    OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW;
            java.io.FileDescriptor outputDescriptor;
            try {
                outputDescriptor = Os.open(target.getAbsolutePath(), outputFlags, 0000);
            } catch (ErrnoException error) {
                if (error.errno == OsConstants.EEXIST) {
                    throw new ProviderMaterializationConflictException(
                            existedBeforeExclusiveOpen
                                    ? "Provider materialization destination already existed at exclusive open."
                                    : "Provider materialization destination appeared before exclusive open.",
                            error
                    );
                }
                throw new IOException("Could not exclusively create provider materialization target.", error);
            }
            targetCreated = true;
            try (FileOutputStream output = new FileOutputStream(outputDescriptor)) {
                try {
                    ownedTargetIdentity = Os.fstat(output.getFD());
                } catch (ErrnoException error) {
                    throw new IOException("Could not capture provider materialization target identity.", error);
                }
                if (!OsConstants.S_ISREG(ownedTargetIdentity.st_mode) ||
                        ownedTargetIdentity.st_size != 0L ||
                        permissionBits(ownedTargetIdentity) != 0) {
                    throw new IOException("Provider materialization target was not created as an empty mode-000 regular file.");
                }
                copyProviderMaterializationSource(privateSource, sourceBefore, expectedBytes, output);
                output.getFD().sync();
                validateOwnedProviderMaterializationTarget(
                        target,
                        parent,
                        normalizedRuntimePath,
                        output.getFD(),
                        ownedTargetIdentity,
                        expectedBytes,
                        0000
                );
                requireProviderMaterializationDeadline(
                        deadlineElapsedRealtimeMs,
                        "after private copy and fsync",
                        clock
                );
                try {
                    Os.fchmod(output.getFD(), PROVIDER_TARGET_PRIVATE_MODE);
                    output.getFD().sync();
                } catch (ErrnoException error) {
                    throw new IOException("Could not promote provider materialization permissions.", error);
                }
                validateOwnedProviderMaterializationTarget(
                        target,
                        parent,
                        normalizedRuntimePath,
                        output.getFD(),
                        ownedTargetIdentity,
                        expectedBytes,
                        PROVIDER_TARGET_PRIVATE_MODE
                );
                requireProviderMaterializationDeadline(
                        deadlineElapsedRealtimeMs,
                        "after permission promotion",
                        clock
                );
            }

            validateClosedProviderMaterializationTarget(
                    target,
                    parent,
                    normalizedRuntimePath,
                    ownedTargetIdentity,
                    expectedBytes,
                    PROVIDER_TARGET_PRIVATE_MODE
            );
            requireProviderMaterializationDeadline(
                    deadlineElapsedRealtimeMs,
                    "before materialization receipt",
                    clock
            );
            providerMaterializedFiles.add(relative);
            providerMaterializedDirectories.addAll(createdDirectories);
            providerMaterializationCount++;
            providerMaterializationBytes += expectedBytes;
            return new ProviderMaterialization(target.getAbsolutePath(), expectedBytes);
        } catch (ProviderMaterializationConflictException error) {
            providerMaterializationConflictCount++;
            providerMaterializationFailureCount++;
            throw error;
        } catch (IOException error) {
            providerMaterializationFailureCount++;
            throw error;
        } catch (Throwable error) {
            providerMaterializationFailureCount++;
            throw new IOException("Unable to materialize provider source.", error);
        } finally {
            if (targetCreated && !providerMaterializedFiles.contains(relative)) {
                deleteProviderTargetIfOwnedRegularInode(target, ownedTargetIdentity);
            }
            if (!providerMaterializedFiles.contains(relative)) {
                pruneCreatedProviderDirectories(createdDirectories);
            }
        }
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
        if (outputDescriptor == null) {
            // Direct-run session: nothing to snapshot back to a host.
            status = "committed";
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
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_materialized_count",
                Integer.toString(providerMaterializationCount)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_materialized_bytes",
                Long.toString(providerMaterializationBytes)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_materialization_conflict_count",
                Integer.toString(providerMaterializationConflictCount)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_materialization_failure_count",
                Integer.toString(providerMaterializationFailureCount)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_protected_output_file_count",
                Integer.toString(providerProtectedOutputFileCount)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_protected_output_directory_count",
                Integer.toString(providerProtectedOutputDirectoryCount)
        );
        values.put(
                "embedded_script.runtime_plugin.workspace.provider_protected_tombstone_count",
                Integer.toString(providerProtectedTombstoneCount)
        );
        String[] payload = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            payload[index++] = entry.getKey() + "=" + entry.getValue();
        }
        return payload;
    }

    @Override
    public synchronized void close() {
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
                if (providerMaterializedFiles.contains(relative)) {
                    providerProtectedTombstoneCount++;
                    continue;
                }
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
            if (OsConstants.S_ISREG(stat.st_mode) && providerMaterializedFiles.contains(relative)) {
                providerProtectedOutputFileCount++;
                continue;
            }
            if (OsConstants.S_ISDIR(stat.st_mode) &&
                    providerMaterializedDirectories.contains(relative) &&
                    !hasExportableWorkspaceEntry(root, canonical)) {
                providerProtectedOutputDirectoryCount++;
                continue;
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

    private boolean hasExportableWorkspaceEntry(File root, File directory) throws IOException {
        File[] listed = directory.listFiles();
        if (listed == null) {
            throw new IOException("Unable to enumerate protected plugin workspace output.");
        }
        for (File child : listed) {
            File canonical = child.getCanonicalFile();
            ensureContained(root, canonical);
            StructStat stat;
            try {
                stat = Os.lstat(child.getAbsolutePath());
            } catch (Throwable error) {
                throw new IOException("Unable to inspect protected plugin workspace output.", error);
            }
            if (OsConstants.S_ISLNK(stat.st_mode)) {
                throw new IOException("Plugin workspace output contains a symbolic link.");
            }
            String relative = relativePath(root.getAbsolutePath(), canonical.getAbsolutePath());
            if (isTransportProtocolPath(relative)) {
                throw new IOException("Plugin workspace output uses the reserved transport namespace.");
            }
            if (OsConstants.S_ISREG(stat.st_mode)) {
                if (!providerMaterializedFiles.contains(relative)) {
                    return true;
                }
            } else if (OsConstants.S_ISDIR(stat.st_mode)) {
                if (!providerMaterializedDirectories.contains(relative) ||
                        hasExportableWorkspaceEntry(root, canonical)) {
                    return true;
                }
            } else {
                // Let the normal output writer reject the special file.
                return true;
            }
        }
        return false;
    }

    private int checkedEntryCount(int previous) throws IOException {
        if (previous >= maxFiles) {
            throw new IOException("Plugin workspace exceeds the entry-count limit.");
        }
        return previous + 1;
    }

    static boolean isSupportedProviderMaterializationPath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        int dot = normalized.lastIndexOf('.');
        if (dot <= slash || dot >= normalized.length() - 1) {
            return false;
        }
        String extension = normalized.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return ".js".equals(extension) || ".cjs".equals(extension) ||
                ".json".equals(extension) || ".ts".equals(extension) ||
                ".cts".equals(extension) || ".mjs".equals(extension) ||
                ".mts".equals(extension);
    }

    static void validateProviderMaterializationDeadline(
            long nowElapsedRealtimeMs,
            long deadlineElapsedRealtimeMs,
            String stage
    ) throws ProviderMaterializationDeadlineExceededException {
        if (deadlineElapsedRealtimeMs <= 0L || nowElapsedRealtimeMs >= deadlineElapsedRealtimeMs) {
            throw new ProviderMaterializationDeadlineExceededException(
                    "Provider materialization deadline expired " + nonBlank(stage, "during publication") + "."
            );
        }
    }

    private static void requireProviderMaterializationDeadline(
            long deadlineElapsedRealtimeMs,
            String stage,
            ProviderMaterializationClock clock
    ) throws ProviderMaterializationDeadlineExceededException {
        if (clock == null) {
            throw new ProviderMaterializationDeadlineExceededException(
                    "Provider materialization monotonic clock is unavailable."
            );
        }
        validateProviderMaterializationDeadline(
                clock.elapsedRealtime(),
                deadlineElapsedRealtimeMs,
                stage
        );
    }

    private void ensureProviderMaterializationDirectories(
            File directory,
            List<String> createdDirectories
    ) throws IOException {
        ensureContained(runtimeSandboxRoot, directory);
        String rootPath = normalizeAbsolutePath(runtimeSandboxRoot.getAbsolutePath());
        String directoryPath = normalizeAbsolutePath(directory.getAbsolutePath());
        String relative = relativePath(rootPath, directoryPath);
        File current = runtimeSandboxRoot;
        if (relative.isEmpty()) {
            validateProviderDirectory(current);
            return;
        }
        StringBuilder currentRelative = new StringBuilder();
        for (String segment : relative.split("/")) {
            current = new File(current, segment);
            if (currentRelative.length() > 0) currentRelative.append('/');
            currentRelative.append(segment);
            StructStat stat = lstatOrNull(current);
            if (stat == null) {
                try {
                    Os.mkdir(current.getAbsolutePath(), 0700);
                    createdDirectories.add(currentRelative.toString());
                } catch (ErrnoException error) {
                    if (error.errno != OsConstants.EEXIST) {
                        throw new IOException("Unable to create provider materialization directory.", error);
                    }
                }
            }
            validateProviderDirectory(current);
        }
    }

    private void validateProviderMaterializationParent(File parent) throws IOException {
        ensureContained(runtimeSandboxRoot, parent);
        String rootPath = normalizeAbsolutePath(runtimeSandboxRoot.getAbsolutePath());
        String parentPath = normalizeAbsolutePath(parent.getAbsolutePath());
        String relative = relativePath(rootPath, parentPath);
        File current = runtimeSandboxRoot;
        validateProviderDirectory(current);
        if (!relative.isEmpty()) {
            for (String segment : relative.split("/")) {
                current = new File(current, segment);
                validateProviderDirectory(current);
            }
        }
        String canonicalRoot = androidCredentialComparableOrSelf(runtimeSandboxRoot.getCanonicalPath());
        String canonicalParent = androidCredentialComparableOrSelf(parent.getCanonicalPath());
        if (!containsPath(canonicalRoot, canonicalParent)) {
            throw new IOException("Provider materialization parent escapes the runtime workspace.");
        }
    }

    private static void validateProviderDirectory(File directory) throws IOException {
        StructStat stat = lstatOrNull(directory);
        if (stat == null || OsConstants.S_ISLNK(stat.st_mode) || !OsConstants.S_ISDIR(stat.st_mode)) {
            throw new IOException("Provider materialization parent is missing, linked, or not a directory.");
        }
    }

    private static StructStat requireRegularFile(
            File file,
            long expectedBytes,
            String label
    ) throws IOException {
        StructStat stat = lstatOrNull(file);
        if (stat == null || OsConstants.S_ISLNK(stat.st_mode) ||
                !OsConstants.S_ISREG(stat.st_mode) || stat.st_size != expectedBytes) {
            throw new IOException("Invalid " + label + " identity or byte count.");
        }
        return stat;
    }

    private static boolean providerTargetExistsNoFollowForDiagnostics(File target) {
        try {
            Os.lstat(target.getAbsolutePath());
            return true;
        } catch (ErrnoException error) {
            return error.errno != OsConstants.ENOENT;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void validateOwnedProviderMaterializationTarget(
            File target,
            File parent,
            String expectedRuntimePath,
            java.io.FileDescriptor descriptor,
            StructStat ownedIdentity,
            long expectedBytes,
            int expectedMode
    ) throws IOException {
        StructStat descriptorIdentity;
        StructStat pathIdentity;
        try {
            descriptorIdentity = Os.fstat(descriptor);
            pathIdentity = Os.lstat(target.getAbsolutePath());
        } catch (ErrnoException error) {
            throw new IOException("Could not verify provider materialization target identity.", error);
        }
        if (!sameOwnedRegularInode(ownedIdentity, descriptorIdentity) ||
                !sameOwnedRegularInode(ownedIdentity, pathIdentity) ||
                descriptorIdentity.st_size != expectedBytes || pathIdentity.st_size != expectedBytes ||
                permissionBits(descriptorIdentity) != expectedMode ||
                permissionBits(pathIdentity) != expectedMode) {
            throw new IOException("Provider materialization target identity, size, or mode changed.");
        }
        String openedPath = openedDescriptorPath(descriptor);
        if (openedPath == null || !sameAndroidCredentialAliasedPath(
                target.getCanonicalPath(),
                new File(openedPath).getCanonicalPath()
        )) {
            throw new IOException("Opened provider materialization target path changed.");
        }
        validateProviderMaterializationTargetPath(target, parent, expectedRuntimePath);
    }

    private void validateClosedProviderMaterializationTarget(
            File target,
            File parent,
            String expectedRuntimePath,
            StructStat ownedIdentity,
            long expectedBytes,
            int expectedMode
    ) throws IOException {
        StructStat pathIdentity = lstatOrNull(target);
        if (!sameOwnedRegularInode(ownedIdentity, pathIdentity) ||
                pathIdentity.st_size != expectedBytes || permissionBits(pathIdentity) != expectedMode) {
            throw new IOException("Closed provider materialization target identity, size, or mode changed.");
        }
        validateProviderMaterializationTargetPath(target, parent, expectedRuntimePath);
    }

    private void validateProviderMaterializationTargetPath(
            File target,
            File parent,
            String expectedRuntimePath
    ) throws IOException {
        validateProviderMaterializationParent(parent);
        String canonicalTarget = target.getCanonicalFile().getAbsolutePath();
        if (!sameAndroidCredentialAliasedPath(expectedRuntimePath, canonicalTarget) ||
                !containsPath(
                        androidCredentialComparableOrSelf(runtimeSandboxRoot.getCanonicalPath()),
                        androidCredentialComparableOrSelf(canonicalTarget)
                )) {
            throw new IOException("Provider materialization target path changed or escaped its workspace.");
        }
    }

    private static void copyProviderMaterializationSource(
            File privateSource,
            StructStat expectedIdentity,
            long expectedBytes,
            FileOutputStream output
    ) throws IOException {
        java.io.FileDescriptor descriptor;
        try {
            descriptor = Os.open(
                    privateSource.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    0
            );
        } catch (ErrnoException error) {
            throw new IOException("Could not open provider private source without following links.", error);
        }
        try (FileInputStream input = new FileInputStream(descriptor)) {
            StructStat opened;
            try {
                opened = Os.fstat(input.getFD());
            } catch (ErrnoException error) {
                throw new IOException("Could not inspect opened provider private source.", error);
            }
            if (!sameFileIdentity(expectedIdentity, opened) || !OsConstants.S_ISREG(opened.st_mode)) {
                throw new IOException("Provider private source identity changed before read.");
            }
            String openedPath = openedDescriptorPath(input.getFD());
            if (openedPath == null || !sameAndroidCredentialAliasedPath(
                    privateSource.getCanonicalPath(),
                    new File(openedPath).getCanonicalPath()
            )) {
                throw new IOException("Opened provider private source path changed before read.");
            }
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long copied = 0L;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                copied += count;
                if (copied > expectedBytes) {
                    throw new IOException("Provider private source exceeds its declared byte count.");
                }
                output.write(buffer, 0, count);
            }
            if (copied != expectedBytes) {
                throw new IOException("Provider private source byte count changed during read.");
            }
            StructStat completed;
            try {
                completed = Os.fstat(input.getFD());
            } catch (ErrnoException error) {
                throw new IOException("Could not revalidate provider private source.", error);
            }
            if (!sameFileIdentity(expectedIdentity, completed) ||
                    !openedPath.equals(openedDescriptorPath(input.getFD()))) {
                throw new IOException("Provider private source identity changed during read.");
            }
        }
    }

    private static String openedDescriptorPath(java.io.FileDescriptor descriptor) {
        try (ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(descriptor)) {
            String value = Os.readlink("/proc/self/fd/" + duplicate.getFd());
            return value.endsWith(" (deleted)") ? null : new File(value).getAbsolutePath();
        } catch (Throwable error) {
            return null;
        }
    }

    private static StructStat lstatOrNull(File file) throws IOException {
        try {
            return Os.lstat(file.getAbsolutePath());
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) {
                return null;
            }
            throw new IOException("Unable to inspect provider materialization path.", error);
        }
    }

    private static boolean sameFileIdentity(StructStat expected, StructStat actual) {
        return sameOwnedRegularInode(expected, actual) &&
                expected.st_size == actual.st_size;
    }

    private static boolean sameOwnedRegularInode(StructStat expected, StructStat actual) {
        return expected != null && actual != null &&
                OsConstants.S_ISREG(expected.st_mode) && OsConstants.S_ISREG(actual.st_mode) &&
                expected.st_dev == actual.st_dev && expected.st_ino == actual.st_ino;
    }

    private static int permissionBits(StructStat value) {
        return value == null ? -1 : value.st_mode & 0777;
    }

    private static boolean sameAndroidCredentialAliasedPath(String expected, String actual) {
        String normalizedExpected = tryNormalizeAbsolutePath(expected);
        String normalizedActual = tryNormalizeAbsolutePath(actual);
        if (normalizedExpected == null || normalizedActual == null) return false;
        if (normalizedExpected.equals(normalizedActual)) return true;
        String comparableExpected = androidCredentialDataComparablePath(normalizedExpected);
        String comparableActual = androidCredentialDataComparablePath(normalizedActual);
        return comparableExpected != null && comparableExpected.equals(comparableActual);
    }

    private static String androidCredentialComparableOrSelf(String value) throws IOException {
        String normalized = normalizeAbsolutePath(value);
        String comparable = androidCredentialDataComparablePath(normalized);
        return comparable == null ? normalized : comparable;
    }

    private static void deleteProviderTargetIfOwnedRegularInode(File target, StructStat identity) {
        if (target == null || identity == null) return;
        try {
            StructStat current = Os.lstat(target.getAbsolutePath());
            if (sameOwnedRegularInode(identity, current)) {
                Os.remove(target.getAbsolutePath());
            }
        } catch (Throwable ignored) {
            // Request-scoped close removes the entire workspace as a fallback.
        }
    }

    private void pruneCreatedProviderDirectories(List<String> createdDirectories) {
        for (int index = createdDirectories.size() - 1; index >= 0; index--) {
            try {
                File directory = new File(runtimeSandboxRoot, createdDirectories.get(index));
                ensureContained(runtimeSandboxRoot, directory);
                StructStat stat = lstatOrNull(directory);
                File[] children = directory.listFiles();
                if (stat != null && OsConstants.S_ISDIR(stat.st_mode) &&
                        !OsConstants.S_ISLNK(stat.st_mode) && children != null && children.length == 0) {
                    Os.remove(directory.getAbsolutePath());
                }
            } catch (Throwable ignored) {
                // Request-scoped close removes the entire workspace as a fallback.
            }
        }
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

    /**
     * Maps a child between workspace roots after placing Android's two
     * credential-encrypted app-data aliases in one comparison namespace.
     * The alias conversion is deliberately comparison-only: the original
     * containment and relative-path checks still decide whether the candidate
     * is authorized, and the returned path always uses the destination root.
     */
    static String mapContainedPathAcrossAndroidCredentialAlias(
            String value,
            String sourceRoot,
            String destinationRoot
    ) {
        String normalizedCandidate = tryNormalizeAbsolutePath(value);
        String normalizedSourceRoot = tryNormalizeAbsolutePath(sourceRoot);
        String normalizedDestinationRoot = tryNormalizeAbsolutePath(destinationRoot);
        if (normalizedCandidate == null || normalizedSourceRoot == null ||
                normalizedDestinationRoot == null) {
            return value;
        }

        String comparableSourceRoot = androidCredentialDataComparablePath(normalizedSourceRoot);
        String comparableCandidate = androidCredentialDataComparablePath(normalizedCandidate);
        String containmentRoot = comparableSourceRoot != null && comparableCandidate != null
                ? comparableSourceRoot
                : normalizedSourceRoot;
        String containmentCandidate = comparableSourceRoot != null && comparableCandidate != null
                ? comparableCandidate
                : normalizedCandidate;
        if (!containsPath(containmentRoot, containmentCandidate)) {
            return value;
        }
        String relative = relativePath(containmentRoot, containmentCandidate);
        return relative.isEmpty()
                ? normalizedDestinationRoot
                : normalizedDestinationRoot + "/" + relative;
    }

    private static String androidCredentialDataComparablePath(String value) {
        final String userZeroPrefix = "/data/user/0/";
        final String legacyDataPrefix = "/data/data/";
        String relative;
        if (value.startsWith(userZeroPrefix)) {
            relative = value.substring(userZeroPrefix.length());
        } else if (value.startsWith(legacyDataPrefix)) {
            relative = value.substring(legacyDataPrefix.length());
        } else {
            return null;
        }
        int packageEnd = relative.indexOf('/');
        String packageName = packageEnd < 0 ? relative : relative.substring(0, packageEnd);
        if (!packageName.matches("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")) {
            return null;
        }
        return userZeroPrefix + relative;
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

    static final class ProviderMaterialization {
        private final String runtimePath;
        private final long sourceBytes;

        private ProviderMaterialization(String runtimePath, long sourceBytes) {
            this.runtimePath = runtimePath;
            this.sourceBytes = sourceBytes;
        }

        String runtimePath() {
            return runtimePath;
        }

        long sourceBytes() {
            return sourceBytes;
        }
    }

    static final class ProviderMaterializationDeadlineExceededException extends IOException {
        ProviderMaterializationDeadlineExceededException(String message) {
            super(message);
        }
    }

    interface ProviderMaterializationClock {
        long elapsedRealtime();
    }

    private static final class ProviderMaterializationConflictException extends IOException {
        ProviderMaterializationConflictException(String message) {
            super(message);
        }

        ProviderMaterializationConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

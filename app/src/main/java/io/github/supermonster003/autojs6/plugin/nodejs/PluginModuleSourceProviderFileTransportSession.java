package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Request-scoped file transport between the embedded Node isolate and the host
 * module-source Binder. Binder carries only control data and a PFD; decrypted
 * source is copied into a bounded, private temporary file for the native side.
 */
final class PluginModuleSourceProviderFileTransportSession {

    static final String RUNTIME_MODULE_NAME = "autojs6:module-source-provider";
    static final String KEY_PROVIDER_BINDER = "moduleSourceProvider";

    static final String KEY_VERSION = "moduleSourceProviderVersion";
    static final String KEY_REQUEST_ID = "moduleSourceProviderRequestId";
    static final String KEY_PATH = "moduleSourceProviderPath";
    static final String KEY_STATUS = "moduleSourceProviderStatus";
    static final String KEY_RESOLVED_PATH = "moduleSourceProviderResolvedPath";
    static final String KEY_SOURCE_FD = "moduleSourceProviderSourceFd";
    static final String KEY_SOURCE_BYTES = "moduleSourceProviderSourceBytes";
    static final String KEY_ELAPSED_MS = "moduleSourceProviderElapsedMs";

    static final int CONTRACT_VERSION = 1;
    static final long SINGLE_SOURCE_BYTES_LIMIT = 16L * 1024L * 1024L;
    static final long TOTAL_SOURCE_BYTES_LIMIT = 64L * 1024L * 1024L;
    static final int REQUEST_COUNT_LIMIT = 1024;

    private static final long DEFAULT_TIMEOUT_MS = 5000L;
    private static final long HARD_TIMEOUT_MS = 5000L;
    private static final long POLL_INTERVAL_MS = 5L;
    private static final long STOP_JOIN_MS = 1000L;
    private static final int REQUEST_JSON_BYTES_LIMIT = 64 * 1024;
    private static final int COPY_BUFFER_BYTES = 16 * 1024;
    private static final String STATUS_DECRYPTED = "decrypted";
    private static final String STATUS_NOT_ENCRYPTED = "not_encrypted";
    private static final String STATUS_NOT_FOUND = "not_found";
    private static final String STATUS_DENIED = "denied";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_TIMED_OUT = "timed_out";
    private static final String STATUS_FAILED = "failed";

    private static final String ERROR_INVALID_REQUEST = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_INVALID_REQUEST";
    private static final String ERROR_BUDGET_EXCEEDED = "ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED";
    private static final String ERROR_CANCELLED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_CANCELLED";
    private static final String ERROR_TIMED_OUT = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT";
    private static final String ERROR_FAILED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED";

    private final File root;
    private final File requestDir;
    private final File responseDir;
    private final String executionId;
    private final INodeJsModuleSourceProvider provider;
    private final PluginWorkspaceArchiveSession workspaceSession;
    private final long timeoutMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger responseCount = new AtomicInteger(0);
    private final AtomicInteger transportFailureCount = new AtomicInteger(0);
    private final AtomicInteger resolvedCount = new AtomicInteger(0);
    private final AtomicInteger decryptedCount = new AtomicInteger(0);
    private final AtomicInteger notEncryptedCount = new AtomicInteger(0);
    private final AtomicInteger notFoundCount = new AtomicInteger(0);
    private final AtomicInteger deniedCount = new AtomicInteger(0);
    private final AtomicInteger cancelledCount = new AtomicInteger(0);
    private final AtomicInteger timedOutCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicLong sourceBytes = new AtomicLong(0L);
    private final AtomicLong elapsedMs = new AtomicLong(0L);
    private final AtomicInteger mappedRequestPathCount = new AtomicInteger(0);
    private final AtomicInteger mappedResponsePathCount = new AtomicInteger(0);
    private final AtomicReference<String> lastStatus = new AtomicReference<>("");
    private final AtomicReference<String> lastErrorCode = new AtomicReference<>("");
    private final AtomicReference<ParcelFileDescriptor> activeSourceDescriptor = new AtomicReference<>(null);
    private final Thread thread;

    PluginModuleSourceProviderFileTransportSession(
            File cacheDir,
            String executionId,
            INodeJsModuleSourceProvider provider,
            long requestedTimeoutMs,
            PluginWorkspaceArchiveSession workspaceSession
    ) {
        this.executionId = nonBlank(executionId, "execution-" + System.nanoTime());
        this.provider = provider;
        this.workspaceSession = workspaceSession;
        this.timeoutMs = boundedTimeoutMs(requestedTimeoutMs);
        this.root = new File(
                new File(cacheDir, "nodejs-module-source-provider"),
                safeFileName(this.executionId) + "-" + UUID.randomUUID()
        );
        this.requestDir = new File(root, "requests");
        this.responseDir = new File(root, "responses");
        deleteRecursively(root);
        if (!requestDir.mkdirs() || !responseDir.mkdirs()) {
            deleteRecursively(root);
            throw new IllegalStateException("Could not create the module-source provider transport directories.");
        }
        this.thread = new Thread(this::loop);
        this.thread.setName("AutoJs6PluginModuleSourceProvider-" + safeFileName(this.executionId));
        this.thread.setDaemon(true);
    }

    String configJson() {
        try {
            return new JSONObject()
                    .put("enabled", true)
                    .put("version", CONTRACT_VERSION)
                    .put("executionId", executionId)
                    .put("requestDir", requestDir.getAbsolutePath())
                    .put("responseDir", responseDir.getAbsolutePath())
                    .put("timeoutMs", timeoutMs)
                    .put("pollIntervalMs", POLL_INTERVAL_MS)
                    .toString();
        } catch (Throwable error) {
            throw new IllegalStateException("Could not encode module-source provider transport configuration.", error);
        }
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            thread.start();
        }
    }

    void stop(String reason) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        cancelled.set(true);
        cancelProviderAsync(nonBlank(reason, "Node.js runtime plugin execution finished."));
        running.set(false);
        closeActiveSourceDescriptor();
        thread.interrupt();
        try {
            thread.join(STOP_JOIN_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (!thread.isAlive()) {
            deleteRecursively(root);
        }
    }

    String[] nativePayload() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.runtime_plugin.module_provider.available", "true");
        values.put("embedded_script.runtime_plugin.module_provider.transport", "local_file_pfd_v1");
        values.put("embedded_script.runtime_plugin.module_provider.request_count", Integer.toString(requestCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.resolved_count", Integer.toString(resolvedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.decrypted_count", Integer.toString(decryptedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.not_encrypted_count", Integer.toString(notEncryptedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.not_found_count", Integer.toString(notFoundCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.denied_count", Integer.toString(deniedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.cancelled_count", Integer.toString(cancelledCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.timed_out_count", Integer.toString(timedOutCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.failed_count", Integer.toString(failedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.source_bytes", Long.toString(sourceBytes.get()));
        values.put("embedded_script.runtime_plugin.module_provider.elapsed_ms", Long.toString(elapsedMs.get()));
        values.put("embedded_script.runtime_plugin.module_provider.last_status", lastStatus.get());
        values.put("embedded_script.runtime_plugin.module_provider.last_error_code", lastErrorCode.get());
        values.put("embedded_script.runtime_plugin.module_provider.mapped_request_path_count", Integer.toString(mappedRequestPathCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.mapped_response_path_count", Integer.toString(mappedResponsePathCount.get()));
        values.put("embedded_script.module_provider.transport", "file_pfd_v1");
        values.put("embedded_script.module_provider.transport_request_count", Integer.toString(requestCount.get()));
        values.put("embedded_script.module_provider.transport_response_count", Integer.toString(responseCount.get()));
        values.put(
                "embedded_script.module_provider.transport_failure_count",
                Integer.toString(transportFailureCount.get())
        );
        values.put("embedded_script.module_provider.transport_source_bytes", Long.toString(sourceBytes.get()));
        values.put("embedded_script.module_provider.transport_elapsed_ms", Long.toString(elapsedMs.get()));
        return nativePayloadFromMap(values);
    }

    String[] providerNativePayload() {
        FutureTask<Bundle> task = new FutureTask<>(provider::getNativeDiagnostics);
        Thread diagnosticsThread = new Thread(task);
        diagnosticsThread.setName("AutoJs6ModuleSourceDiagnostics");
        diagnosticsThread.setDaemon(true);
        diagnosticsThread.start();
        try {
            Bundle diagnostics = task.get(Math.min(timeoutMs, 1000L), TimeUnit.MILLISECONDS);
            if (diagnostics == null) {
                return new String[0];
            }
            String[] payload = diagnostics.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
            return payload == null ? new String[0] : payload;
        } catch (Throwable error) {
            task.cancel(true);
            return new String[]{
                    "embedded_script.runtime_plugin.module_provider.diagnostics_status=failed"
            };
        }
    }

    private void loop() {
        try {
            while (running.get()) {
                drainRequestFiles();
                sleep(POLL_INTERVAL_MS);
            }
        } finally {
            closeActiveSourceDescriptor();
            if (stopped.get()) {
                deleteRecursively(root);
            }
        }
    }

    private void drainRequestFiles() {
        File[] files = requestDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (!running.get()) {
                break;
            }
            if (seen.add(file.getName())) {
                dispatch(file);
            }
        }
    }

    private void dispatch(File requestFile) {
        long startedAt = SystemClock.elapsedRealtime();
        JSONObject requestJson = null;
        String fallbackId = fileId(requestFile);
        boolean transportFailureRecorded = false;
        int count = requestCount.incrementAndGet();
        try {
            requestJson = new JSONObject(readTextBounded(requestFile));
            String requestedId = nonBlank(requestJson.optString("id"), "");
            boolean validId = !requestedId.isEmpty() &&
                    requestedId.equals(fallbackId) &&
                    requestedId.equals(safeFileName(requestedId));
            String id = validId ? requestedId : fallbackId;
            String requestedExecutionId = nonBlank(requestJson.optString("executionId"), "");
            String path = nonBlank(requestJson.optString("path"), "");
            if (count > REQUEST_COUNT_LIMIT) {
                transportFailureCount.incrementAndGet();
                transportFailureRecorded = true;
                writeResponse(requestFile, failureJson(
                        id,
                        STATUS_FAILED,
                        path,
                        "",
                        elapsedSince(startedAt),
                        ERROR_BUDGET_EXCEEDED,
                        "Module-source provider request count exceeds " + REQUEST_COUNT_LIMIT + "."
                ), null);
                return;
            }
            if (!validId || requestJson.optInt("version", 0) != CONTRACT_VERSION || path.isEmpty()) {
                transportFailureCount.incrementAndGet();
                transportFailureRecorded = true;
                writeResponse(requestFile, failureJson(
                        id,
                        STATUS_FAILED,
                        path,
                        "",
                        elapsedSince(startedAt),
                        ERROR_INVALID_REQUEST,
                        "Invalid module-source provider request identity or version."
                ), null);
                return;
            }
            if (!executionId.equals(requestedExecutionId)) {
                transportFailureCount.incrementAndGet();
                transportFailureRecorded = true;
                writeResponse(requestFile, failureJson(
                        id,
                        STATUS_DENIED,
                        path,
                        "",
                        elapsedSince(startedAt),
                        "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED",
                        "Module-source provider request was denied."
                ), null);
                return;
            }
            long perRequestTimeoutMs = Math.min(
                    timeoutMs,
                    boundedTimeoutMs(requestJson.optLong("timeoutMs", timeoutMs))
            );
            long requestDeadline = deadlineAfter(startedAt, perRequestTimeoutMs);
            long providerWaitMs = remainingMs(requestDeadline);
            if (providerWaitMs <= 0L) {
                throw new ProviderTimeoutException("Module-source provider request deadline expired.");
            }
            Bundle providerResponse = callProvider(id, path, providerWaitMs);
            handleProviderResponse(
                    requestFile,
                    id,
                    path,
                    startedAt,
                    requestDeadline,
                    providerResponse
            );
        } catch (ProviderTimeoutException error) {
            if (!transportFailureRecorded) {
                transportFailureCount.incrementAndGet();
            }
            String id = requestJson == null ? fallbackId : nonBlank(requestJson.optString("id"), fallbackId);
            String path = requestJson == null ? "" : nonBlank(requestJson.optString("path"), "");
            cancelProviderAsync("Module-source provider request timed out: " + id);
            writeResponseQuietly(requestFile, failureJson(
                    id,
                    STATUS_TIMED_OUT,
                    path,
                    "",
                    elapsedSince(startedAt),
                    ERROR_TIMED_OUT,
                    publicFailureMessage(error)
            ));
        } catch (BudgetExceededException error) {
            if (!transportFailureRecorded) {
                transportFailureCount.incrementAndGet();
            }
            String id = requestJson == null ? fallbackId : nonBlank(requestJson.optString("id"), fallbackId);
            String path = requestJson == null ? "" : nonBlank(requestJson.optString("path"), "");
            writeResponseQuietly(requestFile, failureJson(
                    id,
                    STATUS_FAILED,
                    path,
                    "",
                    elapsedSince(startedAt),
                    ERROR_BUDGET_EXCEEDED,
                    publicFailureMessage(error)
            ));
        } catch (Throwable error) {
            if (!transportFailureRecorded) {
                transportFailureCount.incrementAndGet();
            }
            String id = requestJson == null ? fallbackId : nonBlank(requestJson.optString("id"), fallbackId);
            String path = requestJson == null ? "" : nonBlank(requestJson.optString("path"), "");
            writeResponseQuietly(requestFile, failureJson(
                    id,
                    cancelled.get() ? STATUS_CANCELLED : STATUS_FAILED,
                    path,
                    "",
                    elapsedSince(startedAt),
                    cancelled.get() ? ERROR_CANCELLED : ERROR_FAILED,
                    cancelled.get()
                            ? "Module-source provider transport was cancelled."
                            : publicFailureMessage(error)
            ));
        }
    }

    private Bundle callProvider(String id, String path, long callTimeoutMs) throws Exception {
        String providerPath = workspaceSession == null
                ? path
                : workspaceSession.mapRuntimePathToHost(path);
        if (!providerPath.equals(path)) {
            mappedRequestPathCount.incrementAndGet();
        }
        Bundle providerRequest = new Bundle();
        providerRequest.putInt(KEY_VERSION, CONTRACT_VERSION);
        providerRequest.putString(KEY_REQUEST_ID, id);
        providerRequest.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
        providerRequest.putString(KEY_PATH, providerPath);
        providerRequest.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, callTimeoutMs);
        AtomicBoolean abandoned = new AtomicBoolean(false);
        AtomicReference<Bundle> pendingResponse = new AtomicReference<>(null);
        FutureTask<Bundle> task = new FutureTask<>(() -> {
            Bundle result = provider.resolveModuleSource(providerRequest);
            pendingResponse.set(result);
            if (abandoned.get()) {
                closeSourceFd(pendingResponse.getAndSet(null));
            }
            return result;
        });
        Thread callThread = new Thread(task);
        callThread.setName("AutoJs6ModuleSourceBinder-" + safeFileName(id));
        callThread.setDaemon(true);
        callThread.start();
        try {
            Bundle result = task.get(callTimeoutMs, TimeUnit.MILLISECONDS);
            pendingResponse.compareAndSet(result, null);
            return result;
        } catch (TimeoutException error) {
            abandoned.set(true);
            closeSourceFd(pendingResponse.getAndSet(null));
            task.cancel(true);
            throw new ProviderTimeoutException(
                    "Module-source provider request timed out after " + callTimeoutMs + " ms."
            );
        } catch (InterruptedException error) {
            abandoned.set(true);
            closeSourceFd(pendingResponse.getAndSet(null));
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IOException(messageOf(cause), cause);
        }
    }

    private void handleProviderResponse(
            File requestFile,
            String id,
            String requestedPath,
            long startedAt,
            long requestDeadline,
            Bundle response
    ) throws Exception {
        if (response == null) {
            throw new IOException("Module-source provider returned a null response.");
        }
        int version = response.getInt(KEY_VERSION, 0);
        String responseId = nonBlank(response.getString(KEY_REQUEST_ID), "");
        String status = nonBlank(response.getString(KEY_STATUS), "");
        String resolvedPath = nonBlank(response.getString(KEY_RESOLVED_PATH), requestedPath);
        if (workspaceSession != null) {
            String mappedResolvedPath = workspaceSession.mapHostPathToRuntime(resolvedPath);
            if (!mappedResolvedPath.equals(resolvedPath)) {
                mappedResponsePathCount.incrementAndGet();
            }
            resolvedPath = mappedResolvedPath;
        }
        String errorCode = nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_CODE), "");
        String errorMessage = nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), "");
        long providerElapsedMs = Math.max(0L, response.getLong(KEY_ELAPSED_MS, 0L));
        if (version != CONTRACT_VERSION || !id.equals(responseId) || !isStatus(status)) {
            closeSourceFd(response);
            throw new IOException("Module-source provider returned an invalid response identity, version, or status.");
        }
        File sourceFile = null;
        if (STATUS_DECRYPTED.equals(status)) {
            if (!response.containsKey(KEY_SOURCE_FD) || !response.containsKey(KEY_SOURCE_BYTES)) {
                closeSourceFd(response);
                throw new IOException("Decrypted module-source response is missing its PFD or byte count.");
            }
            long declaredBytes = response.getLong(KEY_SOURCE_BYTES, -1L);
            if (declaredBytes < 0L) {
                closeSourceFd(response);
                throw new IOException("Decrypted module-source byte count is invalid.");
            }
            if (declaredBytes > SINGLE_SOURCE_BYTES_LIMIT) {
                closeSourceFd(response);
                throw new BudgetExceededException("Decrypted module source exceeds the single-source byte budget.");
            }
            long aggregateBefore = sourceBytes.get();
            if (aggregateBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes) {
                closeSourceFd(response);
                throw new BudgetExceededException("Decrypted module sources exceed the aggregate byte budget.");
            }
            sourceFile = new File(responseDir, safeFileName(id) + ".source");
            long copiedBytes = copySource(
                    response,
                    sourceFile,
                    declaredBytes,
                    requestDeadline
            );
            if (copiedBytes != declaredBytes) {
                sourceFile.delete();
                throw new IOException(
                        "Decrypted module source byte count mismatch: expected " + declaredBytes +
                                ", copied " + copiedBytes + "."
                );
            }
            sourceBytes.addAndGet(copiedBytes);
        } else {
            closeSourceFd(response);
        }
        long totalElapsedMs = Math.max(elapsedSince(startedAt), providerElapsedMs);
        JSONObject responseJson = new JSONObject()
                .put("version", CONTRACT_VERSION)
                .put("id", id)
                .put("status", status)
                .put("resolvedPath", resolvedPath)
                .put("sourcePath", sourceFile == null ? "" : sourceFile.getAbsolutePath())
                .put("sourceBytes", sourceFile == null ? 0L : sourceFile.length())
                .put("elapsedMs", totalElapsedMs)
                .put("errorCode", errorCode)
                .put("errorMessage", errorMessage);
        writeResponse(requestFile, responseJson, sourceFile);
    }

    private long copySource(
            Bundle response,
            File destination,
            long declaredBytes,
            long deadline
    ) throws IOException {
        @SuppressWarnings("deprecation")
        ParcelFileDescriptor descriptor = response.getParcelable(KEY_SOURCE_FD);
        if (descriptor == null) {
            throw new IOException("Decrypted module-source response PFD is null.");
        }
        if (stopped.get()) {
            descriptor.close();
            throw new IOException("Module-source provider transport is stopped.");
        }
        if (!activeSourceDescriptor.compareAndSet(null, descriptor)) {
            descriptor.close();
            throw new IOException("Another decrypted module-source PFD is already active.");
        }
        if (stopped.get()) {
            closeActiveSourceDescriptor();
            throw new IOException("Module-source provider transport is stopped.");
        }
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        temporary.delete();
        long copied = 0L;
        try {
            long statSize;
            try {
                statSize = descriptor.getStatSize();
            } catch (Throwable error) {
                throw new IOException("Could not stat decrypted module-source PFD.", error);
            }
            if (statSize > SINGLE_SOURCE_BYTES_LIMIT) {
                throw new BudgetExceededException("Decrypted module-source PFD exceeds the single-source byte budget.");
            }
            if (statSize >= 0L && statSize != declaredBytes) {
                throw new IOException("Decrypted module-source PFD size does not match the declared byte count.");
            }
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                int count;
                while (true) {
                    awaitReadable(descriptor, deadline);
                    count = input.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    if (count == 0) {
                        continue;
                    }
                    copied += count;
                    if (copied > SINGLE_SOURCE_BYTES_LIMIT) {
                        throw new BudgetExceededException("Decrypted module source exceeds the single-source byte budget.");
                    }
                    if (copied > declaredBytes) {
                        throw new IOException("Decrypted module source exceeds its declared or permitted byte count.");
                    }
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (stopped.get()) {
                throw new IOException("Module-source provider transport is stopped.");
            }
            destination.delete();
            if (!temporary.renameTo(destination)) {
                throw new IOException("Could not publish decrypted module-source file.");
            }
            return copied;
        } catch (Throwable error) {
            temporary.delete();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(messageOf(error), error);
        } finally {
            activeSourceDescriptor.compareAndSet(descriptor, null);
            try {
                descriptor.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void awaitReadable(ParcelFileDescriptor descriptor, long deadline) throws IOException {
        long remainingMs = deadline - SystemClock.elapsedRealtime();
        if (remainingMs <= 0L) {
            throw new ProviderTimeoutException("Timed out while reading decrypted module source from its PFD.");
        }
        StructPollfd pollFd = new StructPollfd();
        pollFd.fd = descriptor.getFileDescriptor();
        pollFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP | OsConstants.POLLERR);
        try {
            int ready = Os.poll(
                    new StructPollfd[]{pollFd},
                    (int) Math.min(remainingMs, Integer.MAX_VALUE)
            );
            if (ready <= 0) {
                throw new ProviderTimeoutException("Timed out while reading decrypted module source from its PFD.");
            }
        } catch (ErrnoException error) {
            throw new IOException("Could not poll decrypted module-source PFD.", error);
        }
    }

    private void writeResponse(File requestFile, JSONObject response, File sourceFile) throws IOException {
        String id = nonBlank(response.optString("id"), fileId(requestFile));
        File destination = new File(responseDir, safeFileName(id) + ".json");
        File temporary = new File(responseDir, safeFileName(id) + ".json.tmp");
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            destination.delete();
            if (!temporary.renameTo(destination)) {
                throw new IOException("Could not publish module-source provider response.");
            }
        } catch (IOException error) {
            temporary.delete();
            if (sourceFile != null) {
                sourceFile.delete();
            }
            throw error;
        }
        requestFile.delete();
        responseCount.incrementAndGet();
        record(response);
    }

    private void writeResponseQuietly(File requestFile, JSONObject response) {
        try {
            writeResponse(requestFile, response, null);
        } catch (Throwable ignored) {
            requestFile.delete();
            record(response);
        }
    }

    private void record(JSONObject response) {
        String status = nonBlank(response.optString("status"), STATUS_FAILED);
        lastStatus.set(status);
        lastErrorCode.set(nonBlank(response.optString("errorCode"), ""));
        elapsedMs.addAndGet(Math.max(0L, response.optLong("elapsedMs", 0L)));
        switch (status) {
            case STATUS_DECRYPTED:
                resolvedCount.incrementAndGet();
                decryptedCount.incrementAndGet();
                break;
            case STATUS_NOT_ENCRYPTED:
                resolvedCount.incrementAndGet();
                notEncryptedCount.incrementAndGet();
                break;
            case STATUS_NOT_FOUND:
                notFoundCount.incrementAndGet();
                break;
            case STATUS_DENIED:
                deniedCount.incrementAndGet();
                break;
            case STATUS_CANCELLED:
                cancelledCount.incrementAndGet();
                break;
            case STATUS_TIMED_OUT:
                timedOutCount.incrementAndGet();
                break;
            default:
                failedCount.incrementAndGet();
                break;
        }
    }

    private static JSONObject failureJson(
            String id,
            String status,
            String path,
            String resolvedPath,
            long elapsedMs,
            String errorCode,
            String errorMessage
    ) {
        try {
            return new JSONObject()
                    .put("version", CONTRACT_VERSION)
                    .put("id", nonBlank(id, "invalid"))
                    .put("status", status)
                    // Transport failures do not need to echo the requested
                    // absolute path back into runtime diagnostics.
                    .put("resolvedPath", nonBlank(resolvedPath, ""))
                    .put("sourcePath", "")
                    .put("sourceBytes", 0L)
                    .put("elapsedMs", Math.max(0L, elapsedMs))
                    .put("errorCode", nonBlank(errorCode, ERROR_FAILED))
                    .put("errorMessage", nonBlank(errorMessage, "Module-source provider request failed."));
        } catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isStatus(String value) {
        return STATUS_DECRYPTED.equals(value) ||
                STATUS_NOT_ENCRYPTED.equals(value) ||
                STATUS_NOT_FOUND.equals(value) ||
                STATUS_DENIED.equals(value) ||
                STATUS_CANCELLED.equals(value) ||
                STATUS_TIMED_OUT.equals(value) ||
                STATUS_FAILED.equals(value);
    }

    private static long boundedTimeoutMs(long value) {
        long normalized = value <= 0L ? DEFAULT_TIMEOUT_MS : value;
        return Math.max(1L, Math.min(normalized, HARD_TIMEOUT_MS));
    }

    private static long elapsedSince(long startedAt) {
        return Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    }

    private static long deadlineAfter(long startedAt, long timeoutMs) {
        return startedAt > Long.MAX_VALUE - timeoutMs ? Long.MAX_VALUE : startedAt + timeoutMs;
    }

    private static long remainingMs(long deadline) {
        return Math.max(0L, deadline - SystemClock.elapsedRealtime());
    }

    private static String readTextBounded(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > REQUEST_JSON_BYTES_LIMIT) {
                    throw new IOException("Module-source provider request JSON is too large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void closeSourceFd(Bundle bundle) {
        if (bundle == null || !bundle.containsKey(KEY_SOURCE_FD)) {
            return;
        }
        try {
            @SuppressWarnings("deprecation")
            ParcelFileDescriptor descriptor = bundle.getParcelable(KEY_SOURCE_FD);
            if (descriptor != null) {
                descriptor.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void closeActiveSourceDescriptor() {
        ParcelFileDescriptor descriptor = activeSourceDescriptor.getAndSet(null);
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Throwable ignored) {
        }
    }

    private static String[] nativePayloadFromMap(Map<String, String> values) {
        String[] result = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result[index++] = entry.getKey() + "=" + nonBlank(entry.getValue(), "");
        }
        return result;
    }

    private static String safeFileName(String value) {
        String safe = nonBlank(value, "invalid").replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.length() <= 120 ? safe : safe.substring(0, 120);
    }

    private static String fileId(File file) {
        String name = file == null ? "invalid" : file.getName();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String messageOf(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        return nonBlank(error.getMessage(), error.getClass().getSimpleName());
    }

    private static String publicFailureMessage(Throwable error) {
        if (error instanceof ProviderTimeoutException) {
            return "Module-source provider transport timed out.";
        }
        if (error instanceof BudgetExceededException) {
            return "Module-source provider transport budget is exhausted.";
        }
        return "Module-source provider transport failed.";
    }

    private void cancelProviderAsync(String reason) {
        Thread cancelThread = new Thread(() -> {
            try {
                provider.cancel(reason);
            } catch (Throwable ignored) {
            }
        });
        cancelThread.setName("AutoJs6ModuleSourceCancel");
        cancelThread.setDaemon(true);
        cancelThread.start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        try {
            if (OsConstants.S_ISLNK(Os.lstat(file.getAbsolutePath()).st_mode)) {
                file.delete();
                return;
            }
        } catch (Throwable ignored) {
            // Fail closed: never traverse a path whose link type cannot be
            // established. Deleting the path itself remains best effort.
            file.delete();
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static final class ProviderTimeoutException extends IOException {
        ProviderTimeoutException(String message) {
            super(message);
        }
    }

    private static final class BudgetExceededException extends IOException {
        BudgetExceededException(String message) {
            super(message);
        }
    }
}

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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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

    static final String RUNTIME_MODULE_NAME = NodeJsRuntimeContract.RUNTIME_MODULE_SOURCE_PROVIDER_NAME;
    static final String KEY_PROVIDER_BINDER = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER;

    static final String KEY_VERSION = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION;
    static final String KEY_REQUEST_ID = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID;
    static final String KEY_PATH = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH;
    static final String KEY_STATUS = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS;
    static final String KEY_RESOLVED_PATH = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH;
    static final String KEY_SOURCE_FD = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD;
    static final String KEY_SOURCE_BYTES = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES;
    static final String KEY_ELAPSED_MS = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS;
    static final String KEY_OPERATION = NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION;
    static final String KEY_DEADLINE_ELAPSED_REALTIME_MS =
            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS;

    static final int CONTRACT_VERSION = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION;
    static final long SINGLE_SOURCE_BYTES_LIMIT = 16L * 1024L * 1024L;
    static final long TOTAL_SOURCE_BYTES_LIMIT = 64L * 1024L * 1024L;
    static final int REQUEST_COUNT_LIMIT = 1024;
    static final int TRANSPORT_REQUEST_COUNT_LIMIT = REQUEST_COUNT_LIMIT * 2;

    private static final long DEFAULT_TIMEOUT_MS = 5000L;
    private static final long HARD_TIMEOUT_MS = 5000L;
    private static final long POLL_INTERVAL_MS = 5L;
    private static final long STOP_JOIN_MS = 1000L;
    private static final int REQUEST_JSON_BYTES_LIMIT = 64 * 1024;
    private static final int COPY_BUFFER_BYTES = 16 * 1024;
    private static final int TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256;
    private static final String OPERATION_RESOLVE = "resolve";
    private static final String OPERATION_MATERIALIZE_MISSING_PLAINTEXT =
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT;
    private static final String OPERATION_PREPARE_PLAINTEXT_TYPESCRIPT =
            "prepare_plaintext_typescript";
    private static final String STATUS_PREPARED = "prepared";
    private static final String STATUS_MATERIALIZED_PLAINTEXT = "materialized_plaintext";
    private static final String STATUS_DECRYPTED = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED;
    private static final String STATUS_PLAINTEXT = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT;
    private static final String STATUS_NOT_ENCRYPTED = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED;
    private static final String STATUS_NOT_FOUND = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND;
    private static final String STATUS_DENIED = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED;
    private static final String STATUS_CANCELLED = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED;
    private static final String STATUS_TIMED_OUT = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT;
    private static final String STATUS_FAILED = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED;

    private static final String ERROR_INVALID_REQUEST = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST;
    private static final String ERROR_BUDGET_EXCEEDED = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED;
    private static final String ERROR_CANCELLED = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_CANCELLED;
    private static final String ERROR_TIMED_OUT = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_TIMED_OUT;
    private static final String ERROR_FAILED = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED;

    private final File root;
    private final File requestDir;
    private final File responseDir;
    private final String executionId;
    private final INodeJsModuleSourceProvider provider;
    private final PluginWorkspaceArchiveSession workspaceSession;
    private final long timeoutMs;
    // Starts at the requested (or maximum supported) version and downgrades
    // once, permanently, when the provider answers with a v1 envelope.
    private volatile int providerContractVersion;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger providerRequestCount = new AtomicInteger(0);
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
    private final AtomicInteger rawSourceCount = new AtomicInteger(0);
    private final AtomicLong sourceBytes = new AtomicLong(0L);
    private final AtomicInteger missingCandidateRequestCount = new AtomicInteger(0);
    private final AtomicInteger plaintextCount = new AtomicInteger(0);
    private final AtomicInteger materializedCount = new AtomicInteger(0);
    private final AtomicLong materializedSourceBytes = new AtomicLong(0L);
    private final AtomicInteger materializationFailureCount = new AtomicInteger(0);
    private final AtomicInteger rawAlreadyAccountedPreparationCount = new AtomicInteger(0);
    private final AtomicInteger preparedSourceCount = new AtomicInteger(0);
    private final AtomicLong preparedSourceBytes = new AtomicLong(0L);
    private final AtomicInteger typeScriptSourceCount = new AtomicInteger(0);
    private final AtomicInteger typeScriptStrippedCount = new AtomicInteger(0);
    private final AtomicInteger typeScriptFailureCount = new AtomicInteger(0);
    private final AtomicLong typeScriptInputBytes = new AtomicLong(0L);
    private final AtomicLong typeScriptOutputBytes = new AtomicLong(0L);
    private final AtomicInteger plaintextTypeScriptPreparationRequestCount = new AtomicInteger(0);
    private final AtomicInteger plaintextTypeScriptPreparationCount = new AtomicInteger(0);
    private final AtomicLong elapsedMs = new AtomicLong(0L);
    private final AtomicLong transportElapsedMs = new AtomicLong(0L);
    private final AtomicInteger mappedRequestPathCount = new AtomicInteger(0);
    private final AtomicInteger mappedResponsePathCount = new AtomicInteger(0);
    private final AtomicInteger metadataPreflightPathCount = new AtomicInteger(0);
    private final AtomicInteger metadataPreflightProviderRequestCount = new AtomicInteger(0);
    private final AtomicInteger metadataPreflightDefaultCount = new AtomicInteger(0);
    private final AtomicInteger metadataPreflightCacheReplayCount = new AtomicInteger(0);
    private final AtomicLong metadataPreflightSourceBytes = new AtomicLong(0L);
    private final AtomicInteger metadataPreflightSequence = new AtomicInteger(0);
    private final AtomicReference<String> lastStatus = new AtomicReference<>("");
    private final AtomicReference<String> lastErrorCode = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptStatus = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptSourceName = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptExtension = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptStripped = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptErrorCode = new AtomicReference<>("");
    private final AtomicReference<String> lastTypeScriptSyntaxKind = new AtomicReference<>("");
    private final AtomicInteger lastTypeScriptLine = new AtomicInteger(0);
    private final AtomicInteger lastTypeScriptColumn = new AtomicInteger(0);
    private final AtomicReference<ParcelFileDescriptor> activeSourceDescriptor = new AtomicReference<>(null);
    private final Map<String, PendingPlaintextTypeScriptPreparation> pendingPlaintextPreparations =
            new ConcurrentHashMap<>();
    private final Map<String, MetadataPreflightReplay> metadataPreflightReplays =
            new ConcurrentHashMap<>();
    private final Thread thread;

    PluginModuleSourceProviderFileTransportSession(
            File cacheDir,
            String executionId,
            INodeJsModuleSourceProvider provider,
            long requestedTimeoutMs,
            PluginWorkspaceArchiveSession workspaceSession
    ) {
        this(cacheDir, executionId, provider, requestedTimeoutMs, workspaceSession, CONTRACT_VERSION);
    }

    PluginModuleSourceProviderFileTransportSession(
            File cacheDir,
            String executionId,
            INodeJsModuleSourceProvider provider,
            long requestedTimeoutMs,
            PluginWorkspaceArchiveSession workspaceSession,
            int providerContractVersion
    ) {
        this.executionId = nonBlank(executionId, "execution-" + System.nanoTime());
        this.provider = provider;
        this.workspaceSession = workspaceSession;
        this.timeoutMs = boundedTimeoutMs(requestedTimeoutMs);
        this.providerContractVersion = providerContractVersion;
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

    RuntimeMetadataSnapshot readPolicyMetadataSnapshot(
            String workingDirectory,
            String sandboxRoot,
            boolean permissionMetadataRequired,
            boolean bridgeLimitMetadataRequired,
            long requestStartedAtElapsedRealtimeMs
    ) throws IOException {
        if (running.get() || stopped.get()) {
            throw new IOException("Runtime policy metadata preflight must finish before transport startup.");
        }
        if (workspaceSession == null) {
            throw new IOException("Runtime policy metadata preflight requires a private workspace.");
        }
        if (!permissionMetadataRequired && !bridgeLimitMetadataRequired) {
            return RuntimeMetadataSnapshot.empty();
        }
        long deadline = deadlineAfter(requestStartedAtElapsedRealtimeMs, timeoutMs);
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        String workingProjectPath = metadataPath(workingDirectory, "project.json");
        String workingPackagePath = metadataPath(workingDirectory, "package.json");
        String sandboxProjectPath = metadataPath(sandboxRoot, "project.json");
        String sandboxPackagePath = metadataPath(sandboxRoot, "package.json");
        for (String path : policyMetadataExactPaths(
                workingDirectory,
                sandboxRoot,
                permissionMetadataRequired,
                bridgeLimitMetadataRequired
        )) {
            readProviderMetadataOnce(texts, path, deadline);
        }
        return new RuntimeMetadataSnapshot(
                texts.get(workingProjectPath),
                texts.get(workingPackagePath),
                workingProjectPath.equals(sandboxProjectPath) ? null : texts.get(sandboxProjectPath),
                workingPackagePath.equals(sandboxPackagePath) ? null : texts.get(sandboxPackagePath)
        );
    }

    static RuntimeMetadataSnapshot readPolicyMetadataWithoutProvider(
            PluginWorkspaceArchiveSession workspaceSession,
            String workingDirectory,
            String sandboxRoot,
            boolean permissionMetadataRequired,
            boolean bridgeLimitMetadataRequired,
            long requestedTimeoutMs,
            long requestStartedAtElapsedRealtimeMs
    ) throws IOException {
        if (!permissionMetadataRequired && !bridgeLimitMetadataRequired) {
            return RuntimeMetadataSnapshot.empty();
        }
        if (workspaceSession == null) {
            throw new IOException("Runtime policy metadata requires a private workspace.");
        }
        long deadline = deadlineAfter(
                requestStartedAtElapsedRealtimeMs,
                boundedTimeoutMs(requestedTimeoutMs)
        );
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        String workingProjectPath = metadataPath(workingDirectory, "project.json");
        String workingPackagePath = metadataPath(workingDirectory, "package.json");
        String sandboxProjectPath = metadataPath(sandboxRoot, "project.json");
        String sandboxPackagePath = metadataPath(sandboxRoot, "package.json");
        for (String path : policyMetadataExactPaths(
                workingDirectory,
                sandboxRoot,
                permissionMetadataRequired,
                bridgeLimitMetadataRequired
        )) {
            readLocalMetadataOnce(texts, workspaceSession, path, deadline);
        }
        return new RuntimeMetadataSnapshot(
                texts.get(workingProjectPath),
                texts.get(workingPackagePath),
                workingProjectPath.equals(sandboxProjectPath) ? null : texts.get(sandboxProjectPath),
                workingPackagePath.equals(sandboxPackagePath) ? null : texts.get(sandboxPackagePath)
        );
    }

    private String readProviderMetadataOnce(
            Map<String, String> texts,
            String runtimePath,
            long deadline
    ) throws IOException {
        if (texts.containsKey(runtimePath)) {
            return texts.get(runtimePath);
        }
        metadataPreflightPathCount.incrementAndGet();
        String text = readProviderMetadata(runtimePath, deadline);
        texts.put(runtimePath, text);
        return text;
    }

    private static String readLocalMetadataOnce(
            Map<String, String> texts,
            PluginWorkspaceArchiveSession workspaceSession,
            String runtimePath,
            long deadline
    ) throws IOException {
        if (texts.containsKey(runtimePath)) {
            return texts.get(runtimePath);
        }
        byte[] source = workspaceSession.readExactRuntimeMetadataNoFollow(
                runtimePath,
                SINGLE_SOURCE_BYTES_LIMIT,
                deadline
        );
        String text = source == null ? null : decodeStrictUtf8(source);
        texts.put(runtimePath, text);
        return text;
    }

    private String readProviderMetadata(String runtimePath, long deadline) throws IOException {
        MetadataProviderResponse resolve = callMetadataProvider(runtimePath, false, deadline);
        try {
            switch (resolve.status) {
                case STATUS_DECRYPTED: {
                    byte[] source = consumeMetadataPfd(resolve, false, deadline);
                    recordMetadataResponse(STATUS_DECRYPTED, resolve, "");
                    cacheMetadataReplay(runtimePath, STATUS_DECRYPTED, source);
                    return decodeStrictUtf8(source);
                }
                case STATUS_NOT_ENCRYPTED: {
                    if (!metadataProviderStatusReadsWorkspace(resolve.status)) {
                        throw new IOException("Runtime policy metadata local-read decision drifted.");
                    }
                    closeSourceFd(resolve.response);
                    recordMetadataResponse(STATUS_NOT_ENCRYPTED, resolve, "");
                    byte[] source = workspaceSession.readExactRuntimeMetadataNoFollow(
                            runtimePath,
                            SINGLE_SOURCE_BYTES_LIMIT,
                            deadline
                    );
                    if (source == null) {
                        MetadataProviderResponse materialized = callMetadataProvider(
                                runtimePath,
                                true,
                                deadline
                        );
                        try {
                            if (STATUS_NOT_FOUND.equals(materialized.status)) {
                                closeSourceFd(materialized.response);
                                recordMetadataResponse(STATUS_NOT_FOUND, materialized, "");
                                metadataPreflightDefaultCount.incrementAndGet();
                                cacheMetadataReplay(runtimePath, metadataNormalResolveReplayStatus(STATUS_NOT_FOUND), null);
                                return null;
                            }
                            if (!STATUS_PLAINTEXT.equals(materialized.status)) {
                                throw terminalMetadataResponse(runtimePath, materialized);
                            }
                            source = consumeMetadataPfd(materialized, true, deadline);
                            recordMetadataResponse(STATUS_MATERIALIZED_PLAINTEXT, materialized, "");
                            cacheMetadataReplay(
                                    runtimePath,
                                    metadataNormalResolveReplayStatus(STATUS_MATERIALIZED_PLAINTEXT),
                                    null
                            );
                            return decodeStrictUtf8(source);
                        } finally {
                            closeSourceFd(materialized.response);
                        }
                    }
                    admitMetadataBytes(source.length);
                    cacheMetadataReplay(
                            runtimePath,
                            metadataNormalResolveReplayStatus(STATUS_NOT_ENCRYPTED),
                            null
                    );
                    return decodeStrictUtf8(source);
                }
                case STATUS_NOT_FOUND:
                    if (!metadataProviderStatusAllowsDefault(resolve.status)) {
                        throw new IOException("Runtime policy metadata default decision drifted.");
                    }
                    closeSourceFd(resolve.response);
                    recordMetadataResponse(STATUS_NOT_FOUND, resolve, "");
                    metadataPreflightDefaultCount.incrementAndGet();
                    cacheMetadataReplay(runtimePath, metadataNormalResolveReplayStatus(STATUS_NOT_FOUND), null);
                    return null;
                default:
                    throw terminalMetadataResponse(runtimePath, resolve);
            }
        } catch (PolicyMetadataException error) {
            throw error;
        } catch (ProviderTimeoutException error) {
            recordMetadataFailure(STATUS_TIMED_OUT, ERROR_TIMED_OUT);
            cancelProviderAsync("Runtime policy metadata transport timed out.");
            throw new PolicyMetadataException(ERROR_TIMED_OUT, publicFailureMessage(error), error);
        } catch (BudgetExceededException error) {
            recordMetadataFailure(STATUS_FAILED, ERROR_BUDGET_EXCEEDED);
            throw new PolicyMetadataException(ERROR_BUDGET_EXCEEDED, publicFailureMessage(error), error);
        } catch (IOException error) {
            recordMetadataFailure(STATUS_FAILED, ERROR_FAILED);
            throw new PolicyMetadataException(ERROR_FAILED, publicFailureMessage(error), error);
        } finally {
            closeSourceFd(resolve.response);
        }
    }

    private MetadataProviderResponse callMetadataProvider(
            String runtimePath,
            boolean materializationRequest,
            long deadline
    ) throws IOException {
        long remaining = remainingMs(deadline);
        if (remaining <= 0L) {
            recordMetadataFailure(STATUS_TIMED_OUT, ERROR_TIMED_OUT);
            throw new PolicyMetadataException(
                    ERROR_TIMED_OUT,
                    "Runtime policy metadata provider deadline expired."
            );
        }
        int providerCount = providerRequestCount.incrementAndGet();
        metadataPreflightProviderRequestCount.incrementAndGet();
        if (materializationRequest) {
            missingCandidateRequestCount.incrementAndGet();
        }
        if (providerCount > REQUEST_COUNT_LIMIT) {
            recordMetadataFailure(STATUS_FAILED, ERROR_BUDGET_EXCEEDED);
            throw new PolicyMetadataException(
                    ERROR_BUDGET_EXCEEDED,
                    "Module-source provider request count exceeds " + REQUEST_COUNT_LIMIT + "."
            );
        }
        String id = __metadataRequestId();
        Bundle response = null;
        try {
            response = callProvider(id, runtimePath, materializationRequest, deadline, remaining);
            if (response == null) {
                throw new IOException("Module-source provider returned a null policy-metadata response.");
            }
            Object rawVersion = response.get(KEY_VERSION);
            Object rawResponseId = response.get(KEY_REQUEST_ID);
            Object rawStatus = response.get(KEY_STATUS);
            Object rawOperation = response.get(KEY_OPERATION);
            boolean hasSourceFd = response.containsKey(KEY_SOURCE_FD);
            Object rawSourceFd = hasSourceFd ? response.get(KEY_SOURCE_FD) : null;
            boolean hasSourceBytes = response.containsKey(KEY_SOURCE_BYTES);
            Object rawSourceBytes = hasSourceBytes ? response.get(KEY_SOURCE_BYTES) : null;
            validateProviderResponseRawTypes(
                    rawVersion,
                    rawResponseId,
                    rawOperation,
                    rawStatus,
                    hasSourceFd,
                    rawSourceFd instanceof ParcelFileDescriptor,
                    hasSourceBytes,
                    rawSourceBytes
            );
            String expectedOperation = materializationRequest
                    ? OPERATION_MATERIALIZE_MISSING_PLAINTEXT
                    : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING;
            long declaredBytes = hasSourceBytes ? (Long) rawSourceBytes : 0L;
            validateProviderResponseShape(
                    (Integer) rawVersion,
                    nonBlank((String) rawResponseId, ""),
                    id,
                    nonBlank((String) rawOperation, ""),
                    expectedOperation,
                    nonBlank((String) rawStatus, ""),
                    hasSourceFd,
                    hasSourceBytes,
                    declaredBytes,
                    materializationRequest
            );
            String resolvedPath = nonBlank(response.getString(KEY_RESOLVED_PATH), runtimePath);
            String mappedResolvedPath = workspaceSession.mapHostPathToRuntime(resolvedPath);
            if (!mappedResolvedPath.equals(resolvedPath)) {
                mappedResponsePathCount.incrementAndGet();
            }
            String status = nonBlank((String) rawStatus, STATUS_FAILED);
            if ((STATUS_DECRYPTED.equals(status) || STATUS_NOT_ENCRYPTED.equals(status) ||
                    STATUS_PLAINTEXT.equals(status)) && !runtimePath.equals(mappedResolvedPath)) {
                throw new IOException("Runtime policy metadata provider resolved a different exact path.");
            }
            return new MetadataProviderResponse(
                    id,
                    status,
                    mappedResolvedPath,
                    declaredBytes,
                    Math.max(0L, response.getLong(KEY_ELAPSED_MS, 0L)),
                    nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_CODE), ""),
                    nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), ""),
                    response
            );
        } catch (ProviderTimeoutException error) {
            closeSourceFd(response);
            recordMetadataFailure(STATUS_TIMED_OUT, ERROR_TIMED_OUT);
            cancelProviderAsync("Runtime policy metadata provider request timed out: " + id);
            throw new PolicyMetadataException(ERROR_TIMED_OUT, publicFailureMessage(error), error);
        } catch (BudgetExceededException error) {
            closeSourceFd(response);
            recordMetadataFailure(STATUS_FAILED, ERROR_BUDGET_EXCEEDED);
            throw new PolicyMetadataException(ERROR_BUDGET_EXCEEDED, publicFailureMessage(error), error);
        } catch (PolicyMetadataException error) {
            closeSourceFd(response);
            throw error;
        } catch (Throwable error) {
            closeSourceFd(response);
            recordMetadataFailure(STATUS_FAILED, ERROR_FAILED);
            throw new PolicyMetadataException(ERROR_FAILED, publicFailureMessage(error), error);
        }
    }

    private byte[] consumeMetadataPfd(
            MetadataProviderResponse response,
            boolean materialize,
            long deadline
    ) throws IOException {
        long declaredBytes = response.sourceBytes;
        if (declaredBytes < 0L || declaredBytes > SINGLE_SOURCE_BYTES_LIMIT) {
            throw new BudgetExceededException("Runtime policy metadata exceeds the single-source byte budget.");
        }
        if (sourceBytes.get() > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes) {
            throw new BudgetExceededException("Runtime policy metadata exceeds the aggregate source byte budget.");
        }
        File privateSource = new File(responseDir, safeFileName(response.id) + ".metadata.source");
        try {
            long copied = copySource(response.response, privateSource, declaredBytes, deadline);
            if (copied != declaredBytes) {
                throw new IOException("Runtime policy metadata PFD byte count changed during copy.");
            }
            byte[] source = readSourceBytesBounded(privateSource, copied);
            admitMetadataBytes(copied);
            if (materialize) {
                plaintextCount.incrementAndGet();
                PluginWorkspaceArchiveSession.ProviderMaterialization receipt;
                try {
                    receipt = workspaceSession.materializeProviderSourceNoReplace(
                            response.resolvedPath,
                            privateSource,
                            copied,
                            deadline
                    );
                } catch (PluginWorkspaceArchiveSession.ProviderMaterializationDeadlineExceededException error) {
                    throw new ProviderTimeoutException(messageOf(error), error);
                }
                if (!response.resolvedPath.equals(receipt.runtimePath()) ||
                        copied != receipt.sourceBytes()) {
                    throw new IOException("Runtime policy metadata materialization receipt is invalid.");
                }
                materializedCount.incrementAndGet();
                materializedSourceBytes.addAndGet(copied);
                byte[] verified = workspaceSession.readExactRuntimeMetadataNoFollow(
                        response.resolvedPath,
                        SINGLE_SOURCE_BYTES_LIMIT,
                        deadline
                );
                if (verified == null || !Arrays.equals(source, verified)) {
                    throw new IOException("Materialized runtime policy metadata failed exact verified reread.");
                }
            }
            return source;
        } finally {
            privateSource.delete();
        }
    }

    private void admitMetadataBytes(long bytes) throws BudgetExceededException {
        if (bytes < 0L || bytes > SINGLE_SOURCE_BYTES_LIMIT ||
                sourceBytes.get() > TOTAL_SOURCE_BYTES_LIMIT - bytes) {
            throw new BudgetExceededException("Runtime policy metadata exceeds its provider byte budget.");
        }
        rawSourceCount.incrementAndGet();
        sourceBytes.addAndGet(bytes);
        metadataPreflightSourceBytes.addAndGet(bytes);
    }

    private PolicyMetadataException terminalMetadataResponse(
            String runtimePath,
            MetadataProviderResponse response
    ) {
        closeSourceFd(response.response);
        if (!metadataProviderStatusIsTerminal(response.status)) {
            recordMetadataFailure(STATUS_FAILED, ERROR_FAILED);
            return new PolicyMetadataException(
                    ERROR_FAILED,
                    "Runtime policy metadata provider returned an invalid terminal status for an exact path."
            );
        }
        String code;
        switch (response.status) {
            case STATUS_DENIED:
                code = nonBlank(response.errorCode, "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED");
                break;
            case STATUS_CANCELLED:
                code = nonBlank(response.errorCode, ERROR_CANCELLED);
                break;
            case STATUS_TIMED_OUT:
                code = nonBlank(response.errorCode, ERROR_TIMED_OUT);
                break;
            default:
                code = nonBlank(response.errorCode, ERROR_FAILED);
                break;
        }
        recordMetadataResponse(response.status, response, code);
        return new PolicyMetadataException(
                code,
                nonBlank(response.errorMessage,
                        "Runtime policy metadata provider returned " + response.status + " for an exact path.")
        );
    }

    private void recordMetadataResponse(
            String status,
            MetadataProviderResponse response,
            String errorCode
    ) {
        try {
            record(new JSONObject()
                    .put("status", status)
                    .put("elapsedMs", response.elapsedMs)
                    .put("errorCode", nonBlank(errorCode, response.errorCode)), true);
        } catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void recordMetadataFailure(String status, String errorCode) {
        transportFailureCount.incrementAndGet();
        try {
            record(new JSONObject()
                    .put("status", status)
                    .put("elapsedMs", 0L)
                    .put("errorCode", errorCode), true);
        } catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void cacheMetadataReplay(String runtimePath, String status, byte[] source) {
        metadataPreflightReplays.put(
                metadataReplayKey(runtimePath),
                new MetadataPreflightReplay(status, runtimePath, source)
        );
    }

    private String __metadataRequestId() {
        return safeFileName(
                "metadata-" + metadataPreflightSequence.incrementAndGet() + "-" + executionId
        );
    }

    private static String metadataReplayKey(String runtimePath) {
        return OPERATION_RESOLVE + "\n" + runtimePath;
    }

    static java.util.List<String> policyMetadataExactPaths(
            String workingDirectory,
            String sandboxRoot,
            boolean permissionMetadataRequired,
            boolean bridgeLimitMetadataRequired
    ) throws IOException {
        if (!permissionMetadataRequired && !bridgeLimitMetadataRequired) {
            return java.util.Collections.emptyList();
        }
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        paths.add(metadataPath(workingDirectory, "project.json"));
        paths.add(metadataPath(workingDirectory, "package.json"));
        if (permissionMetadataRequired) {
            paths.add(metadataPath(sandboxRoot, "project.json"));
            paths.add(metadataPath(sandboxRoot, "package.json"));
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(paths));
    }

    static boolean metadataProviderStatusReadsWorkspace(String status) {
        return STATUS_NOT_ENCRYPTED.equals(status);
    }

    static boolean metadataProviderStatusAllowsDefault(String status) {
        return STATUS_NOT_FOUND.equals(status);
    }

    static boolean metadataProviderStatusIsTerminal(String status) {
        return STATUS_DENIED.equals(status) || STATUS_FAILED.equals(status) ||
                STATUS_CANCELLED.equals(status) || STATUS_TIMED_OUT.equals(status);
    }

    static String metadataNormalResolveReplayStatus(String preflightStatus) {
        if (STATUS_MATERIALIZED_PLAINTEXT.equals(preflightStatus) ||
                STATUS_NOT_ENCRYPTED.equals(preflightStatus)) {
            return STATUS_NOT_ENCRYPTED;
        }
        if (STATUS_DECRYPTED.equals(preflightStatus) || STATUS_NOT_FOUND.equals(preflightStatus)) {
            return preflightStatus;
        }
        throw new IllegalArgumentException("Runtime policy metadata status cannot be replayed: " + preflightStatus);
    }

    private static String metadataPath(String root, String name) throws IOException {
        String normalizedRoot = nonBlank(root, null);
        if (normalizedRoot == null) {
            throw new IOException("Runtime policy metadata root is missing.");
        }
        return new File(normalizedRoot, name).getAbsolutePath();
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
        pendingPlaintextPreparations.clear();
        metadataPreflightReplays.clear();
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
        values.put("embedded_script.runtime_plugin.module_provider.transport", "local_file_pfd_v2");
        values.put("embedded_script.runtime_plugin.module_provider.request_count", Integer.toString(providerRequestCount.get()));
        values.put(
                "embedded_script.runtime_plugin.module_provider.transport_request_count",
                Integer.toString(requestCount.get())
        );
        values.put("embedded_script.runtime_plugin.module_provider.resolved_count", Integer.toString(resolvedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.decrypted_count", Integer.toString(decryptedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.not_encrypted_count", Integer.toString(notEncryptedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.not_found_count", Integer.toString(notFoundCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.denied_count", Integer.toString(deniedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.cancelled_count", Integer.toString(cancelledCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.timed_out_count", Integer.toString(timedOutCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.failed_count", Integer.toString(failedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.raw_source_count", Integer.toString(rawSourceCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.source_bytes", Long.toString(sourceBytes.get()));
        values.put("embedded_script.runtime_plugin.module_provider.raw_source_bytes", Long.toString(sourceBytes.get()));
        values.put(
                "embedded_script.runtime_plugin.module_provider.missing_candidate_request_count",
                Integer.toString(missingCandidateRequestCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.plaintext_count",
                Integer.toString(plaintextCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.materialized_count",
                Integer.toString(materializedCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.materialized_source_bytes",
                Long.toString(materializedSourceBytes.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.materialization_failure_count",
                Integer.toString(materializationFailureCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.typescript.raw_already_accounted_preparation_count",
                Integer.toString(rawAlreadyAccountedPreparationCount.get())
        );
        values.put("embedded_script.runtime_plugin.module_provider.prepared_source_count", Integer.toString(preparedSourceCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.prepared_source_bytes", Long.toString(preparedSourceBytes.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.source_count", Integer.toString(typeScriptSourceCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.stripped_count", Integer.toString(typeScriptStrippedCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.failure_count", Integer.toString(typeScriptFailureCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.input_bytes", Long.toString(typeScriptInputBytes.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.output_bytes", Long.toString(typeScriptOutputBytes.get()));
        values.put(
                "embedded_script.runtime_plugin.module_provider.typescript.plaintext_preparation_request_count",
                Integer.toString(plaintextTypeScriptPreparationRequestCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.typescript.plaintext_preparation_count",
                Integer.toString(plaintextTypeScriptPreparationCount.get())
        );
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_status", lastTypeScriptStatus.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_source_name", lastTypeScriptSourceName.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_extension", lastTypeScriptExtension.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_stripped", lastTypeScriptStripped.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_error_code", lastTypeScriptErrorCode.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_syntax_kind", lastTypeScriptSyntaxKind.get());
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_line", Integer.toString(lastTypeScriptLine.get()));
        values.put("embedded_script.runtime_plugin.module_provider.typescript.last_column", Integer.toString(lastTypeScriptColumn.get()));
        values.put("embedded_script.runtime_plugin.module_provider.elapsed_ms", Long.toString(elapsedMs.get()));
        values.put("embedded_script.runtime_plugin.module_provider.last_status", lastStatus.get());
        values.put("embedded_script.runtime_plugin.module_provider.last_error_code", lastErrorCode.get());
        values.put("embedded_script.runtime_plugin.module_provider.mapped_request_path_count", Integer.toString(mappedRequestPathCount.get()));
        values.put("embedded_script.runtime_plugin.module_provider.mapped_response_path_count", Integer.toString(mappedResponsePathCount.get()));
        values.put(
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.path_count",
                Integer.toString(metadataPreflightPathCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count",
                Integer.toString(metadataPreflightProviderRequestCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.default_count",
                Integer.toString(metadataPreflightDefaultCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.cache_replay_count",
                Integer.toString(metadataPreflightCacheReplayCount.get())
        );
        values.put(
                "embedded_script.runtime_plugin.module_provider.metadata_preflight.source_bytes",
                Long.toString(metadataPreflightSourceBytes.get())
        );
        values.put("embedded_script.module_provider.transport", "file_pfd_v2");
        values.put("embedded_script.module_provider.transport_request_count", Integer.toString(requestCount.get()));
        values.put("embedded_script.module_provider.transport_response_count", Integer.toString(responseCount.get()));
        values.put(
                "embedded_script.module_provider.transport_failure_count",
                Integer.toString(transportFailureCount.get())
        );
        values.put("embedded_script.module_provider.transport_source_bytes", Long.toString(sourceBytes.get()));
        values.put(
                "embedded_script.module_provider.transport_elapsed_ms",
                Long.toString(transportElapsedMs.get())
        );
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
            pendingPlaintextPreparations.clear();
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
        boolean plaintextPreparationRequest = false;
        boolean materializationRequest = false;
        int count = requestCount.incrementAndGet();
        try {
            requestJson = new JSONObject(readTextBounded(requestFile));
            String requestedId = nonBlank(requestJson.optString("id"), "");
            boolean validId = !requestedId.isEmpty() &&
                    requestedId.equals(fallbackId) &&
                    requestedId.equals(safeFileName(requestedId));
            String id = validId ? requestedId : fallbackId;
            String requestedExecutionId = nonBlank(requestJson.optString("executionId"), "");
            String operation = nonBlank(requestJson.optString("operation"), OPERATION_RESOLVE);
            plaintextPreparationRequest = OPERATION_PREPARE_PLAINTEXT_TYPESCRIPT.equals(operation);
            materializationRequest = OPERATION_MATERIALIZE_MISSING_PLAINTEXT.equals(operation);
            boolean validOperation = OPERATION_RESOLVE.equals(operation) ||
                    materializationRequest || plaintextPreparationRequest;
            String path = plaintextPreparationRequest
                    ? nonBlank(requestJson.optString("sourceName"), "")
                    : nonBlank(requestJson.optString("path"), "");
            if (count > TRANSPORT_REQUEST_COUNT_LIMIT) {
                transportFailureCount.incrementAndGet();
                transportFailureRecorded = true;
                writeResponse(requestFile, failureJson(
                        id,
                        STATUS_FAILED,
                        path,
                        "",
                        elapsedSince(startedAt),
                        ERROR_BUDGET_EXCEEDED,
                        "Module-source provider transport operation count exceeds " +
                                TRANSPORT_REQUEST_COUNT_LIMIT + "."
                ), null, !plaintextPreparationRequest);
                return;
            }
            if (!validId || !validOperation ||
                    requestJson.optInt("version", 0) != CONTRACT_VERSION || path.isEmpty()) {
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
                ), null, !plaintextPreparationRequest);
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
                ), null, !plaintextPreparationRequest);
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
            if (plaintextPreparationRequest) {
                handlePlaintextTypeScriptPreparation(
                        requestFile,
                        id,
                        path,
                        startedAt,
                        requestDeadline,
                        requestJson
                );
                return;
            }
            if (!materializationRequest) {
                MetadataPreflightReplay replay = metadataPreflightReplays.remove(
                        metadataReplayKey(path)
                );
                if (replay != null) {
                    publishMetadataPreflightReplay(requestFile, id, path, startedAt, replay);
                    return;
                }
            }
            if (materializationRequest) {
                if (workspaceSession == null ||
                        !PluginWorkspaceArchiveSession.isSupportedProviderMaterializationPath(path)) {
                    throw new IOException(
                            "Missing plaintext materialization requires a workspace and exact supported extension."
                    );
                }
                missingCandidateRequestCount.incrementAndGet();
            }
            int providerCount = providerRequestCount.incrementAndGet();
            if (providerCount > REQUEST_COUNT_LIMIT) {
                throw new BudgetExceededException(
                        "Module-source provider request count exceeds " + REQUEST_COUNT_LIMIT + "."
                );
            }
            Bundle providerResponse = callProvider(
                    id,
                    path,
                    materializationRequest,
                    requestDeadline,
                    providerWaitMs
            );
            handleProviderResponse(
                    requestFile,
                    id,
                    path,
                    startedAt,
                    requestDeadline,
                    materializationRequest,
                    providerResponse
            );
        } catch (ProviderTimeoutException error) {
            if (!transportFailureRecorded) {
                transportFailureCount.incrementAndGet();
            }
            String id = requestJson == null ? fallbackId : nonBlank(requestJson.optString("id"), fallbackId);
            String path = requestJson == null ? "" : nonBlank(requestJson.optString("path"), "");
            if (!plaintextPreparationRequest) {
                cancelProviderAsync("Module-source provider request timed out: " + id);
            }
            writeResponseQuietly(requestFile, failureJson(
                    id,
                    STATUS_TIMED_OUT,
                    path,
                    "",
                    elapsedSince(startedAt),
                    ERROR_TIMED_OUT,
                    publicFailureMessage(error)
            ), !plaintextPreparationRequest);
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
            ), !plaintextPreparationRequest);
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            transportFailureCount.incrementAndGet();
            recordTypeScriptFailure(
                    error.sourceName(),
                    error.errorCode(),
                    error.syntaxKind(),
                    error.line(),
                    error.column()
            );
            String id = requestJson == null ? fallbackId : nonBlank(requestJson.optString("id"), fallbackId);
            String path = requestJson == null ? "" : nonBlank(requestJson.optString("path"), "");
            writeResponseQuietly(requestFile, failureJson(
                    id,
                    STATUS_FAILED,
                    path,
                    "",
                    elapsedSince(startedAt),
                    error.errorCode(),
                    messageOf(error)
            ), !plaintextPreparationRequest);
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
            ), !plaintextPreparationRequest);
        } finally {
            new File(requestDir, safeFileName(fallbackId) + ".source").delete();
        }
    }

    private void publishMetadataPreflightReplay(
            File requestFile,
            String id,
            String requestedPath,
            long startedAt,
            MetadataPreflightReplay replay
    ) throws IOException {
        if (!requestedPath.equals(replay.resolvedPath)) {
            throw new IOException("Runtime policy metadata replay path changed.");
        }
        File sourceFile = null;
        long replayBytes = 0L;
        if (STATUS_DECRYPTED.equals(replay.status)) {
            byte[] source = replay.source();
            sourceFile = new File(responseDir, safeFileName(id) + ".source");
            replaceSourceAtomically(sourceFile, source);
            replayBytes = source.length;
        }
        JSONObject response;
        try {
            response = new JSONObject()
                    .put("version", CONTRACT_VERSION)
                    .put("id", id)
                    .put("status", replay.status)
                    .put("resolvedPath", replay.resolvedPath)
                    .put("sourcePath", sourceFile == null ? "" : sourceFile.getAbsolutePath())
                    .put("sourceBytes", replayBytes)
                    .put("rawAlreadyAccounted", true)
                    .put("elapsedMs", elapsedSince(startedAt))
                    .put("errorCode", "")
                    .put("errorMessage", "");
        } catch (Throwable error) {
            if (sourceFile != null) sourceFile.delete();
            throw new IOException("Could not encode runtime policy metadata replay.", error);
        }
        writeResponse(requestFile, response, sourceFile, false);
        metadataPreflightCacheReplayCount.incrementAndGet();
    }

    /**
     * Published v1 hosts speak the same AIDL surface but do not know the v2
     * operation vocabulary: they answer with a version-1 envelope, never echo
     * an operation, and cannot materialize missing plaintext. Normalize such
     * responses into the v2 shape the downstream validators expect instead of
     * rejecting the whole execution.
     */
    private Bundle normalizeProviderResponseForContract(Bundle response, boolean materializationRequest) {
        if (response == null) {
            return null;
        }
        Object rawVersion = response.get(KEY_VERSION);
        boolean legacyEnvelope = rawVersion instanceof Integer
                && (Integer) rawVersion == NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION
                && CONTRACT_VERSION != NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION;
        if (!legacyEnvelope) {
            return response;
        }
        response.putInt(KEY_VERSION, CONTRACT_VERSION);
        String operation = nonBlank(response.getString(KEY_OPERATION), "");
        if (operation.isEmpty()) {
            response.putString(
                    KEY_OPERATION,
                    materializationRequest
                            ? OPERATION_MATERIALIZE_MISSING_PLAINTEXT
                            : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING
            );
        }
        if (materializationRequest) {
            String status = nonBlank(response.getString(KEY_STATUS), "");
            if (STATUS_DECRYPTED.equals(status)) {
                response.putString(KEY_STATUS, STATUS_PLAINTEXT);
            } else if (STATUS_NOT_ENCRYPTED.equals(status)) {
                closeSourceFd(response);
                response.remove(KEY_SOURCE_FD);
                response.remove(KEY_SOURCE_BYTES);
                response.putString(KEY_STATUS, STATUS_NOT_FOUND);
            }
        }
        return response;
    }

    /**
     * A published v1 provider rejects any request whose version header is not
     * exactly 1. Detect that rejection so the session can downgrade its
     * request header once and retry.
     */
    private static boolean isLegacyProviderVersionRejection(Bundle response) {
        if (response == null) {
            return false;
        }
        Object rawVersion = response.get(KEY_VERSION);
        return rawVersion instanceof Integer
                && (Integer) rawVersion == NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION
                && ERROR_INVALID_REQUEST.equals(
                        nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_CODE), ""));
    }

    private Bundle callProvider(
            String id,
            String path,
            boolean materializeMissingPlaintext,
            long requestDeadline,
            long callTimeoutMs
    ) throws Exception {
        String providerPath = workspaceSession == null
                ? path
                : workspaceSession.mapRuntimePathToHost(path);
        if (!providerPath.equals(path)) {
            mappedRequestPathCount.incrementAndGet();
        }
        Bundle providerRequest = new Bundle();
        providerRequest.putInt(KEY_VERSION, providerContractVersion);
        providerRequest.putString(KEY_REQUEST_ID, id);
        providerRequest.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
        providerRequest.putString(KEY_PATH, providerPath);
        providerRequest.putString(
                KEY_OPERATION,
                materializeMissingPlaintext
                        ? NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT
                        : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING
        );
        providerRequest.putLong(KEY_DEADLINE_ELAPSED_REALTIME_MS, requestDeadline);
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
            if (providerContractVersion > NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION
                    && isLegacyProviderVersionRejection(result)) {
                // The provider only speaks v1: downgrade this session's
                // request header once and retry the same request.
                providerContractVersion =
                        NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION;
                closeSourceFd(result);
                return callProvider(
                        id,
                        path,
                        materializeMissingPlaintext,
                        requestDeadline,
                        remainingMs(requestDeadline)
                );
            }
            return normalizeProviderResponseForContract(result, materializeMissingPlaintext);
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

    /**
     * Completes the second, plugin-private leg of plaintext TypeScript
     * preparation. Native code publishes this request only after it has
     * revalidated the authorized workspace path and obtained the raw bytes
     * through its fd-identity checked read. Java therefore never reopens the
     * workspace path: it consumes only the exact request-scoped private file,
     * applies the same plugin-owned stripper used for entry and preloaded
     * sources, and publishes another exact private file for native code.
     */
    private void handlePlaintextTypeScriptPreparation(
            File requestFile,
            String id,
            String sourceName,
            long startedAt,
            long requestDeadline,
            JSONObject requestJson
    ) throws Exception {
        plaintextTypeScriptPreparationRequestCount.incrementAndGet();
        if (plaintextTypeScriptPreparationRequestCount.get() > REQUEST_COUNT_LIMIT) {
            throw new BudgetExceededException(
                    "Plaintext TypeScript preparation request count exceeds " + REQUEST_COUNT_LIMIT + "."
            );
        }
        String parentRequestId = nonBlank(requestJson.optString("parentRequestId"), "");
        PendingPlaintextTypeScriptPreparation pending =
                pendingPlaintextPreparations.remove(parentRequestId);
        if (pending == null || !pending.sourceName.equals(sourceName)) {
            throw new IOException("Plaintext TypeScript preparation is not linked to an authorized response.");
        }
        long effectiveDeadline = Math.min(requestDeadline, pending.deadline);
        if (remainingMs(effectiveDeadline) <= 0L) {
            throw new ProviderTimeoutException("Plaintext TypeScript preparation deadline expired.");
        }
        String declaredSourcePath = nonBlank(requestJson.optString("sourcePath"), "");
        File requestSource = new File(requestDir, safeFileName(id) + ".source");
        long declaredBytes;
        try {
            declaredBytes = validatePlaintextTypeScriptPreparationEnvelope(
                    requestDir,
                    id,
                    sourceName,
                    declaredSourcePath,
                    requestJson.opt("sourceBytes")
            );
        } catch (BudgetExceededException error) {
            recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "raw_budget_exceeded", 0, 0);
            throw error;
        }
        byte[] rawSource;
        try {
            if (pending.rawAlreadyAccounted) {
                if (pending.rawSourceBytes != declaredBytes) {
                    throw new IOException(
                            "Plaintext TypeScript preparation raw-byte receipt does not match materialization."
                    );
                }
            } else {
                long rawBefore = sourceBytes.get();
                if (rawBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes) {
                    throw new BudgetExceededException(
                            "Plaintext TypeScript module sources exceed the aggregate raw byte budget."
                    );
                }
            }
            rawSource = readPrivatePreparationSource(
                    requestSource,
                    declaredBytes,
                    effectiveDeadline
            );
        } catch (BudgetExceededException error) {
            recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "raw_budget_exceeded", 0, 0);
            throw error;
        } catch (IOException error) {
            recordTypeScriptFailure(sourceName, ERROR_FAILED, "private_input_io", 0, 0);
            throw error;
        }
        if (pending.rawAlreadyAccounted) {
            rawAlreadyAccountedPreparationCount.incrementAndGet();
        } else {
            rawSourceCount.incrementAndGet();
            sourceBytes.addAndGet(rawSource.length);
        }
        typeScriptSourceCount.incrementAndGet();
        typeScriptInputBytes.addAndGet(rawSource.length);

        File responseSource = new File(responseDir, safeFileName(id) + ".source");
        try {
            PreparedTypeScriptSource preparation =
                    prepareDecryptedTypeScriptBytes(sourceName, rawSource);
            byte[] prepared = preparation.source();
            if (!preparation.typeScript()) {
                throw new IOException("Plaintext TypeScript preparation did not classify its source as TypeScript.");
            }
            if (prepared.length > SINGLE_SOURCE_BYTES_LIMIT) {
                throw new BudgetExceededException(
                        "Prepared plaintext TypeScript module source exceeds the single-source byte budget."
                );
            }
            ensurePreparedSourceBudget(prepared.length);
            replaceSourceAtomically(responseSource, prepared);
            JSONObject responseJson = new JSONObject()
                    .put("version", CONTRACT_VERSION)
                    .put("id", id)
                    .put("status", STATUS_PREPARED)
                    .put("resolvedPath", sourceName)
                    .put("sourcePath", responseSource.getAbsolutePath())
                    .put("sourceBytes", responseSource.length())
                    .put("rawSourceBytes", rawSource.length)
                    .put("elapsedMs", elapsedSince(startedAt))
                    .put("errorCode", "")
                    .put("errorMessage", "");
            writeResponse(requestFile, responseJson, responseSource, false);
            commitPreparedSource(prepared.length);
            if (preparation.stripped()) {
                typeScriptStrippedCount.incrementAndGet();
            }
            typeScriptOutputBytes.addAndGet(prepared.length);
            plaintextTypeScriptPreparationCount.incrementAndGet();
            recordTypeScriptSuccess(sourceName, preparation, prepared.length);
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            responseSource.delete();
            throw error;
        } catch (BudgetExceededException error) {
            responseSource.delete();
            recordTypeScriptFailure(sourceName, ERROR_BUDGET_EXCEEDED, "budget_exceeded", 0, 0);
            throw error;
        } catch (CharacterCodingException error) {
            responseSource.delete();
            recordTypeScriptFailure(sourceName, ERROR_FAILED, "invalid_utf8", 0, 0);
            throw new IOException("Plaintext TypeScript module source is not valid UTF-8.", error);
        } catch (IOException error) {
            responseSource.delete();
            recordTypeScriptFailure(sourceName, ERROR_FAILED, "transport_io", 0, 0);
            throw error;
        }
    }

    static long validatePlaintextTypeScriptPreparationEnvelope(
            File privateRequestDirectory,
            String id,
            String sourceName,
            String declaredSourcePath,
            Object declaredBytesValue
    ) throws IOException {
        File expectedSource = new File(privateRequestDirectory, safeFileName(id) + ".source");
        if (!expectedSource.getAbsolutePath().equals(declaredSourcePath)) {
            throw new IOException("Plaintext TypeScript preparation source path is invalid.");
        }
        if (!(declaredBytesValue instanceof Byte) &&
                !(declaredBytesValue instanceof Short) &&
                !(declaredBytesValue instanceof Integer) &&
                !(declaredBytesValue instanceof Long)) {
            throw new IOException("Plaintext TypeScript preparation byte count is invalid.");
        }
        long declaredBytes = ((Number) declaredBytesValue).longValue();
        if (declaredBytes < 0L || declaredBytes > SINGLE_SOURCE_BYTES_LIMIT) {
            throw new BudgetExceededException(
                    "Plaintext TypeScript module source exceeds the single-source byte budget."
            );
        }
        if (!NodeTypeScriptStripper.isTypeScriptSourceName(sourceName) &&
                !NodeTypeScriptStripper.isUnsupportedTypeScriptSourceName(sourceName)) {
            throw new IOException("Plaintext TypeScript preparation requires a TypeScript source name.");
        }
        return declaredBytes;
    }

    /**
     * Android-free response-envelope seam used by local JVM conformance tests.
     * Production calls this before consuming or copying any provider PFD.
     */
    static void validateProviderResponseShape(
            int version,
            String responseId,
            String expectedId,
            String responseOperation,
            String expectedOperation,
            String status,
            boolean hasSourceFd,
            boolean hasSourceBytes,
            long sourceBytes,
            boolean materializationRequest
    ) throws IOException {
        if (version != CONTRACT_VERSION || !expectedId.equals(responseId) ||
                !expectedOperation.equals(responseOperation) || !isStatus(status)) {
            throw new IOException(
                    "Module-source provider returned an invalid response identity, version, operation, or status."
            );
        }
        if ((materializationRequest &&
                (STATUS_DECRYPTED.equals(status) || STATUS_NOT_ENCRYPTED.equals(status))) ||
                (!materializationRequest && STATUS_PLAINTEXT.equals(status))) {
            throw new IOException("Module-source provider returned a status incompatible with its operation.");
        }
        boolean pfdStatus = STATUS_DECRYPTED.equals(status) || STATUS_PLAINTEXT.equals(status);
        if (pfdStatus && (!hasSourceFd || !hasSourceBytes)) {
            throw new IOException("PFD module-source response is missing its descriptor or byte count.");
        }
        if (!pfdStatus && (hasSourceFd || sourceBytes != 0L)) {
            throw new IOException("Non-PFD module-source response carries forbidden source data.");
        }
    }

    static void validateProviderResponseRawTypes(
            Object version,
            Object responseId,
            Object operation,
            Object status,
            boolean hasSourceFd,
            boolean sourceFdIsParcelFileDescriptor,
            boolean hasSourceBytes,
            Object sourceBytes
    ) throws IOException {
        if (!(version instanceof Integer) || !(responseId instanceof String) ||
                !(operation instanceof String) || !(status instanceof String)) {
            throw new IOException("Module-source provider response control fields have invalid types.");
        }
        if (hasSourceFd && !sourceFdIsParcelFileDescriptor) {
            throw new IOException("Module-source provider response descriptor has an invalid type.");
        }
        if (hasSourceBytes && !(sourceBytes instanceof Long)) {
            throw new IOException("Module-source provider response byte count has an invalid type.");
        }
    }

    private void handleProviderResponse(
            File requestFile,
            String id,
            String requestedPath,
            long startedAt,
            long requestDeadline,
            boolean materializationRequest,
            Bundle response
    ) throws Exception {
        if (response == null) {
            throw new IOException("Module-source provider returned a null response.");
        }
        Object rawVersion = response.get(KEY_VERSION);
        Object rawResponseId = response.get(KEY_REQUEST_ID);
        Object rawStatus = response.get(KEY_STATUS);
        Object rawResponseOperation = response.get(KEY_OPERATION);
        boolean hasSourceFd = response.containsKey(KEY_SOURCE_FD);
        Object rawSourceFd = hasSourceFd ? response.get(KEY_SOURCE_FD) : null;
        boolean hasSourceBytes = response.containsKey(KEY_SOURCE_BYTES);
        Object rawSourceBytes = hasSourceBytes ? response.get(KEY_SOURCE_BYTES) : null;
        try {
            validateProviderResponseRawTypes(
                    rawVersion,
                    rawResponseId,
                    rawResponseOperation,
                    rawStatus,
                    hasSourceFd,
                    rawSourceFd instanceof ParcelFileDescriptor,
                    hasSourceBytes,
                    rawSourceBytes
            );
        } catch (IOException error) {
            closeSourceFd(response);
            throw error;
        }
        int version = (Integer) rawVersion;
        String responseId = nonBlank((String) rawResponseId, "");
        String status = nonBlank((String) rawStatus, "");
        String responseOperation = nonBlank((String) rawResponseOperation, "");
        long declaredResponseSourceBytes = hasSourceBytes ? (Long) rawSourceBytes : 0L;
        String expectedOperation = materializationRequest
                ? NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT
                : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING;
        try {
            validateProviderResponseShape(
                    version,
                    responseId,
                    id,
                    responseOperation,
                    expectedOperation,
                    status,
                    hasSourceFd,
                    hasSourceBytes,
                    declaredResponseSourceBytes,
                    materializationRequest
            );
        } catch (IOException error) {
            closeSourceFd(response);
            throw error;
        }
        String resolvedPath = nonBlank(response.getString(KEY_RESOLVED_PATH), requestedPath);
        if (workspaceSession != null) {
            String mappedResolvedPath = workspaceSession.mapHostPathToRuntime(resolvedPath);
            if (!mappedResolvedPath.equals(resolvedPath)) {
                mappedResponsePathCount.incrementAndGet();
            }
            resolvedPath = mappedResolvedPath;
        }
        try {
            validatePositiveProviderResolvedPath(requestedPath, resolvedPath, status);
        } catch (IOException error) {
            closeSourceFd(response);
            throw error;
        }
        String errorCode = nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_CODE), "");
        String errorMessage = nonBlank(response.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), "");
        long providerElapsedMs = Math.max(0L, response.getLong(KEY_ELAPSED_MS, 0L));
        boolean pfdStatus = STATUS_DECRYPTED.equals(status) || STATUS_PLAINTEXT.equals(status);
        File sourceFile = null;
        long responseSourceBytes = 0L;
        String nativeStatus = status;
        if (pfdStatus) {
            long declaredBytes = declaredResponseSourceBytes;
            if (declaredBytes < 0L) {
                closeSourceFd(response);
                throw new IOException("PFD module-source byte count is invalid.");
            }
            if (declaredBytes > SINGLE_SOURCE_BYTES_LIMIT) {
                closeSourceFd(response);
                throw new BudgetExceededException("PFD module source exceeds the single-source byte budget.");
            }
            long aggregateBefore = sourceBytes.get();
            if (aggregateBefore > TOTAL_SOURCE_BYTES_LIMIT - declaredBytes) {
                closeSourceFd(response);
                throw new BudgetExceededException("PFD module sources exceed the aggregate byte budget.");
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
            rawSourceCount.incrementAndGet();
            sourceBytes.addAndGet(copiedBytes);
            responseSourceBytes = copiedBytes;
            if (STATUS_PLAINTEXT.equals(status)) {
                plaintextCount.incrementAndGet();
                try {
                    if (remainingMs(requestDeadline) <= 0L) {
                        throw new ProviderTimeoutException(
                                "Module-source provider deadline expired before workspace publication."
                        );
                    }
                    PluginWorkspaceArchiveSession.ProviderMaterialization materialization =
                            workspaceSession.materializeProviderSourceNoReplace(
                                    resolvedPath,
                                    sourceFile,
                                    copiedBytes,
                                    requestDeadline
                            );
                    if (!requestedPath.equals(materialization.runtimePath()) ||
                            copiedBytes != materialization.sourceBytes()) {
                        throw new IOException("Workspace plaintext materialization receipt is invalid.");
                    }
                } catch (Throwable error) {
                    materializationFailureCount.incrementAndGet();
                    sourceFile.delete();
                    if (error instanceof PluginWorkspaceArchiveSession
                            .ProviderMaterializationDeadlineExceededException) {
                        throw new ProviderTimeoutException(messageOf(error), error);
                    }
                    if (error instanceof Exception) {
                        throw (Exception) error;
                    }
                    throw new IOException(messageOf(error), error);
                }
                sourceFile.delete();
                sourceFile = null;
                materializedCount.incrementAndGet();
                materializedSourceBytes.addAndGet(copiedBytes);
                nativeStatus = STATUS_MATERIALIZED_PLAINTEXT;
            } else {
                prepareDecryptedTypeScriptSource(sourceFile, resolvedPath, copiedBytes);
                responseSourceBytes = sourceFile.length();
            }
        } else {
            closeSourceFd(response);
        }
        long totalElapsedMs = Math.max(elapsedSince(startedAt), providerElapsedMs);
        JSONObject responseJson = new JSONObject()
                .put("version", CONTRACT_VERSION)
                .put("id", id)
                .put("status", nativeStatus)
                .put("resolvedPath", resolvedPath)
                .put("sourcePath", sourceFile == null ? "" : sourceFile.getAbsolutePath())
                .put("sourceBytes", responseSourceBytes)
                .put("rawAlreadyAccounted", STATUS_MATERIALIZED_PLAINTEXT.equals(nativeStatus))
                .put("elapsedMs", totalElapsedMs)
                .put("errorCode", errorCode)
                .put("errorMessage", errorMessage);
        PendingPlaintextTypeScriptPreparation pending = null;
        if ((STATUS_NOT_ENCRYPTED.equals(nativeStatus) ||
                STATUS_MATERIALIZED_PLAINTEXT.equals(nativeStatus)) &&
                (NodeTypeScriptStripper.isTypeScriptSourceName(resolvedPath) ||
                        NodeTypeScriptStripper.isUnsupportedTypeScriptSourceName(resolvedPath))) {
            pending = new PendingPlaintextTypeScriptPreparation(
                    resolvedPath,
                    requestDeadline,
                    STATUS_MATERIALIZED_PLAINTEXT.equals(nativeStatus),
                    responseSourceBytes
            );
            if (running.get() && !stopped.get()) {
                pendingPlaintextPreparations.put(id, pending);
                if (!running.get() || stopped.get()) {
                    pendingPlaintextPreparations.remove(id, pending);
                }
            }
        }
        try {
            writeResponse(requestFile, responseJson, sourceFile, true);
        } catch (Throwable error) {
            if (pending != null) {
                pendingPlaintextPreparations.remove(id, pending);
            }
            throw error;
        }
    }

    static void validatePositiveProviderResolvedPath(
            String requestedPath,
            String resolvedPath,
            String status
    ) throws IOException {
        boolean positivePathBoundStatus = STATUS_DECRYPTED.equals(status) ||
                STATUS_NOT_ENCRYPTED.equals(status) ||
                STATUS_PLAINTEXT.equals(status);
        if (positivePathBoundStatus && !requestedPath.equals(resolvedPath)) {
            throw new IOException(
                    "Module-source provider positive response resolved path differs from its exact candidate."
            );
        }
    }

    /**
     * Applies plugin-owned TypeScript erasure only after the raw provider PFD
     * has passed its declared-byte and transport budgets. The Host/Binder
     * accounting therefore remains an intentionally stricter raw-input bound,
     * while the private response consumed by native code reports the prepared
     * UTF-8 byte count.
     */
    private void prepareDecryptedTypeScriptSource(
            File sourceFile,
            String resolvedPath,
            long rawBytes
    ) throws IOException {
        String sourceName = nonBlank(resolvedPath, sourceFile.getName());
        boolean typeScript = NodeTypeScriptStripper.isTypeScriptSourceName(sourceName) ||
                NodeTypeScriptStripper.isUnsupportedTypeScriptSourceName(sourceName);
        if (!typeScript) {
            recordPreparedSource(rawBytes);
            return;
        }

        typeScriptSourceCount.incrementAndGet();
        typeScriptInputBytes.addAndGet(rawBytes);
        try {
            byte[] rawSource = readSourceBytesBounded(sourceFile, rawBytes);
            PreparedTypeScriptSource preparation =
                    prepareDecryptedTypeScriptBytes(sourceName, rawSource);
            byte[] prepared = preparation.source();
            if (prepared.length > SINGLE_SOURCE_BYTES_LIMIT) {
                throw new BudgetExceededException(
                        "Prepared TypeScript module source exceeds the single-source byte budget."
                );
            }
            long preparedBefore = preparedSourceBytes.get();
            if (preparedBefore > TOTAL_SOURCE_BYTES_LIMIT - prepared.length) {
                throw new BudgetExceededException(
                        "Prepared TypeScript module sources exceed the aggregate byte budget."
                );
            }
            if (preparation.stripped()) {
                replaceSourceAtomically(sourceFile, prepared);
                typeScriptStrippedCount.incrementAndGet();
            }
            typeScriptOutputBytes.addAndGet(prepared.length);
            recordPreparedSource(prepared.length);
            recordTypeScriptSuccess(sourceName, preparation, prepared.length);
        } catch (NodeTypeScriptStripper.UnsupportedTypeScriptException error) {
            sourceFile.delete();
            throw error;
        } catch (BudgetExceededException error) {
            sourceFile.delete();
            recordTypeScriptFailure(
                    sourceName,
                    ERROR_BUDGET_EXCEEDED,
                    "budget_exceeded",
                    0,
                    0
            );
            throw error;
        } catch (CharacterCodingException error) {
            sourceFile.delete();
            recordTypeScriptFailure(sourceName, ERROR_FAILED, "invalid_utf8", 0, 0);
            throw new IOException("Decrypted TypeScript module source is not valid UTF-8.", error);
        } catch (IOException error) {
            sourceFile.delete();
            recordTypeScriptFailure(sourceName, ERROR_FAILED, "transport_io", 0, 0);
            throw error;
        }
    }

    private void recordPreparedSource(long bytes) throws BudgetExceededException {
        ensurePreparedSourceBudget(bytes);
        commitPreparedSource(bytes);
    }

    private void ensurePreparedSourceBudget(long bytes) throws BudgetExceededException {
        if (bytes < 0L || bytes > SINGLE_SOURCE_BYTES_LIMIT) {
            throw new BudgetExceededException("Prepared module source exceeds the single-source byte budget.");
        }
        long before = preparedSourceBytes.get();
        if (before > TOTAL_SOURCE_BYTES_LIMIT - bytes) {
            throw new BudgetExceededException("Prepared module sources exceed the aggregate byte budget.");
        }
    }

    private void commitPreparedSource(long bytes) {
        preparedSourceCount.incrementAndGet();
        preparedSourceBytes.addAndGet(bytes);
    }

    private void recordTypeScriptSuccess(
            String sourceName,
            PreparedTypeScriptSource preparation,
            long preparedBytes
    ) {
        lastTypeScriptStatus.set("prepared");
        lastTypeScriptSourceName.set(diagnosticSourceName(sourceName));
        lastTypeScriptExtension.set(nonBlank(
                preparation.diagnostics().get("embedded_script.typescript.extension"),
                sourceExtension(sourceName)
        ));
        lastTypeScriptStripped.set(Boolean.toString(preparation.stripped()));
        lastTypeScriptErrorCode.set("");
        lastTypeScriptSyntaxKind.set("");
        lastTypeScriptLine.set(0);
        lastTypeScriptColumn.set(0);
        if (preparedBytes < 0L) {
            throw new IllegalStateException("Prepared TypeScript byte count cannot be negative.");
        }
    }

    private void recordTypeScriptFailure(
            String sourceName,
            String errorCode,
            String syntaxKind,
            int line,
            int column
    ) {
        typeScriptFailureCount.incrementAndGet();
        lastTypeScriptStatus.set("failed");
        lastTypeScriptSourceName.set(diagnosticSourceName(sourceName));
        lastTypeScriptExtension.set(sourceExtension(sourceName));
        lastTypeScriptStripped.set("false");
        lastTypeScriptErrorCode.set(nonBlank(errorCode, ERROR_FAILED));
        lastTypeScriptSyntaxKind.set(nonBlank(syntaxKind, "unknown"));
        lastTypeScriptLine.set(Math.max(0, line));
        lastTypeScriptColumn.set(Math.max(0, column));
    }

    private static byte[] readSourceBytesBounded(File file, long expectedBytes) throws IOException {
        if (expectedBytes < 0L || expectedBytes > SINGLE_SOURCE_BYTES_LIMIT) {
            throw new BudgetExceededException("Decrypted TypeScript module source exceeds its byte budget.");
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedBytes)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long total = 0L;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > expectedBytes || total > SINGLE_SOURCE_BYTES_LIMIT) {
                    throw new IOException("Decrypted TypeScript module source changed before preparation.");
                }
                output.write(buffer, 0, count);
            }
            if (total != expectedBytes || file.length() != expectedBytes) {
                throw new IOException("Decrypted TypeScript module source byte count changed before preparation.");
            }
            return output.toByteArray();
        }
    }

    private byte[] readPrivatePreparationSource(
            File sourceFile,
            long expectedBytes,
            long deadline
    ) throws IOException {
        if (expectedBytes < 0L || expectedBytes > SINGLE_SOURCE_BYTES_LIMIT) {
            throw new BudgetExceededException(
                    "Plaintext TypeScript preparation source exceeds its byte budget."
            );
        }
        File canonicalRequestDirectory = requestDir.getCanonicalFile();
        File canonicalSource = sourceFile.getCanonicalFile();
        if (!canonicalRequestDirectory.equals(canonicalSource.getParentFile())) {
            throw new IOException("Plaintext TypeScript preparation source escapes its private request directory.");
        }

        android.system.StructStat rootBefore;
        android.system.StructStat before;
        try {
            rootBefore = Os.lstat(requestDir.getAbsolutePath());
            before = Os.lstat(sourceFile.getAbsolutePath());
        } catch (ErrnoException error) {
            throw new IOException("Could not inspect plaintext TypeScript preparation source.", error);
        }
        if (!OsConstants.S_ISDIR(rootBefore.st_mode) || OsConstants.S_ISLNK(rootBefore.st_mode) ||
                !OsConstants.S_ISREG(before.st_mode) || OsConstants.S_ISLNK(before.st_mode) ||
                before.st_size != expectedBytes) {
            throw new IOException("Plaintext TypeScript preparation source is not the expected regular file.");
        }

        java.io.FileDescriptor descriptor;
        try {
            descriptor = Os.open(
                    sourceFile.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    0
            );
        } catch (ErrnoException error) {
            throw new IOException("Could not open plaintext TypeScript preparation source.", error);
        }
        try (FileInputStream input = new FileInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedBytes)) {
            android.system.StructStat opened = Os.fstat(input.getFD());
            String openedPath = openedDescriptorPath(input.getFD());
            if (!OsConstants.S_ISREG(opened.st_mode) ||
                    opened.st_dev != before.st_dev || opened.st_ino != before.st_ino ||
                    opened.st_size != expectedBytes || openedPath == null ||
                    !canonicalSource.equals(new File(openedPath).getCanonicalFile())) {
                throw new IOException("Plaintext TypeScript preparation source changed during open.");
            }

            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long total = 0L;
            while (true) {
                if (stopped.get()) {
                    throw new IOException("Module-source provider transport is stopped.");
                }
                if (remainingMs(deadline) <= 0L) {
                    throw new ProviderTimeoutException("Plaintext TypeScript preparation read timed out.");
                }
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > expectedBytes || total > SINGLE_SOURCE_BYTES_LIMIT) {
                    throw new IOException("Plaintext TypeScript preparation source exceeds its exact byte count.");
                }
                output.write(buffer, 0, count);
            }
            android.system.StructStat completed = Os.fstat(input.getFD());
            android.system.StructStat after = Os.lstat(sourceFile.getAbsolutePath());
            android.system.StructStat rootAfter = Os.lstat(requestDir.getAbsolutePath());
            if (total != expectedBytes ||
                    completed.st_dev != before.st_dev || completed.st_ino != before.st_ino ||
                    completed.st_size != expectedBytes ||
                    after.st_dev != before.st_dev || after.st_ino != before.st_ino ||
                    after.st_size != expectedBytes ||
                    !OsConstants.S_ISDIR(rootAfter.st_mode) || OsConstants.S_ISLNK(rootAfter.st_mode) ||
                    rootAfter.st_dev != rootBefore.st_dev || rootAfter.st_ino != rootBefore.st_ino ||
                    !canonicalRequestDirectory.equals(requestDir.getCanonicalFile()) ||
                    !canonicalSource.equals(sourceFile.getCanonicalFile())) {
                throw new IOException("Plaintext TypeScript preparation source changed while reading.");
            }
            return output.toByteArray();
        } catch (ErrnoException error) {
            throw new IOException("Could not verify plaintext TypeScript preparation source.", error);
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

    private static String decodeStrictUtf8(byte[] source) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(source))
                .toString();
    }

    /**
     * Pure preparation seam shared by production transport and local JVM
     * conformance tests. Classification is deliberately based on the mapped
     * provider {@code resolvedPath}, never the private transport file name.
     */
    static PreparedTypeScriptSource prepareDecryptedTypeScriptBytes(
            String resolvedPath,
            byte[] rawSource
    ) throws CharacterCodingException {
        byte[] boundedRawSource = rawSource == null ? new byte[0] : rawSource;
        boolean typeScript = NodeTypeScriptStripper.isTypeScriptSourceName(resolvedPath) ||
                NodeTypeScriptStripper.isUnsupportedTypeScriptSourceName(resolvedPath);
        if (!typeScript) {
            return new PreparedTypeScriptSource(
                    boundedRawSource.clone(),
                    boundedRawSource.length,
                    false,
                    false,
                    java.util.Collections.emptyMap()
            );
        }
        String source = decodeStrictUtf8(boundedRawSource);
        NodeTypeScriptStripper.Result result =
                NodeTypeScriptStripper.stripIfTypeScript(resolvedPath, source);
        return new PreparedTypeScriptSource(
                result.source().getBytes(StandardCharsets.UTF_8),
                boundedRawSource.length,
                true,
                result.stripped(),
                result.diagnostics()
        );
    }

    private static void replaceSourceAtomically(File destination, byte[] source) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null) {
            throw new IOException("Prepared TypeScript module source has no parent directory.");
        }
        File temporary = new File(parent, destination.getName() + ".typescript.tmp");
        temporary.delete();
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(source);
                output.getFD().sync();
            }
            try {
                Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
            } catch (ErrnoException error) {
                throw new IOException("Could not publish prepared TypeScript module source.", error);
            }
        } finally {
            temporary.delete();
        }
    }

    static String diagnosticSourceName(String sourceName) {
        String normalized = nonBlank(sourceName, "unknown").replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (name.length() <= TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS) {
            return name;
        }
        int start = name.length() - TYPESCRIPT_DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS;
        if (start > 0 &&
                Character.isLowSurrogate(name.charAt(start)) &&
                Character.isHighSurrogate(name.charAt(start - 1))) {
            start++;
        }
        return name.substring(start);
    }

    private static String sourceExtension(String sourceName) {
        String normalized = nonBlank(sourceName, "").replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        int dot = normalized.lastIndexOf('.');
        return dot > slash && dot < normalized.length() - 1
                ? normalized.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)
                : "";
    }

    private static final class PendingPlaintextTypeScriptPreparation {
        private final String sourceName;
        private final long deadline;
        private final boolean rawAlreadyAccounted;
        private final long rawSourceBytes;

        private PendingPlaintextTypeScriptPreparation(
                String sourceName,
                long deadline,
                boolean rawAlreadyAccounted,
                long rawSourceBytes
        ) {
            this.sourceName = sourceName;
            this.deadline = deadline;
            this.rawAlreadyAccounted = rawAlreadyAccounted;
            this.rawSourceBytes = rawSourceBytes;
        }
    }

    static final class PreparedTypeScriptSource {
        private final byte[] source;
        private final long rawSourceBytes;
        private final boolean typeScript;
        private final boolean stripped;
        private final Map<String, String> diagnostics;

        private PreparedTypeScriptSource(
                byte[] source,
                long rawSourceBytes,
                boolean typeScript,
                boolean stripped,
                Map<String, String> diagnostics
        ) {
            this.source = source;
            this.rawSourceBytes = rawSourceBytes;
            this.typeScript = typeScript;
            this.stripped = stripped;
            this.diagnostics = diagnostics;
        }

        byte[] source() {
            return source.clone();
        }

        long sourceBytes() {
            return source.length;
        }

        long rawSourceBytes() {
            return rawSourceBytes;
        }

        boolean typeScript() {
            return typeScript;
        }

        boolean stripped() {
            return stripped;
        }

        Map<String, String> diagnostics() {
            return diagnostics;
        }
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

    private void writeResponse(
            File requestFile,
            JSONObject response,
            File sourceFile,
            boolean providerResponse
    ) throws IOException {
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
        record(response, providerResponse);
    }

    private void writeResponseQuietly(
            File requestFile,
            JSONObject response,
            boolean providerResponse
    ) {
        try {
            writeResponse(requestFile, response, null, providerResponse);
        } catch (Throwable ignored) {
            requestFile.delete();
            record(response, providerResponse);
        }
    }

    private void record(JSONObject response, boolean providerResponse) {
        String status = nonBlank(response.optString("status"), STATUS_FAILED);
        long responseElapsedMs = Math.max(0L, response.optLong("elapsedMs", 0L));
        transportElapsedMs.addAndGet(responseElapsedMs);
        if (!providerResponse) {
            return;
        }
        lastStatus.set(status);
        lastErrorCode.set(nonBlank(response.optString("errorCode"), ""));
        elapsedMs.addAndGet(responseElapsedMs);
        switch (status) {
            case STATUS_DECRYPTED:
                resolvedCount.incrementAndGet();
                decryptedCount.incrementAndGet();
                break;
            case STATUS_MATERIALIZED_PLAINTEXT:
                resolvedCount.incrementAndGet();
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
                STATUS_PLAINTEXT.equals(value) ||
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

    static final class RuntimeMetadataSnapshot {
        final String workingProjectJson;
        final String workingPackageJson;
        final String sandboxProjectJson;
        final String sandboxPackageJson;

        private RuntimeMetadataSnapshot(
                String workingProjectJson,
                String workingPackageJson,
                String sandboxProjectJson,
                String sandboxPackageJson
        ) {
            this.workingProjectJson = workingProjectJson;
            this.workingPackageJson = workingPackageJson;
            this.sandboxProjectJson = sandboxProjectJson;
            this.sandboxPackageJson = sandboxPackageJson;
        }

        static RuntimeMetadataSnapshot empty() {
            return new RuntimeMetadataSnapshot(null, null, null, null);
        }
    }

    static final class PolicyMetadataException extends IOException {
        private final String errorCode;

        PolicyMetadataException(String errorCode, String message) {
            super(message);
            this.errorCode = nonBlank(errorCode, ERROR_FAILED);
        }

        PolicyMetadataException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = nonBlank(errorCode, ERROR_FAILED);
        }

        String errorCode() {
            return errorCode;
        }
    }

    private static final class MetadataProviderResponse {
        final String id;
        final String status;
        final String resolvedPath;
        final long sourceBytes;
        final long elapsedMs;
        final String errorCode;
        final String errorMessage;
        final Bundle response;

        private MetadataProviderResponse(
                String id,
                String status,
                String resolvedPath,
                long sourceBytes,
                long elapsedMs,
                String errorCode,
                String errorMessage,
                Bundle response
        ) {
            this.id = id;
            this.status = status;
            this.resolvedPath = resolvedPath;
            this.sourceBytes = sourceBytes;
            this.elapsedMs = elapsedMs;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.response = response;
        }
    }

    private static final class MetadataPreflightReplay {
        final String status;
        final String resolvedPath;
        private final byte[] source;

        private MetadataPreflightReplay(String status, String resolvedPath, byte[] source) {
            this.status = status;
            this.resolvedPath = resolvedPath;
            this.source = source == null ? new byte[0] : source.clone();
        }

        byte[] source() {
            return source.clone();
        }
    }

    private static final class ProviderTimeoutException extends IOException {
        ProviderTimeoutException(String message) {
            super(message);
        }

        ProviderTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class BudgetExceededException extends IOException {
        BudgetExceededException(String message) {
            super(message);
        }
    }
}

package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.SystemClock;

import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.*;

/**
 * Method object for a single runScript invocation: the full pre-native
 * preparation (compiler-output-only TypeScript admission, workspace,
 * provider/broker transports,
 * runtime-module injection), the native dispatch, and the result/failure
 * envelope assembly. State that outlives one call stays on the service.
 */
final class NodePluginScriptExecution {

    private final NodeJsRuntimePluginService service;

    NodePluginScriptExecution(NodeJsRuntimePluginService service) {
        this.service = service;
    }

    Bundle run(Bundle request, INodeJsRuntimeCallback callback) {
        long startedAt = SystemClock.elapsedRealtime();
        INodeJsHostCapabilityBroker hostBroker = null;
        INodeJsModuleSourceProvider moduleSourceProvider = null;
        PluginNodeBridgeFileTransportSession liveBridgeSession = null;
        PluginModuleSourceProviderFileTransportSession moduleSourceProviderSession = null;
        PluginWorkspaceArchiveSession workspaceSession = null;
        boolean nativeDispatchStarted = false;
        NodeJsRuntimePluginService.StreamingOutputSink streamSink = null;
        boolean typeScriptPrecompiledSnapshot = request.getBoolean(
                NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SNAPSHOT,
                false
        );
        Set<String> typeScriptPrecompiledSourceNames = stringSetFromArray(
                request.getStringArray(NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SOURCE_NAMES)
        );
        notifyEvent(callback, NodeJsRuntimeContract.EVENT_STARTED, null, null);
        try {
            // Acquire the request-scoped provider before any operation that can
            // fail so the finally block can release it even for an empty source
            // or a native-library loading failure.
            moduleSourceProvider = moduleSourceProviderFrom(request);
            NodeJsRuntimePluginService.RuntimeReadiness readiness = service.ensurePersistentRuntimeReady();
            if (!readiness.ready) {
                Bundle failure = service.bundles.failureBundle(
                        request,
                        startedAt,
                        "Node.js persistent process runtime is unavailable: " + readiness.detail,
                        null,
                        NodeJsRuntimePluginService.ERROR_UNAVAILABLE
                );
                appendPredispatchNoCommitReceipt(failure);
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                readiness.nativePayload()
                        )
                );
                notifyOutput(callback, failure);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
                return failure;
            }
            String source = request.getString(NodeJsRuntimeContract.KEY_SOURCE, "");
            if (source.isEmpty()) {
                Bundle failure = service.bundles.failureBundle(
                        request,
                        startedAt,
                        "Node.js runtime request source is empty.",
                        null,
                        "ERR_AUTOJS6_NODE_PLUGIN_EMPTY_SOURCE"
                );
                appendPredispatchNoCommitReceipt(failure);
                notifyOutput(callback, failure);
                notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
                return failure;
            }
            String requestedSourceName = nonBlank(
                    request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME),
                    NodeJsRuntimePluginService.DEFAULT_SOURCE_NAME
            );
            // Entry preparation stays ahead of workspace creation: canonical
            // TypeScript rejections must prove no workspace was materialized
            // (x3e negatives). Hosts recognize such pre-dispatch failures by
            // the unconditional commit_allowed=false marker below.
            source = NodeJsRuntimePluginService.prepareTypeScriptEntryForNative(
                    requestedSourceName,
                    source
            );
            workspaceSession = PluginWorkspaceArchiveSession.hasWorkspaceDescriptors(request)
                    ? PluginWorkspaceArchiveSession.open(service.getCacheDir(), request)
                    : PluginWorkspaceArchiveSession.openDirect(service.getCacheDir(), request);
            String sourceName = workspaceSession.mapHostPathToRuntime(requestedSourceName);
            String workingDirectory = workspaceSession.workingDirectory();
            String sandboxRoot = workspaceSession.sandboxRoot();
            typeScriptPrecompiledSourceNames = workspaceSession.mapHostPathsToRuntime(
                    typeScriptPrecompiledSourceNames
            );
            Map<String, String> moduleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCES)
            );
            moduleSources = NodeJsRuntimePluginService.prepareTypeScriptModuleSourcesForNative(
                    moduleSources
            );
            moduleSources = workspaceSession.mapModuleSourceNames(moduleSources);
            Map<String, String> runtimeModuleSources = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES)
            );
            Map<String, String> env = stringMapFromArrays(
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_NAMES),
                    request.getStringArray(NodeJsRuntimeContract.KEY_ENV_VALUES)
            );
            env = workspaceSession.mapEnvironment(env);
            boolean permissionMetadataRequired = nonBlank(
                    runtimeModuleSources.get(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME),
                    null
            ) == null;
            boolean bridgeLimitMetadataRequired = nonBlank(
                    runtimeModuleSources.get(PluginNodeBridgeFileTransportSession.BRIDGE_LIMITS_RUNTIME_MODULE_NAME),
                    null
            ) == null;
            if (moduleSourceProvider != null) {
                moduleSourceProviderSession = new PluginModuleSourceProviderFileTransportSession(
                        service.getCacheDir(),
                        request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID),
                        moduleSourceProvider,
                        request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L),
                        workspaceSession,
                        NodeJsRuntimePluginService.initialModuleSourceProviderWireVersion(
                                request.get(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION)
                        )
                );
            }
            PluginModuleSourceProviderFileTransportSession.RuntimeMetadataSnapshot runtimeMetadata =
                    moduleSourceProviderSession == null
                            ? PluginModuleSourceProviderFileTransportSession.readPolicyMetadataWithoutProvider(
                                    workspaceSession,
                                    workingDirectory,
                                    sandboxRoot,
                                    permissionMetadataRequired,
                                    bridgeLimitMetadataRequired,
                                    request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L),
                                    startedAt
                            )
                            : moduleSourceProviderSession.readPolicyMetadataSnapshot(
                                    workingDirectory,
                                    sandboxRoot,
                                    permissionMetadataRequired,
                                    bridgeLimitMetadataRequired,
                                    startedAt
                            );
            hostBroker = hostBrokerFrom(request);
            Bundle hostBrokerInfo = hostBrokerInfo(hostBroker);
            NodeRuntimeModuleInjector.RuntimeModuleInjection runtimeModuleInjection = service.moduleInjector.withPluginRuntimeModules(
                    runtimeModuleSources,
                    request,
                    hostBrokerInfo,
                    workingDirectory,
                    workspaceSession,
                    runtimeMetadata
            );
            runtimeModuleSources = runtimeModuleInjection.sources;
            if (hostBroker != null) {
                liveBridgeSession = new PluginNodeBridgeFileTransportSession(
                        service.getCacheDir(),
                        request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID),
                        hostBroker,
                        PluginNodeBridgeFileTransportSession.maxPendingBridgeCallsFromRuntimeModule(runtimeModuleSources)
                );
                runtimeModuleSources = withRuntimeModuleSource(
                        runtimeModuleSources,
                        PluginNodeBridgeFileTransportSession.RUNTIME_MODULE_NAME,
                        liveBridgeSession.configJson()
                );
                liveBridgeSession.start();
            }
            if (moduleSourceProvider != null) {
                runtimeModuleSources = withRuntimeModuleSource(
                        runtimeModuleSources,
                        PluginModuleSourceProviderFileTransportSession.RUNTIME_MODULE_NAME,
                        moduleSourceProviderSession.configJson()
                );
                moduleSourceProviderSession.start();
            }

            runtimeModuleSources =
                    NodeJsRuntimePluginService.prepareTypeScriptRuntimeModuleSourcesForNative(
                            runtimeModuleSources
                    );
            NodeStartupEnvironmentPolicy.Result startupEnvironment =
                    NodeJsRuntimePluginService.prepareNodeStartupEnvironmentForNative(env);
            env = startupEnvironment.environment();

            long nativeCallStartedAt = SystemClock.elapsedRealtime();
            nativeDispatchStarted = true;
            streamSink = service.installOutputStreamSink(callback);
            String[] nativePayload;
            try {
                nativePayload = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScript(
                        source,
                        sourceName,
                        workingDirectory,
                        sandboxRoot,
                        moduleSources,
                        runtimeModuleSources,
                        env,
                        typeScriptPrecompiledSnapshot,
                        typeScriptPrecompiledSourceNames,
                        request.getBoolean(NodeJsRuntimeContract.KEY_ESM_ENABLED, true),
                        request.getBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_ENABLED, true),
                        // Stable capabilities default on. The host still sends
                        // explicit values so diagnostics and test fixtures can
                        // exercise policy-denied paths without build flags.
                        request.getBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, true),
                        BuildConfig.DEBUG && request.getBoolean(
                                NodeJsRuntimeContract.KEY_INSPECTOR_ENABLED,
                                false
                        ),
                        request.getBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_ENABLED, true),
                        request.getBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_ENABLED, true),
                        request.getBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_ENABLED, true)
                );
            } finally {
                if (streamSink != null) {
                    service.clearOutputStreamSink();
                }
            }
            nativePayload = appendNativePayload(
                    nativePayload,
                    nativePayloadFromMap(startupEnvironment.diagnostics())
            );
            long nativeCallFinishedAt = SystemClock.elapsedRealtime();
            service.commitWorkspaceIfProcessStable(workspaceSession);
            long workspaceCommitFinishedAt = SystemClock.elapsedRealtime();
            nativePayload = appendNativePayload(nativePayload, workspaceSession.nativePayload());
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            long liveBridgeStopFinishedAt = SystemClock.elapsedRealtime();
            String[] moduleSourceProviderNativePayload = moduleSourceProviderSession == null
                    ? new String[]{"embedded_script.runtime_plugin.module_provider.available=false"}
                    : moduleSourceProviderSession.providerNativePayload();
            if (moduleSourceProviderSession != null) {
                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished.");
            }
            String[] liveBridgePayload = liveBridgePayload(liveBridgeSession);
            String[] moduleSourceProviderPayload = moduleSourceProviderSession == null
                    ? new String[]{"embedded_script.runtime_plugin.module_provider.available=false"}
                    : moduleSourceProviderSession.nativePayload();
            String[] queuedBridgePayload = dispatchQueuedBridgeRequests(nativePayload, hostBroker);
            boolean hostBrokerWasUsed =
                    (liveBridgeSession != null && liveBridgeSession.hasDispatchedCalls()) ||
                            queuedBridgePayload.length > 0;
            String[] hostBrokerDiagnosticsPayload = hostBrokerNativeDiagnosticsPayload(
                    hostBroker,
                    hostBrokerInfo,
                    hostBrokerWasUsed
            );
            String[] runtimeModulePayload = runtimeModuleInjection.nativePayload();
            String[] runtimePluginPayload = service.bundles.runtimePluginPayload(hostBroker);
            long diagnosticsFinishedAt = SystemClock.elapsedRealtime();
            LinkedHashMap<String, String> serviceTiming = new LinkedHashMap<>();
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.pre_native.ms",
                    Long.toString(Math.max(0L, nativeCallStartedAt - startedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.native_call.ms",
                    Long.toString(Math.max(0L, nativeCallFinishedAt - nativeCallStartedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.workspace_commit.ms",
                    Long.toString(Math.max(0L, workspaceCommitFinishedAt - nativeCallFinishedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.live_bridge_stop.ms",
                    Long.toString(Math.max(0L, liveBridgeStopFinishedAt - workspaceCommitFinishedAt))
            );
            serviceTiming.put(
                    "embedded_script.runtime_plugin.timing.diagnostics.ms",
                    Long.toString(Math.max(0L, diagnosticsFinishedAt - liveBridgeStopFinishedAt))
            );
            String[] serviceTimingPayload = nativePayloadFromMap(serviceTiming);
            Bundle result = service.bundles.resultBundleFromNativePayload(
                    request,
                    workspaceSession.mapRuntimePathToHost(sourceName),
                    appendNativePayload(
                            appendNativePayload(
                                    appendNativePayload(
                                            appendNativePayload(
                                                    appendNativePayload(nativePayload, liveBridgePayload),
                                                    hostBrokerPayload(hostBroker, hostBrokerInfo)
                                            ),
                                            runtimeModulePayload
                                    ),
                                    queuedBridgePayload
                            ),
                            appendNativePayload(
                                    appendNativePayload(
                                            appendNativePayload(hostBrokerDiagnosticsPayload, runtimePluginPayload),
                                            appendNativePayload(moduleSourceProviderPayload, moduleSourceProviderNativePayload)
                                    ),
                                    serviceTimingPayload
                            )
                    ),
                    startedAt
            );
            result.putString(
                    NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,
                    request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY)
            );
            // Streamed executions already delivered stdout/stderr chunk by
            // chunk; replaying the aggregate here would double the output. If
            // the sink never fired (fd capture unavailable), fall back to the
            // pre-M2.1 terminal replay so callback callers still see output.
            if (streamSink == null || !streamSink.deliveredAnything()) {
                notifyOutput(callback, result);
            }
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return result;
        } catch (Throwable error) {
            // Policy-metadata/provider failures occur before native dispatch. Do not
            // serialize a private workspace in that state: materialized plaintext
            // remains private and the caller receives no partial output archive.
            if (NodeJsRuntimePluginService.shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)) {
                service.commitWorkspaceQuietly(workspaceSession);
            }
            String failureErrorCode = NodeJsRuntimePluginService.ERROR_UNAVAILABLE;
            String[] typeScriptFailurePayload = new String[0];
            if (error instanceof NodeTypeScriptSourcePolicy.CompilerRequiredException) {
                NodeTypeScriptSourcePolicy.CompilerRequiredException typeScriptError =
                        (NodeTypeScriptSourcePolicy.CompilerRequiredException) error;
                failureErrorCode = typeScriptError.errorCode();
                typeScriptFailurePayload = nativePayloadFromMap(typeScriptError.diagnostics());
            } else if (error instanceof PluginModuleSourceProviderFileTransportSession.PolicyMetadataException) {
                failureErrorCode = ((PluginModuleSourceProviderFileTransportSession.PolicyMetadataException) error)
                        .errorCode();
            }
            // The message is the user-facing "one readable sentence"; the
            // machine identity travels in KEY_ERROR_CODE. A boilerplate
            // "plugin execution failed:" prefix only pushed real messages
            // past readable length (M1.5).
            Bundle failure = service.bundles.failureBundle(
                    request,
                    startedAt,
                    messageOf(error),
                    error,
                    failureErrorCode
            );
            failure.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    appendNativePayload(
                            failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                            typeScriptFailurePayload
                    )
            );
            if (workspaceSession != null) {
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                workspaceSession.nativePayload()
                        )
                );
            }
            failure.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    appendNativePayload(
                            failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                            new String[]{
                                    "embedded_script.runtime_plugin.native_dispatch_started=" +
                                            nativeDispatchStarted,
                                    "embedded_script.runtime_plugin.workspace.commit_allowed=" +
                                            nativeDispatchStarted,
                                    "embedded_script.runtime_plugin.workspace.predispatch_private_source_exported=false"
                            }
                    )
            );
            if (moduleSourceProviderSession != null) {
                failure.putStringArray(
                        NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                moduleSourceProviderSession.nativePayload()
                        )
                );
            }
            if (streamSink == null || !streamSink.deliveredAnything()) {
                notifyOutput(callback, failure);
            }
            notifyEvent(callback, NodeJsRuntimeContract.EVENT_FINISHED, null, null);
            return failure;
        } finally {
            if (NodeJsRuntimePluginService.shouldCommitWorkspaceAfterFailure(nativeDispatchStarted)) {
                service.commitWorkspaceQuietly(workspaceSession);
            }
            if (moduleSourceProviderSession != null) {
                moduleSourceProviderSession.stop("Node.js runtime plugin execution finished after failure.");
            } else {
                cancelModuleSourceProvider(
                        moduleSourceProvider,
                        "Node.js runtime plugin execution finished before module-source transport startup."
                );
            }
            if (workspaceSession != null) {
                workspaceSession.close();
            }
            if (liveBridgeSession != null) {
                liveBridgeSession.stop();
            }
            destroyHostBroker(hostBroker, "Node.js runtime plugin execution finished.");
        }
    }

    private static Set<String> stringSetFromArray(String[] values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}

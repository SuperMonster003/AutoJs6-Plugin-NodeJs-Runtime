package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.supermonster003.autojs6.plugin.nodejs.NodePluginPayloads.*;

/**
 * Assembles the runtimeInfo / prewarm / result / failure Bundles the binder
 * hands back to hosts. Reads service state (readiness, execution gate,
 * restart flag) through the service reference; writes nothing back.
 */
final class NodePluginBundles {

    private final NodeJsRuntimePluginService service;

    NodePluginBundles(NodeJsRuntimePluginService service) {
        this.service = service;
    }

    Bundle runtimeInfoBundle() {
        NodeJsRuntimePluginService.RuntimeReadiness readiness = service.lastRuntimeReadiness;
        NodeRuntimeExecutionGate.Snapshot activeExecution = service.executionGate.snapshot();
        Bundle info = new Bundle();
        info.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_5);
        info.putString(NodeJsRuntimeContract.KEY_NODE_VERSION, NodeJsRuntimePluginService.NODE_VERSION);
        info.putString(NodeJsRuntimeContract.KEY_NATIVE_LIBRARY_NAME, NodeJsRuntimePluginService.NATIVE_LIBRARY_NAME);
        info.putString(NodeJsRuntimeContract.KEY_BRIDGE_LIBRARY_NAME, NodeJsRuntimePluginService.BRIDGE_LIBRARY_NAME);
        info.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);
        info.putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, NodeJsRuntimePluginService.CAPABILITIES.clone());
        info.putString(NodeJsRuntimePluginService.KEY_NODE_CAPABILITY_CATALOG_SCHEMA, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SCHEMA);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_CAPABILITY_CATALOG_VERSION, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_VERSION);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_CAPABILITY_CATALOG_SHA256, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SHA256);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_RUNTIME_KIT_SCHEMA, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SCHEMA);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_RUNTIME_KIT_VERSION, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
        info.putString(NodeJsRuntimePluginService.KEY_NODE_RUNTIME_KIT_ID, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_ID);
        info.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        info.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        info.putBoolean("runtimeReady", readiness.ready);
        info.putString("runtimeReadinessDetail", readiness.detail);
        info.putString("processAbi", processAbi());
        info.putStringArray("supportedAbis", NodeJsRuntimePluginService.SUPPORTED_ABIS.clone());
        info.putString("processModel", "persistent");
        info.putInt("maxConcurrentExecutions", 1);
        info.putInt("queueCapacity", NodeRuntimeExecutionGate.QUEUE_CAPACITY);
        info.putInt("queuedExecutions", service.executionGate.queuedCount());
        info.putBoolean("persistentProcessRuntime", true);
        info.putBoolean("dedicatedRuntimeProcess", service.isDedicatedRuntimeProcess());
        info.putBoolean("isolatePerExecution", true);
        info.putString("defaultExecutionMode", "one_shot");
        info.putString("cancellationMode", NodeJsRuntimePluginService.CANCELLATION_STRATEGY_COOPERATIVE_STOP);
        info.putString("outputMode", "streaming");
        info.putBoolean("streamingOutput", true);
        info.putInt("terminalEventCount", 1);
        info.putString(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID,
                activeExecution == null ? "" : activeExecution.executionId
        );
        info.putLong(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_FOR_MS,
                activeExecution == null ? 0L : activeExecution.activeForMs
        );
        info.putBoolean(
                NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_CANCELLATION_REQUESTED,
                activeExecution != null && activeExecution.cancellationRequested
        );
        info.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(readiness.nativePayload(), runtimeProcessDiagnosticsPayload())
        );
        return info;
    }

    Bundle requestContractFailureBundle(
            Bundle request,
            long startedAt,
            String diagnosticScope,
            String message,
            int receivedVersion,
            int expectedVersion
    ) {
        Bundle failure = failureBundle(
                request,
                startedAt,
                message,
                null,
                NodeJsRuntimePluginService.ERROR_CONTRACT_MISMATCH
        );
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        String diagnosticPrefix = "embedded_script.runtime_plugin." + diagnosticScope;
        diagnostics.put(diagnosticPrefix + ".status", "rejected");
        diagnostics.put(diagnosticPrefix + ".received_version", Integer.toString(receivedVersion));
        diagnostics.put(diagnosticPrefix + ".expected_version", Integer.toString(expectedVersion));
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                service.lastRuntimeReadiness.nativePayload()
                        ),
                        nativePayloadFromMap(diagnostics)
                )
        );
        return failure;
    }

    Bundle admissionFailureBundle(
            Bundle request,
            long startedAt,
            NodeRuntimeExecutionGate.Admission admission
    ) {
        String message;
        String admissionStatus;
        switch (admission.outcome) {
            case QUEUE_FULL:
                message = "Node.js runtime plugin queue is full (" +
                        NodeRuntimeExecutionGate.QUEUE_CAPACITY + " scripts already waiting).";
                admissionStatus = "queue_full";
                break;
            case WAIT_TIMEOUT:
                message = "Node.js runtime plugin queue wait exceeded " + admission.waitedMs + " ms.";
                admissionStatus = "wait_timeout";
                break;
            case CANCELLED_WHILE_QUEUED:
                message = "Script was cancelled while waiting in the queue.";
                admissionStatus = "cancelled_while_queued";
                break;
            case CLOSED:
            default:
                message = "Node.js runtime plugin process is restarting after cancellation.";
                admissionStatus = "draining";
                break;
        }
        long timeoutMs = request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L);
        boolean totalBudgetTimedOut =
                admission.outcome == NodeRuntimeExecutionGate.AdmissionOutcome.WAIT_TIMEOUT &&
                        timeoutMs > 0L;
        String errorCode = totalBudgetTimedOut
                ? NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT
                : admission.outcome == NodeRuntimeExecutionGate.AdmissionOutcome.CANCELLED_WHILE_QUEUED
                        ? NodeJsRuntimePluginService.ERROR_SCRIPT_CANCELLED
                        : NodeJsRuntimePluginService.ERROR_BUSY;
        Bundle failure = totalBudgetTimedOut
                ? timeoutFailureBundle(request, startedAt, timeoutMs, "queue_wait")
                : failureBundle(request, startedAt, message, null, errorCode);
        NodeRuntimeExecutionGate.Snapshot active = service.executionGate.snapshot();
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("embedded_script.runtime_plugin.admission.status", admissionStatus);
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.queue_capacity",
                Integer.toString(NodeRuntimeExecutionGate.QUEUE_CAPACITY)
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.queued_count",
                Integer.toString(service.executionGate.queuedCount())
        );
        diagnostics.put("embedded_script.runtime_plugin.admission.waited_ms", Long.toString(admission.waitedMs));
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.active_execution_id",
                active == null ? "" : active.executionId
        );
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                        nativePayloadFromMap(diagnostics)
                )
        );
        return failure;
    }

    Bundle timeoutFailureBundle(
            Bundle request,
            long startedAt,
            long timeoutMs,
            String phase
    ) {
        Bundle failure = failureBundle(
                request,
                startedAt,
                timeoutMessage(timeoutMs),
                null,
                NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT
        );
        markResultTimedOut(failure, timeoutMs, elapsedSince(startedAt), phase);
        return failure;
    }

    void markResultTimedOut(Bundle result, long timeoutMs, long elapsedMs, String phase) {
        if (result == null) {
            return;
        }
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, 1);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_NAME, "NodeJsRuntimePluginTimeoutError");
        result.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, timeoutMessage(timeoutMs));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_STACK, null);
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_CODE,
                NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT
        );
        result.putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, true);
        result.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs);
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedMs);
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                        new String[]{
                                "embedded_script.timed_out=true",
                                "embedded_script.error_code=" + NodeJsRuntimePluginService.ERROR_SCRIPT_TIMEOUT,
                                "embedded_script.runtime_plugin.timed_out=true",
                                "embedded_script.runtime_plugin.timeout_ms=" + timeoutMs,
                                "embedded_script.runtime_plugin.timeout_phase=" + phase
                        }
                )
        );
    }

    private static String timeoutMessage(long timeoutMs) {
        return "Node.js script exceeded its " + timeoutMs + " ms wall-clock budget.";
    }

    Bundle busyFailureBundle(Bundle request, long startedAt) {
        NodeRuntimeExecutionGate.Snapshot active = service.executionGate.snapshot();
        boolean draining = service.executionGate.isClosed() || service.processRestartScheduled.get();
        String activeExecutionId = active == null ? "" : active.executionId;
        String message = draining
                ? "Node.js runtime plugin process is restarting after cancellation."
                : "Node.js runtime plugin is busy with execution " + activeExecutionId + ".";
        Bundle failure = failureBundle(request, startedAt, message, null, NodeJsRuntimePluginService.ERROR_BUSY);
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.status",
                draining ? "draining" : "busy"
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.queue_capacity",
                Integer.toString(NodeRuntimeExecutionGate.QUEUE_CAPACITY)
        );
        diagnostics.put("embedded_script.runtime_plugin.admission.active_execution_id", activeExecutionId);
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.active_for_ms",
                Long.toString(active == null ? 0L : active.activeForMs)
        );
        diagnostics.put(
                "embedded_script.runtime_plugin.admission.cancellation_requested",
                Boolean.toString(active != null && active.cancellationRequested)
        );
        failure.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(
                                failure.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD),
                                service.lastRuntimeReadiness.nativePayload()
                        ),
                        nativePayloadFromMap(diagnostics)
                )
        );
        return failure;
    }

    Bundle prewarmRuntimeBundle(NodeJsRuntimePluginService.RuntimeReadiness readiness, long startedAt) {
        String status = readiness.ready ? "ready" : "failed";
        String reason = readiness.ready ? "runtime_ready" : readiness.detail;
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("embedded_script.prewarm_pool.enabled", "true");
        diagnostics.put("embedded_script.prewarm_pool.status", status);
        diagnostics.put("embedded_script.prewarm_pool.started", Boolean.toString(readiness.ready));
        diagnostics.put("embedded_script.prewarm_pool.process_only", "false");
        diagnostics.put(
                "embedded_script.prewarm_pool.runtime_loaded_before_execution",
                Boolean.toString(readiness.ready)
        );
        diagnostics.put("embedded_script.prewarm_pool.reason", reason);
        diagnostics.put("embedded_script.prewarm_pool.policy", "persistent-process-runtime");

        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, readiness.ready);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, readiness.ready ? 0 : 1);
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_NAME,
                readiness.ready ? null : "NodeJsRuntimePluginPrewarmError"
        );
        result.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, readiness.ready ? null : readiness.detail);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, readiness.ready ? null : NodeJsRuntimePluginService.ERROR_UNAVAILABLE);
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putBoolean("started", readiness.ready);
        result.putString("status", status);
        result.putString("reason", reason);
        result.putBoolean("processOnly", false);
        result.putBoolean("runtimeLoaded", readiness.ready);
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(
                        appendNativePayload(readiness.nativePayload(), nativePayloadFromMap(diagnostics)),
                        runtimeProcessDiagnosticsPayload()
                )
        );
        return result;
    }

    String[] runtimePluginPayload(INodeJsHostCapabilityBroker hostBroker) {
        NodeJsRuntimePluginService.RuntimeReadiness readiness = service.lastRuntimeReadiness;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("embedded_script.runtime_plugin.enabled", "true");
        values.put("embedded_script.runtime_plugin.diagnostics_source", "plugin");
        values.put("embedded_script.runtime_plugin.contract_version", Integer.toString(NodeJsRuntimeContract.CONTRACT_VERSION));
        values.put("embedded_script.runtime_plugin.package_name", service.getPackageName());
        values.put("embedded_script.runtime_plugin.service_name", service.getClass().getName());
        values.put("embedded_script.runtime_plugin.package_version_code", Long.toString(service.packageVersionCode()));
        values.put("embedded_script.runtime_plugin.runtime_slot", NodeJsPluginIds.VARIANT_NODE_24_5);
        values.put("embedded_script.runtime_plugin.node_version", NodeJsRuntimePluginService.NODE_VERSION);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SCHEMA, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SCHEMA);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_CAPABILITY_CATALOG_VERSION, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_VERSION);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_CAPABILITY_CATALOG_SHA256, NodeJsRuntimePluginService.NODE_CAPABILITY_CATALOG_SHA256);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_RUNTIME_KIT_SCHEMA, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SCHEMA);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_RUNTIME_KIT_VERSION, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_RUNTIME_KIT_SHA256, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
        values.put(NodeJsRuntimePluginService.DIAGNOSTIC_NODE_RUNTIME_KIT_ID, BuildConfig.NODE_PLUGIN_RUNTIME_KIT_ID);
        values.put("embedded_script.runtime_plugin.capability_count", Integer.toString(NodeJsRuntimePluginService.CAPABILITIES.length));
        values.put(
                "embedded_script.runtime_plugin.capability_host_broker",
                Boolean.toString(service.hasCapability(NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER))
        );
        values.put(
                "embedded_script.runtime_plugin.capability_live_bridge",
                Boolean.toString(service.hasCapability(NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE))
        );
        values.put(
                "embedded_script.runtime_plugin.capability_module_source_provider",
                Boolean.toString(service.hasCapability(NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER))
        );
        values.put("embedded_script.runtime_plugin.process_name", currentProcessName());
        values.put("embedded_script.runtime_plugin.pid", Integer.toString(Process.myPid()));
        values.put(
                "embedded_script.runtime_plugin.dedicated_process",
                Boolean.toString(service.isDedicatedRuntimeProcess())
        );
        values.put("embedded_script.runtime_plugin.persistent_process_runtime", "true");
        values.put("embedded_script.runtime_plugin.max_concurrent_executions", "1");
        values.put(
                "embedded_script.runtime_plugin.queue_capacity",
                Integer.toString(NodeRuntimeExecutionGate.QUEUE_CAPACITY)
        );
        values.put(
                "embedded_script.runtime_plugin.cancellation_mode",
                NodeJsRuntimePluginService.CANCELLATION_STRATEGY_COOPERATIVE_STOP
        );
        values.put(
                "embedded_script.runtime_plugin.runtime_ready",
                Boolean.toString(readiness.ready)
        );
        values.put("embedded_script.runtime_plugin.runtime_readiness_detail", readiness.detail);
        values.put("embedded_script.runtime_plugin.host_broker.attached", Boolean.toString(hostBroker != null));
        return nativePayloadFromMap(values);
    }

    Bundle resultBundleFromNativePayload(
            Bundle request,
            String sourceName,
            String[] nativePayload,
            long startedAt
    ) {
        Map<String, String> nativeValues = parseNativePayload(nativePayload);
        String status = stringValue(nativeValues, "embedded_script.status");
        boolean nativeFailure = "failed".equals(status) || "skipped".equals(status);
        boolean scriptSucceeded = booleanValue(nativeValues, "embedded_script.succeeded");
        boolean succeeded = !nativeFailure && scriptSucceeded;

        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID));
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, succeeded);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, intValue(nativeValues, "embedded_script.exit_code", succeeded ? 0 : 1));
        result.putString(NodeJsRuntimeContract.KEY_RESULT_TEXT, emptyToNull(stringValue(nativeValues, "embedded_script.result_text")));
        result.putString(NodeJsRuntimeContract.KEY_STDOUT, preferMoreComplete(
                stringValue(nativeValues, "embedded_script.stdout"),
                captureText(nativeValues, "stdout_capture")
        ));
        result.putString(NodeJsRuntimeContract.KEY_STDERR, preferMoreComplete(
                stringValue(nativeValues, "embedded_script.stderr"),
                captureText(nativeValues, "stderr_capture")
        ));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_NAME, emptyToNull(stringValue(nativeValues, "embedded_script.error_name")));
        result.putString(
                NodeJsRuntimeContract.KEY_ERROR_MESSAGE,
                emptyToNull(nativeFailure
                        ? nonBlank(stringValue(nativeValues, "embedded_script.detail"), "Embedded script native lifecycle failed.")
                        : stringValue(nativeValues, "embedded_script.error_message"))
        );
        result.putString(NodeJsRuntimeContract.KEY_ERROR_STACK, emptyToNull(stringValue(nativeValues, "embedded_script.error_stack")));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, emptyToNull(stringValue(nativeValues, "embedded_script.error_code")));
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, longValue(nativeValues, "timing.total.ms", elapsedSince(startedAt)));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, sourceName);
        result.putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, booleanValue(nativeValues, "embedded_script.timed_out"));
        result.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, emptyToNull(
                nonBlank(stringValue(nativeValues, "embedded_script.working_directory"),
                        request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY))
        ));
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(nativePayload, runtimeProcessDiagnosticsPayload())
        );
        return result;
    }

    Bundle failureBundle(
            Bundle request,
            long startedAt,
            String message,
            Throwable error,
            String errorCode
    ) {
        Bundle result = new Bundle();
        result.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        result.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID));
        result.putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false);
        result.putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, 1);
        result.putString(NodeJsRuntimeContract.KEY_STDOUT, "");
        result.putString(NodeJsRuntimeContract.KEY_STDERR, "");
        result.putString(NodeJsRuntimeContract.KEY_ERROR_NAME, error == null ? "NodeJsRuntimePluginError" : error.getClass().getSimpleName());
        result.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message);
        result.putString(NodeJsRuntimeContract.KEY_ERROR_STACK, error == null ? null : Log.getStackTraceString(error));
        result.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, errorCode);
        result.putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt));
        result.putString(NodeJsRuntimeContract.KEY_PROCESS_NAME, currentProcessName());
        result.putInt(NodeJsRuntimeContract.KEY_PID, Process.myPid());
        result.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME, NodeJsRuntimePluginService.DEFAULT_SOURCE_NAME));
        result.putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, false);
        result.putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                appendNativePayload(runtimePluginPayload(null), runtimeProcessDiagnosticsPayload())
        );
        return result;
    }
}

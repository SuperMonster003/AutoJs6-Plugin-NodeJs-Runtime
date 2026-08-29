#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

namespace {

enum class EmbeddedScriptPhaseStatusPolicy {
    kTerminal,
    kDone,
};

bool isUnsignedPhaseDuration(const std::string& value) {
    if (value.empty() || value.size() > 20) {
        return false;
    }
    for (const char ch : value) {
        if (ch < '0' || ch > '9') {
            return false;
        }
    }
    return true;
}

bool isObservedPhaseStatus(
        const std::string& status,
        EmbeddedScriptPhaseStatusPolicy policy
) {
    if (policy == EmbeddedScriptPhaseStatusPolicy::kTerminal) {
        return status == "completed" || status == "process_exit" || status == "failed";
    }
    return status == "done" || status == "completed" || status == "available";
}

void putEmbeddedScriptPhaseFields(
        std::vector<std::string>& payload,
        const std::string& text,
        const char* jsonDurationKey,
        const char* jsonStatusKey,
        const char* timingKey,
        const char* statusKey,
        EmbeddedScriptPhaseStatusPolicy policy
) {
    const std::string status = jsonStringField(text, jsonStatusKey);
    if (!status.empty()) {
        putPayload(payload, statusKey, status);
    }
    const std::string duration = jsonNumberField(text, jsonDurationKey);
    if (isObservedPhaseStatus(status, policy) && isUnsignedPhaseDuration(duration)) {
        putPayload(payload, timingKey, duration);
    }
}

}  // namespace

void putEmbeddedScriptExecutionFields(std::vector<std::string>& payload, const std::string& text) {
    putPayload(payload, "embedded_script.result_json", text);
    putPayload(payload, "embedded_script.succeeded", jsonBooleanField(text, "succeeded"));
    putPayload(payload, "embedded_script.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "embedded_script.result_text", jsonStringField(text, "resultText"));
    putPayload(payload, "embedded_script.error_name", jsonStringField(text, "errorName"));
    putPayload(payload, "embedded_script.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "embedded_script.error_stack", jsonStringField(text, "errorStack"));
    putPayload(payload, "embedded_script.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "embedded_script.source_name", jsonStringField(text, "sourceName"));
    putPayload(payload, "embedded_script.working_directory", jsonStringField(text, "workingDirectory"));
    putPayload(payload, "embedded_script.sandbox_root", jsonStringField(text, "sandboxRoot"));
    putPayload(payload, "embedded_script.current_working_directory", jsonStringField(text, "currentWorkingDirectory"));
    putPayloadRaw(payload, "embedded_script.stdout", jsonStringField(text, "stdout"));
    putPayloadRaw(payload, "embedded_script.stderr", jsonStringField(text, "stderr"));
    putPayload(payload, "embedded_script.stdout.bytes", jsonNumberField(text, "stdoutBytes"));
    putPayload(payload, "embedded_script.stderr.bytes", jsonNumberField(text, "stderrBytes"));
    putPayload(payload, "embedded_script.stdout.truncated", jsonBooleanField(text, "stdoutTruncated"));
    putPayload(payload, "embedded_script.stderr.truncated", jsonBooleanField(text, "stderrTruncated"));
    putPayload(payload, "embedded_script.console_events_json", jsonStringField(text, "consoleEventsJson"));
    putPayload(payload, "embedded_script.console_event_count", jsonNumberField(text, "consoleEventCount"));
    putPayload(payload, "embedded_script.console_events_truncated", jsonBooleanField(text, "consoleEventsTruncated"));
    putPayload(payload, "embedded_script.chdir_failure_reason", jsonStringField(text, "chdirFailureReason"));
    putPayload(payload, "embedded_script.create_require_failure_reason", jsonStringField(text, "createRequireFailureReason"));
    putPayload(payload, "embedded_script.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "embedded_script.elapsed_ms", jsonNumberField(text, "elapsedMs"));
    putEmbeddedScriptPhaseFields(
            payload,
            text,
            "bootstrapMs",
            "bootstrapStatus",
            "timing.bootstrap.ms",
            "embedded_script.phase.bootstrap.status",
            EmbeddedScriptPhaseStatusPolicy::kTerminal
    );
    putEmbeddedScriptPhaseFields(
            payload,
            text,
            "modulePreloadMs",
            "modulePreloadStatus",
            "timing.module_preload.ms",
            "embedded_script.phase.module_preload.status",
            EmbeddedScriptPhaseStatusPolicy::kDone
    );
    putEmbeddedScriptPhaseFields(
            payload,
            text,
            "scriptExecutionMs",
            "scriptExecutionStatus",
            "timing.script_execution.ms",
            "embedded_script.phase.script_execution.status",
            EmbeddedScriptPhaseStatusPolicy::kTerminal
    );
    putPayload(payload, "embedded_script.timed_out", jsonBooleanField(text, "timedOut"));
    putPayload(payload, "embedded_script.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "embedded_script.pending_timers", jsonBooleanField(text, "pendingTimers"));
    putPayload(payload, "embedded_script.pending_timer_count", jsonNumberField(text, "pendingTimerCount"));
    putPayload(payload, "embedded_script.pending_timeout_count", jsonNumberField(text, "pendingTimeoutCount"));
    putPayload(payload, "embedded_script.pending_interval_count", jsonNumberField(text, "pendingIntervalCount"));
    putPayload(payload, "embedded_script.pending_immediate_count", jsonNumberField(text, "pendingImmediateCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_callbacks", jsonNumberField(text, "pendingScopedFsCallbacks"));
    putPayload(payload, "embedded_script.pending_scoped_fs_streams", jsonBooleanField(text, "pendingScopedFsStreams"));
    putPayload(payload, "embedded_script.pending_scoped_fs_stream_count", jsonNumberField(text, "pendingScopedFsStreamCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_watch_count", jsonNumberField(text, "pendingScopedFsWatchCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_watch_file_count", jsonNumberField(text, "pendingScopedFsWatchFileCount"));
    putPayload(payload, "embedded_script.pending_workers", jsonBooleanField(text, "pendingWorkers"));
    putPayload(payload, "embedded_script.pending_worker_count", jsonNumberField(text, "pendingWorkerCount"));
    putPayload(payload, "embedded_script.pending_crypto_callbacks", jsonNumberField(text, "pendingCryptoCallbacks"));
    putPayload(payload, "embedded_script.pending_dns_callbacks", jsonNumberField(text, "pendingDnsCallbacks"));
    putPayload(payload, "embedded_script.pending_zlib_callbacks", jsonNumberField(text, "pendingZlibCallbacks"));
    putPayload(payload, "embedded_script.pending_node_test_callbacks", jsonNumberField(text, "pendingNodeTestCallbacks"));
    putPayload(payload, "embedded_script.pending_zlib_streams", jsonBooleanField(text, "pendingZlibStreams"));
    putPayload(payload, "embedded_script.pending_zlib_stream_count", jsonNumberField(text, "pendingZlibStreamCount"));
    putPayload(payload, "embedded_script.pending_async_callbacks", jsonNumberField(text, "pendingAsyncCallbacks"));
    putPayload(payload, "embedded_script.pending_callbacks", jsonBooleanField(text, "pendingCallbacks"));
    putPayload(payload, "embedded_script.bridge_requests_json", jsonStringField(text, "bridgeRequestsJson"));
    putPayload(payload, "embedded_script.bridge_request_count", jsonNumberField(text, "bridgeRequestCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_loaded_count", jsonNumberField(text, "dynamicRequireFallbackLoadedCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_source_bytes", jsonNumberField(text, "dynamicRequireFallbackSourceBytes"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_denied_count", jsonNumberField(text, "dynamicRequireFallbackDeniedCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_request", jsonStringField(text, "dynamicRequireFallbackLastRequest"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_referrer", jsonStringField(text, "dynamicRequireFallbackLastReferrer"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_candidate", jsonStringField(text, "dynamicRequireFallbackLastCandidate"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_denied_reason", jsonStringField(text, "dynamicRequireFallbackLastDeniedReason"));
    putPayload(payload, "embedded_script.module_provider.enabled", jsonBooleanField(text, "moduleProviderEnabled"));
    putPayload(payload, "embedded_script.module_provider.request_count", jsonNumberField(text, "moduleProviderRequestCount"));
    putPayload(payload, "embedded_script.module_provider.missing_candidate_request_count", jsonNumberField(text, "moduleProviderMissingCandidateRequestCount"));
    putPayload(payload, "embedded_script.module_provider.resolved_count", jsonNumberField(text, "moduleProviderResolvedCount"));
    putPayload(payload, "embedded_script.module_provider.decrypted_count", jsonNumberField(text, "moduleProviderDecryptedCount"));
    putPayload(payload, "embedded_script.module_provider.materialized_count", jsonNumberField(text, "moduleProviderMaterializedCount"));
    putPayload(payload, "embedded_script.module_provider.materialized_source_bytes", jsonNumberField(text, "moduleProviderMaterializedSourceBytes"));
    putPayload(payload, "embedded_script.module_provider.not_encrypted_count", jsonNumberField(text, "moduleProviderNotEncryptedCount"));
    putPayload(payload, "embedded_script.module_provider.not_found_count", jsonNumberField(text, "moduleProviderNotFoundCount"));
    putPayload(payload, "embedded_script.module_provider.denied_count", jsonNumberField(text, "moduleProviderDeniedCount"));
    putPayload(payload, "embedded_script.module_provider.cancelled_count", jsonNumberField(text, "moduleProviderCancelledCount"));
    putPayload(payload, "embedded_script.module_provider.timed_out_count", jsonNumberField(text, "moduleProviderTimedOutCount"));
    putPayload(payload, "embedded_script.module_provider.failed_count", jsonNumberField(text, "moduleProviderFailedCount"));
    putPayload(payload, "embedded_script.module_provider.source_bytes", jsonNumberField(text, "moduleProviderSourceBytes"));
    putPayload(payload, "embedded_script.module_provider.elapsed_ms", jsonNumberField(text, "moduleProviderElapsedMs"));
    putPayload(payload, "embedded_script.module_provider.last_status", jsonStringField(text, "moduleProviderLastStatus"));
    putPayload(payload, "embedded_script.module_provider.last_denial_reason", jsonStringField(text, "moduleProviderLastDenialReason"));
    putPayload(payload, "embedded_script.module_provider.last_error_code", jsonStringField(text, "moduleProviderLastErrorCode"));
    putPayload(payload, "embedded_script.module_provider.legacy_directory_scan_ran", jsonBooleanField(text, "moduleProviderLegacyDirectoryScanRan"));
    putPayload(payload, "embedded_script.package_resolution_package", jsonStringField(text, "packageResolutionPackage"));
    putPayload(payload, "embedded_script.package_resolution_package_json", jsonStringField(text, "packageResolutionPackageJson"));
    putPayload(payload, "embedded_script.package_resolution_field", jsonStringField(text, "packageResolutionField"));
    putPayload(payload, "embedded_script.package_resolution_request", jsonStringField(text, "packageResolutionRequest"));
    putPayload(payload, "embedded_script.package_resolution_target", jsonStringField(text, "packageResolutionTarget"));
    putPayload(payload, "embedded_script.package_resolution_condition_keys", jsonStringField(text, "packageResolutionConditionKeys"));
    putPayload(payload, "embedded_script.package_resolution_selected_condition", jsonStringField(text, "packageResolutionSelectedCondition"));
    putPayload(payload, "embedded_script.package_resolution_ignored_fields", jsonStringField(text, "packageResolutionIgnoredFields"));
    putPayload(payload, "embedded_script.package_resolution_ignored_conditions", jsonStringField(text, "packageResolutionIgnoredConditions"));
    putPayload(payload, "embedded_script.package_resolution_reason", jsonStringField(text, "packageResolutionReason"));
    putPayload(payload, "embedded_script.package_type_module_policy", jsonStringField(text, "packageTypeModulePolicy"));
    putPayload(payload, "embedded_script.package_type_module_package", jsonStringField(text, "packageTypeModulePackage"));
    putPayload(payload, "embedded_script.package_type_module_package_json", jsonStringField(text, "packageTypeModulePackageJson"));
    putPayload(payload, "embedded_script.package_type_module_filename", jsonStringField(text, "packageTypeModuleFilename"));
    putPayload(payload, "embedded_script.esm_enabled", jsonBooleanField(text, "esmEnabled"));
    putPayload(payload, "embedded_script.esm_linker", jsonStringField(text, "esmLinker"));
    putPayload(payload, "embedded_script.esm_live_bindings", jsonBooleanField(text, "esmLiveBindings"));
    putPayload(payload, "embedded_script.esm_entry", jsonBooleanField(text, "esmEntry"));
    putPayload(payload, "embedded_script.esm_module_graph_root", jsonStringField(text, "esmModuleGraphRoot"));
    putPayload(payload, "embedded_script.esm_module_graph_size", jsonNumberField(text, "esmModuleGraphSize"));
    putPayload(payload, "embedded_script.esm_module_graph_modules", jsonStringField(text, "esmModuleGraphModules"));
    putPayload(payload, "embedded_script.esm_denied_count", jsonNumberField(text, "esmDeniedCount"));
    putPayload(payload, "embedded_script.esm_last_denied_reason", jsonStringField(text, "esmLastDeniedReason"));
    putPayload(payload, "embedded_script.dynamic_import_enabled", jsonBooleanField(text, "dynamicImportEnabled"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.snapshot_enabled", jsonBooleanField(text, "typeScriptPrecompiledSnapshotEnabled"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.source_count", jsonNumberField(text, "typeScriptPrecompiledSnapshotSourceCount"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.mapped_count", jsonNumberField(text, "typeScriptDynamicSpecifierMappedCount"));
    putPayload(payload, "embedded_script.typescript.on_demand_compile.count", jsonNumberField(text, "typeScriptOnDemandCompileCount"));
    putPayload(payload, "embedded_script.typescript.on_demand_compile.source_bytes", jsonNumberField(text, "typeScriptOnDemandCompileSourceBytes"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.rejected_count", jsonNumberField(text, "typeScriptDynamicSpecifierRejectedCount"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.ambiguous_count", jsonNumberField(text, "typeScriptDynamicSpecifierAmbiguousCount"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.last_specifier", jsonStringField(text, "typeScriptDynamicSpecifierLastSpecifier"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.last_source", jsonStringField(text, "typeScriptDynamicSpecifierLastSource"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.last_generated", jsonStringField(text, "typeScriptDynamicSpecifierLastGenerated"));
    putPayload(payload, "embedded_script.typescript.dynamic_specifier.last_error_code", jsonStringField(text, "typeScriptDynamicSpecifierLastErrorCode"));
    putPayload(payload, "embedded_script.worker_threads_enabled", jsonBooleanField(text, "workerThreadsEnabled"));
    putPayload(payload, "embedded_script.worker_active_count", jsonNumberField(text, "workerActiveCount"));
    putPayload(payload, "embedded_script.worker_max_active_count", jsonNumberField(text, "workerMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_created_count", jsonNumberField(text, "workerCreatedCount"));
    putPayload(payload, "embedded_script.worker_terminated_count", jsonNumberField(text, "workerTerminatedCount"));
    putPayload(payload, "embedded_script.worker_startup_timeout_count", jsonNumberField(text, "workerStartupTimeoutCount"));
    putPayload(payload, "embedded_script.worker_rejected_by_policy_count", jsonNumberField(text, "workerRejectedByPolicyCount"));
    putPayload(payload, "embedded_script.worker_cleanup_requested_count", jsonNumberField(text, "workerCleanupRequestedCount"));
    putPayload(payload, "embedded_script.worker_cleanup_terminate_requested_count", jsonNumberField(text, "workerCleanupTerminateRequestedCount"));
    putPayload(payload, "embedded_script.worker_cleanup_error_count", jsonNumberField(text, "workerCleanupErrorCount"));
    putPayload(payload, "embedded_script.worker_cleanup_pending_message_rejected_count", jsonNumberField(text, "workerCleanupPendingMessageRejectedCount"));
    putPayload(payload, "embedded_script.worker_message_port_active_count", jsonNumberField(text, "workerMessagePortActiveCount"));
    putPayload(payload, "embedded_script.worker_message_port_max_active_count", jsonNumberField(text, "workerMessagePortMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_message_port_created_count", jsonNumberField(text, "workerMessagePortCreatedCount"));
    putPayload(payload, "embedded_script.worker_message_port_closed_count", jsonNumberField(text, "workerMessagePortClosedCount"));
    putPayload(payload, "embedded_script.worker_message_rejected_count", jsonNumberField(text, "workerMessageRejectedCount"));
    putPayload(payload, "embedded_script.worker_last_message_rejection", jsonStringField(text, "workerLastMessageRejection"));
    putPayload(payload, "embedded_script.worker_pool_active_count", jsonNumberField(text, "workerPoolActiveCount"));
    putPayload(payload, "embedded_script.worker_pool_max_active_count", jsonNumberField(text, "workerPoolMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_pool_created_count", jsonNumberField(text, "workerPoolCreatedCount"));
    putPayload(payload, "embedded_script.worker_pool_closed_count", jsonNumberField(text, "workerPoolClosedCount"));
    putPayload(payload, "embedded_script.worker_pool_task_completed_count", jsonNumberField(text, "workerPoolTaskCompletedCount"));
    putPayload(payload, "embedded_script.worker_pool_task_cancelled_count", jsonNumberField(text, "workerPoolTaskCancelledCount"));
    putPayload(payload, "embedded_script.worker_pool_task_timeout_count", jsonNumberField(text, "workerPoolTaskTimeoutCount"));
    putPayload(payload, "embedded_script.worker_pool_queue_rejected_count", jsonNumberField(text, "workerPoolQueueRejectedCount"));
    putPayload(payload, "embedded_script.worker_last_policy_rejection", jsonStringField(text, "workerLastPolicyRejection"));
    putPayload(payload, "embedded_script.source_map_enabled", jsonBooleanField(text, "sourceMapEnabled"));
    putPayload(payload, "embedded_script.source_map_loaded_count", jsonNumberField(text, "sourceMapLoadedCount"));
    putPayload(payload, "embedded_script.source_map_mapped_frame_count", jsonNumberField(text, "sourceMapMappedFrameCount"));
    putPayload(payload, "embedded_script.source_map_denied_count", jsonNumberField(text, "sourceMapDeniedCount"));
    putPayload(payload, "embedded_script.source_map_last_source", jsonStringField(text, "sourceMapLastSource"));
    putPayload(payload, "embedded_script.source_map_last_map", jsonStringField(text, "sourceMapLastMap"));
    putPayload(payload, "embedded_script.source_map_last_error", jsonStringField(text, "sourceMapLastError"));
    putPayload(payload, "embedded_script.generated_stack_mapped_frame_count", jsonNumberField(text, "generatedStackMappedFrameCount"));
    putPayload(payload, "embedded_script.generated_stack_last_source", jsonStringField(text, "generatedStackLastSource"));
    putPayload(payload, "embedded_script.generated_stack_last_generated_line", jsonNumberField(text, "generatedStackLastGeneratedLine"));
    putPayload(payload, "embedded_script.generated_stack_last_original_line", jsonNumberField(text, "generatedStackLastOriginalLine"));
    putPayload(payload, "embedded_script.compile_cache.requested", jsonBooleanField(text, "compileCacheRequested"));
    putPayload(payload, "embedded_script.compile_cache.enabled", jsonBooleanField(text, "compileCacheEnabled"));
    putPayload(payload, "embedded_script.compile_cache.status", jsonStringField(text, "compileCacheStatus"));
    putPayload(payload, "embedded_script.compile_cache.status_code", jsonStringField(text, "compileCacheStatusCode"));
    putPayload(payload, "embedded_script.compile_cache.directory", jsonStringField(text, "compileCacheDirectory"));
    putPayload(payload, "embedded_script.compile_cache.key", jsonStringField(text, "compileCacheKey"));
    putPayload(payload, "embedded_script.compile_cache.runtime_key", jsonStringField(text, "compileCacheRuntimeKey"));
    putPayload(payload, "embedded_script.compile_cache.project_key", jsonStringField(text, "compileCacheProjectKey"));
    putPayload(payload, "embedded_script.compile_cache.loader", jsonStringField(text, "compileCacheLoader"));
    putPayload(payload, "embedded_script.compile_cache.schema", jsonStringField(text, "compileCacheSchema"));
    putPayload(payload, "embedded_script.compile_cache.message", jsonStringField(text, "compileCacheMessage"));
    putPayload(payload, "embedded_script.compile_cache.v8_version", jsonStringField(text, "compileCacheV8Version"));
    putPayload(payload, "embedded_script.compile_cache.flush_supported", jsonBooleanField(text, "compileCacheFlushSupported"));
    putPayload(payload, "embedded_script.compile_cache.flush_attempted", jsonBooleanField(text, "compileCacheFlushAttempted"));
    putPayload(payload, "embedded_script.compile_cache.flushed", jsonBooleanField(text, "compileCacheFlushed"));
    putPayload(payload, "embedded_script.compile_cache.custom_loader", jsonBooleanField(text, "compileCacheCustomLoader"));
    putPayload(payload, "embedded_script.compile_cache.hit_count", jsonNumberField(text, "compileCacheHitCount"));
    putPayload(payload, "embedded_script.compile_cache.miss_count", jsonNumberField(text, "compileCacheMissCount"));
    putPayload(payload, "embedded_script.compile_cache.hit_miss_status", jsonStringField(text, "compileCacheHitMissStatus"));
}

bool readJsResultGlobal(
        v8::Isolate* isolate,
        v8::Local<v8::Context> context,
        V8ContextGlobal contextGlobal,
        V8StringNewFromUtf8 stringNewFromUtf8,
        V8ObjectGet objectGet,
        V8ValueToString valueToString,
        V8StringUtf8ValueConstructor utf8ValueConstructor,
        V8StringUtf8ValueDestructor utf8ValueDestructor,
        std::string& resultText,
        std::string& error
) {
    if (isolate == nullptr || context.IsEmpty()) {
        error = "isolate or context is unavailable";
        return false;
    }

    v8::Context* contextRaw = context.operator->();
    v8::Local<v8::Object> global = contextGlobal(contextRaw);
    if (global.IsEmpty()) {
        error = "Context::Global returned empty";
        return false;
    }

    v8::Local<v8::String> key;
    if (!stringNewFromUtf8(
            isolate,
            kJsResultPropertyName,
            v8::NewStringType::kNormal,
            -1
    ).ToLocal(&key)) {
        error = "String::NewFromUtf8 returned empty";
        return false;
    }

    v8::Local<v8::Value> value;
    if (!objectGet(global.operator->(), context, key.As<v8::Value>()).ToLocal(&value)) {
        error = "Object::Get returned empty";
        return false;
    }

    v8::Local<v8::String> stringValue;
    if (!valueToString(value.operator->(), context).ToLocal(&stringValue)) {
        error = "Value::ToString returned empty";
        return false;
    }

    alignas(v8::String::Utf8Value) unsigned char utf8Storage[sizeof(v8::String::Utf8Value)] = {};
    auto* utf8Value = reinterpret_cast<v8::String::Utf8Value*>(utf8Storage);
    utf8ValueConstructor(
            utf8Value,
            isolate,
            stringValue.As<v8::Value>(),
            v8::String::REPLACE_INVALID_UTF8
    );
    const char* chars = **utf8Value;
    if (chars == nullptr) {
        utf8ValueDestructor(utf8Value);
        error = "String::Utf8Value returned null";
        return false;
    }
    resultText.assign(chars, utf8Value->length());
    utf8ValueDestructor(utf8Value);
    return true;
}

const char* uvHandleTypeNameForPayload(uv_handle_type type) {
    switch (type) {
        case UV_ASYNC:
            return "async";
        case UV_CHECK:
            return "check";
        case UV_FS_EVENT:
            return "fs_event";
        case UV_FS_POLL:
            return "fs_poll";
        case UV_HANDLE:
            return "handle";
        case UV_IDLE:
            return "idle";
        case UV_NAMED_PIPE:
            return "pipe";
        case UV_POLL:
            return "poll";
        case UV_PREPARE:
            return "prepare";
        case UV_PROCESS:
            return "process";
        case UV_STREAM:
            return "stream";
        case UV_TCP:
            return "tcp";
        case UV_TIMER:
            return "timer";
        case UV_TTY:
            return "tty";
        case UV_UDP:
            return "udp";
        case UV_SIGNAL:
            return "signal";
        case UV_FILE:
            return "file";
        case UV_UNKNOWN_HANDLE:
        default:
            return "unknown";
    }
}

std::string pointerToHex(const void* pointer) {
    char buffer[32];
    std::snprintf(
            buffer,
            sizeof(buffer),
            "0x%" PRIxPTR,
            static_cast<uintptr_t>(reinterpret_cast<uintptr_t>(pointer))
    );
    return std::string(buffer);
}


void collectUvHandleDiagnostic(uv_handle_t* handle, void* arg) {
    auto* context = static_cast<UvDiagnosticsContext*>(arg);
    if (context == nullptr || handle == nullptr) {
        return;
    }
    const uv_handle_type type = context->handleGetType(handle);
    const int active = context->isActive(handle);
    const int closing = context->isClosing(handle);
    context->count += 1;
    if (active != 0) {
        context->active += 1;
    }
    if (closing != 0) {
        context->closing += 1;
    }
    const int typeIndex = static_cast<int>(type);
    if (typeIndex >= 0 && typeIndex < static_cast<int>(context->typeCounts.size())) {
        context->typeCounts[static_cast<size_t>(typeIndex)] += 1;
    } else {
        context->typeCounts[static_cast<size_t>(UV_UNKNOWN_HANDLE)] += 1;
    }
    if (context->samples.size() < 16) {
        context->samples.push_back(
                {
                        pointerToHex(handle),
                        uvHandleTypeNameForPayload(type),
                        active != 0,
                        closing != 0,
                }
        );
    }
}

void collectUvCloseTarget(uv_handle_t* handle, void* arg) {
    auto* context = static_cast<UvCloseContext*>(arg);
    if (context == nullptr || handle == nullptr) {
        return;
    }
    const uv_handle_type type = context->handleGetType(handle);
    const int active = context->isActive(handle);
    const int closing = context->isClosing(handle);
    context->seen += 1;
    if (closing != 0) {
        context->skipped += 1;
        return;
    }
    context->targets.push_back(
            {
                    handle,
                    pointerToHex(handle),
                    uvHandleTypeNameForPayload(type),
                    active != 0,
                    false,
            }
    );
}

void noopUvCloseCallback(uv_handle_t* /* handle */) {}

UvDiagnosticsSummary appendUvDiagnosticsPayload(
        std::vector<std::string>& payload,
        const std::string& phase,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
) {
    const std::string prefix = "uv.diagnostics." + phase;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.diagnostics.start phase=%s", phase.c_str());
    const auto startedAt = Clock::now();
    UvDiagnosticsSummary summary;
    if (eventLoop == nullptr) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_diagnostics_" + phase + ".ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed reason=missing_loop", phase.c_str());
        return summary;
    }

    try {
        UvDiagnosticsContext context(uvHandleGetType, uvIsActive, uvIsClosing);
        uvWalk(eventLoop, collectUvHandleDiagnostic, &context);
        summary.valid = true;
        summary.count = context.count;
        summary.active = context.active;
        summary.closing = context.closing;
        putPayload(payload, prefix + ".status", "done");
        putPayload(payload, prefix + ".detail", "uv_walk returned");
        putPayload(payload, prefix + ".count", static_cast<long long>(context.count));
        putPayload(payload, prefix + ".active", static_cast<long long>(context.active));
        putPayload(payload, prefix + ".closing", static_cast<long long>(context.closing));
        for (size_t i = 0; i < context.typeCounts.size(); ++i) {
            const int count = context.typeCounts[i];
            if (count <= 0) {
                continue;
            }
            const std::string key = prefix + ".type." +
                    uvHandleTypeNameForPayload(static_cast<uv_handle_type>(i));
            putPayload(payload, key, static_cast<long long>(count));
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.diagnostics.%s.%s=%d",
                    phase.c_str(),
                    key.substr(prefix.size() + 1).c_str(),
                    count
            );
        }
        putPayload(payload, prefix + ".handle.sample.count", static_cast<long long>(context.samples.size()));
        for (size_t i = 0; i < context.samples.size(); ++i) {
            const UvHandleDiagnosticSample& sample = context.samples[i];
            const std::string handlePrefix = prefix + ".handle." + std::to_string(i);
            putPayload(payload, handlePrefix + ".address", sample.address);
            putPayload(payload, handlePrefix + ".type", sample.type);
            putPayload(payload, handlePrefix + ".active", sample.active);
            putPayload(payload, handlePrefix + ".closing", sample.closing);
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.diagnostics.handle.%zu phase=%s type=%s active=%s closing=%s address=%s",
                    i,
                    phase.c_str(),
                    sample.type.c_str(),
                    sample.active ? "true" : "false",
                    sample.closing ? "true" : "false",
                    sample.address.c_str()
            );
        }
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.diagnostics.%s.count=%d active=%d closing=%d",
                phase.c_str(),
                context.count,
                context.active,
                context.closing
        );
    } catch (const std::exception& e) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed error=%s", phase.c_str(), e.what());
    } catch (...) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed error=unknown", phase.c_str());
    }
    putPayload(payload, "timing.uv_diagnostics_" + phase + ".ms", elapsedMs(startedAt));
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.diagnostics.done phase=%s elapsed=%lldms", phase.c_str(), elapsedMs(startedAt));
    return summary;
}

void appendUvClosePayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvClose uvClose,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.close.start");
    const auto startedAt = Clock::now();
    if (eventLoop == nullptr) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_close.ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed reason=missing_loop");
        return;
    }

    try {
        UvCloseContext context(uvHandleGetType, uvIsActive, uvIsClosing);
        uvWalk(eventLoop, collectUvCloseTarget, &context);
        int succeeded = 0;
        int failed = 0;
        for (size_t i = 0; i < context.targets.size(); ++i) {
            const UvCloseTarget& target = context.targets[i];
            const std::string handlePrefix = "uv.close.handle." + std::to_string(i);
            putPayload(payload, handlePrefix + ".address", target.address);
            putPayload(payload, handlePrefix + ".type", target.type);
            putPayload(payload, handlePrefix + ".active.before", target.activeBefore);
            putPayload(payload, handlePrefix + ".closing.before", target.closingBefore);
            uvClose(target.handle, noopUvCloseCallback);
            const bool closingAfter = uvIsClosing(target.handle) != 0;
            putPayload(payload, handlePrefix + ".closing.after", closingAfter);
            putPayload(payload, handlePrefix + ".status", closingAfter ? "closing" : "not_closing");
            if (closingAfter) {
                succeeded += 1;
            } else {
                failed += 1;
            }
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.close.handle.%zu type=%s active_before=%s closing_after=%s address=%s",
                    i,
                    target.type.c_str(),
                    target.activeBefore ? "true" : "false",
                    closingAfter ? "true" : "false",
                    target.address.c_str()
            );
        }
        putPayload(payload, "uv.close.status", failed == 0 ? "done" : "failed");
        putPayload(payload, "uv.close.detail", failed == 0 ? "uv_close returned for collected handles" : "uv_close did not mark every collected handle as closing");
        putPayload(payload, "uv.close.seen", static_cast<long long>(context.seen));
        putPayload(payload, "uv.close.attempted", static_cast<long long>(context.targets.size()));
        putPayload(payload, "uv.close.succeeded", static_cast<long long>(succeeded));
        putPayload(payload, "uv.close.skipped", static_cast<long long>(context.skipped));
        putPayload(payload, "uv.close.failed", static_cast<long long>(failed));
        __android_log_print(
                failed == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                kLogTag,
                "uv.close.done seen=%d attempted=%zu succeeded=%d skipped=%d failed=%d elapsed=%lldms",
                context.seen,
                context.targets.size(),
                succeeded,
                context.skipped,
                failed,
                elapsedMs(startedAt)
        );
    } catch (const std::exception& e) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed error=%s", e.what());
    } catch (...) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed error=unknown");
    }
    putPayload(payload, "timing.uv_close.ms", elapsedMs(startedAt));
}

bool appendUvRunCleanupPayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvRun uvRun,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing,
        bool collectDiagnostics
) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.run.start mode=UV_RUN_NOWAIT max_iterations=%d", kUvRunCleanupMaxIterations);
    const auto startedAt = Clock::now();
    putPayload(payload, "uv.run.mode", "UV_RUN_NOWAIT");
    putPayload(payload, "uv.run.diagnostics", collectDiagnostics ? "full" : "lightweight");
    putPayload(payload, "uv.run.max_iterations", static_cast<long long>(kUvRunCleanupMaxIterations));
    putPayload(payload, "uv.run.completed_iterations", static_cast<long long>(0));
    if (eventLoop == nullptr) {
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_run.ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed reason=missing_loop");
        return false;
    }

    bool failed = false;
    bool handleCountZero = false;
    int completedIterations = 0;
    try {
        for (int iteration = 0; iteration < kUvRunCleanupMaxIterations; ++iteration) {
            const auto iterationStartedAt = Clock::now();
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.run.iteration.%d.start mode=UV_RUN_NOWAIT", iteration);
            const int result = uvRun(eventLoop, UV_RUN_NOWAIT);
            completedIterations = iteration + 1;
            const std::string iterationPrefix = "uv.run.iteration." + std::to_string(iteration);
            putPayload(payload, iterationPrefix + ".rc", static_cast<long long>(result));
            putPayload(payload, "timing.uv_run_iteration_" + std::to_string(iteration) + ".ms", elapsedMs(iterationStartedAt));
            int handleCount = -1;
            int activeCount = -1;
            int closingCount = -1;
            if (collectDiagnostics) {
                const UvDiagnosticsSummary summary = appendUvDiagnosticsPayload(
                        payload,
                        "after_run_" + std::to_string(iteration),
                        eventLoop,
                        uvWalk,
                        uvHandleGetType,
                        uvIsActive,
                        uvIsClosing
                );
                if (summary.valid) {
                    handleCount = summary.count;
                    activeCount = summary.active;
                    closingCount = summary.closing;
                    putPayload(payload, iterationPrefix + ".handle.count", static_cast<long long>(summary.count));
                    putPayload(payload, iterationPrefix + ".handle.active", static_cast<long long>(summary.active));
                    putPayload(payload, iterationPrefix + ".handle.closing", static_cast<long long>(summary.closing));
                    handleCountZero = summary.count == 0;
                }
            } else {
                handleCountZero = result == 0;
            }
            putPayload(payload, "uv.run.completed_iterations", static_cast<long long>(completedIterations));
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.run.iteration.%d.done rc=%d handle_count=%d active=%d closing=%d elapsed=%lldms",
                    iteration,
                    result,
                    handleCount,
                    activeCount,
                    closingCount,
                    elapsedMs(iterationStartedAt)
            );
            if (handleCountZero) {
                break;
            }
        }
    } catch (const std::exception& e) {
        failed = true;
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed error=%s", e.what());
    } catch (...) {
        failed = true;
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed error=unknown");
    }

    if (!failed) {
        putPayload(payload, "uv.run.status", "done");
        putPayload(
                payload,
                "uv.run.detail",
                handleCountZero
                        ? "bounded UV_RUN_NOWAIT cleanup released all handles"
                        : "bounded UV_RUN_NOWAIT cleanup completed with handles still present"
        );
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.run.done completed_iterations=%d handle_count_zero=%s elapsed=%lldms",
                completedIterations,
                handleCountZero ? "true" : "false",
                elapsedMs(startedAt)
        );
    }
    putPayload(payload, "timing.uv_run.ms", elapsedMs(startedAt));
    return !failed;
}

int v8BuildConfiguration() {
    int buildConfiguration = 0;
    if (v8::internal::PointerCompressionIsEnabled()) {
        buildConfiguration |= 1 << 0;
    }
    if (v8::internal::SmiValuesAre31Bits()) {
        buildConfiguration |= 1 << 1;
    }
    if (v8::internal::SandboxIsEnabled()) {
        buildConfiguration |= 1 << 2;
    }
#ifdef V8_TARGET_OS_ANDROID
    buildConfiguration |= 1 << 3;
#endif
#ifdef V8_ENABLE_CHECKS
    buildConfiguration |= 1 << 4;
#endif
    return buildConfiguration;
}

std::string uvLoopResultDetail(const char* operation, int result) {
    std::ostringstream stream;
    stream << operation << " returned " << result;
    if (result == UV_EBUSY) {
        stream << " (UV_EBUSY: uv_loop_t still has active handles or requests; handles were not force-closed)";
    }
    return stream.str();
}

}  // namespace autojs6::node_bridge::internal

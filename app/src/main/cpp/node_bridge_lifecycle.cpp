#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

class LifecyclePlatformRef {
public:
    explicit LifecyclePlatformRef(node::MultiIsolatePlatform* borrowed = nullptr)
            : pointer_(borrowed) {
    }

    LifecyclePlatformRef& operator=(std::unique_ptr<node::MultiIsolatePlatform> owned) {
        owned_ = std::move(owned);
        pointer_ = owned_.get();
        return *this;
    }

    node::MultiIsolatePlatform* get() const {
        return pointer_;
    }

    node::MultiIsolatePlatform* release() {
        if (owned_ != nullptr) {
            pointer_ = owned_.release();
            return pointer_;
        }
        node::MultiIsolatePlatform* borrowed = pointer_;
        pointer_ = nullptr;
        return borrowed;
    }

    bool operator==(std::nullptr_t) const {
        return pointer_ == nullptr;
    }

    bool operator!=(std::nullptr_t) const {
        return pointer_ != nullptr;
    }

private:
    std::unique_ptr<node::MultiIsolatePlatform> owned_;
    node::MultiIsolatePlatform* pointer_ = nullptr;
};

node::ProcessInitializationFlags::Flags embeddedNodeInitializationFlags(bool enableStdioInitialization) {
    uint32_t flags =
            static_cast<uint32_t>(node::ProcessInitializationFlags::kLegacyInitializeNodeWithArgsBehavior) |
            static_cast<uint32_t>(node::ProcessInitializationFlags::kDisableNodeOptionsEnv) |
            static_cast<uint32_t>(node::ProcessInitializationFlags::kNoParseGlobalDebugVariables);
    if (enableStdioInitialization) {
        flags &= ~static_cast<uint32_t>(node::ProcessInitializationFlags::kNoStdioInitialization);
    }
    return static_cast<node::ProcessInitializationFlags::Flags>(flags);
}

const char* embeddedNodeInitializationFlagsName(bool enableStdioInitialization) {
    return enableStdioInitialization
           ? "kLegacyInitializeNodeWithArgsBehavior_without_kNoStdioInitialization|kDisableNodeOptionsEnv|kNoParseGlobalDebugVariables"
           : "kLegacyInitializeNodeWithArgsBehavior|kDisableNodeOptionsEnv|kNoParseGlobalDebugVariables";
}

bool isTruthyEnvValue(const std::string& value) {
    std::string normalized;
    normalized.reserve(value.size());
    for (char ch : value) {
        if (!std::isspace(static_cast<unsigned char>(ch))) {
            normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
        }
    }
    return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on";
}

bool embeddedScriptFullUvDiagnosticsRequested(const EmbeddedScriptExecutionRequest& request) {
    for (const auto& entry : request.env) {
        if (entry.first == "AUTOJS6_NODE_FULL_UV_DIAGNOSTICS") {
            return isTruthyEnvValue(entry.second);
        }
    }
    return false;
}


std::vector<std::string> runEmbeddedScriptExecution(
        const EmbeddedScriptExecutionRequest& request,
        const char* runtimeAdapterPath,
        const char* runtimeAdapterMode
) {
    std::lock_guard<std::recursive_mutex> processRuntimeLock(embeddedProcessRuntimeExecutionMutex());
    const auto startedAt = Clock::now();
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "embedded_script.enter");
    const std::string sourceNameText = request.sourceName.empty()
            ? "<embedded-user-script.js>"
            : request.sourceName;
    std::vector<std::string> payload;
    putPayload(payload, "embedded_script.request.source_name", sourceNameText);
    putPayload(payload, "embedded_script.request.source.length", static_cast<long long>(request.source.size()));
    putPayload(payload, "embedded_script.request.working_directory", request.workingDirectory);
    putPayload(payload, "embedded_script.request.sandbox_root", request.sandboxRoot);
    putPayload(payload, "embedded_script.request.env.count", static_cast<long long>(request.env.size()));
    putPayload(payload, "embedded_script.request.module_sources.count", static_cast<long long>(request.moduleSources.size()));
    putPayload(
            payload,
            "embedded_script.request.runtime_module_sources.count",
            static_cast<long long>(request.runtimeModuleSources.size())
    );
    putPayload(
            payload,
            "embedded_script.request.typescript_precompiled_snapshot",
            request.typeScriptPrecompiledSnapshot
    );
    putPayload(
            payload,
            "embedded_script.request.typescript_precompiled_source_names.count",
            static_cast<long long>(request.typeScriptPrecompiledSourceNames.size())
    );
    putPayload(payload, "embedded_script.request.source_kind", "kotlin_provided_source");
    putPayload(payload, "embedded_script.request.user_file_read_by_native", false);
    putPayload(payload, "embedded_script.request.require_allowed", true);
    putPayload(
            payload,
            "embedded_script.request.require_mode",
            request.esmEnabled ? "restricted_commonjs_mvp_with_esm" : "restricted_commonjs_mvp"
    );
    putPayload(payload, "embedded_script.request.fs_allowed", "scoped_working_directory_sync_mvp");
    putPayload(payload, "embedded_script.request.import_allowed", request.esmEnabled);
    putPayload(payload, "embedded_script.request.esm_enabled", request.esmEnabled);
    putPayload(payload, "embedded_script.request.dynamic_import_enabled", request.dynamicImportEnabled);
    putPayload(payload, "embedded_script.request.raw_node_network_modules_enabled", request.rawNodeNetworkModulesEnabled);
    putPayload(payload, "embedded_script.request.worker_threads_enabled", request.workerThreadsEnabled);
    putPayload(payload, "embedded_script.request.child_process_enabled", request.childProcessEnabled);
    putPayload(payload, "embedded_script.request.java_interop_enabled", request.javaInteropEnabled);
    putPayload(payload, "embedded_script.request.node_modules_allowed", "working_directory_commonjs_mvp");
    putPayload(payload, "embedded_script.request.autojs_api_allowed", false);
    putPayload(payload, "embedded_script.request.android_bridge_allowed", false);
    if (runtimeAdapterPath != nullptr && std::strlen(runtimeAdapterPath) > 0) {
        putPayload(payload, "embedded_script.runtime_adapter.path", runtimeAdapterPath);
    }
    if (runtimeAdapterMode != nullptr && std::strlen(runtimeAdapterMode) > 0) {
        putPayload(payload, "embedded_script.runtime_adapter.mode", runtimeAdapterMode);
    }

    ScopedWorkingDirectory scopedWorkingDirectory;
    std::string workingDirectoryError;
    if (!scopedWorkingDirectory.enter(request.workingDirectory, &workingDirectoryError)) {
        putPayload(payload, "embedded_script.request.working_directory_entered", false);
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", workingDirectoryError);
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_WORKING_DIRECTORY");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        __android_log_print(
                ANDROID_LOG_WARN,
                kLogTag,
                "embedded_script.exit status=failed reason=working_directory elapsed=%lldms",
                elapsedMs(startedAt)
        );
        return payload;
    }
    putPayload(payload, "embedded_script.request.working_directory_entered", !request.workingDirectory.empty());
    const auto executionSourceBuildStartedAt = Clock::now();
    std::string wrappedSource = buildEmbeddedScriptExecutionSource(
            request.source,
            sourceNameText,
            request.workingDirectory,
            request.sandboxRoot,
            request.moduleSources,
            request.runtimeModuleSources,
            request.env,
            request.typeScriptPrecompiledSnapshot,
            request.typeScriptPrecompiledSourceNames,
            request.esmEnabled,
            request.dynamicImportEnabled,
            request.rawNodeNetworkModulesEnabled,
            request.workerThreadsEnabled,
            request.childProcessEnabled,
            request.javaInteropEnabled
    );
    putPayload(
            payload,
            "timing.execution_source_build.ms",
            elapsedMs(executionSourceBuildStartedAt)
    );
    putPayload(
            payload,
            "embedded_script.execution_source.bytes",
            static_cast<long long>(wrappedSource.size())
    );
    const bool processRuntimePersistent = embeddedProcessRuntimePersistentEnabled();
    EmbeddedProcessRuntimeExecution processExecution;
    void* handle = nullptr;
    if (processRuntimePersistent) {
        putPayload(payload, "process_runtime.execution.mode", "persistent_process_fresh_isolate");
        if (!beginEmbeddedProcessRuntimeExecution(payload, processExecution)) {
            putPayload(payload, "embedded_script.status", "failed");
            putPayload(payload, "embedded_script.detail", "persistent Node.js process runtime is unavailable or poisoned");
            putPayload(payload, "embedded_script.succeeded", false);
            putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
            putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_PROCESS_RUNTIME_UNAVAILABLE");
            putCommonEmbeddedProbePayload(payload, 0, startedAt);
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    kLogTag,
                    "embedded_script.exit status=failed reason=process_runtime_unavailable elapsed=%lldms",
                    elapsedMs(startedAt)
            );
            return payload;
        }
        handle = processExecution.libnodeHandle;
    } else {
        handle = probeLoadedLibnodeHandle(payload);
        if (!beginEmbeddedProcessRuntimeOneShotLifecycle(payload, "embedded_script")) {
            putPayload(payload, "embedded_script.status", "failed");
            putPayload(payload, "embedded_script.detail", "legacy one-shot execution requires a fresh process");
            putPayload(payload, "embedded_script.succeeded", false);
            putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
            putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ONE_SHOT_REQUIRES_FRESH_PROCESS");
            putCommonEmbeddedProbePayload(payload, 0, startedAt);
            return payload;
        }
    }
    const char* workingDirectoryPtr = request.workingDirectory.empty() ? nullptr : request.workingDirectory.c_str();
    const bool fullUvDiagnostics = embeddedScriptFullUvDiagnosticsRequested(request);
    putPayload(payload, "embedded_script.uv_diagnostics.mode", fullUvDiagnostics ? "full" : "lightweight");
    putPayload(payload, "embedded_script.uv_diagnostics.full_requested", fullUvDiagnostics);
    bool processExecutionTeardownClean = false;
    try {
        runEmbeddedScriptNodeLifecycle(
                payload,
                handle,
                &wrappedSource,
                "embedded_script",
                workingDirectoryPtr,
                fullUvDiagnostics,
                processRuntimePersistent ? processExecution.platform : nullptr,
                processRuntimePersistent,
                processRuntimePersistent ? &processExecutionTeardownClean : nullptr
        );
    } catch (const std::exception& error) {
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", error.what());
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_NATIVE_EXECUTION_EXCEPTION");
        processExecutionTeardownClean = false;
    } catch (...) {
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "unknown native execution exception");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_NATIVE_EXECUTION_EXCEPTION");
        processExecutionTeardownClean = false;
    }
    if (processRuntimePersistent) {
        finishEmbeddedProcessRuntimeExecution(
                payload,
                processExecution,
                processExecutionTeardownClean,
                "per-execution Node/V8/libuv teardown did not complete cleanly"
        );
    }
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "embedded_script.exit elapsed=%lldms", elapsedMs(startedAt));
    return payload;
}

void runEmbeddedScriptNodeLifecycle(
        std::vector<std::string>& payload,
        void* handle,
        const std::string* sourceOverride,
        const char* sourceLabelOverride,
        const char* workingDirectoryOverride,
        bool fullUvDiagnostics,
        node::MultiIsolatePlatform* processRuntimePlatform,
        bool processRuntimePersistent,
        bool* processRuntimeTeardownClean
) {
    std::lock_guard<std::recursive_mutex> processRuntimeLock(embeddedProcessRuntimeExecutionMutex());
    if (processRuntimeTeardownClean != nullptr) {
        *processRuntimeTeardownClean = false;
    }
    // Former probe-selector parameters, now fixed for script execution.
    const bool diagnoseUvHandles = true;
    const bool closeUvHandles = true;
    const bool runUvLoopCleanup = true;
    const bool createIsolateData = true;
    const bool createEnvironment = true;
    const bool loadEnvironment = true;
    const bool spinEventLoop = true;
    const bool stdoutCapture = true;
    const bool scriptExecution = true;
    const bool stdoutCaptureAuxiliary = true;
    const char* lifecycleLogName = "embedded_script.lifecycle";
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s.start", lifecycleLogName);
    const auto lifecycleStartedAt = Clock::now();
    if (scriptExecution) {
        putPayload(payload, "symbol.count", static_cast<long long>(0));
        putPayload(payload, "timing.symbols.ms", static_cast<long long>(0));
        putPayload(payload, "bootstrap_script.status", "skipped");
        putPayload(payload, "completion_drain.status", "skipped");
        putPayload(payload, "js_result.status", "skipped");
        putPayload(payload, "output_envelope.status", "skipped");
        putPayload(payload, "embedded_script.probe_payload_generation.mode", "script_execution_fast_path");
        putPayload(payload, "embedded_script.probe_payload_generation.omitted_optional_placeholders", true);
    }
    const bool collectUvDiagnostics = diagnoseUvHandles && (!scriptExecution || fullUvDiagnostics);
    putPayload(
            payload,
            "uv.diagnostics.collection",
            collectUvDiagnostics
                    ? "full"
                    : diagnoseUvHandles
                    ? "lightweight_cleanup"
                    : "not_requested"
    );

#if AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE || AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION
    const bool lifecycleCodeEnabled = scriptExecution
            ? AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION != 0
            : AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE != 0;
    if (!lifecycleCodeEnabled) {
        if (scriptExecution) {
            putPayload(payload, "embedded_script.status", "skipped");
            putPayload(payload, "embedded_script.detail", "compile-time embedded script execution is disabled");
            putPayload(payload, "embedded_script.succeeded", false);
            putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        }
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s.done status=skipped reason=build_disabled elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (handle == nullptr) {
        if (scriptExecution) {
            putPayload(payload, "embedded_script.status", "failed");
            putPayload(payload, "embedded_script.detail", "libnode.so handle was unavailable");
            putPayload(payload, "embedded_script.succeeded", false);
            putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        }
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=skipped reason=missing_handle elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }

    SymbolLookup initializeLookup = lookupSymbol(handle, kInitializeOncePerProcessNode24Symbol);
    SymbolLookup teardownLookup = lookupSymbol(handle, kTearDownOncePerProcessSymbol);
    SymbolLookup platformCreateLookup = lookupSymbol(handle, kMultiIsolatePlatformCreateSymbol);
    SymbolLookup v8InitializePlatformLookup = lookupSymbol(handle, kV8InitializePlatformSymbol);
    SymbolLookup v8InitializeLookup = lookupSymbol(handle, kV8InitializeSymbol);
    SymbolLookup uvLoopInitLookup = lookupSymbol(handle, kUvLoopInitSymbol);
    SymbolLookup uvLoopCloseLookup = lookupSymbol(handle, kUvLoopCloseSymbol);
    SymbolLookup allocatorCreateLookup = lookupSymbol(handle, kArrayBufferAllocatorCreateSymbol);
    SymbolLookup newIsolateLookup = lookupSymbol(handle, kNewIsolateRawSymbol);
    SymbolLookup createIsolateDataLookup;
    SymbolLookup freeIsolateDataLookup;
    SymbolLookup newContextLookup;
    SymbolLookup createEnvironmentLookup;
    SymbolLookup freeEnvironmentLookup;
    SymbolLookup loadEnvironmentLookup;
    SymbolLookup spinEventLoopLookup;
    SymbolLookup isolateEnterLookup;
    SymbolLookup isolateExitLookup;
    SymbolLookup handleScopeConstructorLookup;
    SymbolLookup handleScopeDestructorLookup;
    SymbolLookup contextEnterLookup;
    SymbolLookup contextExitLookup;
    SymbolLookup contextGlobalLookup;
    SymbolLookup stringNewFromUtf8Lookup;
    SymbolLookup objectGetLookup;
    SymbolLookup valueToStringLookup;
    SymbolLookup stringUtf8ValueConstructorLookup;
    SymbolLookup stringUtf8ValueDestructorLookup;
    SymbolLookup platformDisposeIsolateLookup =
            lookupSymbol(handle, kMultiIsolatePlatformDisposeIsolateSymbol);
    SymbolLookup uvWalkLookup;
    SymbolLookup uvHandleGetTypeLookup;
    SymbolLookup uvIsActiveLookup;
    SymbolLookup uvIsClosingLookup;
    SymbolLookup uvCloseLookup;
    SymbolLookup uvRunLookup;
    if (diagnoseUvHandles) {
        uvWalkLookup = lookupSymbol(handle, kUvWalkSymbol);
        uvHandleGetTypeLookup = lookupSymbol(handle, kUvHandleGetTypeSymbol);
        uvIsActiveLookup = lookupSymbol(handle, kUvIsActiveSymbol);
        uvIsClosingLookup = lookupSymbol(handle, kUvIsClosingSymbol);
    }
    if (closeUvHandles) {
        uvCloseLookup = lookupSymbol(handle, kUvCloseSymbol);
    }
    if (runUvLoopCleanup) {
        uvRunLookup = lookupSymbol(handle, kUvRunSymbol);
    }
    if (createIsolateData) {
        createIsolateDataLookup = lookupSymbol(handle, kCreateIsolateDataRawSymbol);
        freeIsolateDataLookup = lookupSymbol(handle, kFreeIsolateDataSymbol);
    }
    if (createEnvironment) {
        newContextLookup = lookupSymbol(handle, kNewContextSymbol);
        createEnvironmentLookup = lookupSymbol(handle, kCreateEnvironmentRawSymbol);
        freeEnvironmentLookup = lookupSymbol(handle, kFreeEnvironmentSymbol);
        isolateEnterLookup = lookupSymbol(handle, kV8IsolateEnterSymbol);
        isolateExitLookup = lookupSymbol(handle, kV8IsolateExitSymbol);
        handleScopeConstructorLookup = lookupSymbol(handle, kV8HandleScopeConstructorSymbol);
        handleScopeDestructorLookup = lookupSymbol(handle, kV8HandleScopeDestructorSymbol);
        contextEnterLookup = lookupSymbol(handle, kV8ContextEnterSymbol);
        contextExitLookup = lookupSymbol(handle, kV8ContextExitSymbol);
    }
    if (loadEnvironment) {
        loadEnvironmentLookup = lookupSymbol(handle, kLoadEnvironmentSourceSymbol);
    }
    if (spinEventLoop) {
        spinEventLoopLookup = lookupSymbol(handle, kSpinEventLoopSymbol);
    }
    const bool readGlobalResult = scriptExecution;
    if (readGlobalResult) {
        contextGlobalLookup = lookupSymbol(handle, kV8ContextGlobalSymbol);
        stringNewFromUtf8Lookup = lookupSymbol(handle, kV8StringNewFromUtf8Symbol);
        objectGetLookup = lookupSymbol(handle, kV8ObjectGetSymbol);
        valueToStringLookup = lookupSymbol(handle, kV8ValueToStringSymbol);
        stringUtf8ValueConstructorLookup = lookupSymbol(handle, kV8StringUtf8ValueConstructorSymbol);
        stringUtf8ValueDestructorLookup = lookupSymbol(handle, kV8StringUtf8ValueDestructorSymbol);
    }

    if (!initializeLookup.found || initializeLookup.address == nullptr) {
        const std::string detail = "InitializeOncePerProcess Node 24 symbol is missing: " + initializeLookup.error;
        putPayload(payload, "initialize.status", "failed");
        putPayload(payload, "initialize.detail", detail);
        putPayload(payload, "teardown.status", "skipped");
        putPayload(payload, "teardown.detail", "InitializeOncePerProcess was not called");
        putPayload(payload, "platform.create.detail", "InitializeOncePerProcess symbol is unavailable");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "initialize.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!teardownLookup.found || teardownLookup.address == nullptr) {
        const std::string detail = "TearDownOncePerProcess symbol is missing: " + teardownLookup.error;
        putPayload(payload, "initialize.status", "skipped");
        putPayload(payload, "initialize.detail", "TearDownOncePerProcess symbol is unavailable");
        putPayload(payload, "teardown.status", "failed");
        putPayload(payload, "teardown.detail", detail);
        putPayload(payload, "platform.create.detail", "TearDownOncePerProcess symbol is unavailable");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "teardown.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!platformCreateLookup.found || platformCreateLookup.address == nullptr) {
        const std::string detail = "MultiIsolatePlatform::Create symbol is missing: " + platformCreateLookup.error;
        putPayload(payload, "platform.create.status", "failed");
        putPayload(payload, "platform.create.detail", detail);
        putPayload(payload, "allocator.create.detail", "MultiIsolatePlatform::Create was not available");
        putPayload(payload, "isolate.create.detail", "MultiIsolatePlatform::Create was not available");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "platform.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!v8InitializePlatformLookup.found || v8InitializePlatformLookup.address == nullptr) {
        const std::string detail = "v8::V8::InitializePlatform symbol is missing: " + v8InitializePlatformLookup.error;
        putPayload(payload, "v8.initialize_platform.status", "failed");
        putPayload(payload, "v8.initialize_platform.detail", detail);
        putPayload(payload, "v8.initialize.detail", "V8::InitializePlatform symbol is unavailable");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize_platform.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!v8InitializeLookup.found || v8InitializeLookup.address == nullptr) {
        const std::string detail = "v8::V8::Initialize(int) symbol is missing: " + v8InitializeLookup.error;
        putPayload(payload, "v8.initialize.status", "failed");
        putPayload(payload, "v8.initialize.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!uvLoopInitLookup.found || uvLoopInitLookup.address == nullptr) {
        const std::string detail = "uv_loop_init symbol is missing: " + uvLoopInitLookup.error;
        putPayload(payload, "uv.loop.init.status", "failed");
        putPayload(payload, "uv.loop.init.detail", detail);
        putPayload(payload, "uv.loop.close.detail", "uv_loop_init was not called");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.init.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!uvLoopCloseLookup.found || uvLoopCloseLookup.address == nullptr) {
        const std::string detail = "uv_loop_close symbol is missing: " + uvLoopCloseLookup.error;
        putPayload(payload, "uv.loop.init.status", "skipped");
        putPayload(payload, "uv.loop.init.detail", "uv_loop_close symbol is unavailable");
        putPayload(payload, "uv.loop.close.status", "failed");
        putPayload(payload, "uv.loop.close.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.close.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!allocatorCreateLookup.found || allocatorCreateLookup.address == nullptr) {
        const std::string detail = "ArrayBufferAllocator::Create symbol is missing: " + allocatorCreateLookup.error;
        putPayload(payload, "allocator.create.status", "failed");
        putPayload(payload, "allocator.create.detail", detail);
        putPayload(payload, "isolate.create.detail", "ArrayBufferAllocator::Create was not available");
        putPayload(payload, "isolate.dispose.detail", "isolate was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "allocator.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!newIsolateLookup.found || newIsolateLookup.address == nullptr) {
        const std::string detail = "node::NewIsolate raw pointer symbol is missing: " + newIsolateLookup.error;
        putPayload(payload, "isolate.create.status", "failed");
        putPayload(payload, "isolate.create.detail", detail);
        putPayload(payload, "isolate.dispose.detail", "isolate was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createIsolateData && (!createIsolateDataLookup.found || createIsolateDataLookup.address == nullptr)) {
        const std::string detail = "node::CreateIsolateData raw pointer symbol is missing: " + createIsolateDataLookup.error;
        putPayload(payload, "isolate_data.create.status", "failed");
        putPayload(payload, "isolate_data.create.detail", detail);
        putPayload(payload, "isolate_data.free.detail", "isolate data was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createIsolateData && (!freeIsolateDataLookup.found || freeIsolateDataLookup.address == nullptr)) {
        const std::string detail = "node::FreeIsolateData symbol is missing: " + freeIsolateDataLookup.error;
        putPayload(payload, "isolate_data.create.detail", "FreeIsolateData symbol is unavailable");
        putPayload(payload, "isolate_data.free.status", "failed");
        putPayload(payload, "isolate_data.free.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.free.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!newContextLookup.found || newContextLookup.address == nullptr)) {
        const std::string detail = "node::NewContext symbol is missing: " + newContextLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "environment.free.detail", "environment was not created");
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!createEnvironmentLookup.found || createEnvironmentLookup.address == nullptr)) {
        const std::string detail = "node::CreateEnvironment symbol is missing: " + createEnvironmentLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "environment.free.detail", "environment was not created");
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!freeEnvironmentLookup.found || freeEnvironmentLookup.address == nullptr)) {
        const std::string detail = "node::FreeEnvironment symbol is missing: " + freeEnvironmentLookup.error;
        putPayload(payload, "environment.create.detail", "FreeEnvironment symbol is unavailable");
        putPayload(payload, "environment.free.status", "failed");
        putPayload(payload, "environment.free.detail", detail);
        putPayload(payload, "load_environment.detail", "environment cleanup unavailable");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.free.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (loadEnvironment && (!loadEnvironmentLookup.found || loadEnvironmentLookup.address == nullptr)) {
        const std::string detail = "node::LoadEnvironment string_view symbol is missing: " + loadEnvironmentLookup.error;
        putPayload(payload, "load_environment.status", "failed");
        putPayload(payload, "load_environment.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "load_environment.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (spinEventLoop && (!spinEventLoopLookup.found || spinEventLoopLookup.address == nullptr)) {
        const std::string detail = "node::SpinEventLoop symbol is missing: " + spinEventLoopLookup.error;
        putPayload(payload, "spin_event_loop.status", "failed");
        putPayload(payload, "spin_event_loop.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "spin_event_loop.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!contextGlobalLookup.found || contextGlobalLookup.address == nullptr)) {
        const std::string detail = "v8::Context::Global symbol is missing: " + contextGlobalLookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!stringNewFromUtf8Lookup.found || stringNewFromUtf8Lookup.address == nullptr)) {
        const std::string detail = "v8::String::NewFromUtf8 symbol is missing: " + stringNewFromUtf8Lookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!objectGetLookup.found || objectGetLookup.address == nullptr)) {
        const std::string detail = "v8::Object::Get symbol is missing: " + objectGetLookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!valueToStringLookup.found || valueToStringLookup.address == nullptr)) {
        const std::string detail = "v8::Value::ToString symbol is missing: " + valueToStringLookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!stringUtf8ValueConstructorLookup.found || stringUtf8ValueConstructorLookup.address == nullptr)) {
        const std::string detail = "v8::String::Utf8Value constructor symbol is missing: " + stringUtf8ValueConstructorLookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (readGlobalResult && (!stringUtf8ValueDestructorLookup.found || stringUtf8ValueDestructorLookup.address == nullptr)) {
        const std::string detail = "v8::String::Utf8Value destructor symbol is missing: " + stringUtf8ValueDestructorLookup.error;
        putPayload(payload, "js_result.status", "failed");
        putPayload(payload, "js_result.detail", detail);
        putPayload(payload, "stdout_write.status", "failed");
        putPayload(payload, "stdout_write.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "js_result.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!isolateEnterLookup.found || isolateEnterLookup.address == nullptr)) {
        const std::string detail = "v8::Isolate::Enter symbol is missing: " + isolateEnterLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!isolateExitLookup.found || isolateExitLookup.address == nullptr)) {
        const std::string detail = "v8::Isolate::Exit symbol is missing: " + isolateExitLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!handleScopeConstructorLookup.found || handleScopeConstructorLookup.address == nullptr)) {
        const std::string detail = "v8::HandleScope constructor symbol is missing: " + handleScopeConstructorLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!handleScopeDestructorLookup.found || handleScopeDestructorLookup.address == nullptr)) {
        const std::string detail = "v8::HandleScope destructor symbol is missing: " + handleScopeDestructorLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!contextEnterLookup.found || contextEnterLookup.address == nullptr)) {
        const std::string detail = "v8::Context::Enter symbol is missing: " + contextEnterLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (createEnvironment && (!contextExitLookup.found || contextExitLookup.address == nullptr)) {
        const std::string detail = "v8::Context::Exit symbol is missing: " + contextExitLookup.error;
        putPayload(payload, "environment.create.status", "failed");
        putPayload(payload, "environment.create.detail", detail);
        putPayload(payload, "load_environment.detail", "environment was not created");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (!platformDisposeIsolateLookup.found || platformDisposeIsolateLookup.address == nullptr) {
        const std::string detail =
                "node::MultiIsolatePlatform::DisposeIsolate symbol is missing: " +
                platformDisposeIsolateLookup.error;
        putPayload(payload, "isolate.dispose.status", "failed");
        putPayload(payload, "isolate.dispose.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.dispose.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (diagnoseUvHandles && (!uvWalkLookup.found || uvWalkLookup.address == nullptr)) {
        const std::string detail = "uv_walk symbol is missing: " + uvWalkLookup.error;
        putPayload(payload, "uv.diagnostics.status", "failed");
        putPayload(payload, "uv.diagnostics.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (diagnoseUvHandles && (!uvHandleGetTypeLookup.found || uvHandleGetTypeLookup.address == nullptr)) {
        const std::string detail = "uv_handle_get_type symbol is missing: " + uvHandleGetTypeLookup.error;
        putPayload(payload, "uv.diagnostics.status", "failed");
        putPayload(payload, "uv.diagnostics.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (diagnoseUvHandles && (!uvIsActiveLookup.found || uvIsActiveLookup.address == nullptr)) {
        const std::string detail = "uv_is_active symbol is missing: " + uvIsActiveLookup.error;
        putPayload(payload, "uv.diagnostics.status", "failed");
        putPayload(payload, "uv.diagnostics.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (diagnoseUvHandles && (!uvIsClosingLookup.found || uvIsClosingLookup.address == nullptr)) {
        const std::string detail = "uv_is_closing symbol is missing: " + uvIsClosingLookup.error;
        putPayload(payload, "uv.diagnostics.status", "failed");
        putPayload(payload, "uv.diagnostics.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (closeUvHandles && (!uvCloseLookup.found || uvCloseLookup.address == nullptr)) {
        const std::string detail = "uv_close symbol is missing: " + uvCloseLookup.error;
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }
    if (runUvLoopCleanup && (!uvRunLookup.found || uvRunLookup.address == nullptr)) {
        const std::string detail = "uv_run symbol is missing: " + uvRunLookup.error;
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", detail);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed reason=%s", detail.c_str());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }

    auto initializeOncePerProcess =
            reinterpret_cast<NodeInitializeOncePerProcess>(initializeLookup.address);
    auto tearDownOncePerProcess =
            reinterpret_cast<NodeTearDownOncePerProcess>(teardownLookup.address);
    auto createPlatform =
            reinterpret_cast<NodeMultiIsolatePlatformCreate>(platformCreateLookup.address);
    auto v8InitializePlatform =
            reinterpret_cast<V8InitializePlatform>(v8InitializePlatformLookup.address);
    auto v8Initialize =
            reinterpret_cast<V8Initialize>(v8InitializeLookup.address);
    auto uvLoopInit =
            reinterpret_cast<UvLoopInit>(uvLoopInitLookup.address);
    auto uvLoopClose =
            reinterpret_cast<UvLoopClose>(uvLoopCloseLookup.address);
    auto createAllocator =
            reinterpret_cast<NodeArrayBufferAllocatorCreate>(allocatorCreateLookup.address);
    auto newIsolate =
            reinterpret_cast<NodeNewIsolateRaw>(newIsolateLookup.address);
    auto createIsolateDataRaw =
            reinterpret_cast<NodeCreateIsolateDataRaw>(createIsolateData ? createIsolateDataLookup.address : nullptr);
    auto freeIsolateData =
            reinterpret_cast<NodeFreeIsolateData>(createIsolateData ? freeIsolateDataLookup.address : nullptr);
    auto newContext =
            reinterpret_cast<NodeNewContext>(createEnvironment ? newContextLookup.address : nullptr);
    auto createEnvironmentRaw =
            reinterpret_cast<NodeCreateEnvironmentRaw>(createEnvironment ? createEnvironmentLookup.address : nullptr);
    auto freeEnvironment =
            reinterpret_cast<NodeFreeEnvironment>(createEnvironment ? freeEnvironmentLookup.address : nullptr);
    auto loadEnvironmentSource =
            reinterpret_cast<NodeLoadEnvironmentSource>(loadEnvironment ? loadEnvironmentLookup.address : nullptr);
    auto spinEventLoopRaw =
            reinterpret_cast<NodeSpinEventLoop>(spinEventLoop ? spinEventLoopLookup.address : nullptr);
    auto getContextGlobal =
            reinterpret_cast<V8ContextGlobal>(readGlobalResult ? contextGlobalLookup.address : nullptr);
    auto newStringFromUtf8 =
            reinterpret_cast<V8StringNewFromUtf8>(readGlobalResult ? stringNewFromUtf8Lookup.address : nullptr);
    auto getObjectProperty =
            reinterpret_cast<V8ObjectGet>(readGlobalResult ? objectGetLookup.address : nullptr);
    auto toString =
            reinterpret_cast<V8ValueToString>(readGlobalResult ? valueToStringLookup.address : nullptr);
    auto constructUtf8Value =
            reinterpret_cast<V8StringUtf8ValueConstructor>(readGlobalResult ? stringUtf8ValueConstructorLookup.address : nullptr);
    auto destructUtf8Value =
            reinterpret_cast<V8StringUtf8ValueDestructor>(readGlobalResult ? stringUtf8ValueDestructorLookup.address : nullptr);
    auto enterIsolate =
            reinterpret_cast<V8IsolateEnter>(createEnvironment ? isolateEnterLookup.address : nullptr);
    auto exitIsolate =
            reinterpret_cast<V8IsolateExit>(createEnvironment ? isolateExitLookup.address : nullptr);
    auto constructHandleScope =
            reinterpret_cast<V8HandleScopeConstructor>(createEnvironment ? handleScopeConstructorLookup.address : nullptr);
    auto destructHandleScope =
            reinterpret_cast<V8HandleScopeDestructor>(createEnvironment ? handleScopeDestructorLookup.address : nullptr);
    auto enterContext =
            reinterpret_cast<V8ContextEnter>(createEnvironment ? contextEnterLookup.address : nullptr);
    auto exitContext =
            reinterpret_cast<V8ContextExit>(createEnvironment ? contextExitLookup.address : nullptr);
    auto disposeIsolate = reinterpret_cast<NodeMultiIsolatePlatformDisposeIsolate>(
            platformDisposeIsolateLookup.address
    );
    auto uvWalk =
            reinterpret_cast<UvWalk>(diagnoseUvHandles ? uvWalkLookup.address : nullptr);
    auto uvHandleGetType =
            reinterpret_cast<UvHandleGetType>(diagnoseUvHandles ? uvHandleGetTypeLookup.address : nullptr);
    auto uvIsActive =
            reinterpret_cast<UvIsActive>(diagnoseUvHandles ? uvIsActiveLookup.address : nullptr);
    auto uvIsClosing =
            reinterpret_cast<UvIsClosing>(diagnoseUvHandles ? uvIsClosingLookup.address : nullptr);
    auto uvClose =
            reinterpret_cast<UvClose>(closeUvHandles ? uvCloseLookup.address : nullptr);
    auto uvRun =
            reinterpret_cast<UvRun>(runUvLoopCleanup ? uvRunLookup.address : nullptr);

    const int buildConfiguration = v8BuildConfiguration();
    std::vector<std::string> args = {"autojs6-embedded-script"};
    const auto flags = embeddedNodeInitializationFlags(stdoutCapture);
    const char* flagsName = embeddedNodeInitializationFlagsName(stdoutCapture);
    putPayload(payload, "initialize.symbol", initializeLookup.symbol);
    putPayload(payload, "initialize.flags", flagsName);
    putPayload(payload, "teardown.symbol", teardownLookup.symbol);
    putPayload(payload, "platform.create.symbol", platformCreateLookup.symbol);
    putPayload(payload, "v8.initialize_platform.symbol", v8InitializePlatformLookup.symbol);
    putPayload(payload, "v8.initialize.symbol", v8InitializeLookup.symbol);
    putPayload(payload, "v8.initialize.build_config", static_cast<long long>(buildConfiguration));
    putPayload(payload, "uv.loop.init.symbol", uvLoopInitLookup.symbol);
    putPayload(payload, "uv.loop.close.symbol", uvLoopCloseLookup.symbol);
    putPayload(payload, "allocator.create.symbol", allocatorCreateLookup.symbol);
    putPayload(payload, "isolate.create.symbol", newIsolateLookup.symbol);
    putPayload(payload, "isolate.create.loop", "non_null");
    putPayload(payload, "isolate.dispose.symbol", platformDisposeIsolateLookup.symbol);
    putPayload(payload, "isolate.dispose.strategy", "platform_deinitialize_unregister_free");
    if (createIsolateData) {
        putPayload(payload, "isolate_data.create.symbol", createIsolateDataLookup.symbol);
        putPayload(payload, "isolate_data.free.symbol", freeIsolateDataLookup.symbol);
    }
    if (createEnvironment) {
        putPayload(payload, "environment.new_context.symbol", newContextLookup.symbol);
        putPayload(payload, "environment.create.symbol", createEnvironmentLookup.symbol);
        putPayload(payload, "environment.free.symbol", freeEnvironmentLookup.symbol);
        putPayload(payload, "environment.isolate_enter.symbol", isolateEnterLookup.symbol);
        putPayload(payload, "environment.isolate_exit.symbol", isolateExitLookup.symbol);
        putPayload(payload, "environment.handle_scope.ctor.symbol", handleScopeConstructorLookup.symbol);
        putPayload(payload, "environment.handle_scope.dtor.symbol", handleScopeDestructorLookup.symbol);
        putPayload(payload, "environment.context_enter.symbol", contextEnterLookup.symbol);
        putPayload(payload, "environment.context_exit.symbol", contextExitLookup.symbol);
    }
    if (loadEnvironment) {
        putPayload(payload, "load_environment.symbol", loadEnvironmentLookup.symbol);
        putPayload(
                payload,
                "load_environment.source",
                "empty_string"
        );
    }
    if (spinEventLoop) {
        putPayload(payload, "spin_event_loop.symbol", spinEventLoopLookup.symbol);
    }
    if (diagnoseUvHandles) {
        putPayload(payload, "uv.diagnostics.walk.symbol", uvWalkLookup.symbol);
        putPayload(payload, "uv.diagnostics.handle_get_type.symbol", uvHandleGetTypeLookup.symbol);
        putPayload(payload, "uv.diagnostics.is_active.symbol", uvIsActiveLookup.symbol);
        putPayload(payload, "uv.diagnostics.is_closing.symbol", uvIsClosingLookup.symbol);
    }
    if (closeUvHandles) {
        putPayload(payload, "uv.close.symbol", uvCloseLookup.symbol);
    }
    if (runUvLoopCleanup) {
        putPayload(payload, "uv.run.symbol", uvRunLookup.symbol);
    }
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "v8.initialize_platform.symbol.found symbol=%s", v8InitializePlatformLookup.symbol.c_str());
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "v8.initialize.symbol.found symbol=%s", v8InitializeLookup.symbol.c_str());

    bool initialized = processRuntimePersistent;
    bool lifecycleFailed = false;
    bool executionTeardownClean = true;
    bool v8PlatformInitialized = processRuntimePersistent;
    bool v8Initialized = processRuntimePersistent;
    bool uvLoopInitialized = false;
    bool uvLoopCloseAttempted = false;
    bool uvLoopClosed = false;
    std::shared_ptr<node::InitializationResult> initializationResult;
    LifecyclePlatformRef platform(processRuntimePersistent ? processRuntimePlatform : nullptr);
    std::unique_ptr<uv_loop_t> eventLoop;
    std::unique_ptr<node::ArrayBufferAllocator> allocator;
    v8::Isolate* isolate = nullptr;
    node::IsolateData* isolateData = nullptr;
    node::Environment* environment = nullptr;
    bool loadEnvironmentSucceeded = false;
    ScopedFdOutputCapture outputCapture;
    bool stdoutCaptureStarted = false;
    bool stdoutCaptureFailed = false;
    Clock::time_point stdoutCaptureStartedAt = Clock::now();

    if (stdoutCapture) {
        stdoutCaptureStartedAt = Clock::now();
        std::string captureError;
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "stdout_capture.start method=fd_pipe phase=before_initialize expected=%s",
                kStdoutCaptureExpectedText
        );
        stdoutCaptureStarted = outputCapture.start(captureError);
        if (stdoutCaptureStarted) {
            putPayload(payload, "stdout_capture.status", "running");
            putPayload(payload, "stdout_capture.detail", "fd/pipe stdout capture started before InitializeOncePerProcess");
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "stdout_capture.start.done phase=before_initialize");
        } else {
            stdoutCaptureFailed = true;
            if (!stdoutCaptureAuxiliary) {
                lifecycleFailed = true;
            }
            putPayload(payload, "stdout_capture.status", "failed");
            putPayload(payload, "stdout_capture.detail", captureError.empty() ? "fd/pipe stdout capture did not start" : captureError);
            putPayload(payload, "stdout_capture.bytes", static_cast<long long>(0));
            putPayload(payload, "stdout_capture.text", "");
            putPayload(payload, "stdout_capture.truncated", false);
            putPayload(payload, "stdout_capture.contains_expected", false);
            putPayload(payload, "stderr_capture.bytes", static_cast<long long>(0));
            putPayload(payload, "stderr_capture.text", "");
            putPayload(payload, "stderr_capture.truncated", false);
            putPayload(payload, "stderr_capture.contains_expected", false);
            putPayload(payload, "timing.stdout_capture.ms", elapsedMs(stdoutCaptureStartedAt));
            __android_log_print(
                    ANDROID_LOG_WARN,
                    kLogTag,
                    "stdout_capture.start.failed phase=before_initialize error=%s",
                    captureError.c_str()
            );
        }
    }

    if (processRuntimePersistent) {
        putPayload(payload, "initialize.status", "reused");
        putPayload(payload, "initialize.detail", "process-global InitializeOncePerProcess state was reused");
        putPayload(payload, "initialize.result", "process_runtime_ready");
        putPayload(payload, "timing.initialize.ms", static_cast<long long>(0));
        putPayload(payload, "platform.create.status", "reused");
        putPayload(payload, "platform.create.detail", "Node-owned process-global MultiIsolatePlatform was reused");
        putPayload(payload, "timing.platform_create.ms", static_cast<long long>(0));
        putPayload(payload, "v8.initialize_platform.status", "reused");
        putPayload(payload, "v8.initialize_platform.detail", "Node initialized the process-global V8 platform");
        putPayload(payload, "timing.v8_initialize_platform.ms", static_cast<long long>(0));
        putPayload(payload, "v8.initialize.status", "reused");
        putPayload(payload, "v8.initialize.detail", "Node initialized process-global V8 and cppgc state");
        putPayload(payload, "timing.v8_initialize.ms", static_cast<long long>(0));
    } else {
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "initialize.start symbol=%s flags=%s",
            initializeLookup.symbol.c_str(),
            flagsName
    );
    const auto initializeStartedAt = Clock::now();
    try {
        initializationResult = initializeOncePerProcess(args, flags);
        initialized = true;
        putPayload(payload, "initialize.status", "done");
        putPayload(
                payload,
                "initialize.detail",
                initializationResult == nullptr
                        ? "InitializeOncePerProcess returned null"
                        : "InitializeOncePerProcess returned"
        );
        putPayload(payload, "initialize.result", initializationResult == nullptr ? "null" : "non_null");
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "initialize.done elapsed=%lldms result=%s",
                elapsedMs(initializeStartedAt),
                initializationResult == nullptr ? "null" : "non_null"
        );
    } catch (const std::exception& e) {
        lifecycleFailed = true;
        putPayload(payload, "initialize.status", "failed");
        putPayload(payload, "initialize.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "initialize.failed elapsed=%lldms error=%s", elapsedMs(initializeStartedAt), e.what());
    } catch (...) {
        lifecycleFailed = true;
        putPayload(payload, "initialize.status", "failed");
        putPayload(payload, "initialize.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "initialize.failed elapsed=%lldms error=unknown", elapsedMs(initializeStartedAt));
    }
    putPayload(payload, "timing.initialize.ms", elapsedMs(initializeStartedAt));

    if (!initialized) {
        putPayload(payload, "teardown.status", "skipped");
        putPayload(payload, "teardown.detail", "InitializeOncePerProcess did not complete");
        putPayload(payload, "timing.teardown.ms", static_cast<long long>(0));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.done status=failed elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
        return;
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "platform.create.start symbol=%s threadPoolSize=1",
            platformCreateLookup.symbol.c_str()
    );
    const auto platformStartedAt = Clock::now();
    try {
        platform = createPlatform(1, nullptr, nullptr);
        putPayload(payload, "platform.create.status", platform == nullptr ? "failed" : "done");
        putPayload(
                payload,
                "platform.create.detail",
                platform == nullptr ? "MultiIsolatePlatform::Create returned null" : "MultiIsolatePlatform::Create returned"
        );
        if (platform == nullptr) {
            lifecycleFailed = true;
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "platform.create.failed elapsed=%lldms error=null_result", elapsedMs(platformStartedAt));
        } else {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "platform.create.done elapsed=%lldms", elapsedMs(platformStartedAt));
        }
    } catch (const std::exception& e) {
        lifecycleFailed = true;
        putPayload(payload, "platform.create.status", "failed");
        putPayload(payload, "platform.create.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "platform.create.failed elapsed=%lldms error=%s", elapsedMs(platformStartedAt), e.what());
    } catch (...) {
        lifecycleFailed = true;
        putPayload(payload, "platform.create.status", "failed");
        putPayload(payload, "platform.create.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "platform.create.failed elapsed=%lldms error=unknown", elapsedMs(platformStartedAt));
    }
    putPayload(payload, "timing.platform_create.ms", elapsedMs(platformStartedAt));

    if (platform != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "v8.initialize_platform.start symbol=%s platform=non_null",
                v8InitializePlatformLookup.symbol.c_str()
        );
        const auto v8InitializePlatformStartedAt = Clock::now();
        try {
            v8InitializePlatform(static_cast<v8::Platform*>(platform.get()));
            v8PlatformInitialized = true;
            putPayload(payload, "v8.initialize_platform.status", "done");
            putPayload(payload, "v8.initialize_platform.detail", "V8::InitializePlatform returned");
            putPayload(payload, "v8.initialize_platform.result", "returned");
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "v8.initialize_platform.done elapsed=%lldms", elapsedMs(v8InitializePlatformStartedAt));
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "v8.initialize_platform.status", "failed");
            putPayload(payload, "v8.initialize_platform.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize_platform.failed elapsed=%lldms error=%s", elapsedMs(v8InitializePlatformStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "v8.initialize_platform.status", "failed");
            putPayload(payload, "v8.initialize_platform.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize_platform.failed elapsed=%lldms error=unknown", elapsedMs(v8InitializePlatformStartedAt));
        }
        putPayload(payload, "timing.v8_initialize_platform.ms", elapsedMs(v8InitializePlatformStartedAt));
    }

    if (v8PlatformInitialized) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "v8.initialize.start symbol=%s build_config=%d",
                v8InitializeLookup.symbol.c_str(),
                buildConfiguration
        );
        const auto v8InitializeStartedAt = Clock::now();
        try {
            const bool result = v8Initialize(buildConfiguration);
            v8Initialized = result;
            putPayload(payload, "v8.initialize.result", result);
            putPayload(payload, "v8.initialize.status", result ? "done" : "failed");
            putPayload(payload, "v8.initialize.detail", result ? "V8::Initialize returned true" : "V8::Initialize returned false");
            if (result) {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "v8.initialize.done elapsed=%lldms result=true", elapsedMs(v8InitializeStartedAt));
            } else {
                lifecycleFailed = true;
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize.failed elapsed=%lldms result=false", elapsedMs(v8InitializeStartedAt));
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "v8.initialize.status", "failed");
            putPayload(payload, "v8.initialize.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize.failed elapsed=%lldms error=%s", elapsedMs(v8InitializeStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "v8.initialize.status", "failed");
            putPayload(payload, "v8.initialize.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "v8.initialize.failed elapsed=%lldms error=unknown", elapsedMs(v8InitializeStartedAt));
        }
        putPayload(payload, "timing.v8_initialize.ms", elapsedMs(v8InitializeStartedAt));
    }
    }

    if (v8Initialized) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.loop.init.start symbol=%s",
                uvLoopInitLookup.symbol.c_str()
        );
        const auto uvLoopInitStartedAt = Clock::now();
        try {
            eventLoop = std::make_unique<uv_loop_t>();
            const int result = uvLoopInit(eventLoop.get());
            putPayload(payload, "uv.loop.init.result", static_cast<long long>(result));
            if (result == 0) {
                uvLoopInitialized = true;
                putPayload(payload, "uv.loop.init.status", "done");
                putPayload(payload, "uv.loop.init.detail", "uv_loop_init returned 0");
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.loop.init.done elapsed=%lldms", elapsedMs(uvLoopInitStartedAt));
            } else {
                lifecycleFailed = true;
                const std::string detail = uvLoopResultDetail("uv_loop_init", result);
                putPayload(payload, "uv.loop.init.status", "failed");
                putPayload(payload, "uv.loop.init.detail", detail);
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.init.failed elapsed=%lldms result=%d", elapsedMs(uvLoopInitStartedAt), result);
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "uv.loop.init.status", "failed");
            putPayload(payload, "uv.loop.init.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.init.failed elapsed=%lldms error=%s", elapsedMs(uvLoopInitStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "uv.loop.init.status", "failed");
            putPayload(payload, "uv.loop.init.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.init.failed elapsed=%lldms error=unknown", elapsedMs(uvLoopInitStartedAt));
        }
        putPayload(payload, "timing.uv_loop_init.ms", elapsedMs(uvLoopInitStartedAt));
    }

    if (v8Initialized && uvLoopInitialized) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "allocator.create.start symbol=%s",
                allocatorCreateLookup.symbol.c_str()
        );
        const auto allocatorStartedAt = Clock::now();
        try {
            allocator = createAllocator(false);
            putPayload(payload, "allocator.create.status", allocator == nullptr ? "failed" : "done");
            putPayload(
                    payload,
                    "allocator.create.detail",
                    allocator == nullptr ? "ArrayBufferAllocator::Create returned null" : "ArrayBufferAllocator::Create returned"
            );
            if (allocator == nullptr) {
                lifecycleFailed = true;
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "allocator.create.failed elapsed=%lldms error=null_result", elapsedMs(allocatorStartedAt));
            } else {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "allocator.create.done elapsed=%lldms", elapsedMs(allocatorStartedAt));
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "allocator.create.status", "failed");
            putPayload(payload, "allocator.create.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "allocator.create.failed elapsed=%lldms error=%s", elapsedMs(allocatorStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "allocator.create.status", "failed");
            putPayload(payload, "allocator.create.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "allocator.create.failed elapsed=%lldms error=unknown", elapsedMs(allocatorStartedAt));
        }
        putPayload(payload, "timing.allocator_create.ms", elapsedMs(allocatorStartedAt));
    }

    if (v8Initialized && uvLoopInitialized && platform != nullptr && allocator != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "isolate.create.start symbol=%s loop=non_null snapshot=null",
                newIsolateLookup.symbol.c_str()
        );
        const auto isolateStartedAt = Clock::now();
        try {
            node::IsolateSettings isolateSettings;
            isolate = newIsolate(allocator.get(), eventLoop.get(), platform.get(), nullptr, isolateSettings);
            putPayload(payload, "isolate.create.status", isolate == nullptr ? "failed" : "done");
            putPayload(payload, "isolate.create.detail", isolate == nullptr ? "node::NewIsolate returned null" : "node::NewIsolate returned");
            putPayload(payload, "isolate.create.result", isolate == nullptr ? "null" : "non_null");
            if (isolate == nullptr) {
                lifecycleFailed = true;
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.create.failed elapsed=%lldms result=null", elapsedMs(isolateStartedAt));
            } else {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "isolate.create.done elapsed=%lldms result=non_null", elapsedMs(isolateStartedAt));
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "isolate.create.status", "failed");
            putPayload(payload, "isolate.create.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.create.failed elapsed=%lldms error=%s", elapsedMs(isolateStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "isolate.create.status", "failed");
            putPayload(payload, "isolate.create.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.create.failed elapsed=%lldms error=unknown", elapsedMs(isolateStartedAt));
        }
        putPayload(payload, "timing.isolate_create.ms", elapsedMs(isolateStartedAt));
    }

    if (createIsolateData && isolate != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "isolate_data.create.start symbol=%s",
                createIsolateDataLookup.symbol.c_str()
        );
        const auto isolateDataCreateStartedAt = Clock::now();
        try {
            isolateData = createIsolateDataRaw(
                    isolate,
                    eventLoop.get(),
                    platform.get(),
                    allocator.get(),
                    nullptr
            );
            putPayload(payload, "isolate_data.pointer", pointerToHex(isolateData));
            putPayload(payload, "isolate_data.create.status", isolateData == nullptr ? "failed" : "done");
            putPayload(
                    payload,
                    "isolate_data.create.detail",
                    isolateData == nullptr ? "CreateIsolateData returned null" : "CreateIsolateData returned"
            );
            if (isolateData == nullptr) {
                lifecycleFailed = true;
                putPayload(payload, "isolate_data.free.detail", "isolate data was not created");
                if (createEnvironment) {
                    putPayload(payload, "environment.create.detail", "isolate data was not created");
                    putPayload(payload, "environment.free.detail", "environment was not created");
                }
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.create.failed elapsed=%lldms result=null", elapsedMs(isolateDataCreateStartedAt));
            } else {
                __android_log_print(
                        ANDROID_LOG_INFO,
                        kLogTag,
                        "isolate_data.create.done elapsed=%lldms pointer=%s",
                        elapsedMs(isolateDataCreateStartedAt),
                        pointerToHex(isolateData).c_str()
                );
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "isolate_data.create.status", "failed");
            putPayload(payload, "isolate_data.create.detail", e.what());
            putPayload(payload, "isolate_data.free.detail", "isolate data was not created");
            if (createEnvironment) {
                putPayload(payload, "environment.create.detail", "isolate data was not created");
                putPayload(payload, "environment.free.detail", "environment was not created");
            }
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.create.failed elapsed=%lldms error=%s", elapsedMs(isolateDataCreateStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "isolate_data.create.status", "failed");
            putPayload(payload, "isolate_data.create.detail", "unknown native exception");
            putPayload(payload, "isolate_data.free.detail", "isolate data was not created");
            if (createEnvironment) {
                putPayload(payload, "environment.create.detail", "isolate data was not created");
                putPayload(payload, "environment.free.detail", "environment was not created");
            }
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.create.failed elapsed=%lldms error=unknown", elapsedMs(isolateDataCreateStartedAt));
        }
        putPayload(payload, "timing.isolate_data_create.ms", elapsedMs(isolateDataCreateStartedAt));
    }

    if (createEnvironment && isolate != nullptr && isolateData != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "environment.create.start symbol=%s",
                createEnvironmentLookup.symbol.c_str()
        );
        const auto environmentCreateStartedAt = Clock::now();
        const auto environmentFlags =
                static_cast<node::EnvironmentFlags::Flags>(
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoRegisterESMLoader) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoNativeAddons) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoGlobalSearchPaths) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoBrowserGlobals) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoCreateInspector) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoStartDebugSignalHandler) |
                        static_cast<uint64_t>(node::EnvironmentFlags::kNoWaitForInspectorFrontend)
                );
        std::vector<std::string> environmentArgs = {
                "autojs6-embedded-environment-probe"
        };
        std::vector<std::string> environmentExecArgs;
        v8::Local<v8::Context> context;
        v8::Context* contextRaw = nullptr;
        bool isolateEntered = false;
        bool handleScopeConstructed = false;
        bool contextEntered = false;
        alignas(v8::HandleScope) unsigned char handleScopeStorage[sizeof(v8::HandleScope)] = {};
        auto* handleScope = reinterpret_cast<v8::HandleScope*>(handleScopeStorage);
        try {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.isolate.enter.start");
            enterIsolate(isolate);
            isolateEntered = true;
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.isolate.enter.done");

            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.handle_scope.create.start");
            constructHandleScope(handleScope, isolate);
            handleScopeConstructed = true;
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.handle_scope.create.done");

            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "environment.new_context.start symbol=%s",
                    newContextLookup.symbol.c_str()
            );
            context = newContext(isolate, v8::Local<v8::ObjectTemplate>());
            putPayload(payload, "environment.context.status", context.IsEmpty() ? "failed" : "done");
            putPayload(payload, "environment.context.detail", context.IsEmpty() ? "NewContext returned empty" : "NewContext returned");
            if (context.IsEmpty()) {
                lifecycleFailed = true;
                putPayload(payload, "environment.create.status", "failed");
                putPayload(payload, "environment.create.detail", "NewContext returned empty");
                putPayload(payload, "environment.free.detail", "environment was not created");
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.new_context.failed elapsed=%lldms result=empty", elapsedMs(environmentCreateStartedAt));
            } else {
                contextRaw = context.operator->();
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.new_context.done context=non_empty");
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.context.enter.start");
                enterContext(contextRaw);
                contextEntered = true;
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.context.enter.done");

                putPayload(payload, "environment.flags", static_cast<long long>(environmentFlags));
                environment = createEnvironmentRaw(
                        isolateData,
                        context,
                        environmentArgs,
                        environmentExecArgs,
                        environmentFlags,
                        node::ThreadId{},
                        {}
                );
                putPayload(payload, "environment.pointer", pointerToHex(environment));
                putPayload(payload, "environment.create.status", environment == nullptr ? "failed" : "done");
                putPayload(
                        payload,
                        "environment.create.detail",
                        environment == nullptr ? "CreateEnvironment returned null" : "CreateEnvironment returned"
                );
                if (environment == nullptr) {
                    lifecycleFailed = true;
                    putPayload(payload, "environment.free.detail", "environment was not created");
                    __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed elapsed=%lldms result=null", elapsedMs(environmentCreateStartedAt));
                } else {
                    if (scriptExecution) {
                        // Publish for cooperative cancellation; a stop that
                        // arrived before the environment existed fires now.
                        const bool notPreCancelled = registerActiveScriptEnvironment(handle, environment);
                        putPayload(payload, "embedded_script.cooperative_stop.armed", true);
                        if (!notPreCancelled) {
                            putPayload(payload, "embedded_script.cooperative_stop.pre_dispatch", true);
                        }
                    }
                    __android_log_print(
                            ANDROID_LOG_INFO,
                            kLogTag,
                            "environment.create.done elapsed=%lldms pointer=%s",
                            elapsedMs(environmentCreateStartedAt),
                            pointerToHex(environment).c_str()
                    );
                }
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            putPayload(payload, "environment.create.status", "failed");
            putPayload(payload, "environment.create.detail", e.what());
            putPayload(payload, "environment.free.detail", "environment was not created");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed elapsed=%lldms error=%s", elapsedMs(environmentCreateStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            putPayload(payload, "environment.create.status", "failed");
            putPayload(payload, "environment.create.detail", "unknown native exception");
            putPayload(payload, "environment.free.detail", "environment was not created");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.create.failed elapsed=%lldms error=unknown", elapsedMs(environmentCreateStartedAt));
        }
        putPayload(payload, "timing.environment_create.ms", elapsedMs(environmentCreateStartedAt));

        if (stdoutCapture && !stdoutCaptureStarted && !stdoutCaptureFailed && environment != nullptr) {
            stdoutCaptureStartedAt = Clock::now();
            std::string captureError;
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "stdout_capture.start method=fd_pipe phase=before_load_environment expected=%s", kStdoutCaptureExpectedText);
            stdoutCaptureStarted = outputCapture.start(captureError);
            if (stdoutCaptureStarted) {
                putPayload(payload, "stdout_capture.status", "running");
                putPayload(payload, "stdout_capture.detail", "fd/pipe stdout capture started");
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "stdout_capture.start.done phase=before_load_environment");
            } else {
                stdoutCaptureFailed = true;
                if (!stdoutCaptureAuxiliary) {
                    lifecycleFailed = true;
                }
                putPayload(payload, "stdout_capture.status", "failed");
                putPayload(payload, "stdout_capture.detail", captureError.empty() ? "fd/pipe stdout capture did not start" : captureError);
                putPayload(payload, "stdout_capture.bytes", static_cast<long long>(0));
                putPayload(payload, "stdout_capture.text", "");
                putPayload(payload, "stdout_capture.truncated", false);
                putPayload(payload, "stdout_capture.contains_expected", false);
                putPayload(payload, "stderr_capture.bytes", static_cast<long long>(0));
                putPayload(payload, "stderr_capture.text", "");
                putPayload(payload, "stderr_capture.truncated", false);
                putPayload(payload, "stderr_capture.contains_expected", false);
                putPayload(payload, "timing.stdout_capture.ms", elapsedMs(stdoutCaptureStartedAt));
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "stdout_capture.start.failed phase=before_load_environment error=%s",
                        captureError.c_str()
                );
            }
        } else if (stdoutCapture && !stdoutCaptureStarted && !stdoutCaptureFailed) {
            putPayload(payload, "stdout_capture.status", "skipped");
            putPayload(payload, "stdout_capture.detail", "environment was not created");
            putPayload(payload, "timing.stdout_capture.ms", static_cast<long long>(0));
        }

        if (loadEnvironment && environment != nullptr && (!stdoutCapture || stdoutCaptureStarted)) {
            std::string_view source;
            if (scriptExecution && sourceOverride != nullptr) {
                source = std::string_view(sourceOverride->data(), sourceOverride->size());
            }
            const char* sourceLabel = sourceLabelOverride != nullptr ? sourceLabelOverride : "empty_string";
            if (scriptExecution) {
                putPayload(payload, "embedded_script.status", "running");
                putPayload(payload, "embedded_script.detail", "LoadEnvironment source selected");
                putPayload(payload, "embedded_script.source_name", sourceLabel);
                putPayload(payload, "embedded_script.source.length", static_cast<long long>(source.size()));
                putPayload(payload, "embedded_script.working_directory", workingDirectoryOverride == nullptr ? "" : workingDirectoryOverride);
            }
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "load_environment.start symbol=%s source=%s length=%zu",
                    loadEnvironmentLookup.symbol.c_str(),
                    sourceLabel,
                    source.size()
            );
            const auto loadEnvironmentStartedAt = Clock::now();
            try {
                v8::MaybeLocal<v8::Value> result = loadEnvironmentSource(
                        environment,
                        source,
                        node::EmbedderPreloadCallback{}
                );
                const bool resultEmpty = result.IsEmpty();
                putPayload(payload, "load_environment.status", resultEmpty ? "failed" : "done");
                putPayload(payload, "load_environment.detail", resultEmpty ? "LoadEnvironment returned empty" : "LoadEnvironment returned");
                putPayload(payload, "load_environment.result", resultEmpty ? "empty" : "non_empty");
                if (scriptExecution) {
                    putPayload(payload, "embedded_script.load_environment.result", resultEmpty ? "empty" : "non_empty");
                    if (resultEmpty) {
                        putPayload(payload, "embedded_script.status", "failed");
                        putPayload(payload, "embedded_script.detail", "LoadEnvironment returned empty");
                        putPayload(payload, "embedded_script.succeeded", false);
                        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
                    }
                }
                if (resultEmpty) {
                    lifecycleFailed = true;
                    __android_log_print(
                            ANDROID_LOG_WARN,
                            kLogTag,
                            "load_environment.failed elapsed=%lldms result=empty",
                            elapsedMs(loadEnvironmentStartedAt)
                    );
                } else {
                    loadEnvironmentSucceeded = true;
                    __android_log_print(
                            ANDROID_LOG_INFO,
                            kLogTag,
                            "load_environment.done elapsed=%lldms result=non_empty",
                            elapsedMs(loadEnvironmentStartedAt)
                    );
                }
            } catch (const std::exception& e) {
                lifecycleFailed = true;
                putPayload(payload, "load_environment.status", "failed");
                putPayload(payload, "load_environment.detail", e.what());
                putPayload(payload, "load_environment.result", "exception");
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "load_environment.failed elapsed=%lldms error=%s",
                        elapsedMs(loadEnvironmentStartedAt),
                        e.what()
                );
            } catch (...) {
                lifecycleFailed = true;
                putPayload(payload, "load_environment.status", "failed");
                putPayload(payload, "load_environment.detail", "unknown native exception");
                putPayload(payload, "load_environment.result", "exception");
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "load_environment.failed elapsed=%lldms error=unknown",
                        elapsedMs(loadEnvironmentStartedAt)
                );
            }
            putPayload(payload, "timing.load_environment.ms", elapsedMs(loadEnvironmentStartedAt));
        } else if (loadEnvironment) {
            putPayload(payload, "load_environment.status", "skipped");
            putPayload(
                    payload,
                    "load_environment.detail",
                    environment == nullptr
                            ? "environment was not created"
                            : "stdout capture did not start"
            );
            putPayload(payload, "load_environment.result", "skipped");
            putPayload(payload, "timing.load_environment.ms", static_cast<long long>(0));
        }

        if (spinEventLoop && environment != nullptr && loadEnvironmentSucceeded) {
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "spin_event_loop.start symbol=%s",
                    spinEventLoopLookup.symbol.c_str()
            );
            const auto spinEventLoopStartedAt = Clock::now();
            try {
                v8::Maybe<int> result = spinEventLoopRaw(environment);
                int exitCode = 0;
                if (!result.To(&exitCode)) {
                    lifecycleFailed = true;
                    putPayload(payload, "spin_event_loop.status", "failed");
                    putPayload(payload, "spin_event_loop.detail", "SpinEventLoop returned nothing");
                    putPayload(payload, "spin_event_loop.result", "nothing");
                    __android_log_print(
                            ANDROID_LOG_WARN,
                            kLogTag,
                            "spin_event_loop.failed elapsed=%lldms result=nothing",
                            elapsedMs(spinEventLoopStartedAt)
                    );
                } else {
                    putPayload(payload, "spin_event_loop.status", "done");
                    putPayload(payload, "spin_event_loop.detail", "SpinEventLoop returned");
                    putPayload(payload, "spin_event_loop.result", static_cast<long long>(exitCode));
                    __android_log_print(
                            ANDROID_LOG_INFO,
                            kLogTag,
                            "spin_event_loop.done elapsed=%lldms result=%d",
                            elapsedMs(spinEventLoopStartedAt),
                            exitCode
                    );
                }
            } catch (const std::exception& e) {
                lifecycleFailed = true;
                putPayload(payload, "spin_event_loop.status", "failed");
                putPayload(payload, "spin_event_loop.detail", e.what());
                putPayload(payload, "spin_event_loop.result", "exception");
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "spin_event_loop.failed elapsed=%lldms error=%s",
                        elapsedMs(spinEventLoopStartedAt),
                        e.what()
                );
            } catch (...) {
                lifecycleFailed = true;
                putPayload(payload, "spin_event_loop.status", "failed");
                putPayload(payload, "spin_event_loop.detail", "unknown native exception");
                putPayload(payload, "spin_event_loop.result", "exception");
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "spin_event_loop.failed elapsed=%lldms error=unknown",
                        elapsedMs(spinEventLoopStartedAt)
                );
            }
            putPayload(payload, "timing.spin_event_loop.ms", elapsedMs(spinEventLoopStartedAt));
            if (scriptExecution) {
                // The loop has drained; a cancel arriving from here on has
                // nothing to stop and must not touch the freeing environment.
                clearActiveScriptEnvironment();
            }
        } else if (spinEventLoop) {
            putPayload(payload, "spin_event_loop.status", "skipped");
            putPayload(
                    payload,
                    "spin_event_loop.detail",
                    environment == nullptr
                            ? "environment was not created"
                            : "LoadEnvironment did not complete"
            );
            putPayload(payload, "spin_event_loop.result", "skipped");
            putPayload(payload, "timing.spin_event_loop.ms", static_cast<long long>(0));
        }

        if (readGlobalResult && environment != nullptr && loadEnvironmentSucceeded && spinEventLoop) {
            const char* resultLogPrefix = "embedded_script";
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s.read.start property=%s", resultLogPrefix, kJsResultPropertyName);
            const auto jsResultStartedAt = Clock::now();
            std::string resultText;
            std::string error;
            try {
                const bool resultRead = readJsResultGlobal(
                        isolate,
                        context,
                        getContextGlobal,
                        newStringFromUtf8,
                        getObjectProperty,
                        toString,
                        constructUtf8Value,
                        destructUtf8Value,
                        resultText,
                        error
                );
                if (resultRead) {
                    if (scriptExecution) {
                        putPayload(payload, "embedded_script.status", "done");
                        putPayload(payload, "embedded_script.detail", "globalThis.__autojs6_probe_result read");
                        putEmbeddedScriptExecutionFields(payload, resultText);
                    }
                    __android_log_print(
                            ANDROID_LOG_INFO,
                            kLogTag,
                            "%s.read.done elapsed=%lldms bytes=%zu contains_version=%s",
                            resultLogPrefix,
                            elapsedMs(jsResultStartedAt),
                            resultText.size(),
                            resultText.find(kJsResultExpectedText) != std::string::npos ? "true" : "false"
                    );
                } else {
                    lifecycleFailed = true;
                    if (scriptExecution) {
                        putPayload(payload, "embedded_script.status", "failed");
                        putPayload(payload, "embedded_script.detail", error.empty() ? "embedded script result extraction failed" : error);
                        putPayload(payload, "embedded_script.succeeded", false);
                        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
                    }
                    __android_log_print(
                            ANDROID_LOG_WARN,
                            kLogTag,
                            "%s.read.failed elapsed=%lldms error=%s",
                            resultLogPrefix,
                            elapsedMs(jsResultStartedAt),
                            error.c_str()
                    );
                }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            if (scriptExecution) {
                putPayload(payload, "embedded_script.status", "failed");
                putPayload(payload, "embedded_script.detail", e.what());
                putPayload(payload, "embedded_script.succeeded", false);
                putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
            }
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.read.failed elapsed=%lldms error=%s", resultLogPrefix, elapsedMs(jsResultStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            if (scriptExecution) {
                putPayload(payload, "embedded_script.status", "failed");
                putPayload(payload, "embedded_script.detail", "unknown native exception");
                putPayload(payload, "embedded_script.succeeded", false);
                putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
            }
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s.read.failed elapsed=%lldms error=unknown", resultLogPrefix, elapsedMs(jsResultStartedAt));
            }
            putPayload(payload, "timing.embedded_script_result.ms", elapsedMs(jsResultStartedAt));
        } else if (readGlobalResult) {
            if (scriptExecution) {
                putPayload(payload, "embedded_script.status", "failed");
                putPayload(
                        payload,
                        "embedded_script.detail",
                        environment == nullptr
                                ? "environment was not created"
                                : "JavaScript did not complete"
                );
                putPayload(payload, "embedded_script.succeeded", false);
                putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
                putPayload(payload, "timing.embedded_script_result.ms", static_cast<long long>(0));
            }
        }

        if (environment != nullptr) {
            if (scriptExecution) {
                // Failure paths can reach teardown without ever spinning the
                // loop; make sure no cancel can race the free below.
                clearActiveScriptEnvironment();
            }
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "environment.free.start symbol=%s pointer=%s",
                    freeEnvironmentLookup.symbol.c_str(),
                    pointerToHex(environment).c_str()
            );
            const auto environmentFreeStartedAt = Clock::now();
            try {
                freeEnvironment(environment);
                environment = nullptr;
                putPayload(payload, "environment.free.status", "done");
                putPayload(payload, "environment.free.detail", "FreeEnvironment returned");
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.free.done elapsed=%lldms", elapsedMs(environmentFreeStartedAt));
            } catch (const std::exception& e) {
                lifecycleFailed = true;
                executionTeardownClean = false;
                putPayload(payload, "environment.free.status", "failed");
                putPayload(payload, "environment.free.detail", e.what());
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.free.failed elapsed=%lldms error=%s", elapsedMs(environmentFreeStartedAt), e.what());
            } catch (...) {
                lifecycleFailed = true;
                executionTeardownClean = false;
                putPayload(payload, "environment.free.status", "failed");
                putPayload(payload, "environment.free.detail", "unknown native exception");
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "environment.free.failed elapsed=%lldms error=unknown", elapsedMs(environmentFreeStartedAt));
            }
            putPayload(payload, "timing.environment_free.ms", elapsedMs(environmentFreeStartedAt));
        }

        if (contextEntered && contextRaw != nullptr) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.context.exit.start");
            exitContext(contextRaw);
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.context.exit.done");
        }
        if (handleScopeConstructed) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.handle_scope.destroy.start");
            destructHandleScope(handleScope);
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.handle_scope.destroy.done");
        }
        if (isolateEntered) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.isolate.exit.start");
            exitIsolate(isolate);
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "environment.isolate.exit.done");
        }
    }

    if (createIsolateData && isolateData != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "isolate_data.free.start symbol=%s pointer=%s",
                freeIsolateDataLookup.symbol.c_str(),
                pointerToHex(isolateData).c_str()
        );
        const auto isolateDataFreeStartedAt = Clock::now();
        try {
            freeIsolateData(isolateData);
            isolateData = nullptr;
            putPayload(payload, "isolate_data.free.status", "done");
            putPayload(payload, "isolate_data.free.detail", "FreeIsolateData returned");
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "isolate_data.free.done elapsed=%lldms", elapsedMs(isolateDataFreeStartedAt));
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "isolate_data.free.status", "failed");
            putPayload(payload, "isolate_data.free.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.free.failed elapsed=%lldms error=%s", elapsedMs(isolateDataFreeStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "isolate_data.free.status", "failed");
            putPayload(payload, "isolate_data.free.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate_data.free.failed elapsed=%lldms error=unknown", elapsedMs(isolateDataFreeStartedAt));
        }
        putPayload(payload, "timing.isolate_data_free.ms", elapsedMs(isolateDataFreeStartedAt));
    }

    if (isolate != nullptr) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "isolate.dispose.start symbol=%s",
                platformDisposeIsolateLookup.symbol.c_str()
        );
        const auto disposeStartedAt = Clock::now();
        try {
            disposeIsolate(platform.get(), isolate);
            isolate = nullptr;
            putPayload(payload, "isolate.dispose.status", "done");
            putPayload(
                    payload,
                    "isolate.dispose.detail",
                    "MultiIsolatePlatform::DisposeIsolate returned"
            );
            putPayload(payload, "isolate.unregister.status", "done");
            putPayload(
                    payload,
                    "isolate.unregister.detail",
                    "DisposeIsolate deinitialized, unregistered, and freed the isolate"
            );
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "isolate.dispose.done elapsed=%lldms", elapsedMs(disposeStartedAt));
            putPayload(payload, "timing.isolate_unregister.ms", static_cast<long long>(0));
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "isolate.dispose.status", "failed");
            putPayload(payload, "isolate.dispose.detail", e.what());
            putPayload(payload, "isolate.unregister.status", "skipped");
            putPayload(payload, "isolate.unregister.detail", "Isolate::Dispose did not complete");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.dispose.failed elapsed=%lldms error=%s", elapsedMs(disposeStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "isolate.dispose.status", "failed");
            putPayload(payload, "isolate.dispose.detail", "unknown native exception");
            putPayload(payload, "isolate.unregister.status", "skipped");
            putPayload(payload, "isolate.unregister.detail", "Isolate::Dispose did not complete");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "isolate.dispose.failed elapsed=%lldms error=unknown", elapsedMs(disposeStartedAt));
        }
        putPayload(payload, "timing.isolate_dispose.ms", elapsedMs(disposeStartedAt));
    }

    if (collectUvDiagnostics && uvLoopInitialized) {
        putPayload(payload, "uv.diagnostics.status", "running");
        putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics started");
        appendUvDiagnosticsPayload(
                payload,
                "before_close",
                eventLoop.get(),
                uvWalk,
                uvHandleGetType,
                uvIsActive,
                uvIsClosing
        );
    }

    if (closeUvHandles && uvLoopInitialized) {
        putPayload(payload, "uv.close.status", "running");
        putPayload(payload, "uv.close.detail", "uv_close marking started");
        appendUvClosePayload(
                payload,
                eventLoop.get(),
                uvWalk,
                uvClose,
                uvHandleGetType,
                uvIsActive,
                uvIsClosing
        );
        if (collectUvDiagnostics) {
            appendUvDiagnosticsPayload(
                    payload,
                    "after_uv_close",
                    eventLoop.get(),
                    uvWalk,
                    uvHandleGetType,
                    uvIsActive,
                    uvIsClosing
            );
        }
    }

    if (runUvLoopCleanup && uvLoopInitialized) {
        putPayload(payload, "uv.run.status", "running");
        putPayload(payload, "uv.run.detail", "bounded UV_RUN_NOWAIT cleanup started");
        const bool uvRunCleanupCompleted = appendUvRunCleanupPayload(
                payload,
                eventLoop.get(),
                uvRun,
                uvWalk,
                uvHandleGetType,
                uvIsActive,
                uvIsClosing,
                collectUvDiagnostics
        );
        if (!uvRunCleanupCompleted) {
            lifecycleFailed = true;
            executionTeardownClean = false;
        }
    }

    if (uvLoopInitialized) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.loop.close.start symbol=%s",
                uvLoopCloseLookup.symbol.c_str()
        );
        const auto uvLoopCloseStartedAt = Clock::now();
        try {
            uvLoopCloseAttempted = true;
            const int result = uvLoopClose(eventLoop.get());
            putPayload(payload, "uv.loop.close.result", static_cast<long long>(result));
            if (result == 0) {
                uvLoopClosed = true;
                putPayload(payload, "uv.loop.close.status", "done");
                putPayload(payload, "uv.loop.close.detail", "uv_loop_close returned 0");
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.loop.close.done elapsed=%lldms", elapsedMs(uvLoopCloseStartedAt));
            } else {
                lifecycleFailed = true;
                executionTeardownClean = false;
                const std::string detail = uvLoopResultDetail("uv_loop_close", result);
                putPayload(payload, "uv.loop.close.status", "failed");
                putPayload(payload, "uv.loop.close.detail", detail);
                __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.close.failed elapsed=%lldms result=%d", elapsedMs(uvLoopCloseStartedAt), result);
            }
        } catch (const std::exception& e) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "uv.loop.close.status", "failed");
            putPayload(payload, "uv.loop.close.detail", e.what());
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.close.failed elapsed=%lldms error=%s", elapsedMs(uvLoopCloseStartedAt), e.what());
        } catch (...) {
            lifecycleFailed = true;
            executionTeardownClean = false;
            putPayload(payload, "uv.loop.close.status", "failed");
            putPayload(payload, "uv.loop.close.detail", "unknown native exception");
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.loop.close.failed elapsed=%lldms error=unknown", elapsedMs(uvLoopCloseStartedAt));
        }
        putPayload(payload, "timing.uv_loop_close.ms", elapsedMs(uvLoopCloseStartedAt));
    }

    if (collectUvDiagnostics && uvLoopInitialized) {
        if (closeUvHandles && uvLoopCloseAttempted && !uvLoopClosed) {
            appendUvDiagnosticsPayload(
                    payload,
                    "after_loop_close",
                    eventLoop.get(),
                    uvWalk,
                    uvHandleGetType,
                    uvIsActive,
                    uvIsClosing
            );
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed");
        } else if (closeUvHandles && uvLoopClosed) {
            putPayload(payload, "uv.diagnostics.after_loop_close.status", "skipped");
            putPayload(payload, "uv.diagnostics.after_loop_close.detail", "uv_loop_close returned 0; loop was closed");
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed after uv_close marking");
        } else if (closeUvHandles) {
            putPayload(payload, "uv.diagnostics.after_loop_close.status", "skipped");
            putPayload(payload, "uv.diagnostics.after_loop_close.detail", "uv_loop_close was not attempted");
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed after uv_close marking");
        } else if (uvLoopCloseAttempted && !uvLoopClosed) {
            appendUvDiagnosticsPayload(
                    payload,
                    "after_close",
                    eventLoop.get(),
                    uvWalk,
                    uvHandleGetType,
                    uvIsActive,
                    uvIsClosing
            );
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed");
        } else if (uvLoopClosed) {
            putPayload(payload, "uv.diagnostics.after_close.status", "skipped");
            putPayload(payload, "uv.diagnostics.after_close.detail", "uv_loop_close returned 0; loop was closed");
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed before successful uv_loop_close");
        } else {
            putPayload(payload, "uv.diagnostics.after_close.status", "skipped");
            putPayload(payload, "uv.diagnostics.after_close.detail", "uv_loop_close was not attempted");
            putPayload(payload, "uv.diagnostics.status", "done");
            putPayload(payload, "uv.diagnostics.detail", "uv_walk diagnostics completed before skipped uv_loop_close");
        }
    }

    if (stdoutCapture && stdoutCaptureStarted) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "stdout_capture.stop.start phase=after_uv_loop_close");
        outputCapture.stop();
        const long long stdoutBytes = outputCapture.stdoutBytes();
        const std::string& stdoutText = outputCapture.stdoutText();
        const long long stderrBytes = outputCapture.stderrBytes();
        const std::string& stderrText = outputCapture.stderrText();
        const char* stdoutExpectedText = kStdoutCaptureExpectedText;
        const char* stderrExpectedText = kStdoutCaptureExpectedText;
        const bool containsExpected = stdoutText.find(stdoutExpectedText) != std::string::npos;
        const bool stderrContainsExpected = stderrText.find(stderrExpectedText) != std::string::npos;
        const bool captureSucceeded = stdoutBytes > 0 && containsExpected;
        putPayload(payload, "stdout_capture.status", (captureSucceeded || stdoutCaptureAuxiliary) ? "done" : "failed");
        putPayload(
                payload,
                "stdout_capture.detail",
                captureSucceeded
                        ? "stdout fd/pipe capture contained expected text"
                        : stdoutCaptureAuxiliary
                        ? "auxiliary stdout fd/pipe capture completed without expected text"
                        : "stdout fd/pipe capture did not contain expected text"
        );
        putPayload(payload, "stdout_capture.bytes", stdoutBytes);
        putPayload(payload, "stdout_capture.text.hex", hexEncodeBytes(stdoutText));
        putPayload(payload, "stdout_capture.text", stdoutText);
        putPayload(payload, "stdout_capture.truncated", outputCapture.stdoutTruncated());
        putPayload(payload, "stdout_capture.contains_expected", containsExpected);
        putPayload(payload, "stderr_capture.bytes", stderrBytes);
        putPayload(payload, "stderr_capture.text.hex", hexEncodeBytes(stderrText));
        putPayload(payload, "stderr_capture.text", stderrText);
        putPayload(payload, "stderr_capture.truncated", outputCapture.stderrTruncated());
        putPayload(payload, "stderr_capture.contains_expected", stderrContainsExpected);
        putPayload(payload, "timing.stdout_capture.ms", elapsedMs(stdoutCaptureStartedAt));
        if (!captureSucceeded && !stdoutCaptureAuxiliary) {
            lifecycleFailed = true;
            stdoutCaptureFailed = true;
        }
        const bool captureStatusDone = captureSucceeded || stdoutCaptureAuxiliary;
        __android_log_print(
                captureStatusDone ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                kLogTag,
                "stdout_capture.done status=%s stdout_bytes=%lld stderr_bytes=%lld contains_expected=%s elapsed=%lldms",
                captureStatusDone ? "done" : "failed",
                stdoutBytes,
                stderrBytes,
                containsExpected ? "true" : "false",
                elapsedMs(stdoutCaptureStartedAt)
        );
    } else if (stdoutCapture && !stdoutCaptureFailed) {
        putPayload(payload, "stdout_capture.status", "skipped");
        putPayload(payload, "stdout_capture.detail", "stdout capture was not started");
        putPayload(payload, "stderr_capture.contains_expected", false);
        putPayload(payload, "timing.stdout_capture.ms", static_cast<long long>(0));
    }

    if (environment != nullptr || isolateData != nullptr || isolate != nullptr ||
        (uvLoopInitialized && !uvLoopClosed)) {
        executionTeardownClean = false;
    }
    putPayload(payload, "execution.teardown_clean", executionTeardownClean);
    putPayload(
            payload,
            "execution.teardown_poison_reason",
            executionTeardownClean ? "" : "per-execution Node/V8/libuv teardown did not complete cleanly"
    );
    if (processRuntimeTeardownClean != nullptr) {
        *processRuntimeTeardownClean = executionTeardownClean;
    }

    if (processRuntimePersistent) {
        putPayload(payload, "teardown.status", "deferred");
        putPayload(payload, "teardown.detail", "process-global TearDownOncePerProcess is reserved for terminal shutdown");
        putPayload(payload, "timing.teardown.ms", static_cast<long long>(0));
        putPayload(payload, "v8.platform.lifetime", "process_owned_reused");
    } else {
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "teardown.start symbol=%s",
            teardownLookup.symbol.c_str()
    );
    const auto teardownStartedAt = Clock::now();
    try {
        tearDownOncePerProcess();
        putPayload(payload, "teardown.status", "done");
        putPayload(payload, "teardown.detail", "TearDownOncePerProcess returned");
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "teardown.done elapsed=%lldms", elapsedMs(teardownStartedAt));
    } catch (const std::exception& e) {
        lifecycleFailed = true;
        putPayload(payload, "teardown.status", "failed");
        putPayload(payload, "teardown.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "teardown.failed elapsed=%lldms error=%s", elapsedMs(teardownStartedAt), e.what());
    } catch (...) {
        lifecycleFailed = true;
        putPayload(payload, "teardown.status", "failed");
        putPayload(payload, "teardown.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "teardown.failed elapsed=%lldms error=unknown", elapsedMs(teardownStartedAt));
    }
    putPayload(payload, "timing.teardown.ms", elapsedMs(teardownStartedAt));

    if (v8PlatformInitialized && platform != nullptr) {
        platform.release();
        putPayload(payload, "v8.platform.lifetime", "released_to_probe_process");
    }
    }

    __android_log_print(
            lifecycleFailed ? ANDROID_LOG_WARN : ANDROID_LOG_INFO,
            kLogTag,
            "%s.done status=%s elapsed=%lldms",
            lifecycleLogName,
            lifecycleFailed ? "failed" : "done",
            elapsedMs(lifecycleStartedAt)
    );
#else
    putPayload(payload, "embedded_script.status", "skipped");
    putPayload(payload, "embedded_script.detail", "compile-time embedded script execution is disabled");
    putPayload(payload, "embedded_script.succeeded", false);
    putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s.done status=skipped reason=build_disabled elapsed=%lldms", lifecycleLogName, elapsedMs(lifecycleStartedAt));
#endif
}


}  // namespace autojs6::node_bridge::internal

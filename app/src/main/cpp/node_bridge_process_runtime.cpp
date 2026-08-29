#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

namespace {

enum class ProcessRuntimeState {
    kUninitialized,
    kInitializing,
    kReady,
    kPoisoned,
    kTerminal,
};

struct ProcessRuntime {
    bool persistentEnabled = false;
    ProcessRuntimeState state = ProcessRuntimeState::kUninitialized;
    void* libnodeHandle = nullptr;
    NodeTearDownOncePerProcess tearDownOncePerProcess = nullptr;
    std::shared_ptr<node::InitializationResult> initializationResult;
    node::MultiIsolatePlatform* platform = nullptr;
    uint64_t generation = 0;
    uint64_t initializeAttemptCount = 0;
    uint64_t initializeCount = 0;
    uint64_t executionSequence = 0;
    uint64_t legacyExecutionCount = 0;
    bool initializationReturned = false;
    bool processTeardownRequired = false;
    bool lastReused = false;
    bool lastTeardownClean = true;
    bool poisoned = false;
    std::string poisonReason;
};

ProcessRuntime& processRuntime() {
    static ProcessRuntime runtime;
    return runtime;
}

const char* stateName(ProcessRuntimeState state) {
    switch (state) {
        case ProcessRuntimeState::kUninitialized:
            return "uninitialized";
        case ProcessRuntimeState::kInitializing:
            return "initializing";
        case ProcessRuntimeState::kReady:
            return "ready";
        case ProcessRuntimeState::kPoisoned:
            return "poisoned";
        case ProcessRuntimeState::kTerminal:
            return "terminal";
    }
    return "unknown";
}

node::ProcessInitializationFlags::Flags persistentInitializationFlags() {
    uint32_t flags =
            static_cast<uint32_t>(node::ProcessInitializationFlags::kLegacyInitializeNodeWithArgsBehavior) |
            static_cast<uint32_t>(node::ProcessInitializationFlags::kDisableNodeOptionsEnv) |
            static_cast<uint32_t>(node::ProcessInitializationFlags::kNoParseGlobalDebugVariables);
    flags &= ~static_cast<uint32_t>(node::ProcessInitializationFlags::kNoStdioInitialization);
    flags &= ~static_cast<uint32_t>(node::ProcessInitializationFlags::kNoInitializeV8);
    flags &= ~static_cast<uint32_t>(node::ProcessInitializationFlags::kNoInitializeNodeV8Platform);
    flags &= ~static_cast<uint32_t>(node::ProcessInitializationFlags::kNoInitializeCppgc);
    return static_cast<node::ProcessInitializationFlags::Flags>(flags);
}

const char* persistentInitializationFlagsName() {
    return "kLegacyInitializeNodeWithArgsBehavior_without_kNoStdioInitialization_without_kNoInitializeV8_without_kNoInitializeNodeV8Platform_without_kNoInitializeCppgc|kDisableNodeOptionsEnv|kNoParseGlobalDebugVariables";
}

void appendDiagnosticsUnlocked(std::vector<std::string>& payload) {
    const ProcessRuntime& runtime = processRuntime();
    const bool oneShotReserved = runtime.legacyExecutionCount != 0;
    putPayload(
            payload,
            "process_runtime.state",
            oneShotReserved && runtime.state == ProcessRuntimeState::kUninitialized
                    ? "terminal"
                    : stateName(runtime.state)
    );
    putPayload(payload, "process_runtime.persistent_enabled", runtime.persistentEnabled);
    putPayload(payload, "process_runtime.generation", static_cast<long long>(runtime.generation));
    putPayload(payload, "process_runtime.init_attempt_count", static_cast<long long>(runtime.initializeAttemptCount));
    putPayload(payload, "process_runtime.init_count", static_cast<long long>(runtime.initializeCount));
    putPayload(payload, "process_runtime.reused", runtime.lastReused);
    putPayload(payload, "process_runtime.execution_sequence", static_cast<long long>(runtime.executionSequence));
    putPayload(payload, "process_runtime.legacy_execution_count", static_cast<long long>(runtime.legacyExecutionCount));
    putPayload(payload, "process_runtime.teardown_clean", runtime.lastTeardownClean);
    putPayload(
            payload,
            "process_runtime.healthy",
            runtime.state != ProcessRuntimeState::kPoisoned &&
                    runtime.state != ProcessRuntimeState::kTerminal &&
                    !oneShotReserved
    );
    putPayload(payload, "process_runtime.poisoned", runtime.poisoned);
    putPayload(payload, "process_runtime.poison", runtime.poisoned);
    putPayload(payload, "process_runtime.poison_reason", runtime.poisonReason);
    putPayload(payload, "process_runtime.libnode_handle_cached", runtime.libnodeHandle != nullptr);
    putPayload(payload, "process_runtime.platform_available", runtime.platform != nullptr);
    putPayload(payload, "process_runtime.normal_global_teardown", false);
    putPayload(payload, "process_runtime.restart_required", oneShotReserved || runtime.state == ProcessRuntimeState::kTerminal);
}

void poisonUnlocked(const std::string& reason) {
    ProcessRuntime& runtime = processRuntime();
    runtime.state = ProcessRuntimeState::kPoisoned;
    runtime.poisoned = true;
    runtime.lastTeardownClean = false;
    runtime.poisonReason = reason.empty() ? "persistent execution cleanup gate failed" : reason;
    __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "process_runtime.poisoned generation=%llu sequence=%llu reason=%s",
            static_cast<unsigned long long>(runtime.generation),
            static_cast<unsigned long long>(runtime.executionSequence),
            runtime.poisonReason.c_str()
    );
}

bool ensureUnlocked(std::vector<std::string>& payload) {
    ProcessRuntime& runtime = processRuntime();
    runtime.lastReused = false;
    if (!runtime.persistentEnabled) {
        putPayload(payload, "process_runtime.ensure.status", "skipped");
        putPayload(payload, "process_runtime.ensure.detail", "persistent process runtime is disabled; legacy one-shot execution remains selected");
        appendDiagnosticsUnlocked(payload);
        return true;
    }
    if (runtime.state == ProcessRuntimeState::kReady) {
        runtime.lastReused = true;
        putPayload(payload, "process_runtime.ensure.status", "done");
        putPayload(payload, "process_runtime.ensure.detail", "process-global Node/V8 platform was reused");
        appendDiagnosticsUnlocked(payload);
        return true;
    }
    if (runtime.state == ProcessRuntimeState::kPoisoned) {
        putPayload(payload, "process_runtime.ensure.status", "failed");
        putPayload(payload, "process_runtime.ensure.detail", runtime.poisonReason);
        appendDiagnosticsUnlocked(payload);
        return false;
    }
    if (runtime.state == ProcessRuntimeState::kTerminal) {
        putPayload(payload, "process_runtime.ensure.status", "failed");
        putPayload(payload, "process_runtime.ensure.detail", "terminal shutdown already completed; V8 cannot be initialized again in this process");
        appendDiagnosticsUnlocked(payload);
        return false;
    }
    if (runtime.legacyExecutionCount != 0) {
        poisonUnlocked("persistent initialization was requested after legacy one-shot execution in the same process");
        putPayload(payload, "process_runtime.ensure.status", "failed");
        putPayload(payload, "process_runtime.ensure.detail", processRuntime().poisonReason);
        appendDiagnosticsUnlocked(payload);
        return false;
    }

    runtime.state = ProcessRuntimeState::kInitializing;
    runtime.initializeAttemptCount += 1;
    const auto startedAt = Clock::now();
    putPayload(payload, "process_runtime.initialize.flags", persistentInitializationFlagsName());
    try {
        runtime.libnodeHandle = probeLoadedLibnodeHandle(payload);
        if (runtime.libnodeHandle == nullptr) {
            poisonUnlocked("libnode.so handle was unavailable during persistent process initialization");
        } else {
            const SymbolLookup initializeLookup = lookupSymbol(
                    runtime.libnodeHandle,
                    kInitializeOncePerProcessNode24Symbol
            );
            const SymbolLookup teardownLookup = lookupSymbol(
                    runtime.libnodeHandle,
                    kTearDownOncePerProcessSymbol
            );
            putPayload(payload, "process_runtime.initialize.symbol", initializeLookup.symbol);
            putPayload(payload, "process_runtime.shutdown.symbol", teardownLookup.symbol);
            if (!initializeLookup.found || initializeLookup.address == nullptr) {
                poisonUnlocked("InitializeOncePerProcess Node 24 symbol is unavailable: " + initializeLookup.error);
            } else if (!teardownLookup.found || teardownLookup.address == nullptr) {
                poisonUnlocked("TearDownOncePerProcess symbol is unavailable: " + teardownLookup.error);
            } else {
                const auto initializeOncePerProcess =
                        reinterpret_cast<NodeInitializeOncePerProcess>(initializeLookup.address);
                runtime.tearDownOncePerProcess =
                        reinterpret_cast<NodeTearDownOncePerProcess>(teardownLookup.address);
                const std::vector<std::string> args = {
                        "autojs6-embedded-process-runtime",
                        "--experimental-vm-modules"
                };
                runtime.initializationResult = initializeOncePerProcess(
                        args,
                        persistentInitializationFlags()
                );
                runtime.initializationReturned = true;
                if (runtime.initializationResult == nullptr) {
                    poisonUnlocked("InitializeOncePerProcess returned null");
                } else if (runtime.initializationResult->early_return()) {
                    const std::string errors = joinStrings(runtime.initializationResult->errors(), "; ");
                    poisonUnlocked(
                            errors.empty()
                                    ? "InitializeOncePerProcess requested an early return"
                                    : "InitializeOncePerProcess requested an early return: " + errors
                    );
                } else {
                    // Node's own StartInternal() installs its process teardown guard only
                    // after a non-early initialization result. Mirror that boundary here.
                    runtime.processTeardownRequired = true;
                    runtime.platform = runtime.initializationResult->platform();
                    if (runtime.platform == nullptr) {
                        poisonUnlocked("InitializeOncePerProcess returned no Node-owned MultiIsolatePlatform");
                    } else {
                        runtime.generation += 1;
                        runtime.initializeCount += 1;
                        runtime.state = ProcessRuntimeState::kReady;
                        runtime.lastTeardownClean = true;
                        runtime.poisoned = false;
                        runtime.poisonReason.clear();
                        putPayload(payload, "process_runtime.ensure.status", "done");
                        putPayload(payload, "process_runtime.ensure.detail", "process-global Node/V8/cppgc platform initialized");
                        __android_log_print(
                                ANDROID_LOG_INFO,
                                kLogTag,
                                "process_runtime.ready generation=%llu initialize_count=%llu elapsed=%lldms",
                                static_cast<unsigned long long>(runtime.generation),
                                static_cast<unsigned long long>(runtime.initializeCount),
                                elapsedMs(startedAt)
                        );
                    }
                }
            }
        }
    } catch (const std::exception& error) {
        poisonUnlocked(std::string("persistent process initialization threw: ") + error.what());
    } catch (...) {
        poisonUnlocked("persistent process initialization threw an unknown native exception");
    }
    putPayload(payload, "timing.process_runtime_initialize.ms", elapsedMs(startedAt));
    if (runtime.state != ProcessRuntimeState::kReady) {
        putPayload(payload, "process_runtime.ensure.status", "failed");
        putPayload(payload, "process_runtime.ensure.detail", runtime.poisonReason);
    }
    appendDiagnosticsUnlocked(payload);
    return runtime.state == ProcessRuntimeState::kReady;
}

}  // namespace

std::recursive_mutex& embeddedProcessRuntimeExecutionMutex() {
    static std::recursive_mutex mutex;
    return mutex;
}

bool embeddedProcessRuntimePersistentEnabled() {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    return processRuntime().persistentEnabled;
}

bool embeddedProcessRuntimeOwnsGlobalState() {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    const ProcessRuntime& runtime = processRuntime();
    return runtime.initializationReturned &&
            (runtime.state == ProcessRuntimeState::kReady || runtime.state == ProcessRuntimeState::kPoisoned);
}

bool setEmbeddedProcessRuntimePersistentEnabled(
        bool enabled,
        std::vector<std::string>& payload
) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    ProcessRuntime& runtime = processRuntime();
    if (runtime.persistentEnabled == enabled) {
        putPayload(payload, "process_runtime.mode_change.status", "done");
        putPayload(payload, "process_runtime.mode_change.detail", "requested mode was already selected");
        appendDiagnosticsUnlocked(payload);
        return true;
    }
    if (runtime.state != ProcessRuntimeState::kUninitialized || runtime.legacyExecutionCount != 0) {
        putPayload(payload, "process_runtime.mode_change.status", "failed");
        putPayload(payload, "process_runtime.mode_change.detail", "runtime mode can only change before initialization or execution");
        appendDiagnosticsUnlocked(payload);
        return false;
    }
    runtime.persistentEnabled = enabled;
    putPayload(payload, "process_runtime.mode_change.status", "done");
    putPayload(
            payload,
            "process_runtime.mode_change.detail",
            enabled ? "persistent process runtime selected" : "legacy one-shot runtime selected"
    );
    appendDiagnosticsUnlocked(payload);
    return true;
}

bool ensureEmbeddedProcessRuntime(std::vector<std::string>& payload) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    return ensureUnlocked(payload);
}

bool beginEmbeddedProcessRuntimeExecution(
        std::vector<std::string>& payload,
        EmbeddedProcessRuntimeExecution& execution
) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    if (!ensureUnlocked(payload) || processRuntime().state != ProcessRuntimeState::kReady) {
        return false;
    }
    ProcessRuntime& runtime = processRuntime();
    runtime.executionSequence += 1;
    execution.libnodeHandle = runtime.libnodeHandle;
    execution.platform = runtime.platform;
    execution.generation = runtime.generation;
    execution.executionSequence = runtime.executionSequence;
    execution.reused = runtime.lastReused;
    putPayload(payload, "process_runtime.execution.status", "running");
    putPayload(payload, "process_runtime.execution.generation", static_cast<long long>(execution.generation));
    putPayload(payload, "process_runtime.execution.sequence", static_cast<long long>(execution.executionSequence));
    appendDiagnosticsUnlocked(payload);
    return true;
}

void finishEmbeddedProcessRuntimeExecution(
        std::vector<std::string>& payload,
        const EmbeddedProcessRuntimeExecution& execution,
        bool teardownClean,
        const std::string& poisonReason
) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    ProcessRuntime& runtime = processRuntime();
    if (runtime.generation != execution.generation ||
        runtime.executionSequence != execution.executionSequence) {
        poisonUnlocked("persistent execution token did not match the active runtime generation/sequence");
    } else if (!teardownClean) {
        poisonUnlocked(poisonReason);
    } else if (runtime.state == ProcessRuntimeState::kReady) {
        runtime.lastTeardownClean = true;
    }
    putPayload(
            payload,
            "process_runtime.execution.status",
            runtime.state == ProcessRuntimeState::kReady ? "done" : "poisoned"
    );
    appendDiagnosticsUnlocked(payload);
}

bool beginEmbeddedProcessRuntimeOneShotLifecycle(
        std::vector<std::string>& payload,
        const char* origin
) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    ProcessRuntime& runtime = processRuntime();
    if (runtime.state != ProcessRuntimeState::kUninitialized ||
        runtime.initializationReturned ||
        runtime.legacyExecutionCount != 0) {
        putPayload(payload, "process_runtime.one_shot.status", "failed");
        putPayload(
                payload,
                "process_runtime.one_shot.detail",
                "one-shot Node/V8 lifecycle requires a fresh process"
        );
        appendDiagnosticsUnlocked(payload);
        return false;
    }
    runtime.legacyExecutionCount += 1;
    runtime.lastReused = false;
    putPayload(payload, "process_runtime.execution.mode", "legacy_one_shot");
    putPayload(payload, "process_runtime.one_shot.status", "running");
    putPayload(payload, "process_runtime.one_shot.origin", origin == nullptr ? "unknown" : origin);
    appendDiagnosticsUnlocked(payload);
    return true;
}

void appendEmbeddedProcessRuntimeDiagnostics(std::vector<std::string>& payload) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    appendDiagnosticsUnlocked(payload);
}

bool shutdownEmbeddedProcessRuntime(std::vector<std::string>& payload) {
    std::lock_guard<std::recursive_mutex> lock(embeddedProcessRuntimeExecutionMutex());
    ProcessRuntime& runtime = processRuntime();
    if (runtime.state == ProcessRuntimeState::kTerminal) {
        putPayload(payload, "process_runtime.shutdown.status", "done");
        putPayload(payload, "process_runtime.shutdown.detail", "terminal shutdown was already completed");
        appendDiagnosticsUnlocked(payload);
        return true;
    }

    bool clean = true;
    const auto startedAt = Clock::now();
    if (runtime.processTeardownRequired && runtime.tearDownOncePerProcess != nullptr) {
        try {
            // Keep InitializationResult alive through teardown, matching Node's
            // StartInternal() lifetime. It contains a borrowed platform pointer.
            runtime.tearDownOncePerProcess();
            putPayload(payload, "process_runtime.shutdown.status", "done");
            putPayload(payload, "process_runtime.shutdown.detail", "TearDownOncePerProcess returned during terminal shutdown");
        } catch (const std::exception& error) {
            clean = false;
            runtime.poisoned = true;
            runtime.poisonReason = std::string("terminal TearDownOncePerProcess threw: ") + error.what();
        } catch (...) {
            clean = false;
            runtime.poisoned = true;
            runtime.poisonReason = "terminal TearDownOncePerProcess threw an unknown native exception";
        }
    } else {
        putPayload(payload, "process_runtime.shutdown.status", "done");
        putPayload(payload, "process_runtime.shutdown.detail", "no process-global Node runtime had been initialized");
    }
    runtime.initializationResult.reset();
    runtime.platform = nullptr;
    runtime.state = ProcessRuntimeState::kTerminal;
    runtime.lastTeardownClean = clean;
    if (!clean) {
        putPayload(payload, "process_runtime.shutdown.status", "failed");
        putPayload(payload, "process_runtime.shutdown.detail", runtime.poisonReason);
    }
    putPayload(payload, "timing.process_runtime_shutdown.ms", elapsedMs(startedAt));
    appendDiagnosticsUnlocked(payload);
    return clean;
}

}  // namespace autojs6::node_bridge::internal

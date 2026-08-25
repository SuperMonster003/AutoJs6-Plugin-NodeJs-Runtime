#include "node_bridge_internal.h"
#include "node_runtime_api_v1.h"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

using namespace autojs6::node_bridge;
using namespace autojs6::node_bridge::internal;

#ifndef O_CLOEXEC
#define O_CLOEXEC 0
#endif

namespace {

constexpr const char* kNativePhaseTimingSchema = "autojs6-node-native-lifecycle-timing-v1";
constexpr const char* kNativePhaseTimingSemantics = "nested_non_additive";
constexpr const char* kNativeScriptExecutionSemantics = "entry_to_terminal_completion";
constexpr const char* kNativePhaseTimingClock = "native_steady_clock_and_node_hrtime_bigint";
constexpr size_t kAdapterPhaseTimingPayloadMaxChars = 4096;
constexpr size_t kAdapterPhaseStatusPayloadMaxChars = 4096;

bool startsWith(std::string_view value, std::string_view prefix) {
    return value.size() >= prefix.size() && value.substr(0, prefix.size()) == prefix;
}

bool keepEmbeddedScriptLifecyclePayloadKey(std::string_view key) {
    constexpr std::string_view prefixes[] = {
            "embedded_script.",
            "execution.",
            "process_runtime.",
            "stderr_capture.",
            "stdout_capture.",
            "uv.",
    };
    for (const std::string_view prefix : prefixes) {
        if (startsWith(key, prefix)) {
            return true;
        }
    }
    constexpr std::string_view exactKeys[] = {
            "allocator.create.status",
            "bootstrap_script.status",
            "completion_drain.status",
            "current.abi",
            "environment.create.status",
            "environment.free.status",
            "initialize.status",
            "isolate.create.status",
            "isolate.dispose.status",
            "isolate.unregister.status",
            "isolate_data.create.status",
            "isolate_data.free.status",
            "js_result.status",
            "lifecycle.build.enabled",
            "load.duration.ms",
            "load_environment.status",
            "output_envelope.status",
            "platform.create.status",
            "spin_event_loop.status",
            "symbol.count",
            "teardown.status",
            "timing.allocator_create.ms",
            "timing.bootstrap.ms",
            "timing.bootstrap_script.ms",
            "timing.completion_drain.ms",
            "timing.embedded_script_result.ms",
            "timing.environment_create.ms",
            "timing.environment_free.ms",
            "timing.execution_source_build.ms",
            "timing.initialize.ms",
            "timing.isolate_create.ms",
            "timing.isolate_data_create.ms",
            "timing.isolate_data_free.ms",
            "timing.isolate_dispose.ms",
            "timing.isolate_unregister.ms",
            "timing.js_result.ms",
            "timing.load.ms",
            "timing.load_environment.ms",
            "timing.module_preload.ms",
            "timing.output_envelope.ms",
            "timing.platform_create.ms",
            "timing.process_runtime_initialize.ms",
            "timing.process_runtime_shutdown.ms",
            "timing.script_execution.ms",
            "timing.spin_event_loop.ms",
            "timing.stdout_capture.ms",
            "timing.symbols.ms",
            "timing.teardown.ms",
            "timing.total.ms",
            "timing.uv_close.ms",
            "timing.uv_loop_close.ms",
            "timing.uv_loop_init.ms",
            "timing.uv_run.ms",
            "timing.v8_initialize.ms",
            "timing.v8_initialize_platform.ms",
            "v8.initialize.status",
            "v8.initialize_platform.status",
    };
    for (const std::string_view exactKey : exactKeys) {
        if (key == exactKey) {
            return true;
        }
    }
    return false;
}

void compactEmbeddedScriptLifecyclePayload(std::vector<std::string>& payload) {
    const size_t originalEntryCount = payload.size();
    std::vector<std::string> compacted;
    compacted.reserve(originalEntryCount);
    for (std::string& entry : payload) {
        const size_t separator = entry.find('=');
        if (separator == std::string::npos ||
            keepEmbeddedScriptLifecyclePayloadKey(
                    std::string_view(entry.data(), separator)
            )) {
            compacted.emplace_back(std::move(entry));
        }
    }
    const size_t compactedEntryCount = compacted.size();
    payload = std::move(compacted);
    putPayload(payload, "embedded_script.native_payload_compaction.applied", true);
    putPayload(
            payload,
            "embedded_script.native_payload_compaction.policy",
            "script_contract_runtime_status_phase_timings_and_captures"
    );
    putPayload(
            payload,
            "embedded_script.native_payload_compaction.original_entry_count",
            static_cast<long long>(originalEntryCount)
    );
    putPayload(
            payload,
            "embedded_script.native_payload_compaction.omitted_entry_count",
            static_cast<long long>(originalEntryCount - compactedEntryCount)
    );
    putPayload(
            payload,
            "embedded_script.native_payload_compaction.sent_entry_count",
            static_cast<long long>(compactedEntryCount + 5)
    );
}

enum class NativePhaseStatusPolicy {
    kPresent,
    kDone,
    kDoneOrReused,
    kScriptTerminal,
    kPositiveIntegerMarker,
};

struct NativePhaseTimingSpec {
    const char* timingKey;
    const char* statusKey;
    NativePhaseStatusPolicy statusPolicy;
    bool required;
};

constexpr NativePhaseTimingSpec kNativePhaseTimingSpecs[] = {
        {"timing.execution_source_build.ms", nullptr, NativePhaseStatusPolicy::kPresent, true},
        {"timing.process_runtime_initialize.ms", "process_runtime.ensure.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.load.ms", nullptr, NativePhaseStatusPolicy::kPresent, false},
        {"timing.symbols.ms", "symbol.count", NativePhaseStatusPolicy::kPositiveIntegerMarker, false},
        {"timing.initialize.ms", "initialize.status", NativePhaseStatusPolicy::kDoneOrReused, false},
        {"timing.platform_create.ms", "platform.create.status", NativePhaseStatusPolicy::kDoneOrReused, false},
        {"timing.v8_initialize_platform.ms", "v8.initialize_platform.status", NativePhaseStatusPolicy::kDoneOrReused, false},
        {"timing.v8_initialize.ms", "v8.initialize.status", NativePhaseStatusPolicy::kDoneOrReused, false},
        {"timing.uv_loop_init.ms", "uv.loop.init.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.allocator_create.ms", "allocator.create.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.isolate_create.ms", "isolate.create.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.isolate_data_create.ms", "isolate_data.create.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.environment_create.ms", "environment.create.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.load_environment.ms", "load_environment.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.bootstrap_script.ms", "bootstrap_script.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.bootstrap.ms", "embedded_script.phase.bootstrap.status", NativePhaseStatusPolicy::kScriptTerminal, true},
        {"timing.module_preload.ms", "embedded_script.phase.module_preload.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.script_execution.ms", "embedded_script.phase.script_execution.status", NativePhaseStatusPolicy::kScriptTerminal, true},
        {"timing.spin_event_loop.ms", "spin_event_loop.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.completion_drain.ms", "completion_drain.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.embedded_script_result.ms", "embedded_script.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.js_result.ms", "js_result.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.output_envelope.ms", "output_envelope.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.stdout_capture.ms", "stdout_capture.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.environment_free.ms", "environment.free.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.isolate_data_free.ms", "isolate_data.free.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.isolate_unregister.ms", "isolate.unregister.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.isolate_dispose.ms", "isolate.dispose.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.uv_run.ms", "uv.run.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.uv_close.ms", "uv.close.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.uv_loop_close.ms", "uv.loop.close.status", NativePhaseStatusPolicy::kDone, true},
        {"timing.teardown.ms", "teardown.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.process_runtime_shutdown.ms", "process_runtime.shutdown.status", NativePhaseStatusPolicy::kDone, false},
        {"timing.total.ms", nullptr, NativePhaseStatusPolicy::kPresent, true},
};
constexpr size_t kNativePhaseTimingSpecCount =
        sizeof(kNativePhaseTimingSpecs) / sizeof(kNativePhaseTimingSpecs[0]);

constexpr size_t requiredNativePhaseTimingCount() {
    size_t count = 0;
    for (const NativePhaseTimingSpec& spec : kNativePhaseTimingSpecs) {
        if (spec.required) {
            count += 1;
        }
    }
    return count;
}

static_assert(requiredNativePhaseTimingCount() == 13);

std::string payloadLastValue(const std::vector<std::string>& payload, const std::string& key) {
    const std::string prefix = key + "=";
    for (auto iterator = payload.rbegin(); iterator != payload.rend(); ++iterator) {
        if (iterator->rfind(prefix, 0) == 0) {
            return iterator->substr(prefix.size());
        }
    }
    return "";
}

bool isNativePhaseTimingKey(const std::string& key) {
    for (const NativePhaseTimingSpec& spec : kNativePhaseTimingSpecs) {
        if (key == spec.timingKey) {
            return true;
        }
    }
    return false;
}

bool isUnsignedDecimal(const std::string& value) {
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

bool isNativePhaseStatusKey(const std::string& key) {
    if (key == "symbol.count") {
        return true;
    }
    for (const NativePhaseTimingSpec& spec : kNativePhaseTimingSpecs) {
        if (spec.statusKey != nullptr && key == spec.statusKey) {
            return true;
        }
    }
    return false;
}

bool isSafePhaseStatusValue(const std::string& value) {
    if (value.empty() || value.size() > 64) {
        return false;
    }
    for (const char ch : value) {
        if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_')) {
            return false;
        }
    }
    return true;
}

bool nativePhaseStatusMatches(
        const std::vector<std::string>& payload,
        const NativePhaseTimingSpec& spec
) {
    if (spec.statusPolicy == NativePhaseStatusPolicy::kPresent) {
        return true;
    }
    const std::string status = payloadLastValue(payload, spec.statusKey);
    switch (spec.statusPolicy) {
        case NativePhaseStatusPolicy::kDone:
            return status == "done";
        case NativePhaseStatusPolicy::kDoneOrReused:
            return status == "done" || status == "reused";
        case NativePhaseStatusPolicy::kScriptTerminal:
            return status == "completed" || status == "process_exit" || status == "failed";
        case NativePhaseStatusPolicy::kPositiveIntegerMarker:
            return isUnsignedDecimal(status) && status != "0";
        case NativePhaseStatusPolicy::kPresent:
            return true;
    }
    return false;
}

struct NativePhaseTimingCoverage {
    size_t count = 0;
    size_t requiredCount = 0;
    size_t requiredAvailableCount = 0;
};

NativePhaseTimingCoverage normalizeNativePhaseTimingPayload(
        std::vector<std::string>& payload
) {
    NativePhaseTimingCoverage coverage;
    std::vector<std::pair<std::string, std::string>> observed;
    observed.reserve(kNativePhaseTimingSpecCount);
    for (const NativePhaseTimingSpec& spec : kNativePhaseTimingSpecs) {
        if (spec.required) {
            coverage.requiredCount += 1;
        }
        const std::string value = payloadLastValue(payload, spec.timingKey);
        if (!isUnsignedDecimal(value) || !nativePhaseStatusMatches(payload, spec)) {
            continue;
        }
        observed.emplace_back(spec.timingKey, value);
        coverage.count += 1;
        if (spec.required) {
            coverage.requiredAvailableCount += 1;
        }
    }
    payload.erase(
            std::remove_if(
                    payload.begin(),
                    payload.end(),
                    [](const std::string& entry) {
                        const size_t separator = entry.find('=');
                        return separator != std::string::npos &&
                                isNativePhaseTimingKey(entry.substr(0, separator));
                    }
            ),
            payload.end()
    );
    for (const auto& entry : observed) {
        putPayload(payload, entry.first, entry.second);
    }
    return coverage;
}

size_t appendAdapterPhaseStatusDiagnostics(
        std::vector<std::string>& payload,
        const std::string& statusEntries
) {
    if (statusEntries.size() > kAdapterPhaseStatusPayloadMaxChars) {
        return 0;
    }
    size_t appended = 0;
    size_t start = 0;
    while (start < statusEntries.size() && appended < kNativePhaseTimingSpecCount) {
        const size_t end = statusEntries.find('\n', start);
        const size_t lineEnd = end == std::string::npos ? statusEntries.size() : end;
        const std::string entry = statusEntries.substr(start, lineEnd - start);
        const size_t separator = entry.find('=');
        if (separator != std::string::npos) {
            const std::string key = entry.substr(0, separator);
            const std::string value = entry.substr(separator + 1);
            const bool validValue = key == "symbol.count"
                    ? isUnsignedDecimal(value)
                    : isSafePhaseStatusValue(value);
            if (isNativePhaseStatusKey(key) &&
                    validValue &&
                    payloadLastValue(payload, key).empty()) {
                putPayload(payload, key, value);
                appended += 1;
            }
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return appended;
}

size_t appendAdapterPhaseTimingDiagnostics(
        std::vector<std::string>& payload,
        const char* diagnosticsJson
) {
    if (diagnosticsJson == nullptr) {
        return 0;
    }
    const std::string diagnostics(diagnosticsJson);
    if (jsonStringField(diagnostics, "phaseTimingSchema") != kNativePhaseTimingSchema ||
            jsonStringField(diagnostics, "phaseTimingSemantics") != kNativePhaseTimingSemantics ||
            jsonStringField(diagnostics, "scriptExecutionSemantics") != kNativeScriptExecutionSemantics ||
            jsonStringField(diagnostics, "phaseTimingClock") != kNativePhaseTimingClock) {
        return 0;
    }
    const std::string statusEntries = jsonStringField(diagnostics, "phaseTimingStatusPayload");
    const std::string timingEntries = jsonStringField(diagnostics, "phaseTimingPayload");
    if (statusEntries.size() > kAdapterPhaseStatusPayloadMaxChars ||
            timingEntries.size() > kAdapterPhaseTimingPayloadMaxChars) {
        return 0;
    }
    appendAdapterPhaseStatusDiagnostics(
            payload,
            statusEntries
    );

    size_t appended = 0;
    size_t start = 0;
    while (start < timingEntries.size() && appended < kNativePhaseTimingSpecCount) {
        const size_t end = timingEntries.find('\n', start);
        const size_t lineEnd = end == std::string::npos ? timingEntries.size() : end;
        const std::string entry = timingEntries.substr(start, lineEnd - start);
        const size_t separator = entry.find('=');
        if (separator != std::string::npos) {
            const std::string key = entry.substr(0, separator);
            const std::string value = entry.substr(separator + 1);
            if (isNativePhaseTimingKey(key) &&
                    isUnsignedDecimal(value)) {
                putPayload(payload, key, value);
                appended += 1;
            }
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }

    const std::string executionSourceBytes = jsonStringField(
            diagnostics,
            "executionSourceBytes"
    );
    if (isUnsignedDecimal(executionSourceBytes)) {
        putPayload(
                payload,
                "embedded_script.execution_source.bytes",
                executionSourceBytes
        );
    }
    return appended;
}

void appendNativePhaseTimingMetadata(
        std::vector<std::string>& payload,
        const char* source
) {
    const NativePhaseTimingCoverage coverage = normalizeNativePhaseTimingPayload(payload);
    const char* status = coverage.count == 0
            ? "unavailable"
            : (coverage.requiredAvailableCount == coverage.requiredCount ? "available" : "partial");
    putPayload(payload, "embedded_script.phase_timing.schema", kNativePhaseTimingSchema);
    putPayload(payload, "embedded_script.phase_timing.status", status);
    putPayload(payload, "embedded_script.phase_timing.count", static_cast<long long>(coverage.count));
    putPayload(
            payload,
            "embedded_script.phase_timing.required_count",
            static_cast<long long>(coverage.requiredCount)
    );
    putPayload(
            payload,
            "embedded_script.phase_timing.required_available_count",
            static_cast<long long>(coverage.requiredAvailableCount)
    );
    putPayload(payload, "embedded_script.phase_timing.source", source == nullptr ? "unknown" : source);
    putPayload(payload, "embedded_script.phase_timing.semantics", kNativePhaseTimingSemantics);
    putPayload(
            payload,
            "embedded_script.phase_timing.script_execution_semantics",
            kNativeScriptExecutionSemantics
    );
    putPayload(payload, "embedded_script.phase_timing.clock", kNativePhaseTimingClock);
    putPayload(payload, "embedded_script.phase_timing.non_additive", true);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_org_autojs_autojs_engine_NativeNodeRuntimeBridge_nativeRunMain(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray argv,
        jstring workingDirectory,
        jobject outputSink
) {
    const auto startedAt = Clock::now();
    std::vector<std::string> args = toStringVector(env, argv, "argv");
    if (env->ExceptionCheck()) {
        return -1;
    }
    if (args.empty()) {
        throwJava(env, "java/lang/IllegalArgumentException", "argv must contain at least argv[0]");
        return -1;
    }

    std::lock_guard<std::recursive_mutex> processRuntimeLock(embeddedProcessRuntimeExecutionMutex());
    std::vector<std::string> oneShotDiagnostics;
    if (!beginEmbeddedProcessRuntimeOneShotLifecycle(oneShotDiagnostics, "node_start_main")) {
        throwJava(
                env,
                "java/lang/IllegalStateException",
                "node::Start one-shot lifecycle requires a fresh process"
        );
        return -1;
    }
    std::lock_guard<std::mutex> lock(nodeStartMutex());

    ScopedWorkingDirectory scopedWorkingDirectory;
    if (!scopedWorkingDirectory.enter(env, toStdString(env, workingDirectory))) {
        return -1;
    }

    NodeStart nodeStart = resolveNodeStart(env);
    if (env->ExceptionCheck() || nodeStart == nullptr) {
        return -1;
    }

    std::vector<char*> rawArgs;
    rawArgs.reserve(args.size());
    for (std::string& arg : args) {
        rawArgs.push_back(arg.data());
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Starting Node.js with argc=%zu, script=%s",
            args.size(),
            args.size() > 1 ? args[1].c_str() : ""
    );

    ScopedOutputRedirection outputRedirection;
    if (!outputRedirection.start(env, outputSink)) {
        return -1;
    }

    const auto beforeNodeStart = Clock::now();
    int exitCode = nodeStart(static_cast<int>(rawArgs.size()), rawArgs.data());
    const auto afterNodeStart = Clock::now();
    outputRedirection.stop(env);
    const auto stoppedAt = Clock::now();
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Node.js bridge timings: prepare=%lldms, nodeStart=%lldms, outputStop=%lldms, total=%lldms, exitCode=%d",
            elapsedMs(startedAt, beforeNodeStart),
            elapsedMs(beforeNodeStart, afterNodeStart),
            elapsedMs(afterNodeStart, stoppedAt),
            elapsedMs(startedAt, stoppedAt),
            exitCode
    );
    return exitCode;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeRuntimeBridge_nativeMissingEmbedderSymbols(
        JNIEnv* env,
        jobject /* thiz */
) {
    void* handle = dlopen(kNodeLibraryName, RTLD_NOW | RTLD_GLOBAL);
    std::vector<std::string> missing;
    for (size_t i = 0; i < kEmbedderSymbolCount; ++i) {
        const SymbolRequirement& requirement = kEmbedderSymbols[i];
        if (!hasAnySymbol(handle, requirement)) {
            missing.emplace_back(requirement.description);
        }
    }
    return toJavaStringArray(env, missing);
}

static jobjectArray runEmbeddedScriptLifecycleNative(
        JNIEnv* env,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& envPairs,
        bool typeScriptPrecompiledSnapshot,
        const std::vector<std::string>& typeScriptPrecompiledSourceNames,
        bool esmEnabled,
        bool dynamicImportEnabled,
        bool rawNodeNetworkModulesEnabled,
        bool inspectorEnabled,
        bool workerThreadsEnabled,
        bool childProcessEnabled,
        bool javaInteropEnabled
) {
    EmbeddedScriptExecutionRequest request;
    request.source = toStdString(env, source);
    request.sourceName = toStdString(env, sourceName);
    request.workingDirectory = toStdString(env, workingDirectory);
    request.sandboxRoot = toStdString(env, sandboxRoot);
    request.moduleSources = moduleSourcePairs;
    request.runtimeModuleSources = runtimeModuleSourcePairs;
    request.env = envPairs;
    request.typeScriptPrecompiledSnapshot = typeScriptPrecompiledSnapshot;
    request.typeScriptPrecompiledSourceNames = typeScriptPrecompiledSourceNames;
    request.esmEnabled = esmEnabled;
    request.dynamicImportEnabled = dynamicImportEnabled;
    request.rawNodeNetworkModulesEnabled = rawNodeNetworkModulesEnabled;
    request.inspectorEnabled = inspectorEnabled;
    request.workerThreadsEnabled = workerThreadsEnabled;
    request.childProcessEnabled = childProcessEnabled;
    request.javaInteropEnabled = javaInteropEnabled;
    std::vector<std::string> payload = runEmbeddedScriptExecution(request);
    appendNativePhaseTimingMetadata(payload, "legacy_jni_lifecycle_payload");
    compactEmbeddedScriptLifecyclePayload(payload);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEnsureProcessRuntimeReady(
        JNIEnv* env,
        jobject /* thiz */
) {
    std::vector<std::string> payload;
    ensureEmbeddedProcessRuntime(payload);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeProcessRuntimeDiagnostics(
        JNIEnv* env,
        jobject /* thiz */
) {
    std::vector<std::string> payload;
    appendEmbeddedProcessRuntimeDiagnostics(payload);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeShutdownProcessRuntime(
        JNIEnv* env,
        jobject /* thiz */,
        jstring reason
) {
    std::vector<std::string> payload;
    putPayload(payload, "process_runtime.shutdown.reason", toStdString(env, reason));
    shutdownEmbeddedProcessRuntime(payload);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeSetProcessRuntimePersistentEnabled(
        JNIEnv* env,
        jobject /* thiz */,
        jboolean enabled
) {
    std::vector<std::string> payload;
    setEmbeddedProcessRuntimePersistentEnabled(enabled == JNI_TRUE, payload);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeBeginScriptStopScope(
        JNIEnv* env,
        jobject /* thiz */,
        jstring executionTag
) {
    beginActiveScriptStopScope(toStdString(env, executionTag).c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEndScriptStopScope(
        JNIEnv* /* env */,
        jobject /* thiz */
) {
    endActiveScriptStopScope();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRequestScriptStop(
        JNIEnv* env,
        jobject /* thiz */,
        jstring executionTag
) {
    return requestActiveScriptStop(toStdString(env, executionTag).c_str()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeSetOutputStreamSink(
        JNIEnv* env,
        jobject /* thiz */,
        jobject sink
) {
    std::shared_ptr<JavaOutputSink> previous = currentOutputStreamSink();
    if (sink == nullptr) {
        setCurrentOutputStreamSink(nullptr);
        if (previous != nullptr) {
            previous->release(env);
        }
        return;
    }
    std::shared_ptr<JavaOutputSink> next = std::make_shared<JavaOutputSink>(env, sink);
    if (env->ExceptionCheck() || !next->available()) {
        next->release(env);
        return;
    }
    setCurrentOutputStreamSink(next);
    if (previous != nullptr) {
        previous->release(env);
    }
}

static const char* adapterResultCodeName(int32_t code) {
    switch (code) {
        case AUTOJS_NODE_RESULT_OK:
            return "ok";
        case AUTOJS_NODE_RESULT_INVALID_ARGUMENT:
            return "invalid_argument";
        case AUTOJS_NODE_RESULT_UNAVAILABLE:
            return "unavailable";
        case AUTOJS_NODE_RESULT_INTERRUPTED:
            return "interrupted";
        case AUTOJS_NODE_RESULT_FAILED:
            return "failed";
        default:
            return "unknown";
    }
}

namespace {

constexpr size_t kSignalSafeMarkerRecordBytes = 256;
constexpr const char* kSignalSafeMarkerSchema = "autojs6-node-phase13-signal-safe-crash-marker-v1";
constexpr const char* kSignalSafeMarkerWriter = "native_fixed_size_signal_safe_writer";

struct SignalSafeMarkerWriteResult {
    bool succeeded = false;
    ssize_t bytesWritten = 0;
    int errorNumber = 0;
};

void appendSignalSafeLiteral(char* buffer, size_t* offset, const char* value) {
    if (value == nullptr) {
        return;
    }
    while (*value != '\0' && *offset < kSignalSafeMarkerRecordBytes - 1) {
        buffer[*offset] = *value;
        ++(*offset);
        ++value;
    }
}

void appendSignalSafeTruncated(char* buffer, size_t* offset, const char* value, size_t maxBytes) {
    if (value == nullptr) {
        return;
    }
    size_t copied = 0;
    while (*value != '\0' && copied < maxBytes && *offset < kSignalSafeMarkerRecordBytes - 1) {
        const char next = *value;
        buffer[*offset] = next == '\n' || next == '\r' ? '_' : next;
        ++(*offset);
        ++value;
        ++copied;
    }
}

void appendSignalSafeUnsigned(char* buffer, size_t* offset, unsigned long long value) {
    char digits[32];
    size_t count = 0;
    do {
        digits[count++] = static_cast<char>('0' + (value % 10));
        value /= 10;
    } while (value > 0 && count < sizeof(digits));
    while (count > 0 && *offset < kSignalSafeMarkerRecordBytes - 1) {
        buffer[*offset] = digits[--count];
        ++(*offset);
    }
}

void appendSignalSafeSigned(char* buffer, size_t* offset, long long value) {
    if (value < 0) {
        appendSignalSafeLiteral(buffer, offset, "-");
        appendSignalSafeUnsigned(buffer, offset, static_cast<unsigned long long>(-(value + 1)) + 1ULL);
    } else {
        appendSignalSafeUnsigned(buffer, offset, static_cast<unsigned long long>(value));
    }
}

SignalSafeMarkerWriteResult writeSignalSafeCrashMarkerRecord(
        const char* path,
        const char* markerId,
        const char* executionId,
        const char* scenario,
        const char* reason,
        int pid,
        long long epochMs
) {
    char record[kSignalSafeMarkerRecordBytes];
    for (size_t index = 0; index < sizeof(record); ++index) {
        record[index] = ' ';
    }
    size_t offset = 0;
    appendSignalSafeLiteral(record, &offset, "AJ6SIG13 ");
    appendSignalSafeLiteral(record, &offset, "schema=");
    appendSignalSafeLiteral(record, &offset, kSignalSafeMarkerSchema);
    appendSignalSafeLiteral(record, &offset, " writer=");
    appendSignalSafeLiteral(record, &offset, kSignalSafeMarkerWriter);
    appendSignalSafeLiteral(record, &offset, " marker=");
    appendSignalSafeTruncated(record, &offset, markerId, 40);
    appendSignalSafeLiteral(record, &offset, " exec=");
    appendSignalSafeTruncated(record, &offset, executionId, 40);
    appendSignalSafeLiteral(record, &offset, " scenario=");
    appendSignalSafeTruncated(record, &offset, scenario, 36);
    appendSignalSafeLiteral(record, &offset, " reason=");
    appendSignalSafeTruncated(record, &offset, reason, 28);
    appendSignalSafeLiteral(record, &offset, " pid=");
    appendSignalSafeSigned(record, &offset, static_cast<long long>(pid));
    appendSignalSafeLiteral(record, &offset, " epochMs=");
    appendSignalSafeSigned(record, &offset, epochMs);
    record[kSignalSafeMarkerRecordBytes - 1] = '\n';

    const int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, S_IRUSR | S_IWUSR);
    if (fd < 0) {
        return {false, 0, errno};
    }

    size_t total = 0;
    while (total < sizeof(record)) {
        const ssize_t written = write(fd, record + total, sizeof(record) - total);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            const int errorNumber = errno;
            close(fd);
            return {false, static_cast<ssize_t>(total), errorNumber};
        }
        if (written == 0) {
            close(fd);
            return {false, static_cast<ssize_t>(total), EIO};
        }
        total += static_cast<size_t>(written);
    }

    if (close(fd) != 0) {
        return {false, static_cast<ssize_t>(total), errno};
    }
    return {true, static_cast<ssize_t>(total), 0};
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeWriteSignalSafeCrashMarker(
        JNIEnv* env,
        jobject /* thiz */,
        jstring markerPath,
        jstring markerId,
        jstring executionId,
        jstring scenario,
        jstring reason,
        jint pid,
        jlong epochMs
) {
    const auto startedAt = Clock::now();
    const std::string path = toStdString(env, markerPath);
    const std::string markerIdText = toStdString(env, markerId);
    const std::string executionIdText = toStdString(env, executionId);
    const std::string scenarioText = toStdString(env, scenario);
    const std::string reasonText = toStdString(env, reason);
    std::vector<std::string> payload;
    putPayload(payload, "signal_safe_marker.schema", kSignalSafeMarkerSchema);
    putPayload(payload, "signal_safe_marker.writer", kSignalSafeMarkerWriter);
    putPayload(payload, "signal_safe_marker.record_bytes", static_cast<long long>(kSignalSafeMarkerRecordBytes));
    putPayload(payload, "signal_safe_marker.path", path);
    putPayload(payload, "signal_safe_marker.marker_id", markerIdText);
    putPayload(payload, "signal_safe_marker.execution_id", executionIdText);
    putPayload(payload, "signal_safe_marker.scenario", scenarioText);
    putPayload(payload, "signal_safe_marker.reason", reasonText);
    putPayload(payload, "signal_safe_marker.pid", static_cast<long long>(pid));
    putPayload(payload, "signal_safe_marker.epoch_ms", static_cast<long long>(epochMs));
    putPayload(payload, "signal_safe_marker.append_only", true);
    putPayload(payload, "signal_safe_marker.native_core", "open_write_close_fixed_buffer");
    putPayload(payload, "signal_safe_marker.signal_safe_syscalls", "open|write|close");

    if (path.empty()) {
        putPayload(payload, "signal_safe_marker.status", "failed");
        putPayload(payload, "signal_safe_marker.error", "marker path is empty");
        putPayload(payload, "signal_safe_marker.bytes_written", static_cast<long long>(0));
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        return toJavaStringArray(env, payload);
    }

    const SignalSafeMarkerWriteResult result = writeSignalSafeCrashMarkerRecord(
            path.c_str(),
            markerIdText.c_str(),
            executionIdText.c_str(),
            scenarioText.c_str(),
            reasonText.c_str(),
            static_cast<int>(pid),
            static_cast<long long>(epochMs)
    );
    putPayload(payload, "signal_safe_marker.status", result.succeeded ? "written" : "failed");
    putPayload(payload, "signal_safe_marker.bytes_written", static_cast<long long>(result.bytesWritten));
    putPayload(payload, "signal_safe_marker.errno", static_cast<long long>(result.errorNumber));
    if (!result.succeeded) {
        putPayload(payload, "signal_safe_marker.error", std::strerror(result.errorNumber));
    }
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    return toJavaStringArray(env, payload);
}

static std::string quotedJsonString(const std::string& value) {
    return std::string("\"") + jsonStringLiteral(value) + "\"";
}

static std::string orderedStringPairsJson(const std::vector<std::pair<std::string, std::string>>& pairs) {
    std::string json = "[";
    for (size_t index = 0; index < pairs.size(); ++index) {
        if (index > 0) {
            json += ",";
        }
        json += "[";
        json += quotedJsonString(pairs[index].first);
        json += ",";
        json += quotedJsonString(pairs[index].second);
        json += "]";
    }
    json += "]";
    return json;
}

static std::string adapterModuleSourcesJson(
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::string& sandboxRoot
) {
    return std::string("{\"sandboxRoot\":") + quotedJsonString(sandboxRoot) +
            ",\"moduleSources\":" + orderedStringPairsJson(moduleSourcePairs) +
            ",\"runtimeModuleSources\":" + orderedStringPairsJson(runtimeModuleSourcePairs) + "}";
}

static uint32_t adapterExecutionFlags(
        bool esmEnabled,
        bool dynamicImportEnabled,
        bool rawNodeNetworkModulesEnabled,
        bool workerThreadsEnabled,
        bool childProcessEnabled,
        bool javaInteropEnabled
) {
    uint32_t flags = 0;
    if (esmEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_ESM;
    }
    if (dynamicImportEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_DYNAMIC_IMPORT;
    }
    if (rawNodeNetworkModulesEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_RAW_NODE_NETWORK_MODULES;
    }
    if (workerThreadsEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_WORKER_THREADS;
    }
    if (childProcessEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_CHILD_PROCESS;
    }
    if (javaInteropEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_JAVA_INTEROP;
    }
    return flags;
}

static jobjectArray runEmbeddedScriptAdapterV1ExecuteNative(
        JNIEnv* env,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& envPairs,
        bool esmEnabled,
        bool dynamicImportEnabled,
        bool rawNodeNetworkModulesEnabled,
        bool workerThreadsEnabled,
        bool childProcessEnabled,
        bool javaInteropEnabled
) {
    const auto startedAt = Clock::now();
    const std::string sourceText = toStdString(env, source);
    const std::string sourceNameInput = toStdString(env, sourceName);
    const std::string sourceNameText = sourceNameInput.empty()
            ? "<embedded-user-script.js>"
            : sourceNameInput;
    const std::string workingDirectoryText = toStdString(env, workingDirectory);
    const std::string sandboxRootText = toStdString(env, sandboxRoot);
    const std::string moduleSourcesJson = adapterModuleSourcesJson(moduleSourcePairs, runtimeModuleSourcePairs, sandboxRootText);
    const std::string environmentJson = orderedStringPairsJson(envPairs);
    std::vector<std::string> payload;
    putPayload(payload, "embedded_script.request.source_name", sourceNameText);
    putPayload(payload, "embedded_script.request.source.length", static_cast<long long>(sourceText.size()));
    putPayload(payload, "embedded_script.request.working_directory", workingDirectoryText);
    putPayload(payload, "embedded_script.request.sandbox_root", sandboxRootText);
    putPayload(payload, "embedded_script.request.env.count", static_cast<long long>(envPairs.size()));
    putPayload(payload, "embedded_script.request.module_sources.count", static_cast<long long>(moduleSourcePairs.size()));
    putPayload(
            payload,
            "embedded_script.request.runtime_module_sources.count",
            static_cast<long long>(runtimeModuleSourcePairs.size())
    );
    putPayload(payload, "embedded_script.request.source_kind", "kotlin_provided_source");
    putPayload(payload, "embedded_script.request.user_file_read_by_native", false);
    putPayload(payload, "embedded_script.runtime_adapter.path", "adapter_v1");
    putPayload(payload, "embedded_script.runtime_adapter.fallback_path", "legacy_jni");
    putPayload(payload, "embedded_script.runtime_adapter.mode", "execution_path");

    const AutoJsNodeRuntimeApiV1* api = autojs_node_get_runtime_api_v1();
    if (api == nullptr) {
        putPayload(payload, "embedded_script.runtime_adapter.status", "api_unavailable");
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "autojs_node_get_runtime_api_v1 returned null");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_API_UNAVAILABLE");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        appendNativePhaseTimingMetadata(payload, "adapter_v1_api_unavailable");
        return toJavaStringArray(env, payload);
    }

    putPayload(payload, "embedded_script.runtime_adapter.abi_version", static_cast<long long>(api->abi_version));
    putPayload(payload, "embedded_script.runtime_adapter.struct_size", static_cast<long long>(api->struct_size));
    putPayload(payload, "embedded_script.runtime_adapter.feature_bits", static_cast<long long>(api->feature_bits));
    putPayload(payload, "embedded_script.runtime_adapter.node_version", api->get_node_version == nullptr ? "" : api->get_node_version());
    putPayload(
            payload,
            "embedded_script.runtime_adapter.runtime_descriptor_json",
            api->get_runtime_descriptor == nullptr ? "" : api->get_runtime_descriptor()
    );

    AutoJsNodeCreateOptions createOptions{};
    createOptions.struct_size = sizeof(AutoJsNodeCreateOptions);
    createOptions.runtime_descriptor_json = "{\"requestedPath\":\"adapter_v1\",\"host\":\"autojs6\"}";
    createOptions.working_directory = workingDirectoryText.empty() ? nullptr : workingDirectoryText.c_str();
    createOptions.initial_environment_json = environmentJson.c_str();
    createOptions.timeout_ms = 0;
    AutoJsNodeHandle* handle = nullptr;
    const int32_t createCode = api->create_runtime == nullptr
            ? AUTOJS_NODE_RESULT_FAILED
            : api->create_runtime(&createOptions, &handle);
    putPayload(payload, "embedded_script.runtime_adapter.create_result_code", static_cast<long long>(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.create_result", adapterResultCodeName(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.handle_created", handle != nullptr);
    if (createCode != AUTOJS_NODE_RESULT_OK || handle == nullptr) {
        putPayload(payload, "embedded_script.runtime_adapter.status", "create_failed");
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "adapter_v1 create_runtime failed");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_CREATE_FAILED");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        appendNativePhaseTimingMetadata(payload, "adapter_v1_create_failed");
        return toJavaStringArray(env, payload);
    }

    AutoJsNodeExecutionRequest executionRequest{};
    executionRequest.struct_size = sizeof(AutoJsNodeExecutionRequest);
    executionRequest.flags = adapterExecutionFlags(
            esmEnabled,
            dynamicImportEnabled,
            rawNodeNetworkModulesEnabled,
            workerThreadsEnabled,
            childProcessEnabled,
            javaInteropEnabled
    );
    executionRequest.source_name = sourceNameText.c_str();
    executionRequest.source = {
            sourceText.data(),
            static_cast<uint64_t>(sourceText.size())
    };
    executionRequest.working_directory = workingDirectoryText.empty() ? nullptr : workingDirectoryText.c_str();
    executionRequest.module_sources_json = moduleSourcesJson.c_str();
    executionRequest.environment_json = environmentJson.c_str();
    executionRequest.timeout_ms = 0;
    executionRequest.execution_id = 0;
    AutoJsNodeExecutionResult executionResult{};
    executionResult.struct_size = sizeof(AutoJsNodeExecutionResult);
    const int32_t executeCode = api->execute_script == nullptr
            ? AUTOJS_NODE_RESULT_FAILED
            : api->execute_script(handle, &executionRequest, &executionResult);
    putPayload(payload, "embedded_script.runtime_adapter.execute_result_code", static_cast<long long>(executeCode));
    putPayload(payload, "embedded_script.runtime_adapter.execute_result", adapterResultCodeName(executeCode));
    putPayload(payload, "embedded_script.runtime_adapter.execution_result_code", static_cast<long long>(executionResult.result_code));
    const std::string executionDiagnosticsJson = executionResult.diagnostics_json == nullptr
            ? ""
            : executionResult.diagnostics_json;
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_json", executionDiagnosticsJson);
    if (!executionDiagnosticsJson.empty()) {
        putPayload(
                payload,
                "execution.teardown_clean",
                jsonStringField(executionDiagnosticsJson, "executionTeardownClean")
        );
    }
    putPayload(payload, "embedded_script.runtime_adapter.status", executeCode == AUTOJS_NODE_RESULT_OK ? "executed" : "execute_failed");
    if (executionResult.result_json != nullptr && std::strlen(executionResult.result_json) > 0) {
        putPayload(payload, "embedded_script.status", executeCode == AUTOJS_NODE_RESULT_OK ? "done" : "failed");
        putEmbeddedScriptExecutionFields(payload, executionResult.result_json);
        putPayloadRaw(payload, "embedded_script.stdout", executionResult.stdout_text == nullptr ? "" : executionResult.stdout_text);
        putPayloadRaw(payload, "embedded_script.stderr", executionResult.stderr_text == nullptr ? "" : executionResult.stderr_text);
    } else {
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "adapter_v1 did not return a result envelope");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_RESULT_MISSING");
    }

    if (api->destroy_runtime != nullptr) {
        const int32_t destroyCode = api->destroy_runtime(handle);
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result_code", static_cast<long long>(destroyCode));
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result", adapterResultCodeName(destroyCode));
    }
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    const size_t transferredTimingCount = appendAdapterPhaseTimingDiagnostics(
            payload,
            executionDiagnosticsJson.c_str()
    );
    putPayload(
            payload,
            "embedded_script.phase_timing.adapter_transferred_count",
            static_cast<long long>(transferredTimingCount)
    );
    appendNativePhaseTimingMetadata(payload, "adapter_v1_last_payload_allowlist");
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptAdapterV1Execute(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray runtimeModuleSourceNames,
        jobjectArray runtimeModuleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmEnabled,
        jboolean dynamicImportEnabled,
        jboolean rawNodeNetworkModulesEnabled,
        jboolean workerThreadsEnabled,
        jboolean childProcessEnabled,
        jboolean javaInteropEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceNameValues = toStringVectorOrEmpty(env, runtimeModuleSourceNames, "runtimeModuleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceValues = toStringVectorOrEmpty(env, runtimeModuleSources, "runtimeModuleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (runtimeModuleSourceNameValues.size() != runtimeModuleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "runtimeModuleSourceNames.size != runtimeModuleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> runtimeModuleSourcePairs;
    runtimeModuleSourcePairs.reserve(runtimeModuleSourceNameValues.size());
    for (size_t index = 0; index < runtimeModuleSourceNameValues.size(); ++index) {
        runtimeModuleSourcePairs.emplace_back(runtimeModuleSourceNameValues[index], runtimeModuleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptAdapterV1ExecuteNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            runtimeModuleSourcePairs,
            envPairs,
            esmEnabled == JNI_TRUE,
            dynamicImportEnabled == JNI_TRUE,
            rawNodeNetworkModulesEnabled == JNI_TRUE,
            workerThreadsEnabled == JNI_TRUE,
            childProcessEnabled == JNI_TRUE,
            javaInteropEnabled == JNI_TRUE
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithModulesV2(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmEnabled,
        jboolean dynamicImportEnabled,
        jboolean rawNodeNetworkModulesEnabled,
        jboolean workerThreadsEnabled,
        jboolean childProcessEnabled,
        jboolean javaInteropEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            {},
            envPairs,
            false,
            {},
            esmEnabled == JNI_TRUE,
            dynamicImportEnabled == JNI_TRUE,
            rawNodeNetworkModulesEnabled == JNI_TRUE,
            false,
            workerThreadsEnabled == JNI_TRUE,
            childProcessEnabled == JNI_TRUE,
            javaInteropEnabled == JNI_TRUE
    );
}

// Legacy packaged templates with runtime module sources pass seven JNI
// arguments. Keep this entry point separate from the current V2 signature.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithRuntimeModulesV2(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray runtimeModuleSourceNames,
        jobjectArray runtimeModuleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean typeScriptPrecompiledSnapshot,
        jobjectArray typeScriptPrecompiledSourceNames,
        jboolean esmEnabled,
        jboolean dynamicImportEnabled,
        jboolean rawNodeNetworkModulesEnabled,
        jboolean inspectorEnabled,
        jboolean workerThreadsEnabled,
        jboolean childProcessEnabled,
        jboolean javaInteropEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceNameValues = toStringVectorOrEmpty(env, runtimeModuleSourceNames, "runtimeModuleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceValues = toStringVectorOrEmpty(env, runtimeModuleSources, "runtimeModuleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> typeScriptPrecompiledSourceNameValues = toStringVectorOrEmpty(
            env,
            typeScriptPrecompiledSourceNames,
            "typeScriptPrecompiledSourceNames"
    );
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (runtimeModuleSourceNameValues.size() != runtimeModuleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "runtimeModuleSourceNames.size != runtimeModuleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> runtimeModuleSourcePairs;
    runtimeModuleSourcePairs.reserve(runtimeModuleSourceNameValues.size());
    for (size_t index = 0; index < runtimeModuleSourceNameValues.size(); ++index) {
        runtimeModuleSourcePairs.emplace_back(runtimeModuleSourceNameValues[index], runtimeModuleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            runtimeModuleSourcePairs,
            envPairs,
            typeScriptPrecompiledSnapshot == JNI_TRUE,
            typeScriptPrecompiledSourceNameValues,
            esmEnabled == JNI_TRUE,
            dynamicImportEnabled == JNI_TRUE,
            rawNodeNetworkModulesEnabled == JNI_TRUE,
            inspectorEnabled == JNI_TRUE,
            workerThreadsEnabled == JNI_TRUE,
            childProcessEnabled == JNI_TRUE,
            javaInteropEnabled == JNI_TRUE
    );
}

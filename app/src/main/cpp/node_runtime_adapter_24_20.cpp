#include "node_runtime_api_v1.h"

#include "node_bridge_internal.h"
#include "node_version.h"

#include <atomic>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

struct AutoJsNodeHandle {
    uint64_t runtimeId = 0;
    uint64_t executionId = 0;
    uint32_t state = AUTOJS_NODE_RUNTIME_STATE_CREATED;
    bool destroyed = false;
    bool stopRequested = false;
    std::string workingDirectory;
    std::string runtimeDescriptor;
    std::string stateJson;
    std::string resourceJson = "{}";
    std::string lastErrorJson = "{}";
    std::string lastResultJson = "{}";
    std::string lastDiagnosticsJson = "{}";
    std::string stdoutText;
    std::string stderrText;
    std::vector<std::string> lastPayload;
};

namespace {

using autojs6::node_bridge::fieldValueAfterPrefix;
using autojs6::node_bridge::internal::EmbeddedScriptExecutionRequest;
using autojs6::node_bridge::internal::runEmbeddedScriptExecution;
using autojs6::node_bridge::internal::jsonStringLiteral;

std::string quotedJsonString(const std::string& value) {
    return std::string("\"") + jsonStringLiteral(value) + "\"";
}

constexpr uint64_t kFeatureBits =
        AUTOJS_NODE_RUNTIME_FEATURE_SCRIPT |
        AUTOJS_NODE_RUNTIME_FEATURE_COMMONJS |
        AUTOJS_NODE_RUNTIME_FEATURE_ESM |
        AUTOJS_NODE_RUNTIME_FEATURE_MODULE_SOURCES |
        AUTOJS_NODE_RUNTIME_FEATURE_ENCRYPTED_MODULE_SOURCES |
        AUTOJS_NODE_RUNTIME_FEATURE_PACKAGED_DESCRIPTOR |
        AUTOJS_NODE_RUNTIME_FEATURE_POLL |
        AUTOJS_NODE_RUNTIME_FEATURE_STOP |
        AUTOJS_NODE_RUNTIME_FEATURE_DIAGNOSTICS;

const char kRuntimeDescriptor[] =
        "{\"runtimeSlot\":\"node24_20\","
        "\"nodeVersion\":\"" NODE_VERSION "\","
        "\"adapterAbiVersion\":1,"
        "\"status\":\"adapter_v1_execution_path\","
        "\"execute\":\"embedded_lifecycle_node24_20\","
        "\"defaultAllowed\":false}";

const char kInvalidHandleError[] =
        "{\"code\":\"ERR_AUTOJS6_NODE_ADAPTER_INVALID_HANDLE\","
        "\"message\":\"Adapter runtime handle is invalid or destroyed.\"}";

const char kInvalidArgumentDiagnostics[] =
        "{\"code\":\"ERR_AUTOJS6_NODE_ADAPTER_INVALID_ARGUMENT\","
        "\"phaseTimingSchema\":\"autojs6-node-native-lifecycle-timing-v1\","
        "\"phaseTimingStatus\":\"unavailable\","
        "\"phaseTimingCount\":0,"
        "\"phaseTimingRequiredCount\":13,"
        "\"phaseTimingRequiredAvailableCount\":0,"
        "\"phaseTimingPayload\":\"\","
        "\"phaseTimingStatusPayload\":\"\","
        "\"phaseTimingSemantics\":\"nested_non_additive\","
        "\"scriptExecutionSemantics\":\"entry_to_terminal_completion\","
        "\"phaseTimingClock\":\"native_steady_clock_and_node_hrtime_bigint\","
        "\"executionSourceBytes\":\"\"}";

std::atomic<uint64_t> gNextRuntimeId{1};
std::mutex gHandleMutex;
std::unordered_set<AutoJsNodeHandle*> gLiveHandles;

class JsonCursor {
public:
    explicit JsonCursor(const char* text)
            : text_(text == nullptr ? "" : text),
              size_(std::strlen(text_)) {}

    void skipWhitespace() {
        while (position_ < size_ && std::isspace(static_cast<unsigned char>(text_[position_]))) {
            ++position_;
        }
    }

    bool done() {
        skipWhitespace();
        return position_ >= size_;
    }

    bool consume(char expected) {
        skipWhitespace();
        if (position_ >= size_ || text_[position_] != expected) {
            error_ = std::string("expected '") + expected + "'";
            return false;
        }
        ++position_;
        return true;
    }

    bool peek(char expected) {
        skipWhitespace();
        return position_ < size_ && text_[position_] == expected;
    }

    bool parseString(std::string& out) {
        skipWhitespace();
        if (position_ >= size_ || text_[position_] != '"') {
            error_ = "expected JSON string";
            return false;
        }
        ++position_;
        out.clear();
        while (position_ < size_) {
            const char ch = text_[position_++];
            if (ch == '"') {
                return true;
            }
            if (ch != '\\') {
                out.push_back(ch);
                continue;
            }
            if (position_ >= size_) {
                error_ = "unterminated JSON escape";
                return false;
            }
            const char escaped = text_[position_++];
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    out.push_back(escaped);
                    break;
                case 'b':
                    out.push_back('\b');
                    break;
                case 'f':
                    out.push_back('\f');
                    break;
                case 'n':
                    out.push_back('\n');
                    break;
                case 'r':
                    out.push_back('\r');
                    break;
                case 't':
                    out.push_back('\t');
                    break;
                case 'u':
                    if (!parseUnicodeEscape(out)) {
                        return false;
                    }
                    break;
                default:
                    error_ = "unsupported JSON escape";
                    return false;
            }
        }
        error_ = "unterminated JSON string";
        return false;
    }

    bool skipValue() {
        skipWhitespace();
        if (position_ >= size_) {
            error_ = "unexpected end of JSON";
            return false;
        }
        if (text_[position_] == '"') {
            std::string ignored;
            return parseString(ignored);
        }
        if (text_[position_] == '{') {
            ++position_;
            skipWhitespace();
            if (peek('}')) {
                ++position_;
                return true;
            }
            while (position_ < size_) {
                std::string key;
                if (!parseString(key) || !consume(':') || !skipValue()) {
                    return false;
                }
                skipWhitespace();
                if (peek('}')) {
                    ++position_;
                    return true;
                }
                if (!consume(',')) {
                    return false;
                }
            }
            error_ = "unterminated JSON object";
            return false;
        }
        if (text_[position_] == '[') {
            ++position_;
            skipWhitespace();
            if (peek(']')) {
                ++position_;
                return true;
            }
            while (position_ < size_) {
                if (!skipValue()) {
                    return false;
                }
                skipWhitespace();
                if (peek(']')) {
                    ++position_;
                    return true;
                }
                if (!consume(',')) {
                    return false;
                }
            }
            error_ = "unterminated JSON array";
            return false;
        }
        while (position_ < size_) {
            const char ch = text_[position_];
            if (ch == ',' || ch == ']' || ch == '}' || std::isspace(static_cast<unsigned char>(ch))) {
                break;
            }
            ++position_;
        }
        return true;
    }

    const std::string& error() const {
        return error_;
    }

private:
    bool parseUnicodeEscape(std::string& out) {
        if (position_ + 4 > size_) {
            error_ = "truncated JSON unicode escape";
            return false;
        }
        uint32_t value = 0;
        for (int i = 0; i < 4; ++i) {
            const char ch = text_[position_++];
            value <<= 4;
            if (ch >= '0' && ch <= '9') {
                value += static_cast<uint32_t>(ch - '0');
            } else if (ch >= 'a' && ch <= 'f') {
                value += static_cast<uint32_t>(ch - 'a' + 10);
            } else if (ch >= 'A' && ch <= 'F') {
                value += static_cast<uint32_t>(ch - 'A' + 10);
            } else {
                error_ = "invalid JSON unicode escape";
                return false;
            }
        }
        appendUtf8(out, value);
        return true;
    }

    static void appendUtf8(std::string& out, uint32_t value) {
        if (value <= 0x7f) {
            out.push_back(static_cast<char>(value));
        } else if (value <= 0x7ff) {
            out.push_back(static_cast<char>(0xc0 | ((value >> 6) & 0x1f)));
            out.push_back(static_cast<char>(0x80 | (value & 0x3f)));
        } else {
            out.push_back(static_cast<char>(0xe0 | ((value >> 12) & 0x0f)));
            out.push_back(static_cast<char>(0x80 | ((value >> 6) & 0x3f)));
            out.push_back(static_cast<char>(0x80 | (value & 0x3f)));
        }
    }

    const char* text_;
    size_t size_ = 0;
    size_t position_ = 0;
    std::string error_;
};

std::string payloadValue(const std::vector<std::string>& payload, const char* key) {
    const std::string prefix = std::string(key) + "=";
    for (const std::string& entry : payload) {
        std::string value = fieldValueAfterPrefix(entry, prefix.c_str());
        if (!value.empty() || entry.rfind(prefix, 0) == 0) {
            return value;
        }
    }
    return "";
}

std::string payloadLastValue(const std::vector<std::string>& payload, const char* key) {
    const std::string prefix = std::string(key) + "=";
    for (auto iterator = payload.rbegin(); iterator != payload.rend(); ++iterator) {
        std::string value = fieldValueAfterPrefix(*iterator, prefix.c_str());
        if (!value.empty() || iterator->rfind(prefix, 0) == 0) {
            return value;
        }
    }
    return "";
}

constexpr const char* kPhaseTimingSchema = "autojs6-node-native-lifecycle-timing-v1";
constexpr const char* kPhaseTimingSemantics = "nested_non_additive";
constexpr const char* kScriptExecutionSemantics = "entry_to_terminal_completion";
constexpr const char* kPhaseTimingClock = "native_steady_clock_and_node_hrtime_bigint";

enum class PhaseStatusPolicy {
    kPresent,
    kDone,
    kDoneOrReused,
    kScriptTerminal,
    kPositiveIntegerMarker,
};

struct PhaseTimingSpec {
    const char* timingKey;
    const char* statusKey;
    PhaseStatusPolicy statusPolicy;
    bool required;
};

constexpr PhaseTimingSpec kPhaseTimingSpecs[] = {
        {"timing.execution_source_build.ms", nullptr, PhaseStatusPolicy::kPresent, true},
        {"timing.process_runtime_initialize.ms", "process_runtime.ensure.status", PhaseStatusPolicy::kDone, false},
        {"timing.load.ms", nullptr, PhaseStatusPolicy::kPresent, false},
        {"timing.symbols.ms", "symbol.count", PhaseStatusPolicy::kPositiveIntegerMarker, false},
        {"timing.initialize.ms", "initialize.status", PhaseStatusPolicy::kDoneOrReused, false},
        {"timing.platform_create.ms", "platform.create.status", PhaseStatusPolicy::kDoneOrReused, false},
        {"timing.v8_initialize_platform.ms", "v8.initialize_platform.status", PhaseStatusPolicy::kDoneOrReused, false},
        {"timing.v8_initialize.ms", "v8.initialize.status", PhaseStatusPolicy::kDoneOrReused, false},
        {"timing.uv_loop_init.ms", "uv.loop.init.status", PhaseStatusPolicy::kDone, true},
        {"timing.allocator_create.ms", "allocator.create.status", PhaseStatusPolicy::kDone, false},
        {"timing.isolate_create.ms", "isolate.create.status", PhaseStatusPolicy::kDone, true},
        {"timing.isolate_data_create.ms", "isolate_data.create.status", PhaseStatusPolicy::kDone, false},
        {"timing.environment_create.ms", "environment.create.status", PhaseStatusPolicy::kDone, true},
        {"timing.load_environment.ms", "load_environment.status", PhaseStatusPolicy::kDone, true},
        {"timing.bootstrap_script.ms", "bootstrap_script.status", PhaseStatusPolicy::kDone, false},
        {"timing.bootstrap.ms", "embedded_script.phase.bootstrap.status", PhaseStatusPolicy::kScriptTerminal, true},
        {"timing.module_preload.ms", "embedded_script.phase.module_preload.status", PhaseStatusPolicy::kDone, false},
        {"timing.script_execution.ms", "embedded_script.phase.script_execution.status", PhaseStatusPolicy::kScriptTerminal, true},
        {"timing.spin_event_loop.ms", "spin_event_loop.status", PhaseStatusPolicy::kDone, true},
        {"timing.completion_drain.ms", "completion_drain.status", PhaseStatusPolicy::kDone, false},
        {"timing.embedded_script_result.ms", "embedded_script.status", PhaseStatusPolicy::kDone, true},
        {"timing.js_result.ms", "js_result.status", PhaseStatusPolicy::kDone, false},
        {"timing.output_envelope.ms", "output_envelope.status", PhaseStatusPolicy::kDone, false},
        {"timing.stdout_capture.ms", "stdout_capture.status", PhaseStatusPolicy::kDone, false},
        {"timing.environment_free.ms", "environment.free.status", PhaseStatusPolicy::kDone, true},
        {"timing.isolate_data_free.ms", "isolate_data.free.status", PhaseStatusPolicy::kDone, false},
        {"timing.isolate_unregister.ms", "isolate.unregister.status", PhaseStatusPolicy::kDone, false},
        {"timing.isolate_dispose.ms", "isolate.dispose.status", PhaseStatusPolicy::kDone, true},
        {"timing.uv_run.ms", "uv.run.status", PhaseStatusPolicy::kDone, false},
        {"timing.uv_close.ms", "uv.close.status", PhaseStatusPolicy::kDone, false},
        {"timing.uv_loop_close.ms", "uv.loop.close.status", PhaseStatusPolicy::kDone, true},
        {"timing.teardown.ms", "teardown.status", PhaseStatusPolicy::kDone, false},
        {"timing.process_runtime_shutdown.ms", "process_runtime.shutdown.status", PhaseStatusPolicy::kDone, false},
        {"timing.total.ms", nullptr, PhaseStatusPolicy::kPresent, true},
};

constexpr size_t requiredPhaseTimingCount() {
    size_t count = 0;
    for (const PhaseTimingSpec& spec : kPhaseTimingSpecs) {
        if (spec.required) {
            count += 1;
        }
    }
    return count;
}

static_assert(requiredPhaseTimingCount() == 13);

struct PhaseTimingDiagnostics {
    std::string entries;
    std::string statusEntries;
    std::string executionSourceBytes;
    size_t count = 0;
    size_t requiredCount = 0;
    size_t requiredAvailableCount = 0;
};

bool isUnsignedPhaseTimingValue(const std::string& value) {
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

bool phaseStatusMatches(
        const std::vector<std::string>& payload,
        const PhaseTimingSpec& spec,
        std::string& status
) {
    if (spec.statusPolicy == PhaseStatusPolicy::kPresent) {
        status = "observed";
        return true;
    }
    status = payloadLastValue(payload, spec.statusKey);
    switch (spec.statusPolicy) {
        case PhaseStatusPolicy::kDone:
            return status == "done";
        case PhaseStatusPolicy::kDoneOrReused:
            return status == "done" || status == "reused";
        case PhaseStatusPolicy::kScriptTerminal:
            return status == "completed" || status == "process_exit" || status == "failed";
        case PhaseStatusPolicy::kPositiveIntegerMarker:
            return isUnsignedPhaseTimingValue(status) && status != "0";
        case PhaseStatusPolicy::kPresent:
            return true;
    }
    return false;
}

void appendLine(std::string& text, const std::string& key, const std::string& value) {
    if (!text.empty()) {
        text.push_back('\n');
    }
    text += key;
    text.push_back('=');
    text += value;
}

PhaseTimingDiagnostics collectPhaseTimingDiagnostics(const std::vector<std::string>& payload) {
    PhaseTimingDiagnostics diagnostics;
    std::unordered_set<std::string> emittedStatusKeys;
    for (const PhaseTimingSpec& spec : kPhaseTimingSpecs) {
        if (spec.required) {
            diagnostics.requiredCount += 1;
        }
        const std::string value = payloadLastValue(payload, spec.timingKey);
        if (!isUnsignedPhaseTimingValue(value)) {
            continue;
        }
        std::string status;
        if (!phaseStatusMatches(payload, spec, status)) {
            continue;
        }
        appendLine(diagnostics.entries, spec.timingKey, value);
        if (spec.statusKey != nullptr && emittedStatusKeys.insert(spec.statusKey).second) {
            appendLine(diagnostics.statusEntries, spec.statusKey, status);
        }
        diagnostics.count += 1;
        if (spec.required) {
            diagnostics.requiredAvailableCount += 1;
        }
    }
    const std::string executionSourceBytes = payloadLastValue(
            payload,
            "embedded_script.execution_source.bytes"
    );
    if (isUnsignedPhaseTimingValue(executionSourceBytes)) {
        diagnostics.executionSourceBytes = executionSourceBytes;
    }
    return diagnostics;
}

std::string phaseTimingDiagnosticsJsonFields(const std::vector<std::string>& payload) {
    const PhaseTimingDiagnostics diagnostics = collectPhaseTimingDiagnostics(payload);
    const char* status = diagnostics.count == 0
            ? "unavailable"
            : (diagnostics.requiredAvailableCount == diagnostics.requiredCount
                    ? "available"
                    : "partial");
    return std::string("\"phaseTimingSchema\":") + quotedJsonString(kPhaseTimingSchema) + ","
            + "\"phaseTimingStatus\":"
            + quotedJsonString(status) + ","
            + "\"phaseTimingCount\":" + std::to_string(diagnostics.count) + ","
            + "\"phaseTimingRequiredCount\":" + std::to_string(diagnostics.requiredCount) + ","
            + "\"phaseTimingRequiredAvailableCount\":"
            + std::to_string(diagnostics.requiredAvailableCount) + ","
            + "\"phaseTimingPayload\":" + quotedJsonString(diagnostics.entries) + ","
            + "\"phaseTimingStatusPayload\":" + quotedJsonString(diagnostics.statusEntries) + ","
            + "\"phaseTimingSemantics\":" + quotedJsonString(kPhaseTimingSemantics) + ","
            + "\"scriptExecutionSemantics\":" + quotedJsonString(kScriptExecutionSemantics) + ","
            + "\"phaseTimingClock\":" + quotedJsonString(kPhaseTimingClock) + ","
            + "\"executionSourceBytes\":" + quotedJsonString(diagnostics.executionSourceBytes);
}

bool parseStringPairArray(JsonCursor& cursor, std::vector<std::pair<std::string, std::string>>& out) {
    if (!cursor.consume('[')) {
        return false;
    }
    if (cursor.peek(']')) {
        return cursor.consume(']');
    }
    while (true) {
        std::string first;
        std::string second;
        if (!cursor.consume('[') || !cursor.parseString(first) || !cursor.consume(',') || !cursor.parseString(second)) {
            return false;
        }
        while (!cursor.peek(']')) {
            if (!cursor.consume(',') || !cursor.skipValue()) {
                return false;
            }
        }
        if (!cursor.consume(']')) {
            return false;
        }
        out.emplace_back(std::move(first), std::move(second));
        if (cursor.peek(']')) {
            return cursor.consume(']');
        }
        if (!cursor.consume(',')) {
            return false;
        }
    }
}

bool parseStandalonePairArray(
        const char* json,
        std::vector<std::pair<std::string, std::string>>& out,
        std::string& error
) {
    if (json == nullptr || std::strlen(json) == 0) {
        return true;
    }
    JsonCursor cursor(json);
    if (cursor.done()) {
        return true;
    }
    if (!parseStringPairArray(cursor, out) || !cursor.done()) {
        error = cursor.error().empty() ? "invalid JSON pair array" : cursor.error();
        return false;
    }
    return true;
}

bool parseModuleSourcesJson(
        const char* json,
        std::vector<std::pair<std::string, std::string>>& moduleSources,
        std::vector<std::pair<std::string, std::string>>& runtimeModuleSources,
        std::string& sandboxRoot,
        std::string& error
) {
    if (json == nullptr || std::strlen(json) == 0) {
        return true;
    }
    JsonCursor cursor(json);
    if (cursor.done()) {
        return true;
    }
    if (cursor.peek('[')) {
        return parseStandalonePairArray(json, moduleSources, error);
    }
    if (!cursor.consume('{')) {
        error = cursor.error();
        return false;
    }
    if (cursor.peek('}')) {
        return cursor.consume('}');
    }
    while (true) {
        std::string key;
        if (!cursor.parseString(key) || !cursor.consume(':')) {
            error = cursor.error();
            return false;
        }
        if (key == "moduleSources") {
            if (!parseStringPairArray(cursor, moduleSources)) {
                error = cursor.error();
                return false;
            }
        } else if (key == "runtimeModuleSources") {
            if (!parseStringPairArray(cursor, runtimeModuleSources)) {
                error = cursor.error();
                return false;
            }
        } else if (key == "sandboxRoot") {
            if (!cursor.parseString(sandboxRoot)) {
                error = cursor.error();
                return false;
            }
        } else if (!cursor.skipValue()) {
            error = cursor.error();
            return false;
        }
        if (cursor.peek('}')) {
            return cursor.consume('}') && cursor.done();
        }
        if (!cursor.consume(',')) {
            error = cursor.error();
            return false;
        }
    }
}

int parseIntOrDefault(const std::string& value, int fallback) {
    if (value.empty()) {
        return fallback;
    }
    char* end = nullptr;
    const long parsed = std::strtol(value.c_str(), &end, 10);
    if (end == value.c_str()) {
        return fallback;
    }
    return static_cast<int>(parsed);
}

std::string buildStateJson(const AutoJsNodeHandle& handle, const char* stateName) {
    return std::string("{\"state\":\"") + stateName + "\","
            "\"runtimeId\":" + std::to_string(handle.runtimeId) + ","
            "\"executionId\":" + std::to_string(handle.executionId) + ","
            "\"stopRequested\":" + (handle.stopRequested ? "true" : "false") + "}";
}

std::string stateName(uint32_t state) {
    switch (state) {
        case AUTOJS_NODE_RUNTIME_STATE_CREATED:
            return "created";
        case AUTOJS_NODE_RUNTIME_STATE_INITIALIZING:
            return "initializing";
        case AUTOJS_NODE_RUNTIME_STATE_IDLE:
            return "idle";
        case AUTOJS_NODE_RUNTIME_STATE_RUNNING:
            return "running";
        case AUTOJS_NODE_RUNTIME_STATE_DRAINING:
            return "draining";
        case AUTOJS_NODE_RUNTIME_STATE_STOPPING:
            return "stopping";
        case AUTOJS_NODE_RUNTIME_STATE_STOPPED:
            return "stopped";
        case AUTOJS_NODE_RUNTIME_STATE_FAILED:
            return "failed";
        case AUTOJS_NODE_RUNTIME_STATE_DESTROYED:
            return "destroyed";
        default:
            return "unknown";
    }
}

std::string buildFailureResultJson(
        const char* sourceName,
        const std::string& workingDirectory,
        const char* code,
        const std::string& message
) {
    const std::string resolvedSourceName = sourceName == nullptr || std::strlen(sourceName) == 0
            ? "<embedded-user-script.js>"
            : sourceName;
    return std::string("{\"succeeded\":false,"
            "\"exitCode\":1,"
            "\"resultText\":\"\","
            "\"stdout\":\"\","
            "\"stderr\":\"\","
            "\"errorName\":\"AutoJsNodeAdapterError\","
            "\"errorMessage\":") + quotedJsonString(message) + ","
            "\"errorStack\":\"\","
            "\"errorCode\":\"" + code + "\","
            "\"sourceName\":" + quotedJsonString(resolvedSourceName) + ","
            "\"workingDirectory\":" + quotedJsonString(workingDirectory) + "}";
}

void setHandleState(AutoJsNodeHandle* handle, uint32_t state) {
    if (handle == nullptr) {
        return;
    }
    handle->state = state;
    handle->stateJson = buildStateJson(*handle, stateName(state).c_str());
}

int32_t fillInvalidResult(AutoJsNodeExecutionResult* result) {
    if (result != nullptr && result->struct_size >= sizeof(AutoJsNodeExecutionResult)) {
        result->exit_code = 1;
        result->result_code = AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
        result->stdout_text = "";
        result->stderr_text = "";
        result->result_json = "{}";
        result->diagnostics_json = kInvalidArgumentDiagnostics;
    }
    return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
}

int32_t fillExecutionResult(AutoJsNodeHandle* handle, AutoJsNodeExecutionResult* result, int32_t code) {
    if (result != nullptr && result->struct_size >= sizeof(AutoJsNodeExecutionResult)) {
        result->exit_code = parseIntOrDefault(payloadValue(handle->lastPayload, "embedded_script.exit_code"), code == AUTOJS_NODE_RESULT_OK ? 0 : 1);
        result->result_code = code;
        result->stdout_text = handle->stdoutText.c_str();
        result->stderr_text = handle->stderrText.c_str();
        result->result_json = handle->lastResultJson.c_str();
        result->diagnostics_json = handle->lastDiagnosticsJson.c_str();
    }
    return code;
}

bool validHandle(AutoJsNodeHandle* handle) {
    if (handle == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(gHandleMutex);
    return gLiveHandles.find(handle) != gLiveHandles.end() && !handle->destroyed;
}

uint32_t requestFlags(const AutoJsNodeExecutionRequest* request) {
    return request == nullptr ? 0u : request->flags;
}

int32_t executeRequest(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result,
        const char* entryKind
) {
    if (!validHandle(handle) || request == nullptr || request->struct_size < sizeof(AutoJsNodeExecutionRequest)) {
        return fillInvalidResult(result);
    }
    if (request->source.byte_length > 0 && request->source.data == nullptr) {
        return fillInvalidResult(result);
    }
    setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_RUNNING);
    handle->executionId = request->execution_id == 0 ? handle->executionId + 1 : request->execution_id;

    EmbeddedScriptExecutionRequest execution;
    execution.source.assign(
            request->source.data == nullptr ? "" : request->source.data,
            static_cast<size_t>(request->source.byte_length)
    );
    execution.sourceName = request->source_name == nullptr ? "" : request->source_name;
    execution.workingDirectory = request->working_directory != nullptr
            ? request->working_directory
            : handle->workingDirectory;
    std::string sandboxRoot;
    std::string parseError;
    if (!parseModuleSourcesJson(request->module_sources_json, execution.moduleSources, execution.runtimeModuleSources, sandboxRoot, parseError) ||
            !parseStandalonePairArray(request->environment_json, execution.env, parseError)) {
        handle->lastResultJson = buildFailureResultJson(
                request->source_name,
                execution.workingDirectory,
                "ERR_AUTOJS6_NODE_ADAPTER_BAD_JSON",
                parseError
        );
        handle->stdoutText.clear();
        handle->stderrText.clear();
        handle->lastPayload = {
                "embedded_script.status=failed",
                "embedded_script.succeeded=false",
                "embedded_script.exit_code=1",
                "embedded_script.error_code=ERR_AUTOJS6_NODE_ADAPTER_BAD_JSON",
                "embedded_script.result_json=" + handle->lastResultJson,
        };
        handle->lastDiagnosticsJson = std::string("{\"adapter\":\"adapter_v1\","
                "\"entryKind\":") + quotedJsonString(entryKind) + ","
                "\"errorCode\":\"ERR_AUTOJS6_NODE_ADAPTER_BAD_JSON\","
                "\"message\":" + quotedJsonString(parseError) + ","
                + phaseTimingDiagnosticsJsonFields(handle->lastPayload) + "}";
        setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_FAILED);
        return fillExecutionResult(handle, result, AUTOJS_NODE_RESULT_INVALID_ARGUMENT);
    }
    execution.sandboxRoot = sandboxRoot.empty() ? execution.workingDirectory : sandboxRoot;
    const uint32_t flags = requestFlags(request);
    execution.esmEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_ESM) != 0;
    execution.dynamicImportEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_DYNAMIC_IMPORT) != 0;
    execution.rawNodeNetworkModulesEnabled =
            (flags & AUTOJS_NODE_EXECUTION_FLAG_RAW_NODE_NETWORK_MODULES) != 0;
    execution.inspectorEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_INSPECTOR) != 0;
    execution.workerThreadsEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_WORKER_THREADS) != 0;
    execution.childProcessEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_CHILD_PROCESS) != 0;
    execution.javaInteropEnabled = (flags & AUTOJS_NODE_EXECUTION_FLAG_JAVA_INTEROP) != 0;

    try {
        handle->lastPayload = runEmbeddedScriptExecution(execution, "adapter_v1", "execution_path");
    } catch (const std::exception& error) {
        handle->lastResultJson = buildFailureResultJson(
                request->source_name,
                execution.workingDirectory,
                "ERR_AUTOJS6_NODE_ADAPTER_NATIVE_EXCEPTION",
                error.what()
        );
        handle->lastPayload = {
                "embedded_script.status=failed",
                "embedded_script.succeeded=false",
                "embedded_script.exit_code=1",
                "embedded_script.error_code=ERR_AUTOJS6_NODE_ADAPTER_NATIVE_EXCEPTION",
                "embedded_script.result_json=" + handle->lastResultJson,
        };
    } catch (...) {
        handle->lastResultJson = buildFailureResultJson(
                request->source_name,
                execution.workingDirectory,
                "ERR_AUTOJS6_NODE_ADAPTER_NATIVE_EXCEPTION",
                "unknown native exception"
        );
        handle->lastPayload = {
                "embedded_script.status=failed",
                "embedded_script.succeeded=false",
                "embedded_script.exit_code=1",
                "embedded_script.error_code=ERR_AUTOJS6_NODE_ADAPTER_NATIVE_EXCEPTION",
                "embedded_script.result_json=" + handle->lastResultJson,
        };
    }

    const std::string resultJson = payloadValue(handle->lastPayload, "embedded_script.result_json");
    if (!resultJson.empty()) {
        handle->lastResultJson = resultJson;
    } else if (handle->lastResultJson.empty() || handle->lastResultJson == "{}") {
        const std::string detail = payloadValue(handle->lastPayload, "embedded_script.detail");
        handle->lastResultJson = buildFailureResultJson(
                request->source_name,
                execution.workingDirectory,
                "ERR_AUTOJS6_NODE_ADAPTER_RESULT_MISSING",
                detail.empty() ? "embedded script did not return a result envelope" : detail
        );
    }
    handle->stdoutText = payloadValue(handle->lastPayload, "embedded_script.stdout");
    handle->stderrText = payloadValue(handle->lastPayload, "embedded_script.stderr");
    const std::string errorCode = payloadValue(handle->lastPayload, "embedded_script.error_code");
    const std::string status = payloadValue(handle->lastPayload, "embedded_script.status");
    const std::string executionTeardownClean = payloadValue(handle->lastPayload, "execution.teardown_clean");
    handle->lastErrorJson = errorCode.empty()
            ? "{}"
            : std::string("{\"code\":") + quotedJsonString(errorCode) + "}";
    handle->lastDiagnosticsJson = std::string("{\"adapter\":\"adapter_v1\",")
            + "\"entryKind\":" + quotedJsonString(entryKind) + ","
            + "\"runtimeSlot\":\"node24_20\","
            + "\"status\":" + quotedJsonString(status.empty() ? "done" : status) + ","
            + "\"payloadCount\":" + std::to_string(handle->lastPayload.size()) + ","
            + "\"moduleSourceCount\":" + std::to_string(execution.moduleSources.size()) + ","
            + "\"runtimeModuleSourceCount\":" + std::to_string(execution.runtimeModuleSources.size()) + ","
            + "\"envCount\":" + std::to_string(execution.env.size()) + ","
            + "\"executionTeardownClean\":" + quotedJsonString(executionTeardownClean) + ","
            + "\"errorCode\":" + quotedJsonString(errorCode) + ","
            + phaseTimingDiagnosticsJsonFields(handle->lastPayload) + "}";
    const bool succeeded = payloadValue(handle->lastPayload, "embedded_script.succeeded") == "true";
    setHandleState(handle, succeeded ? AUTOJS_NODE_RUNTIME_STATE_IDLE : AUTOJS_NODE_RUNTIME_STATE_FAILED);
    return fillExecutionResult(handle, result, AUTOJS_NODE_RESULT_OK);
}

const char* getNodeVersion() {
    return NODE_VERSION;
}

const char* getRuntimeDescriptor() {
    return kRuntimeDescriptor;
}

int32_t createRuntime(const AutoJsNodeCreateOptions* options, AutoJsNodeHandle** handle) {
    if (handle == nullptr || options == nullptr || options->struct_size < sizeof(AutoJsNodeCreateOptions)) {
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    *handle = nullptr;
    try {
        AutoJsNodeHandle* runtime = new AutoJsNodeHandle();
        runtime->runtimeId = gNextRuntimeId.fetch_add(1);
        runtime->workingDirectory = options->working_directory == nullptr ? "" : options->working_directory;
        runtime->runtimeDescriptor = options->runtime_descriptor_json == nullptr
                ? kRuntimeDescriptor
                : options->runtime_descriptor_json;
        setHandleState(runtime, AUTOJS_NODE_RUNTIME_STATE_IDLE);
        {
            std::lock_guard<std::mutex> lock(gHandleMutex);
            gLiveHandles.insert(runtime);
        }
        *handle = runtime;
        return AUTOJS_NODE_RESULT_OK;
    } catch (...) {
        return AUTOJS_NODE_RESULT_FAILED;
    }
}

int32_t startRuntime(AutoJsNodeHandle* handle) {
    if (!validHandle(handle)) {
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_IDLE);
    return AUTOJS_NODE_RESULT_OK;
}

int32_t executeScript(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result
) {
    return executeRequest(handle, request, result, "script");
}

int32_t executeCommonjs(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result
) {
    return executeRequest(handle, request, result, "commonjs");
}

int32_t executeModule(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result
) {
    return executeRequest(handle, request, result, "module");
}

int32_t pollRuntime(
        AutoJsNodeHandle* handle,
        const AutoJsNodePollOptions*,
        AutoJsNodeExecutionResult* result
) {
    if (!validHandle(handle)) {
        return fillInvalidResult(result);
    }
    return fillExecutionResult(handle, result, AUTOJS_NODE_RESULT_OK);
}

int32_t requestStop(AutoJsNodeHandle* handle, int32_t) {
    if (!validHandle(handle)) {
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    handle->stopRequested = true;
    if (handle->state == AUTOJS_NODE_RUNTIME_STATE_RUNNING) {
        setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_STOPPING);
    }
    return AUTOJS_NODE_RESULT_OK;
}

int32_t forceStop(AutoJsNodeHandle* handle, int32_t reason) {
    const int32_t stopResult = requestStop(handle, reason);
    if (stopResult != AUTOJS_NODE_RESULT_OK) {
        return stopResult;
    }
    setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_STOPPED);
    return AUTOJS_NODE_RESULT_OK;
}

int32_t destroyRuntime(AutoJsNodeHandle* handle) {
    if (handle == nullptr) {
        return AUTOJS_NODE_RESULT_OK;
    }
    {
        std::lock_guard<std::mutex> lock(gHandleMutex);
        const auto iterator = gLiveHandles.find(handle);
        if (iterator == gLiveHandles.end()) {
            return AUTOJS_NODE_RESULT_OK;
        }
        gLiveHandles.erase(iterator);
    }
    handle->destroyed = true;
    setHandleState(handle, AUTOJS_NODE_RUNTIME_STATE_DESTROYED);
    delete handle;
    return AUTOJS_NODE_RESULT_OK;
}

int32_t postBridgeResult(AutoJsNodeHandle* handle, const char*, uint64_t) {
    return validHandle(handle) ? AUTOJS_NODE_RESULT_OK : AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
}

int32_t getState(AutoJsNodeHandle* handle, AutoJsNodeRuntimeState* state) {
    if (state == nullptr || state->struct_size < sizeof(AutoJsNodeRuntimeState)) {
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    if (!validHandle(handle)) {
        state->state_code = AUTOJS_NODE_RUNTIME_STATE_UNKNOWN;
        state->execution_id = 0;
        state->state_json = kInvalidHandleError;
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    state->state_code = handle->state;
    state->execution_id = handle->executionId;
    state->state_json = handle->stateJson.c_str();
    return AUTOJS_NODE_RESULT_OK;
}

int32_t getDiagnostics(AutoJsNodeHandle* handle, AutoJsNodeDiagnostics* diagnostics) {
    if (diagnostics == nullptr || diagnostics->struct_size < sizeof(AutoJsNodeDiagnostics)) {
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    diagnostics->abi_version = AUTOJS_NODE_RUNTIME_API_ABI_VERSION;
    diagnostics->runtime_descriptor_json = kRuntimeDescriptor;
    if (!validHandle(handle)) {
        diagnostics->state_json = kInvalidHandleError;
        diagnostics->resource_json = "{}";
        diagnostics->last_error_json = kInvalidHandleError;
        return AUTOJS_NODE_RESULT_INVALID_ARGUMENT;
    }
    diagnostics->state_json = handle->stateJson.c_str();
    diagnostics->resource_json = handle->resourceJson.c_str();
    diagnostics->last_error_json = handle->lastErrorJson.c_str();
    return AUTOJS_NODE_RESULT_OK;
}

const AutoJsNodeRuntimeApiV1 kRuntimeApi = {
        sizeof(AutoJsNodeRuntimeApiV1),
        AUTOJS_NODE_RUNTIME_API_ABI_VERSION,
        kFeatureBits,
        getNodeVersion,
        getRuntimeDescriptor,
        createRuntime,
        executeScript,
        executeCommonjs,
        executeModule,
        pollRuntime,
        requestStop,
        forceStop,
        getState,
        getDiagnostics,
        destroyRuntime,
        postBridgeResult,
        createRuntime,
        startRuntime,
        executeScript,
        requestStop,
        destroyRuntime,
        postBridgeResult,
        getDiagnostics,
};

}  // namespace

extern "C" const AutoJsNodeRuntimeApiV1* autojs_node_get_runtime_api_v1(void) {
    return &kRuntimeApi;
}

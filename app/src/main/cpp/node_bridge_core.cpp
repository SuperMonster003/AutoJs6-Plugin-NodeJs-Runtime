#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

namespace {

std::mutex& outputStreamSinkMutex() {
    static std::mutex mutex;
    return mutex;
}

std::shared_ptr<JavaOutputSink>& outputStreamSinkSlot() {
    static std::shared_ptr<JavaOutputSink> slot;
    return slot;
}

}  // namespace

std::shared_ptr<JavaOutputSink> currentOutputStreamSink() {
    std::lock_guard<std::mutex> lock(outputStreamSinkMutex());
    return outputStreamSinkSlot();
}

void setCurrentOutputStreamSink(std::shared_ptr<JavaOutputSink> sink) {
    std::lock_guard<std::mutex> lock(outputStreamSinkMutex());
    outputStreamSinkSlot() = std::move(sink);
}

namespace {

using NodeStopFn = int (*)(node::Environment*, uint32_t);
constexpr const char* kNodeStopSymbol = "_ZN4node4StopEPNS_11EnvironmentENS_9StopFlags5FlagsE";

struct ActiveScriptStopState {
    std::mutex mutex;
    std::string scopeTag;  // empty = no execution scope open
    node::Environment* environment = nullptr;
    NodeStopFn stopFn = nullptr;
    bool stopPending = false;
    bool stopDispatched = false;
};

ActiveScriptStopState& activeScriptStopState() {
    static ActiveScriptStopState state;
    return state;
}

bool dispatchNodeStopLocked(ActiveScriptStopState& state) {
    if (state.environment == nullptr || state.stopFn == nullptr) {
        return false;
    }
    const int result = state.stopFn(state.environment, 0 /* StopFlags::kNoFlags */);
    __android_log_print(
            ANDROID_LOG_INFO,
            "AutoJs6NodeBridge",
            "cooperative_stop.dispatched node_stop_result=%d",
            result
    );
    // node::Stop is one-shot per environment; drop the pointer so a second
    // cancel call cannot touch an environment that is now draining/freeing.
    state.environment = nullptr;
    state.stopDispatched = true;
    return true;
}

}  // namespace

void beginActiveScriptStopScope(const char* executionTag) {
    ActiveScriptStopState& state = activeScriptStopState();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.scopeTag = executionTag == nullptr ? "" : executionTag;
    state.environment = nullptr;
    state.stopPending = false;
    state.stopDispatched = false;
}

void endActiveScriptStopScope() {
    ActiveScriptStopState& state = activeScriptStopState();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.scopeTag.clear();
    state.environment = nullptr;
    state.stopPending = false;
}

bool registerActiveScriptEnvironment(void* libnodeHandle, node::Environment* environment) {
    ActiveScriptStopState& state = activeScriptStopState();
    std::lock_guard<std::mutex> lock(state.mutex);
    if (state.scopeTag.empty()) {
        // No admission scope: probe/diagnostic executions never take part in
        // cooperative cancellation.
        return true;
    }
    if (environment != nullptr && libnodeHandle != nullptr && state.stopFn == nullptr) {
        SymbolLookup lookup = lookupSymbol(libnodeHandle, kNodeStopSymbol);
        state.stopFn = lookup.found ? reinterpret_cast<NodeStopFn>(lookup.address) : nullptr;
    }
    state.environment = environment;
    if (state.stopPending) {
        state.stopPending = false;
        if (dispatchNodeStopLocked(state)) {
            return false;
        }
    }
    return true;
}

void clearActiveScriptEnvironment() {
    ActiveScriptStopState& state = activeScriptStopState();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.environment = nullptr;
}

bool requestActiveScriptStop(const char* executionTag) {
    ActiveScriptStopState& state = activeScriptStopState();
    std::lock_guard<std::mutex> lock(state.mutex);
    if (state.scopeTag.empty() || executionTag == nullptr || state.scopeTag != executionTag) {
        // Stale or mistargeted cancel: the tagged execution already finished
        // (or never dispatched natively). Never touch another script's state.
        return false;
    }
    if (state.environment != nullptr) {
        return dispatchNodeStopLocked(state);
    }
    if (state.stopDispatched) {
        return true;
    }
    // The environment is not up yet (script still in pre-dispatch phases):
    // record the request so registration dispatches it immediately.
    state.stopPending = true;
    return true;
}

std::mutex& nodeStartMutex() {
    static std::mutex mutex;
    return mutex;
}


void throwJava(JNIEnv* env, const char* className, const std::string& message) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return;
    }
    env->ThrowNew(clazz, message.c_str());
    env->DeleteLocalRef(clazz);
}

std::string extractBetween(const std::string& value, const char* prefix, const char* suffix) {
    const size_t start = value.find(prefix);
    if (start == std::string::npos) {
        return {};
    }
    const size_t valueStart = start + std::strlen(prefix);
    const size_t end = value.find(suffix, valueStart);
    if (end == std::string::npos) {
        return {};
    }
    return value.substr(valueStart, end - valueStart);
}

bool isAbiMismatch(const std::string& error) {
    return error.find("wrong ELF class") != std::string::npos ||
            error.find("unexpected e_machine") != std::string::npos ||
            error.find("is 32-bit instead of 64-bit") != std::string::npos ||
            error.find("is 64-bit instead of 32-bit") != std::string::npos ||
            error.find("bad ELF magic") != std::string::npos;
}

bool isLibcxxRuntimeSymbol(const std::string& symbol) {
    return symbol.find("__ndk1") != std::string::npos ||
            symbol.find("NSt6__ndk1") != std::string::npos ||
            symbol.find("basic_ostringstream") != std::string::npos ||
            symbol.find("basic_stringstream") != std::string::npos ||
            symbol.find("basic_istringstream") != std::string::npos ||
            symbol.find("__cxa_demangle") != std::string::npos ||
            symbol.find("__cxa_pure_virtual") != std::string::npos ||
            symbol.find("_ZTVNSt6__ndk1") == 0 ||
            symbol.find("_ZTINSt6__ndk1") == 0 ||
            symbol.find("_ZTSNSt6__ndk1") == 0 ||
            symbol.find("_ZTTNSt6__ndk1") == 0;
}

std::string classifyDlopenFailure(const char* libraryName, const std::string& error) {
    const std::string missingLibrary = extractBetween(error, "library \"", "\" not found");
    const std::string missingSymbol = extractBetween(error, "cannot locate symbol \"", "\"");

    if (error.empty()) {
        return "Android linker did not provide a detail message.";
    }
    if (isAbiMismatch(error)) {
        return std::string(libraryName) + " or one of its dependencies does not match the current process ABI.";
    }
    if (missingLibrary == libraryName) {
        return std::string(libraryName) + " is missing from the APK for the current ABI.";
    }
    if (missingLibrary == "libc++_shared.so") {
        return "libc++_shared.so is missing from the APK for the current ABI.";
    }
    if (!missingSymbol.empty() && isLibcxxRuntimeSymbol(missingSymbol)) {
        return "libc++_shared.so does not export C++ runtime symbol " + missingSymbol +
                " required by " + libraryName + ".";
    }
    if (!missingSymbol.empty()) {
        return std::string(libraryName) + " or one of its dependencies is missing symbol " + missingSymbol + ".";
    }
    if (!missingLibrary.empty()) {
        return std::string(libraryName) + " depends on " + missingLibrary +
                ", but " + missingLibrary + " is missing from the APK or system image.";
    }
    return std::string(libraryName) + " failed to load; see the original linker error.";
}

std::string buildDlopenFailureMessage(const char* libraryName, const char* rawError) {
    const std::string error = rawError == nullptr ? "" : rawError;
    std::string message = std::string("Native Node.js backend failed to dlopen ") + libraryName + ".\n";
    message += "Reason: " + classifyDlopenFailure(libraryName, error);
    if (!error.empty()) {
        message += "\nOriginal linker error: " + error;
    }
    return message;
}

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void putPayloadRaw(std::vector<std::string>& payload, const std::string& key, const std::string& value) {
    payload.push_back(key + "=" + value);
}

std::string hexEncodeBytes(const std::string& value) {
    static constexpr char kHexDigits[] = "0123456789abcdef";
    std::string result;
    result.reserve(value.size() * 2);
    for (const unsigned char byte : value) {
        result.push_back(kHexDigits[(byte >> 4) & 0x0f]);
        result.push_back(kHexDigits[byte & 0x0f]);
    }
    return result;
}


std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray values, const char* label) {
    std::vector<std::string> result;
    const std::string fieldName = label == nullptr || std::strlen(label) == 0 ? "array" : label;
    if (values == nullptr) {
        throwJava(env, "java/lang/NullPointerException", fieldName + " == null");
        return result;
    }

    const jsize length = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        if (value == nullptr) {
            throwJava(env, "java/lang/IllegalArgumentException", fieldName + " contains null value");
            return {};
        }
        result.emplace_back(toStdString(env, value));
        env->DeleteLocalRef(value);
        if (env->ExceptionCheck()) {
            return {};
        }
    }
    return result;
}

std::vector<std::string> toStringVectorOrEmpty(JNIEnv* env, jobjectArray values, const char* label) {
    if (values == nullptr) {
        __android_log_print(
                ANDROID_LOG_WARN,
                kLogTag,
                "optional string array %s was null; treating it as empty",
                label == nullptr || std::strlen(label) == 0 ? "array" : label
        );
        return {};
    }
    return toStringVector(env, values, label);
}

NodeStart resolveNodeStart(JNIEnv* env) {
    dlerror();
    void* symbol = dlsym(RTLD_DEFAULT, kNodeStartSymbol);
    if (symbol == nullptr) {
        void* handle = dlopen(kNodeLibraryName, RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr) {
            const char* error = dlerror();
            throwJava(
                    env,
                    "java/lang/UnsatisfiedLinkError",
                    buildDlopenFailureMessage(kNodeLibraryName, error)
            );
            return nullptr;
        }
        dlerror();
        symbol = dlsym(handle, kNodeStartSymbol);
    }

    if (symbol == nullptr) {
        const char* error = dlerror();
        throwJava(
                env,
                "java/lang/UnsatisfiedLinkError",
                std::string("Failed to resolve Node.js entrypoint ") + kNodeStartSymbol +
                        (error == nullptr ? "" : std::string(": ") + error)
        );
        return nullptr;
    }
    return reinterpret_cast<NodeStart>(symbol);
}

SymbolLookup lookupSymbol(void* handle, const char* symbol) {
    dlerror();
    void* result = dlsym(RTLD_DEFAULT, symbol);
    const char* defaultError = dlerror();
    if (result != nullptr) {
        return {true, symbol, {}, result};
    }

    if (handle != nullptr) {
        dlerror();
        result = dlsym(handle, symbol);
        const char* handleError = dlerror();
        if (result != nullptr) {
            return {true, symbol, {}, result};
        }
        if (handleError != nullptr) {
            return {false, symbol, handleError, nullptr};
        }
    }

    return {
            false,
            symbol,
            defaultError == nullptr ? std::string() : std::string(defaultError),
            nullptr,
    };
}

SymbolLookup lookupAnySymbol(void* handle, const SymbolRequirement& requirement) {
    SymbolLookup lastLookup;
    for (const char* symbol : requirement.symbols) {
        if (symbol == nullptr) {
            break;
        }
        SymbolLookup lookup = lookupSymbol(handle, symbol);
        if (lookup.found) {
            return lookup;
        }
        lastLookup = lookup;
    }
    return lastLookup;
}

SymbolLookup lookupAnySymbolLogged(void* handle, const SymbolRequirement& requirement, int requirementIndex) {
    SymbolLookup lastLookup;
    for (const char* symbol : requirement.symbols) {
        if (symbol == nullptr) {
            break;
        }
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "symbol.candidate.start index=%d description=%s candidate=%s",
                requirementIndex,
                requirement.description,
                symbol
        );
        SymbolLookup lookup = lookupSymbol(handle, symbol);
        __android_log_print(
                lookup.found ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                kLogTag,
                "symbol.candidate.done index=%d description=%s candidate=%s found=%s selected=%s%s%s",
                requirementIndex,
                requirement.description,
                symbol,
                lookup.found ? "true" : "false",
                lookup.found ? lookup.symbol.c_str() : "",
                lookup.error.empty() ? "" : " error=",
                lookup.error.empty() ? "" : lookup.error.c_str()
        );
        if (lookup.found) {
            return lookup;
        }
        lastLookup = lookup;
    }
    return lastLookup;
}

bool hasAnySymbol(void* handle, const SymbolRequirement& requirement) {
    return lookupAnySymbol(handle, requirement).found;
}

long long elapsedMs(Clock::time_point start, Clock::time_point end) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
}

std::string joinSymbolCandidates(const SymbolRequirement& requirement) {
    std::string result;
    for (const char* symbol : requirement.symbols) {
        if (symbol == nullptr) {
            break;
        }
        if (!result.empty()) {
            result += "|";
        }
        result += symbol;
    }
    return result;
}

std::string joinStrings(const std::vector<std::string>& values, const char* separator) {
    std::string result;
    for (const std::string& value : values) {
        if (!result.empty()) {
            result += separator;
        }
        result += value;
    }
    return result;
}

jobjectArray toJavaStringArray(JNIEnv* env, const std::vector<std::string>& values) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(values.size()),
            stringClass,
            nullptr
    );
    env->DeleteLocalRef(stringClass);
    if (result == nullptr) {
        return nullptr;
    }
    for (jsize i = 0; i < static_cast<jsize>(values.size()); ++i) {
        jstring value = env->NewStringUTF(values[static_cast<size_t>(i)].c_str());
        if (value == nullptr) {
            return result;
        }
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    return result;
}


void putCommonEmbeddedProbePayload(
        std::vector<std::string>& payload,
        int probeLevel,
        Clock::time_point startedAt
) {
    putPayload(payload, "probe.level", static_cast<long long>(probeLevel));
    putPayload(payload, "current.abi", AUTOJS6_NODE_ANDROID_ABI);
    putPayload(
            payload,
            "lifecycle.build.enabled",
            AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE != 0
    );
    putPayload(
            payload,
            "isolate.build.enabled",
            AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE != 0
    );
    putPayload(
            payload,
            "uvloop.build.enabled",
            AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE != 0
    );
    putPayload(payload, "timing.total.ms", elapsedMs(startedAt));
}

HandleAttemptResult probeLibnodeDlopenAttempt(
        std::vector<std::string>& payload,
        const char* attemptName,
        int flags,
        const char* flagsText
) {
    const auto loadStartedAt = Clock::now();
    const std::string payloadPrefix = std::string("libnode.handle.") + attemptName;

    putPayload(payload, payloadPrefix + ".flags", flagsText);
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "handle.dlopen.%s.start library=%s flags=%s",
            attemptName,
            kNodeLibraryName,
            flagsText
    );
    dlerror();
    void* handle = dlopen(kNodeLibraryName, flags);
    const char* rawHandleError = dlerror();
    const std::string handleError = rawHandleError == nullptr ? std::string() : std::string(rawHandleError);
    const long long loadDurationMs = elapsedMs(loadStartedAt);

    __android_log_print(
            handle != nullptr ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
            kLogTag,
            "handle.dlopen.%s.%s library=%s available=%s elapsed=%lldms%s%s",
            attemptName,
            handle != nullptr ? "done" : "failed",
            kNodeLibraryName,
            handle != nullptr ? "true" : "false",
            loadDurationMs,
            handleError.empty() ? "" : " error=",
            handleError.empty() ? "" : handleError.c_str()
    );
    if (!handleError.empty()) {
        __android_log_print(
                ANDROID_LOG_WARN,
                kLogTag,
                "handle.dlerror attempt=%s error=%s",
                attemptName,
                handleError.c_str()
        );
        putPayload(payload, payloadPrefix + ".error", handleError);
    }
    putPayload(payload, payloadPrefix + ".available", handle != nullptr);
    putPayload(payload, payloadPrefix + ".duration.ms", loadDurationMs);
    return {attemptName, flagsText, handle, handleError, loadDurationMs};
}

HandleAttemptResult unavailableLibnodeDlopenAttempt(
        std::vector<std::string>& payload,
        const char* attemptName,
        const char* flagsText,
        const char* reason
) {
    const std::string payloadPrefix = std::string("libnode.handle.") + attemptName;
    putPayload(payload, payloadPrefix + ".flags", flagsText);
    putPayload(payload, payloadPrefix + ".available", false);
    putPayload(payload, payloadPrefix + ".error", reason);
    putPayload(payload, payloadPrefix + ".duration.ms", static_cast<long long>(0));
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "handle.dlopen.%s.start library=%s flags=%s",
            attemptName,
            kNodeLibraryName,
            flagsText
    );
    __android_log_print(
            ANDROID_LOG_WARN,
            kLogTag,
            "handle.dlopen.%s.failed library=%s available=false elapsed=0ms error=%s",
            attemptName,
            kNodeLibraryName,
            reason
    );
    __android_log_print(ANDROID_LOG_WARN, kLogTag, "handle.dlerror attempt=%s error=%s", attemptName, reason);
    return {attemptName, flagsText, nullptr, reason, 0};
}

void* probeLoadedLibnodeHandle(std::vector<std::string>& payload) {
    struct CachedLibnodeHandle {
        void* handle = nullptr;
        std::string selected;
        std::string flags;
    };
    static std::mutex cacheMutex;
    static CachedLibnodeHandle cache;
    std::lock_guard<std::mutex> cacheLock(cacheMutex);
    const auto probeStartedAt = Clock::now();
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "handle.probe.enter library=%s", kNodeLibraryName);

    if (cache.handle != nullptr) {
        putPayload(payload, "libnode.handle.cached", true);
        putPayload(payload, "libnode.handle.available", true);
        putPayload(payload, "libnode.handle.selected", "cached_" + cache.selected);
        putPayload(payload, "libnode.handle.flags", cache.flags);
        putPayload(payload, "libnode.handle.noload.skipped", "process-cached libnode handle was reused");
        putPayload(payload, "libnode.handle.normal.skipped", "process-cached libnode handle was reused");
        putPayload(payload, "libnode.handle.lazy.skipped", "process-cached libnode handle was reused");
        putPayload(payload, "timing.load.ms", static_cast<long long>(0));
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "handle.probe.exit library=%s available=true selected=cached_%s elapsed=0ms",
                kNodeLibraryName,
                cache.selected.c_str()
        );
        return cache.handle;
    }
    putPayload(payload, "libnode.handle.cached", false);

#ifdef RTLD_NOLOAD
    HandleAttemptResult noload = probeLibnodeDlopenAttempt(
            payload,
            "noload",
            RTLD_NOW | RTLD_NOLOAD,
            "RTLD_NOW|RTLD_NOLOAD"
    );
#else
    HandleAttemptResult noload = unavailableLibnodeDlopenAttempt(
            payload,
            "noload",
            "RTLD_NOLOAD",
            "RTLD_NOLOAD is unavailable at compile time"
    );
#endif

    HandleAttemptResult normal;
    if (noload.handle == nullptr) {
        normal = probeLibnodeDlopenAttempt(
                payload,
                "normal",
                RTLD_NOW,
                "RTLD_NOW"
        );
    } else {
        putPayload(payload, "libnode.handle.normal.skipped", "RTLD_NOLOAD handle was retained for process lifetime");
    }

    HandleAttemptResult lazy;
    bool lazyAttempted = false;
    if (noload.handle == nullptr && normal.handle == nullptr) {
        lazyAttempted = true;
#ifdef RTLD_LAZY
        lazy = probeLibnodeDlopenAttempt(
                payload,
                "lazy",
                RTLD_LAZY,
                "RTLD_LAZY"
        );
#else
        lazy = unavailableLibnodeDlopenAttempt(
                payload,
                "lazy",
                "RTLD_LAZY",
                "RTLD_LAZY is unavailable at compile time"
        );
#endif
    } else {
        putPayload(payload, "libnode.handle.lazy.skipped", "previous handle attempt succeeded");
    }

    HandleAttemptResult* selected = nullptr;
    if (noload.handle != nullptr) {
        selected = &noload;
    } else if (normal.handle != nullptr) {
        selected = &normal;
    } else if (lazyAttempted && lazy.handle != nullptr) {
        selected = &lazy;
    }

    std::vector<std::string> errors;
    if (!noload.error.empty()) {
        errors.emplace_back("noload=" + noload.error);
    }
    if (!normal.error.empty()) {
        errors.emplace_back("normal=" + normal.error);
    }
    if (lazyAttempted && !lazy.error.empty()) {
        errors.emplace_back("lazy=" + lazy.error);
    }

    void* handle = selected == nullptr ? nullptr : selected->handle;
    if (selected != nullptr) {
        cache.handle = selected->handle;
        cache.selected = selected->name;
        cache.flags = selected->flags;
    }
    putPayload(payload, "libnode.handle.available", handle != nullptr);
    putPayload(payload, "libnode.handle.selected", selected == nullptr ? "" : selected->name);
    putPayload(payload, "libnode.handle.flags", selected == nullptr ? "" : selected->flags);
    if (handle == nullptr && !errors.empty()) {
        putPayload(payload, "libnode.handle.error", joinStrings(errors, "; "));
    }
    putPayload(payload, "timing.load.ms", elapsedMs(probeStartedAt));

    __android_log_print(
            handle != nullptr ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
            kLogTag,
            "handle.probe.exit library=%s available=%s selected=%s elapsed=%lldms",
            kNodeLibraryName,
            handle != nullptr ? "true" : "false",
            selected == nullptr ? "" : selected->name.c_str(),
            elapsedMs(probeStartedAt)
    );
    return handle;
}

void appendEmbeddedSymbolProbePayload(std::vector<std::string>& payload, void* handle) {
    long long symbolsDurationMs = 0;
    const auto nodeStartSymbolStartedAt = Clock::now();
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "symbol.start description=node::Start(int,char**)"
    );
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "symbol.candidate.start index=-1 description=node::Start(int,char**) candidate=%s",
            kNodeStartSymbol
    );
    SymbolLookup startLookup = lookupSymbol(handle, kNodeStartSymbol);
    __android_log_print(
            startLookup.found ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
            kLogTag,
            "symbol.candidate.done index=-1 description=node::Start(int,char**) candidate=%s found=%s selected=%s%s%s",
            kNodeStartSymbol,
            startLookup.found ? "true" : "false",
            startLookup.found ? startLookup.symbol.c_str() : "",
            startLookup.error.empty() ? "" : " error=",
            startLookup.error.empty() ? "" : startLookup.error.c_str()
    );
    __android_log_print(
            startLookup.found ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
            kLogTag,
            "symbol.done description=node::Start(int,char**) found=%s selected=%s",
            startLookup.found ? "true" : "false",
            startLookup.found ? startLookup.symbol.c_str() : ""
    );
    symbolsDurationMs += elapsedMs(nodeStartSymbolStartedAt);
    putPayload(payload, "node.start.found", startLookup.found);
    putPayload(payload, "node.start.symbol", startLookup.found ? startLookup.symbol : "");
    if (!startLookup.error.empty()) {
        putPayload(payload, "node.start.error", startLookup.error);
    }

    const auto symbolsStartedAt = Clock::now();
    const int symbolCount = static_cast<int>(kEmbedderSymbolCount);
    putPayload(payload, "symbol.count", static_cast<long long>(symbolCount));
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "symbols.start count=%d", symbolCount);
    for (int i = 0; i < symbolCount; ++i) {
        const SymbolRequirement& requirement = kEmbedderSymbols[i];
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "symbol.start index=%d description=%s",
                i,
                requirement.description
        );
        SymbolLookup lookup = lookupAnySymbolLogged(handle, requirement, i);
        const std::string prefix = "symbol." + std::to_string(i);
        putPayload(payload, prefix + ".description", requirement.description);
        putPayload(payload, prefix + ".found", lookup.found);
        putPayload(payload, prefix + ".resolved", lookup.found ? lookup.symbol : "");
        putPayload(payload, prefix + ".candidates", joinSymbolCandidates(requirement));
        if (!lookup.error.empty()) {
            putPayload(payload, prefix + ".error", lookup.error);
        }
        __android_log_print(
                lookup.found ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                kLogTag,
                "symbol.done index=%d description=%s found=%s selected=%s",
                i,
                requirement.description,
                lookup.found ? "true" : "false",
                lookup.found ? lookup.symbol.c_str() : ""
        );
    }
    symbolsDurationMs += elapsedMs(symbolsStartedAt);
    putPayload(payload, "timing.symbols.ms", symbolsDurationMs);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "symbols.done elapsed=%lldms", symbolsDurationMs);
}

}  // namespace autojs6::node_bridge::internal

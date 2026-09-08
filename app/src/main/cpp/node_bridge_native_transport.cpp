#include "node_bridge_native_transport.h"

#include <jni.h>
#include <algorithm>
#include <atomic>
#include <deque>
#include <mutex>
#include <unordered_map>
#include <unordered_set>

namespace autojs6::node_bridge::internal {
namespace {

class JavaEnvironment {
public:
    explicit JavaEnvironment(JavaVM* vm) : vm_(vm) {
        if (vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            attached_ = vm_->AttachCurrentThread(&env, nullptr) == JNI_OK;
        }
    }
    ~JavaEnvironment() { if (attached_) vm_->DetachCurrentThread(); }
    JNIEnv* env = nullptr;
private:
    JavaVM* vm_;
    bool attached_ = false;
};

struct JavaBridgeSink {
    JavaVM* vm = nullptr;
    jobject object = nullptr;
    jmethodID post = nullptr;
    jmethodID fallback = nullptr;
    size_t maxPending = 32;
    ~JavaBridgeSink() {
        if (object) {
            JavaEnvironment scope(vm);
            if (scope.env) scope.env->DeleteGlobalRef(object);
        }
    }
    void useFileTransport() const {
        JavaEnvironment scope(vm);
        if (!scope.env) return;
        scope.env->CallVoidMethod(object, fallback);
        if (scope.env->ExceptionCheck()) scope.env->ExceptionClear();
    }
};

std::mutex sinkMutex;
std::shared_ptr<JavaBridgeSink> currentSink;
std::mutex channelsMutex;
std::unordered_map<jlong, std::weak_ptr<NativeBridgeChannel>> channels;
std::atomic<jlong> nextChannelId{1};

std::string bytesToString(JNIEnv* env, jbyteArray bytes) {
    if (!bytes) return {};
    std::string value(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()),
                           reinterpret_cast<jbyte*>(value.data()));
    return value;
}

v8::Local<v8::String> literal(v8::Isolate* isolate, const char* text) {
    return v8::String::NewFromUtf8(isolate, text).ToLocalChecked();
}

void throwBridgeError(v8::Isolate* isolate, const char* code, const char* message) {
    auto error = v8::Exception::Error(literal(isolate, message));
    error.As<v8::Object>()->Set(isolate->GetCurrentContext(), literal(isolate, "code"),
                               literal(isolate, code)).FromMaybe(false);
    isolate->ThrowException(error);
}

}

class NativeBridgeChannel {
public:
    struct AsyncHandle {
        uv_async_t handle{};
        std::shared_ptr<NativeBridgeChannel> channel;
    };
    struct Response { std::string id; std::string json; };
    static constexpr size_t maxEventBytes = 4 * 1024 * 1024;
    static constexpr size_t maxEvents = 128;

    jlong id = nextChannelId.fetch_add(1);
    std::shared_ptr<JavaBridgeSink> sink;
    v8::Isolate* isolate = nullptr;
    v8::Global<v8::Context> context;
    v8::Global<v8::Function> listener;
    AsyncHandle* async = nullptr;
    std::mutex mutex;
    bool closing = false;
    // A true value denotes an already queued reply. Duplicate/late replies are ignored.
    std::unordered_map<std::string, bool> pending;
    std::deque<Response> responses;
    std::unordered_set<std::string> subscriptions;
    size_t eventBytes = 0;
    size_t eventCount = 0;
    size_t droppedEvents = 0;

    void enqueueEvent(std::string json) {
        std::lock_guard lock(mutex);
        if (closing) return;
        if (json.size() > maxEventBytes) { ++droppedEvents; return; }
        while (eventCount >= maxEvents || eventBytes + json.size() > maxEventBytes) {
            auto oldest = std::find_if(responses.begin(), responses.end(),
                                       [](const Response& r) { return r.id.empty(); });
            if (oldest == responses.end()) break;
            eventBytes -= oldest->json.size();
            --eventCount;
            ++droppedEvents;
            responses.erase(oldest);
        }
        eventBytes += json.size();
        ++eventCount;
        responses.push_back({{}, std::move(json)});
        uv_async_send(&async->handle);
    }

    static void subscription(const v8::FunctionCallbackInfo<v8::Value>& args) {
        if (args.Length() != 2 || !args[0]->IsString()) return;
        auto* channel = from(args);
        v8::String::Utf8Value id(args.GetIsolate(), args[0]);
        if (!*id) return;
        const std::string key(*id, id.length());
        {
            std::lock_guard lock(channel->mutex);
            if (args[1]->IsTrue()) channel->subscriptions.insert(key);
            else channel->subscriptions.erase(key);
        }
        channel->updateLoopReference();
    }

    void enqueue(std::string requestId, std::string response) {
        std::lock_guard lock(mutex);
        auto found = pending.find(requestId);
        if (closing || found == pending.end() || found->second) return;
        found->second = true;
        responses.push_back({std::move(requestId), std::move(response)});
        // The mutex also excludes uv_close; no Binder thread touches V8 or uv_ref.
        uv_async_send(&async->handle);
    }

    void updateLoopReference() {
        std::lock_guard lock(mutex);
        auto* handle = reinterpret_cast<uv_handle_t*>(&async->handle);
        if (pending.empty() && subscriptions.empty()) uv_unref(handle);
        else uv_ref(handle);
    }

    void cancel(const std::string& requestId) {
        {
            std::lock_guard lock(mutex);
            pending.erase(requestId);
            std::erase_if(responses, [&](const Response& r) { return r.id == requestId; });
        }
        updateLoopReference();
    }

    static NativeBridgeChannel* from(const v8::FunctionCallbackInfo<v8::Value>& args) {
        return static_cast<NativeBridgeChannel*>(args.Data().As<v8::External>()->Value());
    }

    static void listen(const v8::FunctionCallbackInfo<v8::Value>& args) {
        if (args.Length() == 1 && args[0]->IsFunction()) {
            from(args)->listener.Reset(args.GetIsolate(), args[0].As<v8::Function>());
        }
    }

    static void cancelRequest(const v8::FunctionCallbackInfo<v8::Value>& args) {
        if (args.Length() != 1 || !args[0]->IsString()) return;
        v8::String::Utf8Value id(args.GetIsolate(), args[0]);
        if (*id) from(args)->cancel(std::string(*id, id.length()));
    }

    static void fallback(const v8::FunctionCallbackInfo<v8::Value>& args) {
        from(args)->sink->useFileTransport();
    }

    static void post(const v8::FunctionCallbackInfo<v8::Value>& args) {
        auto* channel = from(args);
        auto* isolate = args.GetIsolate();
        args.GetReturnValue().Set(false);
        if (args.Length() != 1 || !args[0]->IsString() || channel->listener.IsEmpty()) return;
        auto context = isolate->GetCurrentContext();
        v8::Local<v8::Value> request;
        v8::Local<v8::Value> idValue;
        if (!v8::JSON::Parse(context, args[0].As<v8::String>()).ToLocal(&request) ||
            !request->IsObject() ||
            !request.As<v8::Object>()->Get(context, literal(isolate, "id")).ToLocal(&idValue) ||
            !idValue->IsString()) return;
        v8::String::Utf8Value idBytes(isolate, idValue);
        v8::String::Utf8Value requestBytes(isolate, args[0]);
        if (!*idBytes || !*requestBytes) return;
        const std::string requestId(*idBytes, idBytes.length());
        {
            std::lock_guard lock(channel->mutex);
            if (channel->closing) return;
            if (channel->pending.size() >= channel->sink->maxPending ||
                !channel->pending.emplace(requestId, false).second) {
                throwBridgeError(isolate, "ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT",
                                 "AutoJs6 native bridge pending call limit reached.");
                return;
            }
        }
        channel->updateLoopReference();
        JavaEnvironment scope(channel->sink->vm);
        if (!scope.env) {
            channel->cancel(requestId);
            return;
        }
        JNIEnv* env = scope.env;
        jbyteArray bytes = env->NewByteArray(requestBytes.length());
        if (bytes) env->SetByteArrayRegion(bytes, 0, requestBytes.length(),
                                         reinterpret_cast<const jbyte*>(*requestBytes));
        bool accepted = false;
        if (!env->ExceptionCheck() && bytes) {
            accepted = env->CallBooleanMethod(channel->sink->object, channel->sink->post,
                                               channel->id, bytes) == JNI_TRUE;
        }
        if (bytes) env->DeleteLocalRef(bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            channel->cancel(requestId);
            // Dispatch may already have had a side effect: throw, never replay via a file.
            throwBridgeError(isolate, "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED",
                             "AutoJs6 JNI bridge dispatch failed.");
            return;
        }
        if (!accepted) channel->cancel(requestId);
        args.GetReturnValue().Set(accepted);
    }

    static void deliver(uv_async_t* handle) {
        auto channel = static_cast<AsyncHandle*>(handle->data)->channel;
        std::deque<Response> ready;
        {
            std::lock_guard lock(channel->mutex);
            if (channel->closing) return;
            ready.swap(channel->responses);
            channel->eventBytes = 0;
            channel->eventCount = 0;
        }
        v8::HandleScope handles(channel->isolate);
        auto context = channel->context.Get(channel->isolate);
        v8::Context::Scope entered(context);
        // uv_async_send coalesces notifications. Drain the whole queue, not one reply.
        for (const auto& response : ready) {
            {
                std::lock_guard lock(channel->mutex);
                if (!response.id.empty() && !channel->pending.erase(response.id)) continue;
            }
            v8::Local<v8::String> json;
            if (!channel->listener.IsEmpty() &&
                v8::String::NewFromUtf8(channel->isolate, response.json.data(),
                                       v8::NewStringType::kNormal,
                                       static_cast<int>(response.json.size())).ToLocal(&json)) {
                v8::Local<v8::Value> argv[] = {json};
                // MakeCallback drains nextTick/microtasks after resolving the Promise.
                node::MakeCallback(channel->isolate, context->Global(),
                                   channel->listener.Get(channel->isolate), 1, argv,
                                   node::async_context{0, 0});
            }
        }
        channel->updateLoopReference();
    }
};

std::shared_ptr<NativeBridgeChannel> createNativeBridgeChannel(
        v8::Isolate* isolate, v8::Local<v8::Context> context, uv_loop_t* loop) {
    std::shared_ptr<JavaBridgeSink> sink;
    {
        std::lock_guard lock(sinkMutex);
        sink = currentSink;
    }
    if (!sink) return {};
    auto channel = std::make_shared<NativeBridgeChannel>();
    channel->sink = sink;
    channel->isolate = isolate;
    auto* async = new NativeBridgeChannel::AsyncHandle{};
    if (uv_async_init(loop, &async->handle, NativeBridgeChannel::deliver) != 0) {
        delete async;
        sink->useFileTransport();
        return {};
    }
    async->channel = channel;
    async->handle.data = async;
    channel->async = async;
    channel->context.Reset(isolate, context);
    uv_unref(reinterpret_cast<uv_handle_t*>(&async->handle));
    auto data = v8::External::New(isolate, channel.get());
    const std::pair<const char*, v8::FunctionCallback> bindings[] = {
            {"__autojs6_bridge_native_post", NativeBridgeChannel::post},
            {"__autojs6_bridge_native_listen", NativeBridgeChannel::listen},
            {"__autojs6_bridge_native_cancel", NativeBridgeChannel::cancelRequest},
            {"__autojs6_bridge_native_file_fallback", NativeBridgeChannel::fallback},
            {"__autojs6_bridge_native_subscription", NativeBridgeChannel::subscription}
    };
    for (const auto& [name, callback] : bindings) {
        v8::Local<v8::Function> function;
        if (!v8::Function::New(context, callback, data).ToLocal(&function) ||
            !context->Global()->Set(context, literal(isolate, name), function).FromMaybe(false)) {
            closeNativeBridgeChannel(channel);
            sink->useFileTransport();
            return {};
        }
    }
    {
        std::lock_guard lock(channelsMutex);
        channels.emplace(channel->id, channel);
    }
    return channel;
}

size_t nativeBridgeDroppedEvents(const std::shared_ptr<NativeBridgeChannel>& channel) {
    if (!channel) return 0;
    std::lock_guard lock(channel->mutex);
    return channel->droppedEvents;
}

void closeNativeBridgeChannel(const std::shared_ptr<NativeBridgeChannel>& channel) {
    if (!channel) return;
    {
        std::lock_guard lock(channelsMutex);
        channels.erase(channel->id);
    }
    {
        std::lock_guard lock(channel->mutex);
        channel->closing = true;
        channel->responses.clear();
        channel->pending.clear();
        channel->subscriptions.clear();
        uv_close(reinterpret_cast<uv_handle_t*>(&channel->async->handle), [](uv_handle_t* handle) {
            delete static_cast<NativeBridgeChannel::AsyncHandle*>(handle->data);
        });
    }
    // Reset V8 handles before FreeEnvironment/isolate disposal; the uv_close callback
    // keeps the native allocation alive until the lifecycle drains the loop.
    channel->listener.Reset();
    channel->context.Reset();
}

}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeSetBridgeSink(
        JNIEnv* env, jobject, jobject sink, jint maxPending) {
    using namespace autojs6::node_bridge::internal;
    std::shared_ptr<JavaBridgeSink> next;
    if (sink) {
        next = std::make_shared<JavaBridgeSink>();
        env->GetJavaVM(&next->vm);
        jclass type = env->GetObjectClass(sink);
        next->post = env->GetMethodID(type, "post", "(J[B)Z");
        next->fallback = env->GetMethodID(type, "useFileTransport", "()V");
        env->DeleteLocalRef(type);
        if (env->ExceptionCheck()) return;
        next->object = env->NewGlobalRef(sink);
        if (!next->object) return;
        next->maxPending = std::clamp(maxPending, 1, 128);
    }
    std::lock_guard lock(sinkMutex);
    currentSink = std::move(next);
}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeReceiveBridgeResponse(
        JNIEnv* env, jobject, jlong channelId, jbyteArray requestId, jbyteArray response) {
    using namespace autojs6::node_bridge::internal;
    std::shared_ptr<NativeBridgeChannel> channel;
    {
        std::lock_guard lock(channelsMutex);
        auto found = channels.find(channelId);
        if (found != channels.end()) channel = found->second.lock();
    }
    if (channel) channel->enqueue(bytesToString(env, requestId), bytesToString(env, response));
}

extern "C" JNIEXPORT void JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeReceiveBridgeEvent(
        JNIEnv* env, jobject, jlong channelId, jbyteArray event) {
    using namespace autojs6::node_bridge::internal;
    std::shared_ptr<NativeBridgeChannel> channel;
    {
        std::lock_guard lock(channelsMutex);
        auto found = channels.find(channelId);
        if (found != channels.end()) channel = found->second.lock();
    }
    if (channel) channel->enqueueEvent(bytesToString(env, event));
}

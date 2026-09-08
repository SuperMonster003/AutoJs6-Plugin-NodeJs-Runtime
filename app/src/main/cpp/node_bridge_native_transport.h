#pragma once

#include <memory>
#include <node.h>
#include <uv.h>

namespace autojs6::node_bridge::internal {

class NativeBridgeChannel;

// Both lifecycle calls run on the isolate's thread, while its context is entered.
std::shared_ptr<NativeBridgeChannel> createNativeBridgeChannel(
        v8::Isolate* isolate, v8::Local<v8::Context> context, uv_loop_t* loop);
void closeNativeBridgeChannel(const std::shared_ptr<NativeBridgeChannel>& channel);

}

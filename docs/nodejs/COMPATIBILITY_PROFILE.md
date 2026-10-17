# AutoJs6 Node.js Compatibility Profile

Last reviewed: 2026-10-11. Runtime plugin v1.6.0 (build 240), SDK 1.7.0,
capability catalog 1.6.0 and runtime-kit 1.6.0 are published. This checkout develops
1.7.0; the packaged-client changes below are not yet published.

## Runtime and routing

The plugin supplies Node.js 24.21.0, V8 and libuv for arm64-v8a, armeabi-v7a and
x86_64, plus a universal APK. Full ICU locale data is included. AutoJs6 contains
no embedded Node runtime. Node-marked scripts use this plugin; TypeScript first
uses the host Compiler, including provider-v3 on-demand compilation. Raw
TypeScript dispatch is rejected. The 1.7.0 development version supports packaged
applications through the external runtime and a matching host/Builder Runtime Kit.

## Implemented behavior

| Area | Current behavior |
|---|---|
| Builtins | Node's builtin inventory and native module identities; console output remains captured |
| File access and SQLite | Native Node operations, Android UID/SELinux/storage permissions |
| CommonJS / ESM | Native loaders and module.registerHooks for runtime modules, supplied sources and request switches |
| Workers | Native threading, nesting, MessagePort and parent-routed host calls; no plugin count/startup cap |
| Native addons | Android ELF .node loading through Node; process ABI, dependencies and page alignment must match |
| Inspector | Explicit activation in Debug/Release, localhost and ADB forwarding, no blocking waits |
| npm | Bundled CLI, registry and lifecycle behavior follow npm/.npmrc; output streams and npm errors are preserved |
| Process pool | 1-8 configurable slots, default 2; default unlimited FIFO, cancellation and explicit timeout |
| Compile cache | Native portable workspace/Worker cache, enabled by default, Node-managed invalidation |
| Host APIs | Available only with the required provider, declarations and Android authorization |
| Pinyin/Pinyin4j | Awaited host-provider conversions; no small local dictionary fallback |
| Console controls | Awaited per-execution Android window controls; regular log output remains synchronous |
| Coordinates | screen_metrics exposes awaited cX/cY/cXy/cYx and once-per-execution scale bases using the current display |
| Packaged apps (1.7.0) | Independent signer, normal-permission runtime/wake entry, shared pool and install guidance |

An Android build may reject an upstream feature itself. For example, this Node
library can report ERR_TRACE_EVENTS_UNAVAILABLE; a plugin support label does not
invent an unavailable platform implementation. Request-level switches for raw
networking, Workers, child processes and Inspector remain explicit configuration.

## Workspaces and packages

The host transfers private projects as archives or source-provider records.
Relative archive paths, file identities and output commit receipts are validated
as transport data. These checks do not restrict ordinary fs access. A host path
is not automatically accessible under the plugin UID. Native module lookup and
package scopes use the execution workspace; runtime modules remain available via
autojs6: names, with documented npm package shadowing for package-compatible aliases.

The npm CLI is prepared lazily from the bundled archive. Android-compatible
prebuilt addons can load; a desktop binary for the same CPU is insufficient.
Bundled node-gyp scripts do not supply an Android NDK or Python toolchain.
The output ZIP protocol does not copy symlinks, so use bin-links=false when npm
bin links need host writeback. Android can refuse app-data executable shims.
Object-based package_manager requests keep the host local-store contract.

## Lifecycle and diagnostics

Each execution has a fresh isolate in a reusable slot process. Host startup
prewarming overlaps engine/TypeScript/workspace preparation and targets the
first slot. Runtime configuration changes preserve accepted work. Android lmkd
controls memory pressure; unexpected deaths report slotExit and available Node
fatal-error reports. No automatic script replay occurs.

Startup diagnostics distinguish process-cold startup, resident fresh isolates,
host preparation and first stdout. The measured arm64 cold target is not yet
consistently below 400 ms; source/measurement details are in [Roadmap](../../Roadmap.md).
A compiler-output cache hit is distinct from recompiling a changed TypeScript
project. NODE_DISABLE_COMPILE_CACHE=1 disables Node's separate JS module cache.

## Declarations and evidence

Current API details are in [HOST-API](../HOST-API.md) and the live
[capability catalog](../../app/src/main/assets/nodejs/node-capability-catalog.json).
Profile names and legacy type matrices are metadata, not permission grants.
Historical notes and declaration-only version matrices are under history.
Examples use stable or host_provider_required to distinguish execution dependencies.
A callable facade does not establish Android device behavior: capture, recording,
OCR and accessibility still require their documented provider and device conditions.

The runtime handshake remains v2 with additive v3 operations. The prepared
published nodejs-api 1.7.0 artifact includes source-provider v3 while
preserving existing AIDL transaction IDs; it does not replace the immutable API 1.6.0 archive.

The local nodejs-api 1.8.0 snapshot adds packaged runtime/wake actions and their
normal permission. Existing signature-protected host entry points remain compatible.

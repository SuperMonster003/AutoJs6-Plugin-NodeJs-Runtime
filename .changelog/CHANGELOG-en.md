******

### Release History

******

# v1.3.0

###### Unreleased

* `Feature` Node.js bridge subscriptions push sensor, WebSocket, UI, overlay and input events through existing callbacks, with on/once/off listeners, bounded queues and compatible drainEvents
* `Feature` Optional idleExitMs releases an idle Node.js runtime process and reconnects for the next script, with idleForMs diagnostics and resident behavior preserved by default
* `Feature` Native global fetch, Request, Response, Headers, FormData and WebSocket use Node web APIs by default; explicit autojs6:fetch and autojs6:websocket modules retain the host network stack
* `Feature` Added native node:sqlite with filesystem checks, CRUD, transactions and backups; native node:test reporters and the runnable test example are available, with zod, cheerio, date-fns, mqtt and ws added to the offline npm corpus
* `Feature` Native stdin and readline accept console input, with execution-scoped JSON messages through autojs6:host and a backward-compatible v3 postMessage transaction
* `Feature` Node.js screen capture sessions use Android consent and the existing foreground service, with image handles, PNG/JPEG/WebP saving and cleanup when stopped or the script exits
* `Feature` Node.js image handles support clipping, resizing, grayscale, thresholding, template matching and color searches through the host image backend, with independent output handles and execution cleanup
* `Feature` Node.js image.toBytes transfers PNG or RGBA pixels through file descriptors into native Buffers, supports JNI and file bridge modes, and closes attachments after use, timeout or execution exit
* `Feature` Node device APIs expose live build, screen, battery and memory information, brightness control and timed screen wake locks; media supports setting stream volume, with Android permissions enforced
* `Feature` autojs6:events observes Android notifications, external Toast messages, accessibility keys and screen or battery broadcasts through push callbacks, with explicit permissions and automatic cleanup; native Node events remains unchanged
* `Feature` Node app supports Android service starts, broadcasts and installed package queries; dialogs supports text input, choices and progress windows with script cleanup, and keys provides accessibility system actions
* `Feature` Node recorder supports AAC recording with microphone permission, foreground disclosure, duration limits and script cleanup; ui.overlay supports declarative property updates, dragging and pushed events
* `Fix` Fixed growing live-bridge request and response history in resident scripts by retiring completed requests and bounding diagnostics to the latest 32 responses with a size limit
* `Fix` Fixed prematurely successful results losing native async errors and late exit codes; completion now follows the final Node event-loop exit
* `Improvement` Reduced live bridge latency with a default JNI/Binder transport and Node event-loop responses, preserving selectable file fallback and pending-call limits
* `Improvement` Restored native Node.js builtin exports for streams, crypto, timers, utilities, node:test and related modules while preserving filesystem boundaries and host directory policy
* `Improvement` Native workers now default to CPU parallelism (up to eight), inherit execution network/filesystem switches, accept request resource caps and have no default pool task deadline; CPU and WASM examples run real workers
* `Improvement` Kept raw WASI disabled to preserve filesystem boundaries and removed the two disabled WASI examples; ordinary WebAssembly and WASM workers remain available
* `Improvement` Screen OCR examples request Android capture consent and recognize real image handles; unavailable OCR or barcode plugins return readable unavailable errors, while recognition failures remain errors

# v1.2.0

###### 2026/08/29

* `Feature` Added `capabilities()` to the `mediainfo` facade and explicit plugin snapshot v1/v2 selection to its callable entry and `read()`; omitting schema continues to return the existing host-owned Node v1 snapshot
* `Feature` Added module-source provider v3 for on-demand compilation of runtime-created `.ts/.mts/.cts` through byte- and SHA-256-bound PFDs, with an independent 30 s compilation budget, stable path/symlink/ambiguity rejection, and host TypeScript diagnostics plus Source Map stack mapping
* `Feature` Enabled desktop-like filesystem access within Android app permissions while keeping `/proc`, `/sys`, and `/dev` denied
* `Feature` Added `accessibility.swipe` and `accessibility.gesture` behind the dedicated `accessibility.gesture` capability
* `Feature` Added script timeouts covering queueing and execution, returning `ERR_AUTOJS6_SCRIPT_TIMEOUT`; scripts without a timeout can still run indefinitely
* `Feature` Enabled `dgram` (UDP) and `http2` by default, with an explicit disabled error for `trace_events`
* `Feature` Added explicitly enabled local `inspector` debugging in Debug builds, listening only on localhost and connecting through `adb forward`
* `Feature` Added a host-permission-protected activation entry without a UI, completed plugin-center descriptions, and disabled application data backup
* `Fix` Made raw TypeScript fail closed unless the host supplies compiler output, mapped snapshot dynamic imports, and normalized generated/imported stack frames
* `Fix` Replaced the snapshot-based partial ESM adapter with the V8 native linker, fixing mutable exports that failed to update through cyclic re-exports
* `Fix` Fixed ESM imports of AutoJs6 compatibility facades and missing TypeScript suffix probes while preserving installed npm package precedence
* `Improvement` Removed the legacy regex-based TypeScript erasure fallback and its request switch; raw `.ts/.mts/.cts` now always require host compiler output
* `Improvement` Aligned the v2 host/plugin contract, capability manifests, and plugin-only runtime responsibility boundary
* `Improvement` Centralized Node.js examples, TypeScript declarations, the project wizard, runtime defaults, and host-alignment checks in the plugin repository, removing host-side Gradle switches and duplicate development assets
* `Improvement` Expanded verified npm coverage to 15 packages, adding axios, express, and the ESM-only packages nanoid, p-limit, and yocto-queue
* `Improvement` Made the `executionMode` request field authoritative for lifecycle mode and deprecated the no-op `runtimeAdapter` field
* `Improvement` Removed ten historical examples that only printed fixed status values and marked screen capture, image analysis, and recording declarations as lacking host providers

# v1.1.0

###### 2026/08/18

* `Feature` Added live stdout/stderr streaming and cooperative cancellation through `node::Stop`
* `Feature` Replaced BUSY rejection with a bounded serial queue of three waiters and added resident long-running script lifecycle support
* `Feature` Enabled raw Node network builtins, `worker_threads`, and `child_process` by default, and verified ten popular pure-JavaScript npm packages
* `Improvement` Added direct-run workspaces and tolerant v1..v2 module-source-provider negotiation with concise error codes and JavaScript stacks

# v1.0.0

###### 2026/07/18

* `Feature` Added the Node.js runtime plugin service with plugin ID `nodejs`, engine `nodejs`, and runtime slot `node24_5`
* `Feature` Provided the Node.js 24.5.0 native runtime through `libnode.so` and `libautojs6-node.so`
* `Feature` Ran the Node.js runtime in an independent persistent process that reuses process-global Node/V8 state while creating a fresh isolate and Environment for every execution
* `Feature` Added plugin info discovery through `org.autojs.plugin.INFO` and runtime invocation through `org.autojs.plugin.nodejs.RUNTIME`
* `Feature` Supported CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, stdout/stderr result payloads, and runtime prewarm
* `Feature` Supported request-scoped workspace archive transport v2 with explicit input mapping, execution in a plugin-private workspace, output writeback, and deletion tombstone manifests without scanning the host sandbox
* `Feature` Enforced single-active, zero-queue admission with `ERR_AUTOJS6_NODE_PLUGIN_BUSY` backpressure and process-restart cancellation
* `Feature` Added host capability broker and live bridge support with runtime modules such as `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, and `autojs6:bridge-permissions`
* `Feature` Added ABI split APK builds for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and a `universal` APK
* `Feature` Packaged `libc++_shared.so` with the Node.js runtime libraries in split and universal APK outputs
* `Feature` Added `sample/nodejs` projects, a Node resolver diagnostic tool, and runtime build plan verification tools
* `Feature` Added localized plugin metadata, usage instructions, README, and CHANGELOG resources for Spanish, French, Russian, Arabic, Japanese, Korean, English, Simplified Chinese, Hong Kong Traditional Chinese, and Taiwan Traditional Chinese
* `Fix` Process-restart cancellation could commit a partial workspace snapshot while the runtime process was being retired
* `Fix` Workspace archive file descriptors could leak when request-contract validation or workspace materialization failed because ownership was not finalized on every exit path
* `Improvement` Exposed R5 contract metadata and diagnostics for ABI/capabilities, persistent runtime state, admission, cancellation, and dedicated-process attribution
* `Improvement` Added monotonic per-phase diagnostics for execution-source construction, bootstrap, script execution, result creation/retrieval, and per-execution cleanup, with skipped and not-applicable states distinguished

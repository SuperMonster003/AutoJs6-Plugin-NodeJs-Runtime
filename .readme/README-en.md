<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Node.js 24.5.0 native runtime plugin for AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### Introduction

******

The AutoJs6 Node.js Runtime Plugin provides an embedded Node.js 24.5.0 native runtime for AutoJs6 scripts and plugin runtime tasks.

******

### Features

******

- Provides the `nodejs` plugin service with plugin ID `nodejs` and engine `nodejs`.
- Exposes synchronous script execution and runtime prewarm to the host through `org.autojs.plugin.nodejs.RUNTIME`.
- Added script timeouts covering queueing and execution, returning `ERR_AUTOJS6_SCRIPT_TIMEOUT`; scripts without a timeout can still run indefinitely
- Enabled `dgram` (UDP) and `http2` by default, with an explicit disabled error for `trace_events`
- Added explicitly enabled local `inspector` debugging in Debug builds, listening only on localhost and connecting through `adb forward`
- Supports CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, and stdout/stderr result payloads.
- Uses the V8 native linker for ESM entries and dynamic `import()`, preserving cyclic dependencies, mutable exports, and re-export live bindings; CommonJS `require(esm)` retains its synchronous interop boundary.
- Provides desktop-like filesystem access bounded by the plugin app's Android permissions; `/proc`, `/sys`, and `/dev` are always denied by the runtime.
- Executes host-supplied TypeScript compiler output and can request provider-v3 compilation for runtime-created project `.ts`/`.mts`/`.cts`; direct raw dispatch always fails closed because no runtime erasure fallback or compatibility switch remains.
- Provides host capability broker and live bridge support with runtime modules such as `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, and `autojs6:bridge-permissions`.
- Includes `sample/nodejs` projects and the `docs/HOST-API.md` host API capability inventory.
- Plugin metadata, usage instructions, README, and CHANGELOG are localized for Spanish, French, Russian, Arabic, Japanese, Korean, English, Simplified Chinese, Hong Kong Traditional Chinese, and Taiwan Traditional Chinese.

******

### Usage

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

Install and enable the plugin in the AutoJs6 plugin center, then start Node.js scripts with the `"nodejs";` directive. More examples are available in `sample/nodejs`.

******

### Quick Start

******

- **Install** — Download the APK for your ABI from [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) (pick `universal` when unsure) and install it, or build locally with `.\gradlew.bat :app:assembleDebug` and install from `app/build/outputs/apk/debug/`. Then enable this plugin in the AutoJs6 plugin center. On Android 11+, grant this plugin All files access in system settings when scripts need shared storage; `EACCES` is expected without that grant.
- **Run** — Create a script in the AutoJs6 editor whose first line is `"nodejs";` and write the rest like desktop Node.js (CommonJS/ESM, pure-JS npm packages, and network builtins are supported). Hit run; output streams live and the script can be stopped at any time. Raw `.ts`/`.mts`/`.cts` currently must be compiled to JavaScript by the host first; the plugin does not bundle `tsc`.
- **When something fails** — Script failures print the JS stack plus a one-line error code (such as `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) to the console; for more detail inspect the plugin process log with `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`. Host API availability is documented in `docs/HOST-API.md`.

******

### Runtime Profile

******

- Runtime slot: `node24_5`.
- Plugin ID: `nodejs`, engine: `nodejs`.
- Runtime service action: `org.autojs.plugin.nodejs.RUNTIME`.
- Native runtime libraries: `libnode.so` and `libautojs6-node.so`.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `universal`.
- Filesystem: device paths allowed by Android permissions are reachable; `/proc`, `/sys`, and `/dev` are hard boundaries.
- TypeScript: accepts host output and can request provider-v3 compilation for runtime-created project files; direct raw TypeScript returns `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, with native live bindings and cyclic dependencies.
- Capabilities: sync script execution, bundle transport, native embedded runtime, host capability broker, host capability live bridge.

******

### Release History

******

# v1.3.0

###### Unreleased

* `Feature` Node.js bridge subscriptions push sensor, WebSocket, UI, overlay and input events through existing callbacks, with on/once/off listeners, bounded queues and compatible drainEvents
* `Feature` Optional idleExitMs releases an idle Node.js runtime process and reconnects for the next script, with idleForMs diagnostics and resident behavior preserved by default
* `Feature` Native global fetch, Request, Response, Headers, FormData and WebSocket use Node web APIs by default; explicit autojs6:fetch and autojs6:websocket modules retain the host network stack
* `Fix` Fixed growing live-bridge request and response history in resident scripts by retiring completed requests and bounding diagnostics to the latest 32 responses with a size limit
* `Fix` Fixed prematurely successful results losing native async errors and late exit codes; completion now follows the final Node event-loop exit
* `Improvement` Reduced live bridge latency with a default JNI/Binder transport and Node event-loop responses, preserving selectable file fallback and pending-call limits
* `Improvement` Restored native Node.js builtin exports for streams, crypto, timers, utilities, node:test and related modules while preserving filesystem boundaries and host directory policy
* `Improvement` Native workers now default to CPU parallelism (up to eight), inherit execution network/filesystem switches, accept request resource caps and have no default pool task deadline; CPU and WASM examples run real workers

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

##### For more release history

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-en.md)

******

### Build

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

Build parameters come from `version.properties`, the current minimum SDK is 24 and target SDK is 36.

******

### Resource Layout

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` contains localized plugin descriptions; `plugin_instruction.md` contains usage instructions displayed by the host. README and CHANGELOG files are generated from JSON sources by `.python/generate_markdown.py`.

******

### Links

******

- AutoJs6 documentation: https://docs.autojs6.com
- Node.js official project: https://github.com/nodejs/node
- Node.js runtime build plan: tools/nodejs/runtime-build/README.md

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
- Supports CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, and stdout/stderr result payloads.
- Uses the V8 native linker for ESM entries and dynamic `import()`, preserving cyclic dependencies, mutable exports, and re-export live bindings; CommonJS `require(esm)` retains its synchronous interop boundary.
- Provides desktop-like filesystem access bounded by the plugin app's Android permissions; `/proc`, `/sys`, and `/dev` are always denied by the runtime.
- Executes host-supplied TypeScript compiler output; raw `.ts`/`.mts`/`.cts` fails closed by default and legacy erasure is an explicit migration-only opt-in.
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
- TypeScript: compiler output only by default; raw TypeScript returns `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, with native live bindings and cyclic dependencies.
- Capabilities: sync script execution, bundle transport, native embedded runtime, host capability broker, host capability live bridge.

******

### Release History

******

# v1.2.0

###### 2026/08/26

* `Feature` Enabled desktop-like filesystem access within Android app permissions while keeping `/proc`, `/sys`, and `/dev` denied
* `Feature` Added `accessibility.swipe` and `accessibility.gesture` behind the dedicated `accessibility.gesture` capability
* `Fix` Made raw TypeScript fail closed unless the host supplies compiler output, mapped snapshot dynamic imports, and normalized generated/imported stack frames
* `Fix` Replaced the snapshot-based partial ESM adapter with the V8 native linker, fixing mutable exports that failed to update through cyclic re-exports
* `Improvement` Aligned the v2 host/plugin contract, capability manifests, and plugin-only runtime responsibility boundary
* `Improvement` Centralized Node.js examples, TypeScript declarations, the project wizard, runtime defaults, and host-alignment checks in the plugin repository, removing host-side Gradle switches and duplicate development assets

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

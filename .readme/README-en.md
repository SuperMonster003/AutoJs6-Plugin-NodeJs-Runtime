<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Node.js 24.21.0 native runtime plugin for AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
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

The AutoJs6 Node.js Runtime Plugin provides an embedded Node.js 24.21.0 native runtime for AutoJs6 scripts and plugin runtime tasks. On Android 17 or later, allow Nearby devices before enabling this plugin in the AutoJs6 plugin center. You can also manage this permission from the Settings page for this plugin. Without permission, the plugin stays disabled and automatic startup is skipped silently. The grant belongs to this plugin, independently of AutoJs6.

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
- Ships the multi-call terminal launcher `libnodexe.so` and an npm / corepack asset archive declared through `NODE_CLI_*` manifest meta-data, so the AutoJs6 terminal (host 6.8.0+) can run `node`, `npm`, `npx`, `corepack`, `yarn` and `pnpm` in a shell of its own uid.

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

- Runtime slot: `node24_21`.
- Plugin ID: `nodejs`, engine: `nodejs`.
- Runtime service action: `org.autojs.plugin.nodejs.RUNTIME`.
- Native runtime libraries: `libnode.so`, `libautojs6-node.so` and `libnodexe.so`.
- Intl: ICU 78 with English-only locale data (`--with-intl=small-icu`); `Intl`, Unicode property escapes in regular expressions and Node's own error output on stderr work in the AutoJs6 terminal, and `NODE_ICU_DATA` can point at a full ICU data file.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `universal`.
- Filesystem: device paths allowed by Android permissions are reachable; `/proc`, `/sys`, and `/dev` are hard boundaries.
- TypeScript: accepts host output and can request provider-v3 compilation for runtime-created project files; direct raw TypeScript returns `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, with native live bindings and cyclic dependencies.
- Capabilities: sync script execution, bundle transport, native embedded runtime, host capability broker, host capability live bridge.
- File streams, gzip/deflate/Brotli streams and basic VM execution are stable plugin capabilities; the explicitly enabled Debug Inspector is stable within its localhost scope.
- Embedded execution no longer prints ExperimentalWarning notices; warning events, ordinary warnings, deprecations and errors remain available. Upstream Node API stability and terminal defaults are unchanged.

******

### Release History

******

# v1.5.5

###### 2026/09/17

* `Fix` The capability catalog version and digest reported by runtimeInfo are now derived from the runtime kit, correcting the stale 1.5.0 values still reported by 1.5.4
* `Fix` worker_threads honors new Worker(code, { eval: true }) as in Node instead of treating the code as a script path
* `Improvement` The host TypeScript compile route is a stable catalog capability and the retired legacy stripping metadata is removed; the lifecycle catalog lists only runnable execution modes, dropping the packaged_long_running surface and the node_sandboxed / worker_computation placeholders; transport, admission and cancellation metadata match the actual runtime
* `Improvement` The autojs6:profile diagnostics describe the actual runtime instead of historical partial / reserved / deferred markers: Android-bounded file access, the process subset, worker channels, the process pool, and the WASI / native addon decisions
* `Improvement` Sample audit: packaged-esm, packaged-dynamic-import, require-esm, wasm-basic, wasm-plugin and desktop-parity-suite are stable; both parity suites now execute their snippets instead of printing catalog text; compile-cache is marked not applicable and the metadata-only package-install sample is removed
* `Improvement` Module loading follows Android file access like Node: require, import and Worker accept absolute paths, parent-directory targets and file: URLs (/proc, /sys and /dev stay denied); node_modules lookup stays anchored to the workspace
* `Improvement` Worker messages and fs watchers follow native Node limits: the 64 KB message and 32-queued caps and the 16-watcher / 64-events-per-second quotas are removed; only Android memory bounds them
* `Improvement` Inside workers process.exit() ends the worker thread as in Node and process.getBuiltinModule() follows the worker builtin allowlist instead of being disabled
* `Improvement` Workers resolve bare package specifiers through the workspace node_modules as in Node (exports conditions, main, index, self-reference) instead of denying them
* `Improvement` Dynamic import() inside workers goes through the worker partial ESM loader (local paths, file: URLs, workspace packages, allowed builtins, with { type: "json" }) instead of being rejected
* `Improvement` host-events sample promoted to stable after the physical key and notification access acceptance passed
* `Improvement` scheduled-node-task sample prints its lifecycle policy and can keep the scheduled task for a real WorkManager run; the scheduled execution mode gains plugin-side regression coverage
* `Improvement` scheduled execution mode promoted to available in the capability catalog after a real host WorkManager scheduled-runner run through the plugin
* `Improvement` media.play() returns a playback session (pause/resume/seekTo/stop/status) over the host script music service, and the new media_store module exposes scoped MediaStore capabilities/query/get/insert/update/delete/scanFile/exportFile behind the media.playback / media.library / media.library.mutate capabilities; autojs6:compat.media gains the Rhino playMusic-style aliases; both require the matching host build
* `Improvement` media-playback / media-library samples promoted to stable after manual acceptance with the merged host media providers; capability catalog snapshot 1.5.5 published under releases/nodejs-capability-catalog and the host alignment task now points at it
* `Improvement` fs wrapper drops its own option restrictions: stream fs / inherited fd / flags options, watch({ recursive: true }), async cp filters (cpSync keeps Node's ERR_INVALID_RETURN_VALUE), absolute / parent-directory / literal '!' glob patterns with exclude arrays, readableWebStream type / encoding and the Stats / Dirent / Dir constructors now follow native Node 24; filesystemProfile.advancedApis.recursiveWatch reports native
* `Improvement` recursive readdir / opendir are native (the 4096-entry cap and per-entry realpath checks are gone; readdir('/') lists proc/sys/dev names like Node), readlink / chmod / chown / utimes accept absolute paths and chmod follows symlinks, and fs policy errors collapse to ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (hard boundary, shared with the loader) / ERR_AUTOJS6_FS_SCOPED_PATH while ordinary fs failures keep only their Node code
* `Improvement` the runtime drops its own module-source budgets (16 MiB per module, 64 MiB total, 8192 modules, provider request count), a CommonJS entry gets Node's absolute __filename / require.main.filename / process.argv[1] with require.main.id '.', and fs.mkdtemp* is native: it returns the caller's prefix spelling plus the suffix in the requested encoding and no longer rejects a symlinked parent
* `Improvement` node:sqlite hands SQL file operations to native SQLite: ATTACH with a literal filename (including file: URIs) is checked by the SQLite authorizer against the /proc, /sys, /dev boundary instead of by scanning SQL text, VACUUM INTO targets pass the same boundary check through SQLite's internal ATTACH, directory PRAGMAs are no longer intercepted, file: URI strings and the empty temporary database open, and setAuthorizer() composes with the boundary check (only ATTACH with a bound or computed filename is still refused)

# v1.5.4

###### 2026/09/17

* `Fix` Embedded execution no longer prints ExperimentalWarning notices; warning events, ordinary warnings, deprecations and errors remain available. Upstream Node API stability and terminal defaults are unchanged
* `Improvement` File streams, gzip/deflate/Brotli streams and basic VM execution are stable plugin capabilities; the explicitly enabled Debug Inspector is stable within its localhost scope

# v1.5.3

###### 2026/09/16

* `Improvement` Android 17 local network authorization moves to plugin-center enablement and plugin settings, with no launcher permission page; missing permission keeps the plugin disabled and automatic startup silent
* `Improvement` Target Android 17 (SDK 37) with separate local network permission controls and recovery guidance

##### For more release history

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

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
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

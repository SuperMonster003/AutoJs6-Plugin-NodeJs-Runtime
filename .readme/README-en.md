<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Node.js 26.13.0 native runtime plugin for AutoJs6</p>

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

The AutoJs6 Node.js Runtime Plugin provides an embedded Node.js 26.13.0 native runtime for AutoJs6 scripts and plugin runtime tasks. On Android 17 or later, allow Nearby devices before enabling this plugin in the AutoJs6 plugin center. You can also manage this permission from the Settings page for this plugin. Without permission, the plugin stays disabled and automatic startup is skipped silently. The grant belongs to this plugin, independently of AutoJs6.

******

### Features

******

- Provides the `nodejs` plugin service with plugin ID `nodejs` and engine `nodejs`.
- Exposes synchronous script execution and runtime prewarm to the host through `org.autojs.plugin.nodejs.RUNTIME`.
- Added script timeouts covering queueing and execution, returning `ERR_AUTOJS6_SCRIPT_TIMEOUT`; scripts without a timeout can still run indefinitely
- Returns every Node.js builtin as Node exports it, straight from Node's own `module.builtinModules` inventory (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` and the rest); only `worker_threads`, `child_process`, `inspector` and the raw network modules keep a request-level gate (since 1.6.0).
- Local Inspector debugging works in Debug and Release after an explicit host request, using localhost and ADB forwarding
- Supports CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, and stdout/stderr result payloads.
- Runs modules through Node.js's own CommonJS / ESM loaders (`require`, `import()`, `import.meta`, `require(esm)`, package exports / imports are native); the runtime only registers `module.registerHooks` for the `autojs6:*` runtime modules, host-provided module sources and the request-level builtin gate (since 1.6.0).
- Uses Node.js's own fs module; file reachability is decided solely by the plugin app's Android permissions, with no runtime-owned denial of `/proc`, `/sys` or `/dev` (since 1.6.0).
- Executes host-supplied TypeScript compiler output and can request provider-v3 compilation for runtime-created project `.ts`/`.mts`/`.cts`; direct raw dispatch always fails closed because no runtime erasure fallback or compatibility switch remains.
- Provides host capability broker and live bridge support with runtime modules such as `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, and `autojs6:bridge-permissions`.
- Includes `sample/nodejs` projects and the `docs/HOST-API.md` host API capability inventory.
- Plugin metadata, usage instructions, README, and CHANGELOG are localized for Spanish, French, Russian, Arabic, Japanese, Korean, English, Simplified Chinese, Hong Kong Traditional Chinese, and Taiwan Traditional Chinese.
- Ships the multi-call terminal launcher `libnodexe.so` and an npm / corepack asset archive declared through `NODE_CLI_*` manifest meta-data, so the AutoJs6 terminal (host 6.8.0+) can run `node`, `npm`, `npx`, `corepack`, `yarn` and `pnpm` in a shell of its own uid.
- The plugin settings page provides 1-8 parallel execution slots (default 2) and a queue limit (unlimited by default); surplus scripts wait in FIFO order, settings take effect without interrupting accepted work, and idleExitMs releases idle runtime processes
- Faster script startup by preserving native Web API initialization and enabling Node's portable module compile cache for workspaces and Workers by default; cache entries survive workspace copies and Node invalidates changed sources
- Host database/sqlite APIs for Bun and Node.js Workers with parameterized queries, transactions and persistence.
- Sensor sampling and subscriptions for Bun and Node.js Workers with cancellation and automatic listener cleanup.
- Host Shell streaming, Shizuku authorization and process cleanup for Bun, Node.js and Workers.

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

- Runtime slot: `node26_13`.
- Plugin ID: `nodejs`, engine: `nodejs`.
- Runtime service action: `org.autojs.plugin.nodejs.RUNTIME`.
- Native runtime libraries: `libnode.so`, `libautojs6-node.so` and `libnodexe.so`.
- Intl: ICU 78 with full locale data (`--with-intl=full-icu` since 1.6.0; 1.5.1 to 1.5.7 shipped English-only small-icu); `Intl`, locale formatting, Unicode property escapes in regular expressions and Node's own error output on stderr match desktop Node, with no `NODE_ICU_DATA` needed.
- Temporal: since 2.0.0 the built-in `Temporal` global of Node.js 26 handles dates, times, time zones and calendars without extra libraries.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `universal`.
- Filesystem: Node.js's own fs module; any device path allowed by Android permissions is reachable and the runtime keeps no denial of its own.
- TypeScript: accepts host output, maps precompiled snapshots and can request provider-v3 compilation; raw TypeScript modules on disk load through Node's own type stripping, while a raw TypeScript entry dispatched directly still returns `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: Node.js's own loader with `module.registerHooks`; live bindings, cyclic dependencies, `import.meta`, `data:` URLs and `require(esm)` as on desktop.
- Capabilities: sync script execution, bundle transport, native embedded runtime, host capability broker, host capability live bridge.
- File streams, gzip/deflate/Brotli streams and basic VM execution are stable plugin capabilities; the explicitly enabled Inspector (Debug/Release) is stable within its localhost scope.
- Embedded execution no longer prints ExperimentalWarning notices; warning events, ordinary warnings, deprecations and errors remain available. Upstream Node API stability and terminal defaults are unchanged.
- Native Worker threads support nesting, MessagePort APIs, stdio, argv, execArgv and inherited preloads; plugin worker-count and startup-timeout limits are removed, request resourceLimits are overridable defaults, and WorkerPool has no default queue limit or task timeout; Inspector sessions follow the execution request and current build support
- Android native addons load through Node in main scripts and Workers: process.dlopen and the .node extension are native, with no plugin-specific signature or hash allowlist; native-addon provides Node-API binaries for three ABIs with 16 KB alignment and an offline NDK/CMake build; wrong ABI or invalid binaries report Node errors such as ERR_DLOPEN_FAILED, while a device NDK/Python build toolchain is not bundled
- Inspector supports Debug and Release builds after an explicit host request; inspector and inspector/promises keep native module identity, sessions work in root scripts and Workers, listeners bind only to 127.0.0.1 and blocking waits are unavailable; without permission url() returns undefined while activation and Session creation remain disabled
- package_manager install/update/remove accept npm package specs and run the bundled npm in the workspace; npm.cli/exec/runScript/pack use the real CLI, registry/proxy/lifecycle options follow npm and project .npmrc, output streams back and failures retain npm codes; CLI assets are prepared only on first use, while object requests keep the existing host local-store contract
- Node.js 24 line: plugin 1.x (Node.js 24.21.0) is maintained on the `release/1.x` branch with security updates only, until 2028-04-30 (end of life of Node.js 24).

******

### Release History

******

# v2.0.0

###### 2026/10/30

* `Hint` From plugin 2.0.0 on, scripts run on Node.js 26, which removes some APIs deprecated in Node.js 24 (for example SlowBuffer, the recursive option of fs.rmdir, constants such as fs.F_OK, assert.CallTracker and node:_stream_wrap); scripts that use them need updating. To stay on Node.js 24, uninstall 2.x and install a 1.x version (security updates only, maintained until 2028-04-30)
* `Hint` Native addons (.node) built directly against V8 must be rebuilt for Node.js 26; Node-API addons are not affected
* `Feature` The Temporal global built into Node.js 26 handles dates, times, time zones and calendars without extra libraries
* `Feature` app, shell and shizuku modules for Node.js and Workers matching AutoJs6 scripts, including launching, uninstalling and stopping dual (cloned) apps; commands run through Shizuku, root or the plain shell, whichever is available first, and return Promises
* `Feature` System information modules for Node.js and Workers matching AutoJs6 scripts: autojs, installer, memory, settings, sysprops, power_manager and autojs6:system (setScreenMetrics also scales accessibility clicks and gestures), plus notification switches and channels, dialogs.build, engines listing and stopping host scripts, plugins.extend and plugins.tools
* `Feature` device, sensors and media modules for Node.js and Workers matching AutoJs6 scripts: device model, screen and manufacturer checks, the full vibration and keep-screen-on options, sensor events, music playback and media scanning; reading device identifiers requires the new device.identifiers capability
* `Feature` colors/Color, files, zip, mime and the timing recorder for Node.js and Workers matching AutoJs6 scripts; dayjs, pangu.js, cheerio and banana-i18n are available through autojs6:global, and axios requests in Workers are handled by the main thread
* `Fix` The Packages / java / android proxies installed by rhino.install() threw errors when reading static fields, calling describe(), calling static methods or creating objects with new
* `Improvement` Node.js 26 no longer ships corepack; the plugin keeps bundling corepack 0.36.0 and the terminal usage is unchanged
* `Improvement` TypeScript declarations of worker_threads cover WorkerPool options, cancellation, timeouts and diagnostics, so the module is typed without @types/node
* `Improvement` The Node.js runtime library is built with NDK r29 and keeps 16 KB page alignment on all three ABIs; the release acceptance script runs the full device regression, writes a JSON report and measures cold start
* `Dependency` Upgraded Node.js 24.21.0 -> 26.13.0 (V8 14.6, OpenSSL 3.5.9, ICU 78.3) using source-built Android libraries for all three ABIs
* `Dependency` Upgraded npm 11.19.0 -> 11.20.0

# v1.32.0

###### 2026/10/29

* `Feature` ai.agent task controls, events and registered-script context/results for Node.js, Bun and Workers, with explicit authorization for packaged applications
* `Feature` util, species, s13n, fmt/cvt and jsox tool modules and global data functions for Node.js and Workers, matching AutoJs6 scripts and imported by module names such as autojs6:util
* `Fix` web.cancelFileChooser() called right after chooseFiles() could leave the file chooser open
* `Improvement` jsox and byte-unit conversion results and errors now match AutoJs6 scripts, and jsox.extend/extendAll extend Math, Array and Number of the current thread directly

# v1.31.0

###### 2026/10/22

* `Feature` AI text generation, streaming, model catalog and persistent sessions for Node.js, Bun and Workers, with cancellation and per-app consent in 3-Stone AI for packaged apps

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

Build parameters come from `version.properties`, the current minimum SDK is 24 and target SDK is 37.

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

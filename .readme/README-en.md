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
- Returns every Node.js builtin as Node exports it, straight from Node's own `module.builtinModules` inventory (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` and the rest); only `worker_threads`, `child_process`, `inspector` and the raw network modules keep a request-level gate (since 1.6.0).
- Added explicitly enabled local `inspector` debugging in Debug builds, listening only on localhost and connecting through `adb forward`
- Supports CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, and stdout/stderr result payloads.
- Runs modules through Node.js's own CommonJS / ESM loaders (`require`, `import()`, `import.meta`, `require(esm)`, package exports / imports are native); the runtime only registers `module.registerHooks` for the `autojs6:*` runtime modules, host-provided module sources and the request-level builtin gate (since 1.6.0).
- Uses Node.js's own fs module; file reachability is decided solely by the plugin app's Android permissions, with no runtime-owned denial of `/proc`, `/sys` or `/dev` (since 1.6.0).
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
- Intl: ICU 78 with full locale data (`--with-intl=full-icu` since 1.6.0; 1.5.1 to 1.5.7 shipped English-only small-icu); `Intl`, locale formatting, Unicode property escapes in regular expressions and Node's own error output on stderr match desktop Node, with no `NODE_ICU_DATA` needed.
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

******

### Release History

******

# v1.6.0

###### 2026/10/08

* `Improvement` fs, fs/promises and node:sqlite are Node.js's own modules: the runtime's /proc, /sys, /dev denial and its path-validating wrapper are gone, file reachability is decided by Android permissions alone, asynchronous file APIs run on the libuv thread pool instead of blocking the event loop, and path errors carry Node.js's own codes (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH and ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED are no longer produced by file or database operations)
* `Improvement` process.cwd and process.chdir are native Node.js behavior; an execution enters its working directory with a real chdir and relative paths are resolved by Node.js itself
* `Improvement` node:sqlite ATTACH (including bound-parameter and expression filenames), VACUUM INTO, directory PRAGMAs and setAuthorizer are plain SQLite behavior; enableLoadExtension and allowExtension follow Node.js's own state checks
* `Improvement` The Node.js runtime library is built with full ICU data (--with-intl=full-icu), so Intl and locale formatting match desktop Node.js and NODE_ICU_DATA is no longer needed
* `Improvement` crypto.createPrivateKey and the signing APIs accept file: URL OpenSSL STORE key inputs (the Android-only disabling patch is removed)
* `Improvement` Every Node.js builtin (cluster, domain, repl, trace_events, wasi, constants, node:sea and the rest) is returned as Node exports it, straight from Node's own module.builtinModules inventory; require, require.resolve, import, process.getBuiltinModule and module.isBuiltin behave as on desktop, and only worker_threads, child_process, inspector and the raw network modules keep a request-level gate; node:wasi opens up together with the removed /proc boundary (on the current libnode, built without tracing, trace_events throws ERR_TRACE_EVENTS_UNAVAILABLE exactly as Node does)
* `Improvement` process keeps only the lifecycle mapping of exit; dlopen, abort, kill, umask, binding, report, features and POSIX identity calls use Node implementations; os is native, homedir and tmpdir follow HOME/TMPDIR and userInfo reports the Android app user
* `Improvement` The module builtin is Node.js's own object with only createRequire overridden, so registerHooks, enableCompileCache, findPackageJSON and the rest are available; the limited http, https, net, tls and dns implementations that were reachable only with raw networking disabled are removed and that mode now returns ERR_AUTOJS6_BUILTIN_DISABLED directly
* `Improvement` supportedAbis in the plugin INFO and the runtime diagnostics now comes from the org.autojs.plugin.contract.SUPPORTED_ABIS manifest meta-data written at build time (nativeAlignment plugin of AutoJs6-Gradle-Platform-Versions 1.9.0 or later), meaning every ABI the plugin is built for rather than the installed split; without the meta-data it falls back to the ABIs packaged in the installed APKs, and the hand-written ABI constants are gone
* `Improvement` Module loading is Node.js's own CommonJS / ESM loader: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta and require(esm) are native, and the entry runs as Node's main module (require.main === module, __filename / process.argv[1] are realpaths); the runtime only registers module.registerHooks that serve the autojs6:* runtime modules and compat shims (autojs6: scheme; an installed npm package still shadows a shim of the same name), the request-level builtin gate, request-attached / provider-materialized / TypeScript-snapshot module sources and the entry's inline source; the vm.SourceTextModule linker, the custom resolver, the dynamic import() rewrite and the eval / Function guards are gone, and the esmEnabled / dynamicImportEnabled request keys no longer gate anything
* `Improvement` Native Worker threads support nesting, MessagePort APIs, stdio, argv, execArgv and inherited preloads; plugin worker-count and startup-timeout limits are removed, request resourceLimits are overridable defaults, and WorkerPool has no default queue limit or task timeout; Inspector sessions follow the execution request and current build support
* `Improvement` autojs6:bridge.callAutoJs is available in Workers and nested Workers through parent MessagePorts, reusing the host broker permissions, errors, timeout and cancellation; worker-host-bridge demonstrates reading real host device information, with TypeScript declarations for the bridge API
* `Improvement` Raw .ts / .mts / .cts entries and imported modules still require JavaScript output from the host Compiler; snapshot mapping and provider v3 on-demand compilation are retained. Native loader failures replace the retired AutoJs6 ESM and dynamic-import error codes.
* `Improvement` Android native addons load through Node in main scripts and Workers: process.dlopen and the .node extension are native, with no plugin-specific signature or hash allowlist; native-addon provides Node-API binaries for three ABIs with 16 KB alignment and an offline NDK/CMake build; wrong ABI or invalid binaries report Node errors such as ERR_DLOPEN_FAILED, while a device NDK/Python build toolchain is not bundled
* `Improvement` Inspector supports Debug and Release builds after an explicit host request; inspector and inspector/promises keep native module identity, sessions work in root scripts and Workers, listeners bind only to 127.0.0.1 and blocking waits are unavailable; without permission url() returns undefined while activation and Session creation remain disabled
* `Improvement` package_manager install/update/remove accept npm package specs and run the bundled npm in the workspace; npm.cli/exec/runScript/pack use the real CLI, registry/proxy/lifecycle options follow npm and project .npmrc, output streams back and failures retain npm codes; CLI assets are prepared only on first use, while object requests keep the existing host local-store contract

# v1.5.7

###### 2026/10/05

* `Improvement` Plugin Center icons use the sizes, positions, light and dark artwork, and circular backgrounds adjusted in Icon Studio, retaining reproducible sources and parameters
* `Improvement` Android App info icons share Icon Studio artwork and light/dark backgrounds while preserving transparent Plugin Center artwork and existing launcher choices

# v1.5.6

###### 2026/09/19

* `Feature` A Node.js fatal error in a runtime slot (JavaScript heap out of memory, a failed internal check) now leaves Node's own diagnostic report in the plugin cache (crash/<executionId>.json, written before the abort; no signal handler is installed): the failure result carries its event, heap figures, JS stack and path in slotExit.crash and in the error message (for example CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), and getRuntimeInfo exposes the newest report as lastCrash across dispatcher restarts; the new request option maxOldGenerationSizeMb caps the JS heap of one execution (V8 old generation, in MB), which is also how the regression test reproduces the crash
* `Fix` Fixed the runtime slot dying of a V8 heap out-of-memory abort (reported as CRASH_NATIVE / SIGABRT) when a large CommonJS module was required: the loader's comment and string masking appended one character at a time, so a 22 MiB module needed about 700 MB of heap; it now emits runs with identical output (about 44 MB), and the CommonJS import / export scans jump between candidates instead of testing every character
* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3

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

******

### Release History

******

# v1.7.0

###### 2026/10/17

* `Feature` Packaged Node.js projects and scripts use the external Node.js Runtime plugin, support independently signed applications, and show installation guidance when the runtime is unavailable
* `Feature` screen_metrics and autojs6:compat expose asynchronous cX/cY/cXy/cYx and per-execution scale bases using the current display, with coverage checks and an ESM example
* `Feature` Node.js and Bun support asynchronous OpenCC conversions and common image codecs, pixel reads, transforms and color searches; Bun image file access is authorized separately
* `Feature` Node.js Workers support OpenCC and image modules through the parent runtime, including CJS, ESM, nested Workers and binary image data
* `Fix` Pinyin/Pinyin4j now await real host providers instead of a small local dictionary; Android console controls execute on the host and return Promises; accessibility gestures and input_observer declarations are complete
* `Dependency` Backported OpenSSL 3.5.9 security fixes to the Node.js 24.21.0 Android runtime

# v1.6.0

###### 2026/10/09

* `Feature` The plugin settings page provides 1-8 parallel execution slots (default 2) and a queue limit (unlimited by default); surplus scripts wait in FIFO order, settings take effect without interrupting accepted work, and idleExitMs releases idle runtime processes
* `Fix` The project wizard no longer rejects supported native dependencies, npm lifecycle scripts, package exports or Inspector; host permission declaration checks remain in place
* `Improvement` fs, fs/promises and node:sqlite are Node.js's own modules: the runtime's /proc, /sys, /dev denial and its path-validating wrapper are gone, file reachability is decided by Android permissions alone, asynchronous file APIs run on the libuv thread pool instead of blocking the event loop, and path errors carry Node.js's own codes (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH and ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED are no longer produced by file or database operations)
* `Improvement` process.cwd and process.chdir are native Node.js behavior; an execution enters its working directory with a real chdir and relative paths are resolved by Node.js itself
* `Improvement` node:sqlite ATTACH (including bound-parameter and expression filenames), VACUUM INTO, directory PRAGMAs and setAuthorizer are plain SQLite behavior; enableLoadExtension and allowExtension follow Node.js's own state checks
* `Improvement` The Node.js runtime library is built with full ICU data (--with-intl=full-icu), so Intl and locale formatting match desktop Node.js and NODE_ICU_DATA is no longer needed
* `Improvement` crypto.createPrivateKey and the signing APIs accept file: URL OpenSSL STORE key inputs (the Android-only disabling patch is removed)
* `Improvement` Every Node.js builtin (cluster, domain, repl, trace_events, wasi, constants, node:sea and the rest) is returned as Node exports it, straight from Node's own module.builtinModules inventory; require, require.resolve, import, process.getBuiltinModule and module.isBuiltin behave as on desktop, and only worker_threads, child_process, inspector and the raw network modules keep a request-level gate; node:wasi opens up together with the removed /proc boundary (on the current libnode, built without tracing, trace_events throws ERR_TRACE_EVENTS_UNAVAILABLE exactly as Node does)
* `Improvement` process keeps only the lifecycle mapping of exit; dlopen, abort, kill, umask, binding, report, features and POSIX identity calls use Node implementations; os is native, homedir and tmpdir follow HOME/TMPDIR and userInfo reports the Android app user
* `Improvement` supportedAbis in the plugin INFO and the runtime diagnostics now comes from the org.autojs.plugin.contract.SUPPORTED_ABIS manifest meta-data written at build time (nativeAlignment plugin of AutoJs6-Gradle-Platform-Versions 1.9.0 or later), meaning every ABI the plugin is built for rather than the installed split; without the meta-data it falls back to the ABIs packaged in the installed APKs, and the hand-written ABI constants are gone
* `Improvement` Module loading is Node.js's own CommonJS / ESM loader: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta and require(esm) are native, and the entry runs as Node's main module (require.main === module, __filename / process.argv[1] are realpaths); the runtime only registers module.registerHooks that serve the autojs6:* runtime modules and compat shims (autojs6: scheme; an installed npm package still shadows a shim of the same name), the request-level builtin gate, request-attached / provider-materialized / TypeScript-snapshot module sources and the entry's inline source; the vm.SourceTextModule linker, the custom resolver, the dynamic import() rewrite and the eval / Function guards are gone, and the esmEnabled / dynamicImportEnabled request keys no longer gate anything
* `Improvement` Native Worker threads support nesting, MessagePort APIs, stdio, argv, execArgv and inherited preloads; plugin worker-count and startup-timeout limits are removed, request resourceLimits are overridable defaults, and WorkerPool has no default queue limit or task timeout; Inspector sessions follow the execution request and current build support
* `Improvement` autojs6:bridge.callAutoJs is available in Workers and nested Workers through parent MessagePorts, reusing the host broker permissions, errors, timeout and cancellation; worker-host-bridge demonstrates reading real host device information, with TypeScript declarations for the bridge API
* `Improvement` Raw .ts / .mts / .cts entries and imported modules still require JavaScript output from the host Compiler; snapshot mapping and provider v3 on-demand compilation are retained. Native loader failures replace the retired AutoJs6 ESM and dynamic-import error codes.
* `Improvement` Android native addons load through Node in main scripts and Workers: process.dlopen and the .node extension are native, with no plugin-specific signature or hash allowlist; native-addon provides Node-API binaries for three ABIs with 16 KB alignment and an offline NDK/CMake build; wrong ABI or invalid binaries report Node errors such as ERR_DLOPEN_FAILED, while a device NDK/Python build toolchain is not bundled
* `Improvement` Inspector supports Debug and Release builds after an explicit host request; inspector and inspector/promises keep native module identity, sessions work in root scripts and Workers, listeners bind only to 127.0.0.1 and blocking waits are unavailable; without permission url() returns undefined while activation and Session creation remain disabled
* `Improvement` package_manager install/update/remove accept npm package specs and run the bundled npm in the workspace; npm.cli/exec/runScript/pack use the real CLI, registry/proxy/lifecycle options follow npm and project .npmrc, output streams back and failures retain npm codes; CLI assets are prepared only on first use, while object requests keep the existing host local-store contract
* `Improvement` Faster script startup by preserving native Web API initialization and enabling Node's portable module compile cache for workspaces and Workers by default; cache entries survive workspace copies and Node invalidates changed sources
* `Improvement` Runtime capability metadata now describes implemented behavior without provisional status labels; UI/provider-dependent examples identify their host requirements, and unplanned plugin marketplace/update fields are removed

# v1.5.7

###### 2026/10/05

* `Improvement` Plugin Center icons use the sizes, positions, light and dark artwork, and circular backgrounds adjusted in Icon Studio, retaining reproducible sources and parameters
* `Improvement` Android App info icons share Icon Studio artwork and light/dark backgrounds while preserving transparent Plugin Center artwork and existing launcher choices

# v1.5.6

###### 2026/09/19

* `Feature` A Node.js fatal error in a runtime slot (JavaScript heap out of memory, a failed internal check) now leaves Node's own diagnostic report in the plugin cache (crash/<executionId>.json, written before the abort; no signal handler is installed): the failure result carries its event, heap figures, JS stack and path in slotExit.crash and in the error message (for example CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), and getRuntimeInfo exposes the newest report as lastCrash across dispatcher restarts; the new request option maxOldGenerationSizeMb caps the JS heap of one execution (V8 old generation, in MB), which is also how the regression test reproduces the crash
* `Fix` Fixed the runtime slot dying of a V8 heap out-of-memory abort (reported as CRASH_NATIVE / SIGABRT) when a large CommonJS module was required: the loader's comment and string masking appended one character at a time, so a 22 MiB module needed about 700 MB of heap; it now emits runs with identical output (about 44 MB), and the CommonJS import / export scans jump between candidates instead of testing every character
* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3

# v1.5.5

###### 2026/09/18

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
* `Improvement` Bridge quotas are handed to the host: calls beyond the autojs6:bridge-limits maxPendingBridgeCalls window now wait in FIFO order instead of failing with ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT, the runtime no longer caps image handles, concurrent controlled fetch requests or controlled WebSocket connections on its own (the host broker keeps enforcing its policy), and require('fetch').policy.maxConcurrentRequests / require('websocket').policy.maxConnections are retired
* `Improvement` The bridged fetch / WebSocket / axios facades hand their hard caps to the host: timeouts, response size, redirect count, message and queue sizes and the HTTP method are passed to the host provider unclamped (its own policy applies), the runtime follows up to 20 redirects like Node, and the retired hard*/default size fields of policy are replaced by limitsEnforcedBy
* `Improvement` opendir returns Node's own lazy fs.Dir (bufferSize, encoding and recursive go to native opendir, ENOENT / ENOTDIR / ERR_DIR_CLOSED are Node's errors; only dir.path and parentPath keep the caller's spelling), and the runtime's guard against removing the filesystem reach root is retired so rm / rmdir of the root is Node's and Android's decision like any other path (fs policy codes are now only FS_NUL_BYTE and FS_PATH_ESCAPE)
* `Improvement` The host provider transport adopts the per-source / aggregate / request-count sizes the host advertises through getNativeDiagnostics() (the built-in 16 MiB / 64 MiB / 139264 values are only a fallback), bridged fetch response bodies arrive through a file descriptor (bodyTransport "pfd") so they are bounded only by the host maxResponseBytes policy rather than the Binder transaction size, and a host reply that exceeds the Binder transaction size is reported at once as ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (host branch node-m20-2-binder-body-pfd) instead of a bridge timeout
* `Improvement` Bridged fetch request bodies and WebSocket messages above 256 KiB now reach the host as a read-only file descriptor (bodyTransport / messageTransport "pfd") instead of inline base64 JSON, on hosts that advertise bridgeRequestBinaryTransport=pfd (host branch node-m20-2-binder-body-pfd), so uploads are bounded by the host request policy (64 MiB) and maxMessageBytes rather than the Binder transaction size
* `Improvement` Controlled fetch responses now expose the provider's final URL through Response.url (ResponseInit carries no url, so it used to read as an empty string)
* `Improvement` Java interop now forwards every call to the host's declarative allowlist (class → constructors, static and instance methods, fields; the host executes them by reflection with JSON primitives and object handles), the runtime drops its own class table and reads the host's back as java.policy, getStatic() / describe() are added, and exceptions thrown by the member itself are reported as ERR_AUTOJS6_JAVA_CALL_FAILED
* `Improvement` npm precedence over runtime modules is now limited to the modules that stand in for real npm packages (axios, colors, mime, nanoid, opencc, undici): a same-named package under node_modules no longer replaces the java, fetch, websocket, device or other AutoJs6 facades, require.resolve follows the same rule as require, every bare runtime module name can be imported from ESM, autojs6:profile reports the rule as moduleResolutionProfile, and the Rhino Packages proxy gains getStatic() / describe()
* `Improvement` When a runtime slot process dies in the middle of a script (killed by the low-memory killer, a native crash, a plain kill), the failure now reports the system's exit record instead of a bare DeadObjectException: the message reads for example LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB) or CRASH_NATIVE (SIGABRT; see the logcat tombstone), the result carries slotExit and getRuntimeInfo carries lastSlotExit (Android 11+; older systems report that the reason is unavailable)

# v1.5.4

###### 2026/09/17

* `Fix` Embedded execution no longer prints ExperimentalWarning notices; warning events, ordinary warnings, deprecations and errors remain available. Upstream Node API stability and terminal defaults are unchanged
* `Improvement` File streams, gzip/deflate/Brotli streams and basic VM execution are stable plugin capabilities; the explicitly enabled Debug Inspector is stable within its localhost scope

# v1.5.3

###### 2026/09/16

* `Improvement` Android 17 local network authorization moves to plugin-center enablement and plugin settings, with no launcher permission page; missing permission keeps the plugin disabled and automatic startup silent
* `Improvement` Target Android 17 (SDK 37) with separate local network permission controls and recovery guidance

# v1.5.2

###### 2026/09/15

* `Improvement` Raise compileSdk to 37 (Android 17); targetSdk stays at 36 until the behavior that depends on the target is verified

# v1.5.1

###### 2026/09/15

* `Fix` Node's fatal-error and warning text is written to the real stderr on Android in addition to logcat, so a terminal shows uncaught exceptions instead of exiting silently
* `Improvement` `libnode.so` rebuilt with `--with-intl=small-icu`: `Intl` and Unicode property escapes (`\p{...}`) in regular expressions are available with English-only locale data (`NODE_ICU_DATA` accepts a full ICU data file), so corepack can run pnpm 11 and Yarn Berry in the AutoJs6 terminal

# v1.5.0

###### 2026/09/14

* `Feature` Multi-call terminal launcher `libnodexe.so` (node / npm / npx / corepack / yarn / pnpm) packaged for every ABI with `DT_RUNPATH $ORIGIN` and 16 KB page alignment
* `Feature` npm 11.19.0 and corepack 0.36.0 asset archive taken from the official Node.js 24.21.0 tarball, declared through `NODE_CLI_*` manifest meta-data (schema 1) and mirrored into runtimeInfo / PluginInfo as the `nodeCli` capability

# v1.4.2

###### 2026/09/13

* `Fix` Report only the native ABIs present in the installed APK
* `Fix` Use English build dates in plugin metadata regardless of the build machine locale
* `Fix` Consistent version metadata in release packages and archives
* `Fix` Workspace file compatibility on Android 7 while preserving file descriptor isolation

# v1.4.1

###### 2026/09/13

* `Improvement` Build verification of 16 KB page alignment for 64-bit native libraries, including manifest contract checks and JSON reports
* `Improvement` Host activation, plugin metadata, localized documentation and signed release collection follow the common plugin conventions

# v1.4.0

###### 2026/09/10

* `Feature` MediaInfo queries support zero-based streamNumber, countGet stream counts, and infoKind for units, descriptions and readable names; Rhino and Node preserve default first-stream TEXT queries and negotiate extended plugin capabilities
* `Fix` MediaInfo and image file paths now allow absolute paths, parent directories and valid filenames; recording outputs use the same Android file access rules with the updated host
* `Fix` Bridge capability errors now identify missing node.permissions declarations without requiring the diagnostic-only pro_compat_opt_in profile
* `Fix` The project validator no longer rejects absolute fs paths or parent directories as FS_OUTSIDE_SCOPE; Android determines actual file access
* `Improvement` Added standalone Android event and three-second audio recording projects, with project declarations and manual steps for screen capture, OCR, physical keys and MediaInfo
* `Improvement` Screen capture matching, screen OCR and three-second AAC recording passed manual device acceptance; the corresponding examples and Pro parity snippets using the same calls are now stable
* `Improvement` The event acceptance example explains temporarily disabling the host volume-up stop shortcut; the updated host reads project.json node.timeoutMs so longer waits no longer time out after about five seconds

# v1.3.0

###### 2026/09/09

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
* `Feature` Node scripts can run concurrently in two independent runtime processes, with a shared FIFO queue and execution-specific cancellation and input routing
* `Fix` Fixed growing live-bridge request and response history in resident scripts by retiring completed requests and bounding diagnostics to the latest 32 responses with a size limit
* `Fix` Fixed prematurely successful results losing native async errors and late exit codes; completion now follows the final Node event-loop exit
* `Fix` OpenSSL STORE key URLs bypassing filesystem restrictions (read key bytes through node:fs instead)
* `Improvement` Reduced live bridge latency with a default JNI/Binder transport and Node event-loop responses, preserving selectable file fallback and pending-call limits
* `Improvement` Restored native Node.js builtin exports for streams, crypto, timers, utilities, node:test and related modules while preserving filesystem boundaries and host directory policy
* `Improvement` Native workers now default to CPU parallelism (up to eight), inherit execution network/filesystem switches, accept request resource caps and have no default pool task deadline; CPU and WASM examples run real workers
* `Improvement` Kept raw WASI disabled to preserve filesystem boundaries and removed the two disabled WASI examples; ordinary WebAssembly and WASM workers remain available
* `Improvement` Screen OCR examples request Android capture consent and recognize real image handles; unavailable OCR or barcode plugins return readable unavailable errors, while recognition failures remain errors
* `Improvement` Node scripts support negotiated asynchronous startup, releasing Binder threads during long-running execution and returning workspace changes after terminal completion, with legacy synchronous host compatibility
* `Dependency` Upgraded Node.js 24.5.0 → 24.21.0 using source-built Android libraries for all three ABIs

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

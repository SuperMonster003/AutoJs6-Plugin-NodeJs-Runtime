# AutoJs6 Node.js Compatibility Profile

Last reviewed: 2026-10-08. Published plugin: v1.5.7; subsequent local work: v1.6.0 (M23.5, Release Inspector).

The external runtime plugin supplies Node.js 24.21.0 / V8 / libuv for arm64-v8a,
armeabi-v7a and x86_64. The host contains no libnode or Node JNI implementation.
Node-marked scripts require the plugin; ordinary JavaScript routing remains a host
concern. Exported APKs currently cannot bundle or launch this runtime.

CommonJS, ESM, dynamic import and local pure JavaScript npm packages execute in
real Node. TypeScript is compiled to JavaScript by the host Compiler, including
supported on-demand module compilation. The runtime does not strip TypeScript.
The capability catalog marks `typescript` as stable: the host compile route
(precompiled snapshots plus provider v3 on-demand compilation) is the formal
capability, and direct raw TypeScript dispatch fails closed by design. The lifecycle
catalog lists only runnable execution modes; the `packaged_long_running` surface and
the `node_sandboxed` / `worker_computation` placeholder names are gone, and the
catalog identity reported by runtimeInfo is derived from the runtime kit. The
`autojs6:profile` diagnostics describe the runtime as built: Android-bounded file
access (since M23.1 `fs` is Node's own module and the runtime denies nothing itself),
the native `process` (only `exit` is lifecycle-mapped; `dlopen` is native since M23.4),
Node's own builtin inventory (M23.2: no allowlist or denylist, `wasi` included), stable
worker channels, the two-slot process pool, and Android native addon loading;
historical partial / reserved / deferred markers are gone. The sample audit
promoted the ESM, WebAssembly and desktop parity samples, turned both parity
suites into real runners, and `new Worker(code, { eval: true })` now works as
in Node. Module loading follows Android file access like Node (M20.2): `require`,
`import` and `Worker` accept absolute paths, parent-directory targets and `file:`
URLs, with `/proc`, `/sys` and `/dev` following Android as well (M23.1); `node_modules` lookup and
package scopes stay anchored to the workspace. An installed package shadows only
the runtime modules that stand in for real npm packages (axios, colors, mime,
nanoid, opencc, undici); AutoJs6 capability facades and the other runtime modules
answer regardless, in `require`, `require.resolve` and `import` alike, and every
bare runtime module name can be imported from ESM (`autojs6:profile` reports the rule
as `moduleResolutionProfile`). Worker messages and fs watchers
follow native Node limits: the 64 KB message / 32-queued caps and the
16-watcher / 64-events-per-second quotas are gone, so only Android memory bounds them.
Since M23.1 (2026-10-07) there is no scoped fs wrapper at all: `require("fs")` and
`fs/promises` return Node's own modules (`require("fs") === process.getBuiltinModule("fs")`),
asynchronous calls run on the libuv thread pool instead of a synchronous call queued in
`setImmediate`, `opendir`, `readdir`, `watch`, `cp`, `glob`, `mkdtemp`, `FileHandle` and
the `Stats` / `Dirent` / `Dir` constructors are the native ones, `process.cwd()` /
`process.chdir()` are native (the execution enters its working directory with a real
chdir), and fs failures carry Node / Android codes only: `ERR_AUTOJS6_FS_NUL_BYTE`,
`ERR_AUTOJS6_FS_PATH_ESCAPE` and `ERR_AUTOJS6_FS_SCOPED_PATH` are retired for fs and,
since M23.3, for module resolution too. `filesystemProfile.mode` reports `android_file_access` and
`advancedApis.recursiveWatch` reports `native`. The
runtime keeps no module-source budget of its own (the 16 MiB per
module, 64 MiB total, 8192 module and provider request-count caps are gone): modules
read from the filesystem, embedded runtime modules and `data:` URL modules are bounded
by device memory only, while the host provider transport takes its per-source, aggregate
and request-count sizes from the host's own `getNativeDiagnostics()` advertisement (the
plugin's 16 MiB / 64 MiB / 139264 values are only the fallback for hosts that advertise
nothing). A CommonJS entry sees an absolute `__filename` / `require.main.filename` /
`process.argv[1]` with `require.main.id === "."` like Node, and `fs.mkdtemp*` returns
the caller's prefix spelling plus the native suffix in the requested encoding. The
`esmModuleGraphModules` diagnostic is a bounded sample (first 64 entries plus a remainder
count) so a large ESM graph cannot overflow the Binder transaction that carries the
finished event. `node:sqlite` is Node's own module since M23.1: SQLite `file:` URI
strings, the empty temporary database, ATTACH with literal, bound or computed filenames,
VACUUM INTO, directory PRAGMAs, `setAuthorizer()` and the `allowExtension` /
`enableLoadExtension` state machine are plain SQLite / Node behavior, and
`ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED` is retired.
Native Node builtins retain their own names: `events` / `node:events` is EventEmitter;
Android event observation is exposed as `autojs6:events`. The runtime links ICU 78 with
full locale data (`--with-intl=full-icu`, since v1.6.0; v1.5.1 to v1.5.7 shipped the
English-only small-icu): `Intl`, locale formatting and Unicode property escapes match
desktop Node.js, and `NODE_ICU_DATA` is not needed. OpenSSL STORE key inputs (`file:` URLs
passed to `crypto.createPrivateKey` and the signing APIs) are accepted since v1.6.0; the
Android-only disabling patch is gone.

File streams, gzip/deflate/Brotli streams, and native VM script/context execution
are stable plugin capabilities. See the runnable `streams-compression` and
`vm-context` projects. Explicitly enabled Inspector works in Debug and Release within its localhost-only
scope. Packaged-app support remains a separate roadmap item.

Embedded executions use Node's `--disable-warning=ExperimentalWarning` option.
Experimental notices, including the managed ESM linker's VM Modules notice, are
not printed to stderr. Warning events remain available through
`process.on('warning')`; ordinary warnings, deprecations, errors and explicit
`console.error` output are preserved. Terminal `node` invocations keep the upstream
defaults and can use that same option explicitly when needed.

Plugin support status does not change upstream API stability. Since M23.3 the runtime
no longer drives `vm.SourceTextModule`: Node's own CommonJS and ESM loaders run the module
graph (entry, `require`, `import()`, `require(esm)`, `import.meta`, package exports and
imports), and the runtime only registers synchronous `module.registerHooks` (release
candidate in Node 24) to serve the `autojs6:*` runtime modules, the request-level builtin
gate, host-provided module sources and the entry's inline source. Scripts may still use
`vm.Module` / `vm.SourceTextModule` / `vm.SyntheticModule` themselves; they remain
experimental upstream, see the
[Node VM documentation](https://nodejs.org/docs/v24.21.0/api/vm.html#class-vmmodule).
`partial` entries describe remaining implementation or platform limitations and
do not themselves produce `ExperimentalWarning` messages.

The npm corpus now contains 23 packages, including `pirates` and `mock-require`:
extension transforms, `Module._resolveFilename`, cache invalidation and reloading
are checked against desktop Node 24. Native CJS/ESM interop also means that immediate
`require(esm)` cycles report `ERR_REQUIRE_CYCLE_MODULE`, and object-literal
`module.exports` does not promise arbitrary ESM named exports. Use the default
import, or compiler output with explicit `exports.name` assignments.

Each Worker receives a plugin `--require` preload that registers its hooks before
user code, including CJS eval workers. User preload scripts follow `execArgv`
inheritance; arbitrary synchronous hook closures are not automatically copied.
See [Node's customization hooks documentation](https://r2.nodejs.org/docs/latest-v24.x/api/module.html#customization-hooks).
The plugin preload file contains only plugin code; module sources and bridge
state are transferred in memory using Node environment data.

AutoJs6 capabilities use asynchronous host calls, declared in `node.permissions`.
The host applies Android permissions and provider availability. A profile label
such as `pro_compat_opt_in` is diagnostic metadata and does not grant capabilities
or Android permissions. See [HOST-API](../HOST-API.md), [declarations](types/autojs6-node)
and the complete [manual projects](MANUAL-ACCEPTANCE.md).

Node fs accepts ordinary absolute and relative paths within the plugin's Android
file access. The v1.4.0 development media/image/recorder path update similarly uses
Android file access in the host and requires both updated applications; the M18.2
media playback session and `media_store` methods follow the same host-side rules and
need a host build that ships those providers (merged into the host master on
2026-09-17). Archive
transfer and on-demand compiler inputs have separate project-path contracts.

Inspector supports explicit local debugging in Debug/Release through localhost and adb
forward. It is disabled by default; remote listening is unsupported. Java interop reaches
only the classes and members of the host's declarative allowlist, which the host executes
by reflection and publishes as `java.policy` (the runtime keeps no class table of its own);
unrestricted Java reflection remains subject to the host policy. Native addons and
process bindings follow Node and Android.
Inside workers `process.exit()` ends the thread as in Node. The plugin adds no
Worker count cap, startup deadline or nested-worker prohibition. Node's Worker
options, native MessagePort APIs, structured clone and inherited user preloads
remain available. Request `resourceLimits` are defaults that a Worker can override
in either direction; Node validates and enforces them. WorkerPool is an additive
helper with CPU-based default concurrency, no default queue limit and no default
task timeout; callers can set pool-specific limits.

Worker CommonJS/ESM graphs use Node's loaders, including `import()`, `require(esm)`
and JSON import attributes. Request sources, provider-backed worker entries and
compiled TypeScript snapshots remain available without writing those sources to
the preload file. Raw TypeScript still requires host compiler output. The
`autojs6:bridge` module forwards calls through parent MessagePorts to the root
execution's broker, including permission checks, cancellation and timeouts.
Higher-level host facades such as `device` and `ui` remain on the root thread.
Inspector sessions are native when the request enables them; localhost-only open
and the current build requirement remain until M23.5. The request's network gate
also applies in nested workers. Subprocesses retain documented execution limits; bridge calls beyond the
`maxPendingBridgeCalls` window wait in FIFO order instead of failing, and the other
`autojs6:bridge-limits` fields are enforced by the host broker rather than the runtime.
The bridged `autojs6:fetch` / `autojs6:websocket` / `axios` facades pass caller timeouts,
response, message and queue sizes and the HTTP method to the host provider unclamped;
the host policy bounds them and the runtime follows up to 20 redirects like Node. Bridged
fetch response bodies arrive through a file descriptor (`bodyTransport: "pfd"`, mapped
into a Buffer) on hosts that support it, so only `maxResponseBytes` bounds them; fetch
request bodies and WebSocket messages above 256 KiB travel the other way as a descriptor
too (`bodyTransport` / `messageTransport: "pfd"`, written to the session upload directory
and streamed by the host) on hosts that advertise `bridgeRequestBinaryTransport`, so the
host request policy (64 MiB) and `maxMessageBytes` bound them rather than the Binder
transaction size, and a host reply that exceeds the Binder transaction size is reported
at once as `ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED` instead of waiting for the bridge timeout.
Screen capture, OCR and recording have the manual acceptance recorded in
[Roadmap](../../Roadmap.md); physical event receipts remain pending. Android consent
is still required when using those capabilities. A callable facade is not evidence
of a complete Android provider or packaged-app support.

Current implementation metadata is in the embedded
[capability catalog](../../app/src/main/assets/nodejs/node-capability-catalog.json).
Historical profiles are archived under [history](history), and are not current policy.

Since M23.4, Android native addons load through Node's `.node` extension and
`process.dlopen` in both root scripts and workers. Node-API addons must target the
actual process ABI and have Android-compatible dependencies; a desktop binary
for the same CPU is not sufficient. The addon loader owns format/symbol errors,
including `ERR_DLOPEN_FAILED`; the plugin does not impose a signature or hash
allowlist. Binary addon files bypass JavaScript source-provider hooks.

See the [`native-addon` project](../../sample/nodejs/native-addon) and
[NDK build instructions](../../tools/nodejs/addon). The plugin does not include
node-gyp or a device NDK. Native faults affect the runtime slot process and use
its existing crash reporting; worker threads do not add process isolation.

M23.5 makes Inspector activation independent of build type. The host developer
setting is off by default; explicit internal request values override it. Without
permission the native module and url() remain readable, but open and Session
creation are gated. Root scripts and Workers share the localhost/nonblocking
policy, including inspector/promises and ESM imports.

# AutoJs6 Node.js Compatibility Profile

Last reviewed: 2026-09-17. Published plugin: v1.3.0; subsequent local work: v1.5.5.

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
access with `/proc`, `/sys` and `/dev` denied, the native `process` subset, stable
worker channels, the two-slot process pool, and the WASI / native addon decisions;
historical partial / reserved / deferred markers are gone. The sample audit
promoted the ESM, WebAssembly and desktop parity samples, turned both parity
suites into real runners, and `new Worker(code, { eval: true })` now works as
in Node. Module loading follows Android file access like Node (M20.2): `require`,
`import` and `Worker` accept absolute paths, parent-directory targets and `file:`
URLs, with `/proc`, `/sys` and `/dev` still denied; `node_modules` lookup and
package scopes stay anchored to the workspace. An installed package shadows only
the runtime modules that stand in for real npm packages (axios, colors, mime,
nanoid, opencc, undici); AutoJs6 capability facades and the other runtime modules
answer regardless, in `require`, `require.resolve` and `import` alike, and every
bare runtime module name can be imported from ESM (`autojs6:profile` reports the rule
as `moduleResolutionProfile`). Worker messages and fs watchers
follow native Node limits: the 64 KB message / 32-queued caps and the
16-watcher / 64-events-per-second quotas are gone, so only Android memory bounds them.
The scoped fs wrapper also dropped its own option restrictions: stream `fs`, inherited
`fd` / `dest` and arbitrary `flags` options, `watch({ recursive: true })`, async `cp`
filters (`cpSync` keeps Node's `ERR_INVALID_RETURN_VALUE`), absolute, parent-directory
and literal `!` glob patterns with `exclude` arrays, `readableWebStream` `type` /
`encoding`, and the `Stats` / `Dirent` / `Dir` constructors follow native Node 24, so
`filesystemProfile.advancedApis.recursiveWatch` reports `native`. Recursive `readdir` /
`opendir` are native as well (the 4096-entry cap and per-entry realpath checks are gone),
`opendir` returns Node's own lazy `fs.Dir` (`bufferSize`, `encoding` and `recursive` go to
native; only `dir.path` and `parentPath` keep the caller's spelling), `readlink` / `chmod` /
`chown` / `utimes` accept absolute paths, and fs policy errors use two codes only:
`ERR_AUTOJS6_FS_NUL_BYTE` and `ERR_AUTOJS6_FS_PATH_ESCAPE` for the `/proc` / `/sys` /
`/dev` boundary; removing the reach root is Node's decision like any other path. The
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
finished event. `node:sqlite` accepts SQLite `file:` URI strings and the empty temporary
database; ATTACH with a literal filename is checked by SQLite's authorizer against the same
NUL / `/proc` `/sys` `/dev` boundary as the constructor (a bound or computed filename still
throws `ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED`); VACUUM INTO targets pass the same
check through SQLite's internal ATTACH, and directory PRAGMAs are native. `setAuthorizer()`
composes with that check and is reinstalled on open.
Native Node builtins retain their own names: `events` / `node:events` is EventEmitter;
Android event observation is exposed as `autojs6:events`. The runtime links ICU 78 with
English-only locale data (`--with-intl=small-icu`, since v1.5.1): `Intl` exists and
Unicode property escapes parse, while other locales fall back to English unless
`NODE_ICU_DATA` supplies a full ICU data file.

File streams, gzip/deflate/Brotli streams, and native VM script/context execution
are stable plugin capabilities. See the runnable `streams-compression` and
`vm-context` projects. The explicitly enabled Debug Inspector is stable within
its localhost-only scope; Release and packaged-app Inspector remain unavailable.

Embedded executions use Node's `--disable-warning=ExperimentalWarning` option.
Experimental notices, including the managed ESM linker's VM Modules notice, are
not printed to stderr. Warning events remain available through
`process.on('warning')`; ordinary warnings, deprecations, errors and explicit
`console.error` output are preserved. Terminal `node` invocations keep the upstream
defaults and can use that same option explicitly when needed.

Plugin support status does not change upstream API stability. Node 24.21's
`vm.Module`, `vm.SourceTextModule` and `vm.SyntheticModule` are still experimental;
see the [Node VM documentation](https://nodejs.org/docs/v24.21.0/api/vm.html#class-vmmodule).
`partial` entries describe remaining implementation or platform limitations and
do not themselves produce `ExperimentalWarning` messages.

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

The debug Inspector supports explicit local debugging through localhost and adb
forward. It is disabled by default; remote listening is unsupported. Java interop reaches
only the classes and members of the host's declarative allowlist, which the host executes
by reflection and publishes as `java.policy` (the runtime keeps no class table of its own);
native addons, arbitrary private bindings and unrestricted Java reflection remain
unavailable.
Inside workers `process.exit()` ends the thread as in Node and
`process.getBuiltinModule()` follows the worker builtin allowlist, and bare package
specifiers resolve through the workspace `node_modules` as in Node (exports conditions,
main, index, self-reference); dynamic `import()` inside workers goes through the worker
partial ESM loader with `with { type: "json" }` support; nested workers are still
unsupported. Subprocesses retain documented execution limits; bridge calls beyond the
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

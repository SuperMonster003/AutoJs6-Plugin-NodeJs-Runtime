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
package scopes stay anchored to the workspace. Worker messages and fs watchers
follow native Node limits: the 64 KB message / 32-queued caps and the
16-watcher / 64-events-per-second quotas are gone, so only Android memory bounds them.
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
Android file access in the host and requires both updated applications. Archive
transfer and on-demand compiler inputs have separate project-path contracts.

The debug Inspector supports explicit local debugging through localhost and adb
forward. It is disabled by default; remote listening is unsupported. Native addons,
arbitrary private bindings and unrestricted Java reflection remain unavailable.
Inside workers `process.exit()` ends the thread as in Node and
`process.getBuiltinModule()` follows the worker builtin allowlist; nested workers and
bare package specifiers inside workers are still unsupported. Subprocesses and
bridges retain documented execution limits.
Screen capture, OCR and recording have the manual acceptance recorded in
[Roadmap](../../Roadmap.md); physical event receipts remain pending. Android consent
is still required when using those capabilities. A callable facade is not evidence
of a complete Android provider or packaged-app support.

Current implementation metadata is in the embedded
[capability catalog](../../app/src/main/assets/nodejs/node-capability-catalog.json).
Historical profiles are archived under [history](history), and are not current policy.

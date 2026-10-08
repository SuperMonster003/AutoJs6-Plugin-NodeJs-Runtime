# AutoJs6 Node.js Error Catalog

Last reviewed: 2026-10-09.

Errors may originate in either process. The host owns routing, trust, bridge,
permission, workspace, provider, queue, and scheduling failures. The runtime plugin
owns Node/V8/module-loader/runtime failures. Preserve the original error code, name,
message, request ID, and execution ID when reporting a failure.

## Host Routing and Plugin Availability

- **ERR_AUTOJS6_NODE_DISABLED**: this packaged Inrt application does not support the
  external Node.js runtime plugin.
- **ERR_AUTOJS6_NODE_RUNTIME_UNAVAILABLE**: no usable runtime path exists.
- **ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE**: the required plugin cannot be discovered,
  trusted, enabled, bound, or used for the device ABI. It is also the code when a
  runtime slot process dies during an execution; the message then carries the
  system's exit record, for example `LOW_MEMORY (killed by the system low-memory
  killer; rss 2.6 GB)` or `CRASH_NATIVE (SIGABRT; see the logcat tombstone)`, and
  the result carries a `slotExit` Bundle (Android 11+; older systems report that
  the reason is unavailable).
- **ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH**: host and plugin contract ranges do
  not overlap.
- **ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST**: an in-flight plugin execution was lost.
- **ERR_AUTOJS6_NODE_PLUGIN_BUSY**, **ERR_AUTOJS6_NODE_ENGINE_BUSY**: an explicitly configured queue limit is full or an execution ID is duplicated.
  Default pool waiting is unlimited.
- **ERR_AUTOJS6_NODE_QUEUE_FULL**, **ERR_AUTOJS6_NODE_QUEUE_TIMEOUT**,
  **ERR_AUTOJS6_NODE_QUEUE_CANCELLED**: queue policy ended the request.

## Bridge and Capability

- **ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED**: the project did not declare the
  required capability.
- **ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED**: Android/host policy denied authority.
- **ERR_AUTOJS6_BRIDGE_INVALID_REQUEST**: malformed or out-of-contract request, including a
  descriptor upload whose file or byte count does not match the request (M20.2 batch 14).
- **ERR_AUTOJS6_BRIDGE_CANCELLED**, **ERR_AUTOJS6_BRIDGE_TIMEOUT**: terminal
  cancellation or timeout.
- **ERR_AUTOJS6_BRIDGE_PROCESS_DEAD**: the peer process died.
- **ERR_AUTOJS6_BRIDGE_RATE_LIMITED**, **ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT**:
  a host-configured request/resource budget was exceeded. The runtime itself no longer
  rejects calls for the in-flight window (excess calls wait), image handle counts, fetch
  concurrency or WebSocket connection counts.
- **ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED**: a host provider failed after validation, or the
  host reply exceeded the Binder transaction size (hosts with the M20.2 batch-13 broker
  report this at once instead of letting the call run into the bridge timeout).

## Workspace, Modules, and Policy

Since M23.1 (2026-10-07) `fs`, `fs/promises` and `node:sqlite` are Node's own modules:
filesystem failures carry Node/Android codes such as ENOENT, EACCES or
ERR_INVALID_ARG_VALUE (NUL bytes), and `/proc`, `/sys`, `/dev` follow Android like every
other path. The scoped fs wrapper and its policy codes are retired and no longer
produced by fs: `ERR_AUTOJS6_FS_NUL_BYTE`, `ERR_AUTOJS6_FS_PATH_ESCAPE`,
`ERR_AUTOJS6_FS_SCOPED_PATH`. The catalog keeps retired names separately from current error constants.
Since M23.3 (2026-10-08) Node's own CommonJS / ESM loaders run the module graph and the
runtime only registers `module.registerHooks`, so module failures carry Node's codes
(`MODULE_NOT_FOUND`, `ERR_MODULE_NOT_FOUND`, `ERR_PACKAGE_PATH_NOT_EXPORTED`,
`ERR_UNSUPPORTED_ESM_URL_SCHEME`, `ERR_IMPORT_ATTRIBUTE_*`, `ERR_REQUIRE_ASYNC_MODULE`,
`ERR_REQUIRE_CYCLE_MODULE`); the loader's own codes are retired and never produced
any more: `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`, `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`,
`ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED`, `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`,
`ERR_AUTOJS6_MODULE_NOT_FOUND` (main thread) and the loader uses of `ERR_AUTOJS6_FS_NUL_BYTE` /
`ERR_AUTOJS6_FS_PATH_ESCAPE`. `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` remains in use for raw
TypeScript entries, request-attached sources and modules reached through require/import.
TypeScript modules must be mapped to host compiler output; the snapshot codes
`ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND` / `_AMBIGUOUS` still apply. Workers use the same request-level builtin gate codes; host bridge errors retain
the root broker's codes through the parent MessagePort.
`ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED`
and `ERR_AUTOJS6_SQLITE_PATH_UNSUPPORTED` are retired: ATTACH (literal, bound or computed),
VACUUM INTO and directory PRAGMAs are plain SQLite. Workspace transfer and
compiler module providers have their own relative-path validation. Module-source providers use
**ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_*** for invalid, denied, cancelled, timed-out,
or failed requests; `ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED` now only comes from the
provider transport's shared protocol sizes, the runtime's own module-source budgets are
gone. Feature boundaries use specific codes for request-gated builtins, network, workers,
or Java interop. `ERR_AUTOJS6_BUILTIN_DISABLED` is raised for unpermitted
Inspector activation/Session creation or a builtin the request gates off (`worker_threads`, `child_process`, the raw
network modules when `rawNodeNetworkModulesEnabled=false`); every other Node builtin,
`wasi` included, loads as Node exports it. M23.4 retires `ERR_AUTOJS6_NATIVE_ADDON_DISABLED` and
`ERR_AUTOJS6_PROCESS_API_DISABLED`: `process.dlopen` and `.node` loading are native,
including inside workers. Wrong-ABI or invalid binaries and missing native
dependencies report Node/Android errors such as `ERR_DLOPEN_FAILED`. A missing
addon still follows Node's module-resolution error behavior.

Java interop reports the host's decisions with **ERR_AUTOJS6_JAVA_*** codes (M20.2 batch 15):
`ERR_AUTOJS6_JAVA_INTEROP_DISABLED` when the capability is off for the request,
`ERR_AUTOJS6_JAVA_CLASS_DENIED` for classes outside the host allowlist (including results
whose class is not listed), `ERR_AUTOJS6_JAVA_REFLECTION_DENIED` for `java.lang.reflect`,
`java.lang.invoke` and `kotlin.reflect`, `ERR_AUTOJS6_JAVA_METHOD_DENIED` for members the
table does not name, arguments no public overload accepts, or released handles, and
`ERR_AUTOJS6_JAVA_CALL_FAILED` when the admitted member itself threw (the Java exception
class and message are in the error message). The runtime no longer keeps its own class
table; `java.policy` is the table the host published.

Compiler-backed Node TypeScript projects may request provider-v3 compilation for an
exact runtime-created `.ts/.mts/.cts` file. **ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED**
means that the dynamic source produced TypeScript error diagnostics or no exact emitted
module; preserve its file, TS code, line, and column from the message. Snapshot
not-found and ambiguous codes remain authoritative for old providers, missing targets,
extensionless collisions, and candidates that cannot be admitted safely.

Historical **ERR_AUTOJS6_EMBEDDED_NODE_*** codes can still appear from an older plugin
or compatibility payload. They are legacy wire names, not evidence that the current
host contains an embedded runtime.

The host constant inventory is **NodeRuntimeErrorCodes.kt**. Runtime-plugin-specific
codes and JavaScript error shaping are authoritative in the plugin repository.

Since M23.3's worker migration, `ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED` and
`ERR_AUTOJS6_WORKER_TIMEOUT` are retired: the plugin imposes neither a Worker count
cap nor a startup deadline. `WORKER_POOL_*` errors still describe caller-selected
pool limits, task cancellation and pool lifecycle. Native Worker constructor,
transfer-list and structured-clone failures use Node's own errors.

Since M23.5, importing Inspector is allowed even without permission; url() then
returns undefined. Opening an endpoint or constructing a Session without the
explicit request still reports `ERR_AUTOJS6_BUILTIN_DISABLED`. Remote addresses
and blocking waits report `ERR_INVALID_ARG_VALUE` in Debug and Release alike.

M23.6 retires ERR_AUTOJS6_NPM_CLI_DENIED and ERR_AUTOJS6_NPM_REGISTRY_DENIED.
Bundled CLI extraction/setup failures use ERR_AUTOJS6_NPM_CLI_SETUP_FAILED.
Command failures preserve npm stdout/stderr and its reported code (for example
ENOTCACHED, E404 or ECONNREFUSED), with exitCode/signal on the rejected error.
Spawn and AbortSignal errors come from Node. The object-based host local-store
mutation errors and permission requirements are unchanged.

## Retired runtime policy errors in 1.6.0

| Former codes | Current behavior |
|---|---|
| ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED / FS_REALPATH_ESCAPE / FS_SYMLINK_ESCAPE / FS_NUL_BYTE / FS_PATH_ESCAPE / FS_SCOPED_PATH | Native Node/Android path semantics and errors |
| ERR_AUTOJS6_FS_WATCH_LIMIT / FS_WATCH_RATE_LIMITED | Native watcher behavior |
| ERR_AUTOJS6_NATIVE_ADDON_DISABLED / PROCESS_API_DISABLED | Native Android addon/process operations |
| ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED / WORKER_TIMEOUT | Native Worker behavior; caller-selected WorkerPool limits remain |
| ERR_AUTOJS6_NPM_CLI_DENIED / NPM_REGISTRY_DENIED | Bundled npm CLI with native npm errors; setup failures use NPM_CLI_SETUP_FAILED |
| ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED / REQUIRE_ESM_UNSUPPORTED / DYNAMIC_IMPORT_UNSUPPORTED | Native Node module loading errors |

Names shortened after a slash share the ERR_AUTOJS6_ prefix. Old peers or historical
reports can contain these names. They are not current ordinary builtin policy failures.
Current host broker and workspace/provider transport errors retain their own contracts.

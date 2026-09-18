# AutoJs6 Node.js Error Catalog

Last reviewed: 2026-09-10.

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
- **ERR_AUTOJS6_NODE_PLUGIN_BUSY**, **ERR_AUTOJS6_NODE_ENGINE_BUSY**: bounded
  concurrency rejected the request.
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

Native filesystem failures normally retain Node/Android codes such as ENOENT
or EACCES. Remaining runtime policy errors use two **ERR_AUTOJS6_FS_*** codes:
`ERR_AUTOJS6_FS_NUL_BYTE` for NUL bytes and `ERR_AUTOJS6_FS_PATH_ESCAPE` for the `/proc`,
`/sys`, `/dev` hard boundary (shared with the module loader and worker fs). fs no longer
produces `ERR_AUTOJS6_FS_SCOPED_PATH` (removing the reach root is Node's decision; the
code remains only for a non-local `worker_threads` script name), and the other `FS_*`
names still listed in catalog 1.5.5 are retired and no longer produced. Workspace transfer and
compiler module providers have their own relative-path validation. Module-source providers use
**ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_*** for invalid, denied, cancelled, timed-out,
or failed requests; `ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED` now only comes from the
provider transport's shared protocol sizes, the runtime's own module-source budgets are
gone. `ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED` is now limited to ATTACH with a
bound or computed filename; `ERR_AUTOJS6_SQLITE_PATH_UNSUPPORTED` is retired. Feature
boundaries use specific codes for disabled builtins,
network, process APIs, workers, native addons, or Java interop.

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

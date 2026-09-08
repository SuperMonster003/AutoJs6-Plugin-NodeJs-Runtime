# v1.2.0

###### 2026/08/29

* `Feature` Added `capabilities()` to the `mediainfo` facade and explicit plugin snapshot v1/v2 selection to its callable entry and `read()`; omitting schema continues to return the existing host-owned Node v1 snapshot
* `Feature` Added module-source provider v3 for on-demand compilation of runtime-created `.ts/.mts/.cts` through byte- and SHA-256-bound PFDs, with an independent 30 s compilation budget, stable path/symlink/ambiguity rejection, and host TypeScript diagnostics plus Source Map stack mapping
* `Feature` Enabled desktop-like filesystem access within Android app permissions while keeping `/proc`, `/sys`, and `/dev` denied
* `Feature` Added `accessibility.swipe` and `accessibility.gesture` behind the dedicated `accessibility.gesture` capability
* `Feature` Added script timeouts covering queueing and execution, returning `ERR_AUTOJS6_SCRIPT_TIMEOUT`; scripts without a timeout can still run indefinitely
* `Feature` Enabled `dgram` (UDP) and `http2` by default, with an explicit disabled error for `trace_events`
* `Feature` Added explicitly enabled local `inspector` debugging in Debug builds, listening only on localhost and connecting through `adb forward`
* `Fix` Made raw TypeScript fail closed unless the host supplies compiler output, mapped snapshot dynamic imports, and normalized generated/imported stack frames
* `Fix` Replaced the snapshot-based partial ESM adapter with the V8 native linker, fixing mutable exports that failed to update through cyclic re-exports
* `Fix` Fixed ESM imports of AutoJs6 compatibility facades and missing TypeScript suffix probes while preserving installed npm package precedence
* `Improvement` Removed the legacy regex-based TypeScript erasure fallback and its request switch; raw `.ts/.mts/.cts` now always require host compiler output
* `Improvement` Aligned the v2 host/plugin contract, capability manifests, and plugin-only runtime responsibility boundary
* `Improvement` Centralized Node.js examples, TypeScript declarations, the project wizard, runtime defaults, and host-alignment checks in the plugin repository, removing host-side Gradle switches and duplicate development assets
* `Improvement` Expanded verified npm coverage to 15 packages, adding axios, express, and the ESM-only packages nanoid, p-limit, and yocto-queue
* `Improvement` Made the `executionMode` request field authoritative for lifecycle mode and deprecated the no-op `runtimeAdapter` field

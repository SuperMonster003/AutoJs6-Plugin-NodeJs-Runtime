# AutoJs6 Node.js File Access and Android Permissions

Last reviewed: 2026-10-08.

Node/V8 runs in the runtime plugin's Android application. Automation providers run
in the host application. These applications have different UIDs: `fs` accesses
files as the plugin, while `mediainfo`, image file methods, the recorder, media
playback and MediaStore import/export access files as the host. A path readable by one application may be denied to the other.
Android permissions, storage access and private application directories apply.

Since M23.1 (2026-10-07) `fs` is Node's own module: there is no wrapper, no path
validation and no `/proc`, `/sys`, `/dev` denial. Whatever Android lets the plugin
process open, Node opens; whatever Android refuses comes back as a plain Node
error. The execution enters its working directory with a real `process.chdir`, so
relative paths, `process.cwd()` and `process.chdir()` behave as on desktop Node.

Ordinary Node fs paths may be absolute or relative. The v1.4.0 development update
also accepts absolute paths, parents and symlinks for host media/image/recording
files; matching host code is required. Empty and NUL paths remain invalid, and
these methods take file paths rather than content URIs. `media_store` compiles
structured JSON filters into parameterised MediaStore selections, only modifies
items AutoJs6 created on Android 10+, and never passes cursors, Uri objects or
descriptors across the bridge. `media_store` compiles
structured JSON filters into parameterised MediaStore selections, only modifies
items AutoJs6 created on Android 10+, and never passes cursors, Uri objects or
descriptors across the bridge. Permission failures from
Android are reported to the script. The detailed contract is in
[HOST-API](../HOST-API.md).

Project archives and output writeback are a separate transport. Their entry paths,
size and count are checked when crossing application boundaries. Compiler-backed
module providers select project inputs and check byte counts, file identities and
generated module paths. These transport rules do not define ordinary fs access.

M23.3 replaces the custom CJS resolver and `vm.SourceTextModule` graph with Node's
native loaders. Synchronous `module.registerHooks` supplies request-attached and
provider-owned sources, compiler output, runtime modules and compatibility shims.
Raw TypeScript still goes through the host Compiler; loader migration does not
enable Node type stripping as a fallback. Existing request-level builtin switches
and worker lifecycle controls remain until their respective Roadmap items finish.

Host capabilities currently require `node.permissions`, then Android permissions
and an available provider. Screen capture requires the system consent flow;
recording requires microphone permission and a visible foreground service. Profile
selection metadata cannot replace either declaration or Android consent. See
[the manual guide](MANUAL-ACCEPTANCE.md) for runnable projects.

Execution cancellation and resource cleanup remain necessary for usable lifecycle
behavior. Worker message size / queue caps and fs watcher count / event-rate quotas
were removed in v1.5.5, the sensitive fs roots (`/proc`, `/sys`, `/dev`) in M23.1 and the
builtin allowlist / denylist, the process API guards (abort, kill, umask, binding, report,
features, POSIX identity calls) and the os / module facades in M23.2/M23.3. M23.3
also removes Worker count and startup caps, nested-worker denial and resource-limit
ceilings imposed by the plugin. WorkerPool retains only caller-selected scheduling
limits; its queue and task duration are unbounded by default. Node and Android
manage worker memory and thread resources.

Worker host calls use a private parent MessagePort. All hops reach the root
execution's existing capability broker, so declarations and Android permissions
apply as before. This avoids registering process-global JNI callbacks separately
for each isolate. The preload file contains plugin code only; request sources and
bridge ports are cloned/transferred in memory.

M23.4 restores Node's native addon loader in both main scripts and workers, with
no plugin-specific addon signature/ABI allowlist. Android's UID, SELinux and
linker namespace rules still govern library access. Binary `.node` files must be
Android-compatible and match the process architecture; dependencies and page
alignment must also match the device. Addon code runs in the runtime slot
process, and native faults are reported by the existing process-exit/crash path.
M23.5 allows Inspector in Debug and signed Release after an explicit request.
The host developer setting defaults off. Both Inspector modules retain native
exports with activation/Session entry points gated; url() is readable while
inactive. Root scripts and Workers bind only 127.0.0.1, without blocking waits.
Desktop clients use ADB forwarding and see the native Node protocol data.

Plugin discovery, caller trust, Binder ownership and grants to remote MCP callers
remain host integration responsibilities. Shared Android providers do not make a
Node script's settings into MCP authorization. The host has no embedded runtime;
legacy `backend="embedded"` and `embedded_script.*` are compatibility wire names.

M23.6 executes the bundled npm launcher under the runtime plugin UID. Registry,
proxy and lifecycle configuration follow npm/.npmrc, with explicit call options
when requested. The plugin does not add signature or hash allowlists for registry
packages; npm owns their resolution, lockfile and cache integrity. The existing
CLI asset lock verifies only the plugin-owned npm/corepack archive at extraction.
Lifecycle scripts have that child process's Android permissions and filesystem
access. The old host local-store path retains its own explicit mutation contract.

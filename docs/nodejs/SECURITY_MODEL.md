# AutoJs6 Node.js Runtime: Android Authority and Host Integration

Last reviewed: 2026-10-09. Applies to the local 1.6.0 release candidate.

## Ordinary Node access

Android is the operating-system permission boundary for Node code. The runtime
plugin uses its own application UID, SELinux context, storage permissions and
linker namespace. fs, fs/promises, node:sqlite, native networking, addons and
child processes use Node implementations within that Android environment.
The runtime adds no filesystem sandbox, sensitive-directory denylist, addon
signature allowlist or second memory quota enforced instead of Android lmkd.

Absolute paths, parent directories, symlinks, /proc, /sys and /dev follow Android
access decisions. Node reports its own failures such as EACCES or ENOENT. The
execution enters its working directory with a real chdir. This does not promise
that every desktop facility exists on Android: binaries, dependencies and
executable locations must work on this OS and ABI.

## Explicit runtime options and lifecycle

Requests can disable raw networking, child processes or Worker threads.
Inspector activation and Sessions require an explicit request, work in Debug
and Release, and bind to 127.0.0.1 for ADB forwarding. Blocking debugger waits
remain unavailable. Profile names and project metadata grant no Android authority.

The pool defaults to two slots and unlimited FIFO waiting. Users may select
1-8 slots and a queue limit. Cancellation, explicit timeouts and optional
idleExitMs control lifecycle. Android exit records and Node crash reports explain
unexpected slot deaths; the runtime does not replay scripts automatically.

## Host capabilities and cross-process transport

Automation providers run under the host UID. A path readable by the runtime
plugin may be unreadable by the host, or vice versa. Host operations still
require their node.permissions declarations, an available provider and the
relevant Android authorization. Screen capture uses MediaProjection consent;
microphone recording uses permission and a visible foreground service. These
host contracts are distinct from ordinary native Node access.

Workspace ZIPs, source providers and binary PFD messages cross process boundaries.
Entry names, file identities, byte counts and output receipts remain validated.
The workspace root is a transport location, not a Node fs sandbox. Output archives
do not transfer symlinks; npm projects needing bin entries copied back should use
bin-links=false. Android decides whether app-data executables can run. Raw
TypeScript is compiled by the host Compiler before dispatch, without a stripping
fallback in the runtime.

## npm, addons and caches

Bundled npm follows npm/.npmrc registry, proxy, lockfile and lifecycle settings.
Lifecycle scripts run with the plugin UID. The CLI archive digest verifies the
plugin-owned npm/corepack distribution, not a registry package allowlist.
Object-based local-store operations retain their host mutation contract.

Android-compatible .node addons load through Node in root scripts and Workers.
Node/Android report ABI, symbol and dependency errors. Native code shares its
runtime process authority; a Worker is not process isolation.

Portable module caching defaults on for workspaces and Workers. Its default
storage is plugin-private; Node handles source/version invalidation. Cache failure
does not prevent execution. NODE_DISABLE_COMPILE_CACHE opts out and
NODE_COMPILE_CACHE chooses an accessible directory.

Caller trust and Binder access remain host integration concerns. RUNTIME and INFO
require org.autojs.permission.PLUGIN; the current AutoJs6 host declares it as a
signature permission. Independently signed packaged apps remain M24 work and are
not advertised here. Remote MCP authorization is also a host responsibility.

See [HOST-API](../HOST-API.md), [compatibility](COMPATIBILITY_PROFILE.md) and
[error ownership](ERROR_CATALOG.md) for the API contracts.

# AutoJs6 Node.js File Access and Android Permissions

Last reviewed: 2026-09-10.

Node/V8 runs in the runtime plugin's Android application. Automation providers run
in the host application. These applications have different UIDs: `fs` accesses
files as the plugin, while `mediainfo`, image file methods and the recorder access
files as the host. A path readable by one application may be denied to the other.
Android permissions, storage access and private application directories apply.

Ordinary Node fs paths may be absolute or relative. The v1.4.0 development update
also accepts absolute paths, parents and symlinks for host media/image/recording
files; matching host code is required. Empty and NUL paths remain invalid, and
these methods take file paths rather than content URIs. Permission failures from
Android are reported to the script. The detailed contract is in
[HOST-API](../HOST-API.md).

Project archives and output writeback are a separate transport. Their entry paths,
size and count are checked when crossing application boundaries. Compiler-backed
module providers select project inputs and check byte counts, file identities and
generated module paths. These transport rules do not define ordinary fs access.

Host capabilities currently require `node.permissions`, then Android permissions
and an available provider. Screen capture requires the system consent flow;
recording requires microphone permission and a visible foreground service. Profile
selection metadata cannot replace either declaration or Android consent. See
[the manual guide](MANUAL-ACCEPTANCE.md) for runnable projects.

Execution cancellation and resource cleanup remain necessary for usable lifecycle
behavior. Worker message size / queue caps and fs watcher count / event-rate quotas
were removed in v1.5.5; remaining custom restrictions include sensitive fs roots
(`/proc`, `/sys`, `/dev`), the worker-count default (CPU parallelism, adjustable per
request), Binder transport limits, the lifecycle-mapped `process.exit` / denied
`process.kill` / `process.abort` on the shared runtime process, nested workers and bare
package specifiers inside workers, and Java interop denial inside workers. Their gradual reduction,
along with capability defaults, is tracked by [M18-M20](../../Roadmap.md). No claim is made that all custom
restrictions have already been removed.

Plugin discovery, caller trust, Binder ownership and grants to remote MCP callers
remain host integration responsibilities. Shared Android providers do not make a
Node script's settings into MCP authorization. The host has no embedded runtime;
legacy `backend="embedded"` and `embedded_script.*` are compatibility wire names.

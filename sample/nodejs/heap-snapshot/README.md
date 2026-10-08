# heap-snapshot

Collects a heap snapshot through a native Inspector Session. Supported in Debug and Release builds.

Enable **Allow Inspector for Node.js scripts** under the host developer options
(long-press the app icon on the About page), then run the project. The setting is
off by default. A direct runtime caller can instead set `inspectorEnabled=true`.
Without permission, activation/Session creation reports `ERR_AUTOJS6_BUILTIN_DISABLED`;
`inspector.url()` can still be queried and returns `undefined`.

Desktop DevTools connects through `adb forward` to the device loopback port.
Listeners bind only to `127.0.0.1`; blocking waits are unavailable. Profiles and
source locations follow Node's native Inspector protocol. See
[HOST-API](../../../docs/HOST-API.md#本地-inspector-debug--release).

Expected output is listed in `expected-output.txt`.

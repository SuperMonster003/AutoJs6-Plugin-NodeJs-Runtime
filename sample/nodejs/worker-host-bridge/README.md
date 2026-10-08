# worker-host-bridge

A native Node Worker calls the AutoJs6 host's `device.info` method through
`require("autojs6:bridge").callAutoJs(...)`, then returns the device model and
Android API level to the main script.

Run this project from AutoJs6 with the Node.js Runtime plugin installed. The
project declares `node.permissions: ["device"]`; a live host device provider is
required. A minimal direct-run request without a host provider does not supply
device information.

Worker calls travel through a private parent MessagePort to the existing host
broker. Nested workers use the same route and the root execution's permissions.
The optional fourth argument accepts `timeoutMs` and `signal` (AbortSignal).
Arguments and results use Node's structured clone across threads. Use the bridge
module inside workers; the higher-level `device` facade remains on the main
script thread.

Expected output includes the marker in `expected-output.txt`; the device line
depends on the connected Android device.

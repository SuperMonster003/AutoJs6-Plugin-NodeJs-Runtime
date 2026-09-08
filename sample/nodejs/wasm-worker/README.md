# wasm-worker

Compiles and instantiates a real WebAssembly module inside a native Node worker,
then sends the result of `add(19, 23)` to the main thread. The worker exits after
the computation. This uses the default worker policy and needs no host provider.

Worker count and optional resource limits apply as described in HOST-API.
AutoJs bridge modules and inspector remain unavailable inside workers.

Expected output is listed in `expected-output.txt`.

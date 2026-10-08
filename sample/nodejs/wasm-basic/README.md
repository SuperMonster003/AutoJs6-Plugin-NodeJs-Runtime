# wasm-basic

Compiles and instantiates a tiny WebAssembly module with the `WebAssembly` API
that V8 ships inside Node, then calls its exported `add` function, and starts a
minimal WASI module through Node's own `node:wasi` (M23.2).

- Capabilities: `WebAssembly.compile()`, `WebAssembly.instantiate()`, `node:wasi` (`WASI`, `getImportObject()`, `start()`)
- Expected provider: V8 WebAssembly in Node
- Packaged support: not applicable
- Security limitations: `node:wasi` is Node's own module since M23.2 and its preopens follow Android file access like `fs`

Expected output is listed in `expected-output.txt`.

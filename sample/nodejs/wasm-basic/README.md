# wasm-basic

Compiles and instantiates a tiny WebAssembly module with the `WebAssembly` API
that V8 ships inside Node, then calls its exported `add` function.

- Capabilities: `WebAssembly.compile()` and `WebAssembly.instantiate()`
- Expected provider: V8 WebAssembly in Node
- Packaged support: not applicable
- Security limitations: native addons remain unsupported by policy; `node:wasi` is denied by decision, pure WebAssembly imports are the supported path

Expected output is listed in `expected-output.txt`.

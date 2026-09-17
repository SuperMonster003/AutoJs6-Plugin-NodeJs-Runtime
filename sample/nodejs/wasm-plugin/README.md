# wasm-plugin

A checked local JS plugin whose `add` export compiles and instantiates a small
WebAssembly module with V8. There is no separate WASM plugin kind: the plugin
manager loads ordinary JS plugins, and WebAssembly is just what the plugin code
runs.

- Capabilities: `plugins`, `plugin.org.example.wasm_math`, `WebAssembly`
- Expected provider: JS plugin manager plus V8 WebAssembly in Node
- Packaged support: not applicable
- Security limitations: plugin code runs with the project's bridge permissions only; native addons remain unsupported by policy

Expected output is listed in `expected-output.txt`.

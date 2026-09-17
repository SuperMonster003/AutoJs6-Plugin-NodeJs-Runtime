# require-esm

CommonJS entry that loads a local ESM module with `require()`, which Node 24
supports natively for synchronous module graphs.

- Capabilities: CommonJS entry, local ESM module
- Expected provider: Node 24 `require(esm)`
- Packaged support: not applicable
- Security limitations: synchronous ESM graphs only (no top-level await), as in Node; disabled builtins remain denied

Expected output is listed in `expected-output.txt`.

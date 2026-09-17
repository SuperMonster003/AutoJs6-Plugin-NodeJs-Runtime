# compile-cache

Shows why Node's on-disk module compile cache is not available in this runtime
and that in-memory CommonJS module caching still behaves as in Node.

Project modules arrive through the workspace transport and are compiled by the
runtime's own loader, so `module.enableCompileCache()` is not exposed and
`autojs6:profile` reports `compileCache.customLoader=true`. Repeated `require()`
calls return the same module instance.

- Capabilities: CommonJS module cache
- Expected provider: none (not applicable)
- Packaged support: not applicable
- Security limitations: `module.enableCompileCache()` is not exposed; native addons remain unsupported by policy

Expected output is listed in `expected-output.txt`.

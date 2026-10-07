# compile-cache

Shows the state of Node's on-disk module compile cache in this runtime and that
in-memory CommonJS module caching behaves as in Node.

Since M23.2 `module` is Node's own builtin, so `module.enableCompileCache()` is
exposed. Project modules still arrive through the workspace transport and are
compiled by the runtime's own loader (`autojs6:profile` reports
`compileCache.customLoader=true`) until M23.3 hands them to Node's loader; M23.8
then enables the on-disk cache for the workspace. Repeated `require()` calls
return the same module instance.

- Capabilities: CommonJS module cache
- Expected provider: none (not applicable)
- Packaged support: not applicable
- Security limitations: none for the cache itself; native addons stay denied until M23.4

Expected output is listed in `expected-output.txt`.

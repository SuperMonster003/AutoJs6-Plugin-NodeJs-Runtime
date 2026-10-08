# compile-cache

Shows Node's native on-disk module compile cache and in-memory CommonJS module identity.

The runtime enables `module.enableCompileCache` by default before loading project modules.
Its portable cache is stored in the plugin's private cache directory and survives the
per-execution workspace copies. Node handles source/version invalidation and atomic writes.
Workers inherit the cache. Repeated `require()` calls within one execution still return
the same module instance.

Set `NODE_DISABLE_COMPILE_CACHE=1` to opt out, or `NODE_COMPILE_CACHE` to choose a directory
that the plugin can access. Cache setup failures do not prevent execution. Node does not
expose cache hit/miss counters; `autojs6:profile.compileCache` reports the enabled state
and `customLoader=false`, without claiming hit rates.

- Capabilities: native CJS/ESM compile cache and CommonJS module cache
- Expected provider: none
- Packaged support: not applicable

Expected output is listed in `expected-output.txt` and assumes the default enabled cache.

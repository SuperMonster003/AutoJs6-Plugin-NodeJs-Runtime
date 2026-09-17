# desktop-parity-suite

Desktop Node parity catalog. `main.cjs` runs every snippet in order; each
snippet prints its own result lines and an explicit skip code when something
is unavailable, so the output documents what this runtime really does.

| Example | Snippet | Outcome on this runtime |
| --- | --- | --- |
| ESM loader | `snippets/esm-loader.cjs` | Node's native ESM linker imports the local fixture |
| Worker threads | `snippets/worker-threads.cjs` | native `worker_threads` are enabled by default; the worker replies 42 |
| HTTP/HTTPS | `snippets/http-https.cjs` | native `http`/`https` are available; the snippet only checks their shape |
| Raw network | `snippets/raw-network.cjs` | native `http`/`https`/`net`/`tls`/`dns` are available; `dgram` and `http2` need the `raw_network` capability |
| Advanced fs | `snippets/fs-advanced.cjs` | FileHandle, `fs.watch` and `fs/promises` work inside Android file access |
| WASI | `snippets/wasi.cjs` | `node:wasi` is denied by decision; the snippet prints the denial code, pure WebAssembly is available |
| Inspector | `snippets/inspector.cjs` | needs an explicit Debug host request; without it the snippet prints the denial code |
| npm dependency | `snippets/npm-dependency.cjs` | a checked-in pure JavaScript dependency loads through `require()`; registry installs happen in the host terminal |

Every snippet can also run on its own (`node snippets/<name>.cjs` semantics:
it exports `run()` and executes when it is the entry).

`smoke.cjs` is the focused subset used by the host smoke run: local ESM import,
`http`/`https` shape, the checked-in `vendor/pure-math` dependency and the
FileHandle plus non-persistent watcher path. It does not dispatch network I/O.

Expected output is listed in `expected-output.txt`.

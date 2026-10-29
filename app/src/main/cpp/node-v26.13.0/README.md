# Node.js 26.13.0 headers

These are the C/C++ headers used by the Android bridge from the official
[Node.js 26.13.0 header archive](https://nodejs.org/dist/v26.13.0/node-v26.13.0-headers.tar.xz).
The archive SHA-256 is
`519e269c3e78bf513fdf53d2ab114b763173a7c9424bd477d4e182d782ea366f`.
The unused OpenSSL header subtree and the node-gyp `common.gypi` / `config.gypi`
files are omitted. Included headers retain the upstream bytes and copyright
notices; the complete upstream license from the
[Node.js 26.13.0 source archive](https://nodejs.org/dist/v26.13.0/node-v26.13.0.tar.xz)
is in [LICENSE](LICENSE).

CMake selects this directory together with `node_runtime_adapter_26_13.cpp`
when `AUTOJS6_NODE_RUNTIME_SLOT=node26_13`, the default runtime slot.
Source libraries and device checks are recorded in Roadmap M25.2.

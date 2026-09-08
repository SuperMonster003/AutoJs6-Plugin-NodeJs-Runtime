# Node.js 24.20.0 headers

These are the 122 C/C++ headers used by the Android bridge from the official
[Node.js 24.20.0 header archive](https://nodejs.org/dist/v24.20.0/node-v24.20.0-headers.tar.xz).
The archive SHA-256 is
`cb5420ff135b64fdcb3d050223146ecc3e257c413abe915604d48e589498ae47`.
The unused OpenSSL header subtree is omitted. Included headers retain the
upstream bytes and copyright notices; the complete upstream license is in
[LICENSE](LICENSE).

CMake selects this directory together with `node_runtime_adapter_24_20.cpp`
when `AUTOJS6_NODE_RUNTIME_SLOT=node24_20`. The default remains `node24_5`
until the three source-built Android libraries pass the Roadmap M17 checks.
Adding these headers does not change the embedded runtime version.

# Node.js 24.21.0 headers

These are the C/C++ headers used by the Android bridge from the official
[Node.js 24.21.0 header archive](https://nodejs.org/dist/v24.21.0/node-v24.21.0-headers.tar.xz).
The archive SHA-256 is
`7497abc2fac1d332fae580046b6b39d0de283dfaf267207a32b59753739c1517`.
The unused OpenSSL header subtree is omitted. Included headers retain the
upstream bytes and copyright notices; the complete upstream license is in
[LICENSE](LICENSE).

CMake selects this directory together with `node_runtime_adapter_24_21.cpp`
when `AUTOJS6_NODE_RUNTIME_SLOT=node24_21`, the default runtime slot.
Source libraries and device checks are recorded in Roadmap M17.2.

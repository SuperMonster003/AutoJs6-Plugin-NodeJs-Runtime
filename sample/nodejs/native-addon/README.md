# native-addon

Loads a real Android Node-API addon, calls `hello()` and checks `add(20, 22) === 42`.
The loader selects an Android ABI using the Node process architecture, which also
handles a 32-bit plugin installed on a 64-bit device.

Import and run the complete project from AutoJs6. Keep the `prebuilds` directory
with the project and materialize its Git LFS files in a source checkout. The addon
is built for this plugin's Node runtime and Android API 24 or later.

The source and offline rebuild instructions are in
[`tools/nodejs/addon`](../../../tools/nodejs/addon). Addons must target Android and
the actual process ABI; desktop Linux, macOS and Windows binaries are not Android
binaries. Node reports wrong ABI, missing dependencies and invalid modules through
its normal `ERR_DLOPEN_FAILED` errors. Bundled npm includes node-gyp JavaScript, but no device NDK/Python build environment.

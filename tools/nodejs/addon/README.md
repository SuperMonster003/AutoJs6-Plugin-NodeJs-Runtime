# Android Node-API sample

This directory builds `sample/nodejs/native-addon/prebuilds/<abi>/hello.node`
for arm64-v8a, armeabi-v7a and x86_64. Use an installed Android NDK and CMake/Ninja:

```text
py tools/nodejs/addon/build-addon.py --ndk <ndk-directory> --cmake <cmake-executable> --ninja <ninja-executable>
```

The build is offline. It uses this repository's Node 26.13 headers and each ABI's
materialized `libnode.so`, producing a `DT_NEEDED` dependency on `libnode.so`.
No Node binaries from another repository are used. Build intermediates live in
`app/build/native-addon`; checked-in `.node` files use Git LFS.

The addon uses Node-API 8, stores no environment-specific handles globally, and
can initialize in separate workers and repeated script executions. See the
[Node-API documentation](https://r2.nodejs.org/docs/latest-v26.x/api/n-api.html).
The linker uses `-Wl,-z,max-page-size=16384` for Android's
[16 KB page support](https://developer.android.com/guide/practices/page-sizes).

This is a cross-compilation example. Bundled npm includes node-gyp JavaScript, but the runtime plugin does not
provide CMake, an NDK or Python on the device. Other addons need Android-compatible prebuilt
binaries or a corresponding cross-build, including compatible native dependencies.

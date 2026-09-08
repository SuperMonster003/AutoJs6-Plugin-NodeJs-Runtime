# Third-party notices

The plugin's own source is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party components retain their own copyright notices and licenses. The README license badge refers to the plugin source license.

## Embedded Node.js

The distributed `libnode.so` binaries are Node.js 24.5.0 from [degaso/nodejs-mobile v24.5.0-r1](https://github.com/degaso/nodejs-mobile/releases/tag/v24.5.0-r1), source commit `116bad1dc919702d4a701d49df26d960403ee4a4`. The complete upstream [LICENSE](https://github.com/degaso/nodejs-mobile/blob/116bad1dc919702d4a701d49df26d960403ee4a4/LICENSE) is preserved in [nodejs-mobile-LICENSE.txt](app/src/main/assets/licenses/nodejs-mobile-LICENSE.txt) and bundled in every APK under `assets/licenses/`.

The following table summarizes major components. The bundled license file contains the full terms and notices, including the additional third-party components listed by upstream; this table does not replace them.

| Component | License |
|---|---|
| Node.js | MIT |
| V8 | BSD-style; additional third-party notices in the upstream license |
| ICU | Unicode/ICU licenses and third-party data notices |
| OpenSSL | Apache License 2.0 |
| zlib | zlib license |
| libuv | MIT and incorporated third-party notices |
| c-ares | MIT-style |
| nghttp2 / nghttp3 / ngtcp2 | MIT |
| simdjson / simdutf | Apache License 2.0 / MIT |
| llhttp / undici / ada / uvwasi | MIT / MIT / Apache License 2.0 or MIT / MIT |
| Brotli / Zstandard | MIT / BSD and GPL alternatives as described upstream |

Binary provenance is recorded in [runtime-build.lock.json](tools/nodejs/runtime-build/runtime-build.lock.json). Its current Android artifact is `nodejs-mobile-24.05-android.zip`, SHA-256 `02e541cc42e7b61bc4d514e4ef2354abb1d46bcf024dd58ce4fbf6b4cdc3a128`.

| ABI | `libnode.so` SHA-256 |
|---|---|
| arm64-v8a | `b1f9b6f95be74336e8b70801967c1dbbb5dac6b86053b9de7dd8c7b74dc81b85` |
| armeabi-v7a | `ee50698e264ce78a12d450de6781bdd4b7df4e7a7546e93d1b96bac2b62cab63` |
| x86_64 | `a62031b38a467027dd5f98db8e91cdc8c31521380ee2dd8c70cd8afbaedc8e2b` |

The lock also describes a future Node 24.17.0 build plan. That plan is not the source or version of the currently shipped binaries.

## Android C++ runtime

`libc++_shared.so` is supplied by the Android NDK selected by the Android Gradle Plugin. The M11 build uses NDK `28.2.13676358` (r28c). LLVM/libc++ uses Apache License 2.0 with LLVM exceptions and includes additional upstream notices. The installed NDK's `toolchains/llvm/prebuilt/windows-x86_64/NOTICE` is preserved in [android-ndk-llvm-NOTICE.txt](app/src/main/assets/licenses/android-ndk-llvm-NOTICE.txt) and bundled in every APK. This complete toolchain notice includes components beyond the shared C++ runtime; inclusion does not imply every toolchain component ships in the APK.

## Other application dependencies

Kotlin standard library and JetBrains annotations use Apache License 2.0. AndroidX components use Apache License 2.0. The common plugin API is the AutoJs6 `common-plugin-api.aar`, whose source is maintained in the [AutoJs6 repository](https://github.com/SuperMonster003/AutoJs6/tree/master/plugin-api/common-plugin-api) under that project's MPL 2.0 license. The Node plugin API is maintained in this repository. npm ecosystem fixtures and their individual license files are Android test assets, not application runtime dependencies.

## 16 KB page-size validation

ELF segment alignment, APK ZIP alignment and execution on a device with 16 KB pages are separate checks. This plugin uses `jniLibs.useLegacyPackaging = true`, so native libraries are compressed in the APK and extracted on installation. Direct memory mapping of uncompressed ZIP entries does not apply; ELF alignment still matters after extraction. Release APKs must nevertheless pass `zipalign -c -P 16 -v 4` for their actual entries.

Use the selected NDK's `llvm-readelf -lW <library.so>` for each ABI's `libnode.so`, `libautojs6-node.so` and `libc++_shared.so`, and inspect every `LOAD` segment's alignment. Runtime validation must first confirm `adb -s <serial> shell getconf PAGE_SIZE` returns `16384`, then run SimpleRunSmokeTest and NpmEcosystemSmokeTest. The measured results and device coverage for this release are recorded in Roadmap M11.6; ELF checks alone do not establish coverage of all 16 KB devices.

On 2026-09-08 the Debug build was checked with NDK r28c `llvm-readelf -lW`:

| ABI | libnode.so | libautojs6-node.so | libc++_shared.so |
|---|---|---|---|
| arm64-v8a | 0x4000 | 0x4000 | 0x4000 |
| x86_64 | 0x4000 | 0x4000 | 0x4000 |
| armeabi-v7a | 0x4000 | 0x1000 | 0x1000 |

This corrects the earlier Roadmap assumption that every library across all three ABIs had 0x4000 alignment. The [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes#elf-alignment) identifies arm64-v8a and x86_64 as the ABIs to check for 16 KB alignment. The 32-bit artifacts are retained with their actual values, without a 16 KB support claim.

The API 36 x86_64 AVD reported PAGE_SIZE=16384 and passed 25/25 instrumentation cases, including SimpleRunSmokeTest, the 15-package NpmEcosystemSmokeTest and all 11 conformance cases. A Xiaomi 23046RP50C on API 35 reported PAGE_SIZE=4096 and passed the same 25/25 cases. These results establish tested x86_64 16 KB behavior and arm64 4 KB behavior; arm64 execution on a 16 KB device has not been tested.

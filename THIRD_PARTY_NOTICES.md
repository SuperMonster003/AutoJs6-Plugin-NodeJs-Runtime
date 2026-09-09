# Third-party notices

The plugin's own source is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party components retain their own copyright notices and licenses. The README license badge refers to the plugin source license.

## Embedded Node.js

The plugin builds `libnode.so` from official [Node.js 24.21.0](https://github.com/nodejs/node/tree/v24.21.0), source commit `955266bfdd854cd280dffd47548673914484e4c0`, with the Android patches described in [runtime-build/README.md](tools/nodejs/runtime-build/README.md). The Android port patch is derived from degaso/nodejs-mobile commit `116bad1dc919702d4a701d49df26d960403ee4a4`; a separate local patch supplies Linux host trap-handler sources for the cross-build tools. A third Android-only patch disables OpenSSL STORE key URLs after a device regression demonstrated that the new loader bypassed the existing filesystem policy; callers can pass key bytes read through node:fs instead. The complete official Node.js [LICENSE](https://github.com/nodejs/node/blob/v24.21.0/LICENSE) is preserved in [nodejs-mobile-LICENSE.txt](app/src/main/assets/licenses/nodejs-mobile-LICENSE.txt) and bundled in every APK under `assets/licenses/`. The existing asset filename is retained.

The following table summarizes major components. The bundled license file contains the full terms and notices, including the additional third-party components listed by upstream; this table does not replace them.

| Component | License |
|---|---|
| Node.js | MIT |
| V8 | BSD-style; additional third-party notices in the upstream license |
| ICU (not linked in this build) | Unicode/ICU licenses and third-party data notices retained in the complete upstream license |
| OpenSSL | Apache License 2.0 |
| zlib | zlib license |
| libuv | MIT and incorporated third-party notices |
| c-ares | MIT-style |
| nghttp2 / nghttp3 / ngtcp2 | MIT |
| simdjson / simdutf | Apache License 2.0 / MIT |
| llhttp / undici / ada / uvwasi | MIT / MIT / Apache License 2.0 or MIT / MIT |
| Brotli / Zstandard | MIT / BSD and GPL alternatives as described upstream |

Binary provenance is recorded in [runtime-build.lock.json](tools/nodejs/runtime-build/runtime-build.lock.json), including the official `node-v24.21.0.tar.xz` archive (SHA-256 `a6f54defb6fd7c84f41dba13d61e78e9b4e0961712cf61f29715c05f5ced94fc`), the three patch hashes, and the exact Docker image and NDK used. The original Node 24.5 Android archive is retained in the lock as historical provenance and for reconstruction of that version.

| ABI | `libnode.so` SHA-256 |
|---|---|
| arm64-v8a | `c48da9b772635e8e76ce0a06de09e71dbd70efe602903f1f5c9042672e629b2d` |
| armeabi-v7a | `8e9f6c326ac3fdaac114cc37323e2d12dba96894fb306423c1426c1a7cd8c99d` |
| x86_64 | `ef576b2958de84b6cd641c18f684f42d00f4e97da09c4be3ebfb8bff4dd722b5` |

The [24.21.0 bridge headers](app/src/main/cpp/node-v24.21.0/README.md) come from the official Node.js header archive, with its complete [upstream license](app/src/main/cpp/node-v24.21.0/LICENSE) preserved alongside them. All three runtime libraries are built from the same official source and patch set; they use no ICU and retain the Inspector backend for explicit Debug use.

## Android C++ runtime

`libc++_shared.so` is supplied by the Android NDK selected by the Android Gradle Plugin. The runtime and bridge builds use NDK `28.2.13676358` (r28c). LLVM/libc++ uses Apache License 2.0 with LLVM exceptions and includes additional upstream notices. The installed NDK's `toolchains/llvm/prebuilt/windows-x86_64/NOTICE` is preserved in [android-ndk-llvm-NOTICE.txt](app/src/main/assets/licenses/android-ndk-llvm-NOTICE.txt) and bundled in every APK. This complete toolchain notice includes components beyond the shared C++ runtime; inclusion does not imply every toolchain component ships in the APK.

## Other application dependencies

Kotlin standard library and JetBrains annotations use Apache License 2.0. AndroidX components use Apache License 2.0. The common plugin API is the AutoJs6 `common-plugin-api.aar`, whose source is maintained in the [AutoJs6 repository](https://github.com/SuperMonster003/AutoJs6/tree/master/plugin-api/common-plugin-api) under that project's MPL 2.0 license. The Node plugin API is maintained in this repository. npm ecosystem fixtures and their individual license files are Android test assets, not application runtime dependencies.

## 16 KB page-size validation

ELF segment alignment, APK ZIP alignment and execution on a device with 16 KB pages are separate checks. This plugin uses `jniLibs.useLegacyPackaging = true`, so native libraries are compressed in the APK and extracted on installation. Direct memory mapping of uncompressed ZIP entries does not apply; ELF alignment still matters after extraction. Release APKs must nevertheless pass `zipalign -c -P 16 -v 4` for their actual entries.

Use the selected NDK's `llvm-readelf -lW <library.so>` for each ABI's `libnode.so`, `libautojs6-node.so` and `libc++_shared.so`, and inspect every `LOAD` segment's alignment. Runtime validation must first confirm `adb -s <serial> shell getconf PAGE_SIZE` returns `16384`, then run SimpleRunSmokeTest and NpmEcosystemSmokeTest. Current source-runtime validation is recorded in Roadmap M17.1/M17.2; the historical Node 24.5 checks below are from M11.6; ELF checks alone do not establish coverage of all 16 KB devices.

On 2026-09-08 the Node 24.5 Debug build was checked with NDK r28c `llvm-readelf -lW`:

| ABI | libnode.so | libautojs6-node.so | libc++_shared.so |
|---|---|---|---|
| arm64-v8a | 0x4000 | 0x4000 | 0x4000 |
| x86_64 | 0x4000 | 0x4000 | 0x4000 |
| armeabi-v7a | 0x4000 | 0x1000 | 0x1000 |

This corrects the earlier Roadmap assumption that every library across all three ABIs had 0x4000 alignment. The [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes#elf-alignment) identifies arm64-v8a and x86_64 as the ABIs to check for 16 KB alignment. The 32-bit artifacts are retained with their actual values, without a 16 KB support claim.

The API 36 x86_64 AVD reported PAGE_SIZE=16384 and passed 25/25 instrumentation cases, including SimpleRunSmokeTest, the 15-package NpmEcosystemSmokeTest and all 11 conformance cases. A Xiaomi 23046RP50C on API 35 reported PAGE_SIZE=4096 and passed the same 25/25 cases. These results establish tested x86_64 16 KB behavior and arm64 4 KB behavior; arm64 execution on a 16 KB device has not been tested.

The signed Release APKs (versionCode 105) were subsequently checked with `apksigner verify --verbose` and `zipalign -c -P 16 -v 4`, all four passing. The extracted Release ELF libraries have the same alignment values shown above, and all native ZIP entries are compressed. The arm64 APK on the Xiaomi device, armeabi-v7a APK on a Sony G8441 (API 28), x86_64 APK on the 16 KB AVD, and universal APK on the Xiaomi device each passed SimpleRunSmokeTest, NpmEcosystemSmokeTest, PluginInfoContractTest and PluginManifestContractTest: 16/16 Release cases in total. Both complete notice files were also verified byte-for-byte inside every Release APK.

On 2026-09-09 the Node 24.21 source runtime and both Debug/Release bridges were
checked again: all nine native libraries have the same alignment matrix above.
The three physical devices (Xiaomi API 35, Sony XQ-AT72 API 31 and Sony G8441
API 28) and the API 36 x86_64 AVD passed the 52-case Debug and Release suites;
the API 28 reports each include one existing SDK requirement skip. Each also
passed the separate Debug Inspector and cold-start tests. The final four signed
v1.3.0 / versionCode 145 APKs passed signature, ZIP alignment, native payload,
license and metadata checks, then each passed the four Release installation
checks listed above, for 16/16 final cases with no skips.

The x86_64 AVD reports `getconf PAGE_SIZE=16384` but
`/proc/self/smaps` reports `KernelPageSize: 4 kB`. This is the Android x86_64
16 KB **userspace simulation on a 4 KB kernel**, as described by the
[AOSP page-size test helpers](https://android.googlesource.com/platform/external/ltp/+/1833505658bf5e3fae06bad5b6f914f790f6703a/include/pgsize_helpers.h).
It does not establish coverage on a physical arm64 device with a 16 KB kernel;
that environment remains untested. The temporary AVD was closed after testing.

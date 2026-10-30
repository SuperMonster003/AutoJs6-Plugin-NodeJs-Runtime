# Third-party notices

The plugin's own source is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party components retain their own copyright notices and licenses. The README license badge refers to the plugin source license.

## Embedded Node.js

The plugin builds `libnode.so` from official [Node.js 26.13.0](https://github.com/nodejs/node/tree/v26.13.0), source commit `18b32a3b1552e199a01f1a8d9fd64aa756d3b75b`, with the Android patches described in [runtime-build/README.md](tools/nodejs/runtime-build/README.md). The Android port patch is derived from degaso/nodejs-mobile commit `116bad1dc919702d4a701d49df26d960403ee4a4`; separate local patches supply Linux host trap-handler sources for the cross-build tools, keep fatal-error text on stderr, and cross-build the Rust crates and host tools. Since plugin 1.6.0 (M23.1) no Android-only policy patch is applied: the earlier OpenSSL STORE key URL patch was removed together with the runtime's own filesystem boundary. The complete official Node.js [LICENSE](https://github.com/nodejs/node/blob/v26.13.0/LICENSE) is preserved in [nodejs-mobile-LICENSE.txt](app/src/main/assets/licenses/nodejs-mobile-LICENSE.txt) and bundled in every APK under `assets/licenses/`. The existing asset filename is retained.

Since plugin 2.0.0, V8's `Temporal` is compiled from the Rust crates vendored in the Node.js source (`deps/crates/vendor`, pinned by `deps/crates/Cargo.lock`: temporal_rs, ICU4X and their dependencies) and linked into `libnode.so`. The upstream LICENSE does not list these crates, so the license file of every vendored crate is bundled in [node-rust-crates-LICENSES.txt](app/src/main/assets/licenses/node-rust-crates-LICENSES.txt), together with each crate's version and license expression.

Plugin 1.x (Node.js 24.21.0) is maintained on the `release/1.x` branch, whose notices describe that runtime.

The following table summarizes major components. The bundled license file contains the full terms and notices, including the additional third-party components listed by upstream; this table does not replace them.

| Component | License |
|---|---|
| Node.js | MIT |
| V8 14.6.202.34 | BSD-style; additional third-party notices in the upstream license |
| ICU 78.3 (linked as `full-icu` since 1.6.0; `small-icu` with English-only locale data from 1.5.1 to 1.5.7) | Unicode/ICU licenses and third-party data notices retained in the complete upstream license |
| OpenSSL 3.5.9 (shipped with Node 26.13.0; backported to Node 24.21.0 in 1.7.0 - 1.32.x) | Apache License 2.0 |
| temporal_rs / temporal_capi / timezone_provider 0.2.3, ICU4X 2.2 and the other vendored Rust crates | MIT or Apache License 2.0 / Unicode License v3 / MIT / Apache License 2.0, per crate in `node-rust-crates-LICENSES.txt` |
| zlib | zlib license |
| libuv | MIT and incorporated third-party notices |
| c-ares | MIT-style |
| nghttp2 / nghttp3 / ngtcp2 | MIT |
| simdjson / simdutf | Apache License 2.0 / MIT |
| llhttp / undici / ada / uvwasi | MIT / MIT / Apache License 2.0 or MIT / MIT |
| Brotli / Zstandard | MIT / BSD and GPL alternatives as described upstream |
| npm 11.20.0 / corepack 0.36.0 (terminal archive `assets/nodejs/cli/node-cli-26.13.0.bin`) | Artistic License 2.0 / MIT; each package keeps its own license file inside the archive |

Binary provenance is recorded in [runtime-build.lock.json](tools/nodejs/runtime-build/runtime-build.lock.json), including the official `node-v26.13.0.tar.xz` archive (SHA-256 `263f639ecfa7a17b49438d76cdbf9aff73179e3c7cb789da69b900718427fa2e`), the Android patch hashes, and the exact Docker image, NDK and Rust toolchain used. Plugin 1.7.0 - 1.32.x backported the OpenSSL 3.5.9 source and architecture updates from [Node PR 66396](https://github.com/nodejs/node/pull/66396) to Node 24.21.0; Node 26.13.0 ships OpenSSL 3.5.9 itself, so 2.0.0 removed those patches, and their provenance stays in the lock and the `v1.32.*` tags. The OpenSSL license is unchanged. The original Node 24.5 Android archive is retained in the lock as historical provenance and for reconstruction of that version.

| ABI | `libnode.so` SHA-256 |
|---|---|
| arm64-v8a | `95011070e6094a377682915faa3aa39706fa77b1fc7ca12c114735b7c7a4d430` |
| armeabi-v7a | `5914bf96ffcb2600ce1db79556141091678263323ae433c50695d6a23b6dc098` |
| x86_64 | `d258e619b2ac8b48a45affb14fe39de9210728ed92752c8e4d0d8b18a6495708` |

The [26.13.0 bridge headers](app/src/main/cpp/node-v26.13.0/README.md) come from the official Node.js header archive, with the complete [upstream license](app/src/main/cpp/node-v26.13.0/LICENSE) preserved alongside them. All three runtime libraries are built from the same official source and patch set; since 1.6.0 they link ICU as `full-icu` and retain the Inspector backend for explicit requests in Debug and Release.

## Android C++ runtime

`libc++_shared.so` is supplied by the Android NDK selected by the Android Gradle Plugin. The runtime libraries (`libnode.so`) are built with NDK `29.0.14206865` (r29, clang-r563880c); the bridge (`libautojs6-node.so`) and `libc++_shared.so` are built with the Android Gradle Plugin's default NDK `28.2.13676358` (r28c). LLVM/libc++ uses Apache License 2.0 with LLVM exceptions and includes additional upstream notices. The installed NDK's `toolchains/llvm/prebuilt/windows-x86_64/NOTICE` is preserved in [android-ndk-llvm-NOTICE.txt](app/src/main/assets/licenses/android-ndk-llvm-NOTICE.txt) and bundled in every APK. This complete toolchain notice includes components beyond the shared C++ runtime; inclusion does not imply every toolchain component ships in the APK.

## Other application dependencies

Kotlin standard library and JetBrains annotations use Apache License 2.0. AndroidX components use Apache License 2.0. The common plugin API is the AutoJs6 `common-plugin-api.aar`, whose source is maintained in the [AutoJs6 repository](https://github.com/SuperMonster003/AutoJs6/tree/master/plugin-api/common-plugin-api) under that project's MPL 2.0 license. The Node plugin API is maintained in this repository. npm ecosystem fixtures and their individual license files are Android test assets, not application runtime dependencies.

The Rhino global libraries of AutoJs6 (api-coverage Q61) ship in `app/src/main/assets/nodejs/libraries`, copied unchanged from the AutoJs6 host's `app/src/main/assets/modules`: dayjs (MIT, Copyright (c) 2018-present iamkun; `dayjs.LICENSE.txt`), pangu.js 10.1.0 (MIT, Copyright (c) 2013 Vinta; `pangu.LICENSE.txt`), cheerio as the single-file build used by AutoX (aiselp), with the htmlparser2 / domhandler / domutils / css-select / entities modules it bundles (MIT, the Cheerio and fb55 contributors) and banana-i18n (MIT, the Wikimedia Foundation and contributors).

## 16 KB page-size validation

ELF segment alignment, APK ZIP alignment and execution on a device with 16 KB pages are separate checks. This plugin uses `jniLibs.useLegacyPackaging = true`, so native libraries are compressed in the APK and extracted on installation. Direct memory mapping of uncompressed ZIP entries does not apply; ELF alignment still matters after extraction. Release APKs must nevertheless pass `zipalign -c -P 16 -v 4` for their actual entries.

Use the selected NDK's `llvm-readelf -lW <library.so>` for each ABI's `libnode.so`, `libautojs6-node.so` and `libc++_shared.so`, and inspect every `LOAD` segment's alignment. Runtime validation must first confirm `adb -s <serial> shell getconf PAGE_SIZE` returns `16384`, and check `KernelPageSize`/`MMUPageSize` in `/proc/self/smaps` to distinguish native 16 KB pages from userspace simulation, then run SimpleRunSmokeTest and NpmEcosystemSmokeTest. Current source-runtime validation is recorded in Roadmap M17.1/M17.2/M17.2a; the historical Node 24.5 checks below are from M11.6; ELF checks alone do not establish coverage of all 16 KB devices.

On 2026-09-08 the Node 24.5 Debug build was checked with NDK r28c `llvm-readelf -lW`:

| ABI | libnode.so | libautojs6-node.so | libc++_shared.so |
|---|---|---|---|
| arm64-v8a | 0x4000 | 0x4000 | 0x4000 |
| x86_64 | 0x4000 | 0x4000 | 0x4000 |
| armeabi-v7a | 0x4000 | 0x1000 | 0x1000 |

This corrects the earlier Roadmap assumption that every library across all three ABIs had 0x4000 alignment. The [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes#elf-alignment) identifies arm64-v8a and x86_64 as the ABIs to check for 16 KB alignment. The 32-bit artifacts are retained with their actual values, without a 16 KB support claim.

The API 36 x86_64 AVD reported PAGE_SIZE=16384 and passed 25/25 instrumentation cases, including SimpleRunSmokeTest, the 15-package NpmEcosystemSmokeTest and all 11 conformance cases. A Xiaomi 23046RP50C on API 35 reported PAGE_SIZE=4096 and passed the same 25/25 cases. These results establish tested x86_64 userspace 16 KB behavior and arm64 4 KB behavior; arm64 execution on a 16 KB device had not been tested at that point.

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
that environment remained untested in the 2026-09-09 run. The temporary AVD was
closed after testing.

On 2026-09-10 the current 1.4.0 development build completed validation on a
physical Samsung SM-A566B, Android 16 / API 36, arm64-v8a, through the
user-provided RDB connection. `getconf PAGE_SIZE=16384` and both shell and actual
Node worker native-library mappings reported `KernelPageSize: 16 kB` and
`MMUPageSize: 16 kB`. Debug build152 passed 53 regular cases plus separate
Inspector and cold-start cases; paired, minified Release build153 passed the
same 53 regular cases. Host media/image integration passed 9 cases with each
Node build, including 1080p RGBA Buffer transfers over JNI and file transports.
The universal Release APK passed four installation/runtime/contract cases on
the same device. These final groups total 130 passing cases with no skips.

Initial host integration had three dependency-configuration failures: OpenCV
was absent and MediaInfo 2.0 did not advertise snapshot v2. Installing OpenCV
1.1.0 and the existing MediaInfo 2.1.0 development APK resolved these failures;
the first run remains recorded. All four collected Release APKs passed signature
and ZIP-alignment checks; every LOAD in the arm64 Debug/Release Node, bridge and
C++ runtime libraries has `0x4000` alignment. This adds native arm64 16 KB
execution evidence for the tested device and artifacts. Details, APK hashes,
scope and remaining manual acceptance are in the
[Samsung acceptance record](docs/ARM64-16KB-ACCEPTANCE-20260910.md).

# AutoJs6 Node Runtime Build

This directory contains the Android Node source build and the original pinned
Node 24.5 binary materializer. Both are manual maintenance tools; neither runs
in the default APK or PR build.

Current status: the Android runtime is Node 26.13.0 LTS (M25.2a), built from
official Node sources with five Android/host patches and `--with-intl=full-icu`
(ICU 78 with full locale data since plugin 1.6.0 / M23.1; `small-icu` from 1.5.1
to 1.5.7). The exact Docker image, NDK, Rust toolchain, patch hashes and per-ABI
outputs are recorded in [runtime-build.lock.json](runtime-build.lock.json). The
replaced Node 24.21.0 facts stay in the lock under `reproducibility.sourceBuild`
(`node26Update.previous` and the earlier update records). Node 24.21.0 builds for
plugin 1.32.x and `release/1.x` use this directory as of the `v1.32.*` tag.

## Node 26.13 changes (M25.2a)

- **Toolchain.** Node 26 requires Clang >= 19.1 and Python >= 3.10. The container
  moved from NDK r28c (clang 19.0.1) and host clang-18 to NDK r29 (29.0.14206865,
  clang 21.0.0 based on r563880c) and host clang-19 (19.1.1); Python stays 3.12.
- **Corepack.** Node 25 removed `deps/corepack` and the `--without-corepack`
  configure switch, so the switch is gone. The terminal packages corepack
  separately (M25.2c).
- **Temporal.** V8 14.6 implements `Temporal` with the vendored Rust crates in
  `deps/crates` (temporal_rs), built by cargo during the Node build. Configure
  silently disables Temporal when cargo or rustc is missing, so the build passes
  `--v8-enable-temporal-support` to make that a configure error. The container
  installs a pinned Rust toolchain through a checksummed rustup-init, with the
  three Android targets and `i686-unknown-linux-gnu`.
- **Rust cross build.** Upstream `deps/crates/crates.gyp` passes a Rust target
  only on Windows and macOS. `0007-android-cross-rust-crates.patch` makes
  `configure.py` set an Android target triple for libnode and a separate Linux
  host triple for host tools such as `mksnapshot` (`i686-unknown-linux-gnu` when
  the target is 32-bit, matching the `-m32` host toolchain; otherwise
  `x86_64-unknown-linux-gnu`). Each GYP toolset then runs its own cargo action and
  links its own static library; the outputs live under different triple
  directories, so they do not collide. Native and macOS/Windows builds keep the
  upstream path.
- **Host libatomic.** The host tool `node_js2c` links simdutf, whose atomic
  base64 helpers call `__atomic_load` / `__atomic_store` on the 32-bit x86 host
  used for ARMv7. Upstream `node.gyp` adds `-latomic` only when GYP's `OS` is
  `linux`, so `0008-android-cross-host-libatomic.patch` adds it to host-toolset
  links of Android cross builds. Target link commands are unchanged.
- **OpenSSL.** Node 26.13.0 ships OpenSSL 3.5.9, so the M25.1a backport patches
  `0005-openssl-3.5.9-sources.patch` and `0006-openssl-3.5.9-archs.patch` were
  removed. Their provenance and digests stay in the lock
  (`node26Update.previous`) and in the `v1.32.*` tag.
- **Verification.** V8 14 defines the `HandleScope` constructor and destructor
  inline and drops the `WriteOptions` parameter from the `String::Utf8Value`
  constructor, so libnode no longer exports the old symbols.
  `verify-source-runtime.py` reads the V8 version embedded in libnode and, from
  V8 14 on, applies the same substitutions as `node_bridge_lifecycle.cpp`.
- **C++ runtime.** libnode links `libc++_shared.so` dynamically, and the APK
  packages the copy from the NDK selected by the Android Gradle Plugin (r28c at
  the time of this update). Every non-weak libnode import resolves against the
  r28c API 24 libc/libm/libdl/liblog stubs and its `libc++_shared.so` (checked
  with `llvm-nm` on 2026-10-30). Repeat the check when either NDK changes.

Each ABI was built once from a fresh source tree; no independent rebuild was run
for this update (Roadmap M25 Q5).

## OpenSSL 3.5.9 security backport (M25.1a, Node 24.21 only)

The 2026-10-11 check found OpenSSL 3.5.8 in both Node v24.21.0 and the Node
`v24.x` maintenance branch. Node 24.21.0 builds backported the complete OpenSSL
update from [Node PR 66396](https://github.com/nodejs/node/pull/66396) without
changing the Node/V8 version or runtime slot: `0005` came from Node commit
[`bee7e2403cedcd8c5e11e34bfaa922a37b5faaa1`](https://github.com/nodejs/node/commit/bee7e2403cedcd8c5e11e34bfaa922a37b5faaa1)
without the `deps/openssl/openssl/doc/build.info` diff (absent from the release
tarball), and `0006` was the unmodified architecture update from
[`6b5ac2ab5d36030e7916b0f4c57b23b515b2d96e`](https://github.com/nodejs/node/commit/6b5ac2ab5d36030e7916b0f4c57b23b515b2d96e).

The [OpenSSL advisory](https://openssl-library.org/news/secadv/20260929.txt)
describes the fixes. `OpenSslSecuritySmokeTest` checks TLS 1.2/1.3, SNI context
switching, certificate rejection and crypto through CJS, ESM, Workers and the
terminal. Its relative CRLDP fixture exercises CVE-2026-35189 in a fresh process
with a 32 MiB RSS-growth ceiling. Signing/verification tests establish functional
compatibility, not a statistical proof of constant-time execution.

## Build Node 26.13 from source

Run on Linux x86_64 with Docker, or inside WSL2. Put build directories on the
Linux filesystem; compiling V8 through `/mnt/c` or `/mnt/d` is much slower.
Python 3.11+ is required by the launcher. Build the environment once (network
required for Ubuntu packages, the official NDK archive and the Rust toolchain).
The Dockerfile needs no build context, so pass it on stdin:

```sh
docker build -t autojs6/node-source:r29-local - \
  < tools/nodejs/runtime-build/container/Dockerfile.source
docker image inspect --format '{{.Id}}' autojs6/node-source:r29-local
```

The Dockerfile pins the Ubuntu base by registry digest, verifies the NDK archive
against its official checksum and rustup-init against its published SHA-256, and
installs a fixed Rust version. Ubuntu package repository contents can
change, so rebuilding this Dockerfile later may produce a different image ID.
Use the **same full `sha256:...` image ID** for both runtime reproducibility
runs. The lock records the image actually used for this maintenance run; its
`containerRepoDigest` is null because no builder image was published. A local
`docker save` / `docker load` archive preserves that exact environment without
requiring a registry publication.

Fetch the pinned official source once; the launcher and container both check
the recorded SHA-256 before extraction. A pre-downloaded archive makes the
source build itself offline:

```sh
mkdir -p /var/tmp/autojs6-node/sources
curl -fL --retry 2 -o /var/tmp/autojs6-node/sources/node-v26.13.0.tar.xz \
  https://nodejs.org/dist/v26.13.0/node-v26.13.0.tar.xz
python3 tools/nodejs/runtime-build/build-node-from-source.py \
  --source-dir /var/tmp/autojs6-node/sources \
  --work-root /var/tmp/autojs6-node/builds --run-id first --jobs 6
```

The launcher defaults to the locked local image ID. If you built a new image,
pass `--image sha256:<full-image-id>` and retain that ID when repeating the
build. `--abi all` builds arm64-v8a, armeabi-v7a and x86_64 sequentially;
`--abi arm64-v8a` selects one. Each build gets a separate source tree mounted
at the same `/work/node` path. `--resume` only resumes a workspace whose
recorded image, source, patch and build script inputs still match. To compare
clean builds, use a fresh `--run-id repeat` with identical inputs and compare
each ABI's `output/libnode.so` SHA-256. Record actual differences if unequal;
an incremental no-op rebuild is not a clean repeat.

For Node 24, the earlier maintenance run first built Node 24.20 from source, resumed after
the Linux host-tool fix, and compared it with an independent clean build using
the corrected inputs. Node 24.21 was then rebuilt separately from each of those
two workspaces, overlaying the verified new source and applying the STORE fix.
No objects were copied between first and repeat builds. All three ABI outputs
have identical SHA-256 and Build IDs at both stages. These Node 24.21 checks
are independent maintenance rebuilds, not two fresh clean builds. The launcher
above also supports fresh source builds for future maintenance.

Artifacts and ELF reports are under
`<work-root>/26.13.0-<abi>-<run-id>/output/`. The build uses Android API 24,
16 KB LOAD alignment, SHA-1 ELF Build IDs, fixed source paths/timestamp, ICU 78
with full locale data (`--with-intl=full-icu`, M23.1), no Node startup snapshot, and keeps
the inspector backend for explicit Debug and Release requests. See Roadmap
M17.3 / M21.2 / M23.1 / M23.5 for the
packaging/ICU/inspector decision; the `0004` patch keeps Node's fatal-error text on
the real stderr in addition to logcat.

The Android patch is extracted from degaso/nodejs-mobile commit
`116bad1dc919702d4a701d49df26d960403ee4a4` relative to official Node 24.5.0,
and applies to official 26.13.0 with line offsets only. It retains Android/host toolchain, 32-bit,
libuv/OpenSSL/zlib and V8 build fixes. iOS changes, mobile product branding
and the fork's blanket V8 trap-handler disable are excluded; upstream already
handles Android trap-handler support. A separate local patch adds the POSIX
trap-handler sources needed by Linux `mksnapshot` when GYP's `OS` is Android
for the cross build. This fixes the observed host linker errors without
changing the Android target's trap-handler policy. The former third patch
(`0003-disable-android-openssl-store.patch`) rejected OpenSSL STORE private-key
URLs on Android to protect the runtime's own `/proc` boundary; M23.1 removed that
boundary and the patch with it, so key URLs load as on desktop Node. `0007`
(Rust cross build) and `0008` (host libatomic) are described above. The five
patch digests are recorded in the lock.

`verify-source-runtime.py` checks architecture, 16 KB LOAD alignment, Build ID
and the Node/V8/libuv symbols dynamically consumed by the current bridge.
After building the Android bridge against the new headers, run it again with
`--bridge <libautojs6-node.so>` to check direct imports and the adapter export
map. `libnode.exports.map` describes the **bridge adapter ABI**, not the export
surface of upstream libnode; applying it to raw libnode would hide required
Node/V8 symbols. ELF checks do not replace the smoke classes, conformance,
Debug inspector and device validation required by Roadmap M17.1/M17.2.

## Reconstruct the original Node 24.5 libraries

The checked-in lock records the exact upstream Node 24.5 Android archive and
each ABI entry's path, size, and SHA-256. The executable materializer downloads
or accepts that archive, verifies it before extraction, and verifies every
output. This reconstructs the historical Node 24.5 libraries. Use the source
build above for Node 26.13. The legacy route consumes a pinned upstream binary
archive and does not rebuild that version from source.

Validate or inspect the pinned plan without materializing artifacts:

```powershell
powershell -ExecutionPolicy Bypass -File tools\nodejs\runtime-build\build-node-runtime.ps1
sh tools/nodejs/runtime-build/build-node-runtime.sh
```

The shell entry point runs `verify-runtime-build-plan.js`; the PowerShell entry
point reads the same lock and prints the selected versions, toolchain, output,
and ABI plan. Both stay in plan-only mode unless their execute switch is passed.

Materialize and verify Node 24.5 without trusting the checked-in libraries:

```powershell
powershell -ExecutionPolicy Bypass -File tools\nodejs\runtime-build\build-node-runtime.ps1 -Execute
sh tools/nodejs/runtime-build/build-node-runtime.sh tools/nodejs/runtime-build/runtime-build.lock.json --execute
```

Both paths write only under `build/` by default. They do not promote the source
build target or change the default runtime slot.

# AutoJs6 Node Runtime Build

This directory contains the Android Node source build and the original pinned
Node 24.5 binary materializer. Both are manual maintenance tools; neither runs
in the default APK or PR build.

Current status: the default Android runtime is Node 24.21.0, built from official
Node sources with three Android patches. All three ABIs passed embedding and
device validation. Independent rebuild results and the exact Docker image,
NDK and patch hashes are recorded in
[runtime-build.lock.json](runtime-build.lock.json). Runtime Kit 1.4.0 and the
four signed APKs are local release artifacts; external publication is separate.

## Build Node 24.21 from source

Run on Linux x86_64 with Docker, or inside WSL2. Put build directories on the
Linux filesystem; compiling V8 through `/mnt/c` or `/mnt/d` is much slower.
Python 3.11+ is required by the launcher. Build the environment once (network
required for Ubuntu packages and the official NDK archive):

```sh
docker build -t autojs6/node-source:r28c-local \
  -f tools/nodejs/runtime-build/container/Dockerfile.source .
docker image inspect --format '{{.Id}}' autojs6/node-source:r28c-local
```

The Dockerfile pins the Ubuntu base by registry digest and verifies the NDK
archive against its official checksum. Ubuntu package repository contents can
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
curl -fL --retry 2 -o /var/tmp/autojs6-node/sources/node-v24.21.0.tar.xz \
  https://nodejs.org/dist/v24.21.0/node-v24.21.0.tar.xz
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

The recorded maintenance run first built Node 24.20 from source, resumed after
the Linux host-tool fix, and compared it with an independent clean build using
the corrected inputs. Node 24.21 was then rebuilt separately from each of those
two workspaces, overlaying the verified new source and applying the STORE fix.
No objects were copied between first and repeat builds. All three ABI outputs
have identical SHA-256 and Build IDs at both stages. These Node 24.21 checks
are independent maintenance rebuilds, not two fresh clean builds. The launcher
above also supports fresh source builds for future maintenance.

Artifacts and ELF reports are under
`<work-root>/24.21.0-<abi>-<run-id>/output/`. The build uses Android API 24,
16 KB LOAD alignment, SHA-1 ELF Build IDs, fixed source paths/timestamp, no
ICU, no Node startup snapshot, and keeps the inspector backend for Debug use.
See Roadmap M17.3 for the packaging/ICU/inspector decision.

The Android patch is extracted from degaso/nodejs-mobile commit
`116bad1dc919702d4a701d49df26d960403ee4a4` relative to official Node 24.5.0,
and applied to official 24.21.0. It retains Android/host toolchain, 32-bit,
libuv/OpenSSL/zlib and V8 build fixes. iOS changes, mobile product branding
and the fork's blanket V8 trap-handler disable are excluded; upstream already
handles Android trap-handler support. A separate local patch adds the POSIX
trap-handler sources needed by Linux `mksnapshot` when GYP's `OS` is Android
for the cross build. This fixes the observed host linker errors without
changing the Android target's trap-handler policy. The third patch rejects
OpenSSL STORE private-key URLs on Android after an actual `file:///proc/self/fd`
bypass was reproduced. Read key files through `node:fs` and pass their bytes to
`crypto.createPrivateKey` or signing APIs. PEM/DER/JWK and KeyObject inputs
continue to work. All three patch digests are recorded in the lock.

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
build above for Node 24.21. The legacy route consumes a pinned upstream binary
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

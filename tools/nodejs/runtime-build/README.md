# AutoJs6 Node Runtime Build

This directory contains the Phase 9 bootstrap plan for reproducible Android
Node runtime artifacts.

Current status: pinned Node 24.5 binary materialization is `ready`; a clean
source build remains `bootstrap_only`.

The checked-in lock records the exact upstream Node 24.5 Android archive and
each ABI entry's path, size, and SHA-256. The executable materializer downloads
or accepts that archive, verifies it before extraction, and verifies every
output. This provides a second reconstruction route for the checked-in
`libnode.so` files. It is a pinned upstream binary pipeline, not a source build.

The same lock retains the Node 24.17 source-build plan, toolchain requirements,
and deferred promotion decision. Maintainers still need a digest-pinned build
container, a published Android patch series, and per-ABI Node 24.17 outputs and
Build IDs before the source-build status can move beyond `bootstrap_only`.

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

Both paths write only under `build/` by default. They do not promote Node 24.17
or change the default runtime slot.

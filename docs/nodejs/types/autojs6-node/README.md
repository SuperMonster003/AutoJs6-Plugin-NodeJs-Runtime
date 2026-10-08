# AutoJs6 Node TypeScript Declarations

Last reviewed: 2026-10-09, runtime plugin 1.6.0.

This directory describes AutoJs6 runtime modules, host bridge APIs and request
metadata. Include `index.d.ts` from your TypeScript project as demonstrated by
[the TypeScript smoke project](../../../../sample/nodejs/typescript-smoke).
The declarations supply AutoJs6 API shapes and selected Node API conveniences;
they are not a complete replacement for upstream Node type declarations.

## Execution and authority

The runtime executes JavaScript using Node 24.21.0. The host Compiler converts
`.ts`, `.cts` and `.mts` projects into JavaScript; there is no runtime regex
stripping fallback. TSX must be compiled before dispatch. Dynamic TypeScript
modules require a host module-source provider supporting compilation (v3).

Node builtins use Node's native implementations. Filesystem access, sockets,
SQLite, native addons and process operations run under the plugin's Android UID.
Host bridge calls separately require a real provider, declared capabilities and
any Android permissions or user consent required by that provider. A type
annotation does not grant authority. Exported APK support is tracked by M24 and
is not supplied by this release.

## Runtime and host modules

- Host automation: `toast`, `app`, `dialogs`, `clipboard`, `device`, `shell`,
  `engines`, `accessibility`, `notifications`, `sensors`, `work_manager`, `ui`,
  `ui.overlay`, `input_observer`, and `autojs6:host-events`.
- Capture and media: `media_projection`, `image`/`images`, `ocr`, `barcode`,
  `media`, `mediainfo`, `media_store`, and `recorder`. Device/provider
  requirements are documented in [HOST-API](../../../HOST-API.md).
- Storage: `storage`/`storages`, `database`/`sqlite`, and `files` expose host
  operations. `node:fs`, `node:fs/promises` and `node:sqlite` are native Node APIs
  and use the plugin process's own filesystem view.
- Networking: Node's `http`, `https`, `net`, `tls`, `dns`, `dgram` and `http2`
  follow Node behavior. AutoJs6 `fetch`, `axios` and `websocket` convenience
  modules retain their documented bridge/request options.
- Utilities and migration: `base64`, `colors`, `formatter`/`fmt`,
  `converter`/`cvt`, `s13n`, `mime`, `nanoid`, `opencc`, `pinyin`, `pinyin4j`,
  `jsox`, `rhino`, `java`, `autojs6:compat` and global `$autojs`.
  Java calls use the host's published class/member policy. Rhino installation
  is explicit; no Rhino globals are installed merely by requiring a module.
- Lifecycle and local plugins: `autojs6:lifecycle`, `plugins`,
  `package_manager`, `npm` and `autojs6:profile`.

## npm and native addons

`package_manager.install/update/remove` accept npm package specifications
(strings or arrays) with optional `cwd`, `registry`, `ignoreScripts`, `offline`,
`env`, `npmArgs`, `signal` and `timeoutMs`. These calls use the bundled npm CLI;
registry access and install scripts follow npm and `.npmrc`. `npm` exposes the
same CLI-backed operations. Failed commands preserve npm output and exit details.

The object-based package-manager overloads operate on the host's local package
store and retain their own permission and integrity requirements. They do not
select the npm CLI overload.

`.node` addons load through Node's native loader, including in Workers. Supply
binaries for Android and the installed ABI; the plugin does not bundle NDK or
node-gyp. The [native-addon example](../../../../sample/nodejs/native-addon)
contains a cross-compiled example for all three supported ABIs.

## Workers, Inspector and compile cache

`autojs6:bridge.callAutoJs<T>` works in root scripts and native Workers. Worker
calls travel through parent MessagePorts to the root execution's host broker.
The [worker-host-bridge example](../../../../sample/nodejs/worker-host-bridge)
shows this route. Higher-level host modules remain available in the root script.

Request worker policy supplies `resourceLimits` defaults; each Worker may
override them. Nested Workers are supported. WorkerPool uses CPU parallelism
by default, an unbounded queue and no automatic task deadline; callers may
choose their own pool limits and cancellation/timeout options.

Inspector endpoint activation and Session creation require the explicit
`inspectorEnabled` request in Debug and Release. Endpoints listen on localhost
and support adb forwarding. This request is distinct from host bridge permissions.

`$autojs.engine.compileCache` reports Node's compile-cache setup and directory.
The default cache persists across executions outside the transferred workspace.
Node's module cache APIs remain available; Workers inherit the default setup.

## Profile metadata and compatibility names

`profile_capabilities.d.ts` describes current module modes and request/provider
requirements. Old Safe/Pro/Desktop/Debug identifiers are compatibility metadata;
choosing a profile type does not enable an API. Local plugin and package-store
status fields describe implemented behavior. Sample statuses are `stable` and
`host_provider_required`.

The older `NodeProfileV12*` and `NodeProfileV13*` names remain declared in
[history/profile_versions.d.ts](history/profile_versions.d.ts) for source
compatibility. Those historical matrices must not be used as a current runtime
capability inventory. Use `autojs6:profile`, the live capability catalog and
[the compatibility guide](../../COMPATIBILITY_PROFILE.md).

## Verification

Run from the runtime-plugin repository:

```powershell
.\gradlew.bat --offline verifyNodePluginExamples typeCheckNodeTypescriptSmoke
```

The smoke project checks module imports, overload selection, bridge result types,
profile fields and the historical compatibility names. Runtime/device validation
is recorded separately in [Roadmap.md](../../../../Roadmap.md).

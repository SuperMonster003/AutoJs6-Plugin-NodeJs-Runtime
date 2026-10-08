# Terminal launcher (node / npm / corepack for 3-Shell Terminal)

Last reviewed: 2026-10-08.

The independent [3-Shell Terminal plugin](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Shell-Terminal)
provides the terminal used by AutoJs6 6.8.0 and runs `/system/bin/sh` under the **terminal plugin** uid.
This runtime plugin makes `node`, `npm`, `npx`, `corepack`, `yarn` and
`pnpm` available inside that shell without any Binder traffic: the terminal plugin reads a small
manifest contract, executes the packaged launcher directly and extracts the npm / corepack
archive from the plugin assets. M23.6 also reuses this launcher and archive from runtime package_manager calls,
with lazy extraction under the runtime plugin UID; the terminal contract is unchanged.

中文: 3-Shell Terminal 以终端插件自己的 uid 运行 shell; 本插件通过 manifest 契约, 可直接执行的启动器和 npm /
corepack 资产为该 shell 提供 `node` / `npm` 等命令, 终端命令不经过 Binder. M23.6 的运行时 package_manager 也复用同一启动器与资产, 在运行时插件 uid 下按需解压.

## Contract (schema 1)

Declared as `<meta-data>` on `NodeJsRuntimePluginService` (constants in
`NodeJsPluginCapabilityKeys`). The terminal plugin reads them from `ServiceInfo.metaData`.

| Key (`org.autojs.plugin.nodejs.` prefix) | Value | Source |
| --- | --- | --- |
| `NODE_CLI_SCHEMA` | `1` | manifest |
| `NODE_CLI_EXECUTABLE` | `libnodexe.so` | manifest |
| `NODE_CLI_COMMANDS` | `node,npm,npx,corepack,yarn,yarnpkg,pnpm,pnpx` | manifest |
| `NODE_CLI_ARCHIVE` | `nodejs/cli/node-cli-<node version>.bin` (asset path) | lock via placeholder |
| `NODE_CLI_ARCHIVE_SHA256` | SHA-256 of the asset | lock via placeholder |
| `NODE_CLI_ARCHIVE_ROOT` | `lib/node_modules` | lock via placeholder |
| `NODE_CLI_ARCHIVE_ENTRY_COUNT` / `NODE_CLI_ARCHIVE_BYTES` | entry count and uncompressed size, terminal extraction limits | lock via placeholder |
| `NODE_CLI_NPM_VERSION` / `NODE_CLI_COREPACK_VERSION` | informational versions | lock via placeholder |

The same facts are mirrored into `getRuntimeInfo()` and `PluginInfo.capabilities`
(`KEY_NODE_CLI_*` in `NodeJsRuntimeContract`) and the runtime advertises the `nodeCli`
capability, so the host Doctor report can show them without parsing the manifest.

Terminal acceptance rules: the runtime signer must be trusted (official plugin signer or the
terminal plugin's own signer), schema must equal `1`, the executable must exist in the runtime's
`nativeLibraryDir` and start with the ELF magic, the SHA-256 must be 64 hex digits, and a
one-shot `libnodexe.so --version` must print the plugin's Node version. Any other outcome
leaves the terminal shell-only with a typed reason (disabled / plugin missing / untrusted / too
old / executable missing / exec denied / setup failed). Node.js Runtime 1.5.0+ is required.
The terminal's direct integration does not depend on the host Plugin Center enable switch.

## Launcher (`libnodexe.so`)

`app/src/main/cpp/node_cli_main.cpp`, built by the `nodexe` CMake target as a PIE
executable named `libnodexe.so` so the package installer extracts it next to `libnode.so`
for every ABI. Facts checked by `:app:verifyNativePageAlignment` and the release digest
task: `DYN` + `PT_INTERP`, `NEEDED libnode.so` / `libc++_shared.so`, `DT_RUNPATH $ORIGIN`,
`PT_LOAD` alignment `0x4000`.

Dispatch is by `basename(argv[0])` (a leading `lib` and trailing `.so` are stripped):

| Command | Behaviour |
| --- | --- |
| `node`, `nodexe`, anything else | `node::Start(argc, argv)` unchanged |
| `npm` / `npx` | inserts `$AUTOJS6_NODE_CLI_ROOT/npm/bin/npm-cli.js` / `npx-cli.js` as `argv[1]` |
| `corepack` / `yarn` / `yarnpkg` / `pnpm` / `pnpx` | inserts `$AUTOJS6_NODE_CLI_ROOT/corepack/dist/<name>.js` as `argv[1]` |

Without `AUTOJS6_NODE_CLI_ROOT`, or when the script is unreadable, the launcher prints a
one-line explanation to stderr and exits with `1`. `process.execPath` resolves to the
launcher itself, so npm lifecycle scripts and `npx` spawn the same binary.

## Archive (`assets/nodejs/cli/node-cli-<version>.bin`)

npm and corepack are pure JavaScript, so they are taken from the official
`node-v<version>-linux-x64.tar.xz` of the exact Node version the runtime is built from and
stored as a deterministic zip (fixed timestamps, sorted entries; `.bin` because `*.zip` is
ignored by Git). Layout: `lib/node_modules/npm/**` and `lib/node_modules/corepack/**`,
minus `npm/docs`, `npm/man`, top-level `*.md` (LICENSE files stay) and symbolic links.

`tools/nodejs/cli/node-cli.lock.json` records the source tarball URL and SHA-256, the
archive SHA-256 / entry count / sizes and the npm / corepack versions. `app/build.gradle.kts`
feeds the lock into manifest placeholders and `BuildConfig`, and `preBuild` depends on
`verifyNodeCliArchive`, which fails when the packaged asset drifts from the lock.

```powershell
py tools/nodejs/cli/build-node-cli-archive.py          # download, verify SHASUMS256, rebuild archive + lock (network)
py tools/nodejs/cli/build-node-cli-archive.py --check  # offline: packaged asset matches the lock
```

## Terminal-plugin layout (for reference)

3-Shell Terminal extracts the archive root into its own `<filesDir>/terminal/usr/lib/autojs6-node-cli/`,
recreates `<filesDir>/terminal/usr/bin/<command>` symbolic links to the launcher on every
session start (the runtime's `nativeLibraryDir` changes on update) and exports
`AUTOJS6_NODE_CLI_ROOT`, `npm_config_prefix`, `npm_config_cache`, `COREPACK_HOME` and the
registry settings. Global installs land in `usr/lib/node_modules`, separate from the bundled
CLI directory.

There is no automatic migration of the old host terminal's home, global npm packages or settings.
The terminal needs its own shared-storage grant to enter `/sdcard` projects. Updating or removing
the runtime does not restart an already running node process; new sessions rediscover the runtime
and refresh links, or remove stale command links and use a plain shell. The schema remains 1.

## Limits (Android W^X)

- Files written in terminal app data cannot be executed: `node_modules/.bin/*` and native
  binaries fetched by npm packages fail with `EACCES`. The terminal sets
  `npm_config_bin_links=false`; run tools as `node node_modules/<pkg>/<entry>.js`.
  `npx` can fetch packages but cannot execute their unavailable bin shims (typically exit 127).
- Native addons must be Android binaries matching the process ABI. The embedded
  runtime also uses Node's native addon loader since M23.4.
- corepack downloads Yarn / pnpm on first use; the registry and network are the user's
  choice in the terminal plugin settings. The runtime package_manager can invoke the bundled npm for registry installs
  since M23.6, using its own workspace/configuration. pnpm 12 and later ship as native binaries
  without an Android build, so the terminal pins corepack to its bundled defaults
  (`COREPACK_DEFAULT_TO_LATEST=0`: pnpm 11.x, Yarn 1.x) unless a version is named.
- Intl: since M23.1 (1.6.0), libnode uses full ICU, including locale data.
  The earlier 1.5.x small-ICU restriction no longer applies to current builds.
- Node's own fatal-error and warning text (uncaught exceptions, `--trace-*`) reaches
  the real stderr as well as logcat (tag `nodejs`) since 1.5.1 (patch
  `0004-android-stderr-passthrough.patch`); 1.5.0 wrote it to logcat only.
- Processes use the terminal plugin's UID and Android permissions. They do not inherit
  the AutoJs6 host's or the Node.js Runtime service's permissions.

## Verification

```powershell
py tools/nodejs/cli/build-node-cli-archive.py --check
.\gradlew.bat --offline :app:testDebugUnitTest :nodejs-api:test :app:verifyNativePageAlignment
adb -s <serial> shell am instrument -w -e class io.github.supermonster003.autojs6.plugin.nodejs.PluginManifestContractTest,io.github.supermonster003.autojs6.plugin.nodejs.NodeCliLauncherSmokeTest io.github.supermonster003.autojs6.plugin.nodejs.test/androidx.test.runner.AndroidJUnitRunner
```

`NodeCliLauncherSmokeTest` executes the installed launcher from the instrumentation
process (same uid): `--version`, an inline script, the missing-root error path, and
`npm` / `npx` / `corepack --version` from the extracted archive through command-named
symbolic links. Cross-uid execution from 3-Shell Terminal is covered by its
`NodeCliInstrumentationTest` and the host's terminal script integration tests.

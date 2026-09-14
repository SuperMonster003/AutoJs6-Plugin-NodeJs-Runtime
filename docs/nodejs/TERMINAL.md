# Terminal launcher (node / npm / corepack for the AutoJs6 terminal)

Last reviewed: 2026-09-14.

The AutoJs6 host (6.8.0 and later) ships an in-app terminal that runs `/system/bin/sh`
under the **host** uid. This plugin makes `node`, `npm`, `npx`, `corepack`, `yarn` and
`pnpm` available inside that shell without any Binder traffic: the host reads a small
manifest contract, executes the packaged launcher directly and extracts the npm / corepack
archive from the plugin assets. The Node runtime service, its AIDL contract and the script
execution path are untouched.

中文: 宿主终端以宿主 uid 运行 shell; 本插件通过 manifest 契约、可直接执行的启动器和 npm /
corepack 资产为该 shell 提供 `node` / `npm` 等命令, 不经过 Binder, 也不改变运行时服务与脚本执行链路.

## Contract (schema 1)

Declared as `<meta-data>` on `NodeJsRuntimePluginService` (constants in
`NodeJsPluginCapabilityKeys`). The host reads them from `ServiceInfo.metaData`.

| Key (`org.autojs.plugin.nodejs.` prefix) | Value | Source |
| --- | --- | --- |
| `NODE_CLI_SCHEMA` | `1` | manifest |
| `NODE_CLI_EXECUTABLE` | `libnodexe.so` | manifest |
| `NODE_CLI_COMMANDS` | `node,npm,npx,corepack,yarn,yarnpkg,pnpm,pnpx` | manifest |
| `NODE_CLI_ARCHIVE` | `nodejs/cli/node-cli-<node version>.bin` (asset path) | lock via placeholder |
| `NODE_CLI_ARCHIVE_SHA256` | SHA-256 of the asset | lock via placeholder |
| `NODE_CLI_ARCHIVE_ROOT` | `lib/node_modules` | lock via placeholder |
| `NODE_CLI_ARCHIVE_ENTRY_COUNT` / `NODE_CLI_ARCHIVE_BYTES` | entry count and uncompressed size, host extraction limits | lock via placeholder |
| `NODE_CLI_NPM_VERSION` / `NODE_CLI_COREPACK_VERSION` | informational versions | lock via placeholder |

The same facts are mirrored into `getRuntimeInfo()` and `PluginInfo.capabilities`
(`KEY_NODE_CLI_*` in `NodeJsRuntimeContract`) and the runtime advertises the `nodeCli`
capability, so the host Doctor report can show them without parsing the manifest.

Host acceptance rules: schema must equal `1`, the executable must exist in the plugin's
`nativeLibraryDir` and start with the ELF magic, the SHA-256 must be 64 hex digits, and a
one-shot `libnodexe.so --version` must print the plugin's Node version. Any other outcome
leaves the terminal shell-only with a typed reason (plugin missing / not authorized / too
old / executable missing / exec denied).

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

## Host-side layout (for reference)

The host extracts the archive root into `<filesDir>/terminal/usr/lib/autojs6-node-cli/`,
recreates `<filesDir>/terminal/usr/bin/<command>` symbolic links to the launcher on every
session start (the plugin's `nativeLibraryDir` changes on update) and exports
`AUTOJS6_NODE_CLI_ROOT`, `npm_config_prefix`, `npm_config_cache`, `COREPACK_HOME` and the
registry settings. Global installs land in `usr/lib/node_modules`, separate from the bundled
CLI directory.

## Limits (Android W^X)

- Files written by the host cannot be executed: `node_modules/.bin/*` and native
  binaries fetched by npm packages fail with `EACCES`. The host sets
  `npm_config_bin_links=false`; run tools as `node node_modules/<pkg>/<entry>.js` or
  through `npx`.
- Native addons (`.node`) are not loadable (unchanged runtime policy).
- corepack downloads yarn / pnpm on first use; the registry and network are the user's
  choice in the host terminal settings. The runtime facade still performs no registry
  download (Roadmap M9.3 stays as decided).
- Processes run under the host uid with the host's Android permissions, not the
  plugin's.

## Verification

```powershell
py tools/nodejs/cli/build-node-cli-archive.py --check
.\gradlew.bat --offline :app:testDebugUnitTest :nodejs-api:test :app:verifyNativePageAlignment
adb -s <serial> shell am instrument -w -e class io.github.supermonster003.autojs6.plugin.nodejs.PluginManifestContractTest,io.github.supermonster003.autojs6.plugin.nodejs.NodeCliLauncherSmokeTest io.github.supermonster003.autojs6.plugin.nodejs.test/androidx.test.runner.AndroidJUnitRunner
```

`NodeCliLauncherSmokeTest` executes the installed launcher from the instrumentation
process (same uid): `--version`, an inline script, the missing-root error path, and
`npm` / `npx` / `corepack --version` from the extracted archive through command-named
symbolic links. Cross-uid execution from the host is covered by the host terminal tests.

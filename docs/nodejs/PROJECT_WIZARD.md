# AutoJs6 Node Project Wizard

Last reviewed: 2026-10-11.

`tools/nodejs/project/autojs6-node-project.js` is a runtime-plugin helper for
creating and validating Node project directories. It runs on the desktop;
it does not execute install scripts, load native binaries or grant permissions.
For the current runtime status behind these templates, see
[COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

Run every command below from the **AutoJs6-Plugin-NodeJs-Runtime** repository.

## Create

List available templates:

```powershell
node tools/nodejs/project/autojs6-node-project.js templates
```

Create a project:

```powershell
node tools/nodejs/project/autojs6-node-project.js create `
  --template commonjs-app `
  --name my-node-app `
  --out D:\work\my-node-app
```

Available templates:

- `commonjs-app`: minimal CommonJS app.
- `esm-app`: stable ESM app using `.mjs`.
- `automation-script`: starter with `toast` and `app` bridge metadata.
- `screenshot-ocr-script`: starter with `image`, `screen_capture`, and `ocr`
  bridge metadata.
- `network-fetch-script`: starter with controlled `fetch` and `network`
  bridge metadata.
- `typescript-cjs`: host-compiled TypeScript CommonJS starter using `.cts`.
- `typescript-esm`: host-compiled TypeScript ESM starter using `.mts`.
- `typescript-automation`: TypeScript bridge starter with `toast` and `app`
  metadata.
- `typescript-ocr`: TypeScript screen capture plus OCR bridge starter.
- `typescript-long-running-task`: TypeScript long-running task starter with
  explicit signal cleanup.
- `compiled-tsx`: build-time TSX starter that runs generated JavaScript only.

Each template writes:

- `project.json`
- `package.json`
- an entry such as `main.cjs`, `main.mjs`, `main.cts`, `main.mts`, or `main.ts`
- `README.md`

Some templates also write build-time support files. For example,
`compiled-tsx` writes `src/view.tsx`, `tsconfig.json`, and a prebuilt
`dist/view.js` entry target. Runtime TSX is still unsupported; TSX must be
compiled on the desktop/build stage.

## Validate

Validate a project before running it:

```powershell
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app
```

Use `--json` for CI or IDE integration:

```powershell
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app --json
```

The JSON report uses schema `autojs6-node-project-validation-v1` and returns
`ok`, `issueCount`, an `issues` array, and a `compatibility` object. Each issue has:

- `severity`
- `code`
- `file`
- `message`
- `hint`

The compatibility object uses schema
`autojs6-node-dependency-compatibility-v1` and classifies the installed project
as one of:

- `compatible`
- `compatible with capability`
- `unsupported`

It records the entry format, required Node builtins, declared bridge
capabilities, capability requirements, package format/export-condition usage,
dependency graph size, and runtime/request requirements. `unsupported` signals
are surfaced as validator issues. Inspector activation requires an explicit host
request, while importing the module is allowed. Packaged apps use the external Node runtime with a matching AutoJs6/Builder
template. This validator checks project inputs; building and device acceptance
remain separate steps.

## Validator Coverage

The validator checks:

- `project.json` exists, parses, and declares `type` or `projectType` as
  `node`.
- `package.json` exists, parses, has a package name, and uses only supported
  `type` values.
- The declared entry file exists, stays inside the project root, and uses a
  supported `.js`, `.cjs`, `.mjs`, `.ts`, `.cts`, or `.mts` extension. `.tsx`
  entries are rejected with an explicit diagnostic.
- Bridge permission arrays are JSON string arrays and use known host bridge
  capabilities or explicit `plugin.<id>` capabilities.
- Native addons, `binding.gyp`, npm lifecycle scripts and ordinary `.bin` files
  are accepted. Addons still need an Android binary for the installed ABI;
  the plugin does not bundle native build tools. The validator does not execute
  scripts or verify binary compatibility.
- Dependency transfer still checks unsupported package-manager layouts,
  symlinks and the desktop scan budget. Dependencies installed on the device by
  bundled npm follow the runtime's native filesystem behavior.
- Source analysis classifies ESM/CJS entry format, required builtins, raw
  network, `child_process`, `worker_threads`, Inspector request requirements,
  native package exports/imports, and host bridge capability declarations.

Common actionable error codes:

- `PROJECT_FILE_MISSING`
- `PROJECT_JSON_TYPE`
- `PACKAGE_NAME_INVALID`
- `PACKAGE_TYPE_UNSUPPORTED`
- `ENTRY_DECLARATION_MISSING`
- `ENTRY_FILE_MISSING`
- `ENTRY_EXTENSION_UNSUPPORTED`
- `ENTRY_TYPESCRIPT_COMPILER_REQUIRED`
- `ENTRY_PATH_UNSAFE`
- `PERMISSIONS_SHAPE`
- `UNKNOWN_PERMISSION`
- `DEPENDENCY_SYMLINK_UNSUPPORTED`
- `DEPENDENCY_BUNDLE_TOO_LARGE`
- `CAPABILITY_DECLARATION_MISSING`
- `UNSUPPORTED_PACKAGE_MANAGER_LAYOUT`

## Verification

The checked-in Gradle gate exercises every template and representative invalid
projects for missing entries, invalid package names, unknown permissions,
unsupported TSX entries and missing host declarations. It also checks native
addon/lifecycle-script/package-exports acceptance, Inspector request reporting,
ordinary filesystem paths and each template's entry format:

```powershell
.\gradlew.bat --console=plain verifyNodeProjectWizard
```

Reports are written to:

- `build/reports/nodejs/project-wizard.json`
- `build/reports/nodejs/project-wizard.md`

## Ordinary file paths

The validator accepts ordinary absolute and parent-directory fs paths. Actual file
access is determined by Android under the runtime plugin's UID. The validator does
not open these files or grant Android storage permissions. Project entry and archive
paths remain relative because they identify files to transfer between applications.
Current runtime behavior is recorded in [Roadmap M23](../../Roadmap.md).
TypeScript templates require the host Compiler; runtime regex stripping is unavailable.

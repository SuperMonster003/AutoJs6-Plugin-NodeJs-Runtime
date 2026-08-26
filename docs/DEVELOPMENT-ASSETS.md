# Node.js Development Asset Ownership

Last reviewed: 2026-08-26.

This repository is the single owner of Node.js runtime-facing development assets:

- `sample/nodejs`: runnable examples and `examples.json` metadata.
- `docs/nodejs/types/autojs6-node`: script-facing TypeScript declarations.
- `tools/nodejs/project/autojs6-node-project.js`: project templates and validator.
- `node-plugin-examples.gradle.kts`: example catalog verification.
- `node-types.gradle.kts`: declaration verification and TypeScript smoke checking.
- `node-project-wizard.gradle.kts`: project-template and validator verification.
- `node-host-alignment.gradle.kts`: host API mirror and capability alignment checks.

The AutoJs6 host no longer carries mirrors of the examples, declarations, wizard,
or their Gradle scripts. Host source remains authoritative for script routing,
plugin discovery/trust, Binder requests, workspace/module-source transport, Android
permissions, AutoJs6 capability providers, and minimal end-to-end integration tests.

## Verification

Run local asset checks from this repository:

```powershell
.\gradlew.bat verifyNodePluginExamples verifyNodeProjectWizard `
  verifyAutoJs6NodeTypeDeclarations typeCheckNodeTypescriptSmoke --offline
```

Run cross-repository alignment checks from this repository:

```powershell
.\gradlew.bat verifyNodeHostIntegration `
  "-Pautojs.host.root=D:\idea-projects\AutoJs6" --offline
```

`autojs.host.root` may be omitted when the repositories use their standard sibling
directory names. This is the only retained location override; runtime behavior and
feature defaults are not selected through host Gradle properties.

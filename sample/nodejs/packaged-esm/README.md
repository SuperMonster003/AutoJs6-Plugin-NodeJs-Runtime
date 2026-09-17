# packaged-esm

CommonJS entry that dynamically imports a local `.mjs` module through Node's
native ESM linker. The name dates from an earlier packaged-APK plan; exported
APKs do not bundle this runtime, so run the project from the host entry.

- Capabilities: `esm`, `dynamic_import`
- Expected provider: Node native ESM linker
- Packaged support: not applicable
- Security limitations: relative specifiers inside the project and `data:` (JavaScript/JSON) imports; absolute paths, parent-directory targets and `file:` URLs load within Android file access (`/proc`, `/sys`, `/dev` denied); `node_modules` lookup stays anchored to the workspace; disabled builtins remain denied

Expected output is listed in `expected-output.txt`.

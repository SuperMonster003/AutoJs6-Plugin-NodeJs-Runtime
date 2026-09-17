# packaged-dynamic-import

CommonJS entry that builds a specifier at runtime and dynamically imports a
local `.mjs` feature module, then imports the same module again through a
`file:` URL. The name dates from an earlier packaged-APK plan;
exported APKs do not bundle this runtime, so run the project from the host entry.

- Capabilities: `dynamic_import`, `esm`
- Expected provider: Node native ESM linker
- Packaged support: not applicable
- Security limitations: computed relative specifiers inside the project resolve through the runtime loader; absolute paths, parent-directory targets and `file:` URLs load within Android file access (`/proc`, `/sys`, `/dev` denied); `node_modules` lookup stays anchored to the workspace; `http(s)` specifiers are not supported by Node; disabled builtins remain denied

Expected output is listed in `expected-output.txt`.

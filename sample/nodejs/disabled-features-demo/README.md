# disabled-features-demo

Runtime policy boundary example.

The script records the current desktop-like defaults (`child_process`,
`worker_threads`, network modules, `process.cwd`, `process.binding` and the native
addon loader). The `native-addon` project exercises a real binary; a missing
`.node` file is a missing module, not evidence that native addons are disabled. Request-level opt-out flags are
covered by the runtime conformance tests; project metadata does not override
those host request flags.

Expected output is listed in `expected-output.txt`.

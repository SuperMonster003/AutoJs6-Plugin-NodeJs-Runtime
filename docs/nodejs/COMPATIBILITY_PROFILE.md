# AutoJs6 Node.js Compatibility Profile

Last reviewed: 2026-09-15. Published plugin: v1.3.0; subsequent local work: v1.5.1.

The external runtime plugin supplies Node.js 24.21.0 / V8 / libuv for arm64-v8a,
armeabi-v7a and x86_64. The host contains no libnode or Node JNI implementation.
Node-marked scripts require the plugin; ordinary JavaScript routing remains a host
concern. Exported APKs currently cannot bundle or launch this runtime.

CommonJS, ESM, dynamic import and local pure JavaScript npm packages execute in
real Node. TypeScript is compiled to JavaScript by the host Compiler, including
supported on-demand module compilation. The runtime does not strip TypeScript.
Native Node builtins retain their own names: `events` / `node:events` is EventEmitter;
Android event observation is exposed as `autojs6:events`. The runtime links ICU 78 with
English-only locale data (`--with-intl=small-icu`, since v1.5.1): `Intl` exists and
Unicode property escapes parse, while other locales fall back to English unless
`NODE_ICU_DATA` supplies a full ICU data file.

AutoJs6 capabilities use asynchronous host calls, declared in `node.permissions`.
The host applies Android permissions and provider availability. A profile label
such as `pro_compat_opt_in` is diagnostic metadata and does not grant capabilities
or Android permissions. See [HOST-API](../HOST-API.md), [declarations](types/autojs6-node)
and the complete [manual projects](MANUAL-ACCEPTANCE.md).

Node fs accepts ordinary absolute and relative paths within the plugin's Android
file access. The v1.4.0 development media/image/recorder path update similarly uses
Android file access in the host and requires both updated applications. Archive
transfer and on-demand compiler inputs have separate project-path contracts.

The debug Inspector supports explicit local debugging through localhost and adb
forward. It is disabled by default; remote listening is unsupported. Native addons,
arbitrary private bindings and unrestricted Java reflection remain unavailable.
Workers, subprocesses and bridges retain documented execution/resource limits.
Screen capture consent, physical events and recording still require the pending
manual acceptance recorded in [Roadmap](../../Roadmap.md). A callable facade is not
evidence of a complete Android provider or packaged-app support.

Current implementation metadata is in the embedded
[capability catalog](../../app/src/main/assets/nodejs/node-capability-catalog.json).
Historical profiles are archived under [history](history), and are not current policy.

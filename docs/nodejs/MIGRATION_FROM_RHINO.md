# Migrating Rhino Scripts to the Node.js Runtime Plugin

Last reviewed: 2026-10-17.

Node.js is a separate engine, not a replacement parser for Rhino source. Migration
should be explicit and reversible.

## Select Node.js

Use the "nodejs" directive, a Node-specific extension, or project metadata with
**type: node**. Keep ordinary Rhino scripts unchanged. If the runtime plugin is not
available, a Node-marked script fails; it is not executed as Rhino.

## Replace Globals Deliberately

Prefer Node modules and the documented AutoJs6 bridge modules. Do not assume every
Rhino global, Java package object, Activity, Context, or raw AutoJs6 runtime object is
available. Consult [the host API reference](../HOST-API.md) and its
[AutoJs6 Node declarations](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/tree/master/docs/nodejs/types/autojs6-node).

Pinyin/Pinyin4j conversions and Android console controls are asynchronous host
calls. Replace `pinyin.simple(text)` with `await pinyin.simple(text)` and replace
console chaining with separate awaited calls. `pinyin.convert()` returns a matrix
after awaiting it; its `compact()` also returns a Promise for a matrix of candidate
tuples. Install the matching Pinyin providers. Missing providers reject the call;
the runtime no longer falls back to a tiny local dictionary.

Do not pass the asynchronous `pinyin.compare` directly to `Array.sort`. Compute
Pinyin keys with `Promise.all` first, then sort those keys synchronously.

Use `screen_metrics` for `cX/cY/cXy/cYx` and scale bases. Each operation returns a
Promise and reads the host's current display. The `autojs6:compat` facade exports
the same functions; global installation remains explicit. The third conversion
argument selects ratio mode, not rounding. Cross-axis ratios accept `"16:9"`.
Keep `setScreenMetrics` separate from the once-per-execution coordinate bases.
See `sample/nodejs/host-api-coverage` for an ESM example.

## Declare Authority

When project capability metadata is present, declare every required host capability.
Sensitive child capabilities such as accessibility gestures, root shell, package
mutation, recording, or overlay authority are not implied by their parent module.
Android permissions and provider readiness are still checked at call time.

## Files and Modules

Use project-relative paths for portability. Node fs, `require`, `import` and
`Worker` also accept absolute paths and `file:` URLs under the runtime plugin's
Android permissions (since M23.1 the runtime denies nothing itself; `/proc`, `/sys` and
`/dev` follow Android too). Host media/image paths use the
host UID and require the matching host update described in [HOST-API](../HOST-API.md).
Use CommonJS/ESM and TypeScript compiled by the host Compiler. Native addons must
be built for Android and the installed runtime ABI; desktop binaries are not
interchangeable with Android binaries. Reflection and process behavior still
depend on the relevant runtime and Android support.

## Long-Running and Scheduled Work

Use bounded plugin execution and the documented work_manager provider. The legacy
work_manager descriptor token **backend="embedded"** is retained for wire compatibility
only. It does not select a host runtime.

## Exported Applications

The host does not embed Node into exported APKs. The 1.7.0 development line supports
packaged clients with an external runtime plugin and a matching host/Builder
Runtime Kit. This is distinct from bundling a standalone Node engine into the APK;
the external plugin must be installed.

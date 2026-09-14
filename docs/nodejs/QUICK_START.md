# AutoJs6 Node.js Quick Start

Last reviewed: 2026-09-10.

## Prerequisites

1. Install a runtime-plugin build compatible with the host contract and device ABI.
2. Enable and authorize the plugin in AutoJs6.
3. Keep the script inside a workspace the host can transfer to the plugin.

The host APK does not contain Node.js. If the plugin is unavailable, Node scripts fail
with an actionable plugin error and never fall back to Rhino.

## Single File

~~~javascript
"nodejs";

console.log(process.version);
console.log(require("node:path").join("hello", "node"));
~~~

Node-specific extensions such as .cjs and .mjs also route to the plugin.

## Project

Declare **type: node** and a relative main entry in project.json. Use package.json for
Node module metadata. When declaring **node.permissions**, include every host bridge
capability the script uses.

~~~json
{
  "name": "hello-node",
  "type": "node",
  "main": "main.cjs",
  "node": {
    "permissions": ["device"]
  }
}
~~~

Use [Example projects](EXAMPLES.md) as working templates.

## Terminal

The AutoJs6 terminal (host 6.8.0+) runs `node`, `npm`, `npx`, `corepack`, `yarn` and
`pnpm` from this plugin's launcher; see [TERMINAL.md](TERMINAL.md) for the contract and
the Android W^X limits.

## Troubleshooting

- Generate a Node.js Doctor report from Developer options.
- Confirm the report says runtime owner **external-plugin** and host embedded runtime
  present **false**.
- Verify plugin discovery, authorization, contract version, Node version, and ABI.
- Review the script console and both host/plugin logcat processes.

Exported applications cannot bundle this runtime. Run Node projects from AutoJs6 with
the plugin installed. See [Compatibility profile](COMPATIBILITY_PROFILE.md) and
[Security model](SECURITY_MODEL.md).

For screen capture, host events and recording, start with the complete projects
and Android consent steps in [Manual acceptance](MANUAL-ACCEPTANCE.md). Bridge
methods returning a Promise must be awaited. `node:events` / `events` is the
Node EventEmitter; Android observations use `autojs6:events`.

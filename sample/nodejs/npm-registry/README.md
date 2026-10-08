# npm-registry

Manually run this project with network access. It installs `is-number@7.0.0`
using the plugin's bundled npm and calls the package in the same execution.
It updates the project package.json, package-lock.json and node_modules as npm
normally does. This example is excluded from automatic offline sample execution.

The registry, proxy and lifecycle options follow npm configuration, including
project `.npmrc`. To select a registry for this call, pass a second argument:
`{ registry: "https://registry.npmjs.org/" }`. `ignoreScripts`, `offline`, `env`,
`npmArgs`, `signal` and `timeoutMs` can also be supplied explicitly.

The plugin prepares its bundled CLI on first use and starts it through the
installed native launcher. Compiler toolchains are not bundled. Android decides
whether downloaded executables can run; use Node to read JS tool entry points
when Android refuses an executable shim in app data.

See [HOST-API](../../../docs/HOST-API.md#npm-registry-与命令行).

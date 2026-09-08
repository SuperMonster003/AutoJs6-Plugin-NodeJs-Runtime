# npm ecosystem corpus

Twenty-one real npm packages run inside the plugin through the workspace archive
transport. Network tests use loopback HTTP, WebSocket and MQTT servers. Android
devices do not contact a registry.

The third batch was prepared on 2026-09-08 in a separate development directory
using the existing package.json and package-lock.json:

```text
npm install --ignore-scripts --no-audit --no-fund --save-exact zod cheerio date-fns mqtt ws
```

Added versions: zod 4.5.4, cheerio 1.2.0, date-fns 4.4.0, mqtt 5.15.2, ws 8.21.3.
All existing dependency versions remained unchanged. The complete dependency
tree and lockfile are included, with package licenses preserved. The tree has
11,656 files (42,630,239 bytes) before APK asset filtering. Underscore directories
such as date-fns/_lib must be retained when packaging Android test assets.

M14.3 added pngjs 7.0.0 from the npm registry on the development machine, with
package scripts disabled and its tarball checked against package-lock integrity.
The previous dependencies are unchanged. The test captures the device display
through instrumentation, adds its PNG and dimensions to the workspace archive,
then asserts `PNG.sync.read` dimensions and RGBA size inside real Node. This
tests PNG decoding independently of the manual MediaProjection consent check.

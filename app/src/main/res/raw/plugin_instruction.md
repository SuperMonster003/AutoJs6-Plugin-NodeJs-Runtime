# AutoJs6 Node.js Runtime

This plugin provides the Node.js 24.21.0 native runtime for AutoJs6.

Install and enable it in the AutoJs6 plugin center, then start a script with:

```js
"nodejs";

console.log(process.version);
```

The runtime slot is `node24_21`. It supports `arm64-v8a`, `armeabi-v7a`, and `x86_64` builds, plus a `universal` APK.

Example projects are in `sample/nodejs`.

The plugin settings page provides 1-8 parallel execution slots (default 2) and a queue limit (unlimited by default); surplus scripts wait in FIFO order, settings take effect without interrupting accepted work, and idleExitMs releases idle runtime processes

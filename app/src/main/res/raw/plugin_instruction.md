# AutoJs6 Node.js Runtime

This plugin provides the Node.js 24.5.0 native runtime for AutoJs6.

Install and enable it in the AutoJs6 plugin center, then start a script with:

```js
"nodejs";

console.log(process.version);
```

The runtime slot is `node24_5`. It supports `arm64-v8a`, `armeabi-v7a`, and `x86_64` builds, plus a `universal` APK.

Example projects are in `sample/nodejs`.

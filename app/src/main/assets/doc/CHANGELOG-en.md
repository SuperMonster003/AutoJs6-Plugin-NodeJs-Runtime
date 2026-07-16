# v1.0.0

###### 2026/07/04

* `Feature` Added the Node.js runtime plugin service with plugin ID `nodejs`, engine `nodejs`, and runtime slot `node24_5`
* `Feature` Provided the Node.js 24.5.0 native runtime through `libnode.so` and `libautojs6-node.so`
* `Feature` Added plugin info discovery through `org.autojs.plugin.INFO` and runtime invocation through `org.autojs.plugin.nodejs.RUNTIME`
* `Feature` Supported CommonJS/ESM source, module sources, working directory, sandbox root, environment variables, stdout/stderr result payloads, and runtime prewarm
* `Feature` Added host capability broker and live bridge support with runtime modules such as `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, and `autojs6:bridge-permissions`
* `Feature` Added ABI split APK builds for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and a `universal` APK
* `Feature` Added `sample/nodejs` projects, a Node resolver diagnostic tool, and runtime build plan verification tools
* `Feature` Added localized plugin metadata, usage instructions, README, and CHANGELOG resources for Spanish, French, Russian, Arabic, Japanese, Korean, English, Simplified Chinese, Hong Kong Traditional Chinese, and Taiwan Traditional Chinese

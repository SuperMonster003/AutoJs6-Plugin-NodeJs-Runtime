# AutoJs6 Node.js Runtime

此插件為 AutoJs6 提供 Node.js 24.5.0 原生執行階段.

在 AutoJs6 插件中心安裝並啟用後, 使用以下指令啟動腳本:

```js
"nodejs";

console.log(process.version);
```

執行階段槽位為 `node24_5`. 支援 `arm64-v8a`, `armeabi-v7a`, `x86_64` 建置, 以及 `universal` 通用包.

更多範例位於 `sample/nodejs`.

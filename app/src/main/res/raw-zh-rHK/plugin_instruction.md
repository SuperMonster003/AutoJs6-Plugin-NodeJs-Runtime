# AutoJs6 Node.js Runtime

此插件為 AutoJs6 提供 Node.js 24.5.0 原生運行時.

在 AutoJs6 插件中心安裝並啟用後, 使用以下指令啟動腳本:

```js
"nodejs";

console.log(process.version);
```

運行時槽位為 `node24_5`. 支援 `arm64-v8a`, `armeabi-v7a`, `x86_64` 構建, 以及 `universal` 通用包.

更多示例位於 `sample/nodejs`.

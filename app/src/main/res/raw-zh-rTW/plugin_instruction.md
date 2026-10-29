# AutoJs6 Node.js Runtime

此插件為 AutoJs6 提供 Node.js 26.13.0 原生執行階段.

在 AutoJs6 插件中心安裝並啟用後, 使用以下指令啟動腳本:

```js
"nodejs";

console.log(process.version);
```

執行階段槽位為 `node26_13`. 支援 `arm64-v8a`, `armeabi-v7a`, `x86_64` 建置, 以及 `universal` 通用包.

更多範例位於 `sample/nodejs`.

外掛設定頁支援 1-8 個平行執行槽 (預設 2) 與排隊上限 (預設不限), 超額指令碼按 FIFO 等待, 設定調整保留已接受的工作, idleExitMs 可回收閒置執行程序

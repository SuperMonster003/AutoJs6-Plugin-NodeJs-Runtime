# AutoJs6 Node.js Runtime

此插件為 AutoJs6 提供 Node.js 26.13.0 原生運行時.

在 AutoJs6 插件中心安裝並啟用後, 使用以下指令啟動腳本:

```js
"nodejs";

console.log(process.version);
```

運行時槽位為 `node26_13`. 支援 `arm64-v8a`, `armeabi-v7a`, `x86_64` 構建, 以及 `universal` 通用包.

更多示例位於 `sample/nodejs`.

插件設定頁支援 1-8 個並行執行槽 (預設 2) 與排隊上限 (預設不限), 超額腳本按 FIFO 等待, 設定調整保留已接受的任務, idleExitMs 可回收閒置執行進程

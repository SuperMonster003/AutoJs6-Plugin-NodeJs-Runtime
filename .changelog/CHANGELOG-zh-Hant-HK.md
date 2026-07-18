******

### 發行歷史

******

# v1.0.0

###### 2026/07/18

* `新增` Node.js 運行時插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 運行時槽位為 `node24_5`
* `新增` 通過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生運行時
* `新增` 支援通過 `org.autojs.plugin.INFO` 發現插件資訊, 並通過 `org.autojs.plugin.nodejs.RUNTIME` 調用運行時服務
* `新增` 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和運行時預熱
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組
* `新增` 支援按 ABI 構建 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` `sample/nodejs` 示例項目, Node 解析診斷工具和運行時構建計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
* `優化` 增加基於單調時鐘的分階段診斷, 覆蓋執行源碼構建/引導/腳本執行/結果生成與讀取/單次執行清理, 並區分已跳過與不適用狀態

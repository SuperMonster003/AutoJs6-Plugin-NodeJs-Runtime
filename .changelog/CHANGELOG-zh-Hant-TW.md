******

### 發行歷史

******

# v1.0.0

###### 2026/07/18

* `新增` Node.js 執行階段插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 執行階段槽位為 `node24_5`
* `新增` 透過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生執行階段
* `新增` 支援透過 `org.autojs.plugin.INFO` 發現插件資訊, 並透過 `org.autojs.plugin.nodejs.RUNTIME` 呼叫執行階段服務
* `新增` 支援 CommonJS/ESM 原始碼, 模組原始碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和執行階段預熱
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等執行階段模組
* `新增` 支援按 ABI 建置 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` `sample/nodejs` 範例專案, Node 解析診斷工具和執行階段建置計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
* `優化` 增加基於單調時鐘的分階段診斷, 涵蓋執行原始碼建構/啟動載入/腳本執行/結果產生與讀取/單次執行清理, 並區分已略過與不適用狀態

# v1.0.0

###### 2026/07/18

* `新增` Node.js 執行階段插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 執行階段槽位為 `node24_5`
* `新增` 透過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生執行階段
* `新增` Node.js 執行階段運作於獨立常駐處理程序, 重複使用處理程序層級 Node/V8 狀態並為每次執行建立全新 isolate 及 Environment
* `新增` 支援透過 `org.autojs.plugin.INFO` 發現插件資訊, 並透過 `org.autojs.plugin.nodejs.RUNTIME` 呼叫執行階段服務
* `新增` 支援 CommonJS/ESM 原始碼, 模組原始碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和執行階段預熱
* `新增` 支援請求層級工作區封存傳輸 v2, 包含明確輸入對應/插件私有工作區執行/輸出回寫/刪除 tombstone 清單, 且不掃描宿主沙盒
* `新增` 單一活動零佇列准入, 透過 `ERR_AUTOJS6_NODE_PLUGIN_BUSY` 提供背壓, 並支援重新啟動處理程序式取消
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等執行階段模組
* `新增` 支援按 ABI 建置 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` 分割及通用 APK 輸出均隨 Node.js 執行階段程式庫封裝 `libc++_shared.so`
* `新增` `sample/nodejs` 範例專案, Node 解析診斷工具和執行階段建置計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
* `修復` 重新啟動處理程序式取消期間, 執行階段處理程序回收時可能提交不完整工作區快照的問題
* `修復` 請求合約驗證或工作區具現化失敗時, 工作區封存檔案描述元可能因擁有權交接未涵蓋全部結束路徑而洩漏的問題
* `優化` 完善 R5 合約中繼資料及診斷, 涵蓋 ABI/能力/常駐執行階段狀態/准入/取消和獨立處理程序歸因
* `優化` 增加基於單調時鐘的分階段診斷, 涵蓋執行原始碼建構/啟動載入/腳本執行/結果產生與讀取/單次執行清理, 並區分已略過與不適用狀態

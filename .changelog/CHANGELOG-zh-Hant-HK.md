******

### 發行歷史

******

# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可調用入口與 `read()` 支援顯式選擇插件快照 v1/v2; 省略 schema 時繼續返回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 透過綁定精確位元組數及 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逃逸, 符號連結和歧義, 並保留宿主 TypeScript 診斷及 Source Map 堆疊映射
* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `新增` 支援涵蓋排隊與執行全程的腳本逾時, 逾時回傳 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定逾時時仍可無限執行
* `新增` 預設啟用 `dgram` (UDP) 與 `http2`, 並對 `trace_events` 回傳明確的停用錯誤
* `新增` 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
* `新增` 新增受宿主外掛權限保護的無介面啟用入口, 補齊外掛中心說明並停用應用程式資料備份
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修復循環 re-export 中可變匯出未能即時更新的問題
* `修復` 修正 AutoJs6 相容 facade 的 ESM 匯入及不存在的 TypeScript 後綴探測, 同時維持已安裝 npm 套件優先
* `優化` 刪除基於 regex 的 legacy TypeScript 類型剝離 fallback 及其請求開關, raw `.ts/.mts/.cts` 現始終要求宿主編譯產物
* `優化` 對齊宿主/插件 v2 合約, 能力清單與 plugin-only 運行時職責邊界
* `優化` 將 Node.js 示例, TypeScript 類型聲明, 項目嚮導, 運行時默認值及宿主對齊校驗統一歸屬插件倉庫, 移除宿主側 Gradle 開關與重複開發資產
* `優化` 實測 npm 生態擴展至 15 個套件, 新增 axios、express 及 ESM-only 套件 nanoid、p-limit、yocto-queue
* `優化` 以 `executionMode` 請求欄位作為生命週期模式的權威來源, 將沒有實際作用的 `runtimeAdapter` 標記為已棄用

# v1.1.0

###### 2026/08/18

* `新增` 支援 stdout/stderr 即時串流輸出與基於 `node::Stop` 的協作式取消
* `新增` 以最多 3 個等待者的有界串行隊列取代 BUSY 直接拒絕, 並支援常駐長運行腳本生命週期
* `新增` 預設啟用 Node 原生網絡內建模組、`worker_threads` 與 `child_process`, 並實測 10 個常用純 JavaScript npm 套件
* `優化` 支援無需工作區歸檔的 direct-run 與 v1..v2 模組源碼 provider 寬容協商, 錯誤輸出收斂為簡明錯誤碼與 JavaScript 堆疊

# v1.0.0

###### 2026/07/18

* `新增` Node.js 運行時插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 運行時槽位為 `node24_5`
* `新增` 通過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生運行時
* `新增` Node.js 運行時運行於獨立常駐進程, 復用進程級 Node/V8 狀態並為每次執行創建全新 isolate 及 Environment
* `新增` 支援通過 `org.autojs.plugin.INFO` 發現插件資訊, 並通過 `org.autojs.plugin.nodejs.RUNTIME` 調用運行時服務
* `新增` 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和運行時預熱
* `新增` 支援請求級工作區歸檔傳輸 v2, 包含顯式輸入映射/插件私有工作區執行/輸出回寫/刪除 tombstone 清單, 且不掃描宿主沙盒
* `新增` 單活動零隊列準入, 通過 `ERR_AUTOJS6_NODE_PLUGIN_BUSY` 提供背壓, 並支援重啟進程式取消
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組
* `新增` 支援按 ABI 構建 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` 分包及通用 APK 輸出均隨 Node.js 運行庫打包 `libc++_shared.so`
* `新增` `sample/nodejs` 示例項目, Node 解析診斷工具和運行時構建計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
* `修復` 重啟進程式取消期間, 運行時進程回收時可能提交不完整工作區快照的問題
* `修復` 請求合約校驗或工作區物化失敗時, 工作區歸檔文件描述符可能因所有權交接未覆蓋全部退出路徑而洩漏的問題
* `優化` 完善 R5 合約元數據及診斷, 覆蓋 ABI/能力/常駐運行時狀態/準入/取消和獨立進程歸因
* `優化` 增加基於單調時鐘的分階段診斷, 覆蓋執行源碼構建/引導/腳本執行/結果生成與讀取/單次執行清理, 並區分已跳過與不適用狀態

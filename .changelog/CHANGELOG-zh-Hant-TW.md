******

### 發行歷史

******

# v1.3.0

###### 尚未發佈

* `修復` 修復常駐指令碼的橋接工作階段持續累積請求與回應記錄的問題, 清理已完成請求, 僅保留最近 32 筆回應並限制診斷大小

# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可呼叫入口與 `read()` 支援明確選擇外掛快照 v1/v2; 省略 schema 時繼續傳回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 透過綁定精確位元組數與 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逸出, 符號連結和歧義, 並保留主機 TypeScript 診斷與 Source Map 堆疊對應
* `新增` 在 Android 應用程式權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 控管
* `新增` 支援涵蓋排隊與執行全程的指令碼逾時, 逾時傳回 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定逾時時仍可無限執行
* `新增` 預設啟用 `dgram` (UDP) 與 `http2`, 並對 `trace_events` 傳回明確的停用錯誤
* `新增` 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
* `新增` 新增受宿主外掛權限保護的無介面啟用入口, 補齊外掛中心說明並停用應用程式資料備份
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 對應並統一產生/匯入堆疊框架
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修正循環 re-export 中可變匯出未即時更新的問題
* `修復` 修正 AutoJs6 相容 facade 的 ESM 匯入與不存在的 TypeScript 副檔名探測, 同時維持已安裝 npm 套件優先
* `優化` 刪除以 regex 實作的 legacy TypeScript 型別移除 fallback 及其請求開關, raw `.ts/.mts/.cts` 現在一律要求宿主編譯產物
* `優化` 對齊宿主/外掛 v2 合約, 能力清單與 plugin-only 執行階段職責邊界
* `優化` 將 Node.js 範例, TypeScript 型別宣告, 專案精靈, 執行階段預設值及宿主對齊驗證統一歸屬外掛儲存庫, 移除宿主端 Gradle 開關與重複開發資產
* `優化` 實測 npm 生態擴展至 15 個套件, 新增 axios、express 及 ESM-only 套件 nanoid、p-limit、yocto-queue
* `優化` 以 `executionMode` 請求欄位作為生命週期模式的權威來源, 將沒有實際作用的 `runtimeAdapter` 標記為已棄用
* `優化` 移除十個僅列印固定狀態的歷史範例, 並在型別宣告中標明截圖、影像分析和錄音尚未提供宿主 provider

# v1.1.0

###### 2026/08/18

* `新增` 支援 stdout/stderr 即時串流輸出與基於 `node::Stop` 的協作式取消
* `新增` 以最多 3 個等待者的有界序列佇列取代 BUSY 直接拒絕, 並支援常駐長時間執行指令碼生命週期
* `新增` 預設啟用 Node 原生網路內建模組、`worker_threads` 與 `child_process`, 並實測 10 個常用純 JavaScript npm 套件
* `優化` 支援不需工作區封存的 direct-run 與 v1..v2 模組原始碼 provider 寬容協商, 錯誤輸出收斂為簡明錯誤碼與 JavaScript 堆疊

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

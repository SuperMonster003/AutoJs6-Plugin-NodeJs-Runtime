# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可調用入口與 `read()` 支援顯式選擇插件快照 v1/v2; 省略 schema 時繼續返回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 透過綁定精確位元組數及 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逃逸, 符號連結和歧義, 並保留宿主 TypeScript 診斷及 Source Map 堆疊映射
* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `新增` 支援涵蓋排隊與執行全程的腳本逾時, 逾時回傳 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定逾時時仍可無限執行
* `新增` 預設啟用 `dgram` (UDP) 與 `http2`, 並對 `trace_events` 回傳明確的停用錯誤
* `新增` 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修復循環 re-export 中可變匯出未能即時更新的問題
* `修復` 修正 AutoJs6 相容 facade 的 ESM 匯入及不存在的 TypeScript 後綴探測, 同時維持已安裝 npm 套件優先
* `優化` 刪除基於 regex 的 legacy TypeScript 類型剝離 fallback 及其請求開關, raw `.ts/.mts/.cts` 現始終要求宿主編譯產物
* `優化` 對齊宿主/插件 v2 合約, 能力清單與 plugin-only 運行時職責邊界
* `優化` 將 Node.js 示例, TypeScript 類型聲明, 項目嚮導, 運行時默認值及宿主對齊校驗統一歸屬插件倉庫, 移除宿主側 Gradle 開關與重複開發資產
* `優化` 實測 npm 生態擴展至 15 個套件, 新增 axios、express 及 ESM-only 套件 nanoid、p-limit、yocto-queue
* `優化` 以 `executionMode` 請求欄位作為生命週期模式的權威來源, 將沒有實際作用的 `runtimeAdapter` 標記為已棄用

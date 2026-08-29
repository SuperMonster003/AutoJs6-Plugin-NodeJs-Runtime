# v1.2.0

###### 2026/08/29

* `新增` 新增 module-source provider v3, 透過綁定精確位元組數及 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逃逸, 符號連結和歧義, 並保留宿主 TypeScript 診斷及 Source Map 堆疊映射
* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修復循環 re-export 中可變匯出未能即時更新的問題
* `優化` 對齊宿主/插件 v2 合約, 能力清單與 plugin-only 運行時職責邊界
* `優化` 將 Node.js 示例, TypeScript 類型聲明, 項目嚮導, 運行時默認值及宿主對齊校驗統一歸屬插件倉庫, 移除宿主側 Gradle 開關與重複開發資產

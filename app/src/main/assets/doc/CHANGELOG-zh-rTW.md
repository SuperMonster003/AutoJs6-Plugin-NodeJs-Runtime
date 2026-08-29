# v1.2.0

###### 2026/08/29

* `新增` 新增 module-source provider v3, 透過綁定精確位元組數與 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逸出, 符號連結和歧義, 並保留主機 TypeScript 診斷與 Source Map 堆疊對應
* `新增` 在 Android 應用程式權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 控管
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 對應並統一產生/匯入堆疊框架
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修正循環 re-export 中可變匯出未即時更新的問題
* `優化` 對齊宿主/外掛 v2 合約, 能力清單與 plugin-only 執行階段職責邊界
* `優化` 將 Node.js 範例, TypeScript 型別宣告, 專案精靈, 執行階段預設值及宿主對齊驗證統一歸屬外掛儲存庫, 移除宿主端 Gradle 開關與重複開發資產

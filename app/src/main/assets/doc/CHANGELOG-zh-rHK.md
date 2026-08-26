# v1.2.0

###### 2026/08/26

* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `優化` 對齊宿主/插件 v2 合約, 能力清單與 plugin-only 運行時職責邊界
* `優化` 將 Node.js 示例, TypeScript 類型聲明, 項目嚮導, 運行時默認值及宿主對齊校驗統一歸屬插件倉庫, 移除宿主側 Gradle 開關與重複開發資產

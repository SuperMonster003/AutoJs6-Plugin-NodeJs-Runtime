# v1.2.0

###### 2026/08/25

* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `優化` 對齊宿主/插件 v2 合約、能力清單、示例鏡像與 plugin-only 運行時職責邊界

# v1.4.0

###### 2026/09/10

* `新增` MediaInfo 查詢支援從 0 開始的 streamNumber, countGet 串流計數以及用於單位, 說明和可讀名稱的 infoKind; Rhino 和 Node 保持預設第 1 條串流的 TEXT 查詢, 並協商外掛擴充能力
* `修復` MediaInfo 與圖片檔案路徑支援絕對路徑, 父目錄和合法檔名; 錄音輸出同步交由更新後的宿主依 Android 檔案權限處理
* `修復` 橋接能力未宣告錯誤直接提示缺少的 node.permissions, 不再要求僅作診斷且不授予權限的 pro_compat_opt_in profile
* `修復` 專案精靈不再將一般 fs 絕對路徑和父目錄路徑誤報為 FS_OUTSIDE_SCOPE, 實際檔案存取交由 Android 判斷
* `優化` 提供 Android 事件與 3 秒錄音獨立專案, 補齊截屏, OCR, 實體按鍵和 MediaInfo 的專案宣告及人工驗收步驟
* `優化` 截屏找圖, 屏幕 OCR 與 3 秒 AAC 錄音完成真機人工驗收, 對應範例及重用相同呼叫鏈的 Pro 對齊片段轉為穩定狀態
* `優化` 事件驗收範例提示暫時關閉宿主音量加停止快捷鍵; 配套宿主讀取 project.json 的 node.timeoutMs, 避免較長等待腳本在約 5 秒後逾時

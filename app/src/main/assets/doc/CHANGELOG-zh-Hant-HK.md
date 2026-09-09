# v1.4.0

###### 尚未發佈

* `修復` MediaInfo 與圖片檔案路徑支援絕對路徑, 父目錄和合法檔名; 錄音輸出同步交由更新後的宿主依 Android 檔案權限處理
* `修復` 橋接能力未宣告錯誤直接提示缺少的 node.permissions, 不再要求僅作診斷且不授予權限的 pro_compat_opt_in profile
* `修復` 專案精靈不再將一般 fs 絕對路徑和父目錄路徑誤報為 FS_OUTSIDE_SCOPE, 實際檔案存取交由 Android 判斷
* `優化` 提供 Android 事件與 3 秒錄音獨立專案, 補齊截屏, OCR, 實體按鍵和 MediaInfo 的專案宣告及人工驗收步驟

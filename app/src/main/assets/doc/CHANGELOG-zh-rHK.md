# v1.3.0

###### 尚未發佈

* `新增` Node.js 橋訂閱透過既有回呼推送感測器, WebSocket, UI, 懸浮窗與輸入事件, 提供 on/once/off 監聽, 有界事件佇列及 drainEvents 相容
* `新增` Node.js 執行階段支援可選 idleExitMs 閒置退出與後續指令碼重新連線, 提供 idleForMs 診斷, 預設保持常駐
* `新增` 全域 fetch, Request, Response, Headers, FormData 與 WebSocket 預設使用 Node 原生 Web API; autojs6:fetch 與 autojs6:websocket 明確模組繼續提供宿主網絡堆疊
* `新增` 新增具檔案邊界檢查的原生 node:sqlite, 支援 CRUD, 交易與備份; 提供原生 node:test 報告器及可運行測試範例, 離線 npm corpus 擴展 zod, cheerio, date-fns, mqtt 與 ws
* `新增` process.stdin / readline 接收主控台輸入, autojs6:host 接收執行級 JSON 訊息, v3 postMessage 交易相容舊宿主
* `新增` Node.js 螢幕擷取工作階段接入 Android 授權和既有前景服務, 支援圖片控制代碼, PNG/JPEG/WebP 儲存及停止或指令碼結束時清理
* `新增` Node.js 圖片控制代碼支援裁剪, 縮放, 灰階, 閾值, 找圖及找色, 重用宿主圖像後端, 傳回獨立圖片並在執行結束時清理
* `新增` Node.js image.toBytes 透過檔案描述符將 PNG 或 RGBA 像素傳入原生 Buffer, 支援 JNI 與檔案橋接, 在使用後, 逾時或執行結束時回收附件
* `新增` Node device 介面提供即時建置, 螢幕, 電池和記憶體資訊, 亮度控制及定時保持螢幕開啟; media 支援設定音訊串流音量, 遵循 Android 權限
* `新增` autojs6:events 透過推送回呼觀察 Android 通知, 外部 Toast, 無障礙按鍵及螢幕和電池廣播, 使用明確權限並自動清理訂閱; Node 原生 events 保持相容
* `修復` 修復長駐指令碼的橋接會話持續累積請求與回應記錄的問題, 清理已完成請求, 僅保留最近 32 條回應並限制診斷體積
* `修復` 原生非同步任務完成前提前產生成功結果, 導致非同步錯誤與後續退出碼遺失的問題; 終態改為跟隨 Node 事件迴圈最終退出
* `優化` 即時橋預設使用 JNI/Binder 直通並經 Node 事件迴圈返回回應, 降低呼叫延遲, 保留可選檔案回退與待處理呼叫上限
* `優化` Node.js stream, crypto, timers, util, node:test 等內建模組恢復原生匯出, 保留檔案系統邊界與宿主目錄策略
* `優化` 原生 worker 預設按 CPU 並行度運行 (最多 8 個), 繼承執行的網絡與檔案開關, 支援請求級資源上限且池任務預設不設逾時; CPU 與 WASM 範例改為真實多執行緒執行
* `優化` 維持原生 WASI 停用以保留檔案系統邊界, 移除兩個 disabled WASI 範例; 一般 WebAssembly 與 WASM worker 繼續可用
* `優化` 螢幕 OCR 範例申請 Android 擷取授權並辨識真實圖片控制代碼; OCR 或條碼插件不可用時傳回可讀 unavailable 錯誤, 辨識失敗仍按錯誤處理

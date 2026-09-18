******

### 發行歷史

******

# v1.5.5

###### 2026/09/17

* `修復` runtimeInfo 回報的能力目錄版本與摘要改由 runtime kit 派生, 修正 1.5.4 中仍為 1.5.0 的舊值
* `修復` worker_threads 按 Node 語意支援 new Worker(code, { eval: true }), 不再把程式碼字串當作指令碼路徑
* `優化` TypeScript 宿主編譯路由在能力目錄中轉為 stable, 刪除已退役的 legacy stripping 中繼資料; 生命週期目錄只保留可執行的執行模式, 移除 packaged_long_running 啟動面與 node_sandboxed / worker_computation 保留名; 傳輸、准入與取消中繼資料按實際執行階段對齊
* `優化` autojs6:profile 診斷物件按實際執行階段描述, 不再沿用歷史 partial / reserved / deferred 文案: Android 權限約束的檔案存取、process 子集、worker 通道、程序集區以及 WASI / native addon 決策
* `優化` 範例清點: packaged-esm、packaged-dynamic-import、require-esm、wasm-basic、wasm-plugin 與 desktop-parity-suite 轉為 stable; 兩個 parity 套件改為真正逐條執行片段而非列印目錄文案; compile-cache 標為不適用, 刪除只列印中繼資料的 package-install 範例
* `優化` 模組載入與 Node 一致地交給 Android 檔案存取: require、import 與 Worker 接受絕對路徑、上層目錄目標與 file: URL (/proc、/sys、/dev 仍拒絕); node_modules 查找仍錨定工作區
* `優化` Worker 訊息與 fs 監視器按 Node 原生限制執行: 移除 64 KB 訊息與 32 條排隊上限, 以及 16 個監視器 / 每秒 64 事件配額; 僅受 Android 記憶體約束
* `優化` worker 內 process.exit() 按 Node 語意只結束該 worker 執行緒, process.getBuiltinModule() 走 worker builtin 名單, 不再整體停用
* `優化` worker 內 bare 套件名按 Node 語意經工作區 node_modules 解析 (exports 條件、main、index、套件自引用), 不再一律拒絕
* `優化` worker 內動態 import() 經 worker 的 partial ESM 載入器執行 (相對/絕對路徑、file: URL、工作區套件、builtin 名單、with { type: "json" }), 不再拒絕
* `優化` host-events 範例在實體按鍵與通知存取人工驗收通過後轉為 stable
* `優化` scheduled-node-task 範例列印生命週期策略並可保留定時任務供 WorkManager 實跑; scheduled 執行模式補齊外掛側回歸
* `優化` scheduled 執行模式在宿主 WorkManager 定時執行器經外掛實跑通過後, 能力目錄狀態轉為 available
* `優化` media.play() 回傳經宿主指令碼音樂服務播放的工作階段物件 (pause/resume/seekTo/stop/status), 新增 media_store 模組提供受限的 MediaStore capabilities/query/get/insert/update/delete/scanFile/exportFile, 分別由 media.playback / media.library / media.library.mutate 能力把關; autojs6:compat.media 補上 Rhino 風格的 playMusic 等別名; 均需配套宿主建置
* `優化` media-playback / media-library 範例經宿主媒體 provider 合併後的人工驗收轉為 stable; 能力目錄快照 1.5.5 發布到 releases/nodejs-capability-catalog, 宿主對齊任務改為對照該快照
* `優化` fs 包裝層移除自設的選項攔截: 串流的 fs / 繼承的 fd / flags 選項、watch({ recursive: true })、非同步 cp filter (cpSync 保持 Node 的 ERR_INVALID_RETURN_VALUE)、絕對路徑 / 父目錄 / 字面 '!' glob 模式與 exclude 陣列、readableWebStream 的 type / encoding 以及 Stats / Dirent / Dir 建構器均按 Node 24 原生語意處理; filesystemProfile.advancedApis.recursiveWatch 回報 native
* `優化` 遞迴 readdir / opendir 交給原生 (移除 4096 條目上限與逐條 realpath 校驗; readdir('/') 與 Node 一樣列出 proc/sys/dev 名稱), readlink / chmod / chown / utimes 接受絕對路徑且 chmod 跟隨符號連結, fs 策略錯誤碼收斂為 ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (硬邊界, 與 loader 一致) / ERR_AUTOJS6_FS_SCOPED_PATH, 一般 fs 失敗只保留 Node 碼
* `優化` 執行階段移除自設的模組來源預算 (單模組 16 MiB、總量 64 MiB、8192 個模組、provider 請求計數), CommonJS 進入點的 __filename / require.main.filename / process.argv[1] 與 Node 一樣為絕對路徑且 require.main.id 為 '.', fs.mkdtemp* 交給原生: 回傳呼叫者的前綴寫法加後綴並支援所要求的編碼, 前綴父目錄為符號連結時不再拒絕
* `優化` node:sqlite 把 SQL 檔案操作交給原生 SQLite: 字面量檔名的 ATTACH (含 file: URI) 改經 SQLite authorizer 按 /proc、/sys、/dev 邊界校驗而不再掃描 SQL 文字, VACUUM INTO 的目標經 SQLite 內部 ATTACH 接受同一邊界校驗, 目錄 PRAGMA 不再攔截, file: URI 字串與空暫存庫可以開啟, setAuthorizer() 與邊界檢查複合 (僅繫結參數或運算式給出檔名的 ATTACH 仍被拒絕)

# v1.5.4

###### 2026/09/17

* `修復` 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、淘汰警告和錯誤; Node 上游 API 穩定性與終端預設行為不變
* `優化` 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援

# v1.5.3

###### 2026/09/16

* `優化` Android 17 區域網路授權統一移至外掛程式中心啟用流程及外掛程式設定, 不再提供啟動器授權頁面; 未獲授權時保持關閉並靜默略過自動啟動
* `優化` 支援 Android 17 (SDK 37), 提供外掛獨立的本機網路權限控制及復原指引

# v1.5.2

###### 2026/09/15

* `優化` 將 compileSdk 提升到 37 (Android 17), targetSdk 保持 36, 待依賴目標版本的行為驗證後再提升

# v1.5.1

###### 2026/09/15

* `修復` Node 自身的致命錯誤與警告文字在 Android 上除 logcat 外同時寫入真實 stderr, 終端機可看到未捕捉的例外而非靜默結束
* `優化` `libnode.so` 以 `--with-intl=small-icu` 重建: `Intl` 與正規表示式的 Unicode 屬性跳脫 (`\p{...}`) 可用 (僅英文區域資料, `NODE_ICU_DATA` 可載入完整 ICU 資料檔), corepack 因此可在 AutoJs6 終端機中執行 pnpm 11 與 Yarn Berry

# v1.5.0

###### 2026/09/14

* `新增` 多入口終端機啟動器 `libnodexe.so` (node / npm / npx / corepack / yarn / pnpm), 各 ABI 均打包, 帶 `DT_RUNPATH $ORIGIN` 與 16 KB 頁對齊
* `新增` 取自官方 Node.js 24.21.0 發行包的 npm 11.19.0 與 corepack 0.36.0 資產, 透過 `NODE_CLI_*` manifest meta-data (schema 1) 宣告, 並以 `nodeCli` 能力鏡像到 runtimeInfo / PluginInfo

# v1.4.2

###### 2026/09/13

* `修復` 外掛資訊只回報目前安裝套件內實際存在的原生 ABI
* `修復` 外掛中繼資料的建置日期固定使用英文, 不受建置機器語言影響
* `修復` 發行套件與封存檔的版本資訊保持一致
* `修復` Android 7 工作目錄檔案相容性, 保留檔案描述符隔離

# v1.4.1

###### 2026/09/13

* `優化` 建置階段校驗 64 位原生函式庫的 16 KB 頁面大小對齊, 檢查 manifest 契約並輸出 JSON 報告
* `優化` 宿主啟用, 外掛中繼資料, 多語言文件與簽章發佈彙整遵循統一外掛規範

# v1.4.0

###### 2026/09/10

* `新增` MediaInfo 查詢支援從 0 開始的 streamNumber, countGet 串流計數以及用於單位, 說明和可讀名稱的 infoKind; Rhino 和 Node 保持預設第 1 條串流的 TEXT 查詢, 並協商外掛擴充能力
* `修復` MediaInfo 與圖片檔案路徑支援絕對路徑, 父目錄和合法檔名; 錄音輸出同步交由更新後的宿主依 Android 檔案權限處理
* `修復` 橋接能力未宣告錯誤直接提示缺少的 node.permissions, 不再要求僅作診斷且不授予權限的 pro_compat_opt_in profile
* `修復` 專案精靈不再將一般 fs 絕對路徑和父目錄路徑誤報為 FS_OUTSIDE_SCOPE, 實際檔案存取交由 Android 判斷
* `優化` 提供 Android 事件與 3 秒錄音獨立專案, 補齊螢幕擷取, OCR, 實體按鍵和 MediaInfo 的專案宣告及人工驗收步驟
* `優化` 螢幕擷取找圖, 螢幕 OCR 與 3 秒 AAC 錄音完成實機人工驗收, 對應範例及重用相同呼叫鏈的 Pro 對齊片段轉為穩定狀態
* `優化` 事件驗收範例提示暫時關閉宿主音量加停止快捷鍵; 配套宿主讀取 project.json 的 node.timeoutMs, 避免較長等待指令碼在約 5 秒後逾時

# v1.3.0

###### 2026/09/09

* `新增` Node.js 橋訂閱透過既有回呼推送感測器, WebSocket, UI, 懸浮窗與輸入事件, 提供 on/once/off 監聽, 有界事件佇列及 drainEvents 相容
* `新增` Node.js 執行階段支援可選 idleExitMs 閒置退出與後續指令碼重新連線, 提供 idleForMs 診斷, 預設保持常駐
* `新增` 全域 fetch, Request, Response, Headers, FormData 與 WebSocket 預設使用 Node 原生 Web API; autojs6:fetch 與 autojs6:websocket 明確模組繼續提供宿主網路堆疊
* `新增` 新增具檔案邊界檢查的原生 node:sqlite, 支援 CRUD, 交易與備份; 提供原生 node:test 報告器及可執行測試範例, 離線 npm corpus 擴充 zod, cheerio, date-fns, mqtt 與 ws
* `新增` process.stdin / readline 接收主控台輸入, autojs6:host 接收執行級 JSON 訊息, v3 postMessage 交易相容舊宿主
* `新增` Node.js 螢幕擷取工作階段接入 Android 授權和既有前景服務, 支援圖片控制代碼, PNG/JPEG/WebP 儲存及停止或指令碼結束時清理
* `新增` Node.js 圖片控制代碼支援裁剪, 縮放, 灰階, 閾值, 找圖及找色, 重用宿主圖像後端, 傳回獨立圖片並在執行結束時清理
* `新增` Node.js image.toBytes 透過檔案描述符將 PNG 或 RGBA 像素傳入原生 Buffer, 支援 JNI 與檔案橋接, 在使用後, 逾時或執行結束時回收附件
* `新增` Node device 介面提供即時建置, 螢幕, 電池和記憶體資訊, 亮度控制及定時保持螢幕開啟; media 支援設定音訊串流音量, 遵循 Android 權限
* `新增` autojs6:events 透過推送回呼觀察 Android 通知, 外部 Toast, 無障礙按鍵及螢幕和電池廣播, 使用明確權限並自動清理訂閱; Node 原生 events 保持相容
* `新增` Node app 支援 Android 服務啟動, 廣播傳送和已安裝應用查詢; dialogs 支援文字輸入, 單選, 多選及隨腳本清理的進度視窗, keys 提供無障礙系統操作
* `新增` Node recorder 支援具備麥克風權限, 前景通知, 時長上限與腳本清理的 AAC 錄音; ui.overlay 支援宣告式屬性更新, 拖曳與事件推送
* `新增` Node 腳本可在兩個獨立執行階段處理程序中並行執行, 共用 FIFO 佇列並按執行工作路由取消和輸入
* `修復` 修復常駐指令碼的橋接工作階段持續累積請求與回應記錄的問題, 清理已完成請求, 僅保留最近 32 筆回應並限制診斷大小
* `修復` 原生非同步任務完成前提前產生成功結果, 導致非同步錯誤與後續退出碼遺失的問題; 終態改為跟隨 Node 事件迴圈最終退出
* `修復` OpenSSL STORE 金鑰 URL 繞過檔案系統限制的問題 (請透過 node:fs 讀取金鑰位元組)
* `優化` 即時橋預設使用 JNI/Binder 直通並經 Node 事件迴圈返回回應, 降低呼叫延遲, 保留可選檔案回退與待處理呼叫上限
* `優化` Node.js stream, crypto, timers, util, node:test 等內建模組恢復原生匯出, 保留檔案系統邊界與宿主目錄策略
* `優化` 原生 worker 預設依 CPU 平行度執行 (最多 8 個), 繼承執行的網路與檔案開關, 支援請求層級資源上限且池任務預設不設逾時; CPU 與 WASM 範例改為實際多執行緒執行
* `優化` 維持原生 WASI 停用以保留檔案系統邊界, 移除兩個 disabled WASI 範例; 一般 WebAssembly 與 WASM worker 繼續可用
* `優化` 螢幕 OCR 範例申請 Android 擷取授權並辨識真實圖片控制代碼; OCR 或條碼插件不可用時傳回可讀 unavailable 錯誤, 辨識失敗仍按錯誤處理
* `優化` Node 腳本支援協商式非同步啟動, 長駐執行釋放 Binder 執行緒, 終態回呼完成後回傳工作區, 相容舊同步宿主
* `依賴` 升級 Node.js 24.5.0 → 24.21.0, 三個 ABI 均使用自建 Android 原始碼產物

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

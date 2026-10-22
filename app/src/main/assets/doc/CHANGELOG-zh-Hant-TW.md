******

### 發行歷史

******

# v1.32.0

###### 2026/10/22

* `新增` Node.js/Bun/Worker 的 ai.agent 任務控制與事件, 註冊指令碼上下文和結果回傳, 以及封裝應用程式的獨立授權

# v1.31.0

###### 2026/10/22

* `新增` Node.js, Bun 與 Worker 的 AI 文字生成, 串流結果, 模型目錄及持續工作階段, 支援取消與 3-Stone AI 對打包應用程式的個別授權

# v1.30.0

###### 2026/10/21

* `新增` Node.js, Bun 與 Worker 的 Flow within/scope 範圍繼承, 羅盤導覽和結果投影, 以及 press/swipe/gesture/gestures 手勢多載

# v1.29.0

###### 2026/10/21

* `新增` Node.js, Bun 及 Worker 的 Flow 進階點擊, 捲動, 輸入, 清單擷取與真實事件等待, 支援監聽就緒, 快照驗證和取消

# v1.28.0

###### 2026/10/21

* `新增` Node.js, Bun 及 Worker 的 flow 非同步鏈與條件/穩定性等待, 支援分支取消, 單一步驟重試及宿主介面快照

# v1.27.0

###### 2026/10/21

* `新增` Node.js/Worker: 郵件帳戶連線, 郵件操作, 附件與信箱監聽, 支援取消及 Worker 結束清理

# v1.26.0

###### 2026/10/21

* `新增` Node.js/Worker: EPUB 書籍讀取, 資源匯出, 搜尋及含事件的閱讀器工作階段

# v1.25.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的 speech 語音辨識與模型管理介面, 支援 WAV/PCM, 聽寫與喚醒工作階段, 模型安裝/匯入, 語音合成及取消清理

# v1.24.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的文字轉語音介面, 支援引擎與音色查詢, 朗讀, 靜音工作, 音訊檔案合成和工作事件

# v1.23.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的 Java 回呼代理, 子類別產生和 JAR/DEX 載入, 支援 Promise 回傳值, 父類別呼叫及執行結束清理

# v1.22.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的 Java/Android 物件互操作, 支援公開建構函式和成員, 陣列, 精確 long 值, 明確多載及應用程式 Context 控制代碼
* `修復` Node.js 宿主模組包含 java.new 等保留字成員時 ESM 匯入失敗的問題

# v1.21.0

###### 2026/10/19

* `新增` Node.js/Bun 的 Android 物件輔助介面及 Worker 封裝, 支援 Paint, ColorStateList, 主題色和 FileProvider URI
* `新增` Node.js/Bun 的持久日曆與 IntentTask API, 支援每日, 每週, 每月, 每年, 單次, 倒數計時和廣播工作

# v1.20.0

###### 2026/10/19

* `新增` Bun 與 Node.js Worker 的裝置控制及對話方塊 API, 支援亮度, 音量和獨立視窗清理

# v1.19.0

###### 2026/10/19

* `新增` Bun 與 Node.js Worker 的宿主事件及通知 API, 支援獨立訂閱與通知清理

# v1.18.0

###### 2026/10/19

* `新增` Bun, Node.js 及 Worker 的宿主 Shell API, 支援串流輸入輸出, Shizuku 授權與程序清理

# v1.17.0

###### 2026/10/19

* `新增` Bun 與 Node.js Worker 的感測器取樣及訂閱 API, 支援取消和監聽器自動釋放
* `修復` 感測器單次取樣取消, 輪詢失敗及宿主銷毀時排隊註冊可能遺留監聽器的問題

# v1.16.0

###### 2026/10/19

* `新增` Node.js Worker 與 Bun 的 database/sqlite API, 支援參數化查詢, 交易和資料庫持久化
* `修復` database/sqlite.open 的物件參數忽略 timeoutMs/signal, 並將空物件當成有效資料庫名稱的問題

# v1.15.0

###### 2026/10/18

* `新增` Node.js/Bun 的 WebView 螢幕共享適配, 前景服務背景錄音及原生背景攝影機錄影, 包括 Node Worker 封裝

# v1.14.0

###### 2026/10/18

* `新增` WebView getUserMedia/MediaRecorder 串流音訊與影片, 支援等待網頁 Promise, 明確申請 Android 媒體權限及 Worker 生命週期管理

# v1.13.0

###### 2026/10/18

* `新增` Node.js/Worker: WebView 影片拍攝與前景 AAC/M4A 錄音, 支援媒體參數, 可設定選擇等待時限及暫存檔案清理

# v1.12.0

###### 2026/10/18

* `新增` Node.js/Worker: WebView 完整照片拍攝, 儲存和目錄選擇, 文件位元組讀取, blob/data 分塊下載及所選位置輸出

# v1.11.0

###### 2026/10/18

* `新增` Node.js/Worker: WebView HTTP(S) 下載, 系統檔案選擇及非同步 WebViewClient/WebChromeClient 回呼, 支援原生導覽和資源規則

# v1.10.0

###### 2026/10/18

* `新增` Node.js/Worker: WebView 頁面載入與 JavaScript 回傳值, Drawer 側欄, Toolbar 導覽與選單, ViewPager 頁面及非同步原生事件

# v1.9.0

###### 2026/10/18

* `新增` Node.js 支援常用 XML 表單與版面配置元件及原生 View 非同步屬性代理, 包含循序賦值, 錯誤復原與 Worker 呼叫

# v1.8.0

###### 2026/10/18

* `新增` Node.js 支援執行所屬 View 控制代碼, 非同步 AndroidView 工廠與 XML Compose 容器掛載, 同步支援 Worker 清理及打包應用程式

# v1.7.0

###### 2026/10/18

* `新增` Node.js 專案和指令碼可打包為獨立簽章的應用程式, 執行時呼叫外部 Node.js Runtime 外掛程式, 外掛程式不可用時顯示安裝指引
* `新增` screen_metrics 和 autojs6:compat 提供依據目前螢幕的非同步 cX/cY/cXy/cYx 及各次執行獨立的換算基數, 並補充覆蓋檢查和 ESM 範例
* `新增` Node.js 和 Bun 支援非同步 OpenCC 轉換及常用影像編解碼, 像素讀取, 變換和顏色搜尋; Bun 影像檔案存取獨立授權
* `新增` Node.js Worker 支援透過父執行環境呼叫 OpenCC 與影像模組, 包含 CJS, ESM, 巢狀 Worker 和影像二進位傳輸
* `新增` Node.js Worker 支援 pinyin, pinyin4j, 主控台視窗和座標換算, 共用父執行狀態並保留原生輸出串流
* `新增` Node.js 主執行緒與 Worker 支援 H.264 MP4 螢幕錄影, 麥克風/播放/混合 AAC 音訊, 錄製期間並行截圖及 Worker 未儲存錄影清理
* `新增` Node.js images 支援進階濾鏡, 色彩空間轉換, 多點尋色, 圓形偵測, 相似度指標及 URL 載入
* `新增` Node.js images 支援 SIFT/ORB 特徵擷取與比對, 回傳場景座標和 ObjectFrame 幾何資訊, 並管理執行內的 Mat/特徵控制代碼
* `新增` Node.js images 支援 PNG 量化和壓縮, 提供品質/記憶體度量, 二進位結果及檔案描述元輸出
* `新增` Node.js UI 與 Compose 支援原生版面配置, 資料元件, 響應式狀態及視窗事件, 提供獨立授權與視窗自動清理
* `修復` Pinyin/Pinyin4j 改為非同步呼叫實際宿主外掛, 移除小字典替代實作; Android 主控台操作透過宿主執行並回傳 Promise; 補齊無障礙手勢和 input_observer 型別宣告
* `修復` Node.js images.captureScreen 將授權選項誤傳給截圖處理入口, 導致一般截圖和錄製期間截圖被拒絕的問題
* `依賴` 為 Node.js 24.21.0 Android 執行階段回補 OpenSSL 3.5.9 安全性修正

# v1.6.0

###### 2026/10/09

* `新增` 外掛設定頁支援 1-8 個平行執行槽 (預設 2) 與排隊上限 (預設不限), 超額指令碼按 FIFO 等待, 設定調整保留已接受的工作, idleExitMs 可回收閒置執行程序
* `修復` 專案精靈不再將已支援的原生相依套件, npm 安裝指令碼, package exports 和 Inspector 誤判為不可用, 保留宿主權限宣告檢查
* `優化` fs, fs/promises 及 node:sqlite 模組改為直接使用 Node.js 原生實作, 移除執行階段自設的 /proc, /sys, /dev 拒絕邊界與路徑檢查包裝層, 檔案可達性完全由 Android 權限決定, 非同步檔案 API 經 libuv 執行緒池執行而不再阻塞事件迴圈, 路徑錯誤回傳 Node.js 原生錯誤碼 (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH 及 ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED 不再由檔案及資料庫操作產生)
* `優化` process.cwd 與 process.chdir 改為 Node.js 原生行為, 指令碼執行開始時以原生 chdir 進入工作目錄, 相對路徑由 Node.js 自行解析
* `優化` node:sqlite 的 ATTACH (含繫結參數及運算式形式的檔名), VACUUM INTO, 目錄 PRAGMA 及 setAuthorizer 均為 SQLite 原生行為, enableLoadExtension 與 allowExtension 遵循 Node.js 原生狀態檢查
* `優化` Node.js 執行庫改用完整 ICU 資料建置 (--with-intl=full-icu), Intl 及各地區的格式化行為與桌面版 Node.js 一致, 不再需要 NODE_ICU_DATA
* `優化` crypto.createPrivateKey 及簽章 API 支援 file: URL 形式的 OpenSSL STORE 金鑰輸入 (移除 Android 建置中的停用修補)
* `優化` 全部 Node.js 內建模組 (含 cluster, domain, repl, trace_events, wasi, constants, node:sea 等) 按 Node 自身的 module.builtinModules 清單直接回傳原生匯出, require, require.resolve, import, process.getBuiltinModule 與 module.isBuiltin 行為與桌面版一致, 僅保留 worker_threads, child_process, inspector 及原生網路模組的請求級閘控; node:wasi 隨 /proc 邊界的刪除一併放開 (trace_events 在目前未啟用 tracing 的執行庫中與 Node 一樣擲出 ERR_TRACE_EVENTS_UNAVAILABLE)
* `優化` process 僅保留 exit 的生命週期對應; dlopen, abort, kill, umask, binding, report, features 及 POSIX 身分呼叫使用 Node 原生實作; os 為原生模組, homedir 與 tmpdir 遵循 HOME/TMPDIR, userInfo 回傳 Android 應用程式使用者
* `優化` 外掛程式資訊 (INFO) 與執行階段診斷的 supportedAbis 改為讀取建置期寫入資訊清單的 org.autojs.plugin.contract.SUPPORTED_ABIS meta-data (需要 AutoJs6-Gradle-Platform-Versions 1.9.0 起的 nativeAlignment 外掛程式), 表示外掛程式建置支援的全部 ABI 而非已安裝分包; meta-data 缺失時回退為已安裝套件內實際打包的 ABI, 手寫的 ABI 常數已刪除
* `優化` 模組載入改為 Node.js 原生 CommonJS / ESM loader: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta 與 require(esm) 全部為 Node 原生行為, 入口按 Node 主模組方式執行 (require.main === module, __filename / process.argv[1] 為 realpath); 執行階段只註冊 module.registerHooks 提供 autojs6:* 執行階段模組與相容 shim (autojs6: scheme, 同名 npm 套件仍優先於 shim)、請求級 builtin 閘門、請求隨附 / provider 具現化 / TypeScript 快照的模組原始碼與入口內嵌原始碼; vm.SourceTextModule 自管 linker, 自訂 resolver, 動態 import 改寫與 eval / Function 守衛全部移除, esmEnabled / dynamicImportEnabled 請求鍵不再生效
* `優化` 原生 Worker 支援巢狀執行, MessagePort API, stdio, argv, execArgv 與預載入繼承; 移除外掛自設的 Worker 數量和啟動時限, 請求 resourceLimits 僅作可覆寫的預設值, WorkerPool 預設不限制排隊數量或工作時長; Inspector 工作階段遵循執行請求與目前組建支援
* `優化` Worker 與巢狀 Worker 可透過父執行緒 MessagePort 使用 autojs6:bridge.callAutoJs, 沿用宿主 broker 的權限檢查, 錯誤, 逾時與取消; worker-host-bridge 範例示範讀取實際宿主裝置資訊, 並提供橋接 API 的 TypeScript 宣告
* `優化` 原始 .ts / .mts / .cts 入口與匯入模組仍須由宿主 Compiler 編譯為 JavaScript; 保留快照對應與 provider v3 按需編譯。模組載入失敗使用 Node 原生錯誤碼, 原 AutoJs6 ESM 與動態匯入錯誤碼退役。
* `優化` 主腳本與 Worker 可透過 Node 載入 Android native addon, process.dlopen 與 .node 副檔名恢復原生行為, 不設外掛自有簽章或雜湊允許清單; native-addon 範例提供三 ABI 的 16 KB 對齊 Node-API 產物和離線 NDK/CMake 建置; 錯誤 ABI 或損壞二進位檔回傳 ERR_DLOPEN_FAILED 等 Node 錯誤, 裝置端不隨附 NDK 或 Python
* `優化` Inspector 在宿主明確允許後支援 Debug 和 Release 建置; inspector 與 inspector/promises 保留 Node 原生模組物件, 主腳本和 Worker 可使用 Session, 監聽僅綁定 127.0.0.1 且不支援阻塞等待; 未開啟時 url() 回傳 undefined, 啟用和 Session 建立保持關閉
* `優化` package_manager 的 install/update/remove 支援 npm 套件規格, 在工作區呼叫隨附 npm; npm.cli/exec/runScript/pack 使用真實命令列, registry/代理/生命週期選項遵循 npm 與專案 .npmrc, 輸出串流回傳並保留 npm 錯誤碼; CLI 資產僅在首次呼叫時準備, 物件請求保留原宿主本機庫契約
* `優化` 減少指令碼啟動時不必要的 Web API 初始化, 工作區與 Worker 預設啟用 Node 原生可攜式模組編譯快取, 快取可跨工作區副本重用並由 Node 自動使變更原始碼失效
* `優化` 執行階段能力中繼資料依實際行為描述, 清除暫定狀態標記; UI 等依賴宿主的範例明確所需提供者, 移除未規劃的外掛市集與更新管道欄位

# v1.5.7

###### 2026/10/05

* `優化` 外掛程式中心圖示採用統一工作台調整後的尺寸, 位置, 明暗圖稿與圓形底色, 保留可重建原稿和參數
* `優化` Android 系統應用程式資訊圖示與圖示工作台共用圖稿及明暗底色, 保留外掛程式中心透明圖稿與現有啟動器選項

# v1.5.6

###### 2026/09/19

* `新增` 執行階段槽處理程序發生 Node.js 致命錯誤 (JavaScript 堆積記憶體耗盡、內部檢查失敗) 時, 現在會在外掛快取目錄留下 Node 自帶的診斷報告 (crash/<executionId>.json, 於中止前寫出, 不安裝訊號處理器): 失敗結果在 slotExit.crash 與錯誤訊息中帶上事件、堆積用量、JS 堆疊與報告路徑 (例如 CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), getRuntimeInfo 以 lastCrash 公開最新報告 (排程處理程序重啟後仍可讀); 新增請求選項 maxOldGenerationSizeMb 為單次執行設定 JS 堆積上限 (V8 老生代, 單位 MB), 回歸測試也藉此重現該當機
* `修復` 修正 require 大型 CommonJS 模組時執行階段槽處理程序因 V8 堆積記憶體耗盡而中止 (回報為 CRASH_NATIVE / SIGABRT) 的問題: 載入器的註解與字串遮罩逐字元串接, 22 MiB 模組需要約 700 MB 堆積記憶體; 現改為按連續區段輸出 (結果相同, 約 44 MB), CommonJS 的 import / export 掃描也改為在候選位置間跳轉而非逐字元檢測
* `修復` AGP 9.1 建置時的 SDK XML v4 解析警告及 JVM 單元測試組裝工作誤觸發 APK 原生程式庫對齊檢查的問題 (共用建置外掛 1.8.3)

# v1.5.5

###### 2026/09/18

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
* `優化` 橋配額交給宿主: 超出 autojs6:bridge-limits 中 maxPendingBridgeCalls 視窗的呼叫改為依先進先出排隊等待而不再以 ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT 失敗, 執行階段不再自行限制圖像控制代碼數、受控 fetch 並行數與受控 WebSocket 連線數 (宿主 broker 繼續執行其原則), require('fetch').policy.maxConcurrentRequests 與 require('websocket').policy.maxConnections 欄位退役
* `優化` 橋接 fetch / WebSocket / axios facade 的硬上限交給宿主: 逾時、回應主體大小、重新導向次數、訊息與佇列大小及 HTTP 方法原樣傳給宿主提供者 (由宿主原則決定), 執行階段像 Node 一樣最多跟隨 20 次重新導向, policy 中退役的 hard* / 預設大小欄位改為 limitsEnforcedBy
* `優化` opendir 直接回傳 Node 原生的惰性 fs.Dir (bufferSize、encoding 與 recursive 交給原生 opendir, ENOENT / ENOTDIR / ERR_DIR_CLOSED 均為 Node 原生錯誤; 只有 dir.path 與 parentPath 保留呼叫者寫法), 執行階段刪除檔案系統可達根的守衛退役, 對根目錄的 rm / rmdir 與其他路徑一樣交給 Node 與 Android 決定 (fs 原則碼只剩 FS_NUL_BYTE 與 FS_PATH_ESCAPE)
* `優化` 宿主 provider 傳輸通道改為採用宿主經 getNativeDiagnostics() 公布的單源 / 總量 / 請求計數尺寸 (內建的 16 MiB / 64 MiB / 139264 僅作回退), 橋接 fetch 的回應主體改經檔案描述符交付 (bodyTransport "pfd"), 只受宿主 maxResponseBytes 原則約束而不再受 Binder 交易尺寸限制, 宿主回覆超過 Binder 交易尺寸時立即回報 ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (宿主分支 node-m20-2-binder-body-pfd) 而非橋逾時
* `優化` 橋接 fetch 的請求主體與 WebSocket 訊息超過 256 KiB 時改以唯讀檔案描述符 (bodyTransport / messageTransport "pfd") 而非內嵌 base64 JSON 送達宿主 (需宿主公布 bridgeRequestBinaryTransport=pfd, 宿主分支 node-m20-2-binder-body-pfd), 上行大小只受宿主請求原則 (64 MiB) 與 maxMessageBytes 約束而不再受 Binder 交易尺寸限制
* `優化` 受控 fetch 的回應物件現經 Response.url 暴露 provider 回傳的最終 URL (ResponseInit 不含 url, 此前恆為空字串)
* `優化` Java 互操作改為整體轉發到宿主的宣告式白名單 (類別 → 建構子 / 靜態與實例方法 / 欄位, 由宿主按表反射執行, 參數支援 JSON 原始值與物件控制代碼), 執行階段不再自設類別表而是把宿主公布的表讀回為 java.policy, 新增 getStatic() / describe(), 成員自身擲出的例外以 ERR_AUTOJS6_JAVA_CALL_FAILED 回報
* `優化` 執行階段模組的 npm 優先規則收窄為只涵蓋代替真實 npm 套件的模組 (axios、colors、mime、nanoid、opencc、undici): node_modules 裡的同名套件不再取代 java、fetch、websocket、device 等 AutoJs6 facade, require.resolve 與 require 同規則, 全部執行階段模組裸名可從 ESM 匯入, autojs6:profile 以 moduleResolutionProfile 公佈該規則, Rhino Packages 代理新增 getStatic() / describe()
* `優化` 執行階段槽處理程序在腳本執行中死亡 (被低記憶體殺手回收、原生崩潰、被直接終止) 時, 失敗結果改為回報系統記錄的結束原因而不再只有 DeadObjectException: 訊息形如 LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB) 或 CRASH_NATIVE (SIGABRT; see the logcat tombstone), 結果附 slotExit, getRuntimeInfo 附 lastSlotExit (Android 11+; 更低版本回報原因不可用)

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

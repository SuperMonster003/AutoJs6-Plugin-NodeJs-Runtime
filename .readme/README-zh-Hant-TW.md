<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Node.js 24.21.0 原生執行階段插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言 (Languages)

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### 簡介

******

AutoJs6 Node.js Runtime 插件為 AutoJs6 提供內嵌 Node.js 24.21.0 原生執行階段, 用於執行 Node.js 腳本和插件化執行階段任務. 在 Android 17 以上, 在 AutoJs6 外掛程式中心啟用此外掛程式前須允許存取附近的裝置. 也可在此外掛程式的設定頁面管理區域網路權限. 未獲授權時外掛程式保持關閉, 自動啟動將靜默略過. 此權限屬於外掛程式本身, 與 AutoJs6 的授權相互獨立.

******

### 功能

******

- 提供 `nodejs` 插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`.
- 透過 `org.autojs.plugin.nodejs.RUNTIME` 為宿主提供同步腳本執行和執行階段預熱.
- 支援涵蓋排隊與執行全程的指令碼逾時, 逾時傳回 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定逾時時仍可無限執行
- 預設啟用 `dgram` (UDP) 與 `http2`, 並對 `trace_events` 傳回明確的停用錯誤
- 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
- 支援 CommonJS/ESM 原始碼, 模組原始碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳.
- ESM 入口與 dynamic `import()` 使用 V8 native linker, 支援循環相依、可變匯出與 re-export live binding; CommonJS `require(esm)` 保留同步互操作邊界.
- 提供由外掛本身 Android 權限約束的桌面式檔案系統存取; `/proc`、`/sys`、`/dev` 一律由執行階段拒絕.
- 執行宿主提供的 TypeScript 編譯產物, 並可透過 provider v3 請求按需編譯執行期間建立的專案 `.ts`/`.mts`/`.cts`; 執行階段型別移除 fallback 與相容開關已刪除, direct raw dispatch 始終 fail-closed.
- 提供宿主能力代理與 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等執行階段模組.
- 附帶 `sample/nodejs` 範例專案與 `docs/HOST-API.md` 宿主 API 能力清單.
- 插件資訊, 使用說明, README 與 CHANGELOG 均支援西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體.
- 附帶多入口終端機啟動器 `libnodexe.so` 與 npm / corepack 資產, 透過 `NODE_CLI_*` manifest meta-data 宣告, 使 AutoJs6 終端機 (宿主 6.8.0+) 能在自身 uid 的 shell 中執行 `node`, `npm`, `npx`, `corepack`, `yarn` 與 `pnpm`.

******

### 使用範例

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

將插件安裝並在 AutoJs6 插件中心啟用後, 以 `"nodejs";` 指令啟動 Node.js 腳本. 更多範例位於 `sample/nodejs`.

******

### 快速上手

******

- **怎麼裝** — 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) 下載對應 ABI 的 APK (不確定就選 `universal`) 並安裝; 或本地建置 `.\gradlew.bat :app:assembleDebug` 後安裝 `app/build/outputs/apk/debug/` 下的產物. 然後在 AutoJs6 的外掛中心啟用本外掛. Android 11+ 如需存取共用儲存空間, 還要在系統設定為本外掛授予「所有檔案存取權」; 未授予時出現 `EACCES` 屬預期行為.
- **怎麼跑** — 在 AutoJs6 編輯器中新建指令碼, 首行寫 `"nodejs";`, 其餘按桌面 Node.js 寫法編寫 (支援 CommonJS/ESM/npm 純 JS 套件/網路內建模組), 點擊執行即可. 執行中即時輸出, 可隨時手動停止. raw `.ts`/`.mts`/`.cts` 目前必須先由宿主編譯為 JavaScript; 外掛不內建 `tsc`.
- **出錯了看哪裡** — 指令碼報錯時主控台顯示 JS 堆疊與一行錯誤碼 (如 `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`); 更多細節用 `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` 查看外掛程序日誌. 宿主 API 可用性以 `docs/HOST-API.md` 為準.

******

### 執行階段資料

******

- 執行階段槽位: `node24_21`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 執行階段服務動作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生執行庫: `libnode.so`, `libautojs6-node.so` 和 `libnodexe.so`.
- Intl: ICU 78, 僅含英文區域資料 (`--with-intl=small-icu`); `Intl`, 正規表示式的 Unicode 屬性跳脫以及 Node 自身寫到 stderr 的錯誤輸出均可在 AutoJs6 終端機中使用, `NODE_ICU_DATA` 可指向完整的 ICU 資料檔.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: 可存取 Android 權限允許的裝置路徑; `/proc`、`/sys`、`/dev` 為硬邊界.
- TypeScript: 接受主機產物並可透過 provider v3 按需編譯執行期間建立的專案檔案; direct raw TypeScript 回傳 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 與循環相依.
- 能力: 同步腳本執行, bundle transport, 原生內嵌執行階段, 宿主能力代理, host capability live bridge.
- 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援.
- 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、淘汰警告和錯誤; Node 上游 API 穩定性與終端預設行為不變.

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
* `優化` 橋配額交給宿主: 超出 autojs6:bridge-limits 中 maxPendingBridgeCalls 視窗的呼叫改為依先進先出排隊等待而不再以 ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT 失敗, 執行階段不再自行限制圖像控制代碼數、受控 fetch 並行數與受控 WebSocket 連線數 (宿主 broker 繼續執行其原則), require('fetch').policy.maxConcurrentRequests 與 require('websocket').policy.maxConnections 欄位退役
* `優化` 橋接 fetch / WebSocket / axios facade 的硬上限交給宿主: 逾時、回應主體大小、重新導向次數、訊息與佇列大小及 HTTP 方法原樣傳給宿主提供者 (由宿主原則決定), 執行階段像 Node 一樣最多跟隨 20 次重新導向, policy 中退役的 hard* / 預設大小欄位改為 limitsEnforcedBy
* `優化` opendir 直接回傳 Node 原生的惰性 fs.Dir (bufferSize、encoding 與 recursive 交給原生 opendir, ENOENT / ENOTDIR / ERR_DIR_CLOSED 均為 Node 原生錯誤; 只有 dir.path 與 parentPath 保留呼叫者寫法), 執行階段刪除檔案系統可達根的守衛退役, 對根目錄的 rm / rmdir 與其他路徑一樣交給 Node 與 Android 決定 (fs 原則碼只剩 FS_NUL_BYTE 與 FS_PATH_ESCAPE)
* `優化` 宿主 provider 傳輸通道改為採用宿主經 getNativeDiagnostics() 公布的單源 / 總量 / 請求計數尺寸 (內建的 16 MiB / 64 MiB / 139264 僅作回退), 橋接 fetch 的回應主體改經檔案描述符交付 (bodyTransport "pfd"), 只受宿主 maxResponseBytes 原則約束而不再受 Binder 交易尺寸限制, 宿主回覆超過 Binder 交易尺寸時立即回報 ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (宿主分支 node-m20-2-binder-body-pfd) 而非橋逾時
* `優化` 橋接 fetch 的請求主體與 WebSocket 訊息超過 256 KiB 時改以唯讀檔案描述符 (bodyTransport / messageTransport "pfd") 而非內嵌 base64 JSON 送達宿主 (需宿主公布 bridgeRequestBinaryTransport=pfd, 宿主分支 node-m20-2-binder-body-pfd), 上行大小只受宿主請求原則 (64 MiB) 與 maxMessageBytes 約束而不再受 Binder 交易尺寸限制
* `優化` 受控 fetch 的回應物件現經 Response.url 暴露 provider 回傳的最終 URL (ResponseInit 不含 url, 此前恆為空字串)
* `優化` Java 互操作改為整體轉發到宿主的宣告式白名單 (類別 → 建構子 / 靜態與實例方法 / 欄位, 由宿主按表反射執行, 參數支援 JSON 原始值與物件控制代碼), 執行階段不再自設類別表而是把宿主公布的表讀回為 java.policy, 新增 getStatic() / describe(), 成員自身擲出的例外以 ERR_AUTOJS6_JAVA_CALL_FAILED 回報

# v1.5.4

###### 2026/09/17

* `修復` 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、淘汰警告和錯誤; Node 上游 API 穩定性與終端預設行為不變
* `優化` 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援

# v1.5.3

###### 2026/09/16

* `優化` Android 17 區域網路授權統一移至外掛程式中心啟用流程及外掛程式設定, 不再提供啟動器授權頁面; 未獲授權時保持關閉並靜默略過自動啟動
* `優化` 支援 Android 17 (SDK 37), 提供外掛獨立的本機網路權限控制及復原指引

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

******

### 建置

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 建置:

```powershell
.\gradlew.bat :app:assembleRelease
```

建置參數來自 `version.properties`, 目前最低 SDK 為 24, 目標 SDK 為 36.

******

### 資源結構

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` 提供插件描述本地化; `plugin_instruction.md` 提供宿主側展示的插件使用說明. README 與 CHANGELOG 由 `.python/generate_markdown.py` 根據 JSON 來源檔案生成.

******

### 相關連結

******

- AutoJs6 文件: https://docs.autojs6.com
- Node.js 官方專案: https://github.com/nodejs/node
- Node.js 執行階段建置計劃: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

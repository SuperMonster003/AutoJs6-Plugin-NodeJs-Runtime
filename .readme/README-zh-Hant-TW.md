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
- 全部 Node.js 內建模組按 Node 自身的 `module.builtinModules` 清單直接回傳原生匯出 (含 `dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` 等), 僅保留 `worker_threads`, `child_process`, `inspector` 與原生網路模組的請求級閘控 (1.6.0 起).
- 本機 Inspector 偵錯支援 Debug 與 Release, 由宿主明確請求啟用, 使用 localhost 與 ADB 轉送
- 支援 CommonJS/ESM 原始碼, 模組原始碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳.
- 模組載入由 Node.js 自身的 CommonJS / ESM loader 承擔 (`require`, `import()`, `import.meta`, `require(esm)`, package exports / imports 均為原生); 執行階段只註冊 `module.registerHooks` 提供 `autojs6:*` 執行階段模組、宿主提供的模組原始碼與請求級 builtin 閘門 (1.6.0 起).
- 檔案系統使用 Node.js 原生 fs 模組, 檔案可達性完全由外掛自身的 Android 權限決定; 執行階段不再保留 `/proc`, `/sys`, `/dev` 等拒絕邊界 (1.6.0 起).
- 執行宿主提供的 TypeScript 編譯產物, 並可透過 provider v3 請求按需編譯執行期間建立的專案 `.ts`/`.mts`/`.cts`; 執行階段型別移除 fallback 與相容開關已刪除, direct raw dispatch 始終 fail-closed.
- 提供宿主能力代理與 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等執行階段模組.
- 附帶 `sample/nodejs` 範例專案與 `docs/HOST-API.md` 宿主 API 能力清單.
- 插件資訊, 使用說明, README 與 CHANGELOG 均支援西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體.
- 附帶多入口終端機啟動器 `libnodexe.so` 與 npm / corepack 資產, 透過 `NODE_CLI_*` manifest meta-data 宣告, 使 AutoJs6 終端機 (宿主 6.8.0+) 能在自身 uid 的 shell 中執行 `node`, `npm`, `npx`, `corepack`, `yarn` 與 `pnpm`.
- 外掛設定頁支援 1-8 個平行執行槽 (預設 2) 與排隊上限 (預設不限), 超額指令碼按 FIFO 等待, 設定調整保留已接受的工作, idleExitMs 可回收閒置執行程序
- 減少指令碼啟動時不必要的 Web API 初始化, 工作區與 Worker 預設啟用 Node 原生可攜式模組編譯快取, 快取可跨工作區副本重用並由 Node 自動使變更原始碼失效

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
- Intl: ICU 78 完整地區資料 (`--with-intl=full-icu`, 1.6.0 起; 1.5.1 至 1.5.7 為僅含英文資料的 small-icu); `Intl`, 地區格式化, 正規表示式的 Unicode 屬性跳脫以及 Node 自身寫到 stderr 的錯誤輸出均與桌面版一致, 無需 `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: Node.js 原生 fs 模組, Android 權限允許範圍內可存取任意裝置路徑, 執行階段自身不保留拒絕邊界.
- TypeScript: 接受宿主編譯產物, 對應預編譯快照並可請求 provider v3 隨需編譯; 磁碟上的 raw TypeScript 模組經 Node 自身的型別剝離載入, 直接派發的 raw TypeScript 入口仍回傳 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: Node.js 原生 loader + `module.registerHooks`; live binding, 循環相依, `import.meta`, `data:` URL 與 `require(esm)` 與桌面一致.
- 能力: 同步腳本執行, bundle transport, 原生內嵌執行階段, 宿主能力代理, host capability live bridge.
- 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Inspector (Debug/Release) 在 localhost 範圍內正式支援.
- 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、淘汰警告和錯誤; Node 上游 API 穩定性與終端預設行為不變.
- 原生 Worker 支援巢狀執行, MessagePort API, stdio, argv, execArgv 與預載入繼承; 移除外掛自設的 Worker 數量和啟動時限, 請求 resourceLimits 僅作可覆寫的預設值, WorkerPool 預設不限制排隊數量或工作時長; Inspector 工作階段遵循執行請求與目前組建支援
- 主腳本與 Worker 可透過 Node 載入 Android native addon, process.dlopen 與 .node 副檔名恢復原生行為, 不設外掛自有簽章或雜湊允許清單; native-addon 範例提供三 ABI 的 16 KB 對齊 Node-API 產物和離線 NDK/CMake 建置; 錯誤 ABI 或損壞二進位檔回傳 ERR_DLOPEN_FAILED 等 Node 錯誤, 裝置端不隨附 NDK 或 Python
- Inspector 在宿主明確允許後支援 Debug 和 Release 建置; inspector 與 inspector/promises 保留 Node 原生模組物件, 主腳本和 Worker 可使用 Session, 監聽僅綁定 127.0.0.1 且不支援阻塞等待; 未開啟時 url() 回傳 undefined, 啟用和 Session 建立保持關閉
- package_manager 的 install/update/remove 支援 npm 套件規格, 在工作區呼叫隨附 npm; npm.cli/exec/runScript/pack 使用真實命令列, registry/代理/生命週期選項遵循 npm 與專案 .npmrc, 輸出串流回傳並保留 npm 錯誤碼; CLI 資產僅在首次呼叫時準備, 物件請求保留原宿主本機庫契約

******

### 發行歷史

******

# v1.7.0

###### 2026/10/17

* `新增` Node.js 專案和指令碼可打包為獨立簽章的應用程式, 執行時呼叫外部 Node.js Runtime 外掛程式, 外掛程式不可用時顯示安裝指引
* `新增` screen_metrics 和 autojs6:compat 提供依據目前螢幕的非同步 cX/cY/cXy/cYx 及各次執行獨立的換算基數, 並補充覆蓋檢查和 ESM 範例
* `新增` Node.js 和 Bun 支援非同步 OpenCC 轉換及常用影像編解碼, 像素讀取, 變換和顏色搜尋; Bun 影像檔案存取獨立授權
* `新增` Node.js Worker 支援透過父執行環境呼叫 OpenCC 與影像模組, 包含 CJS, ESM, 巢狀 Worker 和影像二進位傳輸
* `修復` Pinyin/Pinyin4j 改為非同步呼叫實際宿主外掛, 移除小字典替代實作; Android 主控台操作透過宿主執行並回傳 Promise; 補齊無障礙手勢和 input_observer 型別宣告
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

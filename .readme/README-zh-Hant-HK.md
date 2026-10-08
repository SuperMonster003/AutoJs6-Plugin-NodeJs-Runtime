<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Node.js 24.21.0 原生運行時插件</p>

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
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
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

AutoJs6 Node.js Runtime 插件為 AutoJs6 提供內嵌 Node.js 24.21.0 原生運行時, 用於執行 Node.js 腳本和插件化運行時任務. 在 Android 17 或以上, 在 AutoJs6 外掛程式中心啟用此外掛程式前須允許存取附近的裝置. 亦可在此外掛程式的設定頁面管理區域網絡權限. 未獲授權時外掛程式保持關閉, 自動啟動將靜默略過. 此權限屬於外掛程式本身, 與 AutoJs6 的授權互相獨立.

******

### 功能

******

- 提供 `nodejs` 插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`.
- 通過 `org.autojs.plugin.nodejs.RUNTIME` 為宿主提供同步腳本執行和運行時預熱.
- 支援涵蓋排隊與執行全程的腳本逾時, 逾時回傳 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定逾時時仍可無限執行
- 全部 Node.js 內建模組按 Node 自身的 `module.builtinModules` 清單直接回傳原生匯出 (含 `dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` 等), 僅保留 `worker_threads`, `child_process`, `inspector` 與原生網絡模組的請求級閘控 (1.6.0 起).
- 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
- 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳.
- 模組載入由 Node.js 自身的 CommonJS / ESM loader 承擔 (`require`, `import()`, `import.meta`, `require(esm)`, package exports / imports 均為原生); 執行階段只註冊 `module.registerHooks` 提供 `autojs6:*` 執行階段模組、宿主提供的模組原始碼與請求級 builtin 閘門 (1.6.0 起).
- 檔案系統使用 Node.js 原生 fs 模組, 檔案可達性完全由外掛自身的 Android 權限決定; 執行時不再保留 `/proc`, `/sys`, `/dev` 等拒絕邊界 (1.6.0 起).
- 執行宿主提供的 TypeScript 編譯產物, 並可透過 provider v3 請求按需編譯執行期間建立的項目 `.ts`/`.mts`/`.cts`; 運行時類型剝離 fallback 與兼容開關已刪除, direct raw dispatch 始終 fail-closed.
- 提供宿主能力代理與 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組.
- 附帶 `sample/nodejs` 示例項目與 `docs/HOST-API.md` 宿主 API 能力清單.
- 插件資訊, 使用說明, README 與 CHANGELOG 均支援西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體.
- 附帶多入口終端啟動器 `libnodexe.so` 與 npm / corepack 資產, 透過 `NODE_CLI_*` manifest meta-data 宣告, 使 AutoJs6 終端 (宿主 6.8.0+) 能在自身 uid 的 shell 中執行 `node`, `npm`, `npx`, `corepack`, `yarn` 與 `pnpm`.

******

### 使用示例

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

將插件安裝並在 AutoJs6 插件中心啟用後, 以 `"nodejs";` 指令啟動 Node.js 腳本. 更多示例位於 `sample/nodejs`.

******

### 快速上手

******

- **怎麼裝** — 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) 下載對應 ABI 的 APK (不確定就選 `universal`) 並安裝; 或本地構建 `.\gradlew.bat :app:assembleDebug` 後安裝 `app/build/outputs/apk/debug/` 下的產物. 然後在 AutoJs6 的插件中心啟用本插件. Android 11+ 如需存取共享儲存空間, 還要在系統設定為本插件授予「所有檔案存取」; 未授予時出現 `EACCES` 屬預期行為.
- **怎麼跑** — 在 AutoJs6 編輯器中新建腳本, 首行寫 `"nodejs";`, 其餘按桌面 Node.js 寫法編寫 (支持 CommonJS/ESM/npm 純 JS 包/網絡內建模塊), 點擊運行即可. 運行中即時輸出, 可隨時手動停止. raw `.ts`/`.mts`/`.cts` 目前必須先由宿主編譯為 JavaScript; 插件不內置 `tsc`.
- **出錯了看哪裏** — 腳本報錯時控制枱顯示 JS 棧與一行錯誤碼 (如 `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`); 更多細節用 `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` 查看插件進程日誌. 宿主 API 可用性以 `docs/HOST-API.md` 為準.

******

### 運行時資料

******

- 運行時槽位: `node24_21`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 運行時服務動作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生運行庫: `libnode.so`, `libautojs6-node.so` 和 `libnodexe.so`.
- Intl: ICU 78 完整地區資料 (`--with-intl=full-icu`, 1.6.0 起; 1.5.1 至 1.5.7 為僅含英文資料的 small-icu); `Intl`, 地區格式化, 正規表示式的 Unicode 屬性轉義以及 Node 自身寫到 stderr 的錯誤輸出均與桌面版一致, 無需 `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: Node.js 原生 fs 模組, Android 權限允許範圍內可存取任意裝置路徑, 執行時自身不保留拒絕邊界.
- TypeScript: 接受宿主編譯產物, 對應預編譯快照並可請求 provider v3 隨需編譯; 磁碟上的 raw TypeScript 模組經 Node 自身的型別剝離載入, 直接派發的 raw TypeScript 入口仍回傳 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: Node.js 原生 loader + `module.registerHooks`; live binding, 循環相依, `import.meta`, `data:` URL 與 `require(esm)` 與桌面一致.
- 能力: 同步腳本執行, bundle transport, 原生內嵌運行時, 宿主能力代理, host capability live bridge.
- 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援.
- 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、棄用警告和錯誤; Node 上游 API 穩定性與終端預設行為不變.
- 原生 Worker 支援巢狀執行, MessagePort API, stdio, argv, execArgv 與預載入繼承; 移除外掛自設的 Worker 數量和啟動時限, 請求 resourceLimits 僅作可覆寫的預設值, WorkerPool 預設不限制排隊數量或工作時長; Inspector 工作階段遵循執行請求與目前組建支援
- 主腳本與 Worker 可透過 Node 載入 Android native addon, process.dlopen 與 .node 副檔名恢復原生行為, 不設外掛自有簽章或雜湊允許清單; native-addon 範例提供三 ABI 的 16 KB 對齊 Node-API 產物和離線 NDK/CMake 建置; 錯誤 ABI 或損壞二進位檔回傳 ERR_DLOPEN_FAILED 等 Node 錯誤, 裝置端不隨附 node-gyp 或 NDK

******

### 發行歷史

******

# v1.6.0

###### 2026/10/07

* `優化` fs, fs/promises 及 node:sqlite 模組改為直接使用 Node.js 原生實作, 移除執行時自設的 /proc, /sys, /dev 拒絕邊界與路徑校驗包裝層, 檔案可達性完全由 Android 權限決定, 非同步檔案 API 經 libuv 執行緒池執行而不再阻塞事件迴圈, 路徑錯誤回傳 Node.js 原生錯誤碼 (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH 及 ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED 不再由檔案及資料庫操作產生)
* `優化` process.cwd 與 process.chdir 改為 Node.js 原生行為, 指令碼執行開始時以原生 chdir 進入工作目錄, 相對路徑由 Node.js 自行解析
* `優化` node:sqlite 的 ATTACH (含繫結參數及運算式形式的檔名), VACUUM INTO, 目錄 PRAGMA 及 setAuthorizer 均為 SQLite 原生行為, enableLoadExtension 與 allowExtension 遵循 Node.js 原生狀態檢查
* `優化` Node.js 執行庫改用完整 ICU 資料建置 (--with-intl=full-icu), Intl 及各地區的格式化行為與桌面版 Node.js 一致, 不再需要 NODE_ICU_DATA
* `優化` crypto.createPrivateKey 及簽署 API 支援 file: URL 形式的 OpenSSL STORE 金鑰輸入 (移除 Android 建置中的停用修補)
* `優化` 全部 Node.js 內建模組 (含 cluster, domain, repl, trace_events, wasi, constants, node:sea 等) 按 Node 自身的 module.builtinModules 清單直接回傳原生匯出, require, require.resolve, import, process.getBuiltinModule 與 module.isBuiltin 行為與桌面版一致, 僅保留 worker_threads, child_process, inspector 及原生網絡模組的請求級閘控; node:wasi 隨 /proc 邊界的刪除一併放開 (trace_events 在目前未啟用 tracing 的執行庫中與 Node 一樣擲出 ERR_TRACE_EVENTS_UNAVAILABLE)
* `優化` process 僅保留 exit 的生命週期對應; dlopen, abort, kill, umask, binding, report, features 及 POSIX 身分呼叫使用 Node 原生實作; os 為原生模組, homedir 與 tmpdir 遵循 HOME/TMPDIR, userInfo 回傳 Android 應用程式使用者
* `優化` module 內建模組改為僅覆寫 createRequire 的 Node.js 原生物件, registerHooks, enableCompileCache, findPackageJSON 等可直接使用; 刪除僅在停用原生網絡時可達的 http, https, net, tls, dns 受限實作, 停用時直接回傳 ERR_AUTOJS6_BUILTIN_DISABLED
* `優化` 外掛程式資訊 (INFO) 與執行階段診斷的 supportedAbis 改為讀取建置期寫入資訊清單的 org.autojs.plugin.contract.SUPPORTED_ABIS meta-data (需要 AutoJs6-Gradle-Platform-Versions 1.9.0 起的 nativeAlignment 外掛程式), 表示外掛程式建置支援的全部 ABI 而非已安裝分包; meta-data 缺失時回退為已安裝套件內實際打包的 ABI, 手寫的 ABI 常數已刪除
* `優化` 模組載入改為 Node.js 原生 CommonJS / ESM loader: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta 與 require(esm) 全部為 Node 原生行為, 入口按 Node 主模組方式執行 (require.main === module, __filename / process.argv[1] 為 realpath); 執行階段只註冊 module.registerHooks 提供 autojs6:* 執行階段模組與相容 shim (autojs6: scheme, 同名 npm 套件仍優先於 shim)、請求級 builtin 閘門、請求隨附 / provider 具現化 / TypeScript 快照的模組原始碼與入口內嵌原始碼; vm.SourceTextModule 自管 linker, 自訂 resolver, 動態 import 改寫與 eval / Function 守衛全部移除, esmEnabled / dynamicImportEnabled 請求鍵不再生效
* `優化` 原生 Worker 支援巢狀執行, MessagePort API, stdio, argv, execArgv 與預載入繼承; 移除外掛自設的 Worker 數量和啟動時限, 請求 resourceLimits 僅作可覆寫的預設值, WorkerPool 預設不限制排隊數量或工作時長; Inspector 工作階段遵循執行請求與目前組建支援
* `優化` Worker 與巢狀 Worker 可透過父執行緒 MessagePort 使用 autojs6:bridge.callAutoJs, 沿用宿主 broker 的權限檢查, 錯誤, 逾時與取消; worker-host-bridge 範例示範讀取實際宿主裝置資訊, 並提供橋接 API 的 TypeScript 宣告
* `優化` 原始 .ts / .mts / .cts 入口與匯入模組仍須由宿主 Compiler 編譯為 JavaScript; 保留快照對應與 provider v3 按需編譯。模組載入失敗使用 Node 原生錯誤碼, 原 AutoJs6 ESM 與動態匯入錯誤碼退役。
* `優化` 主腳本與 Worker 可透過 Node 載入 Android native addon, process.dlopen 與 .node 副檔名恢復原生行為, 不設外掛自有簽章或雜湊允許清單; native-addon 範例提供三 ABI 的 16 KB 對齊 Node-API 產物和離線 NDK/CMake 建置; 錯誤 ABI 或損壞二進位檔回傳 ERR_DLOPEN_FAILED 等 Node 錯誤, 裝置端不隨附 node-gyp 或 NDK

# v1.5.7

###### 2026/10/05

* `優化` 外掛程式中心圖示採用統一工作台調整後的尺寸, 位置, 明暗圖稿與圓形底色, 保留可重建原稿和參數
* `優化` Android 系統應用程式資訊圖示與圖示工作台共用圖稿及明暗底色, 保留外掛程式中心透明圖稿與現有啟動器選項

# v1.5.6

###### 2026/09/19

* `新增` 運行時槽進程發生 Node.js 致命錯誤 (JavaScript 堆記憶體耗盡、內部檢查失敗) 時, 現在會在插件快取目錄留下 Node 自帶的診斷報告 (crash/<executionId>.json, 於中止前寫出, 不安裝訊號處理器): 失敗結果在 slotExit.crash 與錯誤訊息中帶上事件、堆用量、JS 堆疊與報告路徑 (例如 CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), getRuntimeInfo 以 lastCrash 公開最新報告 (調度進程重啟後仍可讀); 新增請求選項 maxOldGenerationSizeMb 為單次執行設定 JS 堆上限 (V8 老生代, 單位 MB), 回歸測試也藉此重現該崩潰
* `修復` 修正 require 大型 CommonJS 模組時運行時槽進程因 V8 堆記憶體耗盡而中止 (回報為 CRASH_NATIVE / SIGABRT) 的問題: 加載器的註釋與字串遮罩逐字元拼接, 22 MiB 模組需要約 700 MB 堆記憶體; 現改為按連續區段輸出 (結果相同, 約 44 MB), CommonJS 的 import / export 掃描也改為在候選位置間跳轉而非逐字元檢測
* `修復` AGP 9.1 構建時的 SDK XML v4 解析警告及 JVM 單元測試組裝任務誤觸發 APK 原生程式庫對齊檢查的問題 (共用構建外掛 1.8.3)

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

******

### 構建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 構建:

```powershell
.\gradlew.bat :app:assembleRelease
```

構建參數來自 `version.properties`, 目前最低 SDK 為 24, 目標 SDK 為 36.

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

`strings.xml` 提供插件描述本地化; `plugin_instruction.md` 提供宿主側展示的插件使用說明. README 與 CHANGELOG 由 `.python/generate_markdown.py` 根據 JSON 源文件生成.

******

### 相關連結

******

- AutoJs6 文件: https://docs.autojs6.com
- Node.js 官方項目: https://github.com/nodejs/node
- Node.js 運行時構建計劃: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

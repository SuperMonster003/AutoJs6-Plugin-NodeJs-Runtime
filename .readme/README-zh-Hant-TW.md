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
- Node.js Worker 與 Bun 的 database/sqlite API, 支援參數化查詢, 交易和資料庫持久化.
- Bun 與 Node.js Worker 的感測器取樣及訂閱 API, 支援取消和監聽器自動釋放.
- Bun, Node.js 及 Worker 的宿主 Shell API, 支援串流輸入輸出, Shizuku 授權與程序清理.

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

# v1.26.0

###### 2026/10/21

* `新增` Node.js/Worker: EPUB 書籍讀取, 資源匯出, 搜尋及含事件的閱讀器工作階段

# v1.25.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的 speech 語音辨識與模型管理介面, 支援 WAV/PCM, 聽寫與喚醒工作階段, 模型安裝/匯入, 語音合成及取消清理

# v1.24.0

###### 2026/10/20

* `新增` Node.js/Bun/Worker 的文字轉語音介面, 支援引擎與音色查詢, 朗讀, 靜音工作, 音訊檔案合成和工作事件

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

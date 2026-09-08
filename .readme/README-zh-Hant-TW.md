<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Node.js 24.5.0 原生執行階段插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
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

AutoJs6 Node.js Runtime 插件為 AutoJs6 提供內嵌 Node.js 24.5.0 原生執行階段, 用於執行 Node.js 腳本和插件化執行階段任務.

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

- 執行階段槽位: `node24_5`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 執行階段服務動作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生執行庫: `libnode.so` 和 `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: 可存取 Android 權限允許的裝置路徑; `/proc`、`/sys`、`/dev` 為硬邊界.
- TypeScript: 接受主機產物並可透過 provider v3 按需編譯執行期間建立的專案檔案; direct raw TypeScript 回傳 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 與循環相依.
- 能力: 同步腳本執行, bundle transport, 原生內嵌執行階段, 宿主能力代理, host capability live bridge.

******

### 發行歷史

******

# v1.3.0

###### 尚未發佈

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
* `修復` 修復常駐指令碼的橋接工作階段持續累積請求與回應記錄的問題, 清理已完成請求, 僅保留最近 32 筆回應並限制診斷大小
* `修復` 原生非同步任務完成前提前產生成功結果, 導致非同步錯誤與後續退出碼遺失的問題; 終態改為跟隨 Node 事件迴圈最終退出
* `優化` 即時橋預設使用 JNI/Binder 直通並經 Node 事件迴圈返回回應, 降低呼叫延遲, 保留可選檔案回退與待處理呼叫上限
* `優化` Node.js stream, crypto, timers, util, node:test 等內建模組恢復原生匯出, 保留檔案系統邊界與宿主目錄策略
* `優化` 原生 worker 預設依 CPU 平行度執行 (最多 8 個), 繼承執行的網路與檔案開關, 支援請求層級資源上限且池任務預設不設逾時; CPU 與 WASM 範例改為實際多執行緒執行
* `優化` 維持原生 WASI 停用以保留檔案系統邊界, 移除兩個 disabled WASI 範例; 一般 WebAssembly 與 WASM worker 繼續可用
* `優化` 螢幕 OCR 範例申請 Android 擷取授權並辨識真實圖片控制代碼; OCR 或條碼插件不可用時傳回可讀 unavailable 錯誤, 辨識失敗仍按錯誤處理

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

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-zh-Hant-TW.md)

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

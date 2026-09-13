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

AutoJs6 Node.js Runtime 插件為 AutoJs6 提供內嵌 Node.js 24.21.0 原生執行階段, 用於執行 Node.js 腳本和插件化執行階段任務.

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

- 執行階段槽位: `node24_21`.
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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

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
- 預設啟用 `dgram` (UDP) 與 `http2`, 並對 `trace_events` 回傳明確的停用錯誤
- 支援 Debug 建置明確開啟本機 `inspector` 偵錯, 僅監聽 localhost 並透過 `adb forward` 連線
- 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳.
- ESM 入口與 dynamic `import()` 使用 V8 native linker, 支援循環依賴、可變匯出與 re-export live binding; CommonJS `require(esm)` 保留同步互操作邊界.
- 提供由插件本身 Android 權限約束的桌面式檔案系統存取; `/proc`、`/sys`、`/dev` 一律由運行時拒絕.
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
- Intl: ICU 78, 僅含英文區域資料 (`--with-intl=small-icu`); `Intl`, 正規表示式的 Unicode 屬性跳脫以及 Node 自身寫到 stderr 的錯誤輸出均可在 AutoJs6 終端中使用, `NODE_ICU_DATA` 可指向完整的 ICU 資料檔.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: 可存取 Android 權限容許的裝置路徑; `/proc`、`/sys`、`/dev` 為硬邊界.
- TypeScript: 接受宿主產物並可透過 provider v3 按需編譯執行期間建立的項目檔案; direct raw TypeScript 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 與循環依賴.
- 能力: 同步腳本執行, bundle transport, 原生內嵌運行時, 宿主能力代理, host capability live bridge.
- 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援.
- 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、棄用警告和錯誤; Node 上游 API 穩定性與終端預設行為不變.

******

### 發行歷史

******

# v1.5.5

###### 2026/09/17

* `修復` runtimeInfo 回報的能力目錄版本與摘要改由 runtime kit 派生, 修正 1.5.4 中仍為 1.5.0 的舊值
* `修復` worker_threads 按 Node 語義支援 new Worker(code, { eval: true }), 不再把程式碼字串當作指令碼路徑
* `優化` TypeScript 宿主編譯路由在能力目錄中轉為 stable, 刪除已退役的 legacy stripping 中繼資料; 生命週期目錄只保留可執行的執行模式, 移除 packaged_long_running 啟動面與 node_sandboxed / worker_computation 保留名; 傳輸、准入與取消中繼資料按實際執行時對齊
* `優化` autojs6:profile 診斷物件按實際執行時描述, 不再沿用歷史 partial / reserved / deferred 文案: Android 權限約束的檔案存取、process 子集、worker 通道、程序池以及 WASI / native addon 決策
* `優化` 範例清點: packaged-esm、packaged-dynamic-import、require-esm、wasm-basic、wasm-plugin 與 desktop-parity-suite 轉為 stable; 兩個 parity 套件改為真正逐條執行片段而非列印目錄文案; compile-cache 標為不適用, 刪除只列印中繼資料的 package-install 範例
* `優化` 模組載入與 Node 一致地交給 Android 檔案存取: require、import 與 Worker 接受絕對路徑、上層目錄目標與 file: URL (/proc、/sys、/dev 仍拒絕); node_modules 查找仍錨定工作區
* `優化` Worker 訊息與 fs 監視器按 Node 原生限制執行: 移除 64 KB 訊息與 32 條排隊上限, 以及 16 個監視器 / 每秒 64 事件配額; 僅受 Android 記憶體約束
* `優化` worker 內 process.exit() 按 Node 語義只結束該 worker 執行緒, process.getBuiltinModule() 走 worker builtin 名單, 不再整體停用
* `優化` worker 內 bare 套件名按 Node 語義經工作區 node_modules 解析 (exports 條件、main、index、套件自引用), 不再一律拒絕
* `優化` worker 內動態 import() 經 worker 的 partial ESM 載入器執行 (相對/絕對路徑、file: URL、工作區套件、builtin 名單、with { type: "json" }), 不再拒絕
* `優化` host-events 範例在實體按鍵與通知存取人工驗收通過後轉為 stable
* `優化` scheduled-node-task 範例列印生命週期策略並可保留定時任務供 WorkManager 實跑; scheduled 執行模式補齊插件側回歸
* `優化` scheduled 執行模式在宿主 WorkManager 定時執行器經插件實跑通過後, 能力目錄狀態轉為 available
* `優化` media.play() 回傳經宿主腳本音樂服務播放的會話物件 (pause/resume/seekTo/stop/status), 新增 media_store 模組提供受限的 MediaStore capabilities/query/get/insert/update/delete/scanFile/exportFile, 分別由 media.playback / media.library / media.library.mutate 能力把關; autojs6:compat.media 補上 Rhino 風格的 playMusic 等別名; 均需配套宿主構建
* `優化` media-playback / media-library 範例經宿主媒體 provider 合併後的人工驗收轉為 stable; 能力目錄快照 1.5.5 發佈到 releases/nodejs-capability-catalog, 宿主對齊任務改為對照該快照

# v1.5.4

###### 2026/09/17

* `修復` 嵌入執行不再輸出 ExperimentalWarning 提示, 保留 warning 事件、一般警告、棄用警告和錯誤; Node 上游 API 穩定性與終端預設行為不變
* `優化` 檔案串流、gzip/deflate/Brotli 壓縮串流和基本 VM 執行正式化; 明確啟用的 Debug Inspector 在 localhost 範圍內正式支援

# v1.5.3

###### 2026/09/16

* `優化` Android 17 區域網絡授權統一移至外掛程式中心啟用流程及外掛程式設定, 不再提供啟動器授權頁面; 未獲授權時保持關閉並靜默略過自動啟動
* `優化` 適配 Android 17 (SDK 37), 提供插件獨立的本地網絡權限控制及恢復指引

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

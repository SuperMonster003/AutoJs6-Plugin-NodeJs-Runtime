<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用於 AutoJs6 的 Node.js 24.5.0 原生運行時插件</p>

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

AutoJs6 Node.js Runtime 插件為 AutoJs6 提供內嵌 Node.js 24.5.0 原生運行時, 用於執行 Node.js 腳本和插件化運行時任務.

******

### 功能

******

- 提供 `nodejs` 插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`.
- 通過 `org.autojs.plugin.nodejs.RUNTIME` 為宿主提供同步腳本執行和運行時預熱.
- 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳.
- ESM 入口與 dynamic `import()` 使用 V8 native linker, 支援循環依賴、可變匯出與 re-export live binding; CommonJS `require(esm)` 保留同步互操作邊界.
- 提供由插件本身 Android 權限約束的桌面式檔案系統存取; `/proc`、`/sys`、`/dev` 一律由運行時拒絕.
- 執行宿主提供的 TypeScript 編譯產物, 並可透過 provider v3 請求按需編譯執行期間建立的項目 `.ts`/`.mts`/`.cts`; 運行時類型剝離 fallback 與兼容開關已刪除, direct raw dispatch 始終 fail-closed.
- 提供宿主能力代理與 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組.
- 附帶 `sample/nodejs` 示例項目與 `docs/HOST-API.md` 宿主 API 能力清單.
- 插件資訊, 使用說明, README 與 CHANGELOG 均支援西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體.

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

- 運行時槽位: `node24_5`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 運行時服務動作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生運行庫: `libnode.so` 和 `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 檔案系統: 可存取 Android 權限容許的裝置路徑; `/proc`、`/sys`、`/dev` 為硬邊界.
- TypeScript: 接受宿主產物並可透過 provider v3 按需編譯執行期間建立的項目檔案; direct raw TypeScript 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 與循環依賴.
- 能力: 同步腳本執行, bundle transport, 原生內嵌運行時, 宿主能力代理, host capability live bridge.

******

### 發行歷史

******

# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可調用入口與 `read()` 支援顯式選擇插件快照 v1/v2; 省略 schema 時繼續返回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 透過綁定精確位元組數及 SHA-256 的有界 PFD 按需編譯執行期間建立的 `.ts/.mts/.cts`, 使用獨立 30 s 編譯預算, 穩定拒絕路徑逃逸, 符號連結和歧義, 並保留宿主 TypeScript 診斷及 Source Map 堆疊映射
* `新增` 在 Android 應用權限範圍內啟用桌面式檔案系統存取, 同時繼續拒絕 `/proc`、`/sys`、`/dev`
* `新增` 支援 `accessibility.swipe` 與 `accessibility.gesture`, 並由獨立能力 `accessibility.gesture` 門禁
* `修復` raw TypeScript 在宿主未提供編譯產物時改為 fail-closed, 補齊快照動態 import 映射並統一生成/匯入堆疊幀
* `修復` 以 V8 native linker 取代快照式 partial ESM adapter, 修復循環 re-export 中可變匯出未能即時更新的問題
* `修復` 修正 AutoJs6 相容 facade 的 ESM 匯入及不存在的 TypeScript 後綴探測, 同時維持已安裝 npm 套件優先
* `優化` 刪除基於 regex 的 legacy TypeScript 類型剝離 fallback 及其請求開關, raw `.ts/.mts/.cts` 現始終要求宿主編譯產物
* `優化` 對齊宿主/插件 v2 合約, 能力清單與 plugin-only 運行時職責邊界
* `優化` 將 Node.js 示例, TypeScript 類型聲明, 項目嚮導, 運行時默認值及宿主對齊校驗統一歸屬插件倉庫, 移除宿主側 Gradle 開關與重複開發資產

# v1.1.0

###### 2026/08/18

* `新增` 支援 stdout/stderr 即時串流輸出與基於 `node::Stop` 的協作式取消
* `新增` 以最多 3 個等待者的有界串行隊列取代 BUSY 直接拒絕, 並支援常駐長運行腳本生命週期
* `新增` 預設啟用 Node 原生網絡內建模組、`worker_threads` 與 `child_process`, 並實測 10 個常用純 JavaScript npm 套件
* `優化` 支援無需工作區歸檔的 direct-run 與 v1..v2 模組源碼 provider 寬容協商, 錯誤輸出收斂為簡明錯誤碼與 JavaScript 堆疊

# v1.0.0

###### 2026/07/18

* `新增` Node.js 運行時插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 運行時槽位為 `node24_5`
* `新增` 通過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生運行時
* `新增` Node.js 運行時運行於獨立常駐進程, 復用進程級 Node/V8 狀態並為每次執行創建全新 isolate 及 Environment
* `新增` 支援通過 `org.autojs.plugin.INFO` 發現插件資訊, 並通過 `org.autojs.plugin.nodejs.RUNTIME` 調用運行時服務
* `新增` 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和運行時預熱
* `新增` 支援請求級工作區歸檔傳輸 v2, 包含顯式輸入映射/插件私有工作區執行/輸出回寫/刪除 tombstone 清單, 且不掃描宿主沙盒
* `新增` 單活動零隊列準入, 通過 `ERR_AUTOJS6_NODE_PLUGIN_BUSY` 提供背壓, 並支援重啟進程式取消
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組
* `新增` 支援按 ABI 構建 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` 分包及通用 APK 輸出均隨 Node.js 運行庫打包 `libc++_shared.so`
* `新增` `sample/nodejs` 示例項目, Node 解析診斷工具和運行時構建計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
* `修復` 重啟進程式取消期間, 運行時進程回收時可能提交不完整工作區快照的問題
* `修復` 請求合約校驗或工作區物化失敗時, 工作區歸檔文件描述符可能因所有權交接未覆蓋全部退出路徑而洩漏的問題
* `優化` 完善 R5 合約元數據及診斷, 覆蓋 ABI/能力/常駐運行時狀態/準入/取消和獨立進程歸因
* `優化` 增加基於單調時鐘的分階段診斷, 覆蓋執行源碼構建/引導/腳本執行/結果生成與讀取/單次執行清理, 並區分已跳過與不適用狀態

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-zh-Hant-HK.md)

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

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
- 提供宿主能力代理與 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組.
- 附帶 `sample/nodejs` 示例項目, Node 解析診斷工具和運行時構建計劃校驗工具.
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

### 運行時資料

******

- 運行時槽位: `node24_5`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 運行時服務動作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生運行庫: `libnode.so` 和 `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 能力: 同步腳本執行, bundle transport, 原生內嵌運行時, 宿主能力代理, host capability live bridge.

******

### 發行歷史

******

# v1.0.0

###### 2026/07/18

* `新增` Node.js 運行時插件服務, 插件 ID 為 `nodejs`, 引擎為 `nodejs`, 運行時槽位為 `node24_5`
* `新增` 通過 `libnode.so` 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生運行時
* `新增` 支援通過 `org.autojs.plugin.INFO` 發現插件資訊, 並通過 `org.autojs.plugin.nodejs.RUNTIME` 調用運行時服務
* `新增` 支援 CommonJS/ESM 源碼, 模組源碼, 工作目錄, 沙盒根目錄, 環境變數, stdout/stderr 結果回傳和運行時預熱
* `新增` 支援宿主能力代理與 live bridge, 並注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等運行時模組
* `新增` 支援按 ABI 構建 APK, 包括 `arm64-v8a`/`armeabi-v7a`/`x86_64` 以及 `universal` 通用包
* `新增` `sample/nodejs` 示例項目, Node 解析診斷工具和運行時構建計劃校驗工具
* `新增` 插件資訊, 使用說明, README 與 CHANGELOG 的多語言資源: 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/簡體中文/香港繁體/台灣繁體
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

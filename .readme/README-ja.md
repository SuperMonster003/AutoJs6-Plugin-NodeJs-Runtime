<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 向け Node.js 24.5.0 ネイティブランタイムプラグイン</p>

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

### 言語 (Languages)

******

現在の README.md は次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### 概要

******

AutoJs6 Node.js Runtime プラグインは AutoJs6 に組み込み Node.js 24.5.0 ネイティブランタイムを提供し, Node.js スクリプトとプラグインランタイムタスクを実行します.

******

### 機能

******

- `nodejs` プラグインサービスを提供し, プラグイン ID は `nodejs`, エンジンは `nodejs` です.
- `org.autojs.plugin.nodejs.RUNTIME` を通じて同期スクリプト実行とランタイムの事前ロードをホストに公開します.
- CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロードに対応します.
- ホスト能力ブローカーと live bridge を提供し, `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` などのランタイムモジュールを注入できます.
- `sample/nodejs` プロジェクト, Node resolver 診断ツール, ランタイムビルド計画検証ツールを含みます.
- プラグイン情報, 使用説明, README, CHANGELOG はスペイン語/フランス語/ロシア語/アラビア語/日本語/韓国語/英語/簡体字中国語/香港繁体字/台湾繁体字に対応します.

******

### 使用例

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

AutoJs6 プラグインセンターでプラグインをインストールして有効化し, `"nodejs";` ディレクティブで Node.js スクリプトを開始します. その他の例は `sample/nodejs` にあります.

******

### ランタイムプロファイル

******

- ランタイムスロット: `node24_5`.
- プラグイン ID: `nodejs`, エンジン: `nodejs`.
- ランタイムサービスアクション: `org.autojs.plugin.nodejs.RUNTIME`.
- ネイティブランタイムライブラリ: `libnode.so` と `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal`.
- 能力: 同期スクリプト実行, bundle transport, ネイティブ組み込みランタイム, ホスト能力ブローカー, host capability live bridge.

******

### リリース履歴

******

# v1.0.0

###### 2026/07/18

* `追加` プラグイン ID `nodejs`, エンジン `nodejs`, ランタイムスロット `node24_5` の Node.js ランタイムプラグインサービスを追加
* `追加` `libnode.so` と `libautojs6-node.so` による Node.js 24.5.0 ネイティブランタイムを追加
* `追加` `org.autojs.plugin.INFO` によるプラグイン情報検出と `org.autojs.plugin.nodejs.RUNTIME` によるランタイム呼び出しを追加
* `追加` CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロード, ランタイム事前ロードをサポート
* `追加` `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` などのランタイムモジュールを使うホスト能力ブローカーと live bridge を追加
* `追加` `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal` APK の ABI 分割ビルドを追加
* `追加` `sample/nodejs` プロジェクト, Node resolver 診断ツール, ランタイムビルド計画検証ツールを追加
* `追加` スペイン語/フランス語/ロシア語/アラビア語/日本語/韓国語/英語/簡体字中国語/香港繁体字/台湾繁体字向けのプラグイン情報, 使用説明, README, CHANGELOG リソースを追加
* `改善` 実行ソース構築/ブートストラップ/スクリプト実行/結果生成と取得/実行単位クリーンアップに単調時計によるフェーズ別診断を追加し, スキップと非適用の状態を区別

##### その他のリリース履歴

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-ja.md)

******

### ビルド

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release ビルド:

```powershell
.\gradlew.bat :app:assembleRelease
```

ビルドパラメータは `version.properties` から取得されます, 現在の最小 SDK は 24, ターゲット SDK は 36 です.

******

### リソース構成

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` はローカライズされたプラグイン説明を提供します; `plugin_instruction.md` はホストに表示される使用説明を提供します. README と CHANGELOG は `.python/generate_markdown.py` により JSON ソースから生成されます.

******

### 関連リンク

******

- AutoJs6 ドキュメント: https://docs.autojs6.com
- Node.js 公式プロジェクト: https://github.com/nodejs/node
- Node.js ランタイムビルド計画: tools/nodejs/runtime-build/README.md

<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 向け Node.js 26.13.0 ネイティブランタイムプラグイン</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
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

AutoJs6 Node.js Runtime プラグインは AutoJs6 に組み込み Node.js 26.13.0 ネイティブランタイムを提供し, Node.js スクリプトとプラグインランタイムタスクを実行します. Android 17 以降では, AutoJs6 プラグインセンターで有効にする前に, このプラグインに付近のデバイスへのアクセスを許可してください. プラグインの設定ページでもローカルネットワーク権限を管理できます. 未許可の場合は無効のままとなり, 自動起動は通知せずにスキップされます. この権限はプラグインに属し, AutoJs6 の権限とは独立しています.

******

### 機能

******

- `nodejs` プラグインサービスを提供し, プラグイン ID は `nodejs`, エンジンは `nodejs` です.
- `org.autojs.plugin.nodejs.RUNTIME` を通じて同期スクリプト実行とランタイムの事前ロードをホストに公開します.
- キュー待機と実行の全体を対象とするスクリプトタイムアウトを追加し、超過時に `ERR_AUTOJS6_SCRIPT_TIMEOUT` を返すようにしました。未指定の場合は引き続き無期限で実行できます
- すべての Node.js 組み込みモジュールを Node 自身の `module.builtinModules` 一覧からそのまま返します (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` など); リクエスト単位のゲートが残るのは `worker_threads`, `child_process`, `inspector` とネイティブネットワークモジュールのみです (1.6.0 以降).
- ホストの明示的な要求で Debug と Release のローカル Inspector を有効化し, localhost と ADB 転送で接続
- CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロードに対応します.
- モジュールは Node.js 自身の CommonJS / ESM ローダーで実行されます (`require`, `import()`, `import.meta`, `require(esm)`, パッケージ exports / imports はネイティブ); ランタイムは `autojs6:*` ランタイムモジュール, ホスト提供のモジュールソース, リクエスト単位の builtin ゲートのための `module.registerHooks` のみ登録します (1.6.0 以降).
- Node.js 本来の fs モジュールを使用し, ファイルへの到達可否はプラグインアプリの Android 権限のみで決まります; ランタイム独自の `/proc`, `/sys`, `/dev` 拒否はありません (1.6.0 以降).
- ホストが提供する TypeScript 出力を実行し, 実行中に作成された project `.ts`/`.mts`/`.cts` には provider-v3 compilation を要求できます. runtime 型消去 fallback と互換スイッチは削除され, direct raw dispatch は常に fail-closed です.
- ホスト能力ブローカーと live bridge を提供し, `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` などのランタイムモジュールを注入できます.
- `sample/nodejs` プロジェクトとホスト API 能力一覧 `docs/HOST-API.md` を含みます.
- プラグイン情報, 使用説明, README, CHANGELOG はスペイン語/フランス語/ロシア語/アラビア語/日本語/韓国語/英語/簡体字中国語/香港繁体字/台湾繁体字に対応します.
- マルチコール型ターミナルランチャー `libnodexe.so` と npm / corepack アセットを同梱し, `NODE_CLI_*` manifest meta-data で宣言することで, AutoJs6 ターミナル (ホスト 6.8.0 以降) が自身の uid の shell で `node`, `npm`, `npx`, `corepack`, `yarn`, `pnpm` を実行できます.
- プラグイン設定で同時実行スロットを 1-8 (既定 2), 待機キューの上限を設定可能 (既定は無制限); 超過分は FIFO で待機し, 設定変更後も受理済みの処理を保持, idleExitMs でアイドル状態の実行プロセスを解放
- 不要な Web API 初期化を抑えてスクリプト起動を高速化し, ワークスペースと Worker で Node のポータブルなモジュールコンパイルキャッシュを既定で有効化; ワークスペースのコピー間で再利用し, ソース変更時は Node が無効化
- Node.js Worker と Bun の database/sqlite API. パラメーター付きクエリ, トランザクション, 永続化に対応.
- Bun と Node.js Worker のセンサー取得と購読 API, キャンセルとリスナーの自動解放.
- Bun, Node.js と Worker のホスト Shell API, ストリーム入出力, Shizuku 許可とプロセス解放.

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

### クイックスタート

******

- **インストール** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) から ABI に合った APK をダウンロードしてインストールします (不明な場合は `universal` を選択). あるいは `.\gradlew.bat :app:assembleDebug` でローカルビルドし `app/build/outputs/apk/debug/` からインストールします. その後 AutoJs6 のプラグインセンターで本プラグインを有効化します. Android 11 以降で共有ストレージを使う場合は, システム設定で本プラグインに「すべてのファイルへのアクセス」を許可してください. 未許可時の `EACCES` は想定動作です.
- **実行** — AutoJs6 エディタで先頭行が `"nodejs";` のスクリプトを作成し, 残りはデスクトップ Node.js と同様に記述します (CommonJS/ESM, 純 JS npm パッケージ, ネットワーク組み込みモジュールに対応). 実行すると出力がリアルタイムで流れ, いつでも停止できます. 未コンパイルの `.ts`/`.mts`/`.cts` は先にホストで JavaScript へ変換する必要があり, プラグインは `tsc` を内蔵しません.
- **エラー時の確認先** — スクリプトの失敗時はコンソールに JS スタックと 1 行のエラーコード (例: `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) が表示されます. 詳細は `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` でプラグインプロセスのログを確認してください. ホスト API の可用性は `docs/HOST-API.md` を参照してください.

******

### ランタイムプロファイル

******

- ランタイムスロット: `node26_13`.
- プラグイン ID: `nodejs`, エンジン: `nodejs`.
- ランタイムサービスアクション: `org.autojs.plugin.nodejs.RUNTIME`.
- ネイティブランタイムライブラリ: `libnode.so`, `libautojs6-node.so` と `libnodexe.so`.
- Intl: 完全なロケールデータ付き ICU 78 (`--with-intl=full-icu`, 1.6.0 以降; 1.5.1 から 1.5.7 は英語のみの small-icu); `Intl`, ロケール書式, 正規表現の Unicode プロパティエスケープ, Node 自身の stderr へのエラー出力はデスクトップ版 Node と一致し, `NODE_ICU_DATA` は不要です.
- Temporal: 2.0.0 から Node.js 26 組み込みの `Temporal` グローバルオブジェクトで, 日付, 時刻, タイムゾーン, 暦を追加ライブラリなしで扱えます.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal`.
- ファイルシステム: Node.js 本来の fs モジュール, Android 権限で許可された任意のデバイスパスに到達でき, ランタイム独自の拒否はありません.
- TypeScript: ホストの出力を受け入れ, プリコンパイル済みスナップショットを対応付け, provider v3 のコンパイルを要求可能; ディスク上の raw TypeScript モジュールは Node 自身の型除去で読み込まれ, 直接ディスパッチされた raw TypeScript エントリは引き続き `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` を返します.
- ESM: `module.registerHooks` 付きの Node.js ネイティブローダー; ライブバインディング, 循環依存, `import.meta`, `data:` URL, `require(esm)` はデスクトップと同じ.
- 能力: 同期スクリプト実行, bundle transport, ネイティブ組み込みランタイム, ホスト能力ブローカー, host capability live bridge.
- ファイルストリーム、gzip/deflate/Brotli ストリームと基本 VM 実行を正式サポート; 明示的に有効化した Inspector (Debug/Release) は localhost の範囲で正式サポート.
- 組み込み実行で ExperimentalWarning の表示を停止し、warning イベント、通常の警告、非推奨警告とエラーを維持; Node 上流 API の安定性と端末の既定動作は変更なし.
- ネイティブ Worker がネスト, MessagePort API, stdio, argv, execArgv とプリロードの継承に対応; プラグイン独自の Worker 数と起動時間の上限を削除し, resourceLimits は上書き可能な既定値に変更; WorkerPool のキュー数とタスク時間は既定で無制限, Inspector セッションは実行要求と現在のビルドに従う
- メインスクリプトと Worker で Android ネイティブアドオンを Node からロード可能; process.dlopen と .node はネイティブ動作となり, 独自の署名やハッシュ許可リストを設けない; native-addon は 3 ABI の 16 KB アラインメント済み Node-API バイナリとオフライン NDK/CMake ビルドを提供; ABI 不一致や破損は ERR_DLOPEN_FAILED などの Node エラーとなり, 端末用 NDK や Python は同梱しない
- ホストの明示的な許可により Debug と Release ビルドで Inspector を利用可能; inspector と inspector/promises は Node のネイティブモジュールを維持し, メインスクリプトと Worker で Session を使用可能, 待受は 127.0.0.1 のみでブロッキング待機は不可; 未許可時は url() が undefined を返し, 有効化と Session 作成は無効
- package_manager の install/update/remove が npm パッケージ指定を受け取り, 同梱 npm を作業領域で実行; npm.cli/exec/runScript/pack は実際の CLI を使用し, registry/プロキシ/ライフサイクル設定は npm とプロジェクトの .npmrc に従う, 出力を逐次転送して npm エラーコードを保持; CLI 資産は初回使用時のみ準備し, オブジェクト形式の要求は既存のホストローカルストア契約を維持
- Node.js 24 系: プラグイン 1.x (Node.js 24.21.0) は `release/1.x` ブランチでセキュリティ更新のみを提供し, 2028-04-30 (Node.js 24 のサポート終了) まで保守します.

******

### リリース履歴

******

# v2.0.0

###### 2026/10/30

* `ヒント` プラグイン 2.0.0 から Node.js 26 で動作し, Node.js 24 で非推奨だった一部の API (SlowBuffer, fs.rmdir の recursive オプション, fs.F_OK などの定数, assert.CallTracker, node:_stream_wrap など) が削除されたため, これらを使うスクリプトは書き換えが必要; Node.js 24 を使い続けるには 2.x をアンインストールしてから 1.x 版をインストール (セキュリティ更新のみ, 2028-04-30 まで保守)
* `ヒント` V8 の API を直接使ってビルドした native addon (.node) は Node.js 26 向けに再ビルドが必要, Node-API ベースの拡張は影響なし
* `追加` Node.js 26 組み込みの Temporal グローバルオブジェクトで, 追加ライブラリなしに日付, 時刻, タイムゾーン, 暦を扱える
* `追加` Node.js/Worker に AutoJs6 スクリプトと同じ app, shell, shizuku モジュールを追加, デュアル (クローン) アプリの起動, アンインストール, 停止を含み, コマンドは Shizuku, root, 通常の shell のうち最初に使えるもので実行され Promise を返す
* `追加` Node.js/Worker に AutoJs6 スクリプトと同じシステム情報モジュールを追加: autojs, installer, memory, settings, sysprops, power_manager, autojs6:system (setScreenMetrics はアクセシビリティのクリックとジェスチャー座標も拡大縮小), 通知のスイッチとチャネル, dialogs.build, ホストのスクリプトを一覧, 停止する engines, plugins.extend と plugins.tools
* `追加` Node.js/Worker に AutoJs6 スクリプトと同じ device, sensors, media モジュールを追加: 端末モデル, 画面とメーカーの判定, 振動と画面点灯維持の全オプション, センサーイベント, 音楽再生とメディアスキャン; 端末識別子の読み取りには新しい device.identifiers 能力の宣言が必要
* `追加` Node.js/Worker に AutoJs6 スクリプトと同じ colors/Color, files, zip, mime, 計時レコーダーを追加; autojs6:global から dayjs, pangu.js, cheerio, banana-i18n を利用でき, Worker の axios リクエストはメインスレッドが処理
* `修正` rhino.install() で導入した Packages / java / android プロキシで, 静的フィールドの読み取り, describe() の呼び出し, 静的メソッドの呼び出し, new によるオブジェクト生成がエラーになる問題
* `修正` すぐに終了するコマンドで shell.exec がまれに ERR_STREAM_DESTROYED エラーで失敗する問題
* `改善` Node.js 26 は corepack を同梱しなくなったが, プラグインは引き続き corepack 0.36.0 を同梱し, ターミナルでの使い方は変わらない
* `改善` worker_threads の TypeScript 宣言に WorkerPool のオプション, キャンセル, タイムアウト, 診断を追加, @types/node なしでも型が付く
* `改善` Node.js ランタイムライブラリを NDK r29 でビルドし, 3 つの ABI で 16 KB ページアラインメントを維持; リリース受け入れスクリプトは端末での完全な回帰テスト, JSON レポート出力, コールドスタート計測に対応
* `依存関係` Node.js 24.21.0 -> 26.13.0 に更新 (V8 14.6, OpenSSL 3.5.9, ICU 78.3), 3 つの ABI すべてで Android 向けにソースからビルドしたライブラリを使用
* `依存関係` npm 11.19.0 -> 11.20.0 に更新

# v1.32.0

###### 2026/10/29

* `追加` Node.js/Bun/Worker の ai.agent タスク制御とイベント, 登録済みスクリプトのコンテキストと結果, パッケージ化アプリの明示的な認可
* `追加` Node.js/Worker に AutoJs6 スクリプトと同じ util, species, s13n, fmt/cvt, jsox ツールモジュールとグローバルデータ関数を追加, autojs6:util などのモジュール名で読み込み
* `修正` chooseFiles() の直後に web.cancelFileChooser() を呼ぶとファイル選択画面が閉じない場合がある問題
* `改善` jsox とバイト単位換算の結果とエラーを AutoJs6 スクリプトと一致させ, jsox.extend/extendAll は現在のスレッドの Math, Array, Number を直接拡張

# v1.31.0

###### 2026/10/22

* `追加` Node.js, Bun と Worker の AI テキスト生成, ストリーミング, モデル一覧と永続セッション, キャンセルと 3-Stone AI によるパッケージアプリごとの許可に対応

##### その他のリリース履歴

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

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

ビルドパラメータは `version.properties` から取得されます, 現在の最小 SDK は 24, ターゲット SDK は 37 です.

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
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

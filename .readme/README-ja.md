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
- キュー待機と実行の全体を対象とするスクリプトタイムアウトを追加し、超過時に `ERR_AUTOJS6_SCRIPT_TIMEOUT` を返すようにしました。未指定の場合は引き続き無期限で実行できます
- `dgram` (UDP) と `http2` をデフォルトで有効化し、`trace_events` には明示的な無効化エラーを返すようにしました
- Debug ビルドで明示的に有効化するローカル `inspector` デバッグを追加しました。localhost のみで待ち受け、`adb forward` 経由で接続します
- CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロードに対応します.
- ESM エントリと dynamic `import()` に V8 native linker を使用し, 循環依存, 可変 export, re-export の live binding を保持します; CommonJS `require(esm)` は同期相互運用境界を維持します.
- プラグインアプリの Android 権限を境界とするデスクトップ相当のファイルシステムアクセスを提供し, `/proc`, `/sys`, `/dev` は常に拒否します.
- ホストが提供する TypeScript 出力を実行し, 実行中に作成された project `.ts`/`.mts`/`.cts` には provider-v3 compilation を要求できます. runtime 型消去 fallback と互換スイッチは削除され, direct raw dispatch は常に fail-closed です.
- ホスト能力ブローカーと live bridge を提供し, `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` などのランタイムモジュールを注入できます.
- `sample/nodejs` プロジェクトとホスト API 能力一覧 `docs/HOST-API.md` を含みます.
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

### クイックスタート

******

- **インストール** — [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) から ABI に合った APK をダウンロードしてインストールします (不明な場合は `universal` を選択). あるいは `.\gradlew.bat :app:assembleDebug` でローカルビルドし `app/build/outputs/apk/debug/` からインストールします. その後 AutoJs6 のプラグインセンターで本プラグインを有効化します. Android 11 以降で共有ストレージを使う場合は, システム設定で本プラグインに「すべてのファイルへのアクセス」を許可してください. 未許可時の `EACCES` は想定動作です.
- **実行** — AutoJs6 エディタで先頭行が `"nodejs";` のスクリプトを作成し, 残りはデスクトップ Node.js と同様に記述します (CommonJS/ESM, 純 JS npm パッケージ, ネットワーク組み込みモジュールに対応). 実行すると出力がリアルタイムで流れ, いつでも停止できます. 未コンパイルの `.ts`/`.mts`/`.cts` は先にホストで JavaScript へ変換する必要があり, プラグインは `tsc` を内蔵しません.
- **エラー時の確認先** — スクリプトの失敗時はコンソールに JS スタックと 1 行のエラーコード (例: `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) が表示されます. 詳細は `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` でプラグインプロセスのログを確認してください. ホスト API の可用性は `docs/HOST-API.md` を参照してください.

******

### ランタイムプロファイル

******

- ランタイムスロット: `node24_5`.
- プラグイン ID: `nodejs`, エンジン: `nodejs`.
- ランタイムサービスアクション: `org.autojs.plugin.nodejs.RUNTIME`.
- ネイティブランタイムライブラリ: `libnode.so` と `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal`.
- ファイルシステム: Android 権限が許す端末パスへアクセスでき, `/proc`, `/sys`, `/dev` は厳格な境界です.
- TypeScript: host output を受け入れ, 実行中に作成された project file には provider-v3 compilation を要求できます. direct raw TypeScript は `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` を返します.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, ネイティブ live binding と循環依存に対応します.
- 能力: 同期スクリプト実行, bundle transport, ネイティブ組み込みランタイム, ホスト能力ブローカー, host capability live bridge.

******

### リリース履歴

******

# v1.3.0

###### 未リリース

* `追加` Node.js ブリッジの購読でセンサー, WebSocket, UI, オーバーレイ, 入力イベントを既存のコールバック経由で配信し, on/once/off, 有界キュー, drainEvents 互換性を提供
* `追加` Node.js ランタイムに任意の idleExitMs によるアイドル終了と次のスクリプトの再接続を追加, idleForMs 診断を提供し既定では常駐を維持
* `追加` グローバル fetch, Request, Response, Headers, FormData, WebSocket に Node ネイティブ Web API を使用; autojs6:fetch と autojs6:websocket はホストのネットワークスタックを提供
* `追加` ファイル境界を検査するネイティブ node:sqlite を追加し、CRUD・トランザクション・バックアップに対応。node:test のネイティブレポーターと実行可能なサンプルを提供し、オフライン npm corpus に zod、cheerio、date-fns、mqtt、ws を追加
* `追加` process.stdin / readline がコンソール入力に対応し, autojs6:host が実行ごとの JSON メッセージを受信; v3 postMessage は旧ホストと互換
* `追加` Node.js の画面キャプチャに Android の許可と既存のフォアグラウンドサービスを使用し, 画像ハンドル, PNG/JPEG/WebP 保存, 停止時およびスクリプト終了時の解放に対応
* `追加` Node.js の画像ハンドルがホストの画像処理による切り抜き, 拡大縮小, グレースケール, しきい値処理, テンプレート照合, 色検索に対応し, 独立した出力画像と実行終了時の解放を提供
* `追加` Node.js image.toBytes がファイル記述子で PNG または RGBA の画素をネイティブ Buffer に転送し, JNI とファイルブリッジに対応, 使用後やタイムアウト, 実行終了時に添付データを解放
* `追加` Node device API がビルド, 画面, バッテリー, メモリの最新情報, 明るさ制御と時間指定の画面点灯維持に対応し, media が Android 権限に従って音声ストリームの音量設定に対応
* `追加` autojs6:events が明示的な権限と購読の自動解放により Android 通知, 外部 Toast, ユーザー補助キー, 画面とバッテリーのイベントをプッシュ通知で監視し, Node 標準の events は互換性を維持
* `追加` Node app が Android サービスの起動, ブロードキャスト送信とインストール済みアプリの照会に対応; dialogs が文字入力, 単一選択, 複数選択とスクリプト終了時に閉じる進捗ウィンドウを提供し, keys がユーザー補助のシステム操作を提供
* `追加` Node recorder がマイク権限, フォアグラウンド通知, 時間制限とスクリプト終了時の解放を備えた AAC 録音に対応; ui.overlay が宣言的な属性更新, ドラッグとイベント配信に対応
* `修正` 常駐スクリプトのブリッジでリクエストと応答の履歴が増え続ける問題を修正し, 完了したリクエストを削除して直近 32 件の応答のみをサイズ上限付きで診断に保持
* `修正` ネイティブ非同期処理の完了前に成功と判定してエラーや終了コードが失われる問題を修正; Node イベントループの最終終了時に結果を確定
* `改善` ライブブリッジを既定の JNI/Binder 転送と Node イベントループ応答に変更して遅延を削減し、ファイル転送への切り替えと保留中の呼び出し数制限を維持
* `改善` Node.js の stream, crypto, timers, util, node:test などのネイティブエクスポートを復元, ファイルシステム境界とホストディレクトリポリシーを維持
* `改善` ネイティブ worker の既定数を CPU 並列度 (最大 8) に変更し、実行のネットワークとファイル設定、リクエスト単位のリソース上限に対応。プールタスクの既定タイムアウトを撤廃し、CPU/WASM サンプルを実際の worker 実行に更新
* `改善` ファイルシステム境界を維持するため raw WASI の無効化を継続し, 無効な WASI サンプル 2 件を削除; 通常の WebAssembly と WASM worker は引き続き利用可能
* `改善` 画面 OCR サンプルが Android のキャプチャ許可を要求して実際の画像ハンドルを認識し, OCR またはバーコードプラグインが利用不可なら読みやすい unavailable エラーを返し, 認識失敗はエラーとして処理

# v1.2.0

###### 2026/08/29

* `追加` `mediainfo` facade に `capabilities()` を追加し, 呼び出し可能エントリと `read()` でプラグイン snapshot v1/v2 の明示選択に対応; schema 省略時は従来のホスト所有 Node v1 snapshot を引き続き返却
* `追加` 実行中に作成された `.ts/.mts/.cts` を byte count と SHA-256 で拘束された PFD によりオンデマンドコンパイルする module-source provider v3 を追加し, 独立した 30 s compilation budget, 安定した path escape/symlink/ambiguity 拒否, host TypeScript diagnostics と Source Map stack mapping を提供
* `追加` Android アプリ権限の範囲でデスクトップ相当のファイルシステムアクセスを有効化し, `/proc`, `/sys`, `/dev` は引き続き拒否
* `追加` 専用能力 `accessibility.gesture` の下で `accessibility.swipe` と `accessibility.gesture` を追加
* `追加` キュー待機と実行の全体を対象とするスクリプトタイムアウトを追加し、超過時に `ERR_AUTOJS6_SCRIPT_TIMEOUT` を返すようにしました。未指定の場合は引き続き無期限で実行できます
* `追加` `dgram` (UDP) と `http2` をデフォルトで有効化し、`trace_events` には明示的な無効化エラーを返すようにしました
* `追加` Debug ビルドで明示的に有効化するローカル `inspector` デバッグを追加しました。localhost のみで待ち受け、`adb forward` 経由で接続します
* `追加` ホストのプラグイン権限で保護された画面なしの有効化入口を追加し、プラグインセンターの説明を補完してアプリデータのバックアップを無効化しました
* `修正` ホストがコンパイラ出力を提供しない未コンパイル TypeScript を fail-closed に変更し, snapshot 動的 import のマッピングと生成/インポートスタックフレームの正規化を追加
* `修正` snapshot ベースの partial ESM adapter を V8 native linker に置き換え, 循環 re-export で可変 export が更新されない問題を修正
* `修正` AutoJs6 互換ファサードの ESM インポートと存在しない TypeScript 拡張子の探索を修正し, インストール済み npm パッケージの優先順位を維持
* `改善` regex ベースの legacy TypeScript 型消去 fallback と request switch を削除し, raw `.ts/.mts/.cts` は常に host compiler output を要求
* `改善` ホスト/プラグイン v2 コントラクト, 能力マニフェスト, plugin-only ランタイムの責務境界を整合
* `改善` Node.js サンプル, TypeScript 型宣言, プロジェクトウィザード, ランタイム既定値, ホスト整合性チェックをプラグインリポジトリに集約し, ホスト側の Gradle スイッチと重複する開発アセットを削除
* `改善` npm の実行確認対象を 15 パッケージに拡大し、axios、express と ESM-only の nanoid、p-limit、yocto-queue を追加しました
* `改善` リクエストの `executionMode` をライフサイクルモードの優先情報源とし、動作に影響しない `runtimeAdapter` を非推奨にしました
* `改善` 固定状態だけを出力する旧サンプルを 10 件削除し、画面キャプチャ、画像解析、録音のホスト provider が未提供であることを型宣言に明記しました

# v1.1.0

###### 2026/08/18

* `追加` stdout/stderr のライブストリーミングと `node::Stop` による協調キャンセルを追加
* `追加` BUSY 即時拒否を最大 3 待機の有界直列キューへ置き換え, 常駐長時間スクリプトのライフサイクルを追加
* `追加` Node ネイティブネットワーク組み込み, `worker_threads`, `child_process` を既定で有効化し, 人気の純 JavaScript npm パッケージ 10 個を検証
* `改善` direct-run ワークスペースと v1..v2 モジュールソース provider の寛容な交渉を追加し, 簡潔なエラーコードと JavaScript スタックへ整理

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

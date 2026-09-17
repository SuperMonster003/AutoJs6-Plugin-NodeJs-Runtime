<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 向け Node.js 24.21.0 ネイティブランタイムプラグイン</p>

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

AutoJs6 Node.js Runtime プラグインは AutoJs6 に組み込み Node.js 24.21.0 ネイティブランタイムを提供し, Node.js スクリプトとプラグインランタイムタスクを実行します. Android 17 以降では, AutoJs6 プラグインセンターで有効にする前に, このプラグインに付近のデバイスへのアクセスを許可してください. プラグインの設定ページでもローカルネットワーク権限を管理できます. 未許可の場合は無効のままとなり, 自動起動は通知せずにスキップされます. この権限はプラグインに属し, AutoJs6 の権限とは独立しています.

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
- マルチコール型ターミナルランチャー `libnodexe.so` と npm / corepack アセットを同梱し, `NODE_CLI_*` manifest meta-data で宣言することで, AutoJs6 ターミナル (ホスト 6.8.0 以降) が自身の uid の shell で `node`, `npm`, `npx`, `corepack`, `yarn`, `pnpm` を実行できます.

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

- ランタイムスロット: `node24_21`.
- プラグイン ID: `nodejs`, エンジン: `nodejs`.
- ランタイムサービスアクション: `org.autojs.plugin.nodejs.RUNTIME`.
- ネイティブランタイムライブラリ: `libnode.so`, `libautojs6-node.so` と `libnodexe.so`.
- Intl: 英語ロケールデータのみを含む ICU 78 (`--with-intl=small-icu`); `Intl`, 正規表現の Unicode プロパティエスケープ, Node 自身の stderr へのエラー出力が AutoJs6 ターミナルで利用でき, `NODE_ICU_DATA` で完全な ICU データファイルを指定できます.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal`.
- ファイルシステム: Android 権限が許す端末パスへアクセスでき, `/proc`, `/sys`, `/dev` は厳格な境界です.
- TypeScript: host output を受け入れ, 実行中に作成された project file には provider-v3 compilation を要求できます. direct raw TypeScript は `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` を返します.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, ネイティブ live binding と循環依存に対応します.
- 能力: 同期スクリプト実行, bundle transport, ネイティブ組み込みランタイム, ホスト能力ブローカー, host capability live bridge.
- ファイルストリーム、gzip/deflate/Brotli ストリームと基本 VM 実行を正式サポート; 明示的に有効化した Debug Inspector は localhost の範囲で正式サポート.
- 組み込み実行で ExperimentalWarning の表示を停止し、warning イベント、通常の警告、非推奨警告とエラーを維持; Node 上流 API の安定性と端末の既定動作は変更なし.

******

### リリース履歴

******

# v1.5.5

###### 2026/09/17

* `修正` runtimeInfo が報告する能力カタログのバージョンとダイジェストを runtime kit から導出し、1.5.4 で残っていた 1.5.0 の旧値を修正
* `修正` worker_threads が Node と同様に new Worker(code, { eval: true }) をサポートし、コード文字列をスクリプトパスとして扱わない
* `改善` ホストの TypeScript コンパイル経路を能力カタログで stable に変更し、退役済みの legacy stripping メタデータを削除; ライフサイクルカタログは実行可能なモードのみを列挙し、packaged_long_running 起動面と node_sandboxed / worker_computation の予約名を削除; 転送・受付・キャンセルのメタデータを実際のランタイムに合わせて整合
* `改善` autojs6:profile 診断オブジェクトは過去の partial / reserved / deferred 表記ではなく実際のランタイムを記述: Android 権限に基づくファイルアクセス、process サブセット、worker チャネル、プロセスプール、WASI / native addon の決定
* `改善` サンプル棚卸し: packaged-esm、packaged-dynamic-import、require-esm、wasm-basic、wasm-plugin、desktop-parity-suite を stable に昇格。両 parity スイートはカタログ文言を出力する代わりにスニペットを実際に実行。compile-cache は適用外とし、メタデータのみの package-install サンプルを削除
* `改善` モジュール読み込みは Node と同様に Android のファイルアクセスに従う: require、import、Worker が絶対パス、親ディレクトリのターゲット、file: URL を受け付ける (/proc、/sys、/dev は引き続き拒否)。node_modules の探索はワークスペースに固定のまま
* `改善` Worker メッセージと fs ウォッチャーは Node ネイティブの制限に従う: 64 KB メッセージ / 32 件キュー上限と 16 ウォッチャー / 毎秒 64 イベントのクォータを撤廃。制約は Android のメモリのみ
* `改善` worker 内の process.exit() は Node と同様にその worker スレッドのみを終了し、process.getBuiltinModule() は worker の builtin 許可リストに従う (一律無効化を廃止)
* `改善` worker 内のベアなパッケージ指定子を Node と同様にワークスペースの node_modules から解決 (exports 条件、main、index、自己参照)。一律拒否を廃止
* `改善` worker 内の動的 import() を worker の partial ESM ローダー経由で実行 (相対/絶対パス、file: URL、ワークスペースのパッケージ、許可された builtin、with { type: "json" })。拒否を廃止
* `改善` 実機キーと通知アクセスの手動受け入れに合格し、host-events サンプルを stable に昇格
* `改善` scheduled-node-task サンプルがライフサイクルポリシーを出力し、WorkManager の実行のためにタスクを保持可能に。scheduled 実行モードにプラグイン側の回帰テストを追加
* `改善` ホストの WorkManager スケジュールランナーがプラグイン経由で実行できたため、scheduled 実行モードを能力カタログで available に昇格
* `改善` media.play() はホストのスクリプト音楽サービスで再生するセッション (pause/resume/seekTo/stop/status) を返し、新しい media_store モジュールは media.playback / media.library / media.library.mutate 能力で保護された限定的な MediaStore の capabilities/query/get/insert/update/delete/scanFile/exportFile を提供; autojs6:compat.media に Rhino 風の playMusic 系エイリアスを追加; いずれも対応するホストビルドが必要

# v1.5.4

###### 2026/09/17

* `修正` 組み込み実行で ExperimentalWarning の表示を停止し、warning イベント、通常の警告、非推奨警告とエラーを維持; Node 上流 API の安定性と端末の既定動作は変更なし
* `改善` ファイルストリーム、gzip/deflate/Brotli ストリームと基本 VM 実行を正式サポート; 明示的に有効化した Debug Inspector は localhost の範囲で正式サポート

# v1.5.3

###### 2026/09/16

* `改善` Android 17 のローカルネットワーク認可をプラグインセンターの有効化操作と設定に統一し, ランチャーの認可ページを削除; 未許可時は無効のまま自動起動を通知せずにスキップ
* `改善` Android 17 (SDK 37) に対応し, プラグイン独立のローカルネットワーク権限設定と復旧案内を提供

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

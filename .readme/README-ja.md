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
- すべての Node.js 組み込みモジュールを Node 自身の `module.builtinModules` 一覧からそのまま返します (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` など); リクエスト単位のゲートが残るのは `worker_threads`, `child_process`, `inspector` とネイティブネットワークモジュールのみです (1.6.0 以降).
- Debug ビルドで明示的に有効化するローカル `inspector` デバッグを追加しました。localhost のみで待ち受け、`adb forward` 経由で接続します
- CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロードに対応します.
- ESM エントリと dynamic `import()` に V8 native linker を使用し, 循環依存, 可変 export, re-export の live binding を保持します; CommonJS `require(esm)` は同期相互運用境界を維持します.
- Node.js 本来の fs モジュールを使用し, ファイルへの到達可否はプラグインアプリの Android 権限のみで決まります; ランタイム独自の `/proc`, `/sys`, `/dev` 拒否はありません (1.6.0 以降).
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
- ファイルシステム: Node.js 本来の fs モジュール, Android 権限で許可された任意のデバイスパスに到達でき, ランタイム独自の拒否はありません.
- TypeScript: host output を受け入れ, 実行中に作成された project file には provider-v3 compilation を要求できます. direct raw TypeScript は `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` を返します.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, ネイティブ live binding と循環依存に対応します.
- 能力: 同期スクリプト実行, bundle transport, ネイティブ組み込みランタイム, ホスト能力ブローカー, host capability live bridge.
- ファイルストリーム、gzip/deflate/Brotli ストリームと基本 VM 実行を正式サポート; 明示的に有効化した Debug Inspector は localhost の範囲で正式サポート.
- 組み込み実行で ExperimentalWarning の表示を停止し、warning イベント、通常の警告、非推奨警告とエラーを維持; Node 上流 API の安定性と端末の既定動作は変更なし.

******

### リリース履歴

******

# v1.6.0

###### 2026/10/07

* `改善` fs, fs/promises および node:sqlite は Node.js 本来のモジュールになりました: ランタイム独自の /proc, /sys, /dev 拒否とパス検証ラッパーを撤去し, ファイルへの到達可否は Android の権限のみで決まり, 非同期ファイル API はイベントループを塞がず libuv スレッドプールで実行され, パスエラーは Node.js 本来のコードを返します (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH および ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED はファイル / データベース操作から発生しなくなりました)
* `改善` process.cwd と process.chdir は Node.js 本来の動作になり, 実行開始時に実際の chdir で作業ディレクトリへ移動し, 相対パスは Node.js 自身が解決します
* `改善` node:sqlite の ATTACH (バインドパラメータや式によるファイル名を含む), VACUUM INTO, ディレクトリ PRAGMA および setAuthorizer は SQLite 本来の動作となり, enableLoadExtension と allowExtension は Node.js 本来の状態チェックに従います
* `改善` すべての Node.js 組み込みモジュール (cluster, domain, repl, trace_events, wasi, constants, node:sea など) を Node 自身の module.builtinModules 一覧からそのまま返すようになり, require, require.resolve, import, process.getBuiltinModule, module.isBuiltin はデスクトップ版と同じ動作になりました; リクエスト単位のゲートが残るのは worker_threads, child_process, inspector とネイティブネットワークモジュールのみで, node:wasi は /proc 境界の撤去とともに開放されました (tracing を含まない現在の libnode では trace_events は Node と同様に ERR_TRACE_EVENTS_UNAVAILABLE を投げます)
* `改善` process に残るのは exit のライフサイクル対応付けと dlopen の一時的な拒否のみで, abort, kill, umask, binding, report, features と POSIX の ID 呼び出しは Node.js 本来の動作になりました; os はネイティブで, homedir と tmpdir は実行時の環境変数 HOME/TMPDIR (作業ディレクトリに設定) に従い, userInfo は実際の Android アプリユーザーを返します
* `改善` module 組み込みは createRequire のみを上書きした Node.js 本来のオブジェクトになり, registerHooks, enableCompileCache, findPackageJSON などが利用できます; ネイティブネットワーク無効時にのみ到達できた http, https, net, tls, dns の制限実装を削除し, その場合は ERR_AUTOJS6_BUILTIN_DISABLED を直接返します

# v1.5.7

###### 2026/10/05

* `改善` プラグインセンターのアイコンに Icon Studio で調整したサイズ, 位置, 明暗の図稿と円形背景を適用し, 再生成可能な原稿とパラメーターを保持
* `改善` Android のアプリ情報アイコンに Icon Studio と共通の図案と明暗の背景色を使用し, プラグインセンターの透明な図案と既存のランチャー設定を維持

# v1.5.6

###### 2026/09/19

* `追加` ランタイムスロットで Node.js の致命的エラー (JavaScript ヒープのメモリ不足、内部チェックの失敗) が起きると、Node 自身の診断レポートをプラグインのキャッシュに残すようになりました (crash/<executionId>.json、中止前に書き出し、シグナルハンドラは導入しません)。失敗結果は slotExit.crash とエラーメッセージにイベント・ヒープ使用量・JS スタック・レポートのパスを含み (例: CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json))、getRuntimeInfo は最新のレポートを lastCrash として公開します (ディスパッチャ再起動後も読めます)。新しいリクエストオプション maxOldGenerationSizeMb は 1 回の実行の JS ヒープ上限 (V8 の old generation、MB 単位) を設定し、回帰テストもこれでクラッシュを再現します
* `修正` 大きな CommonJS モジュールを require すると、ランタイムスロットが V8 のヒープメモリ不足で中止する (CRASH_NATIVE / SIGABRT として報告される) 問題を修正しました。ローダーのコメント・文字列マスクが 1 文字ずつ連結していたため 22 MiB のモジュールに約 700 MB のヒープが必要でしたが、現在は同じ結果を連続区間単位で出力し (約 44 MB)、CommonJS の import / export 走査も全文字を検査せず候補位置間をジャンプします
* `修正` 共有ビルドプラグイン 1.8.3 により, AGP 9.1 での SDK XML v4 解析警告と, JVM 単体テストの組み立て時に APK ネイティブライブラリのアラインメント検証が誤って実行される問題

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
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)

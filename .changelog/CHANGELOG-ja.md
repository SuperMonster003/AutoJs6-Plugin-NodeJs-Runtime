******

### リリース履歴

******

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

# v1.1.0

###### 2026/08/18

* `追加` stdout/stderr のライブストリーミングと `node::Stop` による協調キャンセルを追加
* `追加` BUSY 即時拒否を最大 3 待機の有界直列キューへ置き換え, 常駐長時間スクリプトのライフサイクルを追加
* `追加` Node ネイティブネットワーク組み込み, `worker_threads`, `child_process` を既定で有効化し, 人気の純 JavaScript npm パッケージ 10 個を検証
* `改善` direct-run ワークスペースと v1..v2 モジュールソース provider の寛容な交渉を追加し, 簡潔なエラーコードと JavaScript スタックへ整理

# v1.0.0

###### 2026/07/18

* `追加` プラグイン ID `nodejs`, エンジン `nodejs`, ランタイムスロット `node24_5` の Node.js ランタイムプラグインサービスを追加
* `追加` `libnode.so` と `libautojs6-node.so` による Node.js 24.5.0 ネイティブランタイムを追加
* `追加` Node.js ランタイムを独立した常駐プロセスで実行し, プロセス全体の Node/V8 状態を再利用しながら実行ごとに新しい isolate と Environment を作成
* `追加` `org.autojs.plugin.INFO` によるプラグイン情報検出と `org.autojs.plugin.nodejs.RUNTIME` によるランタイム呼び出しを追加
* `追加` CommonJS/ESM ソース, モジュールソース, 作業ディレクトリ, サンドボックスルート, 環境変数, stdout/stderr 結果ペイロード, ランタイム事前ロードをサポート
* `追加` リクエスト単位のワークスペースアーカイブ転送 v2 に対応し, 明示的な入力マッピング/プラグイン専用ワークスペースでの実行/出力の書き戻し/削除 tombstone マニフェストをホストサンドボックス走査なしで提供
* `追加` 単一実行ゼロキューの受け入れ制御で `ERR_AUTOJS6_NODE_PLUGIN_BUSY` バックプレッシャーを返し, プロセス再起動方式のキャンセルに対応
* `追加` `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` などのランタイムモジュールを使うホスト能力ブローカーと live bridge を追加
* `追加` `arm64-v8a`, `armeabi-v7a`, `x86_64`, および `universal` APK の ABI 分割ビルドを追加
* `追加` 分割 APK と `universal` APK の Node.js ランタイムライブラリに `libc++_shared.so` を同梱
* `追加` `sample/nodejs` プロジェクト, Node resolver 診断ツール, ランタイムビルド計画検証ツールを追加
* `追加` スペイン語/フランス語/ロシア語/アラビア語/日本語/韓国語/英語/簡体字中国語/香港繁体字/台湾繁体字向けのプラグイン情報, 使用説明, README, CHANGELOG リソースを追加
* `修正` プロセス再起動方式のキャンセル中にランタイムプロセスが終了処理される際, 不完全なワークスペーススナップショットがコミットされる可能性がある問題
* `修正` リクエストコントラクト検証またはワークスペース展開の失敗時に所有権処理がすべての終了経路で完了せず, ワークスペースアーカイブのファイルディスクリプタがリークする可能性がある問題
* `改善` ABI/能力/常駐ランタイム状態/受け入れ制御/キャンセル/独立プロセス帰属を網羅する R5 コントラクトメタデータと診断を整備
* `改善` 実行ソース構築/ブートストラップ/スクリプト実行/結果生成と取得/実行単位クリーンアップに単調時計によるフェーズ別診断を追加し, スキップと非適用の状態を区別

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

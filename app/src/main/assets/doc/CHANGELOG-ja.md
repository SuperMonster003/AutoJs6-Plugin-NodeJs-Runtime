# v1.2.0

###### 2026/08/26

* `追加` Android アプリ権限の範囲でデスクトップ相当のファイルシステムアクセスを有効化し, `/proc`, `/sys`, `/dev` は引き続き拒否
* `追加` 専用能力 `accessibility.gesture` の下で `accessibility.swipe` と `accessibility.gesture` を追加
* `修正` ホストがコンパイラ出力を提供しない未コンパイル TypeScript を fail-closed に変更し, snapshot 動的 import のマッピングと生成/インポートスタックフレームの正規化を追加
* `改善` ホスト/プラグイン v2 コントラクト, 能力マニフェスト, plugin-only ランタイムの責務境界を整合
* `改善` Node.js サンプル, TypeScript 型宣言, プロジェクトウィザード, ランタイム既定値, ホスト整合性チェックをプラグインリポジトリに集約し, ホスト側の Gradle スイッチと重複する開発アセットを削除

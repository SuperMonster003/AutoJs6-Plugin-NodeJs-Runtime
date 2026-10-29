# AutoJs6 Node.js Runtime

このプラグインは AutoJs6 に Node.js 26.13.0 ネイティブランタイムを提供します.

AutoJs6 プラグインセンターでインストールして有効化し, 次の指示でスクリプトを開始します:

```js
"nodejs";

console.log(process.version);
```

ランタイムスロットは `node26_13` です. `arm64-v8a`, `armeabi-v7a`, `x86_64` ビルドと `universal` APK をサポートします.

サンプルプロジェクトは `sample/nodejs` にあります.

プラグイン設定で同時実行スロットを 1-8 (既定 2), 待機キューの上限を設定可能 (既定は無制限); 超過分は FIFO で待機し, 設定変更後も受理済みの処理を保持, idleExitMs でアイドル状態の実行プロセスを解放

******

### リリース履歴

******

# v1.5.5

###### 2026/09/18

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
* `改善` media-playback / media-library サンプルは、統合されたホストのメディア provider での手動受け入れを経て stable に昇格; 能力カタログのスナップショット 1.5.5 を releases/nodejs-capability-catalog に公開し、ホスト整合タスクはそれを参照
* `改善` fs ラッパーの独自オプション制限を撤廃: ストリームの fs / 継承した fd / flags オプション、watch({ recursive: true })、非同期 cp filter (cpSync は Node の ERR_INVALID_RETURN_VALUE を維持)、絶対パス / 親ディレクトリ / リテラル '!' の glob パターンと exclude 配列、readableWebStream の type / encoding、および Stats / Dirent / Dir コンストラクターは Node 24 のネイティブ動作に従う; filesystemProfile.advancedApis.recursiveWatch は native を報告
* `改善` 再帰的な readdir / opendir をネイティブに委譲 (4096 エントリ上限とエントリごとの realpath 検査を撤廃; readdir('/') は Node と同様に proc/sys/dev の名前を列挙), readlink / chmod / chown / utimes は絶対パスを受け付け chmod はシンボリックリンクを辿る, fs ポリシーエラーコードは ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (ハード境界, loader と共通) / ERR_AUTOJS6_FS_SCOPED_PATH に収束し, 通常の fs 失敗は Node のコードのみを保持
* `改善` ランタイム独自のモジュールソース予算 (モジュールあたり 16 MiB, 合計 64 MiB, 8192 モジュール, provider リクエスト数) を撤廃, CommonJS エントリの __filename / require.main.filename / process.argv[1] は Node と同様に絶対パスになり require.main.id は '.', fs.mkdtemp* はネイティブに委譲: 呼び出し側のプレフィックス表記にサフィックスを付けて要求されたエンコーディングで返し, 親ディレクトリがシンボリックリンクでも拒否しない
* `改善` node:sqlite は SQL のファイル操作をネイティブ SQLite に委ねる: リテラルのファイル名を持つ ATTACH (file: URI を含む) は SQL テキストの走査ではなく SQLite の authorizer で /proc、/sys、/dev 境界を検査し, VACUUM INTO の出力先は SQLite 内部の ATTACH を通じて同じ境界検査を受け, ディレクトリ PRAGMA は遮断されなくなり, file: URI 文字列と空の一時データベースを開け, setAuthorizer() は境界検査と合成される (バインドパラメータや式でファイル名を与える ATTACH のみ引き続き拒否)
* `改善` ブリッジのクォータをホストに委ねる: autojs6:bridge-limits の maxPendingBridgeCalls ウィンドウを超える呼び出しは ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT で失敗せず先入れ先出しで待機するようになり, ランタイム自身は画像ハンドル数・同時実行の制御付き fetch 数・制御付き WebSocket 接続数を制限しなくなり (ホストの broker は引き続き自身のポリシーを適用), require('fetch').policy.maxConcurrentRequests と require('websocket').policy.maxConnections は廃止
* `改善` ブリッジ経由の fetch / WebSocket / axios facade の上限をホストに委ねる: タイムアウト・応答サイズ・リダイレクト回数・メッセージとキューのサイズ・HTTP メソッドを切り詰めずにホストのプロバイダへ渡し (ホスト側ポリシーが適用), ランタイムは Node と同様に最大 20 回のリダイレクトを追い, policy から廃止した hard*/既定サイズ項目は limitsEnforcedBy に置き換え
* `改善` opendir は Node 自身の遅延 fs.Dir を返す (bufferSize・encoding・recursive はネイティブ opendir に渡り, ENOENT / ENOTDIR / ERR_DIR_CLOSED は Node のエラー; dir.path と parentPath だけ呼び出し側の表記を保つ). ランタイム独自のファイルシステム到達ルート削除ガードは廃止し, ルートの rm / rmdir も他のパスと同様に Node と Android の判断に委ねる (fs ポリシーコードは FS_NUL_BYTE と FS_PATH_ESCAPE のみ)
* `改善` ホスト provider トランスポートはホストが getNativeDiagnostics() で公開するソース単体 / 合計 / リクエスト数の上限を採用 (組み込みの 16 MiB / 64 MiB / 139264 はフォールバックのみ), ブリッジ fetch のレスポンス本文はファイルディスクリプタ経由で届き (bodyTransport "pfd") Binder トランザクションサイズではなくホストの maxResponseBytes ポリシーだけで制限され, Binder トランザクションサイズを超えるホスト応答はブリッジタイムアウトではなく直ちに ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED として報告 (ホストブランチ node-m20-2-binder-body-pfd)
* `改善` ブリッジ fetch のリクエスト本文と WebSocket メッセージが 256 KiB を超える場合, インライン base64 JSON ではなく読み取り専用ファイルディスクリプタ (bodyTransport / messageTransport "pfd") でホストへ届くようになり (ホストが bridgeRequestBinaryTransport=pfd を公開する場合, ホストブランチ node-m20-2-binder-body-pfd), 上りサイズは Binder トランザクションサイズではなくホストのリクエストポリシー (64 MiB) と maxMessageBytes だけで制限される
* `改善` 制御付き fetch のレスポンスが Response.url でプロバイダーの最終 URL を公開するようになりました (ResponseInit に url が無いため, 従来は常に空文字列でした)
* `改善` Java 相互運用はホストの宣言的ホワイトリスト (クラス → コンストラクタ / 静的・インスタンスメソッド / フィールド、ホストがテーブルに従ってリフレクションで実行、引数は JSON プリミティブとオブジェクトハンドル) へ全面的に転送されるようになり、ランタイム独自のクラス表を廃止してホストが公開した表を java.policy として読み戻し、getStatic() / describe() を追加、メンバー自身が投げた例外は ERR_AUTOJS6_JAVA_CALL_FAILED で報告されます
* `改善` ランタイムモジュールに対する npm 優先ルールは、実在の npm パッケージを代替するモジュール (axios、colors、mime、nanoid、opencc、undici) のみに限定されました。node_modules 内の同名パッケージが java、fetch、websocket、device などの AutoJs6 ファサードを置き換えることはなくなり、require.resolve は require と同じルールに従い、すべてのランタイムモジュール名を ESM から import でき、autojs6:profile が moduleResolutionProfile としてルールを公開し、Rhino の Packages プロキシに getStatic() / describe() が追加されました
* `改善` スクリプト実行中にランタイムスロットのプロセスが終了した場合 (低メモリキラーによる強制終了、ネイティブクラッシュ、直接の kill)、失敗結果は DeadObjectException だけではなくシステムが記録した終了理由を報告するようになりました。メッセージは LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB) や CRASH_NATIVE (SIGABRT; see the logcat tombstone) のようになり、結果には slotExit、getRuntimeInfo には lastSlotExit が付きます (Android 11 以降; それ以前のバージョンでは理由が取得できない旨を報告)

# v1.5.4

###### 2026/09/17

* `修正` 組み込み実行で ExperimentalWarning の表示を停止し、warning イベント、通常の警告、非推奨警告とエラーを維持; Node 上流 API の安定性と端末の既定動作は変更なし
* `改善` ファイルストリーム、gzip/deflate/Brotli ストリームと基本 VM 実行を正式サポート; 明示的に有効化した Debug Inspector は localhost の範囲で正式サポート

# v1.5.3

###### 2026/09/16

* `改善` Android 17 のローカルネットワーク認可をプラグインセンターの有効化操作と設定に統一し, ランチャーの認可ページを削除; 未許可時は無効のまま自動起動を通知せずにスキップ
* `改善` Android 17 (SDK 37) に対応し, プラグイン独立のローカルネットワーク権限設定と復旧案内を提供

# v1.5.2

###### 2026/09/15

* `改善` compileSdk を 37 (Android 17) に引き上げ, targetSdk はターゲット依存の動作を検証するまで 36 のまま

# v1.5.1

###### 2026/09/15

* `修正` Node 自身の致命的エラーと警告のテキストを Android で logcat に加えて実際の stderr にも書き出すようにし, ターミナルが無言で終了せず未捕捉の例外を表示するようにしました
* `改善` `libnode.so` を `--with-intl=small-icu` で再構築: `Intl` と正規表現の Unicode プロパティエスケープ (`\p{...}`) が英語ロケールデータのみで利用可能に (`NODE_ICU_DATA` で完全な ICU データファイルを指定可能). これにより corepack が AutoJs6 ターミナルで pnpm 11 と Yarn Berry を実行できます

# v1.5.0

###### 2026/09/14

* `追加` マルチコール型ターミナルランチャー `libnodexe.so` (node / npm / npx / corepack / yarn / pnpm) を全 ABI に同梱, `DT_RUNPATH $ORIGIN` と 16 KB ページアライメント付き
* `追加` 公式 Node.js 24.21.0 配布物から取得した npm 11.19.0 と corepack 0.36.0 のアセット, `NODE_CLI_*` manifest meta-data (schema 1) で宣言し, `nodeCli` 機能として runtimeInfo / PluginInfo にミラー

# v1.4.2

###### 2026/09/13

* `修正` インストール済み APK に含まれるネイティブ ABI のみを報告
* `修正` ビルド環境の言語にかかわらずプラグインのビルド日付を英語に統一
* `修正` リリースパッケージとアーカイブのバージョン情報を統一
* `修正` ファイル記述子の分離を保ちながら Android 7 の作業ファイルに対応

# v1.4.1

###### 2026/09/13

* `改善` 64 ビットのネイティブライブラリの 16 KB ページアラインメントをビルド時に検証, manifest 契約の検査と JSON レポートに対応
* `改善` ホストからの有効化, メタデータ, 多言語文書および署名済み APK の収集を共通規約に統一

# v1.4.0

###### 2026/09/10

* `追加` MediaInfo クエリが 0 始まりの streamNumber, countGet によるストリーム数, 単位や説明や表示名を取得する infoKind に対応; Rhino と Node は既定の先頭ストリームの TEXT クエリを維持し, プラグインの拡張機能を確認
* `修正` MediaInfo と画像のファイルパスで絶対パス, 親ディレクトリと有効なファイル名に対応; 録音出力も更新済みホストの Android ファイル権限に従って処理
* `修正` ブリッジ権限エラーは不足する node.permissions 宣言を示し, 診断専用の pro_compat_opt_in プロファイルを要求しなくなった
* `修正` プロジェクト検証で fs の絶対パスや親ディレクトリを FS_OUTSIDE_SCOPE として拒否しなくなった; 実際のファイルアクセスは Android が判断
* `改善` Android イベントと 3 秒間の録音を試す独立プロジェクトを追加し, 画面キャプチャ, OCR, 物理キー, MediaInfo の宣言と手動検証手順を整備
* `改善` スクリーンショットの画像検索, 画面 OCR, 3 秒間の AAC 録音が実機での手動検証に合格し, 対応するサンプルと同じ呼び出しを使う Pro 互換スニペットを安定版に変更
* `改善` イベント検証サンプルに音量上キーによる宿主の停止ショートカットを一時的に無効にする手順を追加; 更新済み宿主は project.json の node.timeoutMs を読み取り, 長い待機が約 5 秒で終了する問題を解消

# v1.3.0

###### 2026/09/09

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
* `追加` Node スクリプトが 2 つの独立したランタイムプロセスで並行実行可能になり, FIFO キューの共有と実行単位のキャンセルおよび入力転送に対応
* `修正` 常駐スクリプトのブリッジでリクエストと応答の履歴が増え続ける問題を修正し, 完了したリクエストを削除して直近 32 件の応答のみをサイズ上限付きで診断に保持
* `修正` ネイティブ非同期処理の完了前に成功と判定してエラーや終了コードが失われる問題を修正; Node イベントループの最終終了時に結果を確定
* `修正` OpenSSL STORE の鍵 URL がファイルシステム制限を回避する問題 (node:fs で鍵データを読み込んで使用)
* `改善` ライブブリッジを既定の JNI/Binder 転送と Node イベントループ応答に変更して遅延を削減し、ファイル転送への切り替えと保留中の呼び出し数制限を維持
* `改善` Node.js の stream, crypto, timers, util, node:test などのネイティブエクスポートを復元, ファイルシステム境界とホストディレクトリポリシーを維持
* `改善` ネイティブ worker の既定数を CPU 並列度 (最大 8) に変更し、実行のネットワークとファイル設定、リクエスト単位のリソース上限に対応。プールタスクの既定タイムアウトを撤廃し、CPU/WASM サンプルを実際の worker 実行に更新
* `改善` ファイルシステム境界を維持するため raw WASI の無効化を継続し, 無効な WASI サンプル 2 件を削除; 通常の WebAssembly と WASM worker は引き続き利用可能
* `改善` 画面 OCR サンプルが Android のキャプチャ許可を要求して実際の画像ハンドルを認識し, OCR またはバーコードプラグインが利用不可なら読みやすい unavailable エラーを返し, 認識失敗はエラーとして処理
* `改善` Node スクリプトはネゴシエーションによる非同期起動に対応し, 長時間実行中の Binder スレッドを解放, 終了後にワークスペースの変更を返却し, 従来の同期ホストとも互換
* `依存関係` Node.js 24.5.0 → 24.21.0 に更新し, 3 つの ABI すべてで Android 向けにソースからビルドしたライブラリを使用

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

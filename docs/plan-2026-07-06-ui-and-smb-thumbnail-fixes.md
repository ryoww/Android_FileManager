# 実行計画: UI 操作性改善と SMB 動画サムネイル停滞の修正（2026-07-06）

## 対象の課題

| # | 課題 | 症状 |
|---|---|---|
| 1 | 右側シークバーにインタラクトできない | `FileScrollIndicator` が Canvas 描画のみで、ドラッグ/タップに反応しない |
| 2 | ファイルプレビューから戻るとスクロール位置がリセットされる | ビューワーを閉じて一覧に戻ると先頭に戻ってしまう |
| 3 | SMB 動画サムネイルの取得が遅い・止まっているように見える | スクロールを止めても動画サムネイルが出ないことがある |
| 4 | 画面端の戻るジェスチャーが効かない | システムの戻るジェスチャーでビューワー/フォルダ階層を戻れない |

## 方針（共通）

- **TDD**: 先にテスト用データ（fake `RemoteReadableFile`、決定的なバイト列、fake クロック）とテストを書き、実装で通す。実機のログ・キャッシュには依存しない
- テスト不能な Compose UI 結線部分は、テスト対象になるロジックを純 Kotlin 関数/クラスへ抽出してからテストする
- 実装は Worker（**model: sonnet**）へ分割委譲し、メイン（本セッション）が設計・レビュー・統合ビルドを行う
- 変更は課題ごとに独立させ、元に戻しやすくする

---

## 課題 1: シークバーのインタラクト対応

### 原因

`explorer/FileCollection.kt` の `FileScrollIndicator` は `Canvas` による描画専用で、ポインタ入力を一切受けていない。

### 対応

1. **純関数の抽出（テスト対象）**: 新規 `explorer/ScrollbarMath.kt` に以下を移動・追加する
   - 既存の `scrollbarMetrics(firstVisibleIndex, visibleItemCount, totalItemCount)`（`FileCollection.kt` から移動、`internal` 化）
   - 新規 `scrollbarTargetIndex(positionFraction, totalItemCount, visibleItemCount): Int` — ドラッグ位置（0f..1f）から `scrollToItem` に渡す先頭アイテム index を返す。`scrollbarMetrics` と往復整合させる
2. **UI 結線**: `FileScrollIndicator` に `pointerInput`（`detectTapGestures` + `detectDragGestures`）を追加
   - タッチ領域はサム描画幅より広く取る（幅 24dp 程度、描画は現状のまま）
   - ドラッグ中は Y 座標 → fraction → `scrollToItem(scrollbarTargetIndex(...))` を呼ぶ
   - grid / list 両モードに適用

### テスト（先に書く）

`app/src/test/.../explorer/ScrollbarMathTest.kt`
- fraction 0f → index 0、fraction 1f → 「最終ページ先頭」の index
- 中間 fraction の単調増加（fraction が増えれば index は減らない）
- `visibleItemCount >= totalItemCount` のとき常に 0（スクロール不要）
- `scrollbarMetrics` → `scrollbarTargetIndex` の往復で自己整合（metrics の positionFraction を入れると元の firstVisibleIndex 近傍へ戻る）

---

## 課題 2: プレビューから戻ってもスクロール位置を維持

### 原因（2 段階）

1. `navigation/AppNavHost.kt` はビューワー表示中、一覧画面（`ExplorerScreen` / `SmbConnectionScreen`）を **composition から完全に外す**。そのため一覧側の `remember` 状態（`LazyGridState` 含む）が破棄される
2. `FileCollection.kt` は `remember(scrollToTopKey) { LazyGridState() }` で状態を生成しており saveable ではない

### 対応

1. `AppNavHost` に `rememberSaveableStateHolder()` を導入し、各 root セクション（EXPLORER / SMB / SETTINGS）を `SaveableStateProvider(key)` でラップする。ビューワー表示で一覧が composition から外れても `rememberSaveable` 状態が保存・復元されるようにする
2. `FileCollection` の `LazyGridState` / `LazyListState` を `rememberSaveable(scrollToTopKey, saver = LazyGridState.Saver)`（list も同様）へ変更する
   - `scrollToTopKey`（= `currentPath`）が変わったらリセットという現行のディレクトリ移動時挙動は**維持**する（キー変更で rememberSaveable が作り直されるため自然に維持される）

### テスト

Compose フレームワーク結線のためユニットテスト対象外（instrumentation テスト基盤は本リポジトリに未整備）。以下の手動確認手順を完了条件とする:

- 一覧を深くスクロール → ファイルを開く → 戻る → **位置が維持されている**（grid / list、ローカル / SMB の 4 通り）
- フォルダを移動したとき → **先頭にリセットされる**（現行挙動の維持）
- 画面回転後も位置が維持される（rememberSaveable の副次効果）

---

## 課題 3: SMB 動画サムネイルの停滞

### 調査で特定した原因候補（コードリーディングによる）

`data/thumbnail/SmbThumbnailRepository.kt` に、症状（スクロールを止めるとサムネイル生成が止まり、再スクロールまで復活しない）と合致する競合がある:

**(a) viewport 更新の順序逆転（最有力・「止まる」症状に合致）**
`updateVisibleThumbnails()` は呼び出しごとに `repositoryScope.launch { updateVisibleSnapshot(...); enqueue(...) }` を発行する。連続スクロールで複数の launch が並ぶと、**古い viewport の launch が新しい viewport の後に実行され得る**。その結果:
- `visibleKeys` が古い可視セットで上書きされる
- `removePendingNotIn(古いセット)` が新しい可視アイテムの pending リクエストを削除する
- 実行中の新しい動画 `RemoteMediaDataSource` がキャンセルされる
- 再エンキュー契機がなく、次のスクロールまでサムネイルが出ない

**(b) `ChunkedRemoteReader` のチャンク再取得と budget の静かな枯渇**
チャンクキャッシュが `MAX_CACHED_CHUNKS = 16`（動画は 1MB × 16 = 16MB）しかなく、MP4 の moov 解析でシークが飛び回るとチャンクを再取得する。再取得も `fetchedBytes` に加算されるため、64MB budget が実転送量より早く尽きて `IOException` → 生成失敗 → `failedKeys` 入り（5 分再試行なし）になり得る。

**(c) watchdog（45 秒）打ち切り → `failedKeys` 5 分固定**
仕様どおりの安全装置だが、(a)(b) と重なると「ずっとアイコンのまま」に見える。今回は (a)(b) を直してから再評価する（watchdog 自体は変更しない）。

### 対応

1. **viewport 更新の直列化 + 世代ガード（(a) の修正）**
   - viewport/queue 状態管理を純 Kotlin クラス `data/thumbnail/ThumbnailRequestCoordinator.kt` へ抽出する（`android.util.Log` 等に依存させない。時刻は `() -> Long` で注入）
   - `updateVisibleThumbnails` の呼び出しに世代番号を採番し、**古い世代の snapshot 適用を破棄**する（Channel への送信 + 単一コンシューマ、または Mutex + 世代比較）
   - `PrioritizedSmbThumbnailQueue` も同ファイルへ移し、テスト可能にする
2. **`ChunkedRemoteReader` のテスト可能化と挙動修正（(b) の修正）**
   - `android.util.LruCache` を純 Kotlin の LRU（`LinkedHashMap(accessOrder = true)`）に置き換え、JVM ユニットテストを可能にする
   - 動画用のチャンクキャッシュ上限を引き上げる（16 → 例: 48。読み取り budget 64MB との整合を確認）
   - budget 超過時の `IOException` メッセージに fetched/budget/ユニークチャンク数を含め、原因が切り分けられるようにする

### テスト（先に書く・テスト用データで実行）

`app/src/test/.../data/smb/ChunkedRemoteReaderTest.kt` — fake `RemoteReadableFile`（`ByteArray` を背後に持ち、`readAt` が決定的に部分読みを返すテストデータ）を作成して:
- チャンク境界をまたぐ read の内容一致
- EOF 近傍・末尾チャンクの長さ
- budget 超過で `IOException`
- キャンセルで `IOException`
- LRU eviction 後の再取得で `fetchedBytes` が増える（現仕様の明文化）

`app/src/test/.../data/thumbnail/ThumbnailRequestCoordinatorTest.kt` — `kotlinx-coroutines-test` を使い:
- **順序逆転の再現テスト**: 世代 2 の viewport 適用後に世代 1 を適用しても、世代 2 の可視セット・pending が壊れない（修正前は失敗するテストとして先に書く）
- 可視アイテムのリクエストが `removePendingNotIn` で誤って消えない
- `failedKeys` の TTL（fake クロックで時間を進める）
- retryable な失敗の即時再エンキュー
- 2 ワーカーで N 件のリクエストが全件処理される

依存追加: `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")`

---

## 課題 4: 画面端の戻るジェスチャー対応

### 現状分析

- `AndroidManifest.xml` に `android:enableOnBackInvokedCallback="true"` が**未コミットの変更として追加済み**（targetSdk 36 では predictive back が関わるため、このフラグは維持する）
- `BackHandler` の配置状況:
  - ビューワー: あり（`AppNavHost`。フルスクリーン解除 → クローズの 2 段階）
  - ローカル Explorer: あり（`canNavigateUp` で上のフォルダへ）
  - SMB ブラウザ: あり（`connected && canNavigateUp`）
  - **SMB 接続フォーム展開中（接続済みで Edit を開いた状態）: なし** → 戻るジェスチャーでアプリが閉じてしまう

### 対応

1. Manifest の `enableOnBackInvokedCallback="true"` を維持（コミット対象に含める）
2. `SmbConnectionScreen` に `BackHandler(enabled = uiState.connected && uiState.connectionFormExpanded) { viewModel.hideConnectionForm() }` を追加（既存の階層バックより優先順位が正しくなる配置にする）
3. 全画面で戻る経路が `OnBackPressedDispatcher`（Compose `BackHandler`）に統一されていることを確認（`onKeyDown` 等の KeyEvent 依存がないこと）

### テスト

- BackHandler は Compose 結線のため手動確認: ビューワー通常/フルスクリーン、Explorer 階層、SMB 階層、SMB 編集フォームの各状態で戻るジェスチャーの挙動を確認する
- 課題 2 の修正と組み合わせ、「戻るジェスチャーで一覧へ戻ってもスクロール位置維持」を確認

---

## 作業分割と順序

メイン（本セッション）が設計・統合・レビューを行い、実装は Worker（**model: sonnet**）へ委譲する。ファイル競合を避けるため以下の分割とする:

| Worker | 担当 | 触るファイル |
|---|---|---|
| 準備（メイン） | `kotlinx-coroutines-test` 依存追加 | `app/build.gradle.kts` |
| W1 (sonnet) | 課題 1 + 課題 2（同一ファイル群のため一括） | `ScrollbarMath.kt`(新規), `ScrollbarMathTest.kt`(新規), `FileCollection.kt`, `AppNavHost.kt` |
| W2 (sonnet) | 課題 3 の `ChunkedRemoteReader` | `ChunkedRemoteReader.kt`, `ChunkedRemoteReaderTest.kt`(新規) |
| W3 (sonnet) | 課題 3 の coordinator 抽出 | `ThumbnailRequestCoordinator.kt`(新規), `ThumbnailRequestCoordinatorTest.kt`(新規), `SmbThumbnailRepository.kt` |
| メイン | 課題 4 + 全体レビュー + ビルド/テスト | `SmbConnectionScreen.kt`, `AndroidManifest.xml` |

順序: 準備 → W1・W2 並行 → W3（W2 の完了を待たない。ファイル独立） → メイン統合 → `.\gradlew.bat testDebugUnitTest` → `.\gradlew.bat assembleDebug`

## 完了条件

1. 追加したユニットテストが全件グリーン（`testDebugUnitTest`）
2. 既存テストにリグレッションなし
3. `assembleDebug` が通る
4. 手動確認項目（課題 2・4）を本ドキュメント記載の手順で確認できる状態（実機確認はユーザーに依頼）

## リスクと元に戻しやすさ

- 課題 3 のリファクタリングは `SmbThumbnailRepository` の内部構造変更を伴うが、公開インターフェース（`ThumbnailRepository`）は変更しない
- 各課題の変更は独立しており、課題単位で revert 可能
- watchdog・budget の値変更は最小限（チャンクキャッシュ上限のみ）とし、挙動変更をテストで固定する

---

## 進捗と結果（2026-07-10 追記）

### 実装状況

| 課題 | 状態 | 実施内容 |
|---|---|---|
| 1. シークバー操作 | 完了 | `ScrollbarMath.kt` 抽出 + `ScrollbarMathTest`（9 件）+ `FileCollection` のドラッグ/タップ結線 |
| 2. スクロール位置維持 | 完了（要実機確認） | `AppNavHost` に `SaveableStateProvider`、`FileCollection` を `rememberSaveable(LazyGridState.Saver / LazyListState.Saver)` 化 |
| 3. SMB 動画サムネイル | 完了（要実機確認） | 下記「レビューで発見・修正した欠陥」参照 |
| 4. 戻るジェスチャー | 完了（要実機確認） | Manifest `enableOnBackInvokedCallback` 維持 + SMB 接続フォーム展開中の `BackHandler` 追加。全画面が `BackHandler` 経由であることを確認 |

### レビューで発見・修正した欠陥（メインエージェントによる Worker 成果物レビュー）

1. **`SmbThumbnailRequestCoordinator.applyViewportSnapshot` の分割ロック競合（課題 3 の本命）**
   世代チェックと「pending 削除 + 可視セット更新」が別々のロック区間に分かれており、並行実行時に古い世代が新しい世代の pending を削除し、可視セットを古い内容で上書きできた（= スクロールを止めてもサムネイルが出ず、再スクロールまで復活しない症状の再現経路）。単一クリティカルセクションに統合し、世代カウンタを `AtomicLong` 化。`SmbThumbnailRequestCoordinatorTest`（12 件）で世代逆転・pending 保護・TTL・再試行・複数ワーカーを固定
2. **watchdog タイムアウトフラグの可視性バグ（課題 3）**
   `timedOut` が plain var のため、watchdog スレッドの書き込みがワーカースレッドから stale に見え、タイムアウトした動画を `retryable=true` と誤判定 → 12 秒ごとに同じ動画を無限再試行し帯域を占有し得た。`AtomicBoolean` 化
3. **スクロールバーの `pointerInput` キー不正（課題 1）**
   `pointerInput(currentMetrics, ...)` とメトリクスをキーにしていたため、ドラッグでスクロールが起きるたびにジェスチャコルーチンが再起動しドラッグ追従が 1 ステップで切れる。キーを `Unit` に固定し最新値は `rememberUpdatedState` 経由で読む方式へ変更。あわせてタッチイベントを consume し、下の一覧との二重スクロールを防止

### チューニング変更（挙動に影響する定数）

| 定数 | 旧 | 新 | 理由 |
|---|---|---|---|
| `VIDEO_WORKER_COUNT` | 2 | 4 | `VideoThumbnailGenerator.renderSemaphore`（4）と整合。フェッチとデコードのパイプライン化 |
| `VIDEO_RENDER_TIMEOUT_MS` | 45 秒 | 12 秒 | ハング動画がワーカーを長時間占有しないように短縮（`SmbFast` プリセットで 1 本あたりの所要が短くなったため成立） |
| `VIDEO_CHUNK_SIZE` | 1MB | 512KB | 先頭フレームのみの取得に必要な範囲を小さく |
| SMB 動画のフレーム選択 | 先頭・10%・30% の 3 候補 | 先頭のみ（`SmbFast`） | 候補ごとの SMB シーク転送を削減。コーデックフォールバックも SMB では無効化 |
| `ChunkedRemoteReader` チャンクキャッシュ | 16 | 48（動画） | moov 解析のシーク飛び回りによるスラッシング・budget 浪費を防止 |

### テスト結果

- `SmbThumbnailRequestCoordinatorTest`（12 件）/ `ChunkedRemoteReaderTest`（7 件）/ `ScrollbarMathTest`（9 件）: green
- 全ユニットテスト + `assembleDebug`: 本ドキュメント追記時点で実行中（結果は会話参照）

### 実機での手動確認項目（ユーザーに依頼）

1. SMB 動画フォルダでスクロールを止めた後、可視範囲の動画サムネイルが順次埋まるか（従来: 再スクロールまで止まることがあった）
2. 一覧を深くスクロール → ファイルを開く → 戻る、で位置が維持されるか（grid / list × ローカル / SMB）
3. 右端のスクロールバーをドラッグして一覧が追従するか。ドラッグ中に下の一覧が別方向に動かないか
4. 戻るジェスチャー: ビューワー（通常/フルスクリーン）・フォルダ階層・SMB 接続編集フォームの各状態
5. 症状が残る場合: `adb logcat -s ThumbPerf` で `dequeue`/`generate done`/`video watchdog` の間隔を確認（budget 超過は `Remote read budget exceeded: ... uniqueChunksFetched=...` で切り分け可能）

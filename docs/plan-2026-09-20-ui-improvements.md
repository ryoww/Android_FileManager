# UI 改善（2026-09-20）

ユーザー依頼「UI の改善を行ってください」に対して、既存コードとスクリーンショット（`.tmp/*.png`）から抽出した課題を修正した記録。
実装は Worker（sonnet）3 名に分割委譲し、メインが設計・レビュー・統合ビルドを行った。

## 対象の課題と対応

| # | 課題（修正前） | 対応 | 主な変更ファイル |
|---|---|---|---|
| A | グリッドカード右上の `...` がタップしても何も起きないダミー表示。名前欄の右端 24dp が常に空く | `...` を削除し、ファイル名を全幅表示 | `explorer/FileGridItem.kt` |
| B | 読み込み中の表示がヘッダー内の大きな円形インジケータで、出るたびにレイアウトが跳ねる。SMB では階層移動中に Test / Reconnect ボタン列が一時的に出現する | ツールバー直下に高さ 3dp の `LinearProgressIndicator` を常設（非表示時も高さを確保）。SMB の接続ボタン列は接続フォーム表示中のみ | `explorer/BrowserStatus.kt`（新規）, `explorer/ExplorerScreen.kt`, `smb/SmbConnectionScreen.kt` |
| C | 空フォルダ・フィルタ結果 0 件のとき一覧が真っ白 | 空状態（アイコン + 見出し + 説明）を表示。フィルタ 0 件は「All に戻す」案内 | 同上 |
| D | エラー / 状態メッセージが素の Text で埋もれる | `BrowserMessage` カード（error は `errorContainer`、info は控えめな背景、アイコン付き） | 同上 |
| E | ローカル Explorer の「← Internal Storage」テキストボタンと SMB の Back ボタンでスタイルが不揃い | Explorer 側も `BrowseLabelButton` + 矢印アイコンに統一。ツールバーは横スクロール可にして狭い画面での見切れを防止 | `explorer/ExplorerScreen.kt` |
| F | ビューワーのヘッダーが「Back」文字ボタン + 種別名（PDF など）のみで、何のファイルを見ているか分からない | `OpenedFile` に `name` を追加し、ファイル名をタイトル・種別をサブタイトルに。戻るはアイコンボタン | `data/model/OpenedFile.kt`, `explorer/ExplorerViewModel.kt`, `smb/SmbExplorerViewModel.kt`, `navigation/AppNavHost.kt` |
| G | 画像ビューワーにピンチズームがない（仕様 13.3 の初期版要件） | ピンチズーム（1〜5 倍）+ パン + ダブルタップで 2.5 倍 / 等倍トグル。画像が画面外へ逃げないようオフセットを制限 | `viewer/ImageViewerScreen.kt` |
| H | 更新手段がリロードアイコンのみ | 一覧に Pull-to-Refresh（Material3 `PullToRefreshBox`）を追加。ファイルを開く処理中の `isLoading` では誤ってインジケータが出ないよう、ユーザーが引っ張ったときだけ表示 | `explorer/FileCollection.kt` |

## 設計上の判断

- **`OpenedFile.name` は末尾デフォルト引数で追加**した。`LocalFileSource` / `ExternalStorageFileSource` / `SmbFileSource` の構築箇所は名前付き引数で呼んでいるため変更不要。名前の補完は `OpenedFile.withNameFallback(fallback)` に集約し、両 ViewModel の `openFile` から 1 行で呼ぶ。
- **進捗インジケータは高さを常に確保**する。`AnimatedVisibility` の退場アニメーション中に Spacer と本体が同時に存在して高さが変わる欠陥が Worker 成果物にあったため、外側 `Box(height = 3.dp)` で固定する形に修正した。
- **Pull-to-Refresh の表示条件**は `pullRequested && isLoading`。`isLoading` は一覧取得だけでなくファイルを開く処理でも true になるため、そのまま渡すとタップのたびに上部にスピナーが出る。
- **メッセージの閉じるボタンは未配線**（`onDismiss = null`）。ViewModel にメッセージを消す API がなく、今回のスコープ（UI 層）を超えるため。

## 検証

- `.\gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL（既存ユニットテストにリグレッションなし）
- Android Studio へ反映済み（ディスク再読み込み + Gradle Sync 完了を `idea.log` で確認）
- エミュレータ（Pixel 10 Pro, API 36）で以下を目視確認済み（2026-09-21）:
  - グリッドの `...` 削除と名前の全幅表示、Explorer の戻るボタンのスタイル統一
  - 空フォルダの空状態、フィルタ（PDF）0 件時の空状態
  - ビューワーのヘッダー（ファイル名 + 種別、長い名前の省略、アイコンの戻るボタン）
  - 画像のダブルタップ拡大（タップ位置基準）と再ダブルタップでの等倍復帰
  - SMB 画面の info メッセージカード
- 未確認: Pull-to-Refresh のインジケータ表示（ローカル一覧の再読み込みが速すぎて撮影できず。操作自体で一覧は壊れない）、SMB 接続中の進捗バーとエラーカード、ピンチ操作（エミュレータでは 2 本指入力が困難）

## 手動確認項目（実機で確認をお願いしたいもの）

1. グリッド表示でカード右上の `...` が消え、長いファイル名が右端まで使われているか
2. フォルダ移動中にツールバー直下の細いバーだけが動き、ヘッダーやボタン列の位置が動かないか（ローカル / SMB）
3. 空フォルダを開いたとき、フィルタで 0 件になったときの空状態表示
4. エラー時（例: SMB 切断）にメッセージが赤系カードで出るか
5. ビューワーのヘッダーにファイル名が表示され、長い名前は末尾が省略されるか（ローカル / SMB、PDF / 画像 / 動画）
6. 画像ビューワーでピンチズーム・パン・ダブルタップが効き、ズームアウトで元の位置に戻るか
7. 一覧を下に引っ張ると再読み込みされ、インジケータが消えるか。右端のスクロールバーをドラッグしたときに Pull-to-Refresh が誤発火しないか
8. 画面回転後も Pull-to-Refresh の状態がおかしくならないか（`pullRequested` は `rememberSaveable`）

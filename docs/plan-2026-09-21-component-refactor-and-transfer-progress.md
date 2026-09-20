# コンポーネント単位のリファクタリングと SMB 転送進捗の表示（2026-09-21）

ユーザー依頼「コンポーネント単位でリファクタリングしてください」「PDF などをダウンロードしているときの表示はシークバーみたいな感じで進捗を確認できるようにしてください」への対応記録。
実装は Worker 3 名（refactor-worker / implementation-worker / test-worker、いずれも sonnet）へ分割委譲し、メインが設計・レビュー・結線・統合検証を行った。

## 1. コンポーネント単位のリファクタリング（振る舞い不変）

### 配置の方針

- 複数画面で使う見た目の部品は `ui/components/` に置く。`ui/components` から `explorer` / `smb` / `viewer` へは依存しない（`data/model` のみ参照可）
- 各画面ファイル（`*Screen.kt`）には「ViewModel との結線」と「レイアウトの骨組み」だけを残し、見た目の部品は画面ごとのパッケージ内の別ファイルへ出す

### 新規・移動・削除

| 種別 | パス | 内容 |
|---|---|---|
| 移動 | `explorer/FileBrowserControls.kt` → `ui/components/BrowseButtons.kt` | `BrowseIconButton` / `BrowseLabelButton` / `FileDisplayModeToggle` / `pressScale` |
| 移動 | `explorer/BrowserStatus.kt` → `ui/components/BrowserStatus.kt` | `BrowserProgressIndicator` / `BrowserMessage` / `BrowserEmptyState` |
| 新規 | `ui/components/BrowserHeader.kt` | タイトル + サブタイトル + 任意の戻るボタン。Explorer と SMB のヘッダーを統合 |
| 新規 | `ui/components/DropdownChoiceButton.kt` | ラベルボタン + ドロップダウン。フィルタとソートの重複を解消 |
| 新規 | `ui/components/ScreenTitle.kt` | 画面タイトル（Settings / SMB Connection） |
| 新規 | `ui/components/TransferProgressBar.kt` | 転送進捗のシークバー風表示（下記 2. 参照） |
| 新規 | `explorer/FileFilter.kt` | `FileFilter` 列挙と `matchesFilter`（純 Kotlin） |
| 新規 | `explorer/ExplorerToolbar.kt` | Explorer のツールバー |
| 新規 | `explorer/ExplorerPermissionCard.kt` | 旧 `EmptyFolderCard`（権限未取得時のカード） |
| 新規 | `smb/SmbConnectionForm.kt` | 接続フォームと Test / Connect / Clear saved ボタン列。ViewModel は渡さずコールバック引数にした |
| 新規 | `smb/SmbConnectedSummary.kt` | 接続済みサマリー |
| 新規 | `smb/SmbBrowserToolbar.kt` | SMB のツールバーと選択中ツールバー |
| 新規 | `viewer/ViewerTopBar.kt` | ビューワーのヘッダーと全画面解除ボタン |
| 新規 | `settings/SettingsComponents.kt` | `SectionLabel` / `SettingsCard` / `SettingsActionRow` |
| 削除 | `settings/CacheSettingsScreen.kt` | どこからも参照されていない薄いラッパーだった |
| 削除 | `ExplorerScreen.kt` 内 `CompactModeButton` | 未使用 |

表示文言・`rememberSaveable` の保存状態・`BackHandler` の条件は変えていない。

## 2. SMB 転送進捗の表示

### 仕組み

```text
SmbFileSource.open / downloadToDownloads / uploadFromUris
  └─ copyWithProgress(reader, output, totalBytes, onProgress)   … data/smb/ProgressCopy.kt（純関数）
       └─ チャンク（256 KB）ごとに累計バイト数を通知
            └─ TransferProgress(kind, fileName, bytesTransferred, totalBytes, completedFiles, totalFiles)
                 └─ SmbExplorerViewModel.reportProgress()  … 100 ms 間引き、完了フレームは必ず通す、Main へ寄せて uiState に反映
                      └─ SmbConnectionScreen: transferProgress != null なら TransferProgressBar、それ以外は従来の細いバー
```

- `FileSource.open(file, onProgress = null)` にデフォルト引数で追加した。ローカルの実装は `onProgress` を無視する（即時に開けるため）
- 従来 `remoteFile.read(output)` で一括コピーしていた箇所を `copyWithProgress` に置き換えた。SMBJ の `File.read(byte[], long, int, int)` をそのまま `PositionedReader` として渡せる
- キャッシュヒット時は進捗を通知しない
- 複数ファイルの Download 保存では、トップレベル選択にディレクトリが含まれると総件数が事前に分からないため `totalFiles = null` にして「n done」表示にする

### 表示

`TransferProgressBar` は 3 行構成:
1. 方向アイコン + 「Downloading {ファイル名}」（右端に「2 / 5」または「3 done」）
2. シークバー風のバー（高さ 6dp のトラック、`primary` の進捗、先端に直径 12dp のつまみ。総量不明なら不確定バー）
3. 「2.3 MB / 12.0 MB」と「19%」

### テスト

- `TransferProgressTest`（4 件）: `fraction` の算出（比率、null / 0 総量、1 超過の丸め）
- `ProgressCopyTest`（7 件）: チャンク境界をまたぐコピーの一致、進捗の単調増加、`totalBytes` での打ち切りと先読み禁止、EOF まで読む、部分読み、空データ、`chunkSize <= 0` の例外
- 全ユニットテスト + `assembleDebug`: BUILD SUCCESSFUL

## 3. 検証

- Android Studio へ反映済み（ディスク再読み込み + Gradle Sync）
- エミュレータ（Pixel 10 Pro, API 36）で NAS に接続し、壁紙フォルダの画像（6.4 MB / 14.1 MB / 15.2 MB）を開いてリグレッションがないことを確認
- **進捗バーの目視は未達**: エミュレータ ↔ NAS はホスト経由の LAN で 6.6 MB が約 0.2 秒で完了し、バーが描画される前にビューワーへ遷移する。エミュレータの回線制限（`emu network speed`）は Wi-Fi 経路に効かず、Play 対応イメージのため root 化して `tc` で絞ることもできなかった
- 実機の Wi-Fi（数 MB/s 〜 数十 MB/s）では数 MB 以上のファイルで 1 秒以上かかるため、そこで表示を確認してほしい。表示されない場合は `SmbExplorerViewModel.reportProgress` の間引き（100 ms）と `TransferProgressBar` のアニメーション（120 ms）を疑う

## 4. 手動確認項目（実機）

1. SMB 上の数 MB 以上の PDF / 画像を開いたとき、ツールバー直下にファイル名・バイト数・% 付きのバーが出て、開き終わると消えるか
2. 複数ファイルを選択して Download したとき「n / m」が進むか。フォルダを含む選択で「n done」になるか
3. アップロード中に「Uploading {名前}」のバーが出るか
4. リファクタリング後の各画面（Explorer / SMB / Viewer / Settings）の見た目と操作が以前と同じか。特にフィルタ・ソートのドロップダウン、SMB 接続フォームの Done / Test / Connect、ビューワーの全画面切替

---

## 追記: PDF / 動画 / 音声ビューワーの改善（2026-09-21）

ユーザー依頼「ビューワー動画も pdf ももうちょいなんとかしてください」への対応。Worker 2 名（PDF / 動画・音声）に分割し、メインがレビュー・修正・エミュレータ確認を行った。

### PDF（`viewer/PdfViewerScreen.kt`、`PdfDocumentRenderer.kt`、`PdfZoomMath.kt`）

| 修正前 | 修正後 |
|---|---|
| ページごとに fd と `PdfRenderer` を開き直す | ドキュメント単位で 1 つ保持し、Mutex で直列化。ビットマップは LRU（96 MB）でキャッシュ |
| ページが `Card` + 「Page x / y」ラベル | 白いページを 8dp 間隔で連続表示。右上にスクロール中だけ「3 / 12」のピル |
| 描画前の高さが不定でスクロールが跳ねる | ページサイズを先読みして正確な高さのプレースホルダ |
| ズームなし | ピンチ（1〜4 倍、2 本指のときだけ Initial パスで奪う）+ ダブルタップ 2 倍。ズーム確定後 250 ms で解像度を量子化して再描画（上限 2000 px） |
| エラーが `throwable.message` 頼み | パスワード保護 / 破損 / 権限切れ / 0 ページを区別 |

レビューで直した欠陥: (1) LRU 追い出し時に `recycle()` すると表示中の `Image` が落ちるため GC に任せる形に変更、(2) `DisposableEffect(viewerState)` の `onDispose` が現在値を読み、Loading → Ready の切替時に開いたばかりのレンダラーを閉じていた（ページが永遠にスピナーのまま）。効果内で値を固定して解決、(3) ダブルタップ判定が古い倍率を見ていた、(4) close 中の描画で `IllegalStateException` にならないよう mutex を取ってから閉じる。

### 動画・音声（`viewer/RememberMediaPlayer.kt`、`MediaPlayerView.kt`、`AudioViewerScreen.kt`）

- アプリが `ON_STOP` で一時停止、`ON_START` で再開。再生中は `keepScreenOn`
- 再生位置と再生状態を `rememberSaveable` で回転後も復元
- 左右ダブルタップで ±10 秒（「−10s」「+10s」を 700 ms 表示）、中央ダブルタップで再生/停止、シングルタップでコントローラ表示切替。コントローラ表示中は下部 96dp をタップ検出から除外
- エラーを `PlaybackException.errorCode` で分類（コーデック非対応 / 読み取り失敗（SMB か ローカルかで文言を変える）/ コンテナ不正 / その他）し、中央カード + Retry
- 音声はアイコン + ファイル名 + 常時表示の `PlayerControlView`（高さ 240dp を明示しないと最小レイアウトになり時間表示が消える。前後トラックボタンは非表示）

### 検証

- `assembleDebug` / `testDebugUnitTest`（`PdfZoomMathTest` 9 件を追加）: BUILD SUCCESSFUL
- エミュレータで生成したサンプル（6 ページ PDF、40 秒の動画、30 秒の音声）を開き、PDF の連続表示・ページピル・ダブルタップ拡大、動画の再生・+10s・コントローラ切替、音声のフル構成コントロールを目視確認
- 未確認: ピンチ操作（2 本指）、回転後の再生位置復元、バックグラウンド一時停止、SMB ストリーミング動画でのエラーカード表示

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

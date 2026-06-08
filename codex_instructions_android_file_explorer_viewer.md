# Codex 指示書: Android ファイルエクスプローラー兼ファイルビューワー

作成日: 2026-06-08  
対象: Kotlin / Android / Jetpack Compose

---

## 0. あなたの役割

あなたは Android / Kotlin / Jetpack Compose に詳しいシニアエンジニアとして振る舞ってください。

目的は、Android 用の **ファイルエクスプローラー兼ファイルビューワー** を段階的に実装することです。

重要なのは、UI を先に作り込むことではありません。最初に **FileSource 抽象化、Viewer 抽象化、キャッシュ設計、SMB 設計** を固めてください。

---

## 1. 最重要方針

以下を厳守してください。

1. いきなり全部を実装しない
2. まずビルド可能な最小構成を作る
3. `FileSource` と `ViewerRouter` を先に作る
4. SMB 処理を UI 層に書かない
5. サムネイル生成を UI 層に書かない
6. PDF/画像/テキスト/コードは一時キャッシュ可能にする
7. SMB 動画/音声は最終的にストリーミング再生にする
8. 一覧表示中に重い処理を同期実行しない
9. 壊れたファイル、権限切れ、SMB切断でクラッシュさせない
10. 各 Phase の完了時点でビルド・静的チェック・簡易動作確認を行う

---

## 2. 実装対象アプリ

Android 上で動作する、以下の機能を持つアプリを作ってください。

- ローカルファイル一覧表示
- SMB 共有の一覧表示
- PDF 1ページ目のサムネイル表示
- 動画サムネイル表示
- 画像サムネイル表示
- プログラムファイルの拡張子別アイコン表示
- PDF / 画像 / 動画 / 音声 / テキスト / コードの簡易ビューワー
- SMB の PDF/画像/テキスト/コードを一時キャッシュして表示
- SMB 動画/音声をストリーミング再生
- キャッシュ使用量表示
- SMB 一時キャッシュ削除
- サムネイルキャッシュ削除
- 全キャッシュ削除

---

## 3. 採用技術

以下を基本としてください。

| 項目 | 技術 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| ファイル一覧 | `LazyVerticalGrid`, `LazyColumn` |
| 画像 | Coil |
| PDF | Android 標準 `PdfRenderer` |
| 動画/音声 | AndroidX Media3 / ExoPlayer |
| ローカルファイル | SAF / `DocumentFile` / `MediaStore` |
| SMB | SMBJ 優先 |
| キャッシュ | `context.cacheDir` |
| 設定保存 | DataStore |
| キャッシュDB | Room。必要になるまで導入しなくてもよい |

---

## 4. 初期ディレクトリ構成

次の構成を基本にしてください。

```text
app/
 ├─ data/
 │   ├─ model/
 │   │   ├─ FileItem.kt
 │   │   ├─ SourceType.kt
 │   │   ├─ ViewerType.kt
 │   │   └─ OpenedFile.kt
 │   │
 │   ├─ source/
 │   │   ├─ FileSource.kt
 │   │   ├─ LocalFileSource.kt
 │   │   └─ SmbFileSource.kt
 │   │
 │   ├─ smb/
 │   │   ├─ SmbClient.kt
 │   │   ├─ SmbConnectionInfo.kt
 │   │   ├─ RemoteReadableFile.kt
 │   │   ├─ SmbReadableFile.kt
 │   │   └─ SmbDataSource.kt
 │   │
 │   ├─ thumbnail/
 │   │   ├─ ThumbnailRepository.kt
 │   │   ├─ PdfThumbnailGenerator.kt
 │   │   ├─ VideoThumbnailGenerator.kt
 │   │   └─ IconResolver.kt
 │   │
 │   └─ cache/
 │       ├─ CacheRepository.kt
 │       ├─ SmbCacheManager.kt
 │       └─ ThumbnailCacheManager.kt
 │
 ├─ explorer/
 │   ├─ ExplorerScreen.kt
 │   ├─ FileGridItem.kt
 │   ├─ FileListItem.kt
 │   └─ ExplorerViewModel.kt
 │
 ├─ viewer/
 │   ├─ ViewerRouter.kt
 │   ├─ PdfViewerScreen.kt
 │   ├─ ImageViewerScreen.kt
 │   ├─ VideoViewerScreen.kt
 │   ├─ AudioViewerScreen.kt
 │   ├─ TextViewerScreen.kt
 │   ├─ CodeViewerScreen.kt
 │   └─ UnsupportedViewerScreen.kt
 │
 ├─ settings/
 │   ├─ SettingsScreen.kt
 │   └─ CacheSettingsScreen.kt
 │
 └─ navigation/
     └─ AppNavHost.kt
```

---

## 5. 最初に作るべきモデル

### 5.1 SourceType

```kotlin
enum class SourceType {
    LOCAL,
    SMB
}
```

### 5.2 FileItem

```kotlin
data class FileItem(
    val name: String,
    val path: String,
    val uri: String?,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAt: Long?,
    val mimeType: String?,
    val sourceType: SourceType
)
```

### 5.3 ViewerType

```kotlin
sealed class ViewerType {
    data object Pdf : ViewerType()
    data object Image : ViewerType()
    data object Video : ViewerType()
    data object Audio : ViewerType()
    data object Text : ViewerType()
    data object Code : ViewerType()
    data object Unsupported : ViewerType()
}
```

### 5.4 OpenedFile

```kotlin
sealed class OpenedFile {
    data class Local(
        val uri: Uri,
        val viewerType: ViewerType
    ) : OpenedFile()

    data class Stream(
        val remoteFile: RemoteReadableFile,
        val viewerType: ViewerType
    ) : OpenedFile()
}
```

---

## 6. FileSource インターフェース

必ずこの抽象を中心に設計してください。

```kotlin
interface FileSource {
    suspend fun list(path: String): List<FileItem>
    suspend fun open(file: FileItem): OpenedFile
}
```

### 実装ルール

- `LocalFileSource` は SAF / `DocumentFile` を使う
- `SmbFileSource` は SMBJ を使う
- `SmbFileSource` の中で動画/音声とそれ以外を分岐する
- UI から SMBJ を直接呼ばない
- UI から `DocumentFile` を直接深く触らない

---

## 7. ViewerType 判定関数

次の関数を作ってください。

```kotlin
fun detectViewerType(name: String, mimeType: String?): ViewerType {
    val ext = name.substringAfterLast('.', "").lowercase()

    return when {
        mimeType?.startsWith("image/") == true -> ViewerType.Image
        mimeType == "application/pdf" || ext == "pdf" -> ViewerType.Pdf
        mimeType?.startsWith("video/") == true -> ViewerType.Video
        mimeType?.startsWith("audio/") == true -> ViewerType.Audio

        ext in setOf(
            "txt", "md", "json", "csv", "log",
            "xml", "yaml", "yml"
        ) -> ViewerType.Text

        ext in setOf(
            "kt", "java", "py", "js", "ts", "tsx", "jsx",
            "html", "css", "cpp", "c", "h", "hpp",
            "rs", "go", "php", "rb", "swift", "sql", "sh"
        ) -> ViewerType.Code

        else -> ViewerType.Unsupported
    }
}
```

---

## 8. SMB の扱い

### 8.1 接続情報

```kotlin
data class SmbConnectionInfo(
    val host: String,
    val shareName: String,
    val username: String?,
    val password: String?,
    val domain: String?,
    val port: Int = 445
)
```

### 8.2 ファイル種別ごとの扱い

```text
PDF / 画像 / テキスト / コード
    ↓
一時キャッシュ
    ↓
OpenedFile.Local として Viewer へ

動画 / 音声
    ↓
SMB ストリーミング
    ↓
OpenedFile.Stream として Viewer へ
```

### 8.3 SMB 動画ストリーミング

`InputStream` だけで済ませないでください。動画のシークに弱くなるためです。

任意位置読み込みを抽象化してください。

```kotlin
interface RemoteReadableFile {
    val size: Long

    suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int

    suspend fun close()
}
```

Media3 へ渡すため、最終的に `SmbDataSource` を実装してください。

```kotlin
class SmbDataSource(
    private val remoteFile: RemoteReadableFile
) : DataSource {
    // open(dataSpec: DataSpec): Long
    // read(buffer: ByteArray, offset: Int, length: Int): Int
    // getUri(): Uri?
    // close()
}
```

難航する場合は、一時的な代替案としてアプリ内 HTTP 中継方式を使ってもよいです。ただし、第一候補は `SmbDataSource` 方式です。

---

## 9. サムネイル設計

### 9.1 Repository

```kotlin
interface ThumbnailRepository {
    suspend fun getThumbnail(file: FileItem): ThumbnailResult
    suspend fun generateThumbnail(file: FileItem): ThumbnailResult
    suspend fun clearThumbnailCache()
}
```

### 9.2 サムネイルキー

```text
thumbnailKey = sourceType + path/uri + size + modifiedAt
```

### 9.3 ルール

- 一覧スクロール中に PDF/動画サムネイル生成を同期実行しない
- サムネイル生成はバックグラウンドで行う
- 生成済みサムネイルは `cacheDir/thumbnails` に保存する
- ファイル更新時にはキーが変わり、再生成されるようにする
- SMB 動画のサムネイルは初期版では動画アイコンでよい

---

## 10. キャッシュ設計

### 10.1 ディレクトリ

```text
cache/
 ├─ smb_cache/
 ├─ thumbnails/
 └─ temp/
```

### 10.2 Repository

```kotlin
interface CacheRepository {
    suspend fun getTotalCacheSize(): Long
    suspend fun getSmbCacheSize(): Long
    suspend fun getThumbnailCacheSize(): Long

    suspend fun clearAll()
    suspend fun clearSmbCache()
    suspend fun clearThumbnailCache()
}
```

### 10.3 設定画面

以下の UI を作ってください。

```text
キャッシュ使用量: xxx MB
SMB 一時ファイル: xxx MB
サムネイル: xxx MB

[すべてのキャッシュを削除]
[SMB 一時ファイルを削除]
[サムネイルキャッシュを削除]
```

---

## 11. Viewer 実装

### 11.1 ViewerRouter

```kotlin
@Composable
fun ViewerRouter(openedFile: OpenedFile) {
    when (openedFile.viewerType) {
        ViewerType.Pdf -> PdfViewerScreen(openedFile)
        ViewerType.Image -> ImageViewerScreen(openedFile)
        ViewerType.Video -> VideoViewerScreen(openedFile)
        ViewerType.Audio -> AudioViewerScreen(openedFile)
        ViewerType.Text -> TextViewerScreen(openedFile)
        ViewerType.Code -> CodeViewerScreen(openedFile)
        ViewerType.Unsupported -> UnsupportedViewerScreen(openedFile)
    }
}
```

### 11.2 各 Viewer の初期仕様

| Viewer | 初期仕様 |
|---|---|
| PDF | `PdfRenderer` でページ表示。ページ送り。ズームは後回し可 |
| Image | Coil で表示。ピンチズームは後回し可 |
| Video | Media3 で再生。ローカル URI と SMB Stream を分岐 |
| Audio | Media3 で再生 |
| Text | UTF-8 表示。大きいファイルは分割読み込みを検討 |
| Code | 等幅フォント。行番号は任意。ハイライトは後回し |
| Unsupported | 未対応表示 + 外部アプリで開く導線 |

---

## 12. 実装 Phase

### Phase 1: ビルド可能な最小基盤

やること:

1. Android Kotlin Compose プロジェクトを確認/作成
2. `FileItem`, `SourceType`, `ViewerType`, `OpenedFile` を追加
3. `FileSource` を追加
4. `detectViewerType` を追加
5. 空の `ViewerRouter` を追加
6. ダミー `FileItem` を表示する `ExplorerScreen` を追加

完了条件:

- ビルドが通る
- ダミーのファイル一覧が表示される
- ファイル種別判定のユニットテストが通る

### Phase 2: ローカルファイル一覧

やること:

1. SAF フォルダ選択を実装
2. 永続 URI 権限を取得
3. `LocalFileSource.list()` を実装
4. フォルダ移動を実装
5. グリッド/リスト表示を実装

完了条件:

- ユーザーが選択したフォルダの中身が表示される
- フォルダ移動できる
- アプリ再起動後も権限が残る

### Phase 3: アイコンとサムネイル

やること:

1. `IconResolver` を作る
2. 拡張子別アイコンを表示する
3. 画像サムネイルを表示する
4. PDF 1ページ目サムネイルを生成する
5. 動画サムネイルを生成する
6. サムネイルキャッシュを実装する

完了条件:

- PDF の1ページ目サムネが表示される
- 動画のサムネが表示される
- コードファイルは拡張子別アイコンが表示される
- スクロール中に大きくカクつかない

### Phase 4: Viewer

やること:

1. `ViewerRouter` を実装
2. `ImageViewerScreen` を実装
3. `PdfViewerScreen` を実装
4. `VideoViewerScreen` を実装
5. `AudioViewerScreen` を実装
6. `TextViewerScreen` を実装
7. `CodeViewerScreen` を実装
8. `UnsupportedViewerScreen` を実装

完了条件:

- 主要ファイルをアプリ内で開ける
- 未対応ファイルでクラッシュしない
- 外部アプリで開く導線がある

### Phase 5: SMB 基本対応

やること:

1. SMB 接続情報モデルを作る
2. SMB 接続画面を作る
3. SMB 接続テストを実装
4. `SmbFileSource.list()` を実装
5. SMB ディレクトリ移動を実装
6. SMB の PDF/画像/テキスト/コードを一時キャッシュして開く

完了条件:

- SMB 共有に接続できる
- SMB のディレクトリ一覧を表示できる
- SMB 上の小さめのファイルを開ける
- 認証失敗時に明確なエラーが出る

### Phase 6: キャッシュ管理

やること:

1. `CacheRepository` を実装
2. SMB 一時キャッシュ容量を計算
3. サムネイルキャッシュ容量を計算
4. 全キャッシュ容量を計算
5. 設定画面に表示
6. 3種類の削除ボタンを実装

完了条件:

- キャッシュ容量が表示される
- SMB 一時キャッシュを削除できる
- サムネイルキャッシュを削除できる
- 全キャッシュを削除できる

### Phase 7: SMB 動画/音声ストリーミング

やること:

1. `RemoteReadableFile` を実装
2. SMB ファイルの任意位置読み込みを実装
3. `SmbDataSource` を実装
4. Media3 と接続
5. ローカル動画と SMB 動画を `VideoViewerScreen` で分岐
6. シークテストを行う
7. 長時間動画を確認する

完了条件:

- SMB 動画をダウンロードせず再生できる
- シークできる
- 長時間動画でクラッシュしない
- SMB 切断時にエラー表示できる

---

## 13. エラー処理方針

曖昧なエラーメッセージは禁止です。

悪い例:

```text
開けませんでした
```

良い例:

```text
SMB 接続に失敗しました。
ホスト名、共有名、ユーザー名、パスワードを確認してください。
```

```text
この動画形式は端末で再生できない可能性があります。
外部アプリで開くことを試してください。
```

---

## 14. テスト方針

最低限、以下を確認してください。

### 14.1 ユニットテスト

- `detectViewerType`
- サムネイルキー生成
- ファイルサイズ整形
- 拡張子別アイコン判定
- キャッシュサイズ計算

### 14.2 手動テスト

- ローカルフォルダ選択
- PDF サムネイル表示
- 動画サムネイル表示
- 画像表示
- PDF 表示
- 動画再生
- テキスト表示
- コード表示
- SMB 接続成功
- SMB 認証失敗
- SMB ファイル一覧
- SMB 小ファイル表示
- SMB 動画ストリーミング
- キャッシュ削除

---

## 15. 実装時の禁止事項

以下を避けてください。

- UI 層から SMBJ を直接呼ぶ
- UI 層で PDF/動画サムネイルを同期生成する
- `InputStream` だけで SMB 動画再生を済ませる
- SMB 動画を毎回フルダウンロードする
- すべてを 1 つの巨大 ViewModel に詰め込む
- `FileItem` に UI 状態を混ぜる
- エラーを握りつぶす
- キャッシュ削除処理を画面 Composable に直書きする
- Android の全ファイルアクセス権限に最初から依存する

---

## 16. 最初の依頼文として Codex に渡すプロンプト

以下をそのまま Codex に渡してください。

```text
Android Kotlin + Jetpack Compose で、ファイルエクスプローラー兼ファイルビューワーアプリを作成してください。

最初から全機能を実装せず、まず Phase 1 の「ビルド可能な最小基盤」を実装してください。

要件:
- Kotlin
- Jetpack Compose
- FileSource 抽象化を先に作る
- FileItem / SourceType / ViewerType / OpenedFile を作る
- detectViewerType() を作る
- ViewerRouter の骨格を作る
- ダミー FileItem を ExplorerScreen にグリッド表示する
- UI から SMB や DocumentFile に直接依存しない構造にする
- 将来 LocalFileSource / SmbFileSource / ThumbnailRepository / CacheRepository / SmbDataSource を追加できる構成にする

ディレクトリ構成は以下を基本にしてください:

app/data/model
app/data/source
app/data/smb
app/data/thumbnail
app/data/cache
app/explorer
app/viewer
app/settings
app/navigation

Phase 1 の完了条件:
- Android Studio でビルドできる
- ダミーのファイル一覧が表示される
- ファイル種別判定のユニットテストがある
- 今後 SMB と Viewer を追加しやすい構成になっている

まず変更内容の設計を説明してから、必要なファイルを作成・修正してください。
```

---

## 17. Phase 2 以降の依頼方法

Phase 1 が完了したら、次のように依頼してください。

```text
Phase 2 として、SAF を使ったローカルファイル一覧を実装してください。
既存の FileSource 抽象化を使い、LocalFileSource を実装してください。
UI 層から DocumentFile を直接扱わないでください。
```

Phase 3:

```text
Phase 3 として、画像/PDF/動画サムネイルと拡張子別アイコン表示を実装してください。
ThumbnailRepository と IconResolver を使い、一覧スクロール中に重い処理を同期実行しないようにしてください。
```

Phase 4:

```text
Phase 4 として、ViewerRouter と各 Viewer を実装してください。
PDF は PdfRenderer、動画/音声は Media3、画像は Coil、テキスト/コードは Compose で表示してください。
```

Phase 5:

```text
Phase 5 として、SMB 基本対応を実装してください。
SMBJ を使い、SmbFileSource で一覧取得とディレクトリ移動を実装してください。
動画/音声以外の SMB ファイルは一時キャッシュして Viewer に渡してください。
```

Phase 6:

```text
Phase 6 として、CacheRepository と設定画面のキャッシュ管理を実装してください。
全キャッシュ削除、SMB 一時ファイル削除、サムネイルキャッシュ削除を実装してください。
```

Phase 7:

```text
Phase 7 として、SMB 動画/音声ストリーミングを実装してください。
RemoteReadableFile と SmbDataSource を作り、Media3 で SMB ファイルをダウンロードせず再生できるようにしてください。
シーク対応を必須にしてください。
```

---

## 18. 参考資料

- Jetpack Compose Lazy lists and grids: https://developer.android.com/develop/ui/compose/lists
- Jetpack Media3: https://developer.android.com/media/media3
- Media3 playback app guide: https://developer.android.com/media/implement/playback-app
- PdfRenderer API reference: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
- SMBJ GitHub: https://github.com/hierynomus/smbj
- Coil Compose documentation: https://coil-kt.github.io/coil/compose/

---

## 19. 最後に

このプロジェクトで一番危険なのは、ファイル一覧 UI を作り込んでから SMB と Viewer を後付けすることです。

必ず以下の順で進めてください。

```text
FileSource
 ↓
FileItem
 ↓
ViewerType
 ↓
ViewerRouter
 ↓
Explorer UI
 ↓
LocalFileSource
 ↓
ThumbnailRepository
 ↓
Viewer
 ↓
SmbFileSource
 ↓
CacheRepository
 ↓
SmbDataSource
```

この順番を崩すと、後で作り直しになります。

# Android ファイルエクスプローラー兼ファイルビューワー 仕様書

作成日: 2026-06-08  
対象: Android / Kotlin / Jetpack Compose

---

## 0. 核心の指摘

このアプリの本質は「見た目のファイル一覧」ではなく、**ローカル/SMB/キャッシュ/ビューワーを統一的に扱うファイル表示基盤**である。

最初に UI だけ作ると、SMB・動画ストリーミング・サムネイルキャッシュを追加した段階で設計が崩れる。したがって、最初に作るべきものは以下である。

1. `FileSource` によるストレージ抽象化
2. `ViewerRouter` によるビューワー抽象化
3. `ThumbnailRepository` によるサムネイル生成/キャッシュ
4. `CacheRepository` によるキャッシュ削除/容量管理
5. SMB 動画用のストリーミング設計

---

## 1. アプリ概要

### 1.1 目的

Android 上で動作する、ファイルエクスプローラー兼ファイルビューワーを作成する。

主な機能は以下。

- ローカルファイルの閲覧
- SMB 共有の閲覧
- PDF 1ページ目のサムネイル表示
- 動画サムネイル表示
- 画像サムネイル表示
- プログラムファイルの拡張子別アイコン表示
- PDF / 画像 / 動画 / 音声 / テキスト / コードの簡易表示
- SMB ファイルの一時キャッシュ
- SMB キャッシュ削除ボタン
- サムネイルキャッシュ削除ボタン
- SMB 動画/音声のストリーミング再生

### 1.2 非目的

初期版では以下を対象外とする。

- Office ファイルの完全な内部表示
- ZIP 内部閲覧
- PDF 注釈編集
- PDF 全文検索
- 高度なコードエディタ
- クラウドストレージ連携
- ファイル同期
- ファイル共有サーバー機能

---

## 2. 技術スタック

| 項目 | 採用候補 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| ファイル一覧 | `LazyVerticalGrid` / `LazyColumn` |
| 画像表示 | Coil |
| PDF 表示/サムネイル | Android 標準 `PdfRenderer` |
| 動画/音声再生 | AndroidX Media3 / ExoPlayer |
| ローカルファイルアクセス | SAF / `DocumentFile` / `MediaStore` |
| SMB 接続 | SMBJ 優先、代替として jcifs-ng |
| キャッシュDB | Room |
| 設定保存 | DataStore、将来的に暗号化 |
| 一時ファイル | `context.cacheDir` |
| 外部アプリ連携 | `FileProvider` |

### 2.1 既存ライブラリに任せるもの

以下は自作しない。

- UI コンポーネント
- 画像読み込み
- PDF レンダリングエンジン
- 動画プレイヤー
- SMB 通信そのもの
- DB/永続化の基礎部分

### 2.2 自作するもの

以下はアプリ固有の中間層として自作する。

- `FileSource` 抽象化
- `LocalFileSource`
- `SmbFileSource`
- `ViewerRouter`
- `ThumbnailRepository`
- `CacheRepository`
- `IconResolver`
- SMB 動画用 `DataSource`
- SMB 接続管理
- サムネイルキー設計
- ファイル種別判定

---

## 3. 対応ファイル種別

### 3.1 ビューワー対象

| 種類 | 対応方針 |
|---|---|
| PDF | アプリ内表示 |
| 画像 | アプリ内表示 |
| 動画 | アプリ内再生 |
| 音声 | アプリ内再生 |
| テキスト | アプリ内表示 |
| コード | アプリ内表示 |
| Office 系 | 外部アプリに委譲 |
| ZIP | 初期版では非対応 |
| 未対応形式 | 未対応画面または外部アプリで開く |

### 3.2 サムネイル対象

| 種類 | サムネイル方針 |
|---|---|
| PDF | 1ページ目を `PdfRenderer` で Bitmap 化 |
| 動画 | 代表フレームを取得 |
| 画像 | 画像そのものを縮小表示 |
| コード | 拡張子別アイコン |
| テキスト | テキストアイコン |
| ディレクトリ | フォルダアイコン |
| その他 | 汎用ファイルアイコン |

---

## 4. アーキテクチャ

### 4.1 ディレクトリ構成案

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

## 5. データモデル

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

## 6. FileSource 設計

### 6.1 共通インターフェース

```kotlin
interface FileSource {
    suspend fun list(path: String): List<FileItem>
    suspend fun open(file: FileItem): OpenedFile
}
```

### 6.2 LocalFileSource

責務:

- SAF / `DocumentFile` 経由でローカルファイル一覧を取得する
- ローカルファイルをそのまま `Uri` として Viewer に渡す
- 権限切れを検出する
- ディレクトリ移動を提供する

処理イメージ:

```text
LocalFileSource.open(file)
 ↓
detectViewerType(file.name, file.mimeType)
 ↓
OpenedFile.Local(uri, viewerType)
```

### 6.3 SmbFileSource

責務:

- SMB 共有に接続する
- ディレクトリ一覧を取得する
- SMB ファイルを開く
- 動画/音声はストリーミングとして開く
- PDF/画像/テキスト/コードは一時キャッシュして開く

処理イメージ:

```text
SmbFileSource.open(file)
 ↓
detectViewerType(file.name, file.mimeType)
 ↓
動画/音声なら OpenedFile.Stream
その他なら cacheDir/smb_cache にコピーして OpenedFile.Local
```

---

## 7. ファイル種別判定

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

## 8. SMB 設計

### 8.1 SMB 接続情報

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

### 8.2 SMB ファイルオープン方針

```text
PDF / 画像 / テキスト / コード
 ↓
一時キャッシュ
 ↓
ローカル URI として Viewer へ渡す

動画 / 音声
 ↓
SMB ストリーミング
 ↓
Media3 / ExoPlayer で再生
```

### 8.3 動画をキャッシュしない理由

動画を毎回ローカルへコピーしてから再生すると、以下の問題が出る。

- 再生開始が遅い
- ストレージを大量消費する
- 長尺動画で破綻する
- キャッシュ削除の重要度が上がりすぎる
- SMB 接続の利点が弱くなる

したがって、動画/音声はストリーミングを基本方針とする。

---

## 9. SMB 動画ストリーミング設計

### 9.1 RemoteReadableFile

シーク対応のため、単なる `InputStream` ではなく、任意位置読み込みを提供する。

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

### 9.2 SmbDataSource

Media3 / ExoPlayer に SMB ファイルを渡すため、独自 `DataSource` を作る。

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

### 9.3 VideoViewer の分岐

```text
OpenedFile.Local
    ↓
MediaItem.fromUri(uri)

OpenedFile.Stream
    ↓
SmbDataSource.Factory
    ↓
Media3 / ExoPlayer
```

### 9.4 代替案

`SmbDataSource` が難航する場合は、アプリ内に簡易 HTTP 中継を立てる。

```text
SMB ファイル
 ↓
Android アプリ内 HTTP サーバー
 ↓
http://127.0.0.1:xxxx/video
 ↓
Media3 で通常 HTTP 動画として再生
```

ただし、第一候補は `SmbDataSource` 方式とする。

---

## 10. サムネイル仕様

### 10.1 ThumbnailRepository

```kotlin
interface ThumbnailRepository {
    suspend fun getThumbnail(file: FileItem): ThumbnailResult
    suspend fun generateThumbnail(file: FileItem): ThumbnailResult
    suspend fun clearThumbnailCache()
}
```

### 10.2 サムネイルキー

```text
thumbnailKey = sourceType + path/uri + size + modifiedAt
```

目的:

- ファイル更新時にサムネイルを再生成する
- 古いサムネイルを誤表示しない
- SMB とローカルの同名ファイルを区別する

### 10.3 PDF サムネイル

```text
PDF ファイル
 ↓
1ページ目を PdfRenderer で Bitmap 化
 ↓
縮小保存
 ↓
一覧画面で表示
```

### 10.4 動画サムネイル

```text
動画ファイル
 ↓
代表フレーム取得
 ↓
縮小保存
 ↓
一覧画面で表示
```

SMB 動画のサムネイルは初期版では動画アイコン表示でよい。後で、先頭部分のみ一時読み込みしてサムネイル生成する。

---

## 11. キャッシュ仕様

### 11.1 キャッシュ分類

```text
cache/
 ├─ smb_cache/
 │   └─ SMB から一時コピーした PDF/画像/テキスト/コード
 │
 ├─ thumbnails/
 │   └─ PDF/動画/画像のサムネイル
 │
 └─ temp/
     └─ 一時処理用
```

### 11.2 CacheRepository

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

### 11.3 設定画面のキャッシュ操作

設定画面に以下を置く。

```text
キャッシュ使用量: xxx MB
SMB 一時ファイル: xxx MB
サムネイル: xxx MB

[すべてのキャッシュを削除]
[SMB 一時ファイルを削除]
[サムネイルキャッシュを削除]
```

### 11.4 削除対象

| ボタン | 削除対象 |
|---|---|
| すべてのキャッシュを削除 | `smb_cache` + `thumbnails` + `temp` |
| SMB 一時ファイルを削除 | `smb_cache` |
| サムネイルキャッシュを削除 | `thumbnails` |

動画/音声の SMB ストリーミングは原則キャッシュしない。

---

## 12. UI 仕様

### 12.1 画面一覧

```text
HomeScreen
 ├─ ローカルストレージ
 ├─ SMB 接続一覧
 └─ 設定

ExplorerScreen
 ├─ ファイル一覧
 ├─ グリッド/リスト切替
 ├─ ソート
 ├─ 戻る
 └─ ファイルタップで Viewer へ

ViewerScreen
 ├─ PDF Viewer
 ├─ Image Viewer
 ├─ Video Viewer
 ├─ Audio Viewer
 ├─ Text Viewer
 ├─ Code Viewer
 └─ Unsupported Viewer

SmbConnectionScreen
 ├─ ホスト
 ├─ 共有名
 ├─ ユーザー名
 ├─ パスワード
 ├─ ドメイン
 └─ 接続テスト

SettingsScreen
 ├─ キャッシュ使用量
 ├─ キャッシュ削除
 └─ アプリ情報
```

### 12.2 ExplorerScreen

必要機能:

- ファイル/フォルダ一覧表示
- グリッド表示
- リスト表示
- サムネイル表示
- 拡張子別アイコン表示
- ファイル名表示
- サイズ表示
- 更新日時表示
- ディレクトリ移動
- 上位ディレクトリへ戻る
- ソート

### 12.3 FileGridItem

表示内容:

```text
[サムネイル or アイコン]
ファイル名
補助情報
```

補助情報例:

```text
PDF / 2.3 MB
MP4 / 128 MB
Folder
Kotlin source
```

---

## 13. Viewer 仕様

### 13.1 ViewerRouter

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

### 13.2 PDF Viewer

初期版:

- ページ表示
- ページ送り
- 1ページずつ `PdfRenderer` で描画
- ズームは後回しでも可

将来:

- ピンチズーム
- ページ一覧
- 検索
- ブックマーク

### 13.3 Image Viewer

初期版:

- 画像表示
- ピンチズーム
- 戻る

将来:

- 回転
- 共有
- スライドショー

### 13.4 Video Viewer

初期版:

- 再生/停止
- シークバー
- 全画面
- ローカル動画再生
- SMB 動画ストリーミング再生

重要:

```text
ローカル動画 → Uri 再生
SMB 動画 → SmbDataSource 再生
```

### 13.5 Text Viewer

初期版:

- UTF-8 テキスト表示
- 長いファイルは分割表示
- 文字化け時はエラー表示

### 13.6 Code Viewer

初期版:

- 等幅フォント表示
- 拡張子表示
- 行番号は任意
- シンタックスハイライトは後回し

将来:

- Kotlin / Java / Python / JS / TS / HTML / CSS / JSON / Markdown の簡易ハイライト
- 検索
- コピー

---

## 14. 拡張子別アイコン仕様

### 14.1 コード系

| 拡張子 | 表示 |
|---|---|
| `.kt` | Kotlin アイコン |
| `.java` | Java アイコン |
| `.py` | Python アイコン |
| `.js` | JavaScript アイコン |
| `.ts` / `.tsx` | TypeScript アイコン |
| `.html` | HTML アイコン |
| `.css` | CSS アイコン |
| `.cpp` / `.c` / `.h` | C/C++ アイコン |
| `.rs` | Rust アイコン |
| `.go` | Go アイコン |
| `.php` | PHP アイコン |
| `.rb` | Ruby アイコン |
| `.swift` | Swift アイコン |
| `.sql` | SQL アイコン |
| `.sh` | Shell アイコン |

### 14.2 汎用

| 種類 | 表示 |
|---|---|
| フォルダ | Folder |
| PDF | PDF |
| 画像 | Image |
| 動画 | Video |
| 音声 | Audio |
| テキスト | Text |
| 不明 | File |

---

## 15. 権限設計

### 15.1 ローカルファイル

基本方針:

```text
SAF でユーザーにフォルダを選ばせる
 ↓
永続 URI 権限を取得
 ↓
DocumentFile で一覧表示
```

Android のストレージ制約を避けるため、初期版では全ファイルアクセス権限に依存しない。

### 15.2 SMB

必要権限:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

SMB 認証情報は慎重に扱う。

初期版:

```text
DataStore
```

将来:

```text
EncryptedSharedPreferences
Android Keystore
```

---

## 16. エラー処理

### 16.1 想定エラー

| 場面 | エラー |
|---|---|
| ローカルアクセス | 権限なし |
| SAF | URI 権限切れ |
| SMB 接続 | ホスト不明 |
| SMB 認証 | ユーザー名/パスワード間違い |
| SMB 一覧 | タイムアウト |
| SMB 動画 | シーク失敗 |
| PDF | 破損ファイル |
| 動画 | コーデック非対応 |
| テキスト | 文字コード非対応 |
| キャッシュ | 容量不足 |

### 16.2 表示方針

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

## 17. MVP 範囲

### 17.1 MVP で作るもの

```text
ローカルファイル一覧
PDF サムネイル
動画サムネイル
画像サムネイル
拡張子別アイコン
PDF Viewer
Image Viewer
Video Viewer
Text Viewer
Code Viewer
SMB 接続
SMB ファイル一覧
SMB の PDF/画像/テキスト/コードを一時キャッシュして表示
キャッシュクリアボタン
```

### 17.2 MVP 後半または次フェーズで作るもの

```text
SMB 動画ストリーミング
SMB 音声ストリーミング
コードのシンタックスハイライト
ファイル検索
最近開いたファイル
ブックマーク
ZIP 内閲覧
WebDAV
Google Drive 連携
```

ただし、SMB 動画ストリーミングは重要機能なので、MVP 後半で必ず検証する。

---

## 18. 実装優先順位

### Phase 1: 基盤

1. Kotlin + Compose プロジェクト作成
2. `FileItem` 作成
3. `ViewerType` 作成
4. `FileSource` インターフェース作成
5. `detectViewerType` 実装
6. 空の `ViewerRouter` 作成

### Phase 2: ローカルエクスプローラー

1. SAF でフォルダ選択
2. `DocumentFile` で一覧取得
3. `ExplorerScreen` 表示
4. フォルダ移動
5. グリッド/リスト表示

### Phase 3: サムネイル

1. 画像サムネイル
2. PDF 1ページ目サムネイル
3. 動画サムネイル
4. 拡張子別アイコン
5. サムネイルキャッシュ

### Phase 4: Viewer

1. `ViewerRouter`
2. `ImageViewer`
3. `PdfViewer`
4. `VideoViewer`
5. `TextViewer`
6. `CodeViewer`
7. `UnsupportedViewer`

### Phase 5: SMB 基本対応

1. SMB 接続画面
2. SMB 接続テスト
3. SMB 一覧取得
4. SMB ディレクトリ移動
5. SMB ファイルを一時キャッシュして開く

### Phase 6: キャッシュ管理

1. キャッシュサイズ計算
2. SMB 一時キャッシュ削除
3. サムネイルキャッシュ削除
4. 全キャッシュ削除
5. 設定画面に表示

### Phase 7: SMB 動画ストリーミング

1. `RemoteReadableFile` 実装
2. SMB 任意位置 read 実装
3. `SmbDataSource` 実装
4. Media3 と接続
5. シーク確認
6. 長時間動画確認

---

## 19. 非機能要件

### 19.1 パフォーマンス

- 一覧スクロール中に重い処理を同期実行しない
- PDF/動画サムネイル生成はバックグラウンドで行う
- サムネイルは必ずキャッシュする
- SMB 一覧取得はタイムアウトを設定する
- SMB 動画は可能な限りストリーミングする
- サムネイル生成ジョブは同時実行数を制限する

### 19.2 安定性

- 破損ファイルでクラッシュしない
- SMB 切断でクラッシュしない
- 権限切れでクラッシュしない
- 容量不足でクラッシュしない
- バックグラウンド処理キャンセル時に中途半端なキャッシュを残さない

### 19.3 保守性

- `LocalFileSource` と `SmbFileSource` を分離する
- Viewer は種類ごとに分離する
- サムネイル生成は Repository に閉じ込める
- SMB 処理を UI に書かない
- キャッシュ削除処理を UI に書かない

---

## 20. 設計上の重要判断

### 20.1 UI から作らない

悪い進め方:

```text
画面を作る
 ↓
ファイルを読む
 ↓
SMB も足す
 ↓
Viewer も足す
 ↓
設計が崩れる
```

良い進め方:

```text
FileSource を作る
 ↓
FileItem を作る
 ↓
ViewerRouter を作る
 ↓
UI を載せる
 ↓
SMB を差し替え可能にする
```

### 20.2 SMB 動画は別設計にする

PDF/画像/テキストはキャッシュでよい。動画/音声までキャッシュすると設計が悪くなる。

```text
小さいファイル → キャッシュ
大きいメディア → ストリーミング
```

### 20.3 ライブラリに任せる部分を間違えない

任せる:

```text
UI
画像表示
PDF 描画
動画再生
DB
SMB 通信
```

自分で握る:

```text
どの FileSource から読むか
どの Viewer で開くか
キャッシュするかストリーミングするか
サムネイルをどう管理するか
```

---

## 21. 将来拡張

- WebDAV 対応
- Google Drive 対応
- OneDrive 対応
- ZIP 内閲覧
- 全文検索
- 最近開いたファイル
- お気に入り
- タグ
- コードハイライト
- Markdown プレビュー
- PDF 検索
- PDF しおり
- SMB 接続情報の暗号化
- タブ表示
- 2ペイン表示
- タブレット最適化

---

## 22. 参考資料

- Jetpack Compose Lazy lists and grids: https://developer.android.com/develop/ui/compose/lists
- Jetpack Media3: https://developer.android.com/media/media3
- Media3 playback app guide: https://developer.android.com/media/implement/playback-app
- PdfRenderer API reference: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
- SMBJ GitHub: https://github.com/hierynomus/smbj
- Coil Compose documentation: https://coil-kt.github.io/coil/compose/

---

## 23. 結論

このアプリは以下の構成で進める。

```text
FileSource でローカル/SMBを抽象化
ViewerRouter でファイル種別ごとに表示を分岐
PDF/画像/テキスト/コードは必要に応じてキャッシュ
動画/音声は SMB ストリーミング
サムネイルは非同期生成 + キャッシュ
設定画面でキャッシュクリア可能にする
```

最初の実装目標は以下。

```text
ローカルファイル一覧
PDF/動画/画像サムネイル
拡張子別アイコン
基本 Viewer
SMB 接続
SMB 一時キャッシュ
キャッシュクリア
```

その後に SMB 動画ストリーミングを実装する。ここを最初から完璧にやろうとすると、アプリ全体の完成が遅れる。

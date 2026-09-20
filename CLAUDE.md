# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Android 用ファイルエクスプローラー兼ファイルビューワー（Kotlin / Jetpack Compose）。詳細仕様は以下の 2 つを参照:

- [android_file_explorer_viewer_spec.md](android_file_explorer_viewer_spec.md) — 全体仕様・設計判断の根拠
- [codex_instructions_android_file_explorer_viewer.md](codex_instructions_android_file_explorer_viewer.md) — 実装 Phase と禁止事項

## ビルド・テストコマンド

JDK 17 / compileSdk 36 / minSdk 26 / AGP 9.2.1。単一モジュール（`:app`）構成。

```powershell
# ビルド
.\gradlew.bat assembleDebug

# ユニットテスト（app/src/test/）
.\gradlew.bat testDebugUnitTest

# 単一テストクラスの実行
.\gradlew.bat testDebugUnitTest --tests "com.ryo.androidfilemanager.data.source.ViewerTypeDetectorTest"

# 端末へインストール
.\gradlew.bat installDebug
```

### SMB 統合テスト

`SmbEnvIntegrationTest` は通常スキップされる。実行するには repo 直下（または上位ディレクトリ）に `.env`（`ADDRESS` 必須、任意で `USERNAME` / `PASSWORD` / `DOMAIN` / `PORT`）を置き、次のいずれかで有効化する:

```powershell
.\gradlew.bat testDebugUnitTest -DandroidFileManager.smbIntegration=true
# または環境変数 ANDROID_FILE_MANAGER_SMB_INTEGRATION=true
```

## アーキテクチャ

本質は「ローカル / SMB / キャッシュ / ビューワーを統一的に扱うファイル表示基盤」。UI ではなく抽象化レイヤーが中心。DI フレームワークや Navigation ライブラリは使っておらず、画面遷移は `navigation/AppNavHost.kt` の Compose state（`openedFile` / `rootSection`）で手動管理している。

### 中核となる抽象

- **`FileSource`**（`data/source/`）: `list(path)` と `open(file)` の 2 メソッドでストレージを抽象化。実装は `LocalFileSource`（SAF / DocumentFile）、`ExternalStorageFileSource`、`SmbFileSource`（SMBJ）
- **`OpenedFile`**（`data/model/`）: `Local(uri)` と `Stream(remoteFile)` の sealed class。SMB の PDF/画像/テキスト/コードは一時キャッシュして `Local`、動画/音声はストリーミングで `Stream` になる
- **`ViewerRouter`**（`viewer/`）: `detectViewerType()`（`data/source/ViewerTypeDetector.kt`）の判定結果で各 Viewer 画面へ分岐
- **`ThumbnailRepository`**（`data/thumbnail/`）: ローカル用 `FileThumbnailRepository` と SMB 用 `SmbThumbnailRepository`。生成は Pdf/Video の Generator、アイコンは `IconResolver`
- **`CacheRepository`**（`data/cache/`）: `cacheDir` 配下の `smb_cache` / `thumbnails` / `temp` の容量計算と削除。設定画面から操作する

### SMB レイヤー（`data/smb/`）

- `SmbConnectionPool`: 接続情報単位で `DiskShare` を再利用するシングルトンプール。接続確立が高コストなため、サムネイル等の細かい読み取りで使い捨て接続にしないこと
- `RemoteReadableFile`: 任意位置読み込み（`readAt`）の抽象。シーク対応のため `InputStream` だけで済ませない
- `RemoteMediaDataSource`: Media3/ExoPlayer 用の DataSource。SMB 動画/音声をダウンロードせず再生する
- `SmbProxyFileDescriptors` + `ChunkedRemoteReader`: `PdfRenderer` のようにシーク可能 fd を要求する API へ、SMB 上のファイルを全量ダウンロードせずに渡す（`ProxyFileDescriptorCallback` 経由）
- 接続情報・フォルダ設定の永続化は DataStore（`SmbConnectionStore`、`data/local/LocalFolderStore`）

### レイヤー規約（仕様書由来・厳守）

- UI 層から SMBJ や `DocumentFile` を直接呼ばない
- UI 層でサムネイルを同期生成しない（一覧スクロール中に重い処理を同期実行しない）
- SMB 動画を毎回フルダウンロードしない（ストリーミングが基本方針）
- キャッシュ削除処理を Composable に直書きしない
- 破損ファイル・権限切れ・SMB 切断でクラッシュさせない。エラーメッセージは原因と対処が分かる具体的な文言にする（「開けませんでした」は禁止）

## Development Environment

- **OS**: Ubuntu or Windows
- **Python**: 3.13 via uv

## 破壊的操作

- ツール（home-manager / brew / chezmoi / pre-commit / pip / npm 等）が auto-rename した `*.backup` / `*.orig` / `*.pre-*` 系を `rm` する前に、内容を `cat` して会話に出すか別ファイルに dump する。最低 1 回の表示を経てから削除する
  （理由: 自分が作ったファイルではないので、消すと「元に何が入っていたか」が永久に失われる。`/etc/zshenv` のような system-level 置き土産が紛れていても気づけなくなる）

## スキル作成

新規 skill を作るとき、配置先を次の指針で決める:

- **project 固有** (`<repo>/.ccodex/skills/` に置く): 特定 repo のドメイン知識・規約・ファイルレイアウトに依存し、他 repo で使う見込みがない
- **グローバル** (`~/.ccodex/skills/` 直置き): 言語・ツール横断、複数 repo で再利用可能、運用ノウハウ
- **判断不能なとき**: ユーザーに「project 固有かグローバルか」を質問してから作成（理由: 後から移動するとパス参照が壊れやすい）

## 運用

**?or？** で終わったらコードの実装はせず、質問に答えるだけにしてください。

# 大規模リファクタリング: `:core` モジュール分離と TDD/DDD 構成（2026-09-21）

ユーザー依頼「`/tdd-ddd-application` で大規模リファクタリング。OSS を活用して巨人の肩に乗る設計に」への対応記録。

## 目標と結果

```text
:core（Kotlin JVM。Android / Compose / SMBJ / DataStore に依存しない）
  domain/       FileItem, ViewerType + 判定, FileSortOption, FileFilter, TransferProgress,
                SmbConnectionInfo + SmbConnectionForm, DirectoryNavigation, FileSelection,
                OpenedFile(Local は uri: String), RemoteReadableFile
  application/  DirectoryBrowser, OpenEntryUseCase, ConnectToShareUseCase, ProgressThrottle, copyWithProgress
  application/port/  FileSource, SmbClient, SmbConnectionRepository
:app（アダプタ）
  Compose UI / ViewModel(StateFlow, ユースケースを呼ぶだけ) / SAF・SMBJ・DataStore・Media3・PdfRenderer / Koin
```

依存方向は `app -> core` のみ。core に Android 型が入ればコンパイルが通らない。

## 採用した OSS（巨人の肩）

| 用途 | 採用 | 理由 |
|---|---|---|
| DI | Koin 4.1（`koin-android`, `koin-androidx-compose`） | 手書き factory を一箇所に集約。コード生成が無く AGP 9 の組み込み Kotlin と干渉しない |
| 状態 | kotlinx.coroutines `StateFlow` + `lifecycle-runtime-compose` の `collectAsStateWithLifecycle` | ViewModel の状態を Compose 非依存にし、ライフサイクルに合わせて購読 |
| テスト | JUnit4 / kotlinx-coroutines-test / Turbine | core は JVM テストだけで回る |
| 既存継続 | Coil, Media3, SMBJ, DataStore, PdfRenderer | 変更なし |

Navigation ライブラリは、戻る操作・全画面・向きの連動が手動実装で安定しているため据え置いた。

## 進め方（skill の手順に沿う）

| フェーズ | 内容 | コミット |
|---|---|---|
| 1. コア分離 | `:core` 新設。既に純 Kotlin だった部分と特性テスト（21 件）を移設。`OpenedFile.Local.uri` を `Uri` から `String` に | `35b3f97` |
| 2. TDD | test-worker が先にテストを書き、未実装によるコンパイル失敗（Red）を確認 → implementation-worker が実装（Green、35 件追加） | `6811e97` |
| 3. アダプタ | ViewModel を StateFlow + ユースケース呼び出しに（refactor-worker）、Koin 配線（implementation-worker） | `798c4d5`, `c43acab` |
| 4. レビュー | review-worker（opus）の指摘に対応。不足テストは先に追加して Red を確認してから実装 | 本コミット |

### コアゲート（アダプタ着手前に確認したこと）

- 要求された振る舞い（履歴、選択、フォーム検証、開く、一覧、接続テスト、進捗の間引き）がユースケース API から UI なしで到達できる
- 不変条件はドメインで強制（ルートで `up()` は自身を返す、フォーム検証、パスワードは trim しない）
- ユースケースのテストは in-memory fake（`FileSource` / `SmbClient` / `SmbConnectionRepository`）だけで動く
- `core/src/main` に `android.*` / `androidx.*` / SMBJ の import が無い（grep で確認）
- core テスト 56 件 green（最終的に 65 件）

## レビュー指摘と対応

| 重大度 | 指摘 | 対応 |
|---|---|---|
| 重大 | 一覧取得のたびに平文パスワード込みの接続情報を DataStore に保存し、その失敗が `runCatching` の外で未捕捉だった | 保存は接続時の 1 回に集約し、失敗は保存済みフラグに留める |
| 中 | `browse` の遷移なし（null）で `isLoading` が残る | 読み込み中表示を戻す |
| 中 | `requireNotNull(currentPath)` が未捕捉クラッシュになり得る | `?: return` |
| 中 | `ProgressThrottle` が完了直後の次ファイル開始フレームを握り潰す / 非 volatile | 完了で間引きをリセット（テスト先行）。`@Volatile` |
| 中 | `RemoteReadableFile` が port にあり domain → application の逆依存 | domain へ移動 |
| 中 | `StateFlow.update` のラムダ内で `_uiState.value` を読む | 引数で渡す |
| 軽微 | 不要キャスト、import 順、`SmbConnectionForm` の同名衝突 | 修正 / 並べ替え / `SmbConnectionFormCard` に改名 |
| テスト | `FileFilter` のテストが無い | 6 件追加 |

### 見送った指摘（次の候補）

- ViewModel が `SmbFileSource` / `SmbConnectionPool` を直接持つ（接続〜切断〜転送がポート化されていない）。次は `SmbTransferPort` として切り出し、ViewModel のテストを書く
- `onCleared()` でサムネイルリポジトリのスコープと接続プールを閉じていない（アプリ終了までリーク）
- `FileFilter` / `FileSortOption` の英語 UI ラベルが domain にある

## 検証

- `.\gradlew.bat assembleDebug testDebugUnitTest :core:test`: BUILD SUCCESSFUL（core 65 件 / app 既存テスト、失敗 0）
- エミュレータ: Koin による起動、Explorer / SMB / Settings の各画面、ローカル PDF を開く、SMB 接続 → 階層移動 → 戻る、を目視確認
- 未確認: 実機、SMB のダウンロード / アップロード（コアのテストとコードの突き合わせのみ）

# AGENTS.md

以下の二つのmdを参考にAndroid用FileExplorerを作る

android_file_explorer_viewer_spec.md
codex_instructions_android_file_explorer_viewer.md


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
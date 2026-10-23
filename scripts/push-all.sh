#!/usr/bin/env bash
# 一键发布：主仓库（完整项目）+ 全部子仓库（拆分镜像）。
#
# 用法：
#   ./scripts/push-all.sh --dry-run              # 只打印将执行的命令
#   ./scripts/push-all.sh                        # 推送 main + 全部子仓库
#   ./scripts/push-all.sh --main-only            # 只推主仓库
#   ./scripts/push-all.sh --subs-only            # 只推子仓库
#   ./scripts/push-all.sh -m "feat: xxx"         # 先提交全部改动，再推送
#   ./scripts/push-all.sh --allow-dirty          # 允许工作区有未提交改动（不建议）
#
# 环境变量：
#   SPLIT_ORG     组织名（默认 GlimBrowser）
#   GIT_REMOTE   主仓库远端名（默认 origin）
#   GIT_BRANCH   主分支名（默认当前分支）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="$ROOT/scripts/repos.conf"
ORG="${SPLIT_ORG:-GlimBrowser}"
REMOTE="${GIT_REMOTE:-origin}"
BRANCH="${GIT_BRANCH:-}"
COMMIT_MSG=""
DRY=0
DO_MAIN=1
DO_SUBS=1
ALLOW_DIRTY=0

usage() { sed -n '2,20p' "$0"; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --main-only) DO_SUBS=0; shift ;;
    --subs-only) DO_MAIN=0; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    -m|--message) COMMIT_MSG="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "未知参数：$1（--help 查看用法）"; exit 1 ;;
  esac
done

cd "$ROOT"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "不是 git 仓库"; exit 1; }
[[ -n "$BRANCH" ]] || BRANCH="$(git branch --show-current)"
[[ -n "$BRANCH" ]] || { echo "无法确定当前分支（detached HEAD？）"; exit 1; }

run() {
  if [[ "$DRY" == "1" ]]; then echo "  [dry-run] $*"; else "$@"; fi
}

echo "== 仓库：$ROOT"
echo "== 组织：https://github.com/$ORG"
echo "== 分支：$BRANCH    远端：$REMOTE"
echo

# --- 0. 提交（可选）---
if [[ -n "$COMMIT_MSG" ]]; then
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "== 提交全部改动：$COMMIT_MSG"
    run git add -A
    run git commit -m "$COMMIT_MSG"
  else
    echo "== 工作区干净，跳过提交"
  fi
fi

# --- 1. 工作区检查 ---
dirty="$(git status --porcelain | wc -l | tr -d ' ')"
if [[ "$dirty" != "0" && "$ALLOW_DIRTY" != "1" ]]; then
  echo "!! 工作区有 $dirty 个未提交改动。先提交，或用 -m \"消息\" 自动提交，或加 --allow-dirty。"
  git status --short | head -10
  exit 1
fi

# --- 2. 子仓库存在性预检（ls-remote，不需要 gh）---
if [[ "$DO_SUBS" == "1" && "$DRY" != "1" ]]; then
  echo "== 预检子仓库是否存在（https）..."
  missing=()
  while IFS='|' read -r path repo desc; do
    [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
    if ! git ls-remote --exit-code "https://github.com/$ORG/$repo.git" >/dev/null 2>&1; then
      missing+=("$repo")
    fi
  done < "$CONF"
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "!! 以下子仓库不存在或无权限（先在组织下创建）："
    printf '   - https://github.com/%s/%s\n' "$ORG" "${missing[@]}"
    echo "   创建：./scripts/create-repos.sh    （或手动在组织页新建）"
    echo "   本次仍会继续推送主仓库；子仓库推送会失败。"
    echo
  fi
fi

# --- 3. 主仓库 ---
if [[ "$DO_MAIN" == "1" ]]; then
  echo "== 推送主仓库（完整项目）→ $REMOTE/$BRANCH"
  run git push -u "$REMOTE" "$BRANCH"
  echo "   $([ "$DRY" = 1 ] && echo '（dry-run）' || echo '完成')"
  echo
fi

# --- 4. 子仓库 ---
if [[ "$DO_SUBS" == "1" ]]; then
  echo "== 拆分并推送子仓库"
  if [[ "$DRY" == "1" ]]; then
    SPLIT_ORG="$ORG" SPLIT_DRY_RUN=1 "$ROOT/scripts/split-repos.sh" --push
  else
    SPLIT_ORG="$ORG" "$ROOT/scripts/split-repos.sh" --push
  fi
  echo
fi

echo "== 全部完成"
echo "   主仓库：https://github.com/$ORG/${REMOTE##*/}"
[[ "$DO_SUBS" == "1" ]] && echo "   子仓库：见 https://github.com/orgs/$ORG/repositories"

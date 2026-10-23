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
#   ./scripts/push-all.sh --check-only           # 只做子仓库存在性预检，不推送
#   ./scripts/push-all.sh --no-skip-missing      # 缺失子仓库也强行推送（会失败）
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
CHECK_ONLY=0
SKIP_MISSING=1

usage() { sed -n '2,20p' "$0"; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --main-only) DO_SUBS=0; shift ;;
    --subs-only) DO_MAIN=0; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --check-only) CHECK_ONLY=1; shift ;;
    --no-skip-missing) SKIP_MISSING=0; shift ;;
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
MISSING_PATHS=()
EXISTING_PATHS=()
if [[ "$DO_SUBS" == "1" && "$DRY" != "1" ]]; then
  echo "== 预检子仓库是否存在（https）..."
  fail_count=0
  total=0
  while IFS='|' read -r path repo desc; do
    [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
    total=$((total + 1))
    if git ls-remote --exit-code "https://github.com/$ORG/$repo.git" >/dev/null 2>&1; then
      EXISTING_PATHS+=("$path")
    else
      MISSING_PATHS+=("$path")
      fail_count=$((fail_count + 1))
    fi
  done < "$CONF"

  if [[ ${#MISSING_PATHS[@]} -gt 0 ]]; then
    echo "!! 有 ${#MISSING_PATHS[@]}/${total} 个子仓库不存在或无权限："
    for p in "${MISSING_PATHS[@]}"; do
      repo="$(awk -F'|' -v p="$p" '$1==p{print $2}' "$CONF")"
      echo "   - https://github.com/$ORG/${repo:-$p}"
    done
    if [[ "$fail_count" == "$total" ]]; then
      echo "   全部失败：通常是仓库还没创建，或本机没有该组织的访问凭证。"
    fi
    echo "   创建：./scripts/create-repos.sh        （或手动在 https://github.com/orgs/$ORG/repositories/new 新建）"
    if [[ "$SKIP_MISSING" == "1" ]]; then
      echo "   本次将跳过不存在的子仓库，只推送已存在的 $((total - fail_count)) 个。"
      echo "   （想强行全部推送：加 --no-skip-missing）"
    fi
    echo
  fi
fi

if [[ "$CHECK_ONLY" == "1" ]]; then
  echo "== 仅检查（--check-only），不推送"
  echo "   存在：${#EXISTING_PATHS[@]}  缺失：${#MISSING_PATHS[@]}"
  exit 0
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
  elif [[ "$SKIP_MISSING" == "1" && ${#MISSING_PATHS[@]} -gt 0 ]]; then
    if [[ ${#EXISTING_PATHS[@]} -eq 0 ]]; then
      echo "   没有已存在的子仓库，跳过。创建后重跑：./scripts/create-repos.sh"
    else
      for p in "${EXISTING_PATHS[@]}"; do
        SPLIT_ORG="$ORG" "$ROOT/scripts/split-repos.sh" --push --only "$p" || true
      done
    fi
  else
    SPLIT_ORG="$ORG" "$ROOT/scripts/split-repos.sh" --push
  fi
  echo
fi

repo_name="$(basename "$(git remote get-url "$REMOTE" 2>/dev/null || echo "$ROOT")" .git)"
[[ -n "$repo_name" && "$repo_name" != "." ]] || repo_name="$(basename "$ROOT")"

echo "== 全部完成"
echo "   主仓库：https://github.com/$ORG/$repo_name"
[[ "$DO_SUBS" == "1" ]] && echo "   子仓库：见 https://github.com/orgs/$ORG/repositories"

#!/usr/bin/env bash
# 把 monorepo 的子目录拆分为独立仓库（git subtree split）。
#
# 用法：
#   ./scripts/split-repos.sh --list                 列出清单
#   ./scripts/split-repos.sh --branch               仅创建本地拆分分支 split/<repo>
#   ./scripts/split-repos.sh --branch --only shared/network
#   SPLIT_ORG=your-org ./scripts/split-repos.sh --push       拆分并推送
#   SPLIT_ORG=your-org SPLIT_DRY_RUN=1 ./scripts/split-repos.sh --push   只看命令
#
# 说明：
#   - 拆分出来的仓库只含该子目录内容，**不含根目录的 Gradle 文件**；
#     它们适合作为只读镜像 / git submodule 引用，独立构建请用 monorepo。
#   - 推送需要目标仓库已存在，且当前用户有 push 权限（SSH key 或 PAT）。
#   - 推送使用 force（--force），因为 subtree split 每次都会重写历史。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="$ROOT/scripts/repos.conf"
ORG="${SPLIT_ORG:-<your-org>}"
DRY_RUN="${SPLIT_DRY_RUN:-0}"
MODE="branch"
ONLY=""

usage() {
  sed -n '2,20p' "$0"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --list) MODE="list"; shift ;;
    --branch) MODE="branch"; shift ;;
    --push) MODE="push"; shift ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "未知参数：$1（用 --help 查看用法）"; exit 1 ;;
  esac
done

[[ -f "$CONF" ]] || { echo "缺少 $CONF"; exit 1; }
cd "$ROOT"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "当前目录不是 git 仓库。先初始化并提交一次："
  echo "  git init -b main && git add -A && git commit -m 'init'"
  exit 1
fi

entries=()
while IFS='|' read -r path repo desc; do
  [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
  entries+=("$path|$repo|$desc")
done < "$CONF"

if [[ "$MODE" == "list" ]]; then
  printf '%-26s %-20s %s\n' "子目录" "子仓库" "说明"
  printf '%-26s %-20s %s\n' "------" "------" "----"
  for e in "${entries[@]}"; do
    IFS='|' read -r path repo desc <<< "$e"
    printf '%-26s %-20s %s\n' "$path" "$repo" "$desc"
  done
  exit 0
fi

# 把根仓库的 LICENSE/NOTICE 追加到拆分分支（子仓库不能"无许可证"分发）
attach_license() {
  local branch="$1"
  local tmp
  tmp="$(mktemp -d)"
  git worktree add -q "$tmp" "$branch"
  if [[ ! -f "$tmp/LICENSE" ]]; then
    cp LICENSE NOTICE "$tmp"/
    git -C "$tmp" add LICENSE NOTICE
    git -C "$tmp" commit -q -m "chore: 继承根仓库许可证（GlimBrowser Non-Commercial 1.0）"
  fi
  git worktree remove "$tmp" --force
}

split_one() {
  local path="$1" repo="$2" desc="$3"
  if [[ -n "$ONLY" && "$path" != "$ONLY" ]]; then return 0; fi
  if [[ ! -d "$path" ]]; then
    echo "跳过（目录不存在）：$path"
    return 0
  fi

  local branch="split/$repo"
  local remote="git@github.com:$ORG/$repo.git"
  local count
  count="$(git ls-files "$path" | wc -l | tr -d ' ')"

  echo "→ ${path} → ${repo}（${count} 个文件）"
  # 每次都从当前 main 重拆：先删除旧分支（否则 subtree split 会因分支已存在而失败）
  git branch -D "$branch" >/dev/null 2>&1 || true

  if [[ "$MODE" == "push" ]]; then
    git subtree split --prefix="$path" -b "$branch" >/dev/null
    attach_license "$branch"
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "   [dry-run] git push --force $remote $branch:main"
    else
      echo "   推送到 $remote"
      git push --force "$remote" "$branch:main"
    fi
  else
    git subtree split --prefix="$path" -b "$branch" >/dev/null
    attach_license "$branch"
    echo "   已创建本地分支 ${branch}（$(git rev-parse --short "${branch}")）"
  fi
}

if [[ -n "$ONLY" ]]; then
  found=0
  for e in "${entries[@]}"; do
    IFS='|' read -r path repo desc <<< "$e"
    [[ "$path" == "$ONLY" ]] && found=1
  done
  [[ "$found" == "1" ]] || { echo "清单里没有 ${ONLY}（用 --list 查看）"; exit 1; }
fi

for e in "${entries[@]}"; do
  IFS='|' read -r path repo desc <<< "$e"
  split_one "$path" "$repo" "$desc"
done

echo
echo "完成。"
[[ "$MODE" == "branch" ]] && echo "查看：git log --oneline split/<repo> | head"
[[ "$MODE" == "push" ]] && echo "记得在 GitHub 上把每个子仓库设为 public，并按需选择许可证。"

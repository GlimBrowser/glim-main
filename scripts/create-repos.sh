#!/usr/bin/env bash
# 在 GitHub 组织下创建子仓库（需要 gh CLI 已登录，且有组织建仓权限）。
#
# 用法：
#   ./scripts/create-repos.sh --dry-run         # 只打印将要执行的命令
#   ./scripts/create-repos.sh                   # 创建（public）
#   ./scripts/create-repos.sh --private         # 创建为 private
#   ./scripts/create-repos.sh --only glim-network
#
# 组织默认 GlimBrowser，可用 SPLIT_ORG 覆盖。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ORG="${SPLIT_ORG:-GlimBrowser}"
CONF="$ROOT/scripts/repos.conf"
DRY=0
VISIBILITY="--public"
ONLY=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --private) VISIBILITY="--private"; shift ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "未知参数：$1"; exit 1 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "未安装 gh CLI：https://cli.github.com/"; exit 1; }

if [[ "$DRY" != "1" ]]; then
  gh auth status >/dev/null 2>&1 || { echo "gh 未登录：gh auth login"; exit 1; }
fi

while IFS='|' read -r path repo desc; do
  [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
  [[ -n "$ONLY" && "$repo" != "$ONLY" ]] && continue
  if [[ "$DRY" == "1" ]]; then
    echo "gh repo create $ORG/$repo $VISIBILITY --description \"$desc\""
  else
    if gh repo view "$ORG/$repo" >/dev/null 2>&1; then
      echo "已存在，跳过：$ORG/$repo"
    else
      gh repo create "$ORG/$repo" "$VISIBILITY" --description "$desc"
      echo "已创建：https://github.com/$ORG/$repo"
    fi
  fi
done < "$CONF"

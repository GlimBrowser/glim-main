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
#   ./scripts/push-all.sh --preflight            # 先探测子仓库可访问性（私有仓库需凭证，否则只提示"无法确认"）
#   ./scripts/push-all.sh --skip-missing         # 跳过探测失败的子仓库（默认不跳过）
#
# 默认使用 gh CLI 取凭证（无需手动配 SSH key / PAT）：
#   gh auth login                     # 一次登录
#   ./scripts/push-all.sh             # 自动 gh auth token + HTTPS 推送
#
# 环境变量：
#   USE_GH        1=用 gh 取 token（默认 auto：装了 gh 且已登录就用）
#   SPLIT_ORG     组织名（默认 GlimBrowser）
#   GIT_REMOTE   主仓库远端名（默认 origin）
#   GIT_BRANCH   主分支名（默认当前分支）
#   SPLIT_TOKEN      有 repo 权限的 PAT（私有仓库走 HTTPS）
#   SPLIT_REMOTE_BASE 子仓库远端前缀（默认 git@github.com:<ORG>；用 HTTPS 时设为 https://github.com/<ORG>）
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
USE_GH=auto
CREATE_MISSING=0
SKIP_MISSING=0
PREFLIGHT=0

usage() { sed -n '2,20p' "$0"; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --main-only) DO_SUBS=0; shift ;;
    --subs-only) DO_MAIN=0; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --check-only) CHECK_ONLY=1; shift ;;
    --gh) USE_GH=1; shift ;;
    --no-gh) USE_GH=0; shift ;;
    --create-missing) CREATE_MISSING=1; shift ;;
    --no-skip-missing) SKIP_MISSING=0; shift ;;
    --preflight) PREFLIGHT=1; shift ;;
    --no-preflight) PREFLIGHT=0; shift ;;
    -m|--message) COMMIT_MSG="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "未知参数：$1（--help 查看用法）"; exit 1 ;;
  esac
done

cd "$ROOT"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "不是 git 仓库"; exit 1; }
[[ -n "$BRANCH" ]] || BRANCH="$(git branch --show-current)"
[[ -n "$BRANCH" ]] || { echo "无法确定当前分支（detached HEAD？）"; exit 1; }

# --- gh 集成：自动取 token，统一走 HTTPS ---
if [[ "$USE_GH" == "auto" ]]; then
  if command -v gh >/dev/null 2>&1; then USE_GH=1; else USE_GH=0; fi
fi

if [[ "$USE_GH" == "1" ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "!! 指定了 --gh 但未安装 gh CLI：https://cli.github.com/"
    exit 1
  fi
  if [[ "$DRY" != "1" ]]; then
    if ! gh auth status >/dev/null 2>&1; then
      echo "!! gh 未登录。先执行：gh auth login"
      exit 1
    fi
    TOKEN="$(gh auth token 2>/dev/null || true)"
    if [[ -z "$TOKEN" ]]; then
      echo "!! 无法从 gh 获取 token（gh auth token 失败）"
      exit 1
    fi
    SPLIT_TOKEN="$TOKEN"
    export SPLIT_TOKEN
  else
    # dry-run：用占位 token，让拆分子仓库的命令也按 HTTPS 形态打印（显示时会被掩码）
    SPLIT_TOKEN="${SPLIT_TOKEN:-DRY_RUN_TOKEN}"
    export SPLIT_TOKEN
  fi
  SPLIT_REMOTE_BASE="${SPLIT_REMOTE_BASE:-https://github.com/$ORG}"
  echo "== 使用 gh（HTTPS）推送；远端前缀：$SPLIT_REMOTE_BASE"
  echo
fi

# 主仓库名：优先从 origin 推导，否则用目录名
repo_name="$(basename "$(git remote get-url "$REMOTE" 2>/dev/null || echo "$ROOT")" .git)"
[[ -n "$repo_name" && "$repo_name" != "." ]] || repo_name="$(basename "$ROOT")"

# 用 gh 创建缺失的子仓库（可选）
if [[ "$CREATE_MISSING" == "1" && "$USE_GH" == "1" ]]; then
  echo "== 用 gh 创建缺失的子仓库"
  while IFS='|' read -r path repo desc; do
    [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
    if gh repo view "$ORG/$repo" >/dev/null 2>&1; then
      echo "   已存在：$ORG/$repo"
    else
      run gh repo create "$ORG/$repo" --public --description "$desc"
    fi
  done < "$CONF"
  echo
fi

# 子仓库远端：优先 PAT（HTTPS），否则 SSH。
remote_url() {
  local repo="$1"
  if [[ -n "${SPLIT_TOKEN:-}" ]]; then
    echo "https://x-access-token:${SPLIT_TOKEN}@github.com/${ORG}/${repo}.git"
  else
    echo "${SPLIT_REMOTE_BASE:-git@github.com:$ORG}/${repo}.git"
  fi
}

# 打印用（隐藏 token）
remote_display() {
  local repo="$1"
  if [[ -n "${SPLIT_TOKEN:-}" ]]; then
    echo "https://x-access-token:***@github.com/${ORG}/${repo}.git"
  else
    echo "${SPLIT_REMOTE_BASE:-git@github.com:$ORG}/${repo}.git"
  fi
}

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

# --- 2. 子仓库可访问性预检（可选，--preflight）---
# 注意：
#   - 新创建的空仓库没有任何 ref，git ls-remote --exit-code 会返回 2（误判为不存在），
#     因此这里**不使用 --exit-code**：只要能连上（exit 0）就视为可推送。
#   - 私有仓库在无凭证时 ls-remote 会失败，因此"失败"只代表无法确认，不代表不存在。
UNCONFIRMED_PATHS=()
CONFIRMED_PATHS=()
if [[ "$DO_SUBS" == "1" && "$DRY" != "1" && "$PREFLIGHT" == "1" ]]; then
  echo "== 预检子仓库可访问性（$([[ -n "${SPLIT_TOKEN:-}" ]] && echo "HTTPS + PAT" || echo "SSH")）..."
  total=0
  while IFS='|' read -r path repo desc; do
    [[ -z "${path// }" || "${path:0:1}" == "#" ]] && continue
    total=$((total + 1))
    if GIT_TERMINAL_PROMPT=0 git ls-remote "$(remote_url "$repo")" >/dev/null 2>&1; then
      CONFIRMED_PATHS+=("$path")
    else
      UNCONFIRMED_PATHS+=("$path")
    fi
  done < "$CONF"

  echo "   可访问：${#CONFIRMED_PATHS[@]}/${total}；无法确认：${#UNCONFIRMED_PATHS[@]}/${total}"
  if [[ ${#UNCONFIRMED_PATHS[@]} -gt 0 ]]; then
    echo "   以下仓库无法确认（私有仓库/无凭证/无网络都会这样，**不代表不存在**）："
    for p in "${UNCONFIRMED_PATHS[@]}"; do
      repo="$(awk -F'|' -v p="$p" '$1==p{print $2}' "$CONF")"
      echo "   - $(remote_display "${repo:-$p}")"
    done
    if [[ ${#CONFIRMED_PATHS[@]} -eq 0 ]]; then
      sample_repo="$(awk -F'|' 'NF>1 && $1 !~ /^#/{print $2; exit}' "$CONF")"
      echo "   诊断样本（$(remote_display "${sample_repo}")）："
      GIT_TERMINAL_PROMPT=0 git ls-remote "$(remote_url "${sample_repo}")" 2>&1 | head -3 | sed 's/^/     /'
      echo
    fi
    echo "   私有仓库请设置凭证后重试："
    echo "     SPLIT_TOKEN=<有 repo 权限的 PAT> ./scripts/push-all.sh"
    echo "   或确认 SSH key 已加入 GitHub 账户。"
    if [[ "$SKIP_MISSING" == "1" ]]; then
      echo "   --skip-missing 已开启：只推送可访问的 ${#CONFIRMED_PATHS[@]} 个。"
    else
      echo "   默认不跳过，仍会尝试推送全部 ${total} 个。"
    fi
    echo
  fi
fi

if [[ "$CHECK_ONLY" == "1" ]]; then
  echo "== 仅检查（--check-only），不推送"
  echo "   可访问：${#CONFIRMED_PATHS[@]}  无法确认：${#UNCONFIRMED_PATHS[@]}"
  exit 0
fi

# --- 3. 主仓库 ---
if [[ "$DO_MAIN" == "1" ]]; then
  echo "== 推送主仓库（完整项目）→ $REMOTE/$BRANCH"
  if [[ "$USE_GH" == "1" ]]; then
    if [[ "$DRY" == "1" ]]; then
      echo "  [dry-run] gh repo view $ORG/$repo_name || gh repo create $ORG/$repo_name --public --source=. --remote=$REMOTE --push"
    elif gh repo view "$ORG/$repo_name" >/dev/null 2>&1; then
      echo "   仓库已存在：https://github.com/$ORG/$repo_name"
    else
      echo "   仓库不存在，用 gh 创建并推送…"
      gh repo create "$ORG/$repo_name" --public --source=. --remote="$REMOTE" --push
      echo "   已创建并推送：https://github.com/$ORG/$repo_name"
    fi
  fi
  run git push -u "$REMOTE" "$BRANCH"
  echo "   $([ "$DRY" = 1 ] && echo '（dry-run）' || echo '完成')"
  echo
fi

# --- 4. 子仓库 ---
if [[ "$DO_SUBS" == "1" ]]; then
  echo "== 拆分并推送子仓库"
  if [[ "$DRY" == "1" ]]; then
    SPLIT_ORG="$ORG" SPLIT_DRY_RUN=1 "$ROOT/scripts/split-repos.sh" --push
  elif [[ "$SKIP_MISSING" == "1" && ${#UNCONFIRMED_PATHS[@]} -gt 0 ]]; then
    if [[ ${#CONFIRMED_PATHS[@]} -eq 0 ]]; then
      echo "   没有可确认的子仓库，跳过（去掉 --skip-missing 可强制尝试）"
    else
      for p in "${CONFIRMED_PATHS[@]}"; do
        SPLIT_ORG="$ORG" "$ROOT/scripts/split-repos.sh" --push --only "$p" || true
      done
    fi
  else
    SPLIT_ORG="$ORG" "$ROOT/scripts/split-repos.sh" --push
  fi
  echo
fi

echo "== 全部完成"
echo "   主仓库：https://github.com/$ORG/$repo_name"
[[ "$DO_SUBS" == "1" ]] && echo "   子仓库：见 https://github.com/orgs/$ORG/repositories"

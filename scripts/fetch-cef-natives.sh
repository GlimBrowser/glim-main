#!/usr/bin/env bash
# 下载 JCEF 原生库（macOS arm64，约 118MB）到 local-libs/
# 版本需与 jcefmaven 对齐（见 .github 或 app/shared/build.gradle.kts）
set -euo pipefail
VER='jcef-1770317+cef-132.3.1+g144febe+chromium-132.0.6834.83'
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../local-libs" && pwd 2>/dev/null || mkdir -p "$(dirname "${BASH_SOURCE[0]}")/../local-libs" && cd "$(dirname "${BASH_SOURCE[0]}")/../local-libs" && pwd)"
OUT="$DIR/jcef-natives-macosx-arm64.jar"
URL="https://repo1.maven.org/maven2/me/friwi/jcef-natives-macosx-arm64/$VER/jcef-natives-macosx-arm64-$VER.jar"
if [[ -f "$OUT" ]]; then echo "已存在：$OUT"; exit 0; fi
echo "下载 $URL"
curl -L --fail --retry 3 -C - -o "$OUT" "$URL"
ls -lh "$OUT"

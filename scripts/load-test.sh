#!/usr/bin/env bash
# 小规模压测基线:热点只读接口,ab 打点,结果人工整理到 docs/load-test-results.md
# 用法: 先起 compose 栈,然后 BASE_URL=http://localhost SHARE_TOKEN=xxx ./load-test.sh
set -euo pipefail

BASE="${BASE_URL:-http://localhost}"
SHARE_TOKEN="${SHARE_TOKEN:?SHARE_TOKEN is required(分享页 token,可从清单分享链接取)}"

bench() {
  local name="$1" path="$2" n="${3:-1000}" c="${4:-10}"
  echo "== $name  n=$n c=$c =="
  ab -n "$n" -c "$c" "$BASE$path" | grep -E "Requests per second|Time per request|99%|50%" || true
}

bench "health"      "/api/health"
bench "share_view"  "/api/share/$SHARE_TOKEN"
bench "share_page"  "/s/$SHARE_TOKEN"
# 登录态接口可选:AUTH_TOKEN=xxx 时才跑(端点路径以实际 controller 为准:/api/circles)
if [ -n "${AUTH_TOKEN:-}" ]; then
  echo "== 登录态接口 =="
  ab -n 1000 -c 10 -H "Authorization: Bearer $AUTH_TOKEN" "$BASE/api/circles" | grep -E "Requests per second|Time per request|99%|50%" || true
fi

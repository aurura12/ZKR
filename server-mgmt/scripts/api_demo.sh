#!/usr/bin/env bash
# API 端到端演示：跑完后完成「查服务器 → 建组 → 建用户 → 查状态 → 分配组 → 清理」
#
# 用法:
#   chmod +x scripts/api_demo.sh
#   ./scripts/api_demo.sh
#
# 可选:
#   BASE_URL=http://127.0.0.1:8765 SERVER_NAME=srv3 ./scripts/api_demo.sh
#   SKIP_CLEANUP=1 ./scripts/api_demo.sh   # 不删除演示用户

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8765}"
DEMO_USER="${DEMO_USER:-demo_api_user}"
DEMO_GROUP="${DEMO_GROUP:-demoDevelopers}"
DEMO_KEY="${DEMO_KEY:-ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDemoKeyForApiTestingOnly demo@api.test}"
SKIP_CLEANUP="${SKIP_CLEANUP:-0}"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

step() { echo -e "\n${CYAN}==> $1${NC}"; }
ok()   { echo -e "${GREEN}✔ $1${NC}"; }
fail() { echo -e "${RED}✘ $1${NC}"; exit 1; }
ok_json() {
  python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get('ok') else 1)" 2>/dev/null
}

health_ok() {
  python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='ok' else 1)" 2>/dev/null
}

pretty() {
  python3 -m json.tool 2>/dev/null || cat
}

request() {
  local method="$1" url="$2"
  shift 2
  curl -sS -X "$method" "$url" "$@"
}

echo "Base URL: $BASE_URL"

# ---------------------------------------------------------------------------
step "0. 健康检查 GET /api/health"
resp="$(request GET "$BASE_URL/api/health")"
echo "$resp" | pretty
echo "$resp" | health_ok || fail "健康检查失败"

# ---------------------------------------------------------------------------
step "1. 查询服务器选项 GET /api/servers/options"
resp="$(request GET "$BASE_URL/api/servers/options")"
echo "$resp" | pretty

SERVER_NAME="${SERVER_NAME:-$(echo "$resp" | python3 -c "
import json, sys
data = json.load(sys.stdin).get('data') or []
print(data[0] if data else '')
")}"

if [ -z "$SERVER_NAME" ]; then
  fail "数据库中没有服务器。请先执行:\n  sudo docker compose --profile tools run --rm import"
fi
ok "使用服务器: $SERVER_NAME"

# ---------------------------------------------------------------------------
step "2. 查询全部服务器 GET /api/servers"
request GET "$BASE_URL/api/servers" | pretty

# ---------------------------------------------------------------------------
step "3. 创建 Group POST /api/groups (name=$DEMO_GROUP)"
resp="$(request POST "$BASE_URL/api/groups" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$DEMO_GROUP\"}")"
echo "$resp" | pretty
if echo "$resp" | ok_json; then
  ok "组已创建"
elif echo "$resp" | grep -q '已存在'; then
  ok "组已存在，继续"
else
  fail "创建组失败: $resp"
fi

# ---------------------------------------------------------------------------
step "4. 查询全部 Group GET /api/groups"
request GET "$BASE_URL/api/groups" | pretty

# ---------------------------------------------------------------------------
step "5. 新增用户 POST /api/users (name=$DEMO_USER, server=$SERVER_NAME)"
resp="$(request POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"$DEMO_USER\",
    \"server_name\": \"$SERVER_NAME\",
    \"ssh_key\": \"$DEMO_KEY\",
    \"state\": \"present\"
  }")"
echo "$resp" | pretty
echo "$resp" | ok_json || fail "新增用户失败"

# ---------------------------------------------------------------------------
step "6. 查询全部用户 GET /api/users"
request GET "$BASE_URL/api/users" | pretty

step "6b. 按条件筛选 GET /api/users?name=$DEMO_USER&server_name=$SERVER_NAME"
request GET "$BASE_URL/api/users?name=$DEMO_USER&server_name=$SERVER_NAME" | pretty

# ---------------------------------------------------------------------------
step "7. 检查用户状态 GET /api/users/status"
resp="$(request GET "$BASE_URL/api/users/status?name=$DEMO_USER&server_name=$SERVER_NAME")"
echo "$resp" | pretty
echo "$resp" | python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get('data',{}).get('exists') else 1)" || fail "用户状态应为 exists=true"

# ---------------------------------------------------------------------------
step "8. 分配 Group POST /api/users/groups"
resp="$(request POST "$BASE_URL/api/users/groups" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"$DEMO_USER\",
    \"server_name\": \"$SERVER_NAME\",
    \"group_name\": \"$DEMO_GROUP\"
  }")"
echo "$resp" | pretty
echo "$resp" | ok_json || fail "分配组失败"
echo "$resp" | grep -q "$DEMO_GROUP" || fail "响应中未包含组名"

# ---------------------------------------------------------------------------
step "9. 再次检查状态（应含 groups）"
request GET "$BASE_URL/api/users/status?name=$DEMO_USER&server_name=$SERVER_NAME" | pretty

# ---------------------------------------------------------------------------
if [ "$SKIP_CLEANUP" = "1" ]; then
  ok "SKIP_CLEANUP=1，保留演示用户 $DEMO_USER @ $SERVER_NAME"
else
  step "10. 删除演示用户 DELETE /api/users"
  resp="$(request DELETE "$BASE_URL/api/users" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"$DEMO_USER\", \"server_name\": \"$SERVER_NAME\"}")"
  echo "$resp" | pretty
  echo "$resp" | ok_json || fail "删除用户失败"

  step "11. 确认已删除"
  resp="$(request GET "$BASE_URL/api/users/status?name=$DEMO_USER&server_name=$SERVER_NAME")"
  echo "$resp" | pretty
  echo "$resp" | python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get('data',{}).get('exists') is False else 1)" || fail "用户应已不存在"
  ok "演示用户已清理"
fi

echo ""
ok "全部 API 演示完成"
echo ""
  echo "  curl -X POST $BASE_URL/api/deploy -H 'Content-Type: application/json' -d '{\"server_name\":\"$SERVER\"}'"

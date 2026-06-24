#!/usr/bin/env bash
# 在指定服务器上创建新用户（写入数据库，可选同步到远程）
#
# 用法:
#   ./scripts/add_user.sh <用户名> <服务器> <SSH公钥>
#   ./scripts/add_user.sh <用户名> <服务器> --key-file ~/.ssh/id_ed25519.pub
#   ./scripts/add_user.sh   # 交互模式
#
# 示例:
#   ./scripts/add_user.sh zhangsan srv3 "ssh-ed25519 AAAA... zhangsan@pc"
#   ./scripts/add_user.sh zhangsan srv3 --key-file ./keys/zhangsan.pub
#   ./scripts/add_user.sh zhangsan srv3 --key-file ./keys/zhangsan.pub --deploy
#
# 环境变量:
#   BASE_URL   API 地址，默认 http://127.0.0.1:8765

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8765}"
DEPLOY=0
KEY_FILE=""
STATE="present"
USERNAME=""
SERVER=""
SSH_KEY=""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

fail() { echo -e "${RED}错误: $1${NC}" >&2; exit 1; }
ok()   { echo -e "${GREEN}✔ $1${NC}"; }
info() { echo -e "${CYAN}$1${NC}"; }

api_get() {
  curl -sS -f "$1"
}

api_post_json() {
  local url="$1" body="$2"
  curl -sS -f -X POST "$url" -H "Content-Type: application/json" -d "$body"
}

list_servers() {
  api_get "$BASE_URL/api/servers/options" | python3 -c "
import json, sys
for name in json.load(sys.stdin).get('data') or []:
    print(name)
"
}

validate_server() {
  local target="$1"
  list_servers | grep -qx "$target" || fail "服务器 '$target' 不存在。可选: $(list_servers | tr '\n' ' ')"
}

read_ssh_key() {
  if [ -n "$KEY_FILE" ]; then
    [ -f "$KEY_FILE" ] || fail "公钥文件不存在: $KEY_FILE"
    SSH_KEY="$(tr -d '\n\r' < "$KEY_FILE")"
  fi
  [ -n "$SSH_KEY" ] || fail "未提供 SSH 公钥"
  [ "${#SSH_KEY}" -ge 20 ] || fail "SSH 公钥过短，请检查输入"
}

create_user() {
  local body resp
  body="$(
    ADD_USER_NAME="$USERNAME" \
    ADD_USER_SERVER="$SERVER" \
    ADD_USER_KEY="$SSH_KEY" \
    ADD_USER_STATE="$STATE" \
    python3 -c "
import json, os
print(json.dumps({
    'name': os.environ['ADD_USER_NAME'],
    'server_name': os.environ['ADD_USER_SERVER'],
    'ssh_key': os.environ['ADD_USER_KEY'],
    'state': os.environ['ADD_USER_STATE'],
}, ensure_ascii=False))
"
  )"
  info "正在创建用户 $USERNAME @ $SERVER ..."
  if ! resp="$(api_post_json "$BASE_URL/api/users" "$body" 2>&1)"; then
    if echo "$resp" | grep -q 'detail'; then
      echo "$resp" | python3 -m json.tool 2>/dev/null || echo "$resp"
    fi
    fail "创建用户失败（请确认 API 已启动: docker compose up -d）"
  fi

  echo "$resp" | python3 -m json.tool
  ok "用户已写入数据库: $USERNAME @ $SERVER"
}

deploy_to_server() {
  info "通过 API 部署到远程服务器 ${SERVER} ..."
  local body resp
  body="$(python3 -c "import json; print(json.dumps({'server_name': '$SERVER'}))")"
  if ! resp="$(curl -sS -f -X POST "$BASE_URL/api/deploy" -H "Content-Type: application/json" -d "$body" 2>&1)"; then
    echo "$resp" | python3 -m json.tool 2>/dev/null || echo "$resp"
    fail "部署失败（若需 sudo 密码，请用: curl -X POST $BASE_URL/api/deploy -d '{\"server_name\":\"$SERVER\",\"become_password\":\"你的密码\"}'）"
  fi
  echo "$resp" | python3 -m json.tool
  ok "已部署到 $SERVER"
}

interactive_mode() {
  info "=== 交互创建用户 ==="
  echo "可选服务器:"
  list_servers | sed 's/^/  - /'
  echo ""
  read -rp "用户名 (小写, 如 zhangsan): " USERNAME
  read -rp "服务器名称 (如 srv3): " SERVER
  echo "SSH 公钥输入方式: 1=粘贴公钥  2=从文件读取"
  read -rp "选择 [1/2]: " key_mode
  case "$key_mode" in
    2)
      read -rp "公钥文件路径: " KEY_FILE
      read_ssh_key
      ;;
    *)
      echo "请粘贴公钥（单行），回车结束:"
      read -r SSH_KEY
      ;;
  esac
  echo ""
  read -rp "创建后立即部署到远程服务器? [y/N]: " ans
  case "$ans" in
    y|Y|yes|YES) DEPLOY=1 ;;
  esac
}

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
if [ $# -eq 0 ]; then
  interactive_mode
else
  case "${1:-}" in
    -h|--help) usage 0 ;;
  esac

  if [ $# -lt 2 ]; then
    usage 1
  fi

  USERNAME="$1"
  SERVER="$2"
  shift 2

  while [ $# -gt 0 ]; do
    case "$1" in
      --key-file)
        [ $# -ge 2 ] || fail "--key-file 需要文件路径"
        KEY_FILE="$2"
        shift 2
        ;;
      --deploy)
        DEPLOY=1
        shift
        ;;
      --state)
        [ $# -ge 2 ] || fail "--state 需要 present 或 absent"
        STATE="$2"
        shift 2
        ;;
      -h|--help)
        usage 0
        ;;
      *)
        if [ -z "$SSH_KEY" ]; then
          SSH_KEY="$1"
          shift
        else
          fail "未知参数: $1"
        fi
        ;;
    esac
  done

  read_ssh_key
fi

# ---------------------------------------------------------------------------
# 校验并执行
# ---------------------------------------------------------------------------
[ -n "$USERNAME" ] || fail "用户名不能为空"
[ -n "$SERVER" ] || fail "服务器不能为空"

api_get "$BASE_URL/api/health" >/dev/null 2>&1 || fail "API 不可达 ($BASE_URL)，请先启动: sudo docker compose up -d"

validate_server "$SERVER"
create_user

if [ "$DEPLOY" = "1" ]; then
  deploy_to_server
else
  echo ""
  info "用户已在数据库中创建。部署到远程服务器:"
  echo "  curl -X POST $BASE_URL/api/deploy -H 'Content-Type: application/json' -d '{\"server_name\":\"$SERVER\"}'"
  echo "  或: ./scripts/add_user.sh ... --deploy"
fi

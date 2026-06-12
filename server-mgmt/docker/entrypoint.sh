#!/bin/sh
set -e

cd /app
export PYTHONPATH=/app

python scripts/db_manage.py init

# 仅当数据库尚无服务器记录时，从挂载文件导入（避免每次重启覆盖 API 改动）
if [ "${IMPORT_ON_START}" = "1" ] && [ -f /app/inventory.ini ] && [ -f /app/users.yml ]; then
  python3 -c "
from pathlib import Path
from db.database import get_connection, list_servers, import_inventory, import_users_yml
with get_connection() as conn:
    if not list_servers(conn):
        n1 = import_inventory(Path('/app/inventory.ini'), conn)
        n2 = import_users_yml(Path('/app/users.yml'), conn)
        conn.commit()
        print(f'首次导入: {n1} 台服务器, {n2} 条用户')
" 2>/dev/null || true
fi

case "${1:-api}" in
  api)
    exec uvicorn api.main:app --host 0.0.0.0 --port "${PORT:-8765}"
    ;;
  export)
    python scripts/db_manage.py export
    ;;
  import)
    python scripts/db_manage.py import
    ;;
  playbook-users)
    python scripts/db_manage.py export
    shift
    exec ansible-playbook playbook-users.yml "$@"
    ;;
  ping)
    exec ansible-playbook playbook-ping.yml "$@"
    ;;
  shell)
    exec sh
    ;;
  *)
    exec "$@"
    ;;
esac

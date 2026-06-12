"""导出配置并执行 Ansible 部署。"""

from __future__ import annotations

import os
import sqlite3
import subprocess
from pathlib import Path
from typing import Any

from db.database import (
    PROJECT_ROOT,
    export_inventory,
    export_users_yml,
    import_inventory,
    import_users_yml,
    list_servers,
)

INVENTORY_PATH = PROJECT_ROOT / "inventory.ini"
USERS_PATH = PROJECT_ROOT / "users.yml"


def ensure_servers_in_db(conn: sqlite3.Connection) -> None:
    """若 servers 表为空，从挂载的 inventory.ini 自动导入。"""
    if list_servers(conn):
        return
    if not INVENTORY_PATH.is_file():
        raise RuntimeError(
            "servers 表为空，且未找到 inventory.ini。"
            "请挂载 inventory.ini 或先调用 POST /api/import"
        )
    import_inventory(INVENTORY_PATH, conn)


def run_export(conn: sqlite3.Connection) -> dict[str, str]:
    ensure_servers_in_db(conn)
    export_inventory(INVENTORY_PATH, conn)
    export_users_yml(USERS_PATH, conn)
    return {
        "inventory": str(INVENTORY_PATH),
        "users": str(USERS_PATH),
    }


def run_deploy(
    conn: sqlite3.Connection,
    *,
    server_name: str | None = None,
    become_password: str | None = None,
) -> dict[str, Any]:
    files = run_export(conn)
    cmd = ["ansible-playbook", "playbook-users.yml"]
    if server_name:
        cmd.extend(["-l", server_name])

    env = os.environ.copy()
    if become_password:
        env["ANSIBLE_BECOME_PASSWORD"] = become_password

    # inventory 中已配置 ansible_password 时无需 -k
    proc = subprocess.run(
        cmd,
        cwd=PROJECT_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )
    return {
        "files": files,
        "server_name": server_name,
        "returncode": proc.returncode,
        "stdout": proc.stdout,
        "stderr": proc.stderr,
        "success": proc.returncode == 0,
    }


def run_import(conn: sqlite3.Connection) -> dict[str, int]:
    if not INVENTORY_PATH.is_file():
        raise RuntimeError(f"未找到 {INVENTORY_PATH}")
    if not USERS_PATH.is_file():
        raise RuntimeError(f"未找到 {USERS_PATH}")
    n_srv = import_inventory(INVENTORY_PATH, conn)
    n_usr = import_users_yml(USERS_PATH, conn)
    return {"servers": n_srv, "users": n_usr}

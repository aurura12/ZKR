"""SQLite 数据库访问与 YAML/INI 导入导出。"""

from __future__ import annotations

import os
import re
import sqlite3
from collections import defaultdict
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None  # type: ignore[assignment]

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB_PATH = Path(
    os.environ.get("SERVER_MGMT_DB_PATH", PROJECT_ROOT / "data" / "server_mgmt.db")
)
SCHEMA_PATH = Path(__file__).resolve().parent / "schema.sql"


def get_connection(db_path: Path | str | None = None) -> sqlite3.Connection:
    path = Path(db_path) if db_path else DEFAULT_DB_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(db_path: Path | str | None = None) -> Path:
    path = Path(db_path) if db_path else DEFAULT_DB_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    with get_connection(path) as conn:
        conn.executescript(schema)
        conn.commit()
    return path


def _now_clause() -> str:
    return "datetime('now', 'localtime')"


# ---------------------------------------------------------------------------
# servers
# ---------------------------------------------------------------------------


def list_servers(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return conn.execute("SELECT * FROM servers ORDER BY name").fetchall()


def upsert_server(
    conn: sqlite3.Connection,
    *,
    name: str,
    host: str,
    port: int = 22,
    ops_user: str,
    password: str | None = None,
    sudo_password: str | None = None,
) -> None:
    conn.execute(
        """
        INSERT INTO servers (name, host, port, ops_user, password, sudo_password, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, """ + _now_clause() + """)
        ON CONFLICT(name) DO UPDATE SET
            host = excluded.host,
            port = excluded.port,
            ops_user = excluded.ops_user,
            password = excluded.password,
            sudo_password = excluded.sudo_password,
            updated_at = """ + _now_clause() + """
        """,
        (name, host, port, ops_user, password, sudo_password),
    )


def add_server(conn: sqlite3.Connection, **kwargs: Any) -> None:
    upsert_server(conn, **kwargs)


def delete_server(conn: sqlite3.Connection, name: str) -> int:
    cur = conn.execute("DELETE FROM servers WHERE name = ?", (name,))
    return cur.rowcount


# ---------------------------------------------------------------------------
# users
# ---------------------------------------------------------------------------


def list_users(
    conn: sqlite3.Connection,
    *,
    name: str | None = None,
    server_name: str | None = None,
) -> list[sqlite3.Row]:
    sql = "SELECT * FROM users WHERE 1=1"
    params: list[Any] = []
    if name:
        sql += " AND name = ?"
        params.append(name)
    if server_name:
        sql += " AND server_name = ?"
        params.append(server_name)
    sql += " ORDER BY name, server_name"
    return conn.execute(sql, params).fetchall()


def upsert_user(
    conn: sqlite3.Connection,
    *,
    name: str,
    server_name: str,
    ssh_key: str,
    state: str = "present",
) -> None:
    if state not in ("present", "absent"):
        raise ValueError(f"state 必须是 present 或 absent，收到: {state!r}")
    conn.execute(
        """
        INSERT INTO users (name, server_name, ssh_key, state, updated_at)
        VALUES (?, ?, ?, ?, """ + _now_clause() + """)
        ON CONFLICT(name, server_name) DO UPDATE SET
            ssh_key = excluded.ssh_key,
            state = excluded.state,
            updated_at = """ + _now_clause() + """
        """,
        (name, server_name, ssh_key.strip(), state),
    )


def add_user(conn: sqlite3.Connection, **kwargs: Any) -> None:
    upsert_user(conn, **kwargs)


def delete_user(
    conn: sqlite3.Connection,
    *,
    name: str,
    server_name: str | None = None,
) -> int:
    if server_name:
        cur = conn.execute(
            "DELETE FROM users WHERE name = ? AND server_name = ?",
            (name, server_name),
        )
    else:
        cur = conn.execute("DELETE FROM users WHERE name = ?", (name,))
    return cur.rowcount


def get_user(
    conn: sqlite3.Connection,
    *,
    name: str,
    server_name: str,
) -> sqlite3.Row | None:
    return conn.execute(
        "SELECT * FROM users WHERE name = ? AND server_name = ?",
        (name, server_name),
    ).fetchone()


def server_exists(conn: sqlite3.Connection, name: str) -> bool:
    row = conn.execute("SELECT 1 FROM servers WHERE name = ?", (name,)).fetchone()
    return row is not None


def list_server_names(conn: sqlite3.Connection) -> list[str]:
    return [r["name"] for r in list_servers(conn)]


# ---------------------------------------------------------------------------
# groups
# ---------------------------------------------------------------------------


def list_groups(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return conn.execute("SELECT * FROM groups ORDER BY name").fetchall()


def create_group(conn: sqlite3.Connection, name: str) -> sqlite3.Row:
    conn.execute("INSERT INTO groups (name) VALUES (?)", (name.strip(),))
    return conn.execute("SELECT * FROM groups WHERE name = ?", (name.strip(),)).fetchone()


def get_group(conn: sqlite3.Connection, name: str) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM groups WHERE name = ?", (name,)).fetchone()


def get_user_group_names(conn: sqlite3.Connection, user_id: int) -> list[str]:
    rows = conn.execute(
        """
        SELECT g.name FROM groups g
        JOIN user_groups ug ON ug.group_id = g.id
        WHERE ug.user_id = ?
        ORDER BY g.name
        """,
        (user_id,),
    ).fetchall()
    return [r["name"] for r in rows]


def assign_user_group(
    conn: sqlite3.Connection,
    *,
    name: str,
    server_name: str,
    group_name: str,
) -> None:
    user = get_user(conn, name=name, server_name=server_name)
    if not user:
        raise LookupError(f"用户 {name!r} 在服务器 {server_name!r} 上不存在")
    group = get_group(conn, group_name)
    if not group:
        raise LookupError(f"组 {group_name!r} 不存在")
    conn.execute(
        "INSERT OR IGNORE INTO user_groups (user_id, group_id) VALUES (?, ?)",
        (user["id"], group["id"]),
    )


def remove_user_group(
    conn: sqlite3.Connection,
    *,
    name: str,
    server_name: str,
    group_name: str,
) -> int:
    user = get_user(conn, name=name, server_name=server_name)
    group = get_group(conn, group_name)
    if not user or not group:
        return 0
    cur = conn.execute(
        "DELETE FROM user_groups WHERE user_id = ? AND group_id = ?",
        (user["id"], group["id"]),
    )
    return cur.rowcount


def user_record_to_dict(conn: sqlite3.Connection, row: sqlite3.Row) -> dict[str, Any]:
    return {
        "id": row["id"],
        "name": row["name"],
        "server_name": row["server_name"],
        "ssh_key": row["ssh_key"],
        "state": row["state"],
        "groups": get_user_group_names(conn, row["id"]),
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }


def server_record_to_dict(row: sqlite3.Row, *, mask_secrets: bool = False) -> dict[str, Any]:
    def _mask(v: str | None) -> str | None:
        if not v:
            return v
        return "******" if mask_secrets else v

    return {
        "id": row["id"],
        "name": row["name"],
        "host": row["host"],
        "port": row["port"],
        "ops_user": row["ops_user"],
        "password": _mask(row["password"]),
        "sudo_password": _mask(row["sudo_password"]),
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }


# ---------------------------------------------------------------------------
# import / export
# ---------------------------------------------------------------------------

_HOST_LINE = re.compile(
    r"^(?P<name>\S+)\s+ansible_host=(?P<host>\S+)(?:\s+ansible_port=(?P<port>\d+))?"
)


def import_inventory(path: Path | str, conn: sqlite3.Connection) -> int:
    """从 inventory.ini 导入服务器；返回写入条数。"""
    text = Path(path).read_text(encoding="utf-8")
    vars_section = False
    group_vars: dict[str, str] = {}
    hosts: list[dict[str, Any]] = []

    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith(";"):
            continue
        if line.startswith("[") and line.endswith("]"):
            vars_section = line == "[servers:vars]"
            continue
        if vars_section:
            if "=" in line:
                k, _, v = line.partition("=")
                group_vars[k.strip()] = v.strip()
            continue
        m = _HOST_LINE.match(line)
        if m:
            hosts.append(
                {
                    "name": m.group("name"),
                    "host": m.group("host"),
                    "port": int(m.group("port") or 22),
                }
            )

    ops_user = group_vars.get("ansible_user", "")
    password = group_vars.get("ansible_password")
    sudo_password = group_vars.get("ansible_become_password")

    count = 0
    for h in hosts:
        upsert_server(
            conn,
            name=h["name"],
            host=h["host"],
            port=h["port"],
            ops_user=ops_user,
            password=password,
            sudo_password=sudo_password,
        )
        count += 1
    return count


def import_users_yml(path: Path | str, conn: sqlite3.Connection) -> int:
    """从 users.yml 导入；多 hosts 展开为多条记录。返回写入条数。"""
    if yaml is None:
        raise RuntimeError("需要 PyYAML：pip install pyyaml")

    data = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
    employees = data.get("employees") or []
    server_names = [r["name"] for r in list_servers(conn)]
    if not server_names:
        raise RuntimeError("请先导入 servers（inventory.ini），users 需要关联已有服务器名称")

    count = 0
    for emp in employees:
        name = emp["name"]
        state = emp.get("state", "present")
        keys = emp.get("ssh_keys") or []
        if not keys:
            raise ValueError(f"用户 {name!r} 缺少 ssh_keys")
        ssh_key = keys[0] if isinstance(keys[0], str) else str(keys[0])
        targets = emp.get("hosts") or server_names

        for srv in targets:
            if srv not in server_names:
                raise ValueError(f"用户 {name!r} 引用了未知服务器 {srv!r}")
            upsert_user(
                conn,
                name=name,
                server_name=srv,
                ssh_key=ssh_key,
                state=state,
            )
            count += 1
    return count


def export_inventory(path: Path | str, conn: sqlite3.Connection) -> None:
    """导出为 inventory.ini（组级 vars 取第一台服务器的运维字段）。"""
    servers = list_servers(conn)
    if not servers:
        raise RuntimeError("servers 表为空，无法导出 inventory.ini")

    first = servers[0]
    lines = [
        "# 由 SQLite 导出 — 编辑后建议 sync 回数据库",
        "# 注意：主机与端口必须分开写",
        "[servers]",
    ]
    for s in servers:
        lines.append(f"{s['name']} ansible_host={s['host']} ansible_port={s['port']}")
    lines.extend(
        [
            "",
            "[servers:vars]",
            f"ansible_user={first['ops_user']}",
        ]
    )
    if first["password"]:
        lines.append(f"ansible_password={first['password']}")
    if first["sudo_password"]:
        lines.append(f"ansible_become_password={first['sudo_password']}")

    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")


def export_users_yml(path: Path | str, conn: sqlite3.Connection) -> None:
    """导出 users.yml：同一用户多服务器合并为 hosts 列表。"""
    if yaml is None:
        raise RuntimeError("需要 PyYAML：pip install pyyaml")

    rows = list_users(conn)
    grouped: dict[tuple[str, str, str, tuple[str, ...]], list[str]] = defaultdict(list)
    for r in rows:
        groups = tuple(get_user_group_names(conn, r["id"]))
        key = (r["name"], r["ssh_key"], r["state"], groups)
        grouped[key].append(r["server_name"])

    employees = []
    for (name, ssh_key, state, groups), hosts in sorted(grouped.items(), key=lambda x: x[0][0]):
        entry: dict[str, Any] = {
            "name": name,
            "hosts": sorted(hosts),
            "ssh_keys": [ssh_key],
            "state": state,
        }
        if groups:
            entry["groups"] = list(groups)
        employees.append(entry)

    content = {
        "employees": employees,
    }
    header = (
        "# 员工账号配置 — 由 SQLite 导出\n"
        "# 编辑后执行 playbook-users.yml\n"
    )
    Path(path).write_text(
        header + yaml.dump(content, allow_unicode=True, sort_keys=False, default_flow_style=False),
        encoding="utf-8",
    )

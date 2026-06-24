#!/usr/bin/env python3
"""本地 SQLite 管理：初始化、导入/导出、查看。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from db.database import (  # noqa: E402
    DEFAULT_DB_PATH,
    delete_server,
    delete_user,
    export_inventory,
    export_users_yml,
    get_connection,
    import_inventory,
    import_users_yml,
    init_db,
    list_servers,
    list_users,
    upsert_server,
    upsert_user,
)


def cmd_init(args: argparse.Namespace) -> None:
    path = init_db(args.db)
    print(f"已初始化数据库: {path}")


def cmd_import(args: argparse.Namespace) -> None:
    init_db(args.db)
    with get_connection(args.db) as conn:
        n_srv = import_inventory(args.inventory, conn)
        n_usr = import_users_yml(args.users, conn)
        conn.commit()
    print(f"已导入 {n_srv} 台服务器、{n_usr} 条用户记录 → {args.db}")


def cmd_export(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        export_inventory(args.inventory, conn)
        export_users_yml(args.users, conn)
    print(f"已导出 → {args.inventory} , {args.users}")


def cmd_list(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        if args.table in ("servers", "all"):
            print("=== servers ===")
            for r in list_servers(conn):
                print(
                    f"  {r['name']:8}  {r['host']}:{r['port']}  "
                    f"ops={r['ops_user']}  pwd={'*' if r['password'] else '-'}"
                )
        if args.table in ("users", "all"):
            print("=== users ===")
            for r in list_users(conn):
                print(
                    f"  {r['name']:16}  {r['server_name']:6}  "
                    f"{r['state']:8}  {r['ssh_key'][:40]}..."
                )


def cmd_add_server(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        upsert_server(
            conn,
            name=args.name,
            host=args.host,
            port=args.port,
            ops_user=args.ops_user,
            password=args.password,
            sudo_password=args.sudo_password,
        )
        conn.commit()
    print(f"已保存服务器: {args.name}")


def cmd_add_user(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        upsert_user(
            conn,
            name=args.name,
            server_name=args.server,
            ssh_key=args.ssh_key,
            state=args.state,
        )
        conn.commit()
    print(f"已保存用户: {args.name} @ {args.server}")


def cmd_rm_server(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        n = delete_server(conn, args.name)
        conn.commit()
    print(f"已删除 {n} 条服务器记录")


def cmd_rm_user(args: argparse.Namespace) -> None:
    with get_connection(args.db) as conn:
        n = delete_user(conn, name=args.name, server_name=args.server)
        conn.commit()
    print(f"已删除 {n} 条用户记录")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="服务器管理 SQLite 工具")
    p.add_argument(
        "--db",
        type=Path,
        default=DEFAULT_DB_PATH,
        help=f"数据库路径（默认 {DEFAULT_DB_PATH}）",
    )

    sub = p.add_subparsers(dest="command", required=True)

    sub.add_parser("init", help="创建/升级表结构")

    imp = sub.add_parser("import", help="从 inventory.ini + users.yml 导入")
    imp.add_argument("--inventory", type=Path, default=PROJECT_ROOT / "inventory.ini")
    imp.add_argument("--users", type=Path, default=PROJECT_ROOT / "users.yml")

    exp = sub.add_parser("export", help="导出为 inventory.ini + users.yml")
    exp.add_argument("--inventory", type=Path, default=PROJECT_ROOT / "inventory.ini")
    exp.add_argument("--users", type=Path, default=PROJECT_ROOT / "users.yml")

    ls = sub.add_parser("list", help="列出数据")
    ls.add_argument("--table", choices=["servers", "users", "all"], default="all")

    srv = sub.add_parser("add-server", help="新增/更新一台服务器")
    srv.add_argument("name")
    srv.add_argument("host")
    srv.add_argument("--port", type=int, default=22)
    srv.add_argument("--ops-user", required=True)
    srv.add_argument("--password", default=None)
    srv.add_argument("--sudo-password", default=None)

    usr = sub.add_parser("add-user", help="新增/更新一条用户记录（单服务器）")
    usr.add_argument("name")
    usr.add_argument("server", help="服务器名称，如 srv1")
    usr.add_argument("ssh_key")
    usr.add_argument("--state", choices=["present", "absent"], default="present")

    rms = sub.add_parser("rm-server", help="删除服务器")
    rms.add_argument("name")

    rmu = sub.add_parser("rm-user", help="删除用户记录")
    rmu.add_argument("name")
    rmu.add_argument("--server", default=None, help="不指定则删除该用户全部记录")

    return p


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    handlers = {
        "init": cmd_init,
        "import": cmd_import,
        "export": cmd_export,
        "list": cmd_list,
        "add-server": cmd_add_server,
        "add-user": cmd_add_user,
        "rm-server": cmd_rm_server,
        "rm-user": cmd_rm_user,
    }
    handlers[args.command](args)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""服务器管理 REST API — 供前端调用。

启动:
    uvicorn api.main:app --reload --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from api.deps import get_db  # noqa: E402
from api.schemas import (  # noqa: E402
    ApiResponse,
    DeployRequest,
    GroupCreate,
    UserCreate,
    UserDelete,
    UserGroupAssign,
)
from api.deploy import run_deploy, run_export, run_import  # noqa: E402
from db.database import (  # noqa: E402
    assign_user_group,
    create_group,
    delete_user,
    get_group,
    get_user,
    list_groups,
    list_server_names,
    list_servers,
    list_users,
    server_exists,
    server_record_to_dict,
    upsert_user,
    user_record_to_dict,
)

app = FastAPI(
    title="服务器管理 API",
    description="用户、服务器、分组管理接口",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# 1. 新增用户
# ---------------------------------------------------------------------------


@app.post("/api/users", response_model=ApiResponse)
def create_user(body: UserCreate, conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    if not server_exists(conn, body.server_name):
        raise HTTPException(
            status_code=400,
            detail=f"服务器 {body.server_name!r} 不存在，请从已有服务器中选择",
        )
    try:
        upsert_user(
            conn,
            name=body.name,
            server_name=body.server_name,
            ssh_key=body.ssh_key,
            state=body.state,
        )
        row = get_user(conn, name=body.name, server_name=body.server_name)
        assert row is not None
        return ApiResponse(
            message="用户已创建/更新",
            data=user_record_to_dict(conn, row),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


# ---------------------------------------------------------------------------
# 2. 查询全部服务器
# ---------------------------------------------------------------------------


@app.get("/api/servers", response_model=ApiResponse)
def get_all_servers(
    mask_secrets: bool = Query(True, description="是否掩码 password / sudo_password"),
    conn: sqlite3.Connection = Depends(get_db),
) -> ApiResponse:
    rows = list_servers(conn)
    return ApiResponse(
        data=[server_record_to_dict(r, mask_secrets=mask_secrets) for r in rows],
    )


@app.get("/api/servers/options", response_model=ApiResponse)
def get_server_options(conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    """供前端下拉框：仅返回可选服务器名称列表。"""
    return ApiResponse(data=list_server_names(conn))


# ---------------------------------------------------------------------------
# 3. 查询全部用户
# ---------------------------------------------------------------------------


@app.get("/api/users", response_model=ApiResponse)
def get_all_users(
    name: str | None = Query(None, description="按用户名筛选"),
    server_name: str | None = Query(None, description="按服务器筛选"),
    conn: sqlite3.Connection = Depends(get_db),
) -> ApiResponse:
    rows = list_users(conn, name=name, server_name=server_name)
    return ApiResponse(
        data=[user_record_to_dict(conn, r) for r in rows],
    )


# ---------------------------------------------------------------------------
# 4. 删除用户
# ---------------------------------------------------------------------------


@app.delete("/api/users", response_model=ApiResponse)
def remove_user(body: UserDelete, conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    if not get_user(conn, name=body.name, server_name=body.server_name):
        raise HTTPException(
            status_code=404,
            detail=f"用户 {body.name!r} 在 {body.server_name!r} 上不存在",
        )
    delete_user(conn, name=body.name, server_name=body.server_name)
    return ApiResponse(message=f"已删除用户 {body.name} @ {body.server_name}")


# ---------------------------------------------------------------------------
# 5. 检查用户状态
# ---------------------------------------------------------------------------


@app.get("/api/users/status", response_model=ApiResponse)
def check_user_status(
    name: str = Query(..., description="用户名"),
    server_name: str = Query(..., description="服务器名称"),
    conn: sqlite3.Connection = Depends(get_db),
) -> ApiResponse:
    row = get_user(conn, name=name, server_name=server_name)
    if not row:
        return ApiResponse(
            ok=True,
            message="用户不存在",
            data={
                "name": name,
                "server_name": server_name,
                "exists": False,
                "state": None,
                "groups": [],
            },
        )
    data = user_record_to_dict(conn, row)
    data["exists"] = True
    data["status"] = row["state"]
    return ApiResponse(message="查询成功", data=data)


# ---------------------------------------------------------------------------
# 6. 创建 group
# ---------------------------------------------------------------------------


@app.post("/api/groups", response_model=ApiResponse)
def create_group_api(body: GroupCreate, conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    if get_group(conn, body.name):
        raise HTTPException(status_code=409, detail=f"组 {body.name!r} 已存在")
    try:
        row = create_group(conn, body.name)
        return ApiResponse(
            message="组已创建",
            data={"id": row["id"], "name": row["name"], "created_at": row["created_at"]},
        )
    except sqlite3.IntegrityError as e:
        raise HTTPException(status_code=409, detail=f"组 {body.name!r} 已存在") from e


@app.get("/api/groups", response_model=ApiResponse)
def get_all_groups(conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    rows = list_groups(conn)
    return ApiResponse(
        data=[{"id": r["id"], "name": r["name"], "created_at": r["created_at"]} for r in rows],
    )


# ---------------------------------------------------------------------------
# 7. 给用户分配 group
# ---------------------------------------------------------------------------


@app.post("/api/users/groups", response_model=ApiResponse)
def assign_group_to_user(
    body: UserGroupAssign,
    conn: sqlite3.Connection = Depends(get_db),
) -> ApiResponse:
    try:
        assign_user_group(
            conn,
            name=body.name,
            server_name=body.server_name,
            group_name=body.group_name,
        )
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e

    row = get_user(conn, name=body.name, server_name=body.server_name)
    assert row is not None
    return ApiResponse(
        message=f"已将用户 {body.name} 加入组 {body.group_name}",
        data=user_record_to_dict(conn, row),
    )


# ---------------------------------------------------------------------------
# 8. 导出 / 导入 / 部署（日常通过 API 完成，无需手动 docker compose run）
# ---------------------------------------------------------------------------


@app.post("/api/import", response_model=ApiResponse)
def import_config(conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    """从挂载的 inventory.ini + users.yml 导入数据库（首次初始化用）。"""
    try:
        result = run_import(conn)
    except RuntimeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return ApiResponse(message="导入完成", data=result)


@app.post("/api/export", response_model=ApiResponse)
def export_config(conn: sqlite3.Connection = Depends(get_db)) -> ApiResponse:
    """数据库 → inventory.ini + users.yml。"""
    try:
        files = run_export(conn)
    except RuntimeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return ApiResponse(message="导出完成", data=files)


@app.post("/api/deploy", response_model=ApiResponse)
def deploy_users(
    body: DeployRequest | None = None,
    conn: sqlite3.Connection = Depends(get_db),
) -> ApiResponse:
    """导出配置并在远程服务器上创建/更新用户（等价于 export + ansible-playbook）。"""
    body = body or DeployRequest()
    try:
        result = run_deploy(
            conn,
            server_name=body.server_name,
            become_password=body.become_password,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    if not result["success"]:
        raise HTTPException(
            status_code=500,
            detail={
                "message": "Ansible 执行失败",
                "returncode": result["returncode"],
                "stderr": result["stderr"][-2000:] if result["stderr"] else "",
                "stdout": result["stdout"][-2000:] if result["stdout"] else "",
            },
        )
    return ApiResponse(
        message=f"部署完成{(' → ' + body.server_name) if body.server_name else ''}",
        data={
            "server_name": body.server_name,
            "files": result["files"],
            "stdout_tail": result["stdout"][-1500:] if result["stdout"] else "",
        },
    )

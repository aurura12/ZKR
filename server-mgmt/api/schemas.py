"""API 请求/响应模型。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator


class UserCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=32, description="Linux 用户名")
    server_name: str = Field(..., description="服务器名称，必须为已存在的服务器")
    ssh_key: str = Field(..., min_length=20, description="SSH 公钥")
    state: Literal["present", "absent"] = "present"

    @field_validator("name")
    @classmethod
    def validate_linux_username(cls, v: str) -> str:
        import re

        if not re.match(r"^[a-z_][a-z0-9_-]*$", v):
            raise ValueError("用户名须以小写字母或下划线开头，仅含小写字母、数字、_-")
        return v

    @field_validator("ssh_key")
    @classmethod
    def strip_ssh_key(cls, v: str) -> str:
        return v.strip()


class UserDelete(BaseModel):
    name: str
    server_name: str


class GroupCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=64)

    @field_validator("name")
    @classmethod
    def validate_group_name(cls, v: str) -> str:
        import re

        v = v.strip()
        if not re.match(r"^[a-zA-Z][a-zA-Z0-9_-]*$", v):
            raise ValueError("组名须以字母开头，仅含字母、数字、_-")
        return v


class UserGroupAssign(BaseModel):
    name: str
    server_name: str
    group_name: str


class DeployRequest(BaseModel):
    server_name: str | None = Field(None, description="仅部署到该服务器，如 srv2；留空则全部")
    become_password: str | None = Field(None, description="远程 sudo 密码（若未配置免密 sudo）")


class ApiResponse(BaseModel):
    ok: bool = True
    message: str = ""
    data: dict | list | None = None

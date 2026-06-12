# 服务器管理 API 文档

## 1. 快速开始

### 服务地址

| 项 | 说明 |
|----|------|
| Base URL | `http://127.0.0.1:<端口>`（默认 **17000**，可用 `PORT=17000 sudo docker compose up -d` 换端口） |
| 交互文档 | `http://127.0.0.1:<端口>/docs` |
| 健康检查 | `GET /api/health` |

### 启动服务

```bash
sudo docker compose up -d --build
curl http://127.0.0.1:17000/api/health
# → {"status":"ok"}
```

首次启动时，若数据库尚无服务器记录，会自动从挂载的 `inventory.ini` + `users.yml` 导入。

### 最常用：新员工开户（两步）

```bash
BASE=http://127.0.0.1:17000 

# ① 写入数据库
curl -X POST $BASE/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "zhonghaoyang",
    "server_name": "srv2",
    "ssh_key": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINJzjar3BO5Np5dFlbZ4lF6S4Ah/eljmUw92aPFGoycU 2624384558@qq.com",
    "state": "present"
  }'

# ② 部署到远程（真正在 Linux 上开户）
curl -X POST $BASE/api/deploy \
  -H "Content-Type: application/json" \
  -d '{"server_name": "srv2"}'
```

也可用脚本一步完成：

```bash
./scripts/add_user.sh zhonghaoyang srv2 "ssh-ed25519 AAAA... 2624384558@qq.com" --deploy
```

---

## 2. 核心概念（必读）

### 数据流

```text
API 修改数据库  →  deploy（自动 export）→  Ansible  →  远程服务器生效
```

| 操作 | 作用 | 是否已在服务器生效 |
|------|------|-------------------|
| `POST /api/users` | 在数据库登记用户 | ❌ 仅数据库 |
| `POST /api/deploy` | 导出配置 + 跑 Ansible | ✅ 远程 Linux 开户/更新 |

### 两个密码（不要混）

| 配置项 | 用途 | 写在哪儿 |
|--------|------|----------|
| `ansible_password` | SSH 登录运维账号 `a` | `inventory.ini` |
| `ansible_become_password` | 登录后 `sudo` 提权 | `inventory.ini` |
| `become_password`（API 参数） | 临时覆盖 sudo 密码 | deploy 请求体（可选） |

示例 `inventory.ini`：

```ini
[servers:vars]
ansible_user=a
ansible_password=rjs12138
ansible_become_password=rjs12138
```

**已在 inventory 写好并 import 后，deploy 一般不必再传 `become_password`。**

### 改 inventory.ini 后要先 import

`deploy` 会先执行 **export（数据库 → 文件）**，直接改文件而不 import 会被数据库里的旧数据覆盖。

```bash
# 1. 编辑 inventory.ini
# 2. 同步进数据库
curl -X POST http://127.0.0.1:17000/api/import
# 3. 再 deploy
curl -X POST http://127.0.0.1:17000/api/deploy -H "Content-Type: application/json" -d '{"server_name": "srv2"}'
```

### 用户记录模型

- 每条记录 = **一个用户 + 一台服务器**
- 同一用户上多台机器 → 多条记录（或多次调用 `POST /api/users`）
- 新增用户前，`server_name` 必须来自 `GET /api/servers/options`

---

## 3. 接口总览

| 分类 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 系统 | GET | `/api/health` | 健康检查 |
| 服务器 | GET | `/api/servers` | 查询服务器列表 |
| 服务器 | GET | `/api/servers/options` | 服务器名称下拉选项 |
| 用户 | POST | `/api/users` | 新增/更新用户 |
| 用户 | GET | `/api/users` | 查询用户（可筛选） |
| 用户 | DELETE | `/api/users` | 删除用户记录 |
| 用户 | GET | `/api/users/status` | 检查用户状态 |
| 分组 | POST | `/api/groups` | 创建 group |
| 分组 | GET | `/api/groups` | 查询 group |
| 分组 | POST | `/api/users/groups` | 给用户分配 group |
| 同步 | POST | `/api/import` | `inventory.ini` + `users.yml` → 数据库 |
| 同步 | POST | `/api/export` | 数据库 → `inventory.ini` + `users.yml` |
| 部署 | POST | `/api/deploy` | 导出 + Ansible 远程开户 |

---

## 4. 通用约定

### 响应格式

业务接口统一结构：

```json
{
  "ok": true,
  "message": "说明文字",
  "data": {}
}
```

- HTTP 4xx/5xx 时，FastAPI 返回 `{"detail": "..."}` 或 `{"detail": {...}}`
- `GET /api/health` 例外，返回 `{"status": "ok"}`

### 命名规则

| 字段 | 规则 |
|------|------|
| `name`（用户名） | 小写字母或下划线开头，仅含 `a-z0-9_-` |
| `group_name` | 字母开头，仅含 `a-zA-Z0-9_-` |
| `server_name` | 与 `servers` 表一致，如 `srv1`、`srv2`、`srv3` |

---

## 5. 接口详情

### 5.1 健康检查

```
GET /api/health
```

```bash
curl http://127.0.0.1:17000/api/health
```

响应：

```json
{"status": "ok"}
```

---

### 5.2 查询服务器

```
GET /api/servers
GET /api/servers?mask_secrets=false
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `mask_secrets` | bool | `true` | 为 `true` 时密码显示为 `******` |

```bash
curl http://127.0.0.1:17000/api/servers
curl "http://127.0.0.1:17000/api/servers?mask_secrets=false"
```

`data` 单项字段：

| 字段 | 说明 |
|------|------|
| `name` | 服务器名称 |
| `host` | IP 或域名 |
| `port` | SSH 端口 |
| `ops_user` | 运维账号 |
| `password` | SSH 登录密码 |
| `sudo_password` | sudo 密码 |

---

### 5.3 服务器下拉选项

前端新增用户前调用，获取可选 `server_name`。

```
GET /api/servers/options
```

```bash
curl http://127.0.0.1:17000/api/servers/options
```

响应：

```json
{
  "ok": true,
  "message": "",
  "data": ["srv1", "srv2", "srv3"]
}
```

---

### 5.4 新增/更新用户

```
POST /api/users
Content-Type: application/json
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | Linux 用户名 |
| `server_name` | 是 | 必须是已有服务器 |
| `ssh_key` | 是 | SSH 公钥整行 |
| `state` | 否 | `present`（默认）或 `absent`（离职标记） |

```bash
curl -X POST http://127.0.0.1:17000/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "zhangsan",
    "server_name": "srv3",
    "ssh_key": "ssh-ed25519 AAAA... zhangsan@pc",
    "state": "present"
  }'
```

成功时 `data` 示例：

```json
{
  "id": 10,
  "name": "zhangsan",
  "server_name": "srv3",
  "ssh_key": "ssh-ed25519 AAAA...",
  "state": "present",
  "groups": [],
  "created_at": "2026-06-10 15:00:00",
  "updated_at": "2026-06-10 15:00:00"
}
```

| HTTP | 原因 |
|------|------|
| 400 | `server_name` 不存在，或用户名格式非法 |
| 422 | JSON 字段缺失或类型错误 |

---

### 5.5 查询用户

```
GET /api/users
GET /api/users?name=zhangsan
GET /api/users?server_name=srv3
GET /api/users?name=zhangsan&server_name=srv3
```

```bash
curl http://127.0.0.1:17000/api/users
curl "http://127.0.0.1:17000/api/users?name=zhangsan&server_name=srv3"
```

`data` 数组单项字段：`id`、`name`、`server_name`、`ssh_key`、`state`、`groups`

---

### 5.6 删除用户

删除数据库记录；远程 Linux 账号需再 `deploy` 才会移除。

```
DELETE /api/users
Content-Type: application/json
```

```bash
curl -X DELETE http://127.0.0.1:17000/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "zhangsan", "server_name": "srv3"}'
```

| HTTP | 原因 |
|------|------|
| 404 | 该用户在此服务器上不存在 |

---

### 5.7 检查用户状态

```
GET /api/users/status?name=zhangsan&server_name=srv3
```

```bash
curl "http://127.0.0.1:17000/api/users/status?name=zhangsan&server_name=srv3"
```

用户存在时 `data` 含 `exists: true`、`status`（同 `state`）、`groups` 等。

用户不存在时：

```json
{
  "name": "zhangsan",
  "server_name": "srv3",
  "exists": false,
  "state": null,
  "groups": []
}
```

---

### 5.8 创建 Group

```
POST /api/groups
```

```bash
curl -X POST http://127.0.0.1:17000/api/groups \
  -H "Content-Type: application/json" \
  -d '{"name": "developers"}'
```

| HTTP | 原因 |
|------|------|
| 409 | 组名已存在 |

---

### 5.9 查询 Group

```
GET /api/groups
```

```bash
curl http://127.0.0.1:17000/api/groups
```

---

### 5.10 给用户分配 Group

按「用户 + 服务器」维度分配。

```
POST /api/users/groups
```

| 字段 | 说明 |
|------|------|
| `name` | 用户名 |
| `server_name` | 服务器名称 |
| `group_name` | 已存在的组名 |

```bash
curl -X POST http://127.0.0.1:17000/api/users/groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "zhangsan",
    "server_name": "srv3",
    "group_name": "developers"
  }'
```

| HTTP | 原因 |
|------|------|
| 404 | 用户或组不存在 |

---

### 5.11 导入配置

从挂载的 `inventory.ini` + `users.yml` 导入数据库。

```
POST /api/import
```

```bash
curl -X POST http://127.0.0.1:17000/api/import
```

响应示例：

```json
{"ok": true, "message": "导入完成", "data": {"servers": 3, "users": 9}}
```

---

### 5.12 导出配置

将数据库写入 `inventory.ini` 和 `users.yml`。

```
POST /api/export
```

```bash
curl -X POST http://127.0.0.1:17000/api/export
```

---

### 5.13 部署到远程（开户）

自动执行：**export → ansible-playbook playbook-users.yml**

```
POST /api/deploy
Content-Type: application/json
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `server_name` | 否 | 仅部署该服务器；留空则全部 |
| `become_password` | 否 | sudo 密码；inventory 已配 `ansible_become_password` 时可省略 |

```bash
# 仅部署 srv2（推荐）
curl -X POST http://127.0.0.1:17000/api/deploy \
  -H "Content-Type: application/json" \
  -d '{"server_name": "srv2"}'

# 部署全部
curl -X POST http://127.0.0.1:17000/api/deploy \
  -H "Content-Type: application/json" \
  -d '{}'

# 临时指定 sudo 密码（inventory 未配置时）
curl -X POST http://127.0.0.1:17000/api/deploy \
  -H "Content-Type: application/json" \
  -d '{"server_name": "srv2", "become_password": "你的sudo密码"}'
```

成功响应示例：

```json
{
  "ok": true,
  "message": "部署完成 → srv2",
  "data": {
    "server_name": "srv2",
    "files": {"inventory": "/app/inventory.ini", "users": "/app/users.yml"},
    "stdout_tail": "PLAY RECAP ... failed=0 ..."
  }
}
```

失败时 HTTP 500，`detail` 中含 Ansible 输出摘要。

---

## 6. 典型场景

### 6.1 新员工入职

```bash
BASE=http://127.0.0.1:17000

curl -X POST $BASE/api/users -H "Content-Type: application/json" -d '{
  "name": "newuser",
  "server_name": "srv2",
  "ssh_key": "ssh-ed25519 AAAA... newuser@pc"
}'

curl -X POST $BASE/api/deploy -H "Content-Type: application/json" \
  -d '{"server_name": "srv2"}'
```

### 6.2 员工离职

```bash
# 方式 A：标记 absent（推荐，保留记录）
curl -X POST $BASE/api/users -H "Content-Type: application/json" -d '{
  "name": "olduser",
  "server_name": "srv2",
  "ssh_key": "ssh-ed25519 AAAA...",
  "state": "absent"
}'

# 方式 B：删除数据库记录
curl -X DELETE $BASE/api/users -H "Content-Type: application/json" \
  -d '{"name": "olduser", "server_name": "srv2"}'

# 使远程生效
curl -X POST $BASE/api/deploy -H "Content-Type: application/json" \
  -d '{"server_name": "srv2"}'
```

### 6.3 修改运维 / sudo 密码

```bash
# 1. 编辑 inventory.ini（ansible_password、ansible_become_password）
# 2. 导入
curl -X POST $BASE/api/import
# 3. 验证
curl "$BASE/api/servers?mask_secrets=false"
# 4. 部署
curl -X POST $BASE/api/deploy -H "Content-Type: application/json" -d '{}'
```

### 6.4 用户上多台服务器

```bash
# srv1
curl -X POST $BASE/api/users -H "Content-Type: application/json" \
  -d '{"name":"lisi","server_name":"srv1","ssh_key":"ssh-ed25519 AAAA..."}'
# srv3
curl -X POST $BASE/api/users -H "Content-Type: application/json" \
  -d '{"name":"lisi","server_name":"srv3","ssh_key":"ssh-ed25519 AAAA..."}'
# 分别或一次性 deploy
curl -X POST $BASE/api/deploy -H "Content-Type: application/json" -d '{}'
```

---

## 7. 部署结果解读

Ansible `PLAY RECAP` 关键字段：

| 字段 | 含义 |
|------|------|
| `failed=0` | 无任务失败 |
| `unreachable=0` | SSH / 网络正常 |
| `changed=0` | 远程已与配置一致，无新改动 |
| `changed>0` | 新建或更新了用户/密钥等 |

示例（全部成功、无变更）：

```text
srv1  ok=6  changed=0  failed=0  unreachable=0
srv2  ok=6  changed=0  failed=0  unreachable=0
srv3  ok=6  changed=0  failed=0  unreachable=0
```

新用户首次部署时，常见 `changed=1`（创建用户目录、写入公钥）。

---

## 8. 常见问题

| 现象 | 原因 | 处理 |
|------|------|------|
| `Not Found` 调 deploy | 旧容器无新接口 | `sudo docker compose down && sudo docker compose up -d --build`，确认 `/docs` 有 deploy |
| `address already in use` | 17000 被占用 | `docker compose down` 或 `PORT=17000` 启动 |
| `UNREACHABLE` + `Invalid/incorrect password` | SSH 密码错 | 改 `ansible_password`，`import` 后再 deploy |
| `Missing sudo password` / `sudo: 需要密码` | sudo 密码未配 | 加 `ansible_become_password`，`import` 后再 deploy |
| 改了 inventory 仍用旧密码 | 未 import | deploy 前执行 `POST /api/import` |
| deploy 成功但 `changed=0` | 用户已存在 | 正常，非失败 |
| `permission denied` docker | 未加 sudo | 使用 `sudo docker compose ...` |

---

## 9. 测试与脚本

### 接口演示（不保留测试用户）

```bash
chmod +x scripts/api_demo.sh
./scripts/api_demo.sh
# 自定义端口
BASE_URL=http://127.0.0.1:17000 ./scripts/api_demo.sh
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `BASE_URL` | `http://127.0.0.1:17000` | API 地址 |
| `SERVER_NAME` | 自动取第一台 | 测试用服务器 |
| `SKIP_CLEANUP` | `0` | 设为 `1` 保留演示用户 |

### 创建用户脚本

```bash
chmod +x scripts/add_user.sh

# 只写数据库
./scripts/add_user.sh zhangsan srv2 "ssh-ed25519 AAAA... comment"

# 写数据库 + 部署
./scripts/add_user.sh zhangsan srv2 "ssh-ed25519 AAAA... comment" --deploy

# 交互模式
./scripts/add_user.sh
```

### 单元测试

```bash
pytest tests/ -v
```

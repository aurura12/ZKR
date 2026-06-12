# 服务器账号批量管理

## 推荐用法（Docker 一键起服务）

日常只需启动容器，其余通过 **API / 网站** 完成：

curl -X POST http://127.0.0.1:17000/api/deploy -H "Content-Type: application/json" -d '{"become_password": "rjs12138"}'


```bash
# 1. 启动（仅此一条，服务常驻 http://localhost:8765）
sudo docker compose up -d --build

# 2. 打开 API 文档
# http://127.0.0.1:8765/docs

# 3. 创建用户并部署（脚本示例）
./scripts/add_user.sh zhonghaoyang srv2 "ssh-ed25519 AAAA... email" --deploy
```

**流程说明：**

| 操作 | 方式 |
|------|------|
| 查服务器 / 用户 | `GET /api/servers`、`GET /api/users` |
| 新增用户 | `POST /api/users` 或 `./scripts/add_user.sh` |
| 部署到远程（开户） | `POST /api/deploy` 或 `add_user.sh --deploy` |

无需再手动 `docker compose run export/ansible-users`。

---

## 本地开发（不用 Docker 时）

```bash
source .venv/bin/activate
uvicorn api.main:app --reload --host 0.0.0.0 --port 8765
```

运行测试：

```bash
pytest tests/ -v
```

### Docker

**国内加速（建议先做）：**

```bash
# 1. 本机 Docker 拉镜像加速（写入 /etc/docker/daemon.json 并重启 docker）
chmod +x docker/setup-docker-mirror.sh
sudo ./docker/setup-docker-mirror.sh

# 若有阿里云专属加速地址，可编辑 docker/daemon.json.example 后再执行上述脚本
```

镜像构建内已配置：
- 基础镜像 `python:3.11-slim`（由 daemon 镜像加速拉取）
- apt / pip / Ansible Galaxy 使用清华 / 阿里云源

**启动：**

```bash
# 构建并启动 API（默认 http://localhost:8765）
sudo docker compose up -d --build

# 查看日志
docker compose logs -f api

# 健康检查
curl http://127.0.0.1:8765/api/health

# 停止
sudo docker compose down
```

部署、导出、导入均已集成到 API，见 [docs/API.md](docs/API.md)。

数据持久化在 Docker volume `server-mgmt-data`。

### HTTP API 速查

交互文档：http://127.0.0.1:8765/docs

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 新增用户 |
| GET | `/api/servers` | 查询服务器 |
| GET | `/api/users` | 查询用户 |
| DELETE | `/api/users` | 删除用户 |
| GET | `/api/users/status` | 检查用户状态 |
| POST | `/api/groups` | 创建 group |
| POST | `/api/users/groups` | 分配 group |
| POST | `/api/deploy` | **导出并部署到远程（开户）** |
| POST | `/api/export` | 仅导出 YAML |
| POST | `/api/import` | 从 YAML 导入数据库 |

```bash
# 创建用户 + 部署到 srv2（一条命令）
./scripts/add_user.sh 用户名 srv2 "ssh-ed25519 AAAA... comment" --deploy

# 或单独部署
curl -X POST http://127.0.0.1:8765/api/deploy \
  -H "Content-Type: application/json" \
  -d '{"server_name": "srv2"}'
```

---

## 需要配置的文件

| 文件 | 是否必改 | 说明 |
|------|----------|------|
| `data/server_mgmt.db` | **推荐** | SQLite 数据源（用户名、服务器、公钥、密码等；已 gitignore） |
| `inventory.ini` | 导出产物 | 由 `db_manage.py export` 生成，供 Ansible 读取 |
| `users.yml` | 导出产物 | 由 `db_manage.py export` 生成，供 Ansible 读取 |
| 运维私钥 | 按需 | 密钥登录时在 `inventory.ini` 配置 `ansible_ssh_private_key_file` |
| `group_vars/all.yml` | 可选 | 默认 shell、是否锁定密码 `lock_user_password`、默认附加组等 |
| `ansible.cfg` | 一般不用改 | 已全局开启 `become`（sudo）；运维账号需有 sudo 权限 |

**`inventory.ini` 注意：** 端口必须单独写 `ansible_port`，不能写成 `ansible_host=ip:端口`。

```ini
# 正确
srv3 ansible_host=47.93.215.230 ansible_port=6000

# 错误（Ansible 会把整串当成主机名）
srv3 ansible_host=47.93.215.230:6000
```

---

## 环境准备（首次）

```bash
cd "/path/to/服务器管理"
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
ansible-galaxy collection install -r requirements.yml
chmod 600 ./id_ed25519   # 若私钥放在项目目录
```

之后每次使用前：`source .venv/bin/activate`（或直接用 `.venv/bin/ansible-playbook`）。

---

## 测试指令

### 1. 测试 SSH / Ansible 连通（不创建用户）

```bash
ansible-playbook playbook-ping.yml --become=false
```

若未配置免密 sudo，需要 sudo 时：

```bash
ansible-playbook playbook-ping.yml -K
```

成功时 `PLAY RECAP` 中对应主机为 `ok=1`，且无 `UNREACHABLE`。

### 2. 预演创建用户（不真正改系统）

```bash
ansible-playbook playbook-users.yml --check --diff -K
```

会显示将要创建的用户、`.ssh` 目录等。**预演时「安装 SSH 公钥」可能报错**（用户尚未真实创建），属 `--check` 限制；以正式执行为准。

### 3. 在远程机上确认（可选）

```bash
ssh -i ./id_ed25519 -p <端口> <运维用户>@<服务器IP> "ls -la /home"
```

---

## 正式执行（创建/更新员工账号）

```bash
ansible-playbook playbook-users.yml -K
```

仅对一台服务器（与 `inventory.ini` 中主机名一致，如 `srv3`）：

```bash
ansible-playbook playbook-users.yml -l srv3 -K
```

执行成功后，在**目标服务器**的 `/home/<用户名>` 下应能看到新员工目录；本地电脑的 `/home` 不会变化。

---

## 日常维护速查

| 场景 | 操作 |
|------|------|
| 新员工入职 | `db_manage.py add-user` → `export` → `playbook-users.yml -K` |
| 员工离职 | `add-user` 设 `--state absent`（或改 DB）→ `export` → 再执行 playbook |
| 更换密钥 | 更新 DB 中 `ssh_key` → `export` → 再执行 playbook |
| 某人只上部分机器 | 同一用户按服务器各写一条 DB 记录（如 srv1、srv3 两条） |
| 授予 sudo | 导出后的 `users.yml` 中设 `sudo: true`（慎用；或后续扩展 DB 字段） |

离职示例（数据库中同一用户在某服务器上设 `state=absent`）：

```bash
python scripts/db_manage.py add-user former_user srv3 "ssh-ed25519 AAAA..." --state absent
python scripts/db_manage.py export
ansible-playbook playbook-users.yml -l srv3 -K
```

---

## 目录结构

```text
服务器管理/
├── api/                  # FastAPI 接口（供前端）
│   ├── main.py
│   ├── schemas.py
│   └── deps.py
├── docker/
│   ├── daemon.json.example   # Docker 镜像加速器模板
│   ├── pip.conf              # 构建时 pip 清华源
│   ├── setup-docker-mirror.sh
│   └── entrypoint.sh
├── docker-compose.yml
├── Dockerfile
├── data/server_mgmt.db   # SQLite（gitignore）
├── db/                   # schema + 数据库模块
├── scripts/db_manage.py  # 初始化 / 导入 / 导出 / CRUD
├── inventory.ini         # Ansible 清单（export 生成）
├── users.yml             # 员工账号（export 生成）
├── group_vars/all.yml    # 全局默认（可选）
├── playbook-ping.yml     # 连通测试
├── playbook-users.yml    # 创建用户 + 公钥
├── ansible.cfg
├── requirements.txt
├── requirements.yml
└── .venv/                # 本地虚拟环境（gitignore）
```

---

## 安全建议

- `group_vars/all.yml` 中 `lock_user_password: true` 会锁定密码，仅允许公钥登录。
- 生产环境建议在服务器 `/etc/ssh/sshd_config` 关闭密码登录：`PasswordAuthentication no`。
- 勿将私钥、密码提交到仓库；`inventory.ini` 含敏感信息时可使用 `inventory.local.ini` 并加入 `.gitignore`。

---

## 常见问题

| 现象 | 处理 |
|------|------|
| `UNPROTECTED PRIVATE KEY` / `bad permissions` | `chmod 600` 私钥 |
| `Missing sudo password` | 加 `-K`，或 ping 时用 `--become=false`，或配置免密 sudo |
| `UNREACHABLE` | 检查 IP、端口、`ansible_user`、防火墙、私钥路径 |
| ping 成功但没有新用户 | ping 只测连通；须跑 `playbook-users.yml` |
| `--check` 时装公钥失败 | 预演限制；正式执行 `playbook-users.yml -K` |

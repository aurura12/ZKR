# 服务器管理集成到 ERP 设计方案

## 背景

`outside/服务器管理/` 是一个基于 FastAPI + Ansible 的 Linux 服务器用户账号批量管理系统，管理三台本地 GPU 服务器（代称 3090/4090/5090）上的用户账号。现需将其集成到 ERP 系统中，作为类似「工资管理」的独立功能模块。

## 目标

1. ERP 用户下拉菜单中新增「服务器管理」入口按钮
2. 通过数据库字段 `is_server_ops_admin` 灵活控制谁能看到并使用此功能
3. 初始开放给李敬儒、傅钟宇
4. 复用 ERP 现有 JWT 鉴权体系
5. 完整功能：服务器列表、用户增删查、一键部署 + 服务器监控（二期）

## 架构

```
浏览器 → nginx(lab-erp-demo:80) → erp-backend(8101) → server-mgmt-api(17000) → SSH → 3090/4090/5090
                                         ↑                        ↑
                                    JWT 鉴权                  纯内部通信
                                   is_server_ops_admin        无端口暴露
```

### 各层职责

| 层 | 组件 | 变更类型 |
|----|------|----------|
| 前端 | `App.vue` + 新页面 `ServerManagementView.vue` + `router/index.js` | 新增 |
| 后端 | `User.java`(加字段) + `ServerMgmtProxyController.java`(新增) | 修改+新增 |
| 数据库 | `sys_user` 表加 `is_server_ops_admin` 字段 | 新增字段 |
| 服务 | `server-mgmt/` Python FastAPI 服务 | 从 outside 迁入 |
| 编排 | `docker-compose.yml` 新增 `server-mgmt-api` service | 新增 |

## 详细设计

### 1. 数据库变更

`sys_user` 表新增字段：

```sql
ALTER TABLE sys_user ADD COLUMN is_server_ops_admin BOOLEAN NOT NULL DEFAULT false;
```

默认 `false`，仅管理员（Zhangqi）通过数据库直接修改或后续通过 UI 管理。

### 2. 后端变更

#### 2.1 User 实体 (`User.java`)

新增字段：

```java
@Column(name = "is_server_ops_admin")
@Builder.Default
private Boolean serverOpsAdmin = false;
```

该字段自动包含在 `/api/auth/me` 响应中，前端无需额外请求即可读取。

#### 2.2 代理 Controller (`ServerMgmtProxyController.java`)

新增 Spring MVC Controller，映射 `/api/server-mgmt/**`：

职责：
1. 从 SecurityContext 获取当前用户
2. 检查 `isServerOpsAdmin == true`，否则返回 403
3. 使用 RestTemplate 将请求转发到 `http://server-mgmt-api:17000`
4. 透传响应体和状态码

关键设计决策：
- 不做接口语义转换，纯 HTTP 代理转发（和 `finance-rag-api` 调用模式一致）
- 鉴权在代理层完成，`server-mgmt-api` 不感知 JWT
- 仅在 `erp-internal` Docker 网络内部通信，不暴露端口

### 3. 前端变更

#### 3.1 路由 (`router/index.js`)

新增路由：

```javascript
{
  path: '/admin/server-management',
  name: 'server-management',
  component: () => import('@/views/ServerManagementView.vue'),
  meta: {
    requiresAuth: true,
    routeDomain: DOMAIN_ERP
  }
}
```

不在路由 meta 层做角色检查（`is_server_ops_admin` 字段式鉴权），统一由后端代理层控制。

#### 3.2 用户下拉菜单 (`App.vue`)

在用户下拉菜单中新增入口（位于 工资管理 下方）：

```html
<el-dropdown-item
  v-if="userStore.activeUserInfo?.serverOpsAdmin"
  command="server-management"
>
  🖥 服务器管理
</el-dropdown-item>
```

命令处理：

```javascript
else if (cmd === 'server-management') router.push('/admin/server-management')
```

#### 3.3 服务器管理页面 (`ServerManagementView.vue`)

三 Tab 布局：

| Tab | 内容 | 数据来源 |
|-----|------|----------|
| **服务器** | 三台服务器卡片：名称、IP、端口、状态 | `GET /api/server-mgmt/servers` |
| **用户管理** | 表格：用户名、所属服务器、SSH Key 掩码、状态；支持增删操作 | `GET/POST/DELETE /api/server-mgmt/users` |
| **部署** | 目标服务器下拉选择 + 一键部署按钮 + 部署日志输出 | `POST /api/server-mgmt/deploy` |

API 调用方式：前端统一通过 `request.js` 的 axios 实例调用 `/api/server-mgmt/...`，由 ERP 后端代理转发。

### 4. server-mgmt 服务迁移

#### 4.1 代码迁移

```bash
cp -r outside/服务器管理/服务器管理/ server-mgmt/
```

`outside/` 目录保留不做删除。

#### 4.2 配置修改

修改 `server-mgmt/inventory.ini`，将 natapp 隧道地址替换为三台服务器的局域网 IP：

```ini
[servers]
3090 ansible_host=<LAN_IP_3090> ansible_port=22
4090 ansible_host=<LAN_IP_4090> ansible_port=22
5090 ansible_host=<LAN_IP_5090> ansible_port=22

[servers:vars]
ansible_user=a
ansible_password=rjs12138
ansible_become_password=rjs12138
```

#### 4.3 Docker Compose 集成

主 `docker-compose.yml` 新增 service：

```yaml
server-mgmt-api:
  build: ./server-mgmt
  image: server-mgmt:latest
  container_name: server-mgmt-api
  environment:
    SERVER_MGMT_DB_PATH: /app/data/server_mgmt.db
    PORT: "17000"
    IMPORT_ON_START: "1"
  volumes:
    - server-mgmt-data:/app/data
    - ./server-mgmt/inventory.ini:/app/inventory.ini
    - ./server-mgmt/users.yml:/app/users.yml
    - ./server-mgmt/id_ed25519:/app/id_ed25519:ro
  networks:
    - erp-internal
  restart: unless-stopped

volumes:
  server-mgmt-data:  # 追加到现有 volumes 列表
```

关键点：
- 仅在 `erp-internal` 网络，不暴露宿主机端口
- SSH 私钥以只读方式挂载
- 数据库持久化到 Docker volume

### 5. 初始管理员设置

上线后，管理员（Zhangqi）通过以下 SQL 设置初始运维管理员：

```sql
UPDATE sys_user SET is_server_ops_admin = true WHERE username = 'lijingru';
UPDATE sys_user SET is_server_ops_admin = true WHERE username = 'fuzhongyu';
```

### 6. 服务器监控（二期）

当前一期不包含服务器监控。二期扩展方向：
- `server-mgmt/api/` 新增 `/api/server-mgmt/metrics` 端点
- 通过 Ansible ad-hoc 或 SSH 命令采集磁盘使用率、CPU、GPU 状态（nvidia-smi）
- 前端服务器卡片展示实时指标

## 涉及文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `erp-backend/.../entity/User.java` | 修改 | 加 `serverOpsAdmin` 字段 |
| `erp-backend/.../controller/ServerMgmtProxyController.java` | 新增 | 代理转发 + 鉴权 |
| `lab-erp-demo/src/router/index.js` | 修改 | 新增路由 |
| `lab-erp-demo/src/App.vue` | 修改 | 新增下拉菜单项 + 命令处理 |
| `lab-erp-demo/src/views/ServerManagementView.vue` | 新增 | 服务器管理页面 |
| `server-mgmt/` (整个目录) | 新增 | 从 outside 迁入 |
| `docker-compose.yml` | 修改 | 新增 server-mgmt-api 服务 |

## 不涉及

- 不修改 `outside/` 目录
- 不修改 `server-mgmt/` 的 FastAPI 代码逻辑
- 不修改现有 `constants/provisioning.js`
- 不修改 JWT 鉴权流程

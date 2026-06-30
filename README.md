# ZKR ERP System

容器化部署的 ERP 系统，包含前端（Vue 3）、后端（Spring Boot）和 RAG 服务（Python）。

**当前版本：** `zhangqi_backend:v1.142` / `zhangqi_frontend:v1.163`

## 最近变更

### 2026-06-30 16:05 — 修复腾讯会议绑定 userid 失败

**原因：** 绑定校验调用 `listAllTmUsers()` 拉取全量腾讯会议用户列表，但 API 返回非2xx时静默返回空列表，导致验出"该腾讯会议账号不存在"，实际是 API 调用失败。

**改动位置：**
- `erp-backend/.../TencentMeetingUserSyncService.java:116,144` — 列表 API 失败时新增日志记录 HTTP 状态码和响应体；新增 `findTmUserById()` 方法直接调用 `GET /v1/users/{userid}` 查询单个用户
- `erp-backend/.../TencentUserMappingController.java:70-81` — 绑定校验改用 `findTmUserById()` 替代 `listAllTmUsers()`，错误信息包含"查询失败"提示

**效果：** 绑定校验直接查询指定 userid，更高效且错误信息明确；API 失败时日志可见具体 HTTP 状态码和响应体。

## 容器架构

| 服务 | 技术栈 | 端口 |
|------|--------|------|
| `erp-backend` | Spring Boot (Java 17) | 8101 |
| `lab-erp-demo` | Vue 3 + Nginx | 80 |
| `rag-service` | Python FastAPI + Qdrant | 8090 |
| `server-mgmt-api` | Python FastAPI + Ansible | 17000 |
| `postgres` | PostgreSQL 16 | 5432 |
| `redis` | Redis 7.4 | 6379 |
| `qdrant` | Qdrant vector DB | 6333 |

## 快速开始

### 1. 环境要求

- Docker & Docker Compose
- 本地镜像仓库 `127.0.0.1:5555`（用于 `docker push/pull`）

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际值
```

关键配置项：
- `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`
- `AUTH_PROVISION_ADMIN_*`（管理员Provisioning）
- `FINANCE_RAG_*`（RAG 服务相关）

### 3. 启动全部服务

```bash
docker compose up -d
```

### 4. 查看状态

```bash
docker compose ps
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
```

## 跑批控制系统（Finance Batch Control）

### 概述

跑批控制系统用于可视化管理 ERP 系统内的两个后台自动任务，支持暂停/恢复执行、修改执行时间、手动触发和项目级精细控制。数据通过 `DynamicBatchSchedulerConfig` 动态调度，修改后立即生效。

### 数据表

| 表名 | 用途 |
|------|------|
| `batch_job_control` | 全局任务级调度参数（启用状态、cron、触发历史） |
| `batch_project_cost_control` | 项目级成本跑批控制（启用状态、优先级、备注） |

### 任务一：项目成本跑批（FINANCE_COST_BATCH）

**功能**：每晚自动汇总前一天所有项目成员的工时成本，写入 `finance_cost_entry` 和 `finance_cost_summary`。

**目标**：财务核算系统依赖每日工时成本数据，确保报表准确。

**工作过程**：
1. 每天 00:00（上海时区）从 `batch_job_control` 读取 cron、enabled 状态
2. 如为 disabled，跳过执行
3. 按 `batch_project_cost_control` 中 enabled=true 的项目逐个汇总前一天工时
4. 结果写入 `finance_cost_entry`（按人/天/项目）和 `finance_cost_summary`（按项目/账期）
5. 完成后更新 `batch_job_control.last_triggered_at / last_status / last_message`

**数据库写操作**：
- `finance_cost_entry`（insert）
- `finance_cost_summary`（insert/update）
- `batch_job_control`（update last_run）

**触发条件**：
- 自动：从 `batch_job_control.schedule_mode` 读取 cron 表达式动态调度（默认 `0 0 0 * * *`，每天 00:00）
- 手动：`POST /api/batch/jobs/run?jobKey=FINANCE_COST_BATCH`

### 任务二：钉钉考勤拉取（ATTENDANCE_PULL）

**功能**：每天凌晨自动从钉钉考勤接口拉取前一天的员工打卡记录，写入 `attendance_record`。

**目标**：同步钉钉打卡数据到本地，供工资核算使用。

**工作过程**：
1. 每天凌晨 2:00（固定）从 `batch_job_control` 读取 enabled 状态
2. 如为 disabled，跳过执行
3. 调用钉钉 `/attendance/list` 分页拉取前一天的考勤记录
4. 遍历钉钉用户列表，每次最多 50 人批量写入 `attendance_record`（去重）
5. 完成后更新 `batch_job_control.last_triggered_at / last_status / last_message`

**数据库写操作**：
- `attendance_record`（insert，userId + userCheckTime + checkType 去重）
- `dingtalk_user_directory`（upsert）
- `batch_job_control`（update last_run）

**触发条件**：
- 自动：`POST /api/batch/jobs/run?jobKey=ATTENDANCE_PULL`
- 手动：`POST /api/batch/jobs/run?jobKey=ATTENDANCE_PULL`

### 调度器实现：`DynamicBatchSchedulerConfig`

使用 Spring `TaskScheduler`（非固定 `@Scheduled` cron），动态调度逻辑：
- `configureTasks()`：注册 `ThreadPoolTaskScheduler`（线程池大小 4）
- `scheduleOrRescheduleJob(job)`：根据 `batch_job_control` 中的 enabled / cron 参数动态注册/取消任务
- 每次调用 `updateJobControl()` 时触发 `triggerReschedule(jobKey)`，让 cron 变更实时生效

### REST API

#### 列表全局任务
```
GET /api/batch/jobs
Response: FinanceApiResponse<List<BatchJobControlVO>>
```

#### 更新任务参数
```
PUT /api/batch/jobs?jobKey=<key>&enabled=<true|false>&runAtHour=<0-23>&runAtMinute=<0-59>
```

#### 手动触发任务
```
POST /api/batch/jobs/run?jobKey=<FINANCE_COST_BATCH|ATTENDANCE_PULL>
```

#### 列表项目成本控制
```
GET /api/batch/projects
Response: FinanceApiResponse<List<BatchProjectCostControlVO>>
```

#### 更新项目成本控制
```
PUT /api/batch/projects?projectId=<id>&enabled=<true|false>&priority=<1-999>&note=<string>
```

### BatchJobControlVO 字段说明

| 字段 | 说明 |
|------|------|
| `job_key` | 任务唯一标识（FINANCE_COST_BATCH / ATTENDANCE_PULL） |
| `display_name` | 任务展示名 |
| `enabled` | 全局启用/暂停 |
| `schedule_mode` | MANUAL_ONLY / DAILY_TIME |
| `run_at_hour` | 执行小时（0-23，上海时区） |
| `run_at_minute` | 执行分钟（0-59） |
| `last_triggered_at` | 上次触发时间 |
| `last_status` | COMPLETED / FAILED / TRIGGERED / RUNNING |
| `last_message` | 执行结果摘要 |

### BatchProjectCostControlVO 字段说明

| 字段 | 说明 |
|------|------|
| `project_id` | 项目ID（对应 sys_project.project_id） |
| `project_name` | 项目名称 |
| `enabled` | 是否参与成本跑批 |
| `priority` | 优先级（数字越小优先级越高，范围 1-999） |
| `note` | 备注 |
| `updated_at` | 更新时间 |


## 主要功能

- **ERP 管理** — 账号创建（支持大模型自然语言解析自动填充）、员工管理、勋章发放
- **项目流** — 产品流/项目流/研究流全生命周期管理，含组队、执行、文件上传、可行性报告
- **文件管理** — 跨项目虚拟目录文件管理器（仅管理员），支持扫描、分类、移动、下载
- **实习协议生成** — 创建账号后自动生成三份实习协议 Word 文档（互联网实习生协议、实习生协议、实习证明）
- **Finance 财务** — 报销采购、成本跑批、考勤拉取、清算分红、调账、工资管理
- **服务器管理** — 服务器状态实时检测（SSH 连通性）、Ansible 集成
- **劳动关系资料** — 实习协议/身份证/学生证文件管理
- **RAG 全局业务检索** — 基于 Qdrant 向量数据库的智能搜索

## 历史修复记录

详细 Bug 修复和功能变更记录见 [AGENTS.md](./AGENTS.md)。

## 版本号规则

每次构建镜像前必须先读取当前运行中的容器镜像版本号，新版本 = 当前版本号 + 1：

```bash
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
```

| 规则 | 说明 |
|------|------|
| 版本号递增 | 后端 `v1.XX` → `v1.XX+1`，前端 `v1.XX` → `v1.XX+1` |
| 禁止时间戳 | 禁止使用 `date +%Y%m%d%H%M` 作为版本号 |
| 构建前确认 | 构建前必须确认当前运行中的实际版本号 |

## 部署流程

1. 读取当前版本号
2. 新版本 = 当前版本 + 1
3. 构建前后端镜像
4. `docker push` 两个版本
5. 更新 `docker-compose.yml` 中的 image tag
6. `docker compose up -d erp-backend lab-erp-demo`
7. 验证容器状态
8. git commit 版本号变更

### 查询下一版本号

```bash
# 前端
python3 scripts/next_image_version.py 127.0.0.1:5555 zhangqi_frontend

# 后端
python3 scripts/next_image_version.py 127.0.0.1:5555 zhangqi_backend
```

### 构建前端镜像

```bash
docker build --build-arg APP_VERSION=<version> \
  -t 127.0.0.1:5555/zhangqi_frontend:<version> \
  -t 127.0.0.1:5555/zhangqi_frontend:latest \
  ./lab-erp-demo

docker push 127.0.0.1:5555/zhangqi_frontend:<version>
docker push 127.0.0.1:5555/zhangqi_frontend:latest
```

### 构建后端镜像

```bash
docker build --build-arg APP_VERSION=<version> \
  -t 127.0.0.1:5555/zhangqi_backend:<version> \
  -t 127.0.0.1:5555/zhangqi_backend:latest \
  ./erp-backend

docker push 127.0.0.1:5555/zhangqi_backend:<version>
docker push 127.0.0.1:5555/zhangqi_backend:latest
```

### 更新 docker-compose.yml

将 `docker-compose.yml` 中的镜像 tag 改为新版本号，然后重新部署：

```bash
docker compose up -d erp-backend lab-erp-demo
```

## 版本回滚

将 `docker-compose.yml` 中对应服务的镜像 tag 改回上一个稳定版本，执行：

```bash
docker compose up -d <service>
```

## 访问地址

| 入口 | 地址 |
|------|------|
| ERP 登录 | `http://<host>:8080/erp-login` |
| Finance 登录 | `http://<host>:8080/login` |
| 本地仓库 API | `http://127.0.0.1:5555/v2/` |

## 目录结构

```
ZKR/
├── docker-compose.yml      # 容器编排
├── erp-backend/            # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/     # Java 源码
├── lab-erp-demo/           # Vue 3 前端
│   ├── Dockerfile
│   ├── package.json
│   └── src/               # Vue 源码
├── rag-service/            # Python RAG 服务
│   ├── Dockerfile
│   ├── app.py
│   └── requirements.txt
└── server-mgmt/            # Python 服务器管理
    ├── Dockerfile
    ├── api/main.py
    └── tests/
```

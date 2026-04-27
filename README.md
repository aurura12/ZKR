# ZKR ERP System

容器化部署的 ERP 系统，包含前端（Vue 3）、后端（Spring Boot）和 RAG 服务（Python）。

## 容器架构

| 服务 | 技术栈 | 端口 |
|------|--------|------|
| `erp-backend` | Spring Boot (Java 17) | 8080 |
| `lab-erp-demo` | Vue 3 + Nginx | 80 |
| `rag-service` | Python FastAPI + Qdrant | 8090 |
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


## Bug 修复记录

### 用户离职后历史项目不可见 & 工资=0保存失败 (2026-04-27)

**现象**：
1. 用户点击"离职"后，该用户从所有历史项目的成员列表中消失
2. 工资管理中，将日工资设置为 0 时保存失败（实际保存到 DB 但每次重启被重置为 300）

**根因**：
1. `UserService.deactivateUser()` 在设置 `active=false` 的同时，遍历用户参与的所有项目，从 `sys_project_member` 中删除了该用户。这与设计文档（`docs/superpowers/specs/2026-04-26-user-deactivation-design.md`）第 14 行"历史项目中该用户仍然可见"的要求冲突。
2. 工资=0 有多层矛盾：`UserService.updateDailyWage()` 接受 0 并写入 DB，但 `UserSchemaMigration` 每次启动执行 `UPDATE sys_user SET daily_wage = 300 WHERE daily_wage <= 0`，导致实际效果总是被覆盖。

**修复措施**：
1. **`UserService.java`**：`deactivateUser()` 只设置 `active=false`，不再删除 `sys_project_member` 条目
2. **`UserService.java`**：新增 `activateUser()` 方法，设置 `active=true` 并从 `project_member_participation_history` 重建 `sys_project_member`（可用于恢复之前误删的记录）
3. **`AdminUserController.java`**：新增 `POST /api/admin/users/{userId}/activate` 还原端点
4. **`WageManagementView.vue`**：离职用户行显示"还原"按钮，点击后调用 activate 接口
5. **`UserService.java`**：`updateDailyWage()` 验证改为 `<= 0`，明确拒绝 0 和负数
6. **`UserSchemaMigration.java`**：移除 `daily_wage <= 0` 重置为 300 的逻辑（仅保留 `NULL` 修复）
7. **`FinanceCostBatchService.java`**：`resolveDailyWage()` 从 `<= 0` 改为 `< 0`
8. **`AuthService.java`**：`provisionUser()` 同样改为 `< 0`
9. **`WageManagementView.vue`**：前端 `el-input-number :min` 改为 `0.01`，验证提示改为"日工资必须大于0"
10. **`UserService.java`**：新增 `findAllUsersIncludingInactive()`，`GET /api/admin/users` 现在返回所有用户（含离职），便于管理员查看和还原
11. **`ProjectMemberParticipationHistoryRepository.java`**：新增 `findByUser_UserId()` 查询方法

**涉及文件**：`UserService.java`, `AdminUserController.java`, `WageManagementView.vue`, `UserSchemaMigration.java`, `FinanceCostBatchService.java`, `AuthService.java`, `ProjectMemberParticipationHistoryRepository.java`

### Finance 侧边栏导航错误 (2026-04-26)

**现象**：Finance 系统左侧侧边栏点击后进入其他栏目，而非目标栏目。例如点击"跑批控制"显示清算中心内容，点击"清算"显示分红中心内容。

**根因**：`lab-erp-demo/src/router/financeRoutes.js` 第 187-217 行，路由定义使用了错误的 `financeNavigationItems` 数组索引。第 6 条路由（跑批控制）错误引用了 `financeNavigationItems[11]`（考勤工资的路径/名称），而非 `[4]`，导致后续全部路由的 path/name 与 component 错位映射。

**错误代码位置**：`lab-erp-demo/src/router/financeRoutes.js:187-217`

**错误映射**：

| 侧边栏点击 | 实际渲染组件 | 应渲染组件 |
|-----------|-------------|-----------|
| 跑批控制 | ClearingCenterView | BatchControlView |
| 清算 | DividendCenterView | ClearingCenterView |
| 分红 | AdjustmentCenterView | DividendCenterView |
| 调账 | FinanceExpenseCenterView | AdjustmentCenterView |
| 报销采购 | RagSearchView | FinanceExpenseCenterView |
| 全局业务检索 | FinanceAiChatView | RagSearchView |
| 全局业务助手 | FinanceAttendanceView | FinanceAiChatView |
| 考勤工资 | (无对应路由) | FinanceAttendanceView |

**修复措施**：将路由定义中的 `financeNavigationItems` 索引从 `[11,4,5,6,7,8,9,10]` 修正为 `[4,5,6,7,8,9,10,11]`，确保每条路由的路径、名称与渲染组件一一对应。

**改后结果**：所有 11 个侧边栏栏目点击后正确进入对应功能页面。

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
└── rag-service/            # Python RAG 服务
    ├── Dockerfile
    ├── app.py
    └── requirements.txt
```

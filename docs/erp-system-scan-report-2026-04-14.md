# ERP 系统深度扫描文档

> 生成日期：2026-04-14
> 项目路径：/home/a/zhangqi/workspace/ZKR/
> 前端路径：/home/a/zhangqi/workspace/ZKR/lab-erp-demo/
> 后端路径：/home/a/zhangqi/workspace/ZKR/erp-backend/

---

## 一、项目架构总览

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              ERP 系统架构                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│  前端 (lab-erp-demo)                                                        │
│  - 技术栈：Vue 3 + Vite + Axios                                            │
│  - 端口：8080 (宿主机映射)                                                 │
│  - 容器名：zkr-lab-erp-demo                                                 │
│  - 镜像：127.0.0.1:5555/zhangqi_frontend:v1.98                             │
│  - Nginx 代理：/api → http://zkr-erp-backend:8101                          │
├──────────────────────────────────────────────────────────────────────────────┤
│  后端 (erp-backend)                                                        │
│  - 技术栈：Spring Boot 3.2.0 + Java 17 + JPA + PostgreSQL                 │
│  - 端口：8101 (容器内)                                                      │
│  - 容器名：zkr-erp-backend                                                 │
│  - 镜像：127.0.0.1:5555/zhangqi_backend:v1.66                               │
│  - 数据库：PostgreSQL 16 (postgres:5432)                                    │
├──────────────────────────────────────────────────────────────────────────────┤
│  RAG 服务 (rag-service) [财务AI专用]                                        │
│  - 技术栈：Python 3.12 + FastAPI + Qdrant + Redis                          │
│  - 端口：8088 (容器内映射到 127.0.0.1:36817)                               │
│  - 容器名：finance-rag-api                                                 │
│  - 依赖：finance-rag-qdrant (36333), finance-rag-redis (36379)             │
├──────────────────────────────────────────────────────────────────────────────┤
│  数据存储                                                                   │
│  - PostgreSQL：postgres-data volume                                         │
│  - Qdrant：finance-rag-qdrant-data volume                                  │
│  - Redis：finance-rag-redis-data volume                                    │
│  - 上传文件：backend-uploads volume → /app/uploads                        │
│  - 公共下载：/home/a/zhangqi/workspace/ZKR/public-downloads                │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 1.1 三流并行架构

ERP 系统采用 **三流并行** 架构，支持三种完全独立的业务轨道：

| 流程类型 | FlowType | 状态枚举 | 典型业务场景 |
|---------|----------|---------|-------------|
| 项目流 | `PROJECT` | `ProjectStatus` | 对外接单，咨询/硬件集成 |
| 产品流 | `PRODUCT` | `ProductStatus` | 内部创投，软件/算法产品 |
| 科研流 | `RESEARCH` | `ResearchStatus` | 技术攻坚，生成中间件资产 |

---

## 二、认证与权限体系

### 2.1 JWT Token 结构

```json
{
  "userId": "string",
  "username": "string",
  "name": "string",
  "role": "string",
  "email": "string",
  "accountDomain": "ERP | FINANCE"
}
```

### 2.2 Security 配置

| 配置项 | 值 |
|--------|-----|
| CORS | 允许所有源 + credentials |
| Session | 无状态 (STATELESS) |
| CSRF | 禁用 |

### 2.3 路径权限矩阵

| 路径模式 | 权限要求 |
|---------|---------|
| `/api/auth/**` | permitAll |
| `/api/users` (POST) | permitAll (注册) |
| `/api/finance/**`, `/api/adjustment/**`, `/api/batch/**`, `/api/clearing/**`, `/api/dividend/**`, `/api/ai/**`, `/api/rag/**` | FINANCE 域 |
| `/api/**` | ERP 域认证 |

### 2.4 系统角色体系

| 角色代码 | 说明 |
|---------|------|
| `ADMIN` | 系统管理员 |
| `BUSINESS` | 商务 (BD) |
| `DATA` | 数据工程师 |
| `DEV` | 开发工程师 |
| `ALGORITHM` | 算法工程师 |
| `RESEARCH` | 科研人员 |
| `PROMOTION` | 推广 |
| `MANAGER` | 项目管理员 |
| `PM` | 项目经理 |

### 2.5 项目成员角色与权限

| 角色 | 权限 |
|------|------|
| `PM` / `LEAD` | READ, WRITE, BUDGET, ASSIGN, DELETE |
| `DEV` / `ALGORITHM` / `DATA` / `QA` | READ, WRITE, UPLOAD |
| `BUSINESS` / `VIEWER` | READ |

### 2.6 特殊权限规则

- **Admin 用户名白名单**: `Zhangqi`, `guojianwen`, `jiaomiao`
- 已完结项目预算修改权限被锁定
- 三流架构权限完全隔离
- FINANCE 域账号只能访问财务相关 API

---

## 三、后端接口 (Backend APIs)

后端源码：`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/`

技术栈：Spring Boot 3.2.0 | Java 17 | Spring Security + JWT | Spring Data JPA | PostgreSQL

### 3.1 统一响应结构

```java
// FinanceApiResponse<T>
{
  "status": "success",        // "success" | "error"
  "message": "操作描述",
  "data": { ... },            // 实际数据
  "meta": {
    "page": 1,
    "size": 20,
    "total": 100,
    "totalPages": 5,
    "traceId": "uuid",
    "timestamp": "2026-04-14T..."
  },
  "timestamp": "2026-04-14T...",
  "traceId": "uuid"
}
```

---

### 3.2 认证模块 — AuthController

路径：`/api/auth`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| POST | `/login` | `LoginRequest {username, password}` | `{token: string, ...}` | 用户登录 |
| POST | `/register` | `RegisterRequest {username, password, email, domain}` | `"注册成功，请登录"` | 用户注册 |
| POST | `/change-password` | `ChangePasswordRequest {oldPassword, newPassword}` | `{message: "密码修改成功"}` | 修改密码 |
| GET | `/me` | 无 | `User` 对象 | 获取当前用户 |
| POST | `/logout` | 无 | 204 | 登出 |

### 3.3 密码重置 — PasswordResetController

路径：`/api/auth/password`

| 方法 | 路径 | 请求体 | 说明 |
|-----|------|--------|------|
| POST | `/send-code` | `EmailVerificationRequest {email}` | 发送邮箱验证码 |
| POST | `/verify-code` | `VerifyCodeRequest {email, code}` | 验证验证码 |
| POST | `/reset` | `ResetPasswordRequest {email, code, newPassword}` | 重置密码 |

### 3.4 管理员用户 — AdminUserController

路径：`/api/admin/users`

| 方法 | 路径 | 请求体 | 说明 |
|-----|------|--------|------|
| POST | `/provision` | `ProvisionUserRequest` | 管理员创建用户账号 |

---

### 3.5 用户管理 — UserController

路径：`/api/users`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/` | 获取人才库列表 |
| PUT | `/avatar` | 更新用户头像 |

### 3.6 用户勋章 — UserBadgeController

路径：`/api/user-badges`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/{userId}` | 获取用户勋章列表 |
| POST | `/` | 发放勋章 |

---

### 3.7 项目管理 — ProjectController

路径：`/api/projects`

| 方法 | 路径 | 参数 | 说明 |
|-----|------|------|------|
| GET | `/` | 无 | 获取参与项目列表（兜底） |
| GET | `/managed` | 无 | 获取管理项目列表 |
| GET | `/managed/summary` | 无 | 获取管理项目汇总 |
| GET | `/dashboard` | 无 | 获取财务看板 |
| GET | `/workspace` | 无 | 获取工作区参与项目 |
| GET | `/{id}` | 路径: id | 获取项目详情 |
| GET | `/{id}/earnings/me` | 路径: id | 获取我的项目收益 |
| POST | `/` | 无 | 创建项目 |
| PUT | `/{id}/critical-task` | `{critical_task: string}` | 更新关键任务 |
| POST | `/{id}/assets` | `file`, `assetCategory` | 上传项目资产 |
| POST | `/{id}/travel-reimbursements` | multipart | 提交出差报销 |
| GET | `/{id}/assets/{assetId}/download` | 路径: id, assetId | 下载资产文件 |
| POST | `/{id}/milestones` | `{title, dueDate}` | 添加里程碑 |
| POST | `/{id}/subtasks` | `ProjectSubtaskRequest` | 创建子任务 |
| PUT | `/{id}/subtasks/{subtaskId}` | 路径: id, subtaskId | 更新子任务 |
| POST | `/{id}/subtasks/{subtaskId}/complete` | 路径: id, subtaskId | 完成子任务 |
| PUT | `/{id}/project-status` | query: status | 更新项目状态 |
| PUT | `/{id}/product-status` | query: status | 更新产品状态 |
| DELETE | `/{id}` | 路径: id | 删除项目 |

---

### 3.8 项目流 — ProjectFlowController

路径：`/api/projects`

| 方法 | 路径 | 请求体 | 说明 |
|-----|------|--------|------|
| POST | `/initiate` | `ProjectInitiateRequestDTO` | 商务发起项目 |
| POST | `/{projectId}/build-team` | `ProjectBuildTeamRequestDTO` | 组建团队 |
| POST | `/{projectId}/execution/plan` | `ExecutionPlanRequestDTO` | 设定实施计划 |
| PATCH | `/{projectId}/execution/team-members` | `ProductMemberUpdateRequest` | 更新团队成员 |
| GET | `/{projectId}/task-assignments` | 无 | 获取任务分配 |
| PUT | `/{projectId}/task-assignments` | `ProjectTaskAssignmentUpdateRequest` | 更新任务分配 |
| PATCH | `/{projectId}/implementation-status` | `{status: string}` | 更新实施状态 |
| PATCH | `/{projectId}/dynamic-info` | `ProjectDynamicInfoUpdateRequest` | 更新动态信息 |
| GET | `/{projectId}/execution/overview` | 无 | 获取执行总览 |
| PATCH | `/{projectId}/execution/schedules/{userId}/confirm` | `{confirmed: boolean}` | 确认成员排期 |
| POST | `/{projectId}/execution/upload` | multipart | 双盲文件上传 |
| GET | `/{projectId}/execution/files/{fileId}/download` | 无 | 下载执行文件 |
| DELETE | `/{projectId}/execution/files/{fileId}` | 无 | 删除执行文件 |
| POST | `/{projectId}/execution/archive-folders` | `{parentPath, folderName}` | 创建归档文件夹 |
| PATCH | `/{projectId}/execution/files/{fileId}/archive-folder` | `{targetFolderPath}` | 移动到归档 |
| PATCH | `/{projectId}/execution/files/{fileId}/category` | `{secondaryCategory}` | 重分类文件 |
| POST | `/{projectId}/settlement/complete` | multipart | OCR结算完结 |

---

### 3.9 项目聊天 — ProjectChatController

路径：`/api/projects/{projectId}/chat`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/messages` | 获取项目聊天消息 |
| POST | `/messages` | 发送项目聊天消息 |
| GET | `/participants` | 获取参与者列表 |

### 3.10 项目Git仓库 — ProjectGitRepositoryController

路径：`/api/projects/{projectId}/git-repositories`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/` | 列出仓库 |
| POST | `/` | 创建仓库 |
| POST | `/{repoId}/test` | 测试连接 |
| GET | `/{repoId}/logs` | 获取提交日志 |

---

### 3.11 产品流 — ProductFlowController

路径：`/api/products`

| 方法 | 路径 | 请求体 | 说明 |
|-----|------|--------|------|
| POST | `/idea` | `ProductIdeaRequest` | 创建产品创意 |
| POST | `/{projectId}/promotion-setup` | `ProductPromotionSetupRequest` | 推广与Demo组队 |
| POST | `/{projectId}/demo/upload` | multipart | 上传Demo文件 |
| POST | `/{projectId}/meeting-decision` | `{meetingMinutes, decision, participantUserIds}` | 虚拟会议决策 |
| POST | `/{projectId}/testing-feedback` | `{testFeedback, isPassed}` | 测试反馈 |
| PATCH | `/{projectId}/team-members` | `ProductMemberUpdateRequest` | 更新团队成员 |
| GET | `/{projectId}/task-assignments` | 无 | 获取任务分配 |
| PUT | `/{projectId}/task-assignments` | `ProductTaskAssignmentUpdateRequest` | 更新任务分配 |

---

### 3.12 科研流 — ResearchFlowController

路径：`/api/research`

| 方法 | 路径 | 请求体 | 说明 |
|-----|------|--------|------|
| POST | `/initiate` | `ResearchInitiateRequest` | 发起科研项目 |
| POST | `/{projectId}/set-construction-mode` | `{executionMode}` | 设置施工执行模式 |
| POST | `/{projectId}/transition` | `ResearchStatusTransitionRequest` | 科研状态流转 |
| POST | `/{projectId}/archive-to-middleware` | `{middlewareName, middlewareDesc, repoUrl}` | 评测入库生成中间件 |

---

### 3.13 消息系统 — InternalMessageController

路径：`/api/messages`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/` | 获取消息列表 |
| GET | `/unread-count` | 获取未读消息数 |
| PATCH | `/{id}/read` | 标记已读 |

---

### 3.14 通用文件 — FileController

路径：`/api/files`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/upload` | 通用文件上传 |

### 3.15 报销提交 — ExpenseSubmissionController

路径：`/api/submissions`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/personal-procurement` | 提交个人采购申请 |

---

### 3.16 中间件中心 — MiddlewareHubController

路径：`/api/middleware-hub`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/` | 获取中间件列表 (支持 keyword, flowType 搜索) |
| GET | `/repository-view` | 中间件仓库视图 |
| GET | `/{id}` | 获取单个中间件详情 |
| POST | `/` | 创建中间件资产 |
| PUT | `/{id}` | 更新中间件资产 |

---

### 3.17 命令执行 — CommandController

路径：`/api/command`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/interpret` | 命令解析预览 |
| POST | `/execute` | 命令执行 |

---

### 3.18 财务报表 — FinanceController

路径：`/api/finance`

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/statements` | 获取财务报表 |
| GET | `/wallets` | 获取钱包总览 |
| GET | `/transactions` | 获取交易记录 |
| GET | `/submissions` | 获取报销提交列表 |
| GET | `/submissions/{submissionId}/invoice` | 下载发票 |
| POST | `/bank_balance` | 记录银行余额快照 |

---

### 3.19 财务工作台 — FinanceWorkbenchController

路径：`/api/batch`, `/api/clearing`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/batch/run_cost` | 运行成本分摊批次 |
| GET | `/api/batch/preview/{ventureId}` | 预览成本分摊 |
| GET | `/api/clearing/ventures` | 获取可清分事业部 |
| POST | `/api/clearing/execute` | 执行清分 |

---

### 3.20 分红管理 — FinanceDividendController

路径：`/api/dividend`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/prepare` | 准备分红清单 |
| GET | `/list` | 列出分红表 |
| POST | `/confirm` | 确认分红 |

---

### 3.21 调账管理 — FinanceAdjustmentController

路径：`/api/adjustment`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/create` | 创建调账记录 |
| GET | `/list` | 列出调账记录 |

---

### 3.22 财务AI — FinanceAiController

路径：`/api/ai`, `/api/rag`

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/ai/chat` | AI 对话 |
| POST | `/api/rag/query` | RAG 知识库查询 |
| POST | `/api/rag/push` | 刷新 RAG 索引 |

---

## 四、数据库字段 (Database Schema)

数据库：PostgreSQL 16 | JPA Hibernate 自动管理 | DDL_AUTO: update

### 4.1 核心 Entity

#### sys_user — 用户表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| user_id | VARCHAR(64) | PK | 用户ID (UUID字符串) |
| username | VARCHAR | UNIQUE, NOT NULL | 用户名 |
| password_hash | VARCHAR | NOT NULL | 密码哈希 |
| name | VARCHAR | | 姓名 |
| email | VARCHAR | | 邮箱 |
| role | VARCHAR | | 系统角色 |
| avatar | VARCHAR | | 头像URL |
| hidden_avatar | BOOLEAN | DEFAULT false | 隐藏头像 |
| account_domain | VARCHAR(32) | DEFAULT 'ERP' | 账号域 |
| is_active | BOOLEAN | DEFAULT true | 是否激活 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

#### sys_project — 系统统一项目表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| project_id | VARCHAR(64) | PK | 项目ID (UUID) |
| name | VARCHAR(100) | NOT NULL | 项目名称 |
| description | TEXT | | 项目描述 |
| project_type | VARCHAR(20) | NOT NULL | 项目类型 |
| flow_type | VARCHAR(20) | NOT NULL | 流程类型 (PROJECT/PRODUCT/RESEARCH) |
| project_status | VARCHAR(20) | | 项目流状态 |
| product_status | VARCHAR(20) | | 产品流状态 |
| research_status | VARCHAR(30) | | 科研流状态 |
| manager_id | VARCHAR(64) | FK → sys_user | 负责人 |
| budget | DECIMAL(15,2) | | 预算 |
| estimated_revenue | DECIMAL(19,4) | | 预计收入 |
| feasibility_report_url | VARCHAR | | 可行性报告 |
| project_tier | VARCHAR(10) | | 项目评级 |
| cost | DECIMAL(15,2) | DEFAULT 0 | 成本 |
| tech_stack | VARCHAR | | 技术栈 |
| repo_url | VARCHAR | | 仓库URL |
| deploy_url | VARCHAR | | 部署URL |
| ocr_timestamp | TIMESTAMP | | OCR时间戳 |
| ocr_work_hours | INTEGER | | OCR工时 |
| settlement_proof_url | VARCHAR | | 结算凭证 |
| start_date | TIMESTAMP | | 开始日期 |
| end_date | TIMESTAMP | | 结束日期 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

#### sys_project_member — 项目成员表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK | 主键 |
| project_id | VARCHAR(64) | NOT NULL | 项目ID |
| user_id | VARCHAR(64) | FK → sys_user | 用户ID |
| role | VARCHAR(20) | DEFAULT 'MEMBER' | 成员角色 |
| weight | INTEGER | DEFAULT 0 | 权重 |
| manager_weight | INTEGER | DEFAULT 0 | 经理权重 |
| joined_at | TIMESTAMP | | 加入时间 |

**唯一约束**: (project_id, user_id)

---

### 4.2 项目扩展 Entity

#### project_asset — 项目资产文件

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| file_name | VARCHAR | 原始文件名 |
| file_type | VARCHAR | 文件后缀 |
| file_path | VARCHAR | 物理路径 |
| file_data | BYTEA | 文件数据 |
| content_type | VARCHAR | MIME类型 |
| file_size | BIGINT | 文件大小 |
| uploader_name | VARCHAR | 上传人 |
| uploaded_at | TIMESTAMP | 上传时间 |
| asset_category | VARCHAR | 资产分类 |

#### execution_file — 实施阶段双盲隔离文件

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| folder_type | VARCHAR(30) | A_MANAGER_ARCHIVE / B_ENGINEER_WORK |
| uploader_user_id | VARCHAR(64) | 上传人ID |
| uploader_name | VARCHAR | 上传人 |
| file_name | VARCHAR | 文件名 |
| secondary_category | VARCHAR | 二级分类/归档路径 |
| file_type | VARCHAR | 文件后缀 |
| file_path | VARCHAR | 物理路径 |
| file_size | BIGINT | 文件大小 |
| uploaded_at | TIMESTAMP | 上传时间 |

#### execution_archive_folder — Manager归档文件夹

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| folder_type | VARCHAR(30) | 文件夹类型 |
| folder_path | VARCHAR | 文件夹路径 |
| parent_path | VARCHAR | 父路径 |
| created_by_user_id | VARCHAR(64) | 创建人 |
| created_at | TIMESTAMP | 创建时间 |

#### file_upload — 通用文件上传

| 字段名 | 类型 | 说明 |
|--------|------|------|
| upload_id | BIGINT | PK |
| flow_type | VARCHAR(20) | 流程类型 |
| flow_id | BIGINT | 流程ID |
| original_name | VARCHAR | 原始文件名 |
| file_path | VARCHAR | 存储路径 |
| file_size | BIGINT | 文件大小 |
| mime_type | VARCHAR | MIME类型 |
| uploader | VARCHAR(64) | FK → sys_user |
| uploaded_at | TIMESTAMP | 上传时间 |
| file_type | VARCHAR | 文件类型 |

---

### 4.3 消息系统 Entity

#### internal_message — 内部消息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| recipient_user_id | VARCHAR(64) | 接收人ID |
| message_type | VARCHAR | 消息类型 |
| title | VARCHAR | 标题 |
| content | TEXT | 内容 |
| project_id | VARCHAR(64) | 关联项目 |
| read | BOOLEAN | 已读标记 |
| created_at | TIMESTAMP | 创建时间 |

#### project_chat_message — 项目聊天消息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| sender_user_id | VARCHAR(64) | 发送者ID |
| sender_name | VARCHAR | 发送者姓名 |
| content | TEXT | 消息内容 |
| stage_tag | VARCHAR | 阶段标签 |
| created_at | TIMESTAMP | 创建时间 |

---

### 4.4 中间件资产 Entity

#### middleware_asset — 中间件资产

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| name | VARCHAR | 中间件名称 |
| description | TEXT | 功能描述 |
| source_project_id | VARCHAR(64) | 溯源项目ID |
| source_flow_type | VARCHAR(20) | 来源流程类型 |
| source_status | VARCHAR | 来源状态 |
| owner_user_id | VARCHAR(64) | 所有者 |
| repo_url | VARCHAR | 仓库地址 |
| rating | VARCHAR | 评级 (默认A级) |
| pricing_model | VARCHAR | 定价模型 |
| unit_price | DECIMAL(15,2) | 单价 |
| internal_cost_price | DECIMAL(15,2) | 内部成本价 |
| market_reference_price | DECIMAL(15,2) | 市场参考价 |
| currency | VARCHAR | 币种 (默认CNY) |
| billing_unit | VARCHAR | 计费单元 (默认PROJECT) |
| version_tag | VARCHAR | 版本标签 |
| lifecycle_status | VARCHAR | 生命周期状态 |
| extra_metadata | TEXT | 额外元数据 |
| created_at | TIMESTAMP | 创建时间 |

#### middleware_royalty_roster — 中间件分润名册

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| middleware_id | BIGINT | FK → middleware_asset |
| user_id | VARCHAR(64) | 分润人 |
| royalty_ratio | DECIMAL(6,4) | 分润比例 |

---

### 4.5 项目管理 Entity

#### project_milestone — 项目里程碑

| 字段名 | 类型 | 说明 |
|--------|------|------|
| milestone_id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| title | VARCHAR | 标题 |
| description | TEXT | 描述 |
| status | VARCHAR | 状态 |
| due_date | TIMESTAMP | 截止日期 |
| completed_date | TIMESTAMP | 完成日期 |
| created_at | TIMESTAMP | 创建时间 |

#### project_subtask — 项目子任务

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| title | VARCHAR | 标题 |
| description | TEXT | 描述 |
| assignee_user_id | VARCHAR(64) | 负责人 |
| assignee_name | VARCHAR | 负责人姓名 |
| sort_order | INTEGER | 排序 |
| completed | BOOLEAN | 是否完成 |
| completed_at | TIMESTAMP | 完成时间 |
| created_by | VARCHAR(64) | 创建人 |
| created_at | TIMESTAMP | 创建时间 |

#### project_member_schedule — 成员排期

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| user_id | VARCHAR(64) | 用户ID |
| expected_start_date | TIMESTAMP | 预期开始 |
| expected_end_date | TIMESTAMP | 预期结束 |
| actual_end_date | TIMESTAMP | 实际完成 |
| task_name | VARCHAR | 任务名称 |
| expected_output | TEXT | 预期产出 |
| completed | BOOLEAN | 是否结业 |
| manager_confirmed | BOOLEAN | Manager确认 |
| manager_confirmed_at | TIMESTAMP | 确认时间 |
| created_at | TIMESTAMP | 创建时间 |

#### project_execution_plan — 实施计划

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| difficulty_level | VARCHAR | 难度分级 |
| project_tier | VARCHAR(10) | 项目评级 |
| goal_description | TEXT | 目标描述 |
| tech_stack_description | TEXT | 技术栈描述 |
| created_by | VARCHAR(64) | 创建人 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### project_budget_history — 预算变更记录

| 字段名 | 类型 | 说明 |
|--------|------|------|
| history_id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| amount | DECIMAL(15,2) | 金额 |
| change_type | VARCHAR | 变更类型 |
| description | TEXT | 描述 |
| changed_by | VARCHAR(64) | FK → sys_user |
| changed_at | TIMESTAMP | 变更时间 |

#### project_git_repository — Git仓库

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| repository_url | VARCHAR | 仓库URL |
| access_token | VARCHAR | 访问令牌 |
| branch | VARCHAR | 分支 |
| provider | VARCHAR | 提供商 |
| created_by_user_id | VARCHAR(64) | 创建人 |
| last_test_status | VARCHAR | 最后测试状态 |
| last_test_message | TEXT | 测试消息 |
| last_tested_at | TIMESTAMP | 测试时间 |
| active | BOOLEAN | 是否激活 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### user_badge — 用户勋章

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| user_id | VARCHAR(64) | 用户ID |
| badge_name | VARCHAR | 勋章名称 |
| badge_icon | VARCHAR | 勋章图标 |
| badge_color | VARCHAR | 勋章颜色 |
| awarded_by | VARCHAR(64) | 发放人 |
| created_at | TIMESTAMP | 创建时间 |

#### email_verification_code — 邮箱验证码

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| email | VARCHAR | 邮箱 |
| code | VARCHAR | 验证码 |
| expires_at | TIMESTAMP | 过期时间 |
| created_at | TIMESTAMP | 创建时间 |
| verified | BOOLEAN | 是否已验证 |

---

### 4.6 流程扩展 Entity

#### research_project_profile — 科研流扩展表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| status | VARCHAR(30) | 科研状态 |
| innovation_point | TEXT | 创新点 |
| idea_text | TEXT | 创意文本 |
| budget_estimate | DECIMAL(15,2) | 预算估算 |
| idea_owner_user_id | VARCHAR(64) | 创意负责人 |
| host_user_id | VARCHAR(64) | 主持人 |
| chief_engineer_user_id | VARCHAR(64) | 首席工程师 |
| blueprint_owner_user_id | VARCHAR(64) | 蓝图负责人 |
| architecture_owner_user_id | VARCHAR(64) | 架构负责人 |
| task_breakdown_owner_user_id | VARCHAR(64) | 任务分解负责人 |
| evaluation_report_owner_user_id | VARCHAR(64) | 评测报告负责人 |
| execution_mode | VARCHAR | 执行模式 |
| workflow_flags | VARCHAR | 工作流标志 |

#### product_idea_detail — 产品流扩展表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | 项目ID |
| target_users | TEXT | 目标用户 |
| core_features | TEXT | 核心特性 |
| use_case | TEXT | 主要用途 |
| problem_statement | TEXT | 要解决的问题 |
| tech_stack_desc | TEXT | 技术栈描述 |
| idea_owner_user_id | VARCHAR(64) | 创意负责人 |
| promotion_ic_user_id | VARCHAR(64) | 推广IC |
| meeting_participant_user_ids | VARCHAR | 会议参与人 |
| test_feedback | TEXT | 测试反馈 |
| demo_engineering_owner_user_id | VARCHAR(64) | Demo工程负责人 |
| demo_file_owner_user_id | VARCHAR(64) | Demo文件负责人 |
| demo_description_owner_user_id | VARCHAR(64) | Demo描述负责人 |
| demo_feasibility_owner_user_id | VARCHAR(64) | Demo可行性负责人 |

---

### 4.7 财务 Entity (完整列表)

#### finance_wallet_account — 钱包账户

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| user_id | VARCHAR(64) | FK, UNIQUE → sys_user |
| balance | DECIMAL(15,2) | 当前余额 |
| total_dividend_earned | DECIMAL(15,2) | 累计分红 |
| total_royalty_earned | DECIMAL(15,2) | 累计版税 |
| total_middleware_profit | DECIMAL(15,2) | 累计中间件利润 |
| total_promotion_expense | DECIMAL(15,2) | 累计推广费用 |
| total_adjustment_amount | DECIMAL(15,2) | 累计调账金额 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### finance_wallet_transaction — 钱包交易

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| wallet_id | BIGINT | FK → finance_wallet_account |
| transaction_type | VARCHAR(20) | 交易类型 |
| cash_flow_direction | VARCHAR(10) | 资金流向 |
| amount | DECIMAL(15,2) | 金额 |
| balance_after | DECIMAL(15,2) | 交易后余额 |
| project_id | VARCHAR(64) | FK → sys_project |
| source_table | VARCHAR(100) | 来源表 |
| source_id | BIGINT | 来源ID |
| remark | TEXT | 备注 |
| created_at | TIMESTAMP | 创建时间 |

#### finance_expense_submission — 报销提交

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| submission_type | VARCHAR(50) | 提交类型 |
| status | VARCHAR(30) | 状态 |
| submitter_user_id | VARCHAR(64) | 提交人ID |
| submitter_name | VARCHAR | 提交人姓名 |
| project_id | VARCHAR(64) | 项目ID |
| project_name | VARCHAR | 项目名称 |
| project_flow_type | VARCHAR(30) | 流程类型 |
| item_name | VARCHAR(200) | 物品名称 |
| item_category | VARCHAR(100) | 物品分类 |
| item_specification | VARCHAR(200) | 规格 |
| quantity | INTEGER | 数量 |
| unit_price | DECIMAL(15,2) | 单价 |
| total_amount | DECIMAL(15,2) | 总金额 |
| supplier_name | VARCHAR | 供应商 |
| invoice_number | VARCHAR(100) | 发票号 |
| occurred_at | TIMESTAMP | 发生时间 |
| purpose | TEXT | 用途 |
| remarks | TEXT | 备注 |
| departure_location | VARCHAR | 出发地 |
| destination_location | VARCHAR | 目的地 |
| travel_start_at | TIMESTAMP | 出行开始 |
| travel_end_at | TIMESTAMP | 出行结束 |
| invoice_file_name | VARCHAR | 发票文件名 |
| invoice_file_path | VARCHAR | 发票路径 |
| invoice_content_type | VARCHAR | 发票类型 |
| invoice_file_size | BIGINT | 发票大小 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### finance_cost_batch — 成本分摊批次

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| ledger_month | VARCHAR(7) | 账期 (YYYY-MM) |
| status | VARCHAR(20) | 状态 |
| generated_record_count | INTEGER | 生成记录数 |
| operator_user_id | VARCHAR(64) | 操作人ID |
| batch_date | DATE | 批次日期 |
| started_at | TIMESTAMP | 开始时间 |
| completed_at | TIMESTAMP | 完成时间 |
| remark | TEXT | 备注 |

#### finance_cost_entry — 成本分摊明细

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| batch_id | BIGINT | FK → finance_cost_batch |
| project_id | VARCHAR(64) | FK → sys_project |
| user_id | VARCHAR(64) | FK → sys_user |
| ledger_month | VARCHAR(7) | 账期 |
| work_hours | DECIMAL(10,2) | 工时 |
| labor_cost | DECIMAL(15,2) | 人工成本 |
| middleware_royalty_fee | DECIMAL(15,2) | 中间件版税 |
| final_settlement_cost | DECIMAL(15,2) | 最终结算成本 |
| source_table | VARCHAR(100) | 来源表 |
| source_id | BIGINT | 来源ID |
| created_at | TIMESTAMP | 创建时间 |

#### finance_cost_summary — 成本汇总

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| batch_id | BIGINT | FK → finance_cost_batch |
| project_id | VARCHAR(64) | FK → sys_project |
| ledger_month | VARCHAR(7) | 账期 |
| total_labor_cost | DECIMAL(15,2) | 总人工成本 |
| total_middleware_fee | DECIMAL(15,2) | 总中间件费用 |
| total_settlement_cost | DECIMAL(15,2) | 总结算成本 |
| entry_count | INTEGER | 条目数 |

#### finance_dividend_sheet — 分红表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| user_id | VARCHAR(64) | FK → sys_user |
| ledger_month | VARCHAR(7) | 账期 |
| amount | DECIMAL(15,2) | 分红金额 |
| dividend_ratio | DECIMAL(6,4) | 分红比例 |
| net_profit_snapshot | DECIMAL(15,2) | 净利润快照 |
| status | VARCHAR(20) | 状态 |
| confirmed_at | TIMESTAMP | 确认时间 |
| confirmed_by | VARCHAR(64) | 确认人 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### finance_clearing_sheet — 清分表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| ledger_month | VARCHAR(7) | 账期 |
| final_revenue | DECIMAL(15,2) | 最终收入 |
| total_cost | DECIMAL(15,2) | 总成本 |
| net_profit | DECIMAL(15,2) | 净利润 |
| middleware_fee | DECIMAL(15,2) | 中间件费用 |
| carry_forward_loss | DECIMAL(15,2) | 结转亏损 |
| status | VARCHAR(20) | 状态 |
| cleared_at | TIMESTAMP | 清分时间 |
| cleared_by | VARCHAR(64) | 清分人 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### finance_adjustment_log — 调账日志

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| wallet_id | BIGINT | FK → finance_wallet_account |
| user_id | VARCHAR(64) | FK → sys_user |
| direction | VARCHAR(10) | 方向 (DEBIT/CREDIT) |
| amount | DECIMAL(15,2) | 金额 |
| reason | TEXT | 原因 |
| source_table | VARCHAR(100) | 来源表 |
| source_id | BIGINT | 来源ID |
| ref_doc_no | VARCHAR(100) | 参考单号 |
| created_by | VARCHAR(64) | 创建人 |
| created_at | TIMESTAMP | 创建时间 |

#### finance_bank_balance_snapshot — 银行余额快照

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| balance | DECIMAL(15,2) | 余额 |
| operator | VARCHAR | 操作人 |
| remark | TEXT | 备注 |
| snapshot_at | TIMESTAMP | 快照时间 |
| created_at | TIMESTAMP | 创建时间 |

#### finance_middleware_usage — 中间件使用记录

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| middleware_id | BIGINT | FK → middleware_asset |
| caller_project_id | VARCHAR(64) | FK → sys_project |
| source_project_id | VARCHAR(64) | FK → sys_project |
| royalty_fee | DECIMAL(15,2) | 版税费用 |
| ledger_month | VARCHAR(7) | 账期 |
| clearing_sheet_id | BIGINT | FK → finance_clearing_sheet |
| created_at | TIMESTAMP | 创建时间 |

#### finance_venture_profile — 事业部配置

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project, UNIQUE |
| legacy_venture_id | BIGINT | UNIQUE, NOT NULL |
| display_name | VARCHAR | 显示名称 |
| legacy_stage | VARCHAR | 遗留阶段 |
| ledger_enabled | BOOLEAN | 账期启用 |
| source_system | VARCHAR | 来源系统 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### finance_venture_equity — 事业部股权

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| project_id | VARCHAR(64) | FK → sys_project |
| user_id | VARCHAR(64) | FK → sys_user |
| equity_ratio | DECIMAL(6,4) | 股权比例 |
| dividend_ratio | DECIMAL(6,4) | 分红比例 |
| role_code | VARCHAR | 角色代码 |
| effective_from | DATE | 生效日期起 |
| effective_to | DATE | 生效日期止 |
| is_active | BOOLEAN | 是否有效 |
| created_at | TIMESTAMP | 创建时间 |

#### finance_knowledge_document — 知识文档

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | PK |
| topic | VARCHAR | 主题 |
| source_table | VARCHAR | 来源表 |
| source_id | BIGINT | 来源ID |
| content | TEXT | 内容 |
| embedding_ref | VARCHAR | 向量引用 |
| active | BOOLEAN | 是否激活 |

---

## 五、枚举值列表 (Enums)

### 5.1 基础枚举

#### AccountDomain
```
FINANCE, ERP
```

#### BusinessRoleEnum
```
BUSINESS("商务"), DATA("数据工程师"), DEV("开发"),
ALGORITHM("算法"), PM("项目经理"), MANAGER("项目管理员")
```

#### ProjectTierEnum
```
S("S级"), A("A级"), B("B级"), C("C级"), N("N级")
```

#### FolderTypeEnum (双盲文件隔离)
```
A_MANAGER_ARCHIVE("Manager归档区"),
B_ENGINEER_WORK("工程师作业区")
```

#### DemoFileType
```
ENGINEERING("工程文件"), DEMO_FILE("Demo包"),
DESCRIPTION("描述文档"), FEASIBILITY("可行性验证材料")
```

### 5.2 项目状态枚举

#### ProjectStatus (项目流)
```
LEAD → BIDDING → INITIATED → TEAM_FORMATION → IMPLEMENTING → ACCEPTANCE → SETTLEMENT → COMPLETED
```

#### ProductStatus (产品流)
```
IDEA → PROMOTION → DEMO_EXECUTION → MEETING_DECISION → TESTING → LAUNCHED / SHELVED
```

#### ResearchStatus (科研流)
```
INIT → BLUEPRINT → EXPANSION → DESIGN → EXECUTION → EVALUATION → ARCHIVE / SHELVED
```

### 5.3 财务枚举

#### FinanceExpenseSubmissionType
```
PERSONAL_PROCUREMENT, PROJECT_TRAVEL_REIMBURSEMENT
```

#### FinanceExpenseSubmissionStatus
```
SUBMITTED (等更多值)
```

#### FinanceWalletTransactionType
```
DIVIDEND, ROYALTY, MIDDLEWARE_PROFIT,
PROMOTION_EXPENSE, WITHDRAWAL, ADJUSTMENT
```

#### FinanceCashFlowDirection
```
IN, OUT
```

#### FinanceAdjustmentDirection
```
DEBIT, CREDIT
```

#### FinanceDividendStatus
```
PENDING, CONFIRMED
```

#### FinanceClearingStatus
```
PENDING, CLEARED
```

#### FinanceBatchStatus
```
PENDING, RUNNING, COMPLETED, FAILED
```

---

## 六、部署规则 (Deployment Rules)

### 6.1 Docker Compose 服务配置

**文件位置**: `/home/a/zhangqi/workspace/ZKR/docker-compose.yml`

| 服务 | 镜像 | 容器名 | 端口映射 | 网络 | 依赖 |
|------|------|--------|---------|------|------|
| postgres | postgres:16-alpine | postgres | 无 | erp-internal | 无 |
| erp-backend | 127.0.0.1:5555/zhangqi_backend:v1.66 | zkr-erp-backend | 无 | erp-internal, runtime-default, rag-isolated | postgres |
| finance-rag-qdrant | qdrant/qdrant:v1.13.4 | finance-rag-qdrant | 36333:6333 | rag-isolated | 无 |
| finance-rag-redis | redis:7.4-alpine | finance-rag-redis | 36379:6379 | rag-isolated | 无 |
| finance-rag-api | build:./rag-service | finance-rag-api | 36817:8088 | rag-isolated | qdrant, redis |
| lab-erp-demo | 127.0.0.1:5555/zhangqi_frontend:v1.98 | zkr-lab-erp-demo | 8080:80 | runtime-default | 无 |

### 6.2 网络隔离策略

| 网络名称 | 驱动 | 用途 |
|---------|------|------|
| erp-internal | bridge | 后端与数据库通信 |
| rag-isolated | bridge | RAG 服务内部通信 |
| runtime-default | external | 前端访问后端 (已存在) |

### 6.3 数据持久化卷

| 卷名 | 用途 | 容器挂载点 |
|------|------|-----------|
| postgres-data | PostgreSQL 数据 | /var/lib/postgresql/data |
| backend-uploads | 后端上传文件 | /app/uploads |
| finance-rag-qdrant-data | Qdrant 向量数据 | /qdrant/storage |
| finance-rag-redis-data | Redis 持久化 | /data |
| finance-rag-cache | RAG 缓存 | /app/cache |

### 6.4 Nginx 代理配置

**文件位置**: `/home/a/zhangqi/workspace/ZKR/lab-erp-demo/nginx.conf`

```nginx
server {
    listen 80;
    client_max_body_size 200m;  # 最大上传 200MB

    location /api {
        proxy_pass http://zkr-erp-backend:8101;
    }

    location /downloads/ {
        alias /srv/public-downloads/;
        autoindex on;
    }

    location / {
        try_files $uri $uri/ /index.html;  # SPA fallback
    }
}
```

### 6.5 环境变量 (.env)

**文件位置**: `/home/a/zhangqi/workspace/ZKR/.env`

| 变量名 | 值示例 | 说明 |
|--------|--------|------|
| POSTGRES_DB | postgres | 数据库名 |
| POSTGRES_USER | postgres | 数据库用户 |
| POSTGRES_PASSWORD | postgres123 | 数据库密码 |
| SPRING_DATASOURCE_URL | jdbc:postgresql://postgres:5432/postgres | JDBC连接 |
| SPRING_JPA_HIBERNATE_DDL_AUTO | update | Hibernate自动DDL |
| JWT_SECRET | change-me... | JWT密钥 |
| APP_UPLOADS_DIR | /app/uploads | 上传目录 |
| FRONTEND_PUBLIC_PORT | 8080 | 前端公网端口 |
| PUBLIC_DOWNLOADS_DIR | /home/a/zhangqi/workspace/ZKR/public-downloads | 公共下载 |
| FINANCE_RAG_LLM_PROVIDER | openai | LLM提供商 |
| FINANCE_RAG_OPENAI_BASE_URL | https://api.yunxicode.online/v1 | OpenAI API |
| FINANCE_RAG_OPENAI_API_KEY | sk-... | OpenAI密钥 |
| FINANCE_RAG_OPENAI_MODEL | gpt-5.4 | OpenAI模型 |

### 6.6 镜像版本

| 服务 | 镜像 | 当前版本 |
|------|------|---------|
| erp-backend | 127.0.0.1:5555/zhangqi_backend | v1.66 |
| lab-erp-demo | 127.0.0.1:5555/zhangqi_frontend | v1.98 |

---

## 七、运行状态 (Runtime Status)

### 7.1 服务健康检查

```bash
# 后端健康检查
curl http://localhost:8080/api/auth/me

# RAG 服务健康检查
curl http://127.0.0.1:36817/health

# PostgreSQL 健康检查
docker exec <container_id> pg_isready -U postgres -d postgres

# Redis 健康检查
docker exec finance-rag-redis redis-cli ping
```

### 7.2 启动顺序

1. **postgres** (等待 health check)
2. **finance-rag-qdrant**
3. **finance-rag-redis**
4. **finance-rag-api** (依赖 qdrant + redis)
5. **erp-backend** (依赖 postgres)
6. **lab-erp-demo**

### 7.3 日志查看

```bash
docker logs zkr-erp-backend --tail 100 -f
docker logs zkr-lab-erp-demo --tail 100 -f
docker logs finance-rag-api --tail 100 -f
```

### 7.4 端口映射汇总

| 服务 | 容器端口 | 宿主机映射 | 本地访问 |
|------|---------|-----------|---------|
| erp-backend | 8101 | 无 | 通过前端代理 |
| lab-erp-demo | 80 | 8080 | http://localhost:8080 |
| finance-rag-api | 8088 | 36817 | http://127.0.0.1:36817 |
| finance-rag-qdrant | 6333 | 36333 | http://127.0.0.1:36333 |
| finance-rag-redis | 6379 | 36379 | localhost:36379 |
| postgres | 5432 | 无 | 通过 erp-internal |

---

## 八、Service 层完整列表

| 类名 | 路径 | 职责 |
|------|------|------|
| AuthService | service/ | 认证：登录、注册、密码重置 |
| UserService | service/ | 用户：查询、头像更新 |
| ProjectService | service/ | 项目：创建、详情、状态流转 |
| ProjectFlowService | service/ | 项目流：实施计划、双盲文件、结算 |
| ProductFlowService | service/ | 产品流：创意、推广、Demo、测试 |
| ResearchFlowService | service/ | 科研流：发起、评测入库 |
| ProjectFinancialMetricsService | service/ | 项目财务指标 |
| ProjectChatService | service/ | 项目聊天 |
| ProjectGitRepositoryService | service/ | Git仓库 |
| MiddlewareHubService | service/ | 中间件资产 |
| InternalMessageService | service/ | 内部消息 |
| FileService | service/ | 通用文件上传 |
| MailService | service/ | 邮件服务 |
| OcrService | service/ | OCR识别 |
| StateMachineService | service/ | 状态机 |
| UserBadgeService | service/ | 用户勋章 |
| CommandService | command/ | 命令执行 |
| FinanceExpenseSubmissionService | finance/service/ | 报销提交 |
| FinanceDividendService | finance/service/ | 分红 |
| FinanceAdjustmentService | finance/service/ | 调账 |
| FinanceClearingService | finance/service/ | 清分 |
| FinanceCostBatchService | finance/service/ | 成本批次 |
| FinanceBankBalanceService | finance/service/ | 银行余额 |
| FinanceReportingService | finance/service/ | 财务报表 |
| FinanceAiService | finance/service/ | 财务AI |
| FinanceRagService | finance/service/ | RAG知识库 |
| FinanceAiContextService | finance/service/ | AI上下文 |
| FinanceReferenceService | finance/service/ | 参考数据 |
| RbacService | security/ | RBAC权限 |
| JwtAuthenticationFilter | security/ | JWT过滤器 |
| CustomUserDetailsService | security/ | 用户详情 |

---

*文档生成时间: 2026-04-14*
*扫描路径: /home/a/zhangqi/workspace/ZKR/*
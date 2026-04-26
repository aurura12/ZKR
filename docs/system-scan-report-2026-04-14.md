# ZKR 系统深度扫描文档

> 生成日期：2026-04-14
> 项目路径：/home/a/zhangqi/workspace/ZKR/

---

## 一、项目架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                         ZKR 系统架构                                  │
├──────────────────────────────────────────────────────────────────────┤
│  前端 (lab-erp-demo)                                                 │
│  - 技术栈：Vue 3 + Vite + Axios                                      │
│  - 端口：8080 (宿主机映射)                                           │
│  - 容器名：zkr-lab-erp-demo                                          │
│  - 镜像：127.0.0.1:5555/zhangqi_frontend:v1.98                       │
│  - Nginx 代理：/api → http://zkr-erp-backend:8101                    │
├──────────────────────────────────────────────────────────────────────┤
│  后端 (erp-backend)                                                  │
│  - 技术栈：Spring Boot 3.2.0 + Java 17 + JPA + PostgreSQL           │
│  - 端口：8101 (容器内)                                               │
│  - 容器名：zkr-erp-backend                                           │
│  - 镜像：127.0.0.1:5555/zhangqi_backend:v1.66                        │
│  - 数据库：PostgreSQL 16 (postgres:5432)                             │
├──────────────────────────────────────────────────────────────────────┤
│  RAG 服务 (rag-service)                                              │
│  - 技术栈：Python 3.12 + FastAPI + Qdrant + Redis                    │
│  - 端口：8088 (容器内映射到 127.0.0.1:36817)                         │
│  - 容器名：finance-rag-api                                           │
│  - 依赖：finance-rag-qdrant (36333), finance-rag-redis (36379)      │
├──────────────────────────────────────────────────────────────────────┤
│  数据存储                                                            │
│  - PostgreSQL：postgres-data volume                                  │
│  - Qdrant：finance-rag-qdrant-data volume                           │
│  - Redis：finance-rag-redis-data volume                              │
│  - 上传文件：backend-uploads volume → /app/uploads                   │
│  - 公共下载：/home/a/zhangqi/workspace/ZKR/public-downloads         │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 二、前端接口 (Frontend APIs)

前端源码位置：`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/api/finance/`

### 2.1 submissions.js — 报销提交中心

| 导出函数 | HTTP方法 | 请求路径 | 请求参数 | 响应数据 | 说明 |
|---------|---------|---------|---------|---------|------|
| `getFinanceSubmissionCenter` | GET | `/api/finance/submissions` | 无 | `FinanceExpenseSubmissionCenterResponse` | 获取报销提交列表 |
| `downloadFinanceSubmissionInvoice` | GET | `/api/finance/submissions/{submissionId}/invoice` | `submissionId` (路径参数) | `Blob` (文件流) | 下载报销发票 |

### 2.2 overview.js — 财务概览

| 导出函数 | HTTP方法 | 请求路径 | 请求参数 | 响应数据 | 说明 |
|---------|---------|---------|---------|---------|------|
| `getFinanceOverview` | GET | `/api/finance/statements` | `params` (可选查询参数) | `FinanceStatementsResponse` | 获取财务报表 |
| `getFinanceDashboard` | GET | `/api/projects/dashboard` 或 `/api/finance/statements` | 无 | 聚合后的 Dashboard 结构 | 获取仪表盘数据，fallback 到 statements |

### 2.3 wallets.js — 钱包账户

| 导出函数 | HTTP方法 | 请求路径 | 请求参数 | 响应数据 | 说明 |
|---------|---------|---------|---------|---------|------|
| `getFinanceWallets` | GET | `/api/finance/wallets` | `params` (可选) | `FinanceWalletOverviewResponse` | 获取钱包总览 |
| `getFinanceTransactions` | GET | `/api/finance/transactions` | `params` (含 normalize 转换) | `FinanceTransactionListResponse` | 获取钱包交易记录 |
| `saveBankBalance` | POST | `/api/finance/bank_balance` | `payload` (normalize 后) | 操作结果 | 保存银行余额快照 |

### 2.4 workbench.js — 财务工作台

| 导出函数 | HTTP方法 | 请求路径 | 请求参数 | 响应数据 | 说明 |
|---------|---------|---------|---------|---------|------|
| `runCostBatch` | POST | `/api/batch/run_cost` | `payload` (`FinanceLedgerMonthRequest`) | 批次执行结果 | 运行指定月份的成本分摊 |
| `getCostBatchPreview` | GET | `/api/batch/preview/{ventureId}` | `ventureId` (路径), `ledgerMonth` (query) | 成本预览数据 | 预览成本分摊 |
| `getClearingVentures` | GET | `/api/clearing/ventures` | `params` (可选) | 清分 Venture 列表 | 获取可清分的事业部 |
| `executeClearing` | POST | `/api/clearing/execute` | `payload` (`FinanceClearingExecuteRequest`) | 清分执行结果 | 执行财务清分 |
| `prepareDividendSheet` | POST | `/api/dividend/prepare` | `payload` (含 projectId) | 分红清单准备结果 | 准备分红清单 |
| `getDividendSheets` | GET | `/api/dividend/list` | `params` (含 projectId, status) | 分红清单列表 | 获取分红清单 |
| `confirmDividendSheet` | POST | `/api/dividend/confirm` | `payload` (含 projectId) | 分红确认结果 | 确认分红 |
| `createAdjustment` | POST | `/api/adjustment/create` | `payload` (`FinanceAdjustmentCreateRequest`) | 调账创建结果 | 创建调账记录 |
| `getAdjustmentLogs` | GET | `/api/adjustment/list` | `params` (含 userId) | 调账日志列表 | 获取调账记录 |

### 2.5 ai.js — 财务AI

| 导出函数 | HTTP方法 | 请求路径 | 请求参数 | 响应数据 | 说明 |
|---------|---------|---------|---------|---------|------|
| `rebuildFinanceRag` | POST | `/api/rag/push` | `payload` | RAG 重建结果 | 重建 RAG 索引 |
| `queryFinanceRag` | POST | `/api/rag/query` | `payload` (`FinanceRagQueryRequest`) | RAG 查询结果 | 查询 RAG 知识库 |
| `chatWithFinanceAi` | POST | `/api/ai/chat` | `payload` (`FinanceAiChatRequest`) | AI 聊天结果 | 与财务 AI 对话 |

### 2.6 前端请求工具 (request.js)

位置：`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/utils/request.js`

```javascript
// Axios 实例配置
baseURL: '',           // 相对路径，通过 nginx 代理
timeout: 120000        // 120秒超时

// Token 管理
- active_auth_scope: 'ERP' | 'FINANCE' | null
- erp_token: ERP 域 JWT token
- finance_token: FINANCE 域 JWT token

// 请求拦截器
- 认证请求 (/api/auth/login, /api/auth/register): 不携带 Authorization
- 其他请求: 携带 Bearer Token

// 响应拦截器
- 直接返回 response.data，自动脱壳
```

---

## 三、后端接口 (Backend APIs)

后端源码位置：`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/`

技术栈：Spring Boot 3.2.0 | Java 17 | Spring Security + JWT | Spring Data JPA | PostgreSQL

### 3.1 统一响应结构 — FinanceApiResponse

```java
// 泛型响应包装器
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

### 3.2 认证接口 — AuthController

路径：`/api/auth`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| POST | `/login` | `LoginRequest {username, password}` | `{token: string, ...}` | 用户登录 |
| POST | `/register` | `RegisterRequest {username, password, email, domain}` | `"注册成功，请登录"` | 用户注册 |
| POST | `/change-password` | `ChangePasswordRequest {oldPassword, newPassword}` | `{message: "密码修改成功"}` | 修改密码 |
| GET | `/me` | 无 | `User` 对象 | 获取当前用户 |
| POST | `/logout` | 无 | 204 No Content | 登出 |

### 3.3 财务报表接口 — FinanceController

路径：`/api/finance`

| 方法 | 路径 | 查询参数 | 响应 | 说明 |
|-----|------|---------|------|------|
| GET | `/statements` | 无 | `FinanceStatementsResponse` | 获取财务报表 |
| GET | `/wallets` | 无 | `FinanceWalletOverviewResponse` | 获取钱包总览 |
| GET | `/transactions` | `limit`, `userId`, `type`, `direction`, `sourceTable` | `FinanceTransactionListResponse` | 获取交易记录 |
| GET | `/submissions` | 无 | `FinanceExpenseSubmissionCenterResponse` | 获取报销提交列表 |
| GET | `/submissions/{submissionId}/invoice` | 无 | `ByteArrayResource` (文件流) | 下载发票 |

### 3.4 财务工作台接口 — FinanceWorkbenchController

路径：`/api/batch`, `/api/clearing`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| POST | `/api/batch/run_cost` | `FinanceLedgerMonthRequest {ledgerMonth, rerunExistingMonth}` | 执行结果 | 运行成本分摊批次 |
| GET | `/api/batch/preview/{ventureId}` | query: `ledgerMonth` | 预览数据 | 预览成本分摊 |
| GET | `/api/clearing/ventures` | 无 | 清分事业部列表 | 获取可清分的事业部 |
| POST | `/api/clearing/execute` | `FinanceClearingExecuteRequest {ventureId, finalRevenue}` | 清分结果 | 执行清分 |

### 3.5 分红接口 — FinanceDividendController

路径：`/api/dividend`

| 方法 | 路径 | 参数 | 请求体 | 响应 | 说明 |
|-----|------|------|--------|------|------|
| POST | `/prepare` | 无 | `FinanceDividendPrepareRequest {projectId}` | `FinanceDividendPrepareResponse` | 准备分红清单 |
| GET | `/list` | query: `projectId`, `status` | 无 | `FinanceDividendListResponse` | 获取分红清单列表 |
| POST | `/confirm` | 无 | `FinanceDividendConfirmRequest {projectId, ...}` | `FinanceDividendConfirmResponse` | 确认分红 |

### 3.6 调账接口 — FinanceAdjustmentController

路径：`/api/adjustment`

| 方法 | 路径 | 参数 | 请求体 | 响应 | 说明 |
|-----|------|------|--------|------|------|
| POST | `/create` | 无 | `FinanceAdjustmentCreateRequest {userId, subject, direction, amount, remark, reason, refDocNo}` | `FinanceAdjustmentCreateResponse` | 创建调账 |
| GET | `/list` | query: `userId` | 无 | `FinanceAdjustmentListResponse` | 获取调账日志 |

### 3.7 财务AI接口 — FinanceAiController

路径：`/api/ai`, `/api/rag`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| POST | `/api/ai/chat` | `FinanceAiChatRequest {message, clearHistory}` | `FinanceAiChatResponse` | AI 对话 |
| POST | `/api/rag/query` | `FinanceRagQueryRequest {prompt, limit}` | `FinanceRagQueryResponse` | RAG 查询 |
| POST | `/api/rag/push` | 无 | `FinanceRagPushResponse` | 重建 RAG 索引 |

### 3.8 项目接口 — ProjectController

路径：`/api/projects`

| 方法 | 路径 | 参数 | 请求体 | 响应 | 说明 |
|-----|------|------|--------|------|------|
| GET | `/` | 无 | 无 | `List<SysProject>` | 获取参与项目列表 |
| GET | `/managed` | 无 | 无 | `List<SysProject>` | 获取管理项目 |
| GET | `/managed/summary` | 无 | 无 | `ManagedProjectsSummaryResponse` | 管理项目汇总 |
| GET | `/dashboard` | 无 | 无 | `FinanceDashboardResponse` | 财务仪表盘 |
| GET | `/workspace` | 无 | 无 | `List<SysProject>` | 工作区侧边栏 |
| GET | `/{id}` | 路径: `id` | 无 | `ProjectDetailResponse` | 项目详情 |
| GET | `/{id}/earnings/me` | 路径: `id` | 无 | `ProjectMemberEarningsResponse` | 我的项目收益 |
| POST | `/` | 无 | `CreateProjectRequest` | `SysProject` | 创建项目 |
| PUT | `/{id}/critical-task` | 路径: `id` | `{critical_task: string}` | 204 | 更新关键任务 |
| POST | `/{id}/assets` | 路径: `id`; form: `file`, `assetCategory` | Multipart | 上传资产文件 |
| POST | `/{id}/travel-reimbursements` | 路径: `id`; form: 多字段 | Multipart | 提交出差报销 |
| GET | `/{id}/assets/{assetId}/download` | 路径: `id`, `assetId` | 无 | `ByteArrayResource` | 下载资产 |
| POST | `/{id}/milestones` | 路径: `id` | `{title, dueDate}` | 201 | 添加里程碑 |
| POST | `/{id}/subtasks` | 路径: `id` | `ProjectSubtaskRequest` | `ProjectSubtaskResponse` | 创建子任务 |
| PUT | `/{id}/subtasks/{subtaskId}` | 路径: `id`, `subtaskId` | `ProjectSubtaskRequest` | `ProjectSubtaskResponse` | 更新子任务 |
| POST | `/{id}/subtasks/{subtaskId}/complete` | 路径: `id`, `subtaskId` | 无 | `{success: true}` | 完成子任务 |
| PUT | `/{id}/project-status` | query: `status` | 无 | `{success: true, newStatus}` | 更新项目状态 |
| PUT | `/{id}/product-status` | query: `status` | 无 | `{success: true, newStatus}` | 更新产品状态 |
| DELETE | `/{id}` | 路径: `id` | 无 | 204 | 删除项目 |

### 3.9 项目流接口 — ProjectFlowController

路径：`/api/projects`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| POST | `/initiate` | `ProjectInitiateRequestDTO` | `SysProject` | 商务发起项目 |
| POST | `/{projectId}/build-team` | `ProjectBuildTeamRequestDTO` | `SysProject` | 组建团队 |
| POST | `/{projectId}/execution/plan` | `ExecutionPlanRequestDTO` | 执行计划结果 | 设定实施计划 |
| PATCH | `/{projectId}/execution/team-members` | `ProductMemberUpdateRequest` | 团队成员更新结果 | 更新执行团队成员 |
| GET | `/{projectId}/task-assignments` | 无 | `List<ProjectTaskAssignmentDTO>` | 获取任务分配 |
| PUT | `/{projectId}/task-assignments` | `ProjectTaskAssignmentUpdateRequest` | 更新结果 | 更新任务分配 |
| PATCH | `/{projectId}/implementation-status` | `{status: string}` | 更新结果 | 更新实施状态 |
| PATCH | `/{projectId}/dynamic-info` | `ProjectDynamicInfoUpdateRequest` | 动态信息更新结果 | 更新项目动态信息 |
| GET | `/{projectId}/execution/overview` | 无 | `ExecutionOverviewResponseDTO` | 获取执行总览 |
| PATCH | `/{projectId}/execution/schedules/{userId}/confirm` | `{confirmed: boolean}` | 确认结果 | 确认成员排期 |
| POST | `/{projectId}/execution/upload` | multipart: `file`, `folderType`, `secondaryCategory` | 上传结果 | 双盲文件上传 |
| GET | `/{projectId}/execution/files/{fileId}/download` | 无 | `ByteArrayResource` | 下载执行文件 |
| DELETE | `/{projectId}/execution/files/{fileId}` | 无 | 204 | 删除执行文件 |
| POST | `/{projectId}/execution/archive-folders` | `{parentPath, folderName}` | 创建结果 | 创建归档文件夹 |
| PATCH | `/{projectId}/execution/files/{fileId}/archive-folder` | `{targetFolderPath}` | 移动结果 | 移动文件到归档 |
| PATCH | `/{projectId}/execution/files/{fileId}/category` | `{secondaryCategory}` | `{success: true}` | 重分类文件 |
| POST | `/{projectId}/settlement/complete` | multipart: `contractVoucherFile` | 结算结果 | OCR结算完结 |

### 3.10 RAG Service API (Python FastAPI)

路径：`/health`, `/api/index`, `/api/query`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|-----|------|--------|------|------|
| GET | `/health` | 无 | 健康状态对象 | 服务健康检查 |
| POST | `/api/index` | `{contextBlocks: [{title, content, sourceType, sourceKey}]}` | `{indexName, status, documentCount, message}` | 重建 RAG 索引 |
| POST | `/api/query` | `{prompt: string, limit?: number, contextBlocks?: []}` | `{answer: string, dataRows: [...]}` | RAG 查询 |

---

## 四、数据库字段 (Database Schema)

数据库：PostgreSQL 16 | JPA Hibernate 自动管理 | DDL_AUTO: update

### 4.1 sys_user — 用户表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| user_id | VARCHAR(64) | PK | 用户ID (UUID字符串) |
| username | VARCHAR | UNIQUE, NOT NULL | 用户名 |
| password_hash | VARCHAR | NOT NULL | 密码哈希 |
| name | VARCHAR | | 姓名 |
| email | VARCHAR | | 邮箱 |
| role | VARCHAR | | 角色 (RESEARCH等) |
| avatar | VARCHAR | | 头像URL |
| hidden_avatar | BOOLEAN | NOT NULL DEFAULT false | 隐藏头像 |
| account_domain | VARCHAR(32) | DEFAULT 'ERP' | 账号域 (ERP/FINANCE) |
| is_active | BOOLEAN | DEFAULT true | 是否激活 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

### 4.2 sys_project — 系统项目表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| project_id | VARCHAR(64) | PK | 项目ID (UUID) |
| name | VARCHAR(100) | NOT NULL | 项目名称 |
| description | TEXT | | 项目描述 |
| project_type | VARCHAR(20) | NOT NULL | 项目类型枚举 |
| flow_type | VARCHAR(20) | NOT NULL | 流程类型 (PROJECT/PRODUCT/RESEARCH) |
| project_status | VARCHAR(20) | | 项目流状态 |
| product_status | VARCHAR(20) | | 产品流状态 |
| research_status | VARCHAR(30) | | 科研流状态 |
| manager_id | VARCHAR(64) | FK → sys_user | 负责人 |
| budget | DECIMAL(15,2) | | 预算 |
| estimated_revenue | DECIMAL(19,4) | | 预计收入 |
| feasibility_report_url | VARCHAR | | 可行性报告URL |
| project_tier | VARCHAR(10) | | 项目评级 |
| cost | DECIMAL(15,2) | DEFAULT 0 | 成本 |
| tech_stack | VARCHAR | | 技术栈 |
| repo_url | VARCHAR | | 仓库URL |
| deploy_url | VARCHAR | | 部署URL |
| ocr_timestamp | TIMESTAMP | | OCR时间戳 |
| ocr_work_hours | INTEGER | | OCR工时 |
| settlement_proof_url | VARCHAR | | 结算凭证URL |
| start_date | TIMESTAMP | | 开始日期 |
| end_date | TIMESTAMP | | 结束日期 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

### 4.3 sys_project_member — 项目成员表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| project_id | VARCHAR(64) | NOT NULL | 项目ID |
| user_id | VARCHAR(64) | FK → sys_user | 用户ID |
| role | VARCHAR(20) | DEFAULT 'MEMBER' | 角色 (MEMBER/ADMIN/VIEWER) |
| weight | INTEGER | DEFAULT 0 | 权重 |
| manager_weight | INTEGER | DEFAULT 0 | 经理权重 |
| joined_at | TIMESTAMP | | 加入时间 |

**唯一约束**: (project_id, user_id)

### 4.4 finance_wallet_account — 钱包账户表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| user_id | VARCHAR(64) | FK → sys_user, UNIQUE | 用户ID |
| balance | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 当前余额 |
| total_dividend_earned | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 累计分红 |
| total_royalty_earned | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 累计版税 |
| total_middleware_profit | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 累计中间件利润 |
| total_promotion_expense | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 累计推广费用 |
| total_adjustment_amount | DECIMAL(15,2) | NOT NULL DEFAULT 0 | 累计调账金额 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

**索引**: idx_finance_wallet_user (user_id)

### 4.5 finance_wallet_transaction — 钱包交易表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| wallet_id | BIGINT | FK → finance_wallet_account, NOT NULL | 钱包ID |
| transaction_type | VARCHAR(20) | NOT NULL | 交易类型枚举 |
| cash_flow_direction | VARCHAR(10) | NOT NULL | 资金流向 (IN/OUT) |
| amount | DECIMAL(15,2) | NOT NULL | 金额 |
| balance_after | DECIMAL(15,2) | NOT NULL | 交易后余额 |
| project_id | VARCHAR(64) | FK → sys_project | 项目ID |
| source_table | VARCHAR(100) | | 来源表 |
| source_id | BIGINT | | 来源ID |
| remark | TEXT | | 备注 |
| created_at | TIMESTAMP | | 创建时间 |

**索引**: idx_finance_wallet_transaction_wallet, idx_finance_wallet_transaction_project

### 4.6 finance_expense_submission — 报销提交表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| submission_type | VARCHAR(50) | NOT NULL | 提交类型 (TRAVEL等) |
| status | VARCHAR(30) | NOT NULL | 状态枚举 |
| submitter_user_id | VARCHAR(64) | NOT NULL | 提交人ID |
| submitter_name | VARCHAR(100) | NOT NULL | 提交人姓名 |
| project_id | VARCHAR(64) | | 项目ID |
| project_name | VARCHAR(150) | | 项目名称 |
| project_flow_type | VARCHAR(30) | | 项目流程类型 |
| item_name | VARCHAR(200) | NOT NULL | 物品名称 |
| item_category | VARCHAR(100) | | 物品类别 |
| item_specification | VARCHAR(200) | | 物品规格 |
| quantity | INTEGER | | 数量 |
| unit_price | DECIMAL(15,2) | | 单价 |
| total_amount | DECIMAL(15,2) | NOT NULL | 总金额 |
| supplier_name | VARCHAR(150) | | 供应商名称 |
| invoice_number | VARCHAR(100) | NOT NULL | 发票号码 |
| occurred_at | TIMESTAMP | | 发生时间 |
| purpose | TEXT | NOT NULL | 用途 |
| remarks | TEXT | | 备注 |
| departure_location | VARCHAR(120) | | 出发地 |
| destination_location | VARCHAR(120) | | 目的地 |
| travel_start_at | TIMESTAMP | | 出行开始 |
| travel_end_at | TIMESTAMP | | 出行结束 |
| invoice_file_name | VARCHAR(255) | NOT NULL | 发票文件名 |
| invoice_file_path | VARCHAR(500) | NOT NULL | 发票文件路径 |
| invoice_content_type | VARCHAR(255) | | 发票Content-Type |
| invoice_file_size | BIGINT | | 发票文件大小 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

**索引**: idx_finance_expense_submission_type, idx_finance_expense_submission_project, idx_finance_expense_submission_submitter, idx_finance_expense_submission_created_at

### 4.7 finance_cost_batch — 成本分摊批次表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| ledger_month | VARCHAR(7) | NOT NULL | 账期 (YYYY-MM) |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | 批次状态 |
| generated_record_count | INTEGER | DEFAULT 0 | 生成记录数 |
| operator_user_id | VARCHAR(64) | | 操作人ID |
| batch_date | DATE | | 批次日期 |
| started_at | TIMESTAMP | | 开始时间 |
| completed_at | TIMESTAMP | | 完成时间 |
| remark | TEXT | | 备注 |

**索引**: idx_finance_cost_batch_month

### 4.8 finance_cost_entry — 成本分摊明细表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| batch_id | BIGINT | FK → finance_cost_batch, NOT NULL | 批次ID |
| project_id | VARCHAR(64) | FK → sys_project, NOT NULL | 项目ID |
| user_id | VARCHAR(64) | FK → sys_user | 用户ID |
| ledger_month | VARCHAR(7) | NOT NULL | 账期 |
| work_hours | DECIMAL(10,2) | | 工时 |
| labor_cost | DECIMAL(15,2) | | 人工成本 |
| middleware_royalty_fee | DECIMAL(15,2) | | 中间件版税 |
| final_settlement_cost | DECIMAL(15,2) | | 最终结算成本 |
| source_table | VARCHAR(100) | | 来源表 |
| source_id | BIGINT | | 来源ID |
| created_at | TIMESTAMP | | 创建时间 |

**索引**: idx_finance_cost_entry_batch_project, idx_finance_cost_entry_month

### 4.9 finance_dividend_sheet — 分红清单表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| project_id | VARCHAR(64) | FK → sys_project, NOT NULL | 项目ID |
| user_id | VARCHAR(64) | FK → sys_user, NOT NULL | 用户ID |
| ledger_month | VARCHAR(7) | | 账期 |
| amount | DECIMAL(15,2) | NOT NULL | 分红金额 |
| dividend_ratio | DECIMAL(6,4) | | 分红比例 |
| net_profit_snapshot | DECIMAL(15,2) | | 净利润快照 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | 状态 |
| confirmed_at | TIMESTAMP | | 确认时间 |
| confirmed_by | VARCHAR(64) | | 确认人 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

**索引**: idx_finance_dividend_project_status, idx_finance_dividend_user_status

### 4.10 finance_clearing_sheet — 清分表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| project_id | VARCHAR(64) | FK → sys_project, NOT NULL | 项目ID |
| ledger_month | VARCHAR(7) | NOT NULL | 账期 |
| final_revenue | DECIMAL(15,2) | | 最终收入 |
| total_cost | DECIMAL(15,2) | | 总成本 |
| net_profit | DECIMAL(15,2) | | 净利润 |
| middleware_fee | DECIMAL(15,2) | | 中间件费用 |
| carry_forward_loss | DECIMAL(15,2) | | 结转亏损 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | 状态 |
| cleared_at | TIMESTAMP | | 清分时间 |
| cleared_by | VARCHAR(64) | | 清分人 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

**索引**: idx_finance_clearing_project_month

### 4.11 finance_adjustment_log — 调账日志表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| wallet_id | BIGINT | FK → finance_wallet_account, NOT NULL | 钱包ID |
| user_id | VARCHAR(64) | FK → sys_user, NOT NULL | 用户ID |
| direction | VARCHAR(10) | NOT NULL | 方向 (DEBIT/CREDIT) |
| amount | DECIMAL(15,2) | NOT NULL | 金额 |
| reason | TEXT | | 原因 |
| source_table | VARCHAR(100) | | 来源表 |
| source_id | BIGINT | | 来源ID |
| ref_doc_no | VARCHAR(100) | | 参考单据号 |
| created_by | VARCHAR(64) | | 创建人 |
| created_at | TIMESTAMP | | 创建时间 |

**索引**: idx_finance_adjustment_user_created

### 4.12 finance_bank_balance_snapshot — 银行余额快照表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| balance | DECIMAL(15,2) | NOT NULL | 余额 |
| operator | VARCHAR(100) | NOT NULL | 操作人 |
| remark | TEXT | | 备注 |
| snapshot_at | TIMESTAMP | NOT NULL | 快照时间 |
| created_at | TIMESTAMP | | 创建时间 |

### 4.13 finance_venture_profile — 事业部档案表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| project_id | VARCHAR(64) | FK → sys_project, UNIQUE | 项目ID |
| legacy_venture_id | BIGINT | UNIQUE NOT NULL | 遗留事业部ID |
| display_name | VARCHAR(200) | NOT NULL | 显示名称 |
| legacy_stage | VARCHAR(100) | | 遗留阶段 |
| ledger_enabled | BOOLEAN | NOT NULL DEFAULT true | 账期启用 |
| source_system | VARCHAR(50) | | 来源系统 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

**索引**: idx_finance_venture_profile_project, idx_finance_venture_profile_legacy

### 4.14 finance_venture_equity — 事业部股权表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| project_id | VARCHAR(64) | FK → sys_project, NOT NULL | 项目ID |
| user_id | VARCHAR(64) | FK → sys_user, NOT NULL | 用户ID |
| equity_ratio | DECIMAL(6,4) | NOT NULL | 股权比例 |
| dividend_ratio | DECIMAL(6,4) | | 分红比例 |
| role_code | VARCHAR(50) | | 角色代码 |
| effective_from | DATE | | 生效日期起 |
| effective_to | DATE | | 生效日期止 |
| is_active | BOOLEAN | NOT NULL DEFAULT true | 是否激活 |
| created_at | TIMESTAMP | | 创建时间 |

**索引**: idx_finance_equity_project_user

---

## 五、部署规则 (Deployment Rules)

### 5.1 Docker Compose 服务配置

**文件位置**: `/home/a/zhangqi/workspace/ZKR/docker-compose.yml`

#### 5.1.1 postgres — PostgreSQL 数据库

```yaml
image: postgres:16-alpine
container_name: zkr-postgres  # 注意：实际名为 postgres (无前缀)
networks: erp-internal
volumes: postgres-data:/var/lib/postgresql/data
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres -d postgres"]
  interval: 10s / timeout: 5s / retries: 5
```

#### 5.1.2 erp-backend — Spring Boot 后端

```yaml
image: 127.0.0.1:5555/zhangqi_backend:v1.66
container_name: zkr-erp-backend
networks: erp-internal, runtime-default, rag-isolated
depends_on: postgres (condition: service_healthy)
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/postgres
  SPRING_JPA_HIBERNATE_DDL_AUTO: update  # 重要：自动更新表结构
  JWT_SECRET: <from .env>
  APP_UPLOADS_DIR: /app/uploads
  FINANCE_RAG_BASE_URL: http://finance-rag-api:8088
ports: (无宿主机端口映射，通过 nginx 代理)
volumes: backend-uploads:/app/uploads
restart: unless-stopped
```

#### 5.1.3 finance-rag-qdrant — 向量数据库

```yaml
image: qdrant/qdrant:v1.13.4
container_name: finance-rag-qdrant
networks: rag-isolated
ports: 127.0.0.1:36333:6333  # 仅本地访问
volumes: finance-rag-qdrant-data:/qdrant/storage
```

#### 5.1.4 finance-rag-redis — 缓存

```yaml
image: redis:7.4-alpine
container_name: finance-rag-redis
networks: rag-isolated
ports: 127.0.0.1:36379:6379  # 仅本地访问
command: ["redis-server", "--appendonly", "yes"]
volumes: finance-rag-redis-data:/data
```

#### 5.1.5 finance-rag-api — Python RAG 服务

```yaml
build: ./rag-service
container_name: finance-rag-api
networks: rag-isolated
ports: 127.0.0.1:36817:8088  # 仅本地访问
depends_on: finance-rag-qdrant, finance-rag-redis
environment:
  QDRANT_URL: http://finance-rag-qdrant:6333
  REDIS_URL: redis://finance-rag-redis:6379/0
  LLM_PROVIDER: openai
  EMBEDDING_PROVIDER: ollama
  OLLAMA_BASE_URL: http://host.docker.internal:11434
  OPENAI_BASE_URL: <from .env>
  OPENAI_API_KEY: <from .env>
  INDEX_NAME: finance-rag-index
volumes: finance-rag-cache:/app/cache
extra_hosts: host.docker.internal:host-gateway
```

#### 5.1.6 lab-erp-demo — Vue 前端

```yaml
image: 127.0.0.1:5555/zhangqi_frontend:v1.98
container_name: zkr-lab-erp-demo
networks: runtime-default
ports: "${FRONTEND_PUBLIC_PORT}:80"  # 8080:80
volumes:
  ./lab-erp-demo/nginx.conf:/etc/nginx/conf.d/default.conf:ro
  ${PUBLIC_DOWNLOADS_DIR}:/srv/public-downloads:ro
restart: unless-stopped
```

### 5.2 网络隔离策略

| 网络名称 | 驱动 | 用途 |
|---------|------|------|
| erp-internal | bridge | 后端与数据库通信 |
| rag-isolated | bridge | RAG 服务内部通信 |
| runtime-default | external | 前端访问后端 (已存在) |

**网络隔离规则**:
- `erp-internal`: 后端+数据库+RAG API 可访问
- `rag-isolated`: 仅 RAG 相关服务 (Qdrant/Redis/RAG API) 可访问
- `runtime-default`: 前端和后端共享网络，前端通过 nginx 反向代理访问后端

### 5.3 数据持久化卷

| 卷名 | 用途 | 容器挂载点 |
|------|------|-----------|
| postgres-data | PostgreSQL 数据 | /var/lib/postgresql/data |
| backend-uploads | 后端上传文件 | /app/uploads |
| finance-rag-qdrant-data | Qdrant 向量数据 | /qdrant/storage |
| finance-rag-redis-data | Redis 持久化 | /data |
| finance-rag-cache | RAG 缓存 | /app/cache |

### 5.4 Nginx 代理配置

**文件位置**: `/home/a/zhangqi/workspace/ZKR/lab-erp-demo/nginx.conf`

```nginx
server {
    listen 80;
    client_max_body_size 200m;  # 最大上传 200MB
    
    location /api {
        proxy_pass http://zkr-erp-backend:8101;  # 反向代理到后端
        proxy_http_version 1.1;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    location /downloads/ {
        alias /srv/public-downloads/;  # 公共下载目录
        autoindex on;
        limit_except GET HEAD { deny all; }
    }
    
    location / {
        try_files $uri $uri/ /index.html;  # SPA fallback
    }
}
```

### 5.5 后端 Dockerfile

**文件位置**: `/home/a/zhangqi/workspace/ZKR/erp-backend/Dockerfile`

```dockerfile
# 多阶段构建
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml . && mvn dependency:go-offline
COPY src ./src && mvn package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV APP_UPLOADS_DIR=/app/uploads
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser \
    && mkdir -p /app/uploads && chown -R appuser:appgroup /app
COPY --from=build /app/target/*.jar /app/app.jar
USER appuser
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 5.6 RAG 服务 Dockerfile

**文件位置**: `/home/a/zhangqi/workspace/ZKR/rag-service/Dockerfile`

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt . && pip install --no-cache-dir -r requirements.txt
COPY app.py ./
EXPOSE 8088
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8088"]
```

**Python 依赖**:
- fastapi==0.115.12
- uvicorn==0.34.0
- qdrant-client==1.14.2
- redis==5.2.1
- requests==2.32.3
- pydantic==2.10.6

### 5.7 环境变量 (.env)

**文件位置**: `/home/a/zhangqi/workspace/ZKR/.env`

| 变量名 | 值示例 | 说明 |
|--------|--------|------|
| POSTGRES_DB | postgres | 数据库名 |
| POSTGRES_USER | postgres | 数据库用户 |
| POSTGRES_PASSWORD | postgres123 | 数据库密码 |
| SPRING_DATASOURCE_URL | jdbc:postgresql://postgres:5432/postgres | JDBC连接字符串 |
| SPRING_JPA_HIBERNATE_DDL_AUTO | update | Hibernate自动DDL |
| JWT_SECRET | change-me-to-a-long-random-secret | JWT密钥 |
| APP_UPLOADS_DIR | /app/uploads | 上传目录 |
| FRONTEND_PUBLIC_PORT | 8080 | 前端公网端口 |
| PUBLIC_DOWNLOADS_DIR | /home/a/zhangqi/workspace/ZKR/public-downloads | 公共下载目录 |
| FINANCE_RAG_LLM_PROVIDER | openai | LLM提供商 |
| FINANCE_RAG_EMBEDDING_PROVIDER | ollama | Embedding提供商 |
| FINANCE_RAG_OPENAI_BASE_URL | https://api.yunxicode.online/v1 | OpenAI兼容API |
| FINANCE_RAG_OPENAI_API_KEY | sk-... | OpenAI API密钥 |
| FINANCE_RAG_OPENAI_MODEL | gpt-5.4 | OpenAI模型 |

### 5.8 镜像版本管理

| 服务 | 镜像 | 当前版本 |
|------|------|---------|
| erp-backend | 127.0.0.1:5555/zhangqi_backend | v1.66 |
| lab-erp-demo | 127.0.0.1:5555/zhangqi_frontend | v1.98 |

**版本更新脚本**: `/home/a/zhangqi/workspace/ZKR/scripts/next_image_version.py`

---

## 六、运行状态 (Runtime Status)

### 6.1 服务健康检查

#### 后端健康检查
```bash
curl http://localhost:8080/api/auth/me
# 或直接
curl http://zkr-erp-backend:8101/actuator/health  # 如果启用actuator
```

#### RAG 服务健康检查
```bash
curl http://127.0.0.1:36817/health
```

响应示例:
```json
{
  "status": "ok",
  "indexName": "finance-rag-index",
  "collectionExists": true,
  "llmProvider": "openai",
  "embeddingProvider": "ollama",
  "ollamaBaseUrl": "http://host.docker.internal:11434",
  "openaiBaseUrl": "https://api.yunxicode.online/v1",
  "model": "gpt-5.4",
  "embeddingModel": "qwen3-embedding:4b"
}
```

#### PostgreSQL 健康检查
```bash
docker exec <container_id> pg_isready -U postgres -d postgres
```

#### Redis 健康检查
```bash
docker exec finance-rag-redis redis-cli ping
# 响应: PONG
```

### 6.2 启动顺序

1. **postgres** (等待 health check 通过)
2. **finance-rag-qdrant** (无依赖)
3. **finance-rag-redis** (无依赖)
4. **finance-rag-api** (依赖 qdrant + redis)
5. **erp-backend** (依赖 postgres health)
6. **lab-erp-demo** (无依赖，可直接启动)

### 6.3 日志查看

```bash
# 后端日志
docker logs zkr-erp-backend --tail 100 -f

# 前端/nginx日志
docker logs zkr-lab-erp-demo --tail 100 -f

# RAG API日志
docker logs finance-rag-api --tail 100 -f

# Qdrant日志
docker logs finance-rag-qdrant --tail 100 -f
```

### 6.4 常见运维命令

```bash
# 重启后端
docker restart zkr-erp-backend

# 重启 RAG 服务
docker restart finance-rag-api

# 进入后端容器
docker exec -it zkr-erp-backend /bin/bash

# 数据库连接 (从宿主机)
psql postgresql://postgres:postgres123@localhost:5432/postgres

# 查看所有容器状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 查看卷使用
docker volume inspect zkr_postgres-data
```

### 6.5 端口映射汇总

| 服务 | 容器内端口 | 宿主机映射 | 本地访问地址 |
|------|-----------|-----------|-------------|
| erp-backend | 8101 | 无 | 通过前端代理访问 |
| lab-erp-demo | 80 | 8080 | http://localhost:8080 |
| finance-rag-api | 8088 | 36817 | http://127.0.0.1:36817 |
| finance-rag-qdrant | 6333 | 36333 | http://127.0.0.1:36333 |
| finance-rag-redis | 6379 | 36379 | localhost:36379 |
| postgres | 5432 | 无 | 通过 erp-internal 网络 |

---

## 七、关键枚举说明

### 7.1 财务相关枚举

| 枚举类 | 枚举值 | 说明 |
|--------|--------|------|
| `FinanceWalletTransactionType` | DIVIDEND, ROYALTY, PROMOTION, ADJUSTMENT, REFUND, ... | 钱包交易类型 |
| `FinanceCashFlowDirection` | IN, OUT | 资金流向 |
| `FinanceExpenseSubmissionStatus` | PENDING, APPROVED, REJECTED, REIMBURSED, ... | 报销状态 |
| `FinanceExpenseSubmissionType` | TRAVEL, PROJECT, PRODUCT, RESEARCH, ... | 报销类型 |
| `FinanceDividendStatus` | PENDING, CONFIRMED, PAID, CANCELLED | 分红状态 |
| `FinanceClearingStatus` | PENDING, PROCESSING, COMPLETED, FAILED | 清分状态 |
| `FinanceAdjustmentDirection` | DEBIT, CREDIT | 调账方向 |
| `FinanceBatchStatus` | PENDING, RUNNING, COMPLETED, FAILED | 批次状态 |

### 7.2 项目流相关枚举

| 枚举类 | 枚举值 | 说明 |
|--------|--------|------|
| `ProjectStatus` | LEAD, TEAM_FORMATION, IMPLEMENTING, SETTLEMENT, COMPLETED, CANCELLED | 项目流状态 |
| `ProductStatus` | IDEA, PROMOTION, DEMO_EXECUTION, MEETING_DECISION, TESTING, LAUNCHED, SHELVED | 产品流状态 |
| `ResearchStatus` | INIT, BLUEPRINT, EXPANSION, DESIGN, EXECUTION, EVALUATION, ARCHIVE, SHELVED | 科研流状态 |
| `FlowType` | PROJECT, PRODUCT, RESEARCH | 流程类型 |
| `ProjectTierEnum` | S, A, B, C, N | 项目评级 |
| `FolderTypeEnum` | A_MANAGER_ARCHIVE, B_ENGINEER_WORK | 双盲文件夹类型 |
| `AccountDomain` | ERP, FINANCE | 账号域 |

---

*文档生成时间: 2026-04-14*
*扫描路径: /home/a/zhangqi/workspace/ZKR/*
# ZKR ERP System

容器化部署的 ERP 系统，包含前端（Vue 3）、后端（Spring Boot）和 RAG 服务（Python）。

**当前版本：** `zhangqi_backend:v1.149` / `zhangqi_frontend:v1.169`

## 最近变更

### 2026-07-10 16:14 — 修复成本跑批 0607 以来为 0 的根因

**原因：** 考勤表 `attendance_record.user_name` 与系统用户表 `sys_user.name` 存在格式差异导致全量匹配失败：
- 系统用户名含 `_实习` 后缀（如 `刘浩洋_实习`），考勤名为纯名（如 `刘浩洋`）→ 39 人无法匹配
- 考勤名偶有 `主机位` 后缀（如 `刘忠益主机位`）和 `MM-DD-` 日期前缀（如 `02-22-赵翌池`）

**改动位置：**
- `erp-backend/.../finance/service/FinanceCostBatchService.java:518-525,534,793-803` — 新增 `normalizeSysUserName()`（去 `_实习`）和 `normalizeAttendUserName()`（去 `主机位`、`\d{2}-\d{2}-`）方法；name→userId 映射与考勤查找两处均做归一化

**效果：** 归一化后匹配率从 ~40% 升至 94%（缺 `郭健雯`/`孙鑫` 两名无系统记录用户），跑批恢复正常产出

### 2026-07-10 16:01 — 侧边栏瘦身：跑批日志弹窗化 + AI/审计合并

**原因：** Finance 侧边栏 14 个条目过于琐碎，跑批日志仅 70 行却独占一个路由。

**改动位置：**
- `lab-erp-demo/src/views/finance/BatchControlView.vue` — 新增「查看日志」按钮 + el-dialog 弹窗，内嵌跑批执行记录卡片列表
- `lab-erp-demo/src/views/finance/FinanceAiHub.vue` — **新建**，双 tab 包装器：全局检索 | 智能助手
- `lab-erp-demo/src/views/finance/FinanceAuditHub.vue` — **新建**，双 tab 包装器：手工调账 | 成本调整日志
- `lab-erp-demo/src/router/financeRoutes.js` — 删除 5 个 navItem（调账、全局业务检索、全局业务助手、成本调整日志、跑批日志），新增 2 个（AI 业务、审计）

**效果：** 侧边栏 14→11 条目；跑批日志在跑批控制页内一键查看；AI 和审计各聚合为一个入口

### 2026-07-10 15:50 — 项目文件管理器全面修复和功能增强

**原因：** 文件系统只能浏览目录，无法选中文件，下载按钮名存实亡，缺少上传/预览/删除功能。

**改动位置：**
- `erp-backend/.../projectfile/ProjectFileSourceType.java` — 新增 `UPLOADED_FILE` 枚举值
- `erp-backend/.../projectfile/ProjectFileManagerService.java:161-241` — 新增 `uploadFile`、`deleteFile`、`downloadUploadedFile`、`getMimeType` 方法
- `erp-backend/.../projectfile/ProjectFileManagerController.java:75-127` — 修复下载 Content-Disposition 双编码 bug；新增 `previewFile`（inline + 正确 MIME 类型）、`uploadFile`（multipart）、`deleteFile` 端点
- `lab-erp-demo/src/views/ProjectFileManagerView.vue` — 修复文件节点不可点击（替换未定义 CSS 变量 `--science-blue-soft`）；新增预览/上传/删除按钮和对应逻辑；修复 `link.download` 空文件名
- `lab-erp-demo/src/components/ProjectFileTree.vue` — 文件单点改为预览打开新标签页，hover/active 状态修复

**效果：** 文件节点可点击选中并高亮显示；双击预览（新标签页 inline 展示）；工具栏支持下载/预览/移动/删除/上传完整操作；上传文件保存到 `uploads/project-files/{projectId}/` 目录。

**原因：** `App.vue` 通过 ref 调用子组件 submit 方法，但三个子组件均使用 `<script setup>` 默认闭包，未 `defineExpose`，父组件调用时 `confirmCreate`/`submit` 为 `undefined`，静默跳过无效果。

**改动位置：**
- `lab-erp-demo/src/App.vue:164-171,238-250` — 添加模板 ref + `submitLaunchForm` 委托逻辑
- `lab-erp-demo/src/views/CreateProject.vue:136` — `defineExpose({ confirmCreate })`
- `lab-erp-demo/src/views/CreateDeliveryProjectView.vue:197` — `defineExpose({ submit })`
- `lab-erp-demo/src/views/CreateResearchView.vue:146` — `defineExpose({ submit })`

**效果：** 发起弹窗点击确认后正确调用对应 API，提交成功后弹窗自动关闭。

### 2026-07-08 15:45 — 修复发起弹窗确认按钮无响应（第一版）

### 2026-07-08 10:37 — 统一发起按钮 + 会议自动定时强提醒

**原因：** 三个独立发起按钮（产品/项目/科研）分散在不同位置，入口不统一；会议系统缺少参会提醒。

**改动位置：**
- `erp-backend/.../db/migration/V20260707_001__add_meeting_reminder.sql` — **新建** meeting_record 加 last_reminded_at
- `erp-backend/.../meeting/entity/MeetingRecord.java:62-63` — 新增 lastRemindedAt 字段
- `erp-backend/.../meeting/repository/MeetingRecordRepository.java:27-29` — 新增查询：SCHEDULED + 15分钟内 + 未提醒
- `erp-backend/.../meeting/scheduler/MeetingReminderScheduler.java` — **新建** @Scheduled(cron="0 * * * * *") 每分钟扫描即将开始的会议，通过 InternalMessageService 向所有参会人发送 MEETING_REMINDER 强提醒
- `lab-erp-demo/.../App.vue:43-44,162-180,188-220,290-306,635-654` — 替换「发起产品」为统一样式「发起」按钮（圆角矩形，#0066cc）；新增发起弹窗 + el-tabs（产品/项目/科研三个 tab 按权限显示）；新增会议提醒 toast
- `lab-erp-demo/.../CreateProject.vue` — 新增 embedded prop + submitted emit，嵌入式表单跳过外层遮罩
- `lab-erp-demo/.../CreateDeliveryProjectView.vue` — 同上
- `lab-erp-demo/.../CreateResearchView.vue` — 同上
- `lab-erp-demo/.../ManagerDashboard.vue:38-59` — 移除独立的「发起项目」「发起科研」按钮

**效果：**
- 导航栏统一「发起」按钮代替分散的三个按钮，点击弹窗按权限显示 tab（产品→所有人，项目→BUSINESS，科研→RESEARCH/白名单）
- 三个 tab 直接嵌入创建表单，提交后自动关闭弹窗
- 会议开始前 15 分钟自动向所有参会人发送站内消息（MEETING_REMINDER），前端 toast 提示「⏰ 会议即将开始」
- ManagerDashboard 按钮精简为会议中心/队长管理/队长工作台

**原因：** Windows 创建的 ZIP 文件名使用 GBK 编码，`ZipInputStream` 默认 UTF-8 解码失败抛 `MalformedInputException` → 全局异常处理器返回「系统内部错误」，用户无法定位问题。

**改动位置：**
- `erp-backend/.../service/ReimbursementZipService.java:95-135` — `process()` 新增 `extractZipWithFallback()`：UTF-8 解压失败自动回退 GBK 编码重试；GBK 也失败时抛「请使用 UTF-8 格式压缩文件（推荐 7-Zip 重新打包）」的明确提示
- `erp-backend/.../service/ProjectService.java:2093-2099` — ZIP 处理异常捕获从 `BusinessException` 扩展为 `Exception`，确保所有错误都包装为中文提示

**效果：** UTF-8/GBK 双编码自动兼容；编码问题有明确修复指引而非「系统内部错误」。

**原因：** 报销 Excel 模板下载入口隐藏在 `.zip-hint` 提示区，不够醒目。将模板下载按钮提升到底部操作栏，永远可见。

**改动位置：**
- `lab-erp-demo/.../ExpenseSubmissionForm.vue:100-107` — 模板下载按钮从 `.zip-hint` 内移到 `.footer-row` 操作栏最左侧（与提交按钮、提示文案同行）

**效果：** 报销表单底部始终显示「下载报销模板」按钮，无论是否选择发票文件均可见。

### 2026-07-06 10:24 — 阶段3：合同台账建表与合同 OCR 识别入账

**原因：** 合同（`ProjectExpense` 类型 `EXTERNAL_SERVICE`）审批通过后需 OCR 扫描合同文件（PDF/DOCX/图片），提取 23 个结构化字段写入合同台账，并与发票台账通过 `contract_no` 关联。

**改动位置：**
- `erp-backend/.../db/migration/V20260703_002__create_contract_ledger.sql` — **新建** Flyway 迁移，创建 `contract_ledger` 表（23 业务列 + OCR 审计列 + 3 索引）
- `erp-backend/.../entity/ContractLedger.java` — **新建** JPA 实体
- `erp-backend/.../repository/ContractLedgerRepository.java` — **新建** JPA 仓储
- `erp-backend/.../ocr/ContractOcrService.java` — **新建** `@Async @Transactional`：读取合同文件 → 调 `OcrClient.recognizeContract()` → 回填 23 字段 → 写 `contract_ledger`
- `erp-backend/.../ocr/ContractOcrResult.java` — **新建** 合同 OCR 结果 DTO（23 业务字段 + 置信度 + 错误信息）
- `erp-backend/.../ocr/OcrClient.java:88-149` — 新增 `recognizeContract()` 方法，`POST /ocr/contract`；重构 `buildRestTemplate()` 公共方法
- `erp-backend/.../service/ProjectService.java:133,2187-2192` — 注入 `ContractOcrService`；`reviewExpense()` 中 APPROVED 时按类型分流：`EXTERNAL_SERVICE` → 合同 OCR，其他 → 发票 OCR
- `erp-backend/.../controller/ProjectController.java:300` — `submitProjectExpense()` 新增 `counterparty` 可选参数
- `erp-backend/.../dto/SubmitProjectExpenseRequest.java` — 新增 `counterparty` 字段
- `paddle-ocr/Dockerfile` — 新增 `poppler-utils` 系统包 + `pdf2image python-docx PyPDF2` Python 包
- `paddle-ocr/requirements.txt` — 新增 `pdf2image python-docx PyPDF2`
- `paddle-ocr/app.py:220-360` — 新增 `POST /ocr/contract` 端点；文件类型自动分流（图片→PaddleOCR，PDF→PyPDF2/pdf2image，DOCX→python-docx）；`_parse_contract_fields()` 正则提取 23 字段
- `lab-erp-demo/.../ProjectDetail.vue:1442-1444,1562,3002,3011` — 合同对话框新增「合同对方名称」字段；`expenseForm` 新增 `counterparty`；提交 FormData 携带 `counterparty`

**效果：**
- 审批通过时按 `expenseType` 自动分流：合同（`EXTERNAL_SERVICE`）触发 `ContractOcrService` 写入 `contract_ledger`，其他类型触发 `InvoiceOcrService` 写入 `invoice_ledger`
- 合同 OCR 支持 PDF（含扫描件 OCR 回退）、DOCX（文本直提）、图片格式
- 23 字段由正则提取 + PaddleOCR 文字识别自动填入
- 发票台账通过 `contract_no` 与合同台账关联（1:N）
- 合同对话框新增「合同对方名称」字段供用户手动补充

### 2026-07-03 16:59 — 阶段2：PaddleOCR 引擎集成与异步发票识别入账

**原因：** 审批通过的报销需自动 OCR 扫描发票图片，提取金额/发票号/日期/往来单位等字段回填发票台账，并进行 Excel 与 OCR 的金额交叉校验。

**改动位置：**
- `paddle-ocr/Dockerfile` — **新建** 基于 `paddlepaddle/paddleocr:2.8.1`，安装 FastAPI + uvicorn，内部端口 18952（不暴露宿主机）
- `paddle-ocr/requirements.txt` — **新建** Python 依赖
- `paddle-ocr/app.py` — **新建** FastAPI OCR 服务：`GET /health` 健康检查 + `POST /ocr/invoice` 发票识别，PaddleOCR 中文模型 + 正则提取发票号码/日期/金额/税额/税率/购销方/标的物
- `docker-compose.yml:155-168` — 新增 `erp-paddle-ocr` 服务（`restart: unless-stopped`，仅加入 `erp-internal` 网络，无 host 端口映射，外部不可见）
- `erp-backend/.../ocr/OcrClient.java` — **新建** RestTemplate HTTP 客户端，调用 `POST http://erp-paddle-ocr:18952/ocr/invoice`，返回 `OcrInvoiceResult`
- `erp-backend/.../ocr/OcrProperties.java` — **新建** `@ConfigurationProperties("erp.ocr")`，默认 `baseUrl=http://erp-paddle-ocr:18952`
- `erp-backend/.../ocr/OcrInvoiceResult.java` — **新建** OCR 结果 DTO（发票号/日期/金额/税额/税率/购销方/标的物/置信度）
- `erp-backend/.../ocr/InvoiceOcrService.java` — **新建** `@Async @Transactional triggerOcr(expenseId)`：遍历 `invoice_ledger` PENDING 行 → 读图片 → 调 OCR → 回填字段 → 交叉校验 → `verified_status = MATCH/MISMATCH`
- `erp-backend/.../service/ProjectService.java:131,2184-2187` — 注入 `InvoiceOcrService`；`reviewExpense()` 中 APPROVED 后调用 `invoiceOcrService.triggerOcr()` 异步触发 OCR

**效果：**
- 报销三级审批全部通过（状态变 APPROVED）→ 异步触发 PaddleOCR 扫描所有关联发票图片
- OCR 提取的字段（发票号码/不含税金额/税额/税率/含税金额/日期/往来单位/购方抬头/摘要）自动回填 `invoice_ledger`
- `data_source` 更新为 `EXCEL+OCR`，`ocr_status → DONE`，`ocr_raw_json` 保存完整 OCR 原始返回
- Excel 金额与 OCR 金额交叉校验：不含税+税额 vs 含税 → `verified_status = MATCH/MISMATCH`
- PaddleOCR 容器无宿主机端口映射，仅 `erp-internal` 网络内可见

### 2026-07-03 16:52 — 阶段1：报销 ZIP 解压与发票台账建表

**原因：** 用户通过 ZIP 压缩包提交项目报销（命名规范 `姓名+项目+金额.zip`），内含发票图片和汇总 Excel。需要解包入库并写入发票台账流水表，为后续 OCR 入账做准备。

**改动位置：**
- `erp-backend/.../db/migration/V20260703_001__create_invoice_ledger.sql` — **新建** Flyway 迁移，创建 `invoice_ledger` 审计流水表（19 业务列 + 7 审计列 + 3 索引）
- `erp-backend/.../entity/InvoiceLedger.java` — **新建** JPA 实体，`seq_no` BIGSERIAL 全局流水主键
- `erp-backend/.../repository/InvoiceLedgerRepository.java` — **新建** Spring Data JPA 仓储
- `erp-backend/.../service/ReimbursementZipService.java` — **新建** 核心服务：ZIP 解压、文件名解析 `姓名+项目+金额.zip`、POI 解析 Excel 汇总表（7 列模板）、发票图片 SHA256 哈希、写入 `invoice_ledger` 流水行、`generateTemplateExcel()` 生成 Excel 模板
- `erp-backend/.../service/ProjectService.java:130` — 注入 `ReimbursementZipService`；`submitProjectExpense()` 先保存 expense 再处理文件；检测 `.zip` 后缀走解包流程写入 `invoice_ledger`；检测 `.rar` 暂作普通附件存储
- `erp-backend/.../controller/ProjectController.java:50,359-369` — 注入 `ReimbursementZipService`；新增 `GET /api/projects/expenses/reimbursement-template` 端点，下载 7 列 Excel 模板
- `lab-erp-demo/.../ExpenseSubmissionForm.vue:101-105,292-305,407-422` — 新增 ZIP 命名规范提示文案（`姓名+项目+金额.zip`）；新增「下载 Excel 模板」按钮及 `downloadTemplate()` 方法；新增 `.zip-hint` 样式

**效果：**
- ZIP 上传时自动解压，Excel 数据写入 `invoice_ledger`（`data_source=EXCEL, ocr_status=PENDING`）
- 发票图片落盘并计算 SHA256 哈希
- ZIP 文件名解析出提交人/项目名/总金额，写入发票行
- 用户可通过前端下载模板填入后打包上传

**已知占位（预留后续阶段）：**
- `ocr_status` 始终 `PENDING`，审批通过后异步 OCR 机制待阶段 2 实现
- `tracking_no` 自动生成格式待实现
- `company`/`company_code` 由 OCR 从发票抬头提取，待阶段 2 填充
- `contract_ledger` 合同台账表待阶段 3 建表

### 2026-07-02 10:43 — 员工管理模块新增离职/复职/创建操作日志

**原因：** 员工离职和复职操作无审计记录，无法追溯操作历史；`User` 实体虽有 `departureDate` 字段但从未写入。

**改动位置：**
- `erp-backend/.../entity/UserStatusLog.java` — **新建** JPA 实体，映射 `user_status_log` 表（user_id, action, operator_id, created_at）
- `erp-backend/.../repository/UserStatusLogRepository.java` — **新建** Repository
- `erp-backend/.../db/migration/V20260702_001__create_user_status_log.sql` — **新建** Flyway 迁移脚本
- `erp-backend/.../service/UserService.java:110-111,131-132` — `deactivateUser()` 写入 `departureDate = LocalDate.now()`；`activateUser()` 写入 `departureDate = null`
- `erp-backend/.../controller/AdminUserController.java:65-70,100-113,121-125` — `provisionUser()` 写入 CREATE 日志；`deactivateUser()` 写入 DEACTIVATE 日志；`activateUser()` 写入 ACTIVE 日志；新增 `GET /{userId}/status-history` 查询接口

**效果：** 每次创建账号、离职、复职均硬记录到 `user_status_log`，后端管理员可通过 API 或数据库直接查询任意用户的操作历史。

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

# Agents Instructions

## 版本号规则

每次构建镜像前，必须先读取当前运行中的容器镜像版本号，新版本 = 当前版本号 + 1：

```bash
# 查看当前运行版本
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
```

| 规则 | 说明 |
|------|------|
| 版本号递增 | 后端从 `v1.XX` → `v1.XX+1`，前端从 `v1.XX` → `v1.XX+1` |
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

---

## 已知 Bug 记录

### 2026-06-22：ERP 账号创建模块集成协议生成功能并接入大模型自然语言识别

**更新：** 在 ERP 账号创建流程中集成 outside 协议生成能力，创建账号后可直接生成三份实习协议 Word 文档并下载；同时接入 OpenCode AI DeepSeek V4 Pro，支持从自然语言文本中自动识别账号信息并填充表单。

**涉及改动：**
- 后端新增 `school_department`、`address` 字段，`agreement_template` 表存储三份协议模板。
- 新增 `AgreementGenerationService` / `AgreementZipService`，使用 Apache POI 重写 outside Python 脚本逻辑，生成 `互联网实习生协议`、`实习生协议`、`实习证明` 三份 `.docx`。
- 新增/扩展接口：
  - `POST /api/admin/users/{userId}/agreement?type=...`（单份生成并保存）
  - `POST /api/admin/users/{userId}/agreements/batch`（勾选多份，打包 zip 下载）
  - `GET/PUT /api/admin/agreement-templates/{code}`（模板管理）
  - `POST /api/admin/users/parse-natural-language`（自然语言解析为账号 JSON）
- 新增 `LlmClient` / `NaturalLanguageParserService`，调用 OpenCode AI `/v1/chat/completions` 提取结构化账号信息。
- 前端 `AdminCreateUserView.vue` 新增「学校院系」「住址」字段；新增自然语言输入框和「智能识别并填充」按钮；创建成功后弹出协议生成对话框。
- 部署镜像版本：`zhangqi_backend:v1.136`、`zhangqi_frontend:v1.160`。

**注意：**
- 默认协议模板通过 `AgreementTemplateInitializer` 在启动时从 classpath 自动写入数据库；后续可通过管理接口替换。
- 旧 `POST .../agreement` 生成 `.txt` 的行为已替换为生成 `.docx`。
- `.env` 中 LLM 配置已切换为 OpenCode AI：`ERP_LLM_BASE_URL`、`ERP_LLM_API_KEY`、`ERP_LLM_MODEL`。

### 2026-05-09：项目流发起时 dataEngineerId 传递了 "userId-ROLE" 导致后端查不到用户

**问题：** `CreateDeliveryProjectView.vue` 中数据工程师下拉框的 option value 用了 `"${u.userId}-${u.role}"` 格式（如 `"000010-DATA_ENGINEER"`），提交给后端 `/api/projects/initiate` 时后端直接用这个值查数据库 `userRepository.findById()`，查不到，报 "指定的数据工程师不存在"。

**修复：** 将 option `id` 改为纯 `String(u.userId || '')`，与后端数据库 userId 一致。

### 2026-05-09：项目流发起时数据工程师候选列表不完整，且组队时同一用户以 DATA/DATA_ENGINEER 两个角色重复出现

**问题 1：** `CreateDeliveryProjectView.vue` 从 `workflow_member_role` 表查 PROJECT 类型候选人，只返回已加入过项目的用户，系统中未参与过 PROJECT 的 DATA 用户不可见。

**修复 1：** 直接调用 `/api/users` 全量拉取，过滤 data 角色，并按 userId 归一化去重（DATA_ENGINEER 和 DATA 视为同一用户，只保留一条）。

**问题 2：** `ProjectDetail.vue` 的 `memberCandidates` 去重 key 为 `${userId}-${role}`，同一用户在 `workflow_member_role` 表中同时有 DATA 和 DATA_ENGINEER 记录时显示两行。

**修复 2：** `appendCandidate` 中 dedup 时将 `DATA_ENGINEER` 归一化为 `DATA`，使同一用户只出现一次。

### 2026-05-11：组队阶段数据工程师兼任 Manager 时权责比中不显示本人，且有幽灵成员

**问题：** `buildInitialTeamState()` 初始化 `teamMembers` 用纯 `userId`（如 `"000037"`），而 `memberCandidates` 中 entry.id 是 `${userId}-DATA` 格式。导致：
1. 数据工程师 Manager 本人在权责比列表中不可见
2. `submitBuildTeam` 中 `includes` 匹配失败，报错"请在团队成员列表中保留该数据工程师"
3. `selectedMemberDetails` 过滤时匹配不上，显示空 name 的幽灵成员

**修复：** `buildInitialTeamState()` 中将 `initialTeamMembers` 统一为 `${userId}-DATA` 格式与 `memberCandidates` 对齐；`submitBuildTeam` 中的 `includes` 改为 `startsWith(userId + '-')` 匹配。

### 2026-05-11：替换 Manager 后旧 Manager 的 managerWeight 未被清零

**问题：** `applyProjectResponsibilityAllocation()` 只更新新 Manager 的权重，不遍历所有成员清零旧 Manager 的 `manager_weight`。导致替换 Manager（如焦淼→李昊天）后，旧 Manager 的管理权责比仍然保留，权责比总和超过 100（如 125）。

**修复：** 在设置新 Manager 权重前，先将项目所有成员的 `manager_weight` 清零。

### 2026-05-16：项目流可行性报告上传按钮不可见

**问题：** `ProjectDetail.vue` 中 `canUploadProjectAsset` 计算属性（判断当前用户是否为被选中的数据工程师 + INITIATED 阶段）已正确实现，`triggerFileInput()` 函数和隐藏 `<input type="file">` 也已就绪，但模板中从未使用 `canUploadProjectAsset`，也从未调用 `triggerFileInput()`。导致数据工程师在 INITIATED 阶段看不到任何上传入口，无法通过 UI 上传可行性报告。

**修复：** 在 PROJECT 流的智能信息面板（`product-flow-grid`）中，可行性报告状态行新增「上传可行性报告」按钮，绑定 `v-if="canUploadProjectAsset"` 和 `@click="triggerFileInput"`，并添加 `.execution-row` 样式使按钮与状态文本同行显示。

### 2026-06-22：server-mgmt-api 容器网络隔离导致 ERP 后端无法访问

**问题：** `server-mgmt-api` 容器实际运行在 `server-mgmt_default` 网络（由 `/home/iiiioooo/Workspace/服务器管理/docker-compose.yml` 单独启动），而 `erp-backend` 在 `zkr_erp-internal` 网络。两者不在同一 Docker 网络，`ServerMgmtProxyController` 配置的目标地址 `http://server-mgmt-api:17000` 不可解析，导致前端「服务器管理」页面加载服务器/用户列表均失败。

**修复：** 停止旧独立实例，在 ZKR 项目根目录用 `docker compose up -d server-mgmt-api` 启动，使其自动加入 `zkr_erp-internal` 网络。迁移旧数据卷 `server-mgmt_server-mgmt-data` → `zkr_server-mgmt-data`。

### 2026-06-22：劳动关系资料模块 Finance 域无法访问 ERP 域 API

**问题：** `LaborRelationsView.vue` 前端页面注册在 Finance 路由 `/finance/labor-relations` 下，使用 `finance_token`，但它调用的 `/api/admin/users` 和 `/api/admin/users/users/{userId}/documents` 是 ERP 域接口（SecurityConfig 中 `/api/**` 要求 `requireErpDomain`），Finance token 被拦截返回 401。此外 `AdminUserController.getAllUsers()` 还要求 `requireProvisionAdmin()`（仅允许 guojianwen/jiaomiao/Zhangqi 等特定账号），普通 Finance 用户即使换 ERP token 也被拒绝。

**修复：** 新增 Finance 域接口：
- `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceLaborRelationsController.java` — `@RequestMapping("/api/finance/labor-relations")`，走 `requireFinanceDomain`，不要求 provision admin。
- `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceLaborRelationsService.java` — 复用原有文件存储逻辑，`GET /users` 直接返回带 `hasAgreement/hasIdCard/hasStudentCard` 标记的用户列表，消除前端 N+1 查询。
- 前端 `LaborRelationsView.vue` 将 `USERS_BASE` 和 `DOCS_BASE` 从 `/api/admin/users` 改为 `/api/finance/labor-relations/users`。`fetchUsers` 移除逐用户拉取文档的循环，改用后端预计算标记。

### 2026-06-22：服务器状态硬编码 'ok'，缺少真实 SSH 连通检测

**问题：** `ServerManagementView.vue` 中服务器状态硬编码为 `'ok'`，无法反映真实的 SSH 连通性。原 `outside/服务器管理` 项目有 `playbook-ping.yml` 可测试 SSH，但未暴露为 API。

**修复：**
- `server-mgmt/api/main.py` 新增 `GET /api/servers/status` 端点，执行 `ansible-playbook playbook-ping.yml` 并解析 PLAY RECAP，返回每台服务器 `ok/unreachable/auth_failed/unknown`。
- 新增 `server-mgmt/tests/test_status.py`（7 个单测）。
- `ServerManagementView.vue` 新增真实状态标签、刷新按钮、每次切换到「服务器」Tab 自动检测。

### 2026-06-23：项目文件管理器（Provision Admin 专用）

**功能：** 跨项目文件统一管理视图，用虚拟目录（folder + mapping）而非物理移动来组织文件。支持自动扫描、手动创建文件夹、移动文件、下载。整理效果同步至各项目详情页。

**设计方案：**
- 虚拟目录模型：`project_file_folder`（树结构，`parent_id` 自关联）+ `project_file_mapping`（记录每个文件所属虚拟文件夹和来源表）
- 数据来源覆盖 5 张表：`project_asset`、`execution_file`、`project_expense_file`、`finance_expense_submission`、`project_cost_adjustment`
- 启动时自动扫描已有文件，按来源类型创建默认系统文件夹（`项目资料`、`执行文件`、`费用报销`、`财务报销`、`成本调整`）

**涉及改动：**
- 后端新增 `erp-backend/src/main/java/com/smartlab/erp/projectfile/`：
  - `ProjectFileFolder` / `ProjectFileFolderRepository` — 虚拟文件夹实体
  - `ProjectFileMapping` / `ProjectFileMappingRepository` — 文件到虚拟文件夹的映射
  - `ProjectFileManagerController` — REST API（`/api/admin/project-files`）
  - `ProjectFileManagerService` — 核心逻辑（scan、tree、createFolder、deleteFolder、moveFile、download）
  - `ProjectFileMappingInitializer` — 启动时扫描已有文件并建立映射
  - `ProjectFileSourceType` — 文件来源类型枚举
- 前端新增：
  - `ProjectFileManagerView.vue` — 全局文件管理页面，路由 `/admin/project-files`
  - `ProjectFileTree.vue` — 可复用的只读文件树组件，用于 ProjectDetail 同步
  - `App.vue` 导航栏新增「📁 项目文件」快捷按钮，仅 Provision Admin 可见
  - `ProjectDetail.vue` 新增「项目文件目录」面板，读取虚拟树展示同步结果
- 部署镜像版本：`zhangqi_backend:v1.137`、`zhangqi_frontend:v1.161`。

**注意事项：**
- 权限控制：仅 Provision Admin（`Zhangqi`、`guojianwen`、`jiaomiao`）可访问全局文件管理器
- ProjectDetail 中的文件树面板同样仅管理员可见，普通成员不可见
- 移动文件操作调用 `PATCH /files/{mappingId}/move`，修改 `folder_id` 即完成虚拟移动
- `ProjectFileMapping` 不生成物理 ID（自增 Long id），由 `source_type` + `source_id` + `project_id` 唯一索引保证不重复映射

### 2026-06-24：项目文件管理器下载修复

**问题：** `ProjectFileTree.vue` 和 `ProjectFileManagerView.vue` 中文件下载使用 `window.open()` / `<a href>` 直接发送 GET 请求，未携带 JWT token，导致后端返回「当前账号未登录或登录已过期」。

**修复：** 改为 `request.get(url, { responseType: 'blob' })` 通过 axios 拦截器自动注入 Authorization header，获取 blob 后创建 ObjectURL 触发下载。

**部署版本：** `zhangqi_frontend:v1.162`。

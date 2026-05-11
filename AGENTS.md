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

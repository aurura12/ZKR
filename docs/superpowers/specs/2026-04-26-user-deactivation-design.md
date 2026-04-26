# 用户离职功能设计

**日期:** 2026-04-26  
**状态:** 已确认

---

## 需求概述

当用户离职时，ADMIN 可将该用户标记为离职状态。离职后：
- 用户无法登录
- 在成员管理界面添加人员时不可见
- 跑批不再为该用户产生新费用
- 历史项目中该用户仍然可见

## 操作权限

仅 ADMIN（Provision Admin）可操作。

---

## 实现方案

### 1. 新增离职接口

`POST /api/admin/users/{userId}/deactivate`

1. 校验当前操作者为 Provision Admin
2. 查找目标用户，设置 `active = false`，`save()`
3. 遍历该用户参与的所有活跃项目（`findParticipatedProjects`），对每个项目调用 `projectMemberParticipationService.recordLeave(projectId, userId, Instant.now())`
4. 同时从 `sys_project_member` 中删除该用户（`deleteByProjectIdAndUserUserId`），与现有 `removeProjectMember` 流程一致

### 2. 用户列表过滤 inactive

- `UserService.findAllUsers()` (no-arg): 增加 `.filter(user -> Boolean.TRUE.equals(user.getActive()))`
- `UserService.findAllUsers(AccountDomain)`: 同上
- 所有前端通过 `GET /api/users` 和 `GET /api/admin/users` 获取的用户列表自动排除离职用户

### 3. 跑批双重保险

`FinanceCostBatchService.ensureCurrentMemberHistories()` 同步当前成员到 history 时，跳过 `active=false` 的用户（防止离职操作和跑批并发的边界情况）。

### 4. 前端离职按钮

在 `WageManagementView.vue` 表格中增加一列"操作"，包含"离职"按钮，调用 `POST /api/admin/users/{userId}/deactivate`，刷新列表。

---

## 不新增的内容

- 不新增表/字段（复用 `User.active`）
- 不修改 `ProjectMemberParticipationHistory`（复用 `recordLeave`）
- 不修改项目详情页成员列表渲染（历史成员通过 `leftAt` 时间范围自然保留在历史中）

## 修改文件清单

| # | 文件 | 变更类型 |
|---|------|----------|
| 1 | `AdminUserController.java` | 新增 `POST /{userId}/deactivate` |
| 2 | `UserService.java` | `findAllUsers()` 加 active 过滤；新增 `deactivateUser()` |
| 3 | `FinanceCostBatchService.java` | `ensureCurrentMemberHistories` 跳过 inactive |
| 4 | `WageManagementView.vue` | 添加离职操作列 |

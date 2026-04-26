# 日工资输入框 + 工资管理模块 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在创建账号时显示日工资输入框，并添加一个仅 admin 可见的工资管理模块。

**Architecture:** `daily_wage` 字段已存在于 `User` 实体和数据库表中（默认值 300.00），但从未在 UI 或创建流程中暴露。本计划在前端创建表单中增加该输入框，在后端 DTO 及创建逻辑中透传该字段，并新增一个独立的工资管理页面（仅 admin 可见），展示所有用户的日工资并支持内联编辑。

**Tech Stack:** Vue 3 + Element Plus (前端), Spring Boot + JPA (后端)

---

### Task 1: 后端 - ProvisionUserRequest DTO 添加 dailyWage 字段

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/ProvisionUserRequest.java`

- [ ] **Step 1: 在 DTO 中添加 dailyWage 字段**

```java
package com.smartlab.erp.dto;

import com.smartlab.erp.enums.AccountDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProvisionUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "账号域不能为空")
    private AccountDomain domain;

    private BigDecimal dailyWage;
}
```

- [ ] **Step 2: 验证后端编译通过**

Run: `mvn compile -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml`

---

### Task 2: 后端 - AuthService.provisionUser 设置 dailyWage

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java:111-137`

- [ ] **Step 1: 在 User.builder() 调用中增加 dailyWage 设置**

Replace lines 122-131 in the provisionUser method:

```java
        BigDecimal wage = request.getDailyWage();
        if (wage == null || wage.compareTo(BigDecimal.ZERO) <= 0) {
            wage = new BigDecimal("300.00");
        }

        User user = User.builder()
                .userId(generateNextUserId())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(initialPassword))
                .name(request.getName().trim())
                .email(null)
                .role(normalizeRegisterRole(request.getRole()))
                .accountDomain(domain)
                .active(true)
                .dailyWage(wage)
                .build();
```

Specific edit: add the `BigDecimal wage = ...` block before `User user = User.builder()` and add `.dailyWage(wage)` to the builder chain.

- [ ] **Step 2: 验证后端编译通过**

Run: `mvn compile -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml`

---

### Task 3: 前端 - AdminCreateUserView 添加日工资输入框

**Files:**
- Modify: `lab-erp-demo/src/views/AdminCreateUserView.vue`

- [ ] **Step 1: 在表单 grid 中添加日工资输入框**

在 `<div class="field-block">` 角色选择框之后（第 37 行后）插入：

```html
        <div class="field-block">
          <label>日工资 (元/天)</label>
          <el-input-number v-model="form.dailyWage" :min="0" :precision="2" :step="10" placeholder="默认 300.00" />
        </div>
```

需要 import `ElInputNumber` if not already imported (Element Plus 通常全局注册，检查 `main.js`)。

- [ ] **Step 2: 在 form reactive 对象中添加 dailyWage 字段**

Replace the form definition (line 59-64):

```js
const form = reactive({
  username: '',
  name: '',
  role: '',
  domain: 'ERP',
  dailyWage: 300.00
})
```

- [ ] **Step 3: 在 handleSubmit 请求体中包含 dailyWage**

Replace the request body (line 84-89):

```js
    const response = await request.post('/api/admin/users/provision', {
      username: form.username.trim(),
      name: form.name.trim(),
      role: form.role,
      domain: form.domain,
      dailyWage: form.dailyWage
    })
```

- [ ] **Step 4: 重置表单时恢复 dailyWage 默认值**

在成功回调中（line 91-95），添加 `form.dailyWage = 300.00`:

```js
    form.username = ''
    form.name = ''
    form.role = ''
    form.domain = 'ERP'
    form.dailyWage = 300.00
```

- [ ] **Step 5: 验证前端编译通过**

Run: `npm run build --prefix /home/a/zhangqi/workspace/ZKR/lab-erp-demo`

---

### Task 4: 后端 - 添加用户列表和工资更新 API

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/dto/UpdateDailyWageRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AdminUserController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/UserService.java`

- [ ] **Step 1: 创建 UpdateDailyWageRequest DTO**

Create file `erp-backend/src/main/java/com/smartlab/erp/dto/UpdateDailyWageRequest.java`:

```java
package com.smartlab.erp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDailyWageRequest {
    @NotNull(message = "日工资不能为空")
    private BigDecimal dailyWage;
}
```

- [ ] **Step 2: 在 UserService 中添加 findAllUsers 和 updateDailyWage 方法**

在 `UserService.java` 中添加：

```java
    public List<User> findAllUsers() {
        return userRepository.findAll().stream()
                .peek(this::enrichUser)
                .toList();
    }

    @Transactional
    public void updateDailyWage(String userId, BigDecimal dailyWage) {
        if (dailyWage == null || dailyWage.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("日工资不能为负数");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        user.setDailyWage(dailyWage);
        userRepository.save(user);
    }
```

- [ ] **Step 3: 在 AdminUserController 中添加 GET /api/admin/users 和 PUT /api/admin/users/{userId}/daily-wage**

Replace `AdminUserController.java` content:

```java
package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.dto.UpdateDailyWageRequest;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.service.AuthService;
import com.smartlab.erp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @PostMapping("/provision")
    public ResponseEntity<Map<String, String>> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        authService.provisionUser(request);
        return ResponseEntity.ok(Map.of("message", "账号创建成功，初始密码为：账号+123"));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        requireProvisionAdmin();
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @PutMapping("/{userId}/daily-wage")
    public ResponseEntity<Map<String, String>> updateDailyWage(
            @PathVariable String userId,
            @Valid @RequestBody UpdateDailyWageRequest request) {
        requireProvisionAdmin();
        userService.updateDailyWage(userId, request.getDailyWage());
        return ResponseEntity.ok(Map.of("message", "日工资更新成功"));
    }
}
```

**注意:** `AuthService.getCurrentUser()` 和 `PermissionDeniedException` 是否已存在需确认——如果 `getCurrentUser()` 是 private 方法，需要将其改为 public 或将权限检查封装到其他方式。执行时如遇到编译错误，检查 `AuthService.java` 中 `getCurrentUser()` 的可见性。

- [ ] **Step 4: 验证后端编译通过**

Run: `mvn compile -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml`

---

### Task 5: 前端 - 创建 WageManagementView 工资管理页面

**Files:**
- Create: `lab-erp-demo/src/views/WageManagementView.vue`

- [ ] **Step 1: 创建工资管理页面**

Create file `lab-erp-demo/src/views/WageManagementView.vue`:

```vue
<template>
  <div class="wage-page">
    <div class="wage-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">ADMIN ONLY</div>
          <h1>工资管理</h1>
          <p class="subtitle">管理所有用户的日工资标准</p>
        </div>
        <el-tag type="primary">仅授权账号可见</el-tag>
      </div>

      <div class="table-wrapper">
        <el-table :data="users" stripe v-loading="loading" style="width: 100%">
          <el-table-column prop="userId" label="ID" width="100" />
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column prop="accountDomain" label="域" width="100" />
          <el-table-column label="日工资 (元/天)" min-width="200">
            <template #default="{ row }">
              <el-input-number
                v-model="row.dailyWage"
                :min="0"
                :precision="2"
                :step="10"
                size="small"
                :disabled="savingIds.has(row.userId)"
                @change="handleWageChange(row)"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const users = ref([])
const loading = ref(false)
const savingIds = ref(new Set())

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/users')
    users.value = Array.isArray(res) ? res.map(u => ({
      ...u,
      dailyWage: u.dailyWage != null ? Number(u.dailyWage) : 300
    })) : []
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleWageChange = async (row) => {
  if (row.dailyWage == null || row.dailyWage < 0) {
    ElMessage.warning('日工资不能为负数')
    return
  }

  savingIds.value.add(row.userId)
  try {
    await request.put(`/api/admin/users/${row.userId}/daily-wage`, {
      dailyWage: row.dailyWage
    })
    ElMessage.success(`${row.name} 日工资已更新为 ${row.dailyWage}`)
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
    fetchUsers()
  } finally {
    savingIds.value.delete(row.userId)
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.wage-page {
  min-height: calc(100vh - var(--nav-height));
  padding: 32px 20px;
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.06), transparent 240px), var(--science-canvas);
}

.wage-card {
  width: min(100%, 1000px);
  margin: 0 auto;
  padding: 32px;
  border-radius: 24px;
  border: 1px solid var(--border-soft);
  background: var(--science-surface);
  box-shadow: var(--shadow-md);
}

.header-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--science-blue);
  font-weight: 700;
}

h1 {
  margin: 8px 0 6px;
  color: var(--text-main);
}

.subtitle {
  margin: 0;
  color: var(--text-sub);
}

.table-wrapper {
  margin-top: 8px;
}
</style>
```

---

### Task 6: 前端 - 注册工资管理路由

**Files:**
- Modify: `lab-erp-demo/src/router/domainAccess.js:6`
- Modify: `lab-erp-demo/src/router/index.js`

- [ ] **Step 1: 在 domainAccess.js 中添加新路由前缀**

Replace line 6:
```js
const ERP_EXACT_PATHS = ['/admin/users/create', '/admin/wage-management']
```

- [ ] **Step 2: 在 router/index.js 中添加工资管理路由**

添加 import (在 LeaderManagementView import 之后):
```js
import WageManagementView from '../views/WageManagementView.vue'
```

在 AdminCreateUserView 路由之后添加 (第 134 行后):
```js
    {
      path: '/admin/wage-management',
      name: 'wage-management',
      component: WageManagementView,
      meta: {
        requiresAuth: true,
        requiresProvisionAdmin: true,
        routeDomain: DOMAIN_ERP
      }
    },
```

- [ ] **Step 3: 验证前端编译通过**

Run: `npm run build --prefix /home/a/zhangqi/workspace/ZKR/lab-erp-demo`

---

### Task 7: 集成验证

- [ ] **Step 1: 启动后端服务，验证 API 可用**

Run backend and test with curl:
```bash
# 测试获取所有用户
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/users
# 测试更新日工资
curl -X PUT -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"dailyWage": 350.00}' http://localhost:8080/api/admin/users/000001/daily-wage
```

- [ ] **Step 2: 前端验证**

- 以 admin 账号登录，访问 `/admin/users/create`，确认日工资输入框显示
- 创建账号时填入日工资，验证后端正确保存
- 访问 `/admin/wage-management`，确认显示所有用户及其日工资
- 修改某个用户的日工资，确认刷新后值保持

---

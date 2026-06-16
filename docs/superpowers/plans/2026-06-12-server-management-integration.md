# 服务器管理集成到 ERP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 outside/服务器管理 的 FastAPI+Ansible 服务集成到 ERP 系统，新增「服务器管理」菜单入口，通过 `is_server_ops_admin` 字段控制访问权限。

**Architecture:** ERP 后端新增代理 Controller 转发 `/api/server-mgmt/**` 到 Docker 内部服务 `server-mgmt-api:17000`，前端新增三 Tab 管理页面。

**Tech Stack:** Java/Spring Boot (RestTemplate proxy), Vue3/Element Plus, Python/FastAPI (existing), Docker Compose

---

### Task 1: 数据库迁移 + User 实体加字段

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`

- [ ] **Step 1: 在 User.java 中新增 `serverOpsAdmin` 字段**

在 `dailyWage` 字段之后、`badges` 字段之前，插入以下代码：

```java
@Column(name = "is_server_ops_admin")
@Builder.Default
private Boolean serverOpsAdmin = false;
```

完整插入位置在 `User.java` 第 71 行之后（`dailyWage` 行之后）：

```
70:     @Column(name = "daily_wage", nullable = false, precision = 10, scale = 2)
71:     @Builder.Default
72:     private BigDecimal dailyWage = new BigDecimal("300.00");
  →  在这里插入新字段，badges 之前
73:     @Transient
74:     @Builder.Default
75:     private List<UserBadge> badges = new ArrayList<>();
```

- [ ] **Step 2: 生成数据库迁移 SQL**

编写并在数据库中执行：

```sql
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_server_ops_admin BOOLEAN NOT NULL DEFAULT false;
```

- [ ] **Step 3: 设置初始运维管理员**

```sql
UPDATE sys_user SET is_server_ops_admin = true WHERE username = 'lijingru';
UPDATE sys_user SET is_server_ops_admin = true WHERE username = 'fuzhongyu';
```

- [ ] **Step 4: 验证字段可正常序列化**

启动后端，调用 `GET /api/auth/me`，确认响应中包含 `"serverOpsAdmin": true/false` 字段。

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/entity/User.java
git commit -m "feat: add is_server_ops_admin field to User entity"
```

---

### Task 2: 后端代理 Controller

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/controller/ServerMgmtProxyController.java`

- [ ] **Step 1: 创建 ServerMgmtProxyController.java**

```java
package com.smartlab.erp.controller;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/server-mgmt")
@RequiredArgsConstructor
public class ServerMgmtProxyController {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${server.mgmt.api.base-url:http://server-mgmt-api:17000}")
    private String backendBaseUrl;

    @RequestMapping("/**")
    public ResponseEntity<?> proxy(HttpServletRequest request) {
        User currentUser = requireServerOpsAdmin();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无服务器管理权限"));
        }

        String remainingPath = extractRemainingPath(request);
        String queryString = request.getQueryString();
        String targetUrl = backendBaseUrl + "/api" + remainingPath;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());
        HttpHeaders headers = new HttpHeaders();

        String body = null;
        if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            try {
                body = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            if (body != null && !body.isEmpty()) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, method, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "服务器管理服务不可用: " + e.getMessage()));
        }
    }

    private User requireServerOpsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getServerOpsAdmin())) {
            return null;
        }
        return user;
    }

    private String extractRemainingPath(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithinApp = fullPath.substring(contextPath.length());
        return pathWithinApp.substring("/api/server-mgmt".length());
    }
}
```

- [ ] **Step 2: 配置属性**

在 `application.yml` 中添加（可选，有默认值）：

```yaml
server:
  mgmt:
    api:
      base-url: ${SERVER_MGMT_API_BASE_URL:http://server-mgmt-api:17000}
```

- [ ] **Step 3: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/controller/ServerMgmtProxyController.java
git add erp-backend/src/main/resources/application.yml
git commit -m "feat: add ServerMgmtProxyController for server management API proxy"
```

---

### Task 3: server-mgmt 代码迁移 + inventory 更新

**Files:**
- Create: `server-mgmt/` (整个目录从 outside 复制)
- Modify: `server-mgmt/inventory.ini`

- [ ] **Step 1: 复制代码到项目目录**

```bash
cp -r /home/a/zhangqi/workspace/ZKR/outside/服务器管理/服务器管理/ /home/a/zhangqi/workspace/ZKR/server-mgmt/
```

- [ ] **Step 2: 更新 inventory.ini 为局域网 IP**

将 `server-mgmt/inventory.ini` 内容替换为：

```ini
[servers]
3090 ansible_host=192.168.66.41 ansible_port=22
4090 ansible_host=192.168.66.224 ansible_port=22
5090 ansible_host=192.168.66.223 ansible_port=22

[servers:vars]
ansible_user=a
ansible_password=rjs12138
ansible_become_password=rjs12138
```

- [ ] **Step 3: Commit**

```bash
git add server-mgmt/
git commit -m "feat: migrate server-mgmt service from outside with LAN inventory"
```

---

### Task 4: Docker Compose 集成

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: 在 docker-compose.yml 中新增 server-mgmt-api 服务**

在 `lab-erp-demo` 服务定义之后、`networks` 之前，插入：

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
```

- [ ] **Step 2: 在 volumes 列表末尾追加**

```yaml
  server-mgmt-data:
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add server-mgmt-api service to docker-compose"
```

---

### Task 5: 前端路由 + 域名配置

**Files:**
- Modify: `lab-erp-demo/src/router/index.js`
- Modify: `lab-erp-demo/src/router/domainAccess.js`

- [ ] **Step 1: 在 router/index.js 中新增路由**

在 `LeaderManagementView` 路由之后（第 175 行）、`...financeRoutes` 之前（第 176 行），插入：

```javascript
    {
      path: '/admin/server-management',
      name: 'server-management',
      component: () => import('@/views/ServerManagementView.vue'),
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
```

- [ ] **Step 2: 在 domainAccess.js 中注册 ERP 路径**

修改 `domainAccess.js` 第 5 行，将 `/admin/server-management` 加入 `ERP_EXACT_PATHS`：

```javascript
const ERP_EXACT_PATHS = ['/admin/users/create', '/admin/wage-management', '/admin/server-management']
```

- [ ] **Step 3: Commit**

```bash
git add lab-erp-demo/src/router/index.js lab-erp-demo/src/router/domainAccess.js
git commit -m "feat: add server-management route and domain config"
```

---

### Task 6: 前端菜单入口 + 命令处理

**Files:**
- Modify: `lab-erp-demo/src/App.vue`

- [ ] **Step 1: 新增 computed 属性控制菜单可见性**

在 `App.vue` 的 `<script setup>` 中，`showExpenseReviewEntry` 之后（第 182 行后）新增：

```javascript
const showServerManagementEntry = computed(() => {
  return userStore.isErpLoggedIn && Boolean(userStore.activeUserInfo?.serverOpsAdmin)
})
```

- [ ] **Step 2: 在模板中新增下拉菜单项**

在工资管理菜单项（第 59 行）之后、费用审批（第 60 行）之前，插入：

```html
              <el-dropdown-item v-if="showServerManagementEntry" command="server-management">🖥️ 服务器管理</el-dropdown-item>
```

- [ ] **Step 3: 新增命令处理**

在 `handleCommand` 函数中，`wage-management` 分支（第 448 行）之后，插入：

```javascript
  else if (cmd === 'server-management') router.push('/admin/server-management')
```

- [ ] **Step 4: Commit**

```bash
git add lab-erp-demo/src/App.vue
git commit -m "feat: add server management dropdown menu entry"
```

---

### Task 7: 前端 ServerManagementView 页面

**Files:**
- Create: `lab-erp-demo/src/views/ServerManagementView.vue`

- [ ] **Step 1: 创建 ServerManagementView.vue**

```vue
<template>
  <div class="server-mgmt-page">
    <div class="server-mgmt-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">SERVER OPS</div>
          <h1>服务器管理</h1>
          <p class="subtitle">管理 3090 / 4090 / 5090 三台 GPU 服务器的用户账号</p>
        </div>
        <el-tag type="warning">仅运维管理员可见</el-tag>
      </div>

      <el-tabs v-model="activeTab" class="mgmt-tabs">
        <!-- Tab 1: 服务器列表 -->
        <el-tab-pane label="服务器" name="servers">
          <div class="server-cards">
            <div v-for="srv in servers" :key="srv.name" class="server-card" :class="{ 'server-card-ok': srv.status === 'ok' }">
              <div class="srv-header">
                <span class="srv-name">{{ srv.name }}</span>
                <el-tag :type="srv.status === 'ok' ? 'success' : 'danger'" size="small">
                  {{ srv.status === 'ok' ? '在线' : '离线' }}
                </el-tag>
              </div>
              <div class="srv-body">
                <div class="srv-row"><span class="label">IP</span><code>{{ srv.host }}</code></div>
                <div class="srv-row"><span class="label">端口</span><code>{{ srv.port }}</code></div>
              </div>
            </div>
          </div>
          <div v-if="servers.length === 0 && !serversLoading" class="empty-hint">暂无服务器数据</div>
        </el-tab-pane>

        <!-- Tab 2: 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div class="user-toolbar">
            <el-button type="primary" @click="openAddUserDialog">+ 新增用户</el-button>
          </div>
          <el-table :data="users" stripe v-loading="usersLoading" style="width: 100%">
            <el-table-column prop="name" label="用户名" width="140" />
            <el-table-column prop="server_name" label="服务器" width="100" />
            <el-table-column prop="ssh_key" label="SSH Key" min-width="200">
              <template #default="{ row }">
                <code class="key-masked">{{ maskKey(row.ssh_key) }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="state" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.state === 'present' ? 'success' : 'info'" size="small">{{ row.state === 'present' ? '在职' : '离职' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-popconfirm title="确认删除该用户？" @confirm="handleDeleteUser(row)">
                  <template #reference>
                    <el-button type="danger" size="small" :loading="deletingName === row.name">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="users.length === 0 && !usersLoading" class="empty-hint">暂无用户数据</div>
        </el-tab-pane>

        <!-- Tab 3: 部署 -->
        <el-tab-pane label="部署" name="deploy">
          <div class="deploy-section">
            <div class="deploy-row">
              <span class="deploy-label">目标服务器</span>
              <el-select v-model="deployTarget" placeholder="选择服务器（留空=全部）" clearable style="width: 240px">
                <el-option v-for="srv in serverOptions" :key="srv" :label="srv" :value="srv" />
              </el-select>
            </div>
            <el-button type="primary" :loading="deploying" @click="handleDeploy">
              {{ deploying ? '部署中...' : '一键部署' }}
            </el-button>
            <div v-if="deployLog" class="deploy-log">
              <pre>{{ deployLog }}</pre>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新增用户对话框 -->
    <el-dialog v-model="addUserVisible" title="新增用户" width="500px">
      <el-form :model="addUserForm" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="addUserForm.name" placeholder="Linux 用户名" />
        </el-form-item>
        <el-form-item label="服务器">
          <el-select v-model="addUserForm.server_name" placeholder="选择服务器" style="width: 100%">
            <el-option v-for="srv in serverOptions" :key="srv" :label="srv" :value="srv" />
          </el-select>
        </el-form-item>
        <el-form-item label="SSH 公钥">
          <el-input v-model="addUserForm.ssh_key" type="textarea" :rows="3" placeholder="ssh-ed25519 AAAA..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addUserVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingUser" @click="handleAddUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const BASE = '/api/server-mgmt'

const activeTab = ref('servers')

const servers = ref([])
const serversLoading = ref(false)

const users = ref([])
const usersLoading = ref(false)
const deletingName = ref(null)

const serverOptions = ref([])

const deployTarget = ref('')
const deploying = ref(false)
const deployLog = ref('')

const addUserVisible = ref(false)
const addingUser = ref(false)
const addUserForm = ref({ name: '', server_name: '', ssh_key: '' })

const maskKey = (key) => {
  if (!key) return ''
  return key.length > 30 ? key.slice(0, 20) + '...' + key.slice(-10) : key
}

const fetchServers = async () => {
  serversLoading.value = true
  try {
    const res = await request.get(`${BASE}/servers`, { params: { mask_secrets: true } })
    const data = res?.data || []
    servers.value = data.map(s => ({ ...s, status: 'ok' }))
    serverOptions.value = data.map(s => s.name)
  } catch (e) {
    ElMessage.error('加载服务器列表失败')
  } finally {
    serversLoading.value = false
  }
}

const fetchUsers = async () => {
  usersLoading.value = true
  try {
    const res = await request.get(`${BASE}/users`)
    users.value = res?.data || []
  } catch (e) {
    ElMessage.error('加载用户列表失败')
  } finally {
    usersLoading.value = false
  }
}

const handleDeleteUser = async (row) => {
  deletingName.value = row.name
  try {
    await request.delete(`${BASE}/users`, { data: { name: row.name, server_name: row.server_name } })
    ElMessage.success(`已删除用户 ${row.name}`)
    fetchUsers()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  } finally {
    deletingName.value = null
  }
}

const openAddUserDialog = () => {
  addUserForm.value = { name: '', server_name: '', ssh_key: '' }
  addUserVisible.value = true
}

const handleAddUser = async () => {
  if (!addUserForm.value.name || !addUserForm.value.server_name || !addUserForm.value.ssh_key) {
    ElMessage.warning('请填写完整信息')
    return
  }
  addingUser.value = true
  try {
    await request.post(`${BASE}/users`, addUserForm.value)
    ElMessage.success(`用户 ${addUserForm.value.name} 已创建`)
    addUserVisible.value = false
    fetchUsers()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    addingUser.value = false
  }
}

const handleDeploy = async () => {
  deploying.value = true
  deployLog.value = ''
  try {
    const body = deployTarget.value ? { server_name: deployTarget.value } : {}
    const res = await request.post(`${BASE}/deploy`, body)
    deployLog.value = res?.data?.stdout_tail || res?.message || '部署完成'
    ElMessage.success('部署成功')
  } catch (e) {
    const detail = e.response?.data?.detail
    deployLog.value = typeof detail === 'string' ? detail : (detail?.stderr || e.message || '部署失败')
    ElMessage.error('部署失败')
  } finally {
    deploying.value = false
  }
}

onMounted(() => {
  fetchServers()
  fetchUsers()
})
</script>

<style scoped>
.server-mgmt-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 32px 24px;
}
.server-mgmt-card {
  background: var(--bg-surface);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-soft);
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.eyebrow {
  font-size: 0.75rem;
  letter-spacing: 0.15em;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.header-row h1 { margin: 0; font-size: 1.5rem; }
.subtitle { color: var(--text-secondary); margin: 4px 0 0; font-size: 0.875rem; }

.mgmt-tabs { margin-top: 8px; }

.server-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px 0;
}
.server-card {
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 20px;
}
.server-card-ok { border-color: #22c55e; }
.srv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.srv-name { font-weight: 600; font-size: 1.1rem; }
.srv-row { display: flex; align-items: center; gap: 8px; margin: 6px 0; font-size: 0.875rem; }
.srv-row .label { color: var(--text-secondary); min-width: 32px; }
.srv-row code { font-family: monospace; background: var(--bg-base); padding: 2px 6px; border-radius: 4px; }

.user-toolbar { margin-bottom: 12px; }
.key-masked { font-size: 0.8rem; color: var(--text-secondary); }

.deploy-section { display: flex; flex-direction: column; gap: 16px; padding: 16px 0; }
.deploy-row { display: flex; align-items: center; gap: 12px; }
.deploy-label { font-weight: 500; min-width: 80px; }
.deploy-log { background: #1e293b; color: #e2e8f0; padding: 16px; border-radius: 8px; max-height: 400px; overflow: auto; }
.deploy-log pre { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 0.8rem; }

.empty-hint { text-align: center; color: var(--text-secondary); padding: 40px 0; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add lab-erp-demo/src/views/ServerManagementView.vue
git commit -m "feat: add ServerManagementView page with servers/users/deploy tabs"
```

---

### Task 8: 构建部署验证

- [ ] **Step 1: 构建并启动 server-mgmt-api**

```bash
sudo docker compose build server-mgmt-api
sudo docker compose up -d server-mgmt-api
```

- [ ] **Step 2: 验证 server-mgmt-api 健康状态**

```bash
curl http://127.0.0.1:17000/api/health
# Expected: {"status":"ok"} -- but port 17000 is NOT exposed, test from within erp-backend container:
sudo docker compose exec erp-backend curl -s http://server-mgmt-api:17000/api/health
# Expected: {"status":"ok"}
```

- [ ] **Step 3: 重新构建并启动 erp-backend**

```bash
sudo docker compose build erp-backend
sudo docker compose up -d erp-backend
```

- [ ] **Step 4: 验证代理转发**

登录为 lijingru 后获取 JWT token，测试代理：

```bash
# 用 lijingru 的 token 调用
curl -H "Authorization: Bearer <TOKEN>" http://127.0.0.1:8101/api/server-mgmt/servers?mask_secrets=true
# Expected: 返回服务器列表 JSON
```

- [ ] **Step 5: 验证非管理员被拒绝**

用一个普通用户（非 server_ops_admin）的 token 调用：

```bash
curl -H "Authorization: Bearer <NON_ADMIN_TOKEN>" http://127.0.0.1:8101/api/server-mgmt/servers
# Expected: 403 或 500 with "无服务器管理权限"
```

- [ ] **Step 6: 重新构建并启动前端**

根据 AGENTS.md 的部署流程部署前端新版本。

- [ ] **Step 7: 浏览器验证**

用李敬儒账号登录 ERP，确认：
1. 用户下拉菜单出现「🖥️ 服务器管理」选项
2. 点击进入页面，三个 Tab 正常加载数据
3. 用普通用户登录，菜单中不出现该选项

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: complete server management integration into ERP"
```

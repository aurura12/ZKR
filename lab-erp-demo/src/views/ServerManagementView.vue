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

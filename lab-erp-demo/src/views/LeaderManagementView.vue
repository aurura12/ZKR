<template>
  <div class="leader-management">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">👑 队长管理</h2>
        <div class="subtitle">管理员专属 · 设置各技术角色的队长</div>
      </div>
      <el-button type="primary" @click="$router.push('/manager/dashboard')">
        <el-icon><Back /></el-icon>
        返回管理看板
      </el-button>
    </div>

    <!-- 角色切换标签 -->
    <el-tabs v-model="activeRole" class="role-tabs" @tab-change="handleRoleChange">
      <el-tab-pane label="开发队长 (DEV)" name="DEV">
        <template #label>
          <span class="tab-label">
            <el-icon><Monitor /></el-icon>
            开发队长
          </span>
        </template>
      </el-tab-pane>

      <el-tab-pane label="算法队长 (ALGORITHM)" name="ALGORITHM">
        <template #label>
          <span class="tab-label">
            <el-icon><Cpu /></el-icon>
            算法队长
          </span>
        </template>
      </el-tab-pane>

      <el-tab-pane label="数据队长 (DATA)" name="DATA">
        <template #label>
          <span class="tab-label">
            <el-icon><DataAnalysis /></el-icon>
            数据队长
          </span>
        </template>
      </el-tab-pane>

      <el-tab-pane label="研究队长 (RESEARCH)" name="RESEARCH">
        <template #label>
          <span class="tab-label">
            <el-icon><Reading /></el-icon>
            研究队长
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 当前队长信息 -->
    <el-card class="current-leader-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>🎯 当前{{ roleTitle }}队长</span>
          <el-tag v-if="currentLeader" type="success" size="large">已设置</el-tag>
          <el-tag v-else type="warning" size="large">未设置</el-tag>
        </div>
      </template>

      <div v-if="currentLeader" class="leader-info">
        <img :src="currentLeader.avatar || defaultAvatar" class="leader-avatar" />
        <div class="leader-details">
          <div class="leader-name">{{ currentLeader.name || currentLeader.username }}</div>
          <div class="leader-meta">
            <el-tag size="small">{{ currentLeader.username }}</el-tag>
            <el-tag size="small" type="info">{{ currentLeader.email || '无邮箱' }}</el-tag>
          </div>
        </div>
        <el-button
            type="danger"
            plain
            @click="confirmRemoveLeader"
        >
          取消队长
        </el-button>
      </div>

      <el-empty v-else description="暂无队长，请从下方成员中选择" :image-size="80" />
    </el-card>

    <!-- 可选成员列表 -->
    <el-card class="members-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>👥 {{ roleTitle }}成员列表</span>
          <el-input
              v-model="searchKeyword"
              placeholder="搜索成员姓名或账号"
              prefix-icon="Search"
              clearable
              style="width: 300px"
          />
        </div>
      </template>

      <el-table
          :data="filteredMembers"
          stripe
          style="width: 100%"
          v-loading="loading"
      >
        <el-table-column label="成员信息" min-width="250">
          <template #default="{ row }">
            <div class="member-cell">
              <img :src="row.avatar || defaultAvatar" class="member-avatar" />
              <div class="member-info">
                <div class="member-name">{{ row.name || row.username }}</div>
                <div class="member-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" width="200" />

        <el-table-column label="当前角色" width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" size="small">
              {{ formatRole(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
                v-if="!isCurrentLeader(row)"
                type="primary"
                size="small"
                @click="confirmSetLeader(row)"
            >
              设为队长
            </el-button>
            <el-tag v-else type="success" size="small">当前队长</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
          v-if="!loading && filteredMembers.length === 0"
          description="暂无该角色的成员"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Monitor, Cpu, DataAnalysis, Reading } from '@element-plus/icons-vue'
import { getAllUsers, assignRole } from '@/api/leader'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const activeRole = ref('DEV')
const allUsers = ref([])
const currentLeader = ref(null)
const searchKeyword = ref('')

const roleTitles = {
  'DEV': '开发',
  'ALGORITHM': '算法',
  'DATA': '数据',
  'RESEARCH': '研究'
}

const roleTitle = computed(() => roleTitles[activeRole.value] || '技术')

const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=default'

// 过滤出当前角色的成员
const roleMembers = computed(() => {
  return allUsers.value.filter(user => {
    const userRole = String(user.role || '').toUpperCase()
    return userRole === activeRole.value
  })
})

// 搜索过滤
const filteredMembers = computed(() => {
  if (!searchKeyword.value) return roleMembers.value

  const keyword = searchKeyword.value.toLowerCase()
  return roleMembers.value.filter(user =>
      (user.name && user.name.toLowerCase().includes(keyword)) ||
      (user.username && user.username.toLowerCase().includes(keyword)) ||
      (user.email && user.email.toLowerCase().includes(keyword))
  )
})

// 加载所有用户
const loadUsers = async () => {
  loading.value = true
  try {
    const res = await getAllUsers()
    allUsers.value = Array.isArray(res) ? res : []
  } catch (error) {
    ElMessage.error('加载用户列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 检查是否是当前队长
const isCurrentLeader = (user) => {
  // TODO: 需要从后端获取真正的队长信息
  return false
}

// 确认设置为队长
const confirmSetLeader = async (user) => {
  try {
    await ElMessageBox.confirm(
        `确定要将 "${user.name || user.username}" 设置为${roleTitle.value}队长吗？`,
        '确认操作',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await assignRole({
      userId: user.userId,
      role: activeRole.value,
      isLeader: true
    })

    ElMessage.success('设置队长成功')
    await loadUsers() // 重新加载
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '设置失败')
    }
  }
}

// 确认取消队长
const confirmRemoveLeader = async () => {
  if (!currentLeader.value) return

  try {
    await ElMessageBox.confirm(
        `确定要取消 "${currentLeader.value.name || currentLeader.value.username}" 的队长身份吗？`,
        '确认操作',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await assignRole({
      userId: currentLeader.value.userId,
      role: activeRole.value,
      isLeader: false
    })

    ElMessage.success('已取消队长身份')
    currentLeader.value = null
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '操作失败')
    }
  }
}

// 角色切换
const handleRoleChange = async (role) => {
  activeRole.value = role
  searchKeyword.value = ''
}

// 格式化角色名称
const formatRole = (role) => {
  const map = {
    'DEV': '开发',
    'ALGORITHM': '算法',
    'DATA': '数据',
    'RESEARCH': '研究',
    'ADMIN': '管理员',
    'BUSINESS': '商务'
  }
  return map[role] || role
}

// 获取角色标签类型
const getRoleTagType = (role) => {
  const typeMap = {
    'DEV': 'primary',
    'ALGORITHM': 'success',
    'DATA': 'warning',
    'RESEARCH': '',
    'ADMIN': 'danger'
  }
  return typeMap[role] || 'info'
}

onMounted(async () => {
  await loadUsers()
})
</script>

<style scoped>
.leader-management {
  max-width: 1400px;
  margin: 0 auto;
  padding: 30px;
  background-color: var(--science-canvas, #f5f7fa);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-main, #1a1a1a);
  margin: 0;
}

.subtitle {
  color: var(--text-sub, #666);
  font-size: 14px;
  margin-top: 5px;
}

.role-tabs {
  margin-bottom: 24px;
  background: white;
  padding: 0 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.current-leader-card,
.members-card {
  margin-bottom: 24px;
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.leader-info {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 0;
}

.leader-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.leader-details {
  flex: 1;
}

.leader-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-main, #1a1a1a);
  margin-bottom: 8px;
}

.leader-meta {
  display: flex;
  gap: 8px;
}

.member-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.member-info {
  display: flex;
  flex-direction: column;
}

.member-name {
  font-weight: 600;
  color: var(--text-main, #1a1a1a);
}

.member-username {
  font-size: 12px;
  color: var(--text-sub, #999);
}

:deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
}

:deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #409eff, #66b1ff);
}
</style>

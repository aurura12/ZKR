<template>
  <div class="leader-dashboard">
    <!-- 如果没有权限，显示提示 -->
    <el-result
        v-if="!hasLeaderPermission && !loading"
        icon="warning"
        title="无权访问"
        sub-title="您不是该角色的队长，无法查看此页面"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/workspace')">
          返回工作台
        </el-button>
      </template>
    </el-result>
    <template v-else>
      <div class="leader-dashboard">
<!--    <div class="dashboard-header">-->
        <div class="header-left">
          <h2 class="page-title">{{ roleTitle }}队长工作台</h2>
          <div class="subtitle">查看本角色成员的项目参与情况 · {{ currentDate }}</div>
        </div>
      </div>

      <!-- KPI 卡片 -->
      <div class="kpi-row">
        <div class="kpi-card">
          <div class="kpi-icon-box bg-blue">👥</div>
          <div class="kpi-content">
            <div class="kpi-label">团队成员</div>
            <div class="kpi-value-group">
              <span class="big-num">{{ memberCount }}</span><span class="unit">人</span>
            </div>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon-box bg-purple">📦</div>
          <div class="kpi-content">
            <div class="kpi-label">进行中项目</div>
            <div class="kpi-value-group">
              <span class="big-num">{{ activeProjectCount }}</span><span class="unit">个</span>
            </div>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon-box bg-green">⚡</div>
          <div class="kpi-content">
            <div class="kpi-label">总投入权重</div>
            <div class="kpi-value-group">
              <span class="big-num">{{ totalWeight }}</span><span class="unit">%</span>
            </div>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon-box bg-red">💰</div>
          <div class="kpi-content">
            <div class="kpi-label">预估人力成本</div>
            <div class="kpi-value-group">
              <span class="big-num">¥{{ (totalCost / 10000).toFixed(1) }}</span><span class="unit">万</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 成员列表 -->
      <div class="members-section">
        <div class="section-header">
          团队成员详情
          <span class="count-badge">{{ memberCount }}</span>
        </div>

        <el-collapse v-model="activeCollapse" class="member-collapse">
          <el-collapse-item
              v-for="member in dashboardData?.members || []"
              :key="member.userId"
              :name="member.userId"
          >
            <template #title>
              <div class="member-header">
                <img
                    :src="member.avatar || defaultAvatar"
                    class="member-avatar"
                />
                <div class="member-info">
                  <div class="member-name">{{ member.name || member.username }}</div>
                  <div class="member-meta">
                    参与项目: {{ member.projects.length }} 个 |
                    总权重: {{ member.totalWeight }}% |
                    预估成本: ¥{{ (member.estimatedCost / 10000).toFixed(1) }}万
                  </div>
                </div>
              </div>
            </template>

            <div class="member-projects">
              <el-table :data="member.projects" stripe style="width: 100%">
                <el-table-column prop="projectName" label="项目名称" min-width="200" />
                <el-table-column prop="projectType" label="领域" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getProjectTypeColor(row.projectType)" size="small">
                      {{ formatProjectType(row.projectType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="flowType" label="流程类型" width="120">
                  <template #default="{ row }">
                    {{ formatFlowType(row.flowType) }}
                  </template>
                </el-table-column>
                <el-table-column prop="memberRole" label="担任角色" width="120" />
                <el-table-column prop="weight" label="投入权重" width="100">
                  <template #default="{ row }">
                    <el-progress
                        :percentage="row.weight"
                        :stroke-width="8"
                        :color="getProgressColor(row.weight)"
                    />
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="项目状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="getStatusType(row.status)" size="small">
                      {{ row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="managerName" label="项目经理" width="120" />
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button
                        type="primary"
                        size="small"
                        link
                        @click="viewProject(row.projectId)"
                    >
                      查看详情
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-empty
            v-if="!loading && (!dashboardData?.members || dashboardData.members.length === 0)"
            description="暂无团队成员"
        />
      </div>
    </template>
    </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLeaderDashboard,checkLeaderStatus} from '@/api/leader'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const dashboardData = ref(null)
const activeCollapse = ref([])
const hasLeaderPermission = ref(false)


// 从路由参数或用户信息中获取角色
const role = computed(() => {
  return userStore.activeUserInfo?.role || 'DEV'
})

//检查是否有队长权限
const checkPermission = async () => {
  try {
    const res = await checkLeaderStatus(role.value)
    hasLeaderPermission.value = res.isLeader

    if (!res.isLeader) {
      ElMessage.warning('您不是该角色的队长，无权访问此页面')
      // 可以选择重定向到其他页面
      setTimeout(() => {
        router.push('/workspace')
      }, 2000)
    }
  } catch (error) {
    console.error('权限检查失败:', error)
    hasLeaderPermission.value = false
  }
}

const roleTitle = computed(() => {
  const roleMap = {
    'DEV': '开发',
    'ALGORITHM': '算法',
    'DATA': '数据',
    'RESEARCH': '研究'
  }
  return roleMap[role.value] || '技术'
})

const currentDate = computed(() => {
  return new Date().toLocaleDateString('zh-CN')
})

const memberCount = computed(() => {
  return dashboardData.value?.members?.length || 0
})

const activeProjectCount = computed(() => {
  if (!dashboardData.value?.members) return 0
  return dashboardData.value.members.reduce((count, member) => {
    return count + member.projects.filter(p => p.status === 'ACTIVE').length
  }, 0)
})

const totalWeight = computed(() => {
  if (!dashboardData.value?.members) return 0
  return dashboardData.value.members.reduce((sum, member) => {
    return sum + member.totalWeight
  }, 0)
})

const totalCost = computed(() => {
  if (!dashboardData.value?.members) return 0
  return dashboardData.value.members.reduce((sum, member) => {
    return sum + member.estimatedCost
  }, 0)
})

const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=default'

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getLeaderDashboard(role.value)
    dashboardData.value = res
    // 默认展开所有成员
    activeCollapse.value = res.members?.map(m => m.userId) || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载失败')
    console.error('加载队长工作台失败:', error)
  } finally {
    loading.value = false
  }
}

const formatProjectType = (type) => {
  const map = {
    'MILITARY': '军工',
    'AI_FOR_SCIENCE': 'AI科学',
    'MEDICAL': '医药',
    'INDUSTRIAL': '工业',
    'SWARM_INTEL': '群体智能',
    'BUSINESS': '业务'
  }
  return map[type] || type
}

const getProjectTypeColor = (type) => {
  const colorMap = {
    'MILITARY': 'danger',
    'AI_FOR_SCIENCE': 'primary',
    'MEDICAL': 'success',
    'INDUSTRIAL': 'warning',
    'SWARM_INTEL': ''
  }
  return colorMap[type] || 'info'
}

const formatFlowType = (flowType) => {
  const map = {
    'PROJECT': '项目流',
    'PRODUCT': '产品流',
    'RESEARCH': '科研流'
  }
  return map[flowType] || '项目流'
}

const getStatusType = (status) => {
  const typeMap = {
    'ACTIVE': 'success',
    'COMPLETED': 'info',
    'ARCHIVED': 'warning',
    'CANCELLED': 'danger'
  }
  return typeMap[status] || 'info'
}

const getProgressColor = (weight) => {
  if (weight >= 80) return '#67c23a'
  if (weight >= 50) return '#e6a23c'
  return '#909399'
}

const viewProject = (projectId) => {
  router.push(`/workspace/project/${projectId}`)
}

onMounted(async () => {
  await checkPermission()
  if (hasLeaderPermission.value) {
    await loadDashboard()
  }
})
</script>

<style scoped>
.leader-dashboard {
  max-width: 1400px;
  margin: 0 auto;
  padding: 30px;
  background-color: var(--science-canvas, #f5f7fa);
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 30px;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -1px;
  color: var(--text-main, #1a1a1a);
  margin: 0;
}

.subtitle {
  color: var(--text-sub, #666);
  font-size: 15px;
  margin-top: 5px;
  font-weight: 400;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.kpi-card {
  background: var(--science-surface, #fff);
  border-radius: 8px;
  border: 1px solid var(--border-soft, #e0e0e0);
  box-shadow: var(--shadow-md, 0 2px 8px rgba(0,0,0,0.08));
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 136px;
  transition: transform 0.3s;
}

.kpi-card:hover {
  transform: translateY(-5px);
}

.kpi-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.bg-blue { background: #EAF4FF; color: #007AFF; }
.bg-purple { background: #F3E5F5; color: #AF52DE; }
.bg-green { background: #E8F5E9; color: #34C759; }
.bg-red { background: #FFEBEE; color: #FF3B30; }

.kpi-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-sub, #666);
  text-transform: uppercase;
}

.kpi-value-group {
  margin-top: 8px;
}

.big-num {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -1px;
  color: var(--text-main, #1a1a1a);
}

.unit {
  font-size: 14px;
  color: var(--text-sub, #666);
  font-weight: 500;
  margin-left: 4px;
}

.members-section {
  margin-top: 10px;
}

.section-header {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-main, #1a1a1a);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.count-badge {
  font-size: 14px;
  background: var(--science-surface-muted, #f0f0f0);
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--text-sub, #666);
  font-weight: 600;
}

.member-collapse {
  background: transparent;
  border: none;
}

:deep(.el-collapse-item__header) {
  background: var(--science-surface, #fff);
  border-radius: 8px;
  margin-bottom: 10px;
  padding: 16px 20px;
  border: 1px solid var(--border-soft, #e0e0e0);
}

:deep(.el-collapse-item__wrap) {
  background: var(--science-surface, #fff);
  border-radius: 8px;
  margin-bottom: 10px;
  border: 1px solid var(--border-soft, #e0e0e0);
  border-top: none;
}

.member-header {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.member-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.member-info {
  flex: 1;
}

.member-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main, #1a1a1a);
  margin-bottom: 4px;
}

.member-meta {
  font-size: 13px;
  color: var(--text-sub, #666);
}

.member-projects {
  padding: 20px;
}

@media (max-width: 1200px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .kpi-row {
    grid-template-columns: 1fr;
  }

  .leader-dashboard {
    padding: 20px;
  }
}
</style>

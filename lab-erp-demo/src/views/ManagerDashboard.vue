<template>
  <div class="dashboard-container">
    <div class="dashboard-header">
      <div class="header-left">
        <h2 class="page-title">PM Command Center</h2>
        <div class="subtitle">项目经理指挥中心 · {{ new Date().toLocaleDateString() }}</div>
      </div>

      <div class="header-right">
        <el-button
            v-if="isAdmin"
            type="danger"
            size="large"
            @click="$router.push('/admin/leader-management')"            style="font-weight: bold; border-radius: 8px; padding: 12px 24px; margin-right: 8px;"
        >
          <el-icon style="margin-right: 6px"><Setting /></el-icon>
          队长管理
        </el-button>
        <!--●📕新增队长工作台-->
        <el-button
            v-if="showLeaderDashboard"
            type="warning"
            size="large"
            @click="$router.push('/leader/dashboard')"            style="font-weight: bold; border-radius: 8px; padding: 12px 24px; margin-right: 8px;"
        >
          <el-icon style="margin-right: 6px"><User /></el-icon>
          队长工作台
        </el-button>
        <el-button
            v-if="showBusinessLaunch"
            type="primary"
            size="large"
            color="#34C759"
            @click="$router.push('/manager/project/create')"
            style="font-weight: bold; border-radius: 8px; padding: 12px 24px;"
        >
          <el-icon style="margin-right: 6px"><Plus /></el-icon>
          发起项目
        </el-button>
        <el-button
            v-if="showResearchLaunch"
            type="primary"
            size="small"
            color="#007AFF"
            @click="$router.push('/manager/research/create')"
            style="font-weight: 700; border-radius: 8px; padding: 8px 14px; margin-left: 8px;"
        >
          <el-icon style="margin-right: 6px"><Plus /></el-icon>
          发起科研
        </el-button>
      </div>
    </div>

    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-icon-box bg-blue">📦</div>
        <div class="kpi-content">
          <div class="kpi-label">Active Missions</div>
          <div class="kpi-value-group">
            <span class="big-num">{{ activeProjectCount }}</span><span class="unit">个在研</span>
          </div>
          <div class="kpi-sub">管理半径: <span class="bold">{{ managementRadius }}%</span></div>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon-box bg-purple">📉</div>
        <div class="kpi-content">
          <div class="kpi-label">Cost Monitor</div>
          <div class="kpi-value-group">
            <span class="big-num">¥{{ (totalHumanCost/10000).toFixed(1) }}</span><span class="unit">万</span>
          </div>
          <div class="progress-box">
            <div class="progress-track"><div class="progress-fill" :style="{ width: costUsageRate + '%' }"></div></div>
          </div>
          <div class="kpi-sub">{{ lastCostBatchLabel ? `最近跑批: ${lastCostBatchLabel}` : '等待夜间成本跑批' }}</div>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon-box" :class="Number(profitMargin) < 20 || totalRemainingProfit <= 0 ? 'bg-red' : 'bg-green'">💰</div>
        <div class="kpi-content">
          <div class="kpi-label">Profit</div>
          <div class="kpi-value-group">
            <span class="big-num" :class="{'text-danger': Number(profitMargin) < 20 || totalRemainingProfit <= 0}">¥{{ (totalRemainingProfit/10000).toFixed(1) }}</span>
            <span class="unit">万</span>
          </div>
          <div class="kpi-sub">{{ Number(profitMargin) < 20 ? '⚠️ ' : '' }}利润率: <span class="bold">{{ profitMargin }}%</span></div>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon-box bg-red">🧩</div>
        <div class="kpi-content">
          <div class="kpi-label">Pending Work</div>
          <div class="kpi-value-group">
            <span class="big-num">{{ pendingSubtaskCount }}</span><span class="unit">项待办</span>
          </div>
          <div class="kpi-sub">风险项目: <span class="bold">{{ riskProjectCount }}</span></div>
        </div>
      </div>
    </div>

    <div class="charts-row">
      <div class="chart-card">
        <div class="card-title">资金投入趋势 (6 Months)</div>
        <div ref="lineChartRef" class="chart-canvas"></div>
      </div>

      <div class="chart-card">
        <div class="card-header-row">
          <div class="card-title">领域分布 </div>
          <div v-if="currentIndustryFilter" class="filter-badge" @click="resetFilter">
            {{ formatIndustry(currentIndustryFilter) }} ×
          </div>
        </div>
        <div ref="pieChartRef" class="chart-canvas"></div>
      </div>
    </div>

    <div class="projects-section">
      <div class="section-header">
        {{ currentIndustryFilter ? `${formatIndustry(currentIndustryFilter)} SECTOR` : 'ALL MISSIONS' }}
        <span class="count-badge">{{ filteredProjects.length }}</span>
      </div>

      <div class="project-grid-row">
        <div
            v-for="p in filteredProjects"
            :key="p.projectId"
            class="mission-card"
            :class="[p.projectType, getFlowTypeClass(p)]"
            @click="openProjectModal(p)"
        >
          <div class="card-left-border"></div>

          <div class="mission-content">
            <div class="mission-top">
              <span class="mission-code">{{ getProjectCode(p) }}</span>
              <span class="flow-type-badge" :class="getFlowTypeClass(p)">{{ getFlowTypeLabel(p) }}</span>
              <span class="status-badge" :class="p.status">{{ p.status }}</span>
            </div>

            <h3 class="mission-name">{{ p.name }}</h3>
            <p class="mission-desc">{{ p.description || '暂无描述' }}</p>

            <div class="mission-footer">
              <div class="launch-pill">
                {{ getLaunchLabel(p) }}
              </div>
              <div class="pm-info">
                <img :src="p.managerAvatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + p.projectId" class="pm-avatar">
                <span>{{ p.managerName || 'PM' }}</span>
              </div>
              <div class="cost-pill">
                消耗: {{ p.budget ? Math.round((p.cost||0)/p.budget*100) : 0 }}%
              </div>
            </div>
          </div>

          <div class="hover-arrow">🔍</div>
        </div>
      </div>
    </div>

    <el-dialog
        v-model="showDetailModal"
        :title="null"
        width="94%"
        top="5vh"
        destroy-on-close
        :show-close="false"
        custom-class="command-modal"
    >
      <div class="modal-header-actions">
        <span class="modal-tag">COMMAND VIEW · {{ formatIndustry(selectedProjectType) }}</span>
        <button class="modal-close-btn" @click="showDetailModal = false">ESC</button>
      </div>
      <div class="modal-detail-shell">
        <ProjectDetail v-if="selectedProjectId" :projectId="selectedProjectId" />
      </div>
    </el-dialog>

  </div>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'

// 🟢 1. 核心修改：直接引入你现有的 request 工具 (不再引用那个不存在的 project.js)
import request from '@/utils/request'

import * as echarts from 'echarts'
import { Plus,User,Setting } from '@element-plus/icons-vue'
import ProjectDetail from '@/views/ProjectDetail.vue'
import { useRouter } from "vue-router" // 修正：组件内通常用 useRouter
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()
const researchInitiatorWhitelist = [
  '焦淼', '胡军', '任涛', '余文清',
  'jiaomiao', 'hujun', 'rentao', 'yuwenqing'
]
const projects = ref([])
const summary = ref(null)
const lineChartRef = ref(null)
const pieChartRef = ref(null)
let pieChartInstance = null
let summaryRefreshTimer = null

// 状态管理
const showDetailModal = ref(false)
const selectedProjectId = ref('')
const selectedProjectType = ref('')
const currentIndustryFilter = ref(null)
const showResearchLaunch = computed(() => {
  if (!userStore.isErpLoggedIn) return false
  const role = String(userStore.activeUserInfo?.role || '').toUpperCase()
  const username = String(userStore.activeUserInfo?.username || '').trim()
  const name = String(userStore.activeUserInfo?.name || '').trim()
  return role === 'RESEARCH'
    || researchInitiatorWhitelist.includes(username)
    || researchInitiatorWhitelist.includes(name)
})
const showBusinessLaunch = computed(() => userStore.isErpLoggedIn && String(userStore.activeUserInfo?.role || '').toUpperCase() === 'BUSINESS')

const isAdmin = computed(() => {
  if (!userStore.isErpLoggedIn) return false
  const role = String(userStore.activeUserInfo?.role || '').toUpperCase()
  const username = String(userStore.activeUserInfo?.username || '').trim()
  const adminUsernames = ['zhangqi', 'guojianwen', 'jiaomiao']
  return role === 'ADMIN' || adminUsernames.includes(username.toLowerCase())
})

const showLeaderDashboard = computed(() => {
  if (!userStore.isErpLoggedIn) return false
  const role = String(userStore.activeUserInfo?.role || '').toUpperCase()
  const leaderRoles = ['DEV', 'ALGORITHM', 'DATA', 'RESEARCH']
  return leaderRoles.includes(role)
})

// 1. 行业映射字典
const industryMap = {
  'BUSINESS': '业务',
  'MILITARY': '军工',
  'AI_FOR_SCIENCE': 'AI FOR SCIENCE',
  'MEDICAL': '医药',
  'INDUSTRIAL': '工业',
  'SWARM_INTEL': '群体智能',
  'OTHER': '未分类'
}

// 2. 行业颜色配置
const industryColors = {
  'BUSINESS': '#0ea5e9',
  'MILITARY': '#FF3B30',
  'AI_FOR_SCIENCE': '#007AFF',
  'MEDICAL': '#30B0C7',
  'INDUSTRIAL': '#34C759',
  'SWARM_INTEL': '#AF52DE',
  'OTHER': '#8E8E93'
}

// 列表过滤
const filteredProjects = computed(() => {
  if (!currentIndustryFilter.value) {
    return projects.value.filter(p => p.status !== 'ARCHIVED')
  }
  if (currentIndustryFilter.value === 'OTHER') {
    return projects.value.filter(p => !industryMap[p.projectType])
  }
  return projects.value.filter(p =>
      p.status !== 'ARCHIVED' && p.projectType === currentIndustryFilter.value
  )
})

const activeProjectCount = computed(() => projects.value.filter(p => p.status !== 'ARCHIVED').length)
const pendingSubtaskCount = computed(() => Number(summary.value?.pendingSubtaskCount || 0))
const riskProjectCount = computed(() => Number(summary.value?.riskProjectCount || 0))
const managementRadius = computed(() => Number(summary.value?.managementRadius || 0).toFixed(1))
const lastCostBatchLabel = computed(() => {
  const raw = summary.value?.lastCostBatchAt
  if (!raw) return ''
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return ''
  return parsed.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

const totalBudget = computed(() => {
  const backendBudget = Number(summary.value?.totalBudget)
  if (Number.isFinite(backendBudget)) return backendBudget
  return projects.value.reduce((sum, p) => sum + Number(p.budget || 0), 0)
})

const totalHumanCost = computed(() => {
  const backendCost = Number(summary.value?.totalHumanCost ?? summary.value?.totalCost)
  if (Number.isFinite(backendCost)) return backendCost
  return projects.value.reduce((sum, p) => sum + Number(p.cost || 0), 0)
})

const totalRemainingProfit = computed(() => {
  const backendProfit = Number(summary.value?.totalRemainingProfit)
  if (Number.isFinite(backendProfit)) return backendProfit
  return Math.max(totalBudget.value - totalHumanCost.value, 0)
})

// 利润率计算
const profitMargin = computed(() => {
  const backendMargin = Number(summary.value?.profitMargin)
  if (Number.isFinite(backendMargin)) return backendMargin.toFixed(1)
  if (!totalBudget.value || totalBudget.value === 0) return 0
  const margin = (totalRemainingProfit.value / totalBudget.value) * 100
  return margin.toFixed(1)
})

const costUsageRate = computed(() => {
  const backendRate = Number(summary.value?.costUsageRate)
  if (Number.isFinite(backendRate)) return backendRate.toFixed(1)
  if (!totalBudget.value || totalBudget.value === 0) return 0
  return ((totalHumanCost.value / totalBudget.value) * 100).toFixed(1)
})

// 打开浮窗
const openProjectModal = (project) => {
  selectedProjectId.value = project.projectId
  selectedProjectType.value = project.projectType
  showDetailModal.value = true
}

const getProjectCode = (p) => {
  const map = {
    'MILITARY': 'MIL', 'AI_FOR_SCIENCE': 'AI4S',
    'MEDICAL': 'MED', 'INDUSTRIAL': 'IND', 'SWARM_INTEL': 'SWM'
  }
  const prefix = map[p.projectType] || 'GEN'
  return `${prefix}-${p.projectId ? p.projectId.substring(0,4).toUpperCase() : '000'}`
}

const getFlowTypeLabel = (p) => {
  const flowType = String(p.flowType || 'PROJECT').toUpperCase()
  const labels = {
    'PROJECT': '项目流',
    'PRODUCT': '产品流',
    'RESEARCH': '科研流'
  }
  return labels[flowType] || '项目流'
}

const getFlowTypeClass = (p) => {
  const flowType = String(p.flowType || 'PROJECT').toUpperCase()
  return `flow-${flowType.toLowerCase()}`
}

const formatLaunchDate = value => {
  if (!value) return '未知时间'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '未知时间'
  return parsed.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

const getLaunchLabel = p => `${getFlowTypeLabel(p).replace('流', '')}发起于 ${formatLaunchDate(p.createdAt)}`

const formatIndustry = (key) => industryMap[key] || key

const resetFilter = () => {
  currentIndustryFilter.value = null
  if (pieChartInstance) {
    pieChartInstance.dispatchAction({ type: 'downplay' })
  }
}

const loadSummary = async () => {
  try {
    const res = await request.get('/api/projects/managed/summary')
    const resolvedSummary = res?.data || res || null
    const resolvedProjects = Array.isArray(resolvedSummary?.projects) ? resolvedSummary.projects : []

    summary.value = resolvedSummary
    projects.value = resolvedProjects
  } catch (error) {
    console.error('加载管理汇总失败:', error)
    summary.value = null
    projects.value = []
  }

  if (!summary.value || !Array.isArray(summary.value?.projects)) {
    summary.value = {
      pendingSubtaskCount: 0,
      riskProjectCount: 0,
      managementRadius: 0,
      totalBudget: 0,
      totalHumanCost: 0,
      totalRemainingProfit: 0,
      profitMargin: 0,
      costUsageRate: 0,
      projects: projects.value
    }
  }
}

const startAutoRefresh = () => {
  if (summaryRefreshTimer) window.clearInterval(summaryRefreshTimer)
  summaryRefreshTimer = window.setInterval(() => {
    loadSummary()
  }, 5 * 60 * 1000)
}

// 🟢 3. 生命周期挂载
onMounted(async () => {
  await loadSummary()
  console.log('管理看板数据:', projects.value)

  nextTick(() => {
    initLineChart()
    initPieChart()
  })

  startAutoRefresh()
})

onBeforeUnmount(() => {
  if (summaryRefreshTimer) {
    window.clearInterval(summaryRefreshTimer)
    summaryRefreshTimer = null
  }
})

// 图表逻辑保持不变...
const initLineChart = () => {
  if (!lineChartRef.value) return
  const myChart = echarts.init(lineChartRef.value)
  const cost = totalHumanCost.value / 10000
  myChart.setOption({
    grid: { top: 10, bottom: 20, left: 40, right: 10 },
    xAxis: { type: 'category', data: ['Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan'], axisLine: { lineStyle: { color: '#ddd' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed' } } },
    series: [{
      type: 'line',
      smooth: true,
      data: [20, 32, 25, 45, 60, cost > 0 ? cost : 65],
      itemStyle: { color: '#0079ff' },
      areaStyle: { opacity: 0.1 }
    }]
  })
  window.addEventListener('resize', () => myChart.resize())
}

const initPieChart = () => {
  if (!pieChartRef.value) return
  if (pieChartInstance) pieChartInstance.dispose()
  pieChartInstance = echarts.init(pieChartRef.value)

  const countMap = {}
  Object.keys(industryMap).forEach(k => countMap[k] = 0)

  projects.value.forEach(p => {
    let type = p.projectType;
    if (!type || !industryMap[type]) {
      type = 'OTHER';
    }
    countMap[type] = (countMap[type] || 0) + 1
  })

  const chartData = Object.keys(countMap)
      .filter(key => countMap[key] > 0)
      .map(key => ({
        value: countMap[key],
        name: industryMap[key],
        rawKey: key,
        itemStyle: { color: industryColors[key] || '#999' }
      }))

  if (chartData.length === 0) {
    chartData.push({ value: 0, name: '无数据', itemStyle: { color: '#eee' } })
  }

  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: '0%', icon: 'circle', itemGap: 15 },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: chartData
    }]
  }

  pieChartInstance.setOption(option)

  pieChartInstance.on('click', (params) => {
    const clickedType = params.data.rawKey
    if (!clickedType) return
    if (currentIndustryFilter.value === clickedType) {
      resetFilter()
    } else {
      currentIndustryFilter.value = clickedType
    }
  })
  window.addEventListener('resize', () => pieChartInstance.resize())
}

watch(projects, () => {
  nextTick(() => {
    initLineChart()
    initPieChart()
  })
}, { deep: true })
</script>

<style scoped>
.dashboard-container {
  max-width: 1200px; margin: 0 auto; padding: 30px;
  background-color: var(--science-canvas);
}

/* Header & KPI (复用之前的样式) */
.dashboard-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 30px; }
.header-left { display: flex; flex-direction: column; }
.header-right { display: flex; align-items: center; padding-bottom: 5px; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.page-title { font-size: 34px; font-weight: 700; letter-spacing: -1px; color: var(--text-main); margin: 0; }
.subtitle { color: var(--text-sub); font-size: 15px; margin-top: 5px; font-weight: 400; }

.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 30px; }
.kpi-card { background: var(--science-surface) !important; border-radius: 8px; border: 1px solid var(--border-soft); box-shadow: var(--shadow-md); padding: 24px; display: flex; flex-direction: column; justify-content: space-between; min-height: 136px; transition: transform 0.3s; }
.kpi-card:hover { transform: translateY(-5px); }
.kpi-icon-box { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.bg-blue { background: #EAF4FF; color: #007AFF; }
.bg-purple { background: #F3E5F5; color: #AF52DE; }
.bg-green { background: #E8F5E9; color: #34C759; }
.bg-red { background: #FFEBEE; color: #FF3B30; }
.kpi-label { font-size: 12px; font-weight: 600; color: var(--text-sub); text-transform: uppercase; }
.big-num { font-size: 32px; font-weight: 700; letter-spacing: -1px; color: var(--text-main); }
.unit { font-size: 14px; color: var(--text-sub); font-weight: 500; margin-left: 4px; }
.kpi-sub { font-size: 13px; color: var(--text-sub); margin-top: auto; }

/* 图表区 */
.charts-row { display: grid; grid-template-columns: 2fr 1fr; gap: 20px; margin-bottom: 40px; }
.chart-card { background: var(--science-surface); border-radius: 8px; border: 1px solid var(--border-soft); padding: 24px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.card-title { font-size: 17px; font-weight: 600; color: var(--text-main); }
.chart-canvas { height: 260px; width: 100%; }
.filter-badge { background: #EAF4FF; color: #007AFF; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: bold; cursor: pointer; transition: all 0.2s; }
.filter-badge:hover { background: #FF3B30; color: white; }

/* 项目列表区 */
.projects-section { margin-top: 10px; }
.section-header { font-size: 19px; font-weight: 700; color: var(--text-main); margin-bottom: 20px; display: flex; align-items: center; gap: 10px; }
.count-badge { font-size: 14px; background: var(--science-surface-muted); padding: 2px 8px; border-radius: 10px; color: var(--text-sub); font-weight: 600; }
.project-grid-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }

.mission-card { padding: 0; overflow: hidden; display: flex; cursor: pointer; background: var(--science-surface); border-radius: 8px; border: 1px solid var(--border-soft); transition: transform 0.2s; }
.mission-card:hover { transform: translateY(-5px); box-shadow: 0 10px 30px rgba(0,0,0,0.08); }
.card-left-border { width: 6px; }

/* 🟢 5. 核心修改：行业左边框颜色映射 */
.mission-card.MILITARY .card-left-border { background: #FF3B30; }     /* 军工: 红 */
.mission-card.AI_FOR_SCIENCE .card-left-border { background: #007AFF; } /* AI: 蓝 */
.mission-card.MEDICAL .card-left-border { background: #30B0C7; }        /* 医药: 青 */
.mission-card.INDUSTRIAL .card-left-border { background: #34C759; }     /* 工业: 绿 */
.mission-card.SWARM_INTEL .card-left-border { background: #AF52DE; }    /* 群智: 紫 */

.mission-content { padding: 20px; flex: 1; }
.mission-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; gap: 6px; }
.mission-code { font-size: 11px; font-weight: 600; color: var(--text-sub); opacity: 0.8; }
.flow-type-badge { font-size: 10px; font-weight: 600; padding: 2px 6px; border-radius: 4px; white-space: nowrap; }
.flow-type-badge.flow-project { background: #e8f5e9; color: #2e7d32; }
.flow-type-badge.flow-product { background: #e3f2fd; color: #1565c0; }
.flow-type-badge.flow-research { background: #f3e5f5; color: #7b1fa2; }
.status-badge { font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 10px; background: var(--science-surface-muted); color: var(--text-sub); }
.mission-name { font-size: 17px; font-weight: 600; color: var(--text-main); margin: 0 0 6px 0; letter-spacing: -0.3px; }
.mission-desc { font-size: 13px; color: var(--text-sub); line-height: 1.4; margin-bottom: 16px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.mission-footer { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.launch-pill { font-size: 11px; font-weight: 600; background: var(--science-surface-muted); padding: 3px 8px; border-radius: 999px; color: var(--text-sub); border: 1px solid var(--border-soft); }
.pm-info { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 500; color: var(--text-main); }
.pm-avatar { width: 20px; height: 20px; border-radius: 50%; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.cost-pill { font-size: 11px; font-weight: 600; background: var(--science-surface-muted); padding: 3px 8px; border-radius: 6px; color: var(--text-sub); }
.hover-arrow { position: absolute; right: 20px; top: 50%; transform: translateY(-50%); opacity: 0; transition: all 0.3s; color: #007AFF; font-weight: bold; font-size: 18px; }
.mission-card:hover .hover-arrow { opacity: 1; transform: translate(5px, -50%); }

/* 弹窗样式 */
.modal-header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px 10px;
  background: var(--science-surface-muted);
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}
.modal-tag { font-size: 10px; font-weight: 800; color: var(--text-sub); letter-spacing: 2px; text-transform: uppercase; }
.modal-close-btn { background: none; border: 1px solid var(--border-soft); padding: 4px 8px; border-radius: 4px; font-size: 10px; color: var(--text-sub); cursor: pointer; transition: all 0.2s; }
.modal-close-btn:hover { background: #fee2e2; color: #ef4444; border-color: #fee2e2; }

:global(.el-overlay .el-dialog.command-modal) {
  border-radius: 12px;
  overflow: hidden;
  background: var(--science-canvas) !important;
  color: var(--text-main);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-md);
}
:global(.el-overlay .el-dialog.command-modal .el-dialog__header) { display: none; }
:global(.el-overlay .el-dialog.command-modal .el-dialog__body) {
  padding: 0;
  height: 80vh;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--science-surface-muted);
}
.modal-detail-shell { background: var(--science-canvas); min-height: calc(80vh - 44px); }
.modal-detail-shell :deep(.detail-container) {
  background: var(--science-canvas) !important;
  width: 100%;
  max-width: none;
  margin: 0 auto;
  padding: 28px 24px 36px;
  box-sizing: border-box;
}
.modal-detail-shell :deep(.panel) { background: var(--science-surface); border-color: var(--border-soft); }

.modal-detail-shell :deep(.detail-body-grid),
.modal-detail-shell :deep(.product-flow-grid),
.modal-detail-shell :deep(.main-grid),
.modal-detail-shell :deep(.file-folder-grid) {
  grid-template-columns: minmax(0, 1fr) !important;
  gap: 24px;
}

.modal-detail-shell :deep(.detail-body-grid),
.modal-detail-shell :deep(.product-flow-grid) {
  margin-top: 18px;
}

.modal-detail-shell :deep(.main-grid) {
  margin-top: 12px;
}

.modal-detail-shell :deep(.left-col),
.modal-detail-shell :deep(.right-col),
.modal-detail-shell :deep(.flow-column) {
  gap: 24px;
}

.modal-detail-shell :deep(.panel) { width: 100%; }

.modal-detail-shell :deep(.panel-header-row) {
  min-height: 36px;
  margin-bottom: 16px;
  align-items: center;
}

@media (max-width: 1280px) {
  .modal-detail-shell :deep(.detail-container) {
    padding: 22px 18px 30px;
  }
}
</style>

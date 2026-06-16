<template>
  <div class="workspace-layout">
    <div class="sidebar" :class="{ 'pm-sidebar': userStore.isManager }">

      <div class="sidebar-header">
        <div class="title">WORKSPACE</div>
        <div class="subtitle">共 {{ visibleProjects.length }} 个项目 · {{ responsibilityStats.ratio }} 主控</div>
      </div>

      <div class="project-list">
        <div v-for="p in visibleProjects" :key="p.projectId"
             class="nav-item"
             @click="goToProject(p.projectId)"
             :class="{ active: currentId == p.projectId }">

          <div class="indicator-bar" :style="{ backgroundColor: getIndustryColor(p.projectType) }"></div>

          <div class="project-info">
            <div class="header-row">
              <span class="project-name">{{ p.name }}</span>
              <span v-if="isManager(p)" class="role-badge owner">OWNER</span>
              <span v-else class="role-badge member">MEMBER</span>
            </div>

            <div class="meta-row">
              <div class="flow-tag" :class="p.flowType">
                {{ formatFlowLabel(p.flowType) }}
              </div>
              <div class="status-tag">{{ formatWorkspaceStatus(p) }}</div>
            </div>

            <div class="detail-row">
              <span class="detail-label">负责人</span>
              <span class="detail-value">{{ resolveManagerName(p) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-footer" v-if="projects.length > 0">
        <div class="ratio-label">
          <span>管理半径 ({{ responsibilityStats.manager }}/{{ projects.length }})</span>
          <span>{{ responsibilityStats.ratio }}</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: responsibilityStats.ratio }"></div>
        </div>
      </div>

    </div>

    <div class="content-area">
      <div v-if="!currentId" class="empty-state">
        <div class="empty-icon">👈 请从左侧选择项目开始工作</div>
        <div class="empty-sub">SmartLab Workspace</div>
      </div>
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component"/>
        </transition>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

// 🟢 1. 核心修改：删掉原来的 import API，改成直接引入 request 工具
import request from '@/utils/request'

import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const projects = ref([])
const managedSummary = ref(null)

// 获取当前路由 ID 用于高亮
const currentId = computed(() => route.params.id)
const visibleProjects = computed(() => projects.value.filter(p => p.costControlEnabled !== false))

// 计算权责比数据
const responsibilityStats = computed(() => {
  const total = projects.value.length
  if (total === 0) return { manager: 0, member: 0, ratio: '0%' }

  const managerCount = projects.value.filter(p => isManager(p)).length
  const ratioFromSummary = Number(managedSummary.value?.managementRadius)
  const ratio = Number.isFinite(ratioFromSummary)
    ? Math.max(0, Math.min(100, Math.round(ratioFromSummary)))
    : Math.round((managerCount / total) * 100)

  return {
    manager: managerCount,
    member: total - managerCount,
    ratio: ratio + '%'
  }
})

// 判断我是不是这个项目的“王”
const isManager = (project) => {
  const currentUserId = String(userStore.activeUserInfo?.userId || '')
  const managerId = String(project?.managerId || project?.manager?.userId || '')
  if (managerId && managerId === currentUserId) return true

  const memberRole = String(project?.myRole || project?.role || '').toUpperCase()
  return memberRole === 'MANAGER' || memberRole === 'ADMIN'
}

// 颜色映射逻辑
const getIndustryColor = (type) => {
  const colors = {
    'MILITARY': '#FF3B30',
    'AI_FOR_SCIENCE': '#007AFF',
    'MEDICAL': '#30B0C7',
    'INDUSTRIAL': '#34C759',
    'SWARM_INTEL': '#AF52DE',
    'SELF_USE': '#FF9500'
  }
  return colors[type] || '#CBD5E1'
}

const formatWorkspaceStatus = (project) => {
  const flow = String(project?.flowType || '').toUpperCase()
  if (flow === 'PRODUCT') {
    const map = {
      IDEA: '创意',
      PROMOTION: '推广',
      DEMO_EXECUTION: 'Demo',
      MEETING_DECISION: '评审',
      TESTING: '测试',
      LAUNCHED: '立项',
      SHELVED: '搁置'
    }
    return map[String(project?.productStatus || '').toUpperCase()] || '产品'
  }
  if (flow === 'RESEARCH') {
    const map = {
      INIT: '发起',
      BLUEPRINT: '蓝图',
      EXPANSION: '深化',
      DESIGN: '设计',
      EXECUTION: '执行',
      EVALUATION: '评测',
      ARCHIVE: '归档',
      SHELVED: '搁置'
    }
    return map[String(project?.researchStatus || '').toUpperCase()] || '科研'
  }
  const map = {
    INITIATED: '发起',
    IMPLEMENTING: '实施',
    SETTLEMENT: '结算',
    COMPLETED: '归档'
  }
  return map[String(project?.projectStatus || '').toUpperCase()] || '项目'
}

const formatFlowLabel = flowType => {
  const flow = String(flowType || '').toUpperCase()
  if (flow === 'PRODUCT') return '💻 产品研发'
  if (flow === 'RESEARCH') return '🧪 科研创新'
  return '📊 项目交付'
}

const fetchProjects = async () => {
  try {
    // 🟢 2. 核心修改：直接写死请求路径，不再调用外部函数
    // 这里的路径对应后端 Controller 的 @GetMapping("/workspace")
    const res = await request.get('/api/projects/workspace')

    projects.value = res.data || res || []

    const missingManagerProjects = projects.value.filter(project => !String(project?.managerName || project?.manager?.name || '').trim() && project?.projectId)
    if (missingManagerProjects.length) {
      const detailResults = await Promise.all(
        missingManagerProjects.map(async project => {
          try {
            const detailRes = await request.get(`/api/projects/${project.projectId}`)
            const detail = detailRes.data || detailRes || {}
            return {
              projectId: project.projectId,
              managerId: detail.managerId || project.managerId || '',
              managerName: detail.managerName || detail.manager?.name || detail.manager?.username || ''
            }
          } catch {
            return null
          }
        })
      )
      const detailMap = new Map(detailResults.filter(Boolean).map(item => [item.projectId, item]))
      projects.value = projects.value.map(project => {
        const detail = detailMap.get(project.projectId)
        if (!detail) return project
        return {
          ...project,
          managerId: detail.managerId || project.managerId || '',
          managerName: detail.managerName || project.managerName || ''
        }
      })
    }

    try {
      const summaryRes = await request.get('/api/projects/managed/summary')
      managedSummary.value = summaryRes.data || summaryRes || null
    } catch {
      managedSummary.value = null
    }
    ensureActiveProjectSelection()
    console.log('侧边栏加载成功:', projects.value)
  } catch (error) {
    console.error('加载项目失败:', error)
  }
}

onMounted(fetchProjects)

const ensureActiveProjectSelection = () => {
  if (!projects.value.length) return
  const routeProjectId = String(route.params.id || '')
  const hasMatchedRouteProject = visibleProjects.value.some(project => String(project.projectId) === routeProjectId)
  if (hasMatchedRouteProject) return

  const firstProjectId = String(visibleProjects.value[0]?.projectId || '')
  if (!firstProjectId) return
  router.replace(`/workspace/project/${firstProjectId}`)
}

const goToProject = (id) => {
  if (id) {
    router.push(`/workspace/project/${id}`)
  }
}

const resolveManagerName = (project) => {
  const direct = String(project?.managerName || project?.manager?.name || project?.manager?.username || '').trim()
  if (direct) return direct

  if (isManager(project)) {
    return userStore.activeUserInfo?.name || userStore.activeUserInfo?.username || '当前用户'
  }

  const managerId = String(project?.managerId || project?.manager?.userId || '').trim()
  if (managerId && managerId === String(userStore.activeUserInfo?.userId || '')) {
    return userStore.activeUserInfo?.name || userStore.activeUserInfo?.username || '当前用户'
  }

  const memberMatch = (project?.members || []).find(member => String(member?.userId || member?.id || '') === managerId)
  if (memberMatch?.name) return memberMatch.name

  return '未指派'
}
</script>

<style scoped>
/* --- 布局容器 --- */
.workspace-layout {
  display: flex;
  height: calc(100vh - 60px); /* 假设导航栏高度 60px，或者保留你的 var(--nav-height) */
  background: var(--science-canvas); /* 整体背景色 */
  overflow: hidden;
}

/* --- 侧边栏整体 --- */
.sidebar {
  width: 260px; /* 稍微加宽一点，给标签留空间 */
  background: var(--science-surface);
  border-right: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column; /* 关键：让 footer 能推到底部 */
  padding: 16px 12px;
  box-shadow: 4px 0 24px rgba(0,0,0,0.02);
  z-index: 10;
}

/* --- 侧边栏头部 --- */
.sidebar-header {
  margin-bottom: 20px;
  padding: 0 8px;
}

.sidebar-header .title {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.sidebar-header .subtitle {
  font-size: 11px;
  color: var(--text-sub);
  font-weight: 500;
}

/* --- 项目列表区 --- */
.project-list {
  flex: 1; /* 占据剩余空间 */
  overflow-y: auto; /* 只有列表滚动，头部和底部固定 */
  padding-right: 4px; /* 防止滚动条遮挡 */
}

/* --- 导航卡片项 --- */
.nav-item {
  position: relative; /* 为绝对定位的色条做参照 */
  display: flex;
  align-items: center;
  padding: 12px 14px;
  margin-bottom: 8px;
  cursor: pointer;
  border-radius: 10px;
  background: var(--science-surface-muted); /* 默认浅灰背景 */
  border: 1px solid transparent;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-item:hover {
  background: var(--science-canvas);
  transform: translateX(2px);
}

.nav-item.active {
  background: var(--science-surface);
  border-color: var(--border-soft);
  box-shadow: 0 4px 12px rgba(148, 163, 184, 0.15);
}

/* 🟢 新增：左侧指示色条 */
.indicator-bar {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  /* 背景色由 inline-style 动态控制 */
}

/* --- 项目信息布局 --- */
.project-info {
  flex: 1;
  margin-left: 10px; /* 避开左侧色条 */
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 第一行：名称 + 徽章 */
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100px;
}

.nav-item.active .project-name {
  color: var(--text-main);
  font-weight: 700;
}

/* 🟢 新增：权责徽章样式 */
.role-badge {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
  text-transform: uppercase;
  transform: scale(0.9); /* 稍微缩小一点更精致 */
  transform-origin: right center;
}

.role-badge.owner {
  background-color: #FEF3C7; /* 浅金背景 */
  color: #D97706;          /* 深金文字 */
  border: 1px solid #FCD34D;
}

.role-badge.member {
  background-color: var(--science-surface-muted); /* 浅灰背景 */
  color: var(--text-sub);          /* 深灰文字 */
  border: 1px solid var(--border-soft);
}

/* 第二行：类型标签 */
.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.flow-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.flow-tag.PRODUCT {
  color: #3B82F6;
  background: rgba(59, 130, 246, 0.05);
}

.flow-tag.PROJECT {
  color: #10B981;
  background: rgba(16, 185, 129, 0.05);
}

.status-tag {
  font-size: 10px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--science-surface);
  border: 1px solid var(--border-soft);
  color: var(--text-sub);
  font-weight: 700;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--text-sub);
}

.detail-label {
  opacity: 0.75;
}

.detail-value {
  max-width: 120px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-main);
}

/* --- 🟢 新增：底部统计条区域 --- */
.sidebar-footer {
  margin-top: auto; /* 自动推到底部 */
  padding-top: 16px;
  border-top: 1px solid var(--border-soft);
}

.ratio-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-sub);
  margin-bottom: 6px;
  font-weight: 600;
}

.progress-track {
  height: 6px;
  background: var(--science-surface-muted);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3B82F6 0%, #8B5CF6 100%); /* 蓝紫渐变，科技感强 */
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

/* --- 右侧内容区 --- */
.content-area {
  flex: 1;
  background: var(--science-canvas); /* 稍微带点灰，区分层级 */
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
}

/* 空状态 */
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
}

.empty-icon {
  font-size: 14px;
  margin-bottom: 8px;
}

.empty-sub {
  font-size: 12px;
  font-weight: 300;
  letter-spacing: 2px;
  opacity: 0.6;
}

/* 路由切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(5px);
}
</style>

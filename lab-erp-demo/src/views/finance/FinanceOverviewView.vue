<template>
  <section class="cockpit-shell" :class="{ 'cockpit-shell-fullscreen': isFullscreenMode }">
    <div v-if="!isFullscreenMode" class="cockpit-avatar-zone">
      <el-dropdown trigger="click" @command="handleAvatarCommand">
        <div class="avatar-trigger">
          <img :src="displayAvatar" alt="avatar" class="avatar-img">
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="switch-view">切换视角</el-dropdown-item>
            <el-dropdown-item v-if="!isFullscreenMode" command="enter-fullscreen">进入全屏驾驶舱</el-dropdown-item>
            <el-dropdown-item v-else command="exit-fullscreen">退出全屏</el-dropdown-item>
            <el-dropdown-item command="logout" style="color:#FF3B30">退出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- Main Three-Column View -->
    <main v-if="!selectedFlow" class="cockpit-main">
      <div class="flow-columns">
        <div class="flow-column flow-project" @click="selectFlow('projects')">
          <div class="flow-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
              <rect x="3" y="3" width="18" height="18" rx="2"/>
              <path d="M9 9h6M9 12h6M9 15h4"/>
            </svg>
          </div>
          <h2 class="flow-title">项目交付</h2>
          <p class="flow-desc">PROJECT FLOW</p>
          <div class="flow-stat">
            <span class="stat-num">{{ dashboardData?.projects?.length || 0 }}</span>
            <span class="stat-label">个在研</span>
          </div>
          <div class="flow-enter">
            <span>进入驾驶舱</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
          <div class="flow-actor" aria-hidden="true">
            <div class="actor-fallback actor-fallback-blue">👨‍💼</div>
          </div>
        </div>

        <div class="flow-column flow-product" @click="selectFlow('products')">
          <div class="flow-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5M2 12l10 5 10-5"/>
            </svg>
          </div>
          <h2 class="flow-title">产品研发</h2>
          <p class="flow-desc">PRODUCT FLOW</p>
          <div class="flow-stat">
            <span class="stat-num">{{ dashboardData?.products?.length || 0 }}</span>
            <span class="stat-label">个在研</span>
          </div>
          <div class="flow-enter">
            <span>进入驾驶舱</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
          <div class="flow-actor" aria-hidden="true">
            <div class="actor-fallback actor-fallback-purple">👩‍💻</div>
          </div>
        </div>

        <div class="flow-column flow-research" @click="selectFlow('research')">
          <div class="flow-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
              <path d="M11 8v6M8 11h6"/>
            </svg>
          </div>
          <h2 class="flow-title">科研创新</h2>
          <p class="flow-desc">RESEARCH FLOW</p>
          <div class="flow-stat">
            <span class="stat-num">{{ dashboardData?.research?.length || 0 }}</span>
            <span class="stat-label">个在研</span>
          </div>
          <div class="flow-enter">
            <span>进入驾驶舱</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
          <div class="flow-actor" aria-hidden="true">
            <div class="actor-fallback actor-fallback-green">🧑‍🔬</div>
          </div>
        </div>
      </div>
    </main>

    <!-- Rating Page -->
    <main v-else class="rating-main">
      <div class="rating-header">
        <button class="back-btn" @click="selectedFlow = null; selectedTier = null">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回总览
        </button>
        <h2 class="rating-title">{{ flowLabel(selectedFlow) }} — 评级页面</h2>
        <span class="rating-count">{{ currentFlowProjects.length }} 个项目</span>
      </div>

      <!-- Tier Cards -->
      <div v-if="!selectedTier" class="tier-cards">
        <div

          v-for="tier in ['S','C','B','A','N']"
          :key="tier"
          class="tier-card"
          :class="'tier-card-' + tier.toLowerCase()"
          @click="selectTier(tier)"
        >
          <div class="tier-letter">{{ tier }}</div>
          <div class="tier-name">{{ tierName(tier) }}</div>
          <div class="tier-count">{{ countByTier(tier) }} 个</div>
          <div class="tier-progress">
            <div class="tier-progress-fill" :style="{ width: progressByTier(tier) + '%' }"></div>
          </div>
        </div>
      </div>

      <!-- Film Strip Carousel -->
      <div v-else class="film-strip-view">
        <div class="film-header">
          <button class="back-btn small" @click="selectedTier = null">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
            {{ tierName(selectedTier) }} 级项目
          </button>
          <span class="film-count">{{ tierProjects.length }} 个项目</span>
        </div>

        <div class="film-strip-wrapper">
          <button class="film-nav film-nav-prev" @click="prevCard" :disabled="filmIndex <= 0">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
              <path d="M15 18l-6-6 6-6"/>
            </svg>
          </button>

          <div class="film-track">
            <div class="film-cards" :style="{ transform: `translateX(-${filmIndex * 320}px)` }">
              <div
                v-for="(proj, idx) in tierProjects"
                :key="proj.projectId"
                class="film-card"
                :class="{ 'film-card-active': idx === filmIndex }"
                @click="openProjectCard(proj, idx)"
              >
                <div class="film-card-header">
                  <span class="film-tier-badge" :class="'tier-' + selectedTier.toLowerCase()">{{ selectedTier }}</span>
                  <span class="film-status-badge">{{ proj.status || '未知' }}</span>
                </div>
                <div class="film-card-industry">{{ formatIndustry(proj.projectType) }}</div>
                <h3 class="film-card-name">{{ proj.name }}</h3>
                <div class="film-card-desc">{{ proj.description || '暂无描述' }}</div>
                <div class="film-card-manager">
                  <div class="manager-avatar">
                    <img :src="'https://api.dicebear.com/7.x/avataaars/svg?seed=' + proj.projectId" alt="avatar"/>
                  </div>
                  <div class="manager-info">
                    <span class="manager-label">负责人</span>
                    <span class="manager-name">{{ resolveOwnerName(proj) }}</span>
                  </div>
                </div>
                <div class="film-card-members">
                  <span class="members-label">当前成员</span>
                  <div class="members-list">
                    <span v-for="m in (proj.members || []).slice(0, 5)" :key="m.userId" class="member-chip" :title="m.name + ' · ' + m.role">
                      {{ shortMemberTag(m) }}
                    </span>
                    <span v-if="(proj.members || []).length > 5" class="member-chip more">+{{ (proj.members || []).length - 5 }}</span>
                  </div>
                </div>
                <div class="film-card-footer">
                  <span v-if="proj.budget" class="film-budget">预算: ¥{{ (Number(proj.budget) / 10000).toFixed(1) }}万</span>
                  <span v-if="proj.cost" class="film-cost">消耗: ¥{{ (Number(proj.cost) / 10000).toFixed(1) }}万</span>
                </div>
              </div>
            </div>
          </div>

          <button class="film-nav film-nav-next" @click="nextCard" :disabled="filmIndex >= tierProjects.length - 1">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </button>
        </div>

        <!-- Film perforations decoration -->
        <div class="film-perforations film-perf-top">
          <span v-for="i in 20" :key="i" class="perf-hole"></span>
        </div>
        <div class="film-perforations film-perf-bottom">
          <span v-for="i in 20" :key="i" class="perf-hole"></span>
        </div>

        <!-- Dot indicators -->
        <div class="film-dots" v-if="tierProjects.length > 1">
          <span
            v-for="(_, idx) in tierProjects"
            :key="idx"
            class="film-dot"
            :class="{ active: idx === filmIndex }"
            @click="filmIndex = idx"
          ></span>
        </div>

      </div>
    </main>

    <!-- Loading overlay -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>加载数据中...</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFinanceDashboard } from '@/api/finance/overview'
import { useUserStore } from '@/stores/userStore'

const loading = ref(false)
const dashboardData = ref(null)
const selectedFlow = ref(null)
const selectedTier = ref(null)
const filmIndex = ref(0)
const autoRefreshTimer = ref(null)
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tierNames = {
  S: '高难度',
  A: '较高难度',
  B: '中等难度',
  C: '低难度',
  N: '重点项目'
}
const isFullscreenMode = computed(() => route.query?.fullscreen === '1')
const displayAvatar = computed(() => userStore.activeUserInfo?.hiddenAvatar
  ? 'https://api.dicebear.com/7.x/shapes/svg?seed=masked'
  : (userStore.activeUserInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'))

const flowLabel = (flow) => ({ projects: '项目交付', products: '产品研发', research: '科研创新' }[flow] || flow)
const tierName = (tier) => tierNames[tier] || tier

const currentFlowProjects = computed(() => {
  if (!dashboardData.value || !selectedFlow.value) return []
  return dashboardData.value[selectedFlow.value] || []
})

const tierProjects = computed(() => {
  if (!currentFlowProjects.value.length || !selectedTier.value) return []
  return currentFlowProjects.value.filter(p => {
    const tier = p.projectTier || 'N'
    return tier === selectedTier.value
  })
})

const countByTier = (tier) => {
  return currentFlowProjects.value.filter(p => (p.projectTier || 'N') === tier).length
}

const progressByTier = (tier) => {
  const total = currentFlowProjects.value.length
  if (!total) return 0
  return Math.round((countByTier(tier) / total) * 100)
}

const formatIndustry = (type) => {
  const map = {
    BUSINESS: '业务',
    MILITARY: '军工', AI_FOR_SCIENCE: 'AI for Science', MEDICAL: '医药',
    INDUSTRIAL: '工业', SWARM_INTEL: '群体智能', OTHER: '其他'
  }
  return map[type] || type || '未分类'
}

const selectFlow = (flow) => {
  selectedFlow.value = flow
  selectedTier.value = null
  filmIndex.value = 0
}

const selectTier = (tier) => {
  selectedTier.value = tier
  filmIndex.value = 0
}

const prevCard = () => {
  if (filmIndex.value > 0) filmIndex.value--
}

const nextCard = () => {
  if (filmIndex.value < tierProjects.value.length - 1) filmIndex.value++
}

const pickMemberByRoles = (proj, roles) => {
  const roleSet = new Set((roles || []).map(role => String(role || '').toUpperCase()))
  return (proj?.members || []).find(member => roleSet.has(String(member?.role || '').toUpperCase()))
}

const resolveOwnerName = proj => {
  const primaryOwnerName = String(proj?.primaryOwnerName || '').trim()
  if (primaryOwnerName) return primaryOwnerName

  const managerName = String(proj?.managerName || '').trim()
  if (managerName) return managerName

  const host = pickMemberByRoles(proj, ['HOST'])
  if (host?.name) return host.name

  const managerLike = pickMemberByRoles(proj, ['MANAGER', 'PM', 'LEAD'])
  if (managerLike?.name) return managerLike.name

  const initiator = pickMemberByRoles(proj, ['ADMIN', 'OWNER', 'INITIATOR', 'BUSINESS'])
  if (initiator?.name) return initiator.name

  const fallbackMember = (proj?.members || [])[0]
  return fallbackMember?.name || '发起人待同步'
}

const shortMemberTag = member => {
  const name = String(member?.name || '').trim()
  const uid = String(member?.userId || '').trim()
  const fallback = String(member?.role || '').trim()
  const source = name || uid || fallback || '?'
  return source.slice(0, 2)
}

const openProjectCard = (_, idx) => {
  filmIndex.value = idx
}

const refreshData = async () => {
  await loadDashboard()
}

const requestFullscreen = async () => {
  const element = document.documentElement
  const enter = element.requestFullscreen || element.webkitRequestFullscreen || element.msRequestFullscreen
  if (typeof enter === 'function') {
    await enter.call(element)
  }
}

const exitFullscreen = async () => {
  const exit = document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen
  if (typeof exit === 'function') {
    await exit.call(document)
  }
}

const ensureWindowsFullscreen = async () => {
  if (!isFullscreenMode.value) return
  const isWindows = /windows/i.test(navigator.userAgent)
  if (!isWindows) return
  try {
    await requestFullscreen()
  } catch {
    // browser may block fullscreen without direct interaction
  }
}

const handleAvatarCommand = async (command) => {
  if (command === 'switch-view') {
    await router.push('/finance/classic')
    return
  }
  if (command === 'enter-fullscreen') {
    await router.push({ path: '/finance/overview', query: { fullscreen: '1' } })
    await ensureWindowsFullscreen()
    return
  }
  if (command === 'exit-fullscreen') {
    await exitFullscreen()
    await router.push({ path: '/finance/overview' })
    return
  }
  if (command === 'logout') {
    userStore.logout()
    await router.push('/login')
  }
}

const startAutoRefresh = () => {
  if (autoRefreshTimer.value) clearInterval(autoRefreshTimer.value)
  autoRefreshTimer.value = setInterval(() => {
    refreshData()
  }, 30 * 60 * 1000)
}

const handleGlobalRefresh = async () => {
  await loadDashboard()
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getFinanceDashboard()
    dashboardData.value = res?.data || res || { projects: [], products: [], research: [] }
    const timestamp = new Date().toISOString()
    localStorage.setItem('finance-dashboard-last-updated', timestamp)
    window.dispatchEvent(new Event('finance-dashboard-updated'))
  } catch (e) {
    console.error('加载驾驶舱数据失败', e)
  } finally {
    loading.value = false
  }
}

watch(selectedFlow, flow => {
  filmIndex.value = 0
  if (!flow) {
    selectedTier.value = null
  }
})

watch(selectedTier, () => {
  filmIndex.value = 0
})

watch(tierProjects, projects => {
  if (!projects.length) {
    return
  }
  const safeIndex = Math.max(0, Math.min(filmIndex.value, projects.length - 1))
  filmIndex.value = safeIndex
}, { immediate: true })

onMounted(() => {
  loadDashboard()
  ensureWindowsFullscreen()
  startAutoRefresh()
  window.addEventListener('finance-global-refresh', handleGlobalRefresh)
})

onBeforeUnmount(() => {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
  window.removeEventListener('finance-global-refresh', handleGlobalRefresh)
})

watch(isFullscreenMode, () => {
  ensureWindowsFullscreen()
})
</script>

<style scoped>
.cockpit-shell {
  position: relative;
  min-height: 100vh;
  background: #0a0f1e;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow-x: clip;
  overflow-y: visible;
  isolation: isolate;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

.cockpit-shell-fullscreen {
  min-height: 100vh;
  position: fixed;
  inset: 0;
  overflow: hidden;
}

.cockpit-shell::before,
.cockpit-shell::after {
  content: '';
  position: absolute;
  inset: -12%;
  pointer-events: none;
}

.cockpit-shell::before {
  background:
    radial-gradient(circle at 14% 22%, rgba(56, 189, 248, 0.16), transparent 32%),
    radial-gradient(circle at 80% 16%, rgba(167, 139, 250, 0.16), transparent 34%),
    radial-gradient(circle at 62% 82%, rgba(52, 211, 153, 0.14), transparent 36%);
  animation: ambientDrift 36s ease-in-out infinite alternate;
  z-index: 0;
}

.cockpit-shell::after {
  background: linear-gradient(120deg, rgba(255,255,255,0.035), rgba(255,255,255,0));
  animation: ambientPulse 14s ease-in-out infinite;
  z-index: 0;
}

.cockpit-avatar-zone {
  position: fixed;
  right: 24px;
  top: 16px;
  z-index: 1200;
}

.avatar-trigger {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  overflow: hidden;
  cursor: pointer;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Header */
.cockpit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  background: linear-gradient(135deg, #0f1a2e 0%, #1a2540 100%);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.header-brand { display: flex; align-items: center; gap: 16px; }
.brand-icon { width: 44px; height: 44px; background: linear-gradient(135deg, #3b82f6, #8b5cf6); border-radius: 12px; display: flex; align-items: center; justify-content: center; }
.brand-icon svg { width: 24px; height: 24px; stroke: white; fill: none; }
.brand-title { font-size: 22px; font-weight: 700; margin: 0; letter-spacing: -0.5px; }
.brand-sub { font-size: 12px; color: #6b7280; margin: 0; }
.header-actions { display: flex; gap: 12px; }
.header-btn {
  display: flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  color: #9ca3af; padding: 8px 16px; border-radius: 8px; cursor: pointer;
  font-size: 13px; transition: all 0.2s;
}
.header-btn:hover { background: rgba(255,255,255,0.1); color: #fff; }

/* Main Content */
.cockpit-main, .rating-main {
  flex: 1;
  overflow: visible;
  padding: 72px 40px 40px;
  position: relative;
  z-index: 1;
}

/* Three Flow Columns */
.flow-columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
  min-height: calc(100vh - 120px);
  align-items: stretch;
}

.cockpit-shell-fullscreen .cockpit-main,
.cockpit-shell-fullscreen .rating-main {
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
}

.cockpit-shell-fullscreen .cockpit-main::-webkit-scrollbar,
.cockpit-shell-fullscreen .rating-main::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.flow-column {
  border-radius: 24px;
  padding: 36px 32px 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: visible;
  border: none;
}

.flow-column::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.4s;
}

.flow-column:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 30px 60px rgba(0,0,0,0.3);
}
.flow-column:hover::before { opacity: 1; }

.flow-project {
  background: linear-gradient(145deg, #0d1f3c 0%, #1a3a6b 100%);
}
.flow-project::before { background: linear-gradient(145deg, rgba(59,130,246,0.15), rgba(59,130,246,0.05)); }

.flow-product {
  background: linear-gradient(145deg, #1a0d3c 0%, #3a1a6b 100%);
}
.flow-product::before { background: linear-gradient(145deg, rgba(139,92,246,0.15), rgba(139,92,246,0.05)); }

.flow-research {
  background: linear-gradient(145deg, #0d3c1a 0%, #1a6b3a 100%);
}
.flow-research::before { background: linear-gradient(145deg, rgba(16,185,129,0.15), rgba(16,185,129,0.05)); }

.flow-icon { margin-bottom: 24px; opacity: 0.9; }
.flow-title { font-size: 32px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.5px; }
.flow-desc { font-size: 11px; color: rgba(255,255,255,0.4); letter-spacing: 3px; margin: 0 0 24px; }
.flow-stat { display: flex; align-items: baseline; gap: 6px; margin-bottom: 16px; }
.stat-num { font-size: 56px; font-weight: 800; line-height: 1; letter-spacing: -2px; }
.stat-label { font-size: 14px; color: rgba(255,255,255,0.5); }

.flow-tier-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-bottom: 24px; }
.tier-chip {
  font-size: 11px; font-weight: 700; padding: 4px 10px;
  border-radius: 20px; letter-spacing: 0.5px;
}
.tier-chip.tier-S { background: rgba(234,179,8,0.2); color: #eab308; }
.tier-chip.tier-A { background: rgba(59,130,246,0.2); color: #60a5fa; }
.tier-chip.tier-B { background: rgba(34,197,94,0.2); color: #4ade80; }
.tier-chip.tier-C { background: rgba(249,115,22,0.2); color: #fb923c; }
.tier-chip.tier-N { background: rgba(107,114,128,0.2); color: #9ca3af; }

.flow-enter {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.4);
  transition: color 0.3s;
  margin-bottom: 6px;
}
.flow-column:hover .flow-enter { color: rgba(255,255,255,0.8); }

.flow-actor {
  position: absolute;
  left: 50%;
  bottom: 20px;
  width: 128px;
  height: 92px;
  transform: translateX(-50%);
  pointer-events: none;
  animation: actorBob 2.6s ease-in-out infinite;
}

.actor-fallback {
  width: 100%;
  height: 100%;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 58px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.actor-fallback-blue {
  background: radial-gradient(circle at 35% 25%, rgba(96, 165, 250, 0.5), rgba(15, 23, 42, 0.8));
}

.actor-fallback-purple {
  background: radial-gradient(circle at 35% 25%, rgba(167, 139, 250, 0.5), rgba(30, 27, 75, 0.82));
}

.actor-fallback-green {
  background: radial-gradient(circle at 35% 25%, rgba(74, 222, 128, 0.45), rgba(6, 78, 59, 0.82));
}

.actor-head {
  position: absolute;
  top: 2px;
  left: 54px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #ffe2c9 0, #f6c7a2 60%, #e9b487 100%);
  box-shadow: 0 0 0 2px rgba(255,255,255,0.1);
}

.actor-eye {
  position: absolute;
  top: 8px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #111827;
}

.eye-left { left: 6px; }
.eye-right { left: 14px; }

.actor-nose {
  position: absolute;
  left: 10px;
  top: 11px;
  width: 4px;
  height: 6px;
  border-radius: 2px;
  background: rgba(170, 93, 54, 0.45);
}

.actor-mouth {
  position: absolute;
  left: 7px;
  top: 17px;
  width: 8px;
  height: 3px;
  border-radius: 999px;
  background: #8b1e3f;
}

.actor-body {
  position: absolute;
  top: 24px;
  left: 44px;
  width: 44px;
  height: 52px;
  border-radius: 12px;
  background: linear-gradient(170deg, rgba(255,255,255,0.22), rgba(255,255,255,0.1));
}

.actor-leg {
  position: absolute;
  top: 73px;
  width: 9px;
  height: 24px;
  border-radius: 8px;
  background: #cbd5e1;
}

.leg-left { left: 52px; }
.leg-right { left: 70px; }

.actor-arm {
  position: absolute;
  top: 34px;
  width: 30px;
  height: 8px;
  border-radius: 999px;
  background: #f2c8aa;
  transform-origin: 4px 4px;
}

.arm-left { left: 24px; transform: rotate(18deg); }
.arm-right { left: 80px; transform: rotate(-18deg); }

.actor-project .arm-right {
  animation: armWave 0.85s ease-in-out infinite;
}

.actor-project .actor-mouth {
  animation: mouthTalk 0.42s linear infinite;
}

.actor-phone {
  position: absolute;
  width: 11px;
  height: 18px;
  left: 92px;
  top: 21px;
  border-radius: 3px;
  background: #111827;
  border: 1px solid rgba(148, 163, 184, 0.45);
}

.actor-phone::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 2px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #22d3ee;
}

.actor-spit {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(186, 230, 253, 0.9);
  opacity: 0;
}

.spit-1 { left: 73px; top: 18px; animation: spitJet 2.1s linear infinite; }
.spit-2 { left: 76px; top: 21px; animation: spitJet 2.1s linear infinite 1.05s; }

.actor-product .arm-left { animation: typingTap 0.42s ease-in-out infinite; }
.actor-product .arm-right { animation: typingTap 0.42s ease-in-out infinite 0.2s; }

.actor-cup {
  position: absolute;
  left: 86px;
  top: 44px;
  width: 15px;
  height: 14px;
  border-radius: 0 0 5px 5px;
  border: 2px solid #fde68a;
  border-top: 0;
  background: rgba(255, 248, 220, 0.2);
  animation: sipTilt 2.4s ease-in-out infinite;
}

.actor-cup::after {
  content: '';
  position: absolute;
  right: -6px;
  top: 2px;
  width: 5px;
  height: 7px;
  border: 2px solid #fde68a;
  border-left: 0;
  border-radius: 0 7px 7px 0;
}

.actor-steam {
  position: absolute;
  width: 4px;
  height: 14px;
  border-radius: 10px;
  background: rgba(255,255,255,0.65);
  opacity: 0;
}

.steam-1 { left: 90px; top: 29px; animation: steamRise 1.5s ease-in-out infinite; }
.steam-2 { left: 97px; top: 27px; animation: steamRise 1.5s ease-in-out infinite 0.65s; }

.actor-keyboard {
  position: absolute;
  left: 40px;
  top: 76px;
  width: 52px;
  height: 12px;
  border-radius: 8px;
  background:
    repeating-linear-gradient(90deg, rgba(255,255,255,0.2) 0 2px, transparent 2px 5px),
    linear-gradient(180deg, rgba(203,213,225,0.92), rgba(100,116,139,0.86));
}

.actor-mask {
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, rgba(51, 65, 85, 0.92), rgba(15, 23, 42, 0.96));
  border: 1px solid rgba(148, 163, 184, 0.5);
}

.actor-research .actor-mouth {
  animation: mouthTalk 0.5s linear infinite;
  background: #94a3b8;
}

.actor-research .actor-eye {
  background: #e2e8f0;
}

.actor-research .actor-nose {
  background: rgba(203, 213, 225, 0.55);
}

.actor-welder {
  position: absolute;
  left: 86px;
  top: 43px;
  width: 23px;
  height: 6px;
  border-radius: 999px;
  background: linear-gradient(90deg, #94a3b8, #475569);
  transform: rotate(-25deg);
  animation: weldPulse 0.8s ease-in-out infinite;
}

.actor-welder::after {
  content: '';
  position: absolute;
  right: -4px;
  top: 1px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fde68a;
}

.actor-spark {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  opacity: 0;
  box-shadow: 0 0 10px rgba(245,158,11,0.9);
}

.spark-1 { left: 106px; top: 46px; animation: sparkFlash 0.9s linear infinite; }
.spark-2 { left: 110px; top: 50px; animation: sparkFlash 0.9s linear infinite 0.25s; }
.spark-3 { left: 102px; top: 54px; animation: sparkFlash 0.9s linear infinite 0.45s; }

@keyframes actorBob {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-4px); }
}

@keyframes armWave {
  0%, 100% { transform: rotate(-24deg); }
  50% { transform: rotate(16deg); }
}

@keyframes mouthTalk {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(1.9); }
}

@keyframes spitJet {
  0%, 72% { opacity: 0; transform: translate(0, 0) scale(0.6); }
  78% { opacity: 1; transform: translate(8px, 1px) scale(1); }
  100% { opacity: 0; transform: translate(17px, -3px) scale(0.3); }
}

@keyframes sipTilt {
  0%, 70%, 100% { transform: rotate(0deg); }
  80% { transform: rotate(-14deg); }
}

@keyframes steamRise {
  0% { opacity: 0; transform: translateY(6px); }
  40% { opacity: 0.65; }
  100% { opacity: 0; transform: translateY(-8px); }
}

@keyframes typingTap {
  0%, 100% { transform: rotate(8deg) translateY(0); }
  50% { transform: rotate(15deg) translateY(2px); }
}

@keyframes weldPulse {
  0%, 100% { box-shadow: 0 0 0 rgba(245,158,11,0); }
  50% { box-shadow: 0 0 16px rgba(245,158,11,0.65); }
}

@keyframes sparkFlash {
  0%, 50%, 100% { opacity: 0; transform: translate(0, 0) scale(0.6); }
  12% { opacity: 1; transform: translate(5px, -2px) scale(1); }
  30% { opacity: 0; transform: translate(12px, 4px) scale(0.3); }
}

@keyframes ambientDrift {
  0% { transform: translate(-2%, -1%) scale(1); }
  100% { transform: translate(3%, 2%) scale(1.08); }
}

@keyframes ambientPulse {
  0%, 100% { opacity: 0.28; }
  50% { opacity: 0.52; }
}

@media (prefers-reduced-motion: reduce) {
  .cockpit-shell::before,
  .cockpit-shell::after,
  .flow-actor,
  .flow-actor * {
    animation: none !important;
  }
}

/* Rating Page */
.rating-main {
  overflow: visible;
  display: flex;
  flex-direction: column;
}
.rating-header {
  display: flex; align-items: center; gap: 20px;
  margin-bottom: 40px; padding-bottom: 24px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.rating-title { font-size: 24px; font-weight: 700; margin: 0; }
.rating-count { font-size: 13px; color: #6b7280; margin-left: auto; }

.back-btn {
  display: flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  color: #9ca3af; padding: 8px 16px; border-radius: 8px; cursor: pointer;
  font-size: 13px; transition: all 0.2s;
}
.back-btn:hover { background: rgba(255,255,255,0.1); color: #fff; }
.back-btn.small { padding: 4px 12px; font-size: 12px; }

/* Tier Cards */
.tier-cards {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(5, minmax(130px, 1fr));
  justify-content: center;
  align-items: center;
  gap: 18px;
  width: min(1200px, 92vw);
  margin: 0 auto;
  padding: 14vh 0 3vh;
}

.tier-card {
  border-radius: 14px 14px 0 0;
  padding: 18px 14px 56px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  justify-content: flex-start;
  position: relative;
  border: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.03);
  min-height: min(62vh, 520px);
}
.tier-card:hover {
  transform: translateY(-10px);
  box-shadow: 0 20px 40px rgba(0,0,0,0.3);
}

.tier-card::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 24px solid transparent;
  border-right: 24px solid transparent;
  border-bottom: 24px solid var(--science-dark-bg);
  opacity: 0.9;
}

.tier-card-S { background: linear-gradient(145deg, #2d2005, #4d3508); border-color: rgba(234,179,8,0.3); }
.tier-card-S:hover { background: linear-gradient(145deg, #3d2a07, #5d4510); }
.tier-card-A { background: linear-gradient(145deg, #0d2047, #1a3a6b); border-color: rgba(59,130,246,0.3); }
.tier-card-A:hover { background: linear-gradient(145deg, #152d5a, #204580); }
.tier-card-B { background: linear-gradient(145deg, #0a2e1a, #1a4a2a); border-color: rgba(34,197,94,0.3); }
.tier-card-B:hover { background: linear-gradient(145deg, #103d22, #1a5a32); }
.tier-card-C { background: linear-gradient(145deg, #2e1a0a, #4a2a1a); border-color: rgba(249,115,22,0.3); }
.tier-card-C:hover { background: linear-gradient(145deg, #3d2210, #5a3520); }
.tier-card-N { background: linear-gradient(145deg, #1a1a2e, #2a2a3a); border-color: rgba(107,114,128,0.3); }
.tier-card-N:hover { background: linear-gradient(145deg, #222233, #333344); }

.tier-letter {
  min-width: 86px;
  font-size: 74px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 1px;
  margin: 11vh 0 12px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  text-align: center;
}
.tier-card-S .tier-letter { color: #eab308; text-shadow: 0 0 40px rgba(234,179,8,0.5); }
.tier-card-A .tier-letter { color: #60a5fa; text-shadow: 0 0 40px rgba(59,130,246,0.5); }
.tier-card-B .tier-letter { color: #4ade80; text-shadow: 0 0 40px rgba(34,197,94,0.5); }
.tier-card-C .tier-letter { color: #fb923c; text-shadow: 0 0 40px rgba(249,115,22,0.5); }
.tier-card-N .tier-letter { color: #9ca3af; text-shadow: 0 0 40px rgba(107,114,128,0.5); }

.tier-name { font-size: 18px; font-weight: 700; margin: 0 0 12px; min-width: 0; }
.tier-count { font-size: 14px; color: rgba(255,255,255,0.85); margin: 0 0 16px; }
.tier-progress { width: 100%; min-width: 0; height: 10px; background: rgba(255,255,255,0.1); border-radius: 999px; overflow: hidden; margin-top: auto; }
.tier-progress-fill { height: 100%; border-radius: 2px; transition: width 0.5s; }
.tier-card-S .tier-progress-fill { background: #eab308; }
.tier-card-A .tier-progress-fill { background: #60a5fa; }
.tier-card-B .tier-progress-fill { background: #4ade80; }
.tier-card-C .tier-progress-fill { background: #fb923c; }
.tier-card-N .tier-progress-fill { background: #9ca3af; }

/* Film Strip */
.film-strip-view { position: relative; }
.film-header { display: flex; align-items: center; gap: 16px; margin-bottom: 32px; }
.film-count { font-size: 13px; color: #6b7280; margin-left: auto; }

.film-strip-wrapper {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 32px 0;
}

.film-track {
  flex: 1;
  overflow: hidden;
  border-radius: 16px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  padding: 24px 16px;
}

.film-cards {
  display: flex;
  gap: 16px;
  transition: transform 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.film-card {
  flex: 0 0 288px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
}
.film-card:hover, .film-card-active {
  background: rgba(255,255,255,0.08);
  border-color: rgba(255,255,255,0.15);
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0,0,0,0.3);
}

.film-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.film-tier-badge {
  font-size: 12px; font-weight: 800; width: 28px; height: 28px;
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
}
.film-tier-badge.tier-s { background: rgba(234,179,8,0.2); color: #eab308; border: 1px solid rgba(234,179,8,0.4); }
.film-tier-badge.tier-a { background: rgba(59,130,246,0.2); color: #60a5fa; border: 1px solid rgba(59,130,246,0.4); }
.film-tier-badge.tier-b { background: rgba(34,197,94,0.2); color: #4ade80; border: 1px solid rgba(34,197,94,0.4); }
.film-tier-badge.tier-c { background: rgba(249,115,22,0.2); color: #fb923c; border: 1px solid rgba(249,115,22,0.4); }
.film-tier-badge.tier-n { background: rgba(107,114,128,0.2); color: #9ca3af; border: 1px solid rgba(107,114,128,0.4); }

.film-status-badge {
  font-size: 10px; font-weight: 600; padding: 3px 8px;
  border-radius: 10px; background: rgba(59,130,246,0.15); color: #60a5fa;
  border: 1px solid rgba(59,130,246,0.2);
}

.film-card-industry {
  font-size: 10px; font-weight: 600; letter-spacing: 1px;
  color: #6b7280; text-transform: uppercase; margin-bottom: 6px;
}
.film-card-name { font-size: 15px; font-weight: 700; margin: 0 0 8px; line-height: 1.3; }
.film-card-desc {
  font-size: 12px; color: #6b7280; margin-bottom: 14px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.film-card-manager { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; padding: 10px; background: rgba(255,255,255,0.03); border-radius: 10px; }
.manager-avatar img { width: 32px; height: 32px; border-radius: 50%; }
.manager-info { display: flex; flex-direction: column; }
.manager-label { font-size: 10px; color: #6b7280; }
.manager-name { font-size: 13px; font-weight: 600; }

.film-card-members { margin-bottom: 12px; }
.members-label { font-size: 10px; color: #6b7280; display: block; margin-bottom: 6px; }
.members-list { display: flex; flex-wrap: wrap; gap: 4px; }
.member-chip {
  font-size: 10px; font-weight: 600; width: 24px; height: 24px;
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: rgba(139,92,246,0.15); color: #a78bfa; border: 1px solid rgba(139,92,246,0.2);
}
.member-chip.more { background: rgba(255,255,255,0.05); color: #6b7280; border-color: rgba(255,255,255,0.1); }

.film-card-footer { display: flex; justify-content: space-between; font-size: 11px; color: #6b7280; padding-top: 10px; border-top: 1px solid rgba(255,255,255,0.05); }

.film-nav {
  flex-shrink: 0; width: 48px; height: 48px; border-radius: 50%;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  color: #9ca3af; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.film-nav:hover:not(:disabled) { background: rgba(255,255,255,0.1); color: #fff; }
.film-nav:disabled { opacity: 0.3; cursor: not-allowed; }

/* Film perforations */
.film-perforations {
  display: flex; gap: 20px; padding: 8px 16px;
  pointer-events: none;
}
.film-perf-top { margin-bottom: 4px; }
.film-perf-bottom { margin-top: 4px; }
.perf-hole {
  width: 12px; height: 12px; border-radius: 50%;
  background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.05);
  flex-shrink: 0;
}

/* Dot indicators */
.film-dots { display: flex; justify-content: center; gap: 8px; margin-top: 20px; }
.film-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255,255,255,0.2); cursor: pointer; transition: all 0.2s;
}
.film-dot.active { background: #60a5fa; transform: scale(1.3); }

@media (max-width: 900px) {
  .tier-cards {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
    width: min(94vw, 760px);
    padding: 7vh 0 4vh;
  }

  .tier-card {
    min-height: 280px;
  }

  .tier-letter {
    margin-top: 7vh;
  }

  .tier-count {
    margin: 0;
    margin-bottom: 12px;
  }

  .tier-progress {
    width: 100%;
    min-width: 0;
  }
}

/* Loading */
.loading-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  z-index: 1000;
}
.loading-spinner {
  width: 48px; height: 48px; border: 3px solid rgba(255,255,255,0.1);
  border-top-color: #60a5fa; border-radius: 50%;
  animation: spin 0.8s linear infinite; margin-bottom: 16px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loading-overlay p { font-size: 14px; color: #9ca3af; }
</style>

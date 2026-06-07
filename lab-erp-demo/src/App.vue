<template>
  <div class="app-root">
    <button
        v-if="isAuthEntry"
        class="theme-toggle floating"
        @click="toggleTheme"
        :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
    >
      {{ theme === 'dark' ? '☀' : '☾' }}
    </button>

    <div v-if="showAuthenticatedNavbar" class="tech-navbar">
      <div class="nav-left">
        <div class="logo-box">
          <span class="logo-icon">♟️</span>
          <span class="logo-text">智能博弈实验室</span>
        </div>

        <div class="role-badge" :class="badgeClass">
          {{ currentBadge }}
        </div>

        <button
          v-if="showFinanceShortcut"
          class="nav-shortcut"
          :class="{ active: route.path.startsWith('/finance') }"
          @click="router.push('/finance')"
        >
          Finance
        </button>
      </div>

      <div class="nav-right">
        <button v-if="showCreateProductAction" class="tech-btn primary" @click="$router.push('/manager/product/create')">
          <span class="icon">+</span> 发起产品
        </button>

        <button v-if="showAuthenticatedNavbar" class="mail-trigger" @click="openMessageDrawer">
          ✉️
          <span v-if="unreadMessageCount > 0" class="mail-count">+{{ unreadMessageCount > 99 ? '99+' : unreadMessageCount }}</span>
        </button>

        <AiCommandLauncher v-if="userStore.isErpLoggedIn" />

        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-trigger">
            <img
                :src="displayAvatar"
                class="avatar-hex"
            >
            <span class="arrow-down">▼</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="apple-dropdown">
              <div class="dropdown-header">{{ userStore.activeUserInfo?.name || '未定义用户' }}</div>
              <el-dropdown-item v-if="userStore.isErpLoggedIn" command="personal-procurement">🛒 个人采购申请</el-dropdown-item>
              <el-dropdown-item command="profile">👤 个人中心</el-dropdown-item>
              <el-dropdown-item v-if="showProvisionUserAction" command="provision-user">🪪 创建账号</el-dropdown-item>
              <el-dropdown-item v-if="showProvisionUserAction" command="wage-management">💰 工资管理</el-dropdown-item>
              <el-dropdown-item v-if="showExpenseReviewEntry" command="expense-review">
                📋 费用审批
                <span v-if="pendingExpenseCount > 0" class="mail-count expense-badge">+{{ pendingExpenseCount > 99 ? '99+' : pendingExpenseCount }}</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="showProvisionUserAction" command="award-badge">🏅 发放勋章</el-dropdown-item>
              <el-dropdown-item v-if="showFullscreenCockpitEntry" command="fullscreen-cockpit">🖥️ 进入全屏驾驶舱</el-dropdown-item>
              <el-dropdown-item command="theme">{{ theme === 'dark' ? '☀ 切换浅色模式' : '☾ 切换深色模式' }}</el-dropdown-item>
              <el-dropdown-item command="switch" divided>🔄 切换视角</el-dropdown-item>
              <el-dropdown-item command="logout" divided style="color:#FF3B30">🚪 退出</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <router-view v-slot="{ Component }">
      <transition name="fade-scale" mode="out-in">
        <component :is="Component" :key="$route.fullPath" />
      </transition>
    </router-view>

    <el-drawer v-model="showMessageDrawer" size="440px" custom-class="msg-drawer">
      <template #header>
        <div class="msg-drawer-header">
          <span class="msg-drawer-title">站内消息</span>
          <span v-if="unreadMessageCount > 0" class="msg-unread-badge">{{ unreadMessageCount }}</span>
        </div>
      </template>

      <div class="msg-toolbar">
        <div class="msg-tabs">
          <button v-for="tab in msgTabs" :key="tab.key" class="msg-tab" :class="{ active: activeMsgTab === tab.key }" @click="activeMsgTab = tab.key">
            {{ tab.label }}
          </button>
        </div>
        <div class="msg-actions-row">
          <el-input v-model="msgSearchText" placeholder="搜索消息..." size="small" clearable class="msg-search" />
          <el-button v-if="unreadMessageCount > 0" size="small" text type="primary" @click="markAllRead">全部已读</el-button>
        </div>
      </div>

      <div v-if="!filteredMessages.length" class="drawer-empty">
        <span v-if="msgSearchText">未找到匹配的消息</span>
        <span v-else>{{ activeMsgTab === 'all' ? '暂无消息' : '暂无此类消息' }}</span>
      </div>
      <div v-else class="message-list">
        <div
          v-for="message in filteredMessages"
          :key="message.id"
          class="message-card"
          :class="{ unread: !message.read }"
        >
          <div class="msg-card-head">
            <span class="msg-type-badge" :class="msgTypeClass(message.messageType)">{{ msgTypeLabel(message.messageType) }}</span>
            <span class="message-time">{{ message.createdAt }}</span>
          </div>
          <div class="msg-card-title">{{ message.title }}</div>
          <div class="message-content">{{ message.content }}</div>
          <div class="message-actions">
            <el-button v-if="message.projectId" size="small" text type="primary" @click="goToProject(message)">前往项目</el-button>
            <el-button v-if="!message.read" size="small" text type="success" @click="markMessageRead(message)">已读</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="showBadgeDialog" title="发放勋章" width="460px">
      <el-form label-position="top" :model="badgeForm">
        <el-form-item label="目标用户" required>
          <el-select v-model="badgeForm.userId" filterable placeholder="选择用户">
            <el-option v-for="user in userOptions" :key="user.userId" :label="user.name || user.username" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="勋章名称" required>
          <el-input v-model="badgeForm.badgeName" placeholder="例如：灭火队长" />
        </el-form-item>
        <el-form-item label="勋章图标">
          <el-input v-model="badgeForm.badgeIcon" placeholder="例如：🏅" />
        </el-form-item>
        <el-form-item label="勋章颜色">
          <el-input v-model="badgeForm.badgeColor" placeholder="例如：#f59e0b" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="badgeForm.hiddenAvatar" active-text="隐藏该用户头像" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBadgeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBadgeAward">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {useUserStore} from '@/stores/userStore'
import {useRouter, useRoute} from 'vue-router'
import { FINANCE_ALLOWED_ROLES } from '@/router/financeRoutes'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import AiCommandLauncher from '@/components/AiCommandLauncher.vue'
import { canAccessProvisioning } from '@/constants/provisioning'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const theme = ref('light')
const AUTH_ENTRY_PATHS = ['/login', '/erp-login']
const isAuthEntry = computed(() => AUTH_ENTRY_PATHS.includes(route.path))
const isFinanceRoute = computed(() => route.path.startsWith('/finance'))
const isFullscreenCockpitRoute = computed(() => route.name === 'finance-overview' && route.query?.fullscreen === '1')
const showAuthenticatedNavbar = computed(() => userStore.isLoggedIn && !isAuthEntry.value && !isFinanceRoute.value && !isFullscreenCockpitRoute.value)
const activeDomain = computed(() => userStore.activeSession?.accountDomain || '')
const activeRole = computed(() => (userStore.activeUserInfo?.role || '').toUpperCase())
const canAccessFinance = computed(() => FINANCE_ALLOWED_ROLES.includes(activeRole.value))
const showFinanceShortcut = computed(() => activeDomain.value === 'FINANCE' && canAccessFinance.value)
const showCreateProductAction = computed(() => userStore.isErpLoggedIn)
const showProvisionUserAction = computed(() => userStore.isErpLoggedIn && canAccessProvisioning(userStore.activeUserInfo?.username))
const showExpenseReviewEntry = computed(() => {
  const uid = String(userStore.activeUserInfo?.userId || '')
  return uid === '000027' || uid === '000044'
})
const showFullscreenCockpitEntry = computed(() => activeDomain.value === 'FINANCE' && canAccessFinance.value)
const showMessageDrawer = ref(false)
const showBadgeDialog = ref(false)
const messages = ref([])
const unreadMessageCount = ref(0)
const pendingExpenseCount = ref(0)
const activeMsgTab = ref('all')
const msgSearchText = ref('')
const userOptions = ref([])
const badgeForm = ref({ userId: '', badgeName: '', badgeIcon: '🏅', badgeColor: '#f59e0b', hiddenAvatar: false })
const displayAvatar = computed(() => userStore.activeUserInfo?.hiddenAvatar ? 'https://api.dicebear.com/7.x/shapes/svg?seed=masked' : (userStore.activeUserInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'))
const messagePollTimer = ref(null)

const msgTabs = [
  { key: 'all', label: '全部' },
  { key: 'expense', label: '费用' },
  { key: 'project', label: '项目' },
  { key: 'task', label: '任务' }
]

const EXPENSE_TYPES = new Set(['EXPENSE_PENDING', 'EXPENSE_APPROVED', 'EXPENSE_REJECTED', 'EXPENSE_STATUS'])
const PROJECT_TYPES = new Set(['PROJECT_JOINED', 'PROJECT_STATUS', 'PROJECT_DYNAMIC_INFO', 'MILESTONE_UPDATE'])
const TASK_TYPES = new Set(['SUBTASK_ASSIGNED', 'TASK_ASSIGNED', 'EXECUTION_PLANNING', 'DDL_REMINDER'])

const msgTypeCategory = type => {
  if (EXPENSE_TYPES.has(type)) return 'expense'
  if (PROJECT_TYPES.has(type)) return 'project'
  if (TASK_TYPES.has(type)) return 'task'
  return 'all'
}

const filteredMessages = computed(() => {
  let list = messages.value
  if (activeMsgTab.value !== 'all') {
    list = list.filter(m => msgTypeCategory(m.messageType) === activeMsgTab.value)
  }
  if (msgSearchText.value.trim()) {
    const q = msgSearchText.value.trim().toLowerCase()
    list = list.filter(m =>
      (m.title || '').toLowerCase().includes(q) ||
      (m.content || '').toLowerCase().includes(q))
  }
  return list
})

const msgTypeLabel = type => {
  const map = {
    EXPENSE_PENDING: '待审批', EXPENSE_APPROVED: '已通过', EXPENSE_REJECTED: '已拒绝', EXPENSE_STATUS: '状态更新',
    PROJECT_JOINED: '加入项目', PROJECT_STATUS: '状态变更', PROJECT_DYNAMIC_INFO: '动态更新', MILESTONE_UPDATE: '里程碑',
    SUBTASK_ASSIGNED: '子任务', TASK_ASSIGNED: '任务分配', EXECUTION_PLANNING: '执行规划', DDL_REMINDER: 'DDL提醒'
  }
  return map[type] || type
}

const msgTypeClass = type => {
  if (EXPENSE_TYPES.has(type)) return 'badge-expense'
  if (PROJECT_TYPES.has(type)) return 'badge-project'
  if (TASK_TYPES.has(type)) return 'badge-task'
  return ''
}

const MSG_TOAST_LABELS = {
  EXPENSE_PENDING: '📋 新的费用待审批',
  EXPENSE_APPROVED: '✅ 费用审批通过',
  EXPENSE_REJECTED: '❌ 费用审批被拒绝',
  EXPENSE_STATUS: '📢 费用审批进展更新',
  PROJECT_JOINED: '👋 您加入了新项目',
  PROJECT_STATUS: '🔄 项目状态已更新',
  MILESTONE_UPDATE: '📌 项目里程碑更新',
  SUBTASK_ASSIGNED: '📝 您有新的子任务',
  TASK_ASSIGNED: '📋 任务已分配',
  EXECUTION_PLANNING: '📐 项目进入执行规划',
  DDL_REMINDER: '⏰ 项目截止日期提醒',
  PROJECT_DYNAMIC_INFO: '📊 项目动态信息更新'
}

const applyTheme = (value) => {
  const finalTheme = value === 'dark' ? 'dark' : 'light'
  theme.value = finalTheme
  document.documentElement.classList.toggle('dark', finalTheme === 'dark')
  document.documentElement.setAttribute('data-theme', finalTheme)
  localStorage.setItem('app-theme', finalTheme)
}

const toggleTheme = () => {
  applyTheme(theme.value === 'dark' ? 'light' : 'dark')
}

onMounted(() => {
  const savedTheme = localStorage.getItem('app-theme')
  if (savedTheme === 'dark' || savedTheme === 'light') {
    applyTheme(savedTheme)
  } else {
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
    applyTheme(prefersDark ? 'dark' : 'light')
  }

  fetchMessages()
  messagePollTimer.value = window.setInterval(pollNewMessages, 5000)
})

let prevUnreadMsgIds = new Set()

const pollNewMessages = async () => {
  if (!userStore.isLoggedIn) { unreadMessageCount.value = 0; return }
  try {
    const list = await request.get('/api/messages')
    const unreadMessages = list.filter(m => !m.read)
    unreadMessageCount.value = unreadMessages.length

    const currentIds = new Set(unreadMessages.map(m => m.id))
    const newMessages = unreadMessages.filter(m => !prevUnreadMsgIds.has(m.id))
    prevUnreadMsgIds = currentIds
    for (const msg of newMessages) {
      const label = MSG_TOAST_LABELS[msg.messageType]
      if (label) {
        ElMessage.info(`${label}：${msg.title}`)
      }
    }
    messages.value = list
    fetchPendingExpenseCount()
  } catch {}
}

onBeforeUnmount(() => {
  if (messagePollTimer.value) {
    window.clearInterval(messagePollTimer.value)
    messagePollTimer.value = null
  }
})

const fetchMessages = async () => {
  if (!userStore.isLoggedIn) return
  try {
    messages.value = await request.get('/api/messages')
    unreadMessageCount.value = messages.value.filter(message => !message.read).length
  } catch {}
}

const fetchUnreadCount = async () => {
  if (!userStore.isLoggedIn) {
    unreadMessageCount.value = 0
    return
  }
  try {
    const res = await request.get('/api/messages/unread-count')
    unreadMessageCount.value = Number(res?.count || 0)
  } catch {
    // ignore polling errors
  }
}

const fetchPendingExpenseCount = async () => {
  if (!showExpenseReviewEntry.value) {
    pendingExpenseCount.value = 0
    return
  }
  try {
    const res = await request.get('/api/projects/expenses/pending-count')
    pendingExpenseCount.value = Number(res?.count || 0)
  } catch {
    // ignore polling errors
  }
}

const openMessageDrawer = async () => {
  activeMsgTab.value = 'all'
  msgSearchText.value = ''
  await fetchMessages()
  showMessageDrawer.value = true
}

const markMessageRead = async message => {
  if (message.read) return
  await request.patch(`/api/messages/${message.id}/read`)
  message.read = true
  unreadMessageCount.value = Math.max(0, unreadMessageCount.value - 1)
}

const markAllRead = async () => {
  if (unreadMessageCount.value <= 0) return
  try {
    await request.patch('/api/messages/read-all')
    messages.value.forEach(m => { m.read = true })
    unreadMessageCount.value = 0
    ElMessage.success('全部已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const goToProject = message => {
  showMessageDrawer.value = false
  if (message.projectId) {
    router.push(`/workspace/project/${message.projectId}`)
  }
}

const openBadgeDialog = async () => {
  showBadgeDialog.value = true
  if (userOptions.value.length) return
  userOptions.value = await request.get('/api/users')
}

const submitBadgeAward = async () => {
  try {
    await request.post('/api/user-badges', badgeForm.value)
    ElMessage.success('勋章已发放')
    showBadgeDialog.value = false
    badgeForm.value = { userId: '', badgeName: '', badgeIcon: '🏅', badgeColor: '#f59e0b', hiddenAvatar: false }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '发放勋章失败')
  }
}

// 动态徽章文字
const currentBadge = computed(() => {
  if (activeDomain.value === 'FINANCE') return 'FINANCE'

  const path = route.path
  if (path.includes('/manager')) return 'ERP · BUSINESS'
  if (path.includes('/workspace')) return 'ERP · WORKSPACE'
  if (activeDomain.value === 'ERP') return 'ERP'
  return 'GUEST'
})

// 徽章颜色样式
const badgeClass = computed(() => {
  if (activeDomain.value === 'FINANCE' || route.path.includes('/finance')) return 'finance'
  if (route.path.includes('/manager')) return 'pm'
  return 'eng'
})

const handleSwitchView = () => {
  if (!userStore.isErpLoggedIn) {
    router.push('/finance/classic')
    return
  }

  const currentPath = route.path
  if (currentPath.includes('/manager')) {
    router.push('/workspace')
  } else {
    router.push('/manager/dashboard')
  }
}

const requestBrowserFullscreen = async () => {
  const isWindows = /windows/i.test(navigator.userAgent)
  if (!isWindows) return
  const element = document.documentElement
  const enter = element.requestFullscreen || element.webkitRequestFullscreen || element.msRequestFullscreen
  if (typeof enter === 'function') {
    try {
      await enter.call(element)
    } catch {
      // browser may block fullscreen if gesture context changed
    }
  }
}

const handleCommand = (cmd) => {
  if (cmd === 'personal-procurement') router.push('/erp/personal-procurement')
  else if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'provision-user') router.push('/admin/users/create')
  else if (cmd === 'wage-management') router.push('/admin/wage-management')
  else if (cmd === 'expense-review') router.push('/expense-review')
  else if (cmd === 'award-badge') openBadgeDialog()
  else if (cmd === 'fullscreen-cockpit') {
    requestBrowserFullscreen()
    router.push({ path: '/finance/overview', query: { fullscreen: '1' } })
  }
  else if (cmd === 'theme') toggleTheme()
  else if (cmd === 'switch') handleSwitchView()
  else if (cmd === 'logout') {
    if (userStore.isErpLoggedIn) {
      userStore.logoutErp()
      router.push('/erp-login')
    } else {
      userStore.logout()
      router.push('/login')
    }
  }
}
</script>

<style>
:root {
  --bg-base: #F1F5F9;
  --bg-surface: #FFFFFF;
  --bg-elevated: #F8FAFC;
  --text-primary: #1E293B;
  --text-secondary: #64748B;
  --border-subtle: #E2E8F0;
  --shadow-soft: 0 10px 30px rgba(2, 6, 23, 0.08);

  --science-dark-bg: #0F172A;
  --science-canvas: var(--bg-base);
  --science-surface: var(--bg-surface);
  --science-surface-muted: var(--bg-elevated);
  --science-blue: #2563EB;
  --science-blue-hover: #1D4ED8;
  --text-on-dark: #F8FAFC;
  --text-main: var(--text-primary);
  --text-sub: var(--text-secondary);
  --border-soft: var(--border-subtle);
  --shadow-md: var(--shadow-soft);
  --sidebar-width: 240px;
  --nav-height: 60px;
  --pad-x: 24px;
}

.dark {
  --bg-base: #0B1220;
  --bg-surface: #111B2E;
  --bg-elevated: #0E172A;
  --text-primary: #E2E8F0;
  --text-secondary: #94A3B8;
  --border-subtle: #1E293B;
  --shadow-soft: 0 10px 30px rgba(2, 6, 23, 0.45);

  --science-dark-bg: #020817;
  --science-canvas: var(--bg-base);
  --science-surface: var(--bg-surface);
  --science-surface-muted: var(--bg-elevated);
  --science-blue: #3B82F6;
  --science-blue-hover: #60A5FA;
  --text-on-dark: #E2E8F0;
  --text-main: var(--text-primary);
  --text-sub: var(--text-secondary);
  --border-soft: var(--border-subtle);
  --shadow-md: var(--shadow-soft);
}

body {
  margin: 0;
  font-family: -apple-system, sans-serif;
  background-color: var(--science-canvas);
  color: var(--text-main);
}

.tech-navbar {
  height: var(--nav-height);
  background-color: var(--science-dark-bg);
  color: var(--text-on-dark);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--pad-x);
  position: sticky;
  top: 0;
  z-index: 999;
}

.nav-left, .nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo-box {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.role-badge {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(148, 163, 184, 0.22);
}

.role-badge.pm {
  color: #E9D5FF;
}

.role-badge.finance {
  color: #D9F99D;
}

.role-badge.eng {
  color: #BFDBFE;
}

.nav-shortcut {
  border: 1px solid rgba(217, 249, 157, 0.22);
  background: rgba(217, 249, 157, 0.08);
  color: #ecfccb;
  padding: 7px 12px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-shortcut.active {
  background: rgba(217, 249, 157, 0.2);
}

.tech-btn.primary {
  background-color: var(--science-blue);
  color: white;
  border: none;
  padding: 7px 16px;
  border-radius: 4px;
  cursor: pointer;
}

.mail-trigger {
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  cursor: pointer;
}

.mail-count {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.avatar-hex {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.2);
  object-fit: cover;
}

.arrow-down {
  font-size: 10px;
  opacity: 0.6;
}

.dropdown-header {
  font-size: 11px;
  color: var(--text-sub);
  padding: 8px 12px;
}

.theme-toggle {
  width: 34px;
  height: 34px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--science-surface);
  color: var(--text-main);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
  transition: transform 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
}

.theme-toggle.floating {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 1001;
}

.fade-scale-enter-active {
  transition: all 0.3s ease;
}

.fade-scale-enter-from {
  opacity: 0;
  transform: scale(0.99);
}

.msg-drawer .el-drawer__header {
  margin-bottom: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-soft);
}

.msg-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-drawer-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.msg-unread-badge {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
}

.msg-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--science-surface);
  padding: 10px 0 8px;
}

.msg-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.msg-tab {
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-soft);
  background: transparent;
  color: var(--text-sub);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.msg-tab:hover {
  border-color: var(--science-blue);
  color: var(--science-blue);
}

.msg-tab.active {
  background: var(--science-blue);
  color: #fff;
  border-color: var(--science-blue);
}

.msg-actions-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.msg-search {
  flex: 1;
}

.drawer-empty {
  color: var(--text-sub);
  text-align: center;
  padding: 40px 0;
  font-size: 14px;
}

.message-list {
  display: grid;
  gap: 8px;
}

.message-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--border-soft);
  background: var(--science-surface);
  transition: border-color 0.15s;
}

.message-card.unread {
  border-left: 3px solid var(--science-blue);
  padding-left: 12px;
}

.msg-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.msg-type-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 4px;
}

.badge-expense { background: #fff3e0; color: #e65100; }
.badge-project { background: #e3f2fd; color: #1565c0; }
.badge-task { background: #e8f5e9; color: #2e7d32; }

.msg-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}

.message-time {
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
}

.message-content {
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.5;
  margin-top: 4px;
}

.message-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}

.dark .el-overlay .el-drawer,
.dark .el-overlay .el-drawer__header,
.dark .el-overlay .el-drawer__body {
  background: var(--science-surface) !important;
  color: var(--text-main) !important;
}

.dark .el-overlay .el-drawer__title {
  color: var(--text-main) !important;
}

.dark .message-card {
  background: var(--science-surface-muted);
  border-color: var(--border-soft);
}

.dark .message-time,
.dark .message-content,
.dark .drawer-empty {
  color: var(--text-sub);
}

.dark .el-select-dropdown,
.dark .el-popper.is-light {
  background: #0f172a !important;
  border-color: #334155 !important;
}

.dark .el-select-dropdown__item,
.dark .el-select-dropdown__item span,
.dark .el-select-dropdown__empty,
.dark .el-select-dropdown__loading,
.dark .el-select-dropdown__wrap,
.dark .el-select-dropdown__list {
  color: #e2e8f0 !important;
}

.dark .el-select-dropdown__item.hover,
.dark .el-select-dropdown__item:hover {
  background: rgba(59, 130, 246, 0.22) !important;
}

.dark .el-input__wrapper,
.dark .el-select .el-select__wrapper,
.dark .el-textarea__inner,
.dark .el-input__inner {
  background: #0f172a !important;
  color: #e2e8f0 !important;
  border-color: #334155 !important;
}

.dark .project-manager-select-popper .option-name,
.dark .project-team-select-popper .option-name,
.dark .project-data-engineer-select-popper .option-name {
  color: #e2e8f0 !important;
}

.dark .project-manager-select-popper .option-role,
.dark .project-team-select-popper .option-role,
.dark .project-data-engineer-select-popper .option-role {
  color: #94a3b8 !important;
}

/* Dialog dark-mode hardening for teleported overlays */
.dark .el-overlay {
  --el-overlay-color-lighter: rgba(2, 6, 23, 0.72);
}

.dark .el-overlay .el-dialog {
  background: var(--science-surface) !important;
  border: 1px solid var(--border-soft) !important;
  box-shadow: var(--shadow-md) !important;
  color: var(--text-main) !important;
}

.dark .el-overlay .el-dialog__header,
.dark .el-overlay .el-dialog__body,
.dark .el-overlay .el-dialog__footer {
  background: var(--science-surface) !important;
  color: var(--text-main) !important;
}

.dark .el-overlay .el-dialog__title,
.dark .el-overlay .el-form-item__label,
.dark .el-overlay .el-input__inner,
.dark .el-overlay .el-textarea__inner {
  color: var(--text-main) !important;
}
</style>

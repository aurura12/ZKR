import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

// 引入视图组件
import LoginView from '../views/LoginView.vue'
import ErpLoginView from '../views/ErpLoginView.vue'
import WorkspaceView from '../views/WorkspaceView.vue'
import ManagerDashboard from '../views/ManagerDashboard.vue'
import ProjectDetail from '../views/ProjectDetail.vue'
import CreateProject from '../views/CreateProject.vue'
import CreateDeliveryProjectView from '../views/CreateDeliveryProjectView.vue'
import CreateResearchView from '../views/CreateResearchView.vue'
import UserProfile from '../views/UserProfile.vue'
import AdminCreateUserView from '../views/AdminCreateUserView.vue'
import PersonalProcurementView from '../views/PersonalProcurementView.vue'
import LeaderDashboardView from '../views/LeaderDashboardView.vue'
import LeaderManagementView from '../views/LeaderManagementView.vue'



import {
  DOMAIN_ERP,
  canAccessRouteDomain,
  getAnonymousProtectedRedirect,
  getAuthenticatedEntryRedirect,
  getDefaultRouteForDomain,
  getErpLandingRoute,
  resolveActiveSession
} from './domainAccess.js'
import financeRoutes from './financeRoutes'
import { canAccessProvisioning } from '@/constants/provisioning'

const ACTIVE_AUTH_SCOPE_KEY = 'active_auth_scope'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/erp-login',
      name: 'erp-login',
      component: ErpLoginView
    },

    // === 轨道 A：研发工作台 (工程师视图) ===
    // 特点：带有左侧侧边栏，适合处理具体项目细节
    {
      path: '/workspace',
      component: WorkspaceView,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      },
      children: [
        { path: '', name: 'workspace-home', component: null }, // 或者写一个 Welcome.vue
        { path: 'project/:id', component: ProjectDetail }
      ]
    },

    // === 轨道 B：管理驾驶舱 (PM 视图) ===
    // 特点：独立全屏视图，不再复用 Workspace 布局
    // 修复点：移出 WorkspaceView 的 children，直接作为顶级页面渲染
    {
      path: '/manager/dashboard',
      name: 'manager-dashboard',
      component: ManagerDashboard,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/manager/project/create',
      name: 'create-delivery-project',
      component: CreateDeliveryProjectView,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/manager/product/create',
      name: 'create-product',
      component: CreateProject,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/manager/research/create',
      name: 'create-research',
      component: CreateResearchView,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    // === 个人中心页面 ===
    {
      path: '/profile',
      name: 'profile',
      component: UserProfile,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/erp/personal-procurement',
      name: 'personal-procurement',
      component: PersonalProcurementView,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/admin/users/create',
      name: 'admin-users-create',
      component: AdminCreateUserView,
      meta: {
        requiresAuth: true,
        requiresProvisionAdmin: true,
        routeDomain: DOMAIN_ERP
      }
    },
      //TODO === 轨道 C：领导驾驶舱 (Leader 视图) ===
    {
      path: '/leader/dashboard',
      name: 'leader-dashboard',
      component: LeaderDashboardView,
      meta: {
        requiresAuth: true,
        routeDomain: DOMAIN_ERP
      }
    },
    {
      path: '/admin/leader-management',
      name: 'leader-management',
      component: LeaderManagementView,
      meta: {
        requiresAuth: true,
        requiresProvisionAdmin: true,
        routeDomain: DOMAIN_ERP
      }
    },
    ...financeRoutes
  ]
})

const CHUNK_RELOAD_KEY = 'chunk_load_reload_once'

router.onError(error => {
  const message = String(error?.message || '')
  const isChunkError =
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed') ||
    message.includes('Unable to preload CSS')

  if (!isChunkError) return

  const hasReloaded = sessionStorage.getItem(CHUNK_RELOAD_KEY) === '1'
  if (hasReloaded) {
    sessionStorage.removeItem(CHUNK_RELOAD_KEY)
    return
  }

  sessionStorage.setItem(CHUNK_RELOAD_KEY, '1')
  window.location.reload()
})

// 全局路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const activeAuthScope = localStorage.getItem(ACTIVE_AUTH_SCOPE_KEY)
  const authRequired = to.matched.some(routeRecord => routeRecord.meta.requiresAuth)
  const authenticatedEntryRedirect = getAuthenticatedEntryRedirect({
    to,
    activeAuthScope,
    financeToken: userStore.token,
    financeUserInfo: userStore.userInfo,
    erpToken: userStore.erpToken,
    erpUserInfo: userStore.erpUserInfo
  })
  const activeSession = resolveActiveSession({
    activeAuthScope,
    financeToken: userStore.token,
    financeUserInfo: userStore.userInfo,
    erpToken: userStore.erpToken,
    erpUserInfo: userStore.erpUserInfo
  })
  const hasAuthenticatedSession = Boolean(activeSession.accountDomain)
  const currentRole = activeSession.role
  const currentDomain = activeSession.accountDomain
  const allowedRoles = to.meta?.allowedRoles
  const isProvisionAdmin = canAccessProvisioning(activeSession.userInfo?.username)

  if (authenticatedEntryRedirect) {
    next(authenticatedEntryRedirect)
  } else if (authRequired && !hasAuthenticatedSession) {
    next(getAnonymousProtectedRedirect(to))
  } else if (to.meta?.requiresProvisionAdmin && !isProvisionAdmin) {
    next('/profile')
  } else if (!canAccessRouteDomain({ accountDomain: currentDomain, to })) {
    next(currentDomain === DOMAIN_ERP ? getErpLandingRoute(currentRole) : getDefaultRouteForDomain(currentDomain))
  } else if (Array.isArray(allowedRoles) && allowedRoles.length > 0 && !allowedRoles.includes(currentRole)) {
    next('/manager/dashboard')
  } else {
    next()
  }
})

export default router

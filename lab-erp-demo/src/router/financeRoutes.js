import { DOMAIN_FINANCE } from './domainAccess.js'
import FinanceShell from '@/views/finance/FinanceShell.vue'
import FinanceOverviewView from '@/views/finance/FinanceOverviewView.vue'
import FinanceClassicView from '@/views/finance/FinanceClassicView.vue'
import FinanceMiddlewareHubView from '@/views/finance/FinanceMiddlewareHubView.vue'

export const FINANCE_ALLOWED_ROLES = ['ADMIN', 'BUSINESS', 'DATA', 'RESEARCH']

export const financeOverviewLinkTargets = {
  risk: 'finance-clearing',
  reconciliation: 'finance-clearing',
  reporting: 'finance-cost-batches'
}

export const createFinanceOverviewLinkActions = router => ({
  openClearingWorkbench: () => router.push({ name: financeOverviewLinkTargets.risk }),
  openCostBatchWorkbench: () => router.push({ name: financeOverviewLinkTargets.reporting })
})

export const financeNavigationItems = [
  {
    path: '/finance/classic',
    routeName: 'finance-classic',
    label: '经典视图',
    eyebrow: '财务总览',
    title: '财务经典视图',
    description: '财务总控入口。',
    highlights: ['总览卡片', '核心模块', '快速跳转']
  },
  {
    path: '/finance/middleware-hub',
    routeName: 'finance-middleware-hub',
    label: '中间件仓库入口',
    eyebrow: '中间件',
    title: 'ERP 中间件仓库',
    description: '查看中间件入库时间、创建人、调用次数和调用价格。',
    highlights: ['入库时间', '创建人', '调用次数', '调用价格']
  },
  {
    path: '/finance/wallets',
    routeName: 'finance-wallets',
    label: '钱包',
    eyebrow: '资金',
    title: '钱包余额',
    description: '钱包余额与交易审计。',
    highlights: ['余额列表', '交易审计', '资金快照']
  },
  {
    path: '/finance/cost-batches',
    routeName: 'finance-cost-batches',
    label: '成本跑批',
    eyebrow: '核算',
    title: '成本跑批工作台',
    description: '会计期间核算与批处理执行。',
    highlights: ['期间选择', '批处理执行', '结果汇总']
  },
  {
    path: '/finance/batch-control',
    routeName: 'finance-batch-control',
    label: '跑批控制',
    eyebrow: '系统',
    title: '跑批控制台',
    description: '管理系统定时跑批任务，支持暂停、手动触发和项目级精细控制。',
    highlights: ['暂停/启用', '手动触发', '项目级控制']
  },
  {
    path: '/finance/clearing',
    routeName: 'finance-clearing',
    label: '清算',
    eyebrow: '结算',
    title: '自动化结算中心',
    description: '差异复核与结算执行。',
    highlights: ['差异汇总', '结算执行', '结果追踪']
  },
  {
    path: '/finance/dividends',
    routeName: 'finance-dividends',
    label: '分红',
    eyebrow: '分配',
    title: '分红中心',
    description: '待处理与已确认分红流程。',
    highlights: ['状态分组', '分红单', '确认面板']
  },
  {
    path: '/finance/adjustments',
    routeName: 'finance-adjustments',
    label: '调账',
    eyebrow: '审计',
    title: '手工调账中心',
    description: '借贷调整与审计记录。',
    highlights: ['调账录入', '审计日志', '校验结果']
  },
  {
    path: '/finance/expenses',
    routeName: 'finance-expenses',
    label: '报销采购',
    eyebrow: '费用',
    title: '采购与差旅报销',
    description: '集中查看 ERP 提交的采购和出差报销。',
    highlights: ['采购申请', '差旅报销', '发票下载']
  },
  {
    path: '/finance/rag',
    routeName: 'finance-rag',
    label: '全局业务检索',
    eyebrow: '业务问答',
    title: '全局业务检索',
    description: '',
    highlights: ['全局检索', '引用列表', '上下文块']
  },
  {
    path: '/finance/ai',
    routeName: 'finance-ai',
    label: '全局业务助手',
    eyebrow: '业务问答',
    title: '全局业务智能助手',
    description: '',
    highlights: ['会话流', '全局上下文', '助手动作']
  },
  {
    path: '/finance/attendance',
    routeName: 'finance-attendance',
    label: '考勤工资',
    eyebrow: '考勤',
    title: '考勤工资',
    description: '钉钉考勤打卡数据总览与纠偏。',
    highlights: ['打卡明细', '月度汇总', '纠偏申请']
  }
]

const withFinanceMeta = item => ({
  path: item.path.replace('/finance/', ''),
  name: item.routeName,
  component: item.component,
  meta: {
    requiresAuth: true,
    allowedRoles: FINANCE_ALLOWED_ROLES,
    routeDomain: DOMAIN_FINANCE,
    financeNavLabel: item.label,
    financeTitle: item.title,
    financeDescription: item.description,
    financeEyebrow: item.eyebrow,
    financeHighlights: item.highlights
  }
})

export const financeRoutes = [
  {
    path: '/finance',
    component: FinanceShell,
    meta: {
      requiresAuth: true,
      allowedRoles: FINANCE_ALLOWED_ROLES,
      routeDomain: DOMAIN_FINANCE
    },
    children: [
      {
        path: '',
        redirect: '/finance/classic'
      },
      withFinanceMeta({
        ...financeNavigationItems[0],
        component: FinanceClassicView
      }),
      withFinanceMeta({
        ...financeNavigationItems[1],
        component: FinanceMiddlewareHubView
      }),
      withFinanceMeta({
        path: '/finance/overview',
        routeName: 'finance-overview',
        label: '驾驶舱',
        eyebrow: '监控',
        title: '财务驾驶舱',
        description: '项目交付、产品研发、科研创新三流监控入口。',
        highlights: ['实时卡片', '等级视图', '胶片序列'],
        component: FinanceOverviewView
      }),
      withFinanceMeta({
        ...financeNavigationItems[2],
        component: () => import('@/views/finance/FinanceWalletsView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[3],
        component: () => import('@/views/finance/CostBatchView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[4],
        component: () => import('@/views/finance/BatchControlView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[5],
        component: () => import('@/views/finance/ClearingCenterView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[6],
        component: () => import('@/views/finance/DividendCenterView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[7],
        component: () => import('@/views/finance/AdjustmentCenterView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[8],
        component: () => import('@/views/finance/FinanceExpenseCenterView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[9],
        component: () => import('@/views/finance/RagSearchView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[10],
        component: () => import('@/views/finance/FinanceAiChatView.vue')
      }),
      withFinanceMeta({
        ...financeNavigationItems[11],
        component: () => import('@/views/finance/FinanceAttendanceView.vue')
      })
    ]
  }
]

export default financeRoutes

<template>
  <section class="classic-root">
    <header class="classic-hero">
      <p class="eyebrow">财务总控台</p>
      <h1>财务工作台</h1>
      <p class="desc">聚焦报表、对账、跑批、结算与资金监控。</p>
      <el-button type="primary" class="cockpit-btn" @click="goCockpit">进入全屏驾驶舱</el-button>
    </header>

    <div class="module-grid">
      <router-link v-for="item in moduleLinks" :key="item.path" :to="item.path" class="module-card">
        <p class="module-eyebrow">{{ item.eyebrow }}</p>
        <h3>{{ item.title }}</h3>
        <p>{{ item.description }}</p>
      </router-link>
    </div>

    <section class="report-card">
      <div class="report-head">
        <div>
          <p class="module-eyebrow">报表数据管理</p>
          <h3>在研项目实时核算</h3>
          <p class="report-desc">资产负债表按在研项目预算与成本实时汇总。</p>
        </div>
        <el-button size="small" :loading="reportLoading" @click="loadReport">刷新报表</el-button>
      </div>

      <div v-if="report" class="report-metrics">
        <div class="metric-item">
          <span>总资产</span>
          <strong>{{ formatMoney(report.balanceSheet?.totalAssets) }}</strong>
        </div>
        <div class="metric-item">
          <span>总负债</span>
          <strong>{{ formatMoney(report.balanceSheet?.totalLiabilities) }}</strong>
        </div>
        <div class="metric-item">
          <span>净资产</span>
          <strong>{{ formatMoney(report.balanceSheet?.netAssets) }}</strong>
        </div>
        <div class="metric-item">
          <span>在研项目资产</span>
          <strong>{{ formatMoney(report.balanceSheet?.activeProjectAssets) }}</strong>
        </div>
      </div>

      <div v-if="report?.activeProjectAccounting?.length" class="project-table-wrap">
        <table class="project-table">
          <thead>
            <tr>
              <th>项目</th>
              <th>流类型</th>
              <th>状态</th>
              <th>资产估值</th>
              <th>负债估值</th>
              <th>净头寸</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in report.activeProjectAccounting" :key="row.projectId">
              <td>{{ row.name }}</td>
              <td>{{ row.flowType }}</td>
              <td>{{ row.status }}</td>
              <td>{{ formatMoney(row.estimatedAsset) }}</td>
              <td>{{ formatMoney(row.estimatedLiability) }}</td>
              <td>{{ formatMoney(row.netPosition) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <el-empty v-else-if="!reportLoading" description="暂无在研项目核算数据" />
    </section>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFinanceOverview } from '@/api/finance/overview'
import { unwrapFinanceEnvelope } from '@/utils/financeAdapters'

const router = useRouter()
const report = ref(null)
const reportLoading = ref(false)
let timer = null

const moduleLinks = [
  { path: '/finance/wallets', eyebrow: '资金', title: '钱包余额', description: '查看账户余额与流水明细。' },
  { path: '/finance/clearing', eyebrow: '结算', title: '自动清算中心', description: '处理差异与结算执行。' },
  { path: '/finance/dividends', eyebrow: '分配', title: '分红中心', description: '管理分红单与确认流程。' },
  { path: '/finance/adjustments', eyebrow: '调账', title: '手工调账', description: '记录借贷调整与审计轨迹。' },
  { path: '/finance/expenses', eyebrow: '费用', title: '采购与差旅报销', description: '汇总 ERP 提交的采购申请与差旅报销。' },
  { path: '/finance/ai', eyebrow: '智能', title: '财务智能助手', description: '辅助问答与财务查询。' }
]

const goCockpit = () => router.push({ path: '/finance/overview', query: { fullscreen: '1' } })

const formatMoney = value => {
  const n = Number(value || 0)
  if (Number.isNaN(n)) return '¥0.00'
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const loadReport = async () => {
  reportLoading.value = true
  try {
    const res = await getFinanceOverview()
    const envelope = unwrapFinanceEnvelope(res)
    report.value = envelope.data || null
    const timestamp = new Date().toISOString()
    localStorage.setItem('finance-dashboard-last-updated', timestamp)
    window.dispatchEvent(new Event('finance-dashboard-updated'))
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  loadReport()
  timer = window.setInterval(loadReport, 60000)
  window.addEventListener('finance-global-refresh', loadReport)
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  window.removeEventListener('finance-global-refresh', loadReport)
})
</script>

<style scoped>
.classic-root {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.classic-hero {
  padding: 24px;
  border-radius: 26px;
  background:
    linear-gradient(130deg, rgba(255, 255, 255, 0.58), rgba(236, 253, 245, 0.5) 48%, rgba(224, 242, 254, 0.52) 100%);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 20px 40px rgba(14, 116, 144, 0.12);
  backdrop-filter: blur(18px) saturate(150%);
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #0f766e;
}

.classic-hero h1 {
  margin: 0;
  font-size: 36px;
  color: #0f172a;
}

.desc {
  margin: 10px 0 18px;
  color: #475569;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  text-decoration: none;
  color: #0f172a;
  padding: 18px;
  border-radius: 20px;
  background:
    linear-gradient(140deg, rgba(255, 255, 255, 0.52), rgba(240, 249, 255, 0.48) 50%, rgba(236, 253, 245, 0.45) 100%);
  border: 1px solid rgba(148, 163, 184, 0.2);
  backdrop-filter: blur(16px) saturate(145%);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.module-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.12);
}

.module-eyebrow {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0f766e;
}

.module-card h3 {
  margin: 8px 0;
}

.module-card p {
  margin: 0;
  color: #475569;
}

@media (max-width: 960px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}

.report-card {
  border-radius: 22px;
  padding: 18px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.7), rgba(237, 254, 255, 0.58));
  border: 1px solid rgba(148, 163, 184, 0.2);
  color: #0f172a;
}

.report-head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.report-head h3 {
  margin: 4px 0;
  color: #0b1324;
}

.report-desc {
  margin: 0;
  color: #1e293b;
}

.report-metrics {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric-item {
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.metric-item span {
  display: block;
  color: #334155;
  font-size: 12px;
}

.metric-item strong {
  color: #020617;
  font-size: 14px;
}

.project-table-wrap {
  margin-top: 12px;
  overflow-x: auto;
}

.project-table {
  width: 100%;
  border-collapse: collapse;
}

.project-table th,
.project-table td {
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  font-size: 13px;
  color: #0f172a;
}

.project-table th {
  color: #020617;
  font-weight: 700;
}

@media (max-width: 960px) {
  .report-metrics {
    grid-template-columns: 1fr 1fr;
  }
}
</style>

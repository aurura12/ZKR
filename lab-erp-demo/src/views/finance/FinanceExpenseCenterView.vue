<template>
  <section class="expense-center-root">
    <header class="hero-card">
      <div>
        <p class="eyebrow">Finance Intake</p>
        <h1>采购与差旅报销汇总</h1>
        <p class="hero-copy">集中查看 ERP 侧提交的个人采购申请与项目出差报销，并直接下载对应发票。</p>
      </div>
      <el-button size="small" :loading="loading" @click="loadCenter">刷新汇总</el-button>
    </header>

    <div class="export-bar">
      <el-date-picker
        v-model="exportDateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        size="small"
        style="width:260px"
      />
      <el-button type="success" size="small" :loading="exporting" @click="exportReimbursements">
        📦 导出报销汇总
      </el-button>
    </div>

    <section class="summary-grid">
      <article class="summary-card">
        <span>总单数</span>
        <strong>{{ summary.totalCount || 0 }}</strong>
      </article>
      <article class="summary-card">
        <span>采购申请</span>
        <strong>{{ summary.procurementCount || 0 }}</strong>
      </article>
      <article class="summary-card">
        <span>出差报销</span>
        <strong>{{ summary.travelCount || 0 }}</strong>
      </article>
      <article class="summary-card">
        <span>累计金额</span>
        <strong>{{ formatMoney(summary.totalAmount) }}</strong>
      </article>
      <article class="summary-card">
        <span>采购金额</span>
        <strong>{{ formatMoney(summary.procurementAmount) }}</strong>
      </article>
      <article class="summary-card">
        <span>差旅金额</span>
        <strong>{{ formatMoney(summary.travelAmount) }}</strong>
      </article>
    </section>

    <section class="list-card">
      <div class="card-head">
        <div>
          <p class="eyebrow">Finance Queue</p>
          <h3>待处理申请列表</h3>
        </div>
      </div>

      <div v-if="rows.length" class="table-wrap">
        <table class="submission-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>提交人</th>
              <th>关联项目</th>
              <th>费用项</th>
              <th>金额</th>
              <th>发票</th>
              <th>说明</th>
              <th>提交时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <div class="primary-line">{{ formatType(row.submissionType) }}</div>
                <div class="sub-line">{{ formatStatus(row.status) }}</div>
              </td>
              <td>
                <div class="primary-line">{{ row.submitterName }}</div>
                <div class="sub-line">{{ row.submitterUserId }}</div>
              </td>
              <td>
                <div class="primary-line">{{ row.projectName || '个人申请' }}</div>
                <div class="sub-line">{{ formatFlowType(row.projectFlowType) }}</div>
              </td>
              <td>
                <div class="primary-line">{{ row.itemName }}</div>
                <div class="sub-line">{{ [row.itemCategory, row.itemSpecification].filter(Boolean).join(' · ') || '未补充分类' }}</div>
              </td>
              <td>
                <div class="primary-line">{{ formatMoney(row.totalAmount) }}</div>
                <div class="sub-line">{{ formatOccurredAt(row) }}</div>
              </td>
              <td>
                <div class="primary-line">{{ row.invoiceNumber }}</div>
                <div class="sub-line">
                  <button class="download-link" @click="downloadInvoice(row)">{{ row.invoiceFileName || '下载发票' }}</button>
                </div>
              </td>
              <td>
                <div class="primary-line">{{ row.purpose }}</div>
                <div class="sub-line">{{ formatRemarks(row) }}</div>
              </td>
              <td>
                <div class="primary-line">{{ formatDateTime(row.createdAt) }}</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <el-empty v-else-if="!loading" description="暂无采购或报销申请" />
    </section>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFinanceSubmissionInvoice, getFinanceSubmissionCenter, exportReimbursementsZip } from '@/api/finance/submissions'
import { unwrapFinanceEnvelope } from '@/utils/financeAdapters'

const loading = ref(false)
const exporting = ref(false)
const exportDateRange = ref([])
const center = ref({ summary: {}, submissions: [] })

const defaultExportRange = () => {
  const now = new Date()
  const thisMonth10 = new Date(now.getFullYear(), now.getMonth(), 10)
  const lastMonth11 = new Date(now.getFullYear(), now.getMonth() - 1, 11)
  return [lastMonth11.toISOString().slice(0, 10), thisMonth10.toISOString().slice(0, 10)]
}

const exportReimbursements = async () => {
  exporting.value = true
  try {
    const [from, to] = exportDateRange.value.length === 2 ? exportDateRange.value : defaultExportRange()
    const blob = await exportReimbursementsZip(from, to)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.download = `报销汇总_${from}_${to}.zip`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败: ' + (e.response?.data?.message || e.message || e))
  } finally {
    exporting.value = false
  }
}

const summary = computed(() => center.value?.summary || {})
const rows = computed(() => center.value?.submissions || [])

const loadCenter = async () => {
  loading.value = true
  try {
    const response = await getFinanceSubmissionCenter()
    const envelope = unwrapFinanceEnvelope(response)
    center.value = envelope.data || { summary: {}, submissions: [] }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '报销汇总加载失败')
  } finally {
    loading.value = false
  }
}

const formatMoney = value => {
  const amount = Number(value || 0)
  if (Number.isNaN(amount)) return '¥0.00'
  return `¥${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const formatType = value => {
  if (value === 'PERSONAL_PROCUREMENT') return '个人采购'
  if (value === 'PROJECT_TRAVEL_REIMBURSEMENT') return '出差报销'
  return value || '未知类型'
}

const formatStatus = value => {
  if (value === 'SUBMITTED') return '已提交'
  return value || '未知状态'
}

const formatFlowType = value => {
  if (value === 'PROJECT') return '项目交付'
  if (value === 'PRODUCT') return '产品研发'
  if (value === 'RESEARCH') return '科研创新'
  return value || '—'
}

const formatDateTime = value => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', { hour12: false })
}

const formatOccurredAt = row => {
  if (!row?.occurredAt) return '未填写日期'
  const date = new Date(row.occurredAt)
  if (Number.isNaN(date.getTime())) return '未填写日期'
  return date.toLocaleDateString('zh-CN')
}

const formatRemarks = row => {
  const parts = []
  if (row?.departureLocation || row?.destinationLocation) {
    parts.push(`${row.departureLocation || '—'} -> ${row.destinationLocation || '—'}`)
  }
  if (row?.travelStartAt || row?.travelEndAt) {
    parts.push(`${formatDateTime(row.travelStartAt)} ~ ${formatDateTime(row.travelEndAt)}`)
  }
  if (row?.remarks) {
    parts.push(row.remarks)
  }
  return parts.join(' · ') || '无补充说明'
}

const downloadInvoice = async row => {
  try {
    const blob = await downloadFinanceSubmissionInvoice(row.id)
    const objectUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = row.invoiceFileName || `invoice-${row.id}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(objectUrl)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '发票下载失败')
  }
}

onMounted(() => {
  exportDateRange.value = defaultExportRange()
  loadCenter()
  window.addEventListener('finance-global-refresh', loadCenter)
})

onBeforeUnmount(() => {
  window.removeEventListener('finance-global-refresh', loadCenter)
})
</script>

<style scoped>
.expense-center-root {
  display: grid;
  gap: 18px;
}

.hero-card,
.list-card {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(237, 254, 255, 0.62));
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #0f766e;
}

.export-bar {
  background: white;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.hero-card h1,
.card-head h3 {
  margin: 0;
}

.hero-copy {
  margin: 10px 0 0;
  color: #475569;
  max-width: 720px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.16);
  display: grid;
  gap: 6px;
}

.summary-card span {
  color: #475569;
  font-size: 12px;
}

.summary-card strong {
  color: #0f172a;
  font-size: 18px;
}

.table-wrap {
  overflow-x: auto;
}

.submission-table {
  width: 100%;
  border-collapse: collapse;
}

.submission-table th,
.submission-table td {
  text-align: left;
  padding: 12px 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  vertical-align: top;
}

.submission-table th {
  color: #0f172a;
  font-size: 12px;
}

.primary-line {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
}

.sub-line {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.download-link {
  border: none;
  background: transparent;
  padding: 0;
  color: #2563eb;
  cursor: pointer;
}

.download-link:hover {
  text-decoration: underline;
}

@media (max-width: 1080px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .hero-card {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

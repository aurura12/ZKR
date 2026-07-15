<template>
  <div class="review-container">
    <div class="review-header">
      <h2>📋 费用审批</h2>
      <span class="subtitle">审批人工作台 · {{ new Date().toLocaleDateString() }}</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">月份</span>
        <el-select v-model="filterMonth" placeholder="全部月份" clearable size="small" style="width:130px">
          <el-option v-for="m in monthOptions" :key="m" :label="m" :value="m" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">状态</span>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width:120px">
          <el-option label="待审批" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已拒绝" value="rejected" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">人员</span>
        <el-select v-model="filterPerson" placeholder="全部人员" clearable size="small" style="width:120px">
          <el-option v-for="p in personOptions" :key="p" :label="p" :value="p" />
        </el-select>
      </div>
      <span class="filter-count">共 {{ filteredExpenses.length }} 条</span>
    </div>

    <!-- 月份分组展示 -->
    <div v-if="groupedByMonth.length === 0" class="empty-state">暂无匹配的费用记录</div>
    <div v-for="group in groupedByMonth" :key="group.month" class="month-group">
      <div class="month-header" @click="group.expanded = !group.expanded">
        <span class="month-toggle">{{ group.expanded ? '▼' : '▶' }}</span>
        <span class="month-title">📅 {{ group.month }}</span>
        <span class="month-count">{{ group.items.length }} 条 · ¥{{ formatMoney(group.total) }}</span>
      </div>
      <template v-if="group.expanded">
        <div v-for="exp in group.items" :key="exp.id" class="expense-card" :class="{ history: isReviewed(exp) }">
          <span class="expense-status" :class="statusClass(exp.status)">{{ statusLabel(exp.status) }}</span>
          <div class="expense-top">
            <span class="expense-type" :class="typeClass(exp.expenseType)">{{ typeLabel(exp.expenseType) }}</span>
          </div>
          <div class="expense-body">
            <div class="expense-name">{{ exp.itemName }}</div>
            <div class="expense-meta">
              <span>{{ exp.projectName }}</span>
              <span>提交人: {{ exp.submitterName }}</span>
              <span>{{ formatTime(exp.createdAt) }}</span>
            </div>
            <div class="expense-amount">¥{{ formatMoney(exp.amount) }}</div>
            <div v-if="exp.files && exp.files.length" class="expense-attachment">
              <div v-for="f in exp.files" :key="f.id" class="attachment-file-row">
                <el-link type="primary" :underline="false" @click="previewFile(f)">
                  📎 {{ f.fileName }}
                </el-link>
              </div>
            </div>
            <div v-else-if="exp.invoiceFileName" class="expense-attachment">
              <el-link type="primary" :underline="false" @click="previewLegacyInvoice(exp)">
                📎 附件：{{ exp.invoiceFileName }}
              </el-link>
            </div>
          </div>
          <div class="expense-review-log">
            <span v-if="exp.jiaomiaoAction">焦淼: {{ exp.jiaomiaoAction === 'APPROVE' ? '✅ 通过' : '❌ 拒绝' }} {{ formatTime(exp.jiaomiaoAt) }}</span>
            <span v-if="exp.financeAction">财务: {{ exp.financeAction === 'APPROVE' ? '✅ 通过' : '❌ 拒绝' }} {{ formatTime(exp.financeAt) }}</span>
            <span v-if="exp.chenleiAction">陈磊: {{ exp.chenleiAction === 'APPROVE' ? '✅ 通过' : '❌ 拒绝' }} {{ formatTime(exp.chenleiAt) }}</span>
            <span v-if="exp.rejectReason" class="reject-reason">原因: {{ exp.rejectReason }}</span>
          </div>
          <div v-if="canApprove(exp)" class="expense-actions">
            <el-button size="small" type="success" @click="review(exp.id, 'APPROVE')">通过</el-button>
            <el-button size="small" type="danger" @click="openReject(exp)">拒绝</el-button>
          </div>
          <div v-else-if="canRevoke(exp)" class="expense-actions">
            <el-button size="small" type="warning" @click="review(exp.id, 'REVOKE')">反审批</el-button>
          </div>
        </div>
      </template>
    </div>

    <el-dialog v-model="showRejectDialog" title="拒绝原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝原因" />
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInvoiceDialog" :title="'附件预览：' + previewingFileName" width="70%" custom-class="invoice-preview-dialog" @closed="closeInvoicePreview">
      <div v-if="previewingType === 'image'" class="invoice-preview-image">
        <img :src="invoicePreviewUrl" :alt="previewingFileName" style="max-width: 100%; max-height: 70vh;" />
      </div>
      <div v-else-if="previewingType === 'pdf'" class="invoice-preview-pdf">
        <iframe :src="invoicePreviewUrl" style="width: 100%; height: 70vh; border: none;" />
      </div>
      <div v-else class="invoice-preview-other">
        <p>此文件类型无法直接预览，请点击下方按钮在浏览器中打开：</p>
        <p class="preview-file-name">{{ previewingFileName }}</p>
        <el-button type="primary" @click="downloadInvoice">在浏览器中打开</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()

const allExpenses = ref([])
const showRejectDialog = ref(false)
const rejectReason = ref('')
const rejectTargetId = ref(null)

const showInvoiceDialog = ref(false)
const previewingFileName = ref('')
const previewingType = ref('')
const invoicePreviewUrl = ref('')
const previewingExpenseId = ref(null)

// 筛选状态
const filterMonth = ref('')
const filterStatus = ref('')
const filterPerson = ref('')

const typeLabel = t => ({ HARDWARE: '硬件采购', EXTERNAL_SERVICE: '合同上传', REIMBURSEMENT: '报销', BUSINESS_MEAL: '商务餐费', NORMAL_TRAVEL: '正常差旅', PRICE_DIFF: '补差价' }[t] || t)
const typeClass = t => `type-${t?.toLowerCase()}`
const statusLabel = s => ({ PENDING_JIAOMIAO: '待焦淼审批', PENDING_FINANCE: '待财务审批', PENDING_CHENLEI: '待陈磊审批', APPROVED: '已通过', REJECTED: '已拒绝' }[s] || s)
const statusClass = s => `status-${s?.toLowerCase()}`

const formatMoney = v => {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatTime = v => {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const formatMonth = v => {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  return `${d.getFullYear()}年${String(d.getMonth() + 1).padStart(2, '0')}月`
}

const isJiaomiao = computed(() => String(userStore.activeUserInfo?.userId) === '000027')
const isChenlei = computed(() => String(userStore.activeUserInfo?.userId) === '000044')
const isFinance = computed(() => String(userStore.activeUserInfo?.userId) === '000101')

// 当前用户在该阶段是否有审批权限
const canApprove = exp => {
  if (!exp) return false
  if (isJiaomiao.value && exp.status === 'PENDING_JIAOMIAO') return true
  if (isFinance.value && exp.status === 'PENDING_FINANCE') return true
  if (isChenlei.value && exp.status === 'PENDING_CHENLEI') return true
  return false
}

const canRevoke = exp => {
  if (isJiaomiao.value && exp.jiaomiaoAction && !exp.financeAction) return true
  if (isFinance.value && exp.financeAction && !exp.chenleiAction) return true
  if (isChenlei.value && exp.chenleiAction) return true
  return false
}

// 判断是否已有审批记录（用于卡片样式）
const isReviewed = exp => {
  return exp.jiaomiaoAction || exp.financeAction || exp.chenleiAction
}

// ---- 筛选选项 ----
const monthOptions = computed(() => {
  const set = new Set()
  allExpenses.value.forEach(e => {
    if (e.createdAt) set.add(formatMonth(e.createdAt))
  })
  return [...set].sort().reverse()
})

const personOptions = computed(() => {
  const set = new Set()
  allExpenses.value.forEach(e => {
    if (e.submitterName) set.add(e.submitterName)
  })
  return [...set].sort()
})

// ---- 筛选逻辑 ----
const filteredExpenses = computed(() => {
  return allExpenses.value.filter(e => {
    if (filterMonth.value && formatMonth(e.createdAt) !== filterMonth.value) return false
    if (filterStatus.value === 'pending' && !['PENDING_JIAOMIAO', 'PENDING_FINANCE', 'PENDING_CHENLEI'].includes(e.status)) return false
    if (filterStatus.value === 'approved' && e.status !== 'APPROVED') return false
    if (filterStatus.value === 'rejected' && e.status !== 'REJECTED') return false
    if (filterPerson.value && e.submitterName !== filterPerson.value) return false
    return true
  })
})

// ---- 月份分组 ----
const groupedByMonth = computed(() => {
  const map = {}
  filteredExpenses.value.forEach(e => {
    const m = e.createdAt ? formatMonth(e.createdAt) : '未知月份'
    if (!map[m]) map[m] = { month: m, items: [], expanded: true, total: 0 }
    map[m].items.push(e)
    map[m].total += Number(e.amount || 0)
  })
  return Object.values(map).sort((a, b) => b.month.localeCompare(a.month))
})

const loadData = async () => {
  try {
    const res = await request.get('/api/projects/expenses/review-list')
    const pending = res?.pending || []
    const history = res?.history || []
    allExpenses.value = [...pending, ...history]
  } catch (e) {
    ElMessage.error('加载审批列表失败')
  }
}

const review = async (id, action) => {
  try {
    await request.post(`/api/projects/expenses/${id}/review`, { action })
    ElMessage.success('操作成功')
    await loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '操作失败')
  }
}

const openReject = exp => {
  rejectTargetId.value = exp.id
  rejectReason.value = ''
  showRejectDialog.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) return ElMessage.warning('请输入拒绝原因')
  try {
    await request.post(`/api/projects/expenses/${rejectTargetId.value}/review`, {
      action: 'REJECT',
      reason: rejectReason.value.trim()
    })
    ElMessage.success('已拒绝')
    showRejectDialog.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '操作失败')
  }
}

const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp']
const PDF_EXTS = ['pdf']

const getFileType = fileName => {
  if (!fileName) return 'other'
  const ext = fileName.split('.').pop()?.toLowerCase()
  if (IMAGE_EXTS.includes(ext)) return 'image'
  if (PDF_EXTS.includes(ext)) return 'pdf'
  return 'other'
}

const previewFile = async file => {
  if (!file || !file.fileName) return
  previewingFileName.value = file.fileName
  previewingType.value = getFileType(file.fileName)
  previewingExpenseId.value = file.id
  try {
    const blob = await request.get(`/api/projects/expenses/files/${file.id}/download`, { responseType: 'blob' })
    invoicePreviewUrl.value = window.URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('加载附件失败: ' + (e.response?.data?.message || e.message || e))
    return
  }
  showInvoiceDialog.value = true
}

const previewLegacyInvoice = async exp => {
  if (!exp.invoiceFileName) return
  previewingFileName.value = exp.invoiceFileName
  previewingType.value = getFileType(exp.invoiceFileName)
  previewingExpenseId.value = exp.id
  try {
    const blob = await request.get(`/api/projects/expenses/${exp.id}/invoice`, { responseType: 'blob' })
    invoicePreviewUrl.value = window.URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('加载附件失败: ' + (e.response?.data?.message || e.message || e))
    return
  }
  showInvoiceDialog.value = true
}

const closeInvoicePreview = () => {
  if (invoicePreviewUrl.value) {
    window.URL.revokeObjectURL(invoicePreviewUrl.value)
  }
  invoicePreviewUrl.value = ''
  previewingExpenseId.value = null
}

const downloadInvoice = () => {
  if (invoicePreviewUrl.value) {
    const link = document.createElement('a')
    link.href = invoicePreviewUrl.value
    link.download = previewingFileName.value
    link.click()
  }
}

onMounted(() => {
  if (isJiaomiao.value || isFinance.value || isChenlei.value) {
    loadData()
  }
})
</script>

<style scoped>
.review-container { max-width: 960px; margin: 0 auto; padding: 30px; }
.review-header { margin-bottom: 20px; }
.review-header h2 { font-size: 28px; font-weight: 700; margin: 0; }
.subtitle { color: #94a3b8; font-size: 14px; }
.empty-state { color: #94a3b8; padding: 24px; text-align: center; background: #f8fafc; border-radius: 8px; margin-top: 16px; }

/* 筛选栏 */
.filter-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; padding: 12px 16px; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; flex-wrap: wrap; }
.filter-group { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 13px; color: #64748b; white-space: nowrap; }
.filter-count { margin-left: auto; font-size: 13px; color: #94a3b8; }

/* 月份分组 */
.month-group { margin-bottom: 16px; }
.month-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: #f1f5f9; border-radius: 8px; cursor: pointer; user-select: none; margin-bottom: 8px; }
.month-header:hover { background: #e2e8f0; }
.month-toggle { font-size: 12px; color: #64748b; width: 14px; text-align: center; }
.month-title { font-size: 15px; font-weight: 600; color: #1e293b; }
.month-count { font-size: 12px; color: #94a3b8; margin-left: auto; }

/* 卡片 */
.expense-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; margin-bottom: 10px; position: relative; }
.expense-card.history { opacity: 0.85; }
.expense-top { display: flex; gap: 8px; margin-bottom: 8px; }
.expense-type { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.expense-status { position: absolute; top: 12px; right: 16px; font-size: 18px; font-weight: 700; padding: 4px 14px; border-radius: 8px; }
.type-hardware { background: #e3f2fd; color: #1565c0; }
.type-external_service { background: #f3e5f5; color: #7b1fa2; }
.type-reimbursement { background: #fce4ec; color: #c62828; }
.type-business_meal { background: #fff8e1; color: #e65100; }
.type-normal_travel { background: #e8f5e9; color: #2e7d32; }
.type-price_diff { background: #fce4ec; color: #c62828; }
.status-pending_jiaomiao, .status-pending_finance, .status-pending_chenlei { background: #fff3e0; color: #e65100; }
.status-approved { background: #e8f5e9; color: #2e7d32; }
.status-rejected { background: #ffebee; color: #c62828; }
.expense-body { margin-bottom: 8px; }
.expense-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.expense-meta { font-size: 12px; color: #94a3b8; display: flex; gap: 12px; flex-wrap: wrap; }
.expense-amount { font-size: 18px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.expense-actions { display: flex; gap: 8px; justify-content: flex-end; }
.expense-review-log { font-size: 12px; color: #64748b; display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 8px; }
.reject-reason { color: #c62828; }
.expense-attachment { margin-top: 6px; font-size: 13px; }
.invoice-preview-image { text-align: center; }
.invoice-preview-other { text-align: center; padding: 20px; }
.preview-file-name { color: #1e293b; font-weight: 600; margin: 8px 0 16px; }
</style>

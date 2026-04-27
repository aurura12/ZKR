<template>
  <div class="review-container">
    <div class="review-header">
      <h2>📋 费用审批</h2>
      <span class="subtitle">审批人工作台 · {{ new Date().toLocaleDateString() }}</span>
    </div>

    <section class="review-section">
      <h3 class="section-title">⏳ 待审批</h3>
      <div v-if="!pending.length" class="empty-state">暂无待审批费用</div>
      <div v-for="exp in pending" :key="exp.id" class="expense-card">
        <div class="expense-top">
          <span class="expense-type" :class="typeClass(exp.expenseType)">{{ typeLabel(exp.expenseType) }}</span>
          <span class="expense-status" :class="statusClass(exp.status)">{{ statusLabel(exp.status) }}</span>
        </div>
        <div class="expense-body">
          <div class="expense-name">{{ exp.itemName }}</div>
          <div class="expense-meta">
            <span>{{ exp.projectName }}</span>
            <span>提交人: {{ exp.submitterName }}</span>
            <span>{{ formatTime(exp.createdAt) }}</span>
          </div>
          <div class="expense-amount">¥{{ formatMoney(exp.amount) }}</div>
        </div>
        <div class="expense-actions">
          <el-button size="small" type="success" @click="review(exp.id, 'APPROVE')">通过</el-button>
          <el-button size="small" type="danger" @click="openReject(exp)">拒绝</el-button>
        </div>
      </div>
    </section>

    <section class="review-section">
      <h3 class="section-title">📜 历史记录</h3>
      <div v-if="!history.length" class="empty-state">暂无历史记录</div>
      <div v-for="exp in history" :key="exp.id" class="expense-card history">
        <div class="expense-top">
          <span class="expense-type" :class="typeClass(exp.expenseType)">{{ typeLabel(exp.expenseType) }}</span>
          <span class="expense-status" :class="statusClass(exp.status)">{{ statusLabel(exp.status) }}</span>
        </div>
        <div class="expense-body">
          <div class="expense-name">{{ exp.itemName }}</div>
          <div class="expense-meta">
            <span>{{ exp.projectName }}</span>
            <span>提交人: {{ exp.submitterName }}</span>
            <span>{{ formatTime(exp.createdAt) }}</span>
          </div>
          <div class="expense-amount">¥{{ formatMoney(exp.amount) }}</div>
        </div>
        <div class="expense-review-log">
          <span v-if="exp.jiaomiaoAction">焦淼: {{ exp.jiaomiaoAction === 'APPROVE' ? '✅ 通过' : '❌ 拒绝' }} {{ formatTime(exp.jiaomiaoAt) }}</span>
          <span v-if="exp.chenleiAction">陈磊: {{ exp.chenleiAction === 'APPROVE' ? '✅ 通过' : '❌ 拒绝' }} {{ formatTime(exp.chenleiAt) }}</span>
          <span v-if="exp.rejectReason" class="reject-reason">原因: {{ exp.rejectReason }}</span>
        </div>
        <div class="expense-actions" v-if="canRevoke(exp)">
          <el-button size="small" type="warning" @click="review(exp.id, 'REVOKE')">反审批</el-button>
        </div>
      </div>
    </section>

    <el-dialog v-model="showRejectDialog" title="拒绝原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝原因" />
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const pending = ref([])
const history = ref([])
const showRejectDialog = ref(false)
const rejectReason = ref('')
const rejectTargetId = ref(null)

const typeLabel = t => ({ HARDWARE: '硬件采购', EXTERNAL_SERVICE: '外部技术服务', REIMBURSEMENT: '报销' }[t] || t)
const typeClass = t => `type-${t?.toLowerCase()}`
const statusLabel = s => ({ PENDING_JIAOMIAO: '待焦淼审批', PENDING_CHENLEI: '待陈磊审批', APPROVED: '已通过', REJECTED: '已拒绝' }[s] || s)
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

const isJiaomiao = computed(() => String(userStore.activeUserInfo?.userId) === '000027')
const isChenlei = computed(() => String(userStore.activeUserInfo?.userId) === '000044')

const canRevoke = exp => {
  if (isJiaomiao.value && exp.jiaomiaoAction && !exp.chenleiAction) return true
  if (isChenlei.value && exp.chenleiAction) return true
  return false
}

const loadData = async () => {
  try {
    const res = await request.get('/api/projects/expenses/review-list')
    pending.value = res.data?.pending || []
    history.value = res.data?.history || []
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

onMounted(() => {
  if (isJiaomiao.value || isChenlei.value) {
    loadData()
  }
})
</script>

<style scoped>
.review-container { max-width: 900px; margin: 0 auto; padding: 30px; }
.review-header { margin-bottom: 24px; }
.review-header h2 { font-size: 28px; font-weight: 700; margin: 0; }
.subtitle { color: #94a3b8; font-size: 14px; }
.section-title { font-size: 18px; font-weight: 600; margin: 24px 0 12px; }
.empty-state { color: #94a3b8; padding: 24px; text-align: center; background: #f8fafc; border-radius: 8px; }
.expense-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; margin-bottom: 12px; }
.expense-card.history { opacity: 0.85; }
.expense-top { display: flex; gap: 8px; margin-bottom: 8px; }
.expense-type { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.type-hardware { background: #e3f2fd; color: #1565c0; }
.type-external_service { background: #f3e5f5; color: #7b1fa2; }
.type-reimbursement { background: #e8f5e9; color: #2e7d32; }
.expense-status { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.status-pending_jiaomiao, .status-pending_chenlei { background: #fff3e0; color: #e65100; }
.status-approved { background: #e8f5e9; color: #2e7d32; }
.status-rejected { background: #ffebee; color: #c62828; }
.expense-body { margin-bottom: 8px; }
.expense-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.expense-meta { font-size: 12px; color: #94a3b8; display: flex; gap: 12px; }
.expense-amount { font-size: 18px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.expense-actions { display: flex; gap: 8px; justify-content: flex-end; }
.expense-review-log { font-size: 12px; color: #64748b; display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 8px; }
.reject-reason { color: #c62828; }
</style>

<template>
  <div class="log-container">
    <div class="log-header">
      <h2>📊 成本调整日志</h2>
      <span class="subtitle">全部项目的成本调整操作追溯记录</span>
    </div>

    <div v-if="!rows.length" class="empty-state">暂无调整记录</div>
    <div v-else class="log-list">
      <div v-for="row in rows" :key="row.id" class="log-card">
        <div class="log-top">
          <span class="log-type" :class="typeClass(row.adjustmentType)">{{ typeLabel(row.adjustmentType) }}</span>
          <span class="log-time">{{ formatTime(row.createdAt) }}</span>
        </div>
        <div class="log-body">
          <div class="log-project">{{ row.projectName }}</div>
          <div class="log-name">{{ row.itemName }}</div>
          <div class="log-amount">¥{{ formatMoney(row.amount) }}</div>
          <div class="log-operator">操作人: {{ row.operatorName }}</div>
        </div>
        <div v-if="row.invoiceFileName" class="log-invoice">📎 {{ row.invoiceFileName }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const rows = ref([])

const typeLabel = t => ({
  HARDWARE: '硬件采购',
  SERVER_COMPUTE: '服务器算力',
  EXTERNAL_SERVICE: '外部技术服务',
  REIMBURSEMENT: '报销'
}[t] || t)

const typeClass = t => `type-${t?.toLowerCase()}`

const formatMoney = v => {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatTime = v => {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

import { ElMessage } from 'element-plus'

onMounted(async () => {
  try {
    rows.value = await request.get('/api/finance/cost-adjustments')
  } catch (e) {
    ElMessage.error('加载成本调整日志失败')
    console.error(e)
  }
})
</script>

<style scoped>
.log-container { max-width: 900px; margin: 0 auto; padding: 30px; }
.log-header { margin-bottom: 24px; }
.log-header h2 { font-size: 28px; font-weight: 700; margin: 0 0 4px; }
.subtitle { color: #94a3b8; font-size: 14px; }
.empty-state { color: #94a3b8; padding: 24px; text-align: center; background: #f8fafc; border-radius: 8px; }
.log-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; margin-bottom: 10px; }
.log-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.log-type { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.type-hardware { background: #e3f2fd; color: #1565c0; }
.type-server_compute { background: #fff3e0; color: #e65100; }
.type-external_service { background: #f3e5f5; color: #7b1fa2; }
.type-reimbursement { background: #e8f5e9; color: #2e7d32; }
.log-time { font-size: 11px; color: #94a3b8; }
.log-project { font-size: 13px; font-weight: 600; color: #334155; }
.log-name { font-size: 14px; font-weight: 500; margin: 2px 0; }
.log-amount { font-size: 18px; font-weight: 700; color: #1e293b; }
.log-operator { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.log-invoice { font-size: 11px; color: #64748b; margin-top: 6px; }
</style>

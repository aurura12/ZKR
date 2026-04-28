<template>
  <div class="log-container">
    <div class="log-header">
      <h2>⚙️ 成本跑批日志</h2>
      <span class="subtitle">每天凌晨执行一次，仅跑昨天的账期，不可重复执行</span>
    </div>

    <div v-if="!rows.length" class="empty-state">暂无跑批记录</div>
    <div v-else class="log-list">
      <div v-for="row in rows" :key="row.id" class="log-card">
        <div class="log-top">
          <span class="log-status" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
          <span class="log-time">{{ formatTime(row.startedAt || row.createdAt) }}</span>
        </div>
        <div class="log-body">
          <div class="log-month">账期: {{ row.ledgerMonth }}</div>
          <div v-if="row.batchDate" class="log-date">执行日: {{ row.batchDate }}</div>
          <div v-if="row.generatedRecordCount != null" class="log-count">产出记录: {{ row.generatedRecordCount }} 条</div>
          <div v-if="row.remark" class="log-remark">{{ row.remark }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const rows = ref([])

const statusLabel = s => ({
  COMPLETED: '成功', RUNNING: '执行中', FAILED: '失败' }[s] || s)

const statusClass = s => ({
  COMPLETED: 'status-completed',
  RUNNING: 'status-running',
  FAILED: 'status-failed' }[s] || '')

const formatTime = v => {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  try {
    rows.value = await request.get('/api/finance/batch-log')
  } catch {}
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
.log-status { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.status-completed { background: #e8f5e9; color: #2e7d32; }
.status-running { background: #fff3e0; color: #e65100; }
.status-failed { background: #ffebee; color: #c62828; }
.log-time { font-size: 11px; color: #94a3b8; }
.log-month { font-size: 14px; font-weight: 600; }
.log-date, .log-count { font-size: 13px; color: #475569; margin-top: 2px; }
.log-remark { font-size: 12px; color: #c62828; margin-top: 4px; word-break: break-all; }
</style>

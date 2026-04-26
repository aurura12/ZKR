<template>
  <section class="attendance-view">
    <!-- Hero 区域 -->
    <div class="hero-card">
      <div>
        <p class="eyebrow">考勤</p>
        <h1>🕐 考勤工资</h1>
        <p class="description">钉钉考勤打卡数据总览，支持历史查询与纠偏调整。</p>
      </div>
      <div class="hero-metrics">
        <div class="metric-pill">
          <span class="metric-label">本月正常</span>
          <strong>{{ monthlyStats.normal }}</strong>
        </div>
        <div class="metric-pill">
          <span class="metric-label">本月迟到</span>
          <strong>{{ monthlyStats.late }}</strong>
        </div>
        <div class="metric-pill">
          <span class="metric-label">本月早退</span>
          <strong>{{ monthlyStats.early }}</strong>
        </div>
        <div class="metric-pill">
          <span class="metric-label">本月异常</span>
          <strong>{{ monthlyStats.illegal }}</strong>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        @change="fetchRecords"
      />
      <el-input v-model="filterUserId" placeholder="钉钉 userId（非系统ID）" style="width: 200px" clearable />
      <el-select v-model="filterResult" placeholder="打卡结果" style="width: 150px" clearable>
        <el-option label="正常" value="Normal" />
        <el-option label="迟到" value="Late" />
        <el-option label="早退" value="Early" />
        <el-option label="旷工" value="Absenteeism" />
        <el-option label="未打卡" value="NotSigned" />
      </el-select>
      <el-button type="primary" @click="fetchRecords">查询</el-button>
      <el-button @click="triggerManualPull" :loading="pulling">手动拉取</el-button>
    </div>

    <!-- 数据表格 -->
    <div class="panel-card">
      <header class="section-header">
        <div>
          <span>考勤记录</span>
          <h2>打卡明细</h2>
        </div>
      </header>
      <el-table :data="records" v-loading="loading" stripe style="width: 100%" :default-sort="{ prop: 'workDate', order: 'descending' }">
        <el-table-column prop="workDate" label="工作日" width="110" sortable />
        <el-table-column label="员工" width="180">
          <template #default="{ row }">
            <el-tooltip :content="'钉钉ID：' + row.userId" placement="top-start" :hide-after="0">
              <div class="user-cell">
                <div class="user-name">{{ row.userName || row.userId }}</div>
                <div class="user-id">{{ row.userName ? '悬停查看钉钉ID' : '钉钉ID：' + row.userId }}</div>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="上班打卡" width="170">
          <template #default="{ row }">
            <div v-if="getCheckIn(row)">
              <div>{{ getCheckIn(row).time }}</div>
              <el-tag :type="getCheckIn(row).result === 'Normal' ? 'success' : 'danger'" size="small">
                {{ getCheckIn(row).result }}
              </el-tag>
            </div>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column label="下班打卡" width="170">
          <template #default="{ row }">
            <div v-if="getCheckOut(row)">
              <div>{{ getCheckOut(row).time }}</div>
              <el-tag :type="getCheckOut(row).result === 'Normal' ? 'success' : 'danger'" size="small">
                {{ getCheckOut(row).result }}
              </el-tag>
            </div>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationResult" label="位置" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.locationResult === 'Normal'" type="success" size="small">正常</el-tag>
            <el-tag v-else-if="row.locationResult === 'Outside'" type="warning" size="small">范围外</el-tag>
            <span v-else class="empty-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="isLegal" label="合法" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isLegal === 'Y' ? 'success' : 'danger'" size="small">{{ row.isLegal === 'Y' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userAddress" label="打卡地点" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openAdjustDialog(row)">申请修正</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 纠偏对话框 -->
    <el-dialog v-model="showAdjustDialog" title="申请考勤纠偏" width="500px">
      <el-form label-position="top">
        <el-form-item label="被调整员工">
          <el-input :model-value="adjustForm.userId" disabled />
        </el-form-item>
        <el-form-item label="被调整日期">
          <el-input :model-value="adjustForm.workDate" disabled />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.reason" type="textarea" rows="3" placeholder="请输入调整原因" />
        </el-form-item>
        <el-form-item label="正确上班打卡时间">
          <el-date-picker v-model="adjustForm.newOnDutyTime" type="datetime" value-format="x" placeholder="选择正确时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="正确下班打卡时间">
          <el-date-picker v-model="adjustForm.newOffDutyTime" type="datetime" value-format="x" placeholder="选择正确时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAdjustment" :loading="adjusting">提交纠偏</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const pulling = ref(false)
const adjusting = ref(false)
const records = ref([])
const dateRange = ref([])
const filterUserId = ref('')
const filterResult = ref('')
const showAdjustDialog = ref(false)
const adjustForm = reactive({
  userId: '',
  workDate: '',
  reason: '',
  newOnDutyTime: null,
  newOffDutyTime: null,
  originalData: {},
  adjustedData: {}
})

const monthlyStats = computed(() => {
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
  const thisMonth = records.value.filter(r => r.workDate >= monthStart)
  return {
    normal: thisMonth.filter(r => r.timeResult === 'Normal').length,
    late: thisMonth.filter(r => r.timeResult === 'Late' || r.timeResult === 'SeriousLate').length,
    early: thisMonth.filter(r => r.timeResult === 'Early').length,
    illegal: thisMonth.filter(r => r.isLegal === 'N').length
  }
})

const initDateRange = () => {
  const end = new Date()
  const start = new Date(Date.now() - 7 * 86400000)
  dateRange.value = [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)]
}

const fetchRecords = async () => {
  loading.value = true
  try {
    const params = {}
    if (dateRange.value && dateRange.value.length === 2) {
      params.from = dateRange.value[0]
      params.to = dateRange.value[1]
    }
    if (filterUserId.value) params.userId = filterUserId.value
    const res = await request.get('/api/attendance/records', { params })
    let data = Array.isArray(res) ? res : (res.data || [])
    if (filterResult.value) {
      data = data.filter(r => r.timeResult === filterResult.value)
    }
    records.value = data
  } catch (e) {
    ElMessage.error('获取考勤记录失败: ' + (e.response?.data?.message || e.message || e))
  } finally {
    loading.value = false
  }
}

const triggerManualPull = async () => {
  pulling.value = true
  try {
    const [from, to] = dateRange.value || []
    await request.post('/api/attendance/pull', null, { params: { from, to } })
    ElMessage.success('拉取已触发，请稍后刷新查询')
    await fetchRecords()
  } catch (e) {
    ElMessage.error('触发拉取失败: ' + (e.response?.data?.message || e.message || e))
  } finally {
    pulling.value = false
  }
}

const getCheckIn = record => {
  if (!record || record.checkType !== 'OnDuty') return null
  return {
    time: record.userCheckTime ? new Date(record.userCheckTime).toLocaleString('zh-CN') : '—',
    result: record.timeResult || '—'
  }
}

const getCheckOut = record => {
  if (!record || record.checkType !== 'OffDuty') return null
  return {
    time: record.userCheckTime ? new Date(record.userCheckTime).toLocaleString('zh-CN') : '—',
    result: record.timeResult || '—'
  }
}

const openAdjustDialog = row => {
  adjustForm.userId = row.userId
  adjustForm.workDate = row.workDate
  adjustForm.reason = ''
  adjustForm.newOnDutyTime = null
  adjustForm.newOffDutyTime = null
  adjustForm.originalData = { userCheckTime: row.userCheckTime, timeResult: row.timeResult }
  adjustForm.adjustedData = {}
  showAdjustDialog.value = true
}

const submitAdjustment = async () => {
  if (!adjustForm.reason) return ElMessage.warning('请填写调整原因')
  const adjustedData = {}
  if (adjustForm.newOnDutyTime) adjustedData.userCheckTime = adjustForm.newOnDutyTime
  if (adjustForm.newOffDutyTime) adjustedData.offDutyTime = adjustForm.newOffDutyTime
  try {
    await request.post('/api/attendance/adjustments', {
      userId: adjustForm.userId,
      adjustDate: adjustForm.workDate,
      originalData: adjustForm.originalData,
      adjustedData,
      reason: adjustForm.reason
    })
    ElMessage.success('纠偏申请已提交')
    showAdjustDialog.value = false
    await fetchRecords()
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.response?.data?.message || e.message || e))
  }
}

onMounted(() => {
  initDateRange()
  fetchRecords()
})
</script>

<style scoped>
.attendance-view {
  padding: 24px;
  min-height: 100vh;
  background: #f5f7fa;
}

.hero-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.hero-card h1 { margin: 0 0 8px; font-size: 28px; }
.hero-card .description { margin: 0; opacity: 0.85; }
.eyebrow { text-transform: uppercase; letter-spacing: 2px; font-size: 12px; opacity: 0.7; margin: 0 0 4px; }

.hero-metrics { display: flex; gap: 24px; }
.metric-pill {
  background: rgba(255,255,255,0.15);
  border-radius: 12px;
  padding: 12px 20px;
  text-align: center;
}
.metric-label { display: block; font-size: 12px; opacity: 0.75; margin-bottom: 4px; }
.metric-pill strong { font-size: 28px; font-weight: 700; }

.filter-bar {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.panel-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
}

.section-header { margin-bottom: 20px; }
.section-header span { font-size: 12px; color: #888; text-transform: uppercase; letter-spacing: 1px; }
.section-header h2 { margin: 4px 0 0; font-size: 18px; }

.empty-cell { color: #ccc; }

.content-grid { display: flex; flex-direction: column; gap: 16px; }

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 600;
  color: #1f2937;
}

.user-id {
  font-size: 12px;
  color: #6b7280;
}
</style>

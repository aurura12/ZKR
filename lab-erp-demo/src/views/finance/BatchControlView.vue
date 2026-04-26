<template>
  <section class="batch-control-view">
    <div class="hero-card">
      <div>
        <p class="eyebrow">系统</p>
        <h1>跑批控制台</h1>
        <p class="description">统一管理系统定时跑批任务，支持暂停、手动触发和项目级精细控制。</p>
      </div>
    </div>

    <div v-if="error" class="feedback-banner error">{{ error }}</div>

    <article class="panel-card">
      <header class="section-header">
        <div>
          <span>全局任务</span>
          <h2>跑批作业</h2>
        </div>
        <el-button type="primary" @click="fetchJobs" :loading="loading">刷新</el-button>
      </header>

      <el-table :data="jobs" v-loading="loading" stripe style="width:100%;margin-top:16px">
        <el-table-column prop="displayName" label="任务名称" min-width="160" />
        <el-table-column prop="jobKey" label="任务Key" width="200" />
        <el-table-column prop="scheduleMode" label="调度模式" width="140" />
        <el-table-column prop="runAtHour" label="执行时间" width="100">
          <template #default="{row}">
            {{ row.scheduleMode === 'DAILY_TIME' ? `${String(row.runAtHour||0).padStart(2,'0')}:${String(row.runAtMinute||0).padStart(2,'0')}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '已暂停' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastStatus" label="上次状态" width="110">
          <template #default="{row}">
            <el-tag v-if="row.lastStatus" :type="statusType(row.lastStatus)" size="small">{{ row.lastStatus }}</el-tag>
            <span v-else style="color:#94a3b8">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggeredAt" label="上次执行" width="160">
          <template #default="{row}">
            {{ row.lastTriggeredAt ? new Date(row.lastTriggeredAt).toLocaleString('zh-CN',{hour12:false}) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{row}">
            <el-switch
              :model-value="row.enabled"
              active-text="启用"
              inactive-text="暂停"
              style="margin-right:8px"
              @change="toggleJob(row, $event)"
            />
            <el-button type="primary" size="small" link @click="triggerJob(row)">手动触发</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <article class="panel-card">
      <header class="section-header">
        <div>
          <span>精细控制</span>
          <h2>项目成本跑批</h2>
        </div>
      </header>
      <p class="description" style="margin-bottom:16px">控制各项目是否参与成本跑批，数字越小优先级越高。</p>

      <el-table :data="projectControls" v-loading="projectLoading" stripe style="width:100%">
        <el-table-column prop="projectName" label="项目名称" min-width="200" />
        <el-table-column prop="projectId" label="项目ID" width="300" show-overflow-tooltip />
        <el-table-column prop="enabled" label="参与跑批" width="120">
          <template #default="{row}">
            <el-switch :model-value="row.enabled" active-text="" inactive-text="" @change="toggleProject(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{row}">
            <el-input-number
              v-model="row.priority"
              :min="1"
              :max="999"
              size="small"
              style="width:80px"
              @change="updateProjectPriority(row, row.priority)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="160">
          <template #default="{row}">
            <el-input v-model="row.note" size="small" placeholder="备注" @blur="updateProjectNote(row, row.note)" />
          </template>
        </el-table-column>
      </el-table>
    </article>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getBatchJobs,
  updateBatchJob,
  triggerBatchJob,
  getBatchProjectControls,
  updateBatchProjectControl
} from '@/api/finance/workbench'

const loading = ref(false)
const projectLoading = ref(false)
const error = ref('')
const jobs = ref([])
const projectControls = ref([])

const statusType = status => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

const fetchJobs = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getBatchJobs()
    jobs.value = (res.data || res || []).map(item => ({ ...item }))
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const toggleJob = async (job, enabled) => {
  try {
    await updateBatchJob({ jobKey: job.jobKey, enabled })
    job.enabled = enabled
    ElMessage.success(`${job.displayName} 已${enabled ? '启用' : '暂停'}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  }
}

const triggerJob = async job => {
  try {
    await triggerBatchJob({ jobKey: job.jobKey })
    ElMessage.success(`${job.displayName} 已触发`)
    await fetchJobs()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '触发失败')
  }
}

const fetchProjectControls = async () => {
  projectLoading.value = true
  try {
    const res = await getBatchProjectControls()
    projectControls.value = (res.data || res || []).map(item => ({ ...item }))
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败'
  } finally {
    projectLoading.value = false
  }
}

const toggleProject = async (row, enabled) => {
  try {
    await updateBatchProjectControl({ projectId: row.projectId, enabled })
    row.enabled = enabled
    ElMessage.success(`项目「${row.projectName}」已${enabled ? '启用' : '暂停'}跑批`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  }
}

const updateProjectPriority = async (row, priority) => {
  try {
    await updateBatchProjectControl({ projectId: row.projectId, priority })
    ElMessage.success(`项目「${row.projectName}」优先级已更新为 ${priority}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  }
}

const updateProjectNote = async (row, note) => {
  try {
    await updateBatchProjectControl({ projectId: row.projectId, note })
  } catch (e) {
    // silent
  }
}

onMounted(() => {
  fetchJobs()
  fetchProjectControls()
})
</script>

<style scoped>
.batch-control-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.hero-card,
.panel-card {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.08);
}
.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  background-image: linear-gradient(135deg, rgba(12, 74, 110, 0.08), rgba(217, 249, 157, 0.22));
}
.eyebrow,
.section-header span,
.metric-label {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0f766e;
}
h1, h2 {
  margin: 0;
  color: #0f172a;
}
h1 { font-size: clamp(28px, 3vw, 40px); }
h2 { font-size: 22px; }
.description {
  color: #475569;
  line-height: 1.6;
  max-width: 60ch;
  margin: 8px 0 0;
}
.panel-card { padding: 24px; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.feedback-banner {
  padding: 14px 18px;
  border-radius: 18px;
  border: 1px solid rgba(220, 38, 38, 0.18);
}
.feedback-banner.error { color: #b91c1c; }
</style>

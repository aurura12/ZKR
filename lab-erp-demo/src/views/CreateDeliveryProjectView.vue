<template>
  <div class="create-shell">
    <div class="card">
      <div class="header">
        <h2>🚀 发起项目（交付流）</h2>
        <button class="close-btn" @click="$router.back()">×</button>
      </div>

      <div class="body">
        <div class="field-label">项目名称</div>
        <el-input v-model="form.projectName" placeholder="项目名称" size="large" class="mb-3" />

        <div class="field-label">预计收入金额 <span class="field-unit">单位：元</span></div>
        <el-input v-model="form.estimatedRevenue" type="number" placeholder="请输入预计收入金额（元）" size="large" class="mb-3">
          <template #append>元</template>
        </el-input>

        <div class="field-label">项目行业</div>
        <el-select v-model="form.projectType" placeholder="选择行业" size="large" class="mb-3" style="width: 100%">
          <el-option label="工业" value="INDUSTRIAL" />
          <el-option label="军工" value="MILITARY" />
          <el-option label="医药" value="MEDICAL" />
          <el-option label="AI For Science" value="AI_FOR_SCIENCE" />
          <el-option label="群体智能" value="SWARM_INTEL" />
          <el-option label="自用" value="SELF_USE" />
        </el-select>

        <div class="field-label">指定数据工程师</div>
        <el-select
          v-model="form.dataEngineerId"
          filterable
          placeholder="指定数据工程师"
          size="large"
          class="mb-3"
          style="width: 100%"
          popper-class="project-data-engineer-select-popper"
        >
          <el-option v-for="u in dataEngineers" :key="u.id" :label="`${u.name} / ${u.role}`" :value="u.id" />
        </el-select>

        <div class="field-hint mb-3">
          {{ selectedEngineerHint }}
        </div>

        <div class="upload-card">
          <div class="upload-title">发起附件（可选）</div>
          <div class="upload-desc">可上传需求说明、背景材料或补充资料；该附件不替代后续可行性报告。</div>
          <div class="upload-actions">
            <label class="upload-button" for="initiation-attachment-input">选择文件</label>
            <button v-if="initiationAttachment" type="button" class="clear-button" @click="clearInitiationAttachment">移除</button>
          </div>
          <div class="upload-file-name">{{ initiationAttachment ? initiationAttachment.name : '未选择文件' }}</div>
          <input id="initiation-attachment-input" ref="attachmentInputRef" type="file" class="hidden-file-input" @change="handleAttachmentChange">
        </div>
      </div>

      <div class="footer">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">确认发起</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const submitting = ref(false)
const dataEngineers = ref([])
const initiationAttachment = ref(null)
const attachmentInputRef = ref(null)

const isDataRole = role => {
  const normalized = String(role || '').trim().toUpperCase()
  return normalized === 'DATA' || normalized === 'DATA_ENGINEER'
}

const form = ref({
  projectName: '',
  estimatedRevenue: '',
  dataEngineerId: '',
  projectType: ''
})

const selectedEngineer = computed(() => dataEngineers.value.find(item => String(item.id) === String(form.value.dataEngineerId || '')) || null)
const selectedEngineerHint = computed(() => {
  if (!selectedEngineer.value) {
    return '被选中的数据工程师会自动成为该项目的默认负责人，并立即在其工作区可见。'
  }
  return `${selectedEngineer.value.name} 将作为该项目的默认负责人，并会立即在其 Workspace 与管理视图中看到该项目。`
})

const canSubmit = computed(() => Boolean(
  String(form.value.projectName || '').trim() &&
  String(form.value.estimatedRevenue || '').trim() &&
  String(form.value.projectType || '').trim() &&
  String(form.value.dataEngineerId || '').trim()
))

onMounted(async () => {
  try {
    const res = await request.get('/api/users')
    const allUsers = (res.data || res) || []
    const seen = new Set()
    dataEngineers.value = allUsers
      .filter(u => isDataRole(u.role))
      .map(u => ({
        id: String(u.userId || ''),
        userId: u.userId,
        role: u.role,
        name: u.name && u.username ? `${u.name}（${u.username}）` : (u.name || u.username)
      }))
      .filter(u => {
        const dedupKey = `${u.userId}-DATA`
        if (seen.has(dedupKey)) return false
        seen.add(dedupKey)
        return true
      })
  } catch {
    dataEngineers.value = []
  }
})

const handleAttachmentChange = event => {
  initiationAttachment.value = event.target.files?.[0] || null
}

const clearInitiationAttachment = () => {
  initiationAttachment.value = null
  if (attachmentInputRef.value) {
    attachmentInputRef.value.value = ''
  }
}

const uploadInitiationAttachment = async projectId => {
  if (!projectId || !initiationAttachment.value) return
  const formData = new FormData()
  formData.append('file', initiationAttachment.value)
  formData.append('assetCategory', 'INITIATION_ATTACHMENT')
  await request.post(`/api/projects/${projectId}/assets`, formData, { timeout: 60000 })
}

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const response = await request.post('/api/projects/initiate', {
      projectName: form.value.projectName.trim(),
      estimatedRevenue: Number(form.value.estimatedRevenue),
      dataEngineerId: form.value.dataEngineerId,
      projectType: form.value.projectType
    })

    const createdProject = response?.data || response || {}
    if (initiationAttachment.value && createdProject.projectId) {
      try {
        await uploadInitiationAttachment(createdProject.projectId)
        ElMessage.success('项目交付发起成功，附件已同步上传')
      } catch (attachmentError) {
        ElMessage.warning(attachmentError.response?.data?.message || attachmentError.message || '项目已创建，但发起附件上传失败')
      }
    } else {
      ElMessage.success('项目交付发起成功')
    }

    router.push('/manager/dashboard')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '发起失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-shell { min-height: calc(100vh - var(--nav-height)); display: grid; place-items: center; background: var(--science-canvas); padding: 24px; }
.card { width: 100%; max-width: 720px; border: 1px solid var(--border-soft); border-radius: 16px; background: var(--science-surface); box-shadow: var(--shadow-md); overflow: hidden; }
.header { padding: 18px 22px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-soft); }
.header h2 { margin: 0; color: var(--text-main); font-size: 20px; }
.close-btn { border: none; background: transparent; color: var(--text-sub); font-size: 24px; cursor: pointer; }
.body { padding: 20px 22px; }
.footer { padding: 16px 22px; border-top: 1px solid var(--border-soft); background: var(--science-surface-muted); display: flex; justify-content: flex-end; gap: 10px; }
.mb-3 { margin-bottom: 12px; }
.field-label { margin-bottom: 8px; color: var(--text-main); font-size: 13px; font-weight: 700; }
.field-unit { color: var(--text-sub); font-weight: 600; }
.field-hint { color: var(--text-sub); font-size: 12px; line-height: 1.6; }
.upload-card { margin-top: 18px; border: 1px dashed var(--border-soft); border-radius: 14px; padding: 16px; background: var(--science-surface-muted); }
.upload-title { color: var(--text-main); font-size: 14px; font-weight: 700; }
.upload-desc { margin-top: 6px; color: var(--text-sub); font-size: 12px; line-height: 1.6; }
.upload-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 14px; }
.upload-button,
.clear-button { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 0 14px; border-radius: 10px; font-size: 13px; font-weight: 700; cursor: pointer; }
.upload-button { border: 1px solid #2563eb; color: #2563eb; background: #eff6ff; }
.clear-button { border: 1px solid var(--border-soft); color: var(--text-sub); background: #fff; }
.upload-file-name { margin-top: 12px; color: var(--text-main); font-size: 13px; word-break: break-all; }
.hidden-file-input { display: none; }
</style>

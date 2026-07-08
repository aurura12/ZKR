<template>
  <div v-if="embedded" class="embedded-form">
    <div class="form-section">
      <el-input v-model="form.projectName" placeholder="产品名称" size="large" class="mb-3">
        <template #prefix>📂</template>
      </el-input>
      <el-input v-model="form.expectedBudget" type="number" placeholder="预计项目投入 (CNY)" class="mb-3" size="large">
        <template #prefix>💰</template>
      </el-input>
      <el-input v-model="form.targetUsers" type="textarea" :rows="2" placeholder="目标用户群（必填）" class="mb-3" />
      <el-input v-model="form.coreFeatures" type="textarea" :rows="2" placeholder="主打功能点（必填）" class="mb-3" />
      <el-input v-model="form.useCase" type="textarea" :rows="2" placeholder="用途（必填）" class="mb-3" />
      <el-input v-model="form.problemStatement" type="textarea" :rows="2" placeholder="针对的问题（必填）" class="mb-3" />
      <el-input v-model="form.techStackDesc" type="textarea" :rows="2" placeholder="可能涉及的技术栈和深度（必填）" class="mb-3" />
      <div class="form-grid mb-3">
        <el-select v-model="form.projectType" placeholder="产品方向" class="flex-1" size="large">
          <el-option v-for="item in projectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="flex-1" />
      </div>
    </div>
  </div>
  <div v-else class="modal-overlay animate-fade-in">
    <div class="modal-card">
      <div class="modal-header">
        <h2>✨ 发起产品</h2>
        <span class="close-btn" @click="$router.back()">×</span>
      </div>
      <div class="modal-body">
        <div class="form-section">
          <el-input v-model="form.projectName" placeholder="产品名称" size="large" class="mb-3">
            <template #prefix>📂</template>
          </el-input>
          <el-input v-model="form.expectedBudget" type="number" placeholder="预计项目投入 (CNY)" class="mb-3" size="large">
            <template #prefix>💰</template>
          </el-input>
          <el-input v-model="form.targetUsers" type="textarea" :rows="2" placeholder="目标用户群（必填）" class="mb-3" />
          <el-input v-model="form.coreFeatures" type="textarea" :rows="2" placeholder="主打功能点（必填）" class="mb-3" />
          <el-input v-model="form.useCase" type="textarea" :rows="2" placeholder="用途（必填）" class="mb-3" />
          <el-input v-model="form.problemStatement" type="textarea" :rows="2" placeholder="针对的问题（必填）" class="mb-3" />
          <el-input v-model="form.techStackDesc" type="textarea" :rows="2" placeholder="可能涉及的技术栈和深度（必填）" class="mb-3" />
          <div class="form-grid mb-3">
            <el-select v-model="form.projectType" placeholder="产品方向" class="flex-1" size="large">
              <el-option v-for="item in projectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <div class="flex-1" />
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmCreate">✅ 确认发起</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({ embedded: { type: Boolean, default: false } })
const emit = defineEmits(['submitted', 'cancel'])
const router = useRouter()
const submitting = ref(false)

const form = ref({
  projectName: '',
  expectedBudget: null,
  projectType: '',
  targetUsers: '',
  coreFeatures: '',
  useCase: '',
  problemStatement: '',
  techStackDesc: ''
})

const projectTypeOptions = [
  { label: '工业项目 (Industrial)', value: 'INDUSTRIAL' },
  { label: '军工项目', value: 'MILITARY' },
  { label: '医药项目', value: 'MEDICAL' },
  { label: 'AI for Science', value: 'AI_FOR_SCIENCE' },
  { label: '群体智能项目 (Swarm)', value: 'SWARM_INTEL' },
  { label: '自用项目', value: 'SELF_USE' }
]

const getValidationMessage = () => {
  if (!String(form.value.projectName || '').trim()) return '请填写产品名称'
  if (form.value.expectedBudget === null || form.value.expectedBudget === '') return '请填写预计项目投入'
  if (!String(form.value.projectType || '').trim()) return '请选择产品方向'
  if (!String(form.value.targetUsers || '').trim()) return '请填写目标用户群'
  if (!String(form.value.coreFeatures || '').trim()) return '请填写主打功能点'
  if (!String(form.value.useCase || '').trim()) return '请填写用途'
  if (!String(form.value.problemStatement || '').trim()) return '请填写针对的问题'
  if (!String(form.value.techStackDesc || '').trim()) return '请填写可能涉及的技术栈和深度'
  return ''
}

const confirmCreate = async () => {
  const validationMessage = getValidationMessage()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  if (submitting.value) return

  submitting.value = true
  try {
    const payload = {
      name: form.value.projectName.trim(),
      expectedBudget: Number(form.value.expectedBudget),
      projectType: form.value.projectType,
      targetUsers: form.value.targetUsers.trim(),
      coreFeatures: form.value.coreFeatures.trim(),
      useCase: form.value.useCase.trim(),
      problemStatement: form.value.problemStatement.trim(),
      techStackDesc: form.value.techStackDesc.trim(),
      description: `${form.value.useCase.trim()}｜${form.value.problemStatement.trim()}`
    }

    await request.post('/api/products/idea', payload)
    ElMessage.success('🚀 产品发起成功！')
    if (props.embedded) {
      emit('submitted')
    } else {
      router.push('/manager/dashboard')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.response?.data || e.message || '产品发起失败')
  } finally {
    submitting.value = false
  }
}

defineExpose({ confirmCreate })
</script>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(8px); display: flex; justify-content: center; align-items: center; z-index: 1000; }
.modal-card { background: var(--science-surface); width: 90vw; max-width: 700px; height: auto; border-radius: 16px; display: flex; flex-direction: column; box-shadow: var(--shadow-md); overflow: hidden; border: 1px solid var(--border-soft); }
.modal-header { padding: 20px 30px; border-bottom: 1px solid var(--border-soft); display: flex; justify-content: space-between; align-items: center; background: var(--science-surface); color: var(--text-main); }
.close-btn { font-size: 28px; cursor: pointer; color: #999; transition: color 0.2s; }
.close-btn:hover { color: var(--text-main); }
.modal-body { padding: 30px; overflow: hidden; flex: 1; display: flex; flex-direction: column; }
.modal-footer { padding: 20px 30px; background: var(--science-surface-muted); border-top: 1px solid var(--border-soft); display: flex; justify-content: flex-end; gap: 10px; }
.form-section { margin-bottom: 20px; flex-shrink: 0; }
.mb-3 { margin-bottom: 15px; }
.form-grid { display: flex; gap: 10px; }
.flex-1 { flex: 1; }
.option-row { display: flex; align-items: center; }
.avatar-small { width: 24px; height: 24px; border-radius: 50%; margin-right: 8px; }
.embedded-form { padding: 0; }
</style>

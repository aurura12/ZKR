<template>
  <div v-if="embedded" class="embedded-body">
    <el-input v-model="form.idea" type="textarea" :rows="3" placeholder="科研 idea（必填）" class="mb-3" />
    <el-input v-model="form.innovationPoint" type="textarea" :rows="3" placeholder="创新点 innovation（必填）" class="mb-3" />
    <el-input v-model="form.budget" type="number" placeholder="预算 budget（必填）" class="mb-3" />
    <el-select v-model="form.hostUserId" filterable clearable placeholder="主持人 Host（可选，默认发起人）" style="width: 100%" class="mb-3">
      <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
    </el-select>
    <el-select v-model="form.chiefEngineerUserId" filterable clearable placeholder="总工程师 Chief Engineer（可选）" style="width: 100%" class="mb-3">
      <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
    </el-select>
    <el-select v-model="form.coreMemberIds" filterable multiple placeholder="核心成员（必选至少2人）" style="width: 100%">
      <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
    </el-select>
    <div class="form-tip">提示：核心成员至少选择2人（不含发起人）</div>
  </div>
  <div v-else class="create-shell">
    <div class="card">
      <div class="header">
        <h2>🧪 发起科研（Research Flow）</h2>
        <button class="close-btn" @click="$router.back()">×</button>
      </div>
      <div class="body">
        <el-input v-model="form.idea" type="textarea" :rows="3" placeholder="科研 idea（必填）" class="mb-3" />
        <el-input v-model="form.innovationPoint" type="textarea" :rows="3" placeholder="创新点 innovation（必填）" class="mb-3" />
        <el-input v-model="form.budget" type="number" placeholder="预算 budget（必填）" class="mb-3" />
        <el-select v-model="form.hostUserId" filterable clearable placeholder="主持人 Host（可选，默认发起人）" style="width: 100%" class="mb-3">
          <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
        </el-select>
        <el-select v-model="form.chiefEngineerUserId" filterable clearable placeholder="总工程师 Chief Engineer（可选）" style="width: 100%" class="mb-3">
          <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
        </el-select>
        <el-select v-model="form.coreMemberIds" filterable multiple placeholder="核心成员（必选至少2人）" style="width: 100%">
          <el-option v-for="u in users" :key="u.optionKey" :label="u.label" :value="u.value" />
        </el-select>
        <div class="form-tip">提示：核心成员至少选择2人（不含发起人）</div>
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getResearchWorkflowMemberRoles, getResearchWorkflowRoleCandidates } from '@/api/workflowMemberRoles'

const props = defineProps({ embedded: { type: Boolean, default: false } })
const emit = defineEmits(['submitted', 'cancel'])
const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const users = ref([])

const form = ref({
  idea: '',
  innovationPoint: '',
  budget: '',
  hostUserId: '',
  chiefEngineerUserId: '',
  coreMemberIds: []
})

const canSubmit = computed(() => Boolean(
  String(form.value.idea || '').trim() &&
  String(form.value.innovationPoint || '').trim() &&
  String(form.value.budget || '').trim() &&
  (form.value.coreMemberIds || []).length >= 2
))

const rowKey = row => `${String(row?.userId || '').trim()}-${String(row?.role || '').trim()}`

const mapRows = rows => (rows || [])
  .filter(row => String(row?.userId || '').trim())
  .map(row => {
    const name = row.name || row.username || row.userId
    const role = row.role || 'N/A'
    return {
      ...row,
      id: String(row.userId),
      optionKey: rowKey(row),
      value: rowKey(row),
      label: `${name} / ${role}`
    }
  })

const unwrapUserId = value => String(value || '').split('-')[0] || ''

onMounted(async () => {
  try {
    const workflowId = String(route.query.workflowId || route.query.projectId || '').trim()
    if (workflowId) {
      const res = await getResearchWorkflowMemberRoles(workflowId)
      const list = res.data || res || []
      users.value = mapRows(list)
      return
    }

    const roleCandidatesRes = await getResearchWorkflowRoleCandidates()
    const roleCandidates = roleCandidatesRes.data || roleCandidatesRes || []
    if (roleCandidates.length > 0) {
      users.value = mapRows(roleCandidates)
      return
    }

    const res = await request.get('/api/users')
    const list = res.data || res || []
    users.value = mapRows(list)
  } catch {
    users.value = []
  }
})

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const res = await request.post('/api/research/initiate', {
      idea: form.value.idea.trim(),
      innovationPoint: form.value.innovationPoint.trim(),
      budget: Number(form.value.budget),
      hostUserId: unwrapUserId(form.value.hostUserId) || null,
      chiefEngineerUserId: unwrapUserId(form.value.chiefEngineerUserId) || null,
      coreMemberIds: (form.value.coreMemberIds || []).map(unwrapUserId).filter(Boolean)
    })
    const project = res.data || res || {}
    ElMessage.success('科研创新发起成功')
    if (props.embedded) {
      emit('submitted')
    } else if (project.projectId) {
      router.push(`/workspace/project/${project.projectId}`)
    } else {
      router.push('/manager/dashboard')
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.response?.data || e?.message || '发起失败'
    ElMessage.error({ message: msg, duration: 5000 })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-shell { min-height: calc(100vh - var(--nav-height)); display: grid; place-items: center; background: var(--science-canvas); padding: 24px; }
.card { width: 100%; max-width: 760px; border: 1px solid var(--border-soft); border-radius: 16px; background: var(--science-surface); box-shadow: var(--shadow-md); overflow: hidden; }
.header { padding: 18px 22px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-soft); }
.header h2 { margin: 0; color: var(--text-main); font-size: 20px; }
.close-btn { border: none; background: transparent; color: var(--text-sub); font-size: 24px; cursor: pointer; }
.body { padding: 20px 22px; }
.footer { padding: 16px 22px; border-top: 1px solid var(--border-soft); background: var(--science-surface-muted); display: flex; justify-content: flex-end; gap: 10px; }
.mb-3 { margin-bottom: 12px; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>

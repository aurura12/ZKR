<template>
  <div class="provision-page">
    <div class="provision-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">ADMIN ONLY</div>
          <h1>创建账号</h1>
          <p class="subtitle">系统将直接创建账号，初始密码规则为：账号 + 123。</p>
        </div>
        <el-tag type="primary">仅授权账号可见</el-tag>
      </div>

      <div class="nl-section">
        <label>自然语言创建（可选）</label>
        <el-input
          v-model="naturalText"
          type="textarea"
          :rows="4"
          placeholder="例如：给中国科学院大学计算机学院的学生张三创建一个研发实习生账号，手机号 13800138000，身份证号 110101200001011234，住址在雁栖湖校区，日工资 300 元"
        />
        <el-button type="info" :loading="parsing" @click="handleNaturalLanguageParse" style="margin-top: 10px;">
          🤖 智能识别并填充
        </el-button>
      </div>

      <div class="form-grid">
        <div class="field-block full-width">
          <label>账号域</label>
          <el-radio-group v-model="form.domain">
            <el-radio-button label="ERP">ERP</el-radio-button>
            <el-radio-button label="FINANCE">FINANCE</el-radio-button>
          </el-radio-group>
        </div>

        <div class="field-block">
          <label>账号</label>
          <el-input v-model="form.username" placeholder="请输入登录账号" @blur="handleUsernameBlur" />
        </div>

        <div class="field-block">
          <label>姓名</label>
          <el-input v-model="form.name" placeholder="请输入用户姓名" />
        </div>

        <div class="field-block">
          <label>角色</label>
          <el-select v-model="form.role" placeholder="请选择角色">
            <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
        </div>

        <div class="field-block">
          <label>岗位</label>
          <el-input v-model="form.position" placeholder="例如：实习生、工程师" />
        </div>

        <div class="field-block">
          <label>民族</label>
          <el-input v-model="form.ethnicity" placeholder="例如：汉族" />
        </div>

        <div class="field-block">
          <label>手机号</label>
          <el-input v-model="form.phone" placeholder="联系电话" />
        </div>

        <div class="field-block full-width">
          <label>身份证号</label>
          <el-input v-model="form.idNumber" placeholder="18位身份证号码" maxlength="18" />
        </div>

        <div class="field-block full-width">
          <label>学校院系</label>
          <el-input v-model="form.schoolDepartment" placeholder="例如：中国科学院大学 计算机软件与理论" />
        </div>

        <div class="field-block full-width">
          <label>住址</label>
          <el-input v-model="form.address" placeholder="例如：中国科学院大学雁栖湖校区" />
        </div>

        <div class="field-block">
          <label>日工资 (元/天)</label>
          <el-input-number v-model="form.dailyWage" :min="0" :precision="2" :step="10" placeholder="默认 300.00" />
        </div>

        <div class="field-block">
          <label>是否兼职</label>
          <el-switch v-model="form.partTime" />
        </div>

        <div class="field-block">
          <label>支付主体</label>
          <el-input v-model="form.paymentEntity" disabled placeholder="国科九天" />
        </div>

        <div class="field-block">
          <label>开户行</label>
          <el-input v-model="form.bankName" placeholder="例如：中国工商银行xxx支行" />
        </div>

        <div class="field-block">
          <label>银行卡号</label>
          <el-input v-model="form.bankAccount" placeholder="银行卡号" />
        </div>

      </div>

      <div class="footer-row">
        <el-button @click="router.push('/profile')">返回个人中心</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleUploadIdCard"
          accept="image/*,.pdf"
          style="display:inline-block;margin:0 8px"
        >
          <el-button type="warning">🪪 上传证件</el-button>
        </el-upload>
        <el-upload
          :show-file-list="false"
          :before-upload="handleUploadStudentCard"
          accept="image/*,.pdf"
          style="display:inline-block"
        >
          <el-button type="info">🎓 上传学生证</el-button>
        </el-upload>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">创建账号</el-button>
      </div>
    </div>

    <el-dialog v-model="showAgreementDialog" title="生成协议" width="420px" :close-on-click-modal="false">
      <p style="margin: 0 0 12px; color: var(--text-sub);">账号创建成功，请选择需要生成的协议文件：</p>
      <el-checkbox-group v-model="selectedAgreements" style="display: grid; gap: 10px;">
        <el-checkbox v-for="opt in agreementOptions" :key="opt.value" :label="opt.value">
          {{ opt.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="closeAgreementDialog">跳过</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerateAgreements">生成并下载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()

const roleOptions = ['RESEARCH', 'BUSINESS', 'PROMOTION', 'DATA', 'DEV', 'ALGORITHM', 'CI']
const agreementOptions = [
  { label: '互联网实习生协议', value: 'INTERNET' },
  { label: '实习生协议', value: 'GENERAL' },
  { label: '实习证明', value: 'PROOF' }
]

const form = reactive({
  username: '',
  name: '',
  role: '',
  domain: 'ERP',
  position: '',
  ethnicity: '',
  phone: '',
  idNumber: '',
  schoolDepartment: '',
  address: '',
  dailyWage: 300.00,
  partTime: false,
  paymentEntity: '国科九天',
  bankName: '',
  bankAccount: ''
})

const submitting = ref(false)
const generating = ref(false)
const parsing = ref(false)
const naturalText = ref('')
const showAgreementDialog = ref(false)
const selectedAgreements = ref([])
const createdUserId = ref('')
const createdUserName = ref('')

const handleUsernameBlur = async () => {
  const q = form.username?.trim()
  if (!q || q.length < 2) return
  try {
    const res = await request.get('/api/admin/users/search', { params: { q } })
    if (res && res.length > 0) {
      const u = res[0]
      if (!form.name) form.name = u.name
      if (!form.role) {
        form.role = u.role
      }
      if (!form.dailyWage || form.dailyWage === 300) form.dailyWage = Number(u.dailyWage || 300)
    }
  } catch (e) {
    // Silently ignore search errors
  }
}

const handleUploadIdCard = async (file) => {
  ElMessage.info('请先创建账号，然后在劳动关系资料模块中上传证件')
  return false
}

const handleUploadStudentCard = async (file) => {
  ElMessage.info('请先创建账号，然后在劳动关系资料模块中上传证件')
  return false
}

const resetForm = () => {
  form.username = ''
  form.name = ''
  form.role = ''
  form.domain = 'ERP'
  form.position = ''
  form.ethnicity = ''
  form.phone = ''
  form.idNumber = ''
  form.schoolDepartment = ''
  form.address = ''
  form.dailyWage = 300.00
  form.partTime = false
  form.paymentEntity = '国科九天'
  form.bankName = ''
  form.bankAccount = ''
}

const closeAgreementDialog = () => {
  showAgreementDialog.value = false
  selectedAgreements.value = []
  resetForm()
}

const handleNaturalLanguageParse = async () => {
  const text = naturalText.value?.trim()
  if (!text) {
    ElMessage.warning('请输入自然语言描述')
    return
  }
  parsing.value = true
  try {
    const parsed = await request.post('/api/admin/users/parse-natural-language', { text })
    if (parsed.username) form.username = parsed.username
    if (parsed.name) form.name = parsed.name
    if (parsed.role) form.role = parsed.role
    if (parsed.domain) form.domain = parsed.domain
    if (parsed.position) form.position = parsed.position
    if (parsed.ethnicity) form.ethnicity = parsed.ethnicity
    if (parsed.phone) form.phone = parsed.phone
    if (parsed.idNumber) form.idNumber = parsed.idNumber
    if (parsed.schoolDepartment) form.schoolDepartment = parsed.schoolDepartment
    if (parsed.address) form.address = parsed.address
    if (parsed.dailyWage !== undefined && parsed.dailyWage !== null) form.dailyWage = Number(parsed.dailyWage)
    if (parsed.partTime !== undefined && parsed.partTime !== null) form.partTime = Boolean(parsed.partTime)
    if (parsed.paymentEntity) form.paymentEntity = parsed.paymentEntity
    if (parsed.bankName) form.bankName = parsed.bankName
    if (parsed.bankAccount) form.bankAccount = parsed.bankAccount
    ElMessage.success('已识别并填充，请检查核对后提交')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '智能识别失败')
  } finally {
    parsing.value = false
  }
}

const handleSubmit = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('请填写账号')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  if (!form.role) {
    ElMessage.warning('请选择角色')
    return
  }

  submitting.value = true
  try {
    const response = await request.post('/api/admin/users/provision', {
      username: form.username.trim(),
      name: form.name.trim(),
      role: form.role,
      domain: form.domain,
      position: form.position,
      ethnicity: form.ethnicity,
      phone: form.phone,
      idNumber: form.idNumber,
      schoolDepartment: form.schoolDepartment,
      address: form.address,
      dailyWage: form.dailyWage,
      partTime: form.partTime,
      paymentEntity: form.paymentEntity,
      bankName: form.bankName,
      bankAccount: form.bankAccount
    })
    createdUserId.value = response?.userId || ''
    createdUserName.value = form.name.trim()
    ElMessage.success(response?.message || `账号创建成功，初始密码为：${form.username.trim()}123`)
    showAgreementDialog.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '账号创建失败')
  } finally {
    submitting.value = false
  }
}

const handleGenerateAgreements = async () => {
  if (selectedAgreements.value.length === 0) {
    ElMessage.warning('请至少选择一份协议')
    return
  }
  if (!createdUserId.value) {
    ElMessage.warning('未获取到用户ID，请重新创建账号')
    return
  }

  generating.value = true
    try {
      const blobData = await request.post(
        `/api/admin/users/${createdUserId.value}/agreements/batch`,
        { types: selectedAgreements.value },
        { responseType: 'blob' }
      )
      const blob = new Blob([blobData], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${createdUserName.value}_实习文件.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('协议生成成功')
    closeAgreementDialog()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '协议生成失败')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.provision-page {
  min-height: calc(100vh - var(--nav-height));
  padding: 32px 20px;
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.06), transparent 240px), var(--science-canvas);
}

.provision-card {
  width: min(100%, 900px);
  margin: 0 auto;
  padding: 32px;
  border-radius: 24px;
  border: 1px solid var(--border-soft);
  background: var(--science-surface);
  box-shadow: var(--shadow-md);
}

.header-row,
.footer-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--science-blue);
  font-weight: 700;
}

h1 {
  margin: 8px 0 6px;
  color: var(--text-main);
}

.subtitle {
  margin: 0;
  color: var(--text-sub);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 28px 0;
}

.field-block {
  display: grid;
  gap: 10px;
}

.field-block label {
  color: var(--text-main);
  font-weight: 600;
}

.full-width {
  grid-column: 1 / -1;
}

.notice-box {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--science-surface-muted);
  color: var(--text-sub);
  display: grid;
  gap: 6px;
  margin-bottom: 24px;
}

.nl-section {
  display: grid;
  gap: 10px;
  margin-bottom: 24px;
  padding: 18px;
  border-radius: 16px;
  background: var(--science-surface-muted);
  border: 1px dashed var(--border-soft);
}

.nl-section label {
  color: var(--text-main);
  font-weight: 600;
}

@media (max-width: 720px) {
  .header-row,
  .footer-row,
  .form-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

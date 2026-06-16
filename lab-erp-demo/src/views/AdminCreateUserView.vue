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
        <el-button type="success" @click="handleGenerateAgreement">📄 生成协议</el-button>
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
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()

const roleOptions = ['RESEARCH', 'BUSINESS', 'PROMOTION', 'DATA', 'DEV', 'ALGORITHM']

const form = reactive({
  username: '',
  name: '',
  role: '',
  domain: 'ERP',
  position: '',
  ethnicity: '',
  phone: '',
  idNumber: '',
  dailyWage: 300.00,
  partTime: false,
  paymentEntity: '国科九天',
  bankName: '',
  bankAccount: ''
})

const submitting = ref(false)

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

const handleGenerateAgreement = async () => {
  ElMessage.info('请先创建账号，然后在劳动关系资料模块中生成协议')
}

const handleUploadIdCard = async (file) => {
  ElMessage.info('请先创建账号，然后在劳动关系资料模块中上传证件')
  return false
}

const handleUploadStudentCard = async (file) => {
  ElMessage.info('请先创建账号，然后在劳动关系资料模块中上传证件')
  return false
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
      dailyWage: form.dailyWage,
      partTime: form.partTime,
      paymentEntity: form.paymentEntity,
      bankName: form.bankName,
      bankAccount: form.bankAccount
    })
    ElMessage.success(response?.message || `账号创建成功，初始密码为：${form.username.trim()}123`)
    form.username = ''
    form.name = ''
    form.role = ''
    form.domain = 'ERP'
    form.position = ''
    form.ethnicity = ''
    form.phone = ''
    form.idNumber = ''
    form.dailyWage = 300.00
    form.partTime = false
    form.paymentEntity = '国科九天'
    form.bankName = ''
    form.bankAccount = ''
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '账号创建失败')
  } finally {
    submitting.value = false
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

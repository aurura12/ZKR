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
          <el-input v-model="form.username" placeholder="请输入登录账号" />
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
          <label>日工资 (元/天)</label>
          <el-input-number v-model="form.dailyWage" :min="0" :precision="2" :step="10" placeholder="默认 300.00" />
        </div>

      </div>

      <div class="footer-row">
        <el-button @click="router.push('/profile')">返回个人中心</el-button>
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
  dailyWage: 300.00
})

const submitting = ref(false)

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
      dailyWage: form.dailyWage
    })
    ElMessage.success(response?.message || `账号创建成功，初始密码为：${form.username.trim()}123`)
    form.username = ''
    form.name = ''
    form.role = ''
    form.domain = 'ERP'
    form.dailyWage = 300.00
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

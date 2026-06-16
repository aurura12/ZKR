<template>
  <div class="labor-page">
    <div class="labor-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">FINANCE · HR</div>
          <h1>劳动关系资料</h1>
          <p class="subtitle">统一管理员工的入职协议及身份证、学生证扫描件</p>
        </div>
      </div>

      <el-table :data="users" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column label="部门" width="100">
          <template #default="{ row }">{{ roleToDepartment(row.role) }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" width="100" />
        <el-table-column label="协议文件" width="160">
          <template #default="{ row }">
            <div class="doc-cell">
              <el-button v-if="row.hasAgreement" size="small" type="primary" link @click="downloadDoc(row.userId, 'agreement')">下载</el-button>
              <el-upload :show-file-list="false" :before-upload="(f) => uploadDoc(row.userId, f, 'agreement')" accept=".txt,.pdf,.doc,.docx" style="display:inline-block">
                <el-button size="small" type="success">上传</el-button>
              </el-upload>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="身份证" width="160">
          <template #default="{ row }">
            <div class="doc-cell">
              <el-button v-if="row.hasIdCard" size="small" type="primary" link @click="downloadDoc(row.userId, 'id_card')">下载</el-button>
              <el-upload :show-file-list="false" :before-upload="(f) => uploadDoc(row.userId, f, 'id_card')" accept="image/*,.pdf" style="display:inline-block">
                <el-button size="small" type="warning">上传</el-button>
              </el-upload>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学生证" width="160">
          <template #default="{ row }">
            <div class="doc-cell">
              <el-button v-if="row.hasStudentCard" size="small" type="primary" link @click="downloadDoc(row.userId, 'student_card')">下载</el-button>
              <el-upload :show-file-list="false" :before-upload="(f) => uploadDoc(row.userId, f, 'student_card')" accept="image/*,.pdf" style="display:inline-block">
                <el-button size="small" type="info">上传</el-button>
              </el-upload>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const USERS_BASE = '/api/admin/users'
const DOCS_BASE = '/api/admin/users/users'

const users = ref([])
const loading = ref(false)

const roleToDepartment = (role) => {
  const map = { RESEARCH:'研究中心', DEV:'开发中心', BUSINESS:'商务中心', BD:'商务中心', ALGORITHM:'算法中心', DATA:'数据中心', DATA_ENGINEER:'数据中心', ADMIN:'管理中心', PROMOTION:'运营中心', QA:'测试中心' }
  return map[role] || '研究中心'
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const userList = await request.get(USERS_BASE)
    const list = Array.isArray(userList) ? userList : []

    for (const u of list) {
      try {
        const docs = await request.get(`${DOCS_BASE}/${u.userId}/documents`)
        const docList = Array.isArray(docs) ? docs : []
        u.hasAgreement = docList.some(d => d.type === '协议' || d.filename?.startsWith('agreement'))
        u.hasIdCard = docList.some(d => d.type === '身份证' || d.filename?.startsWith('id_card'))
        u.hasStudentCard = docList.some(d => d.type === '学生证' || d.filename?.startsWith('student_card'))
      } catch {
        u.hasAgreement = false
        u.hasIdCard = false
        u.hasStudentCard = false
      }
    }
    users.value = list
  } catch {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const uploadDoc = async (userId, file, docType) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('docType', docType)
  try {
    await request.post(`${DOCS_BASE}/${userId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('上传成功')
    fetchUsers()
  } catch {
    ElMessage.error('上传失败')
  }
  return false
}

const downloadDoc = async (userId, docType) => {
  try {
    const docs = await request.get(`${DOCS_BASE}/${userId}/documents`)
    const docList = Array.isArray(docs) ? docs : []
    const match = docList.find(d =>
      d.type === (docType === 'agreement' ? '协议' : docType === 'id_card' ? '身份证' : '学生证') ||
      d.filename?.startsWith(docType)
    )
    if (!match) { ElMessage.warning('文件不存在'); return }

    const axios = (await import('axios')).default
    const blobRes = await axios.get(`${DOCS_BASE}/${userId}/documents/${match.filename}`, {
      responseType: 'blob'
    })
    const blob = new Blob([blobRes.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = match.filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.labor-page { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
.labor-card { background: var(--bg-surface); border-radius: 16px; padding: 32px; box-shadow: var(--shadow-soft); }
.header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.eyebrow { font-size: 0.75rem; letter-spacing: 0.15em; color: var(--text-secondary); margin-bottom: 4px; }
.header-row h1 { margin: 0; font-size: 1.5rem; }
.subtitle { color: var(--text-secondary); margin: 4px 0 0; font-size: 0.875rem; }
.doc-cell { display: flex; gap: 4px; align-items: center; }
</style>

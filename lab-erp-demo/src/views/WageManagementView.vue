<template>
  <div class="wage-page">
    <div class="wage-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">ADMIN ONLY</div>
          <h1>工资管理</h1>
          <p class="subtitle">管理所有用户的日工资标准</p>
        </div>
        <el-tag type="primary">仅授权账号可见</el-tag>
      </div>

      <div class="table-wrapper">
        <el-table :data="sortedUsers" stripe v-loading="loading" style="width: 100%" @sort-change="handleSortChange" row-key="userId">
          <el-table-column prop="userId" label="ID" width="100" sortable="custom" />
          <el-table-column prop="name" label="姓名" width="120" sortable="custom" />
          <el-table-column prop="username" label="账号" width="140" sortable="custom" />
          <el-table-column prop="role" label="角色" width="120" sortable="custom" />
          <el-table-column prop="accountDomain" label="域" width="100" sortable="custom" />
          <el-table-column prop="active" label="状态" width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'danger'" size="small">
                {{ row.active ? '在职' : '离职' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dailyWage" label="日工资 (元/天)" min-width="180" sortable="custom">
            <template #default="{ row }">
              <el-input-number
                v-model="row.dailyWage"
                :min="0"
                :precision="2"
                :step="10"
                size="small"
                :disabled="savingIds.has(row.userId)"
                @change="handleWageChange(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <template v-if="row.active">
                <el-popconfirm
                  title="确认将该用户设为离职？"
                  confirm-button-text="确认"
                  cancel-button-text="取消"
                  @confirm="handleDeactivate(row)"
                >
                  <template #reference>
                    <el-button type="danger" size="small" :loading="savingIds.has(row.userId)">离职</el-button>
                  </template>
                </el-popconfirm>
              </template>
              <template v-else>
                <el-popconfirm
                  title="确认将该用户还原为在职？"
                  confirm-button-text="确认"
                  cancel-button-text="取消"
                  @confirm="handleActivate(row)"
                >
                  <template #reference>
                    <el-button type="success" size="small" :loading="savingIds.has(row.userId)">还原</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const users = ref([])
const loading = ref(false)
const savingIds = ref(new Set())

const sortProp = ref(null)
const sortOrder = ref(null)

const compareValues = (a, b) => {
  if (a == null && b == null) return 0
  if (a == null) return 1
  if (b == null) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  if (typeof a === 'boolean' && typeof b === 'boolean') return (a ? 1 : 0) - (b ? 1 : 0)
  return String(a).localeCompare(String(b), 'zh-CN')
}

const sortedUsers = computed(() => {
  if (!sortProp.value || !sortOrder.value) return users.value
  const prop = sortProp.value
  const order = sortOrder.value
  return [...users.value].sort((a, b) => {
    const result = compareValues(a[prop], b[prop])
    return order === 'ascending' ? result : -result
  })
})

const handleSortChange = ({ prop, order }) => {
  sortProp.value = prop
  sortOrder.value = order
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/users')
    users.value = Array.isArray(res) ? res.map(u => ({
      ...u,
      dailyWage: u.dailyWage != null ? Number(u.dailyWage) : 300
    })) : []
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleWageChange = async (row) => {
  if (row.dailyWage == null || row.dailyWage < 0) {
    ElMessage.warning('日工资不能为负数')
    return
  }

  const prevWage = row.dailyWage
  savingIds.value.add(row.userId)
  try {
    await request.put(`/api/admin/users/${row.userId}/daily-wage`, {
      dailyWage: row.dailyWage
    })
    ElMessage.success(`${row.name} 日工资已更新为 ${row.dailyWage}`)
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
    const user = users.value.find(u => u.userId === row.userId)
    if (user) user.dailyWage = prevWage
  } finally {
    savingIds.value.delete(row.userId)
  }
}

const handleDeactivate = async (row) => {
  savingIds.value.add(row.userId)
  try {
    await request.post(`/api/admin/users/${row.userId}/deactivate`)
    ElMessage.success(`${row.name} 已设为离职`)
    fetchUsers()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    savingIds.value.delete(row.userId)
  }
}

const handleActivate = async (row) => {
  savingIds.value.add(row.userId)
  try {
    await request.post(`/api/admin/users/${row.userId}/activate`)
    ElMessage.success(`${row.name} 已还原为在职`)
    fetchUsers()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    savingIds.value.delete(row.userId)
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.wage-page {
  min-height: calc(100vh - var(--nav-height));
  padding: 32px 20px;
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.06), transparent 240px), var(--science-canvas);
}

.wage-card {
  width: min(100%, 1000px);
  margin: 0 auto;
  padding: 32px;
  border-radius: 24px;
  border: 1px solid var(--border-soft);
  background: var(--science-surface);
  box-shadow: var(--shadow-md);
}

.header-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
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

.table-wrapper {
  margin-top: 8px;
}
</style>

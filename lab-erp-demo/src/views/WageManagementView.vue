<template>
  <div class="wage-page">
    <div class="wage-card">
      <div class="header-row">
        <div>
          <div class="eyebrow">ADMIN ONLY</div>
          <h1>员工管理</h1>
          <p class="subtitle">管理员工信息、日工资及花名册导出</p>
        </div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-tag type="primary">仅授权账号可见</el-tag>
          <el-button type="success" :loading="exporting" @click="handleExport">📥 导出花名册</el-button>
        </div>
      </div>

      <div class="table-wrapper">
        <el-table :data="sortedUsers" stripe v-loading="loading" style="width: 100%" @sort-change="handleSortChange" row-key="userId">
          <el-table-column prop="userId" label="ID" width="90" sortable="custom" />
          <el-table-column prop="name" label="姓名" width="110" sortable="custom" />
          <el-table-column prop="username" label="账号" width="130" sortable="custom" />
          <el-table-column prop="role" label="角色" width="110" sortable="custom" />
          <el-table-column prop="accountDomain" label="域" width="100" sortable="custom" />
          <el-table-column prop="active" label="状态" width="90" sortable="custom" header-align="center">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'danger'" size="small">
                {{ row.active ? '在职' : '离职' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dailyWage" label="日工资 (元/天)" min-width="160" sortable="custom">
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
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="showDetail(row)">详情</el-button>
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

    <!-- 员工详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'员工详情 - ' + (detailData?.name || detailData?.username || '')" width="900px" top="5vh" destroy-on-close>
      <template v-if="loadingDetail">
        <div style="text-align:center;padding:60px"><el-icon class="is-loading" :size="32"><Loading /></el-icon><p>加载中...</p></div>
      </template>
      <template v-else-if="detailData">
        <el-tabs v-model="activeTab" type="border-card">
          <!-- Tab 1: 个人信息 -->
          <el-tab-pane label="个人信息" name="info">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="姓名">{{ detailData.name || '-' }}</el-descriptions-item>
              <el-descriptions-item label="账号">{{ detailData.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="用户ID">{{ detailData.userId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="角色">{{ detailData.role || '-' }}</el-descriptions-item>
              <el-descriptions-item label="登录域">{{ detailData.accountDomain || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="detailData.active ? 'success' : 'danger'" size="small">{{ detailData.active ? '在职' : '离职' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ detailData.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ detailData.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="身份证号">{{ detailData.idNumber || '-' }}</el-descriptions-item>
              <el-descriptions-item label="民族">{{ detailData.ethnicity || '-' }}</el-descriptions-item>
              <el-descriptions-item label="岗位">{{ detailData.position || '-' }}</el-descriptions-item>
              <el-descriptions-item label="学校院系">{{ detailData.schoolDepartment || '-' }}</el-descriptions-item>
              <el-descriptions-item label="住址">{{ detailData.address || '-' }}</el-descriptions-item>
              <el-descriptions-item label="入职日期">{{ detailData.entryDate || '-' }}</el-descriptions-item>
              <el-descriptions-item label="离职日期">{{ detailData.departureDate || '-' }}</el-descriptions-item>
              <el-descriptions-item label="日工资">{{ detailData.dailyWage != null ? '¥' + Number(detailData.dailyWage).toFixed(2) : '-' }}</el-descriptions-item>
              <el-descriptions-item label="支付主体">{{ detailData.paymentEntity || '-' }}</el-descriptions-item>
              <el-descriptions-item label="兼职">
                <el-tag :type="detailData.partTime ? 'warning' : 'info'" size="small">{{ detailData.partTime ? '是' : '否' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="开户行">{{ detailData.bankName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="银行卡号">{{ detailData.bankAccount || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- Tab 2: 合同与文档 -->
          <el-tab-pane label="合同与文档" name="documents">
            <div v-if="!detailData.documents || detailData.documents.length === 0" class="empty-tab">暂无文档</div>
            <el-table v-else :data="detailData.documents" stripe size="small">
              <el-table-column prop="filename" label="文件名" min-width="250" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="size" label="大小" width="100">
                <template #default="{ row }">{{ (row.size / 1024).toFixed(1) }} KB</template>
              </el-table-column>
              <el-table-column prop="modifiedAt" label="上传时间" width="180" />
            </el-table>
          </el-tab-pane>

          <!-- Tab 3: 考勤概况 -->
          <el-tab-pane label="考勤概况" name="attendance">
            <div v-if="loadingAttendance" style="text-align:center;padding:30px"><el-icon class="is-loading" :size="24"><Loading /></el-icon><p>加载中...</p></div>
            <template v-else>
              <!-- 月份切换 -->
              <div class="attendance-header">
                <el-button size="small" @click="changeAttendanceMonth(-1)">‹</el-button>
                <span class="attendance-month">{{ attendanceYear }}年{{ String(attendanceMonthNum).padStart(2, '0') }}月</span>
                <el-button size="small" @click="changeAttendanceMonth(1)">›</el-button>
              </div>
              <!-- 统计 -->
              <div class="attendance-stats">
                <el-descriptions :column="4" border size="small">
                  <el-descriptions-item label="出勤">
                    <span style="font-weight:700;color:#2e7d32">{{ attendanceData?.totalDays || 0 }} 天</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="加班">
                    <span style="font-weight:700;color:#e65100">{{ attendanceData?.overtimeDays || 0 }} 天</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="缺卡">
                    <span style="font-weight:700;color:#c62828">{{ attendanceData?.notSignedDays || 0 }} 天</span>
                  </el-descriptions-item>
                <el-descriptions-item label="合计">
                  <span style="font-weight:700">{{ calendarDays.length }} 天</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
              <!-- 日历 -->
              <div class="calendar">
                <div class="calendar-header">
                  <div v-for="d in weekDays" :key="d" class="calendar-header-cell">{{ d }}</div>
                </div>
                <div v-for="(week, wi) in calendarWeeks" :key="wi" class="calendar-week">
                  <div
                    v-for="(day, di) in week"
                    :key="di"
                    class="calendar-cell"
                    :class="dayClass(day)"
                    :title="dayTitle(day)"
                  >
                    <span class="calendar-day-num">{{ day.date }}</span>
                    <span v-if="day.attendance" class="calendar-day-dot">●</span>
                  </div>
                </div>
              </div>
              <!-- 图例 -->
              <div class="calendar-legend">
                <span><span class="legend-dot" style="background:#e8f5e9;color:#2e7d32">●</span> 正常出勤</span>
                <span><span class="legend-dot" style="background:#fff3e0;color:#e65100">●</span> 加班</span>
                <span><span class="legend-dot" style="background:#ffebee;color:#c62828">●</span> 缺卡</span>
                <span><span class="legend-dot" style="background:#f1f5f9;color:#94a3b8">无数据</span></span>
              </div>
            </template>
          </el-tab-pane>

          <!-- Tab 4: 报销记录 -->
          <el-tab-pane label="报销记录" name="expenses">
            <div v-if="!detailData.expenses || detailData.expenses.length === 0" class="empty-tab">暂无报销记录</div>
            <el-table v-else :data="detailData.expenses" stripe size="small">
              <el-table-column prop="itemName" label="费用名称" min-width="180" />
              <el-table-column prop="expenseType" label="类型" width="120" />
              <el-table-column prop="projectName" label="项目" width="150" />
              <el-table-column prop="amount" label="金额" width="120">
                <template #default="{ row }">¥{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="提交时间" width="160">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 5: 参与项目 -->
          <el-tab-pane label="参与项目" name="projects">
            <div v-if="!detailData.projects || detailData.projects.length === 0" class="empty-tab">暂未参与项目</div>
            <el-table v-else :data="detailData.projects" stripe size="small">
              <el-table-column prop="projectName" label="项目名称" min-width="250" />
              <el-table-column prop="role" label="角色" width="140" />
              <el-table-column prop="joinedAt" label="加入时间" width="180">
                <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 6: 队长身份 -->
          <el-tab-pane label="队长身份" name="leaders">
            <div v-if="!detailData.leaderRoles || detailData.leaderRoles.length === 0" class="empty-tab">无队长身份</div>
            <el-table v-else :data="detailData.leaderRoles" stripe size="small">
              <el-table-column prop="role" label="角色" width="160" />
              <el-table-column label="身份" width="120">
                <template #default>
                  <el-tag type="success">队长</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 7: 分成 -->
          <el-tab-pane label="分成" name="dividends">
            <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
              <el-descriptions-item label="已分成总额">
                <span style="font-weight:700;color:#2e7d32">¥{{ totalDividend }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="待确认分成">
                <span style="font-weight:700;color:#e65100">¥{{ totalPendingDividend }}</span>
              </el-descriptions-item>
            </el-descriptions>
            <h4 style="margin:12px 0 8px">已确认分成</h4>
            <div v-if="!detailData.dividends || detailData.dividends.length === 0" class="empty-tab">暂无已确认分成</div>
            <el-table v-else :data="detailData.dividends" stripe size="small">
              <el-table-column prop="projectName" label="项目" width="150" />
              <el-table-column prop="amount" label="金额" width="120">
                <template #default="{ row }">¥{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
              </el-table-column>
              <el-table-column prop="ledgerMonth" label="账期" width="100" />
              <el-table-column prop="confirmedAt" label="确认时间" width="160">
                <template #default="{ row }">{{ formatDate(row.confirmedAt) }}</template>
              </el-table-column>
            </el-table>
            <h4 style="margin:16px 0 8px">待确认分成</h4>
            <div v-if="!detailData.pendingDividends || detailData.pendingDividends.length === 0" class="empty-tab">暂无待确认分成</div>
            <el-table v-else :data="detailData.pendingDividends" stripe size="small">
              <el-table-column prop="projectName" label="项目" width="150" />
              <el-table-column prop="amount" label="金额" width="120">
                <template #default="{ row }">¥{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
              </el-table-column>
              <el-table-column prop="ledgerMonth" label="账期" width="100" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, shallowRef, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import request from '@/utils/request'
import { Loading } from '@element-plus/icons-vue'

const users = ref([])
const loading = ref(false)
const savingIds = ref(new Set())
const exporting = ref(false)

const sortProp = ref(null)
const sortOrder = ref(null)

// 详情弹窗
const detailVisible = ref(false)
const loadingDetail = ref(false)
const detailData = ref(null)
const activeTab = ref('info')

const compareValues = (a, b) => {
  if (a == null && b == null) return 0
  if (a == null) return 1
  if (b == null) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  if (typeof a === 'boolean' && typeof b === 'boolean') return (a ? 1 : 0) - (b ? 1 : 0)
  return String(a).localeCompare(String(b), 'zh-CN')
}

const sortedUsers = shallowRef([])

const resort = () => {
  if (!sortProp.value || !sortOrder.value) {
    sortedUsers.value = users.value
    return
  }
  const prop = sortProp.value
  const order = sortOrder.value
  sortedUsers.value = [...users.value].sort((a, b) => {
    const result = compareValues(a[prop], b[prop])
    return order === 'ascending' ? result : -result
  })
}

watch(users, resort, { deep: false, immediate: true })

watch([sortProp, sortOrder], () => {
  resort()
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

const handleExport = async () => {
  exporting.value = true
  try {
    const token = localStorage.getItem('erp_token')
    const response = await axios.get('/api/admin/users/users/export', {
      responseType: 'blob',
      headers: { Authorization: `Bearer ${token}` }
    })
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '花名册.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('花名册导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// 详情逻辑
const showDetail = async (row) => {
  detailVisible.value = true
  loadingDetail.value = true
  detailData.value = null
  activeTab.value = 'info'
  try {
    const res = await request.get(`/api/admin/users/${row.userId}/profile`)
    detailData.value = res
  } catch (e) {
    ElMessage.error('加载员工详情失败')
    detailVisible.value = false
  } finally {
    loadingDetail.value = false
  }
}

const totalDividend = computed(() => {
  if (!detailData.value?.dividends) return '0.00'
  return detailData.value.dividends.reduce((s, d) => s + Number(d.amount || 0), 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
})

const totalPendingDividend = computed(() => {
  if (!detailData.value?.pendingDividends) return '0.00'
  return detailData.value.pendingDividends.reduce((s, d) => s + Number(d.amount || 0), 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
})

const formatDate = v => {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 考勤数据
const attendanceData = ref(null)
const attendanceRecords = ref([])
const loadingAttendance = ref(false)
const attendanceYear = ref(new Date().getFullYear())
const attendanceMonthNum = ref(new Date().getMonth() + 1)
const weekDays = ['日', '一', '二', '三', '四', '五', '六']

const getMonthRange = (year, month) => {
  const m = String(month).padStart(2, '0')
  const lastDay = new Date(year, month, 0).getDate()
  return {
    from: `${year}-${m}-01`,
    to: `${year}-${m}-${lastDay}`
  }
}

const calendarDays = computed(() => {
  const days = []
  const year = attendanceYear.value
  const month = attendanceMonthNum.value
  const totalDays = new Date(year, month, 0).getDate()
  for (let d = 1; d <= totalDays; d++) {
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const dow = new Date(year, month - 1, d).getDay()
    const dayRecs = (attendanceRecords.value || []).filter(r => r.workDate === dateStr)
    const onDuty = dayRecs.find(r => r.checkType === 'OnDuty')
    const offDuty = dayRecs.find(r => r.checkType === 'OffDuty')
    let status = 'none'
    if (!onDuty || !offDuty) {
      if (dayRecs.length > 0) status = 'not_signed'
    } else {
      status = 'normal'
      const onTime = onDuty.userCheckTime || onDuty.checkTime
      const offTime = offDuty.userCheckTime || offDuty.checkTime
      if (onTime && offTime) {
        const diff = (new Date(offTime) - new Date(onTime)) / (1000 * 60 * 60)
        if (diff > 11) status = 'overtime'
      }
    }
    days.push({ date: d, dow, dateStr, status })
  }
  return days
})

const calendarWeeks = computed(() => {
  const weeks = []
  let week = []
  const firstDay = new Date(attendanceYear.value, attendanceMonthNum.value - 1, 1).getDay()
  for (let i = 0; i < firstDay; i++) {
    week.push({ date: '', empty: true })
  }
  calendarDays.value.forEach(day => {
    week.push(day)
    if (day.dow === 6) {
      weeks.push(week)
      week = []
    }
  })
  if (week.length > 0) weeks.push(week)
  return weeks
})

const dayClass = day => {
  if (day.empty) return 'calendar-cell empty'
  const base = 'calendar-cell'
  if (day.status === 'normal') return base + ' status-normal'
  if (day.status === 'overtime') return base + ' status-overtime'
  if (day.status === 'not_signed') return base + ' status-not-signed'
  return base + ' status-none'
}

const dayTitle = day => {
  if (day.empty || !day.dateStr) return ''
  const dateLabel = `${attendanceYear.value}年${attendanceMonthNum.value}月${day.date}日`
  if (day.status === 'normal') return dateLabel + ' - 正常出勤'
  if (day.status === 'overtime') return dateLabel + ' - 加班'
  if (day.status === 'not_signed') return dateLabel + ' - 缺卡'
  return dateLabel + ' - 无打卡记录'
}

const changeAttendanceMonth = (delta) => {
  let y = attendanceYear.value
  let m = attendanceMonthNum.value + delta
  if (m < 1) { m = 12; y-- }
  if (m > 12) { m = 1; y++ }
  attendanceYear.value = y
  attendanceMonthNum.value = m
  loadAttendanceData()
}

const loadAttendanceData = async () => {
  if (!detailData.value?.userId) return
  loadingAttendance.value = true
  const range = getMonthRange(attendanceYear.value, attendanceMonthNum.value)
  try {
    const records = await request.get('/api/attendance/records', {
      params: { userId: detailData.value.userId, from: range.from, to: range.to }
    })
    attendanceRecords.value = Array.isArray(records) ? records : []
    const dayMap = {}
    attendanceRecords.value.forEach(r => {
      if (!dayMap[r.workDate]) dayMap[r.workDate] = []
      dayMap[r.workDate].push(r)
    })
    let totalDays = 0, overtimeDays = 0, notSignedDays = 0
    for (const date in dayMap) {
      const dayRecs = dayMap[date]
      const onDuty = dayRecs.find(r => r.checkType === 'OnDuty')
      const offDuty = dayRecs.find(r => r.checkType === 'OffDuty')
      if (!onDuty || !offDuty) {
        notSignedDays++
      } else {
        totalDays++
        const onTime = onDuty.userCheckTime || onDuty.checkTime
        const offTime = offDuty.userCheckTime || offDuty.checkTime
        if (onTime && offTime) {
          const diff = (new Date(offTime) - new Date(onTime)) / (1000 * 60 * 60)
          if (diff > 11) overtimeDays++
        }
      }
    }
    attendanceData.value = { totalDays, overtimeDays, notSignedDays }
  } catch (e) {
    ElMessage.error('加载考勤数据失败')
    attendanceData.value = null
  } finally {
    loadingAttendance.value = false
  }
}

// 监听考勤 Tab 切换
watch(activeTab, async (tab) => {
  if (tab === 'attendance' && detailData.value?.userId && !attendanceData.value) {
    loadAttendanceData()
  }
})

// 关闭弹窗时重置考勤状态
watch(detailVisible, (v) => {
  if (!v) {
    attendanceData.value = null
    attendanceRecords.value = []
    attendanceYear.value = new Date().getFullYear()
    attendanceMonthNum.value = new Date().getMonth() + 1
  }
})
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

.empty-tab {
  color: #94a3b8;
  padding: 40px;
  text-align: center;
  background: #f8fafc;
  border-radius: 8px;
}

/* 考勤日历 */
.attendance-header {
  display: flex; align-items: center; gap: 12px; justify-content: center; margin-bottom: 16px;
}
.attendance-month {
  font-size: 18px; font-weight: 700; min-width: 130px; text-align: center;
}
.calendar { margin-bottom: 12px; max-width: 420px; margin-left: auto; margin-right: auto; }
.attendance-stats { max-width: 420px; margin: 0 auto 16px; }
.calendar-header {
  display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; margin-bottom: 2px;
}
.calendar-header-cell {
  text-align: center; font-size: 11px; font-weight: 600; color: #64748b; padding: 4px 0;
}
.calendar-week { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.calendar-cell {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-radius: 4px; font-size: 12px; cursor: default; position: relative; min-height: 32px; padding: 2px;
}
.calendar-cell.empty { background: transparent; cursor: default; }
.calendar-cell.status-normal { background: #e8f5e9; }
.calendar-cell.status-overtime { background: #fff3e0; }
.calendar-cell.status-not-signed { background: #ffebee; }
.calendar-cell.status-none { background: #f8fafc; color: #94a3b8; }
.calendar-cell .calendar-day-num { font-weight: 600; line-height: 1; }
.calendar-cell .calendar-day-dot { font-size: 10px; line-height: 1; margin-top: 2px; }
.calendar-cell.status-normal .calendar-day-dot { color: #2e7d32; }
.calendar-cell.status-overtime .calendar-day-dot { color: #e65100; }
.calendar-cell.status-not-signed .calendar-day-dot { color: #c62828; }
.calendar-legend {
  display: flex; gap: 16px; justify-content: center; font-size: 12px; color: #64748b;
  padding: 8px; border-top: 1px solid #e2e8f0; margin-top: 8px;
}
.legend-dot { margin-right: 3px; }
</style>

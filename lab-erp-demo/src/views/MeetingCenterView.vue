<template>
  <div class="meeting-center">
    <div class="page-header">
      <h2>会议中心</h2>
      <div class="header-actions">
        <template v-if="myMapping">
          <el-tag type="success" class="mapping-status">
            ✅ 已绑定: {{ myMapping.tencentUsername || myMapping.tencentUserId }}
          </el-tag>
          <el-button type="primary" v-if="myMapping.canCreate" @click="showCreateDialog = true">
            创建会议
          </el-button>
          <el-button link @click="handleUnbind">解绑</el-button>
        </template>
        <template v-else>
          <el-tag type="warning" class="mapping-status">
            ⚠ 未绑定腾讯会议
          </el-tag>
          <el-button @click="showBindDialog = true">立即绑定</el-button>
        </template>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="会议状态" clearable style="width: 150px">
        <el-option label="全部" value="" />
        <el-option label="待开始" value="SCHEDULED" />
        <el-option label="进行中" value="STARTED" />
        <el-option label="已结束" value="ENDED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
    </div>

    <!-- 会议列表 -->
    <el-table :data="meetingList" v-loading="loading" stripe
              highlight-current-row @row-click="showDetail"
              style="cursor: pointer">
      <el-table-column prop="topic" label="会议主题" min-width="200" />
      <el-table-column prop="startTime" label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分钟)" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status, row)">{{ getStatusText(row.status, row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="120" />
      <el-table-column label="参会人" width="150">
        <template #default="{ row }">
          {{ row.participants ? row.participants.length : 0 }} 人
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'SCHEDULED'" type="primary" link @click.stop="joinMeeting(row)">
            加入会议
          </el-button>
          <el-button v-if="row.status === 'STARTED'" type="success" link @click.stop="joinMeeting(row)">
            正在进行
          </el-button>
          <el-button v-if="row.status === 'SCHEDULED'" type="warning" link @click.stop="handleCancel(row)">
            取消
          </el-button>
          <el-button v-if="row.status === 'STARTED'" type="danger" link @click.stop="handleEnd(row)">
            结束
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 绑定腾讯会议弹窗 -->
    <el-dialog v-model="showBindDialog" title="绑定腾讯会议账号" width="480px">
      <el-form :model="bindForm" label-width="140px">
        <el-form-item label="腾讯会议 UserId" required>
          <el-input v-model="bindForm.tencentUserId" placeholder="请输入腾讯会议 userid" />
        </el-form-item>
      </el-form>
      <div style="font-size: 13px; color: #909399; padding: 0 20px 16px;">
        💡 可以在腾讯会议客户端 → 个人资料中找到你的 userid
      </div>
      <template #footer>
        <el-button @click="showBindDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBind" :loading="binding">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 创建会议弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建会议" width="600px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="会议主题" required>
          <el-input v-model="createForm.topic" placeholder="请输入会议主题" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <div style="display: flex; gap: 10px; width: 100%">
            <el-date-picker
              v-model="createForm.startDate"
              type="date"
              placeholder="选择日期"
              style="flex: 1"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledDate"
            />
            <el-time-picker
              v-model="createForm.startTime"
              placeholder="选择时间"
              style="flex: 1"
              format="HH:mm"
              value-format="HH:mm:ss"
            />
          </div>
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="createForm.duration" :min="15" :max="480" :step="15" />
        </el-form-item>
        <el-form-item label="会议密码">
          <el-input v-model="createForm.password" placeholder="可选" />
        </el-form-item>
        <el-form-item label="会议描述">
          <el-input v-model="createForm.description" type="textarea" rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="选择参会人">
          <el-select
            v-model="createForm.participantIds"
            multiple
            filterable
            placeholder="选择参会人"
            style="width: 100%"
          >
            <el-option
              v-for="user in userList"
              :key="user.userId"
              :label="user.name"
              :value="user.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 会议详情弹窗 -->
    <el-dialog v-model="showDetailDialog" title="会议详情" width="600px">
      <template v-if="currentMeeting">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="会议主题">{{ currentMeeting.topic }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(currentMeeting.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ currentMeeting.duration }} 分钟</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentMeeting.status, currentMeeting)">{{ getStatusText(currentMeeting.status, currentMeeting) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentMeeting.creatorName }}</el-descriptions-item>
          <el-descriptions-item label="会议号">{{ currentMeeting.meetingId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="会议密码">{{ currentMeeting.password || '无' }}</el-descriptions-item>
          <el-descriptions-item label="参会人">
            <div v-if="currentMeeting.participants && currentMeeting.participants.length">
              <el-tag v-for="p in currentMeeting.participants" :key="p.userId"
                      style="margin: 2px 4px 2px 0">
                {{ p.userName }}
              </el-tag>
            </div>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentMeeting.description" label="描述">{{ currentMeeting.description }}</el-descriptions-item>
          <el-descriptions-item v-if="currentMeeting.recordingUrl" label="录制回放">
            <a :href="currentMeeting.recordingUrl" target="_blank">查看回放</a>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button type="primary" v-if="currentMeeting?.joinUrl" @click="joinMeeting(currentMeeting)">
          加入会议
        </el-button>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMeetingList, createMeeting, cancelMeeting, endMeeting, getMyMapping, bindMapping, unbindMapping } from '@/api/meeting'
import request from '@/utils/request'

const loading = ref(false)
const creating = ref(false)
const binding = ref(false)
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const showBindDialog = ref(false)
const currentMeeting = ref(null)
const meetingList = ref([])
const userList = ref([])
const filterStatus = ref('')
const myMapping = ref(null)

const createForm = ref({
  topic: '',
  startDate: null,
  startTime: null,
  duration: 60,
  password: '',
  description: '',
  participantIds: []
})

const bindForm = ref({
  tencentUserId: ''
})

const fetchMeetings = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterStatus.value) params.status = filterStatus.value
    const res = await getMeetingList(params)
    meetingList.value = res.data || []
  } catch (e) {
    ElMessage.error('获取会议列表失败')
  } finally {
    loading.value = false
  }
}

const fetchUsers = async () => {
  try {
    const res = await request.get('/api/meetings/tm-users')
    userList.value = res.data || []
  } catch (e) {
    console.error('获取用户列表失败', e)
  }
}

const fetchMyMapping = async () => {
  try {
    const res = await getMyMapping()
    const data = res.data
    myMapping.value = data?.bound ? data : null
  } catch (e) {
    console.error('获取绑定状态失败', e)
  }
}

const submitBind = async () => {
  if (!bindForm.value.tencentUserId) {
    ElMessage.warning('请输入腾讯会议 UserId')
    return
  }
  binding.value = true
  try {
    await bindMapping({
      tencentUserId: bindForm.value.tencentUserId
    })
    ElMessage.success('绑定成功')
    showBindDialog.value = false
    bindForm.value = { tencentUserId: '' }
    fetchMyMapping()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '绑定失败')
  } finally {
    binding.value = false
  }
}

const handleUnbind = async () => {
  try {
    await ElMessageBox.confirm('确定要解绑腾讯会议账号吗？', '确认解绑', { type: 'warning' })
    await unbindMapping()
    ElMessage.success('解绑成功')
    myMapping.value = null
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('解绑失败')
  }
}

const submitCreate = async () => {
  if (!createForm.value.topic) {
    ElMessage.warning('请输入会议主题')
    return
  }
  if (!createForm.value.startDate || !createForm.value.startTime) {
    ElMessage.warning('请选择开始日期和时间')
    return
  }
  const fullDateTime = `${createForm.value.startDate}T${createForm.value.startTime}`
  creating.value = true
  try {
    await createMeeting({
      ...createForm.value,
      startTime: fullDateTime
    })
    ElMessage.success('会议创建成功')
    showCreateDialog.value = false
    createForm.value = {
      topic: '',
      startDate: null,
      startTime: null,
      duration: 60,
      password: '',
      description: '',
      participantIds: []
    }
    fetchMeetings()
  } catch (e) {
    ElMessage.error('创建会议失败')
  } finally {
    creating.value = false
  }
}

const showDetail = (meeting) => {
  currentMeeting.value = meeting
  showDetailDialog.value = true
}

const joinMeeting = (meeting) => {
  if (meeting.joinUrl) {
    window.open(meeting.joinUrl, '_blank')
  } else {
    ElMessage.warning('会议链接暂不可用')
  }
}

const handleCancel = async (meeting) => {
  try {
    await ElMessageBox.confirm(
      '确定要取消该会议吗？会同时在腾讯会议中取消。',
      '确认取消',
      {
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonText: '取消会议',
        cancelButtonText: '保留会议',
        confirmButtonClass: 'el-button--danger'
      }
    )
    await cancelMeeting(meeting.id)
    ElMessage.success('会议已取消')
    fetchMeetings()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消会议失败')
  }
}

const handleEnd = async (meeting) => {
  try {
    await ElMessageBox.confirm('确定要结束该会议吗？', '确认结束', { type: 'warning' })
    await endMeeting(meeting.id)
    ElMessage.success('会议已结束')
    fetchMeetings()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('结束会议失败')
  }
}

const disabledDate = (time) => {
  return time.getTime() < Date.now() - 86400000
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const getStatusType = (status, row) => {
  if (status === 'SCHEDULED' && row.startTime) {
    const endTime = new Date(row.startTime).getTime() + (row.duration || 0) * 60000
    if (Date.now() > endTime) return 'danger'
    if (Date.now() > new Date(row.startTime).getTime()) return 'success'
  }
  const map = { SCHEDULED: 'info', STARTED: 'success', ENDED: '', CANCELLED: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status, row) => {
  if (status === 'SCHEDULED' && row.startTime) {
    const endTime = new Date(row.startTime).getTime() + (row.duration || 0) * 60000
    if (Date.now() > endTime) return '已过期'
    if (Date.now() > new Date(row.startTime).getTime()) return '进行中'
  }
  const map = { SCHEDULED: '待开始', STARTED: '进行中', ENDED: '已结束', CANCELLED: '已取消' }
  return map[status] || status
}

onMounted(() => {
  fetchMeetings()
  fetchUsers()
  fetchMyMapping()
})
</script>

<style scoped>
.meeting-center {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mapping-status {
  font-size: 13px;
}
.filter-bar {
  margin-bottom: 16px;
}
</style>

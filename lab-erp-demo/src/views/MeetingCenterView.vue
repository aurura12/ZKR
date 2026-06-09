<template>
  <div class="meeting-center">
    <div class="page-header">
      <h2>会议中心</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        创建会议
      </el-button>
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
    <el-table :data="meetingList" v-loading="loading" stripe>
      <el-table-column prop="topic" label="会议主题" min-width="200" />
      <el-table-column prop="startTime" label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分钟)" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
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
          <el-button v-if="row.status === 'SCHEDULED'" type="primary" link @click="joinMeeting(row)">
            加入会议
          </el-button>
          <el-button v-if="row.status === 'STARTED'" type="success" link @click="joinMeeting(row)">
            正在进行
          </el-button>
          <el-button v-if="row.status === 'SCHEDULED'" type="warning" link @click="handleCancel(row)">
            取消
          </el-button>
          <el-button v-if="row.status === 'STARTED'" type="danger" link @click="handleEnd(row)">
            结束
          </el-button>
        </template>
      </el-table-column>
    </el-table>

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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMeetingList, createMeeting, cancelMeeting, endMeeting } from '@/api/meeting'
import request from '@/utils/request'

const loading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const meetingList = ref([])
const userList = ref([])
const filterStatus = ref('')

const createForm = ref({
  topic: '',
  startDate: null,
  startTime: null,
  duration: 60,
  password: '',
  description: '',
  participantIds: []
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
    const res = await request.get('/api/meetings/mapped-users')
    userList.value = res.data || []
  } catch (e) {
    console.error('获取用户列表失败', e)
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

const joinMeeting = (meeting) => {
  if (meeting.joinUrl) {
    window.open(meeting.joinUrl, '_blank')
  } else {
    ElMessage.warning('会议链接暂不可用')
  }
}

const handleCancel = async (meeting) => {
  try {
    await ElMessageBox.confirm('确定要取消该会议吗？', '确认取消', { type: 'warning' })
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

const getStatusType = (status) => {
  const map = { SCHEDULED: 'info', STARTED: 'success', ENDED: '', CANCELLED: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { SCHEDULED: '待开始', STARTED: '进行中', ENDED: '已结束', CANCELLED: '已取消' }
  return map[status] || status
}

onMounted(() => {
  fetchMeetings()
  fetchUsers()
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
.filter-bar {
  margin-bottom: 16px;
}
</style>

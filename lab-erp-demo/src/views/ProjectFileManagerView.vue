<template>
  <div class="file-manager-page">
    <div class="file-manager-layout">
      <aside class="project-sidebar">
        <div class="sidebar-header">
          <h2>项目列表</h2>
          <el-input v-model="projectSearch" placeholder="搜索项目" size="small" clearable />
        </div>
        <div class="project-list">
          <div
            v-for="project in filteredProjects"
            :key="project.projectId"
            class="project-item"
            :class="{ active: selectedProjectId === project.projectId }"
            @click="selectProject(project.projectId)"
          >
            <span class="project-name">{{ project.name }}</span>
            <el-tag size="small" type="info">{{ project.flowType }}</el-tag>
          </div>
        </div>
      </aside>

      <main class="file-manager-main">
        <div class="toolbar">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item @click="currentFolderId = null">根目录</el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="crumb in breadcrumbs"
              :key="crumb.id"
              @click="currentFolderId = crumb.id"
            >
              {{ crumb.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <div class="toolbar-actions">
            <el-button size="small" @click="showCreateFolderDialog">新建目录</el-button>
            <el-button size="small" type="primary" @click="triggerUpload">上传文件</el-button>
            <el-button size="small" :disabled="!selectedFileId" @click="previewSelected">预览</el-button>
            <el-button size="small" :disabled="!selectedFileId" @click="downloadSelected">下载</el-button>
            <el-button size="small" :disabled="!selectedFileId" @click="showMoveDialog">移动</el-button>
            <el-button size="small" :disabled="!selectedFileId" type="danger" @click="confirmDeleteFile">删除</el-button>
            <el-button size="small" @click="refresh">刷新</el-button>
          </div>
        </div>

        <div class="file-tree" v-if="selectedProjectId">
          <div v-if="currentFolders.length === 0 && currentFiles.length === 0" class="empty-hint">
            当前目录为空
          </div>
          <div class="folder-section">
            <div
              v-for="folder in currentFolders"
              :key="folder.id"
              class="tree-node folder-node"
              @click="enterFolder(folder.id)"
            >
              <span class="icon">📁</span>
              <span class="name">{{ folder.name }}</span>
            </div>
          </div>
          <div class="file-section">
            <div
              v-for="file in currentFiles"
              :key="file.id"
              class="tree-node file-node"
              :class="{ active: selectedFileId === file.id }"
              @click="selectFile(file.id)"
              @dblclick="previewFile(file.id, file.name)"
            >
              <span class="icon">📄</span>
              <span class="name">{{ file.name }}</span>
              <span class="source-tag">{{ file.sourceType }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint select-project-hint">请从左侧选择一个项目</div>
      </main>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      style="display: none"
      @change="handleFileSelected"
    />

    <el-dialog v-model="createFolderVisible" title="新建目录" width="360px">
      <el-input v-model="newFolderName" placeholder="目录名称" />
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateFolder">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialogVisible" title="移动到" width="360px">
      <el-select v-model="targetFolderId" placeholder="选择目标目录" style="width: 100%">
        <el-option label="根目录" :value="null" />
        <el-option
          v-for="folder in allFolders"
          :key="folder.id"
          :label="folder.path"
          :value="folder.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const projects = ref([])
const projectSearch = ref('')
const selectedProjectId = ref('')
const treeData = ref({ folders: [], files: [] })
const currentFolderId = ref(null)
const selectedFileId = ref(null)
const fileInputRef = ref(null)

const createFolderVisible = ref(false)
const newFolderName = ref('')
const moveDialogVisible = ref(false)
const targetFolderId = ref(null)

const filteredProjects = computed(() => {
  const q = projectSearch.value.trim().toLowerCase()
  if (!q) return projects.value
  return projects.value.filter(p => p.name?.toLowerCase().includes(q))
})

const allFolders = computed(() => treeData.value.folders || [])

const currentFolders = computed(() => {
  return (treeData.value.folders || []).filter(f => f.parentId === currentFolderId.value)
})

const currentFiles = computed(() => {
  return (treeData.value.files || []).filter(f => f.folderId === currentFolderId.value)
})

const breadcrumbs = computed(() => {
  const crumbs = []
  let fid = currentFolderId.value
  const map = new Map((treeData.value.folders || []).map(f => [f.id, f]))
  while (fid) {
    const folder = map.get(fid)
    if (!folder) break
    crumbs.unshift(folder)
    fid = folder.parentId
  }
  return crumbs
})

const fetchProjects = async () => {
  try {
    projects.value = await request.get('/api/admin/project-files/projects')
    if (projects.value.length > 0 && !selectedProjectId.value) {
      selectProject(projects.value[0].projectId)
    }
  } catch (e) {
    ElMessage.error('加载项目列表失败')
  }
}

const fetchTree = async () => {
  if (!selectedProjectId.value) return
  try {
    treeData.value = await request.get(`/api/admin/project-files/${selectedProjectId.value}/tree`)
    selectedFileId.value = null
  } catch (e) {
    ElMessage.error('加载文件树失败')
  }
}

const selectProject = (projectId) => {
  selectedProjectId.value = projectId
  currentFolderId.value = null
  fetchTree()
}

const enterFolder = (folderId) => {
  currentFolderId.value = folderId
  selectedFileId.value = null
}

const selectFile = (fileId) => {
  selectedFileId.value = fileId
}

const refresh = () => {
  fetchProjects()
  fetchTree()
}

const showCreateFolderDialog = () => {
  newFolderName.value = ''
  createFolderVisible.value = true
}

const confirmCreateFolder = async () => {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入目录名称')
    return
  }
  try {
    await request.post(`/api/admin/project-files/${selectedProjectId.value}/folders`, {
      parentId: currentFolderId.value,
      name: newFolderName.value.trim()
    })
    ElMessage.success('目录创建成功')
    createFolderVisible.value = false
    fetchTree()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '目录创建失败')
  }
}

const showMoveDialog = () => {
  targetFolderId.value = null
  moveDialogVisible.value = true
}

const confirmMove = async () => {
  try {
    await request.patch(`/api/admin/project-files/files/${selectedFileId.value}/move`, {
      folderId: targetFolderId.value
    })
    ElMessage.success('移动成功')
    moveDialogVisible.value = false
    fetchTree()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '移动失败')
  }
}

const getSelectedFileName = () => {
  const file = (treeData.value.files || []).find(f => f.id === selectedFileId.value)
  return file ? file.name : 'download'
}

const downloadSelected = () => {
  if (selectedFileId.value) {
    downloadFile(selectedFileId.value, getSelectedFileName())
  }
}

const downloadFile = async (fileId, filename) => {
  try {
    const blob = await request.get(`/api/admin/project-files/files/${fileId}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    const msg = e.response?.data
    if (msg instanceof Blob) {
      ElMessage.error('下载失败')
    } else {
      ElMessage.error(typeof msg === 'string' ? msg : '下载失败')
    }
  }
}

const previewSelected = () => {
  if (selectedFileId.value) {
    previewFile(selectedFileId.value, getSelectedFileName())
  }
}

const previewFile = async (fileId, filename) => {
  try {
    const blob = await request.get(`/api/admin/project-files/files/${fileId}/preview`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => window.URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ElMessage.error('预览失败')
  }
}

const triggerUpload = () => {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择一个项目')
    return
  }
  fileInputRef.value?.click()
}

const handleFileSelected = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  if (currentFolderId.value !== null) {
    formData.append('folderId', currentFolderId.value)
  }
  try {
    await request.post(`/api/admin/project-files/${selectedProjectId.value}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('文件上传成功')
    fetchTree()
  } catch (e) {
    const msg = e.response?.data?.message || '文件上传失败'
    ElMessage.error(msg)
  }
  event.target.value = ''
}

const confirmDeleteFile = async () => {
  const fileName = getSelectedFileName()
  try {
    await ElMessageBox.confirm(
      `确定要删除文件「${fileName}」吗？`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await request.delete(`/api/admin/project-files/files/${selectedFileId.value}?deletePhysical=true`)
    ElMessage.success('文件删除成功')
    selectedFileId.value = null
    fetchTree()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e.response?.data?.message || '文件删除失败')
    }
  }
}

onMounted(fetchProjects)
</script>

<style scoped>
.file-manager-page {
  min-height: calc(100vh - var(--nav-height));
  padding: 20px;
  background: var(--science-canvas);
}

.file-manager-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  height: calc(100vh - var(--nav-height) - 40px);
  background: var(--science-surface);
  border-radius: 20px;
  border: 1px solid var(--border-soft);
  overflow: hidden;
}

.project-sidebar {
  border-right: 1px solid var(--border-soft);
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px;
  display: grid;
  gap: 10px;
  border-bottom: 1px solid var(--border-soft);
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
}

.project-list {
  overflow-y: auto;
  padding: 8px;
}

.project-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  gap: 8px;
}

.project-item:hover,
.project-item.active {
  background: var(--science-surface-muted);
}

.project-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.file-manager-main {
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-soft);
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.file-tree {
  overflow-y: auto;
  padding: 12px 16px;
}

.empty-hint {
  color: var(--text-sub);
  padding: 24px 12px;
  text-align: center;
}

.select-project-hint {
  padding-top: 60px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.tree-node:hover {
  background: var(--science-surface-muted);
}

.tree-node.active {
  background: rgba(0, 102, 204, 0.12);
  outline: 1px solid rgba(0, 102, 204, 0.3);
}

.tree-node .name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-section {
  margin-bottom: 6px;
}

.folder-node .icon {
  font-size: 16px;
}

.file-node {
  padding: 10px 14px 10px 30px;
}

.file-node .source-tag {
  font-size: 11px;
  color: var(--text-sub);
  background: var(--science-surface-muted);
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .file-manager-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .project-sidebar {
    border-right: none;
    border-bottom: 1px solid var(--border-soft);
    max-height: 200px;
  }
}
</style>

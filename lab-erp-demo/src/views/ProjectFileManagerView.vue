<template>
  <div class="file-manager-page">
    <div class="file-manager-layout">
      <!-- 左侧项目列表 -->
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

      <!-- 右侧文件管理器 -->
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
            <el-button size="small" :disabled="!selectedFileId" @click="showMoveDialog">移动</el-button>
            <el-button size="small" :disabled="!selectedFileId" @click="downloadSelected">下载</el-button>
            <el-button size="small" @click="refresh">刷新</el-button>
          </div>
        </div>

        <div class="file-tree">
          <div class="folder-section">
            <div
              v-for="folder in currentFolders"
              :key="folder.id"
              class="tree-node folder-node"
              :class="{ active: currentFolderId === folder.id }"
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
              @dblclick="downloadFile(file.id)"
            >
              <span class="icon">📄</span>
              <span class="name">{{ file.name }}</span>
              <span class="source-tag">{{ file.sourceType }}</span>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 新建目录对话框 -->
    <el-dialog v-model="createFolderVisible" title="新建目录" width="360px">
      <el-input v-model="newFolderName" placeholder="目录名称" />
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateFolder">确定</el-button>
      </template>
    </el-dialog>

    <!-- 移动文件对话框 -->
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
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const projects = ref([])
const projectSearch = ref('')
const selectedProjectId = ref('')
const treeData = ref({ folders: [], files: [] })
const currentFolderId = ref(null)
const selectedFileId = ref(null)

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

const downloadSelected = () => {
  if (selectedFileId.value) downloadFile(selectedFileId.value)
}

const downloadFile = (fileId) => {
  window.open(`/api/admin/project-files/files/${fileId}/download`)
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
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.file-tree {
  overflow-y: auto;
  padding: 12px 16px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.tree-node:hover {
  background: var(--science-surface-muted);
}

.tree-node.active {
  background: var(--science-blue-soft);
}

.tree-node .name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-section {
  margin-bottom: 12px;
}

.file-node .source-tag {
  font-size: 11px;
  color: var(--text-sub);
  background: var(--science-surface-muted);
  padding: 2px 6px;
  border-radius: 4px;
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

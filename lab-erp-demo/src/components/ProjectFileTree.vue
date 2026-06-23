<template>
  <div class="project-file-tree">
    <div v-if="loading" class="empty-text">加载中...</div>
    <div v-else-if="!hasData" class="empty-text">暂无文件</div>
    <div v-else class="tree-body">
      <div
        v-for="folder in visibleFolders"
        :key="folder.id"
        class="tree-row folder-row"
        :style="{ paddingLeft: (level * 16 + 8) + 'px' }"
        @click="toggleFolder(folder.id)"
      >
        <span class="toggle">{{ expandedFolders.has(folder.id) ? '▼' : '▶' }}</span>
        <span class="icon">📁</span>
        <span class="name">{{ folder.name }}</span>
      </div>
      <div
        v-for="file in visibleFiles"
        :key="file.id"
        class="tree-row file-row"
        :style="{ paddingLeft: ((level + 1) * 16 + 8) + 'px' }"
        @click="download(file.id, file.name)"
      >
        <span class="icon">📄</span>
        <span class="name">{{ file.name }}</span>
        <span class="source">{{ file.sourceType }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import request from '@/utils/request'

const props = defineProps({
  projectId: { type: String, required: true }
})

const treeData = ref({ folders: [], files: [] })
const loading = ref(false)
const expandedFolders = ref(new Set())
const level = 0

const hasData = computed(() =>
  (treeData.value.files?.length || 0) > 0 ||
  (treeData.value.folders?.length || 0) > 0
)

const rootFolders = computed(() =>
  (treeData.value.folders || []).filter(f => f.parentId === null)
)

const rootFiles = computed(() =>
  (treeData.value.files || []).filter(f => f.folderId === null)
)

const visibleFolders = computed(() => rootFolders.value)

const visibleFiles = computed(() => {
  const visible = [...rootFiles.value]
  rootFolders.value.forEach(folder => {
    if (expandedFolders.value.has(folder.id)) {
      collectFiles(folder.id, visible)
      collectSubFolders(folder.id, visible)
    }
  })
  return visible
})

const collectFiles = (folderId, list) => {
  (treeData.value.files || []).filter(f => f.folderId === folderId).forEach(f => list.push(f))
}

const collectSubFolders = (parentId, list) => {
  (treeData.value.folders || []).filter(f => f.parentId === parentId).forEach(f => {
    collectFiles(f.id, list)
    if (expandedFolders.value.has(f.id)) {
      collectSubFolders(f.id, list)
    }
  })
}

const toggleFolder = (folderId) => {
  const next = new Set(expandedFolders.value)
  if (next.has(folderId)) {
    next.delete(folderId)
  } else {
    next.add(folderId)
  }
  expandedFolders.value = next
}

const fetchTree = async () => {
  if (!props.projectId) return
  loading.value = true
  try {
    treeData.value = await request.get(`/api/admin/project-files/${props.projectId}/tree`)
  } catch (e) {
    treeData.value = { folders: [], files: [] }
  } finally {
    loading.value = false
  }
}

const download = (fileId, filename) => {
  const link = document.createElement('a')
  link.href = `/api/admin/project-files/files/${fileId}/download`
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(fetchTree)
watch(() => props.projectId, fetchTree)
</script>

<style scoped>
.project-file-tree {
  padding: 8px 0;
}

.empty-text {
  color: var(--text-sub);
  padding: 12px;
  text-align: center;
}

.tree-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.tree-row:hover {
  background: var(--science-surface-muted);
}

.tree-row .toggle {
  font-size: 10px;
  color: var(--text-sub);
  width: 14px;
  text-align: center;
}

.tree-row .name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-row .source {
  font-size: 11px;
  color: var(--text-sub);
  background: var(--science-surface-muted);
  padding: 1px 5px;
  border-radius: 4px;
}

.file-row {
  color: var(--text-main);
}
</style>

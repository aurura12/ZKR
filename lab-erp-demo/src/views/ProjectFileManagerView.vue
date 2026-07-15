<template>
	<div class="file-manager-page">
		<div class="file-manager-layout">
			<aside class="project-sidebar">
				<div class="sidebar-header">
					<h2>项目列表</h2>
					<el-input
						v-model="projectSearch"
						placeholder="搜索项目"
						size="small"
						clearable
					/>
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
						<el-breadcrumb-item @click="currentFolderId = null"
							>根目录</el-breadcrumb-item
						>
						<el-breadcrumb-item
							v-for="crumb in breadcrumbs"
							:key="crumb.id"
							@click="currentFolderId = crumb.id"
						>
							{{ crumb.name }}
						</el-breadcrumb-item>
					</el-breadcrumb>
					<div class="toolbar-actions">
						<el-button size="small" @click="showCreateFolderDialog"
							>新建目录</el-button
						>
						<el-button size="small" type="primary" @click="triggerUpload"
							>上传文件</el-button
						>
						<el-button
							size="small"
							:disabled="selectedFileIds.length > 1 || !currentFile"
							@click="previewFile(currentFile.id, currentFile.name)"
							>预览</el-button
						>
						<el-button
							size="small"
							:disabled="!currentFile && selectedFileIds.length === 0"
							@click="downloadSelected"
							>下载</el-button
						>
						<el-button
							size="small"
							:disabled="!currentFile && selectedFileIds.length === 0"
							@click="showMoveDialog"
							>移动</el-button
						>
						<el-button
							size="small"
							:disabled="selectedFileIds.length === 0"
							type="danger"
							@click="confirmBatchDelete"
						>
							删除选中{{
								selectedFileIds.length > 0 ? `(${selectedFileIds.length})` : ''
							}}
						</el-button>
						<el-button size="small" @click="refresh">刷新</el-button>
					</div>
				</div>

				<div class="file-table-wrapper" v-if="selectedProjectId">
					<el-table
						v-if="sortedItems.length > 0"
						ref="fileTableRef"
						:data="sortedItems"
						style="width: 100%"
						@selection-change="onSelectionChange"
						@row-click="onRowClick"
						@row-dblclick="onRowDblClick"
						stripe
						size="small"
						highlight-current-row
						@sort-change="onSortChange"
					>
						<el-table-column type="selection" width="40" />
						<el-table-column
							label="名称"
							min-width="400"
							sortable="custom"
							prop="sortName"
						>
							<template #default="{ row }">
								<div class="name-cell">
									<span class="item-icon">{{
										row.type === 'folder' ? '📁' : '📄'
									}}</span>
									<span class="item-name">{{ row.name }}</span>
								</div>
							</template>
						</el-table-column>
						<el-table-column
							label="大小"
							width="140"
							sortable="custom"
							prop="sortSize"
						>
							<template #default="{ row }">
								<span v-if="row.type === 'file'">{{
									formatSize(row.size)
								}}</span>
								<span v-else class="text-muted">-</span>
							</template>
						</el-table-column>
						<el-table-column
							label="创建时间"
							width="220"
							sortable="custom"
							prop="createdAt"
						>
							<template #default="{ row }">
								<span v-if="row.type === 'file'">{{
									formatDate(row.createdAt)
								}}</span>
								<span v-else class="text-muted">-</span>
							</template>
						</el-table-column>
					</el-table>

					<div v-if="sortedItems.length === 0" class="empty-hint">
						当前目录为空
					</div>
				</div>
				<div v-else class="empty-hint select-project-hint">
					请从左侧选择一个项目
				</div>
			</main>
		</div>

		<input
			ref="fileInputRef"
			type="file"
			style="display: none"
			@change="handleFileSelected"
		/>

		<el-dialog v-model="createFolderVisible" title="新建目录" width="420px">
			<el-input v-model="newFolderName" placeholder="目录名称" />
			<template #footer>
				<el-button @click="createFolderVisible = false">取消</el-button>
				<el-button type="primary" @click="confirmCreateFolder">确定</el-button>
			</template>
		</el-dialog>

		<el-dialog v-model="moveDialogVisible" title="移动到" width="420px">
			<el-select
				v-model="targetFolderId"
				placeholder="选择目标目录"
				style="width: 100%"
			>
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
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

const projects = ref([]);
const projectSearch = ref('');
const selectedProjectId = ref('');
const treeData = ref({ folders: [], files: [] });
const currentFolderId = ref(null);
const fileInputRef = ref(null);
const fileTableRef = ref(null);

const createFolderVisible = ref(false);
const newFolderName = ref('');
const moveDialogVisible = ref(false);
const targetFolderId = ref(null);

// 当前选中的文件行（点击行高亮，用于预览/下载/移动）
const currentFile = ref(null);

// 多选
const selectedFileIds = ref([]);
const selectedItems = ref([]);

// 排序: { prop: 'sortName'|'sortSize'|'createdAt', order: 'ascending'|'descending' }
const sortState = ref({ prop: 'sortName', order: 'ascending' });

const filteredProjects = computed(() => {
	const q = projectSearch.value.trim().toLowerCase();
	if (!q) return projects.value;
	return projects.value.filter((p) => p.name?.toLowerCase().includes(q));
});

const allFolders = computed(() => treeData.value.folders || []);

const currentFolders = computed(() => {
	return (treeData.value.folders || []).filter(
		(f) => f.parentId === currentFolderId.value,
	);
});

const currentFiles = computed(() => {
	return (treeData.value.files || []).filter(
		(f) => f.folderId === currentFolderId.value,
	);
});

// 合并文件和文件夹，排序
const sortedItems = computed(() => {
	const folders = currentFolders.value.map((f) => ({
		id: f.id,
		name: f.name,
		type: 'folder',
		size: null,
		createdAt: null,
		sortName: (f.name || '').toLowerCase(),
		sortSize: -1,
		_folder: f,
	}));
	const files = currentFiles.value.map((f) => ({
		id: f.id,
		name: f.name,
		type: 'file',
		size: f.size || 0,
		createdAt: f.createdAt || null,
		sortName: (f.name || '').toLowerCase(),
		sortSize: Number(f.size || 0),
		_file: f,
	}));
	const all = [...folders, ...files];
	const { prop, order } = sortState.value;
	const desc = order === 'descending';
	all.sort((a, b) => {
		let va = a[prop];
		let vb = b[prop];
		if (prop === 'sortName') {
			va = String(va || '');
			vb = String(vb || '');
			return desc ? vb.localeCompare(va) : va.localeCompare(vb);
		}
		if (prop === 'createdAt') {
			va = va ? new Date(va).getTime() : 0;
			vb = vb ? new Date(vb).getTime() : 0;
			return desc ? vb - va : va - vb;
		}
		if (prop === 'sortSize') {
			return desc ? vb - va : va - vb;
		}
		return 0;
	});
	return all;
});

const breadcrumbs = computed(() => {
	const crumbs = [];
	let fid = currentFolderId.value;
	const map = new Map((treeData.value.folders || []).map((f) => [f.id, f]));
	while (fid) {
		const folder = map.get(fid);
		if (!folder) break;
		crumbs.unshift(folder);
		fid = folder.parentId;
	}
	return crumbs;
});

const formatDate = (value) => {
	if (!value) return '-';
	const d = new Date(value);
	const pad = (n) => String(n).padStart(2, '0');
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const formatSize = (value) => {
	if (!value && value !== 0) return '-';
	const bytes = Number(value);
	if (bytes <= 0) return '-';
	const units = ['B', 'KB', 'MB', 'GB'];
	let i = 0;
	let size = bytes;
	while (size >= 1024 && i < units.length - 1) {
		size /= 1024;
		i++;
	}
	return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
};

const onSortChange = ({ prop, order }) => {
	sortState.value = { prop: prop || 'sortName', order: order || 'ascending' };
};

const onSelectionChange = (rows) => {
	selectedFileIds.value = rows.map((r) => r.id);
	selectedItems.value = rows;
};

const onRowClick = (row) => {
	currentFile.value = row && row.type === 'file' ? row : null;
};

const onRowDblClick = (row) => {
	if (row.type === 'folder') {
		enterFolder(row.id);
	} else {
		previewFile(row.id, row.name);
	}
};

const fetchProjects = async () => {
	try {
		projects.value = await request.get('/api/admin/project-files/projects');
		if (projects.value.length > 0 && !selectedProjectId.value) {
			selectProject(projects.value[0].projectId);
		}
	} catch (e) {
		ElMessage.error('加载项目列表失败');
	}
};

const fetchTree = async () => {
	if (!selectedProjectId.value) return;
	try {
		treeData.value = await request.get(
			`/api/admin/project-files/${selectedProjectId.value}/tree`,
		);
	} catch (e) {
		ElMessage.error('加载文件树失败');
	}
};

const selectProject = (projectId) => {
	selectedProjectId.value = projectId;
	currentFolderId.value = null;
	selectedFileIds.value = [];
	selectedItems.value = [];
	currentFile.value = null;
	fetchTree();
};

const enterFolder = (folderId) => {
	currentFolderId.value = folderId;
	selectedFileIds.value = [];
	selectedItems.value = [];
	currentFile.value = null;
};

const refresh = () => {
	fetchProjects();
	fetchTree();
};

const showCreateFolderDialog = () => {
	newFolderName.value = '';
	createFolderVisible.value = true;
};

const confirmCreateFolder = async () => {
	if (!newFolderName.value.trim()) {
		ElMessage.warning('请输入目录名称');
		return;
	}
	try {
		await request.post(
			`/api/admin/project-files/${selectedProjectId.value}/folders`,
			{
				parentId: currentFolderId.value,
				name: newFolderName.value.trim(),
			},
		);
		ElMessage.success('目录创建成功');
		createFolderVisible.value = false;
		fetchTree();
	} catch (e) {
		ElMessage.error(e.response?.data?.message || '目录创建失败');
	}
};

const triggerUpload = () => {
	if (!selectedProjectId.value) {
		ElMessage.warning('请先选择一个项目');
		return;
	}
	fileInputRef.value?.click();
};

const downloadSelected = () => {
	const items = selectedItems.value;
	const fileIds = items.filter((r) => r.type === 'file').map((r) => r.id);
	const folderIds = items.filter((r) => r.type === 'folder').map((r) => r.id);

	// 如果没有勾选任何条目，下载单个选中的文件
	if (fileIds.length === 0 && folderIds.length === 0) {
		if (currentFile.value) {
			downloadSingleFile(currentFile.value.id, currentFile.value.name);
		}
		return;
	}

	// 只选中一个文件（没有目录），直接下载文件本身
	if (fileIds.length === 1 && folderIds.length === 0) {
		const item = items.find((r) => r.type === 'file');
		if (item) {
			downloadSingleFile(item.id, item.name);
			return;
		}
	}

	// 批量下载（多个文件或包含目录 -> 打包 zip）
	// 取第一个选中项的名称（去扩展名）作为 zip 文件名
	const firstName = items[0]?.name || 'files';
	const dotIndex = firstName.lastIndexOf('.');
	const zipName =
		(dotIndex > 0 ? firstName.substring(0, dotIndex) : firstName) + '.zip';
	downloadBatchFiles(fileIds, folderIds, zipName);
};

const downloadSingleFile = async (fileId, filename) => {
	try {
		const blob = await request.get(
			`/api/admin/project-files/files/${fileId}/download`,
			{ responseType: 'blob' },
		);
		const url = window.URL.createObjectURL(blob);
		const link = document.createElement('a');
		link.href = url;
		link.download = filename || 'download';
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
		window.URL.revokeObjectURL(url);
	} catch (e) {
		const msg = e.response?.data;
		if (msg instanceof Blob) {
			ElMessage.error('下载失败');
		} else {
			ElMessage.error(typeof msg === 'string' ? msg : '下载失败');
		}
	}
};

const downloadBatchFiles = async (fileIds, folderIds, zipName) => {
	try {
		const blob = await request.post(
			'/api/admin/project-files/download-batch',
			{ fileIds, folderIds, zipName },
			{ responseType: 'blob' },
		);
		const url = window.URL.createObjectURL(blob);
		const link = document.createElement('a');
		link.href = url;
		link.download = zipName || 'files_export.zip';
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
		window.URL.revokeObjectURL(url);
	} catch (e) {
		ElMessage.error('批量下载失败');
	}
};

const showMoveDialog = () => {
	targetFolderId.value = null;
	moveDialogVisible.value = true;
};

const confirmMove = async () => {
	const items = selectedItems.value;
	const fileIds = items.filter((r) => r.type === 'file').map((r) => r.id);
	const folderIds = items.filter((r) => r.type === 'folder').map((r) => r.id);

	// 如果没有勾选，移动单个选中的文件
	if (fileIds.length === 0 && folderIds.length === 0) {
		if (!currentFile.value) return;
		try {
			await request.patch(
				`/api/admin/project-files/files/${currentFile.value.id}/move`,
				{
					folderId: targetFolderId.value,
				},
			);
			ElMessage.success('移动成功');
			moveDialogVisible.value = false;
			fetchTree();
		} catch (e) {
			ElMessage.error(e.response?.data?.message || '移动失败');
		}
		return;
	}

	try {
		await request.patch('/api/admin/project-files/move-batch', {
			fileIds,
			folderIds,
			targetFolderId: targetFolderId.value,
			projectId: selectedProjectId.value,
		});
		ElMessage.success('批量移动成功');
		moveDialogVisible.value = false;
		selectedFileIds.value = [];
		selectedItems.value = [];
		fetchTree();
	} catch (e) {
		ElMessage.error(e.response?.data?.message || '批量移动失败');
	}
};

const handleFileSelected = async (event) => {
	const file = event.target.files?.[0];
	if (!file) return;
	const formData = new FormData();
	formData.append('file', file);
	if (currentFolderId.value !== null) {
		formData.append('folderId', currentFolderId.value);
	}
	try {
		await request.post(
			`/api/admin/project-files/${selectedProjectId.value}/upload`,
			formData,
			{
				headers: { 'Content-Type': 'multipart/form-data' },
			},
		);
		ElMessage.success('文件上传成功');
		fetchTree();
	} catch (e) {
		const msg = e.response?.data?.message || '文件上传失败';
		ElMessage.error(msg);
	}
	event.target.value = '';
};

const previewFile = async (fileId, filename) => {
	try {
		const blob = await request.get(
			`/api/admin/project-files/files/${fileId}/preview`,
			{ responseType: 'blob' },
		);
		const url = window.URL.createObjectURL(blob);
		window.open(url, '_blank');
		setTimeout(() => window.URL.revokeObjectURL(url), 60000);
	} catch (e) {
		ElMessage.error('预览失败');
	}
};

const confirmBatchDelete = async () => {
	if (selectedFileIds.value.length === 0) return;
	const fileItems = selectedItems.value.filter((r) => r.type === 'file');
	const folderItems = selectedItems.value.filter((r) => r.type === 'folder');
	const fileCount = fileItems.length;
	const folderCount = folderItems.length;
	const total = selectedFileIds.value.length;
	let msg = `确定要删除选中的 ${total} 个条目吗？`;
	const title = total > 1 ? '批量删除确认' : '删除确认';
	try {
		await ElMessageBox.confirm(msg, title, {
			confirmButtonText: '确定删除',
			cancelButtonText: '取消',
			type: 'warning',
		});

		if (fileItems.length > 0) {
			await request.delete(
				'/api/admin/project-files/files/batch-delete?deletePhysical=true',
				{
					data: fileItems.map((r) => r.id),
				},
			);
		}
		for (const folder of folderItems) {
			try {
				await request.delete(
					`/api/admin/project-files/${selectedProjectId.value}/folders/${folder.id}`,
				);
			} catch (e) {
				ElMessage.warning(
					`目录「${folder.name}」删除失败: ${e.response?.data?.message || '删除失败'}`,
				);
			}
		}
		ElMessage.success('删除操作完成');
		selectedFileIds.value = [];
		selectedItems.value = [];
		fetchTree();
	} catch (e) {
		if (e !== 'cancel' && e !== 'close') {
			ElMessage.error(e.response?.data?.message || '删除失败');
		}
	}
};

onMounted(fetchProjects);
</script>

<style scoped>
.file-manager-page {
	min-height: calc(100vh - var(--nav-height));
	padding: 24px;
	background: var(--science-canvas);
}

.file-manager-layout {
	display: grid;
	grid-template-columns: 320px 1fr;
	gap: 16px;
	height: calc(100vh - var(--nav-height) - 48px);
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
	padding: 20px;
	display: grid;
	gap: 12px;
	border-bottom: 1px solid var(--border-soft);
}

.sidebar-header h2 {
	margin: 0;
	font-size: 18px;
}

.project-list {
	overflow-y: auto;
	padding: 12px;
}

.project-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 14px 16px;
	border-radius: 10px;
	cursor: pointer;
	gap: 8px;
	font-size: 15px;
}

.project-item:hover,
.project-item.active {
	background: var(--science-surface-muted);
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
	padding: 16px 20px;
	border-bottom: 1px solid var(--border-soft);
	gap: 12px;
	flex-wrap: wrap;
	font-size: 14px;
}

.toolbar-actions {
	display: flex;
	gap: 8px;
	flex-wrap: wrap;
}

.file-table-wrapper {
	overflow-y: auto;
	padding: 12px 0;
}

.empty-hint {
	color: var(--text-sub);
	padding: 24px 12px;
	text-align: center;
}

.select-project-hint {
	padding-top: 60px;
}

.name-cell {
	display: flex;
	align-items: center;
	gap: 10px;
}

.item-icon {
	font-size: 18px;
	flex-shrink: 0;
}

.item-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 14px;
}

.text-muted {
	color: var(--text-sub);
	font-size: 14px;
}

:deep(.el-table .el-table__cell) {
	padding: 12px 0;
}

:deep(.el-table .cell) {
	font-size: 14px;
}

:deep(.el-breadcrumb) {
	font-size: 14px;
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

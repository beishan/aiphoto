<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from '@/utils/feedback'
import { folderApi } from '@/api/folderApi'
import type { ScanFolder } from '@/types'
import type { BrowseItem } from '@/api/folderApi'

const router = useRouter()
const message = useMessage()

const folders = ref<ScanFolder[]>([])
const loading = ref(true)
const showAdd = ref(false)
const newName = ref('')
const newPath = ref('')
const newStorageMode = ref('COPY')
const adding = ref(false)
const scanningIds = ref<Set<number>>(new Set())

// Browse mode state
const inputMode = ref<'input' | 'browse'>('input')
const browseItems = ref<BrowseItem[]>([])
const browseLoading = ref(false)
const browsePath = ref('')
const browsePathParts = computed(() => {
  if (!browsePath.value) return []
  const parts = browsePath.value.split(/[/\\]/).filter(Boolean)
  // Reconstruct paths for each level
  const result: { name: string; path: string }[] = []
  let accumulated = ''
  for (const part of parts) {
    if (accumulated) {
      accumulated += '/' + part
    } else {
      accumulated = part
    }
    // On Windows, the first part might be drive letter like "C:"
    if (accumulated.endsWith(':')) {
      accumulated += '/'
    }
    result.push({ name: part, path: accumulated })
  }
  return result
})

// Polling timer for scan status
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadFolders()
  // Poll every 3 seconds if any folder is scanning
  pollTimer = setInterval(() => {
    if (scanningIds.value.size > 0) {
      loadFolders()
    }
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function loadFolders() {
  loading.value = true
  try {
    const { data } = await folderApi.list()
    folders.value = data
    // Track which folders are scanning
    const newScanning = new Set<number>()
    data.forEach(f => {
      if (f.scanStatus === 'SCANNING') newScanning.add(f.id)
    })
    scanningIds.value = newScanning
  } finally {
    loading.value = false
  }
}

async function loadBrowseItems(path: string) {
  browseLoading.value = true
  try {
    const { data } = await folderApi.browse(path)
    browseItems.value = data
    browsePath.value = path
  } catch (e: any) {
    message.error(e?.response?.data?.message || '无法读取目录')
    browseItems.value = []
  } finally {
    browseLoading.value = false
  }
}

function handleOpenBrowse() {
  inputMode.value = 'browse'
  browsePath.value = ''
  browseItems.value = []
  loadBrowseItems('')
}

function handleOpenInput() {
  inputMode.value = 'input'
}

function navigateToFolder(item: BrowseItem) {
  if (!item.readable) {
    message.warning('该文件夹无读取权限')
    return
  }
  loadBrowseItems(item.path)
}

function navigateToPath(path: string) {
  loadBrowseItems(path)
}

function selectFolder(item: BrowseItem) {
  newPath.value = item.path
  // Auto-fill name from folder name if empty
  if (!newName.value.trim()) {
    newName.value = item.name
  }
  inputMode.value = 'input'
  message.success(`已选择: ${item.name}`)
}

async function handleAdd() {
  if (!newName.value.trim()) {
    message.warning('请输入文件夹名称')
    return
  }
  if (!newPath.value.trim()) {
    message.warning('请输入文件夹路径')
    return
  }
  adding.value = true
  try {
    await folderApi.create({
      name: newName.value.trim(),
      path: newPath.value.trim(),
      storageMode: newStorageMode.value,
    })
    showAdd.value = false
    newName.value = ''
    newPath.value = ''
    newStorageMode.value = 'COPY'
    await loadFolders()
    message.success('文件夹添加成功')
  } catch (e: any) {
    message.error(e?.response?.data?.message || '添加失败')
  } finally {
    adding.value = false
  }
}

async function handleScan(folder: ScanFolder) {
  if (folder.scanStatus === 'SCANNING') return
  try {
    await folderApi.scan(folder.id)
    scanningIds.value.add(folder.id)
    message.success('扫描已开始')
  } catch (e) {
    message.error('启动扫描失败')
  }
}

async function handleDelete(folder: ScanFolder, e: Event) {
  e.stopPropagation()
  if (folder.scanStatus === 'SCANNING') {
    message.warning('正在扫描中，请稍后再试')
    return
  }
  try {
    await folderApi.delete(folder.id)
    await loadFolders()
    message.success('文件夹已删除')
  } catch (e) {
    message.error('删除失败')
  }
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    IDLE: '待扫描',
    SCANNING: '扫描中...',
    COMPLETED: '完成',
    ERROR: '错误',
  }
  return map[status] || status
}

function getStatusColor(status: string) {
  const map: Record<string, string> = {
    IDLE: 'var(--text-tertiary)',
    SCANNING: 'var(--accent)',
    COMPLETED: 'var(--success)',
    ERROR: 'var(--danger)',
  }
  return map[status] || 'var(--text-tertiary)'
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

function closeAddDialog() {
  showAdd.value = false
  newName.value = ''
  newPath.value = ''
  newStorageMode.value = 'COPY'
  inputMode.value = 'input'
  browseItems.value = []
  browsePath.value = ''
}
</script>

<template>
  <div class="folders-view">
    <div class="page-header">
      <button class="back-btn" @click="router.push('/more')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <h1 class="page-title">文件夹管理</h1>
    </div>

    <p class="page-desc">添加 NAS 上的文件夹，自动扫描并导入照片</p>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="folders.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
      </svg>
      <h3>暂无文件夹</h3>
      <p>添加 NAS 文件夹路径开始扫描</p>
      <button class="add-btn" @click="showAdd = true">添加文件夹</button>
    </div>

    <!-- Folder list -->
    <div v-else class="folder-list">
      <div
        v-for="folder in folders"
        :key="folder.id"
        class="folder-card"
      >
        <div class="folder-icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
            <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
          </svg>
        </div>
        <div class="folder-info">
          <div class="folder-header">
            <span class="folder-name">{{ folder.name }}</span>
            <span class="folder-status" :style="{ color: getStatusColor(folder.scanStatus) }">
              <span v-if="folder.scanStatus === 'SCANNING'" class="scan-pulse"></span>
              {{ getStatusText(folder.scanStatus) }}
            </span>
          </div>
          <div class="folder-path">{{ folder.path }}</div>
          <div class="folder-meta">
            <span class="meta-item">
              <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
              </svg>
              {{ folder.photoCount }} 张
            </span>
            <span class="meta-item">
              <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
              </svg>
              {{ folder.storageMode === 'COPY' ? '复制模式' : '链接模式' }}
            </span>
            <span v-if="folder.lastScanAt" class="meta-item">
              <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z" />
              </svg>
              {{ formatDate(folder.lastScanAt) }}
            </span>
          </div>
          <div v-if="folder.errorMessage" class="folder-error">
            {{ folder.errorMessage }}
          </div>
        </div>
        <div class="folder-actions">
          <button
            class="action-btn scan-btn"
            :class="{ scanning: folder.scanStatus === 'SCANNING' }"
            :disabled="folder.scanStatus === 'SCANNING'"
            @click="handleScan(folder)"
          >
            <svg v-if="folder.scanStatus !== 'SCANNING'" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
            </svg>
            <div v-else class="btn-spinner"></div>
            {{ folder.scanStatus === 'SCANNING' ? '扫描中' : '扫描' }}
          </button>
          <button class="action-btn delete-btn" @click="(e: Event) => handleDelete(folder, e)">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
            </svg>
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- Add FAB -->
    <button v-if="!loading" class="fab-add" @click="showAdd = true">
      <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
        <path d="M12 4v16m8-8H4" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
    </button>

    <el-dialog v-model="showAdd" title="添加文件夹" width="620px" class="mv-dialog folder-dialog" @closed="closeAddDialog">

            <div class="form-group">
              <label>名称</label>
              <el-input v-model="newName" placeholder="例如：家庭照片" class="ios-input" clearable />
            </div>

            <div class="form-group">
              <label>文件夹路径</label>
              <!-- Mode toggle -->
              <div class="mode-toggle">
                <button
                  class="toggle-btn"
                  :class="{ active: inputMode === 'input' }"
                  @click="handleOpenInput"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                    <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z" />
                  </svg>
                  手动输入
                </button>
                <button
                  class="toggle-btn"
                  :class="{ active: inputMode === 'browse' }"
                  @click="handleOpenBrowse"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                    <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
                  </svg>
                  点击选择
                </button>
              </div>

              <!-- Input mode -->
              <div v-if="inputMode === 'input'" class="path-input-area">
                <el-input v-model="newPath" placeholder="/nas/photos/family" class="ios-input" clearable />
              </div>

              <!-- Browse mode -->
              <div v-else class="browse-area">
                <!-- Breadcrumb -->
                <div class="browse-breadcrumb">
                  <button class="breadcrumb-item root" @click="navigateToPath('')">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                      <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
                    </svg>
                    根目录
                  </button>
                  <template v-for="(part, idx) in browsePathParts" :key="idx">
                    <span class="breadcrumb-sep">/</span>
                    <button class="breadcrumb-item" @click="navigateToPath(part.path)">
                      {{ part.name }}
                    </button>
                  </template>
                </div>

                <!-- Folder list -->
                <div class="browse-list">
                  <div v-if="browseLoading" class="browse-loading">
                    <div class="loading-spinner small"></div>
                  </div>
                  <div v-else-if="browseItems.length === 0" class="browse-empty">
                    此目录下没有子文件夹
                  </div>
                  <template v-else>
                    <div
                      v-for="item in browseItems"
                      :key="item.path"
                      class="browse-item"
                      :class="{ unreadable: !item.readable }"
                    >
                      <div class="browse-item-icon">
                        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                          <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
                        </svg>
                      </div>
                      <span class="browse-item-name" @click="navigateToFolder(item)">{{ item.name }}</span>
                      <button
                        v-if="item.readable"
                        class="browse-item-select"
                        @click="selectFolder(item)"
                      >
                        选择
                      </button>
                    </div>
                  </template>
                </div>
              </div>

              <span class="form-hint">NAS 挂载到 Docker 容器内的本地路径</span>
            </div>

            <div class="form-group">
              <label>存储模式</label>
              <div class="mode-options">
                <button
                  class="mode-btn"
                  :class="{ active: newStorageMode === 'COPY' }"
                  @click="newStorageMode = 'COPY'"
                >
                  <span class="mode-icon">📦</span>
                  <span class="mode-label">复制</span>
                  <span class="mode-desc">复制到本地持久化存储</span>
                </button>
                <button
                  class="mode-btn"
                  :class="{ active: newStorageMode === 'LINK' }"
                  @click="newStorageMode = 'LINK'"
                >
                  <span class="mode-icon">🔗</span>
                  <span class="mode-label">链接</span>
                  <span class="mode-desc">只记录原始路径</span>
                </button>
              </div>
            </div>

      <template #footer>
        <el-button @click="closeAddDialog">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.folders-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  padding: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  flex: 1;
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 8px 16px 24px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 16px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.loading-spinner.small {
  width: 18px;
  height: 18px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 12px;
  color: var(--text-secondary);
  padding: 0 16px;
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); margin: 0; }

.add-btn {
  margin-top: 8px;
  padding: 10px 24px;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-full);
  font-size: 15px;
  font-weight: 600;
  border: none;
  cursor: pointer;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 16px;
}

.folder-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.folder-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 159, 10, 0.15);
  color: #ff9f0a;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.folder-info {
  flex: 1;
  min-width: 0;
}

.folder-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.folder-name {
  font-size: 16px;
  font-weight: 600;
}

.folder-status {
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.scan-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.folder-path {
  font-size: 13px;
  color: var(--text-secondary);
  font-family: monospace;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.folder-error {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(255, 69, 58, 0.1);
  border-radius: 8px;
  font-size: 12px;
  color: var(--danger);
}

.folder-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.scan-btn {
  background: rgba(10, 132, 255, 0.15);
  color: var(--accent);
}

.scan-btn:hover {
  background: rgba(10, 132, 255, 0.25);
}

.scan-btn.scanning {
  background: rgba(10, 132, 255, 0.1);
  color: var(--accent);
  opacity: 0.7;
}

.scan-btn:disabled {
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(10, 132, 255, 0.3);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.delete-btn {
  background: rgba(255, 69, 58, 0.1);
  color: var(--danger);
  padding: 8px 12px;
}

.delete-btn:hover {
  background: rgba(255, 69, 58, 0.2);
}

.fab-add {
  position: fixed;
  bottom: calc(var(--tab-height) + 20px);
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(10, 132, 255, 0.4);
  z-index: 50;
  border: none;
  cursor: pointer;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-sheet {
  width: 100%;
  max-width: 440px;
  max-height: 85vh;
  border-radius: var(--radius-xl);
  padding: 24px;
  border: 0.5px solid var(--glass-border);
  overflow-y: auto;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.sheet-header h3 { font-size: 18px; font-weight: 600; margin: 0; }
.sheet-close { color: var(--text-secondary); padding: 4px; }

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.ios-input {
  width: 100%;
}

.ios-input :deep(.el-input__wrapper) { min-height: 44px; border-radius: var(--radius-md); }

.form-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* Mode toggle */
.mode-toggle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}

.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border: 1.5px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  border-color: var(--accent);
  color: var(--text-primary);
}

.toggle-btn.active {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.1);
  color: var(--accent);
}

/* Browse area */
.browse-area {
  background: var(--bg-tertiary);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.browse-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.15);
  font-size: 12px;
  min-height: 38px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.breadcrumb-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.breadcrumb-item.root {
  color: var(--accent);
  font-weight: 500;
}

.breadcrumb-sep {
  color: var(--text-tertiary);
  font-size: 11px;
}

.browse-list {
  max-height: 200px;
  overflow-y: auto;
}

.browse-loading {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.browse-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.browse-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 0.5px solid rgba(255, 255, 255, 0.05);
  transition: background 0.15s;
}

.browse-item:last-child {
  border-bottom: none;
}

.browse-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.browse-item.unreadable {
  opacity: 0.5;
}

.browse-item-icon {
  color: #ff9f0a;
  flex-shrink: 0;
}

.browse-item-name {
  flex: 1;
  font-size: 14px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.browse-item-name:hover {
  color: var(--accent);
}

.browse-item-select {
  padding: 5px 10px;
  border-radius: 6px;
  background: var(--accent);
  color: white;
  font-size: 12px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
  flex-shrink: 0;
}

.browse-item-select:hover {
  opacity: 0.85;
}

.path-input-area {
  /* Just spacing */
}

/* Storage mode options */
.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 8px;
  border: 2px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: var(--accent);
}

.mode-btn.active {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.1);
}

.mode-icon {
  font-size: 24px;
}

.mode-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.mode-desc {
  font-size: 11px;
  color: var(--text-secondary);
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: white;
  font-size: 16px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Transitions */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>

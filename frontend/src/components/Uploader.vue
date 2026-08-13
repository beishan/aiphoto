<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage } from '@/utils/feedback'
import { useTaskStore } from '@/stores/taskStore'
import http from '@/api/http'

const emit = defineEmits<{
  uploaded: []
  done: []
}>()

interface UploadFile {
  id: string
  file: File
  name: string
  size: number
  progress: number
  status: 'pending' | 'uploading' | 'completed' | 'failed' | 'duplicate' | 'cancelled'
  error?: string
  xhr?: XMLHttpRequest
}

const message = useMessage()
const taskStore = useTaskStore()
const fileInput = ref<HTMLInputElement | null>(null)
const files = ref<UploadFile[]>([])
const uploading = ref(false)
const MAX_CONCURRENT = 3

const totalFiles = computed(() => files.value.length)
const completedCount = computed(() => files.value.filter((f) => f.status === 'completed').length)
const failedCount = computed(() => files.value.filter((f) => f.status === 'failed' || f.status === 'duplicate').length)
const activeCount = computed(() => files.value.filter((f) => f.status === 'uploading' || f.status === 'pending').length)
const overallProgress = computed(() => {
  if (totalFiles.value === 0) return 0
  const total = files.value.reduce((sum, f) => sum + f.progress, 0)
  return Math.round(total / totalFiles.value)
})

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function triggerSelect() {
  fileInput.value?.click()
}

function addFiles(newFiles: FileList | File[]) {
  const fileArray = Array.from(newFiles)
  for (const file of fileArray) {
    if (files.value.some((f) => f.file.name === file.name && f.file.size === file.size)) continue
    files.value.push({
      id: `upload-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      file,
      name: file.name,
      size: file.size,
      progress: 0,
      status: 'pending',
    })
  }
}

function removeFile(id: string) {
  const f = files.value.find((f) => f.id === id)
  if (f?.xhr) f.xhr.abort()
  files.value = files.value.filter((f) => f.id !== id)
}

function clearCompleted() {
  files.value = files.value.filter((f) => f.status !== 'completed')
}

function cancelAll() {
  files.value.forEach((f) => {
    if (f.xhr) f.xhr.abort()
    if (f.status === 'pending' || f.status === 'uploading') f.status = 'cancelled'
  })
  uploading.value = false
}

async function startUpload() {
  const pending = files.value.filter((f) => f.status === 'pending')
  if (pending.length === 0) return

  uploading.value = true
  let successCount = 0
  let failCount = 0

  const queue = [...pending]
  const workers: Promise<void>[] = []

  for (let i = 0; i < MAX_CONCURRENT; i++) {
    workers.push(
      (async () => {
        while (queue.length > 0) {
          const item = queue.shift()
          if (!item || item.status !== 'pending') continue
          const ok = await uploadSingle(item)
          if (ok) successCount++
          else failCount++
        }
      })()
    )
  }

  await Promise.all(workers)
  uploading.value = false

  if (successCount > 0) {
    message.success(`成功上传 ${successCount} 张照片`)
    emit('uploaded')
  }
  if (failCount > 0) {
    message.warning(`${failCount} 张照片上传失败`)
  }
}

function uploadSingle(item: UploadFile): Promise<boolean> {
  return new Promise((resolve) => {
    const formData = new FormData()
    formData.append('file', item.file)

    const xhr = new XMLHttpRequest()
    item.xhr = xhr
    item.status = 'uploading'
    item.progress = 0

    taskStore.addUploadTask(item.id, item.name)
    taskStore.updateUploadProgress(item.id, 0)

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        const pct = Math.round((e.loaded / e.total) * 100)
        item.progress = pct
        taskStore.updateUploadProgress(item.id, pct)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        item.status = 'completed'
        item.progress = 100
        taskStore.completeUploadTask(item.id)
        resolve(true)
      } else if (xhr.status === 409) {
        item.status = 'duplicate'
        item.error = '重复文件'
        taskStore.failUploadTask(item.id)
        resolve(false)
      } else {
        item.status = 'failed'
        try {
          const resp = JSON.parse(xhr.responseText)
          item.error = resp.message || '上传失败'
        } catch {
          item.error = '上传失败'
        }
        taskStore.failUploadTask(item.id)
        resolve(false)
      }
    })

    xhr.addEventListener('error', () => {
      item.status = 'failed'
      item.error = '网络错误'
      taskStore.failUploadTask(item.id)
      resolve(false)
    })

    xhr.addEventListener('abort', () => {
      item.status = 'cancelled'
      item.error = '已取消'
      taskStore.failUploadTask(item.id)
      resolve(false)
    })

    const token = localStorage.getItem('token')
    xhr.open('POST', '/api/photos/upload')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.send(formData)
  })
}

function retryFailed() {
  files.value.forEach((f) => {
    if (f.status === 'failed' || f.status === 'duplicate') {
      f.status = 'pending'
      f.progress = 0
      f.error = undefined
    }
  })
  startUpload()
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    addFiles(input.files)
    input.value = ''
  }
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  dragging.value = false
  if (e.dataTransfer?.files) addFiles(e.dataTransfer.files)
}

const dragging = ref(false)
function handleDragOver(e: DragEvent) {
  e.preventDefault()
  dragging.value = true
}
function handleDragLeave() {
  dragging.value = false
}

function getObjectUrl(file: File): string {
  return URL.createObjectURL(file)
}
</script>

<template>
  <div class="uploader" @drop="handleDrop" @dragover="handleDragOver" @dragleave="handleDragLeave">
    <input
      ref="fileInput"
      type="file"
      accept="image/*,video/*"
      multiple
      style="display: none"
      @change="handleFileChange"
    />

    <!-- Drop zone (shown when no files selected) -->
    <div v-if="files.length === 0" class="drop-zone" :class="{ dragging }" @click="triggerSelect">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="48" height="48" class="drop-icon">
        <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
        <polyline points="17 8 12 3 7 8"/>
        <line x1="12" y1="3" x2="12" y2="15"/>
      </svg>
      <p class="drop-text">拖拽照片到这里，或点击选择</p>
      <p class="drop-hint">支持 JPG / PNG / HEIC / WebP / MP4 / MOV</p>
    </div>

    <!-- File list -->
    <div v-else class="file-panel">
      <!-- Header -->
      <div class="panel-header">
        <div class="header-left">
          <span class="file-count">{{ totalFiles }} 个文件</span>
          <span v-if="uploading" class="progress-text">{{ overallProgress }}%</span>
        </div>
        <div class="header-actions">
          <button v-if="!uploading" class="btn-add" @click="triggerSelect">添加</button>
          <button v-if="failedCount > 0 && !uploading" class="btn-retry" @click="retryFailed">重试失败</button>
          <button v-if="completedCount > 0 && !uploading" class="btn-clear" @click="clearCompleted">清除已完成</button>
          <button v-if="uploading" class="btn-cancel" @click="cancelAll">取消</button>
          <button
            v-if="!uploading && activeCount > 0"
            class="btn-upload"
            @click="startUpload"
          >
            上传 ({{ activeCount }})
          </button>
        </div>
      </div>

      <!-- Overall progress bar -->
      <div v-if="uploading" class="overall-progress">
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: overallProgress + '%' }"></div>
        </div>
      </div>

      <!-- File items -->
      <div class="file-list">
        <TransitionGroup name="file-item">
          <div v-for="item in files" :key="item.id" class="file-item" :class="item.status">
            <!-- Thumbnail preview -->
            <div class="file-thumb">
              <img
                v-if="item.file.type.startsWith('image/')"
                :src="getObjectUrl(item.file)"
                class="thumb-img"
              />
              <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20" class="thumb-icon">
                <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
              </svg>
            </div>

            <!-- Info -->
            <div class="file-info">
              <span class="file-name">{{ item.name }}</span>
              <span class="file-meta">
                <span class="file-size">{{ formatSize(item.size) }}</span>
                <template v-if="item.status === 'completed'">
                  <span class="status-dot completed"></span> 已完成
                </template>
                <template v-else-if="item.status === 'duplicate'">
                  <span class="status-dot duplicate"></span> 重复文件
                </template>
                <template v-else-if="item.status === 'failed'">
                  <span class="status-dot failed"></span> {{ item.error }}
                </template>
                <template v-else-if="item.status === 'cancelled'">
                  <span class="status-dot cancelled"></span> 已取消
                </template>
                <template v-else-if="item.status === 'uploading'">
                  {{ item.progress }}%
                </template>
                <template v-else>
                  等待中
                </template>
              </span>
            </div>

            <!-- Progress bar -->
            <div v-if="item.status === 'uploading'" class="file-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: item.progress + '%' }"></div>
              </div>
            </div>

            <!-- Actions -->
            <button class="file-remove" @click="removeFile(item.id)">
              <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z"/>
              </svg>
            </button>
          </div>
        </TransitionGroup>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uploader {
  min-height: 120px;
}

/* Drop zone */
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.drop-zone:hover,
.drop-zone.dragging {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.05);
}

.drop-icon {
  color: var(--text-tertiary);
  margin-bottom: 12px;
}

.drop-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.drop-hint {
  font-size: 13px;
  color: var(--text-tertiary);
}

/* File panel */
.file-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  gap: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-text {
  font-size: 12px;
  color: var(--accent);
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-actions button {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.header-actions button:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.btn-upload {
  background: var(--accent) !important;
  color: white !important;
  border-color: var(--accent) !important;
}

.btn-upload:hover {
  opacity: 0.9;
}

.btn-cancel {
  color: var(--danger) !important;
  border-color: var(--danger) !important;
}

/* Overall progress */
.overall-progress {
  padding: 0 14px;
}

.progress-track {
  height: 3px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease;
}

/* File list */
.file-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 4px 0;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  transition: background 0.15s;
}

.file-item:hover {
  background: var(--bg-secondary);
}

.file-item.completed {
  opacity: 0.6;
}

/* Thumbnail */
.file-thumb {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-icon {
  color: var(--text-tertiary);
}

/* File info */
.file-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.file-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  font-size: 11px;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.status-dot.completed { background: var(--success); }
.status-dot.duplicate { background: var(--warning, #f0ad4e); }
.status-dot.failed { background: var(--danger); }
.status-dot.cancelled { background: var(--text-tertiary); }

/* File progress */
.file-progress {
  width: 60px;
  flex-shrink: 0;
}

/* Remove button */
.file-remove {
  color: var(--text-tertiary);
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}

.file-item:hover .file-remove {
  opacity: 1;
}

.file-remove:hover {
  color: var(--danger);
}

/* Transitions */
.file-item-enter-active,
.file-item-leave-active {
  transition: all 0.25s ease;
}

.file-item-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}

.file-item-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>

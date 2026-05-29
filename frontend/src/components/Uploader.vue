<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { photoApi } from '@/api/photoApi'

const emit = defineEmits<{
  uploaded: [photo: any]
  done: []
}>()

const message = useMessage()
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const totalFiles = ref(0)
const completedFiles = ref(0)

function triggerSelect() {
  fileInput.value?.click()
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  totalFiles.value = files.length
  completedFiles.value = 0
  uploadProgress.value = 0
  uploading.value = true

  for (let i = 0; i < files.length; i++) {
    try {
      const { data } = await photoApi.upload(files[i])
      completedFiles.value++
      uploadProgress.value = Math.round((completedFiles.value / totalFiles.value) * 100)
      emit('uploaded', data)
    } catch (e: any) {
      message.error(`${files[i].name} 上传失败`)
    }
  }

  message.success(`${completedFiles.value} 张照片上传成功`)
  uploading.value = false
  input.value = ''
  emit('done')
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    // Create a synthetic event-like object
    const input = document.createElement('input')
    input.type = 'file'
    input.files = files
    handleFileChange({ target: input } as any)
  }
}

function handleDragOver(e: DragEvent) {
  e.preventDefault()
}
</script>

<template>
  <div class="uploader" @drop="handleDrop" @dragover="handleDragOver">
    <input
      ref="fileInput"
      type="file"
      accept="image/*,video/*"
      multiple
      style="display: none"
      @change="handleFileChange"
    />

    <div v-if="uploading" class="upload-progress">
      <div class="progress-info">
        <span>正在上传...</span>
        <span>{{ completedFiles }} / {{ totalFiles }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: `${uploadProgress}%` }"></div>
      </div>
    </div>

    <div v-else class="upload-area" @click="triggerSelect">
      <svg viewBox="0 0 24 24" fill="currentColor" width="40" height="40" class="upload-icon">
        <path d="M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z" />
      </svg>
      <p class="upload-text">点击选择照片</p>
      <p class="upload-hint">或拖拽到此处，支持 JPG/PNG/HEIC/MP4</p>
    </div>
  </div>
</template>

<style scoped>
.uploader {
  min-height: 180px;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.upload-area:hover {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.05);
}

.upload-icon {
  color: var(--text-tertiary);
  margin-bottom: 12px;
}

.upload-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.upload-hint {
  font-size: 13px;
  color: var(--text-tertiary);
}

.upload-progress {
  padding: 24px 20px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.progress-bar {
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 3px;
  transition: width 0.3s ease;
}
</style>

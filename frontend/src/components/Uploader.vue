<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { photoApi } from '@/api/photoApi'
import { useTaskStore } from '@/stores/taskStore'

const emit = defineEmits<{
  uploaded: [photo: any]
  done: []
}>()

const message = useMessage()
const taskStore = useTaskStore()
const fileInput = ref<HTMLInputElement | null>(null)

function triggerSelect() {
  fileInput.value?.click()
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  // Create upload tasks and close modal immediately
  const fileArray = Array.from(files)
  const taskIds: string[] = []

  for (const file of fileArray) {
    const id = `upload-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    taskStore.addUploadTask(id, file.name)
    taskIds.push(id)
  }

  // Close the modal right away
  input.value = ''
  emit('done')

  // Upload in background
  for (let i = 0; i < fileArray.length; i++) {
    const id = taskIds[i]
    try {
      taskStore.updateUploadProgress(id, 0)
      const { data } = await photoApi.upload(fileArray[i])
      taskStore.completeUploadTask(id)
      emit('uploaded', data)
    } catch (e: any) {
      taskStore.failUploadTask(id)
      message.error(`${fileArray[i].name} 上传失败`)
    }
  }

  message.success(`${fileArray.length} 张照片上传成功`)
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
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

    <div class="upload-area" @click="triggerSelect">
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
</style>

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TaskProgress } from '@/types'

export interface UploadTask {
  id: string
  type: 'UPLOAD'
  fileName: string
  progress: number
  status: 'PENDING' | 'UPLOADING' | 'COMPLETED' | 'FAILED'
}

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Map<number, TaskProgress>>(new Map())
  const activeTasks = ref<TaskProgress[]>([])
  const uploadTasks = ref<UploadTask[]>([])

  // Combined count for the floating icon
  const activeCount = computed(() => {
    const aiRunning = activeTasks.value.filter(
      (t) => t.status === 'RUNNING' || t.status === 'PENDING'
    ).length
    const uploading = uploadTasks.value.filter(
      (t) => t.status === 'UPLOADING' || t.status === 'PENDING'
    ).length
    return aiRunning + uploading
  })

  const hasActiveTasks = computed(() => activeCount.value > 0)

  // AI task methods (from WebSocket)
  function updateTask(progress: TaskProgress) {
    tasks.value.set(progress.taskId, progress)

    const index = activeTasks.value.findIndex((t) => t.taskId === progress.taskId)
    if (index !== -1) {
      activeTasks.value[index] = progress
    } else {
      activeTasks.value.push(progress)
    }

    if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
      setTimeout(() => {
        activeTasks.value = activeTasks.value.filter((t) => t.taskId !== progress.taskId)
      }, 5000)
    }
  }

  function clearTask(taskId: number) {
    tasks.value.delete(taskId)
    activeTasks.value = activeTasks.value.filter((t) => t.taskId !== taskId)
  }

  // Upload task methods
  function addUploadTask(id: string, fileName: string) {
    uploadTasks.value.push({
      id,
      type: 'UPLOAD',
      fileName,
      progress: 0,
      status: 'PENDING',
    })
  }

  function updateUploadProgress(id: string, progress: number) {
    const task = uploadTasks.value.find((t) => t.id === id)
    if (task) {
      task.progress = progress
      task.status = 'UPLOADING'
    }
  }

  function completeUploadTask(id: string) {
    const task = uploadTasks.value.find((t) => t.id === id)
    if (task) {
      task.progress = 100
      task.status = 'COMPLETED'
    }
    setTimeout(() => {
      uploadTasks.value = uploadTasks.value.filter((t) => t.id !== id)
    }, 3000)
  }

  function failUploadTask(id: string) {
    const task = uploadTasks.value.find((t) => t.id === id)
    if (task) {
      task.status = 'FAILED'
    }
    setTimeout(() => {
      uploadTasks.value = uploadTasks.value.filter((t) => t.id !== id)
    }, 5000)
  }

  return {
    tasks,
    activeTasks,
    uploadTasks,
    activeCount,
    hasActiveTasks,
    updateTask,
    clearTask,
    addUploadTask,
    updateUploadProgress,
    completeUploadTask,
    failUploadTask,
  }
})

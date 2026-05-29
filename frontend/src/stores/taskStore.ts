import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TaskProgress } from '@/types'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Map<number, TaskProgress>>(new Map())
  const activeTasks = ref<TaskProgress[]>([])

  function updateTask(progress: TaskProgress) {
    tasks.value.set(progress.taskId, progress)

    // Update active tasks list
    const index = activeTasks.value.findIndex((t) => t.taskId === progress.taskId)
    if (index !== -1) {
      activeTasks.value[index] = progress
    } else {
      activeTasks.value.push(progress)
    }

    // Remove completed tasks after 5 seconds
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

  return {
    tasks,
    activeTasks,
    updateTask,
    clearTask,
  }
})

<script setup lang="ts">
import { useTaskStore } from '@/stores/taskStore'

const taskStore = useTaskStore()

function getStatusColor(status: string) {
  switch (status) {
    case 'COMPLETED': return 'var(--success)'
    case 'FAILED': return 'var(--danger)'
    case 'RUNNING': return 'var(--accent)'
    default: return 'var(--text-tertiary)'
  }
}
</script>

<template>
  <div class="task-overlay">
    <TransitionGroup name="task-item" tag="div" class="task-list">
      <div
        v-for="task in taskStore.activeTasks"
        :key="task.taskId"
        class="task-bar glass"
      >
        <div class="task-info">
          <span class="task-type">{{ task.type }}</span>
          <span v-if="task.message" class="task-message">{{ task.message }}</span>
        </div>
        <div class="task-progress-track">
          <div
            class="task-progress-fill"
            :style="{ width: `${task.progress}%`, background: getStatusColor(task.status) }"
          ></div>
        </div>
        <button class="task-dismiss" @click="taskStore.clearTask(task.taskId)">
          <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
            <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
          </svg>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.task-overlay {
  position: fixed;
  top: calc(var(--top-bar-height) + 8px);
  left: 16px;
  right: 16px;
  z-index: 200;
  max-width: 480px;
  margin: 0 auto;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 0.5px solid var(--glass-border);
}

.task-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.task-type {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--accent);
  letter-spacing: 0.03em;
}

.task-message {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-progress-track {
  width: 60px;
  height: 4px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
  flex-shrink: 0;
}

.task-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.task-dismiss {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  padding: 4px;
  border-radius: 6px;
  flex-shrink: 0;
  transition: color 0.2s;
}

.task-dismiss:hover {
  color: var(--text-secondary);
}

/* Transitions */
.task-item-enter-active,
.task-item-leave-active {
  transition: all 0.3s ease;
}
.task-item-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
.task-item-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>

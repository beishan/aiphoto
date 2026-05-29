<script setup lang="ts">
import { ref, computed } from 'vue'
import { useTaskStore } from '@/stores/taskStore'

const taskStore = useTaskStore()
const expanded = ref(false)

const allTasks = computed(() => {
  const uploads = taskStore.uploadTasks.map((t) => ({
    id: t.id,
    label: t.fileName,
    progress: t.progress,
    status: t.status,
    type: 'UPLOAD' as const,
  }))
  const ais = taskStore.activeTasks.map((t) => ({
    id: String(t.taskId),
    label: t.message || t.type,
    progress: t.progress,
    status: t.status,
    type: t.type,
  }))
  return [...uploads, ...ais]
})

function toggle() {
  expanded.value = !expanded.value
}

function getStatusColor(status: string) {
  switch (status) {
    case 'COMPLETED': return 'var(--success)'
    case 'FAILED': return 'var(--danger)'
    case 'RUNNING':
    case 'UPLOADING': return 'var(--accent)'
    default: return 'var(--text-tertiary)'
  }
}

function getTypeLabel(type: string) {
  switch (type) {
    case 'UPLOAD': return '上传'
    case 'INDEX': return '索引'
    case 'TRAIN': return '训练'
    default: return type
  }
}
</script>

<template>
  <div class="task-float-wrapper">
    <!-- Floating icon -->
    <button class="task-float-btn" :class="{ active: taskStore.hasActiveTasks }" @click="toggle">
      <!-- Idle: list icon -->
      <svg v-if="!taskStore.hasActiveTasks" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" width="20" height="20">
        <rect x="4" y="3" width="16" height="18" rx="2"/>
        <line x1="8" y1="8" x2="16" y2="8"/>
        <line x1="8" y1="12" x2="16" y2="12"/>
        <line x1="8" y1="16" x2="12" y2="16"/>
      </svg>
      <!-- Active: spinning arc -->
      <svg v-else class="spin-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="20" height="20">
        <path d="M12 2a10 10 0 019.95 9" />
        <path d="M22 12a10 10 0 01-9 9.95" opacity="0.7"/>
        <path d="M12 22a10 10 0 01-9.95-9" opacity="0.4"/>
        <circle cx="12" cy="12" r="3" fill="currentColor" stroke="none"/>
      </svg>
      <span v-if="taskStore.activeCount > 0" class="task-badge">{{ taskStore.activeCount }}</span>
    </button>

    <!-- Expanded panel -->
    <Transition name="slide-down">
      <div v-if="expanded" class="task-panel glass">
        <div class="panel-header">
          <span class="panel-title">任务进度</span>
          <button class="panel-close" @click="expanded = false">
            <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
              <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
            </svg>
          </button>
        </div>

        <div v-if="allTasks.length === 0" class="panel-empty">
          暂无任务
        </div>

        <div v-else class="task-list">
          <div v-for="task in allTasks" :key="task.id" class="task-item">
            <div class="task-item-header">
              <span class="task-type-badge" :style="{ color: getStatusColor(task.status) }">
                {{ getTypeLabel(task.type) }}
              </span>
              <span class="task-label">{{ task.label }}</span>
            </div>
            <div class="task-progress-track">
              <div
                class="task-progress-fill"
                :style="{ width: `${task.progress}%`, background: getStatusColor(task.status) }"
              ></div>
            </div>
            <span class="task-percent">{{ task.progress }}%</span>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.task-float-wrapper {
  position: relative;
}

.task-float-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--text-secondary);
  transition: color 0.2s, background 0.2s;
}

.task-float-btn:hover {
  color: var(--text-primary);
  background: var(--bg-tertiary);
}

.task-float-btn.active {
  color: var(--accent);
}

.spin-icon {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.task-badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--danger);
  color: white;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.task-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 300px;
  max-height: 400px;
  border-radius: var(--radius-lg);
  border: 0.5px solid var(--glass-border);
  overflow: hidden;
  z-index: 300;
  display: flex;
  flex-direction: column;
  background: rgba(28, 28, 30, 0.92);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
}

:root[data-theme="light"] .task-panel {
  background: rgba(255, 255, 255, 0.92);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 0.5px solid var(--glass-border);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
}

.panel-close {
  color: var(--text-tertiary);
  padding: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
}

.task-list {
  overflow-y: auto;
  padding: 8px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-item-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-type-badge {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.task-label {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.task-progress-track {
  height: 4px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
}

.task-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.task-percent {
  font-size: 11px;
  color: var(--text-tertiary);
  text-align: right;
}

/* Transition */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.2s ease;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>

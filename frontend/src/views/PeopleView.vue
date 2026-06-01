<script setup lang="ts">
import { onMounted, onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import { peopleApi } from '@/api/peopleApi'
import type { Person } from '@/types'

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const people = ref<Person[]>([])
const loading = ref(false)
const reclustering = ref(false)
const refreshing = ref(false)

async function fetchPeople() {
  loading.value = true
  try {
    const { data } = await peopleApi.list()
    people.value = data
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await fetchPeople()
  } finally {
    refreshing.value = false
  }
}

onMounted(fetchPeople)

// Refresh when coming back from detail page
onActivated(fetchPeople)

function goToPerson(person: Person) {
  router.push(`/people/${person.id}`)
}

async function handleRecluster() {
  if (reclustering.value) return
  if (!confirm('自动合并相似的人物？')) return
  reclustering.value = true
  try {
    const { data } = await peopleApi.recluster()
    alert(`合并完成，共合并了 ${data.merged} 个人物`)
    // Reload people list
    const res = await peopleApi.list()
    people.value = res.data
  } catch (e) {
    alert('重聚类失败，请重试')
  } finally {
    reclustering.value = false
  }
}

function confirmDelete(e: Event, person: Person) {
  e.stopPropagation()
  dialog.warning({
    title: '删除人物',
    content: `确定要删除"${person.name || '未命名'}"吗？此操作不会删除照片，只会移除此人物标记。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await peopleApi.delete(person.id)
        message.success('已删除')
        people.value = people.value.filter(p => p.id !== person.id)
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}
</script>

<template>
  <div class="people-view">
    <!-- Header -->
    <div class="people-header">
      <div class="header-left">
        <button class="refresh-btn" @click="handleRefresh" :disabled="refreshing" title="刷新">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" :class="{ spinning: refreshing }">
            <path d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
          </svg>
        </button>
      </div>
      <div class="header-right" v-if="people.length > 1">
        <button class="recluster-btn" @click="handleRecluster" :disabled="reclustering">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z" />
          </svg>
          {{ reclustering ? '合并中...' : '自动合并' }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="people.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2a7.2 7.2 0 01-6-3.22c.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08a7.2 7.2 0 01-6 3.22z" />
      </svg>
      <h3>暂无人物</h3>
      <p>上传照片后，AI 会自动识别照片中的人物</p>
    </div>

    <!-- People grid -->
    <div v-else class="people-grid">
      <div
        v-for="person in people"
        :key="person.id"
        class="person-item"
        @click="goToPerson(person)"
      >
        <div class="person-avatar-wrapper">
          <div class="person-avatar">
            <img v-if="person.coverPhotoUrl" :src="person.coverPhotoUrl" alt="" />
            <span v-else class="avatar-fallback">{{ person.name?.[0] || '?' }}</span>
          </div>
          <button class="person-delete-btn" @click="confirmDelete($event, person)" title="删除">
            <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
            </svg>
          </button>
        </div>
        <span class="person-name">{{ person.name || '未命名' }}</span>
        <span class="person-count">{{ person.photoCount }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.people-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 16px;
}

.people-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left, .header-right {
  display: flex;
  gap: 8px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--glass-border);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.refresh-btn svg.spinning {
  animation: spin 1s linear infinite;
}

.recluster-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.recluster-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.recluster-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 8px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); }
.empty-state p { font-size: 14px; }

.people-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px 16px;
}

@media (min-width: 480px) {
  .people-grid { grid-template-columns: repeat(4, 1fr); }
}

@media (min-width: 768px) {
  .people-grid { grid-template-columns: repeat(5, 1fr); }
}

@media (min-width: 1024px) {
  .people-grid { grid-template-columns: repeat(6, 1fr); }
}

.person-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.person-avatar-wrapper {
  position: relative;
}

.person-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}

.person-delete-btn {
  position: absolute;
  top: 0;
  right: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.person-item:hover .person-delete-btn {
  opacity: 1;
}

.person-delete-btn:hover {
  background: #e74c3c;
  transform: scale(1.1);
}

.person-item:active .person-avatar {
  transform: scale(0.95);
}

.person-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.person-name {
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.person-count {
  font-size: 12px;
  color: var(--text-secondary);
}

@media (max-width: 480px) {
  .person-avatar {
    width: 64px;
    height: 64px;
  }

  .person-delete-btn {
    width: 20px;
    height: 20px;
  }

  .person-delete-btn svg {
    width: 12px;
    height: 12px;
  }

  .avatar-fallback {
    font-size: 22px;
  }
}
</style>

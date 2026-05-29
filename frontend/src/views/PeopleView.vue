<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { peopleApi } from '@/api/peopleApi'
import type { Person } from '@/types'

const people = ref<Person[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await peopleApi.list()
    people.value = data
  } finally {
    loading.value = false
  }
})

function goToPerson(person: Person) {
  // TODO: navigate to person detail
}
</script>

<template>
  <div class="people-view">
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
        <div class="person-avatar">
          <img v-if="person.coverPhotoUrl" :src="person.coverPhotoUrl" alt="" />
          <span v-else class="avatar-fallback">{{ person.name?.[0] || '?' }}</span>
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

  .avatar-fallback {
    font-size: 22px;
  }
}
</style>

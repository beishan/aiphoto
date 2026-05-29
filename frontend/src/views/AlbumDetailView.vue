<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { albumApi } from '@/api/albumApi'
import type { Album, Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const album = ref<Album | null>(null)
const photos = ref<Photo[]>([])
const loading = ref(false)
const viewerVisible = ref(false)
const viewerIndex = ref(0)

const albumId = Number(route.params.id)

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await albumApi.get(albumId)
    album.value = data
  } finally {
    loading.value = false
  }
})

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

async function handleTrain() {
  try {
    await albumApi.train(albumId)
    message.success('训练任务已提交')
  } catch (e) {
    message.error('训练失败')
  }
}
</script>

<template>
  <div class="album-detail">
    <!-- Header -->
    <div class="detail-header">
      <button class="back-btn" @click="router.push('/albums')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <div class="header-info">
        <h2>{{ album?.name || '加载中...' }}</h2>
        <span class="photo-count">{{ album?.photoCount || 0 }} 张照片</span>
      </div>
      <button v-if="album?.type === 'TRAINING'" class="train-btn" @click="handleTrain">
        训练
      </button>
    </div>

    <!-- Content -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="photos.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z" />
      </svg>
      <p>相册中暂无照片</p>
    </div>

    <div v-else class="photo-grid-compact">
      <PhotoCard
        v-for="(photo, index) in photos"
        :key="photo.id"
        :photo="photo"
        @click="openViewer(index)"
      />
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="photos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.album-detail {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.detail-header {
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

.header-info {
  flex: 1;
}

.header-info h2 {
  font-size: 17px;
  font-weight: 600;
}

.photo-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.train-btn {
  padding: 8px 20px;
  border-radius: var(--radius-full);
  background: var(--accent);
  color: white;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
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
  min-height: 40vh;
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }
</style>

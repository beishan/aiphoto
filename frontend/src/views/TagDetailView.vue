<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { tagApi } from '@/api/tagApi'
import type { Tag, Photo } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const tagId = Number(route.params.id)

const tag = ref<Tag | null>(null)
const photos = ref<Photo[]>([])
const loading = ref(true)
const sortBy = ref<'date' | 'rating'>('date')

// Viewer
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

onMounted(async () => {
  try {
    const { data: tags } = await tagApi.list()
    tag.value = tags.find(t => t.id === tagId) || null
    const { data: photosData } = await tagApi.getPhotos(tagId, 0, 500)
    photos.value = photosData
  } finally {
    loading.value = false
  }
})

function openViewer(index: number) {
  viewerPhotos.value = sortedPhotos.value
  viewerIndex.value = index
  viewerVisible.value = true
}

const sortedPhotos = ref<Photo[]>([])
function updateSort() {
  const photosCopy = [...photos.value]
  if (sortBy.value === 'rating') {
    photosCopy.sort((a, b) => (b.rating || 0) - (a.rating || 0))
  } else {
    photosCopy.sort((a, b) => {
      const da = a.exifDate || a.createdAt
      const db = b.exifDate || b.createdAt
      return db.localeCompare(da)
    })
  }
  sortedPhotos.value = photosCopy
}

// Watch for photos change
import { watch } from 'vue'
watch(photos, updateSort, { immediate: true })
watch(sortBy, updateSort)
</script>

<template>
  <div class="tag-detail-view">
    <div class="detail-header">
      <button class="back-btn" @click="router.push('/tags')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <div class="header-info">
        <div class="tag-color-dot" v-if="tag" :style="{ background: tag.color || '#0a84ff' }"></div>
        <h1 class="tag-title">{{ tag?.name || '标签' }}</h1>
        <span class="tag-count">{{ photos.length }} 张照片</span>
      </div>
      <el-select v-model="sortBy" class="sort-select">
        <el-option value="date" label="按时间" />
        <el-option value="rating" label="按评分" />
      </el-select>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="sortedPhotos.length === 0" class="empty-state">
      <p>此标签下暂无照片</p>
    </div>

    <div v-else class="photo-grid-compact">
      <div
        v-for="(photo, index) in sortedPhotos"
        :key="photo.id"
        class="grid-item"
        @click="openViewer(index)"
      >
        <img
          v-if="photo.thumbnailUrl"
          :src="photo.thumbnailUrl"
          :alt="photo.originalFilename || ''"
          loading="lazy"
          class="grid-img"
        />
        <div v-else class="grid-placeholder">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
          </svg>
        </div>
        <div v-if="photo.rating" class="rating-badge">
          {{ '★'.repeat(photo.rating) }}
        </div>
      </div>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="viewerPhotos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.tag-detail-view {
  min-height: calc(100vh - var(--top-bar-height));
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.tag-title {
  font-size: 20px;
  font-weight: 600;
}

.tag-count {
  font-size: 14px;
  color: var(--text-secondary);
}

.sort-select {
  width: 130px;
}

.photo-grid-compact {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 2px;
  padding: 0 16px 20px;
}

.grid-item {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 4px;
  cursor: pointer;
  background: var(--bg-tertiary);
}

.grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}

.grid-item:hover .grid-img {
  transform: scale(1.05);
}

.grid-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.rating-badge {
  position: absolute;
  bottom: 4px;
  right: 4px;
  font-size: 10px;
  color: #ffcc00;
  text-shadow: 0 1px 3px rgba(0,0,0,0.8);
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

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  color: var(--text-secondary);
}
</style>

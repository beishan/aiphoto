<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { albumApi } from '@/api/albumApi'
import type { Album, Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import http from '@/api/http'

const router = useRouter()
const albums = ref<Album[]>([])
const selectedAlbum = ref<number | null>(null)
const timeline = ref<Record<string, Photo[]>>({})
const loading = ref(false)
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

onMounted(async () => {
  const { data } = await albumApi.list()
  albums.value = data.filter((a) => a.type === 'BABY')
  if (albums.value.length > 0) {
    selectedAlbum.value = albums.value[0].id
    loadBabyAlbum()
  }
})

async function loadBabyAlbum() {
  if (!selectedAlbum.value) return
  loading.value = true
  try {
    const { data } = await http.get(`/albums/${selectedAlbum.value}/baby-timeline`)
    timeline.value = data
  } finally {
    loading.value = false
  }
}

function openViewer(photos: Photo[], index: number) {
  viewerPhotos.value = photos
  viewerIndex.value = index
  viewerVisible.value = true
}
</script>

<template>
  <div class="baby-view">
    <!-- Back to albums -->
    <div class="baby-header">
      <button class="back-btn" @click="router.push('/albums')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <h2>宝宝相册</h2>
      <select
        v-if="albums.length > 0"
        v-model="selectedAlbum"
        class="album-select"
        @change="loadBabyAlbum"
      >
        <option v-for="a in albums" :key="a.id" :value="a.id">{{ a.name }}</option>
      </select>
    </div>

    <!-- Empty -->
    <div v-if="albums.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a2.5 2.5 0 110 5 2.5 2.5 0 010-5zm0 13.2c-2.03 0-3.8-.81-5.11-2.12.03-1.99 4-3.08 6.11-3.08 2.03 0 5.97 1.09 6 3.08A7.96 7.96 0 0112 18.2z" />
      </svg>
      <h3>暂无宝宝相册</h3>
      <p>请先创建一个宝宝类型的相册</p>
      <button class="create-btn" @click="router.push('/albums')">去创建</button>
    </div>

    <!-- Timeline -->
    <div v-else-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else class="baby-content">
      <div v-for="(photos, age) in timeline" :key="age" class="age-section">
        <div class="age-label">{{ age }}</div>
        <div class="photo-grid-compact">
          <PhotoCard
            v-for="(photo, index) in photos"
            :key="photo.id"
            :photo="photo"
            @click="openViewer(photos, index)"
          />
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
.baby-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.baby-header {
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

.baby-header h2 {
  font-size: 17px;
  font-weight: 600;
  flex: 1;
}

.album-select {
  padding: 6px 12px;
  background: var(--bg-tertiary);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  outline: none;
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
  padding: 20px;
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); }
.empty-state p { font-size: 14px; }

.create-btn {
  margin-top: 12px;
  padding: 10px 24px;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-full);
  font-size: 15px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.age-section {
  margin-bottom: 24px;
}

.age-label {
  position: sticky;
  top: var(--top-bar-height);
  z-index: 10;
  padding: 10px 16px 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: var(--bg-primary);
  color: var(--text-primary);
}
</style>

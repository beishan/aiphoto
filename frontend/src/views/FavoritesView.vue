<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import type { Photo } from '@/types'
import { photoApi } from '@/api/photoApi'
import { usePhotoStore } from '@/stores/photoStore'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const photoStore = usePhotoStore()
const photos = ref<Photo[]>([])
const page = ref(0)
const pageSize = 40
const totalElements = ref(0)
const loading = ref(false)
const loadingMore = ref(false)

const viewerVisible = ref(false)
const viewerIndex = ref(0)

// Zoom level: 0=10col, 1=8col, 2=6col, 3=4col(default), 4=3col, 5=2col
const zoomLevel = ref(0)
const zoomColumns = [6, 10, 16, 20, 30]

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${zoomColumns[zoomLevel.value]}, 1fr)`,
}))

function initZoom() {
  zoomLevel.value = 2      // Default: 16 columns (third level)
}

// Timeline
interface TimelineItem {
  label: string
  year: number
  month: number
  startIndex: number
  count: number
}

const timelineItems = computed<TimelineItem[]>(() => {
  if (photos.value.length === 0) return []

  const groups: TimelineItem[] = []
  let currentKey = ''
  let startIndex = 0

  photos.value.forEach((photo, i) => {
    const dateStr = photo.exifDate || photo.createdAt
    const d = new Date(dateStr)
    const key = `${d.getFullYear()}-${d.getMonth()}`
    if (key !== currentKey) {
      if (currentKey !== '') {
        const prev = currentKey.split('-')
        groups.push({
          label: `${prev[0]}年${Number(prev[1]) + 1}月`,
          year: Number(prev[0]),
          month: Number(prev[1]),
          startIndex,
          count: i - startIndex,
        })
      }
      currentKey = key
      startIndex = i
    }
  })

  if (currentKey !== '') {
    const parts = currentKey.split('-')
    groups.push({
      label: `${parts[0]}年${Number(parts[1]) + 1}月`,
      year: Number(parts[0]),
      month: Number(parts[1]),
      startIndex,
      count: photos.value.length - startIndex,
    })
  }

  return groups
})

const scrollContainer = ref<HTMLElement | null>(null)

onMounted(async () => {
  initZoom()
  await fetchFavorites()
})

async function fetchFavorites() {
  loading.value = true
  try {
    const { data } = await photoApi.favorites(0, pageSize)
    photos.value = data.content
    totalElements.value = data.totalElements
    page.value = 0
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value) return
  if (photos.value.length >= totalElements.value && totalElements.value > 0) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const prevLength = photos.value.length
    const { data } = await photoApi.favorites(nextPage, pageSize)
    photos.value = [...photos.value, ...data.content]
    totalElements.value = data.totalElements
    // Only increment page if we actually loaded more data
    if (photos.value.length > prevLength) {
      page.value = nextPage
    } else {
      totalElements.value = photos.value.length
    }
  } finally {
    loadingMore.value = false
  }
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 300) {
    loadMore()
  }
}

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

async function handleToggleFavorite(photoId: number) {
  await photoStore.toggleFavorite(photoId)
  // Remove from favorites list since it was unfavorited
  photos.value = photos.value.filter(p => p.id !== photoId)
}
</script>

<template>
  <div class="gallery-wrapper">
    <div ref="scrollContainer" class="gallery-scroll" @scroll="handleScroll">
      <!-- Empty state -->
      <div v-if="!loading && photos.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" class="empty-icon">
          <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z" />
        </svg>
        <h3>还没有喜欢的照片</h3>
        <p>点击照片上的爱心按钮收藏</p>
      </div>

      <!-- Photo grid grouped by month -->
      <template v-else>
        <div
          v-for="item in timelineItems"
          :key="`${item.year}-${item.month}`"
          class="month-section"
          :data-month="`${item.year}-${item.month}`"
        >
          <div class="month-label">{{ item.label }}</div>
          <div class="photo-grid-compact" :style="gridStyle">
            <PhotoCard
              v-for="i in item.count"
              :key="photos[item.startIndex + i - 1].id"
              :photo="photos[item.startIndex + i - 1]"
              @click="openViewer(item.startIndex + i - 1)"
              @toggle-favorite="handleToggleFavorite(photos[item.startIndex + i - 1].id)"
            />
          </div>
        </div>
      </template>

      <!-- Loading indicator -->
      <div v-if="loading || loadingMore" class="loading-indicator">
        <div class="loading-spinner"></div>
      </div>
    </div>

    <!-- Timeline rail -->
    <div v-if="timelineItems.length > 0" class="timeline-rail">
      <el-button
        v-for="item in timelineItems"
        :key="`${item.year}-${item.month}`"
        class="timeline-item"
        :style="{ flex: item.count }"
        :title="item.label"
      >
        <span class="timeline-dot"></span>
        <span class="timeline-label">{{ item.label }}</span>
      </el-button>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="photos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.gallery-wrapper {
  position: relative;
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.gallery-scroll {
  height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  overflow-y: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon {
  color: var(--text-tertiary);
  margin-bottom: 8px;
}

.empty-state h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-state p {
  font-size: 15px;
}

/* Month sections */
.month-section {
  margin-bottom: 8px;
}

.month-label {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
}

/* Timeline rail */
.timeline-rail {
  position: fixed;
  right: 4px;
  top: calc(var(--top-bar-height) + 8px);
  bottom: calc(var(--tab-height) + 8px);
  width: 36px;
  display: flex;
  flex-direction: column;
  z-index: 20;
  pointer-events: none;
}

.timeline-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 0;
  pointer-events: none;
  border: none;
  background: none;
  color: var(--text-tertiary);
  font-size: 9px;
  font-weight: 500;
  white-space: nowrap;
}

.timeline-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  flex-shrink: 0;
}

.timeline-label {
  display: none;
}

.timeline-item:hover .timeline-label {
  display: inline;
}

/* Loading */
.loading-indicator {
  display: flex;
  justify-content: center;
  padding: 24px;
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
</style>

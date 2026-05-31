<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { categoryApi } from '@/api/categoryApi'
import { useCategoryStore } from '@/stores/categoryStore'
import { usePhotoStore } from '@/stores/photoStore'
import type { Category, Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const categoryStore = useCategoryStore()
const photoStore = usePhotoStore()

const categoryId = Number(route.params.id)
const category = ref<Category | null>(null)
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
  const w = window.innerWidth
  if (w < 480) {
    zoomLevel.value = 2      // 16 col
  } else if (w < 768) {
    zoomLevel.value = 1      // 10 col
  } else if (w < 1200) {
    zoomLevel.value = 0      // 6 col
  } else {
    zoomLevel.value = 0      // 6 col
  }
}

onMounted(async () => {
  initZoom()
  await fetchCategory()
  await fetchPhotos()
})

async function fetchCategory() {
  try {
    const { data } = await categoryApi.get(categoryId)
    category.value = data
  } catch (e) {
    message.error('分类不存在')
    router.back()
  }
}

async function fetchPhotos() {
  loading.value = true
  try {
    const { data } = await categoryApi.getPhotos(categoryId, 0, pageSize)
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
    const { data } = await categoryApi.getPhotos(categoryId, nextPage, pageSize)
    photos.value = [...photos.value, ...data.content]
    totalElements.value = data.totalElements
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
  photos.value = photos.value.filter(p => p.id !== photoId)
  totalElements.value = photos.value.length
}

function goBack() {
  router.push('/categories')
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
</script>

<template>
  <div class="detail-view">
    <!-- Header with back button -->
    <div class="detail-header glass">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12l4.58-4.59z" />
        </svg>
      </button>
      <div class="header-info">
        <h2>{{ category?.name || '分类' }}</h2>
        <span class="header-count">{{ totalElements }} 张照片</span>
      </div>
    </div>

    <div class="detail-scroll" @scroll="handleScroll">
      <!-- Empty state -->
      <div v-if="!loading && photos.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
          <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
        </svg>
        <h3>暂无照片</h3>
        <p v-if="category && !category.trained">请先选择模板照片训练此分类</p>
        <p v-else>AI 还没有找到匹配的照片</p>
      </div>

      <!-- Photo grid -->
      <template v-else>
        <div
          v-for="item in timelineItems"
          :key="`${item.year}-${item.month}`"
          class="month-section"
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
      <button
        v-for="item in timelineItems"
        :key="`${item.year}-${item.month}`"
        class="timeline-item"
        :style="{ flex: item.count }"
        :title="item.label"
      >
        <span class="timeline-dot"></span>
        <span class="timeline-label">{{ item.label }}</span>
      </button>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="photos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.detail-view {
  position: relative;
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--glass-border);
  position: sticky;
  top: var(--top-bar-height);
  z-index: 10;
  background: var(--bg-primary);
  backdrop-filter: blur(20px);
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--accent);
  border: none;
  background: none;
  cursor: pointer;
  flex-shrink: 0;
}

.back-btn:active {
  background: var(--bg-tertiary);
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.header-info h2 {
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-scroll {
  height: calc(100vh - var(--top-bar-height) - var(--tab-height) - 56px);
  overflow-y: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
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
  top: calc(var(--top-bar-height) + 64px);
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

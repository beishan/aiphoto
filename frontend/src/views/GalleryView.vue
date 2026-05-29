<script setup lang="ts">
import { onMounted, ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { usePhotoStore } from '@/stores/photoStore'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import Uploader from '@/components/Uploader.vue'

const photoStore = usePhotoStore()
const page = ref(0)
const pageSize = 40
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const showUploader = ref(false)
const loadingMore = ref(false)

// Zoom level: 0=6col, 1=4col(default), 2=3col, 3=2col
const zoomLevel = ref(1)
const zoomColumns = [6, 4, 3, 2]

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${zoomColumns[zoomLevel.value]}, 1fr)`,
}))

function zoomIn() {
  if (zoomLevel.value > 0) zoomLevel.value--
}

function zoomOut() {
  if (zoomLevel.value < 3) zoomLevel.value++
}

// Set initial zoom based on viewport width
function initZoom() {
  const w = window.innerWidth
  if (w < 480) {
    zoomLevel.value = 3      // 2 col
  } else if (w < 768) {
    zoomLevel.value = 2      // 3 col
  } else if (w < 1200) {
    zoomLevel.value = 1      // 4 col
  } else {
    zoomLevel.value = 0      // 6 col
  }
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
  const photos = photoStore.photos
  if (photos.length === 0) return []

  const groups: TimelineItem[] = []
  let currentKey = ''
  let startIndex = 0

  photos.forEach((photo, i) => {
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

  // Push last group
  if (currentKey !== '') {
    const parts = currentKey.split('-')
    groups.push({
      label: `${parts[0]}年${Number(parts[1]) + 1}月`,
      year: Number(parts[0]),
      month: Number(parts[1]),
      startIndex,
      count: photos.length - startIndex,
    })
  }

  return groups
})

const activeMonthKey = ref('')
const scrollContainer = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

function setupObserver() {
  if (observer) observer.disconnect()
  const container = scrollContainer.value
  if (!container) return

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeMonthKey.value = (entry.target as HTMLElement).dataset.month || ''
        }
      }
    },
    { root: container, threshold: 0.3 }
  )

  container.querySelectorAll('[data-month]').forEach((el) => {
    observer!.observe(el)
  })
}

watch(
  () => photoStore.photos.length,
  () => nextTick(setupObserver)
)

onMounted(() => {
  photoStore.fetchPhotos(0, pageSize)
  nextTick(setupObserver)
  initZoom()
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

function scrollToMonth(item: TimelineItem) {
  const el = scrollContainer.value?.querySelector(`[data-month="${item.year}-${item.month}"]`)
  el?.scrollIntoView({ behavior: 'smooth' })
}

async function loadMore() {
  if (loadingMore.value || photoStore.photos.length >= photoStore.totalElements) return
  loadingMore.value = true
  page.value++
  try {
    await photoStore.loadMore(page.value, pageSize)
  } finally {
    loadingMore.value = false
  }
}

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

function handleUploaded() {
  photoStore.fetchPhotos(0, pageSize)
  page.value = 0
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 300) {
    loadMore()
  }
}
</script>

<template>
  <div class="gallery-wrapper">
    <div ref="scrollContainer" class="gallery-scroll" @scroll="handleScroll">
      <!-- Empty state -->
      <div v-if="!photoStore.loading && photoStore.photos.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" class="empty-icon">
          <path d="M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z" />
        </svg>
        <h3>还没有照片</h3>
        <p>点击下方按钮开始上传</p>
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
              :key="photoStore.photos[item.startIndex + i - 1].id"
              :photo="photoStore.photos[item.startIndex + i - 1]"
              @click="openViewer(item.startIndex + i - 1)"
            />
          </div>
        </div>
      </template>

      <!-- Loading indicator -->
      <div v-if="photoStore.loading || loadingMore" class="loading-indicator">
        <div class="loading-spinner"></div>
      </div>
    </div>

    <!-- Timeline rail -->
    <div v-if="timelineItems.length > 0" class="timeline-rail">
      <button
        v-for="item in timelineItems"
        :key="`${item.year}-${item.month}`"
        class="timeline-item"
        :class="{ active: activeMonthKey === `${item.year}-${item.month}` }"
        :style="{ flex: item.count }"
        @click="scrollToMonth(item)"
        :title="item.label"
      >
        <span class="timeline-dot"></span>
        <span class="timeline-label">{{ item.label }}</span>
      </button>
    </div>

    <!-- Zoom controls -->
    <div class="zoom-controls">
      <button class="zoom-btn" @click="zoomIn" :disabled="zoomLevel === 0" title="放大">
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M12 4a1 1 0 011 1v6h6a1 1 0 110 2h-6v6a1 1 0 11-2 0v-6H5a1 1 0 110-2h6V5a1 1 0 011-1z" />
        </svg>
      </button>
      <button class="zoom-btn" @click="zoomOut" :disabled="zoomLevel === 3" title="缩小">
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M5 12a1 1 0 011-1h12a1 1 0 110 2H6a1 1 0 01-1-1z" />
        </svg>
      </button>
    </div>

    <!-- Upload FAB -->
    <button class="fab-upload" @click="showUploader = true">
      <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
        <path d="M12 4v16m8-8H4" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
    </button>

    <!-- Uploader modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showUploader" class="uploader-overlay" @click.self="showUploader = false">
          <div class="uploader-sheet glass">
            <div class="sheet-header">
              <h3>上传照片</h3>
              <button @click="showUploader = false" class="sheet-close">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
                </svg>
              </button>
            </div>
            <Uploader @uploaded="handleUploaded" @done="showUploader = false" />
          </div>
        </div>
      </Transition>
    </Teleport>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="photoStore.photos"
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
  cursor: pointer;
  pointer-events: auto;
  border: none;
  background: none;
  color: var(--text-tertiary);
  font-size: 9px;
  font-weight: 500;
  white-space: nowrap;
  transition: color 0.2s;
}

.timeline-item:hover,
.timeline-item.active {
  color: var(--accent);
}

.timeline-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  flex-shrink: 0;
  transition: background 0.2s, transform 0.2s;
}

.timeline-item.active .timeline-dot {
  background: var(--accent);
  transform: scale(1.5);
}

.timeline-label {
  display: none;
}

.timeline-item:hover .timeline-label,
.timeline-item.active .timeline-label {
  display: inline;
}

/* Zoom controls */
.zoom-controls {
  position: fixed;
  bottom: calc(var(--tab-height) + 88px);
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  z-index: 50;
}

.zoom-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0.5px solid var(--glass-border);
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}

.zoom-btn:hover {
  background: var(--bg-tertiary);
}

.zoom-btn:disabled {
  opacity: 0.3;
  cursor: default;
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

/* FAB */
.fab-upload {
  position: fixed;
  bottom: calc(var(--tab-height) + 20px);
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(10, 132, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 50;
  border: none;
  cursor: pointer;
}

.fab-upload:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 24px rgba(10, 132, 255, 0.5);
}

.fab-upload:active {
  transform: scale(0.95);
}

/* Uploader modal */
.uploader-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.uploader-sheet {
  width: 100%;
  max-width: 480px;
  max-height: 80vh;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding: 20px;
  overflow-y: auto;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sheet-header h3 {
  font-size: 17px;
  font-weight: 600;
}

.sheet-close {
  color: var(--text-secondary);
  padding: 4px;
}

@media (max-width: 768px) {
  .fab-upload {
    bottom: calc(var(--tab-height) + 16px);
    right: 16px;
  }
  .zoom-controls {
    right: 16px;
    bottom: calc(var(--tab-height) + 84px);
  }
}
</style>

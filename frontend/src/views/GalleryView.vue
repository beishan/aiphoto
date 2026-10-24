<script setup lang="ts">
import { onMounted, ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import { usePhotoStore } from '@/stores/photoStore'
import { photoApi } from '@/api/photoApi'
import { albumApi } from '@/api/albumApi'
import type { Album } from '@/types'
import { useMessage, useDialog } from '@/utils/feedback'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import Uploader from '@/components/Uploader.vue'

const photoStore = usePhotoStore()
const message = useMessage()
const dialog = useDialog()
const page = ref(0)
const pageSize = 40
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const showUploader = ref(false)
const loadingMore = ref(false)

// Selection mode
const selectMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())

// Zoom level: 0=10col, 1=8col, 2=6col, 3=4col(default), 4=3col, 5=2col
const zoomLevel = ref(0)
const zoomColumns = [6, 10, 16, 20, 30]

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${zoomColumns[zoomLevel.value]}, 1fr)`,
}))

function zoomIn() {
  if (zoomLevel.value > 0) zoomLevel.value--
}

function zoomOut() {
  if (zoomLevel.value < 4) zoomLevel.value++
}

// Set initial zoom based on viewport width
function initZoom() {
  zoomLevel.value = 2      // Default: 16 columns (third level)
}

// Selection functions
function toggleSelectMode() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) {
    selectedIds.value.clear()
  }
}

function toggleSelect(photoId: number) {
  if (selectedIds.value.has(photoId)) {
    selectedIds.value.delete(photoId)
  } else {
    selectedIds.value.add(photoId)
  }
  // Force reactivity
  selectedIds.value = new Set(selectedIds.value)
}

function selectAll() {
  photoStore.photos.forEach(p => selectedIds.value.add(p.id))
  selectedIds.value = new Set(selectedIds.value)
}

function deselectAll() {
  selectedIds.value.clear()
  selectedIds.value = new Set(selectedIds.value)
}

async function batchDelete() {
  if (selectedIds.value.size === 0) return

  dialog.warning({
    title: '批量删除',
    content: `确定将选中的 ${selectedIds.value.size} 张照片移入回收站吗？可在 Dock 回收站中恢复。`,
    positiveText: '移入回收站',
    negativeText: '取消',
    onPositiveClick: async () => {
      const ids = Array.from(selectedIds.value)

      try {
        const { data } = await photoApi.batchDelete(ids)

        // Remove deleted photos from store
        photoStore.photos = photoStore.photos.filter(p => !selectedIds.value.has(p.id))
        selectedIds.value.clear()
        selectedIds.value = new Set(selectedIds.value)
        selectMode.value = false

        if (data.fail === 0) {
          message.success(`已将 ${data.success} 张照片移入回收站`)
        } else {
          message.warning(`删除完成：成功 ${data.success} 张，失败 ${data.fail} 张`)
        }
      } catch (e) {
        message.error('批量删除失败')
      }
    },
  })
}

async function batchToggleFavorite() {
  if (selectedIds.value.size === 0) return
  const ids = Array.from(selectedIds.value)

  // Determine if we should favorite or unfavorite
  // If any selected photo is not favorited, favorite all; otherwise unfavorite all
  const hasNonFavorite = ids.some(id => {
    const photo = photoStore.photos.find(p => p.id === id)
    return photo && !photo.favorite
  })
  const favorite = hasNonFavorite

  try {
    await photoApi.batchFavorite(ids, favorite)
    // Update local state
    ids.forEach(id => {
      const photo = photoStore.photos.find(p => p.id === id)
      if (photo) photo.favorite = favorite
    })
    message.success(favorite ? `已收藏 ${ids.length} 张照片` : `已取消收藏 ${ids.length} 张照片`)
  } catch (e) {
    message.error('操作失败')
  }
}

// Album picker
const showAlbumPicker = ref(false)
const albums = ref<Album[]>([])

async function openAlbumPicker() {
  if (selectedIds.value.size === 0) return
  const { data } = await albumApi.list()
  albums.value = data.filter(a => a.type !== 'TRAINING')
  showAlbumPicker.value = true
}

async function addToAlbum(albumId: number) {
  if (selectedIds.value.size === 0) return
  const ids = Array.from(selectedIds.value)

  try {
    const { data } = await albumApi.batchAddPhotos(albumId, ids)
    showAlbumPicker.value = false
    const album = albums.value.find(a => a.id === albumId)
    message.success(`已将 ${data.success} 张照片添加到「${album?.name || '相册'}」`)
  } catch (e) {
    message.error('添加到相册失败')
  }
}

// Rating picker
const showRatingPicker = ref(false)

async function setBatchRating(rating: number) {
  if (selectedIds.value.size === 0) return
  const ids = Array.from(selectedIds.value)

  try {
    await photoApi.batchRating(ids, rating)
    // Update local state
    ids.forEach(id => {
      const photo = photoStore.photos.find(p => p.id === id)
      if (photo) photo.rating = rating
    })
    showRatingPicker.value = false
    message.success(`已将 ${ids.length} 张照片评分设为 ${rating} 星`)
  } catch (e) {
    message.error('设置评分失败')
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

let galleryActive = false
let fillingViewport = false

async function fillViewport() {
  if (fillingViewport || !galleryActive || photoStore.loading || loadingMore.value) return
  fillingViewport = true
  try {
    await nextTick()
    const container = scrollContainer.value
    while (galleryActive && container && container.clientHeight > 0
      && container.scrollHeight - container.scrollTop - container.clientHeight < 300) {
      if (!await loadMore()) break
      await nextTick()
    }
  } finally { fillingViewport = false }
}

async function refreshGallery() {
  page.value = 0
  try {
    await photoStore.fetchPhotos(0, pageSize)
    await fillViewport()
  } catch { message.error('照片加载失败，请刷新后重试') }
}

watch(zoomLevel, () => { void fillViewport() })
useResizeObserver(scrollContainer, () => { void fillViewport() })
onMounted(() => {
  galleryActive = true
  initZoom()
  void refreshGallery()
  nextTick(setupObserver)
  window.addEventListener('photos-changed', refreshGallery)
})

onBeforeUnmount(() => {
  galleryActive = false
  window.removeEventListener('photos-changed', refreshGallery)
  if (observer) observer.disconnect()
})

function scrollToMonth(item: TimelineItem) {
  const el = scrollContainer.value?.querySelector(`[data-month="${item.year}-${item.month}"]`)
  el?.scrollIntoView({ behavior: 'smooth' })
}

async function loadMore(): Promise<boolean> {
  if (!galleryActive || photoStore.loading || loadingMore.value) return false
  if (page.value >= photoStore.totalPages - 1) return false
  if (photoStore.photos.length >= photoStore.totalElements) return false
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const added = await photoStore.loadMore(nextPage, pageSize)
    if (added) page.value = nextPage
    return added
  } catch {
    message.error('更多照片加载失败，请再次滚动重试')
    return false
  } finally { loadingMore.value = false }
}

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

async function handleUploaded() {
  showUploader.value = false
  await refreshGallery()
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 300) {
    void loadMore().then(() => fillViewport())
  }
}

async function handleToggleFavorite(photoId: number) {
  await photoStore.toggleFavorite(photoId)
}
</script>

<template>
  <div class="gallery-wrapper">
    <div ref="scrollContainer" class="gallery-scroll" @scroll="handleScroll">
      <!-- Selection toolbar -->
      <div v-if="selectMode" class="selection-toolbar">
        <el-button class="toolbar-btn" circle text aria-label="退出选择" @click="toggleSelectMode">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
        </el-button>
        <span class="toolbar-title">已选择 {{ selectedIds.size }} 项</span>
        <div class="toolbar-actions">
          <el-button class="toolbar-btn text-btn" text @click="selectAll">全选</el-button>
          <el-button class="toolbar-btn text-btn" text @click="deselectAll">取消全选</el-button>
          <el-button class="toolbar-btn action-btn" circle text @click="batchToggleFavorite" :disabled="selectedIds.size === 0" title="收藏">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
            </svg>
          </el-button>
          <div class="picker-wrapper">
            <el-button class="toolbar-btn action-btn" circle text @click="openAlbumPicker" :disabled="selectedIds.size === 0" title="添加到相册">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z"/>
              </svg>
            </el-button>
            <el-dialog v-model="showAlbumPicker" append-to-body title="选择相册" width="min(460px, 92vw)">
              <el-space v-if="albums.length" direction="vertical" fill class="album-picker-list">
                <el-button v-for="album in albums" :key="album.id" class="album-picker-option" @click="addToAlbum(album.id)">
                  {{ album.name }}（{{ album.photoCount }} 张）
                </el-button>
              </el-space>
              <el-empty v-else description="暂无相册" :image-size="64" />
            </el-dialog>
          </div>
          <div class="picker-wrapper">
            <el-button class="toolbar-btn action-btn" circle text @click="showRatingPicker = !showRatingPicker" :disabled="selectedIds.size === 0" title="评分">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
              </svg>
            </el-button>
            <el-dialog v-model="showRatingPicker" append-to-body title="设置批量评分" width="min(360px, 92vw)">
              <el-space direction="vertical" fill class="album-picker-list">
                <el-button v-for="rating in [5, 4, 3, 2, 1]" :key="rating" :aria-label="`评分设为 ${rating} 星`" @click="setBatchRating(rating)">
                  <el-rate :model-value="rating" disabled /><span>{{ rating }} 星</span>
                </el-button>
                <el-button @click="setBatchRating(0)">清除评分</el-button>
              </el-space>
            </el-dialog>
          </div>
          <el-button class="toolbar-btn delete-btn" circle text type="danger" aria-label="删除所选" @click="batchDelete" :disabled="selectedIds.size === 0">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
            </svg>
          </el-button>
        </div>
      </div>

      <!-- Empty state -->
      <el-empty v-if="!photoStore.loading && photoStore.photos.length === 0" class="empty-state" description="还没有照片，点击下方按钮开始上传" :image-size="96" />

      <!-- Photo grid grouped by month -->
      <template v-else>
        <div
          v-for="item in timelineItems"
          :key="`${item.year}-${item.month}-${item.startIndex}`"
          class="month-section"
          :data-month="`${item.year}-${item.month}`"
        >
          <div class="month-label">{{ item.label }}</div>
          <div class="photo-grid-compact" :style="gridStyle">
            <PhotoCard
              v-for="i in item.count"
              :key="photoStore.photos[item.startIndex + i - 1].id"
              :photo="photoStore.photos[item.startIndex + i - 1]"
              :select-mode="selectMode"
              :selected="selectedIds.has(photoStore.photos[item.startIndex + i - 1].id)"
              @click="openViewer(item.startIndex + i - 1)"
              @toggle-favorite="handleToggleFavorite(photoStore.photos[item.startIndex + i - 1].id)"
              @toggle-select="toggleSelect(photoStore.photos[item.startIndex + i - 1].id)"
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
    <div v-if="timelineItems.length > 0 && !selectMode" class="timeline-rail">
      <el-button
        v-for="item in timelineItems"
        :key="`${item.year}-${item.month}-${item.startIndex}`"
        class="ep-control timeline-item"
        :class="{ active: activeMonthKey === `${item.year}-${item.month}` }"
        :style="{ flex: item.count }"
        @click="scrollToMonth(item)"
        :title="item.label"
      >
        <span class="timeline-dot"></span>
        <span class="timeline-label">{{ item.label }}</span>
      </el-button>
    </div>

    <!-- Zoom controls -->
    <div v-if="!selectMode" class="zoom-controls">
      <el-button class="zoom-btn" circle text @click="zoomIn" :disabled="zoomLevel === 0" title="放大">
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M12 4a1 1 0 011 1v6h6a1 1 0 110 2h-6v6a1 1 0 11-2 0v-6H5a1 1 0 110-2h6V5a1 1 0 011-1z" />
        </svg>
      </el-button>
      <el-button class="zoom-btn" circle text @click="zoomOut" :disabled="zoomLevel === 4" title="缩小">
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M5 12a1 1 0 011-1h12a1 1 0 110 2H6a1 1 0 01-1-1z" />
        </svg>
      </el-button>
    </div>

    <!-- Upload FAB -->
    <el-button v-if="!selectMode" type="primary" circle class="fab-upload" aria-label="上传照片" @click="showUploader = true">
      <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
        <path d="M12 4v16m8-8H4" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
    </el-button>

    <!-- Select mode FAB -->
    <el-button v-if="!selectMode && photoStore.photos.length > 0" type="primary" circle class="fab-select" aria-label="选择照片" @click="toggleSelectMode">
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="none" stroke="currentColor" stroke-width="2"/>
      </svg>
    </el-button>

    <el-dialog append-to-body v-model="showUploader" title="上传照片" width="min(720px, calc(100vw - 24px))" class="mv-dialog uploader-dialog" destroy-on-close>
      <Uploader @uploaded="handleUploaded" @done="showUploader = false" />
    </el-dialog>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="photoStore.photos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.album-picker-list{width:100%;max-height:50vh;overflow:auto}.album-picker-list :deep(.el-button){width:100%;min-height:40px;height:auto;white-space:normal}.album-picker-list :deep(.el-button>span){gap:8px}

.gallery-wrapper {
  position: relative;
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.gallery-scroll {
  height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  overflow-y: auto;
}

/* Selection toolbar */
.selection-toolbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: var(--accent);
  color: white;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.toolbar-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.toolbar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-btn.text-btn {
  background: none;
}

.toolbar-btn.text-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.toolbar-btn.delete-btn {
  background: rgba(255, 69, 58, 0.8);
}

.toolbar-btn.delete-btn:hover {
  background: rgba(255, 69, 58, 1);
}

.toolbar-btn.action-btn {
  padding: 6px 8px;
}

/* Picker dropdown */
.picker-wrapper {
  position: relative;
}

/* Rating picker */

.toolbar-title {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
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
  bottom: calc(var(--tab-height) + 130px);
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

/* Select FAB */
.fab-select {
  position: fixed;
  bottom: calc(var(--tab-height) + 86px);
  right: 20px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 50;
  border: 0.5px solid var(--glass-border);
  cursor: pointer;
}

.fab-select svg {
  width: 18px;
  height: 18px;
}

.fab-select:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.fab-select:active {
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
  .fab-select {
    bottom: calc(var(--tab-height) + 80px);
    right: 16px;
  }
  .zoom-controls {
    right: 16px;
    bottom: calc(var(--tab-height) + 124px);
  }
  .toolbar-actions {
    gap: 4px;
  }
  .toolbar-btn {
    padding: 6px 8px;
    font-size: 12px;
  }
}
</style>

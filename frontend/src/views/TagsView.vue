<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { tagApi } from '@/api/tagApi'
import type { Tag, Photo } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'

const router = useRouter()
const tags = ref<Tag[]>([])
const loading = ref(false)
const searchQuery = ref('')
const sortBy = ref<'name' | 'count' | 'date'>('date')
const viewMode = ref<'grid' | 'list'>('grid')

// Cover photos cache
const coverPhotos = ref<Record<number, Photo[]>>({})

onMounted(async () => {
  await loadTags()
})

async function loadTags() {
  loading.value = true
  try {
    const { data } = await tagApi.list(searchQuery.value || undefined, sortBy.value)
    tags.value = data
    // Load cover photos for each tag
    for (const tag of data) {
      if (tag.photoCount > 0) {
        try {
          const { data: covers } = await tagApi.getCoverPhotos(tag.id, 4)
          coverPhotos.value[tag.id] = covers
        } catch { /* ignore */ }
      }
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadTags()
}

function onSortChange() {
  loadTags()
}

function goToTag(id: number) {
  router.push(`/tags/${id}`)
}

const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']

// Viewer
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

function openViewer(photos: Photo[], index: number) {
  viewerPhotos.value = photos
  viewerIndex.value = index
  viewerVisible.value = true
}
</script>

<template>
  <div class="tags-view">
    <!-- Header -->
    <div class="page-header">
      <h1 class="page-title">标签</h1>
    </div>

    <!-- Search & controls -->
    <div class="controls-bar">
      <div class="search-box">
        <el-input
          v-model="searchQuery"
          class="search-input"
          placeholder="搜索标签..."
          clearable
          @keyup.enter="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-select v-model="sortBy" class="sort-select" @change="onSortChange">
        <el-option value="date" label="最近创建" />
        <el-option value="name" label="按名称" />
        <el-option value="count" label="按照片数" />
      </el-select>
      <div class="view-toggle">
        <el-button class="view-btn" :class="{ active: viewMode === 'grid' }" @click="viewMode = 'grid'">
          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
            <path d="M3 3h8v8H3V3zm0 10h8v8H3v-8zM13 3h8v8h-8V3zm0 10h8v8h-8v-8z" />
          </svg>
        </el-button>
        <el-button class="view-btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">
          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
            <path d="M3 5h18v2H3V5zm0 6h18v2H3v-2zm0 6h18v2H3v-2z" />
          </svg>
        </el-button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="tags.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42z" />
      </svg>
      <h3>暂无标签</h3>
      <p>在照片详情页中为照片添加标签</p>
    </div>

    <!-- Grid view -->
    <div v-else-if="viewMode === 'grid'" class="tags-grid">
      <div
        v-for="tag in tags"
        :key="tag.id"
        class="tag-card"
        @click="goToTag(tag.id)"
      >
        <!-- Cover: 4-photo mosaic -->
        <div class="tag-cover">
          <template v-if="coverPhotos[tag.id] && coverPhotos[tag.id].length > 0">
            <div
              v-for="(photo, i) in coverPhotos[tag.id].slice(0, 4)"
              :key="photo.id"
              class="cover-cell"
              :style="{ flex: coverPhotos[tag.id].length === 1 ? '2 2 100%' : coverPhotos[tag.id].length === 2 ? '1 1 50%' : coverPhotos[tag.id].length === 3 && i === 0 ? '2 2 100%' : '1 1 50%' }"
              @click.stop="openViewer(coverPhotos[tag.id], i)"
            >
              <img
                v-if="photo.thumbnailUrl"
                :src="photo.thumbnailUrl"
                :alt="tag.name"
                loading="lazy"
                class="cover-img"
              />
              <div v-else class="cover-placeholder"></div>
            </div>
          </template>
          <div v-else class="cover-empty">
            <svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
              <path d="M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42z" />
            </svg>
          </div>
        </div>
        <!-- Tag info -->
        <div class="tag-info">
          <div class="tag-color-dot" :style="{ background: tag.color || '#0a84ff' }"></div>
          <span class="tag-name">{{ tag.name }}</span>
          <span class="tag-count">{{ tag.photoCount }} 张</span>
        </div>
      </div>
    </div>

    <!-- List view -->
    <div v-else class="tags-list">
      <div
        v-for="tag in tags"
        :key="tag.id"
        class="tag-list-item"
        @click="goToTag(tag.id)"
      >
        <div class="tag-color-dot" :style="{ background: tag.color || '#0a84ff' }"></div>
        <div class="tag-list-info">
          <span class="tag-name">{{ tag.name }}</span>
          <span v-if="tag.description" class="tag-desc">{{ tag.description }}</span>
        </div>
        <span class="tag-count">{{ tag.photoCount }} 张</span>
        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" class="arrow-icon">
          <path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" />
        </svg>
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
.tags-view {
  min-height: calc(100vh - var(--top-bar-height));
  padding: 0 16px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  padding: 12px 0;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
}

.controls-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.search-box {
  flex: 1;
}

.search-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.search-input {
  width: 100%;
}

.sort-select {
  width: 140px;
}

.view-toggle {
  display: flex;
  gap: 2px;
  background: var(--bg-secondary);
  border-radius: 10px;
  padding: 2px;
  border: 0.5px solid var(--glass-border);
}

.view-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--text-tertiary);
  transition: all 0.15s;
}

.view-btn.active {
  background: var(--bg-tertiary);
  color: var(--accent);
}

/* Grid */
.tags-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.tag-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  border: 0.5px solid var(--glass-border);
  transition: transform 0.2s;
}

.tag-card:hover {
  transform: translateY(-2px);
}

.tag-cover {
  display: flex;
  flex-wrap: wrap;
  aspect-ratio: 1;
  gap: 1px;
  background: var(--bg-tertiary);
}

.cover-cell {
  overflow: hidden;
  cursor: pointer;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}

.cover-cell:hover .cover-img {
  transform: scale(1.05);
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  background: var(--bg-tertiary);
}

.cover-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.tag-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
}

.tag-color-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-count {
  font-size: 12px;
  color: var(--text-secondary);
}

/* List */
.tags-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.tag-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-secondary);
  cursor: pointer;
  transition: background 0.15s;
}

.tag-list-item:hover {
  background: var(--bg-tertiary);
}

.tag-list-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.tag-desc {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.arrow-icon {
  color: var(--text-tertiary);
}

/* Loading & empty */
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
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }

.empty-state p { font-size: 14px; }

@media (max-width: 640px) {
  .controls-bar { flex-wrap: wrap; }
  .tags-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
}
</style>

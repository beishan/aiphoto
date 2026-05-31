<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { peopleApi } from '@/api/peopleApi'
import { usePhotoStore } from '@/stores/photoStore'
import type { Person, Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const photoStore = usePhotoStore()

const personId = Number(route.params.id)
const person = ref<Person | null>(null)
const photos = ref<Photo[]>([])
const page = ref(0)
const pageSize = 40
const totalElements = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const editing = ref(false)
const editName = ref('')

const viewerVisible = ref(false)
const viewerIndex = ref(0)

// Zoom level: 0=6col, 1=4col(default), 2=3col, 3=2col
const zoomLevel = ref(0)
const zoomColumns = [6, 10, 16, 20, 30]

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${zoomColumns[zoomLevel.value]}, 1fr)`,
}))

function initZoom() {
  const w = window.innerWidth
  if (w < 480) {
    zoomLevel.value = 2
  } else if (w < 768) {
    zoomLevel.value = 1
  } else {
    zoomLevel.value = 0
  }
}

onMounted(async () => {
  initZoom()
  await fetchPerson()
  await fetchPhotos()
})

async function fetchPerson() {
  try {
    const { data } = await peopleApi.get(personId)
    person.value = data
    editName.value = data.name || ''
  } catch (e) {
    message.error('人物不存在')
    router.back()
  }
}

async function fetchPhotos() {
  loading.value = true
  try {
    const { data } = await peopleApi.getPhotos(personId, 0, pageSize)
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
    const { data } = await peopleApi.getPhotos(personId, nextPage, pageSize)
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
  router.push('/people')
}

function startEdit() {
  editName.value = person.value?.name || ''
  editing.value = true
}

async function saveName() {
  if (!person.value) return
  const name = editName.value.trim()
  if (!name) {
    message.warning('请输入名字')
    return
  }
  try {
    const { data } = await peopleApi.update(personId, { name })
    person.value = data
    editing.value = false
    message.success('已更新')
  } catch (e) {
    message.error('更新失败')
  }
}

function formatRange() {
  if (!person.value) return ''
  const parts: string[] = []
  if (person.value.firstSeen) {
    parts.push(new Date(person.value.firstSeen).toLocaleDateString('zh-CN'))
  }
  if (person.value.lastSeen) {
    parts.push(new Date(person.value.lastSeen).toLocaleDateString('zh-CN'))
  }
  return parts.join(' ~ ')
}
</script>

<template>
  <div class="detail-view">
    <!-- Header -->
    <div class="detail-header glass">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12l4.58-4.59z" />
        </svg>
      </button>
      <div class="header-info">
        <div v-if="editing" class="name-edit">
          <input v-model="editName" type="text" class="name-input" @keyup.enter="saveName" autofocus />
          <button class="name-save" @click="saveName">保存</button>
          <button class="name-cancel" @click="editing = false">取消</button>
        </div>
        <h2 v-else @click="startEdit" class="name-editable">
          {{ person?.name || '未命名' }}
          <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14" class="edit-icon">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
          </svg>
        </h2>
        <span class="header-meta">{{ totalElements }} 张照片 · {{ formatRange() }}</span>
      </div>
    </div>

    <div class="detail-scroll" @scroll="handleScroll">
      <!-- Empty state -->
      <div v-if="!loading && photos.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
          <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
        </svg>
        <h3>暂无照片</h3>
        <p>AI 还没有找到此人的照片</p>
      </div>

      <!-- Photo grid -->
      <template v-else>
        <div class="photo-grid-compact" :style="gridStyle">
          <PhotoCard
            v-for="(photo, i) in photos"
            :key="photo.id"
            :photo="photo"
            @click="openViewer(i)"
            @toggle-favorite="handleToggleFavorite(photo.id)"
          />
        </div>
      </template>

      <!-- Loading indicator -->
      <div v-if="loading || loadingMore" class="loading-indicator">
        <div class="loading-spinner"></div>
      </div>
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
  flex: 1;
}

.name-editable {
  font-size: 17px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.name-editable:hover .edit-icon {
  opacity: 1;
}

.edit-icon {
  color: var(--text-secondary);
  opacity: 0;
  transition: opacity 0.2s;
}

.name-edit {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name-input {
  flex: 1;
  height: 32px;
  padding: 0 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--accent);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 16px;
  font-family: inherit;
  outline: none;
}

.name-save, .name-cancel {
  padding: 4px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.name-save {
  background: var(--accent);
  color: white;
}

.name-cancel {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.header-meta {
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

.photo-grid-compact {
  display: grid;
  gap: 2px;
  padding: 2px;
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

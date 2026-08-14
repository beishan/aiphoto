<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, useDialog } from '@/utils/feedback'
import { peopleApi } from '@/api/peopleApi'
import type { Face } from '@/api/peopleApi'
import { usePhotoStore } from '@/stores/photoStore'
import type { Person, Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const photoStore = usePhotoStore()

const personId = Number(route.params.id)
const person = ref<Person | null>(null)
const faces = ref<Face[]>([])
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

// Zoom level
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
  await Promise.all([fetchPerson(), fetchFaces(), fetchPhotos()])
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

async function fetchFaces() {
  try {
    const { data } = await peopleApi.getFaces(personId)
    faces.value = data
  } catch (e) {
    // ignore
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

function confirmDelete() {
  dialog.warning({
    title: '删除人物',
    content: `确定要删除"${person.value?.name || '未命名'}"吗？此操作不会删除照片，只会移除此人物标记。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await peopleApi.delete(personId)
        message.success('已删除')
        router.push('/people')
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

async function setCoverFace(faceId: number) {
  try {
    await peopleApi.setCoverFace(personId, faceId)
    message.success('已设置封面')
    await fetchPerson()
  } catch (e) {
    message.error('设置失败')
  }
}

function getBboxStyle(bboxJson: string) {
  try {
    const bbox = JSON.parse(bboxJson)
    return {
      left: `${bbox.x * 100}%`,
      top: `${bbox.y * 100}%`,
      width: `${bbox.w * 100}%`,
      height: `${bbox.h * 100}%`,
    }
  } catch {
    return {}
  }
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
          <el-input v-model="editName" class="name-input" autofocus @keyup.enter="saveName" />
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
      <button class="delete-btn" @click="confirmDelete" title="删除人物">
        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
        </svg>
      </button>
    </div>

    <div class="detail-scroll" @scroll="handleScroll">
      <!-- Face thumbnails section -->
      <div v-if="faces.length > 0" class="faces-section">
        <div class="faces-header">
          <span class="faces-title">人脸缩略图 ({{ faces.length }})</span>
          <span class="faces-hint">点击设为封面</span>
        </div>
        <div class="faces-row">
          <div
            v-for="face in faces"
            :key="face.id"
            class="face-thumb"
            :class="{ active: person?.coverFaceId === face.id }"
            @click="setCoverFace(face.id)"
            :title="person?.coverFaceId === face.id ? '当前封面' : '设为封面'"
          >
            <img v-if="face.photoUrl" :src="face.photoUrl" alt="" />
            <div v-if="face.bboxJson" class="face-crop" :style="getBboxStyle(face.bboxJson)"></div>
            <div v-if="person?.coverFaceId === face.id" class="face-badge">封面</div>
          </div>
        </div>
      </div>

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
}
.name-input :deep(.el-input__wrapper) { min-height: 32px; border-radius: var(--radius-md); }

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

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--text-secondary);
  border: none;
  background: none;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.delete-btn:hover {
  color: #e74c3c;
  background: rgba(231, 76, 60, 0.1);
}

.detail-scroll {
  height: calc(100vh - var(--top-bar-height) - var(--tab-height) - 56px);
  overflow-y: auto;
}

/* Faces section */
.faces-section {
  padding: 16px;
  border-bottom: 1px solid var(--border);
}

.faces-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.faces-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.faces-hint {
  font-size: 11px;
  color: var(--text-tertiary);
}

.faces-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 0;
  -webkit-overflow-scrolling: touch;
}

.faces-row::-webkit-scrollbar {
  height: 4px;
}

.faces-row::-webkit-scrollbar-thumb {
  background: var(--bg-tertiary);
  border-radius: 2px;
}

.face-thumb {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-secondary);
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
  border: 2px solid transparent;
  transition: border-color 0.2s, transform 0.2s;
}

.face-thumb:hover {
  transform: scale(1.05);
}

.face-thumb.active {
  border-color: var(--accent);
}

.face-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.face-crop {
  position: absolute;
  border: 1.5px solid rgba(255, 255, 255, 0.8);
  border-radius: 2px;
  pointer-events: none;
}

.face-badge {
  position: absolute;
  bottom: -2px;
  left: 50%;
  transform: translateX(-50%);
  padding: 1px 6px;
  background: var(--accent);
  color: white;
  font-size: 9px;
  font-weight: 600;
  border-radius: 6px;
  white-space: nowrap;
}

/* Empty state */
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

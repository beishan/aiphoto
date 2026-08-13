<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from '@/utils/feedback'
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
const editingDescription = ref(false)
const descriptionText = ref('')
const pickingCover = ref(false)

// 根据视口宽度自适应列数
const gridStyle = computed(() => {
  const w = window.innerWidth
  let cols: number
  if (w < 480) cols = 3
  else if (w < 768) cols = 4
  else if (w < 1024) cols = 6
  else if (w < 1440) cols = 8
  else cols = 10
  return { gridTemplateColumns: `repeat(${cols}, 1fr)` }
})

const albumId = Number(route.params.id)

onMounted(async () => {
  loading.value = true
  try {
    const [albumRes, photosRes] = await Promise.all([
      albumApi.get(albumId),
      albumApi.getPhotos(albumId)
    ])
    album.value = albumRes.data
    photos.value = photosRes.data
    descriptionText.value = album.value?.description || ''
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

function startEditDescription() {
  descriptionText.value = album.value?.description || ''
  editingDescription.value = true
}

async function saveDescription() {
  try {
    const { data } = await albumApi.update(albumId, {
      name: album.value!.name,
      description: descriptionText.value || null
    })
    album.value = data
    editingDescription.value = false
    message.success('描述已保存')
  } catch (e) {
    message.error('保存失败')
  }
}

function cancelEditDescription() {
  editingDescription.value = false
  descriptionText.value = album.value?.description || ''
}

async function handleSetCover(photoId: number) {
  try {
    const { data } = await albumApi.setCoverPhoto(albumId, photoId)
    album.value = data
    pickingCover.value = false
    message.success('封面已设置')
  } catch (e) {
    message.error('设置封面失败')
  }
}

function onPhotoClick(photo: Photo, index: number) {
  if (pickingCover.value) {
    handleSetCover(photo.id)
  } else {
    openViewer(index)
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
      <button v-if="photos.length > 0" class="cover-btn" :class="{ active: pickingCover }" @click="pickingCover = !pickingCover">
        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
          <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
        </svg>
        {{ pickingCover ? '取消' : '封面' }}
      </button>
    </div>

    <!-- Description -->
    <div class="description-section">
      <div v-if="editingDescription" class="description-edit">
        <el-input
          v-model="descriptionText"
          type="textarea"
          placeholder="添加相册描述..."
          class="description-input"
          :rows="3"
        />
        <div class="description-actions">
          <button class="btn-cancel" @click="cancelEditDescription">取消</button>
          <button class="btn-save" @click="saveDescription">保存</button>
        </div>
      </div>
      <div v-else class="description-display" @click="startEditDescription">
        <p v-if="album?.description" class="description-text">{{ album.description }}</p>
        <p v-else class="description-placeholder">点击添加描述...</p>
      </div>
    </div>

    <!-- Content -->
    <div v-if="pickingCover" class="pick-cover-banner">
      <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
      </svg>
      点击照片设为封面
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="photos.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z" />
      </svg>
      <p>相册中暂无照片</p>
    </div>

    <div v-else class="photo-grid-compact" :style="gridStyle">
      <PhotoCard
        v-for="(photo, index) in photos"
        :key="photo.id"
        :photo="photo"
        class="photo-cell"
        :class="{ 'picking-cover': pickingCover }"
        @click="onPhotoClick(photo, index)"
      >
        <template #actions>
          <button
            class="set-cover-btn"
            title="设为封面"
            @click.stop="handleSetCover(photo.id)"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
              <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
            </svg>
          </button>
        </template>
      </PhotoCard>
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

.cover-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s;
}

.cover-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.cover-btn.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.pick-cover-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  margin: 0 16px 8px;
  border-radius: 8px;
  background: var(--accent);
  color: white;
  font-size: 14px;
  font-weight: 500;
  animation: fadeIn 0.2s;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.description-section {
  padding: 0 16px 12px;
}

.description-display {
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.description-display:hover {
  background: var(--bg-secondary);
}

.description-text {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.5;
}

.description-placeholder {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
}

.description-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.description-input {
  width: 100%;
}

.description-input :deep(.el-textarea__inner) { border-radius: 8px; }

.description-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-cancel,
.btn-save {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.btn-cancel {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.btn-save {
  background: var(--accent);
  color: white;
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

.set-cover-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: white;
  border: none;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.set-cover-btn:hover {
  background: var(--accent);
}

.photo-cell.picking-cover {
  cursor: crosshair;
}

.photo-cell.picking-cover:hover {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
</style>

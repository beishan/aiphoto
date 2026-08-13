<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import type { Photo, Album } from '@/types'
import { usePhotoStore } from '@/stores/photoStore'
import { photoApi } from '@/api/photoApi'
import { albumApi } from '@/api/albumApi'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const props = defineProps<{
  show: boolean
  photos: Photo[]
  initialIndex: number
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const photoStore = usePhotoStore()
const currentIndex = ref(props.initialIndex)
const showInfo = ref(false)

// Album picker
const showAlbumPicker = ref(false)
const albums = ref<Album[]>([])
const loadingAlbums = ref(false)
const addingToAlbum = ref(false)
const creatingAlbum = ref(false)
const newAlbumName = ref('')
const showCreateAlbum = ref(false)

const currentPhoto = computed(() => props.photos[currentIndex.value])

const isVideo = computed(() => {
  return currentPhoto.value?.mediaType === 'VIDEO'
})

watch(() => props.initialIndex, (val) => {
  currentIndex.value = val
})

watch(() => props.show, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

function close() {
  emit('update:show', false)
}

function prev() {
  if (currentIndex.value > 0) {
    currentIndex.value--
  }
}

function next() {
  if (currentIndex.value < props.photos.length - 1) {
    currentIndex.value++
  }
}

async function toggleFavorite() {
  if (currentPhoto.value) {
    await photoStore.toggleFavorite(currentPhoto.value.id)
  }
}

async function deletePhoto() {
  if (!currentPhoto.value) return
  try {
    await ElMessageBox.confirm('确定将这张照片移入回收站吗？', '删除照片', {
      type: 'warning',
      confirmButtonText: '移入回收站',
      cancelButtonText: '取消',
      customClass: 'mv-message-box',
    })
  } catch { return }
  const photo = currentPhoto.value
  try {
    await photoApi.delete(photo.id)
  } catch (e) {
    ElMessage.error('删除失败，请重试')
    return
  }
  // Remove from photos array
  const idx = props.photos.findIndex(p => p.id === photo.id)
  if (idx !== -1) {
    props.photos.splice(idx, 1)
  }
  // If no photos left, close viewer
  if (props.photos.length === 0) {
    close()
    return
  }
  // Adjust index
  if (currentIndex.value >= props.photos.length) {
    currentIndex.value = props.photos.length - 1
  }
}

async function openAlbumPicker() {
  if (!currentPhoto.value) return
  showAlbumPicker.value = true
  showCreateAlbum.value = false
  newAlbumName.value = ''
  loadingAlbums.value = true
  try {
    const { data } = await albumApi.list()
    albums.value = data
  } finally {
    loadingAlbums.value = false
  }
}

async function addToAlbum(albumId: number) {
  if (!currentPhoto.value || addingToAlbum.value) return
  addingToAlbum.value = true
  try {
    await albumApi.addPhoto(albumId, currentPhoto.value.id)
    showAlbumPicker.value = false
  } catch (e) {
    ElMessage.error('添加失败，请重试')
  } finally {
    addingToAlbum.value = false
  }
}

async function createAndAdd() {
  if (!currentPhoto.value || !newAlbumName.value.trim() || creatingAlbum.value) return
  creatingAlbum.value = true
  try {
    const { data: newAlbum } = await albumApi.create({ name: newAlbumName.value.trim(), type: 'VIRTUAL' })
    await albumApi.addPhoto(newAlbum.id, currentPhoto.value.id)
    showAlbumPicker.value = false
  } catch (e) {
    ElMessage.error('创建失败，请重试')
  } finally {
    creatingAlbum.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (!props.show) return
  if (e.key === 'Escape' && showAlbumPicker.value) {
    showAlbumPicker.value = false
    return
  }
  if (e.key === 'Escape') close()
  if (e.key === 'ArrowLeft') prev()
  if (e.key === 'ArrowRight') next()
}

function goToDetail() {
  if (!currentPhoto.value) return
  close()
  router.push(`/photos/${currentPhoto.value.id}`)
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="viewer">
      <div v-if="show" class="viewer-overlay" @click.self="close">
        <!-- Top toolbar -->
        <div class="viewer-topbar glass">
          <div class="topbar-left">
            <span class="counter">{{ currentIndex + 1 }} / {{ photos.length }}</span>
          </div>
          <div class="topbar-right">
            <button class="toolbar-btn" @click="toggleFavorite">
              <svg v-if="currentPhoto?.favorite" viewBox="0 0 24 24" fill="#ff453a" width="22" height="22">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
                <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z" />
              </svg>
            </button>
            <button class="toolbar-btn" @click="openAlbumPicker">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </button>
            <button class="toolbar-btn danger" @click="deletePhoto">
              <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
              </svg>
            </button>
            <button class="toolbar-btn" @click="showInfo = !showInfo">
              <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
              </svg>
            </button>
            <button class="toolbar-btn" @click="goToDetail" title="查看详情">
              <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM6 20V4h5v7h7v9H6z"/>
              </svg>
            </button>
            <button class="toolbar-btn" @click="close">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Image area -->
        <div class="viewer-image-area">
          <button class="nav-btn prev-btn" @click="prev" :disabled="currentIndex <= 0">
            <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </button>

          <div class="viewer-image-wrapper">
            <video
              v-if="isVideo && currentPhoto"
              :src="currentPhoto.originalUrl || currentPhoto.thumbnailUrl || ''"
              class="viewer-video"
              controls
              autoplay
              playsinline
              preload="auto"
            />
            <img v-else-if="currentPhoto" :src="currentPhoto.originalUrl || currentPhoto.thumbnailUrl || '/placeholder.png'" class="viewer-img" />
          </div>

          <button class="nav-btn next-btn" @click="next" :disabled="currentIndex >= photos.length - 1">
            <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
              <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z" />
            </svg>
          </button>
        </div>

        <!-- Info panel -->
        <Transition name="slide-up">
          <div v-if="showInfo && currentPhoto" class="info-panel glass">
            <div class="info-row" v-if="currentPhoto.exifDate">
              <span class="info-label">拍摄时间</span>
              <span class="info-value">{{ new Date(currentPhoto.exifDate).toLocaleString('zh-CN') }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.gpsLat">
              <span class="info-label">位置</span>
              <span class="info-value">{{ currentPhoto.gpsLat.toFixed(4) }}, {{ currentPhoto.gpsLng?.toFixed(4) }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.width">
              <span class="info-label">尺寸</span>
              <span class="info-value">{{ currentPhoto.width }} x {{ currentPhoto.height }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.aiCaption">
              <span class="info-label">AI 描述</span>
              <span class="info-value">{{ currentPhoto.aiCaption }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.note">
              <span class="info-label">备注</span>
              <span class="info-value">{{ currentPhoto.note }}</span>
            </div>
          </div>
        </Transition>

        <el-dialog v-model="showAlbumPicker" title="添加到相册" width="460px" :z-index="10020" class="mv-dialog album-picker-dialog">
              <!-- Create new album -->
              <div v-if="showCreateAlbum" class="create-album-row">
                <el-input
                  v-model="newAlbumName"
                  placeholder="输入相册名称"
                  @keyup.enter="createAndAdd"
                />
                <el-button
                  type="primary"
                  @click="createAndAdd"
                  :disabled="!newAlbumName.trim() || creatingAlbum"
                >
                  {{ creatingAlbum ? '创建中...' : '创建并添加' }}
                </el-button>
              </div>
              <button v-else class="create-album-btn" @click="showCreateAlbum = true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                  <path d="M12 5v14M5 12h14" />
                </svg>
                <span>新建相册</span>
              </button>

              <!-- Album list -->
              <div class="album-list" v-if="!loadingAlbums">
                <button
                  v-for="album in albums"
                  :key="album.id"
                  class="album-item"
                  @click="addToAlbum(album.id)"
                  :disabled="addingToAlbum"
                >
                  <div class="album-thumb">
                    <img v-if="album.coverPhotoUrl" :src="album.coverPhotoUrl" alt="" />
                    <svg v-else viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
                      <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z" />
                    </svg>
                  </div>
                  <div class="album-info">
                    <span class="album-name">{{ album.name }}</span>
                    <span class="album-count">{{ album.photoCount }} 张</span>
                  </div>
                </button>
              </div>
              <div v-else class="picker-loading">
                <div class="loading-spinner"></div>
              </div>
        </el-dialog>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.viewer-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: #000;
  display: flex;
  flex-direction: column;
}

.viewer-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  padding-top: calc(8px + var(--safe-top, 0px));
  z-index: 10;
  border-bottom: 0.5px solid var(--glass-border);
}

.topbar-left {
  display: flex;
  align-items: center;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: white;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: opacity 0.2s, background 0.2s;
}

.toolbar-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.toolbar-btn:active {
  opacity: 0.6;
}

.toolbar-btn.danger {
  color: #ff453a;
}

.toolbar-btn.danger:hover {
  background: rgba(255, 69, 58, 0.15);
}

.counter {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

.viewer-image-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.viewer-image-wrapper {
  max-width: 100%;
  max-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.viewer-img {
  max-width: 90vw;
  max-height: 80vh;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.viewer-video {
  max-width: 90vw;
  max-height: 80vh;
  object-fit: contain;
  outline: none;
}

.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  color: white;
  border: none;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
  z-index: 5;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.nav-btn:disabled {
  opacity: 0.2;
  cursor: default;
}

.prev-btn { left: 16px; }
.next-btn { right: 16px; }

.info-panel {
  position: absolute;
  bottom: 80px;
  left: 16px;
  right: 16px;
  max-width: 480px;
  margin: 0 auto;
  border-radius: var(--radius-lg);
  padding: 16px;
  border: 0.5px solid var(--glass-border);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 6px 0;
}

.info-row:not(:last-child) {
  border-bottom: 0.5px solid var(--glass-border);
}

.info-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
  margin-right: 16px;
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

/* Transitions */
.viewer-enter-active,
.viewer-leave-active {
  transition: opacity 0.25s ease;
}
.viewer-enter-from,
.viewer-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .nav-btn {
    display: none;
  }

  .viewer-img {
    max-width: 100vw;
    max-height: 75vh;
  }

  .viewer-video {
    max-width: 100vw;
    max-height: 75vh;
  }
}

/* Album picker */
.album-picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.album-picker {
  width: 340px;
  max-height: 70vh;
  border-radius: var(--radius-lg);
  padding: 0;
  border: 0.5px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 0.5px solid var(--glass-border);
}

.picker-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.picker-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
}

.create-album-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--glass-border);
}

.album-name-input {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  background: var(--bg-primary);
  border: 1px solid var(--separator);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  outline: none;
}

.album-name-input:focus {
  border-color: var(--accent);
}

.create-confirm-btn {
  height: 36px;
  padding: 0 14px;
  background: var(--accent);
  color: white;
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  white-space: nowrap;
}

.create-confirm-btn:disabled {
  opacity: 0.5;
}

.create-album-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: var(--accent);
  font-size: 14px;
  border-bottom: 0.5px solid var(--glass-border);
}

.album-list {
  overflow-y: auto;
  max-height: 40vh;
}

.album-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 16px;
  text-align: left;
}

.album-item:hover {
  background: var(--bg-tertiary);
}

.album-item:disabled {
  opacity: 0.5;
}

.album-thumb {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-tertiary);
}

.album-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.album-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.album-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.picker-loading {
  display: flex;
  justify-content: center;
  padding: 32px;
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

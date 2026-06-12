<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { photoApi } from '@/api/photoApi'
import { peopleApi } from '@/api/peopleApi'
import type { PhotoDetail, Person } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const photoId = Number(route.params.id)
const photo = ref<PhotoDetail | null>(null)
const loading = ref(true)
const editing = ref(false)
const editNote = ref('')
const showViewer = ref(false)

// Rating stars
const hoverRating = ref(0)

const isVideo = computed(() => photo.value?.mediaType === 'VIDEO')
const isImage = computed(() => photo.value?.mediaType === 'PHOTO' || photo.value?.mediaType === 'GIF')

const formattedSize = computed(() => {
  if (!photo.value?.fileSize) return ''
  const bytes = photo.value.fileSize
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(2) + ' GB'
})

const mediaTypeLabel = computed(() => {
  const types: Record<string, string> = {
    PHOTO: '照片',
    VIDEO: '视频',
    GIF: '动图',
    RAW: 'RAW',
  }
  return types[photo.value?.mediaType || 'PHOTO'] || '照片'
})

onMounted(async () => {
  try {
    const { data } = await photoApi.getDetail(photoId)
    photo.value = data
    editNote.value = data.note || ''
  } catch (e) {
    message.error('照片不存在')
    router.back()
  } finally {
    loading.value = false
  }
})

function goBack() {
  router.back()
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

async function toggleFavorite() {
  if (!photo.value) return
  try {
    await photoApi.update(photo.value.id, { favorite: !photo.value.favorite })
    photo.value.favorite = !photo.value.favorite
    message.success(photo.value.favorite ? '已收藏' : '已取消收藏')
  } catch (e) {
    message.error('操作失败')
  }
}

async function setRating(rating: number) {
  if (!photo.value) return
  try {
    await photoApi.update(photo.value.id, { rating })
    photo.value.rating = rating
    message.success(`评分已设为 ${rating} 星`)
  } catch (e) {
    message.error('设置失败')
  }
}

function startEditNote() {
  editNote.value = photo.value?.note || ''
  editing.value = true
}

async function saveNote() {
  if (!photo.value) return
  try {
    await photoApi.update(photo.value.id, { note: editNote.value })
    photo.value.note = editNote.value
    editing.value = false
    message.success('已保存')
  } catch (e) {
    message.error('保存失败')
  }
}

function confirmDelete() {
  if (!photo.value) return
  dialog.warning({
    title: '删除照片',
    content: '确定要删除这张照片吗？此操作不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await photoApi.delete(photo.value!.id)
        message.success('已删除')
        router.back()
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

function goToPerson(personId: number) {
  router.push(`/people/${personId}`)
}

function downloadPhoto() {
  if (!photo.value?.originalUrl) return
  const a = document.createElement('a')
  a.href = photo.value.originalUrl
  a.download = photo.value.originalFilename || 'photo'
  a.target = '_blank'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
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
        <h2 class="header-title">照片详情</h2>
      </div>
      <div class="header-actions">
        <button class="header-btn" @click="downloadPhoto" title="下载">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
          </svg>
        </button>
        <button class="header-btn danger" @click="confirmDelete" title="删除">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="detail-scroll" v-if="photo">
      <!-- Photo preview -->
      <div class="photo-preview" @click="showViewer = true">
        <video
          v-if="isVideo"
          :src="photo.originalUrl || photo.thumbnailUrl || ''"
          class="preview-media"
          controls
          playsinline
        />
        <img
          v-else-if="isImage"
          :src="photo.originalUrl || photo.thumbnailUrl || '/placeholder.png'"
          class="preview-media"
        />
        <div class="preview-overlay">
          <svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
            <path d="M15 8l-5 5h3v4h4v-4h3L15 8zm-5-1L4 7v10l6-4V7z" v-if="isVideo"/>
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" v-else/>
          </svg>
        </div>
      </div>

      <!-- Actions bar -->
      <div class="actions-bar">
        <button class="action-btn" :class="{ active: photo.favorite }" @click="toggleFavorite">
          <svg v-if="photo.favorite" viewBox="0 0 24 24" fill="#ff453a" width="24" height="24">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
            <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/>
          </svg>
          <span>收藏</span>
        </button>
        <button class="action-btn" @click="downloadPhoto">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
          </svg>
          <span>下载</span>
        </button>
        <button class="action-btn" @click="confirmDelete">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
          </svg>
          <span>删除</span>
        </button>
      </div>

      <!-- Rating -->
      <div class="info-section">
        <h3 class="section-title">评分</h3>
        <div class="rating-row">
          <button
            v-for="s in 5"
            :key="s"
            class="star-btn"
            :class="{ active: s <= (hoverRating || photo.rating || 0) }"
            @mouseenter="hoverRating = s"
            @mouseleave="hoverRating = 0"
            @click="setRating(s)"
          >
            <svg viewBox="0 0 24 24" :fill="s <= (hoverRating || photo.rating || 0) ? '#ffcc00' : 'none'" :stroke="s <= (hoverRating || photo.rating || 0) ? '#ffcc00' : 'currentColor'" stroke-width="2" width="28" height="28">
              <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
            </svg>
          </button>
          <button v-if="photo.rating" class="clear-btn" @click="setRating(0)">清除</button>
        </div>
      </div>

      <!-- Note -->
      <div class="info-section">
        <div class="section-header">
          <h3 class="section-title">备注</h3>
          <button v-if="!editing" class="edit-btn" @click="startEditNote">编辑</button>
        </div>
        <div v-if="editing" class="note-edit">
          <textarea v-model="editNote" class="note-input" rows="3" placeholder="添加备注..."></textarea>
          <div class="note-actions">
            <button class="btn-cancel" @click="editing = false">取消</button>
            <button class="btn-save" @click="saveNote">保存</button>
          </div>
        </div>
        <p v-else class="note-text" @click="startEditNote">{{ photo.note || '点击添加备注...' }}</p>
      </div>

      <!-- AI Caption -->
      <div v-if="photo.aiCaption" class="info-section">
        <h3 class="section-title">AI 描述</h3>
        <p class="caption-text">{{ photo.aiCaption }}</p>
      </div>

      <!-- People -->
      <div v-if="photo.people && photo.people.length > 0" class="info-section">
        <h3 class="section-title">人物 ({{ photo.people.length }})</h3>
        <div class="people-row">
          <div
            v-for="person in photo.people"
            :key="person.id"
            class="person-chip"
            @click="goToPerson(person.id)"
          >
            <div class="person-avatar">
              <img v-if="person.coverPhotoUrl" :src="person.coverPhotoUrl" alt="" />
              <span v-else class="avatar-fallback">{{ person.name?.[0] || '?' }}</span>
            </div>
            <span class="person-name">{{ person.name || '未命名' }}</span>
          </div>
        </div>
      </div>

      <!-- Tags -->
      <div v-if="photo.tags && photo.tags.length > 0" class="info-section">
        <h3 class="section-title">标签</h3>
        <div class="tags-row">
          <span
            v-for="tag in photo.tags"
            :key="tag.id"
            class="tag-chip"
            :style="tag.color ? { borderColor: tag.color, color: tag.color } : {}"
          >
            {{ tag.name }}
            <span v-if="tag.confidence" class="tag-confidence">{{ Math.round(tag.confidence * 100) }}%</span>
          </span>
        </div>
      </div>

      <!-- File info -->
      <div class="info-section">
        <h3 class="section-title">文件信息</h3>
        <div class="info-grid">
          <div class="info-item" v-if="photo.originalFilename">
            <span class="info-label">文件名</span>
            <span class="info-value">{{ photo.originalFilename }}</span>
          </div>
          <div class="info-item" v-if="mediaTypeLabel">
            <span class="info-label">类型</span>
            <span class="info-value">{{ mediaTypeLabel }}</span>
          </div>
          <div class="info-item" v-if="formattedSize">
            <span class="info-label">大小</span>
            <span class="info-value">{{ formattedSize }}</span>
          </div>
          <div class="info-item" v-if="photo.width && photo.height">
            <span class="info-label">尺寸</span>
            <span class="info-value">{{ photo.width }} × {{ photo.height }}</span>
          </div>
          <div class="info-item" v-if="photo.fileHashMd5">
            <span class="info-label">MD5</span>
            <span class="info-value hash-value">{{ photo.fileHashMd5 }}</span>
          </div>
        </div>
      </div>

      <!-- EXIF info -->
      <div class="info-section">
        <h3 class="section-title">拍摄信息</h3>
        <div class="info-grid">
          <div class="info-item" v-if="photo.exifDate">
            <span class="info-label">拍摄时间</span>
            <span class="info-value">{{ formatDateTime(photo.exifDate) }}</span>
          </div>
          <div class="info-item" v-if="photo.gpsLat">
            <span class="info-label">位置</span>
            <span class="info-value">
              {{ photo.gpsLat.toFixed(6) }}, {{ photo.gpsLng?.toFixed(6) }}
              <a :href="`https://maps.google.com/?q=${photo.gpsLat},${photo.gpsLng}`" target="_blank" class="map-link">查看地图</a>
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">上传时间</span>
            <span class="info-value">{{ formatDateTime(photo.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Photo Viewer -->
    <PhotoViewer
      v-model:show="showViewer"
      :photos="(photo ? [photo] : []) as any[]"
      :initial-index="0"
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
  flex: 1;
  min-width: 0;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-btn {
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
  transition: all 0.2s;
}

.header-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.header-btn.danger:hover {
  color: var(--danger);
  background: rgba(255, 69, 58, 0.1);
}

.detail-scroll {
  padding-bottom: 24px;
}

/* Photo preview */
.photo-preview {
  position: relative;
  background: #000;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  max-height: 50vh;
}

.preview-media {
  max-width: 100%;
  max-height: 50vh;
  object-fit: contain;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.2s;
  color: white;
}

.photo-preview:hover .preview-overlay {
  opacity: 1;
}

/* Actions bar */
.actions-bar {
  display: flex;
  justify-content: space-around;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: none;
  background: none;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all 0.15s;
}

.action-btn:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.action-btn.active {
  color: #ff453a;
}

/* Info sections */
.info-section {
  padding: 16px;
  border-bottom: 1px solid var(--border);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.edit-btn {
  font-size: 13px;
  color: var(--accent);
  background: none;
  border: none;
  cursor: pointer;
}

/* Rating */
.rating-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.star-btn {
  padding: 4px;
  border: none;
  background: none;
  cursor: pointer;
  transition: transform 0.15s;
}

.star-btn:hover {
  transform: scale(1.1);
}

.clear-btn {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  background: none;
  border: none;
  cursor: pointer;
}

/* Note */
.note-text {
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 8px;
  border-radius: var(--radius-md);
  min-height: 40px;
}

.note-text:hover {
  background: var(--bg-secondary);
}

.note-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
}

.note-input:focus {
  border-color: var(--accent);
}

.note-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn-cancel, .btn-save {
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.btn-cancel {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.btn-save {
  background: var(--accent);
  color: white;
}

/* Caption */
.caption-text {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* People */
.people-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 4px 0;
}

.person-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  background: var(--bg-secondary);
  border-radius: 20px;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
}

.person-chip:hover {
  background: var(--bg-tertiary);
}

.person-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.person-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.person-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

/* Tags */
.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.tag-confidence {
  font-size: 10px;
  opacity: 0.7;
}

/* Info grid */
.info-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.info-label {
  font-size: 13px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

.hash-value {
  font-family: monospace;
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-link {
  display: inline-block;
  margin-left: 8px;
  font-size: 12px;
  color: var(--accent);
  text-decoration: none;
}

.map-link:hover {
  text-decoration: underline;
}

/* Loading */
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
</style>

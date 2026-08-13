<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, useDialog } from '@/utils/feedback'
import { photoApi } from '@/api/photoApi'
import { tagApi } from '@/api/tagApi'
import type { PhotoDetail, Tag } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { ElMessageBox } from 'element-plus'

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
const allTags = ref<Tag[]>([])
const showTagPicker = ref(false)

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

onMounted(async () => {
  await Promise.all([loadPhoto(), loadTags()])
})

async function loadPhoto() {
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
}

async function loadTags() {
  try {
    const { data } = await tagApi.list()
    allTags.value = data
  } catch { /* ignore */ }
}

function goBack() { router.back() }

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

async function toggleFavorite() {
  if (!photo.value) return
  try {
    await photoApi.update(photo.value.id, { favorite: !photo.value.favorite })
    photo.value.favorite = !photo.value.favorite
  } catch { message.error('操作失败') }
}

async function setRating(rating: number) {
  if (!photo.value) return
  const newRating = photo.value.rating === rating ? 0 : rating
  try {
    await photoApi.update(photo.value.id, { rating: newRating })
    photo.value.rating = newRating
  } catch { message.error('设置失败') }
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
  } catch { message.error('保存失败') }
}

async function toggleTimeline() {
  if (!photo.value) return
  try {
    const { data } = await photoApi.toggleTimeline(photo.value.id)
    photo.value.inTimeline = data.inTimeline
    message.success(data.inTimeline ? '已添加到时间线' : '已从时间线移除')
  } catch { message.error('操作失败') }
}

async function addTag(tag: Tag) {
  if (!photo.value) return
  try {
    await tagApi.addToPhoto(photo.value.id, tag.id)
    if (!photo.value.tags.find(t => t.id === tag.id)) {
      photo.value.tags.push(tag)
    }
    message.success(`已添加标签：${tag.name}`)
  } catch { message.error('添加失败') }
}

async function addNewTag() {
  if (!photo.value) return
  let name = ''
  try {
    const result = await ElMessageBox.prompt('请输入标签名称', '新建标签', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '标签名称不能为空',
      customClass: 'mv-message-box',
    })
    name = result.value.trim()
  } catch { return }
  try {
    await tagApi.addByName(photo.value.id, name)
    await loadPhoto()
    await loadTags()
    message.success('标签已创建并关联')
  } catch (e: any) { message.error(e.response?.data?.message || '操作失败') }
}

async function removeTag(tagId: number) {
  if (!photo.value) return
  try {
    await tagApi.removeFromPhoto(photo.value.id, tagId)
    photo.value.tags = photo.value.tags.filter(t => t.id !== tagId)
  } catch { message.error('移除失败') }
}

function confirmDelete() {
  if (!photo.value) return
  dialog.warning({
    title: '移入回收站',
    content: '确定将这张照片移入回收站吗？之后可以从 Dock 回收站恢复。',
    positiveText: '移入回收站',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await photoApi.delete(photo.value!.id)
        message.success('已移入回收站')
        router.back()
      } catch { message.error('删除失败') }
    },
  })
}

const availableTags = computed(() => {
  if (!photo.value) return allTags.value
  const usedIds = photo.value.tags.map(t => t.id)
  return allTags.value.filter(t => !usedIds.includes(t.id))
})

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
    <div class="detail-header glass">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <div class="header-info"><h2 class="header-title">照片详情</h2></div>
      <div class="header-actions">
        <button class="header-btn" @click="downloadPhoto" title="下载">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </button>
        <button class="header-btn danger" @click="confirmDelete" title="删除">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
        </button>
      </div>
    </div>

    <div class="detail-scroll" v-if="photo">
      <!-- Photo preview -->
      <div class="photo-preview" @click="showViewer = true">
        <video v-if="isVideo" :src="photo.originalUrl || photo.thumbnailUrl || ''" class="preview-media" controls playsinline />
        <img v-else-if="isImage" :src="photo.originalUrl || photo.thumbnailUrl || '/placeholder.png'" class="preview-media" />
      </div>

      <!-- Actions bar -->
      <div class="actions-bar">
        <button class="action-btn" :class="{ active: photo.favorite }" @click="toggleFavorite">
          <svg viewBox="0 0 24 24" :fill="photo.favorite ? '#ff453a' : 'none'" :stroke="photo.favorite ? '#ff453a' : 'currentColor'" stroke-width="2" width="24" height="24">
            <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/>
          </svg>
          <span>收藏</span>
        </button>
        <button class="action-btn" :class="{ active: photo.inTimeline }" @click="toggleTimeline">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
            <path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 13l-4-4 1.41-1.41L12 13.17l4.59-4.58L18 10l-6 6z"/>
          </svg>
          <span>{{ photo.inTimeline ? '已加入' : '加入时间线' }}</span>
        </button>
        <button class="action-btn" @click="downloadPhoto">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
          <span>下载</span>
        </button>
      </div>

      <!-- ===== Unified Info Panel ===== -->
      <div class="info-panel">
        <!-- Rating -->
        <div class="info-section">
          <h3 class="section-title">评分</h3>
          <div class="rating-row">
            <button v-for="s in 5" :key="s" class="star-btn" :class="{ active: s <= (hoverRating || photo.rating || 0) }"
              @mouseenter="hoverRating = s" @mouseleave="hoverRating = 0" @click="setRating(s)">
              <svg viewBox="0 0 24 24" :fill="s <= (hoverRating || photo.rating || 0) ? '#ffcc00' : 'none'"
                :stroke="s <= (hoverRating || photo.rating || 0) ? '#ffcc00' : 'currentColor'" stroke-width="2" width="28" height="28">
                <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
              </svg>
            </button>
            <button v-if="photo.rating" class="clear-btn" @click="setRating(0)">清除</button>
          </div>
        </div>

        <!-- Description -->
        <div class="info-section">
          <div class="section-header">
            <h3 class="section-title">描述</h3>
            <button v-if="!editing" class="edit-btn" @click="startEditNote">编辑</button>
          </div>
          <div v-if="editing" class="note-edit">
            <el-input v-model="editNote" class="note-input" type="textarea" :rows="3" placeholder="添加描述..." maxlength="2000" show-word-limit />
            <div class="note-actions">
              <button class="btn-cancel" @click="editing = false">取消</button>
              <button class="btn-save" @click="saveNote">保存</button>
            </div>
          </div>
          <p v-else class="note-text" @click="startEditNote">{{ photo.note || '点击添加描述...' }}</p>
        </div>

        <!-- AI Caption -->
        <div v-if="photo.aiCaption" class="info-section">
          <h3 class="section-title">AI 描述</h3>
          <p class="caption-text">{{ photo.aiCaption }}</p>
        </div>

        <!-- Tags -->
        <div class="info-section">
          <div class="section-header">
            <h3 class="section-title">标签</h3>
            <button class="edit-btn" @click="showTagPicker = !showTagPicker">+ 添加</button>
          </div>
          <div v-if="showTagPicker" class="tag-picker">
            <div v-if="availableTags.length > 0" class="tag-picker-list">
              <button v-for="t in availableTags" :key="t.id" class="tag-pick-btn"
                :style="{ borderColor: t.color || 'transparent', color: t.color || 'inherit' }"
                @click="addTag(t)">{{ t.name }}</button>
            </div>
            <button class="tag-new-btn" @click="addNewTag">+ 新建标签</button>
          </div>
          <div v-if="photo.tags && photo.tags.length > 0" class="tags-row">
            <span v-for="tag in photo.tags" :key="tag.id" class="tag-chip"
              :style="tag.color ? { borderColor: tag.color, color: tag.color } : {}">
              {{ tag.name }}
              <button class="tag-remove" @click="removeTag(tag.id)">×</button>
            </span>
          </div>
          <p v-else class="note-text">暂无标签</p>
        </div>

        <!-- Timeline status -->
        <div class="info-section">
          <div class="section-header">
            <h3 class="section-title">时间线</h3>
            <button class="edit-btn" @click="toggleTimeline">{{ photo.inTimeline ? '移除' : '添加' }}</button>
          </div>
          <p class="info-status-text">
            {{ photo.inTimeline ? '✓ 已加入时间线' : '未加入时间线' }}
          </p>
        </div>

        <!-- File info -->
        <div class="info-section">
          <h3 class="section-title">文件信息</h3>
          <div class="info-grid">
            <div class="info-item" v-if="photo.originalFilename">
              <span class="info-label">文件名</span><span class="info-value">{{ photo.originalFilename }}</span>
            </div>
            <div class="info-item" v-if="photo.filePath">
              <span class="info-label">文件路径</span><span class="info-value hash-value">{{ photo.filePath }}</span>
            </div>
            <div class="info-item" v-if="formattedSize">
              <span class="info-label">文件大小</span><span class="info-value">{{ formattedSize }}</span>
            </div>
            <div class="info-item" v-if="photo.width && photo.height">
              <span class="info-label">图片尺寸</span><span class="info-value">{{ photo.width }} × {{ photo.height }}</span>
            </div>
            <div class="info-item" v-if="photo.sourceFolderName">
              <span class="info-label">所属目录</span><span class="info-value">{{ photo.sourceFolderName }}</span>
            </div>
          </div>
        </div>

        <!-- EXIF info -->
        <div class="info-section">
          <h3 class="section-title">拍摄信息</h3>
          <div class="info-grid">
            <div class="info-item" v-if="photo.exifDate">
              <span class="info-label">拍摄时间</span><span class="info-value">{{ formatDateTime(photo.exifDate) }}</span>
            </div>
            <div class="info-item" v-if="photo.createdAt">
              <span class="info-label">创建时间</span><span class="info-value">{{ formatDateTime(photo.createdAt) }}</span>
            </div>
            <div class="info-item" v-if="photo.gpsLat">
              <span class="info-label">GPS 信息</span><span class="info-value">
                {{ photo.gpsLat.toFixed(6) }}, {{ photo.gpsLng?.toFixed(6) }}
                <a :href="`https://maps.google.com/?q=${photo.gpsLat},${photo.gpsLng}`" target="_blank" class="map-link">查看地图</a>
              </span>
            </div>
            <div class="info-item" v-if="photo.fileHashMd5">
              <span class="info-label">MD5</span><span class="info-value hash-value">{{ photo.fileHashMd5 }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-state"><div class="loading-spinner"></div></div>

    <PhotoViewer v-model:show="showViewer" :photos="(photo ? [photo] : []) as any[]" :initial-index="0" />
  </div>
</template>

<style scoped>
.detail-view { position: relative; min-height: calc(100vh - var(--top-bar-height)); }

.detail-header {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
  border-bottom: 0.5px solid var(--glass-border);
  position: sticky; top: var(--top-bar-height); z-index: 10;
  background: var(--bg-primary); backdrop-filter: blur(20px);
}

.back-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 50%; color: var(--accent); cursor: pointer; flex-shrink: 0; }
.back-btn:active { background: var(--bg-tertiary); }
.header-info { flex: 1; }
.header-title { font-size: 17px; font-weight: 600; }
.header-actions { display: flex; gap: 4px; }
.header-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 50%; color: var(--text-secondary); cursor: pointer; transition: all 0.2s; }
.header-btn:hover { background: var(--bg-tertiary); color: var(--text-primary); }
.header-btn.danger:hover { color: var(--danger); background: rgba(255, 69, 58, 0.1); }
.detail-scroll { padding-bottom: 24px; }

.photo-preview { position: relative; background: #000; cursor: pointer; display: flex; align-items: center; justify-content: center; min-height: 200px; max-height: 50vh; }
.preview-media { max-width: 100%; max-height: 50vh; object-fit: contain; }

.actions-bar { display: flex; justify-content: space-around; padding: 12px 16px; border-bottom: 1px solid var(--border); }
.action-btn { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 8px 16px; border: none; background: none; color: var(--text-secondary); font-size: 12px; cursor: pointer; border-radius: var(--radius-md); transition: all 0.15s; }
.action-btn:hover { background: var(--bg-secondary); color: var(--text-primary); }
.action-btn.active { color: #ff453a; }

.info-panel { padding: 0 16px; }
.info-section { padding: 16px 0; border-bottom: 1px solid var(--border); }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.section-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.section-header .section-title { margin-bottom: 0; }
.edit-btn { font-size: 13px; color: var(--accent); background: none; border: none; cursor: pointer; }

.rating-row { display: flex; align-items: center; gap: 4px; }
.star-btn { padding: 4px; border: none; background: none; cursor: pointer; transition: transform 0.15s; }
.star-btn:hover { transform: scale(1.1); }
.clear-btn { margin-left: 8px; font-size: 12px; color: var(--text-tertiary); background: none; border: none; cursor: pointer; }

.note-text { font-size: 14px; color: var(--text-secondary); cursor: pointer; padding: 8px; border-radius: var(--radius-md); min-height: 40px; }
.note-text:hover { background: var(--bg-secondary); }
.note-edit { display: flex; flex-direction: column; gap: 8px; }
.note-input { width: 100%; }
.note-input :deep(.el-textarea__inner) { border-radius: var(--radius-md); }
.note-actions { display: flex; justify-content: flex-end; gap: 8px; }
.btn-cancel, .btn-save { padding: 6px 14px; border-radius: var(--radius-md); font-size: 13px; border: none; cursor: pointer; }
.btn-cancel { background: var(--bg-tertiary); color: var(--text-secondary); }
.btn-save { background: var(--accent); color: white; }

.caption-text { font-size: 14px; color: var(--text-secondary); line-height: 1.5; }

.tag-picker { margin-bottom: 8px; padding: 8px; background: var(--bg-secondary); border-radius: var(--radius-md); }
.tag-picker-list { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.tag-pick-btn { padding: 4px 10px; border: 1px solid var(--border); border-radius: 12px; font-size: 12px; cursor: pointer; transition: background 0.15s; }
.tag-pick-btn:hover { background: var(--bg-tertiary); }
.tag-new-btn { font-size: 13px; color: var(--accent); background: none; border: none; cursor: pointer; }

.tags-row { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-chip { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 12px; font-size: 12px; color: var(--text-secondary); }
.tag-remove { font-size: 14px; line-height: 1; cursor: pointer; opacity: 0.6; }
.tag-remove:hover { opacity: 1; }

.info-status-text { font-size: 14px; color: var(--text-secondary); }

.info-grid { display: flex; flex-direction: column; gap: 8px; }
.info-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.info-label { font-size: 13px; color: var(--text-tertiary); flex-shrink: 0; }
.info-value { font-size: 13px; color: var(--text-primary); text-align: right; word-break: break-all; }
.hash-value { font-family: monospace; font-size: 11px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; }
.map-link { display: inline-block; margin-left: 8px; font-size: 12px; color: var(--accent); text-decoration: none; }

.loading-state { display: flex; justify-content: center; padding: 80px 0; }
.loading-spinner { width: 24px; height: 24px; border: 2.5px solid var(--bg-tertiary); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Grid as GridIcon, List as ListIcon } from '@element-plus/icons-vue'
import { photoApi } from '@/api/photoApi'
import type { Photo } from '@/types'

defineProps<{ compact?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const photos = ref<Photo[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 12
const restoringAll = ref(false)
const clearingAll = ref(false)
const batchAction = ref<'restore' | 'delete' | null>(null)
const selectedIds = ref(new Set<number>())
const viewMode = ref<'list' | 'card'>(localStorage.getItem('trash-view-mode') === 'card' ? 'card' : 'list')
const selectedCount = computed(() => selectedIds.value.size)
const hasTrashItems = computed(() => total.value > 0 || photos.value.length > 0)

function setViewMode(mode: 'list' | 'card') {
  viewMode.value = mode
  localStorage.setItem('trash-view-mode', mode)
}

function isSelected(id: number) {
  return selectedIds.value.has(id)
}

function toggleSelection(id: number) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
}

async function loadTrash() {
  loading.value = true
  try {
    const { data } = await photoApi.trash(page.value - 1, pageSize)
    photos.value = data.content
    total.value = data.totalElements
  } catch { ElMessage.error('加载回收站失败') }
  finally { loading.value = false }
}

async function restore(photo: Photo) {
  await photoApi.restore(photo.id)
  const next = new Set(selectedIds.value)
  next.delete(photo.id)
  selectedIds.value = next
  ElMessage.success('照片已恢复')
  emit('changed')
  await loadTrash()
}

async function remove(photo: Photo) {
  try {
    await ElMessageBox.confirm(`将永久删除“${photo.originalFilename || '这张照片'}”及其媒体文件，此操作不可恢复。`, '永久删除', { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消' })
  } catch { return }
  await photoApi.permanentDelete(photo.id)
  const next = new Set(selectedIds.value)
  next.delete(photo.id)
  selectedIds.value = next
  ElMessage.success('照片已永久删除')
  emit('changed')
  await loadTrash()
}

async function restoreSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  batchAction.value = 'restore'
  try {
    const { data } = await photoApi.restoreTrashByIds(ids)
    ElMessage.success(`已恢复 ${data.restored} 个选中项目`)
    clearSelection()
    emit('changed')
    await loadTrash()
  } catch { ElMessage.error('恢复选中项目失败') }
  finally { batchAction.value = null }
}

async function deleteSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`将永久删除选中的 ${ids.length} 个项目及其媒体文件，此操作不可恢复。`, '删除选中项目', {
      type: 'error',
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
    })
  } catch { return }

  batchAction.value = 'delete'
  try {
    const { data } = await photoApi.permanentDeleteTrashByIds(ids)
    if (data.fail) ElMessage.warning(`已删除 ${data.success} 个项目，${data.fail} 个删除失败`)
    else ElMessage.success(`已删除 ${data.success} 个选中项目`)
    clearSelection()
    emit('changed')
    await loadTrash()
  } catch { ElMessage.error('删除选中项目失败') }
  finally { batchAction.value = null }
}

async function restoreAll() {
  try {
    await ElMessageBox.confirm(`确定恢复回收站中的 ${total.value} 个项目吗？`, '恢复全部', {
      type: 'info',
      confirmButtonText: '恢复全部',
      cancelButtonText: '取消',
    })
  } catch { return }

  restoringAll.value = true
  try {
    const { data } = await photoApi.restoreAllTrash()
    ElMessage.success(`已恢复 ${data.restored} 个项目`)
    page.value = 1
    emit('changed')
    await loadTrash()
  } catch { ElMessage.error('恢复全部失败') }
  finally { restoringAll.value = false }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm(`将永久删除回收站中的 ${total.value} 个项目及其媒体文件，此操作不可恢复。`, '清空回收站', { type: 'error', confirmButtonText: '清空', cancelButtonText: '取消' })
  } catch { return }
  clearingAll.value = true
  try {
    const { data } = await photoApi.clearTrash()
    ElMessage.success(`已永久删除 ${data.deleted} 个项目`)
    page.value = 1
    emit('changed')
    await loadTrash()
  } catch { ElMessage.error('清空回收站失败') }
  finally { clearingAll.value = false }
}

onMounted(loadTrash)
</script>

<template>
  <section class="recycle-panel" :class="{ compact }" v-loading="loading">
    <div class="trash-toolbar">
      <div class="trash-toolbar-meta">
        <span>{{ selectedCount ? `已选 ${selectedCount} 项` : `共 ${total} 个项目` }}</span>
        <el-button-group class="view-switch" aria-label="切换回收站视图">
          <el-button size="small" :type="viewMode === 'list' ? 'primary' : 'default'" :icon="ListIcon" aria-label="列表视图" title="列表视图" @click="setViewMode('list')" />
          <el-button size="small" :type="viewMode === 'card' ? 'primary' : 'default'" :icon="GridIcon" aria-label="卡片视图" title="卡片视图" @click="setViewMode('card')" />
        </el-button-group>
      </div>
      <div v-if="selectedCount" class="trash-bulk-actions selected-actions" aria-label="选中项目操作">
        <el-button type="primary" plain size="small" :loading="batchAction === 'restore'" :disabled="batchAction !== null" @click="restoreSelected"><el-icon><RefreshLeft /></el-icon><span>恢复选中</span></el-button>
        <el-button type="danger" plain size="small" :loading="batchAction === 'delete'" :disabled="batchAction !== null" @click="deleteSelected"><el-icon><Delete /></el-icon><span>删除选中</span></el-button>
        <el-button text size="small" :disabled="batchAction !== null" @click="clearSelection">取消</el-button>
      </div>
      <div v-else class="trash-bulk-actions" aria-label="回收站批量操作">
        <el-button type="primary" plain size="small" :loading="restoringAll" :disabled="!hasTrashItems || clearingAll" @click="restoreAll"><el-icon><RefreshLeft /></el-icon><span>恢复全部</span></el-button>
        <el-button type="danger" plain size="small" :loading="clearingAll" :disabled="!hasTrashItems || restoringAll" @click="clearAll"><el-icon><Delete /></el-icon><span>清空回收站</span></el-button>
      </div>
    </div>
    <el-empty v-if="!loading && !photos.length" description="回收站是空的" :image-size="72" />
    <div v-else class="trash-list" :class="`is-${viewMode}`">
      <article v-for="photo in photos" :key="photo.id" class="trash-item" :class="{ selected: isSelected(photo.id) }" @click="viewMode === 'card' && toggleSelection(photo.id)">
        <div class="trash-thumb">
          <img v-if="photo.thumbnailUrl" :src="photo.thumbnailUrl" alt="" />
          <el-icon v-else><Picture /></el-icon>
          <el-button class="select-photo" :class="{ selected: isSelected(photo.id) }" circle size="small" :type="isSelected(photo.id) ? 'primary' : 'default'" :aria-label="isSelected(photo.id) ? '取消选择' : '选择图片'" @click.stop="toggleSelection(photo.id)">
            <el-icon v-if="isSelected(photo.id)"><Check /></el-icon>
          </el-button>
          <span v-if="isSelected(photo.id)" class="selected-overlay"><el-icon><Check /></el-icon></span>
        </div>
        <div v-if="viewMode === 'list'" class="trash-copy"><strong>{{ photo.originalFilename || `照片 #${photo.id}` }}</strong><small>{{ photo.deletedAt ? new Date(photo.deletedAt).toLocaleString('zh-CN') : '' }}</small></div>
        <div class="trash-actions"><el-button type="primary" text size="small" @click.stop="restore(photo)">恢复</el-button><el-button type="danger" text size="small" @click.stop="remove(photo)">删除</el-button></div>
      </article>
    </div>
    <el-pagination v-if="total > pageSize" v-model:current-page="page" small layout="prev, pager, next" :page-size="pageSize" :total="total" @current-change="loadTrash" />
  </section>
</template>

<style scoped>
.recycle-panel {
  min-height: 180px;
}

.trash-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.trash-toolbar-meta,
.trash-bulk-actions,
.trash-actions {
  display: flex;
  align-items: center;
}

.trash-toolbar-meta {
  gap: 9px;
  white-space: nowrap;
}

.trash-bulk-actions,
.trash-actions {
  gap: 5px;
}

.view-switch {
  padding: 2px;
  border: 1px solid var(--separator);
  border-radius: 8px;
  background: color-mix(in srgb, var(--bg-tertiary) 72%, transparent);
}

.view-switch :deep(.el-button) {
  width: 25px;
  min-width: 25px;
  height: 23px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  box-shadow: none;
  color: var(--text-secondary);
}

.view-switch :deep(.el-button--primary) {
  background: var(--bg-card);
  color: var(--accent);
  box-shadow: 0 1px 4px var(--shadow-color);
}

.trash-bulk-actions :deep(.el-button) {
  height: 28px;
  margin-left: 0;
  padding: 0 9px;
  border-radius: 8px;
  box-shadow: none;
  font-weight: 600;
}

.trash-bulk-actions :deep(.el-button--primary.is-plain) {
  --el-button-text-color: var(--accent);
  --el-button-bg-color: color-mix(in srgb, var(--accent) 8%, transparent);
  --el-button-border-color: color-mix(in srgb, var(--accent) 24%, transparent);
  --el-button-hover-text-color: var(--accent);
  --el-button-hover-bg-color: color-mix(in srgb, var(--accent) 14%, transparent);
  --el-button-hover-border-color: color-mix(in srgb, var(--accent) 38%, transparent);
}

.trash-bulk-actions :deep(.el-button--danger.is-plain) {
  --el-button-text-color: var(--danger);
  --el-button-bg-color: color-mix(in srgb, var(--danger) 7%, transparent);
  --el-button-border-color: color-mix(in srgb, var(--danger) 22%, transparent);
  --el-button-hover-text-color: var(--danger);
  --el-button-hover-bg-color: color-mix(in srgb, var(--danger) 13%, transparent);
  --el-button-hover-border-color: color-mix(in srgb, var(--danger) 36%, transparent);
}

.trash-bulk-actions :deep(.el-button.is-text) {
  color: var(--text-secondary);
}

.trash-list {
  display: grid;
  gap: 7px;
  max-height: 360px;
  overflow: auto;
  padding: 1px;
}

.trash-item {
  position: relative;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 7px;
  border: 1px solid var(--separator);
  border-radius: 11px;
  background: var(--bg-card);
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.trash-item.selected {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft, rgba(10, 132, 255, .18));
}

.trash-thumb {
  position: relative;
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  overflow: hidden;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-tertiary);
}

.trash-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.select-photo {
  position: absolute;
  top: 5px;
  left: 5px;
  z-index: 4;
  width: 20px;
  min-width: 20px;
  height: 20px;
  margin: 0;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, .88);
  background: rgba(20, 28, 40, .24);
  box-shadow: 0 1px 5px rgba(0, 0, 0, .2);
  color: #fff;
  backdrop-filter: blur(6px);
}

.select-photo.selected {
  border-color: color-mix(in srgb, var(--accent) 72%, #fff);
  background: var(--accent);
  color: #fff;
}

.selected-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--accent) 27%, transparent);
  color: #fff;
  font-size: 23px;
  pointer-events: none;
}

.trash-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.trash-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trash-copy small {
  color: var(--text-tertiary);
  font-size: 9px;
}

.trash-actions :deep(.el-button) {
  height: 25px;
  margin-left: 0;
  padding: 0 6px;
  border-radius: 7px;
  font-weight: 600;
}

.trash-actions :deep(.el-button--primary.is-text) {
  color: var(--accent);
}

.trash-actions :deep(.el-button--danger.is-text) {
  color: var(--danger);
}

.is-card {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}

.is-card .trash-item {
  display: block;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
}

.is-card .trash-item:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.is-card .trash-thumb {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  border-radius: 10px;
}

.is-card .trash-actions {
  position: absolute;
  right: 5px;
  bottom: 5px;
  z-index: 4;
  gap: 3px;
  opacity: 0;
  transition: opacity .18s ease;
}

.is-card .trash-item:hover .trash-actions,
.is-card .trash-item:focus-within .trash-actions,
.is-card .trash-item.selected .trash-actions {
  opacity: 1;
}

.is-card .trash-actions :deep(.el-button) {
  min-width: 0;
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--glass-border);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  box-shadow: none;
  backdrop-filter: blur(10px);
}

.recycle-panel :deep(.el-pagination) {
  justify-content: center;
  margin-top: 10px;
}

.compact .trash-list {
  max-height: 330px;
}

@media (max-width: 620px) {
  .trash-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .trash-toolbar-meta {
    justify-content: space-between;
  }

  .trash-bulk-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .selected-actions {
    grid-template-columns: 1fr 1fr auto;
  }

  .trash-bulk-actions :deep(.el-button) {
    width: 100%;
  }

  .is-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .is-list .trash-item {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .is-list .trash-actions {
    grid-column: 2;
  }

  .is-card .trash-actions {
    grid-column: auto;
  }

  .selected-actions {
    grid-template-columns: 1fr 1fr;
  }

  .selected-actions :deep(.el-button:last-child) {
    grid-column: 1 / -1;
  }
}
</style>

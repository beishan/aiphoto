<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { photoApi } from '@/api/photoApi'
import type { Photo } from '@/types'

defineProps<{ compact?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const photos = ref<Photo[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 12

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
  ElMessage.success('照片已恢复')
  emit('changed')
  await loadTrash()
}

async function remove(photo: Photo) {
  try {
    await ElMessageBox.confirm(`将永久删除“${photo.originalFilename || '这张照片'}”及其媒体文件，此操作不可恢复。`, '永久删除', { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消' })
  } catch { return }
  await photoApi.permanentDelete(photo.id)
  ElMessage.success('照片已永久删除')
  emit('changed')
  await loadTrash()
}

async function clearAll() {
  try {
    await ElMessageBox.confirm(`将永久删除回收站中的 ${total.value} 个项目及其媒体文件，此操作不可恢复。`, '清空回收站', { type: 'error', confirmButtonText: '清空', cancelButtonText: '取消' })
  } catch { return }
  await photoApi.clearTrash()
  ElMessage.success('回收站已清空')
  page.value = 1
  emit('changed')
  await loadTrash()
}

onMounted(loadTrash)
</script>

<template>
  <section class="recycle-panel" :class="{ compact }" v-loading="loading">
    <div class="trash-toolbar">
      <span>共 {{ total }} 个项目</span>
      <el-button v-if="total" type="danger" link @click="clearAll">清空回收站</el-button>
    </div>
    <el-empty v-if="!loading && !photos.length" description="回收站是空的" :image-size="72" />
    <div v-else class="trash-list">
      <article v-for="photo in photos" :key="photo.id" class="trash-item">
        <div class="trash-thumb"><img v-if="photo.thumbnailUrl" :src="photo.thumbnailUrl" alt="" /><el-icon v-else><Picture /></el-icon></div>
        <div class="trash-copy"><strong>{{ photo.originalFilename || `照片 #${photo.id}` }}</strong><small>{{ photo.deletedAt ? new Date(photo.deletedAt).toLocaleString('zh-CN') : '' }}</small></div>
        <div class="trash-actions"><el-button type="primary" link @click="restore(photo)">恢复</el-button><el-button type="danger" link @click="remove(photo)">删除</el-button></div>
      </article>
    </div>
    <el-pagination v-if="total > pageSize" v-model:current-page="page" small layout="prev, pager, next" :page-size="pageSize" :total="total" @current-change="loadTrash" />
  </section>
</template>

<style scoped>
.recycle-panel{min-height:180px}.trash-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;color:var(--text-secondary);font-size:12px}.trash-list{display:grid;gap:7px;max-height:360px;overflow:auto}.trash-item{display:grid;grid-template-columns:46px minmax(0,1fr) auto;align-items:center;gap:10px;padding:7px;border:1px solid var(--separator);border-radius:11px;background:var(--bg-card)}.trash-thumb{display:grid;width:46px;height:46px;place-items:center;overflow:hidden;border-radius:8px;background:var(--bg-tertiary);color:var(--text-tertiary)}.trash-thumb img{width:100%;height:100%;object-fit:cover}.trash-copy{display:grid;min-width:0;gap:3px}.trash-copy strong{overflow:hidden;color:var(--text-primary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.trash-copy small{color:var(--text-tertiary);font-size:9px}.trash-actions{display:flex}.recycle-panel :deep(.el-pagination){justify-content:center;margin-top:10px}.compact .trash-list{max-height:330px}@media(max-width:540px){.trash-item{grid-template-columns:42px minmax(0,1fr)}.trash-actions{grid-column:2}}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { crawlApi } from '@/api/crawlApi'
import { allTaskImportIds, isTaskAssetImportable } from '@/utils/taskGallery'
import { createThumbnailQueue } from '@/utils/thumbnailQueue'
import type { CrawlAsset } from '@/types'

function notify(type: 'info' | 'success' | 'warning' | 'error', message: string) {
  ElMessage({ type, message, customClass: 'task-gallery-message' })
}
const props = defineProps<{ jobId: number; albumId: number | null; tagIds: number[]; pendingIds: Set<number> }>()
const emit = defineEmits<{ close: []; submitted: [batchId: number, ids: number[]] }>()
const items = ref<CrawlAsset[]>([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const selected = ref(new Set<number>())
const thumbs = reactive<Record<number, string>>({})
const preview = ref<CrawlAsset | null>(null)
const previewUrl = ref('')
const previewLoading = ref(false)
let alive = true
let generation = 0
let previewController: AbortController | undefined
let previewObjectUrl = ''
let importKey: { signature: string; key: string } | null = null
const canImport = (asset: CrawlAsset) => isTaskAssetImportable(asset, props.pendingIds)
const visibleItems = computed(() => items.value.filter(canImport))
const pageSelectable = visibleItems
const pageSelected = computed(() => pageSelectable.value.length > 0 && pageSelectable.value.every(asset => selected.value.has(asset.id)))
const queue = createThumbnailQueue(async (id, signal) => {
  const { data } = await crawlApi.thumbnail(id, signal)
  if (signal.aborted || !alive) return
  thumbs[id] = URL.createObjectURL(data)
}, 4)
function clearThumbs() {
  queue.cancel()
  for (const key of Object.keys(thumbs)) { URL.revokeObjectURL(thumbs[Number(key)]); delete thumbs[Number(key)] }
}
async function load() {
  const request = ++generation
  loading.value = true
  error.value = ''
  clearThumbs()
  try {
    const { data } = await crawlApi.assets(props.jobId, page.value, 60, 'DOWNLOADED')
    if (!alive || request !== generation) return
    total.value = data.totalElements
    const lastPage = Math.max(0, Math.ceil(data.totalElements / 60) - 1)
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
      return
    }
    const refreshedIds = new Set(data.content.filter(canImport).map(asset => asset.id))
    items.value = data.content
    if (preview.value && !refreshedIds.has(preview.value.id)) closePreview()
    for (const asset of items.value) if (['DOWNLOADED', 'IMPORTED', 'DUPLICATE'].includes(asset.status)) void queue.enqueue(asset.id)
  } catch (e: any) { if (alive && request === generation) error.value = e?.response?.data?.message || '任务图片加载失败，请重试' }
  finally { if (alive && request === generation) loading.value = false }
}
function toggle(id: number, checked: boolean) {
  const next = new Set(selected.value)
  checked ? next.add(id) : next.delete(id)
  selected.value = next
}
function selectPage(checked: boolean) { for (const asset of pageSelectable.value) toggle(asset.id, checked) }
async function importImages(all: boolean) {
  if (submitting.value) return
  submitting.value = true
  try {
    let ids = [...selected.value].filter(id => !props.pendingIds.has(id))
    if (all) {
      ids = await allTaskImportIds(async number => (await crawlApi.assets(props.jobId, number, 200, 'DOWNLOADED')).data, canImport, () => alive)
    }
    if (!ids.length) { notify('info', '没有可入库的图片'); return }
    const { data: summary } = await crawlApi.importPreview(props.jobId, ids)
    if (!alive) return
    try {
      await ElMessageBox.confirm('已选择 ' + ids.length + ' 张，预计新增 ' + summary.importable + ' 张，重复 ' + summary.duplicates + ' 张，已入库 ' + summary.alreadyImported + ' 张，不可入库 ' + summary.blocked + ' 张。', '确认任务图片入库', { type: 'info', customClass: 'task-gallery-confirm' })
    } catch { return }
    if (!alive) return
    const signature = JSON.stringify([ids.slice().sort((a, b) => a - b), props.albumId, props.tagIds])
    if (importKey?.signature !== signature) importKey = { signature, key: globalThis.crypto?.randomUUID?.() || Date.now() + '-' + Math.random().toString(36).slice(2) }
    const { data } = await crawlApi.importAssets(props.jobId, ids, props.albumId, props.tagIds, importKey.key)
    importKey = null
    selected.value = new Set()
    emit('submitted', data.batchId, ids)
    notify('success', '已提交后台入库，可继续浏览')
  } catch (e: any) { notify('error', e?.response?.data?.message || '入库失败，请重试') }
  finally { submitting.value = false; if (alive) void load() }
}
async function openPreview(asset: CrawlAsset) {
  if (!thumbs[asset.id]) return
  previewController?.abort()
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = ''
  preview.value = asset
  previewUrl.value = thumbs[asset.id]
  previewLoading.value = true
  const controller = new AbortController()
  previewController = controller
  try {
    const { data } = await crawlApi.content(asset.id, controller.signal)
    if (!alive || controller.signal.aborted) return
    previewObjectUrl = URL.createObjectURL(data)
    previewUrl.value = previewObjectUrl
  } catch { if (!controller.signal.aborted) notify('warning', '原图加载失败，当前显示缩略图') }
  finally { if (!controller.signal.aborted) previewLoading.value = false }
}
function closePreview() {
  previewController?.abort()
  preview.value = null
  previewUrl.value = ''
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = ''
}
function status(asset: CrawlAsset) {
  if (props.pendingIds.has(asset.id)) return '后台入库中'
  return ({ DOWNLOADED: '待入库', IMPORTED: '已入库', DUPLICATE: '重复图片', PENDING: '待下载', RUNNING: '下载中', FAILED: '下载失败', SKIPPED: '已跳过', DELETED: '已删除' } as Record<string, string>)[asset.status]
}
watch(() => props.pendingIds, () => {
  if (preview.value && props.pendingIds.has(preview.value.id)) closePreview()
  if (!submitting.value) void load()
})
onMounted(load)
onBeforeUnmount(() => { alive = false; generation++; queue.dispose(); clearThumbs(); closePreview() })
</script>

<template>
  <el-dialog :model-value="true" title="当前任务图片" width="min(1180px, 96vw)" append-to-body :z-index="5000" :close-on-click-modal="false" :close-on-press-escape="!submitting && !preview" :show-close="!submitting" @close="!submitting && emit('close')">
    <div class="task-gallery-toolbar">
      <span>任务 #{{ jobId }} · 共 {{ total }} 张 · 已选择 {{ selected.size }} 张</span>
      <el-space wrap>
        <el-checkbox :model-value="pageSelected" :disabled="loading || submitting" @change="selectPage(Boolean($event))">选择本页可入库图片</el-checkbox>
        <el-button :disabled="submitting || !selected.size" @click="selected = new Set()">清空选择</el-button>
        <el-button :disabled="submitting || loading" @click="load">刷新</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!selected.size" @click="importImages(false)">入库所选（{{ selected.size }}）</el-button>
        <el-button type="primary" plain :loading="submitting" :disabled="loading || !total" @click="importImages(true)">全部可入库图片入库</el-button>
      </el-space>
    </div>
    <p class="task-gallery-hint">展示该任务待入库的图片；选择可跨页保留，已提交的图片暂时隐藏，失败后重新显示。“全部”覆盖整个任务，已入库、下载失败和正在入库的图片自动排除。沿用待整理区当前的相册与标签设置。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading" class="task-gallery-grid">
      <article v-for="asset in visibleItems" :key="asset.id" :class="{ selected: selected.has(asset.id) }">
        <button class="task-gallery-image" type="button" :disabled="!thumbs[asset.id]" :aria-label="'预览 ' + asset.originalFilename" @click="openPreview(asset)">
          <img v-if="thumbs[asset.id]" :src="thumbs[asset.id]" :alt="asset.originalFilename" loading="lazy" />
          <span v-else>{{ status(asset) }}</span>
        </button>
        <div class="task-gallery-copy"><el-checkbox :model-value="selected.has(asset.id)" :disabled="!canImport(asset) || submitting" :aria-label="'选择 ' + asset.originalFilename" @change="toggle(asset.id, Boolean($event))">{{ status(asset) }}</el-checkbox><strong :title="asset.originalFilename">{{ asset.originalFilename }}</strong><small>{{ asset.width || '—' }} × {{ asset.height || '—' }}</small></div>
      </article>
    </div>
    <el-empty v-if="!loading && !visibleItems.length && !error" :description="items.some(asset => pendingIds.has(asset.id)) ? '当前页图片正在后台入库，完成后自动刷新' : '当前任务暂无待入库图片'" />
    <el-pagination v-if="total > 60" :current-page="page + 1" :page-size="60" :total="total" :disabled="submitting || loading" layout="prev, pager, next, total" @current-change="page = $event - 1; load()" />
    <el-dialog :model-value="!!preview" :title="preview?.originalFilename || '图片预览'" width="min(1080px, 96vw)" append-to-body :z-index="5100" @close="closePreview">
      <div class="task-gallery-preview" v-loading="previewLoading"><img v-if="previewUrl" :src="previewUrl" :alt="preview?.originalFilename" /></div>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.task-gallery-toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.task-gallery-toolbar>span{font-weight:600}.task-gallery-hint{color:var(--text-secondary);font-size:12px;line-height:1.7}.task-gallery-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:14px;max-height:62vh;overflow:auto;padding:4px;margin:16px 0;min-height:160px}.task-gallery-grid article{border:2px solid transparent;border-radius:14px;overflow:hidden;background:var(--bg-secondary)}.task-gallery-grid article.selected{border-color:var(--accent)}.task-gallery-image{display:grid;place-items:center;width:100%;height:160px;padding:0;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.task-gallery-image img{width:100%;height:100%;object-fit:cover}.task-gallery-copy{display:grid;gap:5px;padding:10px}.task-gallery-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.task-gallery-copy small{color:var(--text-secondary)}.task-gallery-preview{height:72vh;display:grid;place-items:center}.task-gallery-preview img{max-width:100%;max-height:72vh;object-fit:contain}
</style>
<style>
.el-message.task-gallery-message{z-index:5400!important}
.el-overlay:has(.task-gallery-confirm){z-index:5300!important}
</style>

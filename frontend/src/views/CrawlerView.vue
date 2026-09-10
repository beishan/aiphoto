<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { crawlApi } from '@/api/crawlApi'
import type { CrawlImportBatch, CrawlImportItem } from '@/api/crawlApi'
import { albumApi } from '@/api/albumApi'
import { tagApi } from '@/api/tagApi'
import type { Album, CrawlAsset, CrawlJob, CrawlPage, CrawlRule, Tag } from '@/types'

const rules = ref<CrawlRule[]>([])
const jobs = ref<CrawlJob[]>([])
const pages = ref<CrawlPage[]>([])
const assets = ref<CrawlAsset[]>([])
const activeJobId = ref<number | null>(null)
const selectedAssets = ref(new Set<number>())
const previewUrls = ref<string[]>([])
const previewImageUrls = ref<string[]>([])
const previewImageError = ref('')
const previewImages = reactive<Record<number, string>>({})
const busy = ref('')
const albums = ref<Album[]>([])
const tags = ref<Tag[]>([])
const targetAlbumId = ref<number | null>(null)
const targetTagIds = ref<number[]>([])
const batchNote = ref('')
const assetPage = ref(0)
const assetPageSize = 60
const assetTotal = ref(0)
const assetStatus = ref('')
const exactDuplicateOnly = ref(false)
const similarOnly = ref(false)
const similarityThreshold = ref(8)
const pagePage = ref(0)
const pagePageSize = 50
const pageTotal = ref(0)
const includedPageTotal = ref(0)
const pageQuery = ref('')
const recentImportBatch = ref<CrawlImportBatch | null>(null)
const recentImportItems = ref<CrawlImportItem[]>([])
const importItemStatus = ref('')
const pendingImport = ref<{ signature: string; key: string } | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive<CrawlRule>({
  name: '', startUrl: '', detailSelector: '', detailUrlIncludes: '', detailUrlExcludes: '',
  nextSelector: '', imageSelector: 'img', imageAttributes: 'data-original,data-src,srcset,src',
  imageUrlIncludes: '', imageUrlExcludes: '', allowedHosts: '', maxListPages: 100,
  detailNextSelector: '', maxPagesPerDetail: 20, maxDetailPages: 1000, maxImages: 5000,
  maxFileBytes: 20 * 1024 * 1024,
})

const activeJob = computed(() => jobs.value.find(job => job.id === activeJobId.value) || null)
const reviewAssets = computed(() => assets.value.filter(asset => asset.status !== 'DELETED'))
const deletedAssets = computed(() => assets.value.filter(asset => asset.status === 'DELETED'))
const duplicateHashes = computed(() => {
  return new Set(reviewAssets.value
    .filter(asset => asset.fileHashMd5 && asset.exactDuplicateCount > 1)
    .map(asset => asset.fileHashMd5))
})

async function loadRules() { rules.value = (await crawlApi.rules()).data }
async function loadJobs() {
  jobs.value = (await crawlApi.jobs()).data
  if (!activeJobId.value && jobs.value.length) activeJobId.value = jobs.value[0].id
}

async function loadJobDetail() {
  if (!activeJobId.value) return
  const [jobResult, pageResult, assetResult] = await Promise.all([
    crawlApi.job(activeJobId.value),
    crawlApi.pages(activeJobId.value, pagePage.value, pagePageSize, pageQuery.value),
    crawlApi.assets(activeJobId.value, assetPage.value, assetPageSize, assetStatus.value, exactDuplicateOnly.value, similarOnly.value),
  ])
  const index = jobs.value.findIndex(job => job.id === jobResult.data.id)
  if (index >= 0) jobs.value[index] = jobResult.data
  pages.value = pageResult.data.content
  pageTotal.value = pageResult.data.totalElements
  includedPageTotal.value = pageResult.data.includedElements
  assets.value = assetResult.data.content
  assetTotal.value = assetResult.data.totalElements
  for (const key of Object.keys(previewImages)) {
    const id = Number(key)
    if (!assets.value.some(asset => asset.id === id)) {
      URL.revokeObjectURL(previewImages[id])
      delete previewImages[id]
    }
  }
  selectedAssets.value = new Set([...selectedAssets.value].filter(id => assets.value.some(asset => asset.id === id)))
  await loadPreviews()
}

async function loadPreviews() {
  const wanted = reviewAssets.value.filter(asset => asset.status !== 'FAILED' && !previewImages[asset.id]).slice(0, 80)
  await Promise.all(wanted.map(async asset => {
    try {
      const { data } = await crawlApi.thumbnail(asset.id)
      previewImages[asset.id] = URL.createObjectURL(data)
    } catch { /* failed assets intentionally have no preview */ }
  }))
}

async function saveRule(showMessage = true) {
  if (!form.name || !form.startUrl || !form.detailSelector) {
    ElMessage.warning('请填写名称、起始 URL 和图片页选择器')
    return null
  }
  if (!form.allowedHosts) {
    try { form.allowedHosts = new URL(form.startUrl).hostname } catch { /* backend validates */ }
  }
  const { data } = await crawlApi.saveRule({ ...form })
  Object.assign(form, data)
  await loadRules()
  if (showMessage) ElMessage.success('规则已保存')
  return data
}

async function testRule() {
  busy.value = 'preview'
  try {
    const { data } = await crawlApi.preview({ ...form })
    Object.assign(form, data.rule)
    previewUrls.value = data.detailUrls
    previewImageUrls.value = data.imageUrls
    previewImageError.value = data.imagePreviewError || ''
    await loadRules()
    data.detailUrls.length ? ElMessage.success(`测试命中 ${data.detailUrls.length} 个图片页链接，首页提取 ${data.imageUrls.length} 张图片`)
      : ElMessage.warning('当前页面没有命中图片页链接')
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || error?.message || '规则测试失败') }
  finally { busy.value = '' }
}

function useRule(rule: CrawlRule) {
  Object.assign(form, rule)
  previewUrls.value = []
  previewImageUrls.value = []
  previewImageError.value = ''
}

async function startDiscovery() {
  busy.value = 'discover'
  try {
    const rule = await saveRule(false)
    if (!rule?.id) return
    const { data } = await crawlApi.createJob(rule.id)
    activeJobId.value = data.jobId
    await loadJobs()
    await loadJobDetail()
    ElMessage.success('已开始发现图片页链接')
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || '启动失败') }
  finally { busy.value = '' }
}

async function startDownload() {
  if (!activeJob.value) return
  try { await ElMessageBox.confirm(`已保留 ${includedPageTotal.value} / ${activeJob.value.pagesFound} 个图片页，开始逐页提取并下载图片？`, '开始下载', { type: 'warning' }) }
  catch { return }
  await crawlApi.download(activeJob.value.id)
  await loadJobDetail()
  ElMessage.success('下载任务已开始')
}

async function controlJob(action: 'pause' | 'resume' | 'cancel') {
  if (!activeJob.value) return
  if (action === 'pause') await crawlApi.pause(activeJob.value.id)
  else if (action === 'resume') await crawlApi.resume(activeJob.value.id)
  else await crawlApi.cancel(activeJob.value.id)
  await loadJobs(); await loadJobDetail()
}

async function togglePageIncluded(page: CrawlPage, included: boolean) {
  if (!activeJobId.value) return
  const { data } = await crawlApi.selectPages(activeJobId.value, [page.id], included)
  page.included = included
  includedPageTotal.value = data.included
}

function onPageIncludedChange(page: CrawlPage, value: string | number | boolean) {
  void togglePageIncluded(page, Boolean(value))
}

async function setCurrentPagesIncluded(included: boolean) {
  if (!activeJobId.value || !pages.value.length) return
  const ids = pages.value.filter(page => page.included !== included).map(page => page.id)
  if (!ids.length) return
  const { data } = await crawlApi.selectPages(activeJobId.value, ids, included)
  pages.value.forEach(page => { page.included = included })
  includedPageTotal.value = data.included
}

function searchPages() {
  pagePage.value = 0
  void loadJobDetail()
}

function changePageListPage(page: number) {
  pagePage.value = page - 1
  void loadJobDetail()
}

function toggleAsset(id: number) {
  const next = new Set(selectedAssets.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedAssets.value = next
}

function selectAllDownloadable() {
  selectedAssets.value = new Set(reviewAssets.value.filter(asset => asset.status === 'DOWNLOADED').map(asset => asset.id))
}

async function deleteSelected() {
  if (!activeJobId.value || !selectedAssets.value.size) return
  const { data } = await crawlApi.deleteAssets(activeJobId.value, [...selectedAssets.value])
  ElMessage.success(`已移到待整理区已删除列表：${data.success} 张`)
  selectedAssets.value = new Set()
  await loadJobDetail()
}

async function editSelected() {
  if (!activeJobId.value || !selectedAssets.value.size) return
  const { data } = await crawlApi.editAssets(activeJobId.value, [...selectedAssets.value], batchNote.value)
  ElMessage.success(`已更新 ${data.success} 张图片的备注`)
  await loadJobDetail()
}

async function restoreDeleted(id: number) {
  if (!activeJobId.value) return
  await crawlApi.restoreAssets(activeJobId.value, [id])
  await loadJobDetail()
}

async function analyzeSimilarity() {
  if (!activeJobId.value) return
  busy.value = 'similarity'
  try {
    const { data } = await crawlApi.analyzeSimilarity(activeJobId.value, similarityThreshold.value)
    similarOnly.value = data.groups > 0
    exactDuplicateOnly.value = false
    assetStatus.value = ''
    assetPage.value = 0
    await loadJobDetail()
    if (data.groups) ElMessage.success(`已分析 ${data.analyzed} 张，找到 ${data.groups} 组、共 ${data.matched} 张相似图片`)
    else ElMessage.info(`已分析 ${data.analyzed} 张，没有发现相似图片`)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '相似图片分析失败')
  } finally { busy.value = '' }
}

async function importSelected() {
  if (!activeJobId.value || !selectedAssets.value.size) return
  const ids = [...selectedAssets.value]
  const preview = (await crawlApi.importPreview(activeJobId.value, ids)).data
  const summary = `预计新增 ${preview.importable} 张，图库重复 ${preview.duplicates} 张，已入库 ${preview.alreadyImported} 张，当前不可入库 ${preview.blocked} 张。继续后将逐项处理并启动新增图片的 AI 索引。`
  try { await ElMessageBox.confirm(summary, '入库预检', { type: preview.blocked ? 'warning' : 'info' }) }
  catch { return }
  busy.value = 'import'
  try {
    const signature = JSON.stringify({
      jobId: activeJobId.value,
      ids: [...ids].sort((a, b) => a - b),
      albumId: targetAlbumId.value,
      tagIds: [...targetTagIds.value].sort((a, b) => a - b),
    })
    if (pendingImport.value?.signature !== signature) {
      pendingImport.value = { signature, key: newImportKey() }
    }
    const { data } = await crawlApi.importAssets(
      activeJobId.value, ids, targetAlbumId.value, targetTagIds.value, pendingImport.value.key)
    await loadImportBatch(data.batchId)
    if (data.status === 'QUEUED' || data.status === 'RUNNING') {
      ElMessage.info(`导入批次 #${data.batchId} 已进入后台队列`)
    }
    const completed = await waitForImportBatch(data.batchId)
    await loadImportBatch(data.batchId, completed.fail ? 'FAILED' : '')
    pendingImport.value = null
    if (completed.fail) ElMessage.warning(`入库完成：成功 ${completed.success}，重复跳过 ${completed.skipped}，失败 ${completed.fail}`)
    else ElMessage.success(`入库完成：成功 ${completed.success}，重复跳过 ${completed.skipped}`)
    selectedAssets.value = new Set()
    window.dispatchEvent(new Event('photos-changed'))
    await loadJobDetail()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '入库请求失败，可直接重试，已完成项目不会重复入库')
  } finally { busy.value = '' }
}

function newImportKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

async function loadImportBatch(batchId: number, status = importItemStatus.value) {
  const [batchResult, itemResult] = await Promise.all([
    crawlApi.importBatch(batchId),
    crawlApi.importItems(batchId, 0, 100, status),
  ])
  recentImportBatch.value = batchResult.data
  recentImportItems.value = itemResult.data.content
  importItemStatus.value = status
  return batchResult.data
}

async function waitForImportBatch(batchId: number) {
  for (let attempt = 0; attempt < 1800; attempt++) {
    const { data } = await crawlApi.importBatch(batchId)
    recentImportBatch.value = data
    if (!['QUEUED', 'RUNNING'].includes(data.status)) return data
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error('导入仍在后台运行，请稍后重试查看结果')
}

function filterImportItems(status: string) {
  if (!recentImportBatch.value) return
  void loadImportBatch(recentImportBatch.value.id, status)
}

async function purgeJob() {
  if (!activeJob.value) return
  try { await ElMessageBox.confirm('将永久删除这个采集任务、链接清单和待整理文件。已入库的图库照片不会删除。', '清理采集任务', { type: 'error' }) }
  catch { return }
  const { data } = await crawlApi.purgeJob(activeJob.value.id)
  if (data.fileFail) {
    ElMessage.warning(`${data.fileFail} 个本地文件清理失败，任务记录已保留，可稍后重试`)
    return
  }
  else ElMessage.success('采集任务已清理')
  activeJobId.value = null
  pages.value = []
  assets.value = []
  await loadJobs()
  await loadJobDetail()
}

function formatBytes(size?: number) {
  if (!size) return '—'
  return size < 1024 * 1024 ? `${Math.round(size / 1024)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`
}

watch(activeJobId, () => { assetPage.value = 0; pagePage.value = 0; pageQuery.value = ''; selectedAssets.value = new Set(); void loadJobDetail() })
watch(assetStatus, () => { assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })
watch(exactDuplicateOnly, value => { if (value) similarOnly.value = false; assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })
watch(similarOnly, value => { if (value) exactDuplicateOnly.value = false; assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })

function changeAssetPage(page: number) {
  assetPage.value = page - 1
  selectedAssets.value = new Set()
  void loadJobDetail()
}
onMounted(async () => {
  const [,, albumResult, tagResult] = await Promise.all([loadRules(), loadJobs(), albumApi.list(), tagApi.list()])
  albums.value = albumResult.data
  tags.value = tagResult.data
  await loadJobDetail()
  pollTimer = setInterval(async () => {
    if (activeJob.value?.status === 'RUNNING' || activeJob.value?.status === 'QUEUED') {
      await loadJobs(); await loadJobDetail()
    }
  }, 2500)
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  Object.values(previewImages).forEach(URL.revokeObjectURL)
})
</script>

<template>
  <div class="crawler-page">
    <section class="panel rule-panel">
      <header><div><h2>网站与采集规则</h2><p>先测试规则，再发现全部图片页；下载内容只进入待整理区。</p></div></header>
      <div class="rule-layout">
        <div class="fields">
          <el-input v-model="form.name" placeholder="规则名称" />
          <el-input v-model="form.startUrl" placeholder="起始列表页，例如 https://example.com/gallery" />
          <div class="two"><el-input v-model="form.detailSelector" placeholder="图片页链接 CSS 选择器" /><el-input v-model="form.nextSelector" placeholder="下一页 CSS 选择器（可空）" /></div>
          <div class="two"><el-input v-model="form.detailUrlIncludes" placeholder="图片页 URL 必须包含（逗号分隔，任一命中）" /><el-input v-model="form.detailUrlExcludes" placeholder="图片页 URL 排除片段（逗号分隔）" /></div>
          <div class="two"><el-input v-model="form.imageSelector" placeholder="详情页图片选择器" /><el-input v-model="form.imageAttributes" placeholder="图片属性优先级" /></div>
          <div class="two"><el-input v-model="form.imageUrlIncludes" placeholder="图片 URL 必须包含（逗号分隔，任一命中）" /><el-input v-model="form.imageUrlExcludes" placeholder="图片 URL 排除片段，如 thumb,avatar" /></div>
          <div class="two"><el-input v-model="form.detailNextSelector" placeholder="详情下一页选择器（可空）" /><div class="limit-field"><el-input-number v-model="form.maxPagesPerDetail" :min="1" :max="100" /><span>每个详情页组上限</span></div></div>
          <el-input v-model="form.allowedHosts" placeholder="允许域名，多个用逗号分隔（含图片 CDN）" />
          <div class="limits"><el-input-number v-model="form.maxListPages" :min="1" :max="1000" /><span>列表页上限</span><el-input-number v-model="form.maxDetailPages" :min="1" :max="20000" /><span>图片页上限</span><el-input-number v-model="form.maxImages" :min="1" :max="50000" /><span>图片上限</span></div>
          <div class="actions"><el-button :loading="busy === 'preview'" @click="testRule">测试规则</el-button><el-button @click="saveRule()">保存规则</el-button><el-button type="primary" :loading="busy === 'discover'" @click="startDiscovery">发现图片页</el-button></div>
        </div>
        <aside><strong>已保存规则</strong><button v-for="rule in rules" :key="rule.id" @click="useRule(rule)">{{ rule.name }}<small>{{ rule.startUrl }}</small></button><el-empty v-if="!rules.length" description="暂无规则" :image-size="48" /></aside>
      </div>
      <div v-if="previewUrls.length" class="preview-list"><strong>规则预览（最多 20 条）</strong><a v-for="url in previewUrls" :key="url" :href="url" target="_blank" rel="noreferrer">{{ url }}</a></div>
      <el-alert v-if="previewImageError" class="preview-error" type="warning" :closable="false" :title="`首个图片页解析失败：${previewImageError}`" />
      <div v-if="previewImageUrls.length" class="preview-list"><strong>首个图片页提取结果（最多 20 条）</strong><a v-for="url in previewImageUrls" :key="url" :href="url" target="_blank" rel="noreferrer">{{ url }}</a></div>
    </section>

    <section class="panel task-panel">
      <header><div><h2>采集任务</h2><p>发现和下载分阶段执行，失败项不会中断其他页面。</p></div></header>
      <div class="task-layout">
        <aside class="jobs"><button v-for="job in jobs" :key="job.id" :class="{ active: activeJobId === job.id }" @click="activeJobId = job.id"><strong>{{ job.name }}</strong><small>{{ job.phase }} · {{ job.status }}</small></button><el-empty v-if="!jobs.length" description="暂无任务" :image-size="48" /></aside>
        <div v-if="activeJob" class="task-detail">
          <div class="stats"><span>列表页 <b>{{ activeJob.listProcessed }}</b></span><span>图片页 <b>{{ activeJob.pagesFound }}</b></span><span>已处理 <b>{{ activeJob.pagesProcessed }}</b></span><span>已下载 <b>{{ activeJob.imagesDownloaded }}</b></span><span>失败 <b>{{ activeJob.failCount }}</b></span><span>执行次数 <b>{{ activeJob.attemptCount }}</b></span></div>
          <el-alert v-if="activeJob.errorMessage" type="error" :title="activeJob.errorMessage" :closable="false" />
          <el-button v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" type="primary" :disabled="includedPageTotal === 0" @click="startDownload">确认 {{ includedPageTotal }} 个图片页并开始下载</el-button>
          <div class="actions task-actions"><el-button v-if="activeJob.status === 'RUNNING' || activeJob.status === 'QUEUED'" @click="controlJob('pause')">暂停</el-button><el-button v-if="activeJob.status === 'PAUSED' || activeJob.status === 'FAILED' || activeJob.status === 'PARTIAL'" @click="controlJob('resume')">继续/重试</el-button><el-button v-if="!['COMPLETED','CANCELLED'].includes(activeJob.status)" type="danger" plain @click="controlJob('cancel')">取消</el-button></div>
          <el-button v-if="!['RUNNING','QUEUED'].includes(activeJob.status)" type="danger" text @click="purgeJob">永久清理任务</el-button>
          <div class="page-toolbar"><el-input v-model="pageQuery" clearable placeholder="搜索图片页 URL" @keyup.enter="searchPages" @clear="searchPages"><template #append><el-button @click="searchPages">搜索</el-button></template></el-input><div v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" class="actions"><el-button size="small" @click="setCurrentPagesIncluded(true)">保留本页</el-button><el-button size="small" @click="setCurrentPagesIncluded(false)">排除本页</el-button></div></div>
          <div class="page-list"><div v-for="page in pages" :key="page.id"><el-checkbox v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" :model-value="page.included" @change="onPageIncludedChange(page, $event)" /><el-tag v-else size="small" :type="page.status === 'FAILED' ? 'danger' : page.status === 'SUCCEEDED' ? 'success' : 'info'">{{ page.status }}</el-tag><span :class="{ excluded: !page.included }" :title="page.url">{{ page.url }}</span></div></div>
          <el-pagination v-if="pageTotal > pagePageSize" class="page-pagination" layout="prev, pager, next, total" :current-page="pagePage + 1" :page-size="pagePageSize" :total="pageTotal" @current-change="changePageListPage" />
        </div>
      </div>
    </section>

    <section v-if="activeJob && (assets.length || activeJob.phase === 'REVIEW')" class="panel review-panel">
      <header><div><h2>待整理区</h2><p>共 {{ assetTotal }} 张；本页 {{ reviewAssets.length }} 张可见，涉及 {{ duplicateHashes.size }} 组全任务精确重复，{{ deletedAssets.length }} 张已删除。</p></div><div class="actions"><el-select v-model="assetStatus" class="status-filter" :disabled="exactDuplicateOnly || similarOnly" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option label="可入库" value="DOWNLOADED" /><el-option label="已删除" value="DELETED" /><el-option label="图库重复" value="DUPLICATE" /><el-option label="已入库" value="IMPORTED" /><el-option label="失败" value="FAILED" /></el-select><el-checkbox v-model="exactDuplicateOnly" border>只看精确重复</el-checkbox><el-checkbox v-model="similarOnly" border>只看相似图片</el-checkbox><el-button @click="selectAllDownloadable">选择本页可入库</el-button><el-button :disabled="!selectedAssets.size" @click="deleteSelected">删除选中</el-button><el-button type="primary" :loading="busy === 'import'" :disabled="!selectedAssets.size" @click="importSelected">选择入库（{{ selectedAssets.size }}）</el-button></div></header>
      <div class="similarity-bar"><span>感知哈希距离阈值</span><el-slider v-model="similarityThreshold" :min="0" :max="16" show-input /><el-button :loading="busy === 'similarity'" @click="analyzeSimilarity">分析相似图片</el-button><small>阈值越大匹配越宽松，结果仅供人工整理。</small></div>
      <div class="organize-bar"><el-input v-model="batchNote" placeholder="给选中图片添加备注" clearable /><el-button :disabled="!selectedAssets.size" @click="editSelected">应用备注</el-button><el-select v-model="targetAlbumId" clearable placeholder="入库目标相册"><el-option v-for="album in albums" :key="album.id" :label="album.name" :value="album.id" /></el-select><el-select v-model="targetTagIds" multiple collapse-tags clearable placeholder="入库标签"><el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" /></el-select></div>
      <details v-if="recentImportBatch" class="import-batch" open>
        <summary>最近导入批次 #{{ recentImportBatch.id }}（{{ recentImportBatch.status }}）：成功 {{ recentImportBatch.success }}，跳过 {{ recentImportBatch.skipped }}，失败 {{ recentImportBatch.fail }}</summary>
        <div class="import-toolbar">
          <el-radio-group :model-value="importItemStatus" size="small" @change="filterImportItems(String($event))">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="SUCCESS">成功</el-radio-button>
            <el-radio-button value="SKIPPED">跳过</el-radio-button>
            <el-radio-button value="FAILED">失败</el-radio-button>
          </el-radio-group>
        </div>
        <div class="import-items">
          <div v-for="item in recentImportItems" :key="item.id">
            <el-tag size="small" :type="item.status === 'FAILED' ? 'danger' : item.status === 'SUCCESS' ? 'success' : 'info'">{{ item.status }}</el-tag>
            <span>待整理图片 #{{ item.assetId }}</span>
            <span v-if="item.photoId">图库照片 #{{ item.photoId }}</span>
            <span v-if="item.errorMessage" class="import-error">{{ item.errorMessage }}</span>
          </div>
          <el-empty v-if="!recentImportItems.length" description="当前筛选没有明细" :image-size="42" />
        </div>
      </details>
      <div class="asset-grid">
        <article v-for="asset in reviewAssets" :key="asset.id" :class="{ selected: selectedAssets.has(asset.id), duplicate: duplicateHashes.has(asset.fileHashMd5), similar: asset.similarityCount > 1 }" @click="asset.status === 'DOWNLOADED' && toggleAsset(asset.id)">
          <img v-if="previewImages[asset.id]" :src="previewImages[asset.id]" alt="" />
          <div v-else class="placeholder">{{ asset.status }}</div>
          <div class="asset-copy"><strong>{{ asset.originalFilename || `图片 #${asset.id}` }}</strong><small>{{ asset.width || '?' }} × {{ asset.height || '?' }} · {{ formatBytes(asset.fileSize) }}</small><el-tag v-if="asset.exactDuplicateCount > 1" size="small" type="warning">待整理区相同 × {{ asset.exactDuplicateCount }}</el-tag><el-tag v-if="asset.similarityCount > 1" size="small" type="primary">相似组 × {{ asset.similarityCount }}</el-tag><el-tag v-if="asset.libraryDuplicate" size="small" type="info">图库已有</el-tag><el-tag v-if="asset.libraryTrashDuplicate" size="small" type="danger">回收站已有</el-tag><el-tag v-if="asset.status === 'IMPORTED'" size="small" type="success">已入库</el-tag><el-tag v-if="asset.status === 'DUPLICATE'" size="small" type="info">已跳过重复</el-tag></div>
        </article>
      </div>
      <details v-if="deletedAssets.length"><summary>已删除（{{ deletedAssets.length }}）</summary><div class="deleted-list"><span v-for="asset in deletedAssets" :key="asset.id">{{ asset.originalFilename }} <el-button text size="small" @click="restoreDeleted(asset.id)">恢复</el-button></span></div></details>
      <el-pagination v-if="assetTotal > assetPageSize" class="asset-pagination" layout="prev, pager, next, total" :current-page="assetPage + 1" :page-size="assetPageSize" :total="assetTotal" @current-change="changeAssetPage" />
    </section>
  </div>
</template>

<style scoped>
.status-filter{width:130px}.asset-pagination,.page-pagination{justify-content:center;margin-top:18px}.page-toolbar{display:grid;grid-template-columns:minmax(220px,1fr) auto;gap:10px;margin-top:14px}.page-list span.excluded{text-decoration:line-through;opacity:.55}.similarity-bar{display:grid;grid-template-columns:auto minmax(220px,1fr) auto auto;align-items:center;gap:12px;margin-bottom:14px;padding:10px 12px;border-radius:12px;background:var(--bg-secondary);color:var(--text-secondary);font-size:11px}.import-batch{margin:0 0 14px;padding:12px;border-radius:12px;background:var(--bg-secondary)}.import-toolbar{margin-top:10px}.import-items{display:grid;gap:7px;max-height:210px;margin-top:10px;overflow:auto}.import-items>div{display:flex;align-items:center;gap:8px;color:var(--text-secondary);font-size:11px}.import-error{color:var(--danger,#f56c6c)}
.crawler-page{display:grid;gap:18px;padding:24px 24px 112px}.panel{padding:20px;border:1px solid var(--separator);border-radius:20px;background:var(--bg-card);box-shadow:0 12px 35px rgba(0,0,0,.08)}header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px}h2{margin:0;color:var(--text-primary);font-size:19px}p{margin:5px 0 0;color:var(--text-secondary);font-size:12px}.rule-layout,.task-layout{display:grid;grid-template-columns:minmax(0,1fr) 250px;gap:18px}.task-layout{grid-template-columns:250px minmax(0,1fr)}.fields{display:grid;gap:11px}.two{display:grid;grid-template-columns:1fr 1fr;gap:10px}.limit-field,.limits{display:flex;align-items:center;gap:8px;color:var(--text-secondary);font-size:11px}.limits{flex-wrap:wrap}.actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.organize-bar{display:grid;grid-template-columns:minmax(180px,1fr) auto minmax(160px,.5fr) minmax(180px,.7fr);gap:8px;margin-bottom:14px}aside,.jobs{display:grid;align-content:start;gap:7px;max-height:310px;overflow:auto}aside>strong{margin-bottom:5px;color:var(--text-primary);font-size:12px}aside button,.jobs button{display:grid;gap:3px;padding:10px;border:1px solid var(--separator);border-radius:11px;background:var(--bg-secondary);color:var(--text-primary);text-align:left;cursor:pointer}aside button.active,.jobs button.active{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 11%,var(--bg-secondary))}aside button small,.jobs button small{overflow:hidden;color:var(--text-tertiary);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.preview-list,.page-list{display:grid;gap:7px;max-height:220px;margin-top:16px;overflow:auto}.preview-list a,.page-list>div{overflow:hidden;color:var(--text-secondary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.page-list>div{display:flex;align-items:center;gap:7px}.stats{display:flex;flex-wrap:wrap;gap:9px;margin-bottom:15px}.stats span{padding:8px 10px;border-radius:10px;background:var(--bg-secondary);color:var(--text-secondary);font-size:11px}.stats b{color:var(--text-primary);font-size:15px}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}.asset-grid article{position:relative;overflow:hidden;border:2px solid transparent;border-radius:14px;background:var(--bg-secondary);cursor:pointer}.asset-grid article.selected{border-color:var(--accent)}.asset-grid article.duplicate::after{position:absolute;top:8px;right:8px;padding:3px 6px;border-radius:8px;background:#ff9f0a;color:white;font-size:9px;content:'重复'}.asset-grid img,.placeholder{width:100%;height:145px;object-fit:cover}.placeholder{display:grid;place-items:center;color:var(--text-tertiary)}.asset-copy{display:grid;gap:5px;padding:10px}.asset-copy strong,.asset-copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.asset-copy strong{color:var(--text-primary);font-size:12px}.asset-copy small{color:var(--text-secondary);font-size:10px}details{margin-top:18px;color:var(--text-secondary)}.deleted-list{display:flex;flex-wrap:wrap;gap:9px;margin-top:10px}.deleted-list span{padding:6px 9px;border-radius:9px;background:var(--bg-secondary);font-size:11px}@media(max-width:760px){.crawler-page{padding:14px 14px 90px}.rule-layout,.task-layout{grid-template-columns:1fr}.two,.page-toolbar{grid-template-columns:1fr}.organize-bar{grid-template-columns:1fr}.limits{display:grid;grid-template-columns:1fr 1fr}header{display:grid}.actions{justify-content:flex-start}}
.asset-grid article.similar::before{position:absolute;top:8px;left:8px;padding:3px 6px;border-radius:8px;background:#6366f1;color:white;font-size:9px;content:'相似'}
@media(max-width:760px){.similarity-bar{grid-template-columns:1fr}.similarity-bar small{grid-column:1}}
</style>

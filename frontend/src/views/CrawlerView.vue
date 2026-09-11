<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Connection, DataAnalysis, List, Plus, Refresh, Warning } from '@element-plus/icons-vue'
import { crawlApi } from '@/api/crawlApi'
import type { CrawlImportBatch, CrawlImportItem } from '@/api/crawlApi'
import { albumApi } from '@/api/albumApi'
import { tagApi } from '@/api/tagApi'
import type { Album, CrawlAsset, CrawlJob, CrawlPage, CrawlRule, CrawlSite, Tag } from '@/types'

const sites = ref<CrawlSite[]>([])
const rules = ref<CrawlRule[]>([])
const activeSiteId = ref<number | null>(null)
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
const siteDialogVisible = ref(false)
const siteSaveBusy = ref(false)
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

type TabKey = 'overview' | 'sites' | 'tasks' | 'review'
const activeTab = ref<TabKey>('overview')

const siteForm = reactive<CrawlSite>({
  name: '', startUrl: '', allowedHosts: '', maxListPages: 100, maxDetailPages: 1000,
  maxImages: 5000, maxFileBytes: 20 * 1024 * 1024,
})

const createSiteForm = reactive<CrawlSite>({
  name: '', startUrl: '', allowedHosts: '', maxListPages: 100, maxDetailPages: 1000,
  maxImages: 5000, maxFileBytes: 20 * 1024 * 1024,
})

const form = reactive<CrawlRule>({
  siteId: 0, name: '', enabled: false, detailSelector: '', detailUrlIncludes: '', detailUrlExcludes: '',
  nextSelector: '', imageSelector: 'img', imageAttributes: 'data-original,data-src,srcset,src',
  imageUrlIncludes: '', imageUrlExcludes: '', detailNextSelector: '', maxPagesPerDetail: 20,
})

const activeJob = computed(() => jobs.value.find(job => job.id === activeJobId.value) || null)
const activeJobs = computed(() => jobs.value.filter(job => ['QUEUED', 'RUNNING'].includes(job.status)))
const failedJobs = computed(() => jobs.value.filter(job => ['FAILED', 'PARTIAL'].includes(job.status)))
const reviewJobs = computed(() => jobs.value.filter(job => job.phase === 'REVIEW'))
const totalDownloaded = computed(() => jobs.value.reduce((total, job) => total + job.imagesDownloaded, 0))
const recentJobs = computed(() => jobs.value.slice(0, 6))
const tabs = computed(() => [
  { key: 'overview' as const, label: '采集概览', icon: DataAnalysis, count: 0 },
  { key: 'sites' as const, label: '网站与规则', icon: Connection, count: sites.value.length },
  { key: 'tasks' as const, label: '采集任务', icon: List, count: activeJobs.value.length },
  { key: 'review' as const, label: '待整理区', icon: Collection, count: reviewJobs.value.length },
])
const activeIndex = computed(() => tabs.value.findIndex(tab => tab.key === activeTab.value))
const metrics = computed(() => [
  { label: '采集网站', value: sites.value.length, note: `${sites.value.length ? '已配置来源' : '等待配置'}`, icon: Connection },
  { label: '运行中任务', value: activeJobs.value.length, note: `${jobs.value.length} 个历史任务`, icon: List },
  { label: '已下载图片', value: totalDownloaded.value, note: `${reviewJobs.value.length} 个任务待整理`, icon: Collection },
  { label: '需要关注', value: failedJobs.value.length, note: failedJobs.value.length ? '可进入任务页重试' : '当前运行正常', icon: Warning },
])
const reviewAssets = computed(() => assets.value.filter(asset => asset.status !== 'DELETED'))
const deletedAssets = computed(() => assets.value.filter(asset => asset.status === 'DELETED'))
const duplicateHashes = computed(() => {
  return new Set(reviewAssets.value
    .filter(asset => asset.fileHashMd5 && asset.exactDuplicateCount > 1)
    .map(asset => asset.fileHashMd5))
})

async function loadSites() {
  sites.value = (await crawlApi.sites()).data
  if (!activeSiteId.value && sites.value.length) await selectSite(sites.value[0])
}
async function loadRules(preferredRuleId?: number) {
  if (!activeSiteId.value) { rules.value = []; return }
  rules.value = (await crawlApi.rules(activeSiteId.value)).data
  const selected = rules.value.find(rule => rule.id === preferredRuleId)
    || rules.value.find(rule => rule.enabled) || rules.value[0]
  if (selected) useRule(selected)
  else newRule()
}
async function loadJobs() {
  jobs.value = (await crawlApi.jobs()).data
  if (!activeJobId.value && jobs.value.length) activeJobId.value = jobs.value[0].id
}

async function refresh() {
  await Promise.all([loadSites(), loadJobs()])
  if (activeJobId.value) await loadJobDetail()
}

function openJob(job: CrawlJob, tab: TabKey = 'tasks') {
  activeJobId.value = job.id
  activeTab.value = tab
}

function jobProgress(job: CrawlJob) {
  if (job.phase === 'DISCOVERY') return job.listProcessed ? Math.min(95, job.listProcessed) : 0
  if (job.phase === 'AWAITING_CONFIRMATION') return 45
  if (!job.pagesFound) return job.phase === 'REVIEW' ? 100 : 0
  return Math.min(100, Math.round(job.pagesProcessed / job.pagesFound * 100))
}

function statusLabel(status: CrawlJob['status']) {
  return ({ QUEUED: '等待中', RUNNING: '运行中', PAUSED: '已暂停', COMPLETED: '已完成', PARTIAL: '部分成功', FAILED: '失败', CANCELLED: '已取消' } as const)[status]
}

function statusType(status: CrawlJob['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'danger'
  if (status === 'PARTIAL' || status === 'PAUSED') return 'warning'
  return 'primary'
}

function phaseLabel(phase: CrawlJob['phase']) {
  return ({ DISCOVERY: '发现链接', AWAITING_CONFIRMATION: '等待确认', DOWNLOAD: '下载图片', REVIEW: '待整理' } as const)[phase]
}

function formatTime(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function handleTabKey(event: KeyboardEvent) {
  const keys = tabs.value.map(tab => tab.key)
  let index = activeIndex.value
  if (['ArrowRight', 'ArrowDown'].includes(event.key)) index = (index + 1) % keys.length
  else if (['ArrowLeft', 'ArrowUp'].includes(event.key)) index = (index - 1 + keys.length) % keys.length
  else if (event.key === 'Home') index = 0
  else if (event.key === 'End') index = keys.length - 1
  else return
  event.preventDefault()
  activeTab.value = keys[index]
  requestAnimationFrame(() => document.querySelectorAll<HTMLButtonElement>('.crawler-segment')[index]?.focus())
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
  if (!activeSiteId.value) {
    ElMessage.warning('请先保存网站基础配置')
    return null
  }
  if (!form.name || !form.detailSelector) {
    ElMessage.warning('请填写规则名称和图片页选择器')
    return null
  }
  form.siteId = activeSiteId.value
  const { data } = await crawlApi.saveRule({ ...form })
  Object.assign(form, data)
  await loadRules(data.id)
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

function newRule() {
  Object.assign(form, {
    id: undefined, siteId: activeSiteId.value || 0, name: '', enabled: rules.value.length === 0,
    detailSelector: '', detailUrlIncludes: '', detailUrlExcludes: '', nextSelector: '',
    imageSelector: 'img', imageAttributes: 'data-original,data-src,srcset,src',
    imageUrlIncludes: '', imageUrlExcludes: '', detailNextSelector: '', maxPagesPerDetail: 20,
  })
  previewUrls.value = []
  previewImageUrls.value = []
  previewImageError.value = ''
}

async function selectSite(site: CrawlSite) {
  activeSiteId.value = site.id || null
  Object.assign(siteForm, site)
  await loadRules()
}

function resetCreateSiteForm() {
  Object.assign(createSiteForm, {
    id: undefined, name: '', startUrl: '', allowedHosts: '', maxListPages: 100,
    maxDetailPages: 1000, maxImages: 5000, maxFileBytes: 20 * 1024 * 1024,
  })
}

function openNewSiteDialog() {
  resetCreateSiteForm()
  siteDialogVisible.value = true
}

async function saveSite() {
  if (!siteForm.name || !siteForm.startUrl) {
    ElMessage.warning('请填写网站名称和起始 URL')
    return
  }
  if (!siteForm.allowedHosts) {
    try { siteForm.allowedHosts = new URL(siteForm.startUrl).hostname } catch { /* backend validates */ }
  }
  const { data } = await crawlApi.saveSite({ ...siteForm })
  Object.assign(siteForm, data)
  activeSiteId.value = data.id || null
  await loadSites()
  await loadRules()
  ElMessage.success('网站基础配置已保存')
}

async function createSite() {
  if (!createSiteForm.name || !createSiteForm.startUrl) {
    ElMessage.warning('请填写网站名称和起始 URL')
    return
  }
  if (!createSiteForm.allowedHosts) {
    try { createSiteForm.allowedHosts = new URL(createSiteForm.startUrl).hostname } catch { /* backend validates */ }
  }
  siteSaveBusy.value = true
  try {
    const { data } = await crawlApi.saveSite({ ...createSiteForm })
    siteDialogVisible.value = false
    await loadSites()
    const createdSite = sites.value.find(site => site.id === data.id) || data
    await selectSite(createdSite)
    ElMessage.success('网站已创建')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '网站创建失败')
  } finally {
    siteSaveBusy.value = false
  }
}

async function activateRule(rule: CrawlRule) {
  const { data } = await crawlApi.saveRule({ ...rule, enabled: true })
  await loadRules(data.id)
  useRule(data)
  ElMessage.success(`“${data.name}”已设为当前生效规则`)
}

async function startDiscovery() {
  busy.value = 'discover'
  try {
    const rule = await saveRule(false)
    if (!rule?.id) return
    const { data } = await crawlApi.createJob(rule.id)
    activeJobId.value = data.jobId
    activeTab.value = 'tasks'
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
  activeTab.value = 'tasks'
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
watch(activeTab, tab => {
  if (tab === 'review' && activeJob.value?.phase !== 'REVIEW') {
    activeJobId.value = reviewJobs.value[0]?.id ?? null
  } else if (tab === 'tasks' && !activeJobId.value && jobs.value.length) {
    activeJobId.value = jobs.value[0].id
  }
})
watch(assetStatus, () => { assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })
watch(exactDuplicateOnly, value => { if (value) similarOnly.value = false; assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })
watch(similarOnly, value => { if (value) exactDuplicateOnly.value = false; assetPage.value = 0; selectedAssets.value = new Set(); void loadJobDetail() })

function changeAssetPage(page: number) {
  assetPage.value = page - 1
  selectedAssets.value = new Set()
  void loadJobDetail()
}
onMounted(async () => {
  const [,, albumResult, tagResult] = await Promise.all([loadSites(), loadJobs(), albumApi.list(), tagApi.list()])
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
  <main class="crawler-page">
    <header class="crawler-hero">
      <div>
        <p class="eyebrow">IMAGE COLLECTION PIPELINE</p>
        <h1>图片爬虫</h1>
        <p class="subtitle">从授权网站发现图片、人工确认下载范围，再整理并安全导入私人图库。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="activeTab = 'sites'; openNewSiteDialog()">新增采集网站</el-button>
    </header>

    <nav class="crawler-tabs" role="tablist" aria-label="图片采集中心栏目" @keydown="handleTabKey">
      <span class="crawler-tab-indicator" :style="{ transform: `translateX(${activeIndex * 100}%)`, width: `${100 / tabs.length}%` }" />
      <button v-for="tab in tabs" :key="tab.key" class="crawler-segment" :class="{ active: activeTab === tab.key }"
        role="tab" :aria-selected="activeTab === tab.key" :tabindex="activeTab === tab.key ? 0 : -1" @click="activeTab = tab.key">
        <el-icon><component :is="tab.icon" /></el-icon><span>{{ tab.label }}</span><b v-if="tab.count">{{ tab.count }}</b>
      </button>
    </nav>

    <section v-if="activeTab === 'overview'" class="panel overview-panel" role="tabpanel">
      <div class="metric-grid">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></span>
          <div><strong>{{ metric.value }}</strong><p>{{ metric.label }}</p></div><small>{{ metric.note }}</small>
        </article>
      </div>
      <div class="pipeline-strip" aria-label="图片采集流程">
        <article><b>01</b><div><strong>发现链接</strong><small>按网站规则遍历列表页</small></div></article>
        <i>→</i><article><b>02</b><div><strong>人工确认</strong><small>筛选需要下载的图片页</small></div></article>
        <i>→</i><article><b>03</b><div><strong>下载校验</strong><small>限制域名、大小并隔离失败项</small></div></article>
        <i>→</i><article><b>04</b><div><strong>整理入库</strong><small>去重、标注、选择相册和标签</small></div></article>
      </div>
      <div class="section-heading"><div><p class="eyebrow">LIVE QUEUE</p><h2>最近任务</h2></div><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div>
      <el-table v-if="recentJobs.length" :data="recentJobs" class="task-table">
        <el-table-column label="任务" min-width="230"><template #default="{ row }"><div class="task-name"><strong>{{ row.name }}</strong><p>{{ phaseLabel(row.phase) }} · {{ formatTime(row.createdAt) }}</p></div></template></el-table-column>
        <el-table-column label="进度" min-width="180"><template #default="{ row }"><el-progress :percentage="jobProgress(row)" :stroke-width="7" /></template></el-table-column>
        <el-table-column label="已下载" prop="imagesDownloaded" width="100" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button text type="primary" @click="openJob(row, row.phase === 'REVIEW' ? 'review' : 'tasks')">查看详情</el-button></template></el-table-column>
      </el-table>
      <el-empty v-else description="暂无采集任务，先配置网站和解析规则" />
    </section>

    <template v-else-if="activeTab === 'sites'">
      <section class="panel site-panel" role="tabpanel">
        <div class="section-heading"><div><p class="eyebrow">SOURCES</p><h2>采集网站</h2><p>网站基础配置独立保存，每个网站可以维护多条解析规则。</p></div><el-button :icon="Plus" @click="openNewSiteDialog">新增网站</el-button></div>
        <div class="site-layout">
          <aside class="sites"><button v-for="site in sites" :key="site.id" :class="{ active: activeSiteId === site.id }" @click="selectSite(site)"><span class="site-mark">{{ site.name.slice(0, 1) }}</span><span><strong>{{ site.name }}</strong><small>{{ site.startUrl }}</small></span></button><el-empty v-if="!sites.length" description="暂无网站" :image-size="48" /></aside>
          <div v-if="activeSiteId" class="fields site-form">
            <div class="form-block-title"><b>01</b><div><strong>网站身份与入口</strong><small>只允许访问配置的站点和图片 CDN 域名</small></div></div>
            <div class="two"><el-input v-model="siteForm.name" placeholder="网站名称" /><el-input v-model="siteForm.startUrl" placeholder="起始列表页，例如 https://example.com/gallery" /></div>
            <el-input v-model="siteForm.allowedHosts" placeholder="允许域名，多个用逗号分隔（含图片 CDN）" />
            <div class="form-block-title"><b>02</b><div><strong>单次任务安全上限</strong><small>避免错误规则产生无限翻页或超量下载</small></div></div>
            <div class="limits"><label><span>列表页上限</span><el-input-number v-model="siteForm.maxListPages" :min="1" :max="1000" /></label><label><span>图片页上限</span><el-input-number v-model="siteForm.maxDetailPages" :min="1" :max="20000" /></label><label><span>图片上限</span><el-input-number v-model="siteForm.maxImages" :min="1" :max="50000" /></label></div>
            <div class="actions"><el-button type="primary" @click="saveSite">保存网站配置</el-button></div>
          </div>
          <el-empty v-else description="选择一个网站查看配置，或点击新增网站" :image-size="60" />
        </div>
      </section>

      <section class="panel rule-panel">
        <div class="section-heading"><div><p class="eyebrow">SELECTOR RULES</p><h2>解析规则</h2><p>同一网站可保留多条规则，但同时最多只有一条生效。</p></div><el-button :disabled="!activeSiteId" @click="newRule">新增规则</el-button></div>
        <el-empty v-if="!activeSiteId" description="请先选择或新增一个网站" :image-size="60" />
        <div v-else class="rule-layout">
          <div class="fields">
            <div class="two"><el-input v-model="form.name" placeholder="规则名称" /><el-switch v-model="form.enabled" inline-prompt active-text="生效" inactive-text="停用" /></div>
            <div class="rule-block"><p class="eyebrow">DISCOVERY SELECTORS</p><strong>列表页与图片页链接</strong></div>
            <div class="two"><el-input v-model="form.detailSelector" placeholder="图片页链接 CSS 选择器" /><el-input v-model="form.nextSelector" placeholder="列表下一页选择器（可空）" /></div>
            <div class="two"><el-input v-model="form.detailUrlIncludes" placeholder="图片页 URL 必须包含（逗号分隔）" /><el-input v-model="form.detailUrlExcludes" placeholder="图片页 URL 排除片段（逗号分隔）" /></div>
            <div class="rule-block"><p class="eyebrow">IMAGE SELECTORS</p><strong>原图提取与详情翻页</strong></div>
            <div class="two"><el-input v-model="form.imageSelector" placeholder="详情页图片选择器" /><el-input v-model="form.imageAttributes" placeholder="图片属性优先级" /></div>
            <div class="two"><el-input v-model="form.imageUrlIncludes" placeholder="图片 URL 必须包含（逗号分隔）" /><el-input v-model="form.imageUrlExcludes" placeholder="图片 URL 排除片段，如 thumb,avatar" /></div>
            <div class="two"><el-input v-model="form.detailNextSelector" placeholder="详情下一页选择器（可空）" /><div class="limit-field"><el-input-number v-model="form.maxPagesPerDetail" :min="1" :max="100" /><span>每个详情页组上限</span></div></div>
            <div class="actions"><el-button :loading="busy === 'preview'" @click="testRule">在线测试</el-button><el-button @click="saveRule()">保存规则</el-button><el-button type="primary" :loading="busy === 'discover'" :disabled="!form.enabled" @click="startDiscovery">扫描网站</el-button></div>
          </div>
          <aside class="rules"><strong>规则版本</strong><button v-for="rule in rules" :key="rule.id" :class="{ active: form.id === rule.id }" @click="useRule(rule)"><span class="rule-name">{{ rule.name }}<el-tag v-if="rule.enabled" size="small" type="success">生效中</el-tag></span><small v-if="rule.enabled">新任务将使用此规则</small><small v-else @click.stop="activateRule(rule)">点击设为生效规则</small></button><el-empty v-if="!rules.length" description="暂无规则" :image-size="48" /></aside>
        </div>
        <div v-if="previewUrls.length" class="preview-list"><strong>图片页链接预览（最多 20 条）</strong><a v-for="url in previewUrls" :key="url" :href="url" target="_blank" rel="noreferrer">{{ url }}</a></div>
        <el-alert v-if="previewImageError" class="preview-error" type="warning" :closable="false" :title="`首个图片页解析失败：${previewImageError}`" />
        <div v-if="previewImageUrls.length" class="preview-list"><strong>原图地址预览（最多 20 条）</strong><a v-for="url in previewImageUrls" :key="url" :href="url" target="_blank" rel="noreferrer">{{ url }}</a></div>
      </section>
    </template>

    <section v-else-if="activeTab === 'tasks'" class="panel task-panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">PERSISTENT QUEUE</p><h2>采集任务</h2><p>发现与下载分阶段执行，单页失败不会中断整个任务。</p></div><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div>
      <div class="task-layout">
        <aside class="jobs"><button v-for="job in jobs" :key="job.id" :class="{ active: activeJobId === job.id }" @click="activeJobId = job.id"><strong>{{ job.name }}</strong><span><el-tag size="small" :type="statusType(job.status)">{{ statusLabel(job.status) }}</el-tag><small>{{ phaseLabel(job.phase) }} · {{ formatTime(job.createdAt) }}</small></span></button><el-empty v-if="!jobs.length" description="暂无任务" :image-size="48" /></aside>
        <div v-if="activeJob" class="task-detail">
          <div class="task-summary"><div><p class="eyebrow">{{ phaseLabel(activeJob.phase) }}</p><h3>{{ activeJob.name }}</h3></div><el-tag :type="statusType(activeJob.status)">{{ statusLabel(activeJob.status) }}</el-tag></div>
          <el-progress :percentage="jobProgress(activeJob)" :stroke-width="8" />
          <div class="stats"><span>列表页 <b>{{ activeJob.listProcessed }}</b></span><span>图片页 <b>{{ activeJob.pagesFound }}</b></span><span>已处理 <b>{{ activeJob.pagesProcessed }}</b></span><span>已下载 <b>{{ activeJob.imagesDownloaded }}</b></span><span>失败 <b>{{ activeJob.failCount }}</b></span><span>执行次数 <b>{{ activeJob.attemptCount }}</b></span></div>
          <el-alert v-if="activeJob.errorMessage" type="error" :title="activeJob.errorMessage" :closable="false" />
          <div class="task-actions"><el-button v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" type="primary" :disabled="includedPageTotal === 0" @click="startDownload">确认 {{ includedPageTotal }} 个图片页并开始下载</el-button><el-button v-if="['RUNNING','QUEUED'].includes(activeJob.status)" @click="controlJob('pause')">暂停</el-button><el-button v-if="['PAUSED','FAILED','PARTIAL'].includes(activeJob.status)" @click="controlJob('resume')">继续/重试</el-button><el-button v-if="!['COMPLETED','CANCELLED'].includes(activeJob.status)" type="danger" plain @click="controlJob('cancel')">取消</el-button><el-button v-if="activeJob.phase === 'REVIEW'" type="primary" plain @click="activeTab = 'review'">进入待整理区</el-button><el-button v-if="!['RUNNING','QUEUED'].includes(activeJob.status)" type="danger" text @click="purgeJob">永久清理</el-button></div>
          <div class="page-toolbar"><el-input v-model="pageQuery" clearable placeholder="搜索图片页 URL" @keyup.enter="searchPages" @clear="searchPages"><template #append><el-button @click="searchPages">搜索</el-button></template></el-input><div v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" class="actions"><el-button size="small" @click="setCurrentPagesIncluded(true)">保留本页</el-button><el-button size="small" @click="setCurrentPagesIncluded(false)">排除本页</el-button></div></div>
          <div class="page-list"><div v-for="page in pages" :key="page.id"><el-checkbox v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" :model-value="page.included" @change="onPageIncludedChange(page, $event)" /><el-tag v-else size="small" :type="page.status === 'FAILED' ? 'danger' : page.status === 'SUCCEEDED' ? 'success' : 'info'">{{ page.status }}</el-tag><span :class="{ excluded: !page.included }" :title="page.url">{{ page.url }}</span></div></div>
          <el-empty v-if="!pages.length" description="当前任务暂无图片页记录" :image-size="54" />
          <el-pagination v-if="pageTotal > pagePageSize" class="page-pagination" layout="prev, pager, next, total" :current-page="pagePage + 1" :page-size="pagePageSize" :total="pageTotal" @current-change="changePageListPage" />
        </div>
        <el-empty v-else description="选择一个采集任务查看进度" />
      </div>
    </section>

    <section v-else class="panel review-panel" role="tabpanel">
      <div class="section-heading review-heading"><div><p class="eyebrow">CURATION WORKSPACE</p><h2>待整理区</h2><p>去重、筛选和标注后，再将图片导入目标相册。</p></div><el-select v-model="activeJobId" placeholder="选择待整理任务"><el-option v-for="job in reviewJobs" :key="job.id" :label="`${job.name}（${job.imagesDownloaded} 张）`" :value="job.id" /></el-select></div>
      <el-empty v-if="!activeJob || (!assets.length && activeJob.phase !== 'REVIEW')" description="暂无可整理的图片，完成下载后会出现在这里" />
      <template v-else>
      <header><div><h2>筛选与批量操作</h2><p>共 {{ assetTotal }} 张；本页 {{ reviewAssets.length }} 张可见，涉及 {{ duplicateHashes.size }} 组全任务精确重复，{{ deletedAssets.length }} 张已删除。</p></div><div class="actions"><el-select v-model="assetStatus" class="status-filter" :disabled="exactDuplicateOnly || similarOnly" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option label="可入库" value="DOWNLOADED" /><el-option label="已删除" value="DELETED" /><el-option label="图库重复" value="DUPLICATE" /><el-option label="已入库" value="IMPORTED" /><el-option label="失败" value="FAILED" /></el-select><el-checkbox v-model="exactDuplicateOnly" border>只看精确重复</el-checkbox><el-checkbox v-model="similarOnly" border>只看相似图片</el-checkbox><el-button @click="selectAllDownloadable">选择本页可入库</el-button><el-button :disabled="!selectedAssets.size" @click="deleteSelected">删除选中</el-button><el-button type="primary" :loading="busy === 'import'" :disabled="!selectedAssets.size" @click="importSelected">选择入库（{{ selectedAssets.size }}）</el-button></div></header>
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
      </template>
    </section>

    <el-dialog v-model="siteDialogVisible" title="新增采集网站" width="min(620px, calc(100vw - 28px))" destroy-on-close @closed="resetCreateSiteForm">
      <div class="fields site-dialog-form">
        <div class="form-block-title"><b>01</b><div><strong>网站身份与入口</strong><small>只允许访问配置的站点和图片 CDN 域名</small></div></div>
        <div class="two"><el-input v-model="createSiteForm.name" autofocus placeholder="网站名称" /><el-input v-model="createSiteForm.startUrl" placeholder="起始列表页，例如 https://example.com/gallery" /></div>
        <el-input v-model="createSiteForm.allowedHosts" placeholder="允许域名，多个用逗号分隔（含图片 CDN）" />
        <div class="form-block-title"><b>02</b><div><strong>单次任务安全上限</strong><small>避免错误规则产生无限翻页或超量下载</small></div></div>
        <div class="limits"><label><span>列表页上限</span><el-input-number v-model="createSiteForm.maxListPages" :min="1" :max="1000" /></label><label><span>图片页上限</span><el-input-number v-model="createSiteForm.maxDetailPages" :min="1" :max="20000" /></label><label><span>图片上限</span><el-input-number v-model="createSiteForm.maxImages" :min="1" :max="50000" /></label></div>
      </div>
      <template #footer><el-button @click="siteDialogVisible = false">取消</el-button><el-button type="primary" :loading="siteSaveBusy" @click="createSite">创建网站</el-button></template>
    </el-dialog>
  </main>
</template>

<style scoped>
.crawler-page{display:grid;gap:18px;padding:28px 30px 112px;color:var(--text-primary)}
.crawler-hero{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:5px 2px 8px}.crawler-hero h1{margin:2px 0 0;font-family:'Iowan Old Style','Songti SC',serif;font-size:34px;letter-spacing:-.025em}.crawler-hero .subtitle{margin:7px 0 0;color:var(--text-secondary);font-size:13px}.eyebrow{margin:0;color:var(--accent);font-size:10px;font-weight:800;letter-spacing:.17em}.crawler-tabs{position:relative;display:grid;grid-template-columns:repeat(4,1fr);padding:4px;border:1px solid var(--separator);border-radius:15px;background:var(--bg-secondary);isolation:isolate}.crawler-tab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:-1;width:calc((100% - 8px)/4)!important;border:1px solid var(--separator);border-radius:11px;background:var(--bg-card);box-shadow:0 5px 15px rgba(0,0,0,.09);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.crawler-segment{display:flex;align-items:center;justify-content:center;gap:7px;padding:10px 12px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.crawler-segment.active{color:var(--accent);font-weight:700}.crawler-segment b{min-width:21px;padding:2px 6px;border-radius:999px;background:color-mix(in srgb,var(--accent) 13%,transparent);font-size:10px}.crawler-segment:focus-visible{outline:2px solid var(--accent);outline-offset:-2px;border-radius:11px}
.panel{padding:24px;border:1px solid var(--separator);border-radius:22px;background:var(--bg-card);box-shadow:0 16px 42px rgba(0,0,0,.08)}.section-heading,.review-panel>header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:20px}.section-heading h2,.review-panel>header h2{margin:2px 0 0;font-family:'Iowan Old Style','Songti SC',serif;font-size:23px}.section-heading p:not(.eyebrow),.review-panel>header p{margin:5px 0 0;color:var(--text-secondary);font-size:12px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.metric-card{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:12px;padding:18px;border:1px solid var(--separator);border-radius:17px;background:var(--bg-secondary)}.metric-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:color-mix(in srgb,var(--accent) 13%,transparent);color:var(--accent);font-size:20px}.metric-card strong{font-size:25px;line-height:1}.metric-card p{margin:5px 0 0;color:var(--text-secondary);font-size:11px}.metric-card small{grid-column:1/-1;color:var(--text-tertiary);font-size:10px}.pipeline-strip{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:20px 0 27px;padding:16px;border:1px solid var(--separator);border-radius:17px;background:linear-gradient(120deg,color-mix(in srgb,var(--accent) 8%,var(--bg-secondary)),var(--bg-secondary))}.pipeline-strip article{display:flex;min-width:0;align-items:center;gap:10px}.pipeline-strip article>b{display:grid;flex:0 0 30px;height:30px;place-items:center;border-radius:10px;background:var(--bg-card);color:var(--accent);font-size:10px}.pipeline-strip article div{display:grid;gap:3px}.pipeline-strip article strong{font-size:12px}.pipeline-strip article small{color:var(--text-tertiary);font-size:9px}.pipeline-strip>i{color:var(--text-tertiary);font-style:normal}.task-name{display:grid;gap:4px}.task-name strong{font-size:12px}.task-name p{margin:0;color:var(--text-tertiary);font-size:10px}
.site-layout,.rule-layout,.task-layout{display:grid;grid-template-columns:260px minmax(0,1fr);gap:22px}.rule-layout{grid-template-columns:minmax(0,1fr) 260px}.fields{display:grid;gap:12px}.two{display:grid;grid-template-columns:1fr 1fr;align-items:center;gap:11px}.sites,.rules,.jobs{display:grid;align-content:start;gap:8px;max-height:420px;overflow:auto}.sites button,.rules button,.jobs button{display:grid;gap:5px;padding:11px;border:1px solid var(--separator);border-radius:13px;background:var(--bg-secondary);color:var(--text-primary);text-align:left;cursor:pointer;transition:border-color .18s ease,background .18s ease}.sites button.active,.rules button.active,.jobs button.active{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 10%,var(--bg-secondary))}.sites button{grid-template-columns:38px minmax(0,1fr);align-items:center}.site-mark{display:grid;width:38px;height:38px;place-items:center;border-radius:12px;background:color-mix(in srgb,var(--accent) 14%,transparent);color:var(--accent);font-size:16px;font-weight:800}.sites button>span:last-child,.jobs button>span{display:grid;min-width:0;gap:4px}.sites small,.rules small,.jobs small{overflow:hidden;color:var(--text-tertiary);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.rules>strong{margin-bottom:4px;font-size:12px}.rule-name{display:flex;align-items:center;justify-content:space-between;gap:8px}.form-block-title{display:flex;align-items:center;gap:10px;margin-top:4px;padding-top:4px}.form-block-title>b{display:grid;width:31px;height:31px;place-items:center;border-radius:10px;background:color-mix(in srgb,var(--accent) 12%,transparent);color:var(--accent);font-size:10px}.form-block-title>div{display:grid;gap:2px}.form-block-title strong{font-size:12px}.form-block-title small{color:var(--text-tertiary);font-size:10px}.limits{display:grid;grid-template-columns:repeat(3,1fr);gap:11px}.limits label{display:grid;gap:6px;color:var(--text-secondary);font-size:10px}.limits :deep(.el-input-number){width:100%}.actions,.task-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.rule-block{margin-top:4px;padding-top:13px;border-top:1px solid var(--separator)}.rule-block strong{display:block;margin-top:4px;font-size:13px}.limit-field{display:flex;align-items:center;gap:8px;color:var(--text-secondary);font-size:10px}.preview-list,.page-list{display:grid;gap:7px;max-height:240px;margin-top:16px;overflow:auto}.preview-list{padding:13px;border-radius:13px;background:var(--bg-secondary)}.preview-list strong{font-size:11px}.preview-list a,.page-list>div{overflow:hidden;color:var(--text-secondary);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.page-list>div{display:flex;align-items:center;gap:7px;padding:7px 3px;border-bottom:1px solid var(--separator)}.page-list span.excluded{text-decoration:line-through;opacity:.55}
.site-dialog-form{padding:0 2px 8px}
.task-summary{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.task-summary h3{margin:3px 0 0;font-size:18px}.stats{display:grid;grid-template-columns:repeat(6,1fr);gap:8px;margin:16px 0}.stats span{display:grid;gap:4px;padding:10px;border-radius:11px;background:var(--bg-secondary);color:var(--text-secondary);font-size:9px}.stats b{color:var(--text-primary);font-size:16px}.task-actions{justify-content:flex-start;margin:14px 0}.page-toolbar{display:grid;grid-template-columns:minmax(220px,1fr) auto;gap:10px;margin-top:16px}.page-pagination,.asset-pagination{justify-content:center;margin-top:18px}.review-heading :deep(.el-select){width:min(320px,40vw)}.status-filter{width:130px}.similarity-bar{display:grid;grid-template-columns:auto minmax(220px,1fr) auto auto;align-items:center;gap:12px;margin-bottom:14px;padding:11px 13px;border-radius:13px;background:var(--bg-secondary);color:var(--text-secondary);font-size:10px}.organize-bar{display:grid;grid-template-columns:minmax(180px,1fr) auto minmax(160px,.5fr) minmax(180px,.7fr);gap:8px;margin-bottom:14px}.import-batch{margin:0 0 14px;padding:13px;border-radius:13px;background:var(--bg-secondary);color:var(--text-secondary)}.import-toolbar{margin-top:10px}.import-items{display:grid;gap:7px;max-height:210px;margin-top:10px;overflow:auto}.import-items>div{display:flex;align-items:center;gap:8px;color:var(--text-secondary);font-size:10px}.import-error{color:var(--danger,#f56c6c)}
.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}.asset-grid article{position:relative;overflow:hidden;border:2px solid transparent;border-radius:15px;background:var(--bg-secondary);cursor:pointer}.asset-grid article.selected{border-color:var(--accent)}.asset-grid article.duplicate::after,.asset-grid article.similar::before{position:absolute;top:8px;z-index:1;padding:3px 6px;border-radius:8px;color:white;font-size:9px}.asset-grid article.duplicate::after{right:8px;background:#ff9f0a;content:'重复'}.asset-grid article.similar::before{left:8px;background:#6366f1;content:'相似'}.asset-grid img,.placeholder{width:100%;height:145px;object-fit:cover}.placeholder{display:grid;place-items:center;color:var(--text-tertiary)}.asset-copy{display:grid;gap:5px;padding:10px}.asset-copy strong,.asset-copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.asset-copy strong{font-size:12px}.asset-copy small{color:var(--text-secondary);font-size:10px}details{margin-top:18px;color:var(--text-secondary)}.deleted-list{display:flex;flex-wrap:wrap;gap:9px;margin-top:10px}.deleted-list span{padding:6px 9px;border-radius:9px;background:var(--bg-secondary);font-size:10px}
@media(max-width:1050px){.metric-grid{grid-template-columns:repeat(2,1fr)}.pipeline-strip>i{display:none}.pipeline-strip{display:grid;grid-template-columns:repeat(2,1fr)}.stats{grid-template-columns:repeat(3,1fr)}}
@media(max-width:760px){.crawler-page{padding:16px 14px 92px}.crawler-hero,.section-heading,.review-panel>header{display:grid}.crawler-hero h1{font-size:28px}.crawler-tabs{overflow-x:auto;grid-template-columns:repeat(4,minmax(130px,1fr))}.crawler-tab-indicator{display:none}.crawler-segment.active{border-radius:10px;background:var(--bg-card)}.metric-grid,.pipeline-strip,.site-layout,.rule-layout,.task-layout,.two,.page-toolbar,.organize-bar,.limits{grid-template-columns:1fr}.actions{justify-content:flex-start}.similarity-bar{grid-template-columns:1fr}.similarity-bar small{grid-column:1}.review-heading :deep(.el-select){width:100%}}
@media(prefers-reduced-motion:reduce){.crawler-tab-indicator{transition:none}}
</style>

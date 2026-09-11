<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck, Collection, Connection, Delete, Download, EditPen, FolderOpened,
  List, MagicStick, Picture, Plus, PriceTag, Refresh, Search, Setting, VideoPlay, Warning,
} from '@element-plus/icons-vue'
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
const tabOptions = computed(() => [
  { label: '采集概览', value: 'overview' },
  { label: sites.value.length ? `网站与规则  ${sites.value.length}` : '网站与规则', value: 'sites' },
  { label: activeJobs.value.length ? `采集任务  ${activeJobs.value.length}` : '采集任务', value: 'tasks' },
  { label: reviewJobs.value.length ? `待整理区  ${reviewJobs.value.length}` : '待整理区', value: 'review' },
])
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

function selectSiteById(id: string) {
  const site = sites.value.find(item => item.id === Number(id))
  if (site) void selectSite(site)
}

function selectRuleById(id: string) {
  const rule = rules.value.find(item => item.id === Number(id))
  if (rule) useRule(rule)
}

function selectJobById(id: string) {
  activeJobId.value = Number(id)
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

function toggleAllDownloadable(selected: string | number | boolean) {
  if (Boolean(selected)) selectAllDownloadable()
  else selectedAssets.value = new Set()
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
    <el-card class="hero-card surface-card" shadow="never">
      <div class="crawler-hero">
        <div class="hero-copy">
          <span class="hero-symbol"><el-icon><MagicStick /></el-icon></span>
          <div><p class="eyebrow">PRIVATE COLLECTION AUTOMATION</p><h1>图片采集中心</h1><p class="subtitle">发现、确认、下载、整理，每一步都由你掌控。</p></div>
        </div>
        <el-space wrap>
          <el-tag v-if="activeJobs.length" type="primary" effect="light" round>{{ activeJobs.length }} 个任务运行中</el-tag>
          <el-tag v-else type="success" effect="light" round><el-icon><CircleCheck /></el-icon>&nbsp;系统空闲</el-tag>
          <el-button :icon="Refresh" circle aria-label="刷新采集数据" @click="refresh" />
          <el-button type="primary" :icon="Plus" round @click="activeTab = 'sites'; openNewSiteDialog()">新增网站</el-button>
        </el-space>
      </div>
    </el-card>

    <div class="workspace-switcher surface-card">
      <el-segmented v-model="activeTab" :options="tabOptions" block aria-label="图片采集中心栏目" />
    </div>

    <section v-if="activeTab === 'overview'" class="overview-workspace" role="tabpanel">
      <div class="metric-grid">
        <el-card v-for="metric in metrics" :key="metric.label" class="metric-card surface-card" shadow="never">
          <div class="metric-top"><span class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></span><el-statistic :value="metric.value" /></div>
          <strong>{{ metric.label }}</strong><p>{{ metric.note }}</p>
        </el-card>
      </div>

      <div class="overview-grid">
        <el-card class="pipeline-card surface-card" shadow="never">
          <template #header><div class="card-heading"><div><p class="eyebrow">WORKFLOW</p><h2>采集流程</h2><p>先确认链接范围，再把文件安全带回图库。</p></div><el-tag type="info" effect="plain" round>4 个阶段</el-tag></div></template>
          <el-steps :active="activeJobs.length ? 1 : 0" align-center finish-status="success">
            <el-step title="发现链接" description="扫描列表页" :icon="Search" />
            <el-step title="人工确认" description="筛选图片页" :icon="CircleCheck" />
            <el-step title="下载校验" description="域名与大小限制" :icon="Download" />
            <el-step title="整理入库" description="去重与标注" :icon="Collection" />
          </el-steps>
        </el-card>

        <el-card class="quick-card surface-card" shadow="never">
          <template #header><div class="card-heading"><div><p class="eyebrow">QUICK START</p><h2>快速开始</h2></div></div></template>
          <el-timeline>
            <el-timeline-item type="primary" hollow><strong>添加采集网站</strong><p>限定入口与允许访问的域名</p></el-timeline-item>
            <el-timeline-item type="primary" hollow><strong>配置解析规则</strong><p>选择图片页链接与原图节点</p></el-timeline-item>
            <el-timeline-item type="success" hollow><strong>启动扫描</strong><p>人工确认后再下载图片</p></el-timeline-item>
          </el-timeline>
          <el-button type="primary" :icon="Plus" round @click="activeTab = 'sites'; openNewSiteDialog()">配置第一个网站</el-button>
        </el-card>
      </div>

      <el-card class="recent-card surface-card" shadow="never">
        <template #header><div class="card-heading"><div><p class="eyebrow">RECENT ACTIVITY</p><h2>最近任务</h2></div><el-button link type="primary" :icon="Refresh" @click="refresh">刷新</el-button></div></template>
        <el-table v-if="recentJobs.length" :data="recentJobs" table-layout="fixed">
          <el-table-column label="任务" min-width="230"><template #default="{ row }"><div class="task-name"><strong>{{ row.name }}</strong><p>{{ phaseLabel(row.phase) }} · {{ formatTime(row.createdAt) }}</p></div></template></el-table-column>
          <el-table-column label="进度" min-width="190"><template #default="{ row }"><el-progress :percentage="jobProgress(row)" :stroke-width="7" /></template></el-table-column>
          <el-table-column label="已下载" prop="imagesDownloaded" width="100" align="center" />
          <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light" round>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="" width="100" align="right"><template #default="{ row }"><el-button link type="primary" @click="openJob(row, row.phase === 'REVIEW' ? 'review' : 'tasks')">查看</el-button></template></el-table-column>
        </el-table>
        <el-empty v-else description="暂无采集任务"><el-button type="primary" link @click="activeTab = 'sites'">前往配置网站</el-button></el-empty>
      </el-card>
    </section>

    <section v-else-if="activeTab === 'sites'" class="source-workspace" role="tabpanel">
      <el-card class="source-sidebar surface-card" shadow="never">
        <template #header><div class="card-heading"><div><p class="eyebrow">SOURCES</p><h2>采集网站</h2></div><el-button type="primary" :icon="Plus" circle aria-label="新增网站" @click="openNewSiteDialog" /></div></template>
        <el-scrollbar max-height="620px">
          <el-menu v-if="sites.length" :default-active="String(activeSiteId || '')" class="source-menu" @select="selectSiteById">
            <el-menu-item v-for="site in sites" :key="site.id" :index="String(site.id)">
              <span class="source-avatar">{{ site.name.slice(0, 1) }}</span>
              <div class="menu-copy"><strong>{{ site.name }}</strong><small>{{ site.startUrl }}</small></div>
            </el-menu-item>
          </el-menu>
          <el-empty v-else description="还没有采集网站" :image-size="72"><el-button type="primary" link @click="openNewSiteDialog">立即添加</el-button></el-empty>
        </el-scrollbar>
      </el-card>

      <div class="source-main">
        <el-card class="site-editor surface-card" shadow="never">
          <template #header><div class="card-heading"><div><p class="eyebrow">SITE SETTINGS</p><h2>{{ activeSiteId ? '网站配置' : '选择网站' }}</h2><p v-if="activeSiteId">入口、访问边界与单次任务上限。</p></div><el-button v-if="activeSiteId" type="primary" :icon="CircleCheck" round @click="saveSite">保存更改</el-button></div></template>
          <el-form v-if="activeSiteId" :model="siteForm" label-position="top" class="mac-form">
            <el-row :gutter="16">
              <el-col :xs="24" :md="10"><el-form-item label="网站名称"><el-input v-model="siteForm.name" placeholder="例如：家庭活动图库" /></el-form-item></el-col>
              <el-col :xs="24" :md="14"><el-form-item label="起始列表页"><el-input v-model="siteForm.startUrl" placeholder="https://example.com/gallery" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="允许访问的域名"><el-input v-model="siteForm.allowedHosts" placeholder="多个域名用逗号分隔，包含图片 CDN"><template #prefix><el-icon><Connection /></el-icon></template></el-input></el-form-item></el-col>
            </el-row>
            <el-divider content-position="left"><el-icon><Setting /></el-icon>&nbsp;安全上限</el-divider>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="8"><el-form-item label="列表页"><el-input-number v-model="siteForm.maxListPages" :min="1" :max="1000" controls-position="right" /></el-form-item></el-col>
              <el-col :xs="24" :sm="8"><el-form-item label="图片页"><el-input-number v-model="siteForm.maxDetailPages" :min="1" :max="20000" controls-position="right" /></el-form-item></el-col>
              <el-col :xs="24" :sm="8"><el-form-item label="图片数量"><el-input-number v-model="siteForm.maxImages" :min="1" :max="50000" controls-position="right" /></el-form-item></el-col>
            </el-row>
          </el-form>
          <el-empty v-else description="从左侧选择网站，或新建一个采集来源" :image-size="80"><el-button type="primary" :icon="Plus" @click="openNewSiteDialog">新增网站</el-button></el-empty>
        </el-card>

        <el-card class="rule-editor surface-card" shadow="never">
          <template #header><div class="card-heading"><div><p class="eyebrow">PARSING RULES</p><h2>解析规则</h2><p>定义怎样发现详情页并提取原图。</p></div><el-button :icon="Plus" round :disabled="!activeSiteId" @click="newRule">新增规则</el-button></div></template>
          <el-empty v-if="!activeSiteId" description="选择网站后配置解析规则" :image-size="76" />
          <div v-else class="rule-workspace">
            <aside class="rule-sidebar">
              <el-menu v-if="rules.length" :default-active="String(form.id || '')" class="source-menu" @select="selectRuleById">
                <el-menu-item v-for="rule in rules" :key="rule.id" :index="String(rule.id)">
                  <div class="menu-copy"><strong>{{ rule.name }}</strong><small>{{ rule.enabled ? '当前生效规则' : '已停用' }}</small></div>
                  <el-tag v-if="rule.enabled" size="small" type="success" effect="light" round>生效</el-tag>
                </el-menu-item>
              </el-menu>
              <el-empty v-else description="暂无规则" :image-size="56" />
            </aside>
            <el-form :model="form" label-position="top" class="mac-form rule-form">
              <el-row :gutter="16">
                <el-col :xs="24" :md="16"><el-form-item label="规则名称"><el-input v-model="form.name" placeholder="例如：默认图片解析" /></el-form-item></el-col>
                <el-col :xs="24" :md="8"><el-form-item label="任务状态"><el-switch v-model="form.enabled" inline-prompt active-text="生效" inactive-text="停用" /></el-form-item></el-col>
              </el-row>
              <el-divider content-position="left">链接发现</el-divider>
              <el-row :gutter="16">
                <el-col :xs="24" :md="12"><el-form-item label="图片页链接选择器"><el-input v-model="form.detailSelector" placeholder="CSS 选择器" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="列表下一页选择器"><el-input v-model="form.nextSelector" placeholder="可留空" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="URL 必须包含"><el-input v-model="form.detailUrlIncludes" placeholder="多个片段用逗号分隔" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="URL 排除片段"><el-input v-model="form.detailUrlExcludes" placeholder="多个片段用逗号分隔" /></el-form-item></el-col>
              </el-row>
              <el-divider content-position="left">原图提取</el-divider>
              <el-row :gutter="16">
                <el-col :xs="24" :md="12"><el-form-item label="图片选择器"><el-input v-model="form.imageSelector" placeholder="例如 img.gallery-image" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="图片属性优先级"><el-input v-model="form.imageAttributes" placeholder="data-original,data-src,src" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="图片 URL 必须包含"><el-input v-model="form.imageUrlIncludes" placeholder="可留空" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="图片 URL 排除片段"><el-input v-model="form.imageUrlExcludes" placeholder="例如 thumb,avatar" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="详情下一页选择器"><el-input v-model="form.detailNextSelector" placeholder="可留空" /></el-form-item></el-col>
                <el-col :xs="24" :md="12"><el-form-item label="每个详情页组上限"><el-input-number v-model="form.maxPagesPerDetail" :min="1" :max="100" controls-position="right" /></el-form-item></el-col>
              </el-row>
              <div class="form-actions"><el-button :icon="VideoPlay" :loading="busy === 'preview'" @click="testRule">测试规则</el-button><el-button :icon="CircleCheck" @click="saveRule()">保存规则</el-button><el-button type="primary" :icon="Search" :loading="busy === 'discover'" :disabled="!form.enabled" @click="startDiscovery">开始扫描</el-button></div>
            </el-form>
          </div>
          <el-alert v-if="previewImageError" class="preview-error" type="warning" show-icon :closable="false" :title="`首个图片页解析失败：${previewImageError}`" />
          <el-collapse v-if="previewUrls.length || previewImageUrls.length" class="preview-collapse">
            <el-collapse-item v-if="previewUrls.length" :title="`图片页链接预览（${previewUrls.length}）`" name="pages"><el-scrollbar max-height="220px"><div class="url-list"><el-link v-for="url in previewUrls" :key="url" :href="url" target="_blank" type="primary">{{ url }}</el-link></div></el-scrollbar></el-collapse-item>
            <el-collapse-item v-if="previewImageUrls.length" :title="`原图地址预览（${previewImageUrls.length}）`" name="images"><el-scrollbar max-height="220px"><div class="url-list"><el-link v-for="url in previewImageUrls" :key="url" :href="url" target="_blank" type="primary">{{ url }}</el-link></div></el-scrollbar></el-collapse-item>
          </el-collapse>
        </el-card>
      </div>
    </section>

    <section v-else-if="activeTab === 'tasks'" class="task-workspace" role="tabpanel">
      <el-card class="task-sidebar surface-card" shadow="never">
        <template #header><div class="card-heading"><div><p class="eyebrow">QUEUE</p><h2>采集任务</h2></div><el-button :icon="Refresh" circle aria-label="刷新任务" @click="refresh" /></div></template>
        <el-scrollbar max-height="680px">
          <el-menu v-if="jobs.length" :default-active="String(activeJobId || '')" class="source-menu job-menu" @select="selectJobById">
            <el-menu-item v-for="job in jobs" :key="job.id" :index="String(job.id)">
              <span class="job-dot" :class="job.status.toLowerCase()" />
              <div class="menu-copy"><strong>{{ job.name }}</strong><small>{{ phaseLabel(job.phase) }} · {{ formatTime(job.createdAt) }}</small></div>
              <el-tag size="small" :type="statusType(job.status)" effect="light" round>{{ statusLabel(job.status) }}</el-tag>
            </el-menu-item>
          </el-menu>
          <el-empty v-else description="暂无采集任务" :image-size="72" />
        </el-scrollbar>
      </el-card>

      <el-card class="task-detail surface-card" shadow="never">
        <template v-if="activeJob" #header>
          <div class="card-heading task-title"><div><p class="eyebrow">{{ phaseLabel(activeJob.phase) }}</p><h2>{{ activeJob.name }}</h2><p>创建于 {{ formatTime(activeJob.createdAt) }}</p></div><el-tag :type="statusType(activeJob.status)" effect="light" round size="large">{{ statusLabel(activeJob.status) }}</el-tag></div>
        </template>
        <template v-if="activeJob">
          <div class="progress-card"><div><strong>任务进度</strong><span>{{ jobProgress(activeJob) }}%</span></div><el-progress :percentage="jobProgress(activeJob)" :stroke-width="10" :show-text="false" /></div>
          <el-descriptions :column="3" border class="job-stats">
            <el-descriptions-item label="已扫描列表页">{{ activeJob.listProcessed }}</el-descriptions-item>
            <el-descriptions-item label="已发现图片页">{{ activeJob.pagesFound }}</el-descriptions-item>
            <el-descriptions-item label="已处理图片页">{{ activeJob.pagesProcessed }}</el-descriptions-item>
            <el-descriptions-item label="已下载图片">{{ activeJob.imagesDownloaded }}</el-descriptions-item>
            <el-descriptions-item label="失败项目">{{ activeJob.failCount }}</el-descriptions-item>
            <el-descriptions-item label="执行次数">{{ activeJob.attemptCount }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="activeJob.errorMessage" type="error" show-icon :title="activeJob.errorMessage" :closable="false" />
          <el-space class="task-actions" wrap>
            <el-button v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" type="primary" :icon="Download" round :disabled="includedPageTotal === 0" @click="startDownload">确认 {{ includedPageTotal }} 页并下载</el-button>
            <el-button v-if="['RUNNING','QUEUED'].includes(activeJob.status)" round @click="controlJob('pause')">暂停任务</el-button>
            <el-button v-if="['PAUSED','FAILED','PARTIAL'].includes(activeJob.status)" type="primary" plain round @click="controlJob('resume')">继续 / 重试</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED'].includes(activeJob.status)" type="danger" plain round @click="controlJob('cancel')">取消任务</el-button>
            <el-button v-if="activeJob.phase === 'REVIEW'" type="primary" plain round @click="activeTab = 'review'">进入待整理区</el-button>
            <el-button v-if="!['RUNNING','QUEUED'].includes(activeJob.status)" type="danger" link :icon="Delete" @click="purgeJob">永久清理</el-button>
          </el-space>
          <el-divider />
          <div class="card-heading page-heading"><div><h3>图片页清单</h3><p>检查发现结果，并决定哪些页面进入下载阶段。</p></div><el-space v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" wrap><el-button size="small" @click="setCurrentPagesIncluded(true)">保留本页</el-button><el-button size="small" @click="setCurrentPagesIncluded(false)">排除本页</el-button></el-space></div>
          <el-input v-model="pageQuery" class="page-search" clearable placeholder="搜索图片页 URL" :prefix-icon="Search" @keyup.enter="searchPages" @clear="searchPages"><template #append><el-button @click="searchPages">搜索</el-button></template></el-input>
          <el-table :data="pages" table-layout="fixed" empty-text="当前任务暂无图片页记录">
            <el-table-column v-if="activeJob.phase === 'AWAITING_CONFIRMATION'" label="保留" width="72" align="center"><template #default="{ row }"><el-checkbox :model-value="row.included" @change="onPageIncludedChange(row, $event)" /></template></el-table-column>
            <el-table-column label="图片页 URL" min-width="360" show-overflow-tooltip><template #default="{ row }"><span :class="{ excluded: !row.included }">{{ row.url }}</span></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag size="small" :type="row.status === 'FAILED' ? 'danger' : row.status === 'SUCCEEDED' ? 'success' : 'info'" effect="light" round>{{ row.status }}</el-tag></template></el-table-column>
          </el-table>
          <el-pagination v-if="pageTotal > pagePageSize" class="pagination" layout="prev, pager, next, total" :current-page="pagePage + 1" :page-size="pagePageSize" :total="pageTotal" @current-change="changePageListPage" />
        </template>
        <el-empty v-else description="从左侧选择一个任务查看详情" :image-size="92" />
      </el-card>
    </section>

    <section v-else class="review-workspace" role="tabpanel">
      <el-card class="review-toolbar surface-card" shadow="never">
        <div class="card-heading review-heading">
          <div><p class="eyebrow">CURATION WORKSPACE</p><h2>待整理区</h2><p>筛选、去重和标注后，再将图片导入私人图库。</p></div>
          <el-select v-model="activeJobId" class="review-job-select" placeholder="选择待整理任务"><template #prefix><el-icon><Collection /></el-icon></template><el-option v-for="job in reviewJobs" :key="job.id" :label="`${job.name}（${job.imagesDownloaded} 张）`" :value="job.id" /></el-select>
        </div>
      </el-card>
      <el-empty v-if="!activeJob || (!assets.length && activeJob.phase !== 'REVIEW')" class="surface-card review-empty" description="暂无可整理的图片，完成下载后会出现在这里" :image-size="110" />
      <template v-else>
        <el-card class="filter-card surface-card" shadow="never">
          <el-form label-position="top" class="review-filter-form">
            <el-form-item label="图片状态"><el-select v-model="assetStatus" :disabled="exactDuplicateOnly || similarOnly" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option label="可入库" value="DOWNLOADED" /><el-option label="已删除" value="DELETED" /><el-option label="图库重复" value="DUPLICATE" /><el-option label="已入库" value="IMPORTED" /><el-option label="失败" value="FAILED" /></el-select></el-form-item>
            <el-form-item label="智能筛选"><el-space wrap><el-checkbox v-model="exactDuplicateOnly" border>精确重复</el-checkbox><el-checkbox v-model="similarOnly" border>相似图片</el-checkbox></el-space></el-form-item>
            <el-form-item label="相似度阈值" class="similarity-field"><el-slider v-model="similarityThreshold" :min="0" :max="16" show-input /><el-button :icon="MagicStick" :loading="busy === 'similarity'" @click="analyzeSimilarity">分析</el-button></el-form-item>
          </el-form>
          <el-divider />
          <el-descriptions :column="4" class="review-stats">
            <el-descriptions-item label="任务图片">{{ assetTotal }}</el-descriptions-item>
            <el-descriptions-item label="本页可见">{{ reviewAssets.length }}</el-descriptions-item>
            <el-descriptions-item label="精确重复组">{{ duplicateHashes.size }}</el-descriptions-item>
            <el-descriptions-item label="已删除">{{ deletedAssets.length }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="selection-bar surface-card" shadow="never">
          <div class="selection-summary"><el-checkbox :model-value="selectedAssets.size > 0" @change="toggleAllDownloadable">已选择 <strong>{{ selectedAssets.size }}</strong> 张</el-checkbox><el-button link type="primary" @click="selectAllDownloadable">选择本页可入库图片</el-button></div>
          <el-space wrap><el-button :icon="Delete" :disabled="!selectedAssets.size" @click="deleteSelected">删除</el-button><el-button type="primary" :icon="Download" round :loading="busy === 'import'" :disabled="!selectedAssets.size" @click="importSelected">导入图库</el-button></el-space>
        </el-card>

        <el-card class="organize-card surface-card" shadow="never">
          <template #header><div class="card-heading"><div><p class="eyebrow">BATCH METADATA</p><h3>批量整理</h3></div></div></template>
          <div class="organize-grid">
            <el-input v-model="batchNote" clearable placeholder="给选中图片添加备注"><template #prefix><el-icon><EditPen /></el-icon></template></el-input>
            <el-button :disabled="!selectedAssets.size" @click="editSelected">应用备注</el-button>
            <el-select v-model="targetAlbumId" clearable placeholder="目标相册"><template #prefix><el-icon><FolderOpened /></el-icon></template><el-option v-for="album in albums" :key="album.id" :label="album.name" :value="album.id" /></el-select>
            <el-select v-model="targetTagIds" multiple collapse-tags clearable placeholder="入库标签"><template #prefix><el-icon><PriceTag /></el-icon></template><el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" /></el-select>
          </div>
        </el-card>

        <el-collapse v-if="recentImportBatch" class="history-collapse surface-card">
          <el-collapse-item name="import">
            <template #title><div class="collapse-title"><strong>最近导入批次 #{{ recentImportBatch.id }}</strong><el-tag :type="recentImportBatch.fail ? 'warning' : 'success'" size="small" effect="light" round>{{ recentImportBatch.status }}</el-tag><span>成功 {{ recentImportBatch.success }} · 跳过 {{ recentImportBatch.skipped }} · 失败 {{ recentImportBatch.fail }}</span></div></template>
            <el-radio-group :model-value="importItemStatus" size="small" @change="filterImportItems(String($event))"><el-radio-button value="">全部</el-radio-button><el-radio-button value="SUCCESS">成功</el-radio-button><el-radio-button value="SKIPPED">跳过</el-radio-button><el-radio-button value="FAILED">失败</el-radio-button></el-radio-group>
            <el-table :data="recentImportItems" size="small" empty-text="当前筛选没有明细">
              <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="row.status === 'FAILED' ? 'danger' : row.status === 'SUCCESS' ? 'success' : 'info'" effect="light">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="待整理图片" width="130"><template #default="{ row }">#{{ row.assetId }}</template></el-table-column>
              <el-table-column label="图库照片" width="130"><template #default="{ row }">{{ row.photoId ? `#${row.photoId}` : '—' }}</template></el-table-column>
              <el-table-column label="说明" prop="errorMessage" min-width="220" />
            </el-table>
          </el-collapse-item>
        </el-collapse>

        <div class="asset-grid">
          <el-card v-for="asset in reviewAssets" :key="asset.id" class="asset-card surface-card" :class="{ selected: selectedAssets.has(asset.id) }" shadow="hover" @click="asset.status === 'DOWNLOADED' && toggleAsset(asset.id)">
            <div class="asset-preview">
              <el-image v-if="previewImages[asset.id]" :src="previewImages[asset.id]" fit="cover" loading="lazy" />
              <div v-else class="placeholder"><el-icon><Picture /></el-icon><span>{{ asset.status }}</span></div>
              <el-checkbox v-if="asset.status === 'DOWNLOADED'" class="asset-check" :model-value="selectedAssets.has(asset.id)" @click.stop @change="toggleAsset(asset.id)" />
              <div class="asset-badges"><el-tag v-if="asset.exactDuplicateCount > 1" size="small" type="warning" effect="dark" round>重复 × {{ asset.exactDuplicateCount }}</el-tag><el-tag v-if="asset.similarityCount > 1" size="small" type="primary" effect="dark" round>相似 × {{ asset.similarityCount }}</el-tag></div>
            </div>
            <div class="asset-copy"><strong :title="asset.originalFilename">{{ asset.originalFilename || `图片 #${asset.id}` }}</strong><small>{{ asset.width || '?' }} × {{ asset.height || '?' }} · {{ formatBytes(asset.fileSize) }}</small><div class="asset-status"><el-tag v-if="asset.libraryDuplicate" size="small" type="info">图库已有</el-tag><el-tag v-if="asset.libraryTrashDuplicate" size="small" type="danger">回收站已有</el-tag><el-tag v-if="asset.status === 'IMPORTED'" size="small" type="success">已入库</el-tag><el-tag v-if="asset.status === 'DUPLICATE'" size="small" type="info">已跳过重复</el-tag></div></div>
          </el-card>
        </div>
        <el-pagination v-if="assetTotal > assetPageSize" class="pagination" layout="prev, pager, next, total" :current-page="assetPage + 1" :page-size="assetPageSize" :total="assetTotal" @current-change="changeAssetPage" />
        <el-collapse v-if="deletedAssets.length" class="deleted-collapse surface-card"><el-collapse-item :title="`已删除（${deletedAssets.length}）`" name="deleted"><el-table :data="deletedAssets" size="small"><el-table-column label="文件" prop="originalFilename" min-width="240" /><el-table-column label="大小" width="120"><template #default="{ row }">{{ formatBytes(row.fileSize) }}</template></el-table-column><el-table-column label="" width="90"><template #default="{ row }"><el-button link type="primary" @click="restoreDeleted(row.id)">恢复</el-button></template></el-table-column></el-table></el-collapse-item></el-collapse>
      </template>
    </section>

    <el-dialog v-model="siteDialogVisible" class="site-dialog" width="min(680px, calc(100vw - 28px))" destroy-on-close align-center @closed="resetCreateSiteForm">
      <template #header><div class="dialog-heading"><span class="dialog-icon"><el-icon><Connection /></el-icon></span><div><h2>新增采集网站</h2><p>设置采集入口与访问边界，创建后再配置解析规则。</p></div></div></template>
      <el-form :model="createSiteForm" label-position="top" class="mac-form site-dialog-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="10"><el-form-item label="网站名称" required><el-input v-model="createSiteForm.name" autofocus placeholder="例如：家庭活动图库" /></el-form-item></el-col>
          <el-col :xs="24" :md="14"><el-form-item label="起始列表页" required><el-input v-model="createSiteForm.startUrl" placeholder="https://example.com/gallery" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="允许访问的域名"><el-input v-model="createSiteForm.allowedHosts" placeholder="留空时自动使用起始 URL 的域名；多个域名用逗号分隔" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left"><el-icon><Setting /></el-icon>&nbsp;单次任务安全上限</el-divider>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8"><el-form-item label="列表页"><el-input-number v-model="createSiteForm.maxListPages" :min="1" :max="1000" controls-position="right" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="图片页"><el-input-number v-model="createSiteForm.maxDetailPages" :min="1" :max="20000" controls-position="right" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="图片数量"><el-input-number v-model="createSiteForm.maxImages" :min="1" :max="50000" controls-position="right" /></el-form-item></el-col>
        </el-row>
        <el-alert type="info" show-icon :closable="false" title="采集器只会访问允许域名中的页面和图片资源。" />
      </el-form>
      <template #footer><el-button round @click="siteDialogVisible = false">取消</el-button><el-button type="primary" :icon="Plus" round :loading="siteSaveBusy" @click="createSite">创建网站</el-button></template>
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
<style scoped src="../assets/crawler-view.css"></style>

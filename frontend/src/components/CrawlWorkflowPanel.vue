<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { crawlApi, type CrawlScanPage, type DiscoveredPage, type CrawlSiteQueue } from '@/api/crawlApi'
import { settingApi, type CrawlerExecutor, type CrawlerProxy } from '@/api/settingApi'
import type { CrawlJob, CrawlSite } from '@/types'
import CrawlerExecutorsSettingsPanel from './CrawlerExecutorsSettingsPanel.vue'

const props = defineProps<{ mode: 'scans' | 'pages' | 'collected-pages' | 'queues'; sites: CrawlSite[]; jobs: CrawlJob[]; scopedSiteId?: number | null }>()
const emit = defineEmits<{ 'open-job': [job: CrawlJob]; changed: [] }>()
const scans = ref<CrawlScanPage[]>([])
const pages = ref<DiscoveredPage[]>([])
const selectedPageIds = ref<number[]>([])
const selectedPages = reactive(new Map<number, DiscoveredPage>())
const selectedCollectableIds = computed(() => [...selectedPages.values()].filter(page => page.collectable).map(page => page.id))
const pageTable = ref()
const queues = ref<CrawlSiteQueue[]>([])
const siteId = ref<number>()
const query = ref('')
const pageNumber = ref(1)
const pageSize = ref(50)
const total = ref(0)
const loading = ref(false)
const action = ref('')
const scanDialog = ref(false)
const queueDialog = ref(false)
const executorManager = ref(false)
const saving = ref(false)
const queue = ref<CrawlSiteQueue>()
const allExecutors = ref<CrawlerExecutor[]>([])
const executorIds = ref<number[]>([])
const proxies = ref<CrawlerProxy[]>([])
const canManage = computed(() => {
  try { return JSON.parse(sessionStorage.getItem('user') || '{}')?.role === 'ADMIN' }
  catch { return false }
})
const autoCollectPriorityOptions = [
  { label: '最高', value: 100 }, { label: '高', value: 75 },
  { label: '普通', value: 50 }, { label: '低', value: 25 }, { label: '最低', value: 0 },
]
const defaultScan = (): CrawlScanPage => ({ siteId: props.scopedSiteId || props.sites[0]?.id || 0, name: '', url: '', autoScanEnabled: false, autoCollectEnabled: false, autoCollectPriority: 75, scanIntervalMinutes: 360, maxPages: 100 })
const form = reactive<CrawlScanPage>(defaultScan())
const visibleScans = computed(() => scans.value.filter(s => {
  const selectedSiteId = props.scopedSiteId ?? siteId.value
  return !selectedSiteId || s.siteId === selectedSiteId
}))
const visibleQueues = computed(() => queues.value.filter(q => {
  const selectedSiteId = props.scopedSiteId ?? siteId.value
  return !selectedSiteId || q.siteId === selectedSiteId
}))
const siteName = (id: number) => props.sites.find(s => s.id === id)?.name || `网站 #${id}`
const time = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未扫描'
const status = (value: string) => ({ PENDING: '待采集', QUEUED: '排队中', RUNNING: '运行中', PAUSED: '已暂停', COMPLETED: '已完成', PARTIAL: '部分成功', FAILED: '失败', CANCELLED: '已取消' }[value] || value)
const errorText = (error: any) => error?.response?.data?.message || error?.message || '操作失败，请重试'
const fail = (error: any) => ElMessage.error(errorText(error))
let timer: ReturnType<typeof setInterval> | undefined
let requestId = 0

async function load(silent = false) {
  const request = ++requestId
  if (!silent) loading.value = true
  try {
    if (props.mode === 'scans') {
      const { data } = await crawlApi.scanPages()
      if (request === requestId) scans.value = data
    } else if (props.mode === 'pages' || props.mode === 'collected-pages') {
      const { data } = await crawlApi.collectionPages(
        pageNumber.value - 1, query.value, siteId.value, props.mode === 'pages', pageSize.value,
        props.mode === 'collected-pages')
      if (request === requestId && pageNumber.value > Math.max(1, data.totalPages)) {
        pageNumber.value = Math.max(1, data.totalPages)
        await load(silent)
        return
      }
      if (request === requestId) { pages.value = data.content; total.value = data.totalElements }
    } else {
      const { data } = await crawlApi.queues()
      if (request === requestId) queues.value = data
    }
  } catch (error) { if (!silent) fail(error) }
  finally { if (request === requestId) loading.value = false }
}
function resetPageSelection() {
  selectedPageIds.value = []
  selectedPages.clear()
  pageTable.value?.clearSelection()
}
function search() { resetPageSelection(); pageNumber.value = 1; void load() }
function changePage() { resetPageSelection(); void load() }
function onPageSelectionChange(selection: DiscoveredPage[]) {
  const visibleIds = new Set(pages.value.map(page => page.id))
  const selectedVisibleIds = new Set(selection.map(page => page.id))
  visibleIds.forEach(id => { if (!selectedVisibleIds.has(id)) selectedPages.delete(id) })
  selection.forEach(page => selectedPages.set(page.id, page))
  selectedPageIds.value = [...selectedPages.keys()]
}
async function removePage(page: DiscoveredPage) {
  try { await ElMessageBox.confirm('删除此扫描结果？关联的采集任务和已下载图片会保留。', '删除待采集页', { type: 'warning' }) }
  catch { return }
  try {
    await crawlApi.deleteCollectionPage(page.id)
    pageTable.value?.toggleRowSelection(page, false)
    selectedPageIds.value = selectedPageIds.value.filter(id => id !== page.id)
    await load()
    ElMessage.success('待采集页已删除')
  } catch (error) { fail(error) }
}
async function removeSelectedPages() {
  const ids = [...selectedPageIds.value]
  if (!ids.length) return
  try { await ElMessageBox.confirm(`删除已选择的 ${ids.length} 个扫描结果？关联的采集任务和已下载图片会保留。`, '批量删除待采集页', { type: 'warning' }) }
  catch { return }
  try {
    await crawlApi.deleteCollectionPages(ids)
    resetPageSelection()
    await load()
    ElMessage.success(`已删除 ${ids.length} 个待采集页`)
  } catch (error) { fail(error) }
}
async function collectSelectedPages() {
  const ids = [...selectedCollectableIds.value]
  if (!ids.length) return
  action.value = 'collect-selected'
  const succeeded: number[] = []
  const failures: string[] = []
  try {
    for (let offset = 0; offset < ids.length; offset += 10) {
      const batch = ids.slice(offset, offset + 10)
      const results = await Promise.all(batch.map(async id => {
        try { await crawlApi.collectPage(id); return { id } }
        catch (error) { return { id, error } }
      }))
      results.forEach(result => {
        if ('error' in result) {
          const page = selectedPages.get(result.id)
          failures.push(`${page?.name || page?.url || `#${result.id}`}: ${errorText(result.error)}`)
        } else succeeded.push(result.id)
      })
    }
    emit('changed')
    resetPageSelection()
    await load()
    if (failures.length) {
      const details = failures.slice(0, 3).join('；')
      ElMessage.warning(`已创建 ${succeeded.length} 个采集任务，${failures.length} 个失败：${details}${failures.length > 3 ? '；…' : ''}`)
    } else ElMessage.success(`已创建 ${succeeded.length} 个采集任务并加入网站队列`)
  } finally { action.value = '' }
}
function editScan(scan?: CrawlScanPage) {
  delete form.id
  Object.assign(form, defaultScan(), scan || {})
  if (!scan && siteId.value) form.siteId = siteId.value
  scanDialog.value = true
}
async function saveScan() {
  if (!form.siteId || !form.name.trim() || !form.url.trim()) { ElMessage.warning('请填写网站、名称和扫描页 URL'); return }
  saving.value = true
  try { await crawlApi.saveScanPage({ ...form }); scanDialog.value = false; await load(); ElMessage.success('扫描页已保存') }
  catch (error) { fail(error) } finally { saving.value = false }
}
async function toggleAuto(scan: CrawlScanPage, value: boolean) {
  action.value = `auto-${scan.id}`
  try { await crawlApi.saveScanPage({ ...scan, autoScanEnabled: value }); await load() }
  catch (error) { fail(error) } finally { action.value = '' }
}
async function toggleAutoCollect(scan: CrawlScanPage, value: boolean) {
  action.value = `auto-collect-${scan.id}`
  try {
    await crawlApi.saveScanPage({ ...scan, autoCollectEnabled: value })
    await load()
    ElMessage.success(value ? '扫描完成后将自动创建采集任务' : '已关闭扫描后自动采集')
  } catch (error) { fail(error) } finally { action.value = '' }
}
async function removeScan(scan: CrawlScanPage) {
  try { await ElMessageBox.confirm(`删除扫描页“${scan.name}”？已有扫描结果和任务会保留。`, '删除扫描页', { type: 'warning' }) }
  catch { return }
  try { await crawlApi.deleteScanPage(scan.id!); await load() } catch (error) { fail(error) }
}
async function scan(page: CrawlScanPage) {
  action.value = `scan-${page.id}`
  try {
    const { data } = await crawlApi.scan(page.id!)
    emit('changed'); emit('open-job', data); await load()
    ElMessage.success('扫描任务已创建，发现的 URL 将实时出现在采集页面')
  } catch (error) { fail(error) } finally { action.value = '' }
}
async function collect(page: DiscoveredPage) {
  action.value = `collect-${page.id}`
  try {
    const { data } = await crawlApi.collectPage(page.id)
    emit('changed'); emit('open-job', data); await load()
    ElMessage.success('采集任务已进入网站队列')
  } catch (error) { fail(error) } finally { action.value = '' }
}
async function openTask(id: number) {
  try { emit('open-job', (await crawlApi.job(id)).data) } catch (error) { fail(error) }
}
async function configureQueue(item: CrawlSiteQueue) {
  try {
    allExecutors.value = (await settingApi.getCrawlerExecutors()).data
    queue.value = item
    executorIds.value = allExecutors.value.filter(e => e.options.siteId === item.siteId).map(e => e.id)
    queueDialog.value = true
  } catch (error) { fail(error) }
}
async function saveQueue() {
  if (!queue.value) return
  saving.value = true
  try {
    await crawlApi.assignExecutors(queue.value.siteId, executorIds.value)
    queueDialog.value = false; await load(); ElMessage.success('队列执行器已更新')
  } catch (error) { fail(error) } finally { saving.value = false }
}
async function manageExecutors() {
  try { proxies.value = (await settingApi.getCrawlerSettings()).data.proxies; executorManager.value = true }
  catch (error) { fail(error) }
}
watch(siteId, search)
watch(() => props.mode, () => { pageNumber.value = 1; void load() })
onMounted(() => { void load(); timer = setInterval(() => { if (!loading.value && !selectedPageIds.value.length) void load(true) }, 5000) })
onUnmounted(() => { requestId++; if (timer) clearInterval(timer) })
</script>

<template>
  <section class="workflow-panel surface-card crawler-controls" v-loading="loading" role="tabpanel">
    <header v-if="mode !== 'pages' && mode !== 'collected-pages'" class="workflow-heading">
      <div><p class="eyebrow">{{ mode === 'scans' ? 'DISCOVERY SOURCES' : 'SITE QUEUES' }}</p>
        <h2>{{ mode === 'scans' ? '扫描页' : '采集队列' }}</h2>
        <p>{{ mode === 'scans' ? '独立维护扫描入口，按各自周期发现新的图片页面。' : '一个执行器同时处理一个任务；网站专用执行器数决定该队列的并发上限。' }}</p>
      </div>
      <el-space wrap><el-button :icon="Refresh" circle aria-label="刷新列表" @click="load()" />
        <el-button v-if="mode === 'scans'" type="primary" :icon="Plus" :disabled="!sites.length" @click="editScan()">新增扫描页</el-button>
        <el-button v-if="mode === 'queues' && canManage" type="primary" @click="manageExecutors">执行器管理</el-button>
      </el-space>
    </header>
    <div v-if="!scopedSiteId" class="workflow-toolbar">
      <el-select v-model="siteId" clearable placeholder="全部网站" aria-label="筛选网站"><el-option v-for="site in sites" :key="site.id" :value="site.id!" :label="site.name" /></el-select>
      <template v-if="mode === 'pages' || mode === 'collected-pages'">
        <el-input v-model="query" clearable placeholder="搜索页面名称或 URL" :prefix-icon="Search" @keyup.enter="search" @clear="search"><template #append><el-button @click="search">搜索</el-button></template></el-input>
      </template>
    </div>
    <el-table v-if="mode === 'scans'" :data="visibleScans" empty-text="暂无扫描页，先添加一个扫描入口">
      <el-table-column label="操作" width="90" fixed="left"><template #default="{ row }"><el-button type="primary" plain size="small" :loading="action === `scan-${row.id}`" @click="scan(row)">扫描</el-button></template></el-table-column>
      <el-table-column label="扫描页" min-width="230"><template #default="{ row }"><strong>{{ row.name }}</strong><small>{{ siteName(row.siteId) }}</small><a :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a></template></el-table-column>
      <el-table-column label="自动扫描" width="110"><template #default="{ row }"><el-switch :model-value="row.autoScanEnabled" :loading="action === `auto-${row.id}`" :aria-label="`${row.name}自动扫描`" @change="toggleAuto(row, Boolean($event))" /></template></el-table-column>
      <el-table-column label="自动采集" width="110"><template #default="{ row }"><el-switch :model-value="row.autoCollectEnabled" :loading="action === `auto-collect-${row.id}`" :aria-label="`${row.name}自动采集`" @change="toggleAutoCollect(row, Boolean($event))" /></template></el-table-column>
      <el-table-column label="扫描周期" width="115"><template #default="{ row }">每 {{ row.scanIntervalMinutes }} 分钟</template></el-table-column>
      <el-table-column label="页数上限" prop="maxPages" width="100" />
      <el-table-column label="最近扫描" min-width="165"><template #default="{ row }">{{ time(row.lastScanAt) }}</template></el-table-column>
      <el-table-column label="管理" width="125"><template #default="{ row }"><el-button link type="primary" @click="editScan(row)">编辑</el-button><el-button link type="danger" @click="removeScan(row)">删除</el-button></template></el-table-column>
    </el-table>
    <div v-if="mode === 'pages' || mode === 'collected-pages'" class="page-bulk-bar"><span>已选择 {{ selectedPageIds.length }} 条</span><el-button type="danger" plain size="small" :disabled="!selectedPageIds.length || !!action" @click="removeSelectedPages">批量删除</el-button><el-button v-if="mode === 'pages'" type="primary" plain size="small" :icon="Download" :loading="action === 'collect-selected'" :disabled="!selectedCollectableIds.length || !!action" @click="collectSelectedPages">批量采集（{{ selectedCollectableIds.length }}）</el-button><el-button :icon="Refresh" circle aria-label="刷新列表" :disabled="!!action" @click="load()" /></div>
    <el-table v-if="mode === 'pages' || mode === 'collected-pages'" ref="pageTable" :data="pages" row-key="id" max-height="58vh" :empty-text="mode === 'pages' ? '尚无待采集页，运行扫描任务后会自动出现' : '尚无已采集页，点击待采集页中的采集按钮后会显示在这里'" @selection-change="onPageSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column v-if="mode === 'pages'" label="采集" width="90" fixed="left"><template #default="{ row }"><el-button v-if="row.collectable" type="primary" plain size="small" :loading="action === `collect-${row.id}`" @click="collect(row)">采集</el-button></template></el-table-column>
      <el-table-column label="采集页面" min-width="340"><template #default="{ row }"><a :href="row.url" :title="row.name || row.url" target="_blank" rel="noopener noreferrer" class="page-title">{{ row.name || row.url }}</a></template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'FAILED' ? 'danger' : row.status === 'COMPLETED' ? 'success' : 'info'">{{ status(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="发现时间" min-width="165"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="145" fixed="right"><template #default="{ row }"><el-button v-if="row.collectionJobId" link type="primary" @click="openTask(row.collectionJobId)">查看任务</el-button><el-button link type="danger" @click="removePage(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="mode === 'pages' || mode === 'collected-pages'" class="pages-pagination" v-model:current-page="pageNumber" v-model:page-size="pageSize" :page-sizes="[20, 50, 100, 200, 1000]" :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="search" />
    <div v-if="mode === 'queues'" class="queue-grid">
      <article v-for="item in visibleQueues" :key="item.siteId" class="queue-card">
        <header><h3>{{ item.name }}</h3><el-tag :type="item.dedicated ? 'primary' : 'info'">{{ item.dedicated ? '专用队列' : '共享执行器' }}</el-tag></header>
        <div class="queue-numbers"><div><b>{{ item.concurrency }}</b><small>并发上限</small></div><div><b>{{ item.running }}</b><small>运行中</small></div><div><b>{{ item.queued }}</b><small>排队中</small></div></div>
        <div class="executor-tags"><el-tag v-for="executor in item.executors" :key="executor.id" :type="executor.frozen || !executor.enabled ? 'warning' : 'success'">{{ executor.name }} · {{ executor.frozen ? '已冻结' : !executor.enabled ? '已停用' : executor.activeJobId ? '运行中' : '可用' }}</el-tag><small v-if="!item.executors.length">{{ item.concurrency ? '使用默认执行器（共享，并发 1）' : '暂无可用执行器' }}</small></div>
        <el-button v-if="canManage" plain type="primary" @click="configureQueue(item)">配置队列执行器</el-button>
        <div class="queue-jobs"><button v-for="job in jobs.filter(j => j.siteId === item.siteId && ['QUEUED','RUNNING'].includes(j.status))" :key="job.id" type="button" @click="emit('open-job', job)"><span>{{ status(job.status) }}</span><strong>{{ job.name }}</strong></button></div>
      </article>
      <el-empty v-if="!visibleQueues.length" description="请先添加采集网站" />
    </div>
    <el-dialog v-model="scanDialog" :title="form.id ? '编辑扫描页' : '新增扫描页'" width="min(620px, 94vw)" append-to-body class="crawler-controls">
      <el-form label-position="top"><el-form-item label="采集网站"><el-select v-model="form.siteId" :disabled="!!form.id || !!scopedSiteId"><el-option v-for="site in sites" :key="site.id" :value="site.id!" :label="site.name" /></el-select></el-form-item>
        <el-form-item label="扫描页名称"><el-input v-model="form.name" maxlength="100" placeholder="例如：最新发布、热门图片" /></el-form-item>
        <el-form-item label="扫描入口 URL"><el-input v-model="form.url" maxlength="2048" placeholder="https://example.com/latest/" /></el-form-item>
        <el-form-item label="列表页上限"><el-input-number v-model="form.maxPages" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="自动扫描"><el-switch v-model="form.autoScanEnabled" active-text="按周期扫描" inactive-text="仅手动扫描" /></el-form-item>
        <el-form-item label="自动采集"><el-switch v-model="form.autoCollectEnabled" active-text="扫描后自动创建采集任务" inactive-text="手动发起采集" /></el-form-item>
        <el-form-item v-if="form.autoCollectEnabled" label="自动采集任务优先级"><el-select v-model="form.autoCollectPriority" aria-label="自动采集任务优先级"><el-option v-for="option in autoCollectPriorityOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="扫描周期（分钟）"><el-input-number v-model="form.scanIntervalMinutes" :min="5" :max="10080" /></el-form-item>
        <el-alert type="info" :closable="false" show-icon title="使用网站当前生效的解析规则；开启自动采集后，扫描结束会为本次新发现的图片页自动创建采集任务。" />
      </el-form><template #footer><el-button @click="scanDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveScan">保存扫描页</el-button></template>
    </el-dialog>
    <el-dialog v-model="queueDialog" :title="`${queue?.name || ''} · 队列执行器`" width="min(620px, 94vw)" append-to-body class="crawler-controls">
      <p>选择多个执行器即可并发采集。已停用或冻结的执行器不接收新任务。</p>
      <el-select v-model="executorIds" multiple placeholder="选择该网站专用执行器" style="width:100%"><el-option v-for="executor in allExecutors" :key="executor.id" :value="executor.id" :label="`${executor.name}${executor.options.siteId && executor.options.siteId !== queue?.siteId ? `（当前属于${siteName(executor.options.siteId)}，选择将移动）` : ''}`" /></el-select>
      <p class="field-hint">不选择专用执行器时使用共享池；尚未配置任何执行器时使用默认执行器。运行中的执行器须等待任务完成后才能移动。</p>
      <template #footer><el-button @click="queueDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveQueue">保存队列配置</el-button></template>
    </el-dialog>
    <el-dialog v-model="executorManager" title="采集执行器管理" width="min(1120px, 96vw)" append-to-body destroy-on-close @closed="load()" class="crawler-controls"><CrawlerExecutorsSettingsPanel v-if="executorManager" :proxies="proxies" :scoped-site-id="scopedSiteId" /></el-dialog>
  </section>
</template>

<style scoped>
.page-title{font-weight:600}
.page-bulk-bar{display:flex;align-items:center;gap:12px;margin:0 0 12px;color:var(--text-secondary);font-size:13px}

.workflow-panel{padding:24px;min-width:0}.workflow-heading,.queue-card header{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.workflow-heading{margin-bottom:24px}.workflow-heading h2,.queue-card h3{margin:0}.workflow-heading p,.field-hint{color:var(--text-secondary);font-size:13px;line-height:1.7}.eyebrow{font-size:10px!important;letter-spacing:.16em;color:var(--accent)!important;margin:0 0 6px}.workflow-toolbar{display:flex;gap:12px;align-items:center;margin-bottom:18px}.workflow-toolbar>.el-select{width:200px;flex-shrink:0}.workflow-toolbar>.el-input{max-width:520px}.workflow-panel :deep(.cell)>small,.workflow-panel :deep(.cell)>a{display:block;margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.workflow-panel small{color:var(--text-secondary)}a{color:var(--accent);text-decoration:none}.el-pagination{margin:0 0 16px;padding:10px 12px;background:var(--bg-secondary);border-radius:12px;justify-content:flex-end}.queue-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:18px}.queue-card{border:1px solid var(--separator);border-radius:16px;padding:22px;background:var(--bg-secondary)}.queue-numbers{display:flex;gap:36px;margin:24px 0}.queue-numbers b{font-size:28px;font-variant-numeric:tabular-nums}.queue-numbers small{display:block;margin-top:5px}.executor-tags{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:18px;min-height:26px}.queue-jobs{display:grid;gap:6px;margin-top:16px;max-height:260px;overflow:auto}.queue-jobs button{display:flex;gap:12px;align-items:center;min-height:36px;border:0;background:var(--bg-card);color:var(--text-primary);padding:8px 10px;border-radius:8px;text-align:left;cursor:pointer}.queue-jobs strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.queue-jobs span{flex-shrink:0;color:var(--accent);font-size:12px}button:focus-visible,a:focus-visible{outline:2px solid var(--accent);outline-offset:3px}@media(max-width:760px){.workflow-panel{padding:16px}.workflow-heading{flex-direction:column}.workflow-toolbar{flex-wrap:wrap}.workflow-toolbar>.el-input{max-width:100%}.queue-grid{grid-template-columns:1fr}}
</style>

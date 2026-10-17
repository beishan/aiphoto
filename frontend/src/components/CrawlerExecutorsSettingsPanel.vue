<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { settingApi, type CrawlerExecutor, type CrawlerExecutorRequest, type CrawlerExecutorOptions, type CrawlerProxy, type MihomoProxyNode, type MihomoNodeGroup, type CrawlerExecutionLog } from '@/api/settingApi'
import { crawlApi } from '@/api/crawlApi'
import type { CrawlSite } from '@/types'
const props = defineProps<{ proxies: CrawlerProxy[]; scopedSiteId?: number | null }>()
const executors = ref<CrawlerExecutor[]>([])
const sites = ref<CrawlSite[]>([])
const siteFilter = ref<number | null>(null)
const visibleExecutors = computed(() => executors.value.filter(e => {
  const selectedSiteId = props.scopedSiteId ?? siteFilter.value
  return selectedSiteId == null || e.options.siteId === selectedSiteId
}))
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const editorTab = ref('basic')
const catalog = ref<Record<string, MihomoProxyNode>>({})
const savedGroups = ref<MihomoNodeGroup[]>([])
const loadingNodes = ref(false)
const options = (): CrawlerExecutorOptions => ({ description: '', siteId: null, frozen: false, selectionStrategy: 'ORDERED', ordinaryProxies: [], defaultProxyCooldownSeconds: 300, failureThreshold: 2, manualNode: null, failover: true, rotationSeconds: 300, rotationPages: 50, rotationImages: 100, rotationTasks: 1, nodeGroupId: null })
const defaults = (): CrawlerExecutorRequest => ({ name: '', enabled: true, proxyMode: 'DEFAULT', proxyId: null, proxyGroup: '', nodes: [], switchMode: 'MANUAL', rotateAfterRequests: 100, cooldownSeconds: 300, options: options() })
const editor = reactive(defaults())
const current = computed(() => executors.value.find(e => e.id === editingId.value))
const groups = computed(() => Object.entries(catalog.value).filter(([, n]) => n.type === 'Selector').map(([name]) => name))
const nodeOptions = computed(() => (catalog.value[editor.proxyGroup]?.all || []).filter(name => {
  const node = catalog.value[name]
  return node && !node.all && !['DIRECT', 'REJECT', 'REJECT-DROP', 'PASS', 'COMPATIBLE'].includes(node.type.toUpperCase())
}))
const availableProxies = computed(() => props.proxies.filter(p => p.proxyType === (editor.proxyMode === 'MIHOMO' ? 'MIHOMO' : 'HTTP')))
const selectedProxyIds = computed({ get: () => editor.options.ordinaryProxies.map(p => p.proxyId), set: (ids: number[]) => {
  const previous = new Map(editor.options.ordinaryProxies.map(p => [p.proxyId, p]))
  editor.options.ordinaryProxies = ids.map(id => previous.get(id) || { proxyId: id, cooldownSeconds: null })
}})
const modeLabel = (mode: string) => ({ DEFAULT: '默认代理池', DIRECT: '直连', SELECTED: '指定普通代理', MIHOMO: 'Mihomo 节点组' }[mode] || mode)
const isFrozen = (until: string | null) => !!until && Date.parse(until) > Date.now()
const error = (e: any) => ElMessage.error(e?.response?.data?.message || '执行器操作失败')
let timer: ReturnType<typeof setInterval> | undefined
let loading = false
async function load() {
  if (loading) return
  loading = true
  try { executors.value = (await settingApi.getCrawlerExecutors()).data } catch (e) { error(e) } finally { loading = false }
}
async function loadNodes() {
  catalog.value = {}; savedGroups.value = []
  if (editor.proxyId == null || editor.proxyMode !== 'MIHOMO') return
  loadingNodes.value = true
  try {
    const [nodes, groups] = await Promise.all([settingApi.getMihomoNodes(editor.proxyId), settingApi.getMihomoGroups(editor.proxyId)])
    catalog.value = nodes.data.proxies; savedGroups.value = groups.data
  } catch (e) { error(e) } finally { loadingNodes.value = false }
}
function changeMode() { editor.proxyId = null; editor.proxyGroup = ''; editor.nodes = []; editor.options.nodeGroupId = null; editor.options.manualNode = null; catalog.value = {} }
function selectGroup() {
  const group = savedGroups.value.find(g => g.id === editor.options.nodeGroupId)
  if (group) { editor.nodes = [...group.nodes]; editor.proxyGroup = group.controlGroup; editor.options.manualNode = group.nodes[0] || null }
}
async function edit(executor?: CrawlerExecutor) {
  editingId.value = executor?.id ?? null; editorTab.value = 'basic'
  Object.assign(editor, defaults(), executor ? { ...executor, nodes: [...executor.nodes], options: { ...options(), ...executor.options, ordinaryProxies: executor.options.ordinaryProxies.map(p => ({ ...p })) } } : {})
  if (!executor) { editor.name = `执行器 ${executors.value.length + 1}`; editor.options.siteId = props.scopedSiteId ?? siteFilter.value }
  showEditor.value = true; await loadNodes()
}
async function save() {
  if (!editor.name.trim()) { ElMessage.warning('请填写执行器名称'); return }
  saving.value = true
  try { await settingApi.saveCrawlerExecutor(editingId.value, { ...editor }); showEditor.value = false; await load(); ElMessage.success('执行器已保存，策略在下一次请求生效') }
  catch (e) { error(e) } finally { saving.value = false }
}
async function freeze(executor: CrawlerExecutor) {
  try { await settingApi.freezeCrawlerExecutor(executor.id, !executor.options.frozen); await load() } catch (e) { error(e) }
}
async function unbind() {
  if (editingId.value == null) return
  try { await ElMessageBox.confirm('解除后改为默认普通代理池，保留系统代理和备用节点组。', '解除 Mihomo 引用') } catch { return }
  try { await settingApi.unbindCrawlerExecutor(editingId.value); await load(); await edit(executors.value.find(e => e.id === editingId.value)); ElMessage.success('已解除引用') } catch (e) { error(e) }
}
async function remove(executor: CrawlerExecutor) {
  try { await ElMessageBox.confirm(`删除执行器“${executor.name}”？`, '删除执行器', { type: 'warning' }) } catch { return }
  try { await settingApi.deleteCrawlerExecutor(executor.id); await load() } catch (e) { error(e) }
}
function moveProxy(index: number, direction: number) {
  const list = [...editor.options.ordinaryProxies]; const target = index + direction
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]; editor.options.ordinaryProxies = list
}
function moveNode(index: number, direction: number) {
  const list = [...editor.nodes]; const target = index + direction
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]; editor.nodes = list
}
async function migrateGroup() {
  if (editor.proxyId == null || !editor.proxyGroup || !editor.nodes.length) return
  const proxy = props.proxies.find(p => p.id === editor.proxyId)
  if (!proxy) return
  try {
    const { data } = await settingApi.saveMihomoGroup(proxy.id, null, { name: `${editor.name}备用组`, controlGroup: editor.proxyGroup, host: proxy.host, port: proxy.port, nodes: [...editor.nodes] })
    await loadNodes(); editor.options.nodeGroupId = data.id; ElMessage.success('节点组已建立，请保存执行器完成引用')
  } catch (e) { error(e) }
}
async function testNode(node: string) {
  if (editor.proxyId == null) return
  try { const { data } = await settingApi.testMihomoNode(editor.proxyId, node); ElMessage.success(`${node} 延迟 ${data.delay} ms`) } catch (e) { error(e) }
}
const drawer = ref(false)
const logExecutor = ref<CrawlerExecutor | null>(null)
const logs = ref<CrawlerExecutionLog[]>([])
const logPage = ref(1)
const logTotal = ref(0)
const failedOnly = ref(false)
const logLoading = ref(false)
let logRequest = 0
async function loadLogs() {
  if (!logExecutor.value) return
  const sequence = ++logRequest; logLoading.value = true
  try {
    const { data } = await settingApi.getCrawlerExecutorLogs(logExecutor.value.id, logPage.value - 1, failedOnly.value)
    if (sequence === logRequest && drawer.value) { logs.value = data.content; logTotal.value = data.totalElements }
  } catch (e) { if (sequence === logRequest) error(e) } finally { if (sequence === logRequest) logLoading.value = false }
}
function openLogs(executor: CrawlerExecutor) { logExecutor.value = executor; logs.value = []; logPage.value = 1; logTotal.value = 0; failedOnly.value = false; drawer.value = true; loadLogs() }
onMounted(async () => {
  await load(); try { sites.value = (await crawlApi.sites()).data } catch (e) { error(e) }
  timer = setInterval(load, 10000)
})
onUnmounted(() => { if (timer) clearInterval(timer); logRequest++ })
</script>
<template>
  <section class="executor-card">
    <div class="heading"><div><h3>执行器配置</h3><p>配置代理、轮换与故障策略；每个执行器同时采集一个任务，策略更新在下一次请求生效。</p></div><el-button :disabled="executors.length >= 16" type="primary" @click="edit()">新增执行器</el-button></div>
    <div class="toolbar"><el-select v-if="!scopedSiteId" v-model="siteFilter" clearable placeholder="全部站点 / 全局执行器"><el-option v-for="site in sites" :key="site.id" :value="site.id!" :label="site.name" /></el-select><el-button @click="load">刷新配置</el-button></div>
    <el-table :data="visibleExecutors" empty-text="暂无执行器">
      <el-table-column label="执行器" min-width="170"><template #default="{ row }"><strong>{{ row.name }}</strong><small>{{ row.options.description || '暂无描述' }}</small><small>{{ sites.find(s => s.id === row.options.siteId)?.name || '全部站点' }}</small></template></el-table-column>
      <el-table-column label="代理方式" min-width="145"><template #default="{ row }">{{ modeLabel(row.proxyMode) }}</template></el-table-column>
      <el-table-column label="状态" min-width="135"><template #default="{ row }"><el-tag :type="row.options.frozen ? 'warning' : row.enabled ? 'success' : 'info'">{{ row.options.frozen ? '人工冻结' : row.enabled ? '已启用' : '已停用' }}</el-tag><small>{{ row.activeJobId ? `任务 #${row.activeJobId}` : row.waitingJobId ? `等待恢复 #${row.waitingJobId}` : '空闲' }}</small></template></el-table-column>
      <el-table-column label="操作" min-width="295"><template #default="{ row }"><el-button size="small" @click="openLogs(row)">执行详情</el-button><el-button size="small" @click="edit(row)">配置</el-button><el-button size="small" @click="freeze(row)">{{ row.options.frozen ? '解除冻结' : '冻结' }}</el-button><el-button text type="danger" :disabled="!!row.activeJobId" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="showEditor" append-to-body destroy-on-close :title="editingId ? '配置执行器' : '新增执行器'" width="min(820px, 96vw)" top="6vh" class="executor-editor-dialog">
      <el-tabs v-model="editorTab">
        <el-tab-pane name="basic" label="基础配置">
          <el-form label-position="top"><div class="grid"><el-form-item label="执行器名称"><el-input v-model="editor.name" maxlength="100" /></el-form-item><el-form-item label="启用"><el-switch v-model="editor.enabled" /><small>停用后不接收新任务，已运行任务继续完成。</small></el-form-item></div>
            <el-form-item label="描述"><el-input v-model="editor.options.description" type="textarea" maxlength="500" /></el-form-item>
            <el-form-item label="采集站点范围"><el-select v-model="editor.options.siteId" :disabled="!!scopedSiteId" clearable placeholder="全部站点"><el-option v-for="site in sites" :key="site.id" :value="site.id!" :label="site.name" /></el-select><small>站点存在专用执行器时，任务仅使用专用执行器；全部停用或冻结后保持排队。</small></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane name="proxy" label="代理方式">
          <el-form label-position="top">
            <el-form-item label="代理方式"><el-select v-model="editor.proxyMode" @change="changeMode"><el-option label="默认普通代理池" value="DEFAULT" /><el-option label="直连" value="DIRECT" /><el-option label="普通代理 · 指定代理列表" value="SELECTED" /><el-option label="Mihomo · 系统代理与备用节点组" value="MIHOMO" /></el-select></el-form-item>
            <template v-if="editor.proxyMode !== 'DIRECT'">
              <el-form-item label="代理 / 节点选用顺序"><el-select v-model="editor.options.selectionStrategy"><el-option label="按顺序" value="ORDERED" /><el-option label="随机（节点避免立即重复）" value="RANDOM" /></el-select></el-form-item>
            </template>
            <template v-if="editor.proxyMode === 'DEFAULT' || editor.proxyMode === 'SELECTED'">
              <el-form-item label="默认普通代理冷却（秒）"><el-input-number v-model="editor.options.defaultProxyCooldownSeconds" :min="10" :max="604800" /></el-form-item>
              <template v-if="editor.proxyMode === 'SELECTED'">
                <el-form-item label="选择普通代理"><el-select v-model="selectedProxyIds" multiple filterable><el-option v-for="proxy in availableProxies" :key="proxy.id" :value="proxy.id" :label="proxy.name" :disabled="!proxy.enabled" /></el-select></el-form-item>
                <div v-for="(proxy, index) in editor.options.ordinaryProxies" :key="proxy.proxyId" class="proxy-row"><span>{{ props.proxies.find(p => p.id === proxy.proxyId)?.name || '已删除代理' }}</span><el-input-number v-model="proxy.cooldownSeconds" :min="10" :max="604800" placeholder="使用默认冷却" /><el-button text :disabled="index === 0" @click="moveProxy(index, -1)">上移</el-button><el-button text :disabled="index === editor.options.ordinaryProxies.length - 1" @click="moveProxy(index, 1)">下移</el-button></div>
              </template>
              <el-form-item label="连续代理异常阈值"><el-input-number v-model="editor.options.failureThreshold" :min="1" :max="10" /></el-form-item>
              <el-alert type="info" :closable="false" title="请求重试耗尽记一次异常，达到阈值后冷却；成功清零。默认代理池遵循全局直连回退，指定代理列表不会直连。" />
            </template>
            <template v-if="editor.proxyMode === 'MIHOMO'">
              <el-form-item label="Mihomo 系统代理"><el-select v-model="editor.proxyId" filterable @change="editor.options.nodeGroupId = null; editor.options.manualNode = null; editor.nodes = []; editor.proxyGroup = ''; loadNodes()"><el-option v-for="proxy in availableProxies" :key="proxy.id" :value="proxy.id" :label="proxy.name" :disabled="!proxy.enabled" /></el-select></el-form-item>
              <el-form-item label="已保存的备用节点组"><el-select v-model="editor.options.nodeGroupId" clearable @change="selectGroup"><el-option v-for="group in savedGroups" :key="group.id" :value="group.id" :label="`${group.name} · ${group.nodes.length} 个节点 · ${group.host}:${group.port}`" /></el-select><small>在系统代理配置 → 编辑 → 节点组中管理可复用节点组。</small></el-form-item>
              <el-button :loading="loadingNodes" @click="loadNodes">刷新控制 API / 节点组</el-button>
              <template v-if="!editor.options.nodeGroupId">
                <el-alert type="info" :closable="false" title="兼容直接配置；可保存为命名节点组后引用。" />
                <el-form-item label="专用 Selector 受控组"><el-select v-model="editor.proxyGroup" @change="editor.nodes = []; editor.options.manualNode = null"><el-option v-for="group in groups" :key="group" :value="group" :label="group" /></el-select></el-form-item>
                <el-form-item label="备用具体节点"><el-select v-model="editor.nodes" multiple filterable><el-option v-for="node in nodeOptions" :key="node" :value="node" :label="node" /></el-select></el-form-item>
                <div v-for="(node, index) in editor.nodes" :key="node" class="proxy-row"><span>{{ node }}</span><el-button text @click="testNode(node)">检测</el-button><el-button text :disabled="index === 0" @click="moveNode(index, -1)">上移</el-button><el-button text :disabled="index === editor.nodes.length - 1" @click="moveNode(index, 1)">下移</el-button></div>
                <el-button :disabled="!editor.nodes.length" @click="migrateGroup">保存为命名备用节点组</el-button>
              </template>
              <div v-else class="group-preview"><strong>{{ editor.proxyGroup }}</strong><div><el-tag v-for="node in editor.nodes" :key="node">{{ node }}</el-tag></div></div>
              <el-form-item label="节点切换方式"><el-select v-model="editor.switchMode"><el-option label="手动指定节点" value="MANUAL" /><el-option label="仅故障自动切换" value="FAILOVER" /><el-option label="按运行时间轮换" value="TIME" /><el-option label="按成功页面数轮换" value="PAGE" /><el-option label="按成功图片数轮换" value="IMAGE" /><el-option label="按成功采集任务数轮换" value="TASK" /><el-option label="按成功请求数轮换" value="REQUESTS" /></el-select></el-form-item>
              <el-form-item v-if="editor.switchMode === 'MANUAL'" label="手动指定节点"><el-select v-model="editor.options.manualNode" filterable><el-option v-for="node in editor.nodes" :key="node" :value="node" :label="node" /></el-select><small>固定使用此节点，冻结时等待恢复，不自动切换。</small></el-form-item>
              <div class="grid">
                <el-form-item v-if="editor.switchMode === 'TIME'" label="轮换间隔（秒）"><el-input-number v-model="editor.options.rotationSeconds" :min="30" :max="604800" /><small>仅累计采集请求、限速与重试等待时间，不累计空闲和冻结时间。</small></el-form-item>
                <el-form-item v-if="editor.switchMode === 'PAGE'" label="成功页面数"><el-input-number v-model="editor.options.rotationPages" :min="1" :max="1000000" /></el-form-item>
                <el-form-item v-if="editor.switchMode === 'IMAGE'" label="成功图片数"><el-input-number v-model="editor.options.rotationImages" :min="1" :max="1000000" /></el-form-item>
                <el-form-item v-if="editor.switchMode === 'TASK'" label="成功采集任务数"><el-input-number v-model="editor.options.rotationTasks" :min="1" :max="1000000" /></el-form-item>
                <el-form-item v-if="editor.switchMode === 'REQUESTS'" label="成功请求数"><el-input-number v-model="editor.rotateAfterRequests" :min="1" :max="1000000" /></el-form-item>
                <el-form-item v-if="editor.switchMode !== 'MANUAL'" label="节点故障自动切换"><el-switch :model-value="editor.switchMode === 'FAILOVER' || editor.options.failover" :disabled="editor.switchMode === 'FAILOVER'" @change="editor.options.failover = Boolean($event)" /></el-form-item>
                <el-form-item label="连续节点异常阈值"><el-input-number v-model="editor.options.failureThreshold" :min="1" :max="10" /></el-form-item>
                <el-form-item label="异常节点冻结（秒）"><el-input-number v-model="editor.cooldownSeconds" :min="10" :max="604800" /></el-form-item>
              </div>
              <el-alert type="info" :closable="false" title="同一请求先在当前节点完成重试（至少 2 次），耗尽后记 1 次异常；成功清零该节点连续异常。控制 API 失败不会冻结节点。全部冻结后任务等待，冷却到期自动恢复。" />
            </template>
          </el-form>
        </el-tab-pane>
        <el-tab-pane name="nodes" label="节点状态">
          <div class="runtime"><strong>最近确认节点：{{ current?.currentNode || '尚未确认' }}</strong><small>{{ Math.floor((current?.activeMillis || 0) / 1000) }} 秒 · {{ current?.completedRequests || 0 }} 次请求 · {{ current?.completedPages || 0 }} 个页面 · {{ current?.completedImages || 0 }} 张图片 · {{ current?.completedTasks || 0 }} 个任务</small><el-alert v-if="current?.lastError" :title="current.lastError" type="warning" :closable="false" /><el-button @click="load">刷新状态</el-button></div>
          <el-table :data="current?.proxyStates || []"><el-table-column prop="name" label="代理 / 节点" min-width="160" /><el-table-column label="状态" width="95"><template #default="{ row }"><el-tag :type="isFrozen(row.coolingUntil) ? 'danger' : row.available ? 'success' : 'info'">{{ isFrozen(row.coolingUntil) ? '已冻结' : row.available ? '可用' : '停用' }}</el-tag></template></el-table-column><el-table-column prop="failures" label="连续异常" width="95" /><el-table-column label="冻结详情" min-width="190"><template #default="{ row }"><template v-if="isFrozen(row.coolingUntil)">{{ row.reason }}<small>恢复：{{ new Date(row.coolingUntil).toLocaleString() }}</small></template><template v-else>—</template></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane name="history" label="切换记录">
          <el-button @click="load">刷新记录</el-button><p>最近 50 条切换及节点冻结记录，按时间倒序显示。</p>
          <div class="event-list"><article v-for="(event, index) in current?.events || []" :key="index"><time>{{ new Date(event.time).toLocaleString() }}</time><el-tag :type="event.success ? 'success' : 'danger'">{{ event.success ? '成功' : '失败 / 冻结' }}</el-tag><strong>{{ event.from || '未确认' }} → {{ event.to || '未确认' }}</strong><p>{{ event.reason }}</p></article></div>
          <el-empty v-if="!current?.events.length" description="暂无切换记录" :image-size="64" />
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button v-if="editingId && current?.proxyMode === 'MIHOMO'" type="warning" plain :disabled="!!current.activeJobId" @click="unbind">解除 Mihomo 引用</el-button><el-button @click="showEditor = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存执行器配置</el-button></template>
    </el-dialog>
    <el-drawer v-model="drawer" append-to-body :title="`${logExecutor?.name || '执行器'} · 执行详情`" size="min(1080px, 96vw)">
      <div class="toolbar"><label>只看错误 <el-switch v-model="failedOnly" @change="logPage = 1; loadLogs()" /></label><el-button :loading="logLoading" @click="loadLogs">刷新详情</el-button></div>
      <p>每条记录保存请求发生时的代理与节点；点击行首箭头查看完整 URL。</p>
      <el-table v-loading="logLoading" :data="logs" row-key="id"><el-table-column type="expand"><template #default="{ row }"><div class="log-details"><p>任务 #{{ row.jobId }} · {{ row.jobName }} · {{ row.siteName }} · {{ row.executorName }}</p><p>{{ row.url }}</p><p>{{ row.description }}</p></div></template></el-table-column><el-table-column label="时间" width="165"><template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template></el-table-column><el-table-column label="站点 / 任务" min-width="160"><template #default="{ row }">{{ row.siteName || '—' }}<small>{{ row.jobName || `任务 #${row.jobId}` }}</small></template></el-table-column><el-table-column label="代理 / 节点" min-width="180"><template #default="{ row }">{{ row.proxyName || '尚未发出请求' }}<small>{{ row.proxyNode || '—' }}</small></template></el-table-column><el-table-column label="结果" min-width="180"><template #default="{ row }"><el-tag :type="row.failed ? 'danger' : 'success'">{{ row.failed ? '错误' : '成功' }}</el-tag><small>{{ row.description }}</small></template></el-table-column><el-table-column prop="attempts" label="尝试次数" width="95" /><el-table-column label="耗时" width="110"><template #default="{ row }">{{ row.durationMillis }} ms</template></el-table-column></el-table>
      <el-pagination v-model:current-page="logPage" :total="logTotal" :page-size="20" layout="total, prev, pager, next" @current-change="loadLogs" />
    </el-drawer>
  </section>
</template>
<style scoped>
.heading,.toolbar,.proxy-row{flex-wrap:wrap}
.proxy-row > span{overflow-wrap:anywhere}
.proxy-row :deep(.el-button + .el-button){margin-left:0}
:global(.executor-editor-dialog .el-tabs__header){margin-bottom:20px}


.executor-card{display:grid;gap:16px;padding:18px;border:1px solid var(--separator);border-radius:16px;background:var(--bg-card)}.heading,.toolbar,.proxy-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.toolbar{justify-content:flex-start}.toolbar>.el-select{max-width:360px}h3,p{margin:0}p,small{display:block;margin-top:6px;font-size:12px;line-height:1.7;color:var(--text-secondary)}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}.proxy-row{padding:8px 0}.proxy-row>span{flex:1;min-width:0}.runtime,.group-preview{display:grid;gap:10px;padding:16px;border-radius:12px;background:var(--bg-secondary);margin:12px 0}.runtime>.el-button{justify-self:start}.group-preview .el-tag{margin:4px}.event-list{max-height:420px;overflow:auto}.event-list>article{display:grid;gap:8px;padding:16px 0;border-bottom:1px solid var(--separator)}.event-list .el-tag{justify-self:start}.log-details{padding:16px;overflow-wrap:anywhere}.el-select{width:100%}.el-pagination{margin-top:18px}@media(max-width:640px){.grid{grid-template-columns:1fr}.heading,.toolbar,.proxy-row{flex-wrap:wrap}}
@media(max-width:560px){.grid{grid-template-columns:minmax(0,1fr)}.proxy-row > span{flex-basis:100%}.proxy-row :deep(.el-input-number){max-width:100%}}
</style>

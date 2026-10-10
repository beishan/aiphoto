<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { settingApi, type CrawlerExecutor, type CrawlerExecutorRequest, type CrawlerProxy, type MihomoProxyNode } from '@/api/settingApi'
const props = defineProps<{ proxies: CrawlerProxy[] }>()
const executors = ref<CrawlerExecutor[]>([])
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const catalog = ref<Record<string, MihomoProxyNode>>({})
const loadingNodes = ref(false)
const defaults = (): CrawlerExecutorRequest => ({ name: '', enabled: true, proxyMode: 'DEFAULT', proxyId: null, proxyGroup: '', nodes: [], switchMode: 'MANUAL', rotateAfterRequests: 100, cooldownSeconds: 300 })
const editor = reactive(defaults())
const groups = computed(() => Object.entries(catalog.value).filter(([, node]) => node.type === 'Selector').map(([name]) => name))
const nodeOptions = computed(() => (catalog.value[editor.proxyGroup]?.all || []).filter(name => {
  const node = catalog.value[name]
  return node && !node.all && !['DIRECT', 'REJECT', 'REJECT-DROP', 'PASS', 'COMPATIBLE'].includes(node.type.toUpperCase())
}))
const availableProxies = computed(() => props.proxies.filter(p => p.enabled && p.proxyType === (editor.proxyMode === 'MIHOMO' ? 'MIHOMO' : 'HTTP')))
let timer: ReturnType<typeof setInterval> | undefined
async function load() {
  try { executors.value = (await settingApi.getCrawlerExecutors()).data }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || '加载执行器失败') }
}
async function loadNodes() {
  catalog.value = {}
  if (editor.proxyId == null || editor.proxyMode !== 'MIHOMO') return
  loadingNodes.value = true
  try { catalog.value = (await settingApi.getMihomoNodes(editor.proxyId)).data.proxies; ElMessage.success('控制 API 连接成功') }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || '连接 Mihomo 控制 API 失败') }
  finally { loadingNodes.value = false }
}
function changeMode() { editor.proxyId = null; editor.proxyGroup = ''; editor.nodes = []; catalog.value = {} }
async function edit(executor?: CrawlerExecutor) {
  editingId.value = executor?.id ?? null
  Object.assign(editor, defaults(), executor ? { ...executor, nodes: [...executor.nodes] } : {})
  showEditor.value = true
  await loadNodes()
}
async function save() {
  saving.value = true
  try { await settingApi.saveCrawlerExecutor(editingId.value, { ...editor }); showEditor.value = false; await load(); ElMessage.success('执行器已保存') }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || '保存执行器失败') }
  finally { saving.value = false }
}
async function remove(executor: CrawlerExecutor) {
  try { await ElMessageBox.confirm(`删除执行器“${executor.name}”？`, '删除执行器', { type: 'warning' }) }
  catch { return }
  try { await settingApi.deleteCrawlerExecutor(executor.id); await load() }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || '删除失败') }
}
async function testNode(node: string) {
  if (editor.proxyId == null) return
  try { const { data } = await settingApi.testMihomoNode(editor.proxyId, node); ElMessage.success(`${node} 延迟 ${data.delay} ms`) }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || '节点检测失败') }
}
onMounted(() => { load(); timer = setInterval(load, 10000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <section class="executor-card">
    <div class="heading"><div><h3>执行器配置</h3><p>每个执行器同时处理一个采集任务。新增执行器可提高任务并发数，全部停用后暂停派发。</p></div><el-button @click="edit()">添加执行器</el-button></div>
    <el-alert type="info" :closable="false" title="默认代理池只使用普通代理。Mihomo 需选择专用受控组；固定节点模式使用备用列表的第一个节点。" />
    <article v-for="executor in executors" :key="executor.id" class="executor-row">
      <div><strong>{{ executor.name }}</strong><p>{{ executor.proxyMode }} · {{ executor.enabled ? '已启用' : '已停用' }} · {{ executor.activeJobId ? `任务 #${executor.activeJobId}` : '空闲' }}</p>
        <p v-if="executor.currentNode">当前节点：{{ executor.currentNode }} · 成功请求 {{ executor.completedRequests }}</p>
        <p v-if="executor.lastError" class="error">{{ executor.lastError }}</p>
        <p v-for="(until, node) in executor.cooldowns" :key="node">{{ node }} · 冷却至 {{ new Date(until).toLocaleString() }}</p>
      </div>
      <div><el-button :disabled="!!executor.activeJobId" @click="edit(executor)">编辑</el-button><el-button type="danger" text :disabled="!!executor.activeJobId" @click="remove(executor)">删除</el-button></div>
    </article>
    <el-empty v-if="!executors.length" description="未配置执行器时使用一个默认执行器" :image-size="64" />
    <el-dialog v-model="showEditor" title="执行器配置" width="min(640px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="editor.name" maxlength="100" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editor.enabled" /></el-form-item>
        <el-form-item label="代理模式"><el-select v-model="editor.proxyMode" @change="changeMode"><el-option label="默认普通代理池" value="DEFAULT" /><el-option label="直连" value="DIRECT" /><el-option label="指定普通代理" value="SELECTED" /><el-option label="Mihomo" value="MIHOMO" /></el-select></el-form-item>
        <template v-if="editor.proxyMode === 'SELECTED' || editor.proxyMode === 'MIHOMO'">
          <el-form-item label="系统代理"><el-select v-model="editor.proxyId" @change="editor.nodes = []; editor.proxyGroup = ''; loadNodes()"><el-option v-for="proxy in availableProxies" :key="proxy.id" :value="proxy.id" :label="proxy.name" /></el-select></el-form-item>
        </template>
        <template v-if="editor.proxyMode === 'MIHOMO'">
          <el-button :loading="loadingNodes" @click="loadNodes">测试控制 API / 刷新节点</el-button>
          <el-form-item label="专用 Selector 受控组"><el-select v-model="editor.proxyGroup" @change="editor.nodes = []"><el-option v-for="group in groups" :key="group" :value="group" :label="group" /></el-select></el-form-item>
          <el-form-item label="备用节点（按选择顺序轮换）"><el-select v-model="editor.nodes" multiple filterable><el-option v-for="node in nodeOptions" :key="node" :value="node" :label="node" /></el-select></el-form-item>
          <div class="node-tests"><el-button v-for="node in editor.nodes" :key="node" size="small" @click="testNode(node)">检测 {{ node }}</el-button></div>
          <el-form-item label="切换方式"><el-select v-model="editor.switchMode"><el-option label="固定第一个节点" value="MANUAL" /><el-option label="仅故障切换" value="FAILOVER" /><el-option label="按成功请求次数轮换（含故障切换）" value="REQUESTS" /></el-select></el-form-item>
          <el-form-item v-if="editor.switchMode === 'REQUESTS'" label="轮换请求数"><el-input-number v-model="editor.rotateAfterRequests" :min="1" :max="100000" /></el-form-item>
          <el-form-item label="故障节点冷却（秒）"><el-input-number v-model="editor.cooldownSeconds" :min="1" :max="86400" /></el-form-item>
          <el-alert type="info" :closable="false" title="节点延迟检测不切换节点，也不能保证目标网站可访问。控制 API 失败不会触发节点故障切换。" />
        </template>
      </el-form>
      <template #footer><el-button @click="showEditor = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.executor-card{display:grid;gap:16px;padding:18px;border:1px solid var(--separator);border-radius:16px;background:var(--bg-card)}.heading,.executor-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.executor-row{padding:12px;border-radius:12px;background:var(--bg-secondary)}h3,p{margin:0}p{margin-top:6px;font-size:12px;color:var(--text-secondary)}.error{color:var(--el-color-danger)}.node-tests{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px}.el-select{width:100%}
</style>

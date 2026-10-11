<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { settingApi, type CrawlerProxy, type CrawlerProxyRequest } from '@/api/settingApi'

import { parseProxyAddress, formatProxyAddress } from '@/utils/proxyAddress'

import CrawlerExecutorsSettingsPanel from './CrawlerExecutorsSettingsPanel.vue'
import SystemMihomoNodesPanel from './SystemMihomoNodesPanel.vue'

const props = defineProps<{ proxiesOnly?: boolean }>()
const loading = ref(false)
const saving = ref(false)
const proxies = ref<CrawlerProxy[]>([])
const editingId = ref<number | null>(null)
const showEditor = ref(false)
const draggedId = ref<number | null>(null)
const mergedProxy = computed(() => {
  const saved = proxies.value.find(proxy => proxy.id === editingId.value)
  return editor.proxyType === 'MIHOMO' && saved?.proxyType === 'MIHOMO' ? saved : null
})
const settings = reactive({
  directFallback: true,
  connectTimeoutSeconds: 10,
  requestTimeoutSeconds: 30,
  minRequestIntervalMillis: 1000,
  maxRetries: 2,
  retryBaseDelayMillis: 1000,
})
const editor = reactive<CrawlerProxyRequest>({
  name: '', host: '', port: 7890, username: '', password: '',
  clearPassword: false, enabled: true, proxyType: 'HTTP', controllerUrl: '', secret: '', clearSecret: false,
})
const editingHasPassword = ref(false)

const proxyAddress = ref('')
const editingHasSecret = ref(false)
const testingDraft = ref(false)
const draftResult = ref<{ controllerOk: boolean | null; proxyOk: boolean; message: string } | null>(null)
const testResults = reactive<Record<number, { controllerOk: boolean | null; proxyOk: boolean; message: string; checkedAt: string }>>({})
const enabledCount = computed(() => proxies.value.filter(proxy => proxy.enabled).length)
watch([proxyAddress, editor], () => { draftResult.value = null }, { deep: true })
function draftPayload() {
  const parsed = parseProxyAddress(proxyAddress.value)
  return { ...editor, host: parsed.host, port: parsed.port,
    username: parsed.username || editor.username, password: parsed.password || editor.password }
}
async function testDraft() {
  let payload: CrawlerProxyRequest
  try { payload = draftPayload() } catch (error: any) { ElMessage.warning(error.message); return }
  testingDraft.value = true
  draftResult.value = null
  const snapshot = JSON.stringify(payload)
  const targetId = editingId.value
  try {
    const { data } = await settingApi.previewCrawlerProxy(payload, targetId)
    if (showEditor.value && targetId === editingId.value && snapshot === JSON.stringify(draftPayload())) draftResult.value = data
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || error.message || '连接测试失败') }
  finally { testingDraft.value = false }
}

async function load() {
  loading.value = true
  try {
    const { data } = await settingApi.getCrawlerSettings()
    Object.assign(settings, data)
    proxies.value = data.proxies
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '加载爬虫设置失败')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    const { data } = await settingApi.updateCrawlerSettings({ ...settings })
    Object.assign(settings, data)
    proxies.value = data.proxies
    ElMessage.success('爬虫网络设置已保存')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function addProxy() {
  editingId.value = null
  proxyAddress.value = 'http://127.0.0.1:7890'
  editingHasSecret.value = false
  draftResult.value = null
  editingHasPassword.value = false
  Object.assign(editor, {
    name: '', host: '', port: 7890, username: '', password: '',
    clearPassword: false, enabled: true, proxyType: 'HTTP', controllerUrl: '', secret: '', clearSecret: false,
  })
  showEditor.value = true
}

function editProxy(proxy: CrawlerProxy) {
  editingId.value = proxy.id
  proxyAddress.value = formatProxyAddress(proxy.host, proxy.port)
  editingHasSecret.value = proxy.secretConfigured
  draftResult.value = null
  editingHasPassword.value = proxy.hasPassword
  Object.assign(editor, {
    name: proxy.name, host: proxy.host, port: proxy.port,
    username: proxy.username || '', password: '', clearPassword: false,
    enabled: proxy.enabled, proxyType: proxy.proxyType,
    controllerUrl: proxy.controllerUrl || '', secret: '', clearSecret: false,
  })
  showEditor.value = true
}

async function saveProxy() {
  if (!editor.name.trim()) {
    ElMessage.warning('请填写代理名称')
    return
  }
  let payload: CrawlerProxyRequest
  try { payload = draftPayload() } catch (error: any) { ElMessage.warning(error.message); return }
  saving.value = true
  try {
    if (editingId.value == null) await settingApi.createCrawlerProxy(payload)
    else await settingApi.updateCrawlerProxy(editingId.value, payload)
    if (editingId.value != null) delete testResults[editingId.value]
    showEditor.value = false
    await load()
    ElMessage.success(editingId.value == null ? '代理已添加' : '代理已更新')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存代理失败')
  } finally {
    saving.value = false
  }
}

async function toggleProxy(proxy: CrawlerProxy) {
  try {
    const { data } = await settingApi.updateCrawlerProxy(proxy.id, {
      name: proxy.name, host: proxy.host, port: proxy.port,
      username: proxy.username || '', password: '', clearPassword: false,
      enabled: proxy.enabled, proxyType: proxy.proxyType,
    controllerUrl: proxy.controllerUrl || '', secret: '', clearSecret: false,
    })
    Object.assign(proxy, data)
  } catch (error: any) {
    proxy.enabled = !proxy.enabled
    ElMessage.error(error?.response?.data?.message || '更新代理状态失败')
  }
}

const testingId = ref<number | null>(null)
async function testProxy(proxy: CrawlerProxy) {
  if (testingId.value != null) return
  testingId.value = proxy.id
  try {
    const { data } = await settingApi.testCrawlerProxy(proxy.id)
    testResults[proxy.id] = { ...data, checkedAt: new Date().toLocaleString() }
    const text = `${data.controllerOk == null ? '' : `控制 API ${data.controllerOk ? '可用' : '失败'}；`}${data.message}`
    if (data.proxyOk && data.controllerOk !== false) ElMessage.success(text)
    else ElMessage.warning(text)
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || '连接测试失败') }
  finally { testingId.value = null }
}

async function removeProxy(proxy: CrawlerProxy) {
  try {
    await ElMessageBox.confirm(`确定删除代理“${proxy.name}”吗？`, '删除代理', { type: 'warning' })
  } catch { return }
  try {
    await settingApi.deleteCrawlerProxy(proxy.id)
    delete testResults[proxy.id]
    await load()
    ElMessage.success('代理已删除')
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || '删除代理失败') }
}

function startDrag(id: number) {
  draggedId.value = id
}

async function dropBefore(targetId: number) {
  const sourceId = draggedId.value
  draggedId.value = null
  if (sourceId == null || sourceId === targetId) return
  const next = [...proxies.value]
  const sourceIndex = next.findIndex(proxy => proxy.id === sourceId)
  const targetIndex = next.findIndex(proxy => proxy.id === targetId)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = next.splice(sourceIndex, 1)
  next.splice(targetIndex, 0, moved)
  proxies.value = next
  await persistOrder()
}

async function moveProxy(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= proxies.value.length) return
  const next = [...proxies.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  proxies.value = next
  await persistOrder()
}

async function persistOrder() {
  try {
    const { data } = await settingApi.reorderCrawlerProxies(proxies.value.map(proxy => proxy.id))
    proxies.value = data
    ElMessage.success('代理优先级已更新')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '代理排序保存失败')
    await load()
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="crawler-settings">
    <div class="section-heading">
      <div><h2>{{ props.proxiesOnly ? "系统代理配置" : "爬虫设置" }}</h2><p>{{ props.proxiesOnly ? "管理普通 HTTP 代理与 Mihomo 控制连接，供爬虫执行器使用。" : "控制采集请求策略与执行器。" }}</p></div>
      <el-button v-if="!props.proxiesOnly" type="primary" :loading="saving" @click="saveSettings">保存网络设置</el-button>
    </div>

    <section v-if="!props.proxiesOnly" class="settings-card">
      <h3>请求策略</h3>
      <div class="setting-grid">
        <label><span>连接超时</span><el-input-number v-model="settings.connectTimeoutSeconds" :min="1" :max="60" /><small>秒</small></label>
        <label><span>单次请求超时</span><el-input-number v-model="settings.requestTimeoutSeconds" :min="1" :max="300" /><small>秒</small></label>
        <label><span>同域名最小间隔</span><el-input-number v-model="settings.minRequestIntervalMillis" :min="0" :max="60000" :step="100" /><small>毫秒</small></label>
        <label><span>失败自动重试</span><el-input-number v-model="settings.maxRetries" :min="0" :max="5" /><small>次</small></label>
        <label><span>重试基础等待</span><el-input-number v-model="settings.retryBaseDelayMillis" :min="0" :max="60000" :step="100" /><small>毫秒，指数退避</small></label>
      </div>
      <div class="fallback-row"><div><strong>代理失败后允许直连</strong><small>按优先级尝试全部启用代理后，再使用 NAS 的直接网络连接。</small></div><el-switch v-model="settings.directFallback" /></div>
      <el-alert v-if="!settings.directFallback && !proxies.some(proxy => proxy.enabled && proxy.proxyType === 'HTTP')" type="warning" :closable="false" title="当前没有启用代理且禁止直连，所有采集请求都会失败。" />
    </section>

    <CrawlerExecutorsSettingsPanel v-if="!props.proxiesOnly" :proxies="proxies" />

    <section v-if="props.proxiesOnly" class="settings-card">
      <div class="proxy-heading"><div><h3>代理列表</h3><p>从上到下依次尝试。拖动手柄或使用上下方向键调整优先级。</p></div><el-button @click="addProxy">添加代理</el-button></div>
      <div class="proxy-list">
        <article v-for="(proxy, index) in proxies" :key="proxy.id" class="proxy-row" @dragover.prevent @drop="dropBefore(proxy.id)">
          <el-button class="ep-control drag-handle" draggable="true" :aria-label="`调整 ${proxy.name} 的优先级`" @keydown.up.prevent="moveProxy(index, -1)" @keydown.down.prevent="moveProxy(index, 1)" title="拖动或使用上下方向键调整优先级" @dragstart="startDrag(proxy.id)" @dragend="draggedId = null">⋮⋮</el-button>
          <span class="priority">{{ index + 1 }}</span>
          <div class="proxy-copy"><strong>{{ proxy.name }}</strong><small>{{ proxy.proxyType === "MIHOMO" ? "Mihomo" : "HTTP" }} · {{ proxy.host }}:{{ proxy.port }}<template v-if="proxy.username"> · {{ proxy.username }}</template><template v-if="proxy.hasPassword"> · 已保存密码</template></small><small v-if="proxy.proxyType === 'MIHOMO'">控制 API：{{ proxy.controllerUrl }} · {{ proxy.secretConfigured ? '已保存密钥' : '未配置密钥' }}</small><small v-if="testResults[proxy.id]">{{ testResults[proxy.id].checkedAt }} · {{ testResults[proxy.id].controllerOk == null ? '' : `控制 API ${testResults[proxy.id].controllerOk ? '可用' : '失败'}；` }}{{ testResults[proxy.id].message }}</small></div>
          <el-switch v-model="proxy.enabled" @change="toggleProxy(proxy)" />
          <div class="proxy-actions"><el-button text :disabled="testingId != null && testingId !== proxy.id" :loading="testingId === proxy.id" @click="testProxy(proxy)">测试连接</el-button><el-button text @click="editProxy(proxy)">编辑</el-button><el-button text type="danger" @click="removeProxy(proxy)">删除</el-button></div>
        </article>
        <el-empty v-if="!proxies.length" description="暂无系统代理，请添加普通 HTTP 或 Mihomo 代理" :image-size="64" />
      </div>
      <small class="proxy-summary">共 {{ proxies.length }} 个代理，{{ enabledCount }} 个启用；Mihomo 通过执行器绑定使用。</small>
    </section>

    <el-dialog v-model="showEditor" append-to-body :title="mergedProxy ? `${mergedProxy.name} · 代理配置` : editingId == null ? '添加代理' : '编辑代理'" :width="mergedProxy ? 'min(960px, 96vw)' : 'min(520px, 92vw)'" top="6vh" class="proxy-config-dialog" destroy-on-close>
      <component :is="mergedProxy ? SystemMihomoNodesPanel : 'div'" v-if="showEditor" :proxy="mergedProxy || undefined">
      <el-form label-position="top" class="proxy-editor-form">
        <el-form-item label="代理类型"><el-select v-model="editor.proxyType"><el-option label="普通 HTTP 代理" value="HTTP" /><el-option label="Mihomo 代理" value="MIHOMO" /></el-select></el-form-item>
        <el-form-item label="名称"><el-input v-model="editor.name" placeholder="例如：家庭代理 1" /></el-form-item>
        <el-form-item label="代理地址"><el-input v-model="proxyAddress" placeholder="http://192.168.1.10:7890" autocomplete="off" /><small>实际 HTTP 代理出口；支持粘贴含用户名、密码的地址，保存后凭据加密存储。</small></el-form-item>
        <el-form-item label="用户名（可选）"><el-input v-model="editor.username" autocomplete="off" /></el-form-item>
        <el-form-item label="密码（可选）"><el-input v-model="editor.password" type="password" show-password autocomplete="new-password" :placeholder="editingHasPassword ? '留空则保持现有密码' : '未配置密码'" /></el-form-item>
        <el-checkbox v-if="editingHasPassword" v-model="editor.clearPassword">清除已保存密码</el-checkbox>
        <template v-if="editor.proxyType === 'MIHOMO'">
          <el-form-item label="控制 API 地址"><el-input v-model="editor.controllerUrl" placeholder="http://192.168.1.10:9090" /></el-form-item>
          <el-form-item label="控制 API 密钥"><el-input v-model="editor.secret" type="password" show-password autocomplete="new-password" :placeholder="editingHasSecret ? '已保存密钥，留空保持' : '未配置密钥'" /></el-form-item>
          <el-checkbox v-if="editingHasSecret" v-model="editor.clearSecret">清除控制 API 密钥</el-checkbox>
          <el-alert type="info" :closable="false" title="代理地址填写 HTTP 出口；控制 API 使用独立地址。建议为每个执行器设置专用 Selector 与入口。" />
          <el-alert v-if="!mergedProxy" type="info" :closable="false" title="首次保存 Mihomo 代理后，再次点击编辑即可配置节点与备用组。" />
        </template>
        <el-button :loading="testingDraft" @click="testDraft">{{ editor.proxyType === 'MIHOMO' ? '测试控制 API 与代理出口' : '测试代理出口' }}</el-button>
        <el-alert v-if="draftResult" class="draft-test-result" :type="draftResult.proxyOk && draftResult.controllerOk !== false ? 'success' : 'warning'" :closable="false" :title="`${draftResult.controllerOk == null ? '' : `控制 API ${draftResult.controllerOk ? '可用' : '失败'}；`}${draftResult.message}`" />
        <div class="editor-enabled"><span>保存后立即启用</span><el-switch v-model="editor.enabled" /></div>
      </el-form>
      <div class="proxy-basic-actions">
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" :disabled="testingDraft" :loading="saving" @click="saveProxy">保存配置</el-button>
      </div>
      </component>
    </el-dialog>
  </div>
</template>

<style scoped>
.proxy-editor-form{width:100%;min-width:0;margin:0}
.proxy-editor-form :deep(.el-form-item__content){gap:6px;min-width:0}
.proxy-editor-form :deep(.el-form-item__content > small){width:100%;line-height:1.6;color:var(--text-secondary)}
.proxy-editor-form :deep(.el-input),.proxy-editor-form :deep(.el-select){width:100%}
.proxy-editor-form > .el-alert{margin:12px 0}
.proxy-editor-form > .el-checkbox{display:flex;margin:0 0 12px;height:auto;min-height:32px}
.proxy-basic-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:10px;margin-top:20px}
.proxy-basic-actions :deep(.el-button + .el-button){margin-left:0}
:global(.proxy-config-dialog){display:flex;flex-direction:column;max-height:88vh;margin-bottom:0;padding:0}
:global(.proxy-config-dialog .el-dialog__header){flex:0 0 auto;padding-right:60px}
:global(.proxy-config-dialog .el-dialog__body){min-height:0;overflow-y:auto;overscroll-behavior:contain}
@media(max-width:560px){:global(.proxy-config-dialog .el-dialog__body){padding:16px}.proxy-basic-actions{gap:8px}}

.proxy-summary{color:var(--text-secondary)}.draft-test-result{margin-top:12px}.proxy-copy small{overflow-wrap:anywhere}

.crawler-settings{display:grid;gap:18px}.section-heading,.proxy-heading,.fallback-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.section-heading h2,.settings-card h3{margin:0;color:var(--text-primary)}.section-heading p,.proxy-heading p{margin:5px 0 0;color:var(--text-secondary);font-size:12px}.settings-card{display:grid;gap:16px;padding:18px;border:1px solid var(--separator);border-radius:16px;background:var(--bg-card)}.setting-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px}.setting-grid label{display:grid;grid-template-columns:1fr auto;align-items:center;gap:8px;padding:12px;border-radius:12px;background:var(--bg-secondary)}.setting-grid label>span{grid-column:1/-1;color:var(--text-primary);font-size:12px}.setting-grid small,.fallback-row small,.proxy-copy small{color:var(--text-tertiary);font-size:10px}.fallback-row>div,.proxy-copy{display:grid;gap:4px}.fallback-row strong,.proxy-copy strong{color:var(--text-primary);font-size:12px}.proxy-list{display:grid;gap:8px}.proxy-row{display:grid;grid-template-columns:28px 28px minmax(180px,1fr) auto auto;align-items:center;gap:9px;padding:11px;border:1px solid var(--separator);border-radius:12px;background:var(--bg-secondary)}.drag-handle{border:0;background:none;color:var(--text-tertiary);font-size:18px;cursor:grab}.drag-handle:active{cursor:grabbing}.priority{display:grid;width:24px;height:24px;place-items:center;border-radius:50%;background:var(--bg-tertiary);color:var(--text-secondary);font-size:10px}.proxy-actions{display:flex;flex-wrap:wrap}.editor-grid{display:grid;grid-template-columns:minmax(0,1fr) 150px;gap:12px}.editor-enabled{display:flex;align-items:center;justify-content:space-between;margin-top:16px;color:var(--text-primary);font-size:12px}@media(max-width:800px){.setting-grid{grid-template-columns:1fr}.proxy-row{grid-template-columns:28px 28px minmax(0,1fr) auto}.proxy-actions{grid-column:2/-1}.editor-grid{grid-template-columns:1fr}}
</style>

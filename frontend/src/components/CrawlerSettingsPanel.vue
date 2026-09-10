<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { settingApi, type CrawlerProxy, type CrawlerProxyRequest } from '@/api/settingApi'

const loading = ref(false)
const saving = ref(false)
const proxies = ref<CrawlerProxy[]>([])
const editingId = ref<number | null>(null)
const showEditor = ref(false)
const draggedId = ref<number | null>(null)
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
  clearPassword: false, enabled: true,
})
const editingHasPassword = ref(false)

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
  editingHasPassword.value = false
  Object.assign(editor, {
    name: '', host: '', port: 7890, username: '', password: '',
    clearPassword: false, enabled: true,
  })
  showEditor.value = true
}

function editProxy(proxy: CrawlerProxy) {
  editingId.value = proxy.id
  editingHasPassword.value = proxy.hasPassword
  Object.assign(editor, {
    name: proxy.name, host: proxy.host, port: proxy.port,
    username: proxy.username || '', password: '', clearPassword: false,
    enabled: proxy.enabled,
  })
  showEditor.value = true
}

async function saveProxy() {
  if (!editor.name.trim() || !editor.host.trim()) {
    ElMessage.warning('请填写代理名称和主机')
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) await settingApi.createCrawlerProxy({ ...editor })
    else await settingApi.updateCrawlerProxy(editingId.value, { ...editor })
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
      enabled: proxy.enabled,
    })
    Object.assign(proxy, data)
  } catch (error: any) {
    proxy.enabled = !proxy.enabled
    ElMessage.error(error?.response?.data?.message || '更新代理状态失败')
  }
}

async function removeProxy(proxy: CrawlerProxy) {
  try {
    await ElMessageBox.confirm(`确定删除代理“${proxy.name}”吗？`, '删除代理', { type: 'warning' })
  } catch { return }
  await settingApi.deleteCrawlerProxy(proxy.id)
  await load()
  ElMessage.success('代理已删除')
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
      <div><h2>爬虫设置</h2><p>控制网页采集的连接策略、请求频率和代理优先级。</p></div>
      <el-button type="primary" :loading="saving" @click="saveSettings">保存网络设置</el-button>
    </div>

    <section class="settings-card">
      <h3>请求策略</h3>
      <div class="setting-grid">
        <label><span>连接超时</span><el-input-number v-model="settings.connectTimeoutSeconds" :min="1" :max="60" /><small>秒</small></label>
        <label><span>单次请求超时</span><el-input-number v-model="settings.requestTimeoutSeconds" :min="1" :max="300" /><small>秒</small></label>
        <label><span>同域名最小间隔</span><el-input-number v-model="settings.minRequestIntervalMillis" :min="0" :max="60000" :step="100" /><small>毫秒</small></label>
        <label><span>失败自动重试</span><el-input-number v-model="settings.maxRetries" :min="0" :max="5" /><small>次</small></label>
        <label><span>重试基础等待</span><el-input-number v-model="settings.retryBaseDelayMillis" :min="0" :max="60000" :step="100" /><small>毫秒，指数退避</small></label>
      </div>
      <div class="fallback-row"><div><strong>代理失败后允许直连</strong><small>按优先级尝试全部启用代理后，再使用 NAS 的直接网络连接。</small></div><el-switch v-model="settings.directFallback" /></div>
      <el-alert v-if="!settings.directFallback && !proxies.some(proxy => proxy.enabled)" type="warning" :closable="false" title="当前没有启用代理且禁止直连，所有采集请求都会失败。" />
    </section>

    <section class="settings-card">
      <div class="proxy-heading"><div><h3>代理列表</h3><p>从上到下依次尝试。拖动左侧手柄即可调整优先级。</p></div><el-button @click="addProxy">添加代理</el-button></div>
      <div class="proxy-list">
        <article v-for="(proxy, index) in proxies" :key="proxy.id" class="proxy-row" @dragover.prevent @drop="dropBefore(proxy.id)">
          <button class="drag-handle" draggable="true" title="拖动调整优先级" @dragstart="startDrag(proxy.id)" @dragend="draggedId = null">⋮⋮</button>
          <span class="priority">{{ index + 1 }}</span>
          <div class="proxy-copy"><strong>{{ proxy.name }}</strong><small>HTTP · {{ proxy.host }}:{{ proxy.port }}<template v-if="proxy.username"> · {{ proxy.username }}</template><template v-if="proxy.hasPassword"> · 已保存密码</template></small></div>
          <el-switch v-model="proxy.enabled" @change="toggleProxy(proxy)" />
          <div class="proxy-actions"><el-button text :disabled="index === 0" @click="moveProxy(index, -1)">上移</el-button><el-button text :disabled="index === proxies.length - 1" @click="moveProxy(index, 1)">下移</el-button><el-button text @click="editProxy(proxy)">编辑</el-button><el-button text type="danger" @click="removeProxy(proxy)">删除</el-button></div>
        </article>
        <el-empty v-if="!proxies.length" description="暂无代理，将使用直连模式" :image-size="64" />
      </div>
    </section>

    <el-dialog v-model="showEditor" :title="editingId == null ? '添加代理' : '编辑代理'" width="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="editor.name" placeholder="例如：家庭代理 1" /></el-form-item>
        <div class="editor-grid"><el-form-item label="主机"><el-input v-model="editor.host" placeholder="proxy.example.com 或 192.168.1.10" /></el-form-item><el-form-item label="端口"><el-input-number v-model="editor.port" :min="1" :max="65535" /></el-form-item></div>
        <el-form-item label="用户名（可选）"><el-input v-model="editor.username" autocomplete="off" /></el-form-item>
        <el-form-item label="密码（可选）"><el-input v-model="editor.password" type="password" show-password autocomplete="new-password" :placeholder="editingHasPassword ? '留空则保持现有密码' : '未配置密码'" /></el-form-item>
        <el-checkbox v-if="editingHasPassword" v-model="editor.clearPassword">清除已保存密码</el-checkbox>
        <div class="editor-enabled"><span>保存后立即启用</span><el-switch v-model="editor.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="showEditor = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProxy">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.crawler-settings{display:grid;gap:18px}.section-heading,.proxy-heading,.fallback-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.section-heading h2,.settings-card h3{margin:0;color:var(--text-primary)}.section-heading p,.proxy-heading p{margin:5px 0 0;color:var(--text-secondary);font-size:12px}.settings-card{display:grid;gap:16px;padding:18px;border:1px solid var(--separator);border-radius:16px;background:var(--bg-card)}.setting-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px}.setting-grid label{display:grid;grid-template-columns:1fr auto;align-items:center;gap:8px;padding:12px;border-radius:12px;background:var(--bg-secondary)}.setting-grid label>span{grid-column:1/-1;color:var(--text-primary);font-size:12px}.setting-grid small,.fallback-row small,.proxy-copy small{color:var(--text-tertiary);font-size:10px}.fallback-row>div,.proxy-copy{display:grid;gap:4px}.fallback-row strong,.proxy-copy strong{color:var(--text-primary);font-size:12px}.proxy-list{display:grid;gap:8px}.proxy-row{display:grid;grid-template-columns:28px 28px minmax(180px,1fr) auto auto;align-items:center;gap:9px;padding:11px;border:1px solid var(--separator);border-radius:12px;background:var(--bg-secondary)}.drag-handle{border:0;background:none;color:var(--text-tertiary);font-size:18px;cursor:grab}.drag-handle:active{cursor:grabbing}.priority{display:grid;width:24px;height:24px;place-items:center;border-radius:50%;background:var(--bg-tertiary);color:var(--text-secondary);font-size:10px}.proxy-actions{display:flex;flex-wrap:wrap}.editor-grid{display:grid;grid-template-columns:minmax(0,1fr) 150px;gap:12px}.editor-enabled{display:flex;align-items:center;justify-content:space-between;margin-top:16px;color:var(--text-primary);font-size:12px}@media(max-width:800px){.setting-grid{grid-template-columns:1fr}.proxy-row{grid-template-columns:28px 28px minmax(0,1fr) auto}.proxy-actions{grid-column:2/-1}.editor-grid{grid-template-columns:1fr}}
</style>

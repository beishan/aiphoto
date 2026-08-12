<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingStore } from '@/stores/settingStore'
import { settingApi, type ModelFile, type ModelName, type ModelStatus, type ModelCatalogItem, type OnlineModel, type DownloadTask, type DownloadStatus } from '@/api/settingApi'
import { taskApi, type AiTask } from '@/api/taskApi'
import { userApi } from '@/api/userApi'
import { tagApi } from '@/api/tagApi'
import { folderApi } from '@/api/folderApi'
import { useMessage } from 'naive-ui'
import type { User, Tag, ScanFolder } from '@/types'
import PersonalSettingsPanel from '@/components/PersonalSettingsPanel.vue'
import ThemeSettingsPanel from '@/components/ThemeSettingsPanel.vue'

const router = useRouter()
const settingStore = useSettingStore()
const message = useMessage()

// ===== Settings menu structure =====
const navItems = [
  { key: 'profile', label: '个人设置', icon: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z' },
  { key: 'general', label: '主题风格', icon: 'M12 3a9 9 0 100 18c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.37-.6-.37-.99 0-.83.67-1.5 1.5-1.5H16a5 5 0 005-5c0-4.42-4.03-8-9-8zM6.5 12A1.5 1.5 0 118 10.5 1.5 1.5 0 016.5 12zm2-4A1.5 1.5 0 1110 6.5 1.5 1.5 0 018.5 8zm4-1A1.5 1.5 0 1114 5.5 1.5 1.5 0 0112.5 7zm4 2a1.5 1.5 0 110-3 1.5 1.5 0 010 3z' },
  { key: 'users', label: '用户管理', icon: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z' },
  { key: 'folders', label: '扫描文件夹', icon: 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z' },
  { key: 'models', label: '模型管理', icon: 'M4 6c0-1.1 3.58-2 8-2s8 .9 8 2-3.58 2-8 2-8-.9-8-2zm0 4v4c0 1.1 3.58 2 8 2s8-.9 8-2v-4c-1.72 1.21-5.03 1.75-8 1.75S5.72 11.21 4 10zm0 8v-2c1.72 1.21 5.03 1.75 8 1.75s6.28-.54 8-1.75v2c0 1.1-3.58 2-8 2s-8-.9-8-2z' },
  { key: 'tags', label: '标签管理', icon: 'M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42z' },
  { key: 'photos', label: '照片与视频', icon: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z' },
  { key: 'timeline', label: '时间线设置', icon: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z' },
  { key: 'tasks', label: '任务与日志', icon: 'M19 3H5c-1.11 0-2 .89-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.11-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z' },
  { key: 'storage', label: '存储管理', icon: 'M2 20h20v-4H2v4zm2-3h2v2H4v-2zM2 4v4h20V4H2zm4 3H4V5h2v2zm-4 7h20v-4H2v4zm2-3h2v2H4v-2z' },
  { key: 'system', label: '系统信息', icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z' },
]

const sessionUser = (() => {
  try { return JSON.parse(sessionStorage.getItem('user') || 'null') as User | null }
  catch { return null }
})()
const isCurrentUserAdmin = sessionUser?.role === 'ADMIN'

const navGroups = computed(() => [
  { label: '个人', keys: ['profile'] },
  { label: '外观与偏好', keys: ['general'] },
  { label: '媒体库', keys: ['folders', 'tags', 'photos', 'timeline'] },
  { label: '智能服务', keys: ['models', 'tasks'] },
  { label: '管理', keys: isCurrentUserAdmin ? ['users', 'storage'] : ['storage'] },
  { label: '系统', keys: ['system'] },
].map(group => ({
  label: group.label,
  items: group.keys.map(key => navItems.find(item => item.key === key)!),
})))

const activeSection = ref('profile')

// ===== General Settings =====
const namingRule = ref('original')
const faceThreshold = ref(50)
const searchThreshold = ref(80)

onMounted(async () => {
  // Load settings
  if (!settingStore.loaded) {
    await settingStore.fetchSettings()
  }
  namingRule.value = settingStore.getSetting('photo_naming_rule', 'original')
  faceThreshold.value = Number(settingStore.getSetting('ai_face_cluster_threshold', '50'))
  searchThreshold.value = Number(settingStore.getSetting('ai_search_similarity_threshold', '80'))

  // Load users, tags, folders
  await Promise.all([
    ...(isCurrentUserAdmin ? [loadUsers()] : []),
    loadTags(),
    loadFolders(),
    loadModels(),
    loadTasks(),
    loadStorageInfo(),
    loadSystemInfo(),
  ])
})

const namingOptions = [
  { value: 'original', label: '保留原名', example: 'IMG_20240101.jpg' },
  { value: 'date_original', label: '日期 + 原名', example: '2024-01-01_IMG_20240101.jpg' },
  { value: 'date_time', label: '日期时间', example: '20240101_123456.jpg' },
  { value: 'uuid', label: 'UUID', example: 'a1b2c3d4.jpg' },
  { value: 'timestamp', label: '时间戳', example: '1704067200000.jpg' },
]

// ===== User Management =====
const users = ref<User[]>([])
const showUserDialog = ref(false)
const newUser = ref({ username: '', password: '', role: 'USER', nickname: '' })

async function loadUsers() {
  try {
    const { data } = await userApi.list()
    users.value = data
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载用户列表失败')
  }
}

async function createUser() {
  try {
    await userApi.create(newUser.value)
    message.success('用户创建成功')
    showUserDialog.value = false
    newUser.value = { username: '', password: '', role: 'USER', nickname: '' }
    await loadUsers()
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  }
}

async function deleteUser(id: number, username: string) {
  if (!confirm(`确定要删除用户 "${username}" 吗？此操作不可恢复。`)) return
  try {
    await userApi.delete(id)
    message.success('用户已删除')
    await loadUsers()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

async function resetPassword(id: number) {
  const password = prompt('请输入新密码')
  if (!password) return
  try {
    await userApi.resetPassword(id, password)
    message.success('密码已重置')
  } catch (e: any) {
    message.error(e.response?.data?.message || '重置失败')
  }
}

async function toggleEnabled(id: number) {
  try {
    await userApi.toggleEnabled(id)
    await loadUsers()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

// ===== Tag Management =====
const tags = ref<Tag[]>([])
const showTagDialog = ref(false)
const newTag = ref({ name: '', color: '#0a84ff', description: '' })
const editingTag = ref<Tag | null>(null)
const showEditTagDialog = ref(false)
const editTagData = ref({ name: '', color: '#0a84ff', description: '' })
const tagSearchQuery = ref('')

const presetColors = ['#ff3b30', '#ff9500', '#ffcc00', '#34c759', '#00d4ff', '#007aff', '#af52de', '#ff2d55', '#8e8e93']

async function loadTags() {
  try {
    const { data } = await tagApi.list()
    tags.value = data
  } catch { /* ignore */ }
}

async function createTag() {
  try {
    await tagApi.create(newTag.value)
    message.success('标签创建成功')
    showTagDialog.value = false
    newTag.value = { name: '', color: '#0a84ff', description: '' }
    await loadTags()
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  }
}

async function deleteTag(id: number, name: string, count: number) {
  if (!confirm(`确定要删除标签 "${name}" 吗？\n关联照片数：${count}\n删除后仅解除关联，不删除照片。`)) return
  try {
    await tagApi.delete(id)
    message.success('标签已删除')
    await loadTags()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

async function updateTagColor(tag: Tag, color: string) {
  try {
    await tagApi.update(tag.id, { color })
    tag.color = color
    message.success('颜色已更新')
  } catch (e: any) {
    message.error(e.response?.data?.message || '更新失败')
  }
}

function openEditTagDialog(tag: Tag) {
  editingTag.value = tag
  editTagData.value = { name: tag.name, color: tag.color || '#0a84ff', description: tag.description || '' }
  showEditTagDialog.value = true
}

async function saveEditedTag() {
  if (!editingTag.value) return
  if (!editTagData.value.name.trim()) {
    message.warning('标签名称不能为空')
    return
  }
  try {
    await tagApi.update(editingTag.value.id, {
      name: editTagData.value.name,
      color: editTagData.value.color,
      description: editTagData.value.description,
    })
    message.success('标签已更新')
    showEditTagDialog.value = false
    await loadTags()
  } catch (e: any) {
    message.error(e.response?.data?.message || '更新失败')
  }
}

// ===== Folder Management =====
const folders = ref<ScanFolder[]>([])
const showFolderDialog = ref(false)
const newFolder = ref({ name: '', path: '', storageMode: 'COPY' })

async function loadFolders() {
  try {
    const { data } = await folderApi.list()
    folders.value = data
  } catch { /* ignore */ }
}

async function createFolder() {
  if (!newFolder.value.name || !newFolder.value.path) {
    message.warning('请填写文件夹名称和路径')
    return
  }
  try {
    await folderApi.create(newFolder.value)
    message.success('文件夹已添加')
    showFolderDialog.value = false
    newFolder.value = { name: '', path: '', storageMode: 'COPY' }
    await loadFolders()
  } catch (e: any) {
    message.error(e.response?.data?.message || '添加失败')
  }
}

async function toggleFolderEnabled(id: number) {
  try {
    await folderApi.toggleEnabled(id)
    await loadFolders()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function toggleFolderHidden(id: number) {
  try {
    await folderApi.toggleHidden(id)
    await loadFolders()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function scanFolder(id: number) {
  try {
    await folderApi.scan(id)
    message.success('扫描已开始')
    await loadFolders()
  } catch (e: any) {
    message.error(e.response?.data?.message || '扫描失败')
  }
}

async function scanAll() {
  try {
    await folderApi.scanAll()
    message.success('全部扫描已开始')
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function deleteFolder(id: number, name: string) {
  if (!confirm(`确定要删除扫描文件夹 "${name}" 吗？`)) return
  try {
    await folderApi.delete(id)
    message.success('文件夹已删除')
    await loadFolders()
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

// ===== Model Management =====
const modelRoot = ref('/models')
const models = ref<ModelStatus[]>([])
const modelLoading = ref(false)
const modelViewMode = ref<'local' | 'online' | 'downloads'>('local')
const modelCatalog = ref<ModelCatalogItem[]>([])
const onlineModels = ref<OnlineModel[]>([])
const downloadTasks = ref<DownloadTask[]>([])
const downloadPollTimer = ref<number | null>(null)

const modelLabels: Record<ModelName, string> = {
  clip: 'Chinese-CLIP 语义模型',
  insightface: 'InsightFace 人脸模型',
  yolo: 'YOLO 目标检测模型',
  blip: 'BLIP-2 图片描述模型',
}

// Extended model type labels (for catalog)
const modelTypeLabels: Record<string, string> = {
  face_detection: '人脸检测模型',
  face_recognition: '人脸识别模型',
  image_classification: '图片分类模型',
  image_caption: '图片描述模型',
  ocr: 'OCR 文字识别模型',
  image_vector: '图片向量模型',
  similar_image: '相似图片识别模型',
  duplicate_detection: '重复照片检测模型',
  content_safety: '内容安全识别模型',
  object_detection: '目标检测模型',
}

// Map catalog type keys to AI service model names
const typeToAiName: Record<string, ModelName | null> = {
  image_vector: 'clip',
  face_detection: 'insightface',
  face_recognition: 'insightface',
  object_detection: 'yolo',
  image_classification: 'yolo',
  image_caption: 'blip',
  ocr: null,
  similar_image: null,
  duplicate_detection: null,
  content_safety: null,
}

async function loadModels() {
  modelLoading.value = true
  try {
    const [statusRes, catalogRes, onlineRes, downloadsRes] = await Promise.all([
      settingApi.getModels(),
      settingApi.getModelCatalog(),
      settingApi.getOnlineModels(),
      settingApi.getAllDownloads().catch(() => ({ data: {} })),
    ])
    modelRoot.value = statusRes.data.root
    models.value = statusRes.data.models
    modelCatalog.value = catalogRes.data
    onlineModels.value = onlineRes.data
    downloadTasks.value = Object.values(downloadsRes.data)
    // Start polling if there are active downloads
    if (downloadTasks.value.some(t => t.status === 'DOWNLOADING' || t.status === 'PENDING' || t.status === 'INSTALLING')) {
      startDownloadPolling()
    }
  } catch { /* ignore */ }
  finally { modelLoading.value = false }
}

function startDownloadPolling() {
  if (downloadPollTimer.value) return
  downloadPollTimer.value = window.setInterval(async () => {
    try {
      const { data } = await settingApi.getAllDownloads()
      downloadTasks.value = Object.values(data)
      // Stop polling if no active downloads
      if (!downloadTasks.value.some(t => t.status === 'DOWNLOADING' || t.status === 'PENDING' || t.status === 'INSTALLING')) {
        stopDownloadPolling()
      }
    } catch { /* ignore */ }
  }, 2000)
}

function stopDownloadPolling() {
  if (downloadPollTimer.value) {
    clearInterval(downloadPollTimer.value)
    downloadPollTimer.value = null
  }
}

async function saveModel(model: ModelStatus) {
  modelLoading.value = true
  try {
    const { data } = await settingApi.configureModel(model.name, model.path, model.enabled)
    Object.assign(model, data)
    data.loaded ? message.success(`${modelLabels[model.name]} 已加载`) : message.warning(data.error || '配置已保存')
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  } finally { modelLoading.value = false }
}

async function reloadModel(model: ModelStatus) {
  modelLoading.value = true
  try {
    const { data } = await settingApi.reloadModel(model.name)
    Object.assign(model, data)
    data.loaded ? message.success('模型重新加载成功') : message.error(data.error || '加载失败')
  } finally { modelLoading.value = false }
}

// Online model download
async function startDownload(model: OnlineModel) {
  try {
    await settingApi.startDownload(model.id)
    message.success(`开始下载: ${model.name}`)
    await refreshDownloads()
    startDownloadPolling()
  } catch (e: any) {
    message.error(e.response?.data?.message || '下载失败')
  }
}

async function pauseDownload(taskId: string) {
  try { await settingApi.pauseDownload(taskId); await refreshDownloads() }
  catch { message.error('操作失败') }
}

async function resumeDownload(taskId: string) {
  try { await settingApi.resumeDownload(taskId); await refreshDownloads(); startDownloadPolling() }
  catch { message.error('操作失败') }
}

async function cancelDownload(taskId: string) {
  if (!confirm('确定要取消此下载吗？')) return
  try { await settingApi.cancelDownload(taskId); await refreshDownloads() }
  catch { message.error('操作失败') }
}

async function retryDownload(taskId: string) {
  try { await settingApi.retryDownload(taskId); await refreshDownloads(); startDownloadPolling() }
  catch { message.error('操作失败') }
}

async function setCurrentModel(taskId: string) {
  try {
    await settingApi.setCurrentModel(taskId)
    message.success('已设为当前模型')
    await refreshDownloads()
    await loadModels()
  } catch { message.error('操作失败') }
}

async function refreshDownloads() {
  try {
    const { data } = await settingApi.getAllDownloads()
    downloadTasks.value = Object.values(data)
  } catch { /* ignore */ }
}

function formatSize(size: number | null): string {
  if (size == null) return '-'
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDownloadSpeed(task: DownloadTask): string {
  if (task.status !== 'DOWNLOADING') return ''
  // This is a rough estimate based on progress
  const elapsed = task.startTime ? (Date.now() - new Date(task.startTime).getTime()) / 1000 : 0
  if (elapsed <= 0 || task.downloadedSize <= 0) return '-'
  const speed = task.downloadedSize / elapsed
  if (speed < 1024 * 1024) return `${(speed / 1024).toFixed(1)} KB/s`
  return `${(speed / 1024 / 1024).toFixed(1)} MB/s`
}

function formatEta(task: DownloadTask): string {
  if (task.status !== 'DOWNLOADING' || task.totalSize <= 0 || task.downloadedSize <= 0) return ''
  const elapsed = task.startTime ? (Date.now() - new Date(task.startTime).getTime()) / 1000 : 0
  if (elapsed <= 0) return '-'
  const speed = task.downloadedSize / elapsed
  if (speed <= 0) return '-'
  const remaining = (task.totalSize - task.downloadedSize) / speed
  if (remaining < 60) return `${Math.ceil(remaining)}s`
  if (remaining < 3600) return `${Math.ceil(remaining / 60)}m`
  return `${Math.ceil(remaining / 3600)}h`
}

function getDownloadStatusText(status: DownloadStatus): string {
  const map: Record<string, string> = {
    PENDING: '等待中',
    DOWNLOADING: '下载中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    FAILED: '下载失败',
    CANCELLED: '已取消',
    INSTALLING: '安装中',
    INSTALLED: '已安装',
  }
  return map[status] || status
}

function getDownloadStatusClass(status: DownloadStatus): string {
  return status.toLowerCase()
}

// Check if online model is already downloaded
function isModelDownloaded(modelId: string): boolean {
  return downloadTasks.value.some(t => t.modelId === modelId && (t.status === 'COMPLETED' || t.status === 'INSTALLED'))
}

function getActiveDownloadForModel(modelId: string): DownloadTask | null {
  return downloadTasks.value.find(t => t.modelId === modelId && (t.status === 'DOWNLOADING' || t.status === 'PENDING' || t.status === 'PAUSED')) || null
}

// Get model status for catalog type
function getModelStatusForType(typeKey: string): { loaded: boolean; exists: boolean; enabled: boolean; error: string | null } {
  const aiName = typeToAiName[typeKey]
  if (!aiName) return { loaded: false, exists: false, enabled: false, error: null }
  const model = models.value.find(m => m.name === aiName)
  if (!model) return { loaded: false, exists: false, enabled: false, error: null }
  return { loaded: model.loaded, exists: model.exists, enabled: model.enabled, error: model.error }
}

function getModelStatusText(typeKey: string): string {
  const status = getModelStatusForType(typeKey)
  if (status.loaded) return '使用中'
  if (status.error) return '加载失败'
  if (status.exists && status.enabled) return '已安装'
  if (status.exists) return '已安装'
  const aiName = typeToAiName[typeKey]
  if (!aiName) return '未配置'
  return '未下载'
}

function getModelStatusClass(typeKey: string): string {
  const text = getModelStatusText(typeKey)
  if (text === '使用中') return 'in-use'
  if (text === '已安装') return 'installed'
  if (text === '加载失败') return 'failed'
  if (text === '未配置') return 'not-configured'
  return 'not-downloaded'
}

// ===== Settings save =====
async function handleNamingRuleChange() {
  await settingStore.updateSettings({ photo_naming_rule: namingRule.value })
}

async function handleThresholdChange() {
  await settingStore.updateSettings({ ai_face_cluster_threshold: String(faceThreshold.value) })
}

async function handleSearchThresholdChange() {
  await settingStore.updateSettings({ ai_search_similarity_threshold: String(searchThreshold.value) })
}

function formatSizeLocal(size: number | null) {
  if (size == null) return '-'
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// Cleanup download polling on unmount
import { onUnmounted } from 'vue'
onUnmounted(() => stopDownloadPolling())

// ===== Tasks & Logs =====
const tasks = ref<AiTask[]>([])
const taskFilter = ref<'all' | 'running' | 'completed' | 'failed'>('all')

async function loadTasks() {
  try {
    const { data } = await taskApi.list(0, 50)
    tasks.value = data.content
  } catch { /* ignore */ }
}

const filteredTasks = computed(() => {
  if (taskFilter.value === 'all') return tasks.value
  if (taskFilter.value === 'running') return tasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PENDING')
  return tasks.value.filter(t => t.status === taskFilter.value.toUpperCase())
})

function getTaskTypeText(type: string): string {
  const map: Record<string, string> = {
    INDEX: '照片索引',
    TRAIN: 'AI 训练',
    DEDUP: '去重检测',
    CAPTION: 'AI 描述',
    BATCH_EMBED: '批量向量化',
  }
  return map[type] || type
}

function getTaskStatusText(status: string): string {
  const map: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

function getTaskStatusClass(status: string): string {
  return status.toLowerCase()
}

// ===== Storage Management =====
const storageInfo = ref<Record<string, unknown>>({})

async function loadStorageInfo() {
  try {
    const { data } = await settingApi.getStorageInfo()
    storageInfo.value = data
  } catch { /* ignore */ }
}

// ===== System Info =====
const systemInfo = ref<Record<string, unknown>>({})

async function loadSystemInfo() {
  try {
    const { data } = await settingApi.getSystemInfo()
    systemInfo.value = data
  } catch { /* ignore */ }
}
</script>

<template>
  <div class="settings-page">
    <div class="settings-body">
      <aside class="settings-sidebar">
        <div class="settings-brand">
          <button class="back-btn" aria-label="返回照片" @click="router.push('/')">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </button>
          <div>
            <span class="settings-eyebrow">MEMORYVAULT</span>
            <h1 class="settings-title">设置中心</h1>
          </div>
        </div>
        <nav class="settings-nav" aria-label="设置分类">
          <div v-for="group in navGroups" :key="group.label" class="settings-nav-group">
            <div class="settings-nav-group-title">{{ group.label }}</div>
            <button
              v-for="item in group.items"
              :key="item.key"
              class="nav-item"
              :class="{ active: activeSection === item.key }"
              :aria-current="activeSection === item.key ? 'page' : undefined"
              @click="activeSection = item.key"
            >
              <span class="nav-icon-wrap">
                <svg class="nav-icon" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                  <path :d="item.icon" />
                </svg>
              </span>
              <span class="nav-label">{{ item.label }}</span>
              <svg class="nav-chevron" viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                <path d="M9.29 6.71a1 1 0 000 1.42L13.17 12l-3.88 3.88a1 1 0 101.42 1.42l4.59-4.59a1 1 0 000-1.42L10.71 6.7a1 1 0 00-1.42.01z" />
              </svg>
            </button>
          </div>
        </nav>
      </aside>

      <!-- Right content -->
      <div class="settings-content">
        <!-- ====== 个人设置 ====== -->
        <div v-if="activeSection === 'profile'" class="content-panel">
          <PersonalSettingsPanel />
        </div>

        <!-- ====== 主题风格 ====== -->
        <div v-if="activeSection === 'general'" class="content-panel">
          <ThemeSettingsPanel />
        </div>

        <!-- ====== 用户管理 ====== -->
        <div v-if="activeSection === 'users'" class="content-panel">
          <div class="panel-header-row">
            <button class="btn-primary" @click="showUserDialog = true">+ 新增用户</button>
          </div>
          <div class="panel-card">
            <div class="user-table">
              <div class="table-header">
                <span>用户名</span>
                <span>昵称</span>
                <span>角色</span>
                <span>状态</span>
                <span>创建时间</span>
                <span>最后登录</span>
                <span>操作</span>
              </div>
              <div v-for="u in users" :key="u.id" class="table-row">
                <span class="cell-username">{{ u.username }}</span>
                <span>{{ u.nickname || '-' }}</span>
                <span><span class="role-badge" :class="u.role.toLowerCase()">{{ u.role === 'ADMIN' ? '管理员' : '普通用户' }}</span></span>
                <span><span class="status-badge" :class="u.enabled ? 'enabled' : 'disabled'">{{ u.enabled ? '启用' : '禁用' }}</span></span>
                <span class="cell-date">{{ formatDate(u.createdAt) }}</span>
                <span class="cell-date">{{ formatDate(u.lastLoginAt) }}</span>
                <span class="cell-actions">
                  <button class="action-link" @click="resetPassword(u.id)">重置密码</button>
                  <button class="action-link" @click="toggleEnabled(u.id)">{{ u.enabled ? '禁用' : '启用' }}</button>
                  <button class="action-link danger" @click="deleteUser(u.id, u.username)">删除</button>
                </span>
              </div>
              <div v-if="users.length === 0" class="empty-text">暂无用户</div>
            </div>
          </div>
        </div>

        <!-- ====== 扫描文件夹 ====== -->
        <div v-if="activeSection === 'folders'" class="content-panel">
          <div class="panel-header-row">
            <div style="display: flex; gap: 8px;">
              <button class="btn-primary" @click="showFolderDialog = true">+ 添加目录</button>
              <button class="btn-secondary" @click="scanAll">扫描全部</button>
            </div>
          </div>
          <div v-for="f in folders" :key="f.id" class="panel-card folder-card">
            <div class="folder-header">
              <div>
                <strong>{{ f.name }}</strong>
                <span class="folder-status" :class="f.scanStatus.toLowerCase()">{{ f.scanStatus }}</span>
              </div>
              <div class="folder-actions">
                <button class="action-link" @click="toggleFolderEnabled(f.id)">{{ f.enabled ? '禁用' : '启用' }}</button>
                <button class="action-link" @click="toggleFolderHidden(f.id)">{{ f.hidden ? '显示' : '隐藏' }}</button>
                <button class="action-link" @click="scanFolder(f.id)">扫描</button>
                <button class="action-link danger" @click="deleteFolder(f.id, f.name)">删除</button>
              </div>
            </div>
            <div class="folder-info-grid">
              <div><span class="info-label">路径</span><code>{{ f.path }}</code></div>
              <div><span class="info-label">照片数</span><span>{{ f.photoCount }}</span></div>
              <div><span class="info-label">启用</span><span>{{ f.enabled ? '是' : '否' }}</span></div>
              <div><span class="info-label">隐藏</span><span>{{ f.hidden ? '是' : '否' }}</span></div>
              <div><span class="info-label">上次扫描</span><span>{{ formatDate(f.lastScanAt) }}</span></div>
            </div>
            <div v-if="f.scanStatus === 'SCANNING'" class="scan-progress-bar">
              <div class="progress-fill" :style="{ width: f.scanProgress + '%' }"></div>
            </div>
            <div v-if="f.errorMessage" class="folder-error">{{ f.errorMessage }}</div>
          </div>
          <div v-if="folders.length === 0" class="empty-text">暂无扫描文件夹，请添加 NAS 目录</div>
        </div>

        <!-- ====== 模型管理 ====== -->
        <div v-if="activeSection === 'models'" class="content-panel">
          <div class="panel-card model-root-card">
            <div class="model-root-info">
              <span class="label-text">模型根目录</span>
              <code class="model-root-path">{{ modelRoot }}</code>
            </div>
          </div>

          <!-- Tab switcher -->
          <div class="model-tab-bar">
            <button class="model-tab" :class="{ active: modelViewMode === 'local' }" @click="modelViewMode = 'local'">本地模型</button>
            <button class="model-tab" :class="{ active: modelViewMode === 'online' }" @click="modelViewMode = 'online'">在线下载</button>
            <button class="model-tab" :class="{ active: modelViewMode === 'downloads' }" @click="modelViewMode = 'downloads'">
              下载任务
              <span v-if="downloadTasks.length > 0" class="tab-count-badge">{{ downloadTasks.length }}</span>
            </button>
          </div>

          <!-- Local Models Tab -->
          <div v-if="modelViewMode === 'local'">
            <div v-for="item in modelCatalog" :key="item.key" class="panel-card model-type-card">
              <div class="model-type-header">
                <div class="model-type-info">
                  <strong>{{ item.label }}</strong>
                  <span class="model-default-name">{{ item.defaultModel }}</span>
                </div>
                <span class="model-type-status" :class="getModelStatusClass(item.key)">{{ getModelStatusText(item.key) }}</span>
              </div>
              <!-- Local path config for AI-service-supported models -->
              <template v-if="typeToAiName[item.key]">
                <div v-for="model in models.filter(m => m.name === typeToAiName[item.key])" :key="model.name" class="model-local-config">
                  <label class="model-toggle">
                    <input v-model="model.enabled" type="checkbox" /> 启用
                  </label>
                  <div class="model-path-row">
                    <input v-model.trim="model.path" class="model-path-input" placeholder="模型文件路径" />
                  </div>
                  <div class="model-meta-row">
                    <span v-if="model.loaded" class="model-meta-tag ok">已加载</span>
                    <span v-else-if="model.exists" class="model-meta-tag">文件存在</span>
                    <span v-else class="model-meta-tag warn">文件不存在</span>
                    <span v-if="model.error" class="model-meta-tag err">{{ model.error }}</span>
                  </div>
                  <div class="model-actions">
                    <button class="btn-primary" :disabled="modelLoading" @click="saveModel(model)">保存并加载</button>
                    <button class="btn-secondary" :disabled="modelLoading || !model.enabled" @click="reloadModel(model)">重新加载</button>
                  </div>
                </div>
              </template>
              <div v-else class="model-not-supported-hint">
                <span class="info-label">此模型类型尚未集成到 AI 服务</span>
                <button class="btn-secondary" @click="modelViewMode = 'online'">前往在线下载</button>
              </div>
            </div>
          </div>

          <!-- Online Catalog Tab -->
          <div v-if="modelViewMode === 'online'">
            <div class="online-models-grid">
              <div v-for="om in onlineModels" :key="om.id" class="panel-card online-model-card">
                <div class="online-model-top">
                  <div class="online-model-name-row">
                    <strong>{{ om.name }}</strong>
                    <span class="online-model-type-tag">{{ om.typeLabel }}</span>
                  </div>
                  <span v-if="isModelDownloaded(om.id)" class="model-type-status installed">已下载</span>
                </div>
                <div class="online-model-meta">
                  <div><span class="info-label">版本</span><span>v{{ om.version }}</span></div>
                  <div><span class="info-label">大小</span><span>{{ formatSize(om.size) }}</span></div>
                  <div><span class="info-label">设备</span><span>{{ om.device }}</span></div>
                  <div><span class="info-label">精度</span><span>{{ om.precision }}</span></div>
                  <div><span class="info-label">性能</span><span>{{ om.performance }}</span></div>
                  <div><span class="info-label">来源</span><span class="source-text">{{ om.source }}</span></div>
                </div>
                <!-- Active download progress -->
                <div v-if="getActiveDownloadForModel(om.id)" class="online-download-progress">
                  <div class="download-progress-bar">
                    <div class="progress-fill" :style="{ width: getActiveDownloadForModel(om.id)!.progress + '%' }"></div>
                  </div>
                  <div class="download-progress-meta">
                    <span>{{ getActiveDownloadForModel(om.id)!.progress }}%</span>
                    <span>{{ formatSize(getActiveDownloadForModel(om.id)!.downloadedSize) }} / {{ formatSize(getActiveDownloadForModel(om.id)!.totalSize) }}</span>
                  </div>
                  <div class="download-progress-actions">
                    <button v-if="getActiveDownloadForModel(om.id)!.status === 'DOWNLOADING' || getActiveDownloadForModel(om.id)!.status === 'PENDING'" class="action-link" @click="pauseDownload(getActiveDownloadForModel(om.id)!.taskId)">暂停</button>
                    <button v-if="getActiveDownloadForModel(om.id)!.status === 'PAUSED'" class="action-link" @click="resumeDownload(getActiveDownloadForModel(om.id)!.taskId)">继续</button>
                    <button class="action-link danger" @click="cancelDownload(getActiveDownloadForModel(om.id)!.taskId)">取消</button>
                  </div>
                </div>
                <!-- Download button -->
                <div v-else class="online-model-actions">
                  <button v-if="!isModelDownloaded(om.id)" class="btn-primary" @click="startDownload(om)">下载</button>
                  <button v-else class="btn-secondary" disabled>已下载</button>
                </div>
              </div>
            </div>
          </div>

          <!-- Download Tasks Tab -->
          <div v-if="modelViewMode === 'downloads'">
            <div v-if="downloadTasks.length === 0" class="empty-text">暂无下载任务</div>
            <div v-for="task in downloadTasks" :key="task.taskId" class="panel-card download-task-card">
              <div class="download-task-header">
                <div>
                  <strong>{{ task.modelName }}</strong>
                  <span class="download-task-type">{{ modelTypeLabels[task.typeKey] || task.typeKey }}</span>
                </div>
                <span class="download-status-badge" :class="getDownloadStatusClass(task.status)">{{ getDownloadStatusText(task.status) }}</span>
              </div>
              <div class="download-progress-bar">
                <div class="progress-fill" :style="{ width: task.progress + '%' }"></div>
              </div>
              <div class="download-task-meta">
                <span>{{ task.progress }}%</span>
                <span>{{ formatSize(task.downloadedSize) }} / {{ formatSize(task.totalSize) }}</span>
                <span v-if="task.status === 'DOWNLOADING'">{{ formatDownloadSpeed(task) }}</span>
                <span v-if="task.status === 'DOWNLOADING'">剩余 {{ formatEta(task) }}</span>
              </div>
              <div v-if="task.errorMessage" class="download-error-msg">{{ task.errorMessage }}</div>
              <div class="download-task-actions">
                <button v-if="task.status === 'DOWNLOADING' || task.status === 'PENDING'" class="action-link" @click="pauseDownload(task.taskId)">暂停</button>
                <button v-if="task.status === 'PAUSED'" class="action-link" @click="resumeDownload(task.taskId)">继续</button>
                <button v-if="task.status === 'FAILED' || task.status === 'CANCELLED'" class="action-link" @click="retryDownload(task.taskId)">重试</button>
                <button v-if="task.status !== 'COMPLETED' && task.status !== 'INSTALLED'" class="action-link danger" @click="cancelDownload(task.taskId)">取消</button>
                <button v-if="task.status === 'COMPLETED'" class="action-link" @click="setCurrentModel(task.taskId)">设为当前模型</button>
              </div>
            </div>
          </div>
        </div>

        <!-- ====== 标签管理 ====== -->
        <div v-if="activeSection === 'tags'" class="content-panel">
          <div class="panel-header-row">
            <button class="btn-primary" @click="showTagDialog = true">+ 新建标签</button>
          </div>
          <div class="panel-card">
            <input v-model="tagSearchQuery" class="dialog-input tag-search-input" placeholder="搜索标签..." />
            <div v-for="t in tags.filter(tg => !tagSearchQuery || tg.name.toLowerCase().includes(tagSearchQuery.toLowerCase()))" :key="t.id" class="tag-row">
              <div class="tag-color-dot" :style="{ background: t.color || '#0a84ff' }"></div>
              <span class="tag-name">{{ t.name }}</span>
              <span v-if="t.description" class="tag-desc-text">{{ t.description }}</span>
              <span class="tag-photo-count">{{ t.photoCount }} 张</span>
              <div class="tag-color-picker">
                <button
                  v-for="c in presetColors"
                  :key="c"
                  class="color-swatch"
                  :class="{ active: t.color === c }"
                  :style="{ background: c }"
                  @click="updateTagColor(t, c)"
                ></button>
              </div>
              <button class="action-link" @click="openEditTagDialog(t)">编辑</button>
              <button class="action-link danger" @click="deleteTag(t.id, t.name, t.photoCount)">删除</button>
            </div>
            <div v-if="tags.length === 0" class="empty-text">暂无标签</div>
          </div>
        </div>

        <!-- ====== 照片与视频 ====== -->
        <div v-if="activeSection === 'photos'" class="content-panel">
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">文件命名规则</span>
                <span class="label-desc">上传照片时按此规则重命名文件</span>
              </div>
              <select v-model="namingRule" class="setting-select" @change="handleNamingRuleChange">
                <option v-for="opt in namingOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">AI 设置</span>
                <span class="label-desc">人物聚合与搜索相似度阈值</span>
              </div>
            </div>
          </div>
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">人物聚合阈值</span>
                <span class="label-desc">控制同一人物的合并灵敏度</span>
              </div>
              <span class="threshold-badge">{{ (faceThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="slider-row">
              <input type="range" min="20" max="80" step="5" v-model.number="faceThreshold" class="threshold-slider" @change="handleThresholdChange" />
            </div>
          </div>
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">搜索相似度阈值</span>
                <span class="label-desc">控制语义搜索结果相似度要求</span>
              </div>
              <span class="threshold-badge">{{ (searchThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="slider-row">
              <input type="range" min="20" max="80" step="5" v-model.number="searchThreshold" class="threshold-slider" @change="handleSearchThresholdChange" />
            </div>
          </div>
        </div>

        <!-- ====== 时间线设置 ====== -->
        <div v-if="activeSection === 'timeline'" class="content-panel">
          <div class="panel-card">
            <div class="setting-info">
              <span class="label-text">时间线说明</span>
              <span class="label-desc">时间线页面只展示已添加到时间线的照片。在照片详情页或批量操作中可将照片添加到时间线。</span>
            </div>
          </div>
        </div>

        <!-- ====== 任务与日志 ====== -->
        <div v-if="activeSection === 'tasks'" class="content-panel">
          <div class="panel-header-row">
            <button class="btn-secondary" @click="loadTasks()">刷新</button>
          </div>
          <!-- Filter tabs -->
          <div class="task-filter-bar">
            <button class="task-filter-btn" :class="{ active: taskFilter === 'all' }" @click="taskFilter = 'all'">全部</button>
            <button class="task-filter-btn" :class="{ active: taskFilter === 'running' }" @click="taskFilter = 'running'">运行中</button>
            <button class="task-filter-btn" :class="{ active: taskFilter === 'completed' }" @click="taskFilter = 'completed'">已完成</button>
            <button class="task-filter-btn" :class="{ active: taskFilter === 'failed' }" @click="taskFilter = 'failed'">失败</button>
          </div>
          <div v-if="filteredTasks.length === 0" class="empty-text">暂无任务记录</div>
          <div v-for="t in filteredTasks" :key="t.id" class="panel-card task-log-card">
            <div class="task-log-header">
              <div class="task-log-info">
                <strong>{{ getTaskTypeText(t.type) }}</strong>
                <span class="task-log-id">#{{ t.id }}</span>
              </div>
              <span class="task-log-status" :class="getTaskStatusClass(t.status)">{{ getTaskStatusText(t.status) }}</span>
            </div>
            <div v-if="t.status === 'RUNNING' || t.status === 'PENDING'" class="download-progress-bar">
              <div class="progress-fill" :style="{ width: t.progress + '%' }"></div>
            </div>
            <div class="task-log-meta">
              <span v-if="t.progress > 0">进度: {{ t.progress }}%</span>
              <span>创建: {{ formatDate(t.createdAt) }}</span>
              <span v-if="t.finishedAt">完成: {{ formatDate(t.finishedAt) }}</span>
            </div>
            <div v-if="t.resultJson" class="task-log-result">{{ t.resultJson }}</div>
          </div>
        </div>

        <!-- ====== 存储管理 ====== -->
        <div v-if="activeSection === 'storage'" class="content-panel">
          <div class="panel-header-row">
            <button class="btn-secondary" @click="loadStorageInfo()">刷新</button>
          </div>
          <!-- Storage breakdown -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 14px;">
              <span class="label-text">存储占用</span>
              <span class="label-desc">照片、缩略图和模型文件的存储分布</span>
            </div>
            <div class="storage-breakdown">
              <div class="storage-item">
                <span class="storage-label">照片文件</span>
                <span class="storage-value">{{ formatSize(storageInfo.photosSize as number || 0) }}</span>
              </div>
              <div class="storage-item">
                <span class="storage-label">缩略图</span>
                <span class="storage-value">{{ formatSize(storageInfo.thumbsSize as number || 0) }}</span>
              </div>
              <div class="storage-item">
                <span class="storage-label">模型文件</span>
                <span class="storage-value">{{ formatSize(storageInfo.modelsSize as number || 0) }}</span>
              </div>
              <div class="storage-item total">
                <span class="storage-label">总占用</span>
                <span class="storage-value">{{ formatSize(storageInfo.totalStorageSize as number || 0) }}</span>
              </div>
            </div>
          </div>
          <!-- Disk space -->
          <div v-if="storageInfo.diskTotalSpace" class="panel-card">
            <div class="setting-row" style="margin-bottom: 12px;">
              <div class="setting-info">
                <span class="label-text">磁盘空间</span>
                <span class="label-desc">数据存储所在磁盘的可用空间</span>
              </div>
            </div>
            <div class="disk-usage-bar">
              <div class="disk-used" :style="{ width: ((1 - ((storageInfo.diskFreeSpace as number) / (storageInfo.diskTotalSpace as number))) * 100).toFixed(1) + '%' }"></div>
            </div>
            <div class="disk-usage-meta">
              <span>已用: {{ formatSize((storageInfo.diskTotalSpace as number) - (storageInfo.diskFreeSpace as number)) }}</span>
              <span>可用: {{ formatSize(storageInfo.diskFreeSpace as number) }}</span>
              <span>总计: {{ formatSize(storageInfo.diskTotalSpace as number) }}</span>
            </div>
          </div>
          <!-- Directory paths -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 12px;">
              <span class="label-text">存储路径</span>
            </div>
            <div class="storage-path-list">
              <div class="storage-path-item">
                <span class="info-label">照片目录</span>
                <code>{{ storageInfo.photosDir }}</code>
              </div>
              <div class="storage-path-item">
                <span class="info-label">缩略图目录</span>
                <code>{{ storageInfo.thumbsDir }}</code>
              </div>
              <div class="storage-path-item">
                <span class="info-label">模型目录</span>
                <code>{{ storageInfo.modelDir }}</code>
              </div>
            </div>
          </div>
        </div>

        <!-- ====== 系统信息 ====== -->
        <div v-if="activeSection === 'system'" class="content-panel">
          <div class="panel-header-row">
            <button class="btn-secondary" @click="loadSystemInfo()">刷新</button>
          </div>
          <!-- App info -->
          <div class="panel-card">
            <div class="info-grid">
              <div class="info-item"><span class="info-label">应用版本</span><span>{{ systemInfo.appName }} {{ systemInfo.appVersion }}</span></div>
              <div class="info-item"><span class="info-label">操作系统</span><span>{{ systemInfo.osName }} {{ systemInfo.osVersion }} ({{ systemInfo.osArch }})</span></div>
              <div class="info-item"><span class="info-label">Java 版本</span><span>{{ systemInfo.javaVersion }}</span></div>
              <div class="info-item"><span class="info-label">CPU 核心</span><span>{{ systemInfo.availableProcessors }} 核</span></div>
            </div>
          </div>
          <!-- Memory -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 12px;">
              <span class="label-text">JVM 内存</span>
            </div>
            <div class="disk-usage-bar">
              <div class="disk-used" :style="{ width: (((systemInfo.jvmUsedMemory as number) / (systemInfo.jvmMaxMemory as number)) * 100).toFixed(1) + '%' }"></div>
            </div>
            <div class="disk-usage-meta">
              <span>已用: {{ formatSize(systemInfo.jvmUsedMemory as number || 0) }}</span>
              <span>已分配: {{ formatSize(systemInfo.jvmTotalMemory as number || 0) }}</span>
              <span>最大: {{ formatSize(systemInfo.jvmMaxMemory as number || 0) }}</span>
            </div>
          </div>
          <!-- Database -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 12px;">
              <span class="label-text">数据库</span>
            </div>
            <div class="info-grid">
              <div class="info-item"><span class="info-label">类型</span><span>{{ systemInfo.databaseType }}</span></div>
              <div class="info-item"><span class="info-label">连接地址</span><code style="font-size: 12px;">{{ systemInfo.databaseUrl }}</code></div>
            </div>
          </div>
          <!-- Data statistics -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 12px;">
              <span class="label-text">数据统计</span>
            </div>
            <div class="stats-grid">
              <div class="stat-item"><span class="stat-value">{{ systemInfo.photoCount }}</span><span class="stat-label">照片</span></div>
              <div class="stat-item"><span class="stat-value">{{ systemInfo.folderCount }}</span><span class="stat-label">扫描目录</span></div>
              <div class="stat-item"><span class="stat-value">{{ systemInfo.tagCount }}</span><span class="stat-label">标签</span></div>
              <div class="stat-item"><span class="stat-value">{{ systemInfo.personCount }}</span><span class="stat-label">人物</span></div>
            </div>
          </div>
          <!-- AI Service -->
          <div class="panel-card">
            <div class="setting-info" style="margin-bottom: 12px;">
              <span class="label-text">AI 服务</span>
            </div>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">状态</span>
                <span class="ai-status-badge" :class="systemInfo.aiServiceStatus === 'healthy' ? 'healthy' : 'offline'">
                  {{ systemInfo.aiServiceStatus === 'healthy' ? '● 在线' : '● 离线' }}
                </span>
              </div>
              <div class="info-item"><span class="info-label">服务地址</span><code style="font-size: 12px;">{{ systemInfo.aiServiceUrl }}</code></div>
              <div v-if="systemInfo.aiServiceError" class="info-item"><span class="info-label">错误</span><span style="color: var(--danger); font-size: 12px;">{{ systemInfo.aiServiceError }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- User dialog -->
    <div v-if="showUserDialog" class="dialog-overlay" @click.self="showUserDialog = false">
      <div class="dialog-card">
        <h3>新增用户</h3>
        <div class="dialog-body">
          <input v-model="newUser.username" class="dialog-input" placeholder="用户名" />
          <input v-model="newUser.password" type="password" class="dialog-input" placeholder="密码" />
          <input v-model="newUser.nickname" class="dialog-input" placeholder="昵称（可选）" />
          <select v-model="newUser.role" class="dialog-input">
            <option value="USER">普通用户</option>
            <option value="ADMIN">管理员</option>
          </select>
        </div>
        <div class="dialog-actions">
          <button class="btn-secondary" @click="showUserDialog = false">取消</button>
          <button class="btn-primary" @click="createUser">创建</button>
        </div>
      </div>
    </div>

    <!-- Tag dialog -->
    <div v-if="showTagDialog" class="dialog-overlay" @click.self="showTagDialog = false">
      <div class="dialog-card">
        <h3>新建标签</h3>
        <div class="dialog-body">
          <input v-model="newTag.name" class="dialog-input" placeholder="标签名称" />
          <textarea v-model="newTag.description" class="dialog-input" placeholder="描述（可选）" rows="2"></textarea>
          <div class="color-picker-row">
            <span class="color-label">颜色：</span>
            <button
              v-for="c in presetColors"
              :key="c"
              class="color-swatch"
              :class="{ active: newTag.color === c }"
              :style="{ background: c }"
              @click="newTag.color = c"
            ></button>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn-secondary" @click="showTagDialog = false">取消</button>
          <button class="btn-primary" @click="createTag">创建</button>
        </div>
      </div>
    </div>

    <!-- Folder dialog -->
    <div v-if="showFolderDialog" class="dialog-overlay" @click.self="showFolderDialog = false">
      <div class="dialog-card">
        <h3>添加扫描目录</h3>
        <div class="dialog-body">
          <input v-model="newFolder.name" class="dialog-input" placeholder="文件夹名称" />
          <input v-model="newFolder.path" class="dialog-input" placeholder="NAS 目录完整路径（如 /mnt/photos）" />
          <select v-model="newFolder.storageMode" class="dialog-input">
            <option value="COPY">复制到本地存储</option>
            <option value="LINK">仅链接（不复制）</option>
          </select>
        </div>
        <div class="dialog-actions">
          <button class="btn-secondary" @click="showFolderDialog = false">取消</button>
          <button class="btn-primary" @click="createFolder">添加</button>
        </div>
      </div>
    </div>

    <!-- Edit tag dialog -->
    <div v-if="showEditTagDialog" class="dialog-overlay" @click.self="showEditTagDialog = false">
      <div class="dialog-card">
        <h3>编辑标签</h3>
        <div class="dialog-body">
          <input v-model="editTagData.name" class="dialog-input" placeholder="标签名称" />
          <textarea v-model="editTagData.description" class="dialog-input" placeholder="描述（可选）" rows="2"></textarea>
          <div class="color-picker-row">
            <span class="color-label">颜色：</span>
            <button
              v-for="c in presetColors"
              :key="c"
              class="color-swatch"
              :class="{ active: editTagData.color === c }"
              :style="{ background: c }"
              @click="editTagData.color = c"
            ></button>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn-secondary" @click="showEditTagDialog = false">取消</button>
          <button class="btn-primary" @click="saveEditedTag">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  padding: 4px;
}

.settings-title {
  font-size: 20px;
  font-weight: 600;
}

/* Body - no divider line, use spacing and background */
.settings-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 16px;
}

/* Left nav */
.settings-nav {
  width: 200px;
  flex-shrink: 0;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s ease;
  text-align: left;
  width: 100%;
}

.nav-item:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--accent);
  color: #ffffff;
}

.nav-icon { flex-shrink: 0; }
.nav-label { white-space: nowrap; }

/* Right content */
.settings-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 8px 24px 24px;
}

.content-panel {
  max-width: 800px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-header-row .panel-title {
  margin-bottom: 0;
}

/* Cards */
.panel-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: 16px 18px;
  margin-bottom: 12px;
  border: 0.5px solid var(--glass-border);
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.label-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.label-desc {
  font-size: 13px;
  color: var(--text-secondary);
}

.setting-select {
  padding: 8px 12px;
  border: 1px solid var(--separator);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  min-width: 160px;
}

/* Theme cards */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.theme-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 12px;
  border: 2px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.theme-card:hover { border-color: var(--accent); }
.theme-card.active { border-color: var(--accent); background: rgba(10, 132, 255, 0.08); }
.theme-icon-lg { font-size: 28px; }
.theme-name-lg { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.theme-desc { font-size: 11px; color: var(--text-secondary); text-align: center; line-height: 1.4; }
.theme-card.active .theme-name-lg { color: var(--accent); }

/* Dock config */
.dock-config-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.dock-config-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dock-config-item label {
  width: 100px;
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.config-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--bg-tertiary);
  border-radius: 2px;
  outline: none;
}

.config-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
}

.config-value {
  width: 50px;
  text-align: right;
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

/* Sliders */
.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.threshold-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  background: var(--bg-tertiary);
  border-radius: 2px;
  outline: none;
}

.threshold-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
}

.threshold-badge {
  font-size: 16px;
  font-weight: 700;
  color: var(--accent);
  min-width: 36px;
  text-align: center;
  background: rgba(10, 132, 255, 0.12);
  padding: 4px 10px;
  border-radius: 8px;
}

/* Buttons */
.btn-primary, .btn-secondary {
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  transition: opacity 0.15s;
}

.btn-primary { color: white; background: var(--accent); }
.btn-secondary { color: var(--text-primary); background: var(--bg-tertiary); border: 1px solid var(--separator); }
.btn-primary:disabled, .btn-secondary:disabled { opacity: .5; cursor: not-allowed; }

/* User table */
.user-table {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.table-header, .table-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 0.8fr 0.7fr 1.2fr 1.2fr 1.5fr;
  gap: 8px;
  padding: 10px 8px;
  align-items: center;
  font-size: 13px;
}

.table-header {
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--separator);
}

.table-row {
  border-bottom: 0.5px solid var(--separator);
}

.role-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.role-badge.admin { background: rgba(10, 132, 255, 0.15); color: var(--accent); }
.role-badge.user { background: var(--bg-tertiary); color: var(--text-secondary); }

.status-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
}

.status-badge.enabled { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.status-badge.disabled { background: rgba(255, 69, 58, 0.15); color: var(--danger); }

.cell-date { font-size: 12px; color: var(--text-secondary); }
.cell-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.action-link {
  font-size: 12px;
  color: var(--accent);
  white-space: nowrap;
  cursor: pointer;
}

.action-link.danger { color: var(--danger); }

/* Folder cards */
.folder-card { margin-bottom: 12px; }

.folder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.folder-actions { display: flex; gap: 8px; }

.folder-status {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  margin-left: 8px;
}

.folder-status.idle { background: var(--bg-tertiary); color: var(--text-secondary); }
.folder-status.scanning { background: rgba(10, 132, 255, 0.15); color: var(--accent); }
.folder-status.completed { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.folder-status.error { background: rgba(255, 69, 58, 0.15); color: var(--danger); }

.folder-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  font-size: 13px;
}

.folder-info-grid .info-label {
  color: var(--text-tertiary);
  margin-right: 8px;
}

.folder-info-grid code {
  font-size: 12px;
  color: var(--accent);
  word-break: break-all;
}

.scan-progress-bar {
  height: 4px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  margin-top: 10px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s;
}

.folder-error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--danger);
}

/* Model cards */
.model-root-card {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-root-path {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 14px;
  background: var(--bg-tertiary);
  padding: 4px 10px;
  border-radius: 6px;
}

.model-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.model-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-state { font-size: 12px; color: var(--text-tertiary); }
.model-state.loaded { color: var(--success); }
.model-state.error { color: var(--danger); }

.model-path-input {
  flex: 1;
  padding: 9px 10px;
  border: 1px solid var(--separator);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
}

.model-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--danger);
  word-break: break-word;
}

.model-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

/* Model Tab Bar */
.model-tab-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  padding: 4px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.model-tab {
  flex: 1;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: none;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  position: relative;
}

.model-tab.active {
  background: var(--bg-secondary);
  color: var(--accent);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.tab-count-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--accent);
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 0 4px;
}

/* Model Type Card (Local tab) */
.model-type-card {
  margin-bottom: 12px;
}

.model-type-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.model-type-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.model-type-info strong {
  font-size: 15px;
  color: var(--text-primary);
}

.model-default-name {
  font-size: 12px;
  color: var(--text-tertiary);
}

.model-type-status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.model-type-status.in-use { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.model-type-status.installed { background: rgba(10, 132, 255, 0.15); color: var(--accent); }
.model-type-status.failed { background: rgba(255, 69, 58, 0.15); color: var(--danger); }
.model-type-status.not-configured { background: var(--bg-tertiary); color: var(--text-tertiary); }
.model-type-status.not-downloaded { background: rgba(255, 159, 10, 0.15); color: #ff9500; }

.model-local-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.model-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-meta-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.model-meta-tag.ok { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.model-meta-tag.warn { background: rgba(255, 159, 10, 0.15); color: #ff9500; }
.model-meta-tag.err { background: rgba(255, 69, 58, 0.15); color: var(--danger); }

.model-not-supported-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding: 10px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

/* Online Models Grid */
.online-models-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}

.online-model-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.online-model-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.online-model-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.online-model-name-row strong {
  font-size: 14px;
  color: var(--text-primary);
}

.online-model-type-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(10, 132, 255, 0.12);
  color: var(--accent);
}

.online-model-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
  font-size: 12px;
}

.online-model-meta > div {
  display: flex;
  gap: 6px;
}

.online-model-meta .info-label {
  flex-shrink: 0;
}

.source-text {
  color: var(--text-secondary);
  word-break: break-all;
}

.online-model-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

/* Download Progress */
.online-download-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.download-progress-bar {
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 3px;
  transition: width 0.3s;
}

.download-progress-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-secondary);
}

.download-progress-actions {
  display: flex;
  gap: 8px;
}

/* Download Task Card */
.download-task-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.download-task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.download-task-header strong {
  font-size: 14px;
  color: var(--text-primary);
}

.download-task-type {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 8px;
}

.download-status-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.download-status-badge.downloading { background: rgba(10, 132, 255, 0.15); color: var(--accent); }
.download-status-badge.paused { background: rgba(255, 159, 10, 0.15); color: #ff9500; }
.download-status-badge.completed { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.download-status-badge.installed { background: rgba(48, 209, 88, 0.2); color: var(--success); }
.download-status-badge.failed { background: rgba(255, 69, 58, 0.15); color: var(--danger); }
.download-status-badge.cancelled { background: var(--bg-tertiary); color: var(--text-tertiary); }
.download-status-badge.pending { background: rgba(255, 159, 10, 0.15); color: #ff9500; }

.download-task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.download-error-msg {
  font-size: 12px;
  color: var(--danger);
  background: rgba(255, 69, 58, 0.08);
  padding: 6px 10px;
  border-radius: 6px;
}

.download-task-actions {
  display: flex;
  gap: 8px;
}

/* Tag management */
.tag-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 0.5px solid var(--separator);
}

.tag-row:last-child { border-bottom: none; }

.tag-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-name {
  font-size: 14px;
  font-weight: 500;
  flex: 1;
}

.tag-photo-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.tag-desc-text {
  font-size: 12px;
  color: var(--text-tertiary);
  flex: 0 1 auto;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-search-input {
  margin-bottom: 12px;
}

.tag-color-picker {
  display: flex;
  gap: 3px;
}

.color-swatch {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.1s;
}

.color-swatch:hover { transform: scale(1.2); }
.color-swatch.active { border-color: var(--text-primary); }

/* Info grid */
.info-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.info-label {
  font-size: 13px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

/* Dialogs */
.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.55);
  display: grid;
  place-items: center;
  padding: 16px;
}

.dialog-card {
  width: min(420px, 100%);
  background: var(--bg-secondary);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}

.dialog-card h3 {
  font-size: 18px;
  margin-bottom: 16px;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.dialog-input {
  padding: 10px 12px;
  border: 1px solid var(--separator);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  outline: none;
}

.dialog-input:focus { border-color: var(--accent); }

.color-picker-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.color-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.empty-text {
  padding: 20px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 14px;
}

/* Tasks & Logs */
.task-filter-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  padding: 4px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.task-filter-btn {
  flex: 1;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: none;
  cursor: pointer;
  transition: all 0.15s;
}

.task-filter-btn.active {
  background: var(--bg-secondary);
  color: var(--accent);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.task-log-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-log-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-log-info strong {
  font-size: 14px;
  color: var(--text-primary);
}

.task-log-id {
  font-size: 12px;
  color: var(--text-tertiary);
}

.task-log-status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.task-log-status.pending { background: rgba(255, 159, 10, 0.15); color: #ff9500; }
.task-log-status.running { background: rgba(10, 132, 255, 0.15); color: var(--accent); }
.task-log-status.completed { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.task-log-status.failed { background: rgba(255, 69, 58, 0.15); color: var(--danger); }

.task-log-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.task-log-result {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--bg-tertiary);
  padding: 6px 10px;
  border-radius: 6px;
  word-break: break-all;
}

/* Storage Management */
.storage-breakdown {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.storage-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.storage-item.total {
  background: rgba(10, 132, 255, 0.08);
  border: 1px solid rgba(10, 132, 255, 0.2);
}

.storage-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.storage-item.total .storage-label {
  font-weight: 600;
  color: var(--text-primary);
}

.storage-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.disk-usage-bar {
  height: 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.disk-used {
  height: 100%;
  background: var(--accent);
  border-radius: 4px;
  transition: width 0.3s;
}

.disk-usage-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
}

.storage-path-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.storage-path-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.storage-path-item .info-label {
  width: 80px;
  flex-shrink: 0;
}

.storage-path-item code {
  font-size: 12px;
  color: var(--accent);
  word-break: break-all;
}

/* System Info - Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 8px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--accent);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.ai-status-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 500;
}

.ai-status-badge.healthy { background: rgba(48, 209, 88, 0.15); color: var(--success); }
.ai-status-badge.offline { background: rgba(255, 69, 58, 0.15); color: var(--danger); }

/* Responsive */
@media (max-width: 760px) {
  .settings-body { flex-direction: column; gap: 0; }

  .settings-nav {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
    padding: 8px 12px;
    gap: 4px;
  }

  .nav-item { flex-shrink: 0; padding: 8px 14px; }
  .nav-item .nav-label { font-size: 13px; }

  .settings-content { padding: 16px; }

  .table-header, .table-row {
    grid-template-columns: 1fr 1fr;
    font-size: 12px;
  }

  .table-header span:nth-child(3),
  .table-header span:nth-child(4),
  .table-header span:nth-child(5),
  .table-header span:nth-child(6),
  .table-row span:nth-child(3),
  .table-row span:nth-child(4),
  .table-row span:nth-child(5),
  .table-row span:nth-child(6) {
    display: none;
  }
}

/* ===== Settings workspace refresh ===== */
.settings-page {
  height: 100%;
  min-height: 0;
  padding: 20px clamp(16px, 3vw, 40px) calc(var(--tab-content-padding) + 20px);
  overflow-y: auto;
  background:
    radial-gradient(circle at 16% 4%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 24rem),
    var(--bg-primary);
}

.settings-body {
  width: min(1360px, 100%);
  min-height: min(820px, calc(100vh - 120px));
  margin: 0 auto;
  gap: 24px;
  align-items: flex-start;
}

.settings-sidebar {
  position: sticky;
  top: 0;
  width: 244px;
  max-height: calc(100vh - 92px);
  flex: 0 0 244px;
  padding: 16px;
  overflow-y: auto;
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  background: color-mix(in srgb, var(--bg-secondary) 88%, transparent);
  box-shadow: 0 16px 46px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

.settings-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 2px 2px 16px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--separator);
}

.back-btn {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  padding: 0;
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  transition: transform .18s ease, background .18s ease, color .18s ease;
}

.back-btn:hover {
  color: var(--accent);
  transform: translateX(-2px);
}

.settings-eyebrow {
  display: block;
  margin-bottom: 3px;
  color: var(--accent);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: .13em;
}

.settings-title {
  font-size: 19px;
  line-height: 1.15;
  letter-spacing: -.02em;
}

.settings-nav {
  width: 100%;
  padding: 0;
  gap: 0;
  overflow: visible;
}

.settings-nav-group {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.settings-nav-group + .settings-nav-group {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid color-mix(in srgb, var(--separator) 76%, transparent);
}

.settings-nav-group-title {
  padding: 3px 9px 4px;
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .08em;
  line-height: 1.4;
}

.nav-item {
  min-height: 42px;
  gap: 10px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 13px;
}

.nav-icon-wrap {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border-radius: 9px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  transition: inherit;
}

.nav-chevron {
  margin-left: auto;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity .18s ease, transform .18s ease;
}

.nav-item:hover {
  border-color: var(--glass-border);
  background: color-mix(in srgb, var(--bg-tertiary) 70%, transparent);
}

.nav-item.active {
  border-color: color-mix(in srgb, var(--accent) 28%, transparent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-secondary));
  color: var(--accent);
  box-shadow: none;
}

.nav-item.active .nav-icon-wrap {
  background: var(--accent);
  color: #fff;
  box-shadow: 0 6px 18px color-mix(in srgb, var(--accent) 28%, transparent);
}

.nav-item.active .nav-chevron {
  opacity: .8;
  transform: translateX(0);
}

.settings-content {
  flex: 1;
  min-width: 0;
  padding: 0;
  overflow: visible;
}

.content-panel {
  max-width: none;
  animation: settings-panel-in .22s ease-out;
}

@keyframes settings-panel-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.panel-header-row {
  min-height: 38px;
  justify-content: flex-end;
  margin: -2px 0 12px;
}

.panel-title {
  font-size: 17px;
}

.panel-card {
  padding: 20px 22px;
  margin-bottom: 14px;
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  background: color-mix(in srgb, var(--bg-secondary) 94%, transparent);
  box-shadow: 0 8px 28px rgba(0, 0, 0, .07);
}

.label-text {
  font-size: 14px;
  font-weight: 650;
}

.label-desc {
  max-width: 620px;
  margin-top: 2px;
  line-height: 1.5;
}

.btn-primary,
.btn-secondary {
  min-height: 38px;
  padding: 8px 15px;
  border-radius: 10px;
  font-weight: 650;
  transition: transform .16s ease, filter .16s ease, opacity .16s ease;
}

.btn-primary:hover:not(:disabled),
.btn-secondary:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.07);
}

.btn-primary:active:not(:disabled),
.btn-secondary:active:not(:disabled) {
  transform: translateY(0);
}

.theme-grid {
  gap: 10px;
}

.theme-card {
  position: relative;
  min-height: 132px;
  padding: 18px 14px;
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--bg-primary);
}

.theme-card::after {
  content: '';
  position: absolute;
  top: 10px;
  right: 10px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--separator);
}

.theme-card.active {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 9%, var(--bg-primary));
  box-shadow: inset 0 0 0 1px var(--accent);
}

.theme-card.active::after {
  background: var(--accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent) 15%, transparent);
}

.dock-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 22px;
}

.dock-config-item {
  display: grid;
  grid-template-columns: 88px minmax(80px, 1fr) 50px;
  min-height: 38px;
}

.dock-config-item label {
  width: auto;
}

.config-slider,
.threshold-slider {
  accent-color: var(--accent);
}

.config-slider::-webkit-slider-thumb,
.threshold-slider::-webkit-slider-thumb {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent) 14%, transparent);
}

.setting-select,
.dialog-input,
.model-path-input {
  min-height: 40px;
  border-color: var(--separator);
  border-radius: 10px;
  background: var(--bg-primary);
  outline: none;
  transition: border-color .16s ease, box-shadow .16s ease;
}

.setting-select:focus,
.dialog-input:focus,
.model-path-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 13%, transparent);
}

.user-table {
  min-width: 900px;
}

.content-panel > .panel-card:has(.user-table) {
  overflow-x: auto;
}

.table-header,
.table-row {
  grid-template-columns: 1.05fr 1fr .8fr .7fr 1.15fr 1.15fr 1.55fr;
  min-height: 48px;
  padding: 10px 6px;
}

.table-row:hover {
  background: color-mix(in srgb, var(--bg-tertiary) 45%, transparent);
}

.cell-username {
  font-weight: 650;
}

.action-link {
  min-height: 28px;
  padding: 4px 7px;
  border-radius: 7px;
  font-weight: 600;
  transition: background .15s ease;
}

.action-link:hover {
  background: color-mix(in srgb, var(--accent) 11%, transparent);
}

.action-link.danger:hover {
  background: color-mix(in srgb, var(--danger) 11%, transparent);
}

.folder-header,
.model-type-header,
.download-task-header,
.task-log-header {
  gap: 14px;
}

.folder-actions,
.download-task-actions,
.download-progress-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.folder-info-grid {
  grid-template-columns: minmax(260px, 2fr) repeat(2, minmax(100px, .6fr));
  gap: 10px 20px;
  padding-top: 12px;
  border-top: 1px solid var(--separator);
}

.folder-info-grid > div:first-child {
  grid-row: span 2;
}

.model-root-info {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.model-root-path {
  max-width: 75%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-tab-bar,
.task-filter-bar {
  position: sticky;
  top: 8px;
  z-index: 4;
  padding: 5px;
  border: 1px solid var(--glass-border);
  background: color-mix(in srgb, var(--bg-tertiary) 90%, transparent);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.model-tab,
.task-filter-btn {
  min-height: 38px;
  border-radius: 9px;
}

.online-models-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 14px;
}

.online-model-card {
  min-height: 220px;
}

.online-model-actions {
  margin-top: auto;
}

.tag-row {
  display: grid;
  grid-template-columns: 14px minmax(100px, 1fr) minmax(100px, 1.4fr) auto auto auto auto;
  gap: 10px;
  min-height: 54px;
}

.tag-name {
  min-width: 0;
}

.tag-color-picker {
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 190px;
}

.info-item {
  min-height: 34px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--separator);
}

.info-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.storage-breakdown {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.storage-item {
  min-height: 78px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 5px;
}

.storage-value {
  font-size: 18px;
  font-weight: 700;
}

.disk-usage-bar,
.download-progress-bar {
  height: 8px;
}

.dialog-overlay {
  padding: 20px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.dialog-card {
  width: min(460px, 100%);
  padding: 24px;
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, .42);
}

.dialog-card h3 {
  font-size: 20px;
}

.empty-text {
  min-height: 120px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--separator);
  border-radius: 14px;
  background: color-mix(in srgb, var(--bg-secondary) 65%, transparent);
}

@media (max-width: 1100px) {
  .settings-page {
    padding-inline: 16px;
  }

  .settings-body {
    gap: 16px;
  }

  .settings-sidebar {
    width: 210px;
    flex-basis: 210px;
    padding: 13px;
  }

  .settings-eyebrow {
    display: none;
  }

  .dock-config-grid,
  .storage-breakdown {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .tag-row {
    grid-template-columns: 14px minmax(100px, 1fr) auto auto auto;
  }

  .tag-desc-text,
  .tag-color-picker {
    display: none;
  }
}

@media (max-width: 760px) {
  .settings-page {
    height: 100%;
    padding: 10px 0 calc(var(--tab-content-padding) + 8px);
  }

  .settings-body {
    flex-direction: column;
    min-height: 0;
    gap: 12px;
  }

  .settings-sidebar {
    position: static;
    width: 100%;
    flex: none;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
    backdrop-filter: none;
    overflow: visible;
  }

  .settings-brand {
    display: none;
  }

  .settings-nav {
    width: 100%;
    flex-direction: row;
    gap: 8px;
    padding: 4px 16px 8px;
    overflow-x: auto;
    scroll-snap-type: x proximity;
    scrollbar-width: none;
  }

  .settings-nav-group {
    display: contents;
  }

  .settings-nav-group-title {
    display: none;
  }

  .nav-item {
    width: auto;
    min-height: 40px;
    flex: 0 0 auto;
    padding: 5px 11px 5px 6px;
    scroll-snap-align: start;
    border-color: var(--glass-border);
    background: var(--bg-secondary);
  }

  .nav-icon-wrap {
    width: 29px;
    height: 29px;
    flex-basis: 29px;
  }

  .nav-chevron {
    display: none;
  }

  .settings-content {
    width: 100%;
    padding: 0 16px;
  }

  .panel-card {
    padding: 17px;
    border-radius: 14px;
  }

  .panel-header-row {
    min-height: 0;
  }

  .setting-row,
  .folder-header,
  .model-type-header,
  .download-task-header {
    align-items: flex-start;
  }

  .theme-grid {
    grid-template-columns: 1fr;
  }

  .theme-card {
    min-height: 82px;
    display: grid;
    grid-template-columns: 42px 1fr;
    grid-template-rows: auto auto;
    justify-items: start;
    text-align: left;
  }

  .theme-icon-lg {
    grid-row: 1 / span 2;
    align-self: center;
  }

  .theme-desc {
    text-align: left;
  }

  .dock-config-grid,
  .storage-breakdown {
    grid-template-columns: 1fr;
  }

  .dock-config-item {
    grid-template-columns: 76px minmax(70px, 1fr) 46px;
    gap: 8px;
  }

  .setting-row {
    flex-wrap: wrap;
  }

  .setting-select {
    width: 100%;
  }

  .content-panel > .panel-card:has(.user-table) {
    padding: 10px;
    margin-inline: -2px;
  }

  .table-header,
  .table-row {
    grid-template-columns: 1.05fr 1fr .8fr .7fr 1.15fr 1.15fr 1.55fr;
  }

  .table-header span:nth-child(n),
  .table-row span:nth-child(n) {
    display: initial;
  }

  .cell-actions {
    display: flex !important;
  }

  .folder-header {
    flex-direction: column;
  }

  .folder-actions {
    justify-content: flex-start;
  }

  .folder-info-grid {
    grid-template-columns: 1fr 1fr;
  }

  .folder-info-grid > div:first-child {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .model-root-info {
    align-items: flex-start;
    flex-direction: column;
  }

  .model-root-path {
    width: 100%;
    max-width: 100%;
  }

  .model-tab,
  .task-filter-btn {
    padding-inline: 7px;
    font-size: 12px;
  }

  .online-model-meta {
    grid-template-columns: 1fr;
  }

  .tag-row {
    grid-template-columns: 14px minmax(80px, 1fr) auto auto auto;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .disk-usage-meta {
    flex-direction: column;
    gap: 4px;
  }

  .storage-path-item,
  .info-item {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .dialog-card {
    padding: 20px;
  }
}

@media (max-width: 420px) {
  .settings-content,
  .settings-nav {
    padding-inline: 12px;
  }

  .tag-row {
    grid-template-columns: 14px minmax(80px, 1fr) auto auto;
  }

  .tag-photo-count {
    display: none;
  }
}
</style>

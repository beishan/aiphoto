<script setup lang="ts">
import { ref, inject, type Ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingStore } from '@/stores/settingStore'
import { settingApi, type ModelFile, type ModelName, type ModelStatus } from '@/api/settingApi'
import { useMessage } from 'naive-ui'

const router = useRouter()
const settingStore = useSettingStore()
const message = useMessage()

const theme = inject<Ref<string>>('theme')!
const setTheme = inject<(t: string) => void>('setTheme')!

const namingRule = ref('original')
const faceThreshold = ref(50)
const searchThreshold = ref(80)
const modelRoot = ref('/models')
const models = ref<ModelStatus[]>([])
const modelLoading = ref(false)
const browserModel = ref<ModelName | null>(null)
const browserDirectory = ref('')
const browserFiles = ref<ModelFile[]>([])
const uploadDirectory = ref('uploads')
const uploading = ref(false)

// 左侧导航：当前选中的板块
const activeSection = ref('upload')

const navItems = [
  { key: 'upload', label: '照片上传', icon: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z' },
  { key: 'appearance', label: '外观主题', icon: 'M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-1-.01-.83.67-1.5 1.49-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z' },
  { key: 'ai', label: 'AI 设置', icon: 'M21 10.12h-6.78l2.74-2.82c-2.73-2.7-7.15-2.8-9.88-.1-2.73 2.71-2.73 7.08 0 9.79 2.73 2.71 7.15 2.71 9.88 0C18.32 15.65 19 14.08 19 12.1h2c0 1.98-.88 4.55-2.64 6.29-3.51 3.48-9.21 3.48-12.72 0-3.5-3.47-3.53-9.11-.02-12.58 3.51-3.47 9.14-3.47 12.65 0L21 3v7.12zM12.5 8v4.25l3.5 2.08-.72 1.21L11 13V8h1.5z' },
  { key: 'models', label: '模型管理', icon: 'M4 6c0-1.1 3.58-2 8-2s8 .9 8 2-3.58 2-8 2-8-.9-8-2zm0 4v4c0 1.1 3.58 2 8 2s8-.9 8-2v-4c-1.72 1.21-5.03 1.75-8 1.75S5.72 11.21 4 10zm0 8v-2c1.72 1.21 5.03 1.75 8 1.75s6.28-.54 8-1.75v2c0 1.1-3.58 2-8 2s-8-.9-8-2z' },
]

const themeOptions = [
  { value: 'dark', label: '经典暗色', icon: '🌙', desc: '纯黑背景，护眼省电' },
  { value: 'light', label: '明亮模式', icon: '☀️', desc: '白色背景，清晰醒目' },
  { value: 'liquid-glass', label: '液态玻璃', icon: '💎', desc: '毛玻璃效果，炫彩流光' },
]

const namingOptions = [
  { value: 'original', label: '保留原名', example: 'IMG_20240101.jpg' },
  { value: 'date_original', label: '日期 + 原名', example: '2024-01-01_IMG_20240101.jpg' },
  { value: 'date_time', label: '日期时间', example: '20240101_123456.jpg' },
  { value: 'uuid', label: 'UUID', example: 'a1b2c3d4.jpg' },
  { value: 'timestamp', label: '时间戳', example: '1704067200000.jpg' },
  { value: 'custom', label: '自定义前缀', example: 'photo_1704067200000.jpg' },
]

onMounted(async () => {
  if (!settingStore.loaded) {
    await settingStore.fetchSettings()
  }
  namingRule.value = settingStore.getSetting('photo_naming_rule', 'original')
  faceThreshold.value = Number(settingStore.getSetting('ai_face_cluster_threshold', '50'))
  searchThreshold.value = Number(settingStore.getSetting('ai_search_similarity_threshold', '80'))
  await loadModels()
})

const modelLabels: Record<ModelName, string> = {
  clip: 'Chinese-CLIP 语义模型',
  insightface: 'InsightFace 人脸模型',
  yolo: 'YOLO 目标检测模型',
  blip: 'BLIP-2 图片描述模型',
}

async function loadModels() {
  modelLoading.value = true
  try {
    const { data } = await settingApi.getModels()
    modelRoot.value = data.root
    models.value = data.models
  } catch (error: any) {
    message.error(error.response?.data?.message || '读取模型状态失败')
  } finally {
    modelLoading.value = false
  }
}

async function saveModel(model: ModelStatus) {
  modelLoading.value = true
  try {
    const { data } = await settingApi.configureModel(model.name, model.path, model.enabled)
    Object.assign(model, data)
    data.loaded ? message.success(`${modelLabels[model.name]} 已加载`) : message.warning(data.error || '模型配置已保存，但未加载')
  } catch (error: any) {
    message.error(error.response?.data?.message || error.response?.data?.detail || '保存模型配置失败')
  } finally {
    modelLoading.value = false
  }
}

async function reloadModel(model: ModelStatus) {
  modelLoading.value = true
  try {
    const { data } = await settingApi.reloadModel(model.name)
    Object.assign(model, data)
    data.loaded ? message.success('模型重新加载成功') : message.error(data.error || '模型加载失败')
  } finally {
    modelLoading.value = false
  }
}

async function openBrowser(model: ModelStatus) {
  browserModel.value = model.name
  browserDirectory.value = ''
  await browseDirectory('')
}

async function browseDirectory(directory: string) {
  try {
    const { data } = await settingApi.browseModels(directory)
    browserDirectory.value = directory
    browserFiles.value = data
  } catch (error: any) {
    message.error(error.response?.data?.message || '读取目录失败')
  }
}

function goParentDirectory() {
  const parts = browserDirectory.value.split('/').filter(Boolean)
  parts.pop()
  browseDirectory(parts.join('/'))
}

function selectBrowserPath(path: string) {
  const model = models.value.find(item => item.name === browserModel.value)
  if (model) model.path = path
  browserModel.value = null
}

async function handleModelUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    for (const file of files) await settingApi.uploadModel(uploadDirectory.value, file)
    message.success(`已上传 ${files.length} 个模型文件`)
    if (browserModel.value) await browseDirectory(browserDirectory.value)
  } catch (error: any) {
    message.error(error.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function formatSize(size: number | null) {
  if (size == null) return '目录'
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function handleNamingRuleChange() {
  await settingStore.updateSettings({ photo_naming_rule: namingRule.value })
}

async function handleThresholdChange() {
  await settingStore.updateSettings({ ai_face_cluster_threshold: String(faceThreshold.value) })
}

async function handleSearchThresholdChange() {
  await settingStore.updateSettings({ ai_search_similarity_threshold: String(searchThreshold.value) })
}
</script>

<template>
  <div class="settings-page">
    <!-- 页面顶栏 -->
    <div class="settings-header">
      <button class="back-btn" @click="router.push('/more')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <h1 class="settings-title">设置</h1>
    </div>

    <!-- 主体：左侧导航 + 右侧内容 -->
    <div class="settings-body">
      <!-- 左侧导航 -->
      <nav class="settings-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeSection === item.key }"
          @click="activeSection = item.key"
        >
          <svg class="nav-icon" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path :d="item.icon" />
          </svg>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </nav>

      <!-- 右侧内容区 -->
      <div class="settings-content">
        <!-- ====== 照片上传 ====== -->
        <div v-if="activeSection === 'upload'" class="content-panel">
          <h2 class="panel-title">照片上传</h2>
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">文件命名规则</span>
                <span class="label-desc">上传照片时按此规则重命名文件</span>
              </div>
              <select v-model="namingRule" class="setting-select" @change="handleNamingRuleChange">
                <option v-for="opt in namingOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>
            <div class="hint-box">
              <span class="hint-label">示例</span>
              <code class="hint-code">{{ namingOptions.find(o => o.value === namingRule)?.example }}</code>
            </div>
          </div>
        </div>

        <!-- ====== 外观主题 ====== -->
        <div v-if="activeSection === 'appearance'" class="content-panel">
          <h2 class="panel-title">外观主题</h2>
          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">主题模式</span>
                <span class="label-desc">选择应用的视觉风格</span>
              </div>
            </div>
            <div class="theme-grid">
              <button
                v-for="opt in themeOptions"
                :key="opt.value"
                class="theme-card"
                :class="{ active: theme === opt.value }"
                @click="setTheme(opt.value)"
              >
                <span class="theme-icon-lg">{{ opt.icon }}</span>
                <span class="theme-name-lg">{{ opt.label }}</span>
                <span class="theme-desc">{{ opt.desc }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- ====== AI 设置 ====== -->
        <div v-if="activeSection === 'ai'" class="content-panel">
          <h2 class="panel-title">AI 设置</h2>

          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">人物聚合阈值</span>
                <span class="label-desc">控制同一人物的合并灵敏度，值越大越容易合并</span>
              </div>
              <span class="threshold-badge">{{ (faceThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="slider-row">
              <span class="slider-endpoint">严格</span>
              <input
                type="range"
                min="20"
                max="80"
                step="5"
                v-model.number="faceThreshold"
                class="threshold-slider"
                @change="handleThresholdChange"
              />
              <span class="slider-endpoint">宽松</span>
            </div>
            <div class="hint-box">
              <span class="hint-label">说明</span>
              <span class="hint-value">{{
                faceThreshold <= 30 ? '严格模式：仅非常相似的面孔会被合并，可能产生多个人物条目' :
                faceThreshold >= 70 ? '宽松模式：相似面孔容易合并，可能误合不同人' :
                '均衡模式：推荐设置，平衡准确度和聚合效果'
              }}</span>
            </div>
          </div>

          <div class="panel-card">
            <div class="setting-row">
              <div class="setting-info">
                <span class="label-text">搜索相似度阈值</span>
                <span class="label-desc">控制语义搜索结果的相似度要求</span>
              </div>
              <span class="threshold-badge">{{ (searchThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="slider-row">
              <span class="slider-endpoint">严格</span>
              <input
                type="range"
                min="20"
                max="80"
                step="5"
                v-model.number="searchThreshold"
                class="threshold-slider"
                @change="handleSearchThresholdChange"
              />
              <span class="slider-endpoint">宽松</span>
            </div>
            <div class="hint-box">
              <span class="hint-label">说明</span>
              <span class="hint-value">{{
                searchThreshold <= 40 ? '严格模式：仅高相似度结果，精确但可能遗漏' :
                searchThreshold >= 70 ? '宽松模式：更多结果，可能包含不太相关的内容' :
                '均衡模式：推荐设置，平衡精确度和召回率'
              }}</span>
            </div>
          </div>
        </div>

        <!-- ====== 模型管理 ====== -->
        <div v-if="activeSection === 'models'" class="content-panel">
          <h2 class="panel-title">本地模型管理</h2>

          <div class="panel-card model-root-card">
            <div class="model-root-info">
              <span class="label-text">NAS 挂载根目录</span>
              <code class="model-root-path">{{ modelRoot }}</code>
            </div>
            <span class="offline-badge">仅本地 · 禁止联网下载</span>
          </div>

          <div v-if="modelLoading && !models.length" class="empty-state">正在读取模型状态…</div>

          <div v-for="model in models" :key="model.name" class="panel-card model-card">
            <div class="model-card-top">
              <div class="model-name-row">
                <strong>{{ modelLabels[model.name] }}</strong>
                <span class="model-state" :class="{ loaded: model.loaded, error: model.error }">
                  {{ model.loaded ? '● 已加载' : model.error ? '● 加载失败' : model.exists ? '● 未加载' : '● 文件不存在' }}
                </span>
              </div>
              <label class="model-toggle">
                <input v-model="model.enabled" type="checkbox" /> 启用
              </label>
            </div>
            <div class="model-path-row">
              <input v-model.trim="model.path" class="model-path-input" placeholder="相对于模型根目录的路径" />
              <button class="btn-secondary" @click="openBrowser(model)">选择</button>
            </div>
            <p v-if="model.error" class="model-error">{{ model.error }}</p>
            <div class="model-actions">
              <button class="btn-primary" :disabled="modelLoading" @click="saveModel(model)">保存并加载</button>
              <button class="btn-secondary" :disabled="modelLoading || !model.enabled" @click="reloadModel(model)">重新加载</button>
            </div>
          </div>

          <div class="panel-card upload-panel">
            <div class="setting-info">
              <span class="label-text">上传到模型仓库</span>
              <span class="label-desc">支持多文件；大型目录模型可分批上传到同一目标目录</span>
            </div>
            <div class="upload-row">
              <input v-model.trim="uploadDirectory" class="model-path-input" placeholder="目标目录，如 blip2" />
              <label class="btn-primary file-button">
                {{ uploading ? '上传中…' : '选择文件并上传' }}
                <input type="file" multiple :disabled="uploading" @change="handleModelUpload" />
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 模型浏览器弹窗 -->
    <div v-if="browserModel" class="browser-overlay" @click.self="browserModel = null">
      <div class="browser-dialog">
        <div class="browser-header">
          <strong>选择 {{ modelLabels[browserModel] }}</strong>
          <button class="close-btn" @click="browserModel = null">×</button>
        </div>
        <div class="browser-path"><code>/models/{{ browserDirectory }}</code></div>
        <div class="browser-toolbar">
          <button class="btn-secondary" :disabled="!browserDirectory" @click="goParentDirectory">上一级</button>
          <button v-if="browserModel === 'insightface' || browserModel === 'blip'" class="btn-primary" @click="selectBrowserPath(browserDirectory)">选择当前目录</button>
        </div>
        <div class="browser-list">
          <button v-for="file in browserFiles" :key="file.path" class="browser-entry" @dblclick="file.directory ? browseDirectory(file.path) : selectBrowserPath(file.path)">
            <span class="entry-name" @click="file.directory ? browseDirectory(file.path) : selectBrowserPath(file.path)">{{ file.directory ? '📁' : '📄' }} {{ file.name }}</span>
            <span>{{ formatSize(file.size) }}</span>
          </button>
          <div v-if="!browserFiles.length" class="empty-state">此目录为空</div>
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

/* ===== 顶栏 ===== */
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

/* ===== 主体布局：左导航 + 右内容 ===== */
.settings-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 1px;
  background: var(--separator);
}

/* ===== 左侧导航 ===== */
.settings-nav {
  width: 200px;
  flex-shrink: 0;
  background: var(--bg-primary);
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

.nav-icon {
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
}

/* ===== 右侧内容区 ===== */
.settings-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--bg-primary);
  padding: 20px 24px;
}

.content-panel {
  max-width: 720px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-primary);
}

/* ===== 设置卡片 ===== */
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
  cursor: pointer;
}

/* ===== 提示框 ===== */
.hint-box {
  margin-top: 12px;
  padding: 10px 14px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint-label {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.hint-value {
  color: var(--text-primary);
}

.hint-code {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 13px;
}

/* ===== 主题选择卡片 ===== */
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

.theme-card:hover {
  border-color: var(--accent);
}

.theme-card.active {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.08);
}

.theme-icon-lg {
  font-size: 28px;
}

.theme-name-lg {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.theme-desc {
  font-size: 11px;
  color: var(--text-secondary);
  text-align: center;
  line-height: 1.4;
}

.theme-card.active .theme-name-lg {
  color: var(--accent);
}

/* ===== 滑块 ===== */
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
  appearance: none;
  background: var(--bg-tertiary);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.threshold-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
  border: 2px solid var(--bg-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.threshold-slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
  border: 2px solid var(--bg-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.slider-endpoint {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
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

/* ===== 模型管理 ===== */
.model-root-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.model-root-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.model-root-path {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 14px;
  background: var(--bg-tertiary);
  padding: 4px 10px;
  border-radius: 6px;
}

.offline-badge {
  font-size: 12px;
  color: var(--success);
  white-space: nowrap;
}

.model-card {
  /* inherits panel-card */
}

.model-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.model-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.model-state {
  font-size: 12px;
  color: var(--text-tertiary);
}

.model-state.loaded {
  color: var(--success);
}

.model-state.error {
  color: var(--danger);
}

.model-toggle {
  white-space: nowrap;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.model-path-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-path-input {
  flex: 1;
  min-width: 0;
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

/* ===== 按钮 ===== */
.btn-primary, .btn-secondary {
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  transition: opacity 0.15s;
}

.btn-primary {
  color: white;
  background: var(--accent);
}

.btn-secondary {
  color: var(--text-primary);
  background: var(--bg-tertiary);
  border: 1px solid var(--separator);
}

.btn-primary:disabled, .btn-secondary:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.btn-primary:hover:not(:disabled), .btn-secondary:hover:not(:disabled) {
  opacity: 0.85;
}

/* ===== 上传 ===== */
.upload-panel .setting-info {
  margin-bottom: 10px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-button {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
}

.file-button input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

/* ===== 模型浏览器弹窗 ===== */
.browser-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.55);
  display: grid;
  place-items: center;
  padding: 16px;
}

.browser-dialog {
  width: min(620px, 100%);
  max-height: 75vh;
  overflow: hidden;
  background: var(--bg-secondary);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
}

.browser-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
}

.close-btn {
  font-size: 26px;
  color: var(--text-secondary);
  line-height: 1;
}

.browser-path {
  padding: 10px;
  margin: 10px 0;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.browser-path code {
  color: var(--accent);
  word-break: break-all;
}

.browser-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.browser-list {
  max-height: 45vh;
  overflow: auto;
}

.browser-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px;
  border-bottom: 1px solid var(--separator);
  color: var(--text-primary);
  text-align: left;
}

.entry-name {
  flex: 1;
}

.empty-state {
  padding: 20px;
  color: var(--text-secondary);
  text-align: center;
}

/* ===== 响应式：小屏改为顶部横向导航 ===== */
@media (max-width: 640px) {
  .settings-body {
    flex-direction: column;
  }

  .settings-nav {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
    padding: 8px 12px;
    gap: 4px;
    border-bottom: 0.5px solid var(--separator);
  }

  .nav-item {
    flex-shrink: 0;
    padding: 8px 14px;
  }

  .nav-item .nav-label {
    font-size: 13px;
  }

  .settings-content {
    padding: 16px;
  }

  .theme-grid {
    grid-template-columns: 1fr;
  }

  .model-path-row, .upload-row {
    flex-direction: column;
    align-items: stretch;
  }

  .model-actions {
    justify-content: stretch;
  }

  .model-actions button {
    flex: 1;
  }
}
</style>

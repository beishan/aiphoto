<script setup lang="ts">
import { ref, inject, type Ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingStore } from '@/stores/settingStore'

const router = useRouter()
const settingStore = useSettingStore()

const theme = inject<Ref<string>>('theme')!
const setTheme = inject<(t: string) => void>('setTheme')!

const namingRule = ref('original')
const faceThreshold = ref(50)
const searchThreshold = ref(80)

// Accordion state: 0 = 照片上传, 1 = 外观, 2 = AI 设置
const expandedSection = ref<number | null>(0)

const themeOptions = [
  { value: 'dark', label: '经典', icon: '🌙' },
  { value: 'light', label: '明亮', icon: '☀️' },
  { value: 'liquid-glass', label: 'Liquid Glass', icon: '💎' },
]

const namingOptions = [
  { value: 'original', label: '保留原名', example: 'IMG_20240101.jpg' },
  { value: 'date_original', label: '日期 + 原名', example: '2024-01-01_IMG_20240101.jpg' },
  { value: 'date_time', label: '日期时间', example: '20240101_123456.jpg' },
  { value: 'uuid', label: 'UUID', example: 'a1b2c3d4.jpg' },
  { value: 'timestamp', label: '时间戳', example: '1704067200000.jpg' },
  { value: 'custom', label: '自定义前缀', example: 'photo_1704067200000.jpg' },
]

function toggleSection(index: number) {
  expandedSection.value = expandedSection.value === index ? null : index
}

onMounted(async () => {
  if (!settingStore.loaded) {
    await settingStore.fetchSettings()
  }
  namingRule.value = settingStore.getSetting('photo_naming_rule', 'original')
  faceThreshold.value = Number(settingStore.getSetting('ai_face_cluster_threshold', '50'))
  searchThreshold.value = Number(settingStore.getSetting('ai_search_similarity_threshold', '80'))
})

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
  <div class="settings-view">
    <div class="page-header">
      <button class="back-btn" @click="router.push('/more')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <h1 class="page-title">设置</h1>
    </div>

    <!-- 照片上传 -->
    <div class="accordion-item" :class="{ expanded: expandedSection === 0 }">
      <button class="accordion-header" @click="toggleSection(0)">
        <div class="accordion-title-group">
          <svg class="accordion-icon" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
          </svg>
          <span class="accordion-title">照片上传</span>
        </div>
        <svg class="accordion-chevron" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" />
        </svg>
      </button>
      <div class="accordion-body">
        <div class="accordion-content">
          <div class="setting-item">
            <div class="setting-label">
              <span class="label-text">文件命名规则</span>
              <span class="label-desc">上传照片时按此规则重命名文件</span>
            </div>
            <select v-model="namingRule" class="setting-select" @change="handleNamingRuleChange">
              <option v-for="opt in namingOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
          <div class="setting-hint">
            <span class="hint-label">示例：</span>
            <span class="hint-value">{{ namingOptions.find(o => o.value === namingRule)?.example }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 外观 -->
    <div class="accordion-item" :class="{ expanded: expandedSection === 1 }">
      <button class="accordion-header" @click="toggleSection(1)">
        <div class="accordion-title-group">
          <svg class="accordion-icon" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-1-.01-.83.67-1.5 1.49-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
          </svg>
          <span class="accordion-title">外观</span>
        </div>
        <svg class="accordion-chevron" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" />
        </svg>
      </button>
      <div class="accordion-body">
        <div class="accordion-content">
          <div class="setting-item">
            <div class="setting-label">
              <span class="label-text">主题</span>
              <span class="label-desc">选择应用的视觉风格</span>
            </div>
            <div class="theme-options">
              <button
                v-for="opt in themeOptions"
                :key="opt.value"
                class="theme-option"
                :class="{ active: theme === opt.value }"
                @click="setTheme(opt.value)"
              >
                <span class="theme-icon">{{ opt.icon }}</span>
                <span class="theme-name">{{ opt.label }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- AI 设置 -->
    <div class="accordion-item" :class="{ expanded: expandedSection === 2 }">
      <button class="accordion-header" @click="toggleSection(2)">
        <div class="accordion-title-group">
          <svg class="accordion-icon" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M21 10.12h-6.78l2.74-2.82c-2.73-2.7-7.15-2.8-9.88-.1-2.73 2.71-2.73 7.08 0 9.79 2.73 2.71 7.15 2.71 9.88 0C18.32 15.65 19 14.08 19 12.1h2c0 1.98-.88 4.55-2.64 6.29-3.51 3.48-9.21 3.48-12.72 0-3.5-3.47-3.53-9.11-.02-12.58 3.51-3.47 9.14-3.47 12.65 0L21 3v7.12zM12.5 8v4.25l3.5 2.08-.72 1.21L11 13V8h1.5z" />
          </svg>
          <span class="accordion-title">AI 设置</span>
        </div>
        <svg class="accordion-chevron" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" />
        </svg>
      </button>
      <div class="accordion-body">
        <div class="accordion-content">
          <div class="setting-item">
            <div class="setting-label">
              <span class="label-text">人物聚合阈值</span>
              <span class="label-desc">控制同一人物的合并灵敏度，值越大越容易合并</span>
            </div>
            <div class="threshold-control">
              <span class="threshold-label">严格</span>
              <input
                type="range"
                min="20"
                max="80"
                step="5"
                v-model.number="faceThreshold"
                class="threshold-slider"
                @change="handleThresholdChange"
              />
              <span class="threshold-label">宽松</span>
              <span class="threshold-value">{{ (faceThreshold / 100).toFixed(2) }}</span>
            </div>
          </div>
          <div class="setting-hint">
            <span class="hint-label">说明：</span>
            <span class="hint-value">
              {{ faceThreshold <= 30 ? '严格模式：仅非常相似的面孔会被合并，可能产生多个人物条目' :
                 faceThreshold >= 70 ? '宽松模式：相似面孔容易合并，可能误合不同人' :
                 '均衡模式：推荐设置，平衡准确度和聚合效果' }}
            </span>
          </div>
          <div class="setting-item">
            <div class="setting-label">
              <span class="label-text">搜索相似度阈值</span>
              <span class="label-desc">控制语义搜索结果的相似度要求</span>
            </div>
            <div class="threshold-control">
              <span class="threshold-label">严格</span>
              <input
                type="range"
                min="20"
                max="80"
                step="5"
                v-model.number="searchThreshold"
                class="threshold-slider"
                @change="handleSearchThresholdChange"
              />
              <span class="threshold-label">宽松</span>
              <span class="threshold-value">{{ (searchThreshold / 100).toFixed(2) }}</span>
            </div>
          </div>
          <div class="setting-hint">
            <span class="hint-label">说明：</span>
            <span class="hint-value">
              {{ searchThreshold <= 40 ? '严格模式：仅高相似度结果，精确但可能遗漏' :
                 searchThreshold >= 70 ? '宽松模式：更多结果，可能包含不太相关的内容' :
                 '均衡模式：推荐设置，平衡精确度和召回率' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 0 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  padding: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  flex: 1;
}

/* Accordion */
.accordion-item {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
  overflow: hidden;
}

.accordion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 16px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  transition: background 0.15s;
}

.accordion-header:active {
  background: var(--bg-tertiary);
}

.accordion-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.accordion-icon {
  color: var(--accent);
  flex-shrink: 0;
}

.accordion-title {
  font-size: 16px;
  font-weight: 500;
}

.accordion-chevron {
  color: var(--text-tertiary);
  flex-shrink: 0;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.accordion-item.expanded .accordion-chevron {
  transform: rotate(180deg);
}

.accordion-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.accordion-item.expanded .accordion-body {
  grid-template-rows: 1fr;
}

.accordion-content {
  overflow: hidden;
}

.accordion-content > *:first-child {
  padding: 0 16px;
}

.accordion-content > *:last-child {
  padding-bottom: 16px;
}

/* Setting controls */
.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.setting-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.label-text {
  font-size: 15px;
  font-weight: 500;
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
  font-size: 15px;
  min-width: 140px;
  cursor: pointer;
}

.setting-hint {
  margin-top: 12px;
  margin-left: 16px;
  margin-right: 16px;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  font-size: 13px;
}

.hint-label {
  color: var(--text-secondary);
}

.hint-value {
  color: var(--text-primary);
  font-family: monospace;
}

.threshold-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-slider {
  width: 120px;
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

.threshold-label {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.threshold-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
  min-width: 32px;
  text-align: center;
}

.theme-options {
  display: flex;
  gap: 8px;
}

.theme-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 14px;
  border: 2px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  cursor: pointer;
  transition: all 0.2s;
  min-width: 72px;
}

.theme-option:hover {
  border-color: var(--accent);
}

.theme-option.active {
  border-color: var(--accent);
  background: var(--bg-tertiary);
}

.theme-icon {
  font-size: 20px;
}

.theme-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.theme-option.active .theme-name {
  color: var(--accent);
}
</style>

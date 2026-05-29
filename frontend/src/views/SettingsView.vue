<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSettingStore } from '@/stores/settingStore'

const settingStore = useSettingStore()

const namingRule = ref('original')

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
})

async function handleNamingRuleChange() {
  await settingStore.updateSettings({ photo_naming_rule: namingRule.value })
}
</script>

<template>
  <div class="settings-view">
    <h1 class="page-title">设置</h1>

    <div class="settings-section">
      <h2 class="section-title">照片上传</h2>
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
</template>

<style scoped>
.settings-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 24px 16px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 24px;
}

.settings-section {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 24px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

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
  font-size: 16px;
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
</style>

<script setup lang="ts">
import { computed, inject, onMounted, ref, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Brush, Monitor, Operation, Picture } from '@element-plus/icons-vue'
import DockIcon, { type DockIconName, type DockIconStyle } from '@/components/DockIcon.vue'
import { useDockIconStore } from '@/stores/dockIconStore'
import {
  DEFAULT_BACKGROUNDS,
  DEFAULT_DOCK,
  backgroundStyle,
  getAccent,
  getBackground,
  loadDockConfig,
  normalizeHex,
  resetAccent,
  resetBackground,
  setAccent,
  setBackground,
  setDockConfig,
  type AppTheme,
  type BackgroundConfig,
  type DockConfig,
} from '@/utils/themeAppearance'

type TabId = 'style' | 'color' | 'background' | 'dock'

const theme = inject<Ref<AppTheme>>('theme')!
const setTheme = inject<(value: AppTheme) => void>('setTheme')!
const dockIconStore = useDockIconStore()
const storedTab = localStorage.getItem('themeSettingsActiveTab') as TabId | null
const activeTab = ref<TabId>(['style', 'color', 'background', 'dock'].includes(storedTab || '') ? storedTab! : 'style')
const accent = ref(getAccent(theme.value))
const background = ref<BackgroundConfig>(getBackground(theme.value))
const dock = ref<DockConfig>(loadDockConfig())

const tabs = [
  { id: 'style' as const, label: '主题样式', icon: Monitor },
  { id: 'color' as const, label: '主题色', icon: Brush },
  { id: 'background' as const, label: '背景与表面', icon: Picture },
  { id: 'dock' as const, label: 'Dock 设置', icon: Operation },
]
const backgroundModeOptions = [
  { label: '纯色', value: 'solid' },
  { label: '渐变', value: 'gradient' },
]
const dockIconStyleOptions = [
  { label: '现代简洁', value: 'minimal' as const, description: '高对比矢量图形', previewIcons: ['photo', 'albums', 'settings'] as DockIconName[] },
  { label: 'macOS 26', value: 'macos26' as const, description: '液态玻璃与无边框图标', previewIcons: ['photo', 'albums', 'settings'] as DockIconName[] },
  { label: '自定义', value: 'custom' as const, description: '完整显示自己上传的图片', previewIcons: ['photo', 'albums', 'settings'] as DockIconName[] },
]
const customIconItems: Array<{ name: DockIconName; label: string }> = [
  { name: 'photo', label: '照片' }, { name: 'timeline', label: '时间线' },
  { name: 'tags', label: '标签' }, { name: 'albums', label: '相册' },
  { name: 'baby', label: '宝宝' }, { name: 'search', label: '搜索' },
  { name: 'settings', label: '设置' }, { name: 'trashEmpty', label: '回收站（空）' },
  { name: 'trashFull', label: '回收站（非空）' },
]

const themes: Array<{ id: AppTheme; name: string; description: string; badge: string }> = [
  { id: 'dark', name: '经典暗色', description: '深邃黑色表面，适合夜间浏览与沉浸式看图。', badge: 'DARK' },
  { id: 'light', name: '明亮模式', description: '干净明快的浅色界面，照片与信息更清晰。', badge: 'LIGHT' },
  { id: 'macos26', name: 'MACOS26', description: '通透液态玻璃、柔和彩色光晕与空间层次。', badge: 'GLASS' },
]

const accentGroups: Record<AppTheme, Array<{ name: string; color: string }>> = {
  dark: [
    { name: '电光蓝', color: '#0A84FF' }, { name: '薄荷绿', color: '#30D158' },
    { name: '明亮紫', color: '#BF5AF2' }, { name: '暖橙色', color: '#FF9F0A' },
    { name: '珊瑚红', color: '#FF453A' }, { name: '玫瑰粉', color: '#FF375F' },
  ],
  light: [
    { name: '系统蓝', color: '#007AFF' }, { name: '森林绿', color: '#248A3D' },
    { name: '典雅紫', color: '#8944AB' }, { name: '活力橙', color: '#C76D00' },
    { name: '砖石红', color: '#D92D28' }, { name: '玫瑰红', color: '#D30F45' },
  ],
  macos26: [
    { name: '系统蓝', color: '#007AFF' }, { name: '天空蓝', color: '#32ADE6' },
    { name: '薄荷青', color: '#00C7BE' }, { name: '梦幻紫', color: '#AF52DE' },
    { name: '日落橙', color: '#FF9500' }, { name: '珊瑚红', color: '#FF3B30' },
  ],
}

const backgroundPresets: Record<AppTheme, Array<{ name: string; description: string; value: BackgroundConfig }>> = {
  dark: [
    { name: '午夜黑', description: '纯粹、沉浸', value: DEFAULT_BACKGROUNDS.dark },
    { name: '深海蓝', description: '安静、深邃', value: { mode: 'gradient', pageColor: '#071523', secondaryColor: '#15243D', navColor: '#16263B', navOpacity: 76, surfaceColor: '#172335', surfaceOpacity: 90 } },
    { name: '石墨灰', description: '克制、中性', value: { mode: 'solid', pageColor: '#111214', secondaryColor: '#24262B', navColor: '#232429', navOpacity: 78, surfaceColor: '#222327', surfaceOpacity: 94 } },
    { name: '暗夜紫', description: '柔和、神秘', value: { mode: 'gradient', pageColor: '#130D20', secondaryColor: '#211A38', navColor: '#261D38', navOpacity: 72, surfaceColor: '#241C32', surfaceOpacity: 88 } },
  ],
  light: [
    { name: '纯净白', description: '明亮、极简', value: DEFAULT_BACKGROUNDS.light },
    { name: '冷雾灰', description: '克制、专业', value: { mode: 'solid', pageColor: '#F1F3F6', secondaryColor: '#E7ECF3', navColor: '#FFFFFF', navOpacity: 88, surfaceColor: '#FFFFFF', surfaceOpacity: 96 } },
    { name: '浅天蓝', description: '清新、开阔', value: { mode: 'gradient', pageColor: '#E7F2F8', secondaryColor: '#E4ECFA', navColor: '#F8FCFF', navOpacity: 78, surfaceColor: '#FFFFFF', surfaceOpacity: 80 } },
    { name: '象牙纸', description: '温润、耐看', value: { mode: 'gradient', pageColor: '#FAF6F1', secondaryColor: '#F3E9DC', navColor: '#FFFBF5', navOpacity: 90, surfaceColor: '#FFFBF5', surfaceOpacity: 88 } },
  ],
  macos26: [
    { name: '虹彩天幕', description: 'Liquid Glass', value: DEFAULT_BACKGROUNDS.macos26 },
    { name: '海湾晨光', description: '清透、明亮', value: { mode: 'gradient', pageColor: '#CDEDF4', secondaryColor: '#E9E0FA', navColor: '#F5FCFF', navOpacity: 58, surfaceColor: '#FFFFFF', surfaceOpacity: 54 } },
    { name: '银色空间', description: '克制、精密', value: { mode: 'gradient', pageColor: '#DDE3EA', secondaryColor: '#F1F3F7', navColor: '#FFFFFF', navOpacity: 66, surfaceColor: '#F9FBFD', surfaceOpacity: 62 } },
    { name: '落日玻璃', description: '柔紫、暖光', value: { mode: 'gradient', pageColor: '#F3DDE8', secondaryColor: '#DDE8FA', navColor: '#FFF8FC', navOpacity: 60, surfaceColor: '#FFFFFF', surfaceOpacity: 56 } },
  ],
}

const currentThemeName = computed(() => themes.find(item => item.id === theme.value)?.name || '')
const accentPresets = computed(() => accentGroups[theme.value])
const currentBackgroundPresets = computed(() => backgroundPresets[theme.value])
const previewBackground = computed(() => backgroundStyle(background.value))
const previewSurface = computed(() => hexToRgba(background.value.surfaceColor, background.value.surfaceOpacity))
const previewNav = computed(() => hexToRgba(background.value.navColor, background.value.navOpacity))
const dockPreviewStyle = computed(() => ({
  '--preview-opacity': String(dock.value.opacity),
  '--preview-blur': `${dock.value.blurStrength}px`,
  '--preview-size': '48px',
  '--preview-icon-size': `${dock.value.iconSize}px`,
  '--preview-scale': String(dock.value.maxScale),
  '--preview-gap': '11px',
  '--preview-spread': `${Math.round((dock.value.maxScale - 1) * 36)}px`,
}))

watch(theme, value => {
  accent.value = getAccent(value)
  background.value = getBackground(value)
})

watch(activeTab, value => localStorage.setItem('themeSettingsActiveTab', value))

function selectTheme(value: AppTheme) {
  setTheme(value)
}

function hexToRgba(hex: string, opacity: number) {
  const color = normalizeHex(hex) || '#FFFFFF'
  const channels = [1, 3, 5].map(index => Number.parseInt(color.slice(index, index + 2), 16))
  return `rgba(${channels.join(',')},${opacity / 100})`
}

function updateAccent(value: string) {
  const normalized = normalizeHex(value)
  if (!normalized) return
  accent.value = normalized
  setAccent(theme.value, normalized)
}

function updateAccentFromPicker(value: string | null) {
  if (value) updateAccent(value)
}

function updateBackgroundColor(key: 'pageColor' | 'secondaryColor' | 'navColor' | 'surfaceColor', value: string | null) {
  if (value) updateBackground(key, value)
}

function updateBackgroundMode(value: string | number | boolean) {
  updateBackground('mode', value === 'gradient' ? 'gradient' : 'solid')
}

function sliderValue(value: number | number[]) {
  return Array.isArray(value) ? value[0] : value
}

function updateDockIconStyle(value: string | number | boolean) {
  updateDock('iconStyle', (['minimal', 'custom'].includes(String(value)) ? value : 'macos26') as DockIconStyle)
}

async function uploadDockIcon(name: DockIconName, options: UploadRequestOptions) {
  try {
    await dockIconStore.upload(name, options.file)
    ElMessage.success('Dock 图标已更新')
    options.onSuccess({})
  } catch (error) {
    const text = error instanceof Error ? error.message : '图标上传失败'
    ElMessage.error(text)
  }
}

async function removeDockIcon(name: DockIconName) {
  try { await ElMessageBox.confirm('确定移除这个自定义图标吗？', '移除图标', { type: 'warning' }) }
  catch { return }
  await dockIconStore.remove(name)
  ElMessage.success('已恢复默认图标')
}

function restoreAccent() {
  resetAccent(theme.value)
  accent.value = getAccent(theme.value)
}

function updateBackground<K extends keyof BackgroundConfig>(key: K, value: BackgroundConfig[K]) {
  background.value = { ...background.value, [key]: value }
  setBackground(theme.value, background.value)
}

function applyBackgroundPreset(value: BackgroundConfig) {
  background.value = { ...value }
  setBackground(theme.value, background.value)
}

function restoreBackground() {
  resetBackground(theme.value)
  background.value = getBackground(theme.value)
}

function updateDock<K extends keyof DockConfig>(key: K, value: DockConfig[K]) {
  dock.value = { ...dock.value, [key]: value }
  setDockConfig(dock.value)
}

function restoreDock() {
  dock.value = { ...DEFAULT_DOCK }
  setDockConfig(dock.value)
}

onMounted(() => void dockIconStore.hydrate().catch(() => undefined))
</script>

<template>
  <div class="theme-settings">
    <div class="theme-nav-row">
      <el-tabs v-model="activeTab" class="theme-tabs" stretch aria-label="主题风格设置">
        <el-tab-pane v-for="tab in tabs" :key="tab.id" :name="tab.id">
          <template #label>
            <span class="element-tab-label"><el-icon class="theme-tab-icon"><component :is="tab.icon" /></el-icon>{{ tab.label }}</span>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <section v-if="activeTab === 'style'" class="settings-section">
      <div class="section-intro">
        <div><h2>选择主题样式</h2><p>每种主题都保留独立的主题色和背景方案。</p></div>
        <span class="current-badge">当前 · {{ currentThemeName }}</span>
      </div>
      <div class="style-grid">
        <button v-for="item in themes" :key="item.id" class="style-card" :class="[`preview-${item.id}`, { active: theme === item.id }]" @click="selectTheme(item.id)">
          <span class="style-preview">
            <i class="preview-sidebar"></i><i class="preview-topbar"></i>
            <span class="preview-content"><i></i><i></i><i></i></span>
            <span class="preview-dock"><i></i><i></i><i></i><i></i></span>
          </span>
          <span class="style-copy"><b>{{ item.name }}</b><small>{{ item.description }}</small></span>
          <span class="style-badge">{{ item.badge }}</span>
          <span v-if="theme === item.id" class="selected-check">✓</span>
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'color'" class="settings-section">
      <div class="section-intro">
        <div><h2>主题色</h2><p>调整按钮、选中状态与关键操作的强调色。</p></div>
        <el-button type="primary" link @click="restoreAccent">恢复当前默认</el-button>
      </div>
      <div class="color-layout">
        <div class="live-preview accent-preview" :style="{ '--preview-accent': accent }">
          <span class="preview-label">实时预览</span>
          <div class="accent-window"><span class="accent-window-title">MemoryVault</span><el-button type="primary" size="small">主要操作</el-button><div class="accent-lines"><i></i><i></i><i></i></div></div>
        </div>
        <div class="color-controls">
          <div class="control-title"><strong>自定义颜色</strong><span>{{ currentThemeName }}</span></div>
          <div class="color-input-row">
            <el-color-picker :model-value="accent" aria-label="选择主题色" @active-change="updateAccentFromPicker" @change="updateAccentFromPicker" />
            <el-input :model-value="accent" maxlength="7" aria-label="主题色 HEX 值" @change="updateAccent" />
          </div>
          <div class="preset-heading"><strong>推荐色彩</strong><small>为当前主题调校</small></div>
          <div class="accent-presets">
            <button v-for="preset in accentPresets" :key="preset.color" :class="{ active: accent === preset.color }" @click="updateAccent(preset.color)">
              <span :style="{ background: preset.color }"><i v-if="accent === preset.color">✓</i></span><small>{{ preset.name }}</small>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="activeTab === 'background'" class="settings-section">
      <div class="section-intro">
        <div><h2>背景与表面</h2><p>分别控制页面背景、顶部导航和卡片的通透感。</p></div>
        <el-button type="primary" link @click="restoreBackground">恢复当前默认</el-button>
      </div>
      <div class="background-layout">
        <div class="live-preview surface-preview" :style="{ background: previewBackground }">
          <div class="surface-window" :style="{ background: previewSurface }"><div class="surface-nav" :style="{ background: previewNav }"><b>MemoryVault</b><i></i><i></i></div><div class="surface-body"><span><b>让回忆更有温度</b><i></i></span><div><i></i><i></i><i></i></div></div></div>
          <span class="preview-label">实时预览</span>
        </div>
        <div class="surface-controls">
          <div class="mode-row"><strong>页面背景</strong><el-segmented :model-value="background.mode" :options="backgroundModeOptions" size="small" @change="updateBackgroundMode" /></div>
          <div class="surface-color-grid">
            <label>主背景<span class="element-color-row"><el-color-picker :model-value="background.pageColor" @active-change="(value: string | null) => updateBackgroundColor('pageColor', value)" @change="(value: string | null) => updateBackgroundColor('pageColor', value)" /><el-input :model-value="background.pageColor" size="small" @change="(value: string) => updateBackgroundColor('pageColor', value)" /></span></label>
            <label v-if="background.mode === 'gradient'">渐变尾色<span class="element-color-row"><el-color-picker :model-value="background.secondaryColor" @active-change="(value: string | null) => updateBackgroundColor('secondaryColor', value)" @change="(value: string | null) => updateBackgroundColor('secondaryColor', value)" /><el-input :model-value="background.secondaryColor" size="small" @change="(value: string) => updateBackgroundColor('secondaryColor', value)" /></span></label>
            <label>导航背景<span class="element-color-row"><el-color-picker :model-value="background.navColor" @active-change="(value: string | null) => updateBackgroundColor('navColor', value)" @change="(value: string | null) => updateBackgroundColor('navColor', value)" /><el-input :model-value="background.navColor" size="small" @change="(value: string) => updateBackgroundColor('navColor', value)" /></span></label>
            <label>卡片表面<span class="element-color-row"><el-color-picker :model-value="background.surfaceColor" @active-change="(value: string | null) => updateBackgroundColor('surfaceColor', value)" @change="(value: string | null) => updateBackgroundColor('surfaceColor', value)" /><el-input :model-value="background.surfaceColor" size="small" @change="(value: string) => updateBackgroundColor('surfaceColor', value)" /></span></label>
          </div>
          <label class="range-control"><span><b>导航透明度</b><output>{{ background.navOpacity }}%</output></span><el-slider :model-value="background.navOpacity" :min="20" :max="100" :show-tooltip="false" @input="(value: number | number[]) => updateBackground('navOpacity', sliderValue(value))" /></label>
          <label class="range-control"><span><b>卡片透明度</b><output>{{ background.surfaceOpacity }}%</output></span><el-slider :model-value="background.surfaceOpacity" :min="35" :max="100" :show-tooltip="false" @input="(value: number | number[]) => updateBackground('surfaceOpacity', sliderValue(value))" /></label>
        </div>
      </div>
      <div class="preset-heading background-heading"><strong>推荐方案</strong><small>一键应用整套背景组合</small></div>
      <div class="background-presets">
        <button v-for="preset in currentBackgroundPresets" :key="preset.name" :class="{ active: JSON.stringify(background) === JSON.stringify(preset.value) }" @click="applyBackgroundPreset(preset.value)">
          <span class="background-swatch" :style="{ background: backgroundStyle(preset.value), '--preset-card': hexToRgba(preset.value.surfaceColor, preset.value.surfaceOpacity) }"><i></i></span>
          <span><b>{{ preset.name }}</b><small>{{ preset.description }}</small></span>
        </button>
      </div>
    </section>

    <section v-else class="settings-section">
      <div class="section-intro">
        <div><h2>Dock 设置</h2><p>调整底部菜单的尺寸、玻璃质感与悬浮反馈，效果实时应用。</p></div>
        <el-button type="primary" link @click="restoreDock">恢复默认</el-button>
      </div>
      <div class="dock-layout">
        <div class="dock-stage"><i class="orb one"></i><i class="orb two"></i><div class="dock-preview" :style="dockPreviewStyle"><span v-for="(item,index) in customIconItems.slice(0,7)" :key="item.name" class="dock-preview-item" :class="[item.name,dock.iconStyle,{ magnified:index===2 }]"><DockIcon :name="item.name" :variant="dock.iconStyle" :custom-src="dockIconStore.iconUrls[item.name]" /></span><span class="dock-preview-item trash" :class="dock.iconStyle"><DockIcon name="trashFull" :variant="dock.iconStyle" :custom-src="dockIconStore.iconUrls.trashFull" /></span></div><span class="preview-label">实时预览</span></div>
        <div class="dock-controls">
          <section class="dock-icon-config">
            <div class="dock-icon-config-copy"><b>图标风格</b><small>选择系统图标、macOS 26 图标或上传自己的图标。</small></div>
            <div class="dock-icon-options">
              <button
                v-for="option in dockIconStyleOptions"
                :key="option.value"
                type="button"
                class="dock-icon-option"
                :class="{ active: dock.iconStyle === option.value }"
                :aria-pressed="dock.iconStyle === option.value"
                @click="updateDockIconStyle(option.value)"
              >
                <span class="dock-icon-option-preview">
                  <DockIcon
                    v-for="icon in option.previewIcons"
                    :key="icon"
                    :name="icon"
                    :variant="option.value"
                    :custom-src="option.value === 'custom' ? dockIconStore.iconUrls[icon] : undefined"
                  />
                </span>
                <span class="dock-icon-option-text"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span>
                <span class="dock-icon-option-check">✓</span>
              </button>
            </div>
          </section>
          <label class="range-control"><span><b>Dock 透明度</b><output>{{ Math.round(dock.opacity * 100) }}%</output></span><small>控制玻璃托盘底色的浓淡。</small><el-slider :model-value="dock.opacity" :min="0.3" :max="1" :step="0.05" :show-tooltip="false" @input="(value: number | number[]) => updateDock('opacity', sliderValue(value))" /></label>
          <label class="range-control"><span><b>玻璃模糊</b><output>{{ dock.blurStrength }} px</output></span><small>调整背景折射的柔和程度。</small><el-slider :model-value="dock.blurStrength" :min="5" :max="40" :show-tooltip="false" @input="(value: number | number[]) => updateDock('blurStrength', sliderValue(value))" /></label>
          <label class="range-control"><span><b>图标大小</b><output>{{ dock.iconSize }} px</output></span><small>可放大至接近填满 Dock 单元，不改变玻璃托盘和图标间距。</small><el-slider :model-value="dock.iconSize" :min="16" :max="44" :show-tooltip="false" @input="(value: number | number[]) => updateDock('iconSize', sliderValue(value))" /></label>
          <label class="range-control"><span><b>悬浮放大</b><output>{{ dock.maxScale.toFixed(2) }}×</output></span><small>设置指针靠近图标时的最大比例。</small><el-slider :model-value="dock.maxScale" :min="1.1" :max="2" :step="0.05" :show-tooltip="false" @input="(value: number | number[]) => updateDock('maxScale', sliderValue(value))" /></label>
          <label class="range-control"><span><b>动画速度</b><output>{{ dock.animationSpeed.toFixed(2) }} s</output></span><small>控制 Dock 图标跟随与复位速度。</small><el-slider :model-value="dock.animationSpeed" :min="0.1" :max="0.5" :step="0.05" :show-tooltip="false" @input="(value: number | number[]) => updateDock('animationSpeed', sliderValue(value))" /></label>
        </div>
      </div>
      <div v-if="dock.iconStyle === 'custom'" class="custom-icon-settings">
        <div class="preset-heading background-heading"><strong>自定义 Dock 图标</strong><small>支持 JPG、PNG、WebP，单张不超过 5MB</small></div>
        <div class="custom-icon-grid">
          <article v-for="item in customIconItems" :key="item.name" class="custom-icon-card">
            <div class="custom-icon-image"><img v-if="dockIconStore.iconUrls[item.name]" :src="dockIconStore.iconUrls[item.name]" :alt="item.label" /><DockIcon v-else :name="item.name" variant="minimal" /></div>
            <strong>{{ item.label }}</strong>
            <div class="custom-icon-actions">
              <el-upload action="#" accept="image/jpeg,image/png,image/webp" :show-file-list="false" :http-request="(options: UploadRequestOptions) => uploadDockIcon(item.name, options)"><el-button size="small" :loading="dockIconStore.uploading[item.name]">上传</el-button></el-upload>
              <el-button v-if="dockIconStore.iconUrls[item.name]" type="danger" link size="small" @click="removeDockIcon(item.name)">移除</el-button>
            </div>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.theme-settings { width: 100%; }
.theme-nav-row { display:flex;align-items:stretch;gap:10px;margin-bottom:16px; }.theme-tabs { flex:1;min-width:0;padding:6px 10px 0;border:1px solid var(--glass-border);border-radius:16px;background:var(--bg-secondary);box-shadow:0 8px 26px rgba(0,0,0,.06); }
.auto-save-status { display:flex;min-width:132px;align-items:center;justify-content:center;gap:6px;padding:0 14px;border:1px solid var(--glass-border);border-radius:16px;background:var(--bg-secondary);color:var(--text-secondary);font-size:11px;font-weight:600;box-shadow:0 8px 26px rgba(0,0,0,.06);white-space:nowrap; }.auto-save-status span { display:grid;width:18px;height:18px;place-items:center;border-radius:50%;background:rgba(48,209,88,.14);color:var(--success);font-size:11px;font-weight:800; }
.theme-tab { display: flex; min-height: 44px; align-items: center; justify-content: center; gap: 7px; padding: 9px 12px; border-radius: 11px; color: var(--text-secondary); font-size: 13px; font-weight: 600; transition: .18s ease; }
.theme-tab:hover { color: var(--text-primary); background: var(--bg-tertiary); }.theme-tab.active { color: var(--accent); background: var(--bg-card); box-shadow: 0 5px 18px rgba(0,0,0,.1), inset 0 0 0 1px var(--accent-border, rgba(0,122,255,.25)); }.tab-icon { font-size: 16px; }
.settings-section { padding: 22px; border: 1px solid var(--glass-border); border-radius: 18px; background: var(--bg-secondary); box-shadow: 0 16px 38px rgba(0,0,0,.07); }
.section-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 22px; }.section-intro h2 { margin: 0; color: var(--text-primary); font-size: 18px; }.section-intro p { margin: 5px 0 0; color: var(--text-secondary); font-size: 13px; }.current-badge { padding: 6px 10px; border: 1px solid var(--accent-border); border-radius: 999px; background: var(--accent-soft); color: var(--accent); font-size: 11px; font-weight: 700; white-space: nowrap; }.reset-button { color: var(--accent); font-size: 12px; font-weight: 600; white-space: nowrap; }
.style-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }.style-card { position: relative; overflow: hidden; padding: 9px; border: 1.5px solid var(--separator); border-radius: 16px; background: var(--bg-primary); text-align: left; transition: .2s ease; }.style-card:hover { transform: translateY(-3px); border-color: var(--accent-border); box-shadow: 0 14px 28px rgba(0,0,0,.12); }.style-card.active { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft), 0 14px 28px rgba(0,0,0,.12); }.style-preview { position: relative; display: block; height: 132px; overflow: hidden; border-radius: 11px; background: var(--preview-bg); }.preview-dark { --preview-bg:#09090a;--preview-surface:#242426;--preview-card:#303033; }.preview-light { --preview-bg:#edf0f5;--preview-surface:#fff;--preview-card:#fff; }.preview-macos26 { --preview-bg:linear-gradient(135deg,#c7e9fb,#eadcf8);--preview-surface:rgba(255,255,255,.58);--preview-card:rgba(255,255,255,.54); }.preview-sidebar { position:absolute;inset:0 auto 0 0;width:25%;background:var(--preview-surface); }.preview-topbar { position:absolute;left:25%;right:0;top:0;height:22%;background:var(--preview-surface); }.preview-content { position:absolute;left:31%;right:7%;top:31%;display:grid;grid-template-columns:repeat(3,1fr);gap:6px; }.preview-content i { height:58px;border-radius:7px;background:var(--preview-card);box-shadow:0 5px 12px rgba(0,0,0,.12); }.preview-dock { position:absolute;left:36%;right:12%;bottom:7px;display:flex;justify-content:center;gap:5px;padding:5px;border-radius:9px;background:var(--preview-surface);box-shadow:0 5px 12px rgba(0,0,0,.16); }.preview-dock i { width:12px;height:12px;border-radius:4px;background:#168cff; }.style-copy { display:grid;gap:4px;padding:12px 5px 5px; }.style-copy b { color:var(--text-primary);font-size:14px; }.style-copy small { min-height:48px;color:var(--text-secondary);font-size:11px;line-height:1.45; }.style-badge { position:absolute;right:14px;top:14px;padding:3px 6px;border-radius:5px;background:rgba(0,0,0,.38);color:#fff;font-size:8px;font-weight:800;letter-spacing:.08em; }.selected-check { position:absolute;right:13px;bottom:13px;display:grid;width:21px;height:21px;place-items:center;border-radius:50%;background:var(--accent);color:#fff;font-size:12px;font-weight:800; }
.color-layout,.background-layout,.dock-layout { display:grid;grid-template-columns:minmax(260px,.9fr) minmax(320px,1.1fr);gap:22px; }.live-preview,.dock-stage { position:relative;min-height:340px;display:grid;place-items:center;overflow:hidden;border:1px solid var(--glass-border);border-radius:20px;background:var(--page-background,var(--bg-primary)); }.live-preview::before,.dock-stage::before { position:absolute;width:190px;height:190px;border-radius:50%;background:var(--accent-soft);content:'';filter:blur(3px);transform:translate(45%,-55%); }.preview-label { position:absolute;right:12px;bottom:12px;padding:5px 9px;border:1px solid rgba(255,255,255,.5);border-radius:999px;background:rgba(255,255,255,.56);color:#253247;font-size:10px;font-weight:700;backdrop-filter:blur(12px); }.accent-window { position:relative;width:76%;padding:24px;border:1px solid var(--glass-border);border-radius:16px;background:var(--bg-card);box-shadow:0 18px 42px rgba(0,0,0,.15); }.accent-window-title { display:block;margin-bottom:24px;color:var(--text-primary);font-size:17px;font-weight:700; }.accent-window button { padding:8px 12px;border-radius:8px;background:var(--preview-accent);color:white;font-size:11px;font-weight:700; }.accent-lines { display:grid;gap:7px;margin-top:24px; }.accent-lines i { height:7px;border-radius:9px;background:var(--bg-tertiary); }.accent-lines i:first-child { width:72%;background:var(--preview-accent); }.accent-lines i:last-child { width:48%; }.color-controls,.surface-controls,.dock-controls { display:grid;align-content:start;gap:18px; }.control-title,.preset-heading,.mode-row { display:flex;align-items:center;justify-content:space-between;gap:12px; }.control-title strong,.preset-heading strong,.mode-row strong { color:var(--text-primary);font-size:13px; }.control-title span,.preset-heading small { color:var(--text-tertiary);font-size:11px; }.color-input-row { display:grid;grid-template-columns:50px 1fr;gap:9px; }.native-color,.surface-color-grid input { width:100%;height:40px;padding:3px;border:1px solid var(--separator);border-radius:10px;background:var(--bg-primary); }.hex-input { min-width:0;padding:0 12px;border:1px solid var(--separator);border-radius:10px;background:var(--bg-primary);color:var(--text-primary);font-family:var(--font-mono);font-size:13px; }.accent-presets { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px; }.accent-presets button { display:grid;grid-template-columns:30px 1fr;align-items:center;gap:7px;padding:7px;border:1px solid var(--separator);border-radius:10px;color:var(--text-secondary);text-align:left; }.accent-presets button.active { border-color:var(--accent);background:var(--accent-soft); }.accent-presets button>span { display:grid;width:30px;height:30px;place-items:center;border-radius:8px;color:#fff; }.accent-presets small { font-size:10px; }
.surface-window { position:relative;width:78%;overflow:hidden;border:1px solid rgba(255,255,255,.5);border-radius:16px;box-shadow:0 20px 44px rgba(36,52,80,.2);backdrop-filter:blur(16px); }.surface-nav { display:flex;height:44px;align-items:center;gap:8px;padding:0 13px;color:#172235;backdrop-filter:blur(16px); }.surface-nav b { margin-right:auto;font-size:10px; }.surface-nav i { width:20px;height:4px;border-radius:9px;background:rgba(23,34,53,.35); }.surface-body { min-height:190px;padding:24px;color:#172235; }.surface-body>span { display:grid;gap:8px; }.surface-body>span b { font-size:14px; }.surface-body>span i { width:55%;height:5px;border-radius:9px;background:rgba(23,34,53,.25); }.surface-body>div { display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:26px; }.surface-body>div i { height:52px;border-radius:8px;background:rgba(255,255,255,.62);box-shadow:0 7px 14px rgba(0,0,0,.08); }.segmented { display:flex;padding:3px;border:1px solid var(--separator);border-radius:9px;background:var(--bg-tertiary); }.segmented button { padding:5px 11px;border-radius:6px;color:var(--text-secondary);font-size:11px; }.segmented button.active { background:var(--bg-card);color:var(--accent);box-shadow:0 3px 8px rgba(0,0,0,.1); }.surface-color-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px; }.surface-color-grid label { display:grid;grid-template-columns:34px 1fr;align-items:center;gap:7px;color:var(--text-secondary);font-size:11px; }.surface-color-grid label>input { grid-row:2;width:34px;height:32px; }.surface-color-grid label>span { grid-row:2;color:var(--text-primary);font-family:var(--font-mono);font-size:11px; }.range-control { display:grid;gap:7px;padding-top:12px;border-top:1px solid var(--separator); }.range-control>span { display:flex;justify-content:space-between;gap:12px; }.range-control b { color:var(--text-primary);font-size:12px; }.range-control output { color:var(--accent);font-size:11px;font-weight:700; }.range-control small { color:var(--text-tertiary);font-size:10px; }.range-control input { width:100%;accent-color:var(--accent); }.background-heading { margin-top:22px;padding-top:18px;border-top:1px solid var(--separator); }.background-presets { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:11px; }.background-presets button { min-width:0;padding:7px;border:1px solid var(--separator);border-radius:11px;background:var(--bg-card);text-align:left; }.background-presets button.active { border-color:var(--accent);box-shadow:0 0 0 2px var(--accent-soft); }.background-swatch { position:relative;display:block;height:40px;overflow:hidden;border-radius:7px; }.background-swatch i { position:absolute;right:7px;bottom:6px;width:45%;height:18px;border-radius:4px;background:var(--preset-card);box-shadow:0 3px 8px rgba(0,0,0,.13); }.background-presets button>span:last-child { display:grid;gap:2px;margin-top:6px; }.background-presets b { overflow:hidden;color:var(--text-primary);font-size:10px;text-overflow:ellipsis;white-space:nowrap; }.background-presets small { color:var(--text-tertiary);font-size:9px; }
.dock-stage { min-height:430px;background:linear-gradient(145deg,#c8ebfb,#eadcf8 54%,#d8f1e9); }.orb { position:absolute;border-radius:50%;filter:blur(4px); }.orb.one { width:190px;height:190px;left:-25px;top:-20px;background:rgba(50,173,230,.46); }.orb.two { width:230px;height:230px;right:-45px;bottom:-45px;background:rgba(175,82,222,.32); }.dock-preview { z-index:1;display:flex;align-items:flex-end;gap:var(--preview-gap,10px);padding:9px 11px;border:1px solid rgba(255,255,255,.75);border-radius:18px;background:rgba(255,255,255,var(--preview-opacity));box-shadow:0 18px 38px rgba(46,58,90,.22),inset 0 1px 0 white;backdrop-filter:blur(var(--preview-blur)) saturate(180%); }.dock-preview-item { position:relative;display:grid;width:var(--preview-size);height:var(--preview-size);place-items:center;border-radius:10px;color:#fff;font-size:calc(var(--preview-size) * .5);font-weight:700;text-shadow:0 1px 2px rgba(0,0,0,.2);box-shadow:0 5px 12px rgba(0,0,0,.2); }.dock-preview-item.magnified { z-index:2;margin-inline:var(--preview-spread,0);transform:scale(var(--preview-scale)) translateY(-7px); }.dock-preview-item.custom{overflow:visible;border:0;border-radius:0;background:none!important;box-shadow:none;text-shadow:none}.photo{background:linear-gradient(145deg,#50c7ff,#1677ff)}.timeline{background:linear-gradient(145deg,#ffcc00,#ff6b00)}.albums{background:linear-gradient(145deg,#bf7aff,#6d3ee8)}.people{background:linear-gradient(145deg,#50e3a4,#0a9b75)}.settings{background:linear-gradient(145deg,#aab4c4,#596579)}
.theme-tabs :deep(.el-tabs__header){margin:0}.theme-tabs :deep(.el-tabs__nav-wrap::after){display:none}.theme-tabs :deep(.el-tabs__active-bar){height:3px;border-radius:4px;background:var(--accent)}.theme-tabs :deep(.el-tabs__item){height:44px;color:var(--text-secondary);font-size:13px;font-weight:650}.theme-tabs :deep(.el-tabs__item.is-active){color:var(--accent)}.theme-tabs :deep(.el-tabs__content){display:none}.element-tab-label{display:flex;align-items:center;justify-content:center;gap:7px}.element-tab-label>span{font-size:16px}
.color-input-row{grid-template-columns:40px minmax(0,1fr)}.color-input-row :deep(.el-color-picker__trigger){width:40px;height:40px;border-radius:10px}.color-input-row :deep(.el-input__wrapper){min-height:40px;border-radius:10px}.color-input-row :deep(.el-input__inner){font-family:var(--font-mono)}
.surface-color-grid label{display:grid;grid-template-columns:1fr;align-items:initial}.surface-color-grid label>.element-color-row{display:grid;grid-row:auto;grid-template-columns:34px minmax(0,1fr);gap:7px;color:var(--text-primary);font-family:var(--font-mono)}.element-color-row :deep(.el-color-picker__trigger){width:34px;height:32px;border-radius:9px}.element-color-row :deep(.el-input__wrapper){border-radius:9px}.element-color-row :deep(.el-input__inner){font-family:var(--font-mono);font-size:11px}.range-control :deep(.el-slider){height:24px}.range-control :deep(.el-slider__bar){background:var(--accent)}.range-control :deep(.el-slider__button){border-color:var(--accent)}
.dock-preview-item :deep(.dock-glyph){width:58%;height:58%}.dock-preview-item.custom :deep(.dock-glyph){width:100%;height:100%}.dock-preview-item.tags{background:linear-gradient(145deg,#ff8dc7,#eb3d84 56%,#9e1c61)}.dock-preview-item.baby{background:linear-gradient(145deg,#67dfbd,#18a881 56%,#08715d)}.dock-preview-item.search{background:linear-gradient(145deg,#ab9cff,#655ee8 56%,#3b36a8)}.dock-preview-item.trash{background:linear-gradient(145deg,#edf1f4,#9aa8b4 56%,#53626f)}.dock-icon-style{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:14px;border-bottom:1px solid var(--separator)}.dock-icon-style>div{display:grid;gap:4px}.dock-icon-style b{color:var(--text-primary);font-size:12px}.dock-icon-style small{color:var(--text-tertiary);font-size:10px}.custom-icon-settings{margin-top:22px}.custom-icon-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:12px}.custom-icon-card{display:grid;grid-template-columns:52px minmax(0,1fr);align-items:center;gap:9px;padding:10px;border:1px solid var(--separator);border-radius:12px;background:var(--bg-card)}.custom-icon-card>strong{overflow:hidden;color:var(--text-primary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.custom-icon-image{display:grid;width:52px;height:52px;grid-row:span 2;place-items:center;overflow:hidden;border-radius:12px;background:var(--bg-tertiary);color:var(--text-primary)}.custom-icon-image img{width:100%;height:100%;object-fit:contain}.custom-icon-image :deep(.dock-glyph){width:55%;height:55%}.custom-icon-actions{display:flex;align-items:center;gap:4px}
.dock-preview-item :deep(.dock-glyph){width:var(--preview-icon-size);height:var(--preview-icon-size)}.dock-preview-item.macos26{border:0;background:none!important;box-shadow:none;color:#253247;text-shadow:none}.dock-preview-item.custom :deep(.dock-glyph){width:var(--preview-icon-size);height:var(--preview-icon-size)}.dock-preview-item.tags{background:linear-gradient(145deg,#ff8dc7,#eb3d84 56%,#9e1c61)}.dock-preview-item.baby{background:linear-gradient(145deg,#67dfbd,#18a881 56%,#08715d)}.dock-preview-item.search{background:linear-gradient(145deg,#ab9cff,#655ee8 56%,#3b36a8)}.dock-preview-item.trash{background:linear-gradient(145deg,#edf1f4,#9aa8b4 56%,#53626f)}.dock-icon-style{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:14px;border-bottom:1px solid var(--separator)}.dock-icon-style>div{display:grid;gap:4px}.dock-icon-style b{color:var(--text-primary);font-size:12px}.dock-icon-style small{color:var(--text-tertiary);font-size:10px}.custom-icon-settings{margin-top:22px}.custom-icon-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:12px}.custom-icon-card{display:grid;grid-template-columns:52px minmax(0,1fr);align-items:center;gap:9px;padding:10px;border:1px solid var(--separator);border-radius:12px;background:var(--bg-card)}.custom-icon-card>strong{overflow:hidden;color:var(--text-primary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.custom-icon-image{display:grid;width:52px;height:52px;grid-row:span 2;place-items:center;overflow:hidden;border-radius:12px;background:var(--bg-tertiary);color:var(--text-primary)}.custom-icon-image img{width:100%;height:100%;object-fit:contain}.custom-icon-image :deep(.dock-glyph){width:55%;height:55%}.custom-icon-actions{display:flex;align-items:center;gap:4px}
@media (max-width:760px){.theme-nav-row{flex-direction:column}.theme-tabs{grid-template-columns:repeat(2,1fr)}.auto-save-status{min-height:40px}.style-grid{grid-template-columns:1fr}.color-layout,.background-layout,.dock-layout{grid-template-columns:1fr}.live-preview,.dock-stage{min-height:300px}.background-presets{grid-template-columns:repeat(2,1fr)}}

/* 与 aibook Theme Settings 保持同构的视觉规则 */
.theme-settings {
  width: 100%;
  overflow: hidden;
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  backdrop-filter: blur(var(--glass-blur, 20px));
  -webkit-backdrop-filter: blur(var(--glass-blur, 20px));
}

.theme-nav-row {
  display: block;
  margin: 0;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color-light);
  background: color-mix(in srgb, var(--surface-hover) 55%, transparent);
}

.theme-tabs {
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.theme-tabs :deep(.el-tabs__nav) {
  gap: 7px;
}

.theme-tabs :deep(.el-tabs__item) {
  height: 42px;
  padding: 0 18px !important;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--text-secondary);
  font-size: 14px;
  transition: color .18s ease, background .18s ease, border-color .18s ease, box-shadow .18s ease;
}

.theme-tabs :deep(.el-tabs__item:hover) {
  color: var(--text-primary);
  background: var(--surface-hover);
}

.theme-tabs :deep(.el-tabs__item.is-active) {
  border-color: var(--primary-alpha-20);
  background: var(--surface-elevated);
  color: var(--primary);
  box-shadow: 0 5px 18px var(--shadow-color);
}

.theme-tabs :deep(.el-tabs__active-bar),
.theme-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.element-tab-label {
  gap: 8px;
  font-weight: 650;
}

.theme-tab-icon {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 7px;
  background: var(--primary-alpha-10);
  color: var(--text-secondary);
  font-size: 15px;
  transition: color .18s ease, background .18s ease, transform .18s ease;
}

.theme-tabs :deep(.el-tabs__item.is-active) .theme-tab-icon {
  background: var(--primary-alpha-15);
  color: var(--primary);
  transform: scale(1.04);
}

.settings-section {
  min-height: 500px;
  padding: var(--spacing-xl);
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  animation: theme-pane-in .22s ease-out both;
}

@keyframes theme-pane-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-intro {
  margin-bottom: var(--spacing-lg);
}

.section-intro h2 {
  font-size: var(--font-size-lg);
}

.section-intro p {
  margin-top: 5px;
  font-size: var(--font-size-sm);
}

.current-badge {
  padding: 6px 11px;
  border-color: var(--primary-alpha-20);
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.style-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--spacing-lg);
}

.style-card {
  padding: 0;
  border: 2px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: transparent;
}

.style-card:hover {
  border-color: var(--primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.style-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-alpha-20);
}

.style-preview {
  height: 120px;
  border-radius: 0;
}

.style-copy {
  padding: var(--spacing-md);
}

.style-copy b {
  font-size: 14px;
}

.style-copy small {
  min-height: 34px;
  font-size: var(--font-size-xs);
}

.selected-check {
  top: var(--spacing-sm);
  right: var(--spacing-sm);
  bottom: auto;
  width: 24px;
  height: 24px;
  background: var(--primary);
}

.color-layout,
.background-layout,
.dock-layout {
  grid-template-columns: minmax(300px, .9fr) minmax(380px, 1.1fr);
  gap: var(--spacing-xl);
}

.live-preview,
.dock-stage {
  min-height: 330px;
  border-color: var(--primary-alpha-20);
  border-radius: 24px;
  box-shadow: inset 0 1px white, 0 18px 42px var(--primary-alpha-15);
}

.dock-stage {
  min-height: 300px;
}

.color-controls,
.surface-controls,
.dock-controls {
  align-content: center;
  gap: var(--spacing-xl);
}

.accent-presets {
  gap: 9px;
}

.accent-presets button {
  padding: 9px;
  border-color: var(--border-color-light);
  border-radius: 11px;
  background: var(--surface-card);
}

.accent-presets button:hover {
  border-color: var(--primary-alpha-30);
  background: var(--surface-hover);
  transform: translateY(-2px);
}

.accent-presets button.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
  box-shadow: 0 0 0 2px var(--primary-alpha-10);
}

.dock-icon-config {
  display: grid;
  gap: 13px;
  padding: 4px 4px 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.dock-icon-config-copy {
  display: grid;
  gap: 5px;
}

.dock-icon-config-copy b {
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.dock-icon-config-copy small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.dock-icon-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 10px;
}

.dock-icon-option {
  position: relative;
  display: grid;
  min-width: 0;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 15px;
  background: color-mix(in srgb, var(--surface-card) 82%, transparent);
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
}

.dock-icon-option:hover {
  border-color: var(--primary-alpha-30);
  background: var(--primary-alpha-10);
  transform: translateY(-1px);
}

.dock-icon-option.active {
  border-color: var(--primary);
  background: linear-gradient(145deg, var(--primary-alpha-10), color-mix(in srgb, var(--surface-elevated) 72%, transparent));
  box-shadow: inset 0 0 0 1px var(--primary-alpha-10);
}

.dock-icon-option-preview {
  display: flex;
  height: 36px;
  align-items: center;
  gap: 7px;
  color: var(--primary-dark);
}

.dock-icon-option-preview :deep(.dock-glyph) {
  width: 30px;
  height: 30px;
}

.dock-icon-option-preview :deep(.dock-glyph--minimal) {
  width: 21px;
  height: 21px;
  padding: 4px;
  border-radius: 8px;
  background: var(--primary-alpha-10);
}

.dock-icon-option-text {
  display: grid;
  gap: 2px;
}

.dock-icon-option-text strong {
  font-size: 13px;
}

.dock-icon-option-text small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-icon-option-check {
  position: absolute;
  top: 9px;
  right: 9px;
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  font-size: 11px;
  font-weight: 800;
  opacity: 0;
  transform: scale(.7);
  transition: opacity 160ms ease, transform 160ms ease;
}

.dock-icon-option.active .dock-icon-option-check {
  opacity: 1;
  transform: scale(1);
}

.dock-controls > .range-control {
  display: grid;
  grid-template-columns: minmax(180px, .92fr) minmax(150px, 1.08fr);
  align-items: center;
  gap: 4px var(--spacing-xl);
  padding: 16px 4px;
  border-top: 0;
  border-bottom: 1px solid var(--border-color-light);
}

.dock-controls > .range-control > span {
  grid-column: 1;
  grid-row: 1;
}

.dock-controls > .range-control > small {
  grid-column: 1;
  grid-row: 2;
  font-size: 12px;
  line-height: 1.5;
}

.dock-controls > .range-control > :deep(.el-slider) {
  grid-column: 2;
  grid-row: 1 / span 2;
}

.dock-controls > .range-control :deep(.el-slider__runway) {
  height: 5px;
  background: var(--primary-alpha-10);
}

.dock-controls > .range-control :deep(.el-slider__bar) {
  height: 5px;
  background: linear-gradient(90deg, var(--primary-light), var(--primary));
}

.dock-controls > .range-control :deep(.el-slider__button) {
  width: 18px;
  height: 18px;
  border: 3px solid white;
  background: var(--primary);
  box-shadow: 0 2px 8px var(--primary-alpha-30);
}

.custom-icon-settings {
  margin: 0 var(--spacing-xl) var(--spacing-xl);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.custom-icon-card {
  border-color: var(--border-color-light);
  border-radius: 13px;
  background: color-mix(in srgb, var(--surface-card) 76%, transparent);
}

@media (max-width: 1080px) {
  .color-layout,
  .background-layout,
  .dock-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .theme-nav-row {
    padding: 10px;
    overflow: hidden;
  }

  .theme-tabs :deep(.el-tabs__nav-scroll) {
    overflow-x: auto;
    scrollbar-width: none;
  }

  .theme-tabs :deep(.el-tabs__nav) {
    min-width: 520px;
  }

  .theme-tabs :deep(.el-tabs__item) {
    padding: 0 12px !important;
  }

  .settings-section {
    padding: var(--spacing-lg);
  }

  .style-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dock-controls > .range-control {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .dock-controls > .range-control > span,
  .dock-controls > .range-control > small,
  .dock-controls > .range-control > :deep(.el-slider) {
    grid-column: 1;
    grid-row: auto;
  }

  .dock-icon-options {
    grid-template-columns: 1fr;
  }
}
</style>

export type AppTheme = 'dark' | 'light' | 'macos26'

export interface BackgroundConfig {
  mode: 'solid' | 'gradient'
  pageColor: string
  secondaryColor: string
  navColor: string
  navOpacity: number
  surfaceColor: string
  surfaceOpacity: number
}

export interface DockConfig {
  opacity: number
  blurStrength: number
  iconSize: number
  maxScale: number
  animationSpeed: number
  iconStyle: 'minimal' | 'macos26' | 'custom'
}

export const DEFAULT_ACCENTS: Record<AppTheme, string> = {
  dark: '#0A84FF',
  light: '#007AFF',
  macos26: '#007AFF',
}

export const DEFAULT_BACKGROUNDS: Record<AppTheme, BackgroundConfig> = {
  dark: { mode: 'solid', pageColor: '#000000', secondaryColor: '#121827', navColor: '#1C1C1E', navOpacity: 72, surfaceColor: '#1C1C1E', surfaceOpacity: 100 },
  light: { mode: 'solid', pageColor: '#F2F2F7', secondaryColor: '#E7EEF7', navColor: '#FFFFFF', navOpacity: 72, surfaceColor: '#FFFFFF', surfaceOpacity: 100 },
  macos26: { mode: 'gradient', pageColor: '#DCEBFA', secondaryColor: '#F1E4F8', navColor: '#F8FBFF', navOpacity: 62, surfaceColor: '#FFFFFF', surfaceOpacity: 58 },
}

export const DEFAULT_DOCK: DockConfig = {
  opacity: 0.72,
  blurStrength: 20,
  iconSize: 26,
  maxScale: 1.5,
  animationSpeed: 0.25,
  iconStyle: 'macos26',
}

const ACCENT_KEY = 'themeAccentColors'
const BACKGROUND_KEY = 'themeBackgroundSettings'

const hexPattern = /^#[0-9a-f]{6}$/i

export function normalizeHex(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const color = value.trim().toUpperCase()
  return hexPattern.test(color) ? color : null
}

function parseRecord<T>(key: string): Partial<Record<AppTheme, T>> {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '{}')
    return value && typeof value === 'object' ? value : {}
  }
  catch { return {} }
}

function rgb(hex: string) {
  const value = normalizeHex(hex) || '#FFFFFF'
  return {
    r: Number.parseInt(value.slice(1, 3), 16),
    g: Number.parseInt(value.slice(3, 5), 16),
    b: Number.parseInt(value.slice(5, 7), 16),
  }
}

function rgba(hex: string, opacity: number) {
  const value = rgb(hex)
  return `rgba(${value.r}, ${value.g}, ${value.b}, ${Math.max(0, Math.min(100, opacity)) / 100})`
}

function mix(hex: string, target: '#000000' | '#FFFFFF', amount: number) {
  const source = rgb(hex)
  const destination = rgb(target)
  const channel = (from: number, to: number) => Math.round(from + (to - from) * amount)
  return `rgb(${channel(source.r, destination.r)}, ${channel(source.g, destination.g)}, ${channel(source.b, destination.b)})`
}

function isDark(hex: string) {
  const value = rgb(hex)
  return (value.r * 299 + value.g * 587 + value.b * 114) / 1000 < 145
}

export function getAccent(theme: AppTheme) {
  return normalizeHex(parseRecord<string>(ACCENT_KEY)[theme]) || DEFAULT_ACCENTS[theme]
}

export function setAccent(theme: AppTheme, color: string) {
  const normalized = normalizeHex(color)
  if (!normalized) return
  const values = parseRecord<string>(ACCENT_KEY)
  values[theme] = normalized
  localStorage.setItem(ACCENT_KEY, JSON.stringify(values))
  applyAccent(normalized)
}

export function resetAccent(theme: AppTheme) {
  const values = parseRecord<string>(ACCENT_KEY)
  delete values[theme]
  localStorage.setItem(ACCENT_KEY, JSON.stringify(values))
  applyAccent(DEFAULT_ACCENTS[theme])
}

export function getBackground(theme: AppTheme): BackgroundConfig {
  const fallback = DEFAULT_BACKGROUNDS[theme]
  const stored = parseRecord<Partial<BackgroundConfig>>(BACKGROUND_KEY)[theme] || {}
  return {
    mode: stored.mode === 'gradient' ? 'gradient' : stored.mode === 'solid' ? 'solid' : fallback.mode,
    pageColor: normalizeHex(stored.pageColor) || fallback.pageColor,
    secondaryColor: normalizeHex(stored.secondaryColor) || fallback.secondaryColor,
    navColor: normalizeHex(stored.navColor) || fallback.navColor,
    navOpacity: typeof stored.navOpacity === 'number' ? Math.min(100, Math.max(20, stored.navOpacity)) : fallback.navOpacity,
    surfaceColor: normalizeHex(stored.surfaceColor) || fallback.surfaceColor,
    surfaceOpacity: typeof stored.surfaceOpacity === 'number' ? Math.min(100, Math.max(35, stored.surfaceOpacity)) : fallback.surfaceOpacity,
  }
}

export function setBackground(theme: AppTheme, config: BackgroundConfig) {
  const values = parseRecord<BackgroundConfig>(BACKGROUND_KEY)
  values[theme] = config
  localStorage.setItem(BACKGROUND_KEY, JSON.stringify(values))
  applyBackground(config)
}

export function resetBackground(theme: AppTheme) {
  const values = parseRecord<BackgroundConfig>(BACKGROUND_KEY)
  delete values[theme]
  localStorage.setItem(BACKGROUND_KEY, JSON.stringify(values))
  applyBackground(DEFAULT_BACKGROUNDS[theme])
}

export function applyAccent(color: string) {
  const root = document.documentElement
  root.style.setProperty('--accent', color)
  root.style.setProperty('--accent-hover', mix(color, '#FFFFFF', 0.2))
  root.style.setProperty('--accent-soft', rgba(color, 12))
  root.style.setProperty('--accent-border', rgba(color, 32))
  root.style.setProperty('--el-color-primary', color)
  root.style.setProperty('--el-color-primary-light-3', mix(color, '#FFFFFF', 0.3))
  root.style.setProperty('--el-color-primary-light-5', mix(color, '#FFFFFF', 0.5))
  root.style.setProperty('--el-color-primary-light-7', mix(color, '#FFFFFF', 0.7))
  root.style.setProperty('--el-color-primary-light-9', mix(color, '#FFFFFF', 0.9))
  root.style.setProperty('--el-color-primary-dark-2', mix(color, '#000000', 0.2))
}

export function backgroundStyle(config: BackgroundConfig) {
  return config.mode === 'gradient'
    ? `linear-gradient(135deg, ${config.pageColor} 0%, ${config.secondaryColor} 100%)`
    : config.pageColor
}

export function applyBackground(config: BackgroundConfig) {
  const root = document.documentElement
  const surfaceDark = isDark(config.surfaceColor)
  root.style.setProperty('--page-background', backgroundStyle(config))
  root.style.setProperty('--bg-primary', config.pageColor)
  root.style.setProperty('--bg-secondary', rgba(config.surfaceColor, config.surfaceOpacity))
  root.style.setProperty('--bg-card', rgba(config.surfaceColor, config.surfaceOpacity))
  root.style.setProperty('--bg-tertiary', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.12 : 0.08))
  root.style.setProperty('--surface-background', rgba(config.surfaceColor, config.surfaceOpacity))
  root.style.setProperty('--nav-background', rgba(config.navColor, config.navOpacity))
  root.style.setProperty('--glass-bg', rgba(config.surfaceColor, Math.max(35, config.surfaceOpacity - 10)))
  root.style.setProperty('--glass-border', rgba(surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 12 : 8))
  root.style.setProperty('--text-primary', surfaceDark ? '#FFFFFF' : '#172235')
  root.style.setProperty('--text-secondary', surfaceDark ? 'rgba(255,255,255,.64)' : 'rgba(23,34,53,.7)')
  root.style.setProperty('--text-tertiary', surfaceDark ? 'rgba(255,255,255,.4)' : 'rgba(23,34,53,.46)')
  root.style.setProperty('--el-bg-color', rgba(config.surfaceColor, config.surfaceOpacity))
  root.style.setProperty('--el-bg-color-overlay', rgba(config.surfaceColor, Math.min(100, config.surfaceOpacity + 8)))
  root.style.setProperty('--el-fill-color-blank', rgba(config.surfaceColor, config.surfaceOpacity))
  root.style.setProperty('--el-fill-color-light', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.1 : 0.04))
  root.style.setProperty('--el-fill-color-lighter', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.07 : 0.025))
  root.style.setProperty('--el-fill-color-extra-light', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.045 : 0.015))
  root.style.setProperty('--el-fill-color-dark', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.16 : 0.08))
  root.style.setProperty('--el-border-color', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.2 : 0.12))
  root.style.setProperty('--el-border-color-light', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.14 : 0.08))
  root.style.setProperty('--el-border-color-lighter', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.1 : 0.05))
  root.style.setProperty('--el-text-color-primary', surfaceDark ? '#FFFFFF' : '#172235')
  root.style.setProperty('--el-text-color-regular', surfaceDark ? 'rgba(255,255,255,.68)' : 'rgba(23,34,53,.7)')
  root.style.setProperty('--el-text-color-secondary', surfaceDark ? 'rgba(255,255,255,.52)' : 'rgba(23,34,53,.56)')
  root.style.setProperty('--el-text-color-placeholder', surfaceDark ? 'rgba(255,255,255,.36)' : 'rgba(23,34,53,.4)')
  root.style.setProperty('--el-disabled-bg-color', mix(config.surfaceColor, surfaceDark ? '#FFFFFF' : '#000000', surfaceDark ? 0.06 : 0.03))
  root.style.setProperty('--el-disabled-text-color', surfaceDark ? 'rgba(255,255,255,.32)' : 'rgba(23,34,53,.34)')
}

export function applyThemeAppearance(theme: AppTheme) {
  applyAccent(getAccent(theme))
  applyBackground(getBackground(theme))
}

export function loadDockConfig(): DockConfig {
  try {
    const value = JSON.parse(localStorage.getItem('dockConfig') || '{}')
    return value && typeof value === 'object' ? { ...DEFAULT_DOCK, ...value } : { ...DEFAULT_DOCK }
  }
  catch { return { ...DEFAULT_DOCK } }
}

export function setDockConfig(config: DockConfig, notify = true) {
  localStorage.setItem('dockConfig', JSON.stringify(config))
  if (notify) {
    window.dispatchEvent(new CustomEvent<DockConfig>('dock-config-updated', { detail: config }))
  }
}

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, provide } from 'vue'
import { ElMessage } from 'element-plus'
import { applyThemeAppearance, type AppTheme, type DockConfig } from '@/utils/themeAppearance'
import { userApi } from '@/api/userApi'
import type { User } from '@/types'

type Theme = AppTheme

const storedTheme = localStorage.getItem('theme')
const validThemes: Theme[] = ['dark', 'light', 'macos26']
const sessionTheme = (() => {
  try {
    const value = JSON.parse(sessionStorage.getItem('user') || 'null')?.theme
    return validThemes.includes(value) ? value as Theme : null
  } catch { return null }
})()
const initialTheme: Theme = sessionTheme || (storedTheme === 'liquid-glass'
  ? 'macos26'
  : validThemes.includes(storedTheme as Theme) ? storedTheme as Theme : 'dark')
const theme = ref<Theme>(initialTheme)
let themeSyncQueue = Promise.resolve()
let dockSyncTimer: ReturnType<typeof setTimeout> | undefined

document.documentElement.dataset.theme = initialTheme
applyThemeAppearance(initialTheme)

onMounted(() => {
  document.documentElement.dataset.theme = theme.value
  applyThemeAppearance(theme.value)
  window.addEventListener('dock-config-updated', onDockConfigUpdated)
})

onUnmounted(() => {
  window.removeEventListener('dock-config-updated', onDockConfigUpdated)
  if (dockSyncTimer) clearTimeout(dockSyncTimer)
})

function updateSessionUser(updates: Partial<User>) {
  try {
    const storedUser = JSON.parse(sessionStorage.getItem('user') || 'null') as User | null
    if (storedUser) sessionStorage.setItem('user', JSON.stringify({ ...storedUser, ...updates }))
  } catch { /* ignore invalid legacy session data */ }
}

function onDockConfigUpdated(event: Event) {
  const config = (event as CustomEvent<DockConfig>).detail
  updateSessionUser({ dockConfig: config })
  if (dockSyncTimer) clearTimeout(dockSyncTimer)
  const token = localStorage.getItem('token')
  if (!token) return
  dockSyncTimer = setTimeout(() => {
    if (localStorage.getItem('token') !== token) return
    void userApi.updateDockConfig(config).then(({ data }) => {
      if (localStorage.getItem('token') === token) updateSessionUser({ dockConfig: data.dockConfig })
    }).catch(() => {
      ElMessage.warning('Dock 配置已在当前设备生效，但同步到账号失败')
    })
  }, 400)
}

watch(theme, (val) => {
  document.documentElement.dataset.theme = val
  localStorage.setItem('theme', val)
  applyThemeAppearance(val)

  updateSessionUser({ theme: val })

  const token = localStorage.getItem('token')
  if (token) {
    themeSyncQueue = themeSyncQueue.then(async () => {
      if (localStorage.getItem('token') !== token) return
      try {
        const { data } = await userApi.updateTheme(val)
        if (localStorage.getItem('token') === token) {
          updateSessionUser({ theme: data.theme })
        }
      } catch {
        ElMessage.warning('主题已在当前设备生效，但同步到账号失败')
      }
    })
  }
})

function setTheme(t: Theme) {
  theme.value = t
}

function toggleTheme() {
  const idx = validThemes.indexOf(theme.value)
  theme.value = validThemes[(idx + 1) % validThemes.length]
}

provide('theme', theme)
provide('setTheme', setTheme)
provide('toggleTheme', toggleTheme)

</script>

<template>
  <router-view />
</template>

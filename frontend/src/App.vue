<script setup lang="ts">
import { ref, watch, onMounted, provide } from 'vue'
import { ElMessage } from 'element-plus'
import { applyThemeAppearance, type AppTheme } from '@/utils/themeAppearance'
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

document.documentElement.dataset.theme = initialTheme
applyThemeAppearance(initialTheme)

onMounted(() => {
  document.documentElement.dataset.theme = theme.value
  applyThemeAppearance(theme.value)
})

watch(theme, (val) => {
  document.documentElement.dataset.theme = val
  localStorage.setItem('theme', val)
  applyThemeAppearance(val)

  try {
    const storedUser = JSON.parse(sessionStorage.getItem('user') || 'null') as User | null
    if (storedUser) {
      sessionStorage.setItem('user', JSON.stringify({ ...storedUser, theme: val }))
    }
  } catch { /* ignore invalid legacy session data */ }

  const token = localStorage.getItem('token')
  if (token) {
    themeSyncQueue = themeSyncQueue.then(async () => {
      if (localStorage.getItem('token') !== token) return
      try {
        const { data } = await userApi.updateTheme(val)
        if (localStorage.getItem('token') === token) {
          sessionStorage.setItem('user', JSON.stringify(data))
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

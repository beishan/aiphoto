<script setup lang="ts">
import { ref, computed, watch, onMounted, provide } from 'vue'
import { NConfigProvider, NMessageProvider, NDialogProvider, darkTheme } from 'naive-ui'
import { applyThemeAppearance, type AppTheme } from '@/utils/themeAppearance'

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

const themeOverrides = {
  common: {
    primaryColor: 'var(--accent)',
    primaryColorHover: 'var(--accent-hover)',
    primaryColorPressed: 'var(--accent)',
    primaryColorSuppl: 'var(--accent-hover)',
    borderRadius: '12px',
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif',
  },
}

const naiveTheme = computed(() => theme.value === 'dark' ? darkTheme : undefined)
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <router-view />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

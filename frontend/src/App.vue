<script setup lang="ts">
import { ref, computed, watch, onMounted, provide } from 'vue'
import { NConfigProvider, NMessageProvider, NDialogProvider, darkTheme } from 'naive-ui'

type Theme = 'dark' | 'light' | 'macos26'

const storedTheme = localStorage.getItem('theme')
const theme = ref<Theme>(storedTheme === 'liquid-glass' ? 'macos26' : (storedTheme as Theme) || 'dark')

onMounted(() => {
  document.documentElement.dataset.theme = theme.value
})

watch(theme, (val) => {
  document.documentElement.dataset.theme = val
  localStorage.setItem('theme', val)
})

function setTheme(t: Theme) {
  theme.value = t
}

function toggleTheme() {
  const themes: Theme[] = ['dark', 'light', 'macos26']
  const idx = themes.indexOf(theme.value)
  theme.value = themes[(idx + 1) % themes.length]
}

provide('theme', theme)
provide('setTheme', setTheme)
provide('toggleTheme', toggleTheme)

const themeOverrides = {
  common: {
    primaryColor: '#0a84ff',
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

<script setup lang="ts">
import { computed, inject, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTaskStore } from '@/stores/taskStore'
import TaskProgress from '@/components/TaskProgress.vue'

const router = useRouter()
const route = useRoute()
const taskStore = useTaskStore()

const theme = inject<Ref<string>>('theme')!
const toggleTheme = inject<() => void>('toggleTheme')!

const tabs = [
  { path: '/', name: 'Gallery', label: '照片', icon: 'photo', activeIcon: 'photo.fill' },
  { path: '/albums', name: 'Albums', label: '相册', icon: 'rectangle.stack', activeIcon: 'rectangle.stack.fill' },
  { path: '/people', name: 'People', label: '人物', icon: 'person.crop.circle', activeIcon: 'person.crop.circle.fill' },
  { path: '/search', name: 'Search', label: '搜索', icon: 'magnifyingglass', activeIcon: 'magnifyingglass' },
  { path: '/more', name: 'More', label: '更多', icon: 'ellipsis.circle', activeIcon: 'ellipsis.circle.fill' },
]

const activeTab = computed(() => {
  const path = route.path
  if (path === '/') return '/'
  const tab = tabs.find(t => t.path !== '/' && path.startsWith(t.path))
  return tab?.path || '/'
})

const pageTitle = computed(() => {
  const path = route.path
  if (path === '/') return '照片'
  if (path.startsWith('/albums')) return '相册'
  if (path === '/people') return '人物'
  if (path === '/search') return '搜索'
  if (path === '/more') return '更多'
  return 'MemoryVault'
})

function navigateTo(path: string) {
  router.push(path)
}

// SF Symbols as SVG paths
const sfIcons: Record<string, string> = {
  'photo': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z',
  'photo.fill': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm7.5 2a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM2 15l4.5-6 3.5 4.5L12 11l6 8H2z',
  'rectangle.stack': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'rectangle.stack.fill': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'person.crop.circle': 'M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2a7.2 7.2 0 01-6-3.22c.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08a7.2 7.2 0 01-6 3.22z',
  'person.crop.circle.fill': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3.5a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2c-2.03 0-3.8-.81-5.11-2.12C7.02 16.19 9.5 15.2 12 15.2s4.98.99 5.11 2.38A7.96 7.96 0 0112 19.7z',
  'magnifyingglass': 'M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z',
  'ellipsis.circle': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14h-2v-2h2v2zm0-4h-2V6h2v6zm4 4h-2v-2h2v2zm0-4h-2V6h2v6z',
  'ellipsis.circle.fill': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14h-2v-2h2v2zm0-4h-2V6h2v6zm4 4h-2v-2h2v2zm0-4h-2V6h2v6z',
}
</script>

<template>
  <div class="app-shell">
    <!-- Top bar -->
    <header class="top-bar glass">
      <div class="top-bar-left"></div>
      <h1 class="page-title">{{ pageTitle }}</h1>
      <button class="theme-toggle" @click="toggleTheme" :title="theme === 'dark' ? '切换亮色' : '切换暗色'">
        <!-- Sun icon for light mode -->
        <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M12 7a5 5 0 100 10 5 5 0 000-10zm0-3a1 1 0 01-1-1V1a1 1 0 112 0v2a1 1 0 01-1 1zm0 18a1 1 0 01-1-1v-2a1 1 0 112 0v2a1 1 0 01-1 1zm9-9a1 1 0 01-1 1h-2a1 1 0 110-2h2a1 1 0 011 1zM6 13H4a1 1 0 110-2h2a1 1 0 010 2zm12.07-6.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zM4.93 19.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zm14.14 0a1 1 0 01-1.41 0l-1.42-1.42a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42zM4.93 4.93a1 1 0 01-1.42 0L2.1 3.51a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42z" />
        </svg>
        <!-- Moon icon for dark mode -->
        <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M21.64 13a1 1 0 00-1.05-.14 8.05 8.05 0 01-3.37.73 8.15 8.15 0 01-8.14-8.14 8.59 8.59 0 01.25-2A1 1 0 008 2.36a10.14 10.14 0 1014 11 1 1 0 00-.36-.64z" />
        </svg>
      </button>
    </header>

    <!-- Main content -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- Bottom tab bar -->
    <nav class="tab-bar glass">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: activeTab === tab.path }"
        @click="navigateTo(tab.path)"
      >
        <svg class="tab-icon" viewBox="0 0 24 24" fill="currentColor">
          <path :d="activeTab === tab.path ? sfIcons[tab.activeIcon] : sfIcons[tab.icon]" />
        </svg>
        <span class="tab-label">{{ tab.label }}</span>
      </button>
    </nav>

    <!-- Task progress overlay -->
    <TaskProgress v-if="taskStore.activeTasks.length > 0" />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-primary);
}

.top-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--top-bar-height);
  padding: 0 16px;
  border-bottom: 0.5px solid var(--glass-border);
}

.top-bar-left {
  width: 36px;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  transition: color 0.2s, background 0.2s;
}

.theme-toggle:hover {
  color: var(--text-primary);
  background: var(--bg-tertiary);
}

.main-content {
  flex: 1;
  padding-bottom: var(--tab-height);
  overflow-y: auto;
}

.tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: flex-start;
  justify-content: space-around;
  height: var(--tab-height);
  padding-bottom: var(--safe-bottom);
  border-top: 0.5px solid var(--glass-border);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 8px 0;
  min-width: 64px;
  color: var(--text-tertiary);
  transition: color 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}

.tab-item.active {
  color: var(--accent);
}

.tab-icon {
  width: 24px;
  height: 24px;
}

.tab-label {
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.01em;
}
</style>

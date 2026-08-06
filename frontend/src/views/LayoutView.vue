<script setup lang="ts">
import { computed, inject, ref, watch, nextTick, onMounted, onUnmounted, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import TaskFloat from '@/components/TaskFloat.vue'
import { authApi } from '@/api/authApi'
import type { User } from '@/types'

const router = useRouter()
const route = useRoute()

const theme = inject<Ref<string>>('theme')!
const toggleTheme = inject<() => void>('toggleTheme')!

const tabs = [
  { path: '/', name: 'Gallery', label: '照片', icon: 'photo', activeIcon: 'photo.fill' },
  { path: '/albums', name: 'Albums', label: '相册', icon: 'rectangle.stack', activeIcon: 'rectangle.stack.fill' },
  { path: '/timeline', name: 'Timeline', label: '时间线', icon: 'timeline', activeIcon: 'timeline.fill' },
  { path: '/baby', name: 'Baby', label: '宝宝', icon: 'baby', activeIcon: 'baby.fill' },
  { path: '/categories', name: 'Categories', label: '分类', icon: 'square.grid.2x2', activeIcon: 'square.grid.2x2.fill' },
  { path: '/people', name: 'People', label: '人物', icon: 'person.crop.circle', activeIcon: 'person.crop.circle.fill' },
  { path: '/search', name: 'Search', label: '搜索', icon: 'magnifyingglass', activeIcon: 'magnifyingglass' },
  { path: '/favorites', name: 'Favorites', label: '喜欢', icon: 'heart', activeIcon: 'heart.fill' },
  { path: '/dedup', name: 'Dedup', label: '去重', icon: 'dedup', activeIcon: 'dedup.fill' },
  { path: '/settings', name: 'Settings', label: '设置', icon: 'settings', activeIcon: 'settings.fill' },
]

// Tab-level routes (sub-pages should hide the tab bar)
const tabPaths = tabs.map(t => t.path)

const isTabRoute = computed(() => {
  const path = route.path
  if (path === '/') return true
  return tabPaths.some(p => p !== '/' && path === p)
})

const activeTab = computed(() => {
  const path = route.path
  if (path === '/') return '/'
  const tab = tabs.find(t => t.path !== '/' && path.startsWith(t.path))
  return tab?.path || '/'
})

const activeTabIndex = computed(() => tabs.findIndex(t => t.path === activeTab.value))

// Tab bar indicator position
const tabBarRef = ref<HTMLElement | null>(null)
const indicatorLeft = ref(0)
const indicatorWidth = ref(0)

function updateIndicator() {
  if (!tabBarRef.value) return
  const items = tabBarRef.value.querySelectorAll<HTMLElement>('.tab-item')
  const idx = activeTabIndex.value
  if (idx >= 0 && items[idx]) {
    const item = items[idx]
    const bar = tabBarRef.value
    const barRect = bar.getBoundingClientRect()
    const itemRect = item.getBoundingClientRect()
    indicatorLeft.value = itemRect.left - barRect.left
    indicatorWidth.value = itemRect.width
  }
}

watch(activeTab, () => nextTick(updateIndicator), { immediate: true })

// Also update on theme change (liquid glass changes tab bar dimensions)
watch(theme, () => nextTick(updateIndicator))

const pageTitle = computed(() => {
  const path = route.path
  if (path === '/') return '照片'
  if (path.startsWith('/albums')) return '相册'
  if (path === '/timeline') return '时间线'
  if (path === '/baby') return '宝宝相册'
  if (path.startsWith('/categories')) return '分类'
  if (path.startsWith('/people')) return '人物'
  if (path === '/search') return '搜索'
  if (path === '/favorites') return '喜欢'
  if (path === '/dedup') return '去重检测'
  if (path === '/settings') return '设置'
  if (path === '/more') return '设置'
  if (path === '/folders') return '文件夹'
  return 'MemoryVault'
})

function navigateTo(path: string) {
  router.push(path)
}

// ===== 用户头像菜单 =====
const currentUser = ref<User | null>(null)
const showUserMenu = ref(false)

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    const stored = sessionStorage.getItem('user')
    if (stored) {
      currentUser.value = JSON.parse(stored)
    }
  } catch {
    // ignore
  }
})

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function closeUserMenu() {
  showUserMenu.value = false
}

function handleLogout() {
  localStorage.removeItem('token')
  sessionStorage.removeItem('user')
  closeUserMenu()
  router.push('/login')
}

// 点击外部关闭菜单
function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.user-avatar-btn') && !target.closest('.user-popup-menu')) {
    closeUserMenu()
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))

const themeTitle = computed(() => {
  const map: Record<string, string> = {
    'dark': '切换亮色主题',
    'light': '切换 Liquid Glass 主题',
    'liquid-glass': '切换暗色主题',
  }
  return map[theme.value] || '切换主题'
})

// SF Symbols as SVG paths
const sfIcons: Record<string, string> = {
  'photo': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z',
  'photo.fill': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm7.5 2a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM2 15l4.5-6 3.5 4.5L12 11l6 8H2z',
  'rectangle.stack': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'rectangle.stack.fill': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'person.crop.circle': 'M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2a7.2 7.2 0 01-6-3.22c.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08a7.2 7.2 0 01-6 3.22z',
  'person.crop.circle.fill': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3.5a3.5 3.5 0 110 7 3.5 3.5 0 010-7zm0 14.2c-2.03 0-3.8-.81-5.11-2.12C7.02 16.19 9.5 15.2 12 15.2s4.98.99 5.11 2.38A7.96 7.96 0 0112 19.7z',
  'magnifyingglass': 'M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z',
  'heart': 'M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z',
  'heart.fill': 'M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z',
  'square.grid.2x2': 'M3 3h8v8H3V3zm0 10h8v8H3v-8zM13 3h8v8h-8V3zm0 10h8v8h-8v-8z',
  'square.grid.2x2.fill': 'M3 3h8v8H3V3zm0 10h8v8H3v-8zM13 3h8v8h-8V3zm0 10h8v8h-8v-8z',
  'timeline': 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z',
  'timeline.fill': 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 13l-4-4 1.41-1.41L12 13.17l4.59-4.58L18 10l-6 6z',
  'baby': 'M12 2a10 10 0 100 20 10 10 0 000-20zM8.5 8a1.5 1.5 0 110 3 1.5 1.5 0 010-3zm7 0a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM12 17.5c-2.33 0-4.31-1.46-5.11-3.5h10.22c-.8 2.04-2.78 3.5-5.11 3.5z',
  'baby.fill': 'M12 2a10 10 0 100 20 10 10 0 000-20zM8.5 8a1.5 1.5 0 110 3 1.5 1.5 0 010-3zm7 0a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM12 17.5c-2.33 0-4.31-1.46-5.11-3.5h10.22c-.8 2.04-2.78 3.5-5.11 3.5z',
  'dedup': 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  'dedup.fill': 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  'settings': 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  'settings.fill': 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
}
</script>

<template>
  <div class="app-shell">
    <!-- Top bar -->
    <header class="top-bar glass">
      <div class="top-bar-left">
        <TaskFloat />
      </div>
      <h1 class="page-title">{{ pageTitle }}</h1>
      <button class="theme-toggle" @click="toggleTheme" :title="themeTitle">
        <!-- Sun icon for dark mode -> light -->
        <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M12 7a5 5 0 100 10 5 5 0 000-10zm0-3a1 1 0 01-1-1V1a1 1 0 112 0v2a1 1 0 01-1 1zm0 18a1 1 0 01-1-1v-2a1 1 0 112 0v2a1 1 0 01-1 1zm9-9a1 1 0 01-1 1h-2a1 1 0 110-2h2a1 1 0 011 1zM6 13H4a1 1 0 110-2h2a1 1 0 010 2zm12.07-6.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zM4.93 19.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zm14.14 0a1 1 0 01-1.41 0l-1.42-1.42a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42zM4.93 4.93a1 1 0 01-1.42 0L2.1 3.51a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42z" />
        </svg>
        <!-- Moon icon for light mode -> liquid-glass -->
        <svg v-else-if="theme === 'light'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M21.64 13a1 1 0 00-1.05-.14 8.05 8.05 0 01-3.37.73 8.15 8.15 0 01-8.14-8.14 8.59 8.59 0 01.25-2A1 1 0 008 2.36a10.14 10.14 0 1014 11 1 1 0 00-.36-.64z" />
        </svg>
        <!-- Diamond icon for liquid-glass -> dark -->
        <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M12 2L2 9l10 13L22 9 12 2zm0 2.8L19.5 9 12 19.2 4.5 9 12 4.8z" />
        </svg>
      </button>
    </header>

    <!-- Main content -->
    <main class="main-content" :class="{ 'has-tab-bar': isTabRoute }">
      <router-view v-slot="{ Component }">
        <transition name="slide-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- Bottom tab bar -->
    <nav v-if="isTabRoute" ref="tabBarRef" class="tab-bar glass">
      <!-- Sliding indicator (liquid-glass theme only) -->
      <div
        class="tab-indicator"
        :style="{ left: indicatorLeft + 'px', width: indicatorWidth + 'px' }"
      ></div>
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

      <!-- 用户头像按钮 -->
      <div class="user-avatar-btn" @click.stop="toggleUserMenu">
        <div class="avatar-circle">
          <img v-if="currentUser?.avatar" :src="currentUser.avatar" class="avatar-img" />
          <span v-else class="avatar-text">{{ (currentUser?.username || '?').charAt(0).toUpperCase() }}</span>
        </div>
      </div>
    </nav>

    <!-- 用户弹出菜单 -->
    <Transition name="popup">
      <div v-if="showUserMenu" class="user-popup-menu glass">
        <div class="popup-header">
          <div class="popup-avatar">
            <img v-if="currentUser?.avatar" :src="currentUser.avatar" class="popup-avatar-img" />
            <span v-else class="popup-avatar-text">{{ (currentUser?.username || '?').charAt(0).toUpperCase() }}</span>
          </div>
          <div class="popup-user-info">
            <span class="popup-username">{{ currentUser?.username || '未登录' }}</span>
            <span class="popup-role">{{ currentUser?.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
          </div>
        </div>
        <div class="popup-divider"></div>
        <button class="popup-item" @click="navigateTo('/settings'); closeUserMenu()">
          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
            <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
          </svg>
          <span>设置</span>
        </button>
        <button class="popup-item danger" @click="handleLogout">
          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
            <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z" />
          </svg>
          <span>退出登录</span>
        </button>
      </div>
    </Transition>

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
  display: flex;
  align-items: center;
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
  overflow-y: auto;
}

.main-content.has-tab-bar {
  padding-bottom: var(--tab-content-padding);
}

.tab-bar {
  position: fixed;
  bottom: var(--tab-bar-bottom);
  left: var(--tab-bar-left);
  right: var(--tab-bar-right);
  width: var(--tab-bar-width);
  max-width: var(--tab-bar-max-width);
  transform: var(--tab-bar-translate);
  z-index: 100;
  display: flex;
  align-items: flex-start;
  justify-content: space-around;
  height: var(--tab-bar-height);
  border-radius: var(--tab-bar-radius);
  box-shadow: var(--tab-bar-shadow);
  border: 0.5px solid var(--glass-border);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 8px 0;
  flex: 1;
  min-width: 0;
  color: var(--text-tertiary);
  transition: color 0.15s ease;
  -webkit-tap-highlight-color: transparent;
  position: relative;
  z-index: 1;
}

/* ===== 用户头像按钮 ===== */
.user-avatar-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 6px;
  cursor: pointer;
  position: relative;
  z-index: 1;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2px solid var(--glass-border);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-text {
  color: white;
  font-size: 14px;
  font-weight: 600;
}

/* ===== 弹出菜单 ===== */
.user-popup-menu {
  position: fixed;
  bottom: calc(var(--tab-bar-bottom) + var(--tab-bar-height) + 12px);
  right: 12px;
  width: 220px;
  border-radius: 14px;
  padding: 8px;
  z-index: 200;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
  border: 0.5px solid var(--glass-border);
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
}

.popup-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.popup-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.popup-avatar-text {
  color: white;
  font-size: 16px;
  font-weight: 600;
}

.popup-user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.popup-username {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.popup-role {
  font-size: 12px;
  color: var(--text-secondary);
}

.popup-divider {
  height: 0.5px;
  background: var(--separator);
  margin: 4px 0;
}

.popup-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 8px;
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s;
}

.popup-item:hover {
  background: var(--bg-tertiary);
}

.popup-item.danger {
  color: var(--danger);
}

.popup-item.danger:hover {
  background: rgba(255, 69, 58, 0.1);
}

/* 弹出动画 */
.popup-enter-active {
  transition: transform 0.25s cubic-bezier(0.32, 0.72, 0, 1), opacity 0.2s ease;
}
.popup-leave-active {
  transition: transform 0.18s ease, opacity 0.15s ease;
}
.popup-enter-from {
  transform: translateY(12px) scale(0.95);
  opacity: 0;
}
.popup-leave-to {
  transform: translateY(8px) scale(0.97);
  opacity: 0;
}

.tab-item.active {
  color: var(--accent);
}

.tab-icon {
  width: 22px;
  height: 22px;
}

.tab-label {
  font-size: 9px;
  font-weight: 500;
  letter-spacing: 0;
  white-space: nowrap;
}

/* Sliding indicator - hidden by default, shown in liquid-glass theme */
.tab-indicator {
  display: none;
  position: absolute;
  bottom: 8px;
  left: 0;
  height: 48px;
  border-radius: 16px;
  pointer-events: none;
  z-index: 0;
}
</style>

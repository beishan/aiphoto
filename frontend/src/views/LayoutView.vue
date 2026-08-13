<script setup lang="ts">
import { computed, inject, ref, watch, nextTick, onMounted, onUnmounted, type CSSProperties, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import TaskFloat from '@/components/TaskFloat.vue'
import type { User } from '@/types'
import { loadDockConfig, type DockConfig } from '@/utils/themeAppearance'
import DockIcon, { type DockIconName } from '@/components/DockIcon.vue'
import RecycleBinPanel from '@/components/RecycleBinPanel.vue'
import { useDockIconStore } from '@/stores/dockIconStore'
import { photoApi } from '@/api/photoApi'

const router = useRouter()
const route = useRoute()

const theme = inject<Ref<string>>('theme')!
const toggleTheme = inject<() => void>('toggleTheme')!
const dockIconStore = useDockIconStore()

// ===== Dock Configuration =====
const dockConfig = ref(loadDockConfig())

// Load dock config from localStorage
onMounted(() => {
  dockConfig.value = loadDockConfig()
})

function onDockConfigUpdated(event: Event) {
  dockConfig.value = { ...dockConfig.value, ...(event as CustomEvent<DockConfig>).detail }
}

const tabs: Array<{ path: string; name: string; label: string; dockIcon: DockIconName }> = [
  { path: '/', name: 'Gallery', label: '照片', dockIcon: 'photo' },
  { path: '/timeline', name: 'Timeline', label: '时间线', dockIcon: 'timeline' },
  { path: '/tags', name: 'Tags', label: '标签', dockIcon: 'tags' },
  { path: '/albums', name: 'Albums', label: '相册', dockIcon: 'albums' },
  { path: '/baby', name: 'Baby', label: '宝宝', dockIcon: 'baby' },
  { path: '/search', name: 'Search', label: '搜索', dockIcon: 'search' },
  { path: '/settings', name: 'Settings', label: '设置', dockIcon: 'settings' },
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

const pageTitle = computed(() => {
  const path = route.path
  if (path === '/') return '照片'
  if (path === '/timeline') return '时间线'
  if (path.startsWith('/tags')) return '标签'
  if (path.startsWith('/albums')) return '相册'
  if (path === '/baby') return '宝宝相册'
  if (path.startsWith('/categories')) return '分类'
  if (path.startsWith('/people')) return '人物'
  if (path === '/search') return '搜索'
  if (path === '/favorites') return '喜欢'
  if (path === '/dedup') return '去重检测'
  if (path === '/settings') return '设置'
  if (path === '/folders') return '文件夹'
  return 'MemoryVault'
})

function navigateTo(path: string) {
  router.push(path)
}

function closeDockPopups() {
  closeUserMenu()
  showTrashMenu.value = false
}

// ===== Mouse tracking for Dock magnification =====
const mouseX = ref(-1)
const tabBarRef = ref<HTMLElement | null>(null)

function onMouseMove(e: PointerEvent) {
  if (e.pointerType === 'touch') return
  if (!tabBarRef.value) return
  const rect = tabBarRef.value.getBoundingClientRect()
  mouseX.value = e.clientX - rect.left
}

function onMouseLeave() {
  mouseX.value = -1
}

function getTabScale(index: number): number {
  if (mouseX.value < 0) return 1
  const items = tabBarRef.value?.querySelectorAll<HTMLElement>('.dock-item')
  if (!items || !items[index]) return 1
  const itemRect = items[index].getBoundingClientRect()
  const barRect = tabBarRef.value!.getBoundingClientRect()
  const itemCenter = itemRect.left + itemRect.width / 2 - barRect.left
  const distance = Math.abs(mouseX.value - itemCenter)
  const maxDistance = 120
  if (distance > maxDistance) return 1
  const ratio = 1 - distance / maxDistance
  return 1 + (dockConfig.value.maxScale - 1) * ratio
}

// ===== User avatar menu =====
const currentUser = ref<User | null>(null)
const showUserMenu = ref(false)
const userAvatarRef = ref<HTMLElement | null>(null)
const userMenuStyle = ref<CSSProperties>({})
let userMenuPositionFrame: number | undefined
const showTrashMenu = ref(false)
const trashCount = ref(0)
const trashIcon = computed<DockIconName>(() => trashCount.value > 0 ? 'trashFull' : 'trashEmpty')

async function refreshTrashCount() {
  try { trashCount.value = (await photoApi.trashCount()).data.count }
  catch { trashCount.value = 0 }
}

function toggleTrashMenu() {
  showTrashMenu.value = !showTrashMenu.value
  closeUserMenu()
  if (showTrashMenu.value) void refreshTrashCount()
}

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    const stored = sessionStorage.getItem('user')
    if (stored) {
      currentUser.value = JSON.parse(stored)
    }
  } catch { /* ignore */ }
})

function updateUserMenuPosition() {
  if (!showUserMenu.value || !userAvatarRef.value) return

  const avatarRect = userAvatarRef.value.getBoundingClientRect()
  const menuWidth = 220
  const viewportPadding = 12
  const left = Math.min(
    window.innerWidth - menuWidth - viewportPadding,
    Math.max(viewportPadding, avatarRect.left + avatarRect.width / 2 - menuWidth / 2),
  )

  const nextLeft = `${left}px`
  const nextBottom = `${window.innerHeight - avatarRect.top + 28}px`
  if (userMenuStyle.value.left !== nextLeft || userMenuStyle.value.bottom !== nextBottom) {
    userMenuStyle.value = { left: nextLeft, bottom: nextBottom }
  }
}

function trackUserMenuPosition() {
  if (!showUserMenu.value) {
    userMenuPositionFrame = undefined
    return
  }
  updateUserMenuPosition()
  userMenuPositionFrame = window.requestAnimationFrame(trackUserMenuPosition)
}

function stopTrackingUserMenuPosition() {
  if (userMenuPositionFrame !== undefined) {
    window.cancelAnimationFrame(userMenuPositionFrame)
    userMenuPositionFrame = undefined
  }
}

async function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
  showTrashMenu.value = false
  if (showUserMenu.value) {
    await nextTick()
    stopTrackingUserMenuPosition()
    trackUserMenuPosition()
  } else {
    stopTrackingUserMenuPosition()
  }
}

function closeUserMenu() {
  showUserMenu.value = false
  stopTrackingUserMenuPosition()
}

function handleLogout() {
  localStorage.removeItem('token')
  sessionStorage.removeItem('user')
  closeUserMenu()
  router.push('/login')
}

function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.user-avatar-btn') && !target.closest('.user-popup-menu')) {
    closeUserMenu()
  }
  if (!target.closest('.dock-trash-entry')) showTrashMenu.value = false
}

function onProfileUpdated(event: Event) {
  currentUser.value = (event as CustomEvent<User>).detail
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  window.addEventListener('user-profile-updated', onProfileUpdated)
  window.addEventListener('dock-config-updated', onDockConfigUpdated)
  window.addEventListener('trash-changed', refreshTrashCount)
  window.addEventListener('resize', updateUserMenuPosition)
  void dockIconStore.hydrate().catch(() => undefined)
  void refreshTrashCount()
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('user-profile-updated', onProfileUpdated)
  window.removeEventListener('dock-config-updated', onDockConfigUpdated)
  window.removeEventListener('trash-changed', refreshTrashCount)
  window.removeEventListener('resize', updateUserMenuPosition)
  stopTrackingUserMenuPosition()
})

const themeTitle = computed(() => {
  const map: Record<string, string> = {
    'dark': '切换亮色主题',
    'light': '切换 Liquid Glass 主题',
    'macos26': '切换暗色主题',
  }
  return map[theme.value] || '切换主题'
})

// SF Symbols as SVG paths
const sfIcons: Record<string, string> = {
  'photo': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z',
  'photo.fill': 'M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm7.5 2a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM2 15l4.5-6 3.5 4.5L12 11l6 8H2z',
  'rectangle.stack': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'rectangle.stack.fill': 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  'magnifyingglass': 'M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z',
  'timeline': 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z',
  'timeline.fill': 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 13l-4-4 1.41-1.41L12 13.17l4.59-4.58L18 10l-6 6z',
  'baby': 'M12 2a10 10 0 100 20 10 10 0 000-20zM8.5 8a1.5 1.5 0 110 3 1.5 1.5 0 010-3zm7 0a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM12 17.5c-2.33 0-4.31-1.46-5.11-3.5h10.22c-.8 2.04-2.78 3.5-5.11 3.5z',
  'baby.fill': 'M12 2a10 10 0 100 20 10 10 0 000-20zM8.5 8a1.5 1.5 0 110 3 1.5 1.5 0 010-3zm7 0a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM12 17.5c-2.33 0-4.31-1.46-5.11-3.5h10.22c-.8 2.04-2.78 3.5-5.11 3.5z',
  'tag': 'M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z',
  'tag.fill': 'M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z',
  'settings': 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  'settings.fill': 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
}

// Dock style computed
const dockStyle = computed(() => ({
  '--dock-opacity': dockConfig.value.opacity,
  '--dock-blur': dockConfig.value.blurStrength + 'px',
  '--dock-icon-size': dockConfig.value.iconSize + 'px',
  '--dock-tile-size': '48px',
  '--dock-base-gap': '11px',
  '--dock-anim-speed': dockConfig.value.animationSpeed + 's',
}))

const animSpeed = computed(() => dockConfig.value.animationSpeed + 's')

function getDockItemStyle(index: number) {
  const scale = getTabScale(index)
  const tileSize = 48
  const lift = (scale - 1) * tileSize * 0.72
  const spread = (scale - 1) * tileSize / 2
  return {
    '--dock-item-scale': String(scale),
    '--dock-item-lift': `${lift}px`,
    '--dock-item-spread': `${spread}px`,
    '--dock-item-transition': animSpeed.value,
    zIndex: String(Math.round(scale * 100)),
  }
}
</script>

<template>
  <div class="app-shell" :style="dockStyle">
    <!-- Top bar -->
    <header class="top-bar glass">
      <div class="top-bar-left">
        <TaskFloat />
      </div>
      <h1 class="page-title">{{ pageTitle }}</h1>
      <button class="theme-toggle" @click="toggleTheme" :title="themeTitle">
        <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M12 7a5 5 0 100 10 5 5 0 000-10zm0-3a1 1 0 01-1-1V1a1 1 0 112 0v2a1 1 0 01-1 1zm0 18a1 1 0 01-1-1v-2a1 1 0 112 0v2a1 1 0 01-1 1zm9-9a1 1 0 01-1 1h-2a1 1 0 110-2h2a1 1 0 011 1zM6 13H4a1 1 0 110-2h2a1 1 0 010 2zm12.07-6.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zM4.93 19.07a1 1 0 010-1.41l1.42-1.42a1 1 0 111.41 1.41l-1.41 1.42a1 1 0 01-1.42 0zm14.14 0a1 1 0 01-1.41 0l-1.42-1.42a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42zM4.93 4.93a1 1 0 01-1.42 0L2.1 3.51a1 1 0 011.41-1.41l1.42 1.41a1 1 0 010 1.42z" />
        </svg>
        <svg v-else-if="theme === 'light'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M21.64 13a1 1 0 00-1.05-.14 8.05 8.05 0 01-3.37.73 8.15 8.15 0 01-8.14-8.14 8.59 8.59 0 01.25-2A1 1 0 008 2.36a10.14 10.14 0 1014 11 1 1 0 00-.36-.64z" />
        </svg>
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

    <!-- Bottom Dock (macOS-style frosted glass) -->
    <nav
      v-if="isTabRoute"
      ref="tabBarRef"
      class="dock"
      @pointermove="onMouseMove"
      @mouseleave="onMouseLeave"
    >
      <RouterLink
        v-for="(tab, index) in tabs"
        :key="tab.path"
        :to="tab.path"
        class="dock-item"
        :class="{ active: activeTab === tab.path }"
        :style="getDockItemStyle(index)"
        :aria-label="tab.label"
        @click="closeDockPopups"
      >
        <span class="dock-icon-tile" :class="{ custom: dockConfig.iconStyle === 'custom', minimal: dockConfig.iconStyle === 'minimal' }">
          <span class="dock-icon-glass"></span>
          <DockIcon class="dock-icon" :name="tab.dockIcon" :variant="dockConfig.iconStyle" :custom-src="dockIconStore.iconUrls[tab.dockIcon]" />
        </span>
        <span class="dock-label" role="tooltip">{{ tab.label }}</span>
        <span v-if="activeTab === tab.path" class="dock-indicator-dot"></span>
      </RouterLink>

      <div class="dock-trash-entry" :class="{ open: showTrashMenu }">
        <button class="dock-item dock-trash-btn" :style="getDockItemStyle(tabs.length)" aria-label="回收站" @click.stop="toggleTrashMenu">
          <span class="dock-icon-tile trash-tile" :class="{ custom: dockConfig.iconStyle === 'custom', minimal: dockConfig.iconStyle === 'minimal' }">
            <span class="dock-icon-glass"></span>
            <DockIcon class="dock-icon" :name="trashIcon" :variant="dockConfig.iconStyle" :custom-src="dockIconStore.iconUrls[trashIcon]" />
          </span>
          <span v-if="trashCount" class="trash-count">{{ trashCount > 99 ? '99+' : trashCount }}</span>
          <span class="dock-label" role="tooltip">回收站</span>
        </button>
        <Transition name="popup">
          <section v-if="showTrashMenu" class="trash-popup glass" @click.stop @pointermove.stop>
            <header><div><strong>系统回收站</strong><small>恢复照片，或永久删除媒体文件</small></div><el-button circle text aria-label="关闭" @click="showTrashMenu = false">✕</el-button></header>
            <RecycleBinPanel compact @changed="refreshTrashCount" />
          </section>
        </Transition>
      </div>

      <!-- User avatar button -->
      <div class="user-avatar-entry">
        <div ref="userAvatarRef" class="dock-item user-avatar-btn" :style="getDockItemStyle(tabs.length + 1)" @click.stop="toggleUserMenu">
          <div class="avatar-circle">
            <img v-if="currentUser?.avatar" :src="currentUser.avatar" class="avatar-img" />
            <span v-else class="avatar-text">{{ (currentUser?.username || '?').charAt(0).toUpperCase() }}</span>
          </div>
          <span class="dock-label" role="tooltip">账户</span>
        </div>
      </div>
    </nav>

    <!-- User popup menu -->
    <Transition name="popup">
      <div v-if="showUserMenu" class="user-popup-menu glass" :style="userMenuStyle">
        <div class="popup-header">
          <div class="popup-avatar">
            <img v-if="currentUser?.avatar" :src="currentUser.avatar" class="popup-avatar-img" />
            <span v-else class="popup-avatar-text">{{ (currentUser?.username || '?').charAt(0).toUpperCase() }}</span>
          </div>
          <div class="popup-user-info">
            <span class="popup-username">{{ currentUser?.nickname || currentUser?.username || '未登录' }}</span>
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
  border-bottom: 1px solid var(--glass-border);
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.04);
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
  padding-bottom: calc(110px + var(--safe-bottom));
}

/* ===== macOS-style Frosted Glass Dock ===== */
.dock {
  position: fixed;
  bottom: calc(18px + var(--safe-bottom));
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: var(--dock-base-gap, 10px);
  padding: 7px 10px 9px;
  border-radius: calc(var(--dock-tile-size) * .44);
  max-width: calc(100% - 20px);
  isolation: isolate;
  background: rgba(var(--dock-bg-rgb, 28, 28, 30), var(--dock-opacity, 0.72));
  backdrop-filter: blur(var(--dock-blur, 20px)) saturate(190%) contrast(102%);
  -webkit-backdrop-filter: blur(var(--dock-blur, 20px)) saturate(190%) contrast(102%);
  border: 1px solid rgba(255, 255, 255, 0.38);
  box-shadow:
    0 22px 52px rgba(0, 0, 0, 0.22),
    0 5px 14px rgba(0, 0, 0, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.72),
    inset 0 -1px 0 rgba(0, 0, 0, 0.12);
}

.dock::before {
  content: '';
  position: absolute;
  inset: 1px 8% auto;
  z-index: -1;
  height: 45%;
  border-radius: inherit;
  background: linear-gradient(180deg, rgba(255, 255, 255, .48), transparent);
  pointer-events: none;
}

.dock::after {
  content: '';
  position: absolute;
  inset: 6px;
  z-index: -2;
  border-radius: calc(var(--dock-tile-size) * .34);
  box-shadow: inset 0 0 18px rgba(255, 255, 255, .14);
  pointer-events: none;
}

/* Light theme dock */
:global([data-theme="light"]) .dock {
  --dock-bg-rgb: 255, 255, 255;
  border-color: rgba(255, 255, 255, .8);
  box-shadow:
    0 22px 52px rgba(45, 61, 94, .16),
    0 5px 14px rgba(45, 61, 94, .1),
    inset 0 1px 0 rgba(255, 255, 255, .96),
    inset 0 -1px 0 rgba(91, 111, 148, .1);
}

/* MACOS26 Liquid Glass dock */
:global([data-theme="macos26"]) .dock {
  --dock-bg-rgb: 242, 248, 255;
  border-color: rgba(255, 255, 255, .82);
  box-shadow:
    0 24px 58px rgba(45, 61, 94, .24),
    0 6px 16px rgba(45, 61, 94, .12),
    inset 0 1px 0 rgba(255, 255, 255, .98),
    inset 0 -1px 0 rgba(91, 111, 148, .14);
  backdrop-filter: blur(var(--dock-blur, 28px)) saturate(210%) contrast(106%);
  -webkit-backdrop-filter: blur(var(--dock-blur, 28px)) saturate(210%) contrast(106%);
}

.dock-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--dock-tile-size);
  height: var(--dock-tile-size);
  padding: 0;
  flex-shrink: 0;
  margin-inline: var(--dock-item-spread, 0px);
  color: var(--text-tertiary);
  -webkit-tap-highlight-color: transparent;
  transform: translateY(calc(-1 * var(--dock-item-lift, 0px))) scale(var(--dock-item-scale, 1));
  transform-origin: bottom center;
  transition: transform var(--dock-item-transition, .16s) cubic-bezier(.2, .8, .2, 1), margin var(--dock-item-transition, .16s) cubic-bezier(.2, .8, .2, 1), color .15s ease;
  will-change: transform;
  text-decoration: none;
}

.dock-icon-tile,
.dock-icon-glass,
.dock-icon,
.dock-icon :deep(*) {
  pointer-events: none;
}

.dock-item:active {
  transform: scale(0.9) !important;
  transition: transform 0.1s ease;
}

.dock-item.active {
  color: var(--accent);
}

.dock-icon-tile {
  position: relative;
  display: grid;
  width: calc(var(--dock-tile-size) - 4px);
  height: calc(var(--dock-tile-size) - 4px);
  place-items: center;
  overflow: visible;
  border: 0;
  border-radius: 27%;
  background: none;
  box-shadow: none;
  isolation: isolate;
  transition: filter .18s ease, box-shadow .18s ease;
}

.dock-icon-glass {
  display: none;
}

.dock-icon-tile::after {
  display: none;
}

.dock-icon-tile.custom{overflow:visible;border:0;border-radius:0;background:none;box-shadow:none}.dock-icon-tile.custom .dock-icon-glass,.dock-icon-tile.custom::after{display:none}.dock-icon-tile.custom .dock-icon{width:var(--dock-icon-size);height:var(--dock-icon-size);filter:none}.dock-icon-tile.minimal{border:1px solid var(--separator);overflow:hidden;background:var(--bg-card);box-shadow:0 5px 12px rgba(0,0,0,.14)}.dock-icon-tile.minimal .dock-icon-glass,.dock-icon-tile.minimal::after{display:none}.dock-icon-tile.minimal .dock-icon{color:var(--text-primary);filter:none}.trash-tile{background:none}.dock-trash-entry{position:relative;display:flex}.trash-count{position:absolute;top:-5px;right:-4px;z-index:4;display:grid;min-width:18px;height:18px;padding:0 4px;place-items:center;border:2px solid rgba(255,255,255,.9);border-radius:999px;background:var(--danger);color:#fff;font-size:9px;font-weight:800}.trash-popup{position:absolute;right:-84px;bottom:calc(100% + 22px);z-index:220;width:min(440px,calc(100vw - 24px));padding:14px;border:1px solid var(--glass-border);border-radius:20px;background:var(--glass-bg);box-shadow:0 28px 70px rgba(0,0,0,.28);backdrop-filter:blur(28px) saturate(185%)}.trash-popup::after{position:absolute;right:104px;top:100%;border:9px solid transparent;border-top-color:var(--bg-card);content:''}.trash-popup>header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;padding-bottom:10px;border-bottom:1px solid var(--separator)}.trash-popup>header>div{display:grid;gap:3px}.trash-popup>header strong{color:var(--text-primary);font-size:14px}.trash-popup>header small{color:var(--text-secondary);font-size:10px}

.dock-icon {
  position: relative;
  z-index: 1;
  display: block;
  width: var(--dock-icon-size);
  height: var(--dock-icon-size);
  color: var(--text-primary);
  filter: drop-shadow(0 2px 3px rgba(14, 42, 68, .22));
}

.dock-item:hover .dock-icon-tile,
.dock-item.active .dock-icon-tile {
  filter: none;
  box-shadow: none;
}

.dock-item.active .dock-icon {
  color: var(--accent);
}

.dock-item:hover .dock-icon-tile.custom,
.dock-item.active .dock-icon-tile.custom {
  border: 0;
  background: none;
  box-shadow: none;
  filter: none;
}

.dock-label {
  position: absolute;
  bottom: calc(100% + 13px);
  left: 50%;
  z-index: 5;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, .68);
  border-radius: 9px;
  background: rgba(27, 36, 51, .84);
  box-shadow: 0 8px 22px rgba(15, 32, 52, .2);
  color: #fff;
  font-size: 12px;
  font-weight: 550;
  white-space: nowrap;
  line-height: 1;
  opacity: 0;
  visibility: hidden;
  transform: translateX(-50%);
  transition: opacity .16s ease, transform .16s ease, visibility .16s ease;
  backdrop-filter: blur(12px);
  pointer-events: none;
}

.dock-label::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  border: 5px solid transparent;
  border-top-color: rgba(27, 36, 51, .84);
  transform: translateX(-50%);
}

.dock-item:hover .dock-label {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(-3px);
}

.dock-indicator-dot {
  position: absolute;
  bottom: -6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 6px color-mix(in srgb, var(--accent) 42%, transparent);
}

/* ===== User avatar button ===== */
.user-avatar-entry {
  position: relative;
  display: flex;
  align-items: flex-end;
  flex-shrink: 0;
  margin-left: 12px;
}

.user-avatar-btn {
  cursor: pointer;
}

.user-avatar-entry::before {
  content: '';
  position: absolute;
  top: 14%;
  right: calc(100% + 8px);
  width: 1px;
  height: 72%;
  background: color-mix(in srgb, var(--text-tertiary) 28%, transparent);
  box-shadow: 1px 0 rgba(255, 255, 255, .5);
}

.avatar-circle {
  width: calc(var(--dock-tile-size) - 6px);
  height: calc(var(--dock-tile-size) - 6px);
  border-radius: 50%;
  background: linear-gradient(145deg, #5ac8fa, #007aff 58%, #5e5ce6);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, .9);
  box-shadow: 0 7px 16px rgba(38, 72, 112, .25), inset 0 1px 0 rgba(255, 255, 255, .72);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-text {
  color: white;
  font-size: calc(var(--dock-tile-size) * .34);
  font-weight: 700;
}

/* ===== Popup menu ===== */
.user-popup-menu {
  position: fixed;
  width: 220px;
  border-radius: 18px;
  padding: 8px;
  z-index: 200;
  box-shadow: 0 24px 60px rgba(0, 0, 0, .26), inset 0 1px 0 rgba(255, 255, 255, .72);
  border: 1px solid var(--glass-border);
}

@media (max-width: 600px) {
  .dock {
    bottom: calc(10px + var(--safe-bottom));
    gap: 3px;
    padding: 6px 7px 8px;
    --dock-tile-size: 36px !important;
  }

  .dock-item {
    margin-inline: 0;
    transform: none;
  }

  .user-avatar-entry {
    margin-left: 7px;
  }

  .user-avatar-entry::before {
    right: calc(100% + 5px);
  }

  .dock-label {
    display: none;
  }
  .trash-popup{position:fixed;right:12px;bottom:calc(64px + var(--safe-bottom));left:12px;width:auto}.trash-popup::after{display:none}
}

@media (prefers-reduced-motion: reduce) {
  .dock-item {
    transform: none !important;
    transition: none;
  }
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

/* Popup animation */
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
</style>

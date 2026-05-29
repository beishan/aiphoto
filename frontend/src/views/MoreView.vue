<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function handleLogout() {
  localStorage.removeItem('token')
  router.push('/login')
}

const menuItems = [
  { label: '宝宝相册', icon: 'baby', path: '/baby' },
  { label: '时间线', icon: 'timeline', path: '/timeline' },
  { label: '设置', icon: 'settings', path: '/settings' },
]

const iconPaths: Record<string, string> = {
  baby: 'M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a2.5 2.5 0 110 5 2.5 2.5 0 010-5zm0 13.2c-2.03 0-3.8-.81-5.11-2.12.03-1.99 4-3.08 6.11-3.08 2.03 0 5.97 1.09 6 3.08A7.96 7.96 0 0112 18.2z',
  timeline: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
}
</script>

<template>
  <div class="more-view">
    <!-- Menu sections -->
    <div class="menu-section">
      <div
        v-for="item in menuItems"
        :key="item.path"
        class="menu-item"
        @click="router.push(item.path)"
      >
        <div class="menu-icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
            <path :d="iconPaths[item.icon]" />
          </svg>
        </div>
        <span class="menu-label">{{ item.label }}</span>
        <svg class="menu-arrow" viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
          <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z" />
        </svg>
      </div>
    </div>

    <!-- App info -->
    <div class="app-info">
      <p class="app-name">MemoryVault</p>
      <p class="app-version">AI 智能相册</p>
    </div>

    <!-- Logout -->
    <div class="menu-section">
      <button class="menu-item logout-item" @click="handleLogout">
        <div class="menu-icon danger">
          <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
            <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z" />
          </svg>
        </div>
        <span class="menu-label danger">退出登录</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.more-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 24px 16px;
}

.menu-section {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 24px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.15s;
  width: 100%;
  text-align: left;
  font-family: inherit;
  font-size: 16px;
  color: var(--text-primary);
  border: none;
  background: none;
}

.menu-item:not(:last-child) {
  border-bottom: 0.5px solid var(--separator);
}

.menu-item:active {
  background: var(--bg-tertiary);
}

.menu-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--accent);
  flex-shrink: 0;
}

.menu-icon.danger {
  background: rgba(255, 69, 58, 0.15);
  color: var(--danger);
}

.menu-label {
  flex: 1;
  font-weight: 400;
}

.menu-label.danger {
  color: var(--danger);
}

.menu-arrow {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.app-info {
  text-align: center;
  margin-bottom: 24px;
  padding: 16px;
}

.app-name {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 2px;
}

.app-version {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAlbumStore } from '@/stores/albumStore'
import type { Album } from '@/types'

const router = useRouter()
const albumStore = useAlbumStore()
const message = useMessage()
const showCreate = ref(false)
const newAlbum = ref<{ name: string; type: 'VIRTUAL' | 'DIRECTORY' | 'TRAINING' | 'BABY' }>({ name: '', type: 'VIRTUAL' })

onMounted(() => {
  albumStore.fetchAlbums()
})

function goToAlbum(album: Album) {
  router.push(`/albums/${album.id}`)
}

async function handleCreate() {
  if (!newAlbum.value.name) {
    message.warning('请输入相册名称')
    return
  }
  try {
    await albumStore.createAlbum(newAlbum.value)
    showCreate.value = false
    newAlbum.value = { name: '', type: 'VIRTUAL' }
    message.success('相册创建成功')
  } catch (e) {
    message.error('创建失败')
  }
}

async function handleDelete(album: Album, e: Event) {
  e.stopPropagation()
  try {
    await albumStore.deleteAlbum(album.id)
    message.success('相册已删除')
  } catch (e) {
    message.error('删除失败')
  }
}

const albumTypeLabels: Record<string, string> = {
  VIRTUAL: '虚拟',
  DIRECTORY: '目录',
  TRAINING: '训练',
  BABY: '宝宝',
}

const albumTypeIcons: Record<string, string> = {
  VIRTUAL: 'M3 4a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2V4zm3 5a2 2 0 012-2h10a2 2 0 012 2v2a2 2 0 01-2 2H8a2 2 0 01-2-2V9zm-3 7a2 2 0 012-2h14a2 2 0 012 2v2a2 2 0 01-2 2H5a2 2 0 01-2-2v-2z',
  DIRECTORY: 'M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  TRAINING: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  BABY: 'M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a2.5 2.5 0 110 5 2.5 2.5 0 010-5zm0 13.2c-2.03 0-3.8-.81-5.11-2.12.03-1.99 4-3.08 6.11-3.08 2.03 0 5.97 1.09 6 3.08A7.96 7.96 0 0112 18.2z',
}
</script>

<template>
  <div class="albums-view">
    <!-- Empty -->
    <div v-if="!albumStore.loading && albumStore.albums.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
      </svg>
      <h3>暂无相册</h3>
      <button class="create-btn" @click="showCreate = true">创建第一个相册</button>
    </div>

    <!-- Album grid -->
    <div v-else class="album-grid">
      <div
        v-for="album in albumStore.albums"
        :key="album.id"
        class="album-card"
        @click="goToAlbum(album)"
      >
        <div class="album-cover">
          <img v-if="album.coverPhotoUrl" :src="album.coverPhotoUrl" alt="" />
          <div v-else class="album-cover-placeholder">
            <svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
              <path :d="albumTypeIcons[album.type] || albumTypeIcons.VIRTUAL" />
            </svg>
          </div>
          <div class="album-overlay">
            <span class="album-name">{{ album.name }}</span>
            <span class="album-meta">{{ album.photoCount }} 张 {{ albumTypeLabels[album.type] }}</span>
          </div>
          <button class="album-delete" @click="(e) => handleDelete(album, e)">
            <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Create FAB -->
    <button v-if="albumStore.albums.length > 0" class="fab-create" @click="showCreate = true">
      <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
        <path d="M12 4v16m8-8H4" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
    </button>

    <!-- Create modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
          <div class="modal-sheet glass">
            <div class="sheet-header">
              <h3>新建相册</h3>
              <button @click="showCreate = false" class="sheet-close">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
                </svg>
              </button>
            </div>

            <div class="form-group">
              <label>名称</label>
              <input v-model="newAlbum.name" type="text" placeholder="相册名称" class="ios-input" />
            </div>

            <div class="form-group">
              <label>类型</label>
              <div class="type-selector">
                <button
                  v-for="(label, key) in albumTypeLabels"
                  :key="key"
                  class="type-btn"
                  :class="{ active: newAlbum.type === key }"
                  @click="newAlbum.type = key as typeof newAlbum.type"
                >
                  {{ label }}
                </button>
              </div>
            </div>

            <button class="submit-btn" @click="handleCreate">创建</button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.albums-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); }

.create-btn {
  margin-top: 8px;
  padding: 10px 24px;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-full);
  font-size: 15px;
  font-weight: 600;
  border: none;
  cursor: pointer;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

@media (min-width: 768px) {
  .album-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (min-width: 1024px) {
  .album-grid { grid-template-columns: repeat(4, 1fr); }
}

.album-card {
  cursor: pointer;
}

.album-cover {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--bg-secondary);
}

.album-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.album-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.album-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24px 12px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
}

.album-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: white;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.album-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.album-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  color: white;
  opacity: 0;
  transition: opacity 0.2s;
  border: none;
  cursor: pointer;
}

.album-card:hover .album-delete {
  opacity: 1;
}

.fab-create {
  position: fixed;
  bottom: calc(var(--tab-height) + 20px);
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(10, 132, 255, 0.4);
  z-index: 50;
  border: none;
  cursor: pointer;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-sheet {
  width: 100%;
  max-width: 400px;
  border-radius: var(--radius-xl);
  padding: 24px;
  border: 0.5px solid var(--glass-border);
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.sheet-header h3 { font-size: 20px; font-weight: 600; }
.sheet-close { color: var(--text-secondary); padding: 4px; }

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.ios-input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  background: var(--bg-tertiary);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 16px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}

.ios-input:focus {
  border-color: var(--accent);
}

.type-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-btn {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 14px;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
  transition: all 0.2s;
}

.type-btn.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: white;
  font-size: 17px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
  margin-top: 4px;
}
</style>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { dedupApi } from '@/api/dedupApi'
import { usePhotoStore } from '@/stores/photoStore'
import type { Photo } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'

const router = useRouter()
const message = useMessage()
const photoStore = usePhotoStore()

const activeTab = ref<'exact' | 'similar'>('exact')
const exactGroups = ref<Photo[][]>([])
const similarGroups = ref<Photo[][]>([])
const loading = ref(false)
const scanned = ref(false)

const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

const currentGroups = computed(() =>
  activeTab.value === 'exact' ? exactGroups.value : similarGroups.value
)

const totalDuplicates = computed(() =>
  currentGroups.value.reduce((sum, g) => sum + g.length, 0)
)

async function scan() {
  loading.value = true
  scanned.value = true
  try {
    const [exactRes, similarRes] = await Promise.all([
      dedupApi.getGroups(),
      dedupApi.getSimilar(),
    ])
    exactGroups.value = exactRes.data
    similarGroups.value = similarRes.data
    message.success(`扫描完成：${exactGroups.value.length} 组精确重复，${similarGroups.value.length} 组相似照片`)
  } catch (e) {
    message.error('扫描失败')
  } finally {
    loading.value = false
  }
}

function openGroup(group: Photo[], index: number) {
  viewerPhotos.value = group
  viewerIndex.value = index
  viewerVisible.value = true
}

async function deletePhoto(groupIndex: number, photoIndex: number) {
  const photo = currentGroups.value[groupIndex][photoIndex]
  try {
    await dedupApi.deletePhoto(photo.id)
    // Remove from group
    currentGroups.value[groupIndex].splice(photoIndex, 1)
    // Remove empty groups
    if (currentGroups.value[groupIndex].length < 2) {
      currentGroups.value.splice(groupIndex, 1)
    }
    photoStore.photos = photoStore.photos.filter(p => p.id !== photo.id)
    message.success('已删除')
  } catch (e) {
    message.error('删除失败')
  }
}

function formatSize(bytes: number | null) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="dedup-view">
    <div class="page-header">
      <button class="back-btn" @click="router.push('/more')">
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
      <h1 class="page-title">去重检测</h1>
    </div>

    <!-- Scan button -->
    <div class="scan-section">
      <button class="scan-btn" @click="scan" :disabled="loading">
        <svg v-if="!loading" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
        </svg>
        <div v-else class="scan-spinner"></div>
        {{ loading ? '扫描中...' : '扫描重复照片' }}
      </button>
    </div>

    <!-- Tabs -->
    <div v-if="scanned && !loading" class="dedup-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'exact' }"
        @click="activeTab = 'exact'"
      >
        精确重复
        <span class="tab-badge" v-if="exactGroups.length">{{ exactGroups.length }}</span>
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'similar' }"
        @click="activeTab = 'similar'"
      >
        相似照片
        <span class="tab-badge" v-if="similarGroups.length">{{ similarGroups.length }}</span>
      </button>
    </div>

    <!-- Results -->
    <div v-if="scanned && !loading" class="dedup-results">
      <!-- Empty -->
      <div v-if="currentGroups.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" />
        </svg>
        <h3>没有发现{{ activeTab === 'exact' ? '精确重复' : '相似' }}的照片</h3>
      </div>

      <!-- Group list -->
      <div v-else class="group-list">
        <div v-for="(group, gi) in currentGroups" :key="gi" class="group-card">
          <div class="group-header">
            <span class="group-info">{{ group.length }} 张照片</span>
            <span class="group-size" v-if="group[0]?.fileSize">{{ formatSize(group[0].fileSize) }}</span>
          </div>
          <div class="group-photos">
            <div
              v-for="(photo, pi) in group"
              :key="photo.id"
              class="group-photo"
              @click="openGroup(group, pi)"
            >
              <img :src="photo.thumbnailUrl || undefined" :alt="photo.originalFilename || ''" />
              <button class="delete-btn" @click.stop="deletePhoto(gi, pi)" title="删除">
                <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Initial state -->
    <div v-if="!scanned && !loading" class="initial-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" class="initial-icon">
        <path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
      </svg>
      <h3>照片去重</h3>
      <p>扫描你的照片库，找出重复和相似的照片</p>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="viewerPhotos"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.dedup-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  padding: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  flex: 1;
}

.scan-section {
  display: flex;
  justify-content: center;
  margin: 0 16px 20px;
}

.scan-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-full);
  font-size: 16px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s;
}

.scan-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.scan-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.dedup-tabs {
  display: flex;
  gap: 8px;
  margin: 0 16px 16px;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
}

.tab-btn.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.tab-badge {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 10px;
  background: rgba(255,255,255,0.2);
}

.tab-btn:not(.active) .tab-badge {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.initial-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 12px;
  color: var(--text-secondary);
  padding: 0 16px;
}

.initial-icon, .empty-icon {
  color: var(--text-tertiary);
  margin-bottom: 8px;
}

.initial-state h3, .empty-state h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.initial-state p, .empty-state p {
  font-size: 15px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--border);
}

.group-info {
  font-size: 14px;
  font-weight: 600;
}

.group-size {
  font-size: 13px;
  color: var(--text-secondary);
}

.group-photos {
  display: flex;
  gap: 4px;
  padding: 8px;
  overflow-x: auto;
}

.group-photo {
  position: relative;
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
}

.group-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.delete-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 59, 48, 0.9);
  color: white;
  opacity: 0;
  transition: opacity 0.2s;
  border: none;
  cursor: pointer;
}

.group-photo:hover .delete-btn {
  opacity: 1;
}
</style>

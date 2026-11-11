<script setup lang="ts">
import { onMounted, ref, computed, onUnmounted } from 'vue'
import http from '@/api/http'
import { normalizePage, type SpringPage } from '@/api/pageResponse'
import type { Photo } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'
import PhotoCard from '@/components/PhotoCard.vue'
import { photoApi } from '@/api/photoApi'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const monthLoading = ref(false)
const photos = ref<Photo[]>([])
const page = ref(1)
const total = ref(0)
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)
const activeYearMonth = ref('')
const timelineNodes = ref<{ year: number; month: number; count: number; key: string }[]>([])
const activeNode = computed(() => timelineNodes.value.find(node => node.key === activeYearMonth.value))
let requestId = 0
onUnmounted(() => { requestId++ })
async function loadMonth() {
  const node = activeNode.value
  if (!node) return
  const request = ++requestId
  monthLoading.value = true
  photos.value = []
  try {
    const response = await http.get<SpringPage<Photo>>('/timeline/photos', { params: { year: node.year, month: node.month, page: page.value - 1, size: 60 } })
    if (request !== requestId) return
    const data = normalizePage(response.data)
    photos.value = data.content
    total.value = data.totalElements
  } catch { if (request === requestId) ElMessage.error('时间线照片加载失败，请重试') }
  finally { if (request === requestId) monthLoading.value = false }
}
async function init() {
  loading.value = true
  try {
    const { data } = await http.get<{ year: number; month: number; count: number }[]>('/timeline/months')
    timelineNodes.value = data.map(node => ({ ...node, key: 'ym-' + node.year + '-' + node.month }))
    if (data.length) await scrollToNode(timelineNodes.value[0].key)
  } catch { ElMessage.error('时间线加载失败，请重试') }
  finally { loading.value = false }
}
onMounted(init)
async function scrollToNode(key: string) {
  viewerVisible.value = false
  viewerPhotos.value = []
  activeYearMonth.value = key
  page.value = 1
  total.value = activeNode.value?.count || 0
  await loadMonth()
}
async function toggleFavorite(photo: Photo) {
  try {
    const { data } = await photoApi.update(photo.id, { favorite: !photo.favorite })
    photo.favorite = data.favorite
  } catch { ElMessage.error('收藏更新失败，请重试') }
}
function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }
function openViewer(index: number) {
  viewerPhotos.value = photos.value
  viewerIndex.value = index
  viewerVisible.value = true
}
const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const groupedNodes = computed(() => {
  const groups: Record<number, typeof timelineNodes.value> = {}
  for (const node of timelineNodes.value) (groups[node.year] ||= []).push(node)
  return groups
})
</script>

<template>
  <div class="timeline-view">
    <div class="timeline-layout" :class="{ 'is-empty': !loading && timelineNodes.length === 0 }">
      <!-- Left timeline axis -->
      <aside class="timeline-axis">
        <el-button class="scroll-top-btn" circle text @click="scrollToTop" title="返回顶部">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z" />
          </svg>
        </el-button>
        <div class="axis-line"></div>
        <div
          v-for="(nodes, year) in groupedNodes"
          :key="year"
          class="axis-year-group"
        >
          <div class="axis-year">{{ year }}</div>
          <div
            v-for="node in nodes"
            :key="node.key"
            class="axis-node"
            :class="{ active: activeYearMonth === node.key }"
            @click="scrollToNode(node.key)"
          >
            <div class="axis-dot"></div>
            <span class="axis-month">{{ monthNames[node.month - 1] }}</span>
            <span class="axis-count">{{ node.count }}</span>
          </div>
        </div>
      </aside>

      <!-- Right photo content -->
      <div class="timeline-content" ref="scrollContainer">
        <!-- Loading -->
        <div v-if="loading" class="loading-state"><el-skeleton :rows="6" animated /></div>

        <!-- Empty -->
        <el-empty v-else-if="timelineNodes.length === 0" class="empty-state" description="时间线暂无照片，在照片详情页中添加照片" :image-size="92" />

        <section v-else-if="activeNode" v-loading="monthLoading" class="timeline-month-section">
          <div class="month-header"><span class="month-label">{{ activeNode.year }}年 {{ monthNames[activeNode.month - 1] }}</span><span class="month-count">{{ total }} 张</span><el-button text @click="loadMonth">刷新</el-button></div>
          <div class="photo-grid-compact">
            <PhotoCard v-for="(photo, index) in photos" :key="photo.id" :photo="photo" @click="openViewer(index)" @toggle-favorite="toggleFavorite(photo)" />
          </div>
          <el-empty v-if="!monthLoading && !photos.length" description="本页暂无照片" />
          <el-pagination v-model:current-page="page" :page-size="60" :total="total" :disabled="monthLoading" layout="total, prev, pager, next" @current-change="loadMonth" />
        </section>

      </div>
    </div>
    <PhotoViewer v-model:show="viewerVisible" :photos="viewerPhotos" :initial-index="viewerIndex" />
  </div>
</template>
<style scoped>
.timeline-view {
  min-height: calc(100vh - var(--top-bar-height));
}

.timeline-layout {
  display: flex;
  min-height: calc(100vh - var(--top-bar-height));
}

/* ===== Left timeline axis ===== */
.timeline-axis {
  width: 180px;
  flex-shrink: 0;
  padding: 16px 0 16px 16px;
  position: sticky;
  top: var(--top-bar-height);
  height: calc(100vh - var(--top-bar-height));
  overflow-y: auto;
  border-right: 0.5px solid var(--separator);
}

.timeline-axis::-webkit-scrollbar { display: none; }

.scroll-top-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  margin-bottom: 12px;
  margin-left: 8px;
  transition: all 0.15s;
}

.scroll-top-btn:hover {
  background: var(--accent);
  color: white;
}

.axis-line {
  position: absolute;
  left: 23px;
  top: 60px;
  bottom: 20px;
  width: 2px;
  background: var(--separator);
  border-radius: 1px;
}

.axis-year-group {
  margin-bottom: 16px;
}

.axis-year {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  padding: 8px 0 6px 28px;
  position: relative;
}

.axis-year::before {
  content: '';
  position: absolute;
  left: 14px;
  top: 14px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  border: 2px solid var(--bg-primary);
}

.axis-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px 6px 28px;
  cursor: pointer;
  position: relative;
  border-radius: 8px;
  transition: background 0.15s;
}

.axis-node:hover {
  background: var(--bg-tertiary);
}

.axis-node.active {
  background: rgba(10, 132, 255, 0.12);
}

.axis-dot {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  transition: background 0.15s;
}

.axis-node.active .axis-dot {
  background: var(--accent);
  width: 8px;
  height: 8px;
}

.axis-month {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.axis-node.active .axis-month {
  color: var(--accent);
  font-weight: 600;
}

.axis-count {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: auto;
}

/* ===== Right content ===== */
.timeline-content {
  flex: 1;
  min-width: 0;
  padding: 0 16px 20px;
}

.timeline-layout.is-empty .timeline-axis {
  display: none;
}

.timeline-layout.is-empty .timeline-content {
  display: grid;
  min-height: calc(100vh - var(--top-bar-height));
  padding: 0 20px calc(84px + var(--safe-bottom));
  place-items: center;
}

.timeline-year {
  margin-bottom: 8px;
}

.timeline-month-section {
  margin-bottom: 16px;
}

.month-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 12px 0 8px;
  position: sticky;
  top: var(--top-bar-height);
  z-index: 5;
  background: var(--bg-primary);
}

.month-label {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.month-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.photo-grid-compact {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 2px;
}

.grid-item {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 4px;
  cursor: pointer;
  background: var(--bg-tertiary);
}

.grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}

.grid-item:hover .grid-img {
  transform: scale(1.05);
}

.grid-placeholder {
  width: 100%;
  height: 100%;
  background: var(--bg-tertiary);
}

.rating-badge {
  position: absolute;
  bottom: 4px;
  right: 4px;
  font-size: 10px;
  color: #ffcc00;
  text-shadow: 0 1px 3px rgba(0,0,0,0.8);
}

/* Loading & empty */
.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary);
  text-align: center;
}

.empty-icon { color: var(--text-tertiary); }
.empty-state p { font-size: 14px; }

/* Responsive */
@media (max-width: 768px) {
  .timeline-axis {
    width: 120px;
    padding-left: 8px;
  }

  .axis-year { padding-left: 24px; font-size: 15px; }
  .axis-year::before { left: 10px; }
  .axis-node { padding-left: 24px; }
  .axis-dot { left: 12px; }
  .axis-line { left: 15px; }

  .photo-grid-compact { grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); }
}
</style>
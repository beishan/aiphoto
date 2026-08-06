<script setup lang="ts">
import { onMounted, ref, onUnmounted, nextTick, computed } from 'vue'
import http from '@/api/http'
import type { Photo, TimelineData } from '@/types'
import PhotoViewer from '@/components/PhotoViewer.vue'

const timeline = ref<TimelineData>({})
const loading = ref(false)
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

// Timeline axis
const scrollContainer = ref<HTMLElement | null>(null)
const activeYearMonth = ref<string>('')
const timelineNodes = ref<{ year: number; month: number; count: number; key: string }[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await http.get('/timeline')
    timeline.value = data
    buildTimelineNodes()
    await nextTick()
    setupScrollObserver()
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})

function buildTimelineNodes() {
  const nodes: { year: number; month: number; count: number; key: string }[] = []
  for (const [year, months] of Object.entries(timeline.value)) {
    for (const [month, photos] of Object.entries(months as Record<string, Photo[]>)) {
      nodes.push({
        year: Number(year),
        month: Number(month),
        count: photos.length,
        key: `ym-${year}-${month}`,
      })
    }
  }
  // Sort: newest first
  nodes.sort((a, b) => b.year - a.year || b.month - a.month)
  timelineNodes.value = nodes
  if (nodes.length > 0) {
    activeYearMonth.value = nodes[0].key
  }
}

function setupScrollObserver() {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
}

function onScroll() {
  const sections = document.querySelectorAll('[data-ym-key]')
  for (let i = sections.length - 1; i >= 0; i--) {
    const el = sections[i] as HTMLElement
    const rect = el.getBoundingClientRect()
    if (rect.top <= 120) {
      activeYearMonth.value = el.dataset.ymKey || ''
      break
    }
  }
}

function scrollToNode(key: string) {
  const el = document.querySelector(`[data-ym-key="${key}"]`) as HTMLElement
  if (el) {
    const top = el.offsetTop - 60
    window.scrollTo({ top, behavior: 'smooth' })
    activeYearMonth.value = key
  }
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openViewer(photos: Photo[], index: number) {
  viewerPhotos.value = photos
  viewerIndex.value = index
  viewerVisible.value = true
}

const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']

// Group nodes by year for display
const groupedNodes = computed(() => {
  const groups: Record<number, typeof timelineNodes.value> = {}
  for (const node of timelineNodes.value) {
    if (!groups[node.year]) groups[node.year] = []
    groups[node.year].push(node)
  }
  return groups
})
</script>

<template>
  <div class="timeline-view">
    <div class="timeline-layout">
      <!-- Left timeline axis -->
      <aside class="timeline-axis">
        <button class="scroll-top-btn" @click="scrollToTop" title="返回顶部">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z" />
          </svg>
        </button>
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
        <div v-if="loading" class="loading-state">
          <div class="loading-spinner"></div>
        </div>

        <!-- Empty -->
        <div v-else-if="timelineNodes.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
            <path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z" />
          </svg>
          <h3>时间线暂无照片</h3>
          <p>在照片详情页中点击"添加到时间线"来展示照片</p>
        </div>

        <!-- Timeline -->
        <div v-else>
          <div v-for="(months, year) in timeline" :key="year" class="timeline-year">
            <div v-for="(photos, month) in months" :key="`${year}-${month}`"
              :data-ym-key="`ym-${year}-${month}`"
              class="timeline-month-section">
              <div class="month-header">
                <span class="month-label">{{ year }}年 {{ monthNames[(month as number) - 1] }}</span>
                <span class="month-count">{{ photos.length }} 张</span>
              </div>

              <div class="photo-grid-compact">
                <div
                  v-for="(photo, index) in photos"
                  :key="photo.id"
                  class="grid-item"
                  @click="openViewer(photos, index)"
                >
                  <img
                    v-if="photo.thumbnailUrl"
                    :src="photo.thumbnailUrl"
                    :alt="photo.originalFilename || ''"
                    loading="lazy"
                    class="grid-img"
                  />
                  <div v-else class="grid-placeholder"></div>
                  <div v-if="photo.rating" class="rating-badge">{{ '★'.repeat(photo.rating) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="viewerPhotos"
      :initial-index="viewerIndex"
    />
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 12px;
  color: var(--text-secondary);
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

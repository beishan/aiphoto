<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import type { Photo, TimelineData } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const timeline = ref<TimelineData>({})
const loading = ref(false)
const viewerVisible = ref(false)
const viewerPhotos = ref<Photo[]>([])
const viewerIndex = ref(0)

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await http.get('/timeline')
    timeline.value = data
  } finally {
    loading.value = false
  }
})

function openViewer(photos: Photo[], index: number) {
  viewerPhotos.value = photos
  viewerIndex.value = index
  viewerVisible.value = true
}

const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
</script>

<template>
  <div class="timeline-view">
    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="Object.keys(timeline).length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z" />
      </svg>
      <h3>暂无时间线数据</h3>
    </div>

    <!-- Timeline -->
    <div v-else class="timeline-content">
      <div v-for="(months, year) in timeline" :key="year" class="timeline-year">
        <div class="year-header">
          <span class="year-label">{{ year }}</span>
        </div>

        <div v-for="(photos, month) in months" :key="`${year}-${month}`" class="timeline-month">
          <div class="month-label">
            <span class="month-name">{{ monthNames[(month as number) - 1] }}</span>
            <span class="month-count">{{ photos.length }} 张</span>
          </div>

          <div class="photo-grid-compact">
            <PhotoCard
              v-for="(photo, index) in photos"
              :key="photo.id"
              :photo="photo"
              @click="openViewer(photos, index)"
            />
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
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

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

@keyframes spin {
  to { transform: rotate(360deg); }
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

.empty-icon {
  color: var(--text-tertiary);
}

.timeline-year {
  margin-bottom: 8px;
}

.year-header {
  position: sticky;
  top: var(--top-bar-height);
  z-index: 10;
  padding: 12px 16px 8px;
  background: var(--bg-primary);
}

.year-label {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.timeline-month {
  margin-bottom: 4px;
}

.month-label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 16px 6px;
}

.month-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.month-count {
  font-size: 14px;
  color: var(--text-secondary);
}
</style>

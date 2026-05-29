<script setup lang="ts">
import { computed } from 'vue'
import type { Photo } from '@/types'

const props = defineProps<{
  photo: Photo
}>()

const emit = defineEmits<{
  click: []
  'toggle-favorite': []
}>()

const thumbnailSrc = computed(() => {
  return props.photo.thumbnailUrl || '/placeholder.png'
})

const isVideo = computed(() => {
  return props.photo.mediaType === 'VIDEO'
})

const formattedDate = computed(() => {
  if (!props.photo.exifDate) return null
  return new Date(props.photo.exifDate).toLocaleDateString('zh-CN')
})
</script>

<template>
  <div class="photo-cell" @click="emit('click')">
    <img :src="thumbnailSrc" :alt="photo.note || ''" loading="lazy" class="photo-img" />
    <div v-if="isVideo" class="badge video-badge">
      <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
        <path d="M8 5v14l11-7z" />
      </svg>
    </div>
    <button class="badge fav-btn" :class="{ active: photo.favorite }" @click.stop="emit('toggle-favorite')">
      <svg v-if="photo.favorite" viewBox="0 0 24 24" fill="#ff453a" width="14" height="14">
        <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="2" width="14" height="14">
        <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.photo-cell {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  cursor: pointer;
  background: var(--bg-secondary);
}

.photo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: opacity 0.2s;
}

.photo-cell:active .photo-img {
  opacity: 0.8;
}

.badge {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 3px;
  color: white;
  font-size: 11px;
  font-weight: 500;
}

.fav-btn {
  bottom: 4px;
  left: 4px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s, transform 0.15s;
}

.photo-cell:hover .fav-btn,
.fav-btn.active {
  opacity: 1;
}

.fav-btn:hover {
  background: rgba(0, 0, 0, 0.5);
}

.fav-btn:active {
  transform: scale(0.9);
}

.fav-btn.active {
  background: rgba(255, 69, 58, 0.2);
}

.video-badge {
  bottom: 6px;
  left: 6px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

</style>

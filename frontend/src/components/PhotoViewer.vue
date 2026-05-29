<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { Photo } from '@/types'
import { usePhotoStore } from '@/stores/photoStore'

const props = defineProps<{
  show: boolean
  photos: Photo[]
  initialIndex: number
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const photoStore = usePhotoStore()
const currentIndex = ref(props.initialIndex)
const showInfo = ref(false)

const currentPhoto = computed(() => props.photos[currentIndex.value])

watch(() => props.initialIndex, (val) => {
  currentIndex.value = val
})

watch(() => props.show, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

function close() {
  emit('update:show', false)
}

function prev() {
  if (currentIndex.value > 0) {
    currentIndex.value--
  }
}

function next() {
  if (currentIndex.value < props.photos.length - 1) {
    currentIndex.value++
  }
}

async function toggleFavorite() {
  if (currentPhoto.value) {
    await photoStore.toggleFavorite(currentPhoto.value.id)
  }
}

async function setRating(rating: number) {
  if (currentPhoto.value) {
    await photoStore.setRating(currentPhoto.value.id, rating)
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (!props.show) return
  if (e.key === 'Escape') close()
  if (e.key === 'ArrowLeft') prev()
  if (e.key === 'ArrowRight') next()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="viewer">
      <div v-if="show" class="viewer-overlay" @click.self="close">
        <!-- Close button -->
        <button class="viewer-close" @click="close">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
          </svg>
        </button>

        <!-- Image area -->
        <div class="viewer-image-area">
          <button class="nav-btn prev-btn" @click="prev" :disabled="currentIndex <= 0">
            <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </button>

          <div class="viewer-image-wrapper">
            <img v-if="currentPhoto" :src="currentPhoto.originalUrl || currentPhoto.thumbnailUrl || '/placeholder.png'" class="viewer-img" />
          </div>

          <button class="nav-btn next-btn" @click="next" :disabled="currentIndex >= photos.length - 1">
            <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
              <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z" />
            </svg>
          </button>
        </div>

        <!-- Bottom toolbar -->
        <div class="viewer-toolbar glass">
          <div class="toolbar-left">
            <button class="toolbar-btn" @click="toggleFavorite">
              <svg v-if="currentPhoto?.favorite" viewBox="0 0 24 24" fill="#ff453a" width="22" height="22">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z" />
              </svg>
            </button>
          </div>

          <span class="counter">{{ currentIndex + 1 }} / {{ photos.length }}</span>

          <div class="toolbar-right">
            <button class="toolbar-btn" @click="showInfo = !showInfo">
              <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Info panel -->
        <Transition name="slide-up">
          <div v-if="showInfo && currentPhoto" class="info-panel glass">
            <div class="info-row" v-if="currentPhoto.exifDate">
              <span class="info-label">拍摄时间</span>
              <span class="info-value">{{ new Date(currentPhoto.exifDate).toLocaleString('zh-CN') }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.gpsLat">
              <span class="info-label">位置</span>
              <span class="info-value">{{ currentPhoto.gpsLat.toFixed(4) }}, {{ currentPhoto.gpsLng?.toFixed(4) }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.width">
              <span class="info-label">尺寸</span>
              <span class="info-value">{{ currentPhoto.width }} x {{ currentPhoto.height }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.aiCaption">
              <span class="info-label">AI 描述</span>
              <span class="info-value">{{ currentPhoto.aiCaption }}</span>
            </div>
            <div class="info-row" v-if="currentPhoto.note">
              <span class="info-label">备注</span>
              <span class="info-value">{{ currentPhoto.note }}</span>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.viewer-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: #000;
  display: flex;
  flex-direction: column;
}

.viewer-close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  color: white;
  border: none;
  cursor: pointer;
  transition: background 0.2s;
}

.viewer-close:hover {
  background: rgba(255, 255, 255, 0.25);
}

.viewer-image-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.viewer-image-wrapper {
  max-width: 100%;
  max-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.viewer-img {
  max-width: 90vw;
  max-height: 80vh;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  color: white;
  border: none;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
  z-index: 5;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.nav-btn:disabled {
  opacity: 0.2;
  cursor: default;
}

.prev-btn { left: 16px; }
.next-btn { right: 16px; }

.viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  padding-bottom: calc(12px + var(--safe-bottom));
  border-top: 0.5px solid var(--glass-border);
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 80px;
}

.toolbar-right {
  justify-content: flex-end;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  padding: 4px;
  border-radius: 8px;
  transition: opacity 0.2s;
}

.toolbar-btn:hover {
  opacity: 0.7;
}

.counter {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

.info-panel {
  position: absolute;
  bottom: 80px;
  left: 16px;
  right: 16px;
  max-width: 480px;
  margin: 0 auto;
  border-radius: var(--radius-lg);
  padding: 16px;
  border: 0.5px solid var(--glass-border);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 6px 0;
}

.info-row:not(:last-child) {
  border-bottom: 0.5px solid var(--glass-border);
}

.info-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
  margin-right: 16px;
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

/* Transitions */
.viewer-enter-active,
.viewer-leave-active {
  transition: opacity 0.25s ease;
}
.viewer-enter-from,
.viewer-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .nav-btn {
    display: none;
  }

  .viewer-img {
    max-width: 100vw;
    max-height: 75vh;
  }
}
</style>

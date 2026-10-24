<script setup lang="ts">
import { computed, ref } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import justifiedLayout from 'justified-layout'
import PhotoCard from './PhotoCard.vue'
import type { Photo } from '@/types'

const props = defineProps<{
  photos: Photo[]
  startIndex: number
  count: number
  rowHeight: number
  selectMode: boolean
  selectedIds: Set<number>
}>()
const emit = defineEmits<{
  open: [index: number]
  'toggle-favorite': [id: number]
  'toggle-select': [id: number]
}>()
const container = ref<HTMLElement | null>(null)
const width = ref(0)
useResizeObserver(container, entries => { width.value = entries[0]?.contentRect.width || 0 })
const items = computed(() => props.photos.slice(props.startIndex, props.startIndex + props.count))
const layout = computed(() => {
  if (width.value <= 0) return { boxes: [], containerHeight: 0 }
  return justifiedLayout(items.value.map(photo =>
    photo.width && photo.height && photo.width > 0 && photo.height > 0 ? photo.width / photo.height : 1), {
    containerWidth: width.value,
    containerPadding: 0,
    boxSpacing: 4,
    targetRowHeight: Math.min(props.rowHeight, width.value * 0.65),
    targetRowHeightTolerance: 0.2,
    showWidows: true,
  })
})
</script>

<template>
  <div ref="container" class="justified-photo-grid" :style="{ height: `${layout.containerHeight}px` }">
    <PhotoCard
      v-for="(box, index) in layout.boxes"
      :key="items[index].id"
      class="justified-photo"
      :style="{ left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px`, height: `${box.height}px` }"
      :photo="items[index]"
      :select-mode="selectMode"
      :selected="selectedIds.has(items[index].id)"
      @click="emit('open', startIndex + index)"
      @toggle-favorite="emit('toggle-favorite', items[index].id)"
      @toggle-select="emit('toggle-select', items[index].id)"
    />
  </div>
</template>

<style scoped>
.justified-photo-grid { position: relative; width: 100%; }
.justified-photo-grid :deep(.justified-photo) {
  position: absolute;
  aspect-ratio: auto;
  border-radius: 0;
}
.justified-photo-grid :deep(.photo-img) { display: block; }
</style>

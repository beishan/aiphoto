<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { queueStaticFrame } from '@/utils/staticFrameQueue'
const props = defineProps<{ src: string; alt: string }>()
const emit = defineEmits<{ error: [] }>()
const canvas = ref<HTMLCanvasElement | null>(null)
let observer: IntersectionObserver | undefined
let controller: AbortController | undefined
let visible = false
let generation = 0
function load() {
  const request = ++generation
  controller?.abort()
  const current = new AbortController()
  controller = current
  const src = props.src
  void queueStaticFrame(async () => {
    if (current.signal.aborted) return
    try {
      const response = await fetch(src, { signal: current.signal })
      if (!response.ok) throw new Error('Image unavailable')
      const blob = await response.blob()
      if (current.signal.aborted) return
      const bitmap = await createImageBitmap(blob)
      try {
        const target = canvas.value
        if (!target || request !== generation || current.signal.aborted) return
        const scale = Math.min(1, 400 / Math.max(bitmap.width, bitmap.height))
        target.width = Math.max(1, Math.round(bitmap.width * scale))
        target.height = Math.max(1, Math.round(bitmap.height * scale))
        const context = target.getContext('2d')
        if (!context) throw new Error('Canvas unavailable')
        context.drawImage(bitmap, 0, 0, target.width, target.height)
      } finally { bitmap.close() }
    } catch {
      if (!current.signal.aborted && request === generation) emit('error')
    }
  })
}
watch(() => props.src, () => { if (visible) load() })
onMounted(() => {
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) {
      visible = true
      observer?.disconnect()
      load()
    }
  }, { rootMargin: '200px' })
  if (canvas.value) observer.observe(canvas.value)
})
onBeforeUnmount(() => { generation++; controller?.abort(); observer?.disconnect() })
</script>

<template><canvas ref="canvas" role="img" :aria-label="alt" /></template>

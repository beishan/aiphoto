<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  file: File | null
  uploading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [file: File]
  cancel: []
}>()

const OUTPUT_SIZE = 512
const previewUrl = ref('')
const cropStage = ref<HTMLElement | null>(null)
const stageSize = ref(360)
const imageElement = ref<HTMLImageElement | null>(null)
const imageWidth = ref(0)
const imageHeight = ref(0)
const zoom = ref(1)
const rotation = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, offsetX: 0, offsetY: 0 })

const isQuarterTurn = computed(() => Math.abs(rotation.value / 90) % 2 === 1)
const rotatedWidth = computed(() => isQuarterTurn.value ? imageHeight.value : imageWidth.value)
const rotatedHeight = computed(() => isQuarterTurn.value ? imageWidth.value : imageHeight.value)
const baseScale = computed(() => {
  if (!rotatedWidth.value || !rotatedHeight.value) return 1
  return Math.max(stageSize.value / rotatedWidth.value, stageSize.value / rotatedHeight.value)
})
const displayWidth = computed(() => imageWidth.value * baseScale.value)
const displayHeight = computed(() => imageHeight.value * baseScale.value)
const imageStyle = computed(() => ({
  width: `${displayWidth.value}px`,
  height: `${displayHeight.value}px`,
  transform: `translate(-50%, -50%) translate(${offsetX.value}px, ${offsetY.value}px) rotate(${rotation.value}deg) scale(${zoom.value})`,
}))

function revokePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function resetAdjustments() {
  zoom.value = 1
  rotation.value = 0
  offsetX.value = 0
  offsetY.value = 0
}

function measureStage() {
  if (cropStage.value?.clientWidth) stageSize.value = cropStage.value.clientWidth
}

function clampOffset() {
  const width = rotatedWidth.value * baseScale.value * zoom.value
  const height = rotatedHeight.value * baseScale.value * zoom.value
  const maxX = Math.max(0, (width - stageSize.value) / 2)
  const maxY = Math.max(0, (height - stageSize.value) / 2)
  offsetX.value = Math.max(-maxX, Math.min(maxX, offsetX.value))
  offsetY.value = Math.max(-maxY, Math.min(maxY, offsetY.value))
}

watch(() => props.file, (file) => {
  revokePreview()
  resetAdjustments()
  imageWidth.value = 0
  imageHeight.value = 0
  if (file) previewUrl.value = URL.createObjectURL(file)
}, { immediate: true })

watch(() => props.modelValue, (visible) => {
  if (visible) requestAnimationFrame(() => {
    measureStage()
    clampOffset()
  })
})

watch([zoom, rotation], clampOffset)

function handleImageLoad(event: Event) {
  const image = event.target as HTMLImageElement
  imageElement.value = image
  imageWidth.value = image.naturalWidth
  imageHeight.value = image.naturalHeight
  measureStage()
  clampOffset()
}

function startDrag(event: PointerEvent) {
  if (!imageWidth.value) return
  dragging.value = true
  dragStart.value = {
    x: event.clientX,
    y: event.clientY,
    offsetX: offsetX.value,
    offsetY: offsetY.value,
  }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function moveDrag(event: PointerEvent) {
  if (!dragging.value) return
  offsetX.value = dragStart.value.offsetX + event.clientX - dragStart.value.x
  offsetY.value = dragStart.value.offsetY + event.clientY - dragStart.value.y
  clampOffset()
}

function endDrag() {
  dragging.value = false
}

function rotateLeft() {
  rotation.value = (rotation.value - 90) % 360
}

function rotateRight() {
  rotation.value = (rotation.value + 90) % 360
}

function closeDialog() {
  if (props.uploading || !props.modelValue) return
  emit('update:modelValue', false)
  emit('cancel')
}

async function confirmCrop() {
  const image = imageElement.value
  if (!image || !imageWidth.value) {
    ElMessage.warning('图片仍在加载，请稍后再试')
    return
  }

  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT_SIZE
  canvas.height = OUTPUT_SIZE
  const context = canvas.getContext('2d')
  if (!context) {
    ElMessage.error('当前浏览器无法处理图片')
    return
  }

  const outputRatio = OUTPUT_SIZE / stageSize.value
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
  context.translate(
    OUTPUT_SIZE / 2 + offsetX.value * outputRatio,
    OUTPUT_SIZE / 2 + offsetY.value * outputRatio,
  )
  context.rotate(rotation.value * Math.PI / 180)
  const drawScale = baseScale.value * zoom.value * outputRatio
  context.scale(drawScale, drawScale)
  context.drawImage(image, -imageWidth.value / 2, -imageHeight.value / 2)

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png', 0.92))
  if (!blob) {
    ElMessage.error('头像生成失败，请重新选择图片')
    return
  }
  emit('confirm', new File([blob], 'avatar.png', { type: 'image/png' }))
}

onBeforeUnmount(revokePreview)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="调整头像"
    width="min(560px, calc(100vw - 28px))"
    :close-on-click-modal="!uploading"
    :close-on-press-escape="!uploading"
    :show-close="!uploading"
    class="avatar-crop-dialog"
    @close="closeDialog"
  >
    <div class="crop-content">
      <p class="crop-tip">拖动图片调整位置，圆形区域将作为你的新头像。</p>
      <div
        ref="cropStage"
        class="crop-stage"
        :class="{ dragging }"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
      >
        <img
          v-if="previewUrl"
          :src="previewUrl"
          :style="imageStyle"
          alt="头像裁剪预览"
          draggable="false"
          @load="handleImageLoad"
        />
        <div class="crop-mask" />
      </div>

      <div class="crop-controls">
        <div class="zoom-control">
          <span>缩放</span>
          <el-slider v-model="zoom" :min="1" :max="3" :step="0.01" :show-tooltip="false" />
          <span>{{ Math.round(zoom * 100) }}%</span>
        </div>
        <div class="adjust-actions">
          <el-button @click="rotateLeft">向左旋转</el-button>
          <el-button @click="rotateRight">向右旋转</el-button>
          <el-button @click="resetAdjustments">重置</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button :disabled="uploading" @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="confirmCrop">确认上传</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.crop-content {
  display: grid;
  justify-items: center;
  gap: 18px;
}

.crop-tip {
  width: 100%;
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.crop-stage {
  position: relative;
  width: min(360px, calc(100vw - 76px));
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 18px;
  background: #111827;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.crop-stage.dragging {
  cursor: grabbing;
}

.crop-stage img {
  position: absolute;
  top: 50%;
  left: 50%;
  max-width: none;
  transform-origin: center;
  pointer-events: none;
  will-change: transform;
}

.crop-mask {
  position: absolute;
  inset: 0;
  width: calc(100% - 8px);
  height: calc(100% - 8px);
  margin: 4px;
  border: 2px solid rgba(255, 255, 255, .92);
  border-radius: 50%;
  box-shadow: 0 0 0 999px rgba(4, 10, 22, .58);
  pointer-events: none;
}

.crop-controls {
  display: grid;
  width: 100%;
  gap: 14px;
}

.zoom-control {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.zoom-control span:last-child {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.adjust-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

@media (max-width: 480px) {
  .crop-content {
    gap: 14px;
  }

  .adjust-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }
}
</style>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Clock, Collection, Delete, DeleteFilled, PictureFilled, PriceTag, Search, Setting, UserFilled } from '@element-plus/icons-vue'

export type DockIconName = 'photo' | 'timeline' | 'tags' | 'albums' | 'baby' | 'search' | 'settings' | 'trashEmpty' | 'trashFull'
export type DockIconStyle = 'minimal' | 'macos26' | 'custom'

const props = defineProps<{ name: DockIconName; variant: DockIconStyle; customSrc?: string }>()
const icons: Record<DockIconName, Component> = {
  photo: PictureFilled,
  timeline: Clock,
  tags: PriceTag,
  albums: Collection,
  baby: UserFilled,
  search: Search,
  settings: Setting,
  trashEmpty: Delete,
  trashFull: DeleteFilled,
}
const icon = computed(() => icons[props.name])
</script>

<template>
  <span class="dock-glyph" :class="`dock-glyph--${variant}`">
    <img v-if="variant === 'custom' && customSrc" :src="customSrc" alt="" draggable="false" />
    <el-icon v-else><component :is="icon" /></el-icon>
  </span>
</template>

<style scoped>
.dock-glyph,.dock-glyph :deep(.el-icon),.dock-glyph :deep(svg),.dock-glyph img{display:block;width:100%;height:100%}.dock-glyph{display:grid;place-items:center}.dock-glyph img{object-fit:contain}.dock-glyph--minimal{color:var(--text-primary)}
</style>

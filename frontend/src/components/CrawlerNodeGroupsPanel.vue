<script setup lang="ts">
import type { CrawlerProxy } from '@/api/settingApi'
import SystemMihomoNodesPanel from './SystemMihomoNodesPanel.vue'
defineProps<{ proxy: CrawlerProxy | null }>()
const visible = defineModel<boolean>({ default: false })
</script>

<template>
  <el-dialog v-model="visible" append-to-body destroy-on-close :title="`${proxy?.name || 'Mihomo'} · 节点与备用组`" width="min(960px, 96vw)">
    <SystemMihomoNodesPanel v-if="visible && proxy" :key="proxy.id" :proxy="proxy" />
  </el-dialog>
</template>

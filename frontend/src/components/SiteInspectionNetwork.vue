<script setup lang="ts">
import { computed } from 'vue'
import type { InspectionProxy } from '@/api/crawlApi'

const props = defineProps<{
  mode: 'DEFAULT' | 'DIRECT' | 'SELECTED'
  proxyId: number | null
  proxies: InspectionProxy[]
  loading: boolean
}>()
const emit = defineEmits<{
  'update:mode': [value: 'DEFAULT' | 'DIRECT' | 'SELECTED']
  'update:proxyId': [value: number | null]
  inspect: []
  refresh: []
}>()
const selection = computed({
  get: () => props.mode === 'SELECTED' ? `proxy:${props.proxyId ?? ''}` : props.mode,
  set: (value: string) => {
    emit('update:mode', value.startsWith('proxy:') ? 'SELECTED' : value as 'DEFAULT' | 'DIRECT')
    emit('update:proxyId', value.startsWith('proxy:') ? Number(value.slice(6)) : null)
  },
})
const missingProxy = computed(() => props.mode === 'SELECTED'
  && !props.proxies.some(proxy => proxy.id === props.proxyId))
function onVisibleChange(visible: boolean) {
  if (visible) emit('refresh')
}
</script>

<template>
  <el-form-item label="测试与网站信息使用的代理">
    <div class="inspection-network">
      <div class="inspection-controls">
        <el-select v-model="selection" aria-label="测试与网站信息使用的代理" @visible-change="onVisibleChange">
          <el-option label="系统默认代理策略" value="DEFAULT" />
          <el-option label="直连" value="DIRECT" />
          <el-option v-if="missingProxy" label="原代理不可用，请重新选择" :value="selection" disabled />
          <el-option v-for="proxy in proxies" :key="proxy.id" :value="`proxy:${proxy.id}`"
            :label="`${proxy.name} · ${proxy.proxyType === 'MIHOMO' ? 'Mihomo' : 'HTTP'}${proxy.enabled ? '' : '（已停用）'}`"
            :disabled="!proxy.enabled" />
        </el-select>
        <el-button :loading="loading" @click="emit('inspect')">获取网站信息</el-button>
      </div>
      <p class="inspection-hint">用于测试解析规则和获取网页标题、简介。采集任务的代理由执行器配置。Mihomo 使用当前 HTTP 入口出口。</p>
    </div>
  </el-form-item>
</template>

<style scoped>
.inspection-network { width: 100%; }
.inspection-controls { display: flex; flex-wrap: wrap; gap: 12px; }
.inspection-controls .el-select { flex: 1; min-width: 220px; }
.inspection-hint { margin: 8px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
@media (max-width: 480px) {
  .inspection-controls .el-select { min-width: 0; flex-basis: 100%; }
}
</style>

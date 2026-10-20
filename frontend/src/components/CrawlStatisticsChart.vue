<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type EChartsType } from 'echarts/core'
import { LineChart, BarChart, TreeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, BarChart, TreeChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, AriaComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; label: string; tall?: boolean }>()
const host = ref<HTMLDivElement>()
let chart: EChartsType | undefined
let resize: ResizeObserver | undefined
let theme: MutationObserver | undefined
function render() {
  if (!chart || !host.value) return
  const css = getComputedStyle(host.value)
  chart.setOption({
    color: ['#409eff', '#36b894', '#ef6b73', '#e6a23c', '#9299a5'],
    textStyle: { color: css.getPropertyValue('--text-primary').trim() || '#606266', fontFamily: 'inherit' },
    backgroundColor: 'transparent',
    aria: { enabled: true, description: props.label },
    ...props.option,
  }, { notMerge: true })
}
onMounted(() => {
  chart = init(host.value!)
  resize = new ResizeObserver(() => chart?.resize())
  resize.observe(host.value!)
  theme = new MutationObserver(render)
  theme.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class', 'style'] })
  render()
})
watch(() => props.option, render, { deep: true })
onUnmounted(() => {
  resize?.disconnect()
  theme?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="host" class="statistics-chart" :class="{ tall }" role="img" :aria-label="label" />
</template>

<style scoped>
.statistics-chart { width: 100%; height: 330px; min-width: 0; }
.statistics-chart.tall { height: 460px; }
</style>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import CrawlStatisticsChart from './CrawlStatisticsChart.vue'
import { crawlStatisticsApi, type CrawlStatistics, type CrawlStatisticsCounts, type StatisticsGranularity } from '@/api/crawlStatisticsApi'

const granularity = ref<StatisticsGranularity>('DAY')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const error = ref('')
const statistics = ref<CrawlStatistics | null>(null)
let requestId = 0
const grainOptions = [{ label: '按天', value: 'DAY' }, { label: '按小时', value: 'HOUR' }, { label: '按分钟', value: 'MINUTE' }]
function localTime(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
function resetRange() {
  const end = new Date()
  end.setSeconds(0, 0)
  if (granularity.value === 'DAY') { end.setHours(0, 0, 0, 0); end.setDate(end.getDate() + 1) }
  else if (granularity.value === 'HOUR') { end.setMinutes(0); end.setHours(end.getHours() + 1) }
  else end.setMinutes(end.getMinutes() + 1)
  const start = new Date(end)
  if (granularity.value === 'DAY') start.setDate(start.getDate() - 30)
  else if (granularity.value === 'HOUR') start.setHours(start.getHours() - 24)
  else start.setMinutes(start.getMinutes() - 60)
  range.value = [localTime(start), localTime(end)]
}
async function load() {
  const request = ++requestId
  error.value = ''
  statistics.value = null
  if (!range.value || range.value[0] >= range.value[1]) {
    error.value = '请选择有效的起止时间'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const { data } = await crawlStatisticsApi.get(granularity.value, range.value[0], range.value[1])
    if (request === requestId) statistics.value = data
  } catch (failure: any) {
    if (request === requestId) error.value = failure?.response?.data?.message || '统计数据加载失败，请重试'
  } finally {
    if (request === requestId) loading.value = false
  }
}
defineExpose({ refresh: load })
watch(granularity, () => { resetRange(); void load() })
onMounted(() => { resetRange(); void load() })
onUnmounted(() => { requestId++ })
const counts = computed(() => statistics.value?.total)
function duration(ms: number | undefined, samples = counts.value?.durationSamples ?? 0) {
  if (ms == null || !samples) return '—'
  if (ms < 1000) return `${Math.round(ms)} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)} 秒`
  if (ms < 3600000) return `${(ms / 60000).toFixed(2)} 分钟`
  return `${(ms / 3600000).toFixed(2)} 小时`
}
const cards = computed(() => [
  { title: '新增页面', value: counts.value?.newPages ?? 0, suffix: '页', note: '网站首次发现的页面' },
  { title: '新增图片', value: counts.value?.newImages ?? 0, suffix: '张', note: '新发现的图片链接（任务内去重）' },
  { title: '采集页面', value: counts.value?.pagesCollected ?? 0, suffix: '页', note: '已结束任务所包含的页面' },
  { title: '采集图片', value: counts.value?.imagesSuccess ?? 0, suffix: '张', note: `失败 ${counts.value?.imagesFailed ?? 0} · 跳过 ${counts.value?.imagesSkipped ?? 0}` },
  { title: '平均采集耗时', value: duration(counts.value?.durationAverageMillis), suffix: '', note: `${counts.value?.durationSamples ?? 0} 张已结束图片有耗时记录` },
  { title: '累计采集耗时', value: duration(counts.value?.durationTotalMillis), suffix: '', note: `最短 ${duration(counts.value?.durationMinMillis)} · 最长 ${duration(counts.value?.durationMaxMillis)}` },
])
const empty = computed(() => counts.value && counts.value.newPages + counts.value.newImages
  + counts.value.pagesCollected + counts.value.imagesSuccess + counts.value.imagesFailed + counts.value.imagesSkipped === 0)
const labels = computed(() => statistics.value?.buckets.map(bucket => {
  const time = bucket.time.replace('T', ' ')
  return statistics.value?.granularity === 'DAY' ? time.slice(0, 10) : time.slice(0, 16)
}) ?? [])
type Metric = keyof CrawlStatisticsCounts
function trend(series: { name: string; key: Metric; type?: 'line' | 'bar'; stack?: string; seconds?: boolean; timing?: boolean }[], unit = '数量'): EChartsCoreOption {
  return {
    tooltip: { trigger: 'axis', renderMode: 'richText', confine: true },
    legend: { type: 'scroll', top: 0 },
    grid: { left: 65, right: 24, top: 55, bottom: 85 },
    xAxis: { type: 'category', data: labels.value, axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', name: unit, minInterval: unit === '数量' ? 1 : undefined },
    dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 8, height: 20 }],
    series: series.map(series => ({
      name: series.name, type: series.type ?? 'line', stack: series.stack,
      showSymbol: labels.value.length < 80, connectNulls: false,
      emphasis: { focus: 'series' },
      data: statistics.value?.buckets.map(bucket => {
        if (series.timing && !bucket.counts.durationSamples) return null
        const value = bucket.counts[series.key] as number
        return series.seconds ? Math.round(value / 10) / 100 : value
      }) ?? [],
    })),
  }
}
const growthChart = computed(() => trend([{ name: '新增页面', key: 'newPages' }, { name: '新增图片', key: 'newImages' }]))
const pageSeries = [
  { name: '成功', key: 'pagesSuccess' }, { name: '部分成功', key: 'pagesPartial' },
  { name: '失败', key: 'pagesFailed' }, { name: '已取消', key: 'pagesCancelled' }, { name: '已跳过', key: 'pagesSkipped' },
] as const
const pageChart = computed(() => trend(pageSeries.map(s => ({ ...s, type: 'bar', stack: 'pages' }))))
const imageChart = computed(() => trend([
  { name: '采集成功', key: 'imagesSuccess' }, { name: '采集失败', key: 'imagesFailed' }, { name: '跳过', key: 'imagesSkipped' },
]))
const durationChart = computed(() => trend([
  { name: '平均耗时', key: 'durationAverageMillis', seconds: true, timing: true },
  { name: '最短耗时', key: 'durationMinMillis', seconds: true, timing: true },
  { name: '最长耗时', key: 'durationMaxMillis', seconds: true, timing: true },
], '秒'))
const histogram = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis', renderMode: 'richText', confine: true },
  grid: { left: 65, right: 24, top: 40, bottom: 45 },
  xAxis: { type: 'category', data: ['< 1 秒', '1–5 秒', '5–15 秒', '15–60 秒', '≥ 60 秒'] },
  yAxis: { type: 'value', name: '图片数', minInterval: 1 },
  series: [{ type: 'bar', name: '已结束图片', barMaxWidth: 55, data: counts.value?.durationBands ?? [] }],
}))
const treeChart = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'item', renderMode: 'richText', confine: true },
  series: [{
    type: 'tree', roam: true, top: 30, bottom: 30, left: '10%', right: '22%',
    symbolSize: 9, initialTreeDepth: 2, expandAndCollapse: true,
    label: { position: 'left', verticalAlign: 'middle', align: 'right', fontSize: 12 },
    leaves: { label: { position: 'right', align: 'left' } },
    data: [{ name: `采集页面 ${counts.value?.pagesCollected ?? 0}`, value: counts.value?.pagesCollected ?? 0,
      children: statistics.value?.sites.filter(site => site.counts.pagesCollected > 0).map(site => ({
        name: `${site.siteName}（${site.counts.pagesCollected}）`, value: site.counts.pagesCollected,
        children: pageSeries.filter(s => site.counts[s.key] > 0).map(s => ({ name: `${s.name} ${site.counts[s.key]}`, value: site.counts[s.key] })),
      })) ?? [],
    }],
  }],
}))
const charts = computed(() => [
  { title: '新增页面与图片趋势', label: '按时间统计新增页面和图片链接', option: growthChart.value },
  { title: '页面采集结果', label: '按时间统计成功、部分成功、失败、取消和跳过的页面', option: pageChart.value },
  { title: '图片采集趋势', label: '按时间统计图片采集成功、失败和跳过数', option: imageChart.value },
  { title: '图片采集耗时趋势', label: '按时间统计图片采集平均、最短和最长耗时，单位秒', option: durationChart.value },
  { title: '图片采集耗时分布', label: '按耗时区间统计已结束的图片数', option: histogram.value },
])
</script>

<template>
  <section class="statistics-panel" role="tabpanel" aria-label="采集统计">
    <el-card class="surface-card">
      <div class="statistics-heading"><div><h2>采集统计</h2><p>查看新增内容、采集结果与图片耗时。</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
      <div class="statistics-filters">
        <el-segmented v-model="granularity" :options="grainOptions" aria-label="统计日期维度" />
        <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" :clearable="false" />
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>
      <p class="statistics-hint">切换维度默认查看最近 30 天 / 24 小时 / 60 分钟；可自选范围，最多 1440 个时间段。统计范围包含开始时间、不包含结束时间。<template v-if="statistics">统计时区：{{ statistics.timezone }}。</template></p>
    </el-card>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-loading="loading" class="statistics-body" :aria-busy="loading">
      <template v-if="statistics">
        <div class="statistics-metrics">
          <el-card v-for="card in cards" :key="card.title" class="surface-card"><div class="stat-title">{{ card.title }}</div><div class="stat-value">{{ typeof card.value === 'number' ? card.value.toLocaleString() : card.value }} <small>{{ card.suffix }}</small></div><p>{{ card.note }}</p></el-card>
        </div>
        <el-empty v-if="empty" description="所选时间范围内暂无采集统计数据，请调整时间范围" />
        <div v-else class="statistics-charts">
          <el-card v-for="chart in charts" :key="chart.title" class="surface-card"><template #header><h3>{{ chart.title }}</h3></template><CrawlStatisticsChart :option="chart.option" :label="chart.label" /></el-card>
          <el-card class="surface-card"><template #header><h3>网站与页面采集结果</h3><p>可折叠节点，拖动或缩放查看。</p></template><CrawlStatisticsChart :option="treeChart" label="按网站展示页面成功、部分成功、失败等结果的树状图" tall /></el-card>
        </div>
        <el-card class="statistics-definitions surface-card">
          <h3>统计口径</h3>
          <p>时间使用当前设备时区 {{ statistics.timezone }}。新增页面按网站首次发现时间计数；新增图片按图片链接创建时间计数，同一链接在不同任务中可能分别计数。</p>
          <p>采集页面按任务结束时间统计任务内选中的页面，排除已委派给其他任务的副本，结果沿用任务状态；重试中的任务暂不计入，重试结束后按最新结果和时间统计。</p>
          <p>采集图片数为下载成功的图片，包含后续已入库、删除或去重标记的记录。图片按本次采集结束时间计数；缺少历史计时的记录以任务结束时间归属。</p>
          <p>耗时统计包含有完整计时记录的成功及失败图片，覆盖请求等待、重试和图片处理；排除运行中、已中断、跳过及缺少计时的记录。累计耗时为各图片耗时之和。统计基于现存记录，物理删除及重试会影响历史结果。</p>
        </el-card>
      </template>
      <div v-else-if="loading" class="statistics-placeholder">正在汇总统计数据…</div>
    </div>
  </section>
</template>

<style scoped>
.statistics-panel, .statistics-body { display: grid; gap: 20px; min-width: 0; }
.statistics-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
h2, h3 { margin: 0; color: var(--text-primary); }
h2 { font-size: 22px; } h3 { font-size: 16px; }
p { color: var(--text-secondary); line-height: 1.7; font-size: 13px; margin: 8px 0 0; }
.statistics-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 18px; }
.statistics-filters :deep(.el-date-editor) { max-width: 100%; flex-grow: 0; }
.statistics-hint { margin-top: 12px; }
.statistics-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.stat-title { color: var(--text-secondary); font-size: 14px; }
.stat-value { margin-top: 12px; font-size: 28px; font-weight: 650; color: var(--text-primary); }
.stat-value small { font-size: 13px; font-weight: 400; }
.statistics-charts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.statistics-charts :deep(.el-card__body) { padding: 12px; }
.statistics-charts :deep(.el-card) { min-width: 0; }
.statistics-placeholder { min-height: 260px; display: grid; place-items: center; color: var(--text-secondary); }
@media (max-width: 1050px) { .statistics-charts { grid-template-columns: 1fr; } }
@media (max-width: 650px) { .statistics-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .stat-value { font-size: 23px; } }
</style>

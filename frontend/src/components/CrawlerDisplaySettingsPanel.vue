<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useCrawlerImageVisibility } from '@/composables/useCrawlerImageVisibility'

const { showCrawlerImages, setShowCrawlerImages } = useCrawlerImageVisibility()
function updateVisibility(value: string | number | boolean) {
  try {
    setShowCrawlerImages(value === true)
    ElMessage.success('爬虫图片显示设置已保存')
  } catch (error: any) {
    ElMessage.error(error.message || '设置保存失败')
  }
}
</script>

<template>
  <section class="panel-card crawler-display-settings">
    <h2>爬虫图片显示</h2>
    <div class="setting-row">
      <div>
        <strong>显示爬虫页面图片</strong>
        <p>关闭后，所有爬虫页面均不加载缩略图和大图；文件名、尺寸、下载成功或失败状态及失败原因仍可查看。</p>
        <p>仅对当前账户生效，自动保存在当前浏览器中，不影响后台采集和下载。</p>
      </div>
      <el-switch :model-value="showCrawlerImages" active-text="显示" inactive-text="不显示" @change="updateVisibility" />
    </div>
  </section>
</template>

<style scoped>
.crawler-display-settings { margin-bottom: 20px; }
h2 { margin: 0 0 16px; font-size: 18px; }
.setting-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
p { color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
</style>

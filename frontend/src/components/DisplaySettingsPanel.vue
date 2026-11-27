<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { hideImages, setHideImages } from '@/composables/imageVisibility'

const saving = ref(false)

async function updateVisibility(value: string | number | boolean) {
  saving.value = true
  try {
    await setHideImages(value === true)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '显示设置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="panel-card">
    <h2>显示设置</h2>
    <div class="setting-row">
      <div>
        <strong>全局隐藏图片</strong>
        <p>默认开启。开启后，各页面仅显示图片占位，浏览器不会请求缩略图和原图。设置随当前账户保存。</p>
      </div>
      <el-switch
        aria-label="全局隐藏图片"
        :model-value="hideImages"
        :loading="saving"
        :disabled="saving"
        @change="updateVisibility"
      />
    </div>
  </section>
</template>

<style scoped>
h2 { margin: 0 0 16px; font-size: 18px; }
.setting-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
p { color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
</style>

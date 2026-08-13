<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import http from '@/api/http'
import AvatarCropDialog from '@/components/AvatarCropDialog.vue'
import { applySiteFavicon, faviconUrl, type SiteFaviconStatus } from '@/utils/siteFavicon'

const status = ref<SiteFaviconStatus>({ hasCustom: false, version: 0 })
const loading = ref(true)
const saving = ref(false)
const selectedFile = ref<File | null>(null)
const cropVisible = ref(false)
const previewUrl = computed(() => faviconUrl(status.value))

function applyStatus(nextStatus: SiteFaviconStatus) {
  status.value = nextStatus
  applySiteFavicon(faviconUrl(nextStatus))
}

async function loadStatus() {
  loading.value = true
  try {
    const { data } = await http.get<SiteFaviconStatus>('/site/favicon/status')
    applyStatus(data)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '网站图标加载失败')
  } finally {
    loading.value = false
  }
}

function selectFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('原始图片不能超过 5MB')
    return
  }
  const supportedType = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/x-icon', 'image/vnd.microsoft.icon'].includes(file.type)
  const supportedExtension = /\.(jpe?g|png|webp|gif|ico)$/i.test(file.name)
  if (!supportedType && !supportedExtension) {
    ElMessage.warning('仅支持 JPG、PNG、WebP、GIF 或 ICO 图片')
    return
  }
  selectedFile.value = file
  cropVisible.value = true
}

function cancelCrop() {
  selectedFile.value = null
}

async function uploadCroppedIcon(file: File) {
  saving.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const { data } = await http.post<SiteFaviconStatus>('/site/favicon', body, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    applyStatus(data)
    cropVisible.value = false
    selectedFile.value = null
    ElMessage.success('网站图标已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '网站图标上传失败')
  } finally {
    saving.value = false
  }
}

async function restoreDefault() {
  try {
    await ElMessageBox.confirm('确定恢复系统默认网站图标吗？', '恢复默认图标', {
      type: 'warning',
      confirmButtonText: '恢复默认',
      cancelButtonText: '取消',
      customClass: 'mv-message-box',
    })
  } catch { return }

  saving.value = true
  try {
    const { data } = await http.delete<SiteFaviconStatus>('/site/favicon')
    applyStatus(data)
    ElMessage.success('已恢复默认网站图标')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '恢复默认图标失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadStatus)
</script>

<template>
  <div class="panel-card favicon-card">
    <div class="favicon-heading">
      <div>
        <h2>网站图标</h2>
        <p>设置浏览器标签页和收藏夹中显示的 MemoryVault 图标。</p>
      </div>
      <span class="favicon-status">{{ status.hasCustom ? '自定义' : '默认' }}</span>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />
    <div v-else class="favicon-content">
      <div class="favicon-preview-wrap">
        <div class="favicon-preview"><img :src="previewUrl" alt="当前网站图标" /></div>
        <div>
          <strong>{{ status.hasCustom ? '当前自定义图标' : '当前默认图标' }}</strong>
          <p>选择图片后可拖动、缩放和旋转，最终生成 512 × 512 PNG。</p>
        </div>
      </div>

      <div class="favicon-actions">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept="image/jpeg,image/png,image/webp,image/gif,image/x-icon,.ico"
          :on-change="selectFile"
        >
          <el-button type="primary" :loading="saving">选择图片</el-button>
        </el-upload>
        <el-button v-if="status.hasCustom" type="danger" plain :disabled="saving" @click="restoreDefault">恢复默认</el-button>
      </div>
    </div>
  </div>

  <AvatarCropDialog
    v-model="cropVisible"
    :file="selectedFile"
    :uploading="saving"
    shape="square"
    title="调整网站图标"
    tip="拖动图片调整位置，方形区域将作为浏览器标签页图标。"
    output-name="favicon.png"
    confirm-text="应用并上传"
    @confirm="uploadCroppedIcon"
    @cancel="cancelCrop"
  />
</template>

<style scoped>
.favicon-card { padding: 24px; }
.favicon-heading,.favicon-content,.favicon-preview-wrap,.favicon-actions { display: flex; align-items: center; }
.favicon-heading { justify-content: space-between; gap: 20px; padding-bottom: 20px; border-bottom: 1px solid var(--separator); }
.favicon-heading h2 { margin: 0; color: var(--text-primary); font-size: 19px; }
.favicon-heading p,.favicon-preview-wrap p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; line-height: 1.5; }
.favicon-status { padding: 5px 10px; border-radius: 999px; background: var(--accent-soft); color: var(--accent); font-size: 11px; font-weight: 700; }
.favicon-content { justify-content: space-between; gap: 24px; padding-top: 22px; }
.favicon-preview-wrap { min-width: 0; gap: 16px; }
.favicon-preview { display: grid; width: 82px; height: 82px; flex-shrink: 0; place-items: center; overflow: hidden; border: 1px solid var(--separator); border-radius: 20px; background: var(--bg-tertiary); box-shadow: inset 0 1px 0 rgba(255,255,255,.45); }
.favicon-preview img { width: 62px; height: 62px; object-fit: contain; }
.favicon-preview-wrap strong { color: var(--text-primary); font-size: 14px; }
.favicon-actions { flex-shrink: 0; gap: 9px; }
@media (max-width: 680px) { .favicon-content { align-items: stretch; flex-direction: column; } .favicon-actions :deep(.el-button) { flex: 1; margin-left: 0; } }
</style>

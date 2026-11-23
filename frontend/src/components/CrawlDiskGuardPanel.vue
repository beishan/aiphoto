<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

type Mode = 'OFF' | 'PERCENT' | 'GIB'
type Guard = { mode: Mode; value: number; blocked: boolean }

const isAdmin = (() => {
  try { return JSON.parse(sessionStorage.getItem('user') || 'null')?.role === 'ADMIN' }
  catch { return false }
})()
const visible = ref(false)
const busy = ref(false)
const loaded = ref(false)
const mode = ref<Mode>('OFF')
const value = ref<number>()
const blocked = ref(false)

function apply(settings: Guard) {
  mode.value = settings.mode
  value.value = settings.value || undefined
  blocked.value = settings.blocked
}

async function open() {
  visible.value = true
  loaded.value = false
  busy.value = true
  try {
    const { data } = await http.get<Guard>('/settings/crawler/disk-guard')
    apply(data)
    loaded.value = true
  } catch {
    ElMessage.error('无法读取磁盘保护设置')
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!loaded.value || busy.value) return
  if (mode.value !== 'OFF' && (!value.value || value.value <= 0)) {
    ElMessage.warning('请输入大于 0 的阈值')
    return
  }
  busy.value = true
  try {
    const { data } = await http.put<Guard>('/settings/crawler/disk-guard', {
      mode: mode.value, value: value.value,
    })
    apply(data)
    ElMessage.success('磁盘保护设置已保存')
    visible.value = false
  } catch {
    ElMessage.error('无法保存磁盘保护设置')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div v-if="isAdmin" class="disk-guard">
    <el-button round @click="open">磁盘保护</el-button>
    <el-dialog v-model="visible" title="采集磁盘保护" width="min(480px, 94vw)">
      <el-form label-position="top" @submit.prevent="save">
        <p class="guard-copy">图库或采集存储卷的可用空间达到阈值后，扫描、下载和入库任务将等待空间恢复。</p>
        <el-alert v-if="loaded && blocked" title="当前磁盘保护已触发" type="warning" :closable="false" />
        <fieldset :disabled="busy || !loaded" class="guard-fields">
          <el-form-item label="阈值类型">
            <el-select v-model="mode" aria-label="阈值类型" :disabled="busy || !loaded" @change="value = undefined">
              <el-option label="关闭保护" value="OFF" />
              <el-option label="剩余容量百分比" value="PERCENT" />
              <el-option label="固定剩余空间" value="GIB" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode !== 'OFF'" :label="mode === 'PERCENT' ? '剩余空间（%）' : '剩余空间（GiB）'">
            <el-input-number v-model="value" :min="0.01" :max="mode === 'PERCENT' ? 100 : undefined"
              :step="1" :precision="2" :aria-label="mode === 'PERCENT' ? '剩余空间百分比' : '剩余空间 GiB'" />
          </el-form-item>
        </fieldset>
        <el-button type="primary" native-type="submit" :disabled="!loaded" :loading="busy">保存设置</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped>
.disk-guard { display: flex; justify-content: flex-end; margin: 12px 0; }
.guard-copy { margin: 0 0 16px; line-height: 1.6; }
.guard-fields { border: 0; padding: 0; margin: 14px 0 0; min-width: 0; }
</style>

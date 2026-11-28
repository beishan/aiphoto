<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

type Kind = 'PHOTOS' | 'THUMBNAILS' | 'CRAWL' | 'MODELS'
type Status = 'WAITING' | 'COPYING' | 'COMPLETED' | 'FAILED'
type Job = { kind: Kind; source: string; target: string; status: Status; files: number; total: number; error: string | null }
type Overview = {
  enabled: boolean
  pool: string
  categories: { kind: Kind; active: string; locations: { directory: string; ready: boolean }[] }[]
  job: Job | null
}
type Folder = { name: string; path: string }
type DirectoryView = { path: string; children: Folder[]; selectable: boolean; creatable: boolean }

const emit = defineEmits<{ migrated: [] }>()
const labels: Record<Kind, string> = { PHOTOS: '照片原图', THUMBNAILS: '缩略图', CRAWL: '采集文件', MODELS: 'AI 模型' }
const endpoint = '/settings/storage-locations'
const overview = ref<Overview>()
const error = ref('')
const saving = ref(false)
const dialog = ref(false)
const kindToAdd = ref<Kind>('PHOTOS')
const directory = ref<DirectoryView>()
const browsing = ref(false)
const folderName = ref('')
const directoryError = ref('')
let browseRequest = 0
const selected = ref<Partial<Record<Kind, string>>>({})
let timer: ReturnType<typeof setTimeout> | undefined
let mounted = true

const job = computed(() => overview.value?.job)
const running = computed(() => job.value?.status === 'WAITING' || job.value?.status === 'COPYING')
const progress = computed(() => {
  const current = job.value
  if (!current) return 0
  if (current.status === 'COMPLETED') return 100
  return current.total ? Math.min(99, Math.floor(current.files * 100 / current.total)) : 0
})

function messageOf(failure: unknown, fallback: string) {
  return (failure as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
}

async function refresh() {
  clearTimeout(timer)
  try {
    const { data } = await http.get<Overview>(endpoint)
    if (!mounted) return
    const wasRunning = running.value
    overview.value = data
    error.value = ''
    if (wasRunning && data.job?.status === 'COMPLETED') {
      selected.value[data.job.kind] = undefined
      emit('migrated')
    }
  } catch (failure) {
    if (mounted) error.value = messageOf(failure, '读取存储位置失败')
  } finally {
    if (mounted && running.value) timer = setTimeout(refresh, 2000)
  }
}

function openAdd(kind: Kind) {
  kindToAdd.value = kind
  directory.value = undefined
  folderName.value = ''
  dialog.value = true
  void browse('')
}

const breadcrumbs = computed(() => {
  const segments = directory.value?.path.split('/').filter(Boolean) ?? []
  return segments.map((name, index) => ({ name, path: segments.slice(0, index + 1).join('/') }))
})

async function browse(path: string) {
  const request = ++browseRequest
  browsing.value = true
  directoryError.value = ''
  try {
    const { data } = await http.get<DirectoryView>(endpoint + '/directories', { params: { directory: path } })
    if (request === browseRequest) {
      directory.value = data
      folderName.value = ''
    }
  } catch (failure) {
    if (request === browseRequest) directoryError.value = messageOf(failure, '读取目录失败')
  } finally {
    if (request === browseRequest) browsing.value = false
  }
}

async function createFolder() {
  if (!directory.value?.creatable || !folderName.value.trim()) return
  saving.value = true
  try {
    const { data } = await http.post<Folder>(endpoint + '/directories', {
      parent: directory.value.path, name: folderName.value.trim(),
    })
    await browse(data.path)
  } catch (failure) {
    ElMessage.error(messageOf(failure, '新建目录失败'))
  } finally {
    saving.value = false
  }
}

async function addLocation() {
  const target = directory.value
  if (!target?.selectable) return
  saving.value = true
  try {
    const kind = kindToAdd.value
    const { data } = await http.post<Overview>(endpoint, { kind, directory: target.path })
    overview.value = data
    selected.value[kind] = target.path
    dialog.value = false
  } catch (failure) {
    ElMessage.error(messageOf(failure, '添加存储位置失败'))
  } finally {
    saving.value = false
  }
}

async function migrate(kind: Kind) {
  const directory = selected.value[kind]
  if (!directory || !overview.value) return
  try {
    await ElMessageBox.confirm(
      `将${labels[kind]}复制到「${directory}」，校验通过后切换存储位置。迁移期间该类别的新写入会等待，源文件会保留。`,
      '迁移存储', { type: 'warning', confirmButtonText: '开始迁移', cancelButtonText: '取消' },
    )
  } catch { return }
  saving.value = true
  try {
    const { data } = await http.post<Job>(endpoint + '/migrate', { kind, directory })
    overview.value = { ...overview.value, job: data }
    if (data.status === 'COMPLETED') emit('migrated')
    await refresh()
  } catch (failure) {
    ElMessage.error(messageOf(failure, '启动迁移失败'))
  } finally {
    saving.value = false
  }
}

onMounted(refresh)
onUnmounted(() => { mounted = false; browseRequest++; clearTimeout(timer) })
</script>

<template>
  <section class="panel-card migration-panel">
    <h3>存储位置与迁移</h3>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <el-button v-if="error" link type="primary" @click="refresh">重新加载</el-button>
    <p v-else-if="!overview" class="migration-note">正在读取存储位置…</p>
    <p v-else-if="!overview.enabled" class="migration-note">请在 Jenkins 发版参数「STORAGE_POOL_MAPPINGS」中配置 NAS 目录映射。</p>
    <template v-if="overview">
      <div v-for="category in overview.categories" :key="category.kind" class="migration-row">
        <div class="migration-path">
          <strong>{{ labels[category.kind] }}</strong>
          <code>{{ category.active }}</code>
        </div>
        <div v-if="overview.enabled" class="migration-actions">
          <el-select v-model="selected[category.kind]" :aria-label="labels[category.kind] + '迁移目标'"
            placeholder="选择目标目录" :disabled="running || saving">
            <el-option v-for="location in category.locations" :key="location.directory" :value="location.directory"
              :label="location.ready ? location.directory : location.directory + '（不可用作目标）'" :disabled="!location.ready" />
          </el-select>
          <el-button :disabled="running || saving" @click="openAdd(category.kind)">新增位置</el-button>
          <el-button type="primary" :disabled="!category.locations.some(item => item.ready && item.directory === selected[category.kind]) || running || saving"
            @click="migrate(category.kind)">迁移</el-button>
        </div>
      </div>
      <div v-if="job" class="migration-task" aria-live="polite">
        <strong>{{ labels[job.kind] }}迁移</strong>
        <span>{{ job.status === 'WAITING' ? '等待现有写入完成' : job.status === 'COPYING' ? '正在复制和校验' : job.status === 'COMPLETED' ? '已切换到新位置' : '迁移失败' }}</span>
        <el-progress v-if="job.status !== 'FAILED'" :percentage="progress" :status="job.status === 'COMPLETED' ? 'success' : undefined" />
        <p v-if="job.status === 'COPYING'" class="migration-note">{{ job.files }} / {{ job.total }} 个文件</p>
        <p v-if="job.error" class="migration-note">{{ job.error }}</p>
      </div>
    </template>
    <el-dialog v-model="dialog" :title="'新增' + labels[kindToAdd] + '存储位置'" width="min(580px, 92vw)">
      <nav class="folder-breadcrumbs" aria-label="当前目录">
        <el-button link type="primary" :disabled="browsing || saving" @click="browse('')">已挂载位置</el-button>
        <template v-for="crumb in breadcrumbs" :key="crumb.path">
          <span aria-hidden="true">/</span>
          <el-button link type="primary" :disabled="browsing || saving" @click="browse(crumb.path)">{{ crumb.name }}</el-button>
        </template>
      </nav>
      <div v-loading="browsing" class="folder-list" :aria-busy="browsing">
        <el-alert v-if="directoryError" type="error" :title="directoryError" :closable="false" />
        <template v-else-if="directory">
          <button v-for="folder in directory.children" :key="folder.path" type="button" class="folder-item"
            :disabled="browsing || saving" @click="browse(folder.path)">
            <span>{{ folder.name }}</span><span aria-hidden="true">›</span>
          </button>
          <p v-if="!directory.children.length" class="migration-note">没有子目录</p>
        </template>
      </div>
      <el-form v-if="directory?.creatable" class="folder-create" @submit.prevent="createFolder">
        <el-input v-model="folderName" aria-label="新目录名" placeholder="新目录名" :disabled="browsing || saving" />
        <el-button native-type="submit" :disabled="browsing || saving || !folderName.trim()">新建目录</el-button>
      </el-form>
      <p class="migration-note">{{ directory?.selectable ? '当前目录为空，可以添加。' : '进入一个空目录，或先新建目录。' }}</p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="browsing || !directory?.selectable" @click="addLocation">添加当前目录</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
h3 { margin: 0 0 12px; }
.migration-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.migration-path { display: grid; gap: 5px; min-width: 0; }
.migration-path code { color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.migration-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.migration-actions .el-select { width: min(220px, 100%); }
.migration-actions .el-button { margin-left: 0; }
.migration-note { color: var(--el-text-color-secondary); font-size: 13px; }
.migration-task { display: grid; gap: 8px; margin-top: 16px; }
.folder-breadcrumbs, .folder-create { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.folder-list { min-height: 80px; max-height: 260px; overflow: auto; margin: 12px 0; border-block: 1px solid var(--el-border-color-lighter); }
.folder-item { display: flex; width: 100%; justify-content: space-between; gap: 12px; padding: 10px; border: 0; background: transparent; color: var(--el-text-color-primary); text-align: left; cursor: pointer; }
.folder-item:hover, .folder-item:focus-visible { background: var(--el-fill-color-light); }
.folder-create .el-input { flex: 1; }
</style>

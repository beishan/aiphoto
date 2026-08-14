<script setup lang="ts">
import { ref, computed, onMounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { searchApi, type SearchParams } from '@/api/searchApi'
import { tagApi } from '@/api/tagApi'
import { folderApi } from '@/api/folderApi'
import type { Photo, Tag, ScanFolder } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const query = ref('')
const searchType = ref<'text' | 'semantic'>('semantic')
const results = ref<Photo[]>([])
const loading = ref(false)
const totalElements = ref(0)
const page = ref(0)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const hasSearched = ref(false)
const gridColumns = ref(12)

// Filter state
const showFilters = ref(false)
const allTags = ref<Tag[]>([])
const allFolders = ref<ScanFolder[]>([])

const filters = reactive({
  tagIds: [] as number[],
  minRating: 0,
  maxRating: 0,
  hasDescription: null as boolean | null,
  inTimeline: null as boolean | null,
  folderId: null as number | null,
  fileType: '' as string,
  startDate: '',
  endDate: '',
  sortBy: 'date',
  sortOrder: 'desc',
})

const activeFilterCount = computed(() => {
  let count = 0
  if (filters.tagIds.length > 0) count++
  if (filters.minRating > 0) count++
  if (filters.maxRating > 0) count++
  if (filters.hasDescription !== null) count++
  if (filters.inTimeline !== null) count++
  if (filters.folderId !== null) count++
  if (filters.fileType) count++
  if (filters.startDate) count++
  if (filters.endDate) count++
  return count
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${gridColumns.value}, 1fr)`
}))

onMounted(() => {
  const q = route.query.q as string
  if (q) {
    query.value = q
    handleSearch()
  }
  loadFilterOptions()
})

async function loadFilterOptions() {
  try {
    const [tagRes, folderRes] = await Promise.all([
      tagApi.list(),
      folderApi.list(),
    ])
    allTags.value = tagRes.data
    allFolders.value = folderRes.data
  } catch { /* ignore */ }
}

function buildSearchParams(): SearchParams {
  const params: SearchParams = {
    query: query.value.trim() || undefined,
    type: searchType.value,
    page: page.value,
  }
  if (filters.tagIds.length === 1) {
    params.tagId = filters.tagIds[0]
  }
  if (filters.minRating > 0) params.minRating = filters.minRating
  if (filters.maxRating > 0) params.maxRating = filters.maxRating
  if (filters.hasDescription !== null) params.hasDescription = filters.hasDescription
  if (filters.inTimeline !== null) params.inTimeline = filters.inTimeline
  if (filters.folderId !== null) params.folderId = filters.folderId
  if (filters.fileType) params.fileType = filters.fileType
  if (filters.startDate) params.startDate = filters.startDate
  if (filters.endDate) params.endDate = filters.endDate
  params.sortBy = filters.sortBy
  params.sortOrder = filters.sortOrder
  return params
}

async function handleSearch() {
  // Allow search without query if filters are active
  if (!query.value.trim() && activeFilterCount.value === 0) return

  loading.value = true
  hasSearched.value = true
  page.value = 0
  try {
    const params = buildSearchParams()
    const { data } = await searchApi.search(params)
    results.value = data.content
    totalElements.value = data.totalElements
  } finally {
    loading.value = false
  }
}

// Auto-search when filters change (if already searched)
watch(() => activeFilterCount.value, () => {
  if (hasSearched.value) handleSearch()
})
watch(() => filters.sortBy, () => { if (hasSearched.value) handleSearch() })
watch(() => filters.sortOrder, () => { if (hasSearched.value) handleSearch() })

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

function toggleSearchType() {
  searchType.value = searchType.value === 'text' ? 'semantic' : 'text'
  if (hasSearched.value) handleSearch()
}

function toggleTag(id: number) {
  const idx = filters.tagIds.indexOf(id)
  if (idx >= 0) {
    filters.tagIds.splice(idx, 1)
  } else {
    if (filters.tagIds.length < 5) filters.tagIds.push(id)
  }
}

function setMinRating(r: number) {
  filters.minRating = filters.minRating === r ? 0 : r
}

function setMaxRating(r: number) {
  filters.maxRating = filters.maxRating === r ? 0 : r
}

function toggleHasDescription(val: boolean) {
  filters.hasDescription = filters.hasDescription === val ? null : val
}

function toggleInTimeline(val: boolean) {
  filters.inTimeline = filters.inTimeline === val ? null : val
}

function clearAllFilters() {
  filters.tagIds = []
  filters.minRating = 0
  filters.maxRating = 0
  filters.hasDescription = null
  filters.inTimeline = null
  filters.folderId = null
  filters.fileType = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.sortBy = 'date'
  filters.sortOrder = 'desc'
}

function removeFilter(type: string) {
  switch (type) {
    case 'tag': filters.tagIds = []; break
    case 'rating': filters.minRating = 0; filters.maxRating = 0; break
    case 'description': filters.hasDescription = null; break
    case 'timeline': filters.inTimeline = null; break
    case 'folder': filters.folderId = null; break
    case 'fileType': filters.fileType = ''; break
    case 'date': filters.startDate = ''; filters.endDate = ''; break
  }
}

// Active filter chips for display
const activeFilterChips = computed(() => {
  const chips: { type: string; label: string }[] = []
  if (filters.tagIds.length > 0) {
    const names = filters.tagIds.map(id => allTags.value.find(t => t.id === id)?.name).filter(Boolean)
    chips.push({ type: 'tag', label: `标签: ${names.join(', ')}` })
  }
  if (filters.minRating > 0 || filters.maxRating > 0) {
    let label = '评分: '
    if (filters.minRating > 0 && filters.maxRating > 0) label += `${filters.minRating}★ ~ ${filters.maxRating}★`
    else if (filters.minRating > 0) label += `≥ ${filters.minRating}★`
    else label += `≤ ${filters.maxRating}★`
    chips.push({ type: 'rating', label })
  }
  if (filters.hasDescription !== null) {
    chips.push({ type: 'description', label: filters.hasDescription ? '有描述' : '无描述' })
  }
  if (filters.inTimeline !== null) {
    chips.push({ type: 'timeline', label: filters.inTimeline ? '在时间线' : '不在时间线' })
  }
  if (filters.folderId !== null) {
    const folder = allFolders.value.find(f => f.id === filters.folderId)
    chips.push({ type: 'folder', label: `目录: ${folder?.name || ''}` })
  }
  if (filters.fileType) {
    const typeMap: Record<string, string> = { PHOTO: '照片', VIDEO: '视频', GIF: 'GIF', RAW: 'RAW' }
    chips.push({ type: 'fileType', label: `类型: ${typeMap[filters.fileType] || filters.fileType}` })
  }
  if (filters.startDate || filters.endDate) {
    chips.push({ type: 'date', label: `日期: ${filters.startDate || '?'} ~ ${filters.endDate || '?'}` })
  }
  return chips
})

const fileTypeOptions = [
  { value: '', label: '全部' },
  { value: 'PHOTO', label: '照片' },
  { value: 'VIDEO', label: '视频' },
  { value: 'GIF', label: 'GIF' },
  { value: 'RAW', label: 'RAW' },
]
</script>

<template>
  <div class="search-view">
    <!-- Search bar -->
    <div class="search-header">
      <div class="search-input-wrapper">
        <el-input
          v-model="query"
          placeholder="搜索照片..."
          class="search-input"
          clearable
          @clear="hasSearched = false; results = []"
          @keyup.enter="handleSearch"
          autofocus
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <el-button class="type-toggle" @click="toggleSearchType">
        {{ searchType === 'text' ? '全文' : '语义' }}
      </el-button>

      <el-button
        class="filter-btn"
        :class="{ active: showFilters || activeFilterCount > 0 }"
        @click="showFilters = !showFilters"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z" />
        </svg>
        <span>筛选</span>
        <span v-if="activeFilterCount > 0" class="filter-badge">{{ activeFilterCount }}</span>
      </el-button>
    </div>

    <!-- Active filter chips -->
    <div v-if="activeFilterChips.length > 0" class="filter-chips-bar">
      <div
        v-for="chip in activeFilterChips"
        :key="chip.type"
        class="filter-chip"
      >
        <span>{{ chip.label }}</span>
        <el-button class="chip-remove" @click="removeFilter(chip.type)">×</el-button>
      </div>
      <el-button class="clear-all-btn" @click="clearAllFilters">清除全部</el-button>
    </div>

    <!-- Filter panel -->
    <Transition name="filter-slide">
      <div v-if="showFilters" class="filter-panel">
        <!-- Tags filter -->
        <div class="filter-section">
          <div class="filter-section-title">标签</div>
          <div class="tag-filter-list">
            <el-button
              v-for="tag in allTags"
              :key="tag.id"
              class="tag-filter-btn"
              :class="{ active: filters.tagIds.includes(tag.id) }"
              :style="filters.tagIds.includes(tag.id) ? { borderColor: tag.color || '#0a84ff', color: tag.color || '#0a84ff', background: (tag.color || '#0a84ff') + '15' } : {}"
              @click="toggleTag(tag.id)"
            >
              <span v-if="tag.color" class="tag-dot" :style="{ background: tag.color }"></span>
              {{ tag.name }}
              <span class="tag-filter-count">{{ tag.photoCount }}</span>
            </el-button>
            <span v-if="allTags.length === 0" class="filter-empty-hint">暂无标签</span>
          </div>
        </div>

        <!-- Rating filter -->
        <div class="filter-section">
          <div class="filter-section-title">评分</div>
          <div class="rating-filter-row">
            <div class="rating-filter-group">
              <span class="rating-label">最低</span>
              <div class="star-picker">
                <el-button
                  v-for="s in 5"
                  :key="s"
                  class="star-pick"
                  :class="{ active: s <= filters.minRating }"
                  @click="setMinRating(s)"
                >★</el-button>
              </div>
            </div>
            <div class="rating-filter-group">
              <span class="rating-label">最高</span>
              <div class="star-picker">
                <el-button
                  v-for="s in 5"
                  :key="s"
                  class="star-pick"
                  :class="{ active: s <= filters.maxRating }"
                  @click="setMaxRating(s)"
                >★</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- Toggles: description & timeline -->
        <div class="filter-section">
          <div class="filter-section-title">条件</div>
          <div class="toggle-filter-grid">
            <div class="toggle-filter-item">
              <span class="toggle-label">描述</span>
              <div class="toggle-btns">
                <el-button class="toggle-btn" :class="{ active: filters.hasDescription === true }" @click="toggleHasDescription(true)">有</el-button>
                <el-button class="toggle-btn" :class="{ active: filters.hasDescription === false }" @click="toggleHasDescription(false)">无</el-button>
              </div>
            </div>
            <div class="toggle-filter-item">
              <span class="toggle-label">时间线</span>
              <div class="toggle-btns">
                <el-button class="toggle-btn" :class="{ active: filters.inTimeline === true }" @click="toggleInTimeline(true)">在</el-button>
                <el-button class="toggle-btn" :class="{ active: filters.inTimeline === false }" @click="toggleInTimeline(false)">不在</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- Date range -->
        <div class="filter-section">
          <div class="filter-section-title">拍摄日期</div>
          <div class="date-range-row">
            <el-date-picker v-model="filters.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" class="date-input" />
            <span class="date-sep">~</span>
            <el-date-picker v-model="filters.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="date-input" />
          </div>
        </div>

        <!-- Folder & File type -->
        <div class="filter-section two-col">
          <div class="filter-col">
            <div class="filter-section-title">扫描目录</div>
            <el-select v-model="filters.folderId" class="filter-select" placeholder="全部目录" clearable :value-on-clear="null">
              <el-option v-for="f in allFolders" :key="f.id" :value="f.id" :label="`${f.name} (${f.photoCount})`" />
            </el-select>
          </div>
          <div class="filter-col">
            <div class="filter-section-title">文件类型</div>
            <el-select v-model="filters.fileType" class="filter-select">
              <el-option v-for="opt in fileTypeOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
          </div>
        </div>

        <!-- Sort -->
        <div class="filter-section two-col">
          <div class="filter-col">
            <div class="filter-section-title">排序</div>
            <el-select v-model="filters.sortBy" class="filter-select">
              <el-option value="date" label="拍摄日期" />
              <el-option value="rating" label="评分" />
              <el-option value="name" label="文件名" />
            </el-select>
          </div>
          <div class="filter-col">
            <div class="filter-section-title">方向</div>
            <el-select v-model="filters.sortOrder" class="filter-select">
              <el-option value="desc" label="降序" />
              <el-option value="asc" label="升序" />
            </el-select>
          </div>
        </div>

        <!-- Action buttons -->
        <div class="filter-actions">
          <el-button class="btn-clear" @click="clearAllFilters">清除筛选</el-button>
          <el-button class="btn-apply" @click="handleSearch(); showFilters = false">
            {{ activeFilterCount > 0 ? `应用筛选 (${activeFilterCount})` : '应用' }}
          </el-button>
        </div>
      </div>
    </Transition>

    <!-- Results -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="hasSearched && results.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z" />
      </svg>
      <p>未找到匹配的照片</p>
      <el-button v-if="activeFilterCount > 0" class="btn-clear" @click="clearAllFilters(); handleSearch()">清除筛选重试</el-button>
    </div>

    <div v-else-if="results.length > 0" class="results-area">
      <div class="result-meta">
        <span>{{ totalElements }} 张照片</span>
        <span v-if="activeFilterCount > 0" class="filter-hint">· {{ activeFilterCount }} 个筛选条件</span>
      </div>
      <div class="photo-grid-compact" :style="gridStyle">
        <PhotoCard
          v-for="(photo, index) in results"
          :key="photo.id"
          :photo="photo"
          @click="openViewer(index)"
        />
      </div>
    </div>

    <!-- Suggestions when not searched -->
    <div v-else class="suggestions">
      <h3>搜索建议</h3>
      <p class="suggestion-hint">试试用自然语言描述你想找的照片，或使用筛选条件精确查找</p>
      <div class="suggestion-tags">
        <el-button class="tag" @click="query = '吃美食'; handleSearch()">吃美食</el-button>
        <el-button class="tag" @click="query = '海边风景'; handleSearch()">海边风景</el-button>
        <el-button class="tag" @click="query = '家庭聚会'; handleSearch()">家庭聚会</el-button>
        <el-button class="tag" @click="query = '宠物'; handleSearch()">宠物</el-button>
        <el-button class="tag" @click="query = '日落黄昏'; handleSearch()">日落黄昏</el-button>
        <el-button class="tag" @click="query = '旅行风景'; handleSearch()">旅行风景</el-button>
      </div>

      <!-- Quick filter shortcuts -->
      <div class="quick-filters">
        <h4>快捷筛选</h4>
        <div class="quick-filter-row">
          <el-button class="quick-filter-btn" @click="filters.inTimeline = true; handleSearch()">时间线照片</el-button>
          <el-button class="quick-filter-btn" @click="filters.hasDescription = true; handleSearch()">有描述的照片</el-button>
          <el-button class="quick-filter-btn" @click="filters.minRating = 4; handleSearch()">4星以上</el-button>
          <el-button class="quick-filter-btn" @click="filters.fileType = 'VIDEO'; handleSearch()">仅视频</el-button>
        </div>
      </div>
    </div>

    <PhotoViewer
      v-model:show="viewerVisible"
      :photos="results"
      :initial-index="viewerIndex"
    />
  </div>
</template>

<style scoped>
.search-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
}

.search-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
}

.search-input-wrapper {
  flex: 1;
  height: 40px;
}

.search-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

.search-input {
  width: 100%;
  height: 100%;
}
.search-input :deep(.el-input__wrapper) { height: 40px; border-radius: var(--radius-md); }

.clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  padding: 4px;
  border-radius: 50%;
}

.type-toggle {
  flex-shrink: 0;
  padding: 8px 14px;
  border-radius: var(--radius-full);
  background: var(--bg-tertiary);
  color: var(--accent);
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
  transition: all 0.2s;
}

.type-toggle:hover {
  background: rgba(10, 132, 255, 0.12);
}

.filter-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.filter-btn:hover {
  background: rgba(10, 132, 255, 0.1);
}

.filter-btn.active {
  background: rgba(10, 132, 255, 0.15);
  color: var(--accent);
  border-color: var(--accent);
}

.filter-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--accent);
  color: white;
  font-size: 11px;
  font-weight: 600;
  padding: 0 4px;
}

/* Active filter chips */
.filter-chips-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 0 16px 8px;
}

.filter-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(10, 132, 255, 0.12);
  border: 1px solid rgba(10, 132, 255, 0.3);
  border-radius: 12px;
  font-size: 12px;
  color: var(--accent);
}

.chip-remove {
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.7;
  background: none;
  border: none;
  color: inherit;
}

.chip-remove:hover { opacity: 1; }

.clear-all-btn {
  font-size: 12px;
  color: var(--danger);
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
}

/* Filter panel */
.filter-panel {
  margin: 0 16px 12px;
  background: var(--bg-secondary);
  border: 0.5px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-section.two-col {
  flex-direction: row;
  gap: 16px;
}

.filter-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

/* Tag filter */
.tag-filter-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-filter-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--border);
  border-radius: 14px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-primary);
  cursor: pointer;
  transition: all 0.15s;
}

.tag-filter-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-filter-count {
  font-size: 10px;
  opacity: 0.6;
}

.filter-empty-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* Rating filter */
.rating-filter-row {
  display: flex;
  gap: 24px;
}

.rating-filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rating-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.star-picker {
  display: flex;
  gap: 2px;
}

.star-pick {
  font-size: 18px;
  color: var(--text-tertiary);
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1;
}

.star-pick:hover {
  transform: scale(1.15);
}

.star-pick.active {
  color: #ffcc00;
}

/* Toggle filters */
.toggle-filter-grid {
  display: flex;
  gap: 24px;
}

.toggle-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.toggle-btns {
  display: flex;
  gap: 2px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 2px;
}

.toggle-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
  background: none;
  cursor: pointer;
  transition: all 0.15s;
}

.toggle-btn.active {
  background: var(--accent);
  color: white;
}

/* Date range */
.date-range-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-input {
  flex: 1;
  width: auto;
}

.date-sep {
  color: var(--text-tertiary);
}

/* Filter select */
.filter-select {
  width: 100%;
}

/* Filter actions */
.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 0.5px solid var(--separator);
}

.btn-clear, .btn-apply {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s;
}

.btn-clear {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.btn-apply {
  background: var(--accent);
  color: white;
}

.btn-clear:hover, .btn-apply:hover {
  opacity: 0.85;
}

/* Filter panel animation */
.filter-slide-enter-active {
  transition: all 0.25s cubic-bezier(0.32, 0.72, 0, 1);
  overflow: hidden;
}
.filter-slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.filter-slide-enter-from, .filter-slide-leave-to {
  opacity: 0;
  max-height: 0;
  margin: 0 16px;
  padding: 0;
}
.filter-slide-enter-to, .filter-slide-leave-from {
  opacity: 1;
  max-height: 800px;
}

/* Loading */
.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }

/* Results */
.results-area {
  padding: 0 0 16px;
}

.result-meta {
  padding: 0 16px 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.filter-hint {
  color: var(--accent);
}

/* Suggestions */
.suggestions {
  padding: 40px 16px;
}

.suggestions h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.suggestion-hint {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 32px;
}

.tag {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
  transition: all 0.2s;
}

.tag:hover {
  background: rgba(10, 132, 255, 0.12);
  border-color: var(--accent);
  color: var(--accent);
}

/* Quick filters */
.quick-filters h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.quick-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-filter-btn {
  padding: 8px 14px;
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: inherit;
  border: 0.5px solid var(--glass-border);
  cursor: pointer;
  transition: all 0.2s;
}

.quick-filter-btn:hover {
  background: rgba(10, 132, 255, 0.1);
  color: var(--accent);
  border-color: var(--accent);
}

/* Responsive */
@media (max-width: 640px) {
  .filter-section.two-col {
    flex-direction: column;
  }
  .rating-filter-row, .toggle-filter-grid {
    flex-direction: column;
    gap: 12px;
  }
  .date-range-row {
    flex-wrap: wrap;
  }
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchApi } from '@/api/searchApi'
import type { Photo } from '@/types'
import PhotoCard from '@/components/PhotoCard.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const query = ref('')
const searchType = ref<'text' | 'semantic'>('text')
const results = ref<Photo[]>([])
const loading = ref(false)
const totalElements = ref(0)
const page = ref(0)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const hasSearched = ref(false)

onMounted(() => {
  const q = route.query.q as string
  if (q) {
    query.value = q
    handleSearch()
  }
})

async function handleSearch() {
  if (!query.value.trim()) return

  loading.value = true
  hasSearched.value = true
  try {
    const { data } = await searchApi.search({
      query: query.value,
      type: searchType.value,
      page: page.value,
    })
    results.value = data.content
    totalElements.value = data.totalElements
  } finally {
    loading.value = false
  }
}

function openViewer(index: number) {
  viewerIndex.value = index
  viewerVisible.value = true
}

function toggleSearchType() {
  searchType.value = searchType.value === 'text' ? 'semantic' : 'text'
  if (hasSearched.value) handleSearch()
}
</script>

<template>
  <div class="search-view">
    <!-- Search bar -->
    <div class="search-header">
      <div class="search-input-wrapper">
        <svg class="search-icon" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z" />
        </svg>
        <input
          v-model="query"
          type="text"
          placeholder="搜索照片..."
          class="search-input"
          @keyup.enter="handleSearch"
          autofocus
        />
        <button v-if="query" class="clear-btn" @click="query = ''; hasSearched = false; results = []">
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
            <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
          </svg>
        </button>
      </div>

      <button class="type-toggle" @click="toggleSearchType">
        {{ searchType === 'text' ? '全文' : '语义' }}
      </button>
    </div>

    <!-- Results -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="hasSearched && results.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z" />
      </svg>
      <p>未找到匹配的照片</p>
    </div>

    <div v-else-if="results.length > 0" class="results-area">
      <div class="result-meta">
        <span>{{ totalElements }} 张照片</span>
      </div>
      <div class="photo-grid-compact">
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
      <div class="suggestion-tags">
        <button class="tag" @click="query = '海边'; handleSearch()">海边</button>
        <button class="tag" @click="query = '日落'; handleSearch()">日落</button>
        <button class="tag" @click="query = '家庭'; handleSearch()">家庭</button>
        <button class="tag" @click="query = '旅行'; handleSearch()">旅行</button>
        <button class="tag" @click="query = '美食'; handleSearch()">美食</button>
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
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.search-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

.search-input {
  flex: 1;
  height: 100%;
  background: none;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 16px;
  font-family: inherit;
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

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

.results-area {
  padding: 0 0 16px;
}

.result-meta {
  padding: 0 16px 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.suggestions {
  padding: 40px 16px;
}

.suggestions h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 16px;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
</style>

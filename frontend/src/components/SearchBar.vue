<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const query = ref('')

function handleSearch() {
  if (query.value.trim()) {
    router.push({ path: '/search', query: { q: query.value } })
  }
}
</script>

<template>
  <div class="search-bar-wrapper" @click="router.push('/search')">
    <svg class="search-icon" viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
      <path d="M10 2a8 8 0 105.293 14.293l4.707 4.707 1.414-1.414-4.707-4.707A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z" />
    </svg>
    <input
      v-model="query"
      type="text"
      placeholder="搜索"
      class="search-input"
      @keyup.enter="handleSearch"
    />
  </div>
</template>

<style scoped>
.search-bar-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 400px;
  height: 36px;
  padding: 0 12px;
  background: var(--bg-tertiary);
  border-radius: 10px;
  cursor: text;
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
  font-size: 15px;
  font-family: inherit;
}

.search-input::placeholder {
  color: var(--text-tertiary);
}
</style>

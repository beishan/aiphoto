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
  <div class="search-bar-wrapper">
    <el-input
      v-model="query"
      placeholder="搜索"
      class="search-input"
      clearable
      @focus="router.push('/search')"
      @keyup.enter="handleSearch"
    >
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>
  </div>
</template>

<style scoped>
.search-bar-wrapper {
  width: 100%;
  max-width: 400px;
}

.search-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

.search-input :deep(.el-input__wrapper) { min-height: 36px; border-radius: 10px; }
</style>

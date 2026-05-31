<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useCategoryStore } from '@/stores/categoryStore'
import type { Category } from '@/types'
import { photoApi } from '@/api/photoApi'
import { categoryApi } from '@/api/categoryApi'
import type { Photo } from '@/types'

const router = useRouter()
const categoryStore = useCategoryStore()
const message = useMessage()

const showCreate = ref(false)
const newCategoryName = ref('')
const newCategoryColor = ref('#0a84ff')

// Photo picker for training
const showPhotoPicker = ref(false)
const pickedCategory = ref<Category | null>(null)
const pickerPhotos = ref<Photo[]>([])
const selectedPhotoIds = ref<number[]>([])
const pickerPage = ref(0)
const pickerLoading = ref(false)
const pickerDone = ref(false)

const colorOptions = [
  '#34c759', '#007aff', '#ff9500', '#af52de', '#5856d6',
  '#30d158', '#ff2d55', '#ff9f0a', '#8e8e93', '#636366',
  '#ff3b30', '#ffcc00', '#00c7be', '#ff6482', '#bf5af2'
]

const categoryIcons: Record<string, string> = {
  landscape: 'M14 6l-3.75 5 2.85 3.4-1.6 1.2C9.81 13.75 7 10 7 10l-6 8h22L14 6z',
  person: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  food: 'M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z',
  animal: 'M4.5 9.5m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M9 5m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M15 5m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M19.5 9.5m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M17 17.5c0 2.49-2.01 4.5-4.5 4.5h-1C9.01 22 7 19.99 7 17.5V16h10v1.5z',
  building: 'M15 11V5l-3-3-3 3v2H3v14h18V11h-6zm-8 8H5v-2h2v2zm0-4H5v-2h2v2zm0-4H5V9h2v2zm6 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V9h2v2zm0-4h-2V5h2v2zm6 12h-2v-2h2v2zm0-4h-2v-2h2v2z',
  plant: 'M12 22V12M12 12C12 9 9.5 6.5 6 6c0 3.5 2.5 6 6 6zm0 0c0-3 2.5-5.5 6-6 0 3.5-2.5 6-6 6zM6 6C4 8 4 11 6 14c2-3 2-6 0-8zm12 0c2 2 2 5 0 8-2-3-2-6 0-8z',
  travel: 'M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z',
  event: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z',
  screenshot: 'M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7l-2 3v1h8v-1l-2-3h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z',
  document: 'M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z',
  folder: 'M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
}

onMounted(() => {
  categoryStore.fetchCategories()
})

function goToCategory(category: Category) {
  router.push(`/categories/${category.id}`)
}

async function handleCreate() {
  if (!newCategoryName.value.trim()) {
    message.warning('请输入分类名称')
    return
  }
  try {
    const category = await categoryStore.createCategory({
      name: newCategoryName.value.trim(),
      color: newCategoryColor.value,
    })
    showCreate.value = false
    newCategoryName.value = ''
    newCategoryColor.value = '#0a84ff'

    // If user wants to train with photos, open photo picker
    pickedCategory.value = category
    showPhotoPicker.value = true
    await loadPickerPhotos()
    message.success('分类创建成功')
  } catch (e) {
    message.error('创建失败')
  }
}

async function loadPickerPhotos() {
  pickerLoading.value = true
  pickerDone.value = false
  pickerPage.value = 0
  try {
    const { data } = await photoApi.list(0, 40)
    pickerPhotos.value = data.content
    if (pickerPhotos.value.length >= data.totalElements) {
      pickerDone.value = true
    }
  } finally {
    pickerLoading.value = false
  }
}

async function loadMorePickerPhotos() {
  if (pickerLoading.value || pickerDone.value) return
  pickerLoading.value = true
  try {
    const nextPage = pickerPage.value + 1
    const { data } = await photoApi.list(nextPage, 40)
    pickerPhotos.value = [...pickerPhotos.value, ...data.content]
    pickerPage.value = nextPage
    if (pickerPhotos.value.length >= data.totalElements) {
      pickerDone.value = true
    }
  } finally {
    pickerLoading.value = false
  }
}

function togglePhotoSelection(photoId: number) {
  const idx = selectedPhotoIds.value.indexOf(photoId)
  if (idx >= 0) {
    selectedPhotoIds.value.splice(idx, 1)
  } else if (selectedPhotoIds.value.length < 5) {
    selectedPhotoIds.value.push(photoId)
  } else {
    message.warning('最多选择 5 张模板照片')
  }
}

async function handleTrain() {
  if (!pickedCategory.value) return
  if (selectedPhotoIds.value.length < 2) {
    message.warning('请至少选择 2 张模板照片')
    return
  }
  try {
    await categoryStore.trainCategory(pickedCategory.value.id, selectedPhotoIds.value)
    showPhotoPicker.value = false
    selectedPhotoIds.value = []
    pickedCategory.value = null
    message.success('训练完成！已自动聚合相似照片')
  } catch (e) {
    message.error('训练失败')
  }
}

function closePhotoPicker() {
  showPhotoPicker.value = false
  selectedPhotoIds.value = []
  pickedCategory.value = null
}

async function handleDelete(category: Category, e: Event) {
  e.stopPropagation()
  try {
    await categoryStore.deleteCategory(category.id)
    message.success('分类已删除')
  } catch (e) {
    message.error('删除失败')
  }
}

const reclassifying = ref(false)
async function handleReclassify() {
  reclassifying.value = true
  try {
    const { data } = await categoryApi.reclassify()
    await categoryStore.fetchCategories()
    message.success(data.message)
  } catch (e) {
    message.error('分类失败')
  } finally {
    reclassifying.value = false
  }
}
</script>

<template>
  <div class="categories-view">
    <!-- Loading -->
    <div v-if="categoryStore.loading" class="loading-state">
      <div class="loading-spinner"></div>
    </div>

    <!-- Empty -->
    <div v-else-if="categoryStore.categories.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="empty-icon">
        <path d="M3 3h8v8H3V3zm0 10h8v8H3v-8zM13 3h8v8h-8V3zm0 10h8v8h-8v-8z" />
      </svg>
      <h3>暂无分类</h3>
      <button class="create-btn" @click="showCreate = true">创建第一个分类</button>
    </div>

    <!-- Category grid -->
    <div v-else class="category-grid">
      <div
        v-for="category in categoryStore.categories"
        :key="category.id"
        class="category-card"
        @click="goToCategory(category)"
      >
        <div class="category-icon" :style="{ background: category.color || '#8e8e93' }">
          <svg viewBox="0 0 24 24" fill="white" width="28" height="28">
            <path :d="categoryIcons[category.icon || 'folder'] || categoryIcons.folder" />
          </svg>
        </div>
        <div class="category-info">
          <span class="category-name">{{ category.name }}</span>
          <span class="category-count">{{ category.photoCount }} 张</span>
        </div>
        <button
          v-if="!category.isSystem"
          class="category-delete"
          @click="(e: Event) => handleDelete(category, e)"
        >
          <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
            <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Create FAB -->
    <button v-if="!categoryStore.loading && categoryStore.categories.length > 0" class="fab-create" @click="showCreate = true">
      <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
        <path d="M12 4v16m8-8H4" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
    </button>

    <!-- Reclassify FAB -->
    <button v-if="!categoryStore.loading && categoryStore.categories.length > 0" class="fab-reclassify" :disabled="reclassifying" @click="handleReclassify" :title="reclassifying ? '分类中...' : '智能分类'">
      <svg v-if="!reclassifying" viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
        <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none" />
      </svg>
      <div v-else class="fab-spinner"></div>
    </button>

    <!-- Create modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
          <div class="modal-sheet glass">
            <div class="sheet-header">
              <h3>新建分类</h3>
              <button @click="showCreate = false" class="sheet-close">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
                </svg>
              </button>
            </div>

            <div class="form-group">
              <label>名称</label>
              <input v-model="newCategoryName" type="text" placeholder="分类名称" class="ios-input" />
            </div>

            <div class="form-group">
              <label>颜色</label>
              <div class="color-selector">
                <button
                  v-for="color in colorOptions"
                  :key="color"
                  class="color-btn"
                  :class="{ active: newCategoryColor === color }"
                  :style="{ background: color }"
                  @click="newCategoryColor = color"
                />
              </div>
            </div>

            <button class="submit-btn" @click="handleCreate">创建并选择模板</button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Photo picker modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showPhotoPicker" class="modal-overlay" @click.self="closePhotoPicker">
          <div class="modal-sheet glass picker-sheet">
            <div class="sheet-header">
              <h3>选择模板照片 ({{ selectedPhotoIds.length }}/5)</h3>
              <button @click="closePhotoPicker" class="sheet-close">
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M18.3 5.71a1 1 0 00-1.42 0L12 10.59 7.12 5.71a1 1 0 00-1.42 1.42L10.59 12l-4.89 4.88a1 1 0 101.42 1.42L12 13.41l4.88 4.89a1 1 0 001.42-1.42L13.41 12l4.89-4.88a1 1 0 000-1.41z" />
                </svg>
              </button>
            </div>

            <p class="picker-hint">选择 2-5 张代表此分类的照片，AI 将自动找到相似的照片</p>

            <div class="picker-grid">
              <div
                v-for="photo in pickerPhotos"
                :key="photo.id"
                class="picker-item"
                :class="{ selected: selectedPhotoIds.includes(photo.id) }"
                @click="togglePhotoSelection(photo.id)"
              >
                <img :src="photo.thumbnailUrl || undefined" :alt="photo.originalFilename || ''" />
                <div v-if="selectedPhotoIds.includes(photo.id)" class="picker-check">
                  <svg viewBox="0 0 24 24" fill="white" width="20" height="20">
                    <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" />
                  </svg>
                </div>
              </div>
            </div>

            <div v-if="!pickerDone" class="picker-load-more">
              <button class="load-more-btn" @click="loadMorePickerPhotos" :disabled="pickerLoading">
                {{ pickerLoading ? '加载中...' : '加载更多' }}
              </button>
            </div>

            <button
              class="submit-btn"
              :disabled="selectedPhotoIds.length < 2"
              @click="handleTrain"
            >
              开始训练 (已选 {{ selectedPhotoIds.length }} 张)
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.categories-view {
  min-height: calc(100vh - var(--top-bar-height) - var(--tab-height));
  padding: 16px;
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
  min-height: 60vh;
  gap: 12px;
  color: var(--text-secondary);
}

.empty-icon { color: var(--text-tertiary); }
.empty-state h3 { font-size: 20px; font-weight: 600; color: var(--text-primary); }

.create-btn {
  margin-top: 8px;
  padding: 10px 24px;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-full);
  font-size: 15px;
  font-weight: 600;
  border: none;
  cursor: pointer;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

@media (min-width: 480px) {
  .category-grid { grid-template-columns: repeat(4, 1fr); }
}

@media (min-width: 768px) {
  .category-grid { grid-template-columns: repeat(5, 1fr); }
}

@media (min-width: 1024px) {
  .category-grid { grid-template-columns: repeat(6, 1fr); }
}

.category-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 8px;
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  cursor: pointer;
  transition: transform 0.2s, background 0.2s;
  position: relative;
}

.category-card:active {
  transform: scale(0.96);
}

.category-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.category-name {
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.category-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  opacity: 0;
  transition: opacity 0.2s;
  border: none;
  cursor: pointer;
}

.category-card:hover .category-delete {
  opacity: 1;
}

.fab-create {
  position: fixed;
  bottom: calc(var(--tab-height) + 20px);
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(10, 132, 255, 0.4);
  z-index: 50;
  border: none;
  cursor: pointer;
}

.fab-reclassify {
  position: fixed;
  bottom: calc(var(--tab-height) + 84px);
  right: 20px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--bg-secondary);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  z-index: 50;
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s;
}

.fab-reclassify:hover {
  background: var(--bg-tertiary);
  transform: scale(1.05);
}

.fab-reclassify:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.fab-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--bg-tertiary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-sheet {
  width: 100%;
  max-width: 400px;
  max-height: 80vh;
  border-radius: var(--radius-xl);
  padding: 24px;
  border: 0.5px solid var(--glass-border);
  overflow-y: auto;
}

.picker-sheet {
  max-width: 500px;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sheet-header h3 { font-size: 18px; font-weight: 600; }
.sheet-close { color: var(--text-secondary); padding: 4px; }

.picker-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.ios-input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  background: var(--bg-tertiary);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 16px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}

.ios-input:focus {
  border-color: var(--accent);
}

.color-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.color-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 3px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.color-btn.active {
  border-color: white;
  box-shadow: 0 0 0 2px var(--accent);
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  margin-bottom: 16px;
  max-height: 40vh;
  overflow-y: auto;
}

.picker-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.picker-item.selected {
  border-color: var(--accent);
}

.picker-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.picker-check {
  position: absolute;
  inset: 0;
  background: rgba(10, 132, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.picker-load-more {
  text-align: center;
  margin-bottom: 16px;
}

.load-more-btn {
  padding: 8px 20px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border-radius: var(--radius-full);
  font-size: 14px;
  font-family: inherit;
  border: 0.5px solid var(--border);
  cursor: pointer;
}

.load-more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: white;
  font-size: 16px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Transitions */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>

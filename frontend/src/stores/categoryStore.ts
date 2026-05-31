import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Category } from '@/types'
import { categoryApi } from '@/api/categoryApi'

export const useCategoryStore = defineStore('category', () => {
  const categories = ref<Category[]>([])
  const loading = ref(false)

  async function fetchCategories() {
    loading.value = true
    try {
      const { data } = await categoryApi.list()
      categories.value = data
    } finally {
      loading.value = false
    }
  }

  async function createCategory(category: Partial<Category>) {
    const { data } = await categoryApi.create(category)
    categories.value.push(data)
    return data
  }

  async function deleteCategory(id: number) {
    await categoryApi.delete(id)
    categories.value = categories.value.filter(c => c.id !== id)
  }

  async function trainCategory(id: number, photoIds: number[], threshold?: number) {
    const { data } = await categoryApi.train(id, photoIds, threshold)
    await fetchCategories()
    return data
  }

  return { categories, loading, fetchCategories, createCategory, deleteCategory, trainCategory }
})

import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Photo } from '@/types'
import { photoApi } from '@/api/photoApi'

export const usePhotoStore = defineStore('photo', () => {
  const photos = ref<Photo[]>([])
  const currentPhoto = ref<Photo | null>(null)
  const loading = ref(false)
  const totalElements = ref(0)
  const totalPages = ref(0)

  async function fetchPhotos(page = 0, size = 20) {
    loading.value = true
    try {
      const { data } = await photoApi.list(page, size)
      photos.value = data.content
      totalElements.value = data.totalElements
      totalPages.value = data.totalPages
    } finally {
      loading.value = false
    }
  }

  async function loadMore(page = 0, size = 40) {
    try {
      const { data } = await photoApi.list(page, size)
      photos.value = [...photos.value, ...data.content]
      totalElements.value = data.totalElements
      totalPages.value = data.totalPages
    } catch (e) {
      // silently fail on load more
    }
  }

  async function fetchPhoto(id: number) {
    loading.value = true
    try {
      const { data } = await photoApi.get(id)
      currentPhoto.value = data
      return data
    } finally {
      loading.value = false
    }
  }

  async function updatePhoto(id: number, updates: Partial<Photo>) {
    const { data } = await photoApi.update(id, updates)
    const index = photos.value.findIndex((p) => p.id === id)
    if (index !== -1) {
      photos.value[index] = { ...photos.value[index], ...updates }
    }
    if (currentPhoto.value?.id === id) {
      currentPhoto.value = { ...currentPhoto.value, ...updates }
    }
    return data
  }

  async function toggleFavorite(id: number) {
    const photo = photos.value.find((p) => p.id === id) || currentPhoto.value
    if (photo) {
      return updatePhoto(id, { favorite: !photo.favorite })
    }
  }

  async function setRating(id: number, rating: number) {
    return updatePhoto(id, { rating })
  }

  return {
    photos,
    currentPhoto,
    loading,
    totalElements,
    totalPages,
    fetchPhotos,
    loadMore,
    fetchPhoto,
    updatePhoto,
    toggleFavorite,
    setRating,
  }
})

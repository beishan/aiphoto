import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Album } from '@/types'
import { albumApi } from '@/api/albumApi'

export const useAlbumStore = defineStore('album', () => {
  const albums = ref<Album[]>([])
  const currentAlbum = ref<Album | null>(null)
  const loading = ref(false)

  async function fetchAlbums() {
    loading.value = true
    try {
      const { data } = await albumApi.list()
      albums.value = data
    } finally {
      loading.value = false
    }
  }

  async function fetchAlbum(id: number) {
    loading.value = true
    try {
      const { data } = await albumApi.get(id)
      currentAlbum.value = data
      return data
    } finally {
      loading.value = false
    }
  }

  async function createAlbum(album: Partial<Album>) {
    const { data } = await albumApi.create(album)
    albums.value.push(data)
    return data
  }

  async function deleteAlbum(id: number) {
    await albumApi.delete(id)
    albums.value = albums.value.filter((a) => a.id !== id)
  }

  return {
    albums,
    currentAlbum,
    loading,
    fetchAlbums,
    fetchAlbum,
    createAlbum,
    deleteAlbum,
  }
})

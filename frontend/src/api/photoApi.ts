import http from './http'
import type { Photo, PageResponse } from '@/types'

export const photoApi = {
  list(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos', { params: { page, size } })
  },

  get(id: number) {
    return http.get<Photo>(`/photos/${id}`)
  },

  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<Photo>('/photos/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  batchUpload(files: File[], onProgress?: (index: number, progress: number) => void) {
    const formData = new FormData()
    files.forEach((f) => formData.append('files', f))

    return http.post<{ fileName: string; success: boolean; photo?: Photo; error?: string; message?: string }[]>(
      '/photos/batch-upload',
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 600000,
      }
    )
  },

  update(id: number, data: Partial<Photo>) {
    return http.put<Photo>(`/photos/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/photos/${id}`)
  },

  batchDelete(ids: number[]) {
    return http.delete<{ success: number; fail: number }>('/photos/batch', { data: ids })
  },

  batchFavorite(ids: number[], favorite: boolean) {
    return http.post<{ success: number; total: number }>('/photos/batch-favorite', { ids, favorite })
  },

  batchRating(ids: number[], rating: number) {
    return http.post<{ success: number; total: number }>('/photos/batch-rating', { ids, rating })
  },

  favorites(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/favorites', { params: { page, size } })
  },

  rated(minRating = 3, page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/rated', { params: { minRating, page, size } })
  },
}

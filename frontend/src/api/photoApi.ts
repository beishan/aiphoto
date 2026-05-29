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

  update(id: number, data: Partial<Photo>) {
    return http.put<Photo>(`/photos/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/photos/${id}`)
  },

  favorites(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/favorites', { params: { page, size } })
  },

  rated(minRating = 3, page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/rated', { params: { minRating, page, size } })
  },
}

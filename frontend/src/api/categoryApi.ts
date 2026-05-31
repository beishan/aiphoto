import http from './http'
import type { Category, PageResponse, Photo } from '@/types'

export const categoryApi = {
  list() {
    return http.get<Category[]>('/categories')
  },

  get(id: number) {
    return http.get<Category>(`/categories/${id}`)
  },

  getPhotos(id: number, page = 0, size = 40) {
    return http.get<PageResponse<Photo>>(`/categories/${id}/photos`, { params: { page, size } })
  },

  create(data: Partial<Category>) {
    return http.post<Category>('/categories', data)
  },

  update(id: number, data: Partial<Category>) {
    return http.put<Category>(`/categories/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/categories/${id}`)
  },

  train(id: number, photoIds: number[], threshold?: number) {
    return http.post(`/categories/${id}/train`, { photoIds, threshold })
  },

  addPhoto(id: number, photoId: number) {
    return http.post(`/categories/${id}/photos/${photoId}`)
  },

  removePhoto(id: number, photoId: number) {
    return http.delete(`/categories/${id}/photos/${photoId}`)
  },

  reclassify() {
    return http.post<{ assigned: number; message: string }>('/categories/reclassify')
  },
}

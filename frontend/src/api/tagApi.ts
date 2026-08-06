import http from './http'
import type { Tag, Photo } from '@/types'

export const tagApi = {
  list(search?: string, sortBy?: string) {
    return http.get<Tag[]>('/tags', { params: { search, sortBy } })
  },

  create(data: { name: string; color?: string; description?: string }) {
    return http.post<Tag>('/tags', data)
  },

  update(id: number, data: Partial<Tag>) {
    return http.put<Tag>(`/tags/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/tags/${id}`)
  },

  merge(sourceId: number, targetId: number) {
    return http.post('/tags/merge', { sourceId, targetId })
  },

  getPhotos(id: number, page = 0, size = 20) {
    return http.get<Photo[]>(`/tags/${id}/photos`, { params: { page, size } })
  },

  getCoverPhotos(id: number, limit = 4) {
    return http.get<Photo[]>(`/tags/${id}/cover`, { params: { limit } })
  },

  addToPhoto(photoId: number, tagId: number) {
    return http.post(`/tags/photos/${photoId}`, { tagId })
  },

  addByName(photoId: number, tagName: string) {
    return http.post(`/tags/photos/${photoId}`, { tagName })
  },

  removeFromPhoto(photoId: number, tagId: number) {
    return http.delete(`/tags/photos/${photoId}/${tagId}`)
  },

  batchAdd(photoIds: number[], tagId: number) {
    return http.post('/tags/batch-add', { photoIds, tagId })
  },

  batchRemove(photoIds: number[], tagId: number) {
    return http.post('/tags/batch-remove', { photoIds, tagId })
  },
}

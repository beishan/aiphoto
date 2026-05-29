import http from './http'
import type { Album } from '@/types'

export const albumApi = {
  list() {
    return http.get<Album[]>('/albums')
  },

  get(id: number) {
    return http.get<Album>(`/albums/${id}`)
  },

  create(data: Partial<Album>) {
    return http.post<Album>('/albums', data)
  },

  addPhoto(albumId: number, photoId: number) {
    return http.post(`/albums/${albumId}/photos/${photoId}`)
  },

  removePhoto(albumId: number, photoId: number) {
    return http.delete(`/albums/${albumId}/photos/${photoId}`)
  },

  delete(id: number) {
    return http.delete(`/albums/${id}`)
  },

  train(albumId: number, threshold?: number) {
    return http.post(`/albums/${albumId}/train`, { threshold })
  },
}

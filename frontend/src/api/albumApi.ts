import http from './http'
import type { Album, Photo } from '@/types'

export const albumApi = {
  list() {
    return http.get<Album[]>('/albums')
  },

  get(id: number) {
    return http.get<Album>(`/albums/${id}`)
  },

  getPhotos(id: number) {
    return http.get<Photo[]>(`/albums/${id}/photos`)
  },

  create(data: Partial<Album>) {
    return http.post<Album>('/albums', data)
  },

  update(id: number, data: Partial<Album>) {
    return http.put<Album>(`/albums/${id}`, data)
  },

  setCoverPhoto(albumId: number, photoId: number) {
    return http.put<Album>(`/albums/${albumId}/cover`, { photoId })
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

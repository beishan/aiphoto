import http from './http'
import type { ScanFolder, Photo, PageResponse } from '@/types'

export interface BrowseItem {
  name: string
  path: string
  isDirectory: boolean
  readable: boolean
}

export const folderApi = {
  list() {
    return http.get<ScanFolder[]>('/folders')
  },

  get(id: number) {
    return http.get<ScanFolder>(`/folders/${id}`)
  },

  create(data: { name: string; path: string; storageMode: string }) {
    return http.post<ScanFolder>('/folders', data)
  },

  update(id: number, data: Partial<ScanFolder>) {
    return http.put<ScanFolder>(`/folders/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/folders/${id}`)
  },

  scan(id: number) {
    return http.post<{ message: string }>(`/folders/${id}/scan`)
  },

  scanAll() {
    return http.post<{ message: string }>('/folders/scan-all')
  },

  toggleEnabled(id: number) {
    return http.post<ScanFolder>(`/folders/${id}/toggle-enabled`)
  },

  toggleHidden(id: number) {
    return http.post<ScanFolder>(`/folders/${id}/toggle-hidden`)
  },

  getPhotos(id: number, page = 0, size = 20) {
    return http.get<PageResponse<Photo>>(`/folders/${id}/photos`, {
      params: { page, size },
    })
  },

  browse(path = '') {
    return http.get<BrowseItem[]>('/folders/browse', {
      params: { path },
    })
  },
}

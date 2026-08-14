import http from './http'
import type { Photo, PhotoDetail, PageResponse } from '@/types'

export const photoApi = {
  list(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos', { params: { page, size } })
  },

  get(id: number) {
    return http.get<Photo>(`/photos/${id}`)
  },

  getDetail(id: number) {
    return http.get<PhotoDetail>(`/photos/${id}/detail`)
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

  async delete(id: number) {
    const response = await http.delete(`/photos/${id}`)
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async batchDelete(ids: number[]) {
    const response = await http.delete<{ success: number; fail: number }>('/photos/batch', { data: ids })
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  trash(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/trash', { params: { page, size } })
  },

  trashCount() {
    return http.get<{ count: number }>('/photos/trash/count')
  },

  async restore(id: number) {
    const response = await http.post<Photo>(`/photos/trash/${id}/restore`)
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async restoreAllTrash() {
    const response = await http.post<{ restored: number }>('/photos/trash/restore-all')
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async restoreTrashByIds(ids: number[]) {
    const response = await http.post<{ restored: number }>('/photos/trash/batch-restore', ids)
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async permanentDelete(id: number) {
    const response = await http.delete(`/photos/trash/${id}`)
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async permanentDeleteTrashByIds(ids: number[]) {
    const response = await http.delete<{ success: number; fail: number }>('/photos/trash/batch', { data: ids })
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  async clearTrash() {
    const response = await http.delete<{ deleted: number }>('/photos/trash')
    window.dispatchEvent(new Event('trash-changed'))
    return response
  },

  batchFavorite(ids: number[], favorite: boolean) {
    return http.post<{ success: number; total: number }>('/photos/batch-favorite', { ids, favorite })
  },

  batchRating(ids: number[], rating: number) {
    return http.post<{ success: number; total: number }>('/photos/batch-rating', { ids, rating })
  },

  batchTimeline(ids: number[], inTimeline: boolean) {
    return http.post<{ success: number; total: number }>('/photos/batch-timeline', { ids, inTimeline })
  },

  batchNote(ids: number[], note: string) {
    return http.post<{ success: number; total: number }>('/photos/batch-note', { ids, note })
  },

  toggleTimeline(id: number) {
    return http.post<Photo>(`/photos/${id}/toggle-timeline`)
  },

  favorites(page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/favorites', { params: { page, size } })
  },

  rated(minRating = 3, page = 0, size = 20) {
    return http.get<PageResponse<Photo>>('/photos/rated', { params: { minRating, page, size } })
  },
}

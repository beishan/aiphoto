import http from './http'
import type { Photo } from '@/types'

export const dedupApi = {
  similarPage(afterId = 0) {
    return http.get<{ groups: Photo[][]; nextCursor: number; hasMore: boolean; truncated: boolean }>('/dedup/similar-page', { params: { afterId } })
  },
  exactPage(page = 0) {
    return http.get<{ groups: { hash: string; photos: Photo[]; total: number }[]; hasMore: boolean }>('/dedup/exact-page', { params: { page } })
  },
  getGroups() {
    return http.get<Photo[][]>('/dedup/groups')
  },

  getSimilar() {
    return http.get<Photo[][]>('/dedup/similar')
  },

  deletePhoto(photoId: number) {
    return http.delete(`/dedup/${photoId}`)
  },
}

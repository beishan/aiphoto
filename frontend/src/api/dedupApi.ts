import http from './http'
import type { Photo } from '@/types'

export const dedupApi = {
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

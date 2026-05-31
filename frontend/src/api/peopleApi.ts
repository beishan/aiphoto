import http from './http'
import type { Person, PageResponse, Photo } from '@/types'

export const peopleApi = {
  list() {
    return http.get<Person[]>('/people')
  },

  get(id: number) {
    return http.get<Person>(`/people/${id}`)
  },

  getPhotos(id: number, page = 0, size = 40) {
    return http.get<PageResponse<Photo>>(`/people/${id}/photos`, { params: { page, size } })
  },

  update(id: number, data: Partial<Person>) {
    return http.put<Person>(`/people/${id}`, data)
  },

  merge(targetId: number, sourceId: number) {
    return http.post('/people/merge', { targetId, sourceId })
  },

  recluster() {
    return http.post<{ merged: number }>('/people/recluster')
  },
}

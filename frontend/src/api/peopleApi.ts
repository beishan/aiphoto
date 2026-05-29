import http from './http'
import type { Person } from '@/types'

export const peopleApi = {
  list() {
    return http.get<Person[]>('/people')
  },

  get(id: number) {
    return http.get<Person>(`/people/${id}`)
  },

  update(id: number, data: Partial<Person>) {
    return http.put<Person>(`/people/${id}`, data)
  },

  merge(targetId: number, sourceId: number) {
    return http.post('/people/merge', { targetId, sourceId })
  },
}

import http from './http'
import type { Person, PageResponse, Photo } from '@/types'

export interface Face {
  id: number
  photoId: number
  photoUrl: string | null
  bboxJson: string
  confidence: number | null
  personId: number | null
  personName: string | null
}

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

  getFaces(id: number) {
    return http.get<Face[]>(`/people/${id}/faces`)
  },

  getUnnamedFaces() {
    return http.get<Face[]>('/people/faces/unassigned')
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

  delete(id: number) {
    return http.delete(`/people/${id}`)
  },

  assignFace(faceId: number, personId: number) {
    return http.post(`/people/faces/${faceId}/assign`, { personId })
  },

  setCoverFace(personId: number, faceId: number) {
    return http.put(`/people/${personId}/cover-face`, { faceId })
  },
}

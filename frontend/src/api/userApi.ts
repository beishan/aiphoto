import http from './http'
import type { User } from '@/types'

export const userApi = {
  list() {
    return http.get<User[]>('/users')
  },

  me() {
    return http.get<User>('/users/me')
  },

  create(data: { username: string; password: string; role?: string; nickname?: string }) {
    return http.post<User>('/users', data)
  },

  delete(id: number) {
    return http.delete(`/users/${id}`)
  },

  resetPassword(id: number, password: string) {
    return http.post(`/users/${id}/reset-password`, { password })
  },

  toggleEnabled(id: number) {
    return http.post<User>(`/users/${id}/toggle-enabled`)
  },

  update(id: number, data: { nickname?: string; role?: string }) {
    return http.put<User>(`/users/${id}`, data)
  },
}

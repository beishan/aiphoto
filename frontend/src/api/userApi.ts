import http from './http'
import type { User } from '@/types'
import type { DockConfig } from '@/utils/themeAppearance'

export const userApi = {
  list() {
    return http.get<User[]>('/users')
  },

  me() {
    return http.get<User>('/users/me')
  },

  updateMe(data: {
    nickname?: string | null
    mood?: string | null
    birthDate?: string | null
    photoPreferences?: string | null
    notes?: string | null
  }) {
    return http.put<User>('/users/me', data)
  },

  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http.post<User>('/users/me/avatar', form)
  },

  deleteAvatar() {
    return http.delete<User>('/users/me/avatar')
  },

  updateTheme(theme: 'dark' | 'light' | 'macos26') {
    return http.put<User>('/users/me/theme', { theme })
  },

  updateDockConfig(config: DockConfig) {
    return http.put<User>('/users/me/dock-config', config)
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

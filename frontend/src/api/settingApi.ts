import http from './http'

export const settingApi = {
  getAll() {
    return http.get<Record<string, string>>('/settings')
  },

  update(settings: Record<string, string>) {
    return http.put('/settings', settings)
  },
}

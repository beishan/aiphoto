import http from './http'
import type { User } from '@/types'

export const authApi = {
  login(username: string, password: string) {
    return http.post<{ token: string; user: User }>('/auth/login', { username, password })
  },

  register(username: string, password: string) {
    return http.post<User>('/auth/register', { username, password })
  },
}

import http from './http'
import type { User, LoginResponse } from '@/types'

export const authApi = {
  login(username: string, password: string) {
    return http.post<LoginResponse>('/auth/login', { username, password })
  },

  register(username: string, password: string) {
    return http.post<User>('/auth/register', { username, password })
  },

  me() {
    return http.get<User>('/auth/me')
  },
}

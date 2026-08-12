import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// Request interceptor - add auth token (skip for auth endpoints)
http.interceptors.request.use((config) => {
  const url = config.url || ''
  // Don't send token to login/register endpoints
  if (!url.startsWith('/auth/')) {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// Response interceptor - an expired session is indicated by 401; 403 is an authorization error.
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default http

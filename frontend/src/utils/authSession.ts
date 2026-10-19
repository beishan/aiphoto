export function isSessionTokenValid(token: string | null, now = Date.now()): boolean {
  if (!token) return false
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return false
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp)
      && payload.exp * 1000 > now
  } catch { return false }
}

export function clearSession() {
  localStorage.removeItem('token')
  sessionStorage.removeItem('user')
}

let redirecting = false
export function redirectToLogin() {
  clearSession()
  if (window.location.pathname === '/login' || redirecting) return
  redirecting = true
  window.location.replace('/login')
}

export function checkSession() {
  if (window.location.pathname !== '/login'
      && !isSessionTokenValid(localStorage.getItem('token'))) redirectToLogin()
}

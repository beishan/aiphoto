import { readonly, ref } from 'vue'
import { userApi } from '@/api/userApi'
import type { User } from '@/types'

const hidden = ref(true)
export const hideImages = readonly(hidden)

function sessionUser(token: string | null): User | null {
  if (!token) return null
  try {
    const user: User | null = JSON.parse(sessionStorage.getItem('user') || 'null')
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(encoded), char => char.charCodeAt(0))
    const { sub } = JSON.parse(new TextDecoder().decode(bytes))
    return user?.username === sub ? user : null
  } catch {
    return null
  }
}

export function restoreImageVisibility(token: string | null) {
  hidden.value = sessionUser(token)?.hideImages !== false
}

export async function setHideImages(value: boolean) {
  const token = localStorage.getItem('token')
  const user = sessionUser(token)
  if (!user) throw new Error('请先登录后再保存显示设置')
  const { data } = await userApi.updateImageDisplay(value)
  if (token !== localStorage.getItem('token')) return
  const current = sessionUser(token)
  if (!current || current.id !== data.id) return
  sessionStorage.setItem('user', JSON.stringify({ ...current, hideImages: data.hideImages }))
  restoreImageVisibility(token)
}

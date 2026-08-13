import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import http from '@/api/http'
import type { DockIconName } from '@/components/DockIcon.vue'

const ICON_NAMES: DockIconName[] = ['photo', 'timeline', 'tags', 'albums', 'baby', 'search', 'settings', 'trashEmpty', 'trashFull']
const emptyUrls = () => Object.fromEntries(ICON_NAMES.map(name => [name, ''])) as Record<DockIconName, string>
const CACHE_PREFIX = 'memoryvault:dock-icons:'

function currentUserCacheKey(): string | null {
  try {
    const user = JSON.parse(sessionStorage.getItem('user') || 'null') as { id?: number; username?: string } | null
    const identity = user?.id ?? user?.username
    return identity === undefined || identity === null || identity === '' ? null : `${CACHE_PREFIX}${identity}`
  } catch {
    return null
  }
}

function readCachedUrls(key: string | null): Record<DockIconName, string> {
  if (!key) return emptyUrls()
  try {
    const cached = JSON.parse(localStorage.getItem(key) || '{}') as Partial<Record<DockIconName, unknown>>
    return Object.fromEntries(ICON_NAMES.map(name => [name, typeof cached[name] === 'string' ? cached[name] : ''])) as Record<DockIconName, string>
  } catch {
    return emptyUrls()
  }
}

export const useDockIconStore = defineStore('dockIconStore', () => {
  const activeCacheKey = ref<string | null>(currentUserCacheKey())
  const iconUrls = reactive<Record<DockIconName, string>>(readCachedUrls(activeCacheKey.value))
  const uploading = reactive<Record<DockIconName, boolean>>(Object.fromEntries(ICON_NAMES.map(name => [name, false])) as Record<DockIconName, boolean>)
  const loading = ref(false)
  let hydratedCacheKey: string | null = null
  let hydratePromise: Promise<void> | null = null

  function applyUrls(urls: Partial<Record<DockIconName, string>>, persist = true) {
    ICON_NAMES.forEach(name => { iconUrls[name] = urls[name] || '' })
    if (persist && activeCacheKey.value) {
      localStorage.setItem(activeCacheKey.value, JSON.stringify(iconUrls))
    }
  }

  function restoreCached() {
    const nextKey = currentUserCacheKey()
    if (nextKey === activeCacheKey.value) return
    activeCacheKey.value = nextKey
    hydratedCacheKey = null
    applyUrls(readCachedUrls(nextKey), false)
  }

  function deactivate() {
    activeCacheKey.value = null
    hydratedCacheKey = null
    applyUrls(emptyUrls(), false)
  }

  async function hydrate(force = false) {
    restoreCached()
    const requestCacheKey = activeCacheKey.value
    if (!requestCacheKey || (!force && hydratedCacheKey === requestCacheKey)) return
    if (hydratePromise) return hydratePromise

    loading.value = true
    hydratePromise = (async () => {
      try {
        const { data } = await http.get<Record<DockIconName, string>>('/users/me/dock-icons')
        if (activeCacheKey.value === requestCacheKey) {
          applyUrls(data)
          hydratedCacheKey = requestCacheKey
        }
      } finally {
        loading.value = false
        hydratePromise = null
      }
    })()
    return hydratePromise
  }

  async function upload(name: DockIconName, file: File) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('仅支持 JPG、PNG 或 WebP 图片')
    if (file.size > 5 * 1024 * 1024) throw new Error('图标图片不能超过 5MB')
    const body = new FormData()
    body.append('file', file)
    uploading[name] = true
    try {
      const { data } = await http.post<Record<DockIconName, string>>(`/users/me/dock-icons/${name}`, body, { headers: { 'Content-Type': 'multipart/form-data' } })
      applyUrls(data)
    } finally { uploading[name] = false }
  }

  async function remove(name: DockIconName) {
    uploading[name] = true
    try {
      const { data } = await http.delete<Record<DockIconName, string>>(`/users/me/dock-icons/${name}`)
      applyUrls(data)
    } finally { uploading[name] = false }
  }

  return { iconUrls, uploading, loading, hydrate, restoreCached, deactivate, upload, remove }
})

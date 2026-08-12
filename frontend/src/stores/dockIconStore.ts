import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import http from '@/api/http'
import type { DockIconName } from '@/components/DockIcon.vue'

const ICON_NAMES: DockIconName[] = ['photo', 'timeline', 'tags', 'albums', 'baby', 'search', 'settings', 'trashEmpty', 'trashFull']
const emptyUrls = () => Object.fromEntries(ICON_NAMES.map(name => [name, ''])) as Record<DockIconName, string>

export const useDockIconStore = defineStore('dockIconStore', () => {
  const iconUrls = reactive<Record<DockIconName, string>>(emptyUrls())
  const uploading = reactive<Record<DockIconName, boolean>>(Object.fromEntries(ICON_NAMES.map(name => [name, false])) as Record<DockIconName, boolean>)
  const loading = ref(false)

  function applyUrls(urls: Partial<Record<DockIconName, string>>) {
    ICON_NAMES.forEach(name => { iconUrls[name] = urls[name] || '' })
  }

  async function hydrate() {
    loading.value = true
    try {
      const { data } = await http.get<Record<DockIconName, string>>('/users/me/dock-icons')
      applyUrls(data)
    } finally { loading.value = false }
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

  return { iconUrls, uploading, loading, hydrate, upload, remove }
})

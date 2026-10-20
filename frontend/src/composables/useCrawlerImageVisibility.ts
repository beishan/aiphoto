import { onMounted, onUnmounted, ref } from 'vue'
import { userApi } from '@/api/userApi'
import type { User } from '@/types'

const changeEvent = 'crawler-image-visibility-changed'
let saveRevision = 0

function currentUser(): User | null {
  try { return JSON.parse(sessionStorage.getItem('user') || 'null') }
  catch { return null }
}

export function useCrawlerImageVisibility() {
  const showCrawlerImages = ref(true)
  function restore() {
    showCrawlerImages.value = currentUser()?.crawlerShowImages !== false
  }
  function applyUser(user: User, token: string) {
    const current = currentUser()
    if (localStorage.getItem('token') !== token || current?.id !== user.id) return
    sessionStorage.setItem('user', JSON.stringify({ ...current, crawlerShowImages: user.crawlerShowImages }))
    window.dispatchEvent(new Event(changeEvent))
  }
  async function setShowCrawlerImages(value: boolean) {
    const user = currentUser()
    const token = localStorage.getItem('token')
    if (!user?.id || !token) throw new Error('请先登录后再保存图片显示设置')
    saveRevision++
    const { data } = await userApi.updateCrawlerDisplay(value)
    applyUser(data, token)
  }
  restore()
  onMounted(() => {
    restore()
    window.addEventListener(changeEvent, restore)
    window.addEventListener('storage', restore)
    window.addEventListener('user-profile-updated', restore)
    const token = localStorage.getItem('token')
    if (token && currentUser()?.id) {
      const revision = saveRevision
      void userApi.me().then(({ data }) => {
        if (revision === saveRevision) applyUser(data, token)
      }).catch(() => {
        // Keep the last account value when the server is temporarily unavailable.
      })
    }
  })
  onUnmounted(() => {
    window.removeEventListener(changeEvent, restore)
    window.removeEventListener('storage', restore)
    window.removeEventListener('user-profile-updated', restore)
  })
  return { showCrawlerImages, setShowCrawlerImages }
}

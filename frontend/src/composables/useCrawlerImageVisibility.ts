import { onMounted, onUnmounted, ref } from 'vue'

const changeEvent = 'crawler-image-visibility-changed'

function currentUserKey(): string | null {
  try {
    const user = JSON.parse(sessionStorage.getItem('user') || 'null')
    const identity = user?.id ?? user?.username
    return identity === undefined || identity === null || identity === ''
      ? null : `aiphoto:crawler-show-images:${identity}`
  } catch { return null }
}

export function useCrawlerImageVisibility() {
  const showCrawlerImages = ref(true)
  function restore() {
    const key = currentUserKey()
    try { showCrawlerImages.value = !key || localStorage.getItem(key) !== 'false' }
    catch { showCrawlerImages.value = true }
  }
  function setShowCrawlerImages(value: boolean) {
    const key = currentUserKey()
    if (!key) throw new Error('请先登录后再保存图片显示设置')
    localStorage.setItem(key, String(value))
    window.dispatchEvent(new Event(changeEvent))
  }
  restore()
  onMounted(() => {
    restore()
    window.addEventListener(changeEvent, restore)
    window.addEventListener('storage', restore)
    window.addEventListener('user-profile-updated', restore)
  })
  onUnmounted(() => {
    window.removeEventListener(changeEvent, restore)
    window.removeEventListener('storage', restore)
    window.removeEventListener('user-profile-updated', restore)
  })
  return { showCrawlerImages, setShowCrawlerImages }
}

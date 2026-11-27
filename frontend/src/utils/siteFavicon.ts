import { watch } from 'vue'
import { hideImages } from '@/composables/imageVisibility'

export interface SiteFaviconStatus {
  hasCustom: boolean
  url?: string
  version: number
}

const placeholderUrl = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'

export const DEFAULT_FAVICON_URL = '/vite.svg'

export function faviconUrl(status: SiteFaviconStatus) {
  return status.hasCustom && status.url
    ? `${status.url}?v=${status.version}`
    : DEFAULT_FAVICON_URL
}

let currentUrl = DEFAULT_FAVICON_URL

export function applySiteFavicon(url: string) {
  currentUrl = url
  updateFaviconLink()
}

function updateFaviconLink() {
  let link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.removeAttribute('type')
  link.href = hideImages.value ? placeholderUrl : currentUrl
}

watch(hideImages, updateFaviconLink)

export async function loadSiteFavicon() {
  try {
    const response = await fetch('/api/site/favicon/status', { cache: 'no-store' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const status = await response.json() as SiteFaviconStatus
    applySiteFavicon(faviconUrl(status))
  } catch {
    applySiteFavicon(DEFAULT_FAVICON_URL)
  }
}

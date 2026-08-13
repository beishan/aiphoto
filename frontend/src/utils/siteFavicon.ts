export interface SiteFaviconStatus {
  hasCustom: boolean
  url?: string
  version: number
}

export const DEFAULT_FAVICON_URL = '/vite.svg'

export function faviconUrl(status: SiteFaviconStatus) {
  return status.hasCustom && status.url
    ? `${status.url}?v=${status.version}`
    : DEFAULT_FAVICON_URL
}

export function applySiteFavicon(url: string) {
  let link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.removeAttribute('type')
  link.href = url
}

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

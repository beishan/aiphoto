import type { CrawlAsset } from '@/types'

/** Enumerate the task, independent of the currently displayed card page. */
export async function allTaskImportIds(
  fetchPage: (page: number) => Promise<{ content: CrawlAsset[]; totalElements: number }>,
  eligible: (asset: CrawlAsset) => boolean,
  active: () => boolean,
): Promise<number[]> {
  const ids = new Set<number>()
  let page = 0
  let count = 0
  while (active()) {
    const data = await fetchPage(page++)
    if (!active()) return []
    data.content.filter(eligible).forEach(asset => ids.add(asset.id))
    count += data.content.length
    if (!data.content.length || count >= data.totalElements) break
  }
  return [...ids]
}

/** Completed and pending imports leave the review cards; failed imports become selectable again. */
export function isTaskAssetImportable(asset: Pick<CrawlAsset, 'id' | 'status' | 'importedPhotoId'>, pending: Set<number>): boolean {
  return asset.status === 'DOWNLOADED' && !asset.importedPhotoId && !pending.has(asset.id)
}

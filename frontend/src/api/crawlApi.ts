import http from './http'
import { normalizePage, type SpringPage } from './pageResponse'
import type { CrawlAsset, CrawlImageSkip, CrawlJob, CrawlPage, CrawlRule, CrawlSite, PageResponse } from '@/types'

export interface CrawlImportBatch {
  id: number
  jobId: number
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
  requested: number
  success: number
  fail: number
  skipped: number
  albumId: number | null
  tagIdsJson: string
  createdAt: string
  finishedAt: string | null
}

export interface CrawlImportItem {
  id: number
  batchId: number
  assetId: number
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'SKIPPED' | 'FAILED'
  photoId: number | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface InspectionProxy { id: number; name: string; enabled: boolean; proxyType: 'HTTP' | 'MIHOMO' }
export interface CrawlImageProgress { total: number; processed: number; downloaded: number; runningAsset: CrawlAsset | null; currentProxyName: string | null }
export interface CrawlSiteInfo { title: string; description: string; finalUrl: string }
export interface CrawlScanResults {
  content: { id: number; url: string; name: string | null; result: 'NEW' | 'DUPLICATE'; occurrences: number }[]
  totalElements: number; newCount: number; duplicateCount: number
}
export interface CrawlReviewPageGroup { id: number; name: string | null; url: string; assetCount: number }
export interface CrawlScanPage {
  id?: number; siteId: number; name: string; url: string; autoScanEnabled: boolean; autoCollectEnabled: boolean;
  autoCollectPriority: number;
  scanIntervalMinutes: number; maxPages: number; lastScanAt?: string
}
export interface DiscoveredPage {
  id: number; scanJobId: number; siteId: number; scanPageId: number | null; name: string | null; url: string;
  createdAt: string; collectionJobId: number | null; status: CrawlJob['status'] | 'PENDING'; collectable: boolean
}
interface CollectionPageResponse {
  content: DiscoveredPage[]
  page: Omit<PageResponse<DiscoveredPage>, 'content'>
}
export interface CrawlSiteQueue {
  siteId: number; name: string; dedicated: boolean; concurrency: number; queued: number; running: number;
  executors: { id: number; name: string; enabled: boolean; frozen: boolean; activeJobId: number | null }[]
}

export const crawlApi = {
  scanResults: (id: number, page = 0) => http.get<CrawlScanResults>(`/crawl/jobs/${id}/scan-results`, { params: { page, size: 50 } }),
  scanPages: () => http.get<CrawlScanPage[]>('/crawl/scan-pages'),
  saveScanPage: (page: CrawlScanPage) => page.id
    ? http.put<CrawlScanPage>(`/crawl/scan-pages/${page.id}`, page)
    : http.post<CrawlScanPage>('/crawl/scan-pages', page),
  deleteScanPage: (id: number) => http.delete(`/crawl/scan-pages/${id}`),
  scan: (id: number) => http.post<CrawlJob>(`/crawl/scan-pages/${id}/scan`),
  collectionPages: (page = 0, query = '', siteId?: number, pending = false, size = 50, collected = false) =>
    http.get<PageResponse<DiscoveredPage> | CollectionPageResponse>('/crawl/collection-pages', { params: { page, size, query, siteId, pending, collected } })
      .then(response => ({
        ...response,
        // Spring VIA_DTO places pagination metadata inside `page`.
        data: 'page' in response.data
          ? { content: response.data.content, ...response.data.page }
          : response.data,
      })),
  collectPage: (id: number) => http.post<CrawlJob>(`/crawl/collection-pages/${id}/collect`),
  deleteCollectionPage: (id: number) => http.delete(`/crawl/collection-pages/${id}`),
  deleteCollectionPages: (ids: number[]) => http.delete('/crawl/collection-pages', { data: ids }),
  queues: () => http.get<CrawlSiteQueue[]>('/crawl/queues'),
  assignExecutors: (siteId: number, ids: number[]) => http.put(`/crawl/queues/${siteId}/executors`, ids),
  inspectionProxies: () => http.get<InspectionProxy[]>('/crawl/inspection-proxies'),
  inspectSite: (site: CrawlSite) => http.post<CrawlSiteInfo>('/crawl/sites/inspect', site, { timeout: 120000 }),
  sites: () => http.get<CrawlSite[]>('/crawl/sites'),
  saveSite: (site: CrawlSite) => site.id
    ? http.put<CrawlSite>(`/crawl/sites/${site.id}`, site)
    : http.post<CrawlSite>('/crawl/sites', site),
  rules: (siteId: number) => http.get<CrawlRule[]>(`/crawl/sites/${siteId}/rules`),
  saveRule: (rule: CrawlRule) => rule.id
    ? http.put<CrawlRule>(`/crawl/rules/${rule.id}`, rule)
    : http.post<CrawlRule>('/crawl/rules', rule),
  preview: (rule: CrawlRule, site: CrawlSite) => http.post<{ rule: CrawlRule; detailUrls: string[]; imageUrls: string[]; imagePreviewError: string | null }>('/crawl/rules/preview', rule, {
    params: { inspectionProxyMode: site.inspectionProxyMode, inspectionProxyId: site.inspectionProxyId ?? undefined },
    timeout: 120000,
  }),
  jobs: (pageName?: string) => http.get<CrawlJob[]>('/crawl/jobs', { params: pageName ? { pageName } : {} }),
  createJob: (ruleId: number) => http.post<{ jobId: number }>('/crawl/jobs', { ruleId }),
  job: (id: number) => http.get<CrawlJob>(`/crawl/jobs/${id}`),
  updateJobPriorities: (jobIds: number[], priority: number | null) => http.patch<CrawlJob[]>('/crawl/jobs/priority', { jobIds, priority }),
  updateJobPriority: (id: number, priority: number | null) => http.patch<CrawlJob>(`/crawl/jobs/${id}/priority`, { priority }),
  imageProgress: (id: number) => http.get<CrawlImageProgress>(`/crawl/jobs/${id}/image-progress`),
  pages: (id: number, page = 0, size = 50, query = '') => http.get<PageResponse<CrawlPage> & { includedElements: number }>(`/crawl/jobs/${id}/pages`, { params: { page, size, query: query || undefined } }),
  selectPages: (jobId: number, ids: number[], included: boolean) => http.patch<{ success: number; included: number }>(`/crawl/jobs/${jobId}/pages`, { ids, included }),
  download: (id: number) => http.post(`/crawl/jobs/${id}/download`),
  pause: (id: number) => http.post<CrawlJob>(`/crawl/jobs/${id}/pause`),
  resume: (id: number) => http.post(`/crawl/jobs/${id}/resume`),
  cancel: (id: number) => http.post<CrawlJob>(`/crawl/jobs/${id}/cancel`),
  assets: (id: number, page = 0, size = 60, status = '', exactDuplicates = false, similarOnly = false, pageId?: number) => http.get<SpringPage<CrawlAsset>>(`/crawl/jobs/${id}/assets`, { params: { page, size, status: status || undefined, exactDuplicates, similarOnly, pageId } })
    .then(response => ({ ...response, data: normalizePage(response.data) })),
  reviewPages: () => http.get<CrawlReviewPageGroup[]>('/crawl/review/pages'),
  reviewAssets: (page = 0, size = 60, status = '', exactDuplicates = false, similarOnly = false, sourcePageId?: number | null, importStatus = 'NOT_IMPORTED') => http.get<SpringPage<CrawlAsset>>('/crawl/review/assets', { params: { page, size, status: status || undefined, exactDuplicates, similarOnly, sourcePageId: sourcePageId ?? undefined, importStatus } })
    .then(response => ({ ...response, data: normalizePage(response.data) })),
  skipAssets: (jobId: number, ids: number[], reason: string) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/skip`, { ids, reason }),
  imageSkips: (page = 0, size = 50, query = '') => http.get<PageResponse<CrawlImageSkip>>('/crawl/image-skips', { params: { page, size, query: query || undefined } }),
  deleteImageSkip: (id: number) => http.delete<{ success: number }>(`/crawl/image-skips/${id}`),
  analyzeSimilarity: (jobId: number, threshold: number) => http.post<{ analyzed: number; groups: number; matched: number; threshold: number }>(`/crawl/jobs/${jobId}/similarity`, null, { params: { threshold } }),
  deleteDownloadedFiles: (jobId: number) => http.post<{ success: number; failed: number }>(`/crawl/jobs/${jobId}/assets/delete-downloaded`),
  deleteAssets: (jobId: number, ids: number[]) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/delete`, ids),
  restoreAssets: (jobId: number, ids: number[]) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/restore`, ids),
  editAssets: (jobId: number, ids: number[], note: string) => http.patch<{ success: number }>(`/crawl/jobs/${jobId}/assets`, { ids, note }),
  importAssets: (jobId: number, ids: number[], albumId: number | null, tagIds: number[], idempotencyKey: string) => http.post<{ batchId: number; status: CrawlImportBatch['status']; success: number; fail: number; skipped: number }>(`/crawl/jobs/${jobId}/import`, { ids, albumId, tagIds, idempotencyKey }),
  importPreview: (jobId: number, ids: number[]) => http.post<{ requested: number; importable: number; duplicates: number; alreadyImported: number; blocked: number }>(`/crawl/jobs/${jobId}/import-preview`, { ids, albumId: null, tagIds: [] }),
  importBatch: (batchId: number) => http.get<CrawlImportBatch>(`/crawl/imports/${batchId}`),
  importItems: (batchId: number, page = 0, size = 50, status = '') => http.get<PageResponse<CrawlImportItem>>(`/crawl/imports/${batchId}/items`, { params: { page, size, status: status || undefined } }),
  purgeJob: (jobId: number) => http.delete<{ success: number; fileFail: number }>(`/crawl/jobs/${jobId}`),
  content: (assetId: number) => http.get<Blob>(`/crawl/assets/${assetId}/content`, { responseType: 'blob' }),
  thumbnail: (assetId: number, signal?: AbortSignal) => http.get<Blob>(`/crawl/assets/${assetId}/thumbnail`, { responseType: 'blob', signal }),
}

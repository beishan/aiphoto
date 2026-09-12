import http from './http'
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

export const crawlApi = {
  sites: () => http.get<CrawlSite[]>('/crawl/sites'),
  saveSite: (site: CrawlSite) => site.id
    ? http.put<CrawlSite>(`/crawl/sites/${site.id}`, site)
    : http.post<CrawlSite>('/crawl/sites', site),
  rules: (siteId: number) => http.get<CrawlRule[]>(`/crawl/sites/${siteId}/rules`),
  saveRule: (rule: CrawlRule) => rule.id
    ? http.put<CrawlRule>(`/crawl/rules/${rule.id}`, rule)
    : http.post<CrawlRule>('/crawl/rules', rule),
  preview: (rule: CrawlRule) => http.post<{ rule: CrawlRule; detailUrls: string[]; imageUrls: string[]; imagePreviewError: string | null }>('/crawl/rules/preview', rule),
  jobs: () => http.get<CrawlJob[]>('/crawl/jobs'),
  createJob: (ruleId: number) => http.post<{ jobId: number }>('/crawl/jobs', { ruleId }),
  job: (id: number) => http.get<CrawlJob>(`/crawl/jobs/${id}`),
  pages: (id: number, page = 0, size = 50, query = '') => http.get<PageResponse<CrawlPage> & { includedElements: number }>(`/crawl/jobs/${id}/pages`, { params: { page, size, query: query || undefined } }),
  selectPages: (jobId: number, ids: number[], included: boolean) => http.patch<{ success: number; included: number }>(`/crawl/jobs/${jobId}/pages`, { ids, included }),
  download: (id: number) => http.post(`/crawl/jobs/${id}/download`),
  pause: (id: number) => http.post<CrawlJob>(`/crawl/jobs/${id}/pause`),
  resume: (id: number) => http.post(`/crawl/jobs/${id}/resume`),
  cancel: (id: number) => http.post<CrawlJob>(`/crawl/jobs/${id}/cancel`),
  assets: (id: number, page = 0, size = 60, status = '', exactDuplicates = false, similarOnly = false, pageId?: number) => http.get<PageResponse<CrawlAsset>>(`/crawl/jobs/${id}/assets`, { params: { page, size, status: status || undefined, exactDuplicates, similarOnly, pageId } }),
  skipAssets: (jobId: number, ids: number[], reason: string) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/skip`, { ids, reason }),
  imageSkips: (page = 0, size = 50, query = '') => http.get<PageResponse<CrawlImageSkip>>('/crawl/image-skips', { params: { page, size, query: query || undefined } }),
  deleteImageSkip: (id: number) => http.delete<{ success: number }>(`/crawl/image-skips/${id}`),
  analyzeSimilarity: (jobId: number, threshold: number) => http.post<{ analyzed: number; groups: number; matched: number; threshold: number }>(`/crawl/jobs/${jobId}/similarity`, null, { params: { threshold } }),
  deleteAssets: (jobId: number, ids: number[]) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/delete`, ids),
  restoreAssets: (jobId: number, ids: number[]) => http.post<{ success: number }>(`/crawl/jobs/${jobId}/assets/restore`, ids),
  editAssets: (jobId: number, ids: number[], note: string) => http.patch<{ success: number }>(`/crawl/jobs/${jobId}/assets`, { ids, note }),
  importAssets: (jobId: number, ids: number[], albumId: number | null, tagIds: number[], idempotencyKey: string) => http.post<{ batchId: number; status: CrawlImportBatch['status']; success: number; fail: number; skipped: number }>(`/crawl/jobs/${jobId}/import`, { ids, albumId, tagIds, idempotencyKey }),
  importPreview: (jobId: number, ids: number[]) => http.post<{ requested: number; importable: number; duplicates: number; alreadyImported: number; blocked: number }>(`/crawl/jobs/${jobId}/import-preview`, { ids, albumId: null, tagIds: [] }),
  importBatch: (batchId: number) => http.get<CrawlImportBatch>(`/crawl/imports/${batchId}`),
  importItems: (batchId: number, page = 0, size = 50, status = '') => http.get<PageResponse<CrawlImportItem>>(`/crawl/imports/${batchId}/items`, { params: { page, size, status: status || undefined } }),
  purgeJob: (jobId: number) => http.delete<{ success: number; fileFail: number }>(`/crawl/jobs/${jobId}`),
  content: (assetId: number) => http.get<Blob>(`/crawl/assets/${assetId}/content`, { responseType: 'blob' }),
  thumbnail: (assetId: number) => http.get<Blob>(`/crawl/assets/${assetId}/thumbnail`, { responseType: 'blob' }),
}

import type { CrawlJob } from '@/types'

/** Both task lists and detail views report processed images during collection. */
export function crawlJobProgress(job: CrawlJob, images?: { total: number; processed: number }): number {
  if (job.status === 'COMPLETED') return 100
  if (job.phase === 'DISCOVERY') return Math.max(0, Math.min(95, job.listProcessed || 0))
  if (job.phase === 'AWAITING_CONFIRMATION') return 45
  const total = images?.total ?? job.imagesTotal ?? 0
  const processed = images?.processed ?? job.imagesProcessed ?? 0
  if (total <= 0) return 0
  const limit = ['RUNNING', 'QUEUED', 'PAUSED'].includes(job.status) ? 99 : 100
  return Math.max(0, Math.min(limit, Math.round(processed / total * 100)))
}

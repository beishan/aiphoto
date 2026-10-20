import http from './http'

export type StatisticsGranularity = 'DAY' | 'HOUR' | 'MINUTE'
export interface CrawlStatisticsCounts {
  newPages: number
  newImages: number
  pagesCollected: number
  pagesSuccess: number
  pagesFailed: number
  pagesPartial: number
  pagesCancelled: number
  pagesSkipped: number
  imagesSuccess: number
  imagesFailed: number
  imagesSkipped: number
  durationSamples: number
  durationTotalMillis: number
  durationAverageMillis: number
  durationMinMillis: number
  durationMaxMillis: number
  durationBands: number[]
}
export interface CrawlStatistics {
  granularity: StatisticsGranularity
  start: string
  end: string
  timezone: string
  total: CrawlStatisticsCounts
  buckets: { time: string; counts: CrawlStatisticsCounts }[]
  sites: { siteId: number; siteName: string; counts: CrawlStatisticsCounts }[]
}
export const crawlStatisticsApi = {
  get: (granularity: StatisticsGranularity, start: string, end: string) =>
    http.get<CrawlStatistics>('/crawl/statistics', { params: { granularity, start, end, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone } }),
}

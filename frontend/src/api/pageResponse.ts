import type { PageResponse } from '@/types'

export type SpringPage<T> = PageResponse<T> | {
  content: T[]
  page: Omit<PageResponse<T>, 'content'>
}

/** Spring VIA_DTO nests counts under page; older responses expose them directly. */
export function normalizePage<T>(data: SpringPage<T>): PageResponse<T> {
  return 'page' in data ? { content: data.content, ...data.page } : data
}

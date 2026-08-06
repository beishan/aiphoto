import http from './http'
import type { Photo, PageResponse } from '@/types'

export interface SearchParams {
  query?: string
  type?: 'text' | 'semantic'
  startDate?: string
  endDate?: string
  personId?: number
  tagId?: number
  minRating?: number
  maxRating?: number
  hasDescription?: boolean
  inTimeline?: boolean
  folderId?: number
  fileType?: string
  sortBy?: string
  sortOrder?: string
  page?: number
  size?: number
}

export const searchApi = {
  search(params: SearchParams) {
    return http.get<PageResponse<Photo>>('/search', { params })
  },
}

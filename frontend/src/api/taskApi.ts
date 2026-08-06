import http from './http'
import type { PageResponse } from '@/types'

export interface AiTask {
  id: number
  type: 'INDEX' | 'TRAIN' | 'DEDUP' | 'CAPTION' | 'BATCH_EMBED'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  progress: number
  photoIdsJson: string | null
  resultJson: string | null
  createdAt: string
  finishedAt: string | null
}

export const taskApi = {
  list(page = 0, size = 20) {
    return http.get<PageResponse<AiTask>>('/tasks', { params: { page, size } })
  },

  get(id: number) {
    return http.get<AiTask>(`/tasks/${id}`)
  },
}

import http from './http'

export type ModelName = 'clip' | 'insightface' | 'yolo' | 'blip'

export interface ModelStatus {
  name: ModelName
  enabled: boolean
  path: string
  exists: boolean
  loaded: boolean
  error: string | null
}

export interface ModelOverview {
  root: string
  offline: boolean
  models: ModelStatus[]
}

export interface ModelFile {
  name: string
  path: string
  directory: boolean
  size: number | null
}

// Model catalog types
export interface ModelCatalogItem {
  key: string
  label: string
  defaultModel: string
  category: string
}

export interface OnlineModel {
  id: string
  name: string
  typeKey: string
  typeLabel: string
  version: string
  size: number
  device: string
  precision: string
  performance: string
  source: string
  url: string
}

export type DownloadStatus = 'PENDING' | 'DOWNLOADING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'INSTALLING' | 'INSTALLED'

export interface DownloadTask {
  taskId: string
  modelId: string
  modelName: string
  typeKey: string
  url: string
  totalSize: number
  downloadedSize: number
  progress: number
  status: DownloadStatus
  errorMessage: string | null
  startTime: string | null
  endTime: string | null
}

export const settingApi = {
  getAll() {
    return http.get<Record<string, string>>('/settings')
  },

  update(settings: Record<string, string>) {
    return http.put('/settings', settings)
  },

  // Local model management
  getModels() {
    return http.get<ModelOverview>('/settings/models')
  },

  browseModels(directory = '') {
    return http.get<ModelFile[]>('/settings/models/files', { params: { directory } })
  },

  configureModel(name: ModelName, path: string, enabled: boolean) {
    return http.put<ModelStatus>(`/settings/models/${name}`, { path, enabled })
  },

  reloadModel(name: ModelName) {
    return http.post<ModelStatus>(`/settings/models/${name}/reload`)
  },

  uploadModel(directory: string, file: File) {
    const form = new FormData()
    form.append('file', file)
    return http.post<ModelFile>('/settings/models/upload', form, {
      params: { directory },
      timeout: 0,
    })
  },

  // Model catalog
  getModelCatalog() {
    return http.get<ModelCatalogItem[]>('/settings/models/catalog')
  },

  getOnlineModels() {
    return http.get<OnlineModel[]>('/settings/models/online')
  },

  // Model download management
  startDownload(modelId: string) {
    return http.post<DownloadTask>('/settings/models/download', { modelId })
  },

  getAllDownloads() {
    return http.get<Record<string, DownloadTask>>('/settings/models/downloads')
  },

  getDownload(taskId: string) {
    return http.get<DownloadTask>(`/settings/models/downloads/${taskId}`)
  },

  pauseDownload(taskId: string) {
    return http.post<DownloadTask>(`/settings/models/downloads/${taskId}/pause`)
  },

  resumeDownload(taskId: string) {
    return http.post<DownloadTask>(`/settings/models/downloads/${taskId}/resume`)
  },

  cancelDownload(taskId: string) {
    return http.post<DownloadTask>(`/settings/models/downloads/${taskId}/cancel`)
  },

  retryDownload(taskId: string) {
    return http.post<DownloadTask>(`/settings/models/downloads/${taskId}/retry`)
  },

  setCurrentModel(taskId: string) {
    return http.post<DownloadTask>(`/settings/models/downloads/${taskId}/set-current`)
  },

  // System info & storage
  getStorageInfo() {
    return http.get<Record<string, unknown>>('/settings/storage')
  },

  getSystemInfo() {
    return http.get<Record<string, unknown>>('/settings/system-info')
  },
}

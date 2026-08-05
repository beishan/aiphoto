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

export const settingApi = {
  getAll() {
    return http.get<Record<string, string>>('/settings')
  },

  update(settings: Record<string, string>) {
    return http.put('/settings', settings)
  },

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
}

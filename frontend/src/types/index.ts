export interface Photo {
  id: number
  filePath: string
  exifDate: string | null
  gpsLat: number | null
  gpsLng: number | null
  rating: number | null
  note: string | null
  aiCaption: string | null
  width: number | null
  height: number | null
  fileSize: number | null
  mediaType: 'PHOTO' | 'VIDEO' | 'GIF' | 'RAW'
  favorite: boolean
  originalFilename: string | null
  thumbnailUrl: string | null
  originalUrl: string | null
  tags: string[]
  createdAt: string
}

export interface Album {
  id: number
  name: string
  type: 'VIRTUAL' | 'DIRECTORY' | 'TRAINING' | 'BABY'
  coverPhotoId: number | null
  coverPhotoUrl: string | null
  shared: boolean
  birthDate: string | null
  photoCount: number
  createdAt: string
}

export interface Person {
  id: number
  name: string | null
  coverFaceId: number | null
  coverPhotoUrl: string | null
  photoCount: number
  firstSeen: string | null
  lastSeen: string | null
}

export interface User {
  id: number
  username: string
  role: string
  avatar: string | null
}

export interface LoginResponse {
  token: string
  user: User
}

export interface TaskProgress {
  taskId: number
  type: string
  status: string
  progress: number
  message: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface TimelineData {
  [year: number]: {
    [month: number]: Photo[]
  }
}

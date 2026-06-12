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

export interface Tag {
  id: number
  name: string
  color: string | null
  type: string
  category: string | null
  confidence: number | null
  source: string | null
}

export interface PhotoDetail extends Omit<Photo, 'tags'> {
  fileHashMd5: string | null
  fileHashPhash: string | null
  tags: Tag[]
  people: Person[]
}

export interface Album {
  id: number
  name: string
  description: string | null
  type: 'VIRTUAL' | 'DIRECTORY' | 'TRAINING' | 'BABY'
  coverPhotoId: number | null
  coverPhotoUrl: string | null
  shared: boolean
  birthDate: string | null
  photoCount: number
  createdAt: string
}

export interface Category {
  id: number
  name: string
  icon: string | null
  color: string | null
  isSystem: boolean
  coverPhotoId: number | null
  coverPhotoUrl: string | null
  photoCount: number
  trained: boolean
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

export interface ScanFolder {
  id: number
  name: string
  path: string
  storageMode: 'COPY' | 'LINK'
  scanStatus: 'IDLE' | 'SCANNING' | 'COMPLETED' | 'ERROR'
  lastScanAt: string | null
  photoCount: number
  errorMessage: string | null
  createdAt: string
}

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
  inTimeline: boolean
  originalFilename: string | null
  thumbnailUrl: string | null
  originalUrl: string | null
  tags: string[]
  sourceFolderId: number | null
  createdAt: string
}

export interface Tag {
  id: number
  name: string
  color: string | null
  type: string
  category: string | null
  description: string | null
  sortOrder: number
  photoCount: number
  confidence: number | null
  source: string | null
  createdAt: string
}

export interface PhotoDetail extends Omit<Photo, 'tags'> {
  fileHashMd5: string | null
  fileHashPhash: string | null
  tags: Tag[]
  people: Person[]
  sourceFolderName: string | null
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
  nickname: string | null
  mood: string | null
  birthDate: string | null
  photoPreferences: string | null
  notes: string | null
  enabled: boolean
  createdAt: string
  lastLoginAt: string | null
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
  videoCount: number
  fileCount: number
  scanProgress: number
  enabled: boolean
  hidden: boolean
  errorMessage: string | null
  createdAt: string
}

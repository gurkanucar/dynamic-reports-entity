/** Success envelope of the platform APIs: `{ data, meta }` (docs/REST API Tasarım Rehberi.md). */
export interface Envelope<T> {
  data: T
  meta?: PageMeta | null
}

export interface PageMeta {
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface UserRef {
  username: string
}

export interface ErrorInfo {
  code: string
  message: string
}

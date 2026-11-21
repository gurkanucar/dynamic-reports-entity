import type { UserRef } from "@/shared/types/api.types"

export interface TemplateRequest {
  name: string
  description?: string | null
  subject: string
  bodyHtml: string
}

export interface TemplateSummaryResponse {
  id: number
  name: string
  description: string | null
  owner: UserRef
  version: number
  updatedAt: string
}

export interface TemplateResponse extends TemplateSummaryResponse {
  subject: string
  bodyHtml: string
  createdAt: string
  updatedBy: UserRef
}

export interface TemplatePreviewRequest {
  subject: string
  bodyHtml: string
  model?: Record<string, unknown> | null
}

export interface TemplatePreviewResponse {
  subject: string
  bodyHtml: string
}

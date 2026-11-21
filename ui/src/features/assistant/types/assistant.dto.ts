import type { ErrorInfo } from "@/shared/types/api.types"
import type { FieldType, DatasetDefinition } from "@/features/datasets/types/dataset.types"

/** What the assistant drafts; each has its own endpoint (/api/<kind>-suggestions). */
export type AssistantKind = "dataset" | "mail-template" | "flow" | "cron" | "step" | "survey" | "help"

export interface ChatMessage {
  role: "user" | "assistant"
  content: string
}

// ---- GET /api/dataset-suggestions/settings ----

export interface AssistantSettingsResponse {
  enabled: boolean
  model: string | null
  provider: string | null
}

// ---- POST /api/<kind>-suggestions ----

export interface CreateSuggestionRequest {
  messages: ChatMessage[]
  /** The dataset on screen (may be an unfinished draft); null for a new dataset. */
  currentDefinition?: DatasetDefinition | null
  /** The template or flow on screen, for the other kinds. */
  current?: unknown
}

// ---- GET /api/<kind>-suggestions/{id} ----

export type SuggestionStatus = "queued" | "processing" | "completed" | "failed"

/** draft of a mail template suggestion. */
export interface TemplateDraftDto {
  name: string
  description: string | null
  subject: string
  bodyHtml: string
}

/** A mail step may refer to a template that is still to be created (`templateRef` → `newTemplates[].ref`). */
export interface FlowDraftDto {
  flow: { name: string; steps: Record<string, unknown>[]; edges: { from: string; to: string; when?: string | null }[] }
  schedules: { cron: string; timeZone?: string }[]
  /** Run for every response to a survey; `filter` is JSON Logic over `trigger`. */
  surveyTriggers?: { surveyId: number; filter?: unknown }[]
  newTemplates: (TemplateDraftDto & { ref: string })[]
}

/** draft of a help reply: links to existing pages and, for building work, the specialist assistant to ask. */
export interface HelpDraftDto {
  links: { label: string; path: string }[]
  handoff: { assistant: "dataset" | "flow" | "survey" | "mail-template"; path: string; request: string } | null
}

export interface SuggestionReplyDto {
  message: string | null
  title: string | null
  explanation: string | null
  definition: DatasetDefinition | null
  columns: { label: string; type: FieldType }[]
  /** Mail template: TemplateDraftDto; flow: FlowDraftDto. */
  draft: unknown
}

export interface SuggestionResponse {
  id: number
  status: SuggestionStatus
  createdAt: string
  finishedAt: string | null
  model: string | null
  reply: SuggestionReplyDto | null
  error: ErrorInfo | null
}

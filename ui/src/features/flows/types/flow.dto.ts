import type { ErrorInfo, UserRef } from "@/shared/types/api.types"
import type { DatasetDefinition, ExportFormat } from "@/features/datasets/types/dataset.types"

export type RunStatus = "queued" | "running" | "completed" | "failed" | "cancelled"
export type StepStatus = "ready" | "running" | "waiting" | "completed" | "failed" | "cancelled"

/** A JSON Logic rule (https://jsonlogic.com). */
export type JsonLogicRule = unknown

/** Exactly one of `datasetName` (a saved dataset, used by name) and `definition` (designed for this step). */
export interface DatasetStepDto {
  type: "dataset"
  key: string
  datasetName?: string | null
  definition?: DatasetDefinition | null
  /** xlsx (Excel) or csv; absent = csv (older flows). */
  format?: ExportFormat | null
  fileName?: string | null
  maxRows?: number | null
  /** First rows later steps get as `rows` (the table in a mail body), 0-500; absent = 20. */
  mailRows?: number | null
}

export interface MailStepDto {
  type: "mail"
  key: string
  templateId: number
  to: string[]
  cc?: string[]
  attachments?: string[]
}

export interface ConditionStepDto {
  type: "condition"
  key: string
  expression: JsonLogicRule
}

export interface FilterStepDto {
  type: "filter"
  key: string
  source: string
  expression: JsonLogicRule
}

export interface ApprovalStepDto {
  type: "approval"
  key: string
  groupId: number
  title: string
  description?: string | null
  timeoutHours?: number | null
  allowSelfApproval?: boolean | null
  notifyMembers?: boolean | null
}

export interface WebhookStepDto {
  type: "webhook"
  key: string
  url: string
  method?: "POST" | "PUT" | "PATCH" | "GET" | "DELETE" | null
  headers?: Record<string, string>
  body?: string | null
  contentType?: string | null
  maxAttempts?: number | null
}

export interface KafkaPublishStepDto {
  type: "kafka"
  key: string
  topic: string
  messageKey?: string | null
  payload: string
}

export type FlowStepDto =
  | DatasetStepDto | MailStepDto | ConditionStepDto | FilterStepDto | ApprovalStepDto | WebhookStepDto
  | KafkaPublishStepDto

export type StepType = FlowStepDto["type"]

/** `when`: the outcome of `from` the edge is taken on; null = any outcome without an edge of its own. */
export interface FlowEdgeDto {
  from: string
  to: string
  when?: string | null
}

export interface PositionDto {
  x: number
  y: number
}

// ---- flows ----

export interface FlowRequest {
  name: string
  description?: string | null
  steps: FlowStepDto[]
  edges?: FlowEdgeDto[]
  layout?: Record<string, PositionDto>
}

export interface FlowSummaryResponse {
  id: number
  name: string
  description: string | null
  owner: UserRef
  stepCount: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface FlowDetailResponse {
  id: number
  name: string
  description: string | null
  owner: UserRef
  steps: FlowStepDto[]
  edges: FlowEdgeDto[]
  layout: Record<string, PositionDto>
  version: number
  createdAt: string
  updatedAt: string
  updatedBy: UserRef
}

// ---- schedules ----

export interface ScheduleRequest {
  cron: string
  timeZone: string
  enabled: boolean
}

export interface ScheduleResponse {
  id: number
  flowId: number
  cron: string
  timeZone: string
  enabled: boolean
  nextFireAt: string | null
  lastFiredAt: string | null
  lastRunId: number | null
  lastSkipReason: string | null
  version: number
}

// ---- runs ----

export interface RunSummaryResponse {
  id: number
  flow: { id: number; name: string }
  status: RunStatus
  cancelRequested: boolean
  trigger: "manual" | "schedule" | "webhook" | "kafka"
  triggeredBy: string
  runAs: UserRef
  scheduleId: number | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  error: ErrorInfo | null
}

// ---- GET /api/flow-files/{id}/content ----

export interface FileContentResponse {
  fileName: string
  contentType: string
  content: string
}

export interface RunStepDto {
  key: string
  type: string
  status: StepStatus
  outcome: string | null
  attempt: number
  maxAttempts: number
  timeoutSeconds: number
  startedAt: string | null
  finishedAt: string | null
  nextAttemptAt: string | null
  waitingUntil: string | null
  error: ErrorInfo | null
  output: Record<string, unknown> | null
}

export interface RunFileDto {
  id: number
  stepKey: string
  fileName: string
  sizeBytes: number
  expiresAt: string
  expired: boolean
  downloadUrl: string | null
}

export interface RunEventDto {
  id: number
  type: string
  stepKey: string | null
  message: string | null
  occurredAt: string
}

export interface RunDetailResponse extends RunSummaryResponse {
  steps: RunStepDto[]
  files: RunFileDto[]
  events: RunEventDto[]
  /** The webhook body or Kafka message that started the run. */
  triggerPayload: unknown
  /** The flow as this run executes it (its own copy). */
  definition: { steps: FlowStepDto[]; edges: FlowEdgeDto[]; layout: Record<string, PositionDto> }
}

// ---- event triggers ----

export type TriggerType = "webhook" | "kafka"

export interface TriggerRequest {
  type: TriggerType
  enabled: boolean
  topic?: string | null
  filter?: JsonLogicRule | null
}

export interface TriggerResponse {
  id: number
  flowId: number
  type: TriggerType
  enabled: boolean
  topic: string | null
  filter: JsonLogicRule | null
  /** Path to call; the secret is only returned when created or rotated. */
  webhookUrl: string | null
  lastFiredAt: string | null
  lastRunId: number | null
  version: number
  createdBy: UserRef
  createdAt: string
  updatedAt: string
}

export interface TriggerWithSecretResponse {
  trigger: TriggerResponse
  secret: string | null
}

export interface DownloadLinkResponse {
  url: string | null
  fileName: string
}

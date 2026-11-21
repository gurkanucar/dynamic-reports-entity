import type { FieldProblem } from "@/shared/api/http-client"

/** Question types; `section` is a heading without an answer. */
export type FieldType =
  | "shortText" | "longText" | "email" | "phone" | "number"
  | "singleChoice" | "multipleChoice" | "dropdown"
  | "date" | "rating" | "nps" | "yesNo" | "section"

export type SurveyStatus = "draft" | "published" | "closed"
export type FollowStatus = "new" | "in_progress" | "resolved" | "unresolved"
export type Priority = "low" | "normal" | "high"
export type ResultsVisibility = "owner" | "respondents" | "everyone"

/** JSON Logic, evaluated on the server (and in the browser for visibility). */
export type Logic = unknown

export interface SurveyOption {
  id: string
  label: string
}

export interface SurveyField {
  id: string
  type: FieldType
  label: string
  help?: string
  placeholder?: string
  required?: boolean
  options?: SurveyOption[]
  /** number: smallest value; multipleChoice: fewest choices. */
  min?: number
  /** number: largest value; rating: stars (3-10); multipleChoice: most choices. */
  max?: number
  minLength?: number
  maxLength?: number
  /** Asked only when true over the earlier answers. */
  visibleWhen?: Logic
  width?: "full" | "half"
  /** Counted in the results others may see (choice, yes/no, rating, score questions). */
  showInResults?: boolean
}

/** A check over all answers; the response is refused with `message` when `logic` is false. */
export interface SurveyRule {
  id: string
  message: string
  /** Absent in what respondents get. */
  logic?: Logic
}

export interface SurveyDefinition {
  submitLabel?: string
  successMessage?: string
  fields: SurveyField[]
  rules: SurveyRule[]
}

export type AnswerValue = string | number | boolean | string[]
export type Answers = Record<string, AnswerValue | undefined>

/** An extra field the owner fills in per response, e.g. "Sipariş no". */
export interface TrackingField {
  id: string
  label: string
  type: "text" | "choice" | "date" | "number"
  options?: SurveyOption[]
}

export interface SurveySettings {
  tracking: { status: boolean; priority: boolean; followUpDate: boolean; assignee: boolean; fields: TrackingField[] }
  results: ResultsVisibility
}

// ---- /api/surveys ----

export interface SurveyRequest {
  title: string
  description?: string | null
  status: SurveyStatus
  definition: SurveyDefinition
  anonymous: boolean
  oneResponsePerUser: boolean
  closesAt?: string | null
  settings?: SurveySettings
}

export interface SurveyResponse extends SurveyRequest {
  id: number
  version: number
  owner: string
  createdAt: string
  updatedAt: string
  updatedBy: string
  publishedAt: string | null
  responseCount: number
  settings: SurveySettings
  followUpCounts: Record<FollowStatus, number>
}

export interface SurveyListItem {
  id: number
  title: string
  description: string | null
  status: SurveyStatus
  owner: string
  closesAt: string | null
  updatedAt: string
  questionCount: number
  responseCount: number
  answered: boolean
  anonymous: boolean
}

export type ClosedReason = "draft" | "closed" | "deadline_passed" | "answered" | "invitation_invalid"
  | "invitation_used" | "invitation_expired"

export interface SurveyFormResponse {
  id: number
  title: string
  description: string | null
  status: SurveyStatus
  anonymous: boolean
  oneResponsePerUser: boolean
  closesAt: string | null
  definition: SurveyDefinition
  open: boolean
  closedReason: ClosedReason | null
  alreadyAnswered: boolean
  /** A draft opened by its owner to try it. */
  preview: boolean
  resultsVisibility: ResultsVisibility
  /** Some questions are counted in the results. */
  hasResults: boolean
}

export interface SubmittedResponse {
  responseId: number
  submittedAt: string
  successMessage: string | null
}

export interface ResponseRow {
  id: number
  surveyVersion: number
  respondent: string | null
  submittedAt: string
  answers: Record<string, AnswerValue>
  display: Record<string, string>
  status: FollowStatus
  priority: Priority | null
  followUpAt: string | null
  assignee: string | null
  tracking: Record<string, string | number>
  /** How many notes it has. */
  notes: number
  /** What waited for it, e.g. `flow-step:42`. */
  source: string | null
  updatedAt: string | null
  updatedBy: string | null
}

export interface ResponseNote {
  id: number
  author: string
  text: string
  createdAt: string
}

/** PATCH of a response's follow-up: present fields change, null clears. */
export interface FollowUpChange {
  status?: FollowStatus
  priority?: Priority | null
  followUpAt?: string | null
  assignee?: string | null
  tracking?: Record<string, string | number>
}

export interface ResponseFilters {
  status: FollowStatus[]
  priority: Priority[]
  q: string
  sort: string
}

export interface SurveySummary {
  total: number
  fields: {
    fieldId: string
    label: string
    type: FieldType
    answered: number
    counts: { key: string; label: string; count: number }[]
    average: number | null
    samples: string[]
  }[]
}

export interface SurveyCheckResponse {
  valid: boolean
  problems: FieldProblem[]
}

/** draft of a survey suggestion. */
export interface SurveyDraftDto {
  title: string
  description: string | null
  definition: SurveyDefinition
  assumptions: string[]
  suggestions: string[]
}

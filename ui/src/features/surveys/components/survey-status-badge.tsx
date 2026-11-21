import { StatusBadge, type Tone } from "@/shared/components/status-badge"
import type { FollowStatus, Priority, SurveyStatus } from "../types/survey.dto"

export const STATUS_LABELS: Record<SurveyStatus, string> = { draft: "Taslak", published: "Yayında", closed: "Kapalı" }
const STATUS_TONES: Record<SurveyStatus, Tone> = { draft: "muted", published: "success", closed: "neutral" }

export function SurveyStatusBadge({ status }: { status: SurveyStatus }) {
  return <StatusBadge tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</StatusBadge>
}

export const FOLLOW_LABELS: Record<FollowStatus, string> = {
  new: "Yeni", in_progress: "İşlemde", resolved: "Çözüldü", unresolved: "Çözülemedi",
}
const FOLLOW_TONES: Record<FollowStatus, Tone> = {
  new: "info", in_progress: "warning", resolved: "success", unresolved: "danger",
}

/** Where the owner is with a response. */
export function FollowStatusBadge({ status }: { status: FollowStatus }) {
  return <StatusBadge tone={FOLLOW_TONES[status]}>{FOLLOW_LABELS[status]}</StatusBadge>
}

export const PRIORITY_LABELS: Record<Priority, string> = { low: "Düşük", normal: "Normal", high: "Yüksek" }
const PRIORITY_TONES: Record<Priority, Tone> = { low: "muted", normal: "neutral", high: "danger" }

export function PriorityBadge({ priority }: { priority: Priority }) {
  return <StatusBadge tone={PRIORITY_TONES[priority]}>{PRIORITY_LABELS[priority]}</StatusBadge>
}

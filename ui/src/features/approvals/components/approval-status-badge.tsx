import { StatusBadge, type Tone } from "@/shared/components/status-badge"
import type { ApprovalStatus } from "../types/approval.dto"

export const APPROVAL_LABELS: Record<ApprovalStatus, string> = {
  pending: "Bekliyor",
  approved: "Onaylandı",
  rejected: "Reddedildi",
  timed_out: "Zaman aşımı",
  cancelled: "İptal",
}

const TONES: Record<ApprovalStatus, Tone> = {
  pending: "warning",
  approved: "success",
  rejected: "danger",
  timed_out: "muted",
  cancelled: "neutral",
}

export function ApprovalStatusBadge({ status }: { status: ApprovalStatus }) {
  return <StatusBadge tone={TONES[status]}>{APPROVAL_LABELS[status]}</StatusBadge>
}

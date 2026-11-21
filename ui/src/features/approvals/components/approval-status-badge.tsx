import { Badge } from "@/components/ui/badge"
import type { ApprovalStatus } from "../types/approval.dto"

const LABELS: Record<ApprovalStatus, string> = {
  pending: "Bekliyor",
  approved: "Onaylandı",
  rejected: "Reddedildi",
  timed_out: "Zaman aşımı",
  cancelled: "İptal",
}

const VARIANTS: Record<ApprovalStatus, "secondary" | "destructive" | "outline"> = {
  pending: "outline",
  approved: "secondary",
  rejected: "destructive",
  timed_out: "outline",
  cancelled: "outline",
}

export function ApprovalStatusBadge({ status }: { status: ApprovalStatus }) {
  return <Badge variant={VARIANTS[status]}>{LABELS[status]}</Badge>
}

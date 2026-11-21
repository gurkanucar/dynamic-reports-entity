import { StatusBadge, type Tone } from "@/shared/components/status-badge"
import type { ProjectStatus, RecordStatus } from "../api/admin.types"

export const PROJECT_STATUSES: Record<ProjectStatus, { label: string; tone: Tone }> = {
  PLANNED: { label: "Planlandı", tone: "info" },
  ACTIVE: { label: "Aktif", tone: "success" },
  ON_HOLD: { label: "Beklemede", tone: "warning" },
  COMPLETED: { label: "Tamamlandı", tone: "neutral" },
}

export const RECORD_STATUSES: Record<RecordStatus, { label: string; tone: Tone }> = {
  PASSED: { label: "Geçti", tone: "success" },
  FAILED: { label: "Kaldı", tone: "danger" },
  IN_PROGRESS: { label: "Devam ediyor", tone: "info" },
}

export function ProjectStatusBadge({ status }: { status: ProjectStatus }) {
  const item = PROJECT_STATUSES[status] ?? { label: status, tone: "muted" as Tone }
  return <StatusBadge tone={item.tone}>{item.label}</StatusBadge>
}

export function RecordStatusBadge({ status }: { status: RecordStatus }) {
  const item = RECORD_STATUSES[status] ?? { label: status, tone: "muted" as Tone }
  return <StatusBadge tone={item.tone}>{item.label}</StatusBadge>
}

/** A renewal date: overdue (red), within 60 days (amber) or later. */
export function RenewalBadge({ date }: { date: string | null }) {
  if (!date) return <span className="text-muted-foreground">Süresiz</span>
  const days = Math.round((new Date(`${date}T00:00:00`).getTime() - new Date(new Date().toDateString()).getTime()) / 86_400_000)
  const text = new Date(`${date}T00:00:00`).toLocaleDateString("tr-TR")
  if (days < 0) return <StatusBadge tone="danger">{text} · {-days} gün geçti</StatusBadge>
  if (days <= 60) return <StatusBadge tone="warning">{text} · {days} gün</StatusBadge>
  return <span className="tabular-nums">{text}</span>
}

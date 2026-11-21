import { ClipboardListIcon, RadioTowerIcon, UserIcon, WebhookIcon, type LucideIcon } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { StatusBadge } from "@/shared/components/status-badge"

const SOURCES: Record<string, { label: string; icon: LucideIcon }> = {
  webhook: { label: "Webhook", icon: WebhookIcon },
  kafka: { label: "Kafka", icon: RadioTowerIcon },
  event: { label: "Anket yanıtı", icon: ClipboardListIcon },
  schedule: { label: "Zamanlama", icon: ClipboardListIcon },
}

/** Who started the run that asks: a person, or another system ("webhook:3" → Webhook). */
export function RequesterBadge({ requestedBy }: { requestedBy: string }) {
  const source = SOURCES[requestedBy.split(":")[0]]
  if (source && requestedBy.includes(":")) {
    return <Badge variant="secondary"><source.icon data-icon="inline-start" />{source.label}</Badge>
  }
  return <Badge variant="outline"><UserIcon data-icon="inline-start" />{requestedBy}</Badge>
}

/** How much time is left: overdue, today, soon, or the days left. */
export function DueBadge({ dueAt }: { dueAt: string }) {
  const [now] = useState(() => Date.now())   // when it was shown; the list refreshes on its own
  const hours = (new Date(dueAt).getTime() - now) / 3_600_000
  if (hours < 0) return <StatusBadge tone="danger">Süresi geçti</StatusBadge>
  if (hours < 24) return <StatusBadge tone="warning">{Math.max(1, Math.round(hours))} saat kaldı</StatusBadge>
  return <StatusBadge tone="muted">{Math.round(hours / 24)} gün kaldı</StatusBadge>
}

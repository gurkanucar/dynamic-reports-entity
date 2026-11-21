import { CheckIcon, CircleDashedIcon, HourglassIcon, LoaderIcon, XIcon, type LucideIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { Tone } from "@/shared/components/status-badge"
import type { RunStatus, StepStatus } from "../types/flow.dto"

type Status = RunStatus | StepStatus

const LABELS: Record<Status, string> = {
  queued: "Sırada",
  ready: "Bekliyor",
  running: "Çalışıyor",
  waiting: "Bekliyor",
  completed: "Tamamlandı",
  failed: "Hata",
  cancelled: "İptal",
}

const TONES: Record<Status, Tone> = {
  queued: "muted",
  ready: "muted",
  running: "info",
  waiting: "warning",
  completed: "success",
  failed: "danger",
  cancelled: "neutral",
}

const CLASSES: Record<Tone, string> = {
  success: "bg-success/10 text-success",
  warning: "bg-warning/15 text-warning",
  info: "bg-info/10 text-info",
  danger: "bg-destructive/10 text-destructive",
  neutral: "bg-secondary text-secondary-foreground",
  muted: "border-border text-muted-foreground",
}

const ICONS: Record<Status, LucideIcon> = {
  queued: CircleDashedIcon,
  ready: CircleDashedIcon,
  running: LoaderIcon,
  waiting: HourglassIcon,
  completed: CheckIcon,
  failed: XIcon,
  cancelled: XIcon,
}

export function RunStatusBadge({ status }: { status: Status }) {
  const Icon = ICONS[status]
  const tone = TONES[status]
  return (
    <Badge variant="outline" className={cn(tone !== "muted" && "border-transparent", CLASSES[tone])}>
      <Icon data-icon="inline-start" className={status === "running" ? "animate-spin" : undefined} />
      {LABELS[status]}
    </Badge>
  )
}

import { CheckIcon, CircleDashedIcon, HourglassIcon, LoaderIcon, XIcon, type LucideIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import type { RunStatus, StepStatus } from "../types/flow.dto"

type Status = RunStatus | StepStatus

const LABELS: Record<Status, string> = {
  queued: "Sırada",
  ready: "Bekliyor",
  running: "Çalışıyor",
  waiting: "Onay bekliyor",
  completed: "Tamamlandı",
  failed: "Hata",
  cancelled: "İptal",
}

/** Red (the brand colour) only for failures; success stays neutral, the icon tells it apart. */
const VARIANTS: Record<Status, "secondary" | "destructive" | "outline"> = {
  queued: "outline",
  ready: "outline",
  running: "outline",
  waiting: "outline",
  completed: "secondary",
  failed: "destructive",
  cancelled: "outline",
}

const ICONS: Partial<Record<Status, LucideIcon>> = {
  queued: CircleDashedIcon,
  ready: CircleDashedIcon,
  running: LoaderIcon,
  waiting: HourglassIcon,
  completed: CheckIcon,
  failed: XIcon,
}

export function RunStatusBadge({ status }: { status: Status }) {
  const Icon = ICONS[status]
  return (
    <Badge variant={VARIANTS[status]}>
      {Icon && <Icon data-icon="inline-start" className={status === "running" ? "animate-spin" : undefined} />}
      {LABELS[status]}
    </Badge>
  )
}

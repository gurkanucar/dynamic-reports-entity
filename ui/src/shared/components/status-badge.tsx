import type { ReactNode } from "react"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

export type Tone = "success" | "warning" | "info" | "danger" | "neutral" | "muted"

const TONES: Record<Tone, string> = {
  success: "bg-success/10 text-success",
  warning: "bg-warning/15 text-warning",
  info: "bg-info/10 text-info",
  danger: "bg-destructive/10 text-destructive",
  neutral: "bg-secondary text-secondary-foreground",
  muted: "border-border text-muted-foreground",
}

const DOTS: Record<Tone, string> = {
  success: "bg-success", warning: "bg-warning", info: "bg-info", danger: "bg-destructive",
  neutral: "bg-muted-foreground", muted: "bg-muted-foreground/60",
}

/** A soft, coloured status pill with a dot: the same look for runs, approvals, surveys and responses. */
export function StatusBadge({ tone, children, className }: { tone: Tone; children: ReactNode; className?: string }) {
  return (
    <Badge variant="outline" className={cn("gap-1.5 border-transparent", TONES[tone], className)}>
      <span aria-hidden className={cn("size-1.5 rounded-full", DOTS[tone])} />
      {children}
    </Badge>
  )
}

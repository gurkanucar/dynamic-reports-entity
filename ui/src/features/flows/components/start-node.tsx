import { Handle, Position, type Node, type NodeProps } from "@xyflow/react"
import { CalendarClockIcon, ClipboardListIcon, HandIcon, PlayIcon, RadioTowerIcon, WebhookIcon, type LucideIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import type { TriggerKind } from "../lib/triggers"

export interface StartLine {
  kind: TriggerKind | "manual"
  text: string
  enabled: boolean
}

export interface StartNodeData extends Record<string, unknown> {
  lines: StartLine[]
  errors?: string[]
}

export type StartNodeType = Node<StartNodeData, "start">

const ICONS: Record<StartLine["kind"], LucideIcon> = {
  manual: HandIcon, schedule: CalendarClockIcon, webhook: WebhookIcon, kafka: RadioTowerIcon, survey: ClipboardListIcon,
}

/** What starts the flow (n8n's trigger node): always by hand, plus schedules, webhooks, Kafka and survey responses. Not deletable. */
export function StartNode({ data, selected }: NodeProps<StartNodeType>) {
  const errors = data.errors ?? []
  return (
    <div className={cn("w-60 rounded-xl border-2 border-dashed bg-card text-card-foreground shadow-xs",
      selected && "border-primary ring-3 ring-primary/20", errors.length > 0 && "border-destructive")}
      title={errors.join("\n") || undefined}>
      <div className="flex items-center gap-3 border-b p-3">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <PlayIcon className="size-4" />
        </div>
        <div className="flex min-w-0 flex-col">
          <span className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">Başlangıç</span>
          <span className="truncate text-sm font-medium">Akışı başlatan</span>
        </div>
      </div>
      <ul className="flex flex-col gap-1.5 p-3">
        {data.lines.map((line, index) => {
          const Icon = ICONS[line.kind]
          return (
            <li key={index} className={cn("flex items-center gap-2 text-xs", !line.enabled && "text-muted-foreground line-through")}>
              <Icon className="size-3.5 shrink-0 text-muted-foreground" />
              <span className="truncate">{line.text}</span>
            </li>
          )
        })}
      </ul>
      <Handle type="source" position={Position.Right} isConnectable={false}
        className="!size-2.5 !border-2 !border-card !bg-primary" />
    </div>
  )
}

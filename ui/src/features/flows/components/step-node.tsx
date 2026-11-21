import { Handle, Position, type Node, type NodeProps } from "@xyflow/react"
import { CircleAlertIcon, TriangleAlertIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import { stepType, summary } from "../lib/step-types"
import type { FlowStepDto, StepStatus } from "../types/flow.dto"
import { Hint } from "@/shared/components/hint"
import { RunStatusBadge } from "./run-status-badge"

export interface StepNodeData extends Record<string, unknown> {
  step: FlowStepDto
  /** In a run: what happened to this step (undefined = it did not run). */
  status?: StepStatus
  outcome?: string | null
  /** In a run: the step was not reached. */
  skipped?: boolean
  invalid?: boolean
  /** Problems the checklist found (errors) and things that may surprise the owner (warnings). */
  errors?: string[]
  warnings?: string[]
  editable?: boolean
}

export type StepNodeType = Node<StepNodeData, "step">

/**
 * A step on the canvas, left to right: one entry on the left, one exit per outcome on the right (next to the
 * outcome's name, so it is clear which branch a connection takes).
 */
export function StepNode({ data, selected }: NodeProps<StepNodeType>) {
  const info = stepType(data.step.type)
  const Icon = info.icon
  const branches = info.outcomes.length > 1
  return (
    <div className={cn("w-60 rounded-xl border bg-card text-card-foreground shadow-xs transition-shadow",
      selected && "border-primary ring-3 ring-primary/20",
      (data.invalid || data.status === "failed" || (data.errors?.length ?? 0) > 0) && "border-destructive",
      data.skipped && "opacity-45")}>
      <Handle type="target" position={Position.Left} className="!size-2.5 !border-2 !border-card !bg-muted-foreground" />
      <div className="flex items-start gap-3 p-3">
        <div className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground",
          data.status !== undefined && "text-foreground")}>
          <Icon className="size-4" />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">{info.label}</span>
            {(data.errors?.length ?? 0) > 0 ? (
              <Hint text={data.errors!.join("\n")}>
                <span className="ml-auto text-destructive"><CircleAlertIcon className="size-3.5" /></span>
              </Hint>
            ) : (data.warnings?.length ?? 0) > 0 && (
              <Hint text={data.warnings!.join("\n")}>
                <span className="ml-auto text-muted-foreground"><TriangleAlertIcon className="size-3.5" /></span>
              </Hint>
            )}
            {data.status && <span className="ml-auto"><RunStatusBadge status={data.status} /></span>}
          </div>
          <span className="truncate text-sm font-medium">{data.step.key}</span>
          <span className="truncate text-xs text-muted-foreground">{summary(data.step)}</span>
        </div>
      </div>
      {branches ? (
        <div className="flex flex-col border-t py-1">
          {info.outcomes.map((outcome) => (
            <div key={outcome.id} className={cn("relative py-1 pr-4 text-right text-[11px] text-muted-foreground",
              data.outcome === outcome.id && "font-medium text-foreground")}>
              {outcome.label}
              <Handle id={outcome.id} type="source" position={Position.Right}
                className="!size-2.5 !border-2 !border-card !bg-primary" />
            </div>
          ))}
          {/* the engine's default way: outcomes without an edge of their own take it */}
          <div className="relative py-1 pr-4 text-right text-[11px] text-muted-foreground/70 italic"
            title="Kendi bağlantısı olmayan sonuçlar buradan devam eder">
            Diğer
            <Handle id="out" type="source" position={Position.Right}
              className="!size-2.5 !border-2 !border-card !bg-muted-foreground" />
          </div>
        </div>
      ) : (
        <Handle id="out" type="source" position={Position.Right} className="!size-2.5 !border-2 !border-card !bg-primary" />
      )}
    </div>
  )
}

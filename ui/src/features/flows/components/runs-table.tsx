import { CalendarClockIcon, ClipboardListIcon, HandIcon, HistoryIcon, RadioTowerIcon, WebhookIcon, type LucideIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { SortableHead } from "@/shared/components/sortable-head"
import { formatDateTime } from "@/shared/lib/format"
import type { RunSummaryResponse } from "../types/flow.dto"
import { RunStatusBadge } from "./run-status-badge"

export const TRIGGERS: Record<RunSummaryResponse["trigger"], { label: string; icon: LucideIcon }> = {
  manual: { label: "Elle", icon: HandIcon },
  schedule: { label: "Zamanlama", icon: CalendarClockIcon },
  webhook: { label: "Webhook", icon: WebhookIcon },
  kafka: { label: "Kafka", icon: RadioTowerIcon },
  event: { label: "Anket", icon: ClipboardListIcon },
}

/** How a run started: a person's name for manual runs, else the kind of trigger. */
export function TriggerBadge({ run }: { run: RunSummaryResponse }) {
  const trigger = TRIGGERS[run.trigger]
  return (
    <Badge variant={run.trigger === "manual" ? "outline" : "secondary"}>
      <trigger.icon data-icon="inline-start" />{run.trigger === "manual" ? run.triggeredBy : trigger.label}
    </Badge>
  )
}

/** Runs; with `sort`/`onSort` the columns sort the server-side list. */
export function RunsTable({ runs, showFlow, onSelect, sort, onSort, filtered }: {
  runs: RunSummaryResponse[]
  showFlow?: boolean
  onSelect: (runId: string) => void
  sort?: string
  onSort?: (sort: string) => void
  filtered?: boolean
}) {
  if (runs.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon"><HistoryIcon /></EmptyMedia>
          <EmptyTitle>{filtered ? "Bu filtreye uyan çalışma yok" : "Henüz çalışma yok"}</EmptyTitle>
          <EmptyDescription>
            {filtered ? "Filtreleri değiştirin ya da temizleyin." : "Akış elle ya da zamanlamayla başlatıldığında burada görünür."}
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  const head = (field: string, label: string, className?: string) => sort !== undefined && onSort
    ? <SortableHead field={field} sort={sort} onSort={onSort} descFirst className={className}>{label}</SortableHead>
    : <TableHead className={className}>{label}</TableHead>
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>#</TableHead>
            {showFlow && head("flow", "Akış")}
            {head("status", "Durum")}
            <TableHead>Başlatan</TableHead>
            {head("createdAt", "Oluşturuldu")}
            {head("finishedAt", "Bitti")}
            <TableHead>Hata</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => (
            <TableRow key={run.id} className="cursor-pointer" onClick={() => onSelect(run.id)}>
              <TableCell className="tabular-nums text-muted-foreground">{run.id}</TableCell>
              {showFlow && <TableCell className="max-w-56 truncate font-medium">{run.flow.name}</TableCell>}
              <TableCell><RunStatusBadge status={run.status} /></TableCell>
              <TableCell><TriggerBadge run={run} /></TableCell>
              <TableCell className="tabular-nums">{formatDateTime(run.createdAt)}</TableCell>
              <TableCell className="tabular-nums">{formatDateTime(run.finishedAt)}</TableCell>
              <TableCell className="max-w-64 truncate text-muted-foreground">
                {run.error ? <span title={run.error.message}><code className="text-xs">{run.error.code}</code> {run.error.message}</span> : ""}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

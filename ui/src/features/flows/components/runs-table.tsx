import { HistoryIcon } from "lucide-react"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { formatDateTime } from "@/shared/lib/format"
import type { RunSummaryResponse } from "../types/flow.dto"
import { RunStatusBadge } from "./run-status-badge"

export function RunsTable({ runs, showFlow, onSelect }: {
  runs: RunSummaryResponse[]
  showFlow?: boolean
  onSelect: (runId: number) => void
}) {
  if (runs.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon"><HistoryIcon /></EmptyMedia>
          <EmptyTitle>Henüz çalışma yok</EmptyTitle>
          <EmptyDescription>Akış elle ya da zamanlamayla başlatıldığında burada görünür.</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>#</TableHead>
            {showFlow && <TableHead>Akış</TableHead>}
            <TableHead>Durum</TableHead>
            <TableHead>Başlatan</TableHead>
            <TableHead>Oluşturuldu</TableHead>
            <TableHead>Bitti</TableHead>
            <TableHead>Hata</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => (
            <TableRow key={run.id} className="cursor-pointer" onClick={() => onSelect(run.id)}>
              <TableCell className="tabular-nums">{run.id}</TableCell>
              {showFlow && <TableCell className="max-w-48 truncate">{run.flow.name}</TableCell>}
              <TableCell><RunStatusBadge status={run.status} /></TableCell>
              <TableCell>{run.trigger === "schedule" ? "Zamanlama" : run.triggeredBy}</TableCell>
              <TableCell>{formatDateTime(run.createdAt)}</TableCell>
              <TableCell>{formatDateTime(run.finishedAt)}</TableCell>
              <TableCell className="max-w-64 truncate text-muted-foreground">{run.error?.message ?? ""}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

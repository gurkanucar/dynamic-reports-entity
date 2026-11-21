import { useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { RunSheet } from "../components/run-sheet"
import { RunsTable } from "../components/runs-table"
import { useRuns } from "../queries/use-runs"
import type { RunStatus } from "../types/flow.dto"

const FILTERS: { value: RunStatus; label: string }[] = [
  { value: "running", label: "Çalışan" },
  { value: "queued", label: "Sırada" },
  { value: "failed", label: "Hatalı" },
  { value: "completed", label: "Tamamlanan" },
  { value: "cancelled", label: "İptal" },
]

export function RunsPage() {
  const [statuses, setStatuses] = useState<RunStatus[]>([])
  const [page, setPage] = useState(1)
  const runs = useRuns(statuses, page)
  const [selected, setSelected] = useState<number | null>(null)
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Çalışmalar" description="Görebildiğiniz tüm akışların çalışmaları, en yenisi üstte."
        actions={(
          <ToggleGroup type="multiple" variant="outline" className="max-w-full flex-wrap" value={statuses}
            onValueChange={(value) => { setStatuses(value as RunStatus[]); setPage(1) }}>
            {FILTERS.map((filter) => <ToggleGroupItem key={filter.value} value={filter.value}>{filter.label}</ToggleGroupItem>)}
          </ToggleGroup>
        )} />
      {runs.isPending && <Skeleton className="h-40 w-full" />}
      {runs.isError && <ErrorAlert error={runs.error} />}
      {runs.data && <RunsTable runs={runs.data.data} showFlow onSelect={setSelected} />}
      <Pager meta={runs.data?.meta} onPage={setPage} />
      <RunSheet runId={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

import { useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import { RunSheet } from "../components/run-sheet"
import { RunsTable, TRIGGERS } from "../components/runs-table"
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
  const [triggers, setTriggers] = useState<string[]>([])
  const list = useListState("-createdAt")
  const runs = useRuns(statuses, list.page, { q: list.query || undefined, trigger: triggers, sort: list.sort, size: list.size })
  const [selected, setSelected] = useState<string | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Çalışmalar" description="Görebildiğiniz tüm akışların çalışmaları; 5 saniyede bir yenilenir." />
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup type="multiple" variant="outline" size="sm" className="max-w-full flex-wrap" value={statuses}
          onValueChange={(value) => { setStatuses(value as RunStatus[]); list.resetPage() }}>
          {FILTERS.map((filter) => <ToggleGroupItem key={filter.value} value={filter.value}>{filter.label}</ToggleGroupItem>)}
        </ToggleGroup>
        <ToggleGroup type="multiple" variant="outline" size="sm" className="max-w-full flex-wrap" value={triggers}
          onValueChange={(value) => { setTriggers(value); list.resetPage() }} aria-label="Başlatan">
          {Object.entries(TRIGGERS).map(([value, trigger]) => (
            <ToggleGroupItem key={value} value={value}><trigger.icon />{trigger.label}</ToggleGroupItem>
          ))}
        </ToggleGroup>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Akış adı ara…" />
      </div>
      {runs.isPending && <Skeleton className="h-40 w-full" />}
      {runs.isError && <ErrorAlert error={runs.error} />}
      {runs.data && (
        <RunsTable runs={runs.data.data} showFlow onSelect={setSelected} sort={list.sort} onSort={list.setSort}
          filtered={statuses.length > 0 || triggers.length > 0 || list.query !== ""} />
      )}
      <Pager meta={runs.data?.meta} onPage={list.setPage} onSize={list.setSize} />
      <RunSheet runId={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

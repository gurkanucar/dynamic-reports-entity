import { PlusIcon, WorkflowIcon } from "lucide-react"
import { Link, useNavigate } from "react-router"
import { Button } from "@/components/ui/button"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { SearchInput } from "@/shared/components/search-input"
import { SortableHead } from "@/shared/components/sortable-head"
import { useListState } from "@/shared/lib/list-state"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { formatDateTime } from "@/shared/lib/format"
import { useFlows } from "../queries/use-flows"

export function FlowsPage() {
  const list = useListState("-updatedAt")
  const flows = useFlows(list.page, { q: list.query || undefined, sort: list.sort })
  const navigate = useNavigate()
  const newDataset = (
    <>
          <Button variant="outline" asChild><Link to="/datasets/new">Veri setinden akış oluştur</Link></Button>
          <Button asChild><Link to="/flows/new"><PlusIcon data-icon="inline-start" />Yeni akış</Link></Button>
        </>
  )
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Akışlar" description="Elle, zamanlamayla, webhook, Kafka ya da anket yanıtıyla başlayan iş akışları."
        actions={newDataset} />
      <SearchInput value={list.search} onChange={list.setSearch} placeholder="Akış adı veya açıklama ara…" />
      {flows.isPending && <Skeleton className="h-40 w-full" />}
      {flows.isError && <ErrorAlert error={flows.error} title="Akışlar yüklenemedi" />}
      {flows.data && flows.data.data.length === 0 && list.query && (
        <p className="py-8 text-center text-sm text-muted-foreground">“{list.query}” ile eşleşen akış yok.</p>
      )}
      {flows.data && flows.data.data.length === 0 && !list.query && (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon"><WorkflowIcon /></EmptyMedia>
            <EmptyTitle>Henüz akış yok</EmptyTitle>
            <EmptyDescription>Bir veri seti oluşturup “Zamanla ve gönder” ile ilk akışınızı kurun.</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>{newDataset}</EmptyContent>
        </Empty>
      )}
      {flows.data && flows.data.data.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead field="name" sort={list.sort} onSort={list.setSort}>Ad</SortableHead>
                  <SortableHead field="owner" sort={list.sort} onSort={list.setSort}>Sahibi</SortableHead>
                  <TableHead>Adım</TableHead>
                  <SortableHead field="updatedAt" sort={list.sort} onSort={list.setSort} descFirst>Güncellendi</SortableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {flows.data.data.map((flow) => (
                  <TableRow key={flow.id} className="cursor-pointer" onClick={() => navigate(`/flows/${flow.id}`)}>
                    <TableCell className="max-w-[36rem]">
                      <div className="truncate font-medium">{flow.name}</div>
                      {flow.description && (
                        <div className="truncate text-xs text-muted-foreground" title={flow.description}>{flow.description}</div>
                      )}
                    </TableCell>
                    <TableCell><Badge variant="outline">{flow.owner.username}</Badge></TableCell>
                    <TableCell className="tabular-nums">{flow.stepCount}</TableCell>
                    <TableCell>{formatDateTime(flow.updatedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager meta={flows.data.meta} onPage={list.setPage} />
        </>
      )}
    </div>
  )
}

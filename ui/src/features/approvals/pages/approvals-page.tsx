import { CheckCheckIcon } from "lucide-react"
import { useState } from "react"
import { useSearchParams } from "react-router"
import { Card, CardContent } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { SearchInput } from "@/shared/components/search-input"
import { SortableHead } from "@/shared/components/sortable-head"
import { formatDateTime } from "@/shared/lib/format"
import { useListState } from "@/shared/lib/list-state"
import { DueBadge, RequesterBadge } from "../components/approval-badges"
import { ApprovalStatusBadge } from "../components/approval-status-badge"
import { DecisionDialog } from "../components/decision-dialog"
import { useApprovalGroups } from "../queries/use-approval-groups"
import { useApprovalRequests } from "../queries/use-approval-requests"
import type { ApprovalStatus } from "../types/approval.dto"

const FILTERS: Record<string, ApprovalStatus[]> = {
  pending: ["pending"],
  done: ["approved", "rejected", "timed_out", "cancelled"],
  all: [],
}
const ALL_GROUPS = "__all__"

/** /approvals?request=<id>: the requests of my groups; the mail link opens one directly. */
export function ApprovalsPage() {
  const [params, setParams] = useSearchParams()
  const user = useCurrentUser()
  const admin = user.data?.authorities.includes("PLATFORM_ADMIN") ?? false
  const [filter, setFilter] = useState("pending")
  const [scope, setScope] = useState<"mine" | "all">("mine")
  const [group, setGroup] = useState(ALL_GROUPS)
  const list = useListState("-createdAt")
  const groups = useApprovalGroups()
  const requests = useApprovalRequests(scope, FILTERS[filter], list.page, {
    q: list.query || undefined, sort: list.sort, size: list.size, groupId: group === ALL_GROUPS ? [] : [group],
  })
  const selected = params.get("request")
  const open = (id: string | null) => setParams(id === null ? {} : { request: String(id) })

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Onaylarım" description="Üyesi olduğunuz onay gruplarına gelen talepler." />
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={filter}
          onValueChange={(value) => { if (value) { setFilter(value); list.resetPage() } }}>
          <ToggleGroupItem value="pending">Bekleyen</ToggleGroupItem>
          <ToggleGroupItem value="done">Sonuçlanan</ToggleGroupItem>
          <ToggleGroupItem value="all">Tümü</ToggleGroupItem>
        </ToggleGroup>
        {admin && (
          <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={scope}
            onValueChange={(value) => { if (value) { setScope(value as "mine" | "all"); list.resetPage() } }}>
            <ToggleGroupItem value="mine">Gruplarım</ToggleGroupItem>
            <ToggleGroupItem value="all">Tüm gruplar</ToggleGroupItem>
          </ToggleGroup>
        )}
        <Select value={group} onValueChange={(value) => { setGroup(value); list.resetPage() }}>
          <SelectTrigger size="sm" className="w-48" aria-label="Onay grubu"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL_GROUPS}>Bütün gruplar</SelectItem>
              {groups.data?.map((item) => <SelectItem key={item.id} value={String(item.id)}>{item.name}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Talep veya grup ara…" />
      </div>
      <Card className="py-0">
        <CardContent className="flex flex-col gap-3 px-0">
          {requests.isPending && <Skeleton className="m-4 h-32" />}
          {requests.isError && <div className="p-4"><ErrorAlert error={requests.error} /></div>}
          {requests.data && (requests.data.data.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyMedia variant="icon"><CheckCheckIcon /></EmptyMedia>
                <EmptyTitle>{list.query || group !== ALL_GROUPS ? "Bu filtreye uyan talep yok" : "Talep yok"}</EmptyTitle>
                <EmptyDescription>
                  {filter === "pending" ? "Kararınızı bekleyen talep bulunmuyor." : "Başka bir filtre deneyin."}
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead field="title" sort={list.sort} onSort={list.setSort} className="pl-4">Talep</SortableHead>
                  <SortableHead field="group" sort={list.sort} onSort={list.setSort}>Grup</SortableHead>
                  <TableHead>Başlatan</TableHead>
                  <SortableHead field="createdAt" sort={list.sort} onSort={list.setSort} descFirst className="hidden lg:table-cell">
                    Oluşturuldu
                  </SortableHead>
                  <SortableHead field="dueAt" sort={list.sort} onSort={list.setSort}>Son tarih</SortableHead>
                  <SortableHead field="status" sort={list.sort} onSort={list.setSort} className="pr-4">Durum</SortableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requests.data.data.map((request) => (
                  <TableRow key={request.id} className="cursor-pointer" onClick={() => open(request.id)}>
                    <TableCell className="max-w-96 pl-4">
                      <div className="truncate font-medium">{request.title}</div>
                      <div className="truncate text-xs text-muted-foreground">{request.source.label}</div>
                    </TableCell>
                    <TableCell className="max-w-48 truncate">{request.group.name}</TableCell>
                    <TableCell><RequesterBadge requestedBy={request.requestedBy} /></TableCell>
                    <TableCell className="hidden text-muted-foreground tabular-nums lg:table-cell">{formatDateTime(request.createdAt)}</TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-1">
                        <span className="tabular-nums">{formatDateTime(request.dueAt)}</span>
                        {request.status === "pending" && <DueBadge dueAt={request.dueAt} />}
                      </div>
                    </TableCell>
                    <TableCell className="pr-4">
                      <div className="flex flex-col items-start gap-1">
                        <ApprovalStatusBadge status={request.status} />
                        {request.status === "pending" && !request.canDecide && (
                          <span className="text-xs text-muted-foreground">
                            {request.decisionBlocker === "own_request" ? "Sizin talebiniz" : "Grubunuz değil"}
                          </span>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ))}
        </CardContent>
      </Card>
      <Pager meta={requests.data?.meta} onPage={list.setPage} onSize={list.setSize} />
      <DecisionDialog requestId={selected} onClose={() => open(null)}
        intent={params.get("decision") === "approve" || params.get("decision") === "reject"
          ? params.get("decision") as "approve" | "reject" : null} />
    </div>
  )
}

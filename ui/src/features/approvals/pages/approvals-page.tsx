import { useState } from "react"
import { useSearchParams } from "react-router"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { formatDateTime } from "@/shared/lib/format"
import { ApprovalStatusBadge } from "../components/approval-status-badge"
import { DecisionDialog } from "../components/decision-dialog"
import { useApprovalRequests } from "../queries/use-approval-requests"
import type { ApprovalStatus } from "../types/approval.dto"

const FILTERS: Record<string, ApprovalStatus[]> = {
  pending: ["pending"],
  done: ["approved", "rejected", "timed_out", "cancelled"],
  all: [],
}

/** /approvals?request=<id>: the requests of my groups; the mail link opens one directly. */
export function ApprovalsPage() {
  const [params, setParams] = useSearchParams()
  const user = useCurrentUser()
  const admin = user.data?.authorities.includes("PLATFORM_ADMIN") ?? false
  const [filter, setFilter] = useState("pending")
  const [scope, setScope] = useState<"mine" | "all">("mine")
  const [page, setPage] = useState(1)
  const requests = useApprovalRequests(scope, FILTERS[filter], page)
  const selected = params.get("request") ? Number(params.get("request")) : null
  const open = (id: number | null) => setParams(id === null ? {} : { request: String(id) })

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Onaylarım" description="Üyesi olduğunuz onay gruplarına gelen talepler." />
      <div className="flex flex-wrap gap-2">
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={filter}
          onValueChange={(value) => { if (value) { setFilter(value); setPage(1) } }}>
          <ToggleGroupItem value="pending">Bekleyen</ToggleGroupItem>
          <ToggleGroupItem value="done">Sonuçlanan</ToggleGroupItem>
          <ToggleGroupItem value="all">Tümü</ToggleGroupItem>
        </ToggleGroup>
        {admin && (
          <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={scope}
            onValueChange={(value) => { if (value) { setScope(value as "mine" | "all"); setPage(1) } }}>
            <ToggleGroupItem value="mine">Gruplarım</ToggleGroupItem>
            <ToggleGroupItem value="all">Tüm gruplar</ToggleGroupItem>
          </ToggleGroup>
        )}
      </div>
      <Card>
        <CardContent className="flex flex-col gap-3">
          {requests.isPending && <Skeleton className="h-32 w-full" />}
          {requests.isError && <ErrorAlert error={requests.error} />}
          {requests.data && (requests.data.data.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Talep yok.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Talep</TableHead>
                  <TableHead>Grup</TableHead>
                  <TableHead>Başlatan</TableHead>
                  <TableHead>Son tarih</TableHead>
                  <TableHead>Durum</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requests.data.data.map((request) => (
                  <TableRow key={request.id} className="cursor-pointer" onClick={() => open(request.id)}>
                    <TableCell className="max-w-80">
                      <div className="truncate font-medium">{request.title}</div>
                      <div className="truncate text-xs text-muted-foreground">{request.source.label}</div>
                    </TableCell>
                    <TableCell>{request.group.name}</TableCell>
                    <TableCell>{request.requestedBy}</TableCell>
                    <TableCell className="tabular-nums">{formatDateTime(request.dueAt)}</TableCell>
                    <TableCell><ApprovalStatusBadge status={request.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ))}
          <Pager meta={requests.data?.meta} onPage={setPage} />
        </CardContent>
      </Card>
      <DecisionDialog requestId={selected} onClose={() => open(null)}
        intent={params.get("decision") === "approve" || params.get("decision") === "reject"
          ? params.get("decision") as "approve" | "reject" : null} />
    </div>
  )
}

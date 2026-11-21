import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager, pageLocally } from "@/shared/components/pager"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import { GroupDialog } from "../components/group-dialog"
import { useDeleteApprovalGroup } from "../mutations/use-delete-approval-group"
import { useApprovalGroups } from "../queries/use-approval-groups"
import type { ApprovalGroupResponse } from "../types/approval.dto"

/** /approval-groups: everyone can see the groups, dataset admins can change them. */
export function ApprovalGroupsPage() {
  const groups = useApprovalGroups()
  const remove = useDeleteApprovalGroup()
  const admin = useCurrentUser().data?.authorities.includes("PLATFORM_ADMIN") ?? false
  const [editing, setEditing] = useState<{ group: ApprovalGroupResponse | null } | null>(null)
  const list = useListState("name", 20)
  const needle = list.search.trim().toLocaleLowerCase("tr")
  const matching = (groups.data ?? []).filter((group) => !needle || group.name.toLocaleLowerCase("tr").includes(needle)
    || group.members.some((member) => member.username.toLocaleLowerCase("tr").includes(needle)))
  const { rows, meta } = pageLocally(matching, list.page, list.size)

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Onay grupları" description="Akışlardaki onay adımları bu gruplardan birinin kararını bekler."
        actions={admin && (
          <Button onClick={() => setEditing({ group: null })}><PlusIcon data-icon="inline-start" />Yeni grup</Button>
        )} />
      <SearchInput value={list.search} onChange={(value) => { list.setSearch(value); list.setPage(1) }} placeholder="Grup veya üye ara…" />
      {groups.isPending && <Skeleton className="h-32 w-full" />}
      {groups.isError && <ErrorAlert error={groups.error} />}
      {groups.data?.length === 0 && <p className="text-sm text-muted-foreground">Henüz grup yok.</p>}
      <div className="grid min-w-0 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {rows.map((group) => (
          <Card key={group.id} className="min-w-0">
            <CardHeader>
              <CardTitle className="truncate">{group.name}</CardTitle>
              <CardDescription className="line-clamp-2">{group.description ?? `${group.members.length} üye`}</CardDescription>
              {admin && (
                <CardAction className="flex">
                  <Button variant="ghost" size="icon" aria-label="Düzenle" onClick={() => setEditing({ group })}><PencilIcon /></Button>
                  <Button variant="ghost" size="icon" aria-label="Sil" onClick={() => remove.mutate(group.id, {
                    onSuccess: () => toast.success("Grup silindi"),
                    onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
                  })}><Trash2Icon /></Button>
                </CardAction>
              )}
            </CardHeader>
            <CardContent className="flex flex-wrap gap-1">
              {group.members.map((member) => (
                <Badge key={member.username} variant="secondary" title={member.email ?? undefined}>{member.username}</Badge>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
      {groups.data && <Pager meta={meta} onPage={list.setPage} onSize={list.setSize} />}
      {editing && (
        <GroupDialog key={editing.group?.id ?? "new"} group={editing.group} open
          onOpenChange={(open) => !open && setEditing(null)} />
      )}
    </div>
  )
}

import {
  BarChart3Icon, ClipboardListIcon, CopyIcon, MoreHorizontalIcon, PencilIcon, PlusIcon, SendIcon, Trash2Icon,
} from "lucide-react"
import { useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router"
import { toast } from "sonner"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Item, ItemActions, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { SearchInput } from "@/shared/components/search-input"
import { SortableHead } from "@/shared/components/sortable-head"
import { useListState } from "@/shared/lib/list-state"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { formatDateTime } from "@/shared/lib/format"
import { STATUS_LABELS, SurveyStatusBadge } from "../components/survey-status-badge"
import { useDeleteSurvey } from "../mutations/use-survey-mutations"
import { useSurveys } from "../queries/use-surveys"
import type { SurveyListItem, SurveyStatus } from "../types/survey.dto"

const link = (id: string) => `${window.location.origin}/s/${id}`

/** `/surveys`: the surveys I design, and the ones waiting for my answer. */
export function SurveysPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get("tab") === "open" ? "open" : "mine"
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Anketler"
        description="Anket ve talep formları tasarlayın; yanıtlar kurallarla doğrulanır ve akışları başlatabilir."
        actions={<Button asChild><Link to="/surveys/new"><PlusIcon data-icon="inline-start" />Yeni anket</Link></Button>} />
      <Tabs value={tab} onValueChange={(value) => setParams(value === "open" ? { tab: "open" } : {})}>
        <TabsList>
          <TabsTrigger value="mine">Anketlerim</TabsTrigger>
          <TabsTrigger value="open">Yanıtlanacaklar</TabsTrigger>
        </TabsList>
        <TabsContent value="mine"><MySurveys /></TabsContent>
        <TabsContent value="open"><OpenSurveys /></TabsContent>
      </Tabs>
    </div>
  )
}

function MySurveys() {
  const list = useListState("-updatedAt")
  const [statuses, setStatuses] = useState<SurveyStatus[]>([])
  const surveys = useSurveys("mine", list.page, { q: list.query || undefined, status: statuses, sort: list.sort, size: list.size })
  const filtered = list.query !== "" || statuses.length > 0
  const remove = useDeleteSurvey()
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState<SurveyListItem | null>(null)

  const toolbar = (
    <div className="flex flex-wrap items-center gap-2">
      <ToggleGroup type="multiple" variant="outline" size="sm" value={statuses}
        onValueChange={(value) => { setStatuses(value as SurveyStatus[]); list.resetPage() }}>
        {(Object.keys(STATUS_LABELS) as SurveyStatus[]).map((status) => (
          <ToggleGroupItem key={status} value={status}>{STATUS_LABELS[status]}</ToggleGroupItem>
        ))}
      </ToggleGroup>
      <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Anket ara…" />
    </div>
  )
  if (surveys.isPending) return <div className="flex flex-col gap-3">{toolbar}<Skeleton className="h-48 w-full" /></div>
  if (surveys.isError) return <ErrorAlert error={surveys.error} />
  if (surveys.data.data.length === 0 && filtered) {
    return (
      <div className="flex flex-col gap-3">
        {toolbar}
        <p className="py-10 text-center text-sm text-muted-foreground">Bu filtreye uyan anket yok.</p>
      </div>
    )
  }
  if (surveys.data.data.length === 0) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyMedia variant="icon"><ClipboardListIcon /></EmptyMedia>
          <EmptyTitle>Henüz anketiniz yok</EmptyTitle>
          <EmptyDescription>Hazır bir şablonla ya da asistanla birkaç dakikada bir anket oluşturun.</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Button asChild><Link to="/surveys/new"><PlusIcon data-icon="inline-start" />Yeni anket</Link></Button>
        </EmptyContent>
      </Empty>
    )
  }
  return (
    <div className="flex flex-col gap-3">
      {toolbar}
      <Card className="py-0" data-agent-target="surveys">
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableHead field="title" sort={list.sort} onSort={list.setSort} className="pl-4">Anket</SortableHead>
                <SortableHead field="status" sort={list.sort} onSort={list.setSort}>Durum</SortableHead>
                <TableHead className="text-right">Soru</TableHead>
                <TableHead className="text-right">Yanıt</TableHead>
                <SortableHead field="closesAt" sort={list.sort} onSort={list.setSort} className="hidden lg:table-cell">Son gün</SortableHead>
                <SortableHead field="updatedAt" sort={list.sort} onSort={list.setSort} descFirst className="hidden md:table-cell">
                  Güncellendi
                </SortableHead>
                <TableHead className="w-12"><span className="sr-only">İşlemler</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {surveys.data.data.map((survey) => (
                <TableRow key={survey.id} data-agent-row={survey.id} className="cursor-pointer" onClick={() => navigate(`/surveys/${survey.id}`)}>
                  <TableCell className="max-w-80 pl-4">
                    <div className="flex flex-col">
                      <span className="truncate font-medium">{survey.title}</span>
                      {survey.description && <span className="truncate text-xs text-muted-foreground">{survey.description}</span>}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      <SurveyStatusBadge status={survey.status} />
                      {survey.anonymous && <Badge variant="outline">Anonim</Badge>}
                    </div>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{survey.questionCount}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {survey.responseCount > 0 ? <Badge variant="secondary">{survey.responseCount}</Badge> : <span className="text-muted-foreground">0</span>}
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground lg:table-cell">
                    {survey.closesAt ? formatDateTime(survey.closesAt) : "—"}
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground md:table-cell">{formatDateTime(survey.updatedAt)}</TableCell>
                  <TableCell onClick={(event) => event.stopPropagation()}>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon-sm" aria-label="İşlemler"><MoreHorizontalIcon /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuGroup>
                          <DropdownMenuItem asChild><Link to={`/surveys/${survey.id}/edit`}><PencilIcon />Düzenle</Link></DropdownMenuItem>
                          <DropdownMenuItem asChild><Link to={`/surveys/${survey.id}`}><BarChart3Icon />Sonuçlar</Link></DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => navigator.clipboard.writeText(link(survey.id))
                            .then(() => toast.success("Bağlantı kopyalandı", { description: link(survey.id) }))}>
                            <CopyIcon />Bağlantıyı kopyala
                          </DropdownMenuItem>
                        </DropdownMenuGroup>
                        <DropdownMenuSeparator />
                        <DropdownMenuGroup>
                          <DropdownMenuItem variant="destructive" onSelect={() => setDeleting(survey)}><Trash2Icon />Sil</DropdownMenuItem>
                        </DropdownMenuGroup>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <Pager meta={surveys.data.meta} onPage={list.setPage} onSize={list.setSize} />
      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>“{deleting?.title}” silinsin mi?</AlertDialogTitle>
            <AlertDialogDescription>
              Anket ve {deleting?.responseCount ?? 0} yanıtı kalıcı olarak silinir. Bu anketi dinleyen akışlar artık başlamaz.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Vazgeç</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => deleting && remove.mutate(deleting.id, {
              onSuccess: () => toast.success("Anket silindi"),
              onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
            })}>Sil</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function OpenSurveys() {
  const list = useListState("-updatedAt")
  const surveys = useSurveys("open", list.page, { size: list.size })
  if (surveys.isPending) return <Skeleton className="h-32 w-full" />
  if (surveys.isError) return <ErrorAlert error={surveys.error} />
  if (surveys.data.data.length === 0) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyMedia variant="icon"><SendIcon /></EmptyMedia>
          <EmptyTitle>Yanıt bekleyen anket yok</EmptyTitle>
          <EmptyDescription>Yayındaki anketler burada listelenir.</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  return (
    <div className="flex flex-col gap-3">
    <ItemGroup className="gap-2" data-agent-target="open-surveys">
      {surveys.data.data.map((survey) => (
        <Item key={survey.id} variant="outline" asChild data-agent-row={survey.id}>
          <Link to={`/s/${survey.id}`}>
            <ItemContent className="min-w-0">
              <ItemTitle className="w-full truncate">{survey.title}</ItemTitle>
              <ItemDescription className="truncate">
                {survey.questionCount} soru · {survey.owner}
                {survey.closesAt ? ` · son gün ${formatDateTime(survey.closesAt)}` : ""}
                {survey.anonymous ? " · anonim" : ""}
              </ItemDescription>
            </ItemContent>
            <ItemActions>
              {survey.answered ? <Badge variant="secondary">Yanıtladınız</Badge> : <Badge>Yanıtla</Badge>}
            </ItemActions>
          </Link>
        </Item>
      ))}
    </ItemGroup>
    <Pager meta={surveys.data.meta} onPage={list.setPage} onSize={list.setSize} />
    </div>
  )
}

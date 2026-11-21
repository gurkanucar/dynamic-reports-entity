import {
  CopyIcon, EyeIcon, FileSpreadsheetIcon, InboxIcon, MessageSquareIcon, PencilIcon, StarIcon, WorkflowIcon,
} from "lucide-react"
import { useState } from "react"
import { Link, useParams, useSearchParams } from "react-router"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { SearchInput } from "@/shared/components/search-input"
import { SortableHead } from "@/shared/components/sortable-head"
import { StatusBadge } from "@/shared/components/status-badge"
import { useListState } from "@/shared/lib/list-state"
import { ResponseSheet } from "../components/response-sheet"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { formatDateTime, shortId } from "@/shared/lib/format"
import { surveysApi } from "../api/surveys.api"
import {
  FOLLOW_LABELS, FollowStatusBadge, PRIORITY_LABELS, PriorityBadge, SurveyStatusBadge,
} from "../components/survey-status-badge"
import { typeInfo } from "../lib/field-types"
import { useSurvey, useSurveyResponses, useSurveySummary } from "../queries/use-surveys"
import type { FollowStatus, Priority, SurveyResponse, SurveySummary } from "../types/survey.dto"

/** `/surveys/:surveyId`: the owner's view of the answers: a summary per question, every response, Excel. */
export function SurveyResultsPage() {
  const id = useParams().surveyId ?? ""
  const survey = useSurvey(id)
  const summary = useSurveySummary(id)
  if (survey.isPending) return <Skeleton className="h-96 w-full" />
  if (survey.isError) return <ErrorAlert error={survey.error} title="Anket yüklenemedi" />
  const data = survey.data
  const url = `${window.location.origin}/s/${id}`

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title={data.title}
        description={(
          <span className="flex flex-wrap items-center gap-2">
            <SurveyStatusBadge status={data.status} />
            {data.anonymous && <Badge variant="outline">Anonim</Badge>}
            {data.oneResponsePerUser && <Badge variant="outline">Kişi başı bir yanıt</Badge>}
            {data.closesAt && <span>Son gün {formatDateTime(data.closesAt)}</span>}
          </span>
        )}
        actions={(
          <>
            <Button variant="outline" onClick={() => navigator.clipboard.writeText(url)
              .then(() => toast.success("Bağlantı kopyalandı", { description: url }))}>
              <CopyIcon data-icon="inline-start" />Bağlantı
            </Button>
            <Button variant="outline" asChild><Link to={`/s/${id}`} target="_blank"><EyeIcon data-icon="inline-start" />Formu aç</Link></Button>
            <Button variant="outline" asChild disabled={data.responseCount === 0}>
              <a href={surveysApi.exportUrl(id)} download><FileSpreadsheetIcon data-icon="inline-start" />Excel</a>
            </Button>
            <Button asChild><Link to={`/surveys/${id}/edit`}><PencilIcon data-icon="inline-start" />Düzenle</Link></Button>
          </>
        )} />

      <FlowHint survey={data} />

      <Tabs defaultValue="summary">
        <TabsList>
          <TabsTrigger value="summary">Özet</TabsTrigger>
          <TabsTrigger value="responses">Yanıtlar ({data.responseCount})</TabsTrigger>
        </TabsList>
        <TabsContent value="summary">
          {summary.isPending && <Skeleton className="h-64 w-full" />}
          {summary.isError && <ErrorAlert error={summary.error} />}
          {summary.data && <Summary summary={summary.data} />}
        </TabsContent>
        <TabsContent value="responses"><Responses survey={data} /></TabsContent>
      </Tabs>
    </div>
  )
}

function FlowHint({ survey }: { survey: SurveyResponse }) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><WorkflowIcon />Her yanıtta bir akış başlatın</CardTitle>
        <CardDescription>
          Onaya gönderin, e-posta atın, başka bir sisteme iletin. Akış yanıtı <code>trigger.answers.&lt;anahtar&gt;</code>
          {" "}ve okunur hâlini <code>trigger.text.&lt;anahtar&gt;</code> olarak görür.
        </CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" asChild>
            <Link to={`/flows/new?survey=${survey.id}`}>Akış oluştur</Link>
          </Button>
        </CardAction>
      </CardHeader>
    </Card>
  )
}

function Summary({ summary }: { summary: SurveySummary }) {
  if (summary.total === 0) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyMedia variant="icon"><InboxIcon /></EmptyMedia>
          <EmptyTitle>Henüz yanıt yok</EmptyTitle>
          <EmptyDescription>Anketi yayınlayıp bağlantıyı paylaşın; yanıtlar geldikçe burada özetlenir.</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {summary.fields.map((field) => {
        const info = typeInfo(field.type)
        const max = Math.max(1, ...field.counts.map((count) => count.count))
        return (
          <Card key={field.fieldId} className="min-w-0">
            <CardHeader>
              <CardDescription className="flex items-center gap-2"><info.icon className="size-4" />{info.label}</CardDescription>
              <CardTitle className="leading-snug">{field.label}</CardTitle>
              <CardAction className="text-right text-xs text-muted-foreground">
                {field.answered} / {summary.total} yanıt
              </CardAction>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {field.average !== null && (
                <div className="flex items-baseline gap-2">
                  <span className="text-3xl font-semibold tabular-nums">{field.average.toLocaleString("tr-TR", { maximumFractionDigits: 1 })}</span>
                  <span className="text-sm text-muted-foreground">
                    ortalama{field.type === "rating" && <StarIcon className="ml-1 inline size-3.5 fill-amber-400 text-amber-500" />}
                    {field.type === "nps" && ` · NPS ${nps(field.counts)}`}
                  </span>
                </div>
              )}
              {field.counts.length > 0 && field.type !== "nps" && field.counts.map((count) => (
                <div key={count.key} className="flex flex-col gap-1">
                  <div className="flex justify-between gap-2 text-sm">
                    <span className="truncate">{count.label}</span>
                    <span className="shrink-0 text-muted-foreground tabular-nums">
                      {count.count} · %{Math.round((100 * count.count) / Math.max(1, field.answered))}
                    </span>
                  </div>
                  <Progress value={(100 * count.count) / max} />
                </div>
              ))}
              {field.type === "nps" && (
                <div className="flex h-24 items-end gap-1">
                  {field.counts.map((count) => (
                    <div key={count.key} className="flex flex-1 flex-col items-center gap-1">
                      <div className="w-full rounded-sm bg-primary/80" style={{ height: `${(80 * count.count) / max}px` }}
                        title={`${count.label}: ${count.count}`} />
                      <span className="text-[10px] text-muted-foreground">{count.label}</span>
                    </div>
                  ))}
                </div>
              )}
              {field.samples.length > 0 && (
                <ul className="flex flex-col gap-2">
                  {field.samples.map((sample, index) => (
                    <li key={index} className="rounded-md bg-muted px-3 py-2 text-sm whitespace-pre-line">{sample}</li>
                  ))}
                </ul>
              )}
              {field.counts.length === 0 && field.samples.length === 0 && field.average === null && (
                <p className="text-sm text-muted-foreground">
                  {field.answered === 0 ? "Bu soruyu henüz yanıtlayan yok." : "Bu soru için özet yok; yanıtlar sekmesine bakın."}
                </p>
              )}
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}

/** Net Promoter Score: % of 9-10 minus % of 0-6. */
function nps(counts: { key: string; count: number }[]): number {
  const total = counts.reduce((sum, count) => sum + count.count, 0)
  if (total === 0) return 0
  const promoters = counts.filter((count) => Number(count.key) >= 9).reduce((sum, count) => sum + count.count, 0)
  const detractors = counts.filter((count) => Number(count.key) <= 6).reduce((sum, count) => sum + count.count, 0)
  return Math.round((100 * (promoters - detractors)) / total)
}

function Responses({ survey }: { survey: SurveyResponse }) {
  const list = useListState("-submittedAt")
  const [statuses, setStatuses] = useState<FollowStatus[]>([])
  const [priorities, setPriorities] = useState<Priority[]>([])
  const responses = useSurveyResponses(survey.id, list.page, { status: statuses, priority: priorities,
    q: list.query || undefined, sort: list.sort, size: list.size })
  // "?response=<id>": a run's survey step links straight to the response
  const [params] = useSearchParams()
  const [open, setOpen] = useState<string | null>(params.get("response"))
  const tracking = survey.settings.tracking
  const questions = survey.definition.fields.filter((field) => field.type !== "section")
  const columns = questions.slice(0, 2)
  const filtered = statuses.length > 0 || priorities.length > 0 || list.query !== ""

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        {tracking.status && (
          <ToggleGroup type="multiple" variant="outline" size="sm" className="flex-wrap" value={statuses}
            onValueChange={(value) => { setStatuses(value as FollowStatus[]); list.resetPage() }}>
            {(Object.keys(FOLLOW_LABELS) as FollowStatus[]).map((status) => (
              <ToggleGroupItem key={status} value={status}>
                {FOLLOW_LABELS[status]}
                <span className="text-muted-foreground tabular-nums">{survey.followUpCounts[status] ?? 0}</span>
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        )}
        {tracking.priority && (
          <ToggleGroup type="multiple" variant="outline" size="sm" value={priorities}
            onValueChange={(value) => { setPriorities(value as Priority[]); list.resetPage() }} aria-label="Öncelik">
            {(Object.keys(PRIORITY_LABELS) as Priority[]).map((priority) => (
              <ToggleGroupItem key={priority} value={priority}>{PRIORITY_LABELS[priority]}</ToggleGroupItem>
            ))}
          </ToggleGroup>
        )}
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Kişi, cevap veya atanan ara…" />
      </div>
      {responses.isPending && <Skeleton className="h-48 w-full" />}
      {responses.isError && <ErrorAlert error={responses.error} />}
      {responses.data && responses.data.data.length === 0 && (
        <p className="py-10 text-center text-sm text-muted-foreground">{filtered ? "Bu filtreye uyan yanıt yok." : "Henüz yanıt yok."}</p>
      )}
      {responses.data && responses.data.data.length > 0 && (
        <Card className="py-0">
          <CardContent className="overflow-x-auto px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-16 pl-4">No</TableHead>
                  <SortableHead field="submittedAt" sort={list.sort} onSort={list.setSort} descFirst>Gönderilme</SortableHead>
                  {!survey.anonymous && <SortableHead field="respondent" sort={list.sort} onSort={list.setSort}>Gönderen</SortableHead>}
                  {tracking.status && <SortableHead field="status" sort={list.sort} onSort={list.setSort}>Durum</SortableHead>}
                  {tracking.priority && <SortableHead field="priority" sort={list.sort} onSort={list.setSort} descFirst>Öncelik</SortableHead>}
                  {tracking.followUpDate && <SortableHead field="followUpAt" sort={list.sort} onSort={list.setSort}>Takip</SortableHead>}
                  {tracking.assignee && <SortableHead field="assignee" sort={list.sort} onSort={list.setSort}>Atanan</SortableHead>}
                  {columns.map((field) => <TableHead key={field.id} className="hidden max-w-48 truncate xl:table-cell">{field.label}</TableHead>)}
                  <TableHead className="pr-4"><MessageSquareIcon className="size-4" aria-label="Notlar" /></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {responses.data.data.map((row) => (
                  <TableRow key={row.id} className="cursor-pointer" onClick={() => setOpen(row.id)}>
                    <TableCell className="pl-4 tabular-nums text-muted-foreground" title={row.id}>#{shortId(row.id)}</TableCell>
                    <TableCell className="whitespace-nowrap tabular-nums">{formatDateTime(row.submittedAt)}</TableCell>
                    {!survey.anonymous && <TableCell>{row.respondent}</TableCell>}
                    {tracking.status && <TableCell><FollowStatusBadge status={row.status} /></TableCell>}
                    {tracking.priority && <TableCell>{row.priority ? <PriorityBadge priority={row.priority} /> : <span className="text-muted-foreground">—</span>}</TableCell>}
                    {tracking.followUpDate && (
                      <TableCell className="whitespace-nowrap tabular-nums">
                        {row.followUpAt ? <FollowUpDate date={row.followUpAt} done={row.status === "resolved"} /> : <span className="text-muted-foreground">—</span>}
                      </TableCell>
                    )}
                    {tracking.assignee && <TableCell>{row.assignee ? <Badge variant="outline">{row.assignee}</Badge> : <span className="text-muted-foreground">—</span>}</TableCell>}
                    {columns.map((field) => (
                      <TableCell key={field.id} className="hidden max-w-48 truncate xl:table-cell">{row.display[field.id] ?? "—"}</TableCell>
                    ))}
                    <TableCell className="pr-4 tabular-nums text-muted-foreground">{row.notes || ""}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
      <Pager meta={responses.data?.meta} onPage={list.setPage} onSize={list.setSize} />
      <ResponseSheet survey={survey} responseId={open} onClose={() => setOpen(null)} />
    </div>
  )
}

/** A follow-up date, red when it has passed and the response is not resolved. */
function FollowUpDate({ date, done }: { date: string; done: boolean }) {
  const overdue = !done && date < new Date().toISOString().slice(0, 10)
  return overdue ? <StatusBadge tone="danger">{formatDate(date)}</StatusBadge> : <span>{formatDate(date)}</span>
}

const formatDate = (date: string) => new Date(`${date}T00:00:00`).toLocaleDateString("tr-TR")

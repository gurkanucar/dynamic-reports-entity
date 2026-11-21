import {
  CheckCheckIcon, CircleXIcon, DatabaseIcon, HistoryIcon, PlusIcon, WorkflowIcon, type LucideIcon,
} from "lucide-react"
import { useState } from "react"
import { Link } from "react-router"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, ChartTooltipContent, type ChartConfig,
} from "@/components/ui/chart"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Item, ItemActions, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { RunSheet } from "@/features/flows/components/run-sheet"
import { RunStatusBadge } from "@/features/flows/components/run-status-badge"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { formatDateTime } from "@/shared/lib/format"
import { recent, runsPerDay } from "../lib/run-stats"
import { useDashboard } from "../queries/use-dashboard"

const CHART: ChartConfig = {
  completed: { label: "Tamamlandı", color: "oklch(0.63 0.15 150)" },
  failed: { label: "Başarısız", color: "var(--destructive)" },
  other: { label: "Sürüyor / iptal", color: "var(--muted-foreground)" },
}

const TRIGGERS: Record<string, string> = { manual: "Elle", schedule: "Zamanlama", webhook: "Webhook", kafka: "Kafka" }

/** The home page: what needs attention (approvals, failures) and how the flows have been running. */
export function DashboardPage() {
  const user = useCurrentUser().data
  const dashboard = useDashboard()
  const [runId, setRunId] = useState<number | null>(null)
  const data = dashboard.data

  const week = data ? recent(data.runs, 7) : []
  const failedWeek = week.filter((run) => run.status === "failed")
  const finishedWeek = week.filter((run) => run.status === "completed" || run.status === "failed")
  const successRate = finishedWeek.length
    ? Math.round((100 * (finishedWeek.length - failedWeek.length)) / finishedWeek.length) : null
  const days = data ? runsPerDay(data.runs, 14) : []
  // flows whose latest run failed: what to look at first
  const latestByFlow = new Map<number, (typeof week)[number]>()
  for (const run of data?.runs ?? []) if (!latestByFlow.has(run.flow.id)) latestByFlow.set(run.flow.id, run)
  const broken = [...latestByFlow.values()].filter((run) => run.status === "failed")

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title={`Merhaba${user ? `, ${user.username.split("@")[0]}` : ""}`}
        description="Bekleyen onaylar, akışların son durumu ve son çalışmalar."
        actions={(
          <>
            <Button variant="outline" asChild><Link to="/datasets/new"><DatabaseIcon data-icon="inline-start" />Yeni veri seti</Link></Button>
            <Button asChild><Link to="/flows/new"><PlusIcon data-icon="inline-start" />Yeni akış</Link></Button>
          </>
        )} />
      {dashboard.isError && <ErrorAlert error={dashboard.error} title="Pano yüklenemedi" />}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={CheckCheckIcon} title="Onayınızı bekleyen" to="/approvals" loading={!data}
          value={data?.pendingTotal} hint={data?.pendingTotal ? "Karar vermeniz gereken talepler" : "Bekleyen talep yok"}
          tone={data?.pendingTotal ? "attention" : undefined} />
        <Stat icon={HistoryIcon} title="Son 7 günde çalışma" to="/runs" loading={!data} value={week.length}
          hint={successRate === null ? "Biten çalışma yok" : `%${successRate} başarılı`} />
        <Stat icon={CircleXIcon} title="Başarısız (7 gün)" to="/runs" loading={!data} value={failedWeek.length}
          hint={failedWeek.length ? "Ayrıntılar çalışmalarda" : "Sorun yok"}
          tone={failedWeek.length ? "danger" : undefined} />
        <Stat icon={WorkflowIcon} title="Akışlar / veri setleri" to="/flows" loading={!data}
          value={data ? `${data.flowCount} / ${data.datasetCount}` : undefined} hint="Tanımlı akış ve kayıtlı veri seti" />
      </div>

      <div className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>Son 14 gün</CardTitle>
            <CardDescription>Günlük çalışma sayısı (son 100 çalışmadan)</CardDescription>
          </CardHeader>
          <CardContent>
            {!data ? <Skeleton className="h-64 w-full" /> : (
              <ChartContainer config={CHART} className="h-64 w-full">
                <BarChart data={days} margin={{ left: 0, right: 8 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={12} />
                  <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={32} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="completed" stackId="runs" fill="var(--color-completed)" />
                  <Bar dataKey="failed" stackId="runs" fill="var(--color-failed)" />
                  <Bar dataKey="other" stackId="runs" fill="var(--color-other)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ChartContainer>
            )}
          </CardContent>
        </Card>

        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>Onayınızı bekleyenler</CardTitle>
            <CardDescription>Üyesi olduğunuz gruplara gelen en yeni talepler</CardDescription>
            <CardAction><Button variant="ghost" size="sm" asChild><Link to="/approvals">Tümü</Link></Button></CardAction>
          </CardHeader>
          <CardContent>
            {!data ? <Skeleton className="h-40 w-full" /> : data.pending.length === 0 ? (
              <Empty className="py-6">
                <EmptyHeader>
                  <EmptyMedia variant="icon"><CheckCheckIcon /></EmptyMedia>
                  <EmptyTitle>Her şey yolunda</EmptyTitle>
                  <EmptyDescription>Kararınızı bekleyen talep yok.</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : (
              <ItemGroup className="gap-2">
                {data.pending.map((request) => (
                  <Item key={request.id} variant="outline" size="sm" asChild>
                    <Link to={`/approvals?request=${request.id}`}>
                      <ItemContent className="min-w-0">
                        <ItemTitle className="w-full truncate">{request.title}</ItemTitle>
                        <ItemDescription className="truncate">
                          {request.group.name} · son tarih {formatDateTime(request.dueAt)}
                        </ItemDescription>
                      </ItemContent>
                    </Link>
                  </Item>
                ))}
              </ItemGroup>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>Son çalışmalar</CardTitle>
            <CardDescription>Ayrıntı için tıklayın</CardDescription>
            <CardAction><Button variant="ghost" size="sm" asChild><Link to="/runs">Tümü</Link></Button></CardAction>
          </CardHeader>
          <CardContent>
            {!data ? <Skeleton className="h-48 w-full" /> : data.runs.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">Henüz çalışma yok.</p>
            ) : (
              <ItemGroup className="gap-1">
                {data.runs.slice(0, 8).map((run) => (
                  <Item key={run.id} size="sm" asChild>
                    <button type="button" className="text-left hover:bg-muted" onClick={() => setRunId(run.id)}>
                      <ItemContent className="min-w-0">
                        <ItemTitle className="w-full truncate">{run.flow.name}</ItemTitle>
                        <ItemDescription className="truncate">
                          #{run.id} · {TRIGGERS[run.trigger] ?? run.trigger} · {formatDateTime(run.createdAt)}
                          {run.error ? ` · ${run.error.code}` : ""}
                        </ItemDescription>
                      </ItemContent>
                      <ItemActions><RunStatusBadge status={run.status} /></ItemActions>
                    </button>
                  </Item>
                ))}
              </ItemGroup>
            )}
          </CardContent>
        </Card>

        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>Dikkat isteyen akışlar</CardTitle>
            <CardDescription>Son çalışması başarısız olanlar</CardDescription>
          </CardHeader>
          <CardContent>
            {!data ? <Skeleton className="h-32 w-full" /> : broken.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">Bütün akışların son çalışması sorunsuz.</p>
            ) : (
              <ItemGroup className="gap-2">
                {broken.map((run) => (
                  <Item key={run.flow.id} variant="outline" size="sm" asChild>
                    <Link to={`/flows/${run.flow.id}`}>
                      <ItemContent className="min-w-0">
                        <ItemTitle className="w-full truncate">{run.flow.name}</ItemTitle>
                        <ItemDescription className="truncate">
                          {run.error?.message ?? "Başarısız"} · {formatDateTime(run.createdAt)}
                        </ItemDescription>
                      </ItemContent>
                      <ItemActions><Badge variant="destructive">Başarısız</Badge></ItemActions>
                    </Link>
                  </Item>
                ))}
              </ItemGroup>
            )}
          </CardContent>
        </Card>
      </div>
      <RunSheet runId={runId} onClose={() => setRunId(null)} />
    </div>
  )
}

/** One figure with a link to where it comes from; `tone` draws the eye when something needs attention. */
function Stat({ icon: Icon, title, value, hint, to, loading, tone }: {
  icon: LucideIcon
  title: string
  value: number | string | undefined
  hint: string
  to: string
  loading: boolean
  tone?: "attention" | "danger"
}) {
  return (
    <Link to={to} className="group/stat rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
      <Card className="h-full transition-colors group-hover/stat:bg-muted/40">
        <CardHeader>
          <CardDescription className="flex items-center gap-2"><Icon className="size-4" />{title}</CardDescription>
          {loading ? <Skeleton className="h-9 w-20" /> : (
            <CardTitle className={cn("text-3xl tabular-nums", tone === "danger" && "text-destructive",
              tone === "attention" && "text-primary")}>
              {value}
            </CardTitle>
          )}
          <CardDescription>{hint}</CardDescription>
        </CardHeader>
      </Card>
    </Link>
  )
}

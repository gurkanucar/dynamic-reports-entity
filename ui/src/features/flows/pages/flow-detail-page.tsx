import { PencilIcon, PlayIcon, Trash2Icon } from "lucide-react"
import { useMemo, useState } from "react"
import { Link, useNavigate, useParams } from "react-router"
import { toast } from "sonner"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { FlowCanvas } from "../components/flow-canvas"
import { RunSheet } from "../components/run-sheet"
import { RunsTable } from "../components/runs-table"
import { SchedulesCard } from "../components/schedules-card"
import { TriggersCard } from "../components/triggers-card"
import { withExplicitEdges, withLayout } from "../lib/flow-graph"
import { describeTrigger, toDrafts } from "../lib/triggers"
import type { StartLine } from "../components/start-node"
import { useSchedules } from "../queries/use-schedules"
import { useTriggers } from "../queries/use-triggers"
import { useDeleteFlow } from "../mutations/use-delete-flow"
import { useStartRun } from "../mutations/use-start-run"
import { useFlow } from "../queries/use-flow"
import { useFlowRuns } from "../queries/use-flow-runs"

export function FlowDetailPage() {
  const flowId = Number(useParams().flowId)
  const flow = useFlow(flowId)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const runs = useFlowRuns(flowId, page, size)
  const start = useStartRun()
  const remove = useDeleteFlow()
  const navigate = useNavigate()
  const [selectedRun, setSelectedRun] = useState<number | null>(null)
  const schedules = useSchedules(flowId)
  const triggers = useTriggers(flowId)
  const startLines: StartLine[] | undefined = useMemo(() => schedules.data && triggers.data ? [
    { kind: "manual", text: "Elle (Şimdi çalıştır)", enabled: true },
    ...toDrafts(schedules.data, triggers.data).map((trigger): StartLine => ({
      kind: trigger.kind, text: describeTrigger(trigger), enabled: trigger.enabled })),
  ] : undefined, [schedules.data, triggers.data])
  const model = useMemo(() => flow.data
    ? withLayout(withExplicitEdges({ steps: flow.data.steps, edges: flow.data.edges ?? [], layout: flow.data.layout ?? {} }))
    : null, [flow.data])

  if (flow.isPending) return <Skeleton className="h-64 w-full" />
  if (flow.isError) return <ErrorAlert error={flow.error} title="Akış yüklenemedi" />
  const data = flow.data

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title={data.name} description={`${data.owner.username} yetkileriyle çalışır`}
        actions={(
          <>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="ghost"><Trash2Icon data-icon="inline-start" />Sil</Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Akış silinsin mi?</AlertDialogTitle>
                  <AlertDialogDescription>Zamanlamaları da silinir; geçmiş çalışmalar kalır.</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Vazgeç</AlertDialogCancel>
                  <AlertDialogAction onClick={() => remove.mutate(flowId, {
                    onSuccess: () => { toast.success("Akış silindi"); navigate("/flows") },
                    onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
                  })}>Sil</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
            <Button variant="outline" asChild>
              <Link to={`/flows/${flowId}/edit`}><PencilIcon data-icon="inline-start" />Düzenle</Link>
            </Button>
            <Button disabled={start.isPending} onClick={() => start.mutate(flowId, {
              onSuccess: (run) => { toast.success("Çalışma sıraya alındı"); setSelectedRun(run.id) },
              onError: (error) => toast.error("Başlatılamadı", { description: errorMessage(error) }),
            })}>
              {start.isPending ? <Spinner data-icon="inline-start" /> : <PlayIcon data-icon="inline-start" />}Şimdi çalıştır
            </Button>
          </>
        )} />

      <Card>
        <CardHeader>
          <CardTitle>Akış</CardTitle>
          <CardDescription>Bir adım hata verirse yeniden denenir; onay adımları karar gelene kadar bekler.</CardDescription>
        </CardHeader>
        <CardContent>
          {model && <FlowCanvas model={model} start={startLines} className="h-96 w-full" />}
        </CardContent>
      </Card>

      <div className="grid min-w-0 gap-6 lg:grid-cols-2">
        <SchedulesCard flowId={flowId} />
        <TriggersCard flowId={flowId} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Çalışmalar</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {runs.isError && <ErrorAlert error={runs.error} />}
          {runs.data && <RunsTable runs={runs.data.data} onSelect={setSelectedRun} />}
          <Pager meta={runs.data?.meta} onPage={setPage} onSize={(next) => { setSize(next); setPage(1) }} />
        </CardContent>
      </Card>
      <RunSheet runId={selectedRun} onClose={() => setSelectedRun(null)} />
    </div>
  )
}

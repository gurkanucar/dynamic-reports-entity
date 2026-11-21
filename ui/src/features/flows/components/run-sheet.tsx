import {
  CircleCheckIcon, CircleStopIcon, CircleXIcon, DownloadIcon, HourglassIcon, ListOrderedIcon, PlayIcon, RotateCwIcon,
  type LucideIcon,
} from "lucide-react"
import { useMemo } from "react"
import { Link } from "react-router"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import {
  Trace, TraceCollapsible, TraceCollapsibleContent, TraceCollapsibleTrigger, TraceDescription, TraceIcon, TraceLine,
  TraceTitle,
} from "@/components/ui/trace"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { formatBytes, formatDateTime } from "@/shared/lib/format"
import { useCancelRun } from "../mutations/use-cancel-run"
import { useDownloadFile } from "../mutations/use-download-file"
import { useRun } from "../queries/use-run"
import { withExplicitEdges, withLayout } from "../lib/flow-graph"
import { stepType } from "../lib/step-types"
import { FlowCanvas, type RunState } from "./flow-canvas"
import { RunStatusBadge } from "./run-status-badge"
import { StepOutput } from "./step-output"

const EVENT_LABELS: Record<string, string> = {
  run_queued: "Sıraya alındı",
  step_started: "Adım başladı",
  step_completed: "Adım tamamlandı",
  step_retry_scheduled: "Tekrar denenecek",
  step_released: "Adım geri bırakıldı",
  step_failed: "Adım başarısız",
  step_cancelled: "Adım iptal edildi",
  step_waiting: "Karar bekleniyor",
  approval_approved: "Onaylandı",
  approval_rejected: "Reddedildi",
  approval_timed_out: "Onay zaman aşımına uğradı",
  cancel_requested: "İptal istendi",
  run_completed: "Tamamlandı",
  run_failed: "Başarısız",
  run_cancelled: "İptal edildi",
}

/** An icon per history entry: started, waiting, done, failed ... */
function eventIcon(type: string): LucideIcon {
  if (type.endsWith("failed")) return CircleXIcon
  if (type.endsWith("cancelled") || type === "cancel_requested") return CircleStopIcon
  if (type.endsWith("completed") || type === "approval_approved") return CircleCheckIcon
  if (type === "approval_rejected") return CircleXIcon
  if (type === "step_waiting" || type === "approval_timed_out") return HourglassIcon
  if (type === "step_retry_scheduled" || type === "step_released") return RotateCwIcon
  if (type === "run_queued") return ListOrderedIcon
  return PlayIcon
}

/** Steps, files and history of a run; refreshes itself while the run is active. */
export function RunSheet({ runId, onClose }: { runId: number | null; onClose: () => void }) {
  const run = useRun(runId)
  const cancel = useCancelRun()
  const download = useDownloadFile()
  const data = run.data
  const active = data?.status === "queued" || data?.status === "running"
  const definition = data?.definition
  const steps = data?.steps
  const model = useMemo(() => definition
    ? withLayout(withExplicitEdges({ steps: definition.steps, edges: definition.edges ?? [],
      layout: definition.layout ?? {} }))
    : null, [definition])
  const runState = useMemo(() => Object.fromEntries((steps ?? []).map((step) =>
    [step.key, { status: step.status, outcome: step.outcome } satisfies RunState])), [steps])

  return (
    <Sheet open={runId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto data-[side=right]:w-full data-[side=right]:sm:max-w-3xl">
        <SheetHeader>
          <SheetTitle>Çalışma #{runId}</SheetTitle>
          <SheetDescription>{data ? `${data.flow.name} · ${data.runAs.username} yetkileriyle` : "Yükleniyor…"}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-4 px-4 pb-6">
          {run.isPending && <Skeleton className="h-48 w-full" />}
          {run.isError && <ErrorAlert error={run.error} />}
          {data && (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <RunStatusBadge status={data.status} />
                {data.cancelRequested && active && <Badge variant="outline">İptal ediliyor</Badge>}
                <span className="text-sm text-muted-foreground">
                  {formatDateTime(data.startedAt ?? data.createdAt)} → {formatDateTime(data.finishedAt)}
                </span>
                {active && (
                  <Button variant="destructive" size="sm" className="ml-auto" disabled={cancel.isPending || data.cancelRequested}
                    onClick={() => cancel.mutate(data.id, {
                      onSuccess: () => toast.info("İptal istendi"),
                      onError: (error) => toast.error("İptal edilemedi", { description: errorMessage(error) }),
                    })}>
                    {cancel.isPending ? <Spinner data-icon="inline-start" /> : <CircleStopIcon data-icon="inline-start" />}İptal et
                  </Button>
                )}
              </div>
              {data.error && <ErrorAlert error={new Error(data.error.message)} title={data.error.code} />}

              {model && model.steps.length > 1 && (
                <FlowCanvas model={model} runState={runState} className="h-96 w-full" />
              )}

              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Adımlar</h3>
                {data.steps.map((step) => (
                  <div key={step.key} className="flex flex-col gap-1 rounded-md border p-3 text-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="min-w-0 truncate font-medium">{step.key}</span>
                      <Badge variant="outline">{stepType(step.type as never)?.label ?? step.type}</Badge>
                      <RunStatusBadge status={step.status} />
                      {step.outcome && <Badge variant="secondary">{stepType(step.type as never)?.outcomes
                        .find((outcome) => outcome.id === step.outcome)?.label ?? step.outcome}</Badge>}
                      <span className="ml-auto whitespace-nowrap text-muted-foreground">deneme {step.attempt}/{step.maxAttempts}</span>
                    </div>
                    {step.status === "waiting" && (
                      <span className="text-muted-foreground">
                        {formatDateTime(step.waitingUntil)} tarihine kadar karar bekleniyor ·{" "}
                        <Link className="underline underline-offset-4" to="/approvals">Onaylarım</Link>
                      </span>
                    )}
                    <StepOutput step={step} />
                    {step.nextAttemptAt && <span className="text-muted-foreground">Sonraki deneme: {formatDateTime(step.nextAttemptAt)}</span>}
                    {step.error && <span className="text-destructive">{step.error.code}: {step.error.message}</span>}
                  </div>
                ))}
              </section>

              {data.files.length > 0 && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-sm font-medium">Dosyalar</h3>
                  {data.files.map((file) => (
                    <div key={file.id} className="flex items-center gap-2 rounded-md border p-3 text-sm">
                      <span className="truncate">{file.fileName}</span>
                      <span className="text-muted-foreground">{formatBytes(file.sizeBytes)}</span>
                      {file.expired ? (
                        <Badge variant="outline" className="ml-auto">Süresi doldu</Badge>
                      ) : (
                        <Button variant="outline" size="sm" className="ml-auto" disabled={download.isPending}
                          onClick={() => download.mutate(file.id, {
                            onError: (error) => toast.error("İndirilemedi", { description: errorMessage(error) }),
                          })}>
                          <DownloadIcon data-icon="inline-start" />İndir
                        </Button>
                      )}
                    </div>
                  ))}
                  <p className="text-xs text-muted-foreground">
                    Dosyalar {formatDateTime(data.files[0].expiresAt)} tarihine kadar saklanır.
                  </p>
                </section>
              )}

              {data.triggerPayload != null && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-sm font-medium">Tetikleyen veri</h3>
                  <pre className="max-h-48 overflow-auto rounded-md bg-muted p-3 text-xs">
                    {JSON.stringify(data.triggerPayload, null, 2)}</pre>
                </section>
              )}

              <Separator />
              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Geçmiş</h3>
                <Trace>
                  {data.events.map((event, index) => {
                    const Icon = eventIcon(event.type)
                    const status = event.type.endsWith("failed") ? "error"
                      : active && index === data.events.length - 1 ? "running" : undefined
                    const line = (
                      <TraceLine status={status}>
                        <TraceIcon><Icon /></TraceIcon>
                        <TraceTitle>
                          {EVENT_LABELS[event.type] ?? event.type}{event.stepKey ? ` · ${event.stepKey}` : ""}
                        </TraceTitle>
                        <TraceDescription>{formatDateTime(event.occurredAt)}</TraceDescription>
                      </TraceLine>
                    )
                    return event.message ? (
                      <TraceCollapsible key={event.id}>
                        <TraceCollapsibleTrigger>{line}</TraceCollapsibleTrigger>
                        <TraceCollapsibleContent className="text-xs whitespace-pre-line">{event.message}</TraceCollapsibleContent>
                      </TraceCollapsible>
                    ) : <div key={event.id}>{line}</div>
                  })}
                </Trace>
              </section>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

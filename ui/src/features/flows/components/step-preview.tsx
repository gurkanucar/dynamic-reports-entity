import { EyeIcon, MailIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { useQuery } from "@tanstack/react-query"
import { ErrorAlert } from "@/shared/components/error-alert"
import { HtmlFrame } from "@/shared/components/html-frame"
import { formatCell } from "@/shared/lib/format"
import { datasetsApi } from "@/features/datasets/api/datasets.api"
import { ResultsTable } from "@/features/datasets/components/results-table"
import { cleanDefinition, isRunnable } from "@/features/datasets/lib/definition"
import { useRunDataset } from "@/features/datasets/mutations/use-run-dataset"
import { useSavedDatasets } from "@/features/datasets/queries/use-saved-datasets"
import type { SavedDatasetResponse } from "@/features/datasets/types/saved-dataset.dto"
import type { DatasetDefinition } from "@/features/datasets/types/dataset.types"
import { templatesApi } from "@/features/templates/api/templates.api"
import { stepsBefore, type GraphModel } from "../lib/flow-graph"
import type { FlowStepDto } from "../types/flow.dto"

const PAGE_SIZE = 50
/** Rows a flow keeps for the mail body by default (flows.preview-rows). */
const MAIL_ROWS = 20

type DatasetStep = Extract<FlowStepDto, { type: "dataset" }>

/** The definition a dataset step runs: its own, or the saved dataset's. */
function definitionOf(step: DatasetStep, saved: SavedDatasetResponse[] | undefined): DatasetDefinition | null {
  if (step.datasetName) return saved?.find((dataset) => dataset.name === step.datasetName)?.definition ?? null
  return step.definition && isRunnable(step.definition) ? cleanDefinition(step.definition) : null
}

/** "Önizle" on a dataset step: runs it now, like the dataset editor does, and shows the first rows. */
export function DatasetStepPreview({ step }: { step: DatasetStep }) {
  const saved = useSavedDatasets()
  const run = useRunDataset()
  const [open, setOpen] = useState(false)
  const definition = definitionOf(step, saved.data)
  const execute = (page: number) => definition && run.mutate({ definition, page, size: PAGE_SIZE })
  return (
    <Dialog open={open} onOpenChange={(next) => { setOpen(next); if (next) execute(0) }}>
      <DialogTrigger asChild>
        <Button variant="outline" className="self-start" disabled={!definition}>
          <EyeIcon data-icon="inline-start" />Önizle
        </Button>
      </DialogTrigger>
      <DialogContent className="flex max-h-[90svh] flex-col sm:max-w-5xl">
        <DialogHeader>
          <DialogTitle>Önizleme: {step.key}</DialogTitle>
          <DialogDescription>
            Şimdi sizin yetkilerinizle çalıştırıldı; akış çalışırken akış sahibinin yetkileri geçerlidir.
          </DialogDescription>
        </DialogHeader>
        <div className="min-h-0 overflow-auto">
          {run.isPending && <Skeleton className="h-64 w-full" />}
          {run.isError && <ErrorAlert error={run.error} title="Veri seti çalıştırılamadı" />}
          {run.data && !run.isPending && <ResultsTable result={run.data} onPage={execute} />}
        </div>
      </DialogContent>
    </Dialog>
  )
}

/**
 * What the template sees in a run, built from the steps before the mail: dataset steps are run now (first rows),
 * approvals get an example decision. Flow and run come from the server's sample.
 */
async function previewModel(model: GraphModel, mailKey: string, flowName: string,
  saved: SavedDatasetResponse[] | undefined): Promise<Record<string, unknown>> {
  const sample = await templatesApi.sampleModel()
  const steps: Record<string, unknown> = {}
  for (const before of stepsBefore(model, mailKey)) {
    if (before.type === "dataset") {
      const definition = definitionOf(before, saved)
      if (!definition) continue
      const rows = before.mailRows ?? MAIL_ROWS
      const result = await datasetsApi.run({ definition, page: 0, size: Math.max(rows, 1) })
      const total = result.total ?? result.count
      steps[before.key] = {
        rowCount: total, hasRows: total > 0, fileName: before.fileName ?? `${before.key}.${before.format ?? "csv"}`,
        columns: result.columns.map((column) => column.label),
        rows: result.rows.slice(0, rows).map((row) => ({ cells: row.map((cell) => formatCell(cell)) })),
        previewTruncated: total > Math.min(rows, result.rows.length),
      }
    } else if (before.type === "approval") {
      steps[before.key] = { decision: "approved", decidedBy: "ornek.kullanici", comment: "Örnek yorum",
        title: before.title, groupName: "Onay grubu" }
    }
  }
  return { ...sample, flow: { ...(sample.flow as object), name: flowName || "Yeni akış" }, steps }
}

/** "Maili önizle" on a mail step: the chosen template filled in with what the earlier steps produce now. */
export function MailStepPreview({ step, model, flowName }: {
  step: Extract<FlowStepDto, { type: "mail" }>
  model: GraphModel
  flowName: string
}) {
  const saved = useSavedDatasets()
  const [open, setOpen] = useState(false)
  const preview = useQuery({
    queryKey: ["flow-mail-preview", step.key, step.templateId, flowName, open],
    enabled: open && !!step.templateId,
    staleTime: 0,
    gcTime: 0,
    queryFn: async () => {
      const template = await templatesApi.get(step.templateId!)
      const data = await previewModel(model, step.key, flowName, saved.data)
      return templatesApi.preview({ subject: template.subject, bodyHtml: template.bodyHtml, model: data })
    },
  })
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" className="self-start" disabled={!step.templateId}>
          <MailIcon data-icon="inline-start" />Maili önizle
        </Button>
      </DialogTrigger>
      <DialogContent className="flex max-h-[90svh] flex-col sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="truncate">{preview.data ? preview.data.subject : "Mail önizlemesi"}</DialogTitle>
          <DialogDescription>
            Önceki veri seti adımları şimdi sizin yetkilerinizle çalıştırıldı; alıcılar: {step.to.join(", ") || "—"}
          </DialogDescription>
        </DialogHeader>
        {preview.isPending && <Skeleton className="h-96 w-full" />}
        {preview.isError && <ErrorAlert error={preview.error} title="Önizleme yapılamadı" />}
        {preview.data && <HtmlFrame title="Mail önizlemesi" html={preview.data.bodyHtml} className="h-[min(40rem,70svh)]" />}
      </DialogContent>
    </Dialog>
  )
}

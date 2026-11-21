import { BarChart3Icon, ChevronDownIcon, ChevronRightIcon, ClipboardListIcon, MailIcon } from "lucide-react"
import { Link } from "react-router"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ErrorAlert } from "@/shared/components/error-alert"
import { HtmlFrame } from "@/shared/components/html-frame"
import { formatCell, formatDateTime } from "@/shared/lib/format"
import { useFileContent } from "../queries/use-file-content"
import type { RunStepDto } from "../types/flow.dto"

const DECISIONS: Record<string, string> = { approved: "Onaylandı", rejected: "Reddedildi", timeout: "Zaman aşımı" }

/** What a step produced, by type: the sent mail, the dataset's first rows, the decision, the HTTP answer. */
export function StepOutput({ step }: { step: RunStepDto }) {
  const [open, setOpen] = useState(false)
  const output = step.output
  if (!output) return null
  const summary = summarize(step.type, output)
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2 text-muted-foreground">
        {summary && <span className="min-w-0">{summary}</span>}
        {step.type === "mail" && typeof output.bodyFileId === "string" && (
          <SentMailDialog fileId={output.bodyFileId} subject={String(output.subject ?? "")}
            recipients={[...(output.to as string[] ?? []), ...(output.cc as string[] ?? [])]}
            sentAt={output.sentAt as string | undefined} />
        )}
        {step.type === "survey" && typeof output.surveyId === "string" && (
          <>
            {typeof output.responseId === "string" && (
              <Button variant="outline" size="sm" asChild>
                <Link to={`/surveys/${output.surveyId}?response=${output.responseId}`}>
                  <ClipboardListIcon data-icon="inline-start" />Yanıtı aç
                </Link>
              </Button>
            )}
            <Button variant="outline" size="sm" asChild>
              <Link to={`/surveys/${output.surveyId}`}><BarChart3Icon data-icon="inline-start" />Anket sonuçları</Link>
            </Button>
          </>
        )}
        <Button variant="ghost" size="sm" className="ml-auto" onClick={() => setOpen(!open)} aria-expanded={open}>
          {open ? <ChevronDownIcon data-icon="inline-start" /> : <ChevronRightIcon data-icon="inline-start" />}Çıktı
        </Button>
      </div>
      {open && <OutputDetail type={step.type} output={output} />}
    </div>
  )
}

function summarize(type: string, output: Record<string, unknown>): string | null {
  switch (type) {
    case "dataset": return `${formatCell(output.rowCount)} satır`
    case "mail": return `“${String(output.subject)}” → ${(output.to as string[] ?? []).join(", ")}`
    case "approval":
      return output.decision ? `${DECISIONS[String(output.decision)] ?? String(output.decision)}`
        + (output.decidedBy ? ` · ${String(output.decidedBy)}` : "")
        + (output.comment ? `: “${String(output.comment)}”` : "") : null
    case "webhook": return `HTTP ${String(output.status)}`
    case "filter": return `${String(output.count)} / ${String(output.total)} eleman`
    default: return null
  }
}

function OutputDetail({ type, output }: { type: string; output: Record<string, unknown> }) {
  if (type === "dataset" && Array.isArray(output.columns) && Array.isArray(output.rows)) {
    const columns = output.columns as string[]
    const rows = output.rows as { cells: unknown[] }[]
    return (
      <div className="flex flex-col gap-1">
        <div className="max-h-72 overflow-auto rounded-md border">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/60 hover:bg-muted/60">
                {columns.map((column, index) => (
                  <TableHead key={index} className="border-r font-semibold last:border-r-0">{column}</TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, rowIndex) => (
                <TableRow key={rowIndex}>
                  {row.cells.map((cell, cellIndex) => (
                    <TableCell key={cellIndex} className="border-r last:border-r-0">{formatCell(cell)}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        <span className="text-xs text-muted-foreground">
          {output.previewTruncated ? `İlk ${rows.length} satır; tamamı dosyada.` : `${rows.length} satır.`}
        </span>
      </div>
    )
  }
  if (type === "approval" && output.decision) {
    return (
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-md border p-3">
        <dt className="text-muted-foreground">Karar</dt>
        <dd><Badge variant="outline">{DECISIONS[String(output.decision)] ?? String(output.decision)}</Badge></dd>
        <dt className="text-muted-foreground">Karar veren</dt><dd>{String(output.decidedBy ?? "—")}</dd>
        <dt className="text-muted-foreground">Yorum</dt><dd className="whitespace-pre-line">{String(output.comment ?? "—")}</dd>
        <dt className="text-muted-foreground">Zaman</dt><dd>{formatDateTime(output.decidedAt as string | null)}</dd>
      </dl>
    )
  }
  if (type === "webhook") {
    return (
      <div className="flex flex-col gap-1">
        <span className="text-xs text-muted-foreground">Cevap (HTTP {String(output.status)})</span>
        <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs">{pretty(output.body)}</pre>
      </div>
    )
  }
  return <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs">{pretty(output)}</pre>
}

function pretty(value: unknown): string {
  return typeof value === "string" ? value : JSON.stringify(value, null, 2)
}

/** The mail as recipients got it (kept as long as the run's other files). */
function SentMailDialog({ fileId, subject, recipients, sentAt }: {
  fileId: string
  subject: string
  recipients: string[]
  sentAt?: string
}) {
  const [open, setOpen] = useState(false)
  const content = useFileContent(open ? fileId : null)
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm"><MailIcon data-icon="inline-start" />Giden mail</Button>
      </DialogTrigger>
      <DialogContent className="flex max-h-[90svh] flex-col sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="truncate">{subject}</DialogTitle>
          <DialogDescription>
            {recipients.join(", ")}{sentAt ? ` · ${formatDateTime(sentAt)}` : ""}
          </DialogDescription>
        </DialogHeader>
        {content.isPending && <Skeleton className="h-96 w-full" />}
        {content.isError && <ErrorAlert error={content.error} title="Mail gösterilemiyor" />}
        {content.data && <HtmlFrame title={`Giden mail: ${subject}`} html={content.data.content}
          className="h-[min(40rem,70svh)]" />}
      </DialogContent>
    </Dialog>
  )
}

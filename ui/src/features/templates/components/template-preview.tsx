import { useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { ErrorAlert } from "@/shared/components/error-alert"
import { HtmlFrame } from "@/shared/components/html-frame"
import { useTemplatePreview } from "../mutations/use-template-preview"

/** Renders the draft with sample data (server side, like the real mail) in a sandboxed frame: no scripts run. */
export function TemplatePreview({ subject, bodyHtml, model, sampleLabel }: {
  subject: string
  bodyHtml: string
  /** What the template sees; null = the server's sample data. */
  model?: Record<string, unknown> | null
  sampleLabel?: string
}) {
  const preview = useTemplatePreview()
  const { mutate } = preview
  useEffect(() => {
    if (!subject.trim() || !bodyHtml.trim()) return
    const timer = setTimeout(() => mutate({ subject, bodyHtml, model: model ?? null }), 400)
    return () => clearTimeout(timer)
  }, [subject, bodyHtml, model, mutate])

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <CardTitle>Önizleme</CardTitle>
        <CardDescription>
          {preview.data ? `Konu: ${preview.data.subject}` : "Gönderilecek hâliyle"} · {sampleLabel ?? "örnek verilerle"}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1 flex-col gap-3">
        {preview.isError && <ErrorAlert error={preview.error} title="Şablon hatalı" />}
        {!preview.data && !preview.isError && <Skeleton className="h-64 w-full" />}
        {preview.data && (
          <HtmlFrame title="Mail önizlemesi" html={preview.data.bodyHtml}
            className="h-[min(46rem,calc(100svh-14rem))] flex-1" />
        )}
      </CardContent>
    </Card>
  )
}

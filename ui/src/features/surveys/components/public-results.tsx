import { BarChart3Icon } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import { useSurveyResults } from "../queries/use-surveys"

/** The counted answers respondents may see (questions marked "show in results"); nothing when not allowed. */
export function PublicResults({ surveyId, enabled }: { surveyId: string; enabled: boolean }) {
  const results = useSurveyResults(surveyId, enabled)
  if (!enabled || results.isError) return null
  if (results.isPending) return <Skeleton className="h-40 w-full" />
  const data = results.data
  if (data.fields.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><BarChart3Icon />Sonuçlar</CardTitle>
        <CardDescription>{data.total} yanıt</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {data.fields.map((field) => {
          const best = Math.max(0, ...field.counts.map((count) => count.count))
          return (
            <div key={field.fieldId} className="flex flex-col gap-2">
              <p className="text-sm font-medium">{field.label}</p>
              {field.average !== null && (
                <p className="text-xs text-muted-foreground">Ortalama {field.average.toLocaleString("tr-TR", { maximumFractionDigits: 1 })}</p>
              )}
              {field.counts.map((count) => (
                <div key={count.key} className="flex flex-col gap-1">
                  <div className="flex justify-between gap-2 text-sm">
                    <span className={count.count === best && best > 0 ? "font-medium" : undefined}>{count.label}</span>
                    <span className="text-muted-foreground tabular-nums">
                      %{Math.round((100 * count.count) / Math.max(1, field.answered))} · {count.count}
                    </span>
                  </div>
                  <Progress value={(100 * count.count) / Math.max(1, field.answered)} />
                </div>
              ))}
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}

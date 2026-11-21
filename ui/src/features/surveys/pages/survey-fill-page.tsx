import { ArrowLeftIcon, CircleCheckBigIcon, LockIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { Link, useParams, useSearchParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { ApiError } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { formatDateTime } from "@/shared/lib/format"
import { PublicResults } from "../components/public-results"
import { SurveyForm } from "../components/survey-form"
import { useSubmitResponse } from "../mutations/use-survey-mutations"
import { useSurveyForm } from "../queries/use-surveys"
import type { Answers, ClosedReason } from "../types/survey.dto"

const CLOSED: Record<ClosedReason, { title: string; description: string }> = {
  draft: { title: "Bu anket henüz yayınlanmadı", description: "Taslak; yalnızca sahibi deneyebilir." },
  closed: { title: "Bu anket kapandı", description: "Artık yanıt kabul etmiyor." },
  deadline_passed: { title: "Son gün geçti", description: "Bu anket artık yanıt kabul etmiyor." },
  answered: { title: "Bu anketi zaten yanıtladınız", description: "Her kişi bir kez yanıtlayabilir. Teşekkürler!" },
  invitation_invalid: { title: "Bağlantı geçersiz", description: "Bu bağlantı bu ankete ait değil; size gelen e-postadaki bağlantıyı kullanın." },
  invitation_used: { title: "Bu bağlantı kullanıldı", description: "Bağlantı tek kullanımlıktır ve bu anket zaten yanıtlandı." },
  invitation_expired: { title: "Bağlantının süresi doldu", description: "Artık bu yanıt beklenmiyor; gerekiyorsa göndereninden yeni bağlantı isteyin." },
}

/** `/s/:surveyId`: a platform user fills in a survey, on a page of its own (a link can be sent by mail). */
export function SurveyFillPage() {
  const id = Number(useParams().surveyId)
  const invitation = useSearchParams()[0].get("invitation")
  const form = useSurveyForm(id, invitation)
  const submit = useSubmitResponse(id, invitation)
  const [answers, setAnswers] = useState<Answers>({})
  const [done, setDone] = useState<string | null>(null)

  const errors: Record<string, string> = {}
  const ruleMessages: string[] = []
  if (submit.error instanceof ApiError) {
    for (const problem of submit.error.errors) {
      if (problem.field.startsWith("answers.")) errors[problem.field.slice("answers.".length)] = problem.message
      else if (problem.field.startsWith("rules.")) ruleMessages.push(problem.message)
    }
  }

  const send = (event: FormEvent) => {
    event.preventDefault()
    submit.mutate(answers, {
      onSuccess: (result) => {
        setDone(result.successMessage || "Teşekkürler, yanıtınız alındı.")
        window.scrollTo({ top: 0, behavior: "smooth" })
      },
    })
  }

  return (
    <div className="min-h-svh bg-muted/40 px-4 py-8 sm:py-12">
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-4">
        <Button variant="ghost" size="sm" className="self-start" asChild>
          <Link to="/surveys?tab=open"><ArrowLeftIcon data-icon="inline-start" />Anketler</Link>
        </Button>
        {form.isPending && <Skeleton className="h-96 w-full" />}
        {form.isError && (form.error instanceof ApiError && form.error.status === 404 ? (
          <Empty className="border bg-card">
            <EmptyHeader>
              <EmptyMedia variant="icon"><LockIcon /></EmptyMedia>
              <EmptyTitle>Anket bulunamadı</EmptyTitle>
              <EmptyDescription>Bağlantı yanlış olabilir ya da anket henüz yayınlanmamış.</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : <ErrorAlert error={form.error} title="Anket açılamadı" />)}
        {form.data && done !== null && (
          <Empty className="border bg-card">
            <EmptyHeader>
              <EmptyMedia variant="icon"><CircleCheckBigIcon /></EmptyMedia>
              <EmptyTitle>{form.data.title}</EmptyTitle>
              <EmptyDescription>{done}</EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              {!form.data.oneResponsePerUser && !invitation && (
                <Button variant="outline" onClick={() => { setAnswers({}); setDone(null); submit.reset() }}>Yeni yanıt</Button>
              )}
            </EmptyContent>
          </Empty>
        )}
        {form.data && done === null && (
          <Card>
            <form onSubmit={send} className="flex flex-col gap-6" noValidate>
              <CardHeader>
                <div className="flex flex-wrap gap-2">
                  {form.data.preview && <Badge variant="secondary">Taslak önizleme</Badge>}
                  {form.data.anonymous && <Badge variant="outline">Anonim</Badge>}
                  {form.data.closesAt && <Badge variant="outline">Son gün: {formatDateTime(form.data.closesAt)}</Badge>}
                </div>
                <CardTitle className="text-xl">{form.data.title}</CardTitle>
                {form.data.description && <CardDescription className="whitespace-pre-line">{form.data.description}</CardDescription>}
              </CardHeader>
              <CardContent className="flex flex-col gap-6">
                {!form.data.open && form.data.closedReason && (
                  <Alert>
                    <LockIcon />
                    <AlertTitle>{CLOSED[form.data.closedReason].title}</AlertTitle>
                    <AlertDescription>{CLOSED[form.data.closedReason].description}</AlertDescription>
                  </Alert>
                )}
                <SurveyForm definition={form.data.definition} answers={answers} onChange={setAnswers} errors={errors}
                  disabled={!form.data.open || submit.isPending} />
              </CardContent>
              <CardFooter className="flex-col items-stretch gap-3">
                {ruleMessages.length > 0 && (
                  <Alert variant="destructive">
                    <AlertTitle>Yanıt gönderilemedi</AlertTitle>
                    <AlertDescription>
                      <ul className="list-disc pl-4">{ruleMessages.map((message) => <li key={message}>{message}</li>)}</ul>
                    </AlertDescription>
                  </Alert>
                )}
                {submit.isError && ruleMessages.length === 0 && Object.keys(errors).length === 0 && (
                  <ErrorAlert error={submit.error} title="Yanıt gönderilemedi" />
                )}
                {Object.keys(errors).length > 0 && (
                  <p className="text-sm text-destructive">İşaretli soruları kontrol edin.</p>
                )}
                <Button type="submit" size="lg" className="self-end" disabled={!form.data.open || submit.isPending}>
                  {submit.isPending && <Spinner data-icon="inline-start" />}
                  {form.data.definition.submitLabel || "Gönder"}
                </Button>
              </CardFooter>
            </form>
          </Card>
        )}
        {form.data && (done !== null || form.data.closedReason === "answered" || form.data.resultsVisibility === "everyone") && (
          <PublicResults surveyId={id} enabled={form.data.hasResults && form.data.resultsVisibility !== "owner"} />
        )}
      </div>
    </div>
  )
}

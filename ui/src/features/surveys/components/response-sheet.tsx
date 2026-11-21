import { MessageSquareIcon, Trash2Icon, WorkflowIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { errorMessage } from "@/shared/api/http-client"
import { DatePicker } from "@/shared/components/date-picker"
import { ErrorAlert } from "@/shared/components/error-alert"
import { formatDateTime, shortId } from "@/shared/lib/format"
import { useFollowUp, useResponseNote } from "../mutations/use-survey-mutations"
import { useResponseNotes, useSurveyResponse } from "../queries/use-surveys"
import type { FollowStatus, FollowUpChange, Priority, ResponseRow, SurveyResponse } from "../types/survey.dto"
import { FOLLOW_LABELS, FollowStatusBadge, PRIORITY_LABELS } from "./survey-status-badge"

const NONE = "__none__"

/**
 * One response: the answers as people read them, the owner's follow-up (saved as it changes) and the notes. The
 * follow-up never reaches the respondent.
 */
export function ResponseSheet({ survey, responseId, onClose }: {
  survey: SurveyResponse
  responseId: string | null
  onClose: () => void
}) {
  const response = useSurveyResponse(survey.id, responseId)
  const data = response.data
  const questions = survey.definition.fields.filter((field) => field.type !== "section")
  return (
    <Sheet open={responseId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto data-[side=right]:sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2"><span title={responseId ?? undefined}>Yanıt #{shortId(responseId)}</span> {data && <FollowStatusBadge status={data.status} />}</SheetTitle>
          <SheetDescription>
            {data ? <>
              {formatDateTime(data.submittedAt)} · {data.respondent ?? (survey.anonymous ? "anonim" : "—")}
              {data.source?.startsWith("flow-step:") && <Badge variant="secondary" className="ml-2"><WorkflowIcon data-icon="inline-start" />Akış bekletti</Badge>}
            </> : "Yükleniyor…"}
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-6 px-4 pb-6">
          {response.isPending && <Skeleton className="h-64 w-full" />}
          {response.isError && <ErrorAlert error={response.error} />}
          {data && (
            <>
              <FollowUpEditor key={`${data.id}-${data.updatedAt}`} survey={survey} row={data} />
              <Separator />
              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Cevaplar</h3>
                <dl className="flex flex-col divide-y rounded-lg border">
                  {questions.map((field) => (
                    <div key={field.id} className="grid gap-1 px-3 py-2 sm:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] sm:gap-4">
                      <dt className="text-sm text-muted-foreground">{field.label}</dt>
                      <dd className="text-sm break-words whitespace-pre-line">{data.display[field.id] ?? "—"}</dd>
                    </div>
                  ))}
                </dl>
              </section>
              <Separator />
              <Notes surveyId={survey.id} responseId={data.id} />
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function FollowUpEditor({ survey, row }: { survey: SurveyResponse; row: ResponseRow }) {
  const tracking = survey.settings.tracking
  const followUp = useFollowUp(survey.id)
  const [assignee, setAssignee] = useState(row.assignee ?? "")
  const [values, setValues] = useState<Record<string, string>>(
    Object.fromEntries(Object.entries(row.tracking).map(([key, value]) => [key, String(value)])))
  const save = (change: FollowUpChange, message = "Takip güncellendi") =>
    followUp.mutate({ responseId: row.id, change }, {
      onSuccess: () => toast.success(message),
      onError: (error) => toast.error("Kaydedilemedi", { description: errorMessage(error) }),
    })
  const anything = tracking.status || tracking.priority || tracking.followUpDate || tracking.assignee || tracking.fields.length > 0
  if (!anything) {
    return <p className="text-sm text-muted-foreground">Bu ankette yanıt takibi kapalı (anket ayarlarından açılabilir).</p>
  }
  return (
    <section className="flex flex-col gap-3">
      <h3 className="flex items-center gap-2 text-sm font-medium">
        Takip {followUp.isPending && <Spinner className="size-3.5" />}
      </h3>
      <FieldGroup className="gap-4">
        {tracking.status && (
          <Field>
            <FieldLabel>Durum</FieldLabel>
            <ToggleGroup type="single" variant="outline" size="sm" className="flex-wrap justify-start" value={row.status}
              onValueChange={(status) => status && save({ status: status as FollowStatus })}>
              {(Object.keys(FOLLOW_LABELS) as FollowStatus[]).map((status) => (
                <ToggleGroupItem key={status} value={status}>{FOLLOW_LABELS[status]}</ToggleGroupItem>
              ))}
            </ToggleGroup>
          </Field>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          {tracking.priority && (
            <Field>
              <FieldLabel>Öncelik</FieldLabel>
              <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={row.priority ?? ""}
                onValueChange={(priority) => save({ priority: (priority || null) as Priority | null })}>
                {(Object.keys(PRIORITY_LABELS) as Priority[]).map((priority) => (
                  <ToggleGroupItem key={priority} value={priority}>{PRIORITY_LABELS[priority]}</ToggleGroupItem>
                ))}
              </ToggleGroup>
            </Field>
          )}
          {tracking.followUpDate && (
            <Field>
              <FieldLabel>Takip tarihi</FieldLabel>
              <DatePicker value={row.followUpAt ?? undefined} placeholder="Tarih yok"
                onChange={(value) => save({ followUpAt: value ?? null })} />
            </Field>
          )}
        </div>
        {tracking.assignee && (
          <Field>
            <FieldLabel htmlFor="assignee">Atanan</FieldLabel>
            <div className="flex gap-2">
              <Input id="assignee" value={assignee} maxLength={100} placeholder="Kullanıcı adı, ör. bob"
                onChange={(e) => setAssignee(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") save({ assignee: assignee || null }) }} />
              <Button variant="outline" disabled={assignee === (row.assignee ?? "")}
                onClick={() => save({ assignee: assignee || null })}>Kaydet</Button>
            </div>
          </Field>
        )}
        {tracking.fields.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2">
            {tracking.fields.map((field) => (
              <Field key={field.id}>
                <FieldLabel htmlFor={`tracking-${field.id}`}>{field.label}</FieldLabel>
                {field.type === "choice" ? (
                  <Select value={values[field.id] ?? NONE}
                    onValueChange={(value) => {
                      const next = { ...values, [field.id]: value === NONE ? "" : value }
                      setValues(next)
                      save({ tracking: clean(next) })
                    }}>
                    <SelectTrigger id={`tracking-${field.id}`} className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value={NONE}>—</SelectItem>
                        {field.options?.map((option) => <SelectItem key={option.id} value={option.id}>{option.label}</SelectItem>)}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                ) : field.type === "date" ? (
                  <DatePicker value={values[field.id] || undefined} onChange={(value) => {
                    const next = { ...values, [field.id]: value ?? "" }
                    setValues(next)
                    save({ tracking: clean(next) })
                  }} />
                ) : (
                  <Input id={`tracking-${field.id}`} type={field.type === "number" ? "number" : "text"}
                    value={values[field.id] ?? ""} onChange={(e) => setValues({ ...values, [field.id]: e.target.value })}
                    onBlur={() => (values[field.id] ?? "") !== String(row.tracking[field.id] ?? "")
                      && save({ tracking: clean(values) })} />
                )}
              </Field>
            ))}
          </div>
        )}
        {row.updatedBy && (
          <p className="text-xs text-muted-foreground">Son değişiklik: {row.updatedBy} · {formatDateTime(row.updatedAt)}</p>
        )}
      </FieldGroup>
    </section>
  )
}

const clean = (values: Record<string, string>) =>
  Object.fromEntries(Object.entries(values).filter(([, value]) => value.trim() !== ""))

function Notes({ surveyId, responseId }: { surveyId: string; responseId: string }) {
  const notes = useResponseNotes(surveyId, responseId)
  const { add, remove } = useResponseNote(surveyId, responseId)
  const [text, setText] = useState("")
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!text.trim()) return
    add.mutate(text, {
      onSuccess: () => setText(""),
      onError: (error) => toast.error("Not eklenemedi", { description: errorMessage(error) }),
    })
  }
  return (
    <section className="flex flex-col gap-3">
      <h3 className="flex items-center gap-2 text-sm font-medium"><MessageSquareIcon className="size-4" />Notlar</h3>
      {notes.data?.length === 0 && <p className="text-sm text-muted-foreground">Henüz not yok.</p>}
      <ul className="flex flex-col gap-2">
        {notes.data?.map((note) => (
          <li key={note.id} className="group/note flex gap-2 rounded-lg bg-muted/50 p-3">
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-xs text-muted-foreground">{note.author} · {formatDateTime(note.createdAt)}</span>
              <p className="text-sm break-words whitespace-pre-line">{note.text}</p>
            </div>
            <Button variant="ghost" size="icon-sm" aria-label="Notu sil" className="opacity-0 group-hover/note:opacity-100 focus-visible:opacity-100"
              onClick={() => remove.mutate(note.id)}><Trash2Icon /></Button>
          </li>
        ))}
      </ul>
      <form onSubmit={submit} className="flex flex-col gap-2">
        <Textarea value={text} maxLength={2000} placeholder="Ne yapıldı, kiminle görüşüldü…" className="min-h-16"
          onChange={(e) => setText(e.target.value)} />
        <Button type="submit" size="sm" className="self-end" disabled={!text.trim() || add.isPending}>
          {add.isPending && <Spinner data-icon="inline-start" />}Not ekle
        </Button>
      </form>
    </section>
  )
}

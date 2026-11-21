import {
  BarChart3Icon, CircleCheckIcon, EyeIcon, LayoutTemplateIcon, PlusIcon, SparklesIcon, Undo2Icon,
} from "lucide-react"
import { useState, type ReactNode } from "react"
import { Link, useNavigate, useParams } from "react-router"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { ApiError } from "@/shared/api/http-client"
import { DatePicker } from "@/shared/components/date-picker"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { useAskParam } from "@/features/assistant/lib/use-ask-param"
import { AssistantChat } from "@/features/assistant/components/assistant-chat"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import { surveysApi } from "../api/surveys.api"
import { FieldEditor } from "../components/field-editor"
import { RulesCard } from "../components/rules-card"
import { DEFAULT_SETTINGS, TrackingSettings } from "../components/tracking-settings"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { ChevronDownIcon, SettingsIcon } from "lucide-react"
import { SurveyForm } from "../components/survey-form"
import { STATUS_LABELS } from "../components/survey-status-badge"
import { EMPTY_DEFINITION, FIELD_TYPES, freeId, newField } from "../lib/field-types"
import { STARTERS } from "../lib/starters"
import { useSaveSurvey } from "../mutations/use-survey-mutations"
import { useSurvey } from "../queries/use-surveys"
import type { Answers, SurveyDraftDto, SurveyField, SurveyRequest, SurveyResponse, SurveyStatus } from "../types/survey.dto"

const EXAMPLES = [
  "Yeni başlayan çalışanlar için ilk ay deneyimi anketi",
  "Satın alma talep formu: tutar 50.000 TL'yi geçerse gerekçe zorunlu olsun",
  "Ofise dönüş tercihleri: haftada kaç gün, hangi günler, ulaşım",
]

const NEW_SURVEY: SurveyRequest = {
  title: "", description: "", status: "draft", definition: EMPTY_DEFINITION, anonymous: false,
  oneResponsePerUser: true, closesAt: null, settings: DEFAULT_SETTINGS,
}

/** `/surveys/new` and `/surveys/:surveyId/edit`. */
export function SurveyBuilderPage() {
  const { surveyId } = useParams()
  const id = surveyId ? Number(surveyId) : null
  const survey = useSurvey(id)
  if (id !== null && survey.isPending) return <Skeleton className="h-96 w-full" />
  if (id !== null && survey.isError) return <ErrorAlert error={survey.error} title="Anket yüklenemedi" />
  return <Builder key={survey.data?.id ?? "new"} saved={survey.data ?? null} />
}

function toRequest(saved: SurveyResponse): SurveyRequest {
  return {
    title: saved.title, description: saved.description, status: saved.status, definition: saved.definition,
    anonymous: saved.anonymous, oneResponsePerUser: saved.oneResponsePerUser, closesAt: saved.closesAt,
    settings: saved.settings ?? DEFAULT_SETTINGS,
  }
}

interface Problems {
  fields: Record<number, string[]>
  rules: Record<number, string[]>
  other: string[]
}

const NO_PROBLEMS: Problems = { fields: {}, rules: {}, other: [] }

/** The server's problems placed on the question or rule they belong to. */
function placeProblems(error: unknown): Problems {
  if (!(error instanceof ApiError) || error.errors.length === 0) return NO_PROBLEMS
  const problems: Problems = { fields: {}, rules: {}, other: [] }
  for (const problem of error.errors) {
    const match = /^definition\.(fields|rules)\[(\d+)\](?:\.(.+))?$/.exec(problem.field)
    if (match) {
      const bucket = problems[match[1] as "fields" | "rules"]
      const index = Number(match[2])
      bucket[index] = [...(bucket[index] ?? []), `${match[3] ? `${match[3]}: ` : ""}${problem.message}`]
    } else {
      problems.other.push(`${problem.field}: ${problem.message}`)
    }
  }
  return problems
}

function Builder({ saved }: { saved: SurveyResponse | null }) {
  const navigate = useNavigate()
  const save = useSaveSurvey()
  const assistant = useAssistantSettings()
  const [form, setForm] = useState<SurveyRequest>(saved ? toRequest(saved) : NEW_SURVEY)
  const [history, setHistory] = useState<SurveyRequest[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [problems, setProblems] = useState<Problems>(NO_PROBLEMS)
  const [answers, setAnswers] = useState<Answers>({})
  const [tryResult, setTryResult] = useState<{ valid: boolean; messages: string[]; errors: Record<string, string> } | null>(null)
  const ask = useAskParam()
  const [tab, setTab] = useState(assistant.data?.enabled && (!saved || ask) ? "assistant" : "preview")

  const fields = form.definition.fields
  const set = (change: Partial<SurveyRequest>) => setForm((current) => ({ ...current, ...change }))
  const setFields = (next: SurveyField[]) => set({ definition: { ...form.definition, fields: next } })
  /** A bigger change (starter, assistant) that can be undone in one step. */
  const replace = (next: SurveyRequest) => {
    setHistory((list) => [...list.slice(-19), form])
    setForm(next)
    setProblems(NO_PROBLEMS)
  }
  const undo = () => {
    const previous = history.at(-1)
    if (!previous) return
    setHistory((list) => list.slice(0, -1))
    setForm(previous)
  }

  const add = (type: SurveyField["type"]) => {
    const field = newField(type, fields)
    const at = selected ? fields.findIndex((item) => item.id === selected) + 1 : fields.length
    setFields([...fields.slice(0, at), field, ...fields.slice(at)])
    setSelected(field.id)
  }
  const move = (from: number, to: number) => {
    const next = [...fields]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item)
    setFields(next)
  }

  const submit = (status?: SurveyStatus) => {
    const request = status ? { ...form, status } : form
    if (!request.title.trim()) {
      toast.error("Anketin bir başlığı olmalı")
      return
    }
    save.mutate({ id: saved?.id, version: saved?.version, request }, {
      onSuccess: (result) => {
        setProblems(NO_PROBLEMS)
        setForm(toRequest(result))
        toast.success(status === "published" ? "Anket yayınlandı" : "Anket kaydedildi")
        if (!saved) navigate(`/surveys/${result.id}/edit`, { replace: true })
      },
      onError: (error) => setProblems(placeProblems(error)),
    })
  }

  const tryIt = async () => {
    try {
      const result = await surveysApi.check(form.definition, answers)
      const errors: Record<string, string> = {}
      const messages: string[] = []
      for (const problem of result.problems) {
        if (problem.field.startsWith("answers.")) errors[problem.field.slice("answers.".length)] = problem.message
        else messages.push(problem.message)
      }
      setTryResult({ valid: result.valid, messages, errors })
    } catch (error) {
      setTryResult({ valid: false, messages: [error instanceof Error ? error.message : "Denenemedi"], errors: {} })
    }
  }

  const applyDraft = (draft: SurveyDraftDto) => {
    replace({ ...form, title: draft.title || form.title, description: draft.description ?? form.description,
      definition: { ...EMPTY_DEFINITION, ...draft.definition, rules: draft.definition.rules ?? [] } })
    setAnswers({})
    setTryResult(null)
    toast.success("Asistanın önerisi uygulandı", { description: "Geri al ile önceki hâline dönebilirsiniz." })
  }

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title={saved ? saved.title : "Yeni anket"}
        description="Soruları ekleyin, koşullu soruları ve kuralları tanımlayın; sağda önizleyip deneyin."
        actions={(
          <>
            {history.length > 0 && (
              <Button variant="ghost" onClick={undo}><Undo2Icon data-icon="inline-start" />Geri al</Button>
            )}
            {saved && (
              <Button variant="outline" asChild>
                <Link to={`/surveys/${saved.id}`}><BarChart3Icon data-icon="inline-start" />Sonuçlar</Link>
              </Button>
            )}
            {saved && (
              <Button variant="outline" asChild>
                <Link to={`/s/${saved.id}`} target="_blank"><EyeIcon data-icon="inline-start" />Formu aç</Link>
              </Button>
            )}
            <Button variant="outline" disabled={save.isPending} onClick={() => submit()}>
              {save.isPending && <Spinner data-icon="inline-start" />}Kaydet
            </Button>
            {form.status !== "published" && (
              <Button disabled={save.isPending} onClick={() => submit("published")}>
                <CircleCheckIcon data-icon="inline-start" />Yayınla
              </Button>
            )}
          </>
        )} />
      {save.isError && problems.other.length === 0 && Object.keys(problems.fields).length === 0
        && Object.keys(problems.rules).length === 0 && <ErrorAlert error={save.error} title="Kaydedilemedi" />}
      {(problems.other.length > 0 || Object.keys(problems.fields).length > 0 || Object.keys(problems.rules).length > 0) && (
        <Alert variant="destructive">
          <AlertTitle>Kaydedilemedi: düzeltilmesi gerekenler işaretlendi</AlertTitle>
          {problems.other.length > 0 && <AlertDescription>{problems.other.join(" · ")}</AlertDescription>}
        </Alert>
      )}

      <div className="grid min-w-0 items-start gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,30rem)]">
        <div className="flex min-w-0 flex-col gap-4">
          <SettingsCard form={form} onChange={set} />

          {fields.length === 0 ? (
            <Empty className="border">
              <EmptyHeader>
                <EmptyMedia variant="icon"><LayoutTemplateIcon /></EmptyMedia>
                <EmptyTitle>Boş bir anket</EmptyTitle>
                <EmptyDescription>
                  Hazır bir şablonla başlayın, asistana anlatın ya da soruları tek tek ekleyin.
                </EmptyDescription>
              </EmptyHeader>
              <EmptyContent className="max-w-xl">
                <div className="grid w-full gap-2 sm:grid-cols-3">
                  {STARTERS.map((starter) => (
                    <Button key={starter.title} variant="outline" className="h-auto flex-col items-start gap-1 p-3 text-left whitespace-normal"
                      onClick={() => replace({ ...starter, title: form.title || starter.title })}>
                      <span className="font-medium">{starter.title}</span>
                      <span className="text-xs font-normal text-muted-foreground">{starter.summary}</span>
                    </Button>
                  ))}
                </div>
                <AddQuestionMenu onAdd={add} />
              </EmptyContent>
            </Empty>
          ) : (
            <>
              {fields.map((field, index) => (
                <FieldEditor key={`${index}-${field.type}`} field={field} index={index} count={fields.length}
                  earlier={fields.slice(0, index)} problems={problems.fields[index] ?? []}
                  selected={selected === field.id} onSelect={() => setSelected(field.id)}
                  onChange={(next) => {
                    setFields(fields.map((item, i) => (i === index ? next : item)))
                    if (selected === field.id) setSelected(next.id)
                  }}
                  onMove={(to) => move(index, to)}
                  onDuplicate={() => {
                    const copy = { ...field, id: freeId(fields.map((item) => item.id), field.type === "section" ? "s" : "q") }
                    setFields([...fields.slice(0, index + 1), copy, ...fields.slice(index + 1)])
                    setSelected(copy.id)
                  }}
                  onRemove={() => setFields(fields.filter((_, i) => i !== index))} />
              ))}
              <AddQuestionMenu onAdd={add} />
            </>
          )}

          <RulesCard rules={form.definition.rules} fields={fields} problems={problems.rules}
            onChange={(rules) => set({ definition: { ...form.definition, rules } })} />
        </div>

        <div className="flex min-w-0 flex-col gap-3 xl:sticky xl:top-20">
          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-full">
              <TabsTrigger value="preview"><EyeIcon />Önizleme</TabsTrigger>
              {assistant.data?.enabled && <TabsTrigger value="assistant"><SparklesIcon />Asistan</TabsTrigger>}
            </TabsList>
            <TabsContent value="preview">
              <Card>
                <CardHeader>
                  <CardTitle>{form.title || "Başlıksız anket"}</CardTitle>
                  {form.description && <CardDescription>{form.description}</CardDescription>}
                </CardHeader>
                <CardContent className="max-h-[calc(100svh-18rem)] overflow-y-auto">
                  <SurveyForm definition={form.definition} answers={answers} idPrefix="preview"
                    errors={tryResult?.errors} onChange={(next) => { setAnswers(next); setTryResult(null) }} />
                </CardContent>
                <CardFooter className="flex-col items-stretch gap-3">
                  {tryResult && (
                    <Alert variant={tryResult.valid ? "default" : "destructive"}>
                      <AlertTitle>{tryResult.valid ? "Bu yanıt kabul edilirdi" : "Bu yanıt reddedilirdi"}</AlertTitle>
                      {tryResult.messages.length > 0 && <AlertDescription>{tryResult.messages.join(" · ")}</AlertDescription>}
                    </Alert>
                  )}
                  <div className="flex gap-2">
                    <Button variant="outline" className="flex-1" onClick={() => { setAnswers({}); setTryResult(null) }}>Temizle</Button>
                    <Button className="flex-1" onClick={tryIt} disabled={fields.length === 0}>
                      {form.definition.submitLabel || "Gönder"} (dene)
                    </Button>
                  </div>
                  <FieldDescription>Deneme kaydedilmez; kurallar sunucuda gerçek gönderimdeki gibi çalışır.</FieldDescription>
                </CardFooter>
              </Card>
            </TabsContent>
            {assistant.data?.enabled && (
              <TabsContent value="assistant" forceMount className="data-[state=inactive]:hidden">
                <AssistantChat kind="survey" title="Anket asistanı" model={assistant.data.model} initialInput={ask}
                  description="Kimin, ne için dolduracağını yazın; asistan soruları, koşulları ve kuralları hazırlar. Yanıtları görmez."
                  examples={EXAMPLES} busyLabel="Anket hazırlanıyor…"
                  placeholder={{ empty: "Nasıl bir anket istiyorsunuz?", change: "Ankette neyi değiştireyim?" }}
                  current={fields.length ? { title: form.title, description: form.description, definition: form.definition } : null}
                  applicable={(reply) => reply.draft !== null && reply.draft !== undefined}
                  onApply={(reply) => applyDraft(reply.draft as SurveyDraftDto)} className="h-[min(40rem,calc(100svh-12rem))]" />
              </TabsContent>
            )}
          </Tabs>
        </div>
      </div>
    </div>
  )
}

function AddQuestionMenu({ onAdd }: { onAdd: (type: SurveyField["type"]) => void }) {
  const groups = ["Metin", "Seçim", "Ölçek", "Diğer"] as const
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" className="self-start"><PlusIcon data-icon="inline-start" />Soru ekle</Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-56">
        {groups.map((group, index) => (
          <DropdownMenuGroup key={group}>
            {index > 0 && <DropdownMenuSeparator />}
            <DropdownMenuLabel>{group}</DropdownMenuLabel>
            {FIELD_TYPES.filter((type) => type.group === group).map((type) => (
              <DropdownMenuItem key={type.type} onSelect={() => onAdd(type.type)}><type.icon />{type.label}</DropdownMenuItem>
            ))}
          </DropdownMenuGroup>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function SettingsCard({ form, onChange }: { form: SurveyRequest; onChange: (change: Partial<SurveyRequest>) => void }) {
  return (
    <Card>
      <CardContent>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="survey-title">Başlık</FieldLabel>
            <Input id="survey-title" value={form.title} maxLength={200} placeholder="Örn. Çalışan memnuniyeti 2026"
              onChange={(e) => onChange({ title: e.target.value })} />
          </Field>
          <Field>
            <FieldLabel htmlFor="survey-description">Açıklama</FieldLabel>
            <Textarea id="survey-description" value={form.description ?? ""} maxLength={2000} className="min-h-16"
              placeholder="Neden soruyorsunuz, ne kadar sürer?" onChange={(e) => onChange({ description: e.target.value })} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel>Durum</FieldLabel>
              <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={form.status}
                onValueChange={(status) => status && onChange({ status: status as SurveyStatus })}>
                {(Object.keys(STATUS_LABELS) as SurveyStatus[]).map((status) => (
                  <ToggleGroupItem key={status} value={status}>{STATUS_LABELS[status]}</ToggleGroupItem>
                ))}
              </ToggleGroup>
              <FieldDescription>Yalnızca yayındaki anketler yanıt alır.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel>Son gün (isteğe bağlı)</FieldLabel>
              <DatePicker value={form.closesAt?.slice(0, 10)} placeholder="Süresiz"
                onChange={(value) => onChange({ closesAt: value ? `${value}T23:59:59+03:00` : null })} />
              <FieldDescription>Bu günün sonunda yanıt almayı bırakır.</FieldDescription>
            </Field>
          </div>
          <div className="flex flex-wrap gap-x-8 gap-y-3">
            <Setting id="survey-one" label="Kişi başı bir yanıt" checked={form.oneResponsePerUser}
              onChange={(oneResponsePerUser) => onChange({ oneResponsePerUser })} />
            <Setting id="survey-anonymous" label="Anonim (yanıtlarda kim olduğu saklanmaz)" checked={form.anonymous}
              onChange={(anonymous) => onChange({ anonymous })} />
          </div>
          <Collapsible>
            <CollapsibleTrigger asChild>
              <Button variant="ghost" size="sm" className="group/more -ml-2 self-start">
                <SettingsIcon data-icon="inline-start" />Sonuçlar ve yanıt takibi
                <ChevronDownIcon data-icon="inline-end" className="transition-transform group-data-[state=open]/more:rotate-180" />
              </Button>
            </CollapsibleTrigger>
            <CollapsibleContent className="pt-3">
              <TrackingSettings settings={form.settings ?? DEFAULT_SETTINGS} onChange={(settings) => onChange({ settings })} />
            </CollapsibleContent>
          </Collapsible>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

function Setting({ id, label, checked, onChange }: {
  id: string
  label: ReactNode
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <Field orientation="horizontal" className="w-auto">
      <Switch id={id} checked={checked} onCheckedChange={onChange} />
      <FieldLabel htmlFor={id} className="font-normal">{label}</FieldLabel>
    </Field>
  )
}

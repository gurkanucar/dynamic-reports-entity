import {
  ClipboardListIcon,
  CalendarClockIcon, CircleAlertIcon, CopyIcon, LayoutGridIcon, RadioTowerIcon, SaveIcon, SparklesIcon, TriangleAlertIcon,
  WebhookIcon,
} from "lucide-react"
import { useMemo, useState } from "react"
import { Link, useLocation, useNavigate, useParams } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { cn } from "@/lib/utils"
import { ApiError, errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { Hint } from "@/shared/components/hint"
import { useAskParam } from "@/features/assistant/lib/use-ask-param"
import { AssistantChat } from "@/features/assistant/components/assistant-chat"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import type { FlowDraftDto } from "@/features/assistant/types/assistant.dto"
import { FlowCanvas } from "../components/flow-canvas"
import { applyFlowDraft } from "../lib/flow-draft"
import type { StartLine } from "../components/start-node"
import { StartPanel } from "../components/start-panel"
import { StepConfigPanel } from "../components/step-config-panel"
import { checkFlow, type FlowIssue } from "../lib/flow-checks"
import { removeStep, renameStep, withExplicitEdges, withLayout, type GraphModel } from "../lib/flow-graph"
import { newKey, STEP_GROUPS, STEP_TYPES, stepType } from "../lib/step-types"
import {
  describeTrigger, newTrigger, saveTriggers, START_NODE, surveyTrigger, toDrafts, type TriggerDraft, type TriggerKind,
  type WebhookSecret,
} from "../lib/triggers"
import { useSaveFlow } from "../mutations/use-save-flow"
import { useFlow } from "../queries/use-flow"
import { useSchedules } from "../queries/use-schedules"
import { useTriggers } from "../queries/use-triggers"
import { useSurveys } from "@/features/surveys/queries/use-surveys"
import type { FlowDetailResponse, FlowStepDto, StepType } from "../types/flow.dto"

const TRIGGER_ITEMS: { kind: TriggerKind; label: string; icon: typeof CalendarClockIcon; hint: string }[] = [
  { kind: "schedule", label: "Zamanlama", icon: CalendarClockIcon, hint: "Belirli zamanlarda (cron) başlatır." },
  { kind: "webhook", label: "Webhook ile", icon: WebhookIcon, hint: "Başka bir sistem bir adrese POST ettiğinde başlar." },
  { kind: "kafka", label: "Kafka mesajı ile", icon: RadioTowerIcon, hint: "Bir topic'e mesaj geldiğinde başlar." },
  { kind: "survey", label: "Anket yanıtı ile", icon: ClipboardListIcon, hint: "Bir anket her yanıtlandığında başlar." },
]

/** /flows/new and /flows/:flowId/edit */
export function FlowEditorPage() {
  const params = useParams()
  const flowId = params.flowId ?? null
  const flow = useFlow(flowId ?? "")
  const schedules = useSchedules(flowId ?? "")
  const triggers = useTriggers(flowId ?? "")
  const surveyParam = new URLSearchParams(useLocation().search).get("survey")
  if (flowId === null) {
    // "Akış oluştur" on a survey's results page: start with a trigger on that survey
    return <FlowEditor key="new" flow={null} triggers={surveyParam ? [surveyTrigger(surveyParam)] : []} />
  }
  if (flow.isPending || schedules.isPending || triggers.isPending) return <Skeleton className="h-[32rem] w-full" />
  if (flow.isError) return <ErrorAlert error={flow.error} title="Akış yüklenemedi" />
  if (schedules.isError || triggers.isError) {
    return <ErrorAlert error={schedules.error ?? triggers.error} title="Tetikleyiciler yüklenemedi" />
  }
  // keyed by id only: saving puts the new version in the cache, and a remount would drop the save callbacks
  return <FlowEditor key={flow.data.id} flow={flow.data} triggers={toDrafts(schedules.data, triggers.data)} />
}

function FlowEditor({ flow, triggers: savedTriggers }: { flow: FlowDetailResponse | null; triggers: TriggerDraft[] }) {
  const navigate = useNavigate()
  const save = useSaveFlow()
  // the version editing started from: a change saved meanwhile by someone else answers 412 instead of being overwritten
  const [version] = useState(flow?.version)
  const [original] = useState(savedTriggers)
  const [name, setName] = useState(flow?.name ?? "Yeni akış")
  const [model, setModel] = useState<GraphModel>(() => withLayout(withExplicitEdges({
    steps: flow?.steps ?? [], edges: flow?.edges ?? [], layout: flow?.layout ?? {},
  })))
  const [triggers, setTriggers] = useState<TriggerDraft[]>(savedTriggers)
  const surveys = useSurveys("mine")
  const [selected, setSelected] = useState<string | null>(null)
  const [fitSignal, setFitSignal] = useState(0)
  // full window: the whole editor (steps, canvas, settings), not only the canvas
  const [expanded, setExpanded] = useState(false)
  const [savingTriggers, setSavingTriggers] = useState(false)
  const [secrets, setSecrets] = useState<{ flowId: string; items: WebhookSecret[] } | null>(null)
  const assistant = useAssistantSettings()
  const ask = useAskParam()
  const [showAssistant, setShowAssistant] = useState(ask !== undefined)
  const selectedStep = model.steps.find((step) => step.key === selected) ?? null

  // the server's problems by step key (errors[].field = steps[i]...), shown until the next save
  const serverProblems = useMemo(() => {
    const byKey = new Map<string, string>()
    if (save.error instanceof ApiError) {
      for (const error of save.error.errors) {
        const match = /^steps\[(\d+)]/.exec(error.field)
        const step = match ? model.steps[Number(match[1])] : undefined
        if (step) byKey.set(step.key, [byKey.get(step.key), error.message].filter(Boolean).join(" "))
      }
    }
    return byKey
  }, [save.error, model.steps])

  const issues = useMemo(() => checkFlow(name, model, triggers), [name, model, triggers])
  const errors = issues.filter((issue) => issue.severity === "error")
  const startLines: StartLine[] = useMemo(() => [
    { kind: "manual", text: "Elle (Şimdi çalıştır)", enabled: true },
    ...triggers.map((trigger): StartLine => ({ kind: trigger.kind, enabled: trigger.enabled,
      text: describeTrigger(trigger.kind === "survey" && !trigger.title
        ? { ...trigger, title: surveys.data?.data.find((survey) => survey.id === trigger.surveyId)?.title } : trigger) })),
  ], [triggers, surveys.data])

  const add = (type: StepType) => {
    const key = newKey(type, model.steps.map((step) => step.key))
    // after the selected step, else after the last one: a new step is never left unconnected
    const from = selectedStep ?? (selected === START_NODE ? null : model.steps.at(-1) ?? null)
    const position = from && model.layout[from.key]
      ? { x: model.layout[from.key].x + 320, y: model.layout[from.key].y }
      : { x: model.steps.length * 320, y: 0 }
    let edges = model.edges
    if (from) {   // continue from the step's first free exit
      const free = stepType(from.type).outcomes.find((outcome) =>
        !model.edges.some((edge) => edge.from === from.key && (edge.when ?? "out") === outcome.id))
      if (free) edges = [...edges, { from: from.key, to: key, when: free.id === "out" ? null : free.id }]
    }
    setModel({ steps: [...model.steps, stepType(type).create(key)], edges, layout: { ...model.layout, [key]: position } })
    setSelected(key)
  }

  const addTrigger = (kind: TriggerKind) => {
    setTriggers([...triggers, newTrigger(kind)])
    setSelected(START_NODE)
  }

  // functional: also right when called later from a toast ("Geri al") after other edits
  const replace = (step: FlowStepDto) => setModel((current) =>
    ({ ...current, steps: current.steps.map((existing) => (existing.key === step.key ? step : existing)) }))

  const submit = () => {
    const request = { name: name.trim(), steps: model.steps, edges: model.edges, layout: model.layout }
    save.mutate({ id: flow?.id, version, request }, {
      onSuccess: async (saved) => {
        setSavingTriggers(true)
        try {
          const created = await saveTriggers(saved.id, original, triggers)
          toast.success("Akış kaydedildi")
          if (created.length > 0) setSecrets({ flowId: saved.id, items: created })
          else navigate(`/flows/${saved.id}`)
        } catch (error) {
          // the flow is saved; its triggers can be fixed on the flow page
          toast.error("Akış kaydedildi ama tetikleyiciler kaydedilemedi", { description: errorMessage(error) })
          navigate(`/flows/${saved.id}`)
        } finally {
          setSavingTriggers(false)
        }
      },
    })
  }

  const busy = save.isPending || savingTriggers

  /** The assistant's draft replaces the canvas (new templates are created now); saving stays the user's click. */
  const applyDraft = async (draft: FlowDraftDto) => {
    try {
      const applied = await applyFlowDraft(draft)
      setName(applied.name)
      setModel(applied.model)
      if (applied.schedules.length > 0 || applied.surveys.length > 0) {
        setTriggers([...triggers.filter((trigger) => (applied.schedules.length === 0 || trigger.kind !== "schedule")
          && (applied.surveys.length === 0 || trigger.kind !== "survey")), ...applied.schedules, ...applied.surveys])
      }
      setSelected(null)
      setFitSignal((n) => n + 1)
      toast.success("Asistanın taslağı uygulandı", {
        description: (applied.createdTemplates.length > 0
          ? `Yeni mail şablonu: ${applied.createdTemplates.join(", ")}. ` : "")
          + "Kontrol edip “Kaydet”e basın.",
      })
    } catch (error) {
      toast.error("Taslak uygulanamadı", { description: errorMessage(error) })
    }
  }

  return (
    <div className={cn("flex min-w-0 flex-col gap-4",
      expanded && "fixed inset-0 z-50 overflow-y-auto bg-background p-4")}>
      <div className="flex flex-wrap items-center gap-2">
        <Field className="max-w-md" data-invalid={!name.trim() ? true : undefined}>
          <FieldLabel htmlFor="flow-name" className="sr-only">Akış adı</FieldLabel>
          <Input id="flow-name" className="text-base font-semibold" value={name} maxLength={100}
            placeholder="Akışa bir ad verin" aria-invalid={!name.trim()} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {errors.length > 0 && <span className="text-sm text-destructive">{errors.length} sorun düzeltilmeli</span>}
          {assistant.data?.enabled && (
            <Button variant={showAssistant ? "secondary" : "outline"} aria-pressed={showAssistant}
              onClick={() => setShowAssistant(!showAssistant)}>
              <SparklesIcon data-icon="inline-start" />Asistan
            </Button>
          )}
          <Hint text="Adımları soldan sağa yeniden dizer" wrap={model.steps.length < 2}>
            <Button variant="outline" disabled={model.steps.length < 2}
              onClick={() => { setModel(withLayout({ ...model, layout: {} })); setFitSignal((n) => n + 1) }}>
              <LayoutGridIcon data-icon="inline-start" />Otomatik yerleştir
            </Button>
          </Hint>
          <Button variant="ghost" asChild><Link to={flow ? `/flows/${flow.id}` : "/flows"}>Vazgeç</Link></Button>
          <Hint text={errors.length > 0 ? "Önce kontrol listesindeki sorunları düzeltin" : null} wrap>
            <Button disabled={busy || errors.length > 0} onClick={submit}>
              {busy ? <Spinner data-icon="inline-start" /> : <SaveIcon data-icon="inline-start" />}Kaydet
            </Button>
          </Hint>
        </div>
      </div>
      {save.isError && <ErrorAlert error={save.error} title="Kaydedilemedi" />}

      <div className={cn("grid min-w-0 gap-4 lg:grid-cols-[12rem_minmax(0,1fr)] 2xl:grid-cols-[12rem_minmax(0,1fr)_24rem]",
        expanded && "lg:grid-cols-[12rem_minmax(0,1fr)_22rem] 2xl:grid-cols-[13rem_minmax(0,1fr)_26rem]")}>
        <Card className="self-start">
          <CardContent className="flex flex-col gap-4">
            <PaletteSection title="Tetikleyiciler" hint="Akışı başlatır">
              {TRIGGER_ITEMS.map((item) => (
                <Hint key={item.kind} text={item.hint} side="right">
                  <Button variant="outline" size="sm" className="justify-start" onClick={() => addTrigger(item.kind)}>
                    <item.icon data-icon="inline-start" />{item.label}
                  </Button>
                </Hint>
              ))}
            </PaletteSection>
            {STEP_GROUPS.map((group) => (
              <PaletteSection key={group} title={group} hint={group === "Veri" ? "Seçili adımın ardına eklenir" : undefined}>
                {STEP_TYPES.filter((info) => info.group === group).map((info) => (
                  <Hint key={info.type} text={info.description} side="right">
                    <Button variant="outline" size="sm" className="justify-start" onClick={() => add(info.type)}>
                      <info.icon data-icon="inline-start" />{info.label}
                    </Button>
                  </Hint>
                ))}
              </PaletteSection>
            ))}
          </CardContent>
        </Card>

        <div className="flex min-w-0 flex-col gap-3">
          <FlowCanvas model={model} editable selected={selected} onSelect={setSelected} onChange={setModel}
            invalidKeys={new Set(serverProblems.keys())} issues={issues} start={startLines}
            fitSignal={fitSignal} expanded={expanded} onExpandedChange={setExpanded}
            className={expanded ? "h-[calc(100svh-14rem)] min-h-96 w-full" : "h-[min(40rem,calc(100svh-16rem))] min-h-96 w-full"} />
          <Checklist issues={issues} onSelect={(target) => target !== "flow" && setSelected(target)} />
        </div>

        <div className={cn("min-w-0 lg:col-span-2 2xl:col-span-1",
          expanded && "lg:col-span-1 lg:max-h-[calc(100svh-6rem)] lg:overflow-y-auto", "flex flex-col gap-4")}>
          {assistant.data?.enabled && (
            // kept mounted while hidden: the conversation survives closing the panel
            <AssistantChat kind="flow" title="Akış asistanı" model={assistant.data.model} initialInput={ask}
              className={cn("h-[32rem]", !showAssistant && "hidden")}
              description="Ne olmasını istediğinizi yazın; asistan adımları, zamanlamayı ve gerekirse mail şablonunu hazırlar. Verilerinizi görmez."
              examples={[
                "Her pazartesi 09:00'da İK'ya geciken zorunlu eğitimleri tablo olarak mail at",
                "Her ayın 1'inde departman kadrosunu Excel ekiyle yöneticilere gönder",
                "Kayıt varsa onaya gönder, onaylanırsa finansa mail at",
              ]}
              busyLabel="Akış hazırlanıyor…"
              placeholder={{ empty: "Akış ne yapsın?", change: "Akışta neyi değiştireyim?" }}
              current={model.steps.length > 0 ? {
                name, steps: model.steps, edges: model.edges,
                schedules: triggers.filter((trigger) => trigger.kind === "schedule")
                  .map((trigger) => ({ cron: trigger.cron, timeZone: trigger.timeZone })),
              } : null}
              applicable={(reply) => reply.draft !== null && reply.draft !== undefined}
              onApply={(reply) => void applyDraft(reply.draft as FlowDraftDto)} />
          )}
          {selected === START_NODE ? (
            <StartPanel triggers={triggers} onChange={setTriggers} saved={flow !== null} />
          ) : selectedStep ? (
            <StepConfigPanel step={selectedStep} model={model} flowName={name} error={serverProblems.get(selectedStep.key)} onChange={replace}
              onRename={(key) => { setModel(renameStep(model, selectedStep.key, key)); setSelected(key) }}
              onDelete={() => { setModel(removeStep(model, selectedStep.key)); setSelected(null) }} />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Ayarlar</CardTitle>
                <CardDescription>
                  Ayarlarını görmek için bir adım seçin. Akışı neyin başlatacağı “Başlangıç” düğümündedir.
                </CardDescription>
              </CardHeader>
            </Card>
          )}
        </div>
      </div>

      <Dialog open={secrets !== null} onOpenChange={(open) => { if (!open && secrets) navigate(`/flows/${secrets.flowId}`) }}>
        <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Webhook bilgileri</DialogTitle>
            <DialogDescription>Gizli anahtar yalnızca şimdi gösterilir; çağıran sistemde saklayın.</DialogDescription>
          </DialogHeader>
          {secrets?.items.map((item) => (
            <div key={item.url} className="flex flex-col gap-2 rounded-lg border p-3 text-sm">
              <span className="text-xs text-muted-foreground">Adres (POST)</span>
              <code className="break-all">{item.url}</code>
              <span className="text-xs text-muted-foreground">Gizli anahtar (X-Signature: sha256=HMAC)</span>
              <code className="break-all">{item.secret}</code>
            </div>
          ))}
          <DialogFooter>
            <Button variant="outline" onClick={() => void navigator.clipboard.writeText(
              secrets?.items.map((item) => `${item.url}\n${item.secret}`).join("\n\n") ?? "").then(() => toast.success("Kopyalandı"))}>
              <CopyIcon data-icon="inline-start" />Kopyala
            </Button>
            <Button onClick={() => secrets && navigate(`/flows/${secrets.flowId}`)}>Akışa git</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PaletteSection({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-1.5">
      <div className="flex flex-col">
        <span className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</span>
        {hint && <span className="text-[11px] text-muted-foreground/80">{hint}</span>}
      </div>
      <div className="flex flex-wrap gap-1.5 lg:flex-col">{children}</div>
    </section>
  )
}

/** What to fix before saving (errors) and what may surprise the owner (warnings); a click selects the step. */
function Checklist({ issues, onSelect }: { issues: FlowIssue[]; onSelect: (target: string) => void }) {
  if (issues.length === 0) {
    return <p className="text-xs text-muted-foreground">Kontrol listesi temiz: akış kaydedilebilir.</p>
  }
  return (
    <div className="flex flex-col gap-1 rounded-lg border p-3">
      <span className="text-xs font-medium text-muted-foreground">Kontrol listesi</span>
      <ul className="flex flex-col gap-0.5">
        {issues.map((issue, index) => (
          <li key={index}>
            <button type="button" onClick={() => onSelect(issue.target)}
              className={cn("flex w-full items-start gap-2 rounded-md px-1.5 py-1 text-left text-sm hover:bg-muted",
                issue.severity === "error" ? "text-destructive" : "text-muted-foreground")}>
              {issue.severity === "error"
                ? <CircleAlertIcon className="mt-0.5 size-3.5 shrink-0" />
                : <TriangleAlertIcon className="mt-0.5 size-3.5 shrink-0" />}
              <span>
                {issue.target !== "flow" && (
                  <b className="font-medium">{issue.target === START_NODE ? "Başlangıç" : issue.target}: </b>
                )}
                {issue.message}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

import { BarChart3Icon, EyeIcon, PencilIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { Link } from "react-router"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { GroupDialog } from "@/features/approvals/components/group-dialog"
import { useApprovalGroups } from "@/features/approvals/queries/use-approval-groups"
import type { ApprovalGroupResponse } from "@/features/approvals/types/approval.dto"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { useSurvey, useSurveys } from "@/features/surveys/queries/use-surveys"
import { DefinitionEditor } from "@/features/datasets/components/definition-editor"
import { useCatalog } from "@/features/datasets/queries/use-catalog"
import { useSavedDatasets } from "@/features/datasets/queries/use-saved-datasets"
import type { ExportFormat } from "@/features/datasets/types/dataset.types"
import { TemplateEditor } from "@/features/templates/components/template-editor"
import { DEFAULT_DATASET_TEMPLATE } from "@/features/templates/lib/default-template"
import { useTemplate } from "@/features/templates/queries/use-template"
import { useTemplates } from "@/features/templates/queries/use-templates"
import { stepsBefore, type GraphModel } from "../lib/flow-graph"
import { stepType } from "../lib/step-types"
import type { FlowStepDto } from "../types/flow.dto"
import { JsonLogicEditor } from "./json-logic-editor"
import { StepAssist } from "./step-assist"
import { DatasetStepPreview, MailStepPreview } from "./step-preview"

const KEY_PATTERN = /^[a-z][a-z0-9_]{0,39}$/

/** Paths a rule or template of `key` can use, from the steps before it and the trigger. */
function variablesFor(model: GraphModel, key: string, withItem: boolean): string[] {
  const paths: string[] = withItem ? ["item.amount", "item.status", "item.id"] : []
  for (const step of stepsBefore(model, key)) {
    const prefix = `steps.${step.key}`
    switch (step.type) {
      case "dataset": paths.push(`${prefix}.rowCount`, `${prefix}.hasRows`); break
      case "filter": paths.push(`${prefix}.count`, `${prefix}.items`); break
      case "approval": paths.push(`${prefix}.decision`, `${prefix}.decidedBy`, `${prefix}.comment`); break
      case "survey": paths.push(`${prefix}.respondent`, `${prefix}.answers`, `${prefix}.text`); break
      case "webhook": paths.push(`${prefix}.status`, `${prefix}.body`); break
      case "condition": paths.push(`${prefix}.result`); break
      default: break
    }
  }
  // webhook: the JSON body is `trigger` itself; Kafka: trigger.value (body), trigger.key, trigger.headers
  paths.push("trigger.amount", "trigger.value.amount", "trigger.key", "run.trigger", "run.triggeredBy", "flow.name")
  return paths
}

/** Settings of the selected step. */
export function StepConfigPanel({ step, model, flowName = "", error, onChange, onRename, onDelete }: {
  step: FlowStepDto
  model: GraphModel
  /** For previews: what {{flow.name}} shows. */
  flowName?: string
  /** Problems the server reported for this step. */
  error?: string
  onChange: (step: FlowStepDto) => void
  onRename: (key: string) => void
  onDelete: () => void
}) {
  const info = stepType(step.type)
  const [key, setKey] = useState(step.key)
  const [shownKey, setShownKey] = useState(step.key)
  if (shownKey !== step.key) {   // another step was selected, or it was renamed
    setShownKey(step.key)
    setKey(step.key)
  }
  const keyTaken = key !== step.key && model.steps.some((other) => other.key === key)
  const keyInvalid = !KEY_PATTERN.test(key) || keyTaken

  return (
    <Card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><info.icon />{info.label}</CardTitle>
        <CardAction><StepAssist step={step} model={model} flowName={flowName} onChange={onChange} /></CardAction>
        <CardDescription>{info.description}</CardDescription>
      </CardHeader>
      <CardContent>
        <FieldGroup>
          <Field data-invalid={keyInvalid ? true : undefined}>
            <FieldLabel htmlFor="step-key">Anahtar</FieldLabel>
            <Input id="step-key" className="font-mono" value={key} aria-invalid={keyInvalid}
              onChange={(e) => setKey(e.target.value)}
              onBlur={() => { if (!keyInvalid && key !== step.key) onRename(key) }} />
            {keyInvalid ? <FieldError>{keyTaken ? "Bu anahtar kullanılıyor." : "Küçük harf, rakam ve _; harfle başlar."}</FieldError>
              : <FieldDescription>Sonraki adımlar çıktıya steps.{step.key} ile ulaşır.</FieldDescription>}
          </Field>
          <StepFields step={step} model={model} onChange={onChange} />
          {step.type === "dataset" && <DatasetStepPreview step={step} />}
          {step.type === "mail" && <MailStepPreview step={step} model={model} flowName={flowName} />}
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button variant="outline" className="self-start" onClick={onDelete}>
            <Trash2Icon data-icon="inline-start" />Adımı sil
          </Button>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

function StepFields({ step, model, onChange }: {
  step: FlowStepDto
  model: GraphModel
  onChange: (step: FlowStepDto) => void
}) {
  switch (step.type) {
    case "dataset": return <DatasetFields step={step} onChange={onChange} />
    case "mail": return <MailFields step={step} model={model} onChange={onChange} />
    case "condition":
      return (
        <Field>
          <FieldLabel>Kural</FieldLabel>
          <JsonLogicEditor value={step.expression} variables={variablesFor(model, step.key, false)}
            onChange={(expression) => onChange({ ...step, expression })} />
          <FieldDescription>
            Sağlanırsa “Evet”, sağlanmazsa “Hayır” çıkışından devam eder. Webhook gövdesi trigger.alan, Kafka mesajı
            trigger.value.alan olarak okunur.
          </FieldDescription>
        </Field>
      )
    case "filter":
      return (
        <>
          <Field>
            <FieldLabel htmlFor="filter-source">Liste</FieldLabel>
            <Input id="filter-source" className="font-mono text-xs" value={step.source}
              onChange={(e) => onChange({ ...step, source: e.target.value })} />
            <FieldDescription>Ör. trigger.orders (webhook gövdesi), trigger.value.orders (Kafka), steps.webhook.body.items</FieldDescription>
          </Field>
          <Field>
            <FieldLabel>Tutulacak elemanlar</FieldLabel>
            <JsonLogicEditor value={step.expression} variables={variablesFor(model, step.key, true)}
              onChange={(expression) => onChange({ ...step, expression })} />
            <FieldDescription>Her eleman “item” olarak görünür; çıktı: steps.{step.key}.items ve .count</FieldDescription>
          </Field>
        </>
      )
    case "approval": return <ApprovalFields step={step} onChange={onChange} />
    case "survey": return <SurveyStepFields step={step} onChange={onChange} />
    case "webhook":
      return (
        <>
          <div className="grid grid-cols-[7rem_minmax(0,1fr)] gap-2">
            <Select value={step.method ?? "POST"} onValueChange={(method) => onChange({ ...step, method: method as "POST" })}>
              <SelectTrigger className="w-full" aria-label="Metot"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {["POST", "PUT", "PATCH", "GET", "DELETE"].map((m) => <SelectItem key={m} value={m}>{m}</SelectItem>)}
                </SelectGroup>
              </SelectContent>
            </Select>
            <Input aria-label="URL" value={step.url} onChange={(e) => onChange({ ...step, url: e.target.value })} />
          </div>
          <Field>
            <FieldLabel htmlFor="webhook-body">Gövde (JSON şablonu)</FieldLabel>
            <Textarea id="webhook-body" className="min-h-28 font-mono text-xs" value={step.body ?? ""}
              onChange={(e) => onChange({ ...step, body: e.target.value || null })} />
            <FieldDescription>Değerler JSON için kaçışlanır: {`{"customer": "{{trigger.value.customer}}"}`}</FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor="webhook-headers">Başlıklar</FieldLabel>
            <Textarea id="webhook-headers" className="min-h-16 font-mono text-xs" placeholder="X-Api-Key: ..."
              value={Object.entries(step.headers ?? {}).map(([k, v]) => `${k}: ${v}`).join("\n")}
              onChange={(e) => onChange({ ...step, headers: Object.fromEntries(e.target.value.split("\n")
                .map((line) => line.split(/:(.*)/s).map((part) => part.trim()))
                .filter(([name, value]) => name && value !== undefined)) })} />
          </Field>
        </>
      )
    case "kafka":
      return (
        <>
          <Field>
            <FieldLabel htmlFor="kafka-topic">Topic</FieldLabel>
            <Input id="kafka-topic" className="font-mono" value={step.topic} onChange={(e) => onChange({ ...step, topic: e.target.value })} />
          </Field>
          <Field>
            <FieldLabel htmlFor="kafka-key">Mesaj anahtarı (isteğe bağlı)</FieldLabel>
            <Input id="kafka-key" className="font-mono text-xs" value={step.messageKey ?? ""} placeholder="run-{{run.id}}"
              onChange={(e) => onChange({ ...step, messageKey: e.target.value || null })} />
          </Field>
          <Field>
            <FieldLabel htmlFor="kafka-payload">Mesaj (JSON şablonu)</FieldLabel>
            <Textarea id="kafka-payload" className="min-h-28 font-mono text-xs" value={step.payload}
              onChange={(e) => onChange({ ...step, payload: e.target.value })} />
          </Field>
        </>
      )
  }
}

function DatasetFields({ step, onChange }: {
  step: Extract<FlowStepDto, { type: "dataset" }>
  onChange: (step: FlowStepDto) => void
}) {
  const catalog = useCatalog()
  const saved = useSavedDatasets()
  const [open, setOpen] = useState(false)
  const source = step.datasetName != null ? "saved" : "own"
  const format: ExportFormat = step.format ?? "csv"
  const mailRows = step.mailRows ?? 20
  const fileNameInvalid = !!step.fileName && !step.fileName.toLowerCase().endsWith(`.${format}`)
  const definition = step.definition ?? { root: "", columns: [] }
  return (
    <>
      <Field>
        <FieldLabel>Kaynak</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap justify-start" value={source}
          onValueChange={(value) => {
            if (value === "saved") onChange({ ...step, datasetName: saved.data?.[0]?.name ?? "", definition: null })
            if (value === "own") onChange({ ...step, datasetName: null, definition })
          }}>
          <ToggleGroupItem value="saved">Kayıtlı veri seti</ToggleGroupItem>
          <ToggleGroupItem value="own">Bu akışa özel</ToggleGroupItem>
        </ToggleGroup>
        <FieldDescription>
          {source === "saved"
            ? "Adıyla kullanılır: veri seti değişirse bu akış da yeni hâlini kullanır."
            : "Yalnızca bu adımda tanımlıdır."} Her çalışmada akış sahibinin yetkileriyle hesaplanır.
        </FieldDescription>
      </Field>
      {source === "saved" ? (
        <Field data-invalid={!step.datasetName ? true : undefined}>
          <FieldLabel>Veri seti</FieldLabel>
          <Select value={step.datasetName || undefined} onValueChange={(datasetName) => onChange({ ...step, datasetName })}>
            <SelectTrigger className="w-full" aria-invalid={!step.datasetName}><SelectValue placeholder="Veri seti seçin" /></SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {saved.data?.map((dataset) => (
                  <SelectItem key={dataset.name} value={dataset.name}>{dataset.label ?? dataset.name} ({dataset.name})</SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          {saved.data?.length === 0 && (
            <FieldDescription>
              Henüz kayıtlı veri seti yok. <Link className="underline underline-offset-4" to="/datasets/new">Bir tane oluşturun</Link>
              {" "}ya da “Bu akışa özel”i seçin.
            </FieldDescription>
          )}
          {step.datasetName && (
            <FieldDescription>
              <Link className="underline underline-offset-4" to={`/datasets/${saved.data?.find((item) => item.name === step.datasetName)?.id ?? ""}`} target="_blank">Veri setini aç</Link>
            </FieldDescription>
          )}
        </Field>
      ) : (
        <Field data-invalid={!definition.root || definition.columns.length === 0 ? true : undefined}>
          <FieldLabel>Veri seti</FieldLabel>
          <p className="text-sm text-muted-foreground">
            {definition.root ? `${definition.root} · ${definition.columns.length} kolon` : "Henüz tasarlanmadı."}
          </p>
          <Button variant="outline" className="self-start" disabled={!catalog.data} onClick={() => setOpen(true)}>
            <PencilIcon data-icon="inline-start" />Veri setini tasarla
          </Button>
        </Field>
      )}
      <Field>
        <FieldLabel>Dosya</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap justify-start"
          value={format} onValueChange={(value) => value && onChange({ ...step, format: value as ExportFormat,
            fileName: step.fileName ? step.fileName.replace(/\.(csv|xlsx)$/i, `.${value}`) : null })}>
          <ToggleGroupItem value="xlsx">Excel</ToggleGroupItem>
          <ToggleGroupItem value="csv">CSV</ToggleGroupItem>
        </ToggleGroup>
        <Input id="dataset-file" aria-label="Dosya adı" placeholder={`${step.key}.${format}`} value={step.fileName ?? ""}
          aria-invalid={fileNameInvalid}
          onChange={(e) => onChange({ ...step, fileName: e.target.value || null })} />
        {fileNameInvalid ? <FieldError>Dosya adı .{format} ile bitmeli.</FieldError>
          : <FieldDescription>Mail adımında ek olarak seçilebilir; çalışmalarda indirilebilir.</FieldDescription>}
      </Field>
      <Field>
        <FieldLabel>Mailde gösterilecek satır</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap justify-start"
          value={String(mailRows)} onValueChange={(value) => value && onChange({ ...step, mailRows: Number(value) })}>
          <ToggleGroupItem value="0">Hiç (yalnızca ek)</ToggleGroupItem>
          <ToggleGroupItem value="20">İlk 20</ToggleGroupItem>
          <ToggleGroupItem value="100">İlk 100</ToggleGroupItem>
          <ToggleGroupItem value="500">Tümü (en çok 500)</ToggleGroupItem>
        </ToggleGroup>
        <FieldDescription>
          Mail şablonundaki tabloya bu kadar satır gelir; tamamı her zaman dosyada. Çok satır maili ağırlaştırır.
        </FieldDescription>
      </Field>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent className="w-full overflow-y-auto data-[side=right]:w-full data-[side=right]:sm:max-w-3xl">
          <SheetHeader>
            <SheetTitle>Veri seti: {step.key}</SheetTitle>
            <SheetDescription>Akış sahibinin yetkileriyle çalışır; değişiklikler hemen adıma yazılır.</SheetDescription>
          </SheetHeader>
          <div className="px-4 pb-6">
            {catalog.data && (
              <DefinitionEditor catalog={catalog.data} definition={definition}
                onChange={(next) => onChange({ ...step, definition: next })} />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}

function MailFields({ step, model, onChange }: {
  step: Extract<FlowStepDto, { type: "mail" }>
  model: GraphModel
  onChange: (step: FlowStepDto) => void
}) {
  const templates = useTemplates()
  const datasets = stepsBefore(model, step.key).filter((before) => before.type === "dataset")
  // the template editor in a side sheet: "new" or the chosen template, without leaving the flow
  const [editing, setEditing] = useState<"new" | "current" | null>(null)
  const current = useTemplate(editing === "current" && step.templateId ? step.templateId : null)
  return (
    <>
      <Field>
        <div className="flex items-center justify-between gap-2">
          <FieldLabel>Şablon</FieldLabel>
          <div className="flex gap-1">
            {step.templateId && (
              <Button type="button" variant="ghost" size="sm" onClick={() => setEditing("current")}>
                <PencilIcon data-icon="inline-start" />Düzenle
              </Button>
            )}
            <Button type="button" variant="outline" size="sm" onClick={() => setEditing("new")}>
              <PlusIcon data-icon="inline-start" />Yeni şablon
            </Button>
          </div>
        </div>
        <Select value={step.templateId ? String(step.templateId) : undefined}
          onValueChange={(value) => onChange({ ...step, templateId: value })}>
          <SelectTrigger className="w-full"><SelectValue placeholder="Şablon seçin" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {templates.data?.map((template) => (
                <SelectItem key={template.id} value={String(template.id)}>{template.name}</SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
      <Field>
        <FieldLabel htmlFor="mail-to">Alıcılar</FieldLabel>
        <Input id="mail-to" value={step.to.join(", ")} placeholder="ad@sirket.com, ekip@sirket.com"
          onChange={(e) => onChange({ ...step, to: e.target.value.split(/[,;\s]+/).filter(Boolean) })} />
      </Field>
      <Sheet open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="w-full overflow-y-auto data-[side=right]:w-full data-[side=right]:sm:max-w-[min(90rem,95vw)]">
          <SheetHeader>
            <SheetTitle>{editing === "new" ? "Yeni mail şablonu" : "Mail şablonunu düzenle"}</SheetTitle>
            <SheetDescription>Kaydedince bu adımda seçilir. Şablon başka akışlarda da kullanılabilir.</SheetDescription>
          </SheetHeader>
          <div className="px-4 pb-6">
            {editing === "new" && (
              <TemplateEditor key="new" template={null} initial={DEFAULT_DATASET_TEMPLATE}
                onSaved={(saved) => { onChange({ ...step, templateId: saved.id }); setEditing(null) }} />
            )}
            {editing === "current" && current.data && (
              <TemplateEditor key={`${current.data.id}-${current.data.version}`} template={current.data}
                initial={current.data} onSaved={() => setEditing(null)} />
            )}
          </div>
        </SheetContent>
      </Sheet>
      {datasets.length > 0 && (
        <Field>
          <FieldLabel>Ekler</FieldLabel>
          <ToggleGroup type="multiple" variant="outline" size="sm" className="max-w-full flex-wrap justify-start"
            value={step.attachments ?? []} onValueChange={(attachments) => onChange({ ...step, attachments })}>
            {datasets.map((dataset) => (
              <ToggleGroupItem key={dataset.key} value={dataset.key}>
                {dataset.type === "dataset" ? dataset.fileName ?? `${dataset.key}.${dataset.format ?? "csv"}` : dataset.key}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        </Field>
      )}
    </>
  )
}

/** Which survey goes to whom, with what message, and how long to wait. */
function SurveyStepFields({ step, onChange }: {
  step: Extract<FlowStepDto, { type: "survey" }>
  onChange: (step: FlowStepDto) => void
}) {
  const surveys = useSurveys("mine", 1, { status: ["published"] })
  const survey = useSurvey(step.surveyId || null)
  const [to, setTo] = useState(step.to.join(", "))
  const questions = survey.data?.definition.fields.filter((field) => field.type !== "section") ?? []
  return (
    <>
      <Field>
        <div className="flex items-center justify-between gap-2">
          <FieldLabel>Anket</FieldLabel>
          <Button type="button" variant="ghost" size="sm" asChild>
            <Link to="/surveys/new" target="_blank"><PlusIcon data-icon="inline-start" />Yeni anket</Link>
          </Button>
        </div>
        <Select value={step.surveyId ? String(step.surveyId) : undefined}
          onValueChange={(value) => onChange({ ...step, surveyId: value })}>
          <SelectTrigger className="w-full"><SelectValue placeholder="Yayındaki bir anket seçin" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {surveys.data?.data.map((item) => <SelectItem key={item.id} value={String(item.id)}>{item.title}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
        <FieldDescription>Yalnızca yayındaki anketler gönderilebilir.</FieldDescription>
        {survey.data && (
          <div className="flex flex-col gap-2 rounded-lg border bg-muted/40 p-2.5">
            <p className="text-xs text-muted-foreground">
              {questions.length} soru · {survey.data.definition.rules?.length ?? 0} kural
              {survey.data.anonymous && " · anonim"}
            </p>
            <div className="flex flex-wrap gap-1.5">
              <Button type="button" variant="outline" size="xs" asChild>
                <Link to={`/s/${survey.data.id}`} target="_blank"><EyeIcon data-icon="inline-start" />Önizle</Link>
              </Button>
              <Button type="button" variant="outline" size="xs" asChild>
                <Link to={`/surveys/${survey.data.id}`} target="_blank"><BarChart3Icon data-icon="inline-start" />Sonuçlar</Link>
              </Button>
              <Button type="button" variant="outline" size="xs" asChild>
                <Link to={`/surveys/${survey.data.id}/edit`} target="_blank"><PencilIcon data-icon="inline-start" />Düzenle</Link>
              </Button>
            </div>
          </div>
        )}
      </Field>
      <Field>
        <FieldLabel htmlFor="survey-to">Alıcılar</FieldLabel>
        <Input id="survey-to" value={to} placeholder="yonetici@sirket.com, {{trigger.managerEmail}}"
          onChange={(e) => setTo(e.target.value)}
          onBlur={() => onChange({ ...step, to: to.split(",").map((item) => item.trim()).filter(Boolean) })} />
        <FieldDescription>Virgülle ayırın; {"{{…}}"} ile tetikleyiciden de alınabilir. İlk yanıt adımı devam ettirir.</FieldDescription>
      </Field>
      <Field>
        <FieldLabel htmlFor="survey-subject">Konu (isteğe bağlı)</FieldLabel>
        <Input id="survey-subject" value={step.subject ?? ""} placeholder={`Anket: ${survey.data?.title ?? "…"}`}
          onChange={(e) => onChange({ ...step, subject: e.target.value || null })} />
      </Field>
      <Field>
        <FieldLabel htmlFor="survey-message">Mesaj</FieldLabel>
        <Textarea id="survey-message" className="min-h-20" value={step.message ?? ""}
          placeholder="Merhaba, {{trigger.fullName}} için ekipman listesini doldurur musunuz?"
          onChange={(e) => onChange({ ...step, message: e.target.value || null })} />
      </Field>
      <Field>
        <FieldLabel htmlFor="survey-timeout">En fazla bekleme (saat)</FieldLabel>
        <Input id="survey-timeout" type="number" min={1} max={720} className="w-32" value={step.timeoutHours ?? 72}
          onChange={(e) => onChange({ ...step, timeoutHours: Number(e.target.value) || null })} />
        <FieldDescription>Süre dolarsa “Zaman aşımı” çıkışından devam eder; bağlantı da kapanır.</FieldDescription>
      </Field>
      {questions.length > 0 && (
        <p className="text-xs text-muted-foreground">
          Sonraki adımlarda: <code>{`{{steps.${step.key}.text.${questions[0].id}}}`}</code>, <code>{`steps.${step.key}.respondent`}</code>
          {" "}· sorular: {questions.map((field) => field.id).join(", ")}
        </p>
      )}
    </>
  )
}

function ApprovalFields({ step, onChange }: {
  step: Extract<FlowStepDto, { type: "approval" }>
  onChange: (step: FlowStepDto) => void
}) {
  const groups = useApprovalGroups()
  const admin = useCurrentUser().data?.authorities.includes("PLATFORM_ADMIN") ?? false
  // "new" or the group being edited; keyed so the form starts fresh each time
  const [dialog, setDialog] = useState<{ key: number; group: ApprovalGroupResponse | null } | null>(null)
  const selectedGroup = groups.data?.find((group) => group.id === step.groupId) ?? null
  return (
    <>
      <Field>
        <div className="flex items-center justify-between gap-2">
          <FieldLabel>Onay grubu</FieldLabel>
          {admin && (
            <div className="flex gap-1">
              {selectedGroup && (
                <Button type="button" variant="ghost" size="sm" onClick={() => setDialog({ key: Date.now(), group: selectedGroup })}>
                  <PencilIcon data-icon="inline-start" />Düzenle
                </Button>
              )}
              <Button type="button" variant="outline" size="sm" onClick={() => setDialog({ key: Date.now(), group: null })}>
                <PlusIcon data-icon="inline-start" />Yeni grup
              </Button>
            </div>
          )}
        </div>
        <Select value={step.groupId ? String(step.groupId) : undefined}
          onValueChange={(value) => onChange({ ...step, groupId: value })}>
          <SelectTrigger className="w-full"><SelectValue placeholder="Grup seçin" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {groups.data?.map((group) => (
                <SelectItem key={group.id} value={String(group.id)}>{group.name} ({group.members.length} kişi)</SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        {selectedGroup && (
          <FieldDescription>Üyeler: {selectedGroup.members.map((member) => member.username).join(", ") || "—"}</FieldDescription>
        )}
        {groups.data?.length === 0 && (
          <FieldDescription>
            {admin ? "Henüz grup yok; “Yeni grup” ile hemen oluşturun." : "Henüz grup yok; bir yöneticiden onay grubu oluşturmasını isteyin."}
          </FieldDescription>
        )}
      </Field>
      {dialog && (
        <GroupDialog key={dialog.key} group={dialog.group} open onOpenChange={(open) => !open && setDialog(null)}
          onSaved={(saved) => onChange({ ...step, groupId: saved.id })} />
      )}
      <Field>
        <FieldLabel htmlFor="approval-title">Başlık</FieldLabel>
        <Input id="approval-title" value={step.title} onChange={(e) => onChange({ ...step, title: e.target.value })} />
      </Field>
      <Field>
        <FieldLabel htmlFor="approval-description">Açıklama</FieldLabel>
        <Textarea id="approval-description" className="min-h-20" value={step.description ?? ""}
          placeholder="Onaylayana gösterilecek detaylar; {{trigger.value.amount}} gibi değerler kullanılabilir."
          onChange={(e) => onChange({ ...step, description: e.target.value || null })} />
      </Field>
      <Field>
        <FieldLabel htmlFor="approval-timeout">Süre (saat)</FieldLabel>
        <Input id="approval-timeout" type="number" min={1} max={720} value={step.timeoutHours ?? 72}
          onChange={(e) => onChange({ ...step, timeoutHours: Number(e.target.value) || null })} />
        <FieldDescription>Bu sürede karar verilmezse “Zaman aşımı” çıkışından devam eder.</FieldDescription>
      </Field>
      <Field orientation="horizontal">
        <Switch id="approval-notify" checked={step.notifyMembers !== false}
          onCheckedChange={(notifyMembers) => onChange({ ...step, notifyMembers })} />
        <FieldLabel htmlFor="approval-notify">Grup üyelerine e-posta gönder</FieldLabel>
      </Field>
      <Field orientation="horizontal">
        <Switch id="approval-self" checked={step.allowSelfApproval === true}
          onCheckedChange={(allowSelfApproval) => onChange({ ...step, allowSelfApproval })} />
        <FieldLabel htmlFor="approval-self">Akışı başlatan kişi de onaylayabilsin</FieldLabel>
      </Field>
    </>
  )
}

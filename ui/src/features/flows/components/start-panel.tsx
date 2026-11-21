import { CalendarClockIcon, HandIcon, PlayIcon, RadioTowerIcon, Trash2Icon, WebhookIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { CRON_PRESETS, looksLikeCron } from "../lib/cron"
import { CronAssist } from "./cron-assist"
import { newTrigger, type TriggerDraft, type TriggerKind } from "../lib/triggers"
import { JsonLogicEditor } from "./json-logic-editor"

const CUSTOM = "__custom__"
const KAFKA_VARIABLES = ["trigger.value.type", "trigger.value.amount", "trigger.key", "trigger.headers"]

/** Settings of the start node: how the flow starts. Saved together with the flow. */
export function StartPanel({ triggers, onChange, saved }: {
  triggers: TriggerDraft[]
  onChange: (triggers: TriggerDraft[]) => void
  /** The flow exists already (webhook addresses are known after saving). */
  saved: boolean
}) {
  const update = (uid: string, change: Partial<TriggerDraft>) =>
    onChange(triggers.map((trigger) => (trigger.uid === uid ? { ...trigger, ...change } as TriggerDraft : trigger)))
  const add = (kind: TriggerKind) => onChange([...triggers, newTrigger(kind)])

  return (
    <Card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><PlayIcon />Başlangıç</CardTitle>
        <CardDescription>Akışı neyin başlatacağını seçin. Elle başlatma her zaman açıktır.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <HandIcon className="size-4" />Elle: akış sayfasındaki “Şimdi çalıştır” ile
        </div>
        {triggers.map((trigger) => (
          <div key={trigger.uid} className="flex flex-col gap-3 rounded-lg border p-3">
            <div className="flex items-center gap-2">
              {trigger.kind === "schedule" && <CalendarClockIcon className="size-4" />}
              {trigger.kind === "webhook" && <WebhookIcon className="size-4" />}
              {trigger.kind === "kafka" && <RadioTowerIcon className="size-4" />}
              <span className="text-sm font-medium">
                {trigger.kind === "schedule" ? "Zamanlama" : trigger.kind === "webhook" ? "Webhook" : "Kafka mesajı"}
              </span>
              <Switch className="ml-auto" checked={trigger.enabled} aria-label="Etkin"
                onCheckedChange={(enabled) => update(trigger.uid, { enabled })} />
              <Button variant="ghost" size="icon" aria-label="Kaldır"
                onClick={() => onChange(triggers.filter((other) => other.uid !== trigger.uid))}>
                <Trash2Icon />
              </Button>
            </div>
            {trigger.kind === "schedule" && <ScheduleFields trigger={trigger} onChange={(change) => update(trigger.uid, change)} />}
            {trigger.kind === "webhook" && (
              <p className="text-xs text-muted-foreground">
                {trigger.saved?.webhookUrl
                  ? <>Adres: <code className="break-all">{window.location.origin}{trigger.saved.webhookUrl}</code></>
                  : saved ? "Kaydedince adres ve gizli anahtar bir kez gösterilir."
                    : "Akışı kaydedince adres ve gizli anahtar bir kez gösterilir."}
                {" "}Gönderilen JSON gövdesi adımlarda <code>trigger</code> olarak okunur.
              </p>
            )}
            {trigger.kind === "kafka" && (
              <FieldGroup>
                <Field data-invalid={!trigger.topic.trim() || trigger.topic.trim().endsWith(".") ? true : undefined}>
                  <FieldLabel htmlFor={`topic-${trigger.uid}`}>Topic</FieldLabel>
                  <Input id={`topic-${trigger.uid}`} className="font-mono" value={trigger.topic}
                    onChange={(e) => update(trigger.uid, { topic: e.target.value })} />
                </Field>
                <Field>
                  <FieldLabel>Filtre (isteğe bağlı)</FieldLabel>
                  <JsonLogicEditor value={trigger.filter ?? true} variables={KAFKA_VARIABLES}
                    onChange={(filter) => update(trigger.uid, { filter: filter === true ? null : filter })} />
                  <FieldDescription>Mesaj adımlarda <code>trigger.value</code> (gövde), <code>trigger.key</code> olarak okunur.</FieldDescription>
                </Field>
              </FieldGroup>
            )}
          </div>
        ))}
        <Separator />
        <div className="flex flex-col gap-2">
          <span className="text-xs font-medium text-muted-foreground">Ekle</span>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => add("schedule")}><CalendarClockIcon data-icon="inline-start" />Zamanlama</Button>
            <Button variant="outline" size="sm" onClick={() => add("webhook")}><WebhookIcon data-icon="inline-start" />Webhook</Button>
            <Button variant="outline" size="sm" onClick={() => add("kafka")}><RadioTowerIcon data-icon="inline-start" />Kafka mesajı</Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function ScheduleFields({ trigger, onChange }: {
  trigger: Extract<TriggerDraft, { kind: "schedule" }>
  onChange: (change: Partial<TriggerDraft>) => void
}) {
  const preset = CRON_PRESETS.some((item) => item.value === trigger.cron) ? trigger.cron : CUSTOM
  const invalid = !looksLikeCron(trigger.cron)
  return (
    <FieldGroup>
      <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap justify-start" value={preset}
        onValueChange={(value) => value && value !== CUSTOM && onChange({ cron: value })}>
        {CRON_PRESETS.map((item) => <ToggleGroupItem key={item.value} value={item.value}>{item.label}</ToggleGroupItem>)}
        <ToggleGroupItem value={CUSTOM} onClick={() => preset !== CUSTOM && onChange({ cron: "0 */6 * * *" })}>Özel</ToggleGroupItem>
      </ToggleGroup>
      <CronAssist cron={trigger.cron} onCron={(cron) => onChange({ cron })} />
      {preset === CUSTOM && (
        <Field data-invalid={invalid ? true : undefined}>
          <FieldLabel htmlFor={`cron-${trigger.uid}`}>Cron ifadesi</FieldLabel>
          <Input id={`cron-${trigger.uid}`} className="font-mono" value={trigger.cron} aria-invalid={invalid}
            onChange={(e) => onChange({ cron: e.target.value })} />
          {invalid ? <FieldError>5 alan (dakika saat gün ay haftanın-günü) ya da @daily gibi bir kısaltma.</FieldError>
            : <FieldDescription>Örn. <code>0 */6 * * *</code>: 6 saatte bir. En sık 5 dakikada bir.</FieldDescription>}
        </Field>
      )}
      <FieldDescription>Saat dilimi: {trigger.timeZone}. Bir önceki çalışma sürüyorsa o zaman atlanır.</FieldDescription>
    </FieldGroup>
  )
}

import { CopyIcon, KeyRoundIcon, PlusIcon, RadioTowerIcon, Trash2Icon, WebhookIcon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { formatDateTime } from "@/shared/lib/format"
import { useDeleteTrigger } from "../mutations/use-delete-trigger"
import { useRotateTriggerSecret } from "../mutations/use-rotate-trigger-secret"
import { useSaveTrigger } from "../mutations/use-save-trigger"
import { useTriggers } from "../queries/use-triggers"
import type { JsonLogicRule, TriggerResponse } from "../types/flow.dto"
import { JsonLogicEditor } from "./json-logic-editor"

const FILTER_VARIABLES = ["trigger.value.type", "trigger.value.amount", "trigger.key", "trigger.headers"]

function fullUrl(path: string) {
  return `${window.location.origin}${path}`
}

/** Webhook and Kafka triggers of a flow (cron schedules have their own card). */
export function TriggersCard({ flowId }: { flowId: number }) {
  const triggers = useTriggers(flowId)
  const save = useSaveTrigger(flowId)
  const remove = useDeleteTrigger(flowId)
  const rotate = useRotateTriggerSecret(flowId)
  const [secret, setSecret] = useState<{ url: string; secret: string } | null>(null)
  const [kafkaOpen, setKafkaOpen] = useState(false)
  const fail = (title: string) => (error: unknown) => toast.error(title, { description: errorMessage(error) })

  const showSecret = (trigger: TriggerResponse, value: string | null) => {
    if (trigger.webhookUrl && value) setSecret({ url: fullUrl(trigger.webhookUrl), secret: value })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tetikleyiciler</CardTitle>
        <CardDescription>Başka bir sistem webhook ile ya da bir Kafka mesajıyla bu akışı başlatabilir.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {triggers.isPending && <Skeleton className="h-16 w-full" />}
        {triggers.isError && <ErrorAlert error={triggers.error} />}
        {triggers.data?.map((trigger) => (
          <div key={trigger.id} className="flex min-w-0 flex-wrap items-center gap-3 rounded-md border p-3 text-sm">
            <Switch checked={trigger.enabled} aria-label="Etkin"
              onCheckedChange={(enabled) => save.mutate({ existing: trigger, request: {
                type: trigger.type, enabled, topic: trigger.topic, filter: trigger.filter } },
              { onError: fail("Değiştirilemedi") })} />
            <Badge variant="secondary">
              {trigger.type === "webhook" ? <WebhookIcon data-icon="inline-start" /> : <RadioTowerIcon data-icon="inline-start" />}
              {trigger.type === "webhook" ? "Webhook" : "Kafka"}
            </Badge>
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate font-mono text-xs">
                {trigger.webhookUrl ? fullUrl(trigger.webhookUrl) : trigger.topic}
              </span>
              <span className="text-xs text-muted-foreground">
                {trigger.filter ? "Filtreli · " : ""}Son tetiklenme: {formatDateTime(trigger.lastFiredAt)}
              </span>
            </div>
            {trigger.webhookUrl && (
              <>
                <Button variant="ghost" size="icon" aria-label="Adresi kopyala"
                  onClick={() => navigator.clipboard.writeText(fullUrl(trigger.webhookUrl!)).then(() => toast.success("Kopyalandı"))}>
                  <CopyIcon />
                </Button>
                <Button variant="ghost" size="icon" aria-label="Yeni gizli anahtar" disabled={rotate.isPending}
                  onClick={() => rotate.mutate(trigger.id, {
                    onSuccess: (result) => showSecret(result.trigger, result.secret),
                    onError: fail("Anahtar yenilenemedi"),
                  })}>
                  <KeyRoundIcon />
                </Button>
              </>
            )}
            <Button variant="ghost" size="icon" aria-label="Tetikleyiciyi sil"
              onClick={() => remove.mutate(trigger.id, { onError: fail("Silinemedi") })}>
              <Trash2Icon />
            </Button>
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" disabled={save.isPending}
            onClick={() => save.mutate({ request: { type: "webhook", enabled: true } }, {
              onSuccess: (result) => showSecret(result.trigger, result.secret),
              onError: fail("Webhook eklenemedi"),
            })}>
            <PlusIcon data-icon="inline-start" />Webhook ekle
          </Button>
          <Button variant="outline" onClick={() => setKafkaOpen(true)}>
            <PlusIcon data-icon="inline-start" />Kafka tetikleyicisi
          </Button>
        </div>
      </CardContent>

      <Dialog open={secret !== null} onOpenChange={(open) => !open && setSecret(null)}>
        <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Webhook bilgileri</DialogTitle>
            <DialogDescription>Gizli anahtar yalnızca şimdi gösterilir; çağıran sistemde saklayın.</DialogDescription>
          </DialogHeader>
          {secret && (
            <FieldGroup>
              <Field>
                <FieldLabel>Adres (POST)</FieldLabel>
                <Input readOnly className="font-mono text-xs" value={secret.url} onFocus={(e) => e.target.select()} />
              </Field>
              <Field>
                <FieldLabel>Gizli anahtar</FieldLabel>
                <Input readOnly className="font-mono text-xs" value={secret.secret} onFocus={(e) => e.target.select()} />
                <FieldDescription>
                  Her istekte gövdenin HMAC-SHA256 imzası gönderilir: <code>X-Signature: sha256=&lt;hex&gt;</code>.
                  Aynı olayın tekrarını engellemek için <code>X-Event-Id</code> gönderin.
                </FieldDescription>
              </Field>
              <pre className="overflow-x-auto rounded-md bg-muted p-3 text-xs">{`BODY='{"type":"order","amount":250}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac '${secret.secret}' | sed 's/^.* //')
curl -X POST '${secret.url}' \\
  -H 'Content-Type: application/json' -H "X-Signature: sha256=$SIG" \\
  -H 'X-Event-Id: order-1' -d "$BODY"`}</pre>
            </FieldGroup>
          )}
          <DialogFooter>
            <Button onClick={() => secret && navigator.clipboard.writeText(secret.secret).then(() => toast.success("Kopyalandı"))}>
              <CopyIcon data-icon="inline-start" />Anahtarı kopyala
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <KafkaTriggerDialog open={kafkaOpen} onOpenChange={setKafkaOpen} pending={save.isPending} error={save.error}
        onSave={(topic, filter) => save.mutate({ request: { type: "kafka", enabled: true, topic, filter } }, {
          onSuccess: () => { setKafkaOpen(false); toast.success("Kafka tetikleyicisi eklendi") },
        })} />
    </Card>
  )
}

function KafkaTriggerDialog({ open, onOpenChange, onSave, pending, error }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSave: (topic: string, filter: JsonLogicRule | null) => void
  pending: boolean
  error: unknown
}) {
  const [topic, setTopic] = useState("flows.")
  const [filter, setFilter] = useState<JsonLogicRule>(true)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Kafka tetikleyicisi</DialogTitle>
          <DialogDescription>Topic'e gelen her mesaj (filtreye uyuyorsa) bir çalışma başlatır.</DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="trigger-topic">Topic</FieldLabel>
            <Input id="trigger-topic" className="font-mono" value={topic} onChange={(e) => setTopic(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel>Filtre (isteğe bağlı)</FieldLabel>
            <JsonLogicEditor value={filter} onChange={setFilter} variables={FILTER_VARIABLES} />
            <FieldDescription>Mesaj “trigger” olarak görünür: trigger.value (JSON gövde), trigger.key, trigger.headers.</FieldDescription>
          </Field>
        </FieldGroup>
        {error ? <ErrorAlert error={error} title="Eklenemedi" /> : null}
        <DialogFooter>
          <Button disabled={pending || !topic.trim()} onClick={() => onSave(topic.trim(), filter === true ? null : filter)}>
            {pending && <Spinner data-icon="inline-start" />}Ekle
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

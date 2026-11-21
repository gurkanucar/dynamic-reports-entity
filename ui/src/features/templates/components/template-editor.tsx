import { LayoutTemplateIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ErrorAlert } from "@/shared/components/error-alert"
import { useAskParam } from "@/features/assistant/lib/use-ask-param"
import { AssistantChat } from "@/features/assistant/components/assistant-chat"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import type { TemplateDraftDto } from "@/features/assistant/types/assistant.dto"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useSavedDatasets } from "@/features/datasets/queries/use-saved-datasets"
import { STARTER_TEMPLATES } from "../lib/default-template"
import { useSampleDatasetModel } from "../queries/use-sample-dataset-model"
import { useSaveTemplate } from "../mutations/use-save-template"
import type { TemplateRequest, TemplateResponse } from "../types/template.dto"
import { TemplatePreview } from "./template-preview"

const SAMPLE = "__sample__"

const EXAMPLES = [
  "Veri setini şık bir tablo olarak gönderen, başlığında kayıt sayısı olan bir mail",
  "Kısa bir bilgilendirme: dosya ekte, kayıt yoksa “bu hafta kayıt yok” yazsın",
  "Onay adımının sonucunu (karar, karar veren, yorum) bildiren bir mail",
]

/**
 * Edits one template (or a new one when `template` is null); saving sends the version it started from.
 * Business users start from a ready-made template or describe the mail to the assistant, then fine-tune the text;
 * the CSS lives in a <style> block that the server inlines before sending.
 */
export function TemplateEditor({ template, initial, onSaved }: {
  template: TemplateResponse | null
  initial: TemplateRequest
  onSaved: (saved: TemplateResponse) => void
}) {
  const [form, setForm] = useState<TemplateRequest>(template ?? initial)
  const ask = useAskParam()
  const save = useSaveTemplate()
  const assistant = useAssistantSettings()
  const set = (change: Partial<TemplateRequest>) => setForm((current) => ({ ...current, ...change }))
  const datasets = useSavedDatasets()
  const [sampleName, setSampleName] = useState<string>(SAMPLE)
  const sampleDataset = datasets.data?.find((dataset) => dataset.name === sampleName) ?? null
  const sample = useSampleDatasetModel(sampleDataset)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate({ id: template?.id, version: template?.version, request: form }, {
      onSuccess: (saved) => { toast.success("Şablon kaydedildi"); onSaved(saved) },
    })
  }

  /** A saved template keeps its name; a new one takes the suggested name. */
  const applyDraft = (draft: TemplateDraftDto) => {
    setForm((current) => ({
      name: template ? current.name : draft.name || current.name,
      description: draft.description ?? current.description,
      subject: draft.subject,
      bodyHtml: draft.bodyHtml,
    }))
    toast.success("Asistanın şablonu uygulandı; kaydetmeden önce gözden geçirin")
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-2">
      <div className="flex min-w-0 flex-col gap-6">
        {assistant.data?.enabled && (
          <AssistantChat kind="mail-template" title="Şablon asistanı" model={assistant.data.model}
            initialInput={template ? undefined : ask}
            description="Nasıl bir mail istediğinizi yazın; asistan konuyu, metni ve tasarımı hazırlar. Verilerinizi görmez."
            examples={EXAMPLES} busyLabel="Şablon hazırlanıyor…"
            placeholder={{ empty: "Nasıl bir mail olsun?", change: "Şablonda neyi değiştireyim?" }}
            current={form.bodyHtml.trim() || sampleDataset ? {
              ...form,
              ...(sampleDataset && { sampleDataset: { name: sampleDataset.name, label: sampleDataset.label,
                columns: sample.data?.columns ?? sampleDataset.definition.columns.map((c) => c.label ?? c.field) } }),
            } : null}
            applicable={(reply) => reply.draft !== null && reply.draft !== undefined}
            onApply={(reply) => applyDraft(reply.draft as TemplateDraftDto)} className="h-[26rem]" />
        )}
        <Card>
          <form onSubmit={submit} className="flex h-full flex-col gap-6">
            <CardHeader>
              <CardTitle>{template ? template.name : "Yeni şablon"}</CardTitle>
              <CardDescription>
                Mustache: {"{{flow.name}}"}, {"{{run.date}}"}, {"{{steps.dataset.rowCount}}"}, satırlar için
                {" {{#steps.dataset.rows}}…{{/steps.dataset.rows}}"}.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="template-name">Ad</FieldLabel>
                  <Input id="template-name" value={form.name} maxLength={100} required onChange={(e) => set({ name: e.target.value })} />
                </Field>
                <Field>
                  <FieldLabel htmlFor="template-subject">Konu</FieldLabel>
                  <Input id="template-subject" value={form.subject} maxLength={500} required
                    onChange={(e) => set({ subject: e.target.value })} />
                </Field>
                <Field>
                  <div className="flex items-center justify-between gap-2">
                    <FieldLabel htmlFor="template-body">İçerik (HTML)</FieldLabel>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button type="button" variant="outline" size="sm">
                          <LayoutTemplateIcon data-icon="inline-start" />Hazır tasarım
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-80">
                        <DropdownMenuLabel>İçeriği bununla değiştir</DropdownMenuLabel>
                        <DropdownMenuGroup>
                          {STARTER_TEMPLATES.map((starter) => (
                            <DropdownMenuItem key={starter.name} className="flex-col items-start gap-0.5"
                              onSelect={() => set({ subject: starter.subject, bodyHtml: starter.bodyHtml,
                                ...(template || form.name ? {} : { name: starter.name }) })}>
                              <span className="font-medium">{starter.name}</span>
                              <span className="text-xs text-muted-foreground">{starter.description}</span>
                            </DropdownMenuItem>
                          ))}
                        </DropdownMenuGroup>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                  <Textarea id="template-body" className="min-h-72 font-mono text-xs" spellCheck={false} value={form.bodyHtml}
                    required onChange={(e) => set({ bodyHtml: e.target.value })} />
                  <FieldDescription>
                    Değerler otomatik olarak HTML-escape edilir. Tasarımı en üstteki {"<style>"} bloğunda sınıflarla
                    yazın; gönderirken her öğeye satır içi stil olarak eklenir (Outlook ve Gmail {"<style>"} bloğunu
                    okumaz).
                  </FieldDescription>
                </Field>
              </FieldGroup>
            </CardContent>
            <CardFooter className="mt-auto flex-col items-stretch gap-3">
              {save.isError && <ErrorAlert error={save.error} title="Kaydedilemedi" />}
              <Button type="submit" className="self-end" disabled={save.isPending}>
                {save.isPending && <Spinner data-icon="inline-start" />}Kaydet
              </Button>
            </CardFooter>
          </form>
        </Card>
      </div>
      <div className="flex min-w-0 flex-col gap-3 xl:sticky xl:top-4">
        <Field orientation="horizontal" className="items-center">
          <FieldLabel htmlFor="template-sample" className="shrink-0 whitespace-nowrap">Örnek veri</FieldLabel>
          <Select value={sampleName} onValueChange={setSampleName}>
            <SelectTrigger id="template-sample" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value={SAMPLE}>Hazır örnek (2 satır)</SelectItem>
                {datasets.data?.map((dataset) => (
                  <SelectItem key={dataset.name} value={dataset.name}>{dataset.label ?? dataset.name}</SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        {sample.isError && <ErrorAlert error={sample.error} title="Veri seti çalıştırılamadı" />}
        <TemplatePreview subject={form.subject} bodyHtml={form.bodyHtml} model={sample.data?.model ?? null}
          sampleLabel={sampleDataset ? `${sampleDataset.label ?? sampleDataset.name} ile (ilk 20 satır, sizin yetkilerinizle)`
            : undefined} />
      </div>
    </div>
  )
}

import { PlusIcon, Trash2Icon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { freeId } from "../lib/field-types"
import type { ResultsVisibility, SurveySettings, TrackingField } from "../types/survey.dto"

export const DEFAULT_SETTINGS: SurveySettings = {
  tracking: { status: true, priority: true, followUpDate: true, assignee: true, fields: [] },
  results: "owner",
}

const RESULTS: { value: ResultsVisibility; label: string; hint: string }[] = [
  { value: "owner", label: "Yalnız ben", hint: "Sonuçları sadece anket sahibi görür." },
  { value: "respondents", label: "Yanıtlayanlar", hint: "Yanıt verdikten sonra “sonuçlarda göster” işaretli sorular görünür." },
  { value: "everyone", label: "Herkes", hint: "Formu açabilen herkes işaretli soruların sonuçlarını görür (oylama gibi)." },
]

const TYPES: { value: TrackingField["type"]; label: string }[] = [
  { value: "text", label: "Metin" }, { value: "choice", label: "Seçim" }, { value: "number", label: "Sayı" },
  { value: "date", label: "Tarih" },
]

/** Who sees the results, and what the owner tracks on each response (never shown to respondents). */
export function TrackingSettings({ settings, onChange }: {
  settings: SurveySettings
  onChange: (settings: SurveySettings) => void
}) {
  const tracking = settings.tracking
  const setTracking = (change: Partial<SurveySettings["tracking"]>) =>
    onChange({ ...settings, tracking: { ...tracking, ...change } })
  const setField = (index: number, change: Partial<TrackingField>) =>
    setTracking({ fields: tracking.fields.map((field, i) => (i === index ? { ...field, ...change } : field)) })
  const toggles: { key: "status" | "priority" | "followUpDate" | "assignee"; label: string }[] = [
    { key: "status", label: "Durum (yeni / işlemde / çözüldü)" }, { key: "priority", label: "Öncelik" },
    { key: "followUpDate", label: "Takip tarihi" }, { key: "assignee", label: "Atanan kişi" },
  ]
  return (
    <div className="flex flex-col gap-6">
      <Field>
        <FieldLabel>Sonuçları kim görsün?</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={settings.results}
          onValueChange={(results) => results && onChange({ ...settings, results: results as ResultsVisibility })}>
          {RESULTS.map((item) => <ToggleGroupItem key={item.value} value={item.value}>{item.label}</ToggleGroupItem>)}
        </ToggleGroup>
        <FieldDescription>{RESULTS.find((item) => item.value === settings.results)?.hint} Metin cevapları hiçbir zaman gösterilmez.</FieldDescription>
      </Field>
      <FieldSet>
        <FieldLegend variant="label">Yanıt takibi</FieldLegend>
        <FieldDescription>Her yanıtta sizin dolduracağınız alanlar; yanıtlayan görmez.</FieldDescription>
        <div className="grid gap-3 sm:grid-cols-2">
          {toggles.map((toggle) => (
            <Field key={toggle.key} orientation="horizontal">
              <Switch id={`tracking-${toggle.key}`} checked={tracking[toggle.key]}
                onCheckedChange={(checked) => setTracking({ [toggle.key]: checked })} />
              <FieldLabel htmlFor={`tracking-${toggle.key}`} className="font-normal">{toggle.label}</FieldLabel>
            </Field>
          ))}
        </div>
      </FieldSet>
      <FieldSet>
        <FieldLegend variant="label">Ek takip alanları</FieldLegend>
        <FieldDescription>Örn. “Kayıt no”, “Kategori”, “Tahmini maliyet”.</FieldDescription>
        {tracking.fields.map((field, index) => (
          <div key={index} className="flex flex-col gap-2 rounded-md border p-2">
            <div className="flex flex-wrap items-center gap-2">
              <Input className="h-8 min-w-40 flex-1" aria-label="Alan adı" placeholder="Alan adı" value={field.label}
                maxLength={100} onChange={(e) => setField(index, { label: e.target.value })} />
              <Select value={field.type} onValueChange={(type) => setField(index, {
                type: type as TrackingField["type"],
                options: type === "choice" ? field.options ?? [{ id: "o1", label: "Seçenek 1" }, { id: "o2", label: "Seçenek 2" }] : undefined,
              })}>
                <SelectTrigger size="sm" className="w-28"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectGroup>{TYPES.map((type) => <SelectItem key={type.value} value={type.value}>{type.label}</SelectItem>)}</SelectGroup>
                </SelectContent>
              </Select>
              <Button variant="ghost" size="icon-sm" aria-label="Alanı sil"
                onClick={() => setTracking({ fields: tracking.fields.filter((_, i) => i !== index) })}><Trash2Icon /></Button>
            </div>
            {field.type === "choice" && (
              <Input className="h-8" aria-label="Seçenekler" placeholder="Seçenekler, virgülle: Donanım, Yazılım, Diğer"
                value={(field.options ?? []).map((option) => option.label).join(", ")}
                onChange={(e) => setField(index, { options: e.target.value.split(",").map((label, i) => ({
                  id: field.options?.[i]?.id ?? `o${i + 1}`, label: label.trim(),
                })).filter((option) => option.label !== "" || e.target.value.endsWith(",")) })} />
            )}
          </div>
        ))}
        <Button variant="outline" size="sm" className="self-start" onClick={() => setTracking({
          fields: [...tracking.fields, { id: freeId(tracking.fields.map((field) => field.id), "t"), label: "", type: "text" }],
        })}><PlusIcon data-icon="inline-start" />Alan ekle</Button>
      </FieldSet>
    </div>
  )
}

import {
  ArrowDownIcon, ArrowUpIcon, CopyIcon, EyeIcon, GripVerticalIcon, PlusIcon, Trash2Icon, XIcon,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardHeader } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group"
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import { Hint } from "@/shared/components/hint"
import { changeType, FIELD_TYPES, freeId, hasOptions, TEXT_TYPES, typeInfo } from "../lib/field-types"
import { describeLogic } from "../lib/logic"
import type { FieldType, SurveyField } from "../types/survey.dto"
import { ConditionEditor } from "./condition-editor"

const GROUPS = ["Metin", "Seçim", "Ölçek", "Diğer"] as const
/** Questions whose answers can be counted for respondents to see. */
const RESULT_TYPES: FieldType[] = ["singleChoice", "multipleChoice", "dropdown", "yesNo", "rating", "nps"]

/** One question in the builder: its text, type, options, limits and when it is asked. */
export function FieldEditor({ field, index, count, earlier, problems, selected, onSelect, onChange, onMove, onDuplicate,
  onRemove }: {
  field: SurveyField
  index: number
  count: number
  /** Questions before this one: what its condition may look at. */
  earlier: SurveyField[]
  /** Problems the server reported for this question. */
  problems: string[]
  selected: boolean
  onSelect: () => void
  onChange: (field: SurveyField) => void
  onMove: (to: number) => void
  onDuplicate: () => void
  onRemove: () => void
}) {
  const info = typeInfo(field.type)
  const set = (change: Partial<SurveyField>) => onChange({ ...field, ...change })
  const section = field.type === "section"
  const Icon = info.icon

  return (
    <Card size="sm" onFocusCapture={onSelect} onClick={onSelect}
      className={cn("transition-shadow", selected && "ring-2 ring-primary/40", problems.length && "ring-2 ring-destructive/50",
        section && "bg-muted/40")}>
      <CardHeader className="gap-2">
        <div className="flex min-w-0 items-center gap-2">
          <GripVerticalIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <Badge variant="secondary" className="shrink-0"><Icon data-icon="inline-start" />{info.label}</Badge>
          <span className="truncate font-mono text-xs text-muted-foreground">{field.id}</span>
          {field.visibleWhen !== undefined && field.visibleWhen !== null && (
            <Hint text={`Yalnızca şu durumda sorulur: ${describeLogic(field.visibleWhen, earlier)}`}>
              <Badge variant="outline" className="shrink-0"><EyeIcon data-icon="inline-start" />Koşullu</Badge>
            </Hint>
          )}
        </div>
        <CardAction className="flex items-center">
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Yukarı taşı" disabled={index === 0}
            onClick={() => onMove(index - 1)}><ArrowUpIcon /></Button>
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Aşağı taşı" disabled={index === count - 1}
            onClick={() => onMove(index + 1)}><ArrowDownIcon /></Button>
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Çoğalt" onClick={onDuplicate}><CopyIcon /></Button>
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Sil" onClick={onRemove}><Trash2Icon /></Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <FieldGroup className="gap-4">
          <Field>
            <FieldLabel htmlFor={`label-${field.id}`}>{section ? "Başlık" : "Soru"}</FieldLabel>
            <Input id={`label-${field.id}`} value={field.label} maxLength={500} onChange={(e) => set({ label: e.target.value })} />
          </Field>
          {selected && (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor={`type-${field.id}`}>Tip</FieldLabel>
                  <Select value={field.type} onValueChange={(type) => onChange(changeType(field, type as FieldType))}>
                    <SelectTrigger id={`type-${field.id}`} className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {GROUPS.map((group) => (
                        <SelectGroup key={group}>
                          <SelectLabel>{group}</SelectLabel>
                          {FIELD_TYPES.filter((type) => type.group === group).map((type) => (
                            <SelectItem key={type.type} value={type.type}><type.icon />{type.label}</SelectItem>
                          ))}
                        </SelectGroup>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel htmlFor={`id-${field.id}`}>Anahtar</FieldLabel>
                  <Input id={`id-${field.id}`} className="font-mono" value={field.id} maxLength={40}
                    onChange={(e) => set({ id: e.target.value.replace(/[^a-zA-Z0-9_]/g, "") })} />
                  <FieldDescription>Kurallarda ve akışlarda: <code>trigger.answers.{field.id || "…"}</code></FieldDescription>
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor={`help-${field.id}`}>Açıklama (isteğe bağlı)</FieldLabel>
                <Input id={`help-${field.id}`} value={field.help ?? ""} maxLength={1000} placeholder="Sorunun altında görünen kısa not"
                  onChange={(e) => set({ help: e.target.value || undefined })} />
              </Field>
              {hasOptions(field.type) && <OptionsEditor field={field} onChange={onChange} />}
              <Limits field={field} onChange={set} />
              {!section && (
                <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
                  {field.type !== "yesNo" && (
                    <Field orientation="horizontal" className="w-auto">
                      <Switch id={`required-${field.id}`} checked={field.required ?? false}
                        onCheckedChange={(required) => set({ required })} />
                      <FieldLabel htmlFor={`required-${field.id}`}>Zorunlu</FieldLabel>
                    </Field>
                  )}
                  {RESULT_TYPES.includes(field.type) && (
                    <Field orientation="horizontal" className="w-auto">
                      <Switch id={`results-${field.id}`} checked={field.showInResults ?? false}
                        onCheckedChange={(showInResults) => set({ showInResults: showInResults || undefined })} />
                      <FieldLabel htmlFor={`results-${field.id}`}>Sonuçlarda göster</FieldLabel>
                    </Field>
                  )}
                  <ToggleGroup type="single" variant="outline" size="sm" value={field.width ?? "full"}
                    onValueChange={(width) => width && set({ width: width === "half" ? "half" : undefined })}>
                    <ToggleGroupItem value="full">Tam satır</ToggleGroupItem>
                    <ToggleGroupItem value="half">Yarım satır</ToggleGroupItem>
                  </ToggleGroup>
                </div>
              )}
              <Collapsible defaultOpen={field.visibleWhen !== undefined && field.visibleWhen !== null}>
                <CollapsibleTrigger asChild>
                  <Button type="button" variant="ghost" size="sm" className="-ml-2 self-start">
                    <EyeIcon data-icon="inline-start" />Koşullu göster
                  </Button>
                </CollapsibleTrigger>
                <CollapsibleContent className="pt-2">
                  <ConditionEditor value={field.visibleWhen} fields={earlier} emptyLabel="Şu durumda göster…"
                    onChange={(visibleWhen) => set({ visibleWhen })} />
                </CollapsibleContent>
              </Collapsible>
            </>
          )}
          {problems.length > 0 && (
            <ul className="flex flex-col gap-1 text-sm text-destructive">
              {problems.map((problem) => <li key={problem}>{problem}</li>)}
            </ul>
          )}
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

function OptionsEditor({ field, onChange }: { field: SurveyField; onChange: (field: SurveyField) => void }) {
  const options = field.options ?? []
  const setOptions = (next: typeof options) => onChange({ ...field, options: next })
  return (
    <Field>
      <FieldLabel>Seçenekler</FieldLabel>
      <div className="flex flex-col gap-2">
        {options.map((option, index) => (
          <InputGroup key={option.id}>
            <InputGroupAddon><span className="font-mono text-xs">{option.id}</span></InputGroupAddon>
            <InputGroupInput aria-label={`Seçenek ${index + 1}`} value={option.label} maxLength={200}
              onChange={(e) => setOptions(options.map((o) => (o.id === option.id ? { ...o, label: e.target.value } : o)))}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault()
                  const id = freeId(options.map((o) => o.id), "o")
                  setOptions([...options.slice(0, index + 1), { id, label: "" }, ...options.slice(index + 1)])
                }
              }} />
            <InputGroupAddon align="inline-end">
              <InputGroupButton size="icon-xs" aria-label="Seçeneği sil" disabled={options.length <= 2}
                onClick={() => setOptions(options.filter((o) => o.id !== option.id))}><XIcon /></InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
        ))}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" size="sm"
          onClick={() => setOptions([...options, { id: freeId(options.map((o) => o.id), "o"), label: `Seçenek ${options.length + 1}` }])}>
          <PlusIcon data-icon="inline-start" />Seçenek
        </Button>
        {!options.some((o) => o.id === "other") && (
          <Button type="button" variant="ghost" size="sm" onClick={() => setOptions([...options, { id: "other", label: "Diğer" }])}>
            “Diğer” ekle
          </Button>
        )}
      </div>
      <FieldDescription>Enter ile yeni seçenek. Soldaki anahtar kurallarda kullanılır; metni değiştirmek eski yanıtları bozmaz.</FieldDescription>
    </Field>
  )
}

function Limits({ field, onChange }: { field: SurveyField; onChange: (change: Partial<SurveyField>) => void }) {
  const number = (value: string) => (value === "" ? undefined : Number(value))
  if (field.type === "number" || field.type === "multipleChoice") {
    const what = field.type === "number" ? "değer" : "seçim"
    return (
      <div className="grid grid-cols-2 gap-4">
        <Field>
          <FieldLabel htmlFor={`min-${field.id}`}>En az {what}</FieldLabel>
          <Input id={`min-${field.id}`} type="number" value={field.min ?? ""} onChange={(e) => onChange({ min: number(e.target.value) })} />
        </Field>
        <Field>
          <FieldLabel htmlFor={`max-${field.id}`}>En çok {what}</FieldLabel>
          <Input id={`max-${field.id}`} type="number" value={field.max ?? ""} onChange={(e) => onChange({ max: number(e.target.value) })} />
        </Field>
      </div>
    )
  }
  if (field.type === "rating") {
    return (
      <Field>
        <FieldLabel>Yıldız sayısı</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={String(field.max ?? 5)}
          onValueChange={(value) => value && onChange({ max: Number(value) })}>
          {[3, 4, 5, 7, 10].map((stars) => <ToggleGroupItem key={stars} value={String(stars)}>{stars}</ToggleGroupItem>)}
        </ToggleGroup>
      </Field>
    )
  }
  if (TEXT_TYPES.includes(field.type)) {
    return (
      <div className="grid gap-4 sm:grid-cols-3">
        <Field className="sm:col-span-1">
          <FieldLabel htmlFor={`placeholder-${field.id}`}>Örnek metin</FieldLabel>
          <Input id={`placeholder-${field.id}`} value={field.placeholder ?? ""} maxLength={200}
            onChange={(e) => onChange({ placeholder: e.target.value || undefined })} />
        </Field>
        <Field>
          <FieldLabel htmlFor={`minlen-${field.id}`}>En az karakter</FieldLabel>
          <Input id={`minlen-${field.id}`} type="number" min={0} value={field.minLength ?? ""}
            onChange={(e) => onChange({ minLength: number(e.target.value) })} />
        </Field>
        <Field>
          <FieldLabel htmlFor={`maxlen-${field.id}`}>En çok karakter</FieldLabel>
          <Input id={`maxlen-${field.id}`} type="number" min={1} value={field.maxLength ?? ""}
            onChange={(e) => onChange({ maxLength: number(e.target.value) })} />
        </Field>
      </div>
    )
  }
  return null
}

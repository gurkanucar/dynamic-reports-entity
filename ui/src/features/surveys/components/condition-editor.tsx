import { BracesIcon, ListIcon, PlusIcon, XIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { DatePicker } from "@/shared/components/date-picker"
import { NUMERIC_TYPES, hasOptions } from "../lib/field-types"
import {
  canCompareFields, groupToLogic, logicToGroup, needsValue, OP_LABELS, opsFor, type Condition, type ConditionGroup,
  type ConditionOp,
} from "../lib/logic"
import type { Logic, SurveyField } from "../types/survey.dto"

const VALUE = "__value__"

/**
 * Builds a condition over answers ("Departman eşittir BT ve Tutar büyüktür 10.000") as JSON Logic. What the rows
 * cannot show (written by the assistant or by hand) is edited as JSON.
 */
export function ConditionEditor({ value, fields, onChange, emptyLabel = "Koşul ekle" }: {
  value: Logic | undefined
  /** The questions the condition may look at. */
  fields: SurveyField[]
  onChange: (logic: Logic | undefined) => void
  emptyLabel?: string
}) {
  const parsed = logicToGroup(value, fields)
  const [json, setJson] = useState(parsed === null)
  const answerable = fields.filter((field) => field.type !== "section")

  if (json || parsed === null) {
    return <JsonEditor value={value} onChange={onChange} onRows={parsed === null ? undefined : () => setJson(false)} />
  }
  const group = parsed
  const update = (next: ConditionGroup) => onChange(groupToLogic(next, fields) ?? undefined)
  const add = () => {
    const field = answerable.at(-1)
    if (!field) return
    update({ ...group, conditions: [...group.conditions, { fieldId: field.id, op: opsFor(field.type)[0] }] })
  }

  return (
    <div className="flex flex-col gap-2">
      {group.conditions.length > 1 && (
        <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={group.combinator}
          onValueChange={(combinator) => combinator && update({ ...group, combinator: combinator as "and" | "or" })}>
          <ToggleGroupItem value="and">Hepsi (ve)</ToggleGroupItem>
          <ToggleGroupItem value="or">Herhangi biri (veya)</ToggleGroupItem>
        </ToggleGroup>
      )}
      {group.conditions.map((condition, index) => (
        <ConditionRow key={index} condition={condition} fields={answerable}
          onChange={(next) => update({ ...group, conditions: group.conditions.map((c, i) => (i === index ? next : c)) })}
          onRemove={() => update({ ...group, conditions: group.conditions.filter((_, i) => i !== index) })} />
      ))}
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" size="sm" onClick={add} disabled={answerable.length === 0}>
          <PlusIcon data-icon="inline-start" />{group.conditions.length ? "Koşul ekle" : emptyLabel}
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={() => setJson(true)}>
          <BracesIcon data-icon="inline-start" />JSON
        </Button>
      </div>
      {answerable.length === 0 && <FieldDescription>Önce bu sorudan önce gelen bir soru ekleyin.</FieldDescription>}
    </div>
  )
}

function ConditionRow({ condition, fields, onChange, onRemove }: {
  condition: Condition
  fields: SurveyField[]
  onChange: (condition: Condition) => void
  onRemove: () => void
}) {
  const field = fields.find((item) => item.id === condition.fieldId)
  const ops = field ? opsFor(field.type) : (["filled"] as ConditionOp[])
  const comparable = fields.filter((other) => other.id !== condition.fieldId && other.type === field?.type)

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/30 p-2">
      <Select value={condition.fieldId} onValueChange={(fieldId) => {
        const next = fields.find((item) => item.id === fieldId)
        onChange({ fieldId, op: next ? opsFor(next.type)[0] : "filled" })
      }}>
        <SelectTrigger size="sm" className="max-w-56 min-w-32"><SelectValue placeholder="Soru" /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {fields.map((item) => <SelectItem key={item.id} value={item.id}>{item.label || item.id}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
      <Select value={condition.op} onValueChange={(op) => onChange({ fieldId: condition.fieldId, op: op as ConditionOp })}>
        <SelectTrigger size="sm" className="w-40"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {ops.map((op) => <SelectItem key={op} value={op}>{OP_LABELS[op]}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
      {field && needsValue(condition.op) && (
        <>
          {canCompareFields(condition.op) && comparable.length > 0 && (
            <Select value={condition.otherField ?? VALUE}
              onValueChange={(other) => onChange({ ...condition, otherField: other === VALUE ? undefined : other,
                value: other === VALUE ? condition.value : undefined })}>
              <SelectTrigger size="sm" className="max-w-56"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={VALUE}>bir değer</SelectItem>
                  {comparable.map((other) => <SelectItem key={other.id} value={other.id}>“{other.label}” sorusu</SelectItem>)}
                </SelectGroup>
              </SelectContent>
            </Select>
          )}
          {!condition.otherField && <ValueInput field={field} condition={condition} onChange={onChange} />}
        </>
      )}
      <Button type="button" variant="ghost" size="icon-sm" className="ml-auto" aria-label="Koşulu kaldır" onClick={onRemove}>
        <XIcon />
      </Button>
    </div>
  )
}

function ValueInput({ field, condition, onChange }: {
  field: SurveyField
  condition: Condition
  onChange: (condition: Condition) => void
}) {
  if (condition.op === "oneOf") {
    const values = condition.values ?? []
    return (
      <ToggleGroup type="multiple" variant="outline" size="sm" className="flex-wrap justify-start" value={values}
        onValueChange={(next) => onChange({ ...condition, values: next })}>
        {field.options?.map((option) => <ToggleGroupItem key={option.id} value={option.id}>{option.label}</ToggleGroupItem>)}
      </ToggleGroup>
    )
  }
  if (hasOptions(field.type)) {
    return (
      <Select value={condition.value ?? ""} onValueChange={(value) => onChange({ ...condition, value })}>
        <SelectTrigger size="sm" className="max-w-56 min-w-32"><SelectValue placeholder="Seçenek" /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {field.options?.map((option) => <SelectItem key={option.id} value={option.id}>{option.label}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
    )
  }
  if (field.type === "date") {
    return <DatePicker value={condition.value || undefined} onChange={(value) => onChange({ ...condition, value })}
      className="h-8 w-40" />
  }
  return (
    <Input className="h-8 w-40" type={NUMERIC_TYPES.includes(field.type) ? "number" : "text"} placeholder="değer"
      value={condition.value ?? ""} onChange={(e) => onChange({ ...condition, value: e.target.value })} />
  )
}

function JsonEditor({ value, onChange, onRows }: {
  value: Logic | undefined
  onChange: (logic: Logic | undefined) => void
  /** Back to the rows, when they can show the rule. */
  onRows?: () => void
}) {
  const [text, setText] = useState(value === undefined ? "" : JSON.stringify(value, null, 2))
  const [error, setError] = useState<string | null>(null)
  const apply = (next: string) => {
    setText(next)
    if (!next.trim()) {
      setError(null)
      onChange(undefined)
      return
    }
    try {
      onChange(JSON.parse(next))
      setError(null)
    } catch {
      setError("Geçerli bir JSON değil.")
    }
  }
  return (
    <Field data-invalid={error ? true : undefined}>
      <Textarea className="min-h-24 font-mono text-xs" spellCheck={false} value={text} aria-invalid={error ? true : undefined}
        placeholder='{"==": [{"var": "departman"}, "it"]}' onChange={(e) => apply(e.target.value)} />
      {error ? <FieldError>{error}</FieldError> : (
        <FieldDescription>
          JSON Logic; cevaplar <code>{"{\"var\": \"soruId\"}"}</code> ile okunur. Sunucu kaydederken kontrol eder.
        </FieldDescription>
      )}
      {onRows && (
        <Button type="button" variant="ghost" size="sm" className="self-start" onClick={onRows}>
          <ListIcon data-icon="inline-start" />Satırlarla düzenle
        </Button>
      )}
    </Field>
  )
}

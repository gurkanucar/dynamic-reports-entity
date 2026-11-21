import { StarIcon } from "lucide-react"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import { DatePicker } from "@/shared/components/date-picker"
import { rows } from "../lib/field-types"
import { visibleAnswers } from "../lib/logic"
import type { Answers, AnswerValue, SurveyDefinition, SurveyField } from "../types/survey.dto"

/**
 * The questions of a survey as respondents see them: hidden questions follow their conditions as answers change,
 * two half-width questions share a row. Used by the fill page and by the builder's preview.
 */
export function SurveyForm({ definition, answers, onChange, errors, disabled, idPrefix = "answer" }: {
  definition: SurveyDefinition
  answers: Answers
  onChange: (answers: Answers) => void
  /** By question id (from `answers.<id>` problems). */
  errors?: Record<string, string>
  disabled?: boolean
  idPrefix?: string
}) {
  const { visible } = visibleAnswers(definition.fields, answers)
  const shown = definition.fields.filter((field) => visible.has(field.id))
  const set = (id: string, value: AnswerValue | undefined) => onChange({ ...answers, [id]: value })

  if (shown.length === 0) {
    return <p className="py-6 text-center text-sm text-muted-foreground">Henüz soru yok.</p>
  }
  return (
    <div className="flex flex-col gap-6">
      {rows(shown).map((row) => (
        <div key={row.map((field) => field.id).join("+")}
          className={cn("grid gap-6", row.length === 2 && "sm:grid-cols-2", row.length === 1 && row[0].width === "half" && "sm:grid-cols-2")}>
          {row.map((field) => field.type === "section" ? (
            <div key={field.id} className="flex flex-col gap-1 pt-2 sm:col-span-2">
              <h3 className="text-base font-semibold">{field.label}</h3>
              {field.help && <p className="text-sm text-muted-foreground">{field.help}</p>}
              <Separator className="mt-2" />
            </div>
          ) : (
            <Question key={field.id} field={field} id={`${idPrefix}-${field.id}`} value={answers[field.id]}
              error={errors?.[field.id]} disabled={disabled} onChange={(value) => set(field.id, value)} />
          ))}
        </div>
      ))}
    </div>
  )
}

function Question({ field, id, value, error, disabled, onChange }: {
  field: SurveyField
  id: string
  value: AnswerValue | undefined
  error?: string
  disabled?: boolean
  onChange: (value: AnswerValue | undefined) => void
}) {
  const invalid = error ? true : undefined
  const label = (
    <span>
      {field.label}
      {field.required && field.type !== "yesNo" && <span className="ml-0.5 text-destructive" aria-hidden>*</span>}
    </span>
  )
  const footer = (
    <>
      {field.help && <FieldDescription>{field.help}</FieldDescription>}
      {error && <FieldError>{error}</FieldError>}
    </>
  )

  if (field.type === "singleChoice" || field.type === "multipleChoice") {
    const selected = Array.isArray(value) ? value : []
    return (
      <FieldSet data-invalid={invalid}>
        <FieldLegend variant="label">{label}</FieldLegend>
        {field.type === "singleChoice" ? (
          <RadioGroup value={typeof value === "string" ? value : ""} disabled={disabled}
            onValueChange={(next) => onChange(next)} aria-invalid={invalid}>
            {field.options?.map((option) => (
              <Field key={option.id} orientation="horizontal">
                <RadioGroupItem value={option.id} id={`${id}-${option.id}`} />
                <FieldLabel htmlFor={`${id}-${option.id}`} className="font-normal">{option.label}</FieldLabel>
              </Field>
            ))}
          </RadioGroup>
        ) : (
          <div className="flex flex-col gap-3">
            {field.options?.map((option) => (
              <Field key={option.id} orientation="horizontal">
                <Checkbox id={`${id}-${option.id}`} checked={selected.includes(option.id)} disabled={disabled}
                  aria-invalid={invalid}
                  onCheckedChange={(checked) => onChange(checked
                    ? field.options!.map((o) => o.id).filter((o) => o === option.id || selected.includes(o))
                    : selected.filter((o) => o !== option.id))} />
                <FieldLabel htmlFor={`${id}-${option.id}`} className="font-normal">{option.label}</FieldLabel>
              </Field>
            ))}
          </div>
        )}
        {footer}
      </FieldSet>
    )
  }

  return (
    <Field data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Control field={field} id={id} value={value} invalid={invalid} disabled={disabled} onChange={onChange} />
      {footer}
    </Field>
  )
}

function Control({ field, id, value, invalid, disabled, onChange }: {
  field: SurveyField
  id: string
  value: AnswerValue | undefined
  invalid?: boolean
  disabled?: boolean
  onChange: (value: AnswerValue | undefined) => void
}) {
  const textValue = typeof value === "string" || typeof value === "number" ? String(value) : ""
  switch (field.type) {
    case "longText":
      return <Textarea id={id} value={textValue} placeholder={field.placeholder} maxLength={field.maxLength ?? 5000}
        disabled={disabled} aria-invalid={invalid} onChange={(e) => onChange(e.target.value)} className="min-h-24" />
    case "number":
      return <Input id={id} type="number" inputMode="decimal" value={textValue} min={field.min} max={field.max}
        placeholder={field.placeholder} disabled={disabled} aria-invalid={invalid}
        onChange={(e) => onChange(e.target.value === "" ? undefined : Number(e.target.value))} />
    case "dropdown":
      return (
        <Select value={typeof value === "string" ? value : ""} disabled={disabled} onValueChange={(next) => onChange(next)}>
          <SelectTrigger id={id} className="w-full" aria-invalid={invalid}><SelectValue placeholder="Seçin" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {field.options?.map((option) => <SelectItem key={option.id} value={option.id}>{option.label}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
      )
    case "date":
      return <DatePicker value={typeof value === "string" ? value : undefined} onChange={(next) => onChange(next)}
        className="w-full" />
    case "yesNo":
      return (
        <ToggleGroup id={id} type="single" variant="outline" className="justify-start" disabled={disabled}
          value={value === true ? "yes" : value === false ? "no" : ""}
          onValueChange={(next) => onChange(next === "yes" ? true : next === "no" ? false : undefined)}>
          <ToggleGroupItem value="yes" className="px-4">Evet</ToggleGroupItem>
          <ToggleGroupItem value="no" className="px-4">Hayır</ToggleGroupItem>
        </ToggleGroup>
      )
    case "rating": {
      const stars = field.max ?? 5
      const current = typeof value === "number" ? value : 0
      return (
        <div id={id} role="radiogroup" className="flex gap-1">
          {Array.from({ length: stars }, (_, index) => index + 1).map((star) => (
            <button key={star} type="button" role="radio" aria-checked={current === star} aria-label={`${star} yıldız`}
              disabled={disabled} onClick={() => onChange(current === star ? undefined : star)}
              className="rounded-md p-1 text-muted-foreground transition-colors hover:text-amber-500 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none disabled:opacity-50">
              <StarIcon className={cn("size-6", star <= current && "fill-amber-400 text-amber-500")} />
            </button>
          ))}
        </div>
      )
    }
    case "nps":
      return (
        <div className="flex flex-col gap-1">
          <ToggleGroup id={id} type="single" variant="outline" size="sm" className="flex-wrap justify-start"
            disabled={disabled} value={typeof value === "number" ? String(value) : ""}
            onValueChange={(next) => onChange(next === "" ? undefined : Number(next))}>
            {Array.from({ length: 11 }, (_, score) => (
              <ToggleGroupItem key={score} value={String(score)} className="min-w-9">{score}</ToggleGroupItem>
            ))}
          </ToggleGroup>
          <div className="flex justify-between text-xs text-muted-foreground"><span>Hiç</span><span>Kesinlikle</span></div>
        </div>
      )
    default:
      return <Input id={id} type={field.type === "email" ? "email" : field.type === "phone" ? "tel" : "text"}
        value={textValue} placeholder={field.placeholder} maxLength={field.maxLength ?? 500} disabled={disabled}
        aria-invalid={invalid} onChange={(e) => onChange(e.target.value)} />
  }
}

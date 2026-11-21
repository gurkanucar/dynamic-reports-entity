import { Trash2Icon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { findPath, OPERATOR_LABELS, SIMPLE_OPERATORS, type FieldPath } from "../lib/field-paths"
import type { FilterOperator, DatasetCondition } from "../types/dataset.types"
import { DatePicker } from "@/shared/components/date-picker"
import { FieldPicker } from "./field-picker"

const NO_VALUE = new Set(["IS_NULL", "IS_NOT_NULL"])
const LIST_VALUE = new Set(["IN", "NOT_IN"])
const DAYS = new Set(["NEXT_N_DAYS", "LAST_N_DAYS"])

/** Turns what the user typed into the JSON value the builder expects for the field type and operator. */
function parseValue(text: string, operator: FilterOperator, path: FieldPath | undefined): unknown {
  const single = (raw: string): unknown => {
    const value = raw.trim()
    if (path?.type === "NUMBER" || DAYS.has(operator)) {
      const number = Number(value.replace(",", "."))
      return Number.isNaN(number) ? value : number
    }
    if (path?.type === "BOOLEAN") return value === "true" || value.toLowerCase() === "evet"
    return value
  }
  if (LIST_VALUE.has(operator)) return text.split(";").map(single).filter((v) => v !== "")
  if (operator === "BETWEEN") return text.split(";").slice(0, 2).map(single)
  return single(text)
}

function valueText(value: unknown): string {
  if (Array.isArray(value)) return value.join("; ")
  return value === undefined || value === null ? "" : String(value)
}

/** field · operator · value */
export function ConditionRow({ condition, paths, onChange, onRemove }: {
  condition: DatasetCondition
  paths: FieldPath[]
  onChange: (condition: DatasetCondition) => void
  onRemove: () => void
}) {
  const path = findPath(paths, condition.field)
  const operator = condition.operator ?? "EQ"
  const isDateInput = path?.type === "DATE" && !LIST_VALUE.has(operator) && operator !== "BETWEEN" && !DAYS.has(operator)
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 @2xl:grid-cols-[minmax(0,2fr)_11rem_minmax(0,1.5fr)_auto]">
      <FieldPicker paths={paths} value={condition.field} className="col-span-full @2xl:col-span-1" onChange={(field) => onChange({ ...condition, field })} />
      <Select value={operator}
        onValueChange={(value) => onChange({
          ...condition, operator: value as FilterOperator, value: NO_VALUE.has(value) ? undefined : condition.value,
        })}>
        <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {SIMPLE_OPERATORS.map((op) => <SelectItem key={op} value={op}>{OPERATOR_LABELS[op]}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
      {path?.type === "BOOLEAN" && (operator === "EQ" || operator === "NOT_EQ") ? (
        <Select value={condition.value === false ? "false" : condition.value === true ? "true" : undefined}
          onValueChange={(value) => onChange({ ...condition, value: value === "true" })}>
          <SelectTrigger className="w-full" aria-label="Değer"><SelectValue placeholder="Evet / Hayır" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="true">Evet</SelectItem>
              <SelectItem value="false">Hayır</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      ) : isDateInput ? (
        <DatePicker value={typeof condition.value === "string" ? condition.value : undefined}
          onChange={(value) => onChange({ ...condition, value: value ?? "" })} />
      ) : (
      <Input disabled={NO_VALUE.has(operator)} defaultValue={valueText(condition.value)}
        key={`${operator}-${condition.field}`} type="text"
        placeholder={LIST_VALUE.has(operator) || operator === "BETWEEN" ? "değerleri ; ile ayırın"
          : DAYS.has(operator) ? "gün sayısı" : "değer"}
        onBlur={(e) => onChange({ ...condition, value: parseValue(e.target.value, operator, path) })} />
      )}
      <Button variant="ghost" size="icon" aria-label="Koşulu sil" onClick={onRemove}><Trash2Icon /></Button>
    </div>
  )
}

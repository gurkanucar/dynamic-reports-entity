import { Trash2Icon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { AGGREGATE_LABELS, OPERATOR_LABELS, type FieldPath } from "../lib/field-paths"
import type { Aggregate, DatasetCondition, FilterOperator } from "../types/dataset.types"
import { FieldPicker } from "./field-picker"

/** Aggregates a group filter can compare (LIST is not a number). */
const AGGREGATES = ["COUNT", "COUNT_DISTINCT", "SUM", "AVG", "MIN", "MAX"] as const
const OPERATORS: FilterOperator[] = ["GT", "GTE", "LT", "LTE", "EQ", "NOT_EQ", "BETWEEN"]

function parse(text: string, operator: FilterOperator): unknown {
  const number = (raw: string) => {
    const value = Number(raw.trim().replace(",", "."))
    return Number.isNaN(value) ? raw.trim() : value
  }
  return operator === "BETWEEN" ? text.split(";").slice(0, 2).map(number) : number(text)
}

/**
 * One group filter (HAVING): "Sayı(tüm satırlar) büyüktür 10", "Ortalama(Maaş) küçüktür 50000". Applies to the
 * groups of a grouped dataset, after the rows were filtered.
 */
export function HavingRow({ condition, paths, onChange, onRemove }: {
  condition: DatasetCondition
  paths: FieldPath[]
  onChange: (condition: DatasetCondition) => void
  onRemove: () => void
}) {
  const aggregate = condition.aggregate ?? "COUNT"
  const operator = condition.operator ?? "GT"
  const counts = aggregate === "COUNT"
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 @2xl:grid-cols-[9rem_minmax(0,2fr)_10rem_minmax(0,1fr)_auto]">
      <Select value={aggregate} onValueChange={(value) => onChange({ ...condition, aggregate: value as Aggregate,
        field: value === "COUNT" ? condition.field : condition.field ?? paths.find((p) => p.type === "NUMBER")?.path })}>
        <SelectTrigger className="w-full" aria-label="Hesap"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {AGGREGATES.map((value) => <SelectItem key={value} value={value}>{AGGREGATE_LABELS[value]}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
      <FieldPicker paths={counts ? paths : paths.filter((p) => p.type === "NUMBER" || p.type === "DATE"
        || aggregate === "COUNT_DISTINCT")} value={condition.field}
        allowEmpty={counts ? "tüm satırlar" : undefined} className="col-span-2 @2xl:col-span-1"
        onChange={(field) => onChange({ ...condition, field })} />
      <Select value={operator} onValueChange={(value) => onChange({ ...condition, operator: value as FilterOperator })}>
        <SelectTrigger className="w-full" aria-label="Karşılaştırma"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {OPERATORS.map((op) => <SelectItem key={op} value={op}>{OPERATOR_LABELS[op]}</SelectItem>)}
          </SelectGroup>
        </SelectContent>
      </Select>
      <Input inputMode="decimal" key={`${operator}-${aggregate}`} aria-label="Değer"
        defaultValue={Array.isArray(condition.value) ? condition.value.join("; ") : String(condition.value ?? "")}
        placeholder={operator === "BETWEEN" ? "alt; üst" : "sayı"}
        onBlur={(e) => onChange({ ...condition, value: parse(e.target.value, operator) })} />
      <Button variant="ghost" size="icon" aria-label="Grup filtresini sil" onClick={onRemove}><Trash2Icon /></Button>
    </div>
  )
}

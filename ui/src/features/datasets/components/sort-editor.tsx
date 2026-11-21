import { ArrowDownIcon, ArrowUpIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { AGGREGATE_LABELS, type FieldPath } from "../lib/field-paths"
import type { Aggregate, DatasetSort } from "../types/dataset.types"
import { FieldPicker } from "./field-picker"

const NONE = "__none__"

export function SortEditor({ sort, paths, onChange }: {
  sort: DatasetSort[]
  paths: FieldPath[]
  onChange: (sort: DatasetSort[]) => void
}) {
  const update = (index: number, change: Partial<DatasetSort>) =>
    onChange(sort.map((item, i) => (i === index ? { ...item, ...change } : item)))
  return (
    <div className="flex flex-col gap-2">
      {sort.map((item, index) => (
        <div key={index} className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-2 @2xl:grid-cols-[minmax(0,2fr)_9rem_auto_auto]">
          <FieldPicker paths={paths} value={item.field} allowEmpty="(alan yok)" className="col-span-full @2xl:col-span-1"
            onChange={(field) => update(index, { field })} />
          <Select value={item.aggregate ?? NONE}
            onValueChange={(value) => update(index, { aggregate: value === NONE ? undefined : value as Aggregate })}>
            <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value={NONE}>Değer</SelectItem>
                {Object.entries(AGGREGATE_LABELS).filter(([value]) => value !== "LIST").map(([value, label]) => (
                  <SelectItem key={value} value={value}>{label}</SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <ToggleGroup type="single" variant="outline" value={item.direction ?? "ASC"}
            onValueChange={(value) => value && update(index, { direction: value as "ASC" | "DESC" })}>
            <ToggleGroupItem value="ASC" aria-label="Artan"><ArrowUpIcon /></ToggleGroupItem>
            <ToggleGroupItem value="DESC" aria-label="Azalan"><ArrowDownIcon /></ToggleGroupItem>
          </ToggleGroup>
          <Button variant="ghost" size="icon" aria-label="Sıralamayı sil"
            onClick={() => onChange(sort.filter((_, i) => i !== index))}>
            <Trash2Icon />
          </Button>
        </div>
      ))}
      <div>
        <Button variant="outline" size="sm" onClick={() => onChange([...sort, { direction: "ASC" }])}>
          <PlusIcon data-icon="inline-start" />
          Sıralama ekle
        </Button>
      </div>
    </div>
  )
}

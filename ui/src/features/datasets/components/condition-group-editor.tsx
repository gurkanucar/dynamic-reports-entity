import { BracesIcon, ListPlusIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import { describeCondition, isGroup, isSimpleHaving, isSimplePredicate } from "../lib/definition"
import type { FieldPath } from "../lib/field-paths"
import type { DatasetCondition } from "../types/dataset.types"
import { ConditionRow } from "./condition-row"
import { HavingRow } from "./having-row"

const MAX_DEPTH = 3
const NEW_HAVING: DatasetCondition = { aggregate: "COUNT", operator: "GT", value: 0 }

/**
 * A condition group: "all of" (AND) or "any of" (OR) its conditions, which may be groups again. Conditions the
 * row cannot edit (EXISTS, field comparisons) are kept and shown as a summary line.
 */
export function ConditionGroupEditor({ group, paths, onChange, onRemove, depth = 0, having = false }: {
  group: DatasetCondition
  paths: FieldPath[]
  onChange: (group: DatasetCondition) => void
  onRemove?: () => void
  depth?: number
  /** Group filters (HAVING): rows compare an aggregate ("Sayı büyüktür 10") instead of a field. */
  having?: boolean
}) {
  const conditions = group.conditions ?? []
  const logic = group.logic ?? "AND"
  const replace = (index: number, next: DatasetCondition) =>
    onChange({ ...group, conditions: conditions.map((c, i) => (i === index ? next : c)) })
  const remove = (index: number) => onChange({ ...group, conditions: conditions.filter((_, i) => i !== index) })
  const add = (condition: DatasetCondition) => onChange({ ...group, conditions: [...conditions, condition] })

  return (
    <div className={cn("flex min-w-0 flex-col gap-2", depth > 0 && "rounded-md border border-dashed p-2 @md:p-3")}>
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={logic}
          onValueChange={(value) => value && onChange({ ...group, logic: value as "AND" | "OR" })}>
          <ToggleGroupItem value="AND">Hepsi sağlansın (VE)</ToggleGroupItem>
          <ToggleGroupItem value="OR">Herhangi biri (VEYA)</ToggleGroupItem>
        </ToggleGroup>
        {onRemove && (
          <Button variant="ghost" size="sm" className="ml-auto" onClick={onRemove}>
            <Trash2Icon data-icon="inline-start" />Grubu sil
          </Button>
        )}
      </div>
      {conditions.map((condition, index) => {
        if (isGroup(condition)) {
          return (
            <ConditionGroupEditor key={index} group={condition} paths={paths} depth={depth + 1} having={having}
              onChange={(next) => replace(index, next)} onRemove={() => remove(index)} />
          )
        }
        if (having && isSimpleHaving(condition)) {
          return (
            <HavingRow key={index} condition={condition} paths={paths}
              onChange={(next) => replace(index, next)} onRemove={() => remove(index)} />
          )
        }
        if (!having && isSimplePredicate(condition)) {
          return (
            <ConditionRow key={index} condition={condition} paths={paths}
              onChange={(next) => replace(index, next)} onRemove={() => remove(index)} />
          )
        }
        return (
          <div key={index} className="flex min-w-0 items-center gap-2 rounded-md border p-2 text-sm"
            title="JSON sekmesinden düzenlenir">
            <Badge variant="secondary"><BracesIcon data-icon="inline-start" />Gelişmiş</Badge>
            <span className="min-w-0 flex-1 truncate font-mono text-xs">{describeCondition(condition)}</span>
            <span className="hidden text-xs text-muted-foreground @xl:inline">JSON sekmesinden düzenlenir</span>
            <Button variant="ghost" size="icon" aria-label="Koşulu sil" onClick={() => remove(index)}>
              <Trash2Icon />
            </Button>
          </div>
        )
      })}
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => add(having ? NEW_HAVING : { operator: "EQ", value: "" })}>
          <PlusIcon data-icon="inline-start" />Koşul ekle
        </Button>
        {depth < MAX_DEPTH && (
          <Button variant="outline" size="sm"
            onClick={() => add({ logic: logic === "AND" ? "OR" : "AND",
              conditions: [having ? NEW_HAVING : { operator: "EQ", value: "" }] })}>
            <ListPlusIcon data-icon="inline-start" />Grup ekle
          </Button>
        )}
      </div>
    </div>
  )
}

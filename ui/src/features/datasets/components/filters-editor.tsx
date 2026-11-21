import { fromGroup, toGroup } from "../lib/definition"
import type { FieldPath } from "../lib/field-paths"
import type { DatasetCondition } from "../types/dataset.types"
import { ConditionGroupEditor } from "./condition-group-editor"

/** The dataset's `where`, edited as a tree of AND/OR groups. */
export function FiltersEditor({ where, paths, onChange, having = false }: {
  where: DatasetCondition | null | undefined
  paths: FieldPath[]
  onChange: (where: DatasetCondition | undefined) => void
  /** Edits `having` (group filters) instead of `where`. */
  having?: boolean
}) {
  return <ConditionGroupEditor group={toGroup(where)} paths={paths} having={having}
    onChange={(group) => onChange(fromGroup(group))} />
}

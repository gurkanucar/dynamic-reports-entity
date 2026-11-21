import type { DatasetCondition, DatasetDefinition } from "../types/dataset.types"

export function emptyDefinition(root: string): DatasetDefinition {
  return { root, columns: [] }
}

/** A condition the predicate row can edit: field, operator, value (no EXISTS, no field comparisons). */
export function isSimplePredicate(condition: DatasetCondition): boolean {
  return condition.conditions == null && condition.otherField == null && condition.relation == null
    && condition.aggregate == null && condition.operator !== "EXISTS" && condition.operator !== "NOT_EXISTS"
}

/** A group filter the HAVING row can edit: an aggregate compared with a value. */
export function isSimpleHaving(condition: DatasetCondition): boolean {
  return condition.conditions == null && condition.otherField == null && condition.relation == null
    && condition.aggregate != null && condition.aggregate !== "LIST" && condition.dateGroup == null
}

/** Whether any column aggregates: then the other columns are the groups and group filters apply. */
export function isGrouped(definition: DatasetDefinition): boolean {
  return definition.columns.some((column) => column.aggregate && column.aggregate !== "LIST")
}

export function isGroup(condition: DatasetCondition): boolean {
  return Array.isArray(condition.conditions)
}

/** The editor always works on a group; a single condition (or none) is shown as a group of one (or zero). */
export function toGroup(condition: DatasetCondition | null | undefined): DatasetCondition {
  if (!condition) return { logic: "AND", conditions: [] }
  if (isGroup(condition)) return { logic: condition.logic ?? "AND", conditions: condition.conditions }
  return { logic: "AND", conditions: [condition] }
}

/** Back to the smallest equivalent condition: empty groups vanish, a group of one becomes that one. */
export function fromGroup(group: DatasetCondition): DatasetCondition | undefined {
  const children = (group.conditions ?? [])
    .map((child) => (isGroup(child) ? fromGroup(child) : child))
    .filter((child): child is DatasetCondition => child !== undefined)
  if (children.length === 0) return undefined
  // a group of one condition is just that condition, unless the user chose OR for it (kept while editing)
  if (children.length === 1 && !isGroup(children[0]) && (group.logic ?? "AND") === "AND") return children[0]
  return { logic: group.logic ?? "AND", conditions: children }
}

/** One line for conditions the visual editor only shows (EXISTS, comparisons ...). */
export function describeCondition(condition: DatasetCondition): string {
  if (condition.relation) {
    return `${condition.operator === "NOT_EXISTS" ? "Hiç yok" : "En az bir tane var"}: ${String(condition.relation)}`
      + (condition.match ? " (koşullu)" : "")
  }
  if (condition.otherField) {
    return `${condition.field} ${condition.operator} ${condition.otherAggregate ? `${String(condition.otherAggregate)}(` : ""}`
      + `${String(condition.otherField)}${condition.otherAggregate ? ")" : ""}`
  }
  if (condition.aggregate) return `${condition.aggregate}(${condition.field ?? "*"}) ${condition.operator} ${String(condition.value)}`
  return JSON.stringify(condition)
}

/** Drops empty parts before the definition is sent (the builder rejects empty columns or sorts). */
export function cleanDefinition(definition: DatasetDefinition): DatasetDefinition {
  const result: DatasetDefinition = { ...definition, columns: definition.columns.filter((c) => c.field || c.aggregate) }
  if (definition.sort) {
    const sort = definition.sort.filter((s) => s.field || s.aggregate)
    if (sort.length > 0) result.sort = sort
    else delete result.sort
  }
  if (!result.where) delete result.where
  if (!result.having) delete result.having
  return result
}

export function isRunnable(definition: DatasetDefinition | null): definition is DatasetDefinition {
  return !!definition?.root && definition.columns.some((c) => c.field || c.aggregate)
}

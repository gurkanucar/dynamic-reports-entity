import type { CatalogEntityDto, CatalogResponse } from "../types/dataset.dto"
import type { FieldType } from "../types/dataset.types"

/** A field reachable from a root entity, e.g. `manager.department.name`. */
export interface FieldPath {
  path: string
  label: string
  type: FieldType
  /** The path crosses a to-many relation (aggregates over it need care). */
  toMany: boolean
  depth: number
}

/**
 * Every field reachable from `root` through at most `maxDepth` relations, for pickers. Deeper paths still work when
 * typed in the JSON editor (the backend allows 4 relations).
 */
export function fieldPaths(catalog: CatalogResponse, root: string, maxDepth = 2): FieldPath[] {
  const byName = new Map(catalog.entities.map((e) => [e.name, e]))
  const result: FieldPath[] = []
  const visit = (entity: CatalogEntityDto, prefix: string, labelPrefix: string, depth: number, toMany: boolean) => {
    for (const field of entity.fields) {
      result.push({
        path: prefix + field.name,
        label: labelPrefix + field.label,
        type: field.type,
        toMany,
        depth,
      })
    }
    if (depth >= maxDepth) return
    for (const relation of entity.relations) {
      const target = byName.get(relation.target)
      if (target) {
        visit(target, `${prefix}${relation.name}.`, `${labelPrefix}${relation.label} › `, depth + 1,
          toMany || relation.toMany)
      }
    }
  }
  const start = byName.get(root)
  if (start) visit(start, "", "", 0, false)
  return result
}

export function findPath(paths: FieldPath[], path: string | undefined): FieldPath | undefined {
  return path ? paths.find((p) => p.path === path) : undefined
}

export const AGGREGATE_LABELS: Record<string, string> = {
  COUNT: "Sayı",
  COUNT_DISTINCT: "Benzersiz sayı",
  SUM: "Toplam",
  AVG: "Ortalama",
  MIN: "En küçük",
  MAX: "En büyük",
  LIST: "Liste",
}

export const DATE_GROUP_LABELS: Record<string, string> = {
  YEAR: "Yıl",
  QUARTER: "Çeyrek",
  MONTH: "Ay",
  DAY: "Gün",
  QUARTER_OF_YEAR: "Yılın çeyreği",
  MONTH_OF_YEAR: "Yılın ayı",
}

export const OPERATOR_LABELS: Record<string, string> = {
  EQ: "eşittir",
  NOT_EQ: "eşit değildir",
  LT: "küçüktür",
  LTE: "küçük veya eşit",
  GT: "büyüktür",
  GTE: "büyük veya eşit",
  BETWEEN: "arasında",
  IN: "şunlardan biri",
  NOT_IN: "şunlardan hiçbiri",
  CONTAINS: "içerir",
  STARTS_WITH: "ile başlar",
  IS_NULL: "boş",
  IS_NOT_NULL: "dolu",
  NEXT_N_DAYS: "önümüzdeki N gün",
  LAST_N_DAYS: "son N gün",
}

/** Operators the simple filter editor offers (EXISTS needs the JSON editor). */
export const SIMPLE_OPERATORS = Object.keys(OPERATOR_LABELS)

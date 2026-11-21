// The dataset definition model, exactly as the dataset builder accepts it (POST /api/datasets/run).

export type FieldType = "TEXT" | "NUMBER" | "DATE" | "DATE_TIME" | "BOOLEAN"
export type Aggregate = "COUNT" | "COUNT_DISTINCT" | "SUM" | "AVG" | "MIN" | "MAX" | "LIST"
/** File formats of exports and flow files; Excel is what business users open. */
export type ExportFormat = "xlsx" | "csv"

export type DateGroup = "YEAR" | "QUARTER" | "MONTH" | "DAY" | "QUARTER_OF_YEAR" | "MONTH_OF_YEAR"
export type FilterOperator =
  | "EQ" | "NOT_EQ" | "LT" | "LTE" | "GT" | "GTE" | "BETWEEN" | "IN" | "NOT_IN" | "CONTAINS"
  | "STARTS_WITH" | "IS_NULL" | "IS_NOT_NULL" | "NEXT_N_DAYS" | "LAST_N_DAYS" | "EXISTS" | "NOT_EXISTS"

export interface DatasetColumn {
  field?: string
  aggregate?: Aggregate
  label?: string
  dateGroup?: DateGroup
  /** LIST of objects, child filters, limits ...: kept as they are, edited in JSON. */
  [key: string]: unknown
}

export interface DatasetCondition {
  logic?: "AND" | "OR"
  conditions?: DatasetCondition[]
  field?: string
  aggregate?: Aggregate
  operator?: FilterOperator
  value?: unknown
  dateGroup?: DateGroup
  [key: string]: unknown
}

export interface DatasetSort {
  field?: string
  aggregate?: Aggregate
  direction?: "ASC" | "DESC"
  dateGroup?: DateGroup
}

export interface DatasetDefinition {
  root: string
  columns: DatasetColumn[]
  where?: DatasetCondition | null
  having?: DatasetCondition | null
  sort?: DatasetSort[]
  perRoot?: boolean | null
}

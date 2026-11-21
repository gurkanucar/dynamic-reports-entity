import type { Aggregate, DateGroup, FieldType, FilterOperator, DatasetDefinition } from "./dataset.types"

// ---- GET /api/datasets/catalog ----

export interface CatalogFieldDto {
  name: string
  label: string
  type: FieldType
}

export interface CatalogRelationDto {
  name: string
  label: string
  target: string
  toMany: boolean
}

export interface CatalogEntityDto {
  name: string
  label: string
  fields: CatalogFieldDto[]
  relations: CatalogRelationDto[]
}

export interface CatalogResponse {
  entities: CatalogEntityDto[]
  operators: FilterOperator[]
  aggregates: Aggregate[]
  dateGroups: DateGroup[]
}

// ---- POST /api/datasets/run, /api/datasets/export ----

/** `page` is 0-based on this (older) endpoint. */
export interface RunDatasetRequest {
  definition: DatasetDefinition
  page: number
  size: number
}

export interface ResultColumnDto {
  field: string | null
  aggregate: Aggregate | null
  label: string
  type: FieldType
}

export interface DatasetResultResponse {
  columns: ResultColumnDto[]
  rows: unknown[][]
  count: number
  page: number
  size: number
  hasNext: boolean
  total: number | null
  truncatedLists: string[]
}

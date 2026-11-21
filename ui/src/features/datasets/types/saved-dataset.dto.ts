import type { DatasetDefinition } from "./dataset.types"

// ---- /api/saved-datasets: a definition under a name, always run live with the user's permissions ----

export interface SavedDatasetRequest {
  name: string
  label?: string | null
  description?: string | null
  definition: DatasetDefinition
}

export interface SavedDatasetResponse {
  name: string
  label: string | null
  description: string | null
  definition: DatasetDefinition
  version: number
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

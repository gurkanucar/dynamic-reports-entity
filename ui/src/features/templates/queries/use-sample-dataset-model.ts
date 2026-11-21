import { useQuery } from "@tanstack/react-query"
import { datasetsApi } from "@/features/datasets/api/datasets.api"
import { formatCell } from "@/shared/lib/format"
import type { SavedDatasetResponse } from "@/features/datasets/types/saved-dataset.dto"
import { templatesApi } from "../api/templates.api"

/** Rows a flow shows in the mail body by default. */
const ROWS = 20

export interface SampleDatasetModel {
  /** What the template sees, as in a flow whose dataset step is called `dataset`. */
  model: Record<string, unknown>
  columns: string[]
}

/**
 * Runs a saved dataset now (first rows, with the user's permissions) and shapes it like a flow run, so the template
 * preview shows real data instead of the two sample rows.
 */
export function useSampleDatasetModel(dataset: SavedDatasetResponse | null) {
  return useQuery({
    queryKey: ["templates", "sample-dataset", dataset?.name, dataset?.version],
    enabled: dataset !== null,
    staleTime: 60_000,
    queryFn: async (): Promise<SampleDatasetModel> => {
      const [sample, result] = await Promise.all([templatesApi.sampleModel(),
        datasetsApi.run({ definition: dataset!.definition, page: 0, size: ROWS })])
      const total = result.total ?? result.count
      const columns = result.columns.map((column) => column.label)
      return {
        columns,
        model: { ...sample, steps: { dataset: {
          rowCount: total, hasRows: total > 0, fileName: `${dataset!.name}.xlsx`, columns,
          rows: result.rows.map((row) => ({ cells: row.map((cell) => formatCell(cell)) })),
          previewTruncated: total > result.rows.length,
        } } },
      }
    },
  })
}

import { useMutation } from "@tanstack/react-query"
import { datasetsApi } from "../api/datasets.api"
import type { DatasetDefinition, ExportFormat } from "../types/dataset.types"

/** Downloads the whole dataset as Excel (preferred) or CSV. */
export function useExportDataset() {
  return useMutation({
    mutationFn: ({ definition, fileName, format }: { definition: DatasetDefinition; fileName: string; format: ExportFormat }) =>
      datasetsApi.export(definition, fileName, format),
  })
}

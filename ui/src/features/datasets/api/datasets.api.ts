import { http, saveResponseAsFile } from "@/shared/api/http-client"
import type { CatalogResponse, DatasetResultResponse, RunDatasetRequest } from "../types/dataset.dto"
import type { DatasetDefinition, ExportFormat } from "../types/dataset.types"

export const datasetsApi = {
  catalog: () => http.get<CatalogResponse>("/api/datasets/catalog"),

  run: ({ definition, page, size }: RunDatasetRequest) =>
    http.send<DatasetResultResponse>("POST", `/api/datasets/run?page=${page}&size=${size}&includeTotal=true`, definition),

  async export(definition: DatasetDefinition, fileName: string, format: ExportFormat) {
    const response = await http.raw(`/api/datasets/export?format=${format}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(definition),
    })
    await saveResponseAsFile(response, fileName, fileName)   // named after the dataset
  },
}

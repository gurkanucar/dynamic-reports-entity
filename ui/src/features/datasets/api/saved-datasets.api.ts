import { http } from "@/shared/api/http-client"
import type { Envelope } from "@/shared/types/api.types"
import type { SavedDatasetRequest, SavedDatasetResponse } from "../types/saved-dataset.dto"

const path = (name: string) => `/api/saved-datasets/${encodeURIComponent(name)}`

export const savedDatasetsApi = {
  list: async () => (await http.get<Envelope<SavedDatasetResponse[]>>("/api/saved-datasets")).data,
  get: async (name: string) => (await http.get<Envelope<SavedDatasetResponse>>(path(name))).data,
  create: async (request: SavedDatasetRequest) =>
    (await http.send<Envelope<SavedDatasetResponse>>("POST", "/api/saved-datasets", request)).data,
  update: async (name: string, request: SavedDatasetRequest) =>
    (await http.send<Envelope<SavedDatasetResponse>>("PUT", path(name), request)).data,
  remove: (name: string) => http.send<void>("DELETE", path(name)),
}

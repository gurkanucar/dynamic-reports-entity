import { http } from "@/shared/api/http-client"
import type { Envelope } from "@/shared/types/api.types"
import type { SavedDatasetRequest, SavedDatasetResponse } from "../types/saved-dataset.dto"

/** Saved datasets are addressed by id (a UUID); flows refer to them by name. */
const path = (id: string) => `/api/saved-datasets/${encodeURIComponent(id)}`

export const savedDatasetsApi = {
  list: async () => (await http.get<Envelope<SavedDatasetResponse[]>>("/api/saved-datasets")).data,
  get: async (id: string) => (await http.get<Envelope<SavedDatasetResponse>>(path(id))).data,
  create: async (request: SavedDatasetRequest) =>
    (await http.send<Envelope<SavedDatasetResponse>>("POST", "/api/saved-datasets", request)).data,
  update: async (id: string, request: SavedDatasetRequest) =>
    (await http.send<Envelope<SavedDatasetResponse>>("PUT", path(id), request)).data,
  remove: (id: string) => http.send<void>("DELETE", path(id)),
}

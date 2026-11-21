import { useQuery } from "@tanstack/react-query"
import { savedDatasetsApi } from "../api/saved-datasets.api"
import { datasetKeys } from "./dataset.keys"

/** Saved datasets everyone can use by name (in flows, too). */
export function useSavedDatasets() {
  return useQuery({ queryKey: datasetKeys.saved(), queryFn: savedDatasetsApi.list })
}

export function useSavedDataset(id: string | undefined) {
  return useQuery({
    queryKey: datasetKeys.savedOne(id ?? ""),
    queryFn: () => savedDatasetsApi.get(id!),
    enabled: !!id,
  })
}

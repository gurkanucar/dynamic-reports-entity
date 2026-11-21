import { useQuery } from "@tanstack/react-query"
import { savedDatasetsApi } from "../api/saved-datasets.api"
import { datasetKeys } from "./dataset.keys"

/** Saved datasets everyone can use by name (in flows, too). */
export function useSavedDatasets() {
  return useQuery({ queryKey: datasetKeys.saved(), queryFn: savedDatasetsApi.list })
}

export function useSavedDataset(name: string | undefined) {
  return useQuery({
    queryKey: datasetKeys.savedOne(name ?? ""),
    queryFn: () => savedDatasetsApi.get(name!),
    enabled: !!name,
  })
}

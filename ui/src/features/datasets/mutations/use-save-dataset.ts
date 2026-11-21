import { useMutation, useQueryClient } from "@tanstack/react-query"
import { savedDatasetsApi } from "../api/saved-datasets.api"
import { datasetKeys } from "../queries/dataset.keys"
import type { SavedDatasetRequest } from "../types/saved-dataset.dto"

/** Saves a new dataset, or replaces `existing` (its name stays). */
export function useSaveDataset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ existing, request }: { existing?: string; request: SavedDatasetRequest }) =>
      existing ? savedDatasetsApi.update(existing, request) : savedDatasetsApi.create(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: datasetKeys.saved() }),
  })
}

export function useDeleteDataset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => savedDatasetsApi.remove(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: datasetKeys.saved() }),
  })
}

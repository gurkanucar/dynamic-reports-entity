import { useMutation } from "@tanstack/react-query"
import { datasetsApi } from "../api/datasets.api"
import type { RunDatasetRequest } from "../types/dataset.dto"

/** Runs a page of the dataset on demand (the definition changes with every edit, so it is not cached). */
export function useRunDataset() {
  return useMutation({ mutationFn: (request: RunDatasetRequest) => datasetsApi.run(request) })
}

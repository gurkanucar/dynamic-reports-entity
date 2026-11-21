import { useQuery } from "@tanstack/react-query"
import { datasetsApi } from "../api/datasets.api"
import { datasetKeys } from "./dataset.keys"

/** Entities and fields this user may dataset on; changes only with a deployment or new permissions. */
export function useCatalog() {
  return useQuery({ queryKey: datasetKeys.catalog(), queryFn: datasetsApi.catalog, staleTime: 10 * 60_000 })
}

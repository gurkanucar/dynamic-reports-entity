import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import type { RunStatus } from "../types/flow.dto"
import { flowKeys } from "./flow.keys"

/** Runs of every visible flow, newest first; refreshed every 5 seconds. */
export function useRuns(statuses: RunStatus[], page: number) {
  return useQuery({
    queryKey: flowKeys.runs(statuses, page),
    queryFn: () => flowsApi.runs(statuses, page),
    placeholderData: keepPreviousData,
    refetchInterval: 5000,
  })
}

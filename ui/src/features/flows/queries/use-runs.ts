import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import type { RunStatus } from "../types/flow.dto"
import { flowKeys } from "./flow.keys"

/** Runs of every visible flow, newest first; refreshed every 5 seconds. */
export function useRuns(statuses: RunStatus[], page: number, filters: { q?: string; trigger?: string[]; sort?: string; size?: number } = {}) {
  return useQuery({
    queryKey: [...flowKeys.runs(statuses, page), filters],
    queryFn: () => flowsApi.runs(statuses, page, filters.size ?? 20, filters),
    placeholderData: keepPreviousData,
    refetchInterval: 5000,
  })
}

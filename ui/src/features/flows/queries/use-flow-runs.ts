import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

const ACTIVE = new Set(["queued", "running"])

/** Runs of one flow; refreshes every few seconds while one of them is still active. */
export function useFlowRuns(flowId: number, page: number) {
  return useQuery({
    queryKey: flowKeys.runsOfFlow(flowId, page),
    queryFn: () => flowsApi.runsOfFlow(flowId, page),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => (query.state.data?.data.some((run) => ACTIVE.has(run.status)) ? 3000 : false),
  })
}

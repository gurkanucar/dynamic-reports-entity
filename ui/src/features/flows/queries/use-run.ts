import { useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

/** One run with steps, files and history; polls every 2 seconds until it has finished. */
export function useRun(runId: string | null) {
  return useQuery({
    queryKey: flowKeys.run(runId ?? ""),
    queryFn: () => flowsApi.run(runId!),
    enabled: runId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === "queued" || status === "running" ? 2000 : false
    },
  })
}

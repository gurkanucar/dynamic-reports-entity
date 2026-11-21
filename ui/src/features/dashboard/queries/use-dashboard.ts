import { useQuery } from "@tanstack/react-query"
import { approvalsApi } from "@/features/approvals/api/approvals.api"
import { savedDatasetsApi } from "@/features/datasets/api/saved-datasets.api"
import { flowsApi } from "@/features/flows/api/flows.api"

/** What the home page shows, from the existing APIs; refreshed every 30 seconds. */
export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    refetchInterval: 30_000,
    queryFn: async () => {
      const [runs, pending, flows, datasets] = await Promise.all([
        flowsApi.runs([], 1, 100),
        approvalsApi.requests("mine", ["pending"], 1, 5),
        flowsApi.list(1, 1),
        savedDatasetsApi.list(),
      ])
      return {
        runs: runs.data,
        pending: pending.data,
        pendingTotal: pending.meta?.totalItems ?? pending.data.length,
        flowCount: flows.meta?.totalItems ?? flows.data.length,
        datasetCount: datasets.length,
      }
    },
  })
}

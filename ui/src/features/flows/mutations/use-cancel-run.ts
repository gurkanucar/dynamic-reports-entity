import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useCancelRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (runId: string) => flowsApi.cancel(runId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.all }),
  })
}

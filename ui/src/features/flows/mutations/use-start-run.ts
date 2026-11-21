import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useStartRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (flowId: string) => flowsApi.start(flowId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.all }),
  })
}

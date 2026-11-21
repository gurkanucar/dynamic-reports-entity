import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useRotateTriggerSecret(flowId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => flowsApi.rotateSecret(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.triggers(flowId) }),
  })
}

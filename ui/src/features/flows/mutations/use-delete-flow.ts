import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useDeleteFlow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => flowsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.all }),
  })
}

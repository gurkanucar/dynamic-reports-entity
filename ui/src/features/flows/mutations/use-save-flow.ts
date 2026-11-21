import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"
import type { FlowRequest } from "../types/flow.dto"

/** Creates a flow, or replaces it when `id` and `version` (the ETag) are given. */
export function useSaveFlow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, request }: { id?: number; version?: number; request: FlowRequest }) =>
      id !== undefined && version !== undefined ? flowsApi.replace(id, version, request) : flowsApi.create(request),
    onSuccess: (saved) => {
      queryClient.setQueryData(flowKeys.detail(saved.id), saved)
      return queryClient.invalidateQueries({ queryKey: flowKeys.all })
    },
  })
}

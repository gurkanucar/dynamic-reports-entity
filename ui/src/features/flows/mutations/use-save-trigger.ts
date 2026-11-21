import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"
import type { TriggerRequest, TriggerResponse } from "../types/flow.dto"

/** Adds a trigger to a flow (the answer carries the webhook secret, once), or replaces `existing`. */
export function useSaveTrigger(flowId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ existing, request }: { existing?: TriggerResponse; request: TriggerRequest }) =>
      existing ? { trigger: await flowsApi.replaceTrigger(existing, request), secret: null }
        : flowsApi.createTrigger(flowId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.triggers(flowId) }),
  })
}

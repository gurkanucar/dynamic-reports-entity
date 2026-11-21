import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"
import type { ScheduleRequest, ScheduleResponse } from "../types/flow.dto"

/** Adds a schedule to a flow, or replaces `existing` (its version is sent as If-Match). */
export function useSaveSchedule(flowId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ existing, request }: { existing?: ScheduleResponse; request: ScheduleRequest }) =>
      existing ? flowsApi.replaceSchedule(existing, request) : flowsApi.createSchedule(flowId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.schedules(flowId) }),
  })
}

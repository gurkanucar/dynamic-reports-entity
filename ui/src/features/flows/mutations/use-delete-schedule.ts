import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useDeleteSchedule(flowId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (scheduleId: string) => flowsApi.removeSchedule(scheduleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.schedules(flowId) }),
  })
}

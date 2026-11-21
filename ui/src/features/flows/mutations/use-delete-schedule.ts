import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"

export function useDeleteSchedule(flowId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (scheduleId: number) => flowsApi.removeSchedule(scheduleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.schedules(flowId) }),
  })
}

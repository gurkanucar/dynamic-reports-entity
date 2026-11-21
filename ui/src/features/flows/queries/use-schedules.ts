import { useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

export function useSchedules(flowId: number) {
  return useQuery({ queryKey: flowKeys.schedules(flowId), queryFn: () => flowsApi.schedules(flowId), enabled: flowId > 0 })
}

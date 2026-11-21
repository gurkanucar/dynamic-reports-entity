import { useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

export function useTriggers(flowId: number) {
  return useQuery({ queryKey: flowKeys.triggers(flowId), queryFn: () => flowsApi.triggers(flowId), enabled: flowId > 0 })
}

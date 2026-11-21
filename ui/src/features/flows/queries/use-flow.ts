import { useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

export function useFlow(id: string) {
  return useQuery({ queryKey: flowKeys.detail(id), queryFn: () => flowsApi.get(id), enabled: id !== "" })
}

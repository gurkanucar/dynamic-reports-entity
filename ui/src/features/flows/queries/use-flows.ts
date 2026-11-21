import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

export function useFlows(page: number) {
  return useQuery({ queryKey: flowKeys.list(page), queryFn: () => flowsApi.list(page), placeholderData: keepPreviousData })
}

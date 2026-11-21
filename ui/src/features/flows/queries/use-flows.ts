import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

export function useFlows(page: number, filters: { q?: string; sort?: string } = {}) {
  return useQuery({ queryKey: [...flowKeys.list(page), filters], queryFn: () => flowsApi.list(page, 20, filters),
    placeholderData: keepPreviousData })
}

import { useQuery } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "./template.keys"

export function useTemplate(id: number | null) {
  return useQuery({
    queryKey: templateKeys.detail(id ?? 0),
    queryFn: () => templatesApi.get(id!),
    enabled: id !== null,
  })
}

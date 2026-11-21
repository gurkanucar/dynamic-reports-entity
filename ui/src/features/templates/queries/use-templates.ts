import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "./template.keys"

/** Templates by name (at most 100: pickers and the templates page); `q` filters on the server. */
export function useTemplates(q?: string) {
  return useQuery({
    queryKey: [...templateKeys.list(), q ?? ""],
    queryFn: async () => (await templatesApi.list(1, 100, { q })).data,
    placeholderData: keepPreviousData,
  })
}

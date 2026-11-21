import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "./template.keys"

/** A page of templates for the templates page; `q` filters on the server. */
export function useTemplatePage(page: number, size: number, q?: string) {
  return useQuery({
    queryKey: [...templateKeys.list(), "page", page, size, q ?? ""],
    queryFn: () => templatesApi.list(page, size, { q }),
    placeholderData: keepPreviousData,
  })
}

/** Templates by name (at most 100: pickers); `q` filters on the server. */
export function useTemplates(q?: string) {
  return useQuery({
    queryKey: [...templateKeys.list(), q ?? ""],
    queryFn: async () => (await templatesApi.list(1, 100, { q })).data,
    placeholderData: keepPreviousData,
  })
}

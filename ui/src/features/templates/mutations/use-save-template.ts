import { useMutation, useQueryClient } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "../queries/template.keys"
import type { TemplateRequest } from "../types/template.dto"

/** Creates a template, or replaces it when `id` and `version` (the ETag) are given. */
export function useSaveTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, request }: { id?: number; version?: number; request: TemplateRequest }) =>
      id !== undefined && version !== undefined ? templatesApi.replace(id, version, request)
        : templatesApi.create(request),
    onSuccess: (saved) => {
      queryClient.setQueryData(templateKeys.detail(saved.id), saved)
      return queryClient.invalidateQueries({ queryKey: templateKeys.list() })
    },
  })
}

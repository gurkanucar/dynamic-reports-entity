import { useMutation, useQueryClient } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "../queries/template.keys"

export function useDeleteTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => templatesApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: templateKeys.all }),
  })
}

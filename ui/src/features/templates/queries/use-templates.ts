import { useQuery } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "./template.keys"

export function useTemplates() {
  return useQuery({ queryKey: templateKeys.list(), queryFn: async () => (await templatesApi.list()).data })
}

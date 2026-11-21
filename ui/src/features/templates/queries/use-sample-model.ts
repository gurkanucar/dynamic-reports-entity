import { useQuery } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import { templateKeys } from "./template.keys"

export function useSampleModel() {
  return useQuery({ queryKey: templateKeys.sampleModel(), queryFn: templatesApi.sampleModel, staleTime: Infinity })
}

import { useQuery } from "@tanstack/react-query"
import { assistantApi } from "../api/assistant.api"
import { assistantKeys } from "./assistant.keys"

export function useAssistantSettings() {
  return useQuery({ queryKey: assistantKeys.settings(), queryFn: assistantApi.settings, staleTime: 5 * 60_000 })
}

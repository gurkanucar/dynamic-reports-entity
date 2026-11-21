import { useMutation } from "@tanstack/react-query"
import { assistantApi } from "../api/assistant.api"
import type { AssistantKind, CreateSuggestionRequest } from "../types/assistant.dto"

/** Asks the assistant and resolves once it has answered (the API is asynchronous: 202, then polling). */
export function useCreateSuggestion(kind: AssistantKind) {
  return useMutation({
    mutationFn: async (request: CreateSuggestionRequest) =>
      assistantApi.waitFor(kind, (await assistantApi.create(kind, request)).id),
  })
}

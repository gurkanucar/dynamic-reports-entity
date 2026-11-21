import { http } from "@/shared/api/http-client"
import type { Envelope } from "@/shared/types/api.types"
import type { AssistantKind, AssistantSettingsResponse, CreateSuggestionRequest, SuggestionResponse } from "../types/assistant.dto"

const PATHS: Record<AssistantKind, string> = {
  "dataset": "/api/dataset-suggestions",
  "mail-template": "/api/mail-template-suggestions",
  "flow": "/api/flow-suggestions",
  "cron": "/api/cron-suggestions",
  "step": "/api/step-suggestions",
}

export const assistantApi = {
  settings: async () =>
    (await http.get<Envelope<AssistantSettingsResponse>>(`${PATHS.dataset}/settings`)).data,

  /** Answers 202 at once; poll {@link get} until the status is completed or failed. */
  create: async (kind: AssistantKind, request: CreateSuggestionRequest) =>
    (await http.send<Envelope<SuggestionResponse>>("POST", PATHS[kind], request)).data,

  get: async (kind: AssistantKind, id: number) =>
    (await http.get<Envelope<SuggestionResponse>>(`${PATHS[kind]}/${id}`)).data,

  /** Polls a suggestion until the assistant is done (completed or failed). */
  async waitFor(kind: AssistantKind, id: number, intervalMs = 1000): Promise<SuggestionResponse> {
    for (;;) {
      const suggestion = await assistantApi.get(kind, id)
      if (suggestion.status === "completed" || suggestion.status === "failed") return suggestion
      await new Promise((resolve) => setTimeout(resolve, intervalMs))
    }
  },
}

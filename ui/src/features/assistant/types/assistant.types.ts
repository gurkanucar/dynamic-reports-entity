import type { SuggestionReplyDto } from "./assistant.dto"

/** One bubble of the chat on screen. */
export interface ChatEntry {
  id: string
  role: "user" | "assistant"
  content: string
  /** Assistant turns that produced something to apply: shown with an "apply again" action. */
  reply?: SuggestionReplyDto
  failed?: boolean
}

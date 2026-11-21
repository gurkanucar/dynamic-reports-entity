import { SparklesIcon } from "lucide-react"
import type { ReactNode } from "react"
import {
  Conversation, ConversationContent, ConversationEmptyState, ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import { Message, MessageContent, MessageResponse } from "@/components/ai-elements/message"
import {
  PromptInput, PromptInputBody, PromptInputFooter, PromptInputSubmit, PromptInputTextarea,
} from "@/components/ai-elements/prompt-input"
import { Shimmer } from "@/components/ai-elements/shimmer"
import { Suggestion, Suggestions } from "@/components/ai-elements/suggestion"
import { Kbd, KbdGroup } from "@/components/ui/kbd"
import { cn } from "@/lib/utils"

export interface ThreadEntry {
  id: string
  role: "user" | "assistant"
  content: string
  failed?: boolean
  /** Shown under the message (links, "apply again", hand-over buttons). */
  extra?: ReactNode
}

/**
 * The conversation of an assistant, built from AI Elements: messages that stick to the bottom, markdown answers,
 * suggestions to start with, a shimmering "working" line and a prompt input whose send button stands out.
 */
export function ChatThread({ entries, busy, busyLabel, examples, onSend, input, onInput, placeholder, emptyTitle,
  emptyDescription, className }: {
  entries: ThreadEntry[]
  busy: boolean
  busyLabel: string
  examples: string[]
  onSend: (text: string) => void
  input: string
  onInput: (text: string) => void
  placeholder: string
  emptyTitle: string
  emptyDescription: string
  className?: string
}) {
  return (
    <div className={cn("flex min-h-0 flex-1 flex-col", className)}>
      <Conversation className="min-h-0 flex-1">
        <ConversationContent className="gap-5">
          {entries.length === 0 ? (
            <ConversationEmptyState icon={<SparklesIcon className="size-6 text-primary" />} title={emptyTitle}
              description={emptyDescription} className="gap-4">
              <Suggestions className="w-full">
                <div className="flex w-full flex-col gap-2">
                  {examples.map((example) => (
                    <Suggestion key={example} suggestion={example} onClick={onSend}
                      className="h-auto w-full justify-start rounded-lg py-2 text-left whitespace-normal" />
                  ))}
                </div>
              </Suggestions>
            </ConversationEmptyState>
          ) : entries.map((entry) => (
            <Message key={entry.id} from={entry.role}>
              <MessageContent className={cn(entry.failed && "border border-destructive/30 bg-destructive/10 text-destructive")}>
                {entry.role === "assistant" && !entry.failed
                  ? <MessageResponse>{entry.content}</MessageResponse>
                  : <p className="whitespace-pre-line">{entry.content}</p>}
              </MessageContent>
              {entry.extra}
            </Message>
          ))}
          {busy && (
            <Message from="assistant">
              <MessageContent>
                <Shimmer className="text-sm">{busyLabel}</Shimmer>
              </MessageContent>
            </Message>
          )}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>
      <PromptInput className="mt-3" onSubmit={(message) => onSend(message.text ?? "")}>
        <PromptInputBody>
          <PromptInputTextarea value={input} onChange={(event) => onInput(event.target.value)} placeholder={placeholder}
            maxLength={4000} />
        </PromptInputBody>
        <PromptInputFooter>
          <span className="flex items-center gap-1 text-xs whitespace-nowrap text-muted-foreground">
            <Kbd>Enter</Kbd> gönder · <KbdGroup><Kbd>Shift</Kbd><Kbd>Enter</Kbd></KbdGroup> yeni satır
          </span>
          <PromptInputSubmit status={busy ? "submitted" : undefined} disabled={busy || !input.trim()}
            aria-label="Gönder" className="shadow-sm" />
        </PromptInputFooter>
      </PromptInput>
    </div>
  )
}

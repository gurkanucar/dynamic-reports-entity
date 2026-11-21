import { CheckIcon, SparklesIcon } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChatThread, type ThreadEntry } from "./chat-thread"
import { cn } from "@/lib/utils"
import { errorMessage } from "@/shared/api/http-client"
import { assistantErrorMessage } from "../lib/assistant-errors"
import { useCreateSuggestion } from "../mutations/use-create-suggestion"
import type { AssistantKind, ChatMessage, CreateSuggestionRequest, SuggestionReplyDto, SuggestionResponse } from "../types/assistant.dto"
import type { ChatEntry } from "../types/assistant.types"

export interface AssistantChatProps {
  kind: AssistantKind
  title: string
  description: string
  model: string | null
  examples: string[]
  /** Placeholder before anything is on screen, and once there is something to change. */
  placeholder: { empty: string; change: string }
  busyLabel: string
  /** What is on screen, sent along so the assistant changes it instead of starting over. */
  current: unknown
  /** Whether a reply carries something to apply (a dataset, a template, a flow). */
  applicable: (reply: SuggestionReplyDto) => boolean
  /** Called with every applicable reply right away, and again on "apply again". */
  onApply: (reply: SuggestionReplyDto) => void
  className?: string
  /** Text to start the input with (a request handed over by the help chat: `?ask=`). */
  initialInput?: string
}

/**
 * Chat with one of the assistants. Only the conversation and what is on screen go to the server, which adds
 * metadata (catalog, names of datasets, templates, groups); never data rows. An applicable reply is applied at once;
 * the user fine-tunes it in the editor and saves.
 */
export function AssistantChat({ kind, title, description, model, examples, placeholder, busyLabel, current,
  applicable, onApply, className, initialInput }: AssistantChatProps) {
  const [entries, setEntries] = useState<ChatEntry[]>([])
  const [input, setInput] = useState(initialInput ?? "")
  const create = useCreateSuggestion(kind)
  const busy = create.isPending

  const receive = (data: SuggestionResponse) => {
    if (data.status === "failed" || !data.reply) {
      setEntries((list) => [...list, { id: `${data.id}`, role: "assistant", failed: true,
        content: assistantErrorMessage(data.error, "Asistan cevap veremedi.") }])
      return
    }
    const reply = data.reply
    const usable = applicable(reply)
    setEntries((list) => [...list, { id: `${data.id}`, role: "assistant",
      content: reply.explanation ?? reply.message ?? "", reply: usable ? reply : undefined }])
    if (usable) onApply(reply)
  }

  const send = (text: string) => {
    const content = text.trim()
    if (!content || busy) return
    const next: ChatEntry[] = [...entries, { id: crypto.randomUUID(), role: "user", content }]
    setEntries(next)
    setInput("")
    const messages: ChatMessage[] = next.filter((entry) => !entry.failed && entry.content)
      .slice(-20).map((entry) => ({ role: entry.role, content: entry.content.slice(0, 4000) }))
    const request: CreateSuggestionRequest = kind === "dataset"
      ? { messages, currentDefinition: (current ?? null) as CreateSuggestionRequest["currentDefinition"] }
      : { messages, current: current ?? null }
    create.mutate(request, {
      onSuccess: receive,
      onError: (error) => setEntries((list) => [...list, { id: crypto.randomUUID(), role: "assistant",
        failed: true, content: errorMessage(error) }]),
    })
  }

  const thread: ThreadEntry[] = entries.map((entry) => ({
    id: entry.id, role: entry.role, content: entry.content, failed: entry.failed,
    extra: entry.reply && (
      <div className="flex flex-col gap-2">
        {entry.reply.title && <span className="text-xs font-medium text-muted-foreground">{entry.reply.title}</span>}
        <DraftNotes draft={entry.reply.draft} onSuggest={entry === entries.at(-1) && !busy ? send : undefined} />
        <Button variant="secondary" size="sm" className="self-start" onClick={() => onApply(entry.reply!)}>
          <CheckIcon data-icon="inline-start" />Bu öneriyi tekrar uygula
        </Button>
      </div>
    ),
  }))

  return (
    <Card className={cn("flex min-h-[24rem] flex-col", className)}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><SparklesIcon className="text-primary" />{title}</CardTitle>
        <CardDescription>
          {description}
          {model && <Badge variant="outline" className="ml-1">{model}</Badge>}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1 flex-col">
        <ChatThread entries={thread} busy={busy} busyLabel={busyLabel} examples={examples} onSend={send}
          input={input} onInput={setInput} placeholder={current ? placeholder.change : placeholder.empty}
          emptyTitle="Ne yapmak istersiniz?" emptyDescription="Yazın ya da bir örnekle başlayın." />
      </CardContent>
    </Card>
  )
}

/** What the assistant assumed and what it suggests next (survey drafts carry them); suggestions are one click away. */
function DraftNotes({ draft, onSuggest }: { draft: unknown; onSuggest?: (text: string) => void }) {
  const notes = (draft ?? {}) as { assumptions?: unknown; suggestions?: unknown }
  const assumptions = Array.isArray(notes.assumptions) ? notes.assumptions.filter((item) => typeof item === "string") : []
  const suggestions = Array.isArray(notes.suggestions) ? notes.suggestions.filter((item) => typeof item === "string") : []
  if (assumptions.length === 0 && suggestions.length === 0) return null
  return (
    <div className="flex flex-col gap-2">
      {assumptions.length > 0 && (
        <ul className="flex flex-col gap-0.5 text-xs text-muted-foreground">
          {assumptions.map((assumption) => <li key={assumption}>Varsayım: {assumption}</li>)}
        </ul>
      )}
      {onSuggest && suggestions.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {suggestions.map((suggestion) => (
            <Button key={suggestion} variant="outline" size="xs" className="h-auto py-1 whitespace-normal text-left"
              onClick={() => onSuggest(suggestion)}>{suggestion}</Button>
          ))}
        </div>
      )}
    </div>
  )
}

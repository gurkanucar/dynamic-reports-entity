import { CheckIcon, SendIcon, SparklesIcon } from "lucide-react"
import { useState, type FormEvent, type KeyboardEvent } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/ui/input-group"
import { Bubble, BubbleContent } from "@/components/ui/bubble"
import { Kbd, KbdGroup } from "@/components/ui/kbd"
import { Message, MessageContent } from "@/components/ui/message"
import {
  MessageScroller, MessageScrollerButton, MessageScrollerContent, MessageScrollerItem, MessageScrollerProvider,
  MessageScrollerViewport,
} from "@/components/ui/message-scroller"
import { Spinner } from "@/components/ui/spinner"
import { cn } from "@/lib/utils"
import { errorMessage } from "@/shared/api/http-client"
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
}

/**
 * Chat with one of the assistants. Only the conversation and what is on screen go to the server, which adds
 * metadata (catalog, names of datasets, templates, groups); never data rows. An applicable reply is applied at once;
 * the user fine-tunes it in the editor and saves.
 */
export function AssistantChat({ kind, title, description, model, examples, placeholder, busyLabel, current,
  applicable, onApply, className }: AssistantChatProps) {
  const [entries, setEntries] = useState<ChatEntry[]>([])
  const [input, setInput] = useState("")
  const create = useCreateSuggestion(kind)
  const busy = create.isPending

  const receive = (data: SuggestionResponse) => {
    if (data.status === "failed" || !data.reply) {
      setEntries((list) => [...list, { id: `${data.id}`, role: "assistant", failed: true,
        content: data.error?.message ?? "Asistan cevap veremedi." }])
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

  const submit = (event: FormEvent) => {
    event.preventDefault()
    send(input)
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      send(input)
    }
  }

  return (
    <Card className={cn("flex min-h-[24rem] flex-col", className)}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><SparklesIcon />{title}</CardTitle>
        <CardDescription>
          {description}
          {model && <Badge variant="outline" className="ml-1">{model}</Badge>}
        </CardDescription>
      </CardHeader>
      <CardContent className="min-h-0 flex-1 overflow-hidden p-0">
        <MessageScrollerProvider>
          <MessageScroller>
            <MessageScrollerViewport>
              <MessageScrollerContent className="gap-3 px-(--card-spacing) py-1">
                {entries.length === 0 && (
                  <MessageScrollerItem>
                    <div className="flex flex-col gap-2">
                      {examples.map((example) => (
                        <Button key={example} variant="outline" size="sm"
                          className="h-auto justify-start py-2 whitespace-normal text-left" onClick={() => send(example)}>
                          {example}
                        </Button>
                      ))}
                    </div>
                  </MessageScrollerItem>
                )}
                {entries.map((entry) => (
                  <MessageScrollerItem key={entry.id} messageId={entry.id} scrollAnchor={entry.role === "user"}>
                    <Message align={entry.role === "user" ? "end" : "start"}>
                      <MessageContent>
                        <Bubble variant={entry.failed ? "destructive" : entry.role === "user" ? "default" : "muted"}>
                          <BubbleContent className="flex flex-col gap-1">
                            {entry.reply?.title && <span className="font-medium">{entry.reply.title}</span>}
                            {entry.content && <p className="whitespace-pre-line">{entry.content}</p>}
                          </BubbleContent>
                        </Bubble>
                        {entry.reply && (
                          <Button variant="secondary" size="sm" className="self-start" onClick={() => onApply(entry.reply!)}>
                            <CheckIcon data-icon="inline-start" />Bu öneriyi tekrar uygula
                          </Button>
                        )}
                      </MessageContent>
                    </Message>
                  </MessageScrollerItem>
                ))}
                {busy && (
                  <MessageScrollerItem>
                    <Message>
                      <MessageContent>
                        <Bubble variant="muted">
                          <BubbleContent className="flex items-center gap-2 text-muted-foreground">
                            <Spinner /><span className="shimmer">{busyLabel}</span>
                          </BubbleContent>
                        </Bubble>
                      </MessageContent>
                    </Message>
                  </MessageScrollerItem>
                )}
              </MessageScrollerContent>
            </MessageScrollerViewport>
            <MessageScrollerButton />
          </MessageScroller>
        </MessageScrollerProvider>
      </CardContent>
      <CardFooter>
        <form onSubmit={submit} className="w-full">
          <InputGroup>
            <InputGroupTextarea placeholder={current ? placeholder.change : placeholder.empty}
              value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={onKeyDown} maxLength={4000} rows={2} />
            <InputGroupAddon align="block-end">
              <span className="flex items-center gap-1 text-xs text-muted-foreground max-sm:hidden">
                <Kbd>Enter</Kbd> gönderir · <KbdGroup><Kbd>Shift</Kbd><Kbd>Enter</Kbd></KbdGroup> yeni satır
              </span>
              <InputGroupButton type="submit" size="sm" className="ml-auto" disabled={busy || !input.trim()}>
                <SendIcon data-icon="inline-start" />Gönder
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
        </form>
      </CardFooter>
    </Card>
  )
}

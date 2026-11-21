import { ArrowRightIcon, CircleHelpIcon, SendIcon, SparklesIcon } from "lucide-react"
import { Fragment, useState, type FormEvent, type KeyboardEvent, type ReactNode } from "react"
import { Link, useLocation, useNavigate } from "react-router"
import { Badge } from "@/components/ui/badge"
import { Bubble, BubbleContent } from "@/components/ui/bubble"
import { Button } from "@/components/ui/button"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/ui/input-group"
import { Message, MessageContent } from "@/components/ui/message"
import {
  MessageScroller, MessageScrollerButton, MessageScrollerContent, MessageScrollerItem, MessageScrollerProvider,
  MessageScrollerViewport,
} from "@/components/ui/message-scroller"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Spinner } from "@/components/ui/spinner"
import { useCreateSuggestion } from "@/features/assistant/mutations/use-create-suggestion"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import type { ChatMessage, HelpDraftDto } from "@/features/assistant/types/assistant.dto"
import { errorMessage } from "@/shared/api/http-client"
import { currentPage } from "../lib/current-page"

interface Entry {
  id: string
  role: "user" | "assistant"
  content: string
  failed?: boolean
  help?: HelpDraftDto
}

const ASSISTANT_NAMES: Record<NonNullable<HelpDraftDto["handoff"]>["assistant"], string> = {
  "dataset": "Veri seti asistanı",
  "flow": "Akış asistanı",
  "survey": "Anket asistanı",
  "mail-template": "Şablon asistanı",
}

/** Questions to start with, by the page the user is on. */
function examplesFor(path: string): string[] {
  if (path.startsWith("/datasets")) return ["Başka bir tabloyla nasıl ilişki kurarım?", "Sonucu Excel'e nasıl alırım?", "Departman başına kişi sayısı istiyorum"]
  if (path.startsWith("/flows")) return ["Bir akışı her pazartesi nasıl çalıştırırım?", "Onay adımı nasıl çalışır?", "Akışa anket adımı nasıl eklerim?"]
  if (path.startsWith("/runs")) return ["Başarısız bir çalışmanın nedenini nerede görürüm?", "Çalışmayı nasıl iptal ederim?"]
  if (path.startsWith("/approvals")) return ["Neden onaylayamıyorum?", "Onay süresi dolarsa ne olur?"]
  if (path.startsWith("/surveys")) return ["Anketi kimler görebilir?", "Yanıtları nasıl takip ederim?", "Bir soruyu koşula bağlı nasıl gösteririm?"]
  if (path.startsWith("/templates")) return ["Şablona veri seti tablosu nasıl koyarım?", "Yeni bir şablon tasarlamak istiyorum"]
  if (path.startsWith("/admin")) return ["Kullanılan bir kayıt neden silinmiyor?", "Eğitimi geciken kişileri nasıl bulurum?"]
  return ["Bu platformda neler yapabilirim?", "Haftalık rapor maili nasıl kurarım?", "Onay bekleyen işlerimi nerede görürüm?"]
}

/**
 * The help chat, a button at the bottom right of every page: questions about the pages are answered from the
 * platform's guide (the assistant knows the page the user is on, not their data). Building work goes to the
 * specialist assistant, which opens with the request already typed in.
 */
export function HelpChat() {
  const settings = useAssistantSettings()
  const [open, setOpen] = useState(false)
  if (!settings.data?.enabled) return null
  return (
    <>
      <Button size="icon-lg" aria-label="Yardım" title="Yardım"
        className="fixed right-5 bottom-5 z-40 size-12 rounded-full shadow-lg transition-transform hover:scale-105 active:scale-95"
        onClick={() => setOpen(true)}>
        <CircleHelpIcon className="size-6" />
      </Button>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent className="flex w-full flex-col gap-0 p-0 data-[side=right]:sm:max-w-md">
          <SheetHeader className="border-b">
            <SheetTitle className="flex items-center gap-2"><SparklesIcon className="size-4 text-primary" />Yardım</SheetTitle>
            <SheetDescription>
              Sayfalar ve işlemler hakkında sorun. Verilerinizi görmez.
              {settings.data.model && <Badge variant="outline" className="ml-1">{settings.data.model}</Badge>}
            </SheetDescription>
          </SheetHeader>
          <HelpConversation onLeave={() => setOpen(false)} />
        </SheetContent>
      </Sheet>
    </>
  )
}

function HelpConversation({ onLeave }: { onLeave: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [entries, setEntries] = useState<Entry[]>([])
  const [input, setInput] = useState("")
  const ask = useCreateSuggestion("help")
  const busy = ask.isPending

  const send = (text: string) => {
    const content = text.trim()
    if (!content || busy) return
    const next: Entry[] = [...entries, { id: crypto.randomUUID(), role: "user", content }]
    setEntries(next)
    setInput("")
    const messages: ChatMessage[] = next.filter((entry) => !entry.failed)
      .slice(-12).map((entry) => ({ role: entry.role, content: entry.content.slice(0, 4000) }))
    ask.mutate({ messages, current: currentPage(location.pathname) }, {
      onSuccess: (data) => setEntries((list) => [...list, data.status === "failed" || !data.reply
        ? { id: `${data.id}`, role: "assistant", failed: true, content: data.error?.message ?? "Cevap alınamadı." }
        : { id: `${data.id}`, role: "assistant", content: data.reply.message ?? "", help: data.reply.draft as HelpDraftDto }]),
      onError: (error) => setEntries((list) => [...list,
        { id: crypto.randomUUID(), role: "assistant", failed: true, content: errorMessage(error) }]),
    })
  }
  const go = (path: string) => { onLeave(); navigate(path) }

  const submit = (event: FormEvent) => { event.preventDefault(); send(input) }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(input) }
  }

  return (
    <>
      <div className="min-h-0 flex-1">
        <MessageScrollerProvider>
          <MessageScroller>
            <MessageScrollerViewport>
              <MessageScrollerContent className="gap-3 p-4">
                {entries.length === 0 && (
                  <MessageScrollerItem>
                    <div className="flex flex-col gap-2">
                      <p className="text-sm text-muted-foreground">Örneğin:</p>
                      {examplesFor(location.pathname).map((example) => (
                        <Button key={example} variant="outline" size="sm"
                          className="h-auto justify-start py-2 text-left whitespace-normal" onClick={() => send(example)}>
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
                          <BubbleContent><RichText text={entry.content} /></BubbleContent>
                        </Bubble>
                        {entry.help && entry.help.links.length > 0 && (
                          <div className="flex flex-wrap gap-1.5">
                            {entry.help.links.map((link) => (
                              <Button key={link.path} variant="outline" size="xs" asChild>
                                <Link to={link.path} onClick={onLeave}>{link.label}<ArrowRightIcon data-icon="inline-end" /></Link>
                              </Button>
                            ))}
                          </div>
                        )}
                        {entry.help?.handoff && (
                          <Button size="sm" className="self-start" onClick={() => {
                            const handoff = entry.help!.handoff!
                            const separator = handoff.path.includes("?") ? "&" : "?"
                            go(`${handoff.path}${separator}ask=${encodeURIComponent(handoff.request)}`)
                          }}>
                            <SparklesIcon data-icon="inline-start" />{ASSISTANT_NAMES[entry.help.handoff.assistant]}na aktar
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
                            <Spinner /><span className="shimmer">Yanıt hazırlanıyor…</span>
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
      </div>
      <form onSubmit={submit} className="border-t p-4">
        <InputGroup>
          <InputGroupTextarea placeholder="Sorunuzu yazın…" value={input} rows={2} maxLength={4000}
            onChange={(e) => setInput(e.target.value)} onKeyDown={onKeyDown} />
          <InputGroupAddon align="block-end">
            <InputGroupButton type="submit" size="sm" className="ml-auto" disabled={busy || !input.trim()}>
              <SendIcon data-icon="inline-start" />Gönder
            </InputGroupButton>
          </InputGroupAddon>
        </InputGroup>
      </form>
    </>
  )
}

/** The little markdown the help answers use: paragraphs, "- " / "1. " lists and **bold**. */
function RichText({ text }: { text: string }) {
  const blocks = text.split(/\n{2,}/)
  return (
    <div className="flex flex-col gap-2">
      {blocks.map((block, index) => {
        const lines = block.split("\n")
        const list = lines.every((line) => /^\s*([-*•]|\d+[.)])\s+/.test(line))
        if (list) {
          const ordered = /^\s*\d/.test(lines[0])
          const items = lines.map((line, i) => <li key={i}>{bold(line.replace(/^\s*([-*•]|\d+[.)])\s+/, ""))}</li>)
          return ordered
            ? <ol key={index} className="ml-4 list-decimal">{items}</ol>
            : <ul key={index} className="ml-4 list-disc">{items}</ul>
        }
        return <p key={index} className="whitespace-pre-line">{bold(block)}</p>
      })}
    </div>
  )
}

function bold(text: string): ReactNode {
  return text.split(/(\*\*[^*]+\*\*)/).map((part, i) =>
    part.startsWith("**") && part.endsWith("**")
      ? <strong key={i}>{part.slice(2, -2)}</strong>
      : <Fragment key={i}>{part}</Fragment>)
}

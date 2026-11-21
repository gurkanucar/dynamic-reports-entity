import { SendIcon, SparklesIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group"
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle, PopoverTrigger } from "@/components/ui/popover"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/shared/api/http-client"
import { useCreateSuggestion } from "@/features/assistant/mutations/use-create-suggestion"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"

const EXAMPLES = ["Her pazartesi 09:00", "Her gece 03:00", "Hafta içi her gün 08:30", "Her ayın 1'i saat 07:00"]

/**
 * "AI ile yaz" next to a cron field: the user writes when it should run ("her gece 3'te"), the assistant answers a
 * checked cron expression (valid, at most every 5 minutes). Hidden when the assistant is not configured.
 */
export function CronAssist({ cron, onCron }: { cron?: string; onCron: (cron: string, description: string | null) => void }) {
  const settings = useAssistantSettings()
  const ask = useCreateSuggestion("cron")
  const [open, setOpen] = useState(false)
  const [text, setText] = useState("")
  const [question, setQuestion] = useState<string | null>(null)
  if (!settings.data?.enabled) return null

  const send = (value: string) => {
    const content = value.trim()
    if (!content || ask.isPending) return
    setQuestion(null)
    ask.mutate({ messages: [{ role: "user", content }], current: cron ? { cron } : null }, {
      onSuccess: (data) => {
        const draft = data.reply?.draft as { cron: string; description: string | null } | null | undefined
        if (data.status === "completed" && draft?.cron) {
          onCron(draft.cron, draft.description)
          toast.success(draft.description ?? "Zamanlama ayarlandı", { description: draft.cron })
          setOpen(false)
          setText("")
        } else {
          setQuestion(data.reply?.message ?? data.error?.message ?? "Anlayamadım; başka türlü yazar mısınız?")
        }
      },
      onError: (error) => setQuestion(errorMessage(error)),
    })
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    event.stopPropagation()   // inside other forms (dialogs)
    send(text)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="self-start">
          <SparklesIcon data-icon="inline-start" />AI ile yaz
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80">
        <PopoverHeader>
          <PopoverTitle>Ne zaman çalışsın?</PopoverTitle>
          <PopoverDescription>Günlük dille yazın; cron ifadesine çevrilir (Europe/Istanbul saati).</PopoverDescription>
        </PopoverHeader>
        <form onSubmit={submit} className="flex flex-col gap-2">
          <InputGroup>
            <InputGroupInput autoFocus placeholder="ör. her pazartesi sabah 9" value={text} maxLength={300}
              onChange={(e) => setText(e.target.value)} />
            <InputGroupAddon align="inline-end">
              <InputGroupButton type="submit" size="icon-xs" aria-label="Çevir" disabled={ask.isPending || !text.trim()}>
                {ask.isPending ? <Spinner /> : <SendIcon />}
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
          {question && <p className="text-sm text-muted-foreground">{question}</p>}
          <div className="flex flex-wrap gap-1">
            {EXAMPLES.map((example) => (
              <Button key={example} type="button" variant="ghost" size="sm" className="h-7 px-2 text-xs"
                disabled={ask.isPending} onClick={() => { setText(example); send(example) }}>{example}</Button>
            ))}
          </div>
        </form>
      </PopoverContent>
    </Popover>
  )
}

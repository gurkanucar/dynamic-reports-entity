import { SendIcon, SparklesIcon } from "lucide-react"
import { useState, type FormEvent, type KeyboardEvent } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/ui/input-group"
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle, PopoverTrigger } from "@/components/ui/popover"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/shared/api/http-client"
import { useCreateSuggestion } from "@/features/assistant/mutations/use-create-suggestion"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import type { FlowDraftDto } from "@/features/assistant/types/assistant.dto"
import { createWithFreeName } from "../lib/flow-draft"
import type { GraphModel } from "../lib/flow-graph"
import type { FlowStepDto, StepType } from "../types/flow.dto"

/** What people typically ask of each kind of step. */
const EXAMPLES: Record<StepType, string[]> = {
  dataset: ["Sadece aktif çalışanlar olsun", "CSV yerine Excel olsun, maile 100 satır koy"],
  mail: ["alice@example.com'u cc'ye ekle", "Daha resmi bir mail metni yaz, tabloyu da koy"],
  condition: ["Kayıt sayısı 10'dan fazlaysa devam etsin", "Tutar 100.000 TL ve üstüyse"],
  filter: ["Yalnızca durumu 'OPEN' olanlar kalsın"],
  approval: ["Süre 24 saat olsun ve başlığa tutarı yaz", "Talep eden kendisi onaylayamasın"],
  webhook: ["Gövdeye run.id ve akış adını ekle"],
  kafka: ["Mesaj anahtarı talep numarası olsun"],
  survey: ["Mesajı daha kibar yaz", "Süreyi 2 gün yap"],
}

/**
 * "AI ile düzenle" on a step: the user says what to change, the assistant returns the changed step (checked inside
 * the flow like saving it) and it replaces the step on the canvas; "Geri al" puts the old one back.
 */
export function StepAssist({ step, model, flowName, onChange }: {
  step: FlowStepDto
  model: GraphModel
  flowName: string
  onChange: (step: FlowStepDto) => void
}) {
  const settings = useAssistantSettings()
  const ask = useCreateSuggestion("step")
  const [open, setOpen] = useState(false)
  const [text, setText] = useState("")
  const [answer, setAnswer] = useState<string | null>(null)
  if (!settings.data?.enabled) return null

  const send = (value: string) => {
    const content = value.trim()
    if (!content || ask.isPending) return
    setAnswer(null)
    const before = step
    ask.mutate({
      messages: [{ role: "user", content }],
      current: { flow: { name: flowName || "Yeni akış", steps: model.steps, edges: model.edges }, stepKey: step.key },
    }, {
      onSuccess: async (data) => {
        const draft = data.reply?.draft as { step: Record<string, unknown>; newTemplates: FlowDraftDto["newTemplates"] }
          | null | undefined
        if (data.status !== "completed" || !draft?.step) {
          setAnswer(data.reply?.message ?? data.error?.message ?? "Anlayamadım; başka türlü yazar mısınız?")
          return
        }
        try {
          const ids = new Map<string, string>()
          for (const template of draft.newTemplates ?? []) ids.set(template.ref, (await createWithFreeName(template)).id)
          const { templateRef, ...rest } = draft.step as Record<string, unknown> & { templateRef?: string }
          if (templateRef !== undefined) rest.templateId = ids.get(templateRef) ?? null
          // the key stays: edges and later steps refer to it
          onChange({ ...(rest as unknown as FlowStepDto), key: before.key })
          setOpen(false)
          setText("")
          toast.success("Adım güncellendi", {
            description: data.reply?.explanation ?? undefined,
            action: { label: "Geri al", onClick: () => onChange(before) },
          })
        } catch (error) {
          setAnswer(errorMessage(error))
        }
      },
      onError: (error) => setAnswer(errorMessage(error)),
    })
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    send(text)
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      send(text)
    }
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm"><SparklesIcon data-icon="inline-start" />AI ile düzenle</Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-96">
        <PopoverHeader>
          <PopoverTitle>“{step.key}” adımında ne değişsin?</PopoverTitle>
          <PopoverDescription>Yalnızca bu adım değişir; sonucu kaydetmeden önce görürsünüz.</PopoverDescription>
        </PopoverHeader>
        <form onSubmit={submit} className="flex flex-col gap-2">
          <InputGroup>
            <InputGroupTextarea autoFocus rows={2} maxLength={2000} placeholder="ör. alice@example.com'u cc'ye ekle"
              value={text} onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} />
            <InputGroupAddon align="block-end">
              <InputGroupButton type="submit" size="sm" className="ml-auto" disabled={ask.isPending || !text.trim()}>
                {ask.isPending ? <Spinner data-icon="inline-start" /> : <SendIcon data-icon="inline-start" />}
                {ask.isPending ? "Düzenleniyor…" : "Uygula"}
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
          {answer && <p className="text-sm text-muted-foreground">{answer}</p>}
          <div className="flex flex-wrap gap-1">
            {(EXAMPLES[step.type] ?? []).map((example) => (
              <Button key={example} type="button" variant="ghost" size="sm" className="h-auto px-2 py-1 text-left text-xs whitespace-normal"
                disabled={ask.isPending} onClick={() => { setText(example); send(example) }}>{example}</Button>
            ))}
          </div>
        </form>
      </PopoverContent>
    </Popover>
  )
}

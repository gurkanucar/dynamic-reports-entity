import { CalendarClockIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { useNavigate } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { ErrorAlert } from "@/shared/components/error-alert"
import type { DatasetDefinition } from "@/features/datasets/types/dataset.types"
import { DEFAULT_DATASET_TEMPLATE } from "@/features/templates/lib/default-template"
import { useSaveTemplate } from "@/features/templates/mutations/use-save-template"
import { useTemplates } from "@/features/templates/queries/use-templates"
import { CRON_PRESETS, DEFAULT_TIME_ZONE } from "../lib/cron"
import { CronAssist } from "./cron-assist"
import { useScheduleDataset } from "../mutations/use-schedule-dataset"
import type { ScheduleDatasetForm } from "../types/flow.types"

const CUSTOM = "custom"

/** Turns the dataset on screen into a flow that runs it on a schedule and mails it with the file attached. */
export function ScheduleDatasetDialog({ definition, savedName, defaultName, disabled }: {
  definition: DatasetDefinition
  /** A saved dataset: the flow uses it by name (later changes of the dataset apply to the flow too). */
  savedName?: string
  defaultName: string
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<ScheduleDatasetForm>({
    name: defaultName, templateId: null, recipients: "", cron: CRON_PRESETS[0].value, timeZone: DEFAULT_TIME_ZONE,
  })
  const [preset, setPreset] = useState<string>(CRON_PRESETS[0].value)
  const templates = useTemplates()
  const createTemplate = useSaveTemplate()
  const schedule = useScheduleDataset()
  const navigate = useNavigate()
  const set = (change: Partial<ScheduleDatasetForm>) => setForm((current) => ({ ...current, ...change }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    schedule.mutate({ form, definition, savedName }, {
      onSuccess: (flow) => {
        toast.success("Veri seti zamanlandı", { description: flow.name })
        setOpen(false)
        navigate(`/flows/${flow.id}`)
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => {
      setOpen(next)
      if (next) set({ name: defaultName })
    }}>
      <DialogTrigger asChild>
        <Button variant="outline" disabled={disabled}><CalendarClockIcon data-icon="inline-start" />Zamanla ve gönder</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-lg">
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Veri setini zamanla</DialogTitle>
            <DialogDescription>Veri seti belirlediğiniz zamanlarda sizin yetkilerinizle oluşturulur ve mail ile gönderilir.</DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="flow-name">Ad</FieldLabel>
              <Input id="flow-name" value={form.name} maxLength={100} required onChange={(e) => set({ name: e.target.value })} />
            </Field>
            <Field>
              <FieldLabel>Mail şablonu</FieldLabel>
              {templates.data && templates.data.length > 0 ? (
                <Select value={form.templateId?.toString()} onValueChange={(value) => set({ templateId: value })}>
                  <SelectTrigger className="w-full"><SelectValue placeholder="Şablon seçin" /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {templates.data.map((template) => (
                        <SelectItem key={template.id} value={template.id.toString()}>{template.name}</SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              ) : (
                <Button type="button" variant="secondary" disabled={createTemplate.isPending}
                  onClick={() => createTemplate.mutate({ request: DEFAULT_DATASET_TEMPLATE },
                    { onSuccess: (template) => set({ templateId: template.id }) })}>
                  {createTemplate.isPending && <Spinner data-icon="inline-start" />}Hazır şablonu oluştur
                </Button>
              )}
            </Field>
            <Field>
              <FieldLabel htmlFor="recipients">Alıcılar</FieldLabel>
              <Input id="recipients" placeholder="ad.soyad@sirket.com, ekip@sirket.com" value={form.recipients} required
                onChange={(e) => set({ recipients: e.target.value })} />
              <FieldDescription>Virgülle ayırın.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel>Ne zaman?</FieldLabel>
              <ToggleGroup type="single" variant="outline" className="max-w-full flex-wrap" value={preset}
                onValueChange={(value) => {
                  if (!value) return
                  setPreset(value)
                  if (value !== CUSTOM) set({ cron: value })
                }}>
                {CRON_PRESETS.map((item) => <ToggleGroupItem key={item.value} value={item.value}>{item.label}</ToggleGroupItem>)}
                <ToggleGroupItem value={CUSTOM}>Özel</ToggleGroupItem>
              </ToggleGroup>
              <CronAssist cron={form.cron} onCron={(cron) => {
                setPreset(CRON_PRESETS.some((item) => item.value === cron) ? cron : CUSTOM)
                set({ cron })
              }} />
              {preset === CUSTOM && (
                <Input placeholder="dakika saat gün ay haftanın-günü, örn. 30 7 * * MON-FRI" value={form.cron} required
                  onChange={(e) => set({ cron: e.target.value })} />
              )}
              <FieldDescription>Saat dilimi: {form.timeZone}</FieldDescription>
            </Field>
          </FieldGroup>
          {schedule.isError && <ErrorAlert error={schedule.error} title="Zamanlanamadı" />}
          {createTemplate.isError && <ErrorAlert error={createTemplate.error} title="Şablon oluşturulamadı" />}
          <DialogFooter>
            <Button type="submit" disabled={schedule.isPending || !form.templateId}>
              {schedule.isPending && <Spinner data-icon="inline-start" />}Kaydet
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

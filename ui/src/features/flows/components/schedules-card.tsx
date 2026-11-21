import { PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { errorMessage } from "@/shared/api/http-client"
import { formatDateTime } from "@/shared/lib/format"
import { DEFAULT_TIME_ZONE, describeCron } from "../lib/cron"
import { CronAssist } from "./cron-assist"
import { useDeleteSchedule } from "../mutations/use-delete-schedule"
import { useSaveSchedule } from "../mutations/use-save-schedule"
import { useSchedules } from "../queries/use-schedules"

export function SchedulesCard({ flowId }: { flowId: number }) {
  const schedules = useSchedules(flowId)
  const save = useSaveSchedule(flowId)
  const remove = useDeleteSchedule(flowId)
  const [cron, setCron] = useState("0 8 * * MON-FRI")
  const fail = (title: string) => (error: unknown) => toast.error(title, { description: errorMessage(error) })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Zamanlamalar</CardTitle>
        <CardDescription>Cron ifadeleri {DEFAULT_TIME_ZONE} saatine göre çalışır; en sık 5 dakikada bir.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {schedules.isPending && <Skeleton className="h-16 w-full" />}
        {schedules.data?.map((schedule) => (
          <div key={schedule.id} className="flex flex-wrap items-center gap-3 rounded-md border p-3 text-sm">
            <Switch checked={schedule.enabled} aria-label="Etkin"
              onCheckedChange={(enabled) => save.mutate({ existing: schedule,
                request: { cron: schedule.cron, timeZone: schedule.timeZone, enabled } },
                { onError: fail("Zamanlama değiştirilemedi") })} />
            <div className="flex min-w-0 flex-col">
              <span className="font-medium">{describeCron(schedule.cron)}</span>
              <span className="text-muted-foreground">
                {schedule.enabled ? `Sonraki: ${formatDateTime(schedule.nextFireAt)}` : "Duraklatıldı"}
                {schedule.lastSkipReason && ` · ${schedule.lastSkipReason}`}
              </span>
            </div>
            <Button variant="ghost" size="icon" className="ml-auto" aria-label="Zamanlamayı sil"
              onClick={() => remove.mutate(schedule.id, { onError: fail("Silinemedi") })}>
              <Trash2Icon />
            </Button>
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          <CronAssist cron={cron} onCron={(next) => setCron(next)} />
          <Input value={cron} onChange={(e) => setCron(e.target.value)} aria-label="Cron ifadesi" className="min-w-40 flex-1 font-mono" />
          <Button variant="outline" disabled={save.isPending}
            onClick={() => save.mutate({ request: { cron, timeZone: DEFAULT_TIME_ZONE, enabled: true } },
              { onSuccess: () => toast.success("Zamanlama eklendi"), onError: fail("Zamanlama eklenemedi") })}>
            <PlusIcon data-icon="inline-start" />Ekle
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

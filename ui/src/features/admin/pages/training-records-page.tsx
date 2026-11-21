import { AwardIcon, PlusIcon } from "lucide-react"
import { useState } from "react"
import { useSearchParams } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { DatePicker } from "@/shared/components/date-picker"
import { PageHeader } from "@/shared/components/page-header"
import { useListState } from "@/shared/lib/list-state"
import type { RecordStatus, Ref, TrainingRecord, TrainingRecordRequest } from "../api/admin.types"
import { RECORD_STATUSES, RecordStatusBadge, RenewalBadge } from "../components/badges"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { PersonPicker, TrainingSelect } from "../components/pickers"
import { useAdminDelete, useAdminList, useAdminSave } from "../lib/admin-queries"
import { fieldErrors, formatDate, numberOrNull, todayIso } from "../lib/format"

const PATH = "/api/training-records"

const COLUMNS: Column<TrainingRecord>[] = [
  { key: "person", header: "Çalışan", cell: (row) => <span className="font-medium">{row.personnel.name ?? `#${row.personnel.id}`}</span> },
  { key: "training", header: "Eğitim", cell: (row) => row.training.name },
  { key: "completed", header: "Tamamlandı", sort: "completedOn", descFirst: true, className: "whitespace-nowrap",
    cell: (row) => formatDate(row.completedOn) },
  { key: "renewal", header: "Yenileme", sort: "nextRenewalDate", className: "whitespace-nowrap",
    cell: (row) => <RenewalBadge date={row.nextRenewalDate} /> },
  { key: "score", header: "Puan", sort: "score", descFirst: true, className: "text-right tabular-nums", cell: (row) => row.score ?? "—" },
  { key: "status", header: "Durum", sort: "status", cell: (row) => <RecordStatusBadge status={row.status} /> },
]

/** /admin/training-records?trainingId=: who completed what and when it must be renewed (228.000 in the demo data). */
export function TrainingRecordsPage() {
  const [params] = useSearchParams()
  const list = useListState("nextRenewalDate")
  const [trainingId, setTrainingId] = useState<number | null>(params.get("trainingId") ? Number(params.get("trainingId")) : null)
  const [person, setPerson] = useState<Ref | null>(null)
  const [statuses, setStatuses] = useState<RecordStatus[]>([])
  const [due, setDue] = useState("all")
  const records = useAdminList<TrainingRecord>("training-records", PATH, {
    trainingId, personnelId: person?.id, status: statuses, due: due === "all" ? undefined : due,
    sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<TrainingRecord | "new" | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Eğitim kayıtları" description="Kim hangi eğitimi ne zaman tamamladı; yenilemesi geçen ve yaklaşanlar."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni kayıt</Button>} />
      <div className="flex flex-wrap items-center gap-2">
        <TrainingSelect value={trainingId} onChange={(next) => { setTrainingId(next); list.resetPage() }} noneLabel="Bütün eğitimler"
          size="sm" className="w-56" />
        <PersonPicker value={person} onChange={(next) => { setPerson(next); list.resetPage() }} placeholder="Bütün çalışanlar"
          size="sm" className="w-64" />
        <ToggleGroup type="single" variant="outline" size="sm" value={due}
          onValueChange={(next) => { if (next) { setDue(next); list.resetPage() } }}>
          <ToggleGroupItem value="all">Hepsi</ToggleGroupItem>
          <ToggleGroupItem value="overdue">Süresi geçen</ToggleGroupItem>
          <ToggleGroupItem value="soon">60 gün içinde</ToggleGroupItem>
        </ToggleGroup>
        <ToggleGroup type="multiple" variant="outline" size="sm" value={statuses}
          onValueChange={(next) => { setStatuses(next as RecordStatus[]); list.resetPage() }}>
          {(Object.keys(RECORD_STATUSES) as RecordStatus[]).map((status) => (
            <ToggleGroupItem key={status} value={status}>{RECORD_STATUSES[status].label}</ToggleGroupItem>
          ))}
        </ToggleGroup>
      </div>
      <DataTable columns={COLUMNS} data={records.data?.data} meta={records.data?.meta} loading={records.isFetching}
        error={records.error} sort={list.sort} onSort={list.setSort} onPage={list.setPage} onSize={list.setSize} onOpen={setEditing}
        filtered={trainingId !== null || person !== null || statuses.length > 0 || due !== "all"}
        empty={{ icon: AwardIcon, title: "Kayıt yok", description: "İlk eğitim kaydını ekleyin." }} />
      {editing && <RecordSheet key={editing === "new" ? "new" : editing.id} record={editing === "new" ? null : editing}
        defaultTraining={trainingId} onClose={() => setEditing(null)} />}
    </div>
  )
}

function RecordSheet({ record, defaultTraining, onClose }: { record: TrainingRecord | null; defaultTraining: number | null; onClose: () => void }) {
  const save = useAdminSave<TrainingRecord, TrainingRecordRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const [person, setPerson] = useState<Ref | null>(record?.personnel ?? null)
  const [form, setForm] = useState({
    trainingId: record?.training.id ?? defaultTraining, completedOn: record?.completedOn ?? todayIso() as string | undefined,
    nextRenewalDate: record?.nextRenewalDate ?? undefined as string | undefined, score: record?.score?.toString() ?? "",
    status: record?.status ?? "PASSED" as RecordStatus,
  })
  const set = (change: Partial<typeof form>) => setForm((current) => ({ ...current, ...change }))
  const errors = fieldErrors(save.error)
  return (
    <FormSheet open onClose={onClose} title={record ? `${record.personnel.name} · ${record.training.name}` : "Yeni eğitim kaydı"}
      saving={save.isPending} error={save.error ?? remove.error}
      onSubmit={() => save.mutate({ existing: record, body: {
        personnelId: person?.id ?? 0, trainingId: form.trainingId ?? 0, completedOn: form.completedOn ?? null,
        nextRenewalDate: form.nextRenewalDate ?? null, score: numberOrNull(form.score), status: form.status,
      } }, { onSuccess: () => { toast.success("Kayıt kaydedildi"); onClose() } })}
      deleting={remove.isPending}
      onDelete={record ? () => remove.mutate(record.id, { onSuccess: () => { toast.success("Kayıt silindi"); onClose() } }) : undefined}>
      <FieldGroup>
        <Field data-invalid={errors.personnelId ? true : undefined}>
          <FieldLabel>Çalışan</FieldLabel>
          <PersonPicker value={person} onChange={setPerson} />
          {errors.personnelId && <FieldError>{errors.personnelId}</FieldError>}
        </Field>
        <Field data-invalid={errors.trainingId ? true : undefined}>
          <FieldLabel>Eğitim</FieldLabel>
          <TrainingSelect value={form.trainingId} allowNone={false} onChange={(trainingId) => set({ trainingId })} />
          {errors.trainingId && <FieldError>{errors.trainingId}</FieldError>}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field data-invalid={errors.completedOn ? true : undefined}>
            <FieldLabel>Tamamlanma</FieldLabel>
            <DatePicker value={form.completedOn} onChange={(completedOn) => set({ completedOn })} className="w-full" />
            {errors.completedOn && <FieldError>{errors.completedOn}</FieldError>}
          </Field>
          <Field data-invalid={errors.nextRenewalDate ? true : undefined}>
            <FieldLabel>Yenileme</FieldLabel>
            <DatePicker value={form.nextRenewalDate} placeholder="Otomatik" onChange={(nextRenewalDate) => set({ nextRenewalDate })} className="w-full" />
            {errors.nextRenewalDate ? <FieldError>{errors.nextRenewalDate}</FieldError>
              : <FieldDescription>Boşsa tamamlanma + eğitimin geçerlilik süresi.</FieldDescription>}
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-[8rem_minmax(0,1fr)]">
          <Field data-invalid={errors.score ? true : undefined}>
            <FieldLabel htmlFor="record-score">Puan</FieldLabel>
            <Input id="record-score" type="number" min={0} max={100} value={form.score} onChange={(e) => set({ score: e.target.value })} />
            {errors.score && <FieldError>{errors.score}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Durum</FieldLabel>
            <ToggleGroup type="single" variant="outline" size="sm" className="justify-start" value={form.status}
              onValueChange={(status) => status && set({ status: status as RecordStatus })}>
              {(Object.keys(RECORD_STATUSES) as RecordStatus[]).map((status) => (
                <ToggleGroupItem key={status} value={status}>{RECORD_STATUSES[status].label}</ToggleGroupItem>
              ))}
            </ToggleGroup>
          </Field>
        </div>
      </FieldGroup>
    </FormSheet>
  )
}

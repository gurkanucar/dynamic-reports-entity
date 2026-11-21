import { AwardIcon, GraduationCapIcon, PlusIcon } from "lucide-react"
import { useState } from "react"
import { Link } from "react-router"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { PageHeader } from "@/shared/components/page-header"
import { SearchInput } from "@/shared/components/search-input"
import { StatusBadge } from "@/shared/components/status-badge"
import { useListState } from "@/shared/lib/list-state"
import { adminApi } from "../api/admin.api"
import type { Training, TrainingRequest } from "../api/admin.types"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { useAdminDelete, useAdminList, useAdminSave, useLookup } from "../lib/admin-queries"
import { fieldErrors, numberOrNull, orNull } from "../lib/format"

const PATH = "/api/trainings"
const ALL = "__all__"

const COLUMNS: Column<Training>[] = [
  { key: "name", header: "Eğitim", sort: "name", cell: (row) => <span className="font-medium">{row.name}</span> },
  { key: "category", header: "Kategori", sort: "category",
    cell: (row) => row.category ? <Badge variant="outline">{row.category}</Badge> : "—" },
  { key: "validity", header: "Geçerlilik", sort: "validityMonths", className: "whitespace-nowrap",
    cell: (row) => row.validityMonths ? `${row.validityMonths} ay` : <span className="text-muted-foreground">Süresiz</span> },
  { key: "mandatory", header: "Zorunlu", sort: "mandatory",
    cell: (row) => row.mandatory ? <StatusBadge tone="info">Zorunlu</StatusBadge> : <StatusBadge tone="muted">İsteğe bağlı</StatusBadge> },
  { key: "records", header: "Kayıt", className: "text-right tabular-nums", cell: (row) => row.records.toLocaleString("tr-TR") },
  { key: "overdue", header: "Süresi geçen", className: "text-right",
    cell: (row) => row.overdue > 0 ? <StatusBadge tone="danger">{row.overdue.toLocaleString("tr-TR")}</StatusBadge>
      : <span className="text-muted-foreground">0</span> },
]

/** /admin/trainings: the course catalog. */
export function TrainingsPage() {
  const list = useListState("name")
  const [category, setCategory] = useState(ALL)
  const [mandatory, setMandatory] = useState("all")
  const categories = useLookup("training-categories", adminApi.trainingCategories)
  const trainings = useAdminList<Training>("trainings", PATH, {
    q: list.query || undefined, category: category === ALL ? undefined : category,
    mandatory: mandatory === "all" ? undefined : mandatory, sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<Training | "new" | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Eğitimler" description="Eğitim kataloğu: geçerlilik süresi, zorunluluk ve kaç kişinin süresinin geçtiği."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni eğitim</Button>} />
      <div className="flex flex-wrap items-center gap-2">
        <Select value={category} onValueChange={(next) => { setCategory(next); list.resetPage() }}>
          <SelectTrigger size="sm" className="w-48" aria-label="Kategori"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL}>Bütün kategoriler</SelectItem>
              {categories.data?.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
        <ToggleGroup type="single" variant="outline" size="sm" value={mandatory}
          onValueChange={(next) => { if (next) { setMandatory(next); list.resetPage() } }}>
          <ToggleGroupItem value="all">Hepsi</ToggleGroupItem>
          <ToggleGroupItem value="true">Zorunlu</ToggleGroupItem>
          <ToggleGroupItem value="false">İsteğe bağlı</ToggleGroupItem>
        </ToggleGroup>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Eğitim veya kategori…" />
      </div>
      <DataTable columns={COLUMNS} data={trainings.data?.data} meta={trainings.data?.meta} loading={trainings.isFetching}
        error={trainings.error} sort={list.sort} onSort={list.setSort} onPage={list.setPage} onSize={list.setSize} onOpen={setEditing}
        filtered={!!list.query || category !== ALL || mandatory !== "all"}
        empty={{ icon: GraduationCapIcon, title: "Eğitim yok", description: "Kataloğa ilk eğitimi ekleyin." }} />
      {editing && <TrainingSheet key={editing === "new" ? "new" : editing.id} training={editing === "new" ? null : editing}
        categories={categories.data ?? []} onClose={() => setEditing(null)} />}
    </div>
  )
}

function TrainingSheet({ training, categories, onClose }: { training: Training | null; categories: string[]; onClose: () => void }) {
  const save = useAdminSave<Training, TrainingRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const [form, setForm] = useState({
    name: training?.name ?? "", category: training?.category ?? "", validityMonths: training?.validityMonths?.toString() ?? "",
    mandatory: training?.mandatory ?? false, internalNotes: training?.internalNotes ?? "",
  })
  const set = (change: Partial<typeof form>) => setForm((current) => ({ ...current, ...change }))
  const errors = fieldErrors(save.error)
  return (
    <FormSheet open onClose={onClose} title={training ? training.name : "Yeni eğitim"}
      description={training ? (
        <Link className="inline-flex items-center gap-1 underline underline-offset-4" to={`/admin/training-records?trainingId=${training.id}`}>
          <AwardIcon className="size-3.5" />{training.records.toLocaleString("tr-TR")} kaydı gör
        </Link>
      ) : undefined}
      saving={save.isPending} error={save.error ?? remove.error}
      onSubmit={() => save.mutate({ existing: training, body: {
        name: form.name, category: orNull(form.category), validityMonths: numberOrNull(form.validityMonths),
        mandatory: form.mandatory, internalNotes: orNull(form.internalNotes),
      } }, { onSuccess: () => { toast.success("Eğitim kaydedildi"); onClose() } })}
      deleting={remove.isPending}
      onDelete={training ? () => remove.mutate(training.id, { onSuccess: () => { toast.success("Eğitim silindi"); onClose() } }) : undefined}
      deleteText={training?.records ? `${training.records} eğitim kaydı var; sunucu silmeyi reddedecek.` : undefined}>
      <FieldGroup>
        <Field data-invalid={errors.name ? true : undefined}>
          <FieldLabel htmlFor="training-name">Ad</FieldLabel>
          <Input id="training-name" value={form.name} maxLength={255} aria-invalid={!!errors.name} onChange={(e) => set({ name: e.target.value })} />
          {errors.name && <FieldError>{errors.name}</FieldError>}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="training-category">Kategori</FieldLabel>
            <Input id="training-category" list="training-categories" value={form.category} onChange={(e) => set({ category: e.target.value })} />
            <datalist id="training-categories">{categories.map((item) => <option key={item} value={item} />)}</datalist>
          </Field>
          <Field data-invalid={errors.validityMonths ? true : undefined}>
            <FieldLabel htmlFor="training-validity">Geçerlilik (ay)</FieldLabel>
            <Input id="training-validity" type="number" min={1} max={120} value={form.validityMonths} placeholder="Süresiz"
              aria-invalid={!!errors.validityMonths} onChange={(e) => set({ validityMonths: e.target.value })} />
            {errors.validityMonths ? <FieldError>{errors.validityMonths}</FieldError>
              : <FieldDescription>Yenileme tarihi tamamlanma + bu süre olarak hesaplanır.</FieldDescription>}
          </Field>
        </div>
        <Field orientation="horizontal">
          <Switch id="training-mandatory" checked={form.mandatory} onCheckedChange={(mandatory) => set({ mandatory })} />
          <FieldLabel htmlFor="training-mandatory">Zorunlu eğitim</FieldLabel>
        </Field>
        <Field>
          <FieldLabel htmlFor="training-notes">İç notlar</FieldLabel>
          <Textarea id="training-notes" value={form.internalNotes} maxLength={255} className="min-h-20"
            onChange={(e) => set({ internalNotes: e.target.value })} />
          <FieldDescription>Eğitim ekibine özel; veri setlerinde hiç görünmez.</FieldDescription>
        </Field>
      </FieldGroup>
    </FormSheet>
  )
}

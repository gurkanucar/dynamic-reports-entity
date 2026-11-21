import { Building2Icon, PlusIcon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { PageHeader } from "@/shared/components/page-header"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import type { Department, DepartmentRequest, Ref } from "../api/admin.types"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { DepartmentSelect, PersonPicker } from "../components/pickers"
import { useAdminDelete, useAdminList, useAdminSave } from "../lib/admin-queries"
import { fieldErrors, orNull } from "../lib/format"

const PATH = "/api/departments"

const count = (value: number | undefined) =>
  value ? <Badge variant="secondary" className="tabular-nums">{value}</Badge> : <span className="text-muted-foreground">0</span>

const COLUMNS: Column<Department>[] = [
  { key: "name", header: "Departman", sort: "name", cell: (row) => (
    <div className="flex flex-col">
      <span className="font-medium">{row.name}</span>
      {row.costCenter && <span className="text-xs text-muted-foreground">{row.costCenter}</span>}
    </div>
  ) },
  { key: "parent", header: "Üst departman", cell: (row) => row.parent?.name ?? <span className="text-muted-foreground">—</span> },
  { key: "manager", header: "Yönetici", cell: (row) => row.manager?.name ?? <span className="text-muted-foreground">—</span> },
  { key: "people", header: "Çalışan", className: "text-right", cell: (row) => count(row.usage["çalışan"]) },
  { key: "projects", header: "Proje", className: "text-right", cell: (row) => count(row.usage["proje"]) },
  { key: "children", header: "Alt departman", className: "text-right", cell: (row) => count(row.subDepartments) },
]

/** /admin/departments */
export function DepartmentsPage() {
  const list = useListState("name")
  const [roots, setRoots] = useState(false)
  const departments = useAdminList<Department>("departments", PATH, {
    q: list.query || undefined, roots: roots ? "true" : undefined, sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<Department | "new" | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Departmanlar" description="Şirketin departman ağacı, yöneticileri ve masraf merkezleri."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni departman</Button>} />
      <div className="flex flex-wrap items-center gap-3">
        <Field orientation="horizontal" className="w-auto">
          <Switch id="roots" checked={roots} onCheckedChange={(next) => { setRoots(next); list.resetPage() }} />
          <FieldLabel htmlFor="roots" className="font-normal">Yalnız üst seviye</FieldLabel>
        </Field>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Ad veya masraf merkezi…" />
      </div>
      <DataTable columns={COLUMNS} data={departments.data?.data} meta={departments.data?.meta}
        loading={departments.isFetching} error={departments.error} sort={list.sort} onSort={list.setSort}
        onPage={list.setPage} onSize={list.setSize} onOpen={setEditing} filtered={!!list.query || roots}
        empty={{ icon: Building2Icon, title: "Departman yok", description: "İlk departmanı ekleyin." }} />
      {editing && <DepartmentSheet key={editing === "new" ? "new" : editing.id} department={editing === "new" ? null : editing}
        onClose={() => setEditing(null)} />}
    </div>
  )
}

function DepartmentSheet({ department, onClose }: { department: Department | null; onClose: () => void }) {
  const save = useAdminSave<Department, DepartmentRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const [name, setName] = useState(department?.name ?? "")
  const [costCenter, setCostCenter] = useState(department?.costCenter ?? "")
  const [parentId, setParentId] = useState<number | null>(department?.parent?.id ?? null)
  const [manager, setManager] = useState<Ref | null>(department?.manager ?? null)
  const errors = fieldErrors(save.error)
  const submit = () => save.mutate({ existing: department, body: {
    name, costCenter: orNull(costCenter), parentId, managerId: manager?.id ?? null,
  } }, { onSuccess: () => { toast.success("Departman kaydedildi"); onClose() } })
  const inUse = department ? Object.values(department.usage).some(Boolean) || department.subDepartments > 0 : false
  return (
    <FormSheet open onClose={onClose} title={department ? department.name : "Yeni departman"}
      description={department ? `Departman #${department.id}` : "Ağaçta yeri, yöneticisi ve masraf merkezi."}
      saving={save.isPending} error={save.error ?? remove.error}
      onSubmit={submit} deleting={remove.isPending}
      onDelete={department ? () => remove.mutate(department.id, { onSuccess: () => { toast.success("Departman silindi"); onClose() } }) : undefined}
      deleteText={inUse ? "Bu departman kullanılıyor (çalışan, proje ya da alt departman); sunucu silmeyi reddedecek." : undefined}>
      <FieldGroup>
        <Field data-invalid={errors.name ? true : undefined}>
          <FieldLabel htmlFor="department-name">Ad</FieldLabel>
          <Input id="department-name" value={name} required maxLength={255} aria-invalid={!!errors.name}
            onChange={(e) => setName(e.target.value)} />
          {errors.name && <FieldError>{errors.name}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="department-cost">Masraf merkezi</FieldLabel>
          <Input id="department-cost" value={costCenter} maxLength={255} placeholder="CC-1000"
            onChange={(e) => setCostCenter(e.target.value)} />
        </Field>
        <Field data-invalid={errors.parentId ? true : undefined}>
          <FieldLabel htmlFor="department-parent">Üst departman</FieldLabel>
          <DepartmentSelect id="department-parent" value={parentId} onChange={setParentId} noneLabel="Yok (üst seviye)" />
          {errors.parentId && <FieldError>{errors.parentId}</FieldError>}
        </Field>
        <Field data-invalid={errors.managerId ? true : undefined}>
          <FieldLabel htmlFor="department-manager">Yönetici</FieldLabel>
          <PersonPicker id="department-manager" value={manager} onChange={setManager} />
          {errors.managerId && <FieldError>{errors.managerId}</FieldError>}
        </Field>
      </FieldGroup>
    </FormSheet>
  )
}

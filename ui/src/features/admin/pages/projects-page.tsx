import { BriefcaseIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { errorMessage } from "@/shared/api/http-client"
import { DatePicker } from "@/shared/components/date-picker"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import type { Assignment, AssignmentRequest, Project, ProjectRequest, ProjectStatus, Ref } from "../api/admin.types"
import { PROJECT_STATUSES, ProjectStatusBadge } from "../components/badges"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { DepartmentSelect, PersonPicker } from "../components/pickers"
import { useAdminDelete, useAdminList, useAdminSave } from "../lib/admin-queries"
import { fieldErrors, formatDate, formatMoney, numberOrNull, orNull } from "../lib/format"

const PATH = "/api/projects"

const COLUMNS: Column<Project>[] = [
  { key: "code", header: "Kod", sort: "code", cell: (row) => <span className="font-mono text-xs">{row.code}</span> },
  { key: "name", header: "Proje", sort: "name", cell: (row) => <span className="font-medium">{row.name}</span> },
  { key: "status", header: "Durum", sort: "status", cell: (row) => <ProjectStatusBadge status={row.status} /> },
  { key: "department", header: "Departman", className: "hidden lg:table-cell", cell: (row) => row.department?.name ?? "—" },
  { key: "budget", header: "Bütçe", sort: "budget", descFirst: true, className: "text-right tabular-nums whitespace-nowrap",
    cell: (row) => formatMoney(row.budget) },
  { key: "dates", header: "Tarihler", sort: "startDate", descFirst: true, className: "hidden whitespace-nowrap md:table-cell",
    cell: (row) => `${formatDate(row.startDate)} – ${formatDate(row.endDate)}` },
  { key: "team", header: "Ekip", className: "text-right", cell: (row) => (
    <span className="whitespace-nowrap tabular-nums">{row.assignments} kişi
      {row.assignments > 0 && <span className="text-muted-foreground"> · %{row.allocation}</span>}</span>
  ) },
]

/** /admin/projects */
export function ProjectsPage() {
  const list = useListState("code")
  const [statuses, setStatuses] = useState<ProjectStatus[]>([])
  const [departmentId, setDepartmentId] = useState<number | null>(null)
  const projects = useAdminList<Project>("projects", PATH, {
    q: list.query || undefined, status: statuses, departmentId, sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<Project | "new" | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Projeler" description="Projeler, bütçeleri, sahibi departmanlar ve proje ekipleri."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni proje</Button>} />
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup type="multiple" variant="outline" size="sm" className="flex-wrap" value={statuses}
          onValueChange={(next) => { setStatuses(next as ProjectStatus[]); list.resetPage() }}>
          {(Object.keys(PROJECT_STATUSES) as ProjectStatus[]).map((status) => (
            <ToggleGroupItem key={status} value={status}>{PROJECT_STATUSES[status].label}</ToggleGroupItem>
          ))}
        </ToggleGroup>
        <DepartmentSelect value={departmentId} onChange={(next) => { setDepartmentId(next); list.resetPage() }}
          noneLabel="Bütün departmanlar" size="sm" className="w-56" />
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Kod veya proje adı…" />
      </div>
      <DataTable columns={COLUMNS} data={projects.data?.data} meta={projects.data?.meta} loading={projects.isFetching}
        error={projects.error} sort={list.sort} onSort={list.setSort} onPage={list.setPage} onSize={list.setSize} onOpen={setEditing}
        filtered={!!list.query || statuses.length > 0 || departmentId !== null}
        empty={{ icon: BriefcaseIcon, title: "Proje yok", description: "İlk projeyi ekleyin." }} />
      {editing && <ProjectSheet key={editing === "new" ? "new" : editing.id} project={editing === "new" ? null : editing}
        onClose={() => setEditing(null)} />}
    </div>
  )
}

function ProjectSheet({ project, onClose }: { project: Project | null; onClose: () => void }) {
  const save = useAdminSave<Project, ProjectRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const [form, setForm] = useState({
    code: project?.code ?? "", name: project?.name ?? "", status: project?.status ?? "PLANNED" as ProjectStatus,
    budget: project?.budget?.toString() ?? "", startDate: project?.startDate ?? undefined as string | undefined,
    endDate: project?.endDate ?? undefined as string | undefined, departmentId: project?.department?.id ?? null as number | null,
  })
  const set = (change: Partial<typeof form>) => setForm((current) => ({ ...current, ...change }))
  const errors = fieldErrors(save.error)
  const submit = () => save.mutate({ existing: project, body: {
    code: form.code, name: form.name, status: form.status, budget: numberOrNull(form.budget),
    startDate: form.startDate ?? null, endDate: form.endDate ?? null, departmentId: form.departmentId,
  } }, { onSuccess: () => { toast.success("Proje kaydedildi"); onClose() } })

  const general = (
    <FieldGroup>
      <div className="grid gap-4 sm:grid-cols-[10rem_minmax(0,1fr)]">
        <Field data-invalid={errors.code ? true : undefined}>
          <FieldLabel htmlFor="project-code">Kod</FieldLabel>
          <Input id="project-code" className="font-mono uppercase" value={form.code} maxLength={40} placeholder="PRJ-101"
            aria-invalid={!!errors.code} onChange={(e) => set({ code: e.target.value })} />
          {errors.code && <FieldError>{errors.code}</FieldError>}
        </Field>
        <Field data-invalid={errors.name ? true : undefined}>
          <FieldLabel htmlFor="project-name">Ad</FieldLabel>
          <Input id="project-name" value={form.name} maxLength={255} aria-invalid={!!errors.name} onChange={(e) => set({ name: e.target.value })} />
          {errors.name && <FieldError>{errors.name}</FieldError>}
        </Field>
      </div>
      <Field>
        <FieldLabel>Durum</FieldLabel>
        <ToggleGroup type="single" variant="outline" size="sm" className="flex-wrap justify-start" value={form.status}
          onValueChange={(status) => status && set({ status: status as ProjectStatus })}>
          {(Object.keys(PROJECT_STATUSES) as ProjectStatus[]).map((status) => (
            <ToggleGroupItem key={status} value={status}>{PROJECT_STATUSES[status].label}</ToggleGroupItem>
          ))}
        </ToggleGroup>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field data-invalid={errors.budget ? true : undefined}>
          <FieldLabel htmlFor="project-budget">Bütçe (TL)</FieldLabel>
          <Input id="project-budget" type="number" min={0} value={form.budget} onChange={(e) => set({ budget: e.target.value })} />
          {errors.budget && <FieldError>{errors.budget}</FieldError>}
        </Field>
        <Field data-invalid={errors.departmentId ? true : undefined}>
          <FieldLabel htmlFor="project-department">Sahibi departman</FieldLabel>
          <DepartmentSelect id="project-department" value={form.departmentId} onChange={(departmentId) => set({ departmentId })} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel>Başlangıç</FieldLabel>
          <DatePicker value={form.startDate} onChange={(startDate) => set({ startDate })} className="w-full" />
        </Field>
        <Field data-invalid={errors.endDate ? true : undefined}>
          <FieldLabel>Bitiş</FieldLabel>
          <DatePicker value={form.endDate} onChange={(endDate) => set({ endDate })} className="w-full" />
          {errors.endDate && <FieldError>{errors.endDate}</FieldError>}
        </Field>
      </div>
    </FieldGroup>
  )
  return (
    <FormSheet open onClose={onClose} wide title={project ? `${project.code} · ${project.name}` : "Yeni proje"}
      saving={save.isPending} error={save.error ?? remove.error}
      onSubmit={submit} deleting={remove.isPending}
      onDelete={project ? () => remove.mutate(project.id, { onSuccess: () => { toast.success("Proje silindi"); onClose() } }) : undefined}
      deleteText={project?.assignments ? `Proje ve ${project.assignments} ekip ataması silinir.` : undefined}>
      {project ? (
        <Tabs defaultValue="general">
          <TabsList className="w-full">
            <TabsTrigger value="general">Genel</TabsTrigger>
            <TabsTrigger value="team">Ekip <Badge variant="secondary">{project.assignments}</Badge></TabsTrigger>
          </TabsList>
          <TabsContent value="general" className="pt-4">{general}</TabsContent>
          <TabsContent value="team" className="pt-4"><Team projectId={project.id} /></TabsContent>
        </Tabs>
      ) : general}
    </FormSheet>
  )
}

/** The project's people: add someone, change role or allocation in place, remove. */
function Team({ projectId }: { projectId: number }) {
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const assignments = useAdminList<Assignment>(`assignments-${projectId}`, `${PATH}/${projectId}/assignments`, { page, size })
  const add = useAdminSave<Assignment, AssignmentRequest>(`${PATH}/${projectId}/assignments`)
  const update = useAdminSave<Assignment, AssignmentRequest>("/api/project-assignments")
  const remove = useAdminDelete("/api/project-assignments")
  const [person, setPerson] = useState<Ref | null>(null)
  const [role, setRole] = useState("")
  const [allocation, setAllocation] = useState("50")
  const fail = (title: string) => (error: unknown) => toast.error(title, { description: errorMessage(error) })
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-2 rounded-lg border p-3">
        <Field className="min-w-48 flex-1">
          <FieldLabel>Kişi</FieldLabel>
          <PersonPicker value={person} onChange={setPerson} size="sm" />
        </Field>
        <Field className="w-36">
          <FieldLabel htmlFor="assign-role">Rol</FieldLabel>
          <Input id="assign-role" className="h-8" value={role} placeholder="Geliştirici" onChange={(e) => setRole(e.target.value)} />
        </Field>
        <Field className="w-24">
          <FieldLabel htmlFor="assign-allocation">Pay %</FieldLabel>
          <Input id="assign-allocation" className="h-8" type="number" min={0} max={100} value={allocation}
            onChange={(e) => setAllocation(e.target.value)} />
        </Field>
        <Button type="button" size="sm" disabled={!person || add.isPending} onClick={() => person && add.mutate({ body: {
          personnelId: person.id, role: orNull(role), allocationPercent: numberOrNull(allocation) } }, {
          onSuccess: () => { setPerson(null); setRole(""); toast.success("Ekibe eklendi") },
          onError: fail("Eklenemedi"),
        })}><PlusIcon data-icon="inline-start" />Ekle</Button>
      </div>
      {assignments.data?.data.length === 0 && <p className="text-sm text-muted-foreground">Henüz kimse atanmadı.</p>}
      {(assignments.data?.data.length ?? 0) > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Kişi</TableHead><TableHead>Rol</TableHead><TableHead className="w-24">Pay %</TableHead>
              <TableHead className="hidden sm:table-cell">Atandı</TableHead><TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {assignments.data?.data.map((row) => (
              <TableRow key={row.id}>
                <TableCell className="max-w-48 truncate font-medium">{row.personnel.name}</TableCell>
                <TableCell>
                  <Input className="h-7" defaultValue={row.role ?? ""} aria-label="Rol" onBlur={(e) => e.target.value !== (row.role ?? "")
                    && update.mutate({ existing: row, body: { personnelId: row.personnel.id, role: orNull(e.target.value),
                      allocationPercent: row.allocationPercent, assignedOn: row.assignedOn } }, { onError: fail("Değiştirilemedi") })} />
                </TableCell>
                <TableCell>
                  <Input className="h-7" type="number" min={0} max={100} defaultValue={row.allocationPercent ?? ""} aria-label="Pay"
                    onBlur={(e) => e.target.value !== String(row.allocationPercent ?? "") && update.mutate({ existing: row, body: {
                      personnelId: row.personnel.id, role: row.role, allocationPercent: numberOrNull(e.target.value),
                      assignedOn: row.assignedOn } }, { onError: fail("Değiştirilemedi") })} />
                </TableCell>
                <TableCell className="hidden whitespace-nowrap sm:table-cell">{formatDate(row.assignedOn)}</TableCell>
                <TableCell>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label="Ekipten çıkar"
                    onClick={() => remove.mutate(row.id, { onError: fail("Çıkarılamadı") })}><Trash2Icon /></Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <Pager meta={assignments.data?.meta} onPage={setPage} onSize={(next) => { setSize(next); setPage(1) }} />
    </div>
  )
}


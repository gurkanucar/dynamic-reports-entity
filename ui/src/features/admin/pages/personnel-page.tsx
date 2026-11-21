import { PlusIcon, UserRoundIcon } from "lucide-react"
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { errorMessage } from "@/shared/api/http-client"
import { DatePicker } from "@/shared/components/date-picker"
import { PageHeader } from "@/shared/components/page-header"
import { SearchInput } from "@/shared/components/search-input"
import { StatusBadge } from "@/shared/components/status-badge"
import { useListState } from "@/shared/lib/list-state"
import { adminApi } from "../api/admin.api"
import type { Personnel, PersonnelDetail, PersonnelRequest, Ref } from "../api/admin.types"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { DepartmentSelect, PersonPicker } from "../components/pickers"
import { useAdminDelete, useAdminList, useAdminSave, useLookup } from "../lib/admin-queries"
import { fieldErrors, formatDate, formatMoney, numberOrNull, orNull } from "../lib/format"

const PATH = "/api/personnel"
const BLOOD_TYPES = ["O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"]
const NONE = "__none__"

/** /admin/personnel: 100.000 people in the demo data, so everything is filtered and paged on the server. */
export function PersonnelPage() {
  const user = useCurrentUser()
  const canSeeSalary = user.data?.authorities.includes("SALARY_VIEW") ?? false
  const list = useListState("fullName")
  const [departmentId, setDepartmentId] = useState<number | null>(null)
  const [active, setActive] = useState("all")
  const [hiredFrom, setHiredFrom] = useState<string | undefined>()
  const [hiredTo, setHiredTo] = useState<string | undefined>()
  const people = useAdminList<Personnel>("personnel", PATH, {
    q: list.query || undefined, departmentId: departmentId ? [departmentId] : [],
    active: active === "all" ? undefined : active, hiredFrom, hiredTo, sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<Personnel | "new" | null>(null)
  const filtered = !!list.query || departmentId !== null || active !== "all" || !!hiredFrom || !!hiredTo

  const columns: Column<Personnel>[] = [
    { key: "name", header: "Ad soyad", sort: "fullName", cell: (row) => (
      <div className="flex max-w-72 flex-col">
        <span className="truncate font-medium">{row.fullName}</span>
        <span className="truncate text-xs text-muted-foreground">{row.email ?? row.username ?? "—"}</span>
      </div>
    ) },
    { key: "department", header: "Departman", cell: (row) => row.department?.name ?? "—" },
    { key: "manager", header: "Yönetici", className: "hidden lg:table-cell", cell: (row) => row.manager?.name ?? <span className="text-muted-foreground">—</span> },
    { key: "hireDate", header: "İşe giriş", sort: "hireDate", descFirst: true, className: "whitespace-nowrap", cell: (row) => formatDate(row.hireDate) },
    { key: "active", header: "Durum", sort: "active", cell: (row) => row.active
      ? <StatusBadge tone="success">Aktif</StatusBadge> : <StatusBadge tone="muted">Pasif</StatusBadge> },
    { key: "skills", header: "Beceri", className: "hidden text-right md:table-cell", cell: (row) => <span className="tabular-nums">{row.skillCount}</span> },
    ...(canSeeSalary ? [{ key: "salary", header: "Maaş", sort: "salary", descFirst: true, className: "text-right whitespace-nowrap tabular-nums",
      cell: (row: Personnel) => formatMoney(row.salary) }] : []),
  ]

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Personel" description="Çalışanlar, departmanları, yöneticileri, profilleri ve becerileri."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni personel</Button>} />
      <div className="flex flex-wrap items-center gap-2">
        <DepartmentSelect value={departmentId} onChange={(next) => { setDepartmentId(next); list.resetPage() }}
          noneLabel="Bütün departmanlar" size="sm" className="w-56" />
        <ToggleGroup type="single" variant="outline" size="sm" value={active}
          onValueChange={(next) => { if (next) { setActive(next); list.resetPage() } }}>
          <ToggleGroupItem value="all">Hepsi</ToggleGroupItem>
          <ToggleGroupItem value="true">Aktif</ToggleGroupItem>
          <ToggleGroupItem value="false">Pasif</ToggleGroupItem>
        </ToggleGroup>
        <div className="flex items-center gap-1">
          <DatePicker value={hiredFrom} placeholder="İşe giriş ≥" className="h-8 w-40"
            onChange={(next) => { setHiredFrom(next); list.resetPage() }} />
          <DatePicker value={hiredTo} placeholder="İşe giriş ≤" className="h-8 w-40"
            onChange={(next) => { setHiredTo(next); list.resetPage() }} />
        </div>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Ad, e-posta veya kullanıcı adı…" />
      </div>
      <DataTable columns={columns} data={people.data?.data} meta={people.data?.meta} loading={people.isFetching}
        error={people.error} sort={list.sort} onSort={list.setSort} onPage={list.setPage} onSize={list.setSize} onOpen={setEditing}
        filtered={filtered} empty={{ icon: UserRoundIcon, title: "Personel yok", description: "İlk çalışanı ekleyin." }} />
      {editing && <PersonnelSheet key={editing === "new" ? "new" : editing.id} id={editing === "new" ? null : editing.id}
        onClose={() => setEditing(null)} />}
    </div>
  )
}

function PersonnelSheet({ id, onClose }: { id: number | null; onClose: () => void }) {
  const detail = useQuery({ queryKey: ["admin", "personnel", "detail", id], queryFn: () => adminApi.person(id!),
    enabled: id !== null, gcTime: 0 })
  if (id !== null && detail.isPending) {
    return (
      <FormSheet open onClose={onClose} title="Yükleniyor…" saving onSubmit={() => {}}>
        <Skeleton className="h-96 w-full" />
      </FormSheet>
    )
  }
  return <PersonnelForm detail={detail.data ?? null} onClose={onClose} />
}

function PersonnelForm({ detail, onClose }: { detail: PersonnelDetail | null; onClose: () => void }) {
  const person = detail?.personnel ?? null
  const user = useCurrentUser()
  const canSeeSalary = detail?.canSeeSalary ?? user.data?.authorities.includes("SALARY_VIEW") ?? false
  const canSeeNationalId = detail?.canSeeNationalId ?? user.data?.authorities.includes("NATIONAL_ID_VIEW") ?? false
  const save = useAdminSave<PersonnelDetail, PersonnelRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    fullName: person?.fullName ?? "", username: person?.username ?? "", email: person?.email ?? "",
    hireDate: person?.hireDate ?? undefined as string | undefined, active: person?.active ?? true,
    departmentId: person?.department?.id ?? null as number | null, salary: person?.salary?.toString() ?? "",
    nationalId: person?.nationalId ?? "",
  })
  const [manager, setManager] = useState<Ref | null>(person?.manager ?? null)
  const [profile, setProfile] = useState({
    birthDate: detail?.profile?.birthDate ?? undefined as string | undefined, bloodType: detail?.profile?.bloodType ?? "",
    city: detail?.profile?.city ?? "", emergencyContact: detail?.profile?.emergencyContact ?? "",
  })
  const [skillIds, setSkillIds] = useState<number[]>(detail?.skills.map((skill) => skill.id) ?? [])
  const set = (change: Partial<typeof form>) => setForm((current) => ({ ...current, ...change }))
  const errors = fieldErrors(save.error)

  const submit = () => save.mutate({ existing: person, body: {
    fullName: form.fullName, username: orNull(form.username), email: orNull(form.email), hireDate: form.hireDate ?? null,
    active: form.active, departmentId: form.departmentId, managerId: manager?.id ?? null,
    salary: canSeeSalary ? numberOrNull(form.salary) : null, nationalId: canSeeNationalId ? orNull(form.nationalId) : null,
    profile: { birthDate: profile.birthDate ?? null, bloodType: orNull(profile.bloodType), city: orNull(profile.city),
      emergencyContact: orNull(profile.emergencyContact) },
  } }, {
    onSuccess: async (saved) => {
      const before = (detail?.skills.map((skill) => skill.id) ?? []).sort().join(",")
      if ([...skillIds].sort().join(",") !== before) {
        try {
          await adminApi.setSkills(saved.personnel.id, skillIds)
          await queryClient.invalidateQueries({ queryKey: ["admin"] })
        } catch (error) {
          toast.error("Beceriler kaydedilemedi", { description: errorMessage(error) })
          return
        }
      }
      toast.success("Personel kaydedildi")
      onClose()
    },
  })

  return (
    <FormSheet open onClose={onClose} wide title={person ? person.fullName : "Yeni personel"}
      description={person ? <span className="flex flex-wrap items-center gap-2">#{person.id}
        {person.directReports > 0 && <Badge variant="secondary">{person.directReports} kişi bağlı</Badge>}</span> : undefined}
      saving={save.isPending} error={save.error ?? remove.error}
      onSubmit={submit} deleting={remove.isPending}
      onDelete={person ? () => remove.mutate(person.id, { onSuccess: () => { toast.success("Personel silindi"); onClose() } }) : undefined}
      deleteText="Profil ve becerileri silinir; eğitim kayıtları ve proje atamaları da kaldırılır, bağlı çalışanların yöneticisi boşalır. Ayrılan çalışanlar için genelde “Pasif” yapmak yeterlidir.">
      <Tabs defaultValue="general">
        <TabsList className="w-full">
          <TabsTrigger value="general">Genel</TabsTrigger>
          <TabsTrigger value="profile">Profil</TabsTrigger>
          <TabsTrigger value="skills">Beceriler {skillIds.length > 0 && <Badge variant="secondary">{skillIds.length}</Badge>}</TabsTrigger>
        </TabsList>
        <TabsContent value="general" className="pt-4">
          <FieldGroup>
            <Field data-invalid={errors.fullName ? true : undefined}>
              <FieldLabel htmlFor="person-name">Ad soyad</FieldLabel>
              <Input id="person-name" value={form.fullName} maxLength={255} aria-invalid={!!errors.fullName}
                onChange={(e) => set({ fullName: e.target.value })} />
              {errors.fullName && <FieldError>{errors.fullName}</FieldError>}
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={errors.email ? true : undefined}>
                <FieldLabel htmlFor="person-email">E-posta</FieldLabel>
                <Input id="person-email" type="email" value={form.email} aria-invalid={!!errors.email}
                  onChange={(e) => set({ email: e.target.value })} />
                {errors.email && <FieldError>{errors.email}</FieldError>}
              </Field>
              <Field data-invalid={errors.username ? true : undefined}>
                <FieldLabel htmlFor="person-username">Kullanıcı adı</FieldLabel>
                <Input id="person-username" value={form.username} maxLength={100} aria-invalid={!!errors.username}
                  onChange={(e) => set({ username: e.target.value })} />
                {errors.username ? <FieldError>{errors.username}</FieldError>
                  : <FieldDescription>Giriş adıyla eşleşir; boş bırakılabilir.</FieldDescription>}
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={errors.departmentId ? true : undefined}>
                <FieldLabel htmlFor="person-department">Departman</FieldLabel>
                <DepartmentSelect id="person-department" value={form.departmentId} allowNone={false}
                  onChange={(departmentId) => set({ departmentId })} />
                {errors.departmentId && <FieldError>{errors.departmentId}</FieldError>}
              </Field>
              <Field data-invalid={errors.managerId ? true : undefined}>
                <FieldLabel htmlFor="person-manager">Yönetici</FieldLabel>
                <PersonPicker id="person-manager" value={manager} onChange={setManager} exclude={person?.id} />
                {errors.managerId && <FieldError>{errors.managerId}</FieldError>}
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={errors.hireDate ? true : undefined}>
                <FieldLabel>İşe giriş</FieldLabel>
                <DatePicker value={form.hireDate} onChange={(hireDate) => set({ hireDate })} className="w-full" />
                {errors.hireDate && <FieldError>{errors.hireDate}</FieldError>}
              </Field>
              <Field orientation="horizontal" className="self-end pb-2">
                <Switch id="person-active" checked={form.active} onCheckedChange={(next) => set({ active: next })} />
                <FieldLabel htmlFor="person-active">Aktif çalışan</FieldLabel>
              </Field>
            </div>
            {(canSeeSalary || canSeeNationalId) && (
              <div className="grid gap-4 rounded-lg border border-dashed p-3 sm:grid-cols-2">
                {canSeeSalary && (
                  <Field data-invalid={errors.salary ? true : undefined}>
                    <FieldLabel htmlFor="person-salary">Maaş (TL)</FieldLabel>
                    <Input id="person-salary" type="number" min={0} value={form.salary} aria-invalid={!!errors.salary}
                      onChange={(e) => set({ salary: e.target.value })} />
                    {errors.salary && <FieldError>{errors.salary}</FieldError>}
                  </Field>
                )}
                {canSeeNationalId && (
                  <Field data-invalid={errors.nationalId ? true : undefined}>
                    <FieldLabel htmlFor="person-national">TC kimlik no</FieldLabel>
                    <Input id="person-national" inputMode="numeric" maxLength={11} value={form.nationalId}
                      aria-invalid={!!errors.nationalId} onChange={(e) => set({ nationalId: e.target.value.replace(/\D/g, "") })} />
                    {errors.nationalId && <FieldError>{errors.nationalId}</FieldError>}
                  </Field>
                )}
                <FieldDescription className="sm:col-span-2">Hassas alanlar: yalnızca maaş / kimlik yetkisi olanlar görür ve değiştirir.</FieldDescription>
              </div>
            )}
          </FieldGroup>
        </TabsContent>
        <TabsContent value="profile" className="pt-4">
          <FieldGroup>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel>Doğum tarihi</FieldLabel>
                <DatePicker value={profile.birthDate} onChange={(birthDate) => setProfile({ ...profile, birthDate })} className="w-full" />
              </Field>
              <Field data-invalid={errors["profile.bloodType"] ? true : undefined}>
                <FieldLabel htmlFor="person-blood">Kan grubu</FieldLabel>
                <Select value={profile.bloodType || NONE} onValueChange={(next) => setProfile({ ...profile, bloodType: next === NONE ? "" : next })}>
                  <SelectTrigger id="person-blood" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value={NONE}>—</SelectItem>
                      {BLOOD_TYPES.map((type) => <SelectItem key={type} value={type}>{type}</SelectItem>)}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="person-city">Şehir</FieldLabel>
              <Input id="person-city" value={profile.city} maxLength={255} onChange={(e) => setProfile({ ...profile, city: e.target.value })} />
            </Field>
            <Field>
              <FieldLabel htmlFor="person-emergency">Acil durumda aranacak kişi</FieldLabel>
              <Input id="person-emergency" value={profile.emergencyContact} maxLength={255} placeholder="Ad soyad · telefon"
                onChange={(e) => setProfile({ ...profile, emergencyContact: e.target.value })} />
            </Field>
          </FieldGroup>
        </TabsContent>
        <TabsContent value="skills" className="pt-4">
          <SkillChecklist value={skillIds} onChange={setSkillIds} />
        </TabsContent>
      </Tabs>
    </FormSheet>
  )
}

/** Every skill grouped by category, ticked when the person has it. */
function SkillChecklist({ value, onChange }: { value: number[]; onChange: (ids: number[]) => void }) {
  const skills = useLookup("skills", adminApi.skills)
  const [search, setSearch] = useState("")
  if (skills.isPending) return <Skeleton className="h-48 w-full" />
  const shown = (skills.data ?? []).filter((skill) => skill.name.toLocaleLowerCase("tr").includes(search.toLocaleLowerCase("tr")))
  const groups = new Map<string, typeof shown>()
  shown.forEach((skill) => groups.set(skill.category ?? "Diğer", [...(groups.get(skill.category ?? "Diğer") ?? []), skill]))
  return (
    <div className="flex flex-col gap-4">
      <SearchInput value={search} onChange={setSearch} placeholder="Beceri ara…" className="sm:w-full" />
      {[...groups.entries()].map(([category, items]) => (
        <div key={category} className="flex flex-col gap-2">
          <p className="text-xs font-medium text-muted-foreground">{category}</p>
          <div className="grid gap-2 sm:grid-cols-2">
            {items.map((skill) => (
              <Field key={skill.id} orientation="horizontal">
                <Checkbox id={`skill-${skill.id}`} checked={value.includes(skill.id)}
                  onCheckedChange={(checked) => onChange(checked ? [...value, skill.id] : value.filter((id) => id !== skill.id))} />
                <FieldLabel htmlFor={`skill-${skill.id}`} className="font-normal">{skill.name}</FieldLabel>
              </Field>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

import { PlusIcon, SparklesIcon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { PageHeader } from "@/shared/components/page-header"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import { adminApi } from "../api/admin.api"
import type { Skill, SkillRequest } from "../api/admin.types"
import { DataTable, type Column } from "../components/data-table"
import { FormSheet } from "../components/form-sheet"
import { useAdminDelete, useAdminList, useAdminSave, useLookup } from "../lib/admin-queries"
import { fieldErrors, orNull } from "../lib/format"

const PATH = "/api/skills"
const ALL = "__all__"

const COLUMNS: Column<Skill>[] = [
  { key: "name", header: "Beceri", sort: "name", cell: (row) => <span className="font-medium">{row.name}</span> },
  { key: "category", header: "Kategori", sort: "category",
    cell: (row) => row.category ? <Badge variant="outline">{row.category}</Badge> : <span className="text-muted-foreground">—</span> },
  { key: "holders", header: "Sahip olan", className: "text-right",
    cell: (row) => <span className="tabular-nums">{row.holders.toLocaleString("tr-TR")} kişi</span> },
]

/** /admin/skills */
export function SkillsPage() {
  const list = useListState("name")
  const [category, setCategory] = useState(ALL)
  const categories = useLookup("skill-categories", adminApi.skillCategories)
  const skills = useAdminList<Skill>("skills", PATH, {
    q: list.query || undefined, category: category === ALL ? undefined : category, sort: list.sort, page: list.page, size: list.size,
  })
  const [editing, setEditing] = useState<Skill | "new" | null>(null)
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Beceriler" description="Personelin seçebileceği beceri kataloğu."
        actions={<Button onClick={() => setEditing("new")}><PlusIcon data-icon="inline-start" />Yeni beceri</Button>} />
      <div className="flex flex-wrap items-center gap-3">
        <Select value={category} onValueChange={(next) => { setCategory(next); list.resetPage() }}>
          <SelectTrigger size="sm" className="w-48" aria-label="Kategori"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL}>Bütün kategoriler</SelectItem>
              {categories.data?.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
        <SearchInput className="sm:ml-auto" value={list.search} onChange={list.setSearch} placeholder="Beceri ara…" />
      </div>
      <DataTable columns={COLUMNS} data={skills.data?.data} meta={skills.data?.meta} loading={skills.isFetching}
        error={skills.error} sort={list.sort} onSort={list.setSort} onPage={list.setPage} onSize={list.setSize} onOpen={setEditing}
        filtered={!!list.query || category !== ALL}
        empty={{ icon: SparklesIcon, title: "Beceri yok", description: "Kataloğa ilk beceriyi ekleyin." }} />
      {editing && <SkillSheet key={editing === "new" ? "new" : editing.id} skill={editing === "new" ? null : editing}
        categories={categories.data ?? []} onClose={() => setEditing(null)} />}
    </div>
  )
}

function SkillSheet({ skill, categories, onClose }: { skill: Skill | null; categories: string[]; onClose: () => void }) {
  const save = useAdminSave<Skill, SkillRequest>(PATH)
  const remove = useAdminDelete(PATH)
  const [name, setName] = useState(skill?.name ?? "")
  const [category, setCategory] = useState(skill?.category ?? "")
  const errors = fieldErrors(save.error)
  return (
    <FormSheet open onClose={onClose} title={skill ? skill.name : "Yeni beceri"} saving={save.isPending}
      error={save.error ?? remove.error}
      onSubmit={() => save.mutate({ existing: skill, body: { name, category: orNull(category) } },
        { onSuccess: () => { toast.success("Beceri kaydedildi"); onClose() } })}
      deleting={remove.isPending}
      onDelete={skill ? () => remove.mutate(skill.id, { onSuccess: () => { toast.success("Beceri silindi"); onClose() } }) : undefined}
      deleteText={skill?.holders ? `${skill.holders} kişide bu beceri var; önce onlardan kaldırılmalı.` : undefined}>
      <FieldGroup>
        <Field data-invalid={errors.name ? true : undefined}>
          <FieldLabel htmlFor="skill-name">Ad</FieldLabel>
          <Input id="skill-name" value={name} required maxLength={255} aria-invalid={!!errors.name}
            onChange={(e) => setName(e.target.value)} />
          {errors.name && <FieldError>{errors.name}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="skill-category">Kategori</FieldLabel>
          <Input id="skill-category" list="skill-categories" value={category} maxLength={255}
            onChange={(e) => setCategory(e.target.value)} />
          <datalist id="skill-categories">{categories.map((item) => <option key={item} value={item} />)}</datalist>
          <FieldDescription>Var olan bir kategoriyi seçin ya da yenisini yazın.</FieldDescription>
        </Field>
      </FieldGroup>
    </FormSheet>
  )
}

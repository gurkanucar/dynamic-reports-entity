import { CheckIcon, ChevronsUpDownIcon, XIcon } from "lucide-react"
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { cn } from "@/lib/utils"
import { adminApi } from "../api/admin.api"
import type { Ref } from "../api/admin.types"
import { useLookup } from "../lib/admin-queries"

const NONE = "__none__"

/** A choice from a small list (departments, trainings): a plain select with an optional "none". */
export function RefSelect({ id, value, onChange, options, placeholder, allowNone = true, noneLabel = "—", className,
  size }: {
  id?: string
  value: number | null | undefined
  onChange: (value: number | null) => void
  options: Ref[] | undefined
  placeholder: string
  allowNone?: boolean
  noneLabel?: string
  className?: string
  size?: "sm" | "default"
}) {
  return (
    <Select value={value ? String(value) : allowNone ? NONE : undefined}
      onValueChange={(next) => onChange(next === NONE ? null : Number(next))}>
      <SelectTrigger id={id} size={size} className={cn("w-full", className)}><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {allowNone && <SelectItem value={NONE}>{noneLabel}</SelectItem>}
          {options?.map((option) => <SelectItem key={option.id} value={String(option.id)}>{option.name}</SelectItem>)}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

export function DepartmentSelect(props: Omit<Parameters<typeof RefSelect>[0], "options" | "placeholder"> & { placeholder?: string }) {
  const departments = useLookup("departments", adminApi.departments)
  return <RefSelect placeholder="Departman seçin" {...props} options={departments.data} />
}

export function TrainingSelect(props: Omit<Parameters<typeof RefSelect>[0], "options" | "placeholder"> & { placeholder?: string }) {
  const trainings = useLookup("trainings", adminApi.trainings)
  return <RefSelect placeholder="Eğitim seçin" {...props} options={trainings.data} />
}

/**
 * One person out of many thousands: searches the server as you type (at least 2 letters). `value` carries the
 * name so the current choice shows without a lookup.
 */
export function PersonPicker({ id, value, onChange, placeholder = "Kişi seçin", exclude, className, size }: {
  id?: string
  value: Ref | null
  onChange: (value: Ref | null) => void
  placeholder?: string
  /** E.g. the person being edited (cannot be their own manager). */
  exclude?: number
  className?: string
  size?: "sm" | "default"
}) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState("")
  const people = useQuery({
    queryKey: ["admin", "lookup", "people", search.trim()],
    queryFn: () => adminApi.people(search),
    enabled: open,
    staleTime: 30_000,
  })
  return (
    <div className={cn("flex w-full gap-1", className)}>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button id={id} type="button" variant="outline" size={size} role="combobox" aria-expanded={open}
            className="min-w-0 flex-1 justify-between font-normal">
            <span className={cn("truncate", !value && "text-muted-foreground")}>{value?.name ?? placeholder}</span>
            <ChevronsUpDownIcon data-icon="inline-end" className="opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-(--radix-popover-trigger-width) min-w-72 p-0" align="start">
          <Command shouldFilter={false}>
            <CommandInput placeholder="Ad veya e-posta yazın…" value={search} onValueChange={setSearch} />
            <CommandList>
              <CommandEmpty>{people.isFetching ? "Aranıyor…" : "Kimse bulunamadı."}</CommandEmpty>
              <CommandGroup>
                {people.data?.filter((person) => person.id !== exclude).map((person) => (
                  <CommandItem key={person.id} value={String(person.id)} onSelect={() => { onChange(person); setOpen(false) }}>
                    <CheckIcon className={cn(value?.id === person.id ? "opacity-100" : "opacity-0")} />
                    {person.name}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {value && (
        <Button type="button" variant="ghost" size={size === "sm" ? "icon-sm" : "icon"} aria-label="Temizle"
          onClick={() => onChange(null)}><XIcon /></Button>
      )}
    </div>
  )
}

import { ChevronsUpDownIcon } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"
import { findPath, type FieldPath } from "../lib/field-paths"

/** Searchable list of every field reachable from the root, e.g. "Departman › Yönetici › Ad soyad". */
export function FieldPicker({ paths, value, onChange, placeholder = "Alan seçin", allowEmpty, className }: {
  paths: FieldPath[]
  value: string | undefined
  onChange: (path: string | undefined) => void
  placeholder?: string
  allowEmpty?: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const selected = findPath(paths, value)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" role="combobox" aria-expanded={open} aria-label="Alan"
          className={cn("w-full min-w-0 justify-between", className)}>
          <span className="truncate">{selected?.label ?? value ?? allowEmpty ?? placeholder}</span>
          <ChevronsUpDownIcon data-icon="inline-end" />
        </Button>
      </PopoverTrigger>
      {/* height follows the free space below/above the trigger; the list scrolls inside it */}
      <PopoverContent className="flex max-h-(--radix-popover-content-available-height) w-(--radix-popover-trigger-width) min-w-80 max-w-[calc(100vw-2rem)] flex-col p-0"
        align="start" collisionPadding={16}>
        <Command className="min-h-0">
          <CommandInput placeholder="Alan ara…" />
          <CommandList className="max-h-[min(20rem,calc(var(--radix-popover-content-available-height)-3rem))]">
            <CommandEmpty>Alan bulunamadı.</CommandEmpty>
            {allowEmpty && (
              <CommandGroup>
                <CommandItem value="__empty__" onSelect={() => { onChange(undefined); setOpen(false) }}>
                  {allowEmpty}
                </CommandItem>
              </CommandGroup>
            )}
            {[0, 1, 2].map((depth) => {
              const items = paths.filter((p) => p.depth === depth)
              if (items.length === 0) return null
              return (
                <CommandGroup key={depth} heading={depth === 0 ? "Alanlar" : `${depth}. ilişki üzerinden`}>
                  {items.map((path) => (
                    <CommandItem key={path.path} value={`${path.label} ${path.path}`}
                      onSelect={() => { onChange(path.path); setOpen(false) }}>
                      <span className="truncate">{path.label}</span>
                      {path.toMany && <Badge variant="secondary">çoklu</Badge>}
                    </CommandItem>
                  ))}
                </CommandGroup>
              )
            })}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

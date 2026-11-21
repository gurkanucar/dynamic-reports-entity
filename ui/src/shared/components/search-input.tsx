import { SearchIcon, XIcon } from "lucide-react"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group"
import { cn } from "@/lib/utils"

/** A search box for server-side lists; the server searches from 2 characters on. */
export function SearchInput({ value, onChange, placeholder = "Ara…", className }: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
}) {
  return (
    <InputGroup className={cn("w-full sm:w-72", className)}>
      <InputGroupAddon><SearchIcon /></InputGroupAddon>
      <InputGroupInput type="search" aria-label={placeholder} placeholder={placeholder} value={value}
        onChange={(e) => onChange(e.target.value)} />
      {value && (
        <InputGroupAddon align="inline-end">
          <InputGroupButton size="icon-xs" aria-label="Aramayı temizle" onClick={() => onChange("")}><XIcon /></InputGroupButton>
        </InputGroupAddon>
      )}
    </InputGroup>
  )
}

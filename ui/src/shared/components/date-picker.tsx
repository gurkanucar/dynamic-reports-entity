import { format, isValid, parseISO } from "date-fns"
import { tr } from "date-fns/locale"
import { CalendarIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"

/** A date as "YYYY-MM-DD" (what the API takes), picked from a calendar and shown as "21 Kasım 2026". */
export function DatePicker({ value, onChange, placeholder = "Tarih seçin", className }: {
  value: string | undefined
  onChange: (value: string | undefined) => void
  placeholder?: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const date = value ? parseISO(value) : undefined
  const valid = date && isValid(date) ? date : undefined
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" aria-label="Tarih"
          className={cn("w-full justify-start font-normal", !valid && "text-muted-foreground", className)}>
          <CalendarIcon data-icon="inline-start" />
          <span className="truncate">{valid ? format(valid, "d MMMM yyyy", { locale: tr }) : placeholder}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar mode="single" selected={valid} defaultMonth={valid} locale={tr} captionLayout="dropdown"
          onSelect={(picked) => { onChange(picked ? format(picked, "yyyy-MM-dd") : undefined); setOpen(false) }} />
      </PopoverContent>
    </Popover>
  )
}
